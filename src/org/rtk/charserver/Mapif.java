package org.rtk.charserver;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.mmo.CharStatus;
import org.rtk.common.mmo.CharStatusCodec;

import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;

import static org.rtk.charserver.CharServer.*;

/**
 * Port of char/mapif.c — the char-server side of the map-server link.
 *
 * The handshake (0x3000), map registration (0x3001), login routing (0x3002),
 * logout (0x3005) and broadcast plumbing are fully ported. The handlers that
 * move the compressed mmo_charstatus blob (0x3003 request / 0x3004 save) and
 * the message-board / mail traffic depend on the map-server gameplay port and
 * currently log a warning; see the project README for the porting roadmap.
 */
public final class Mapif {

    private static final Logger log = LogManager.getLogger(Mapif.class);

    public static final int ALL = 0;
    public static final int ALLWOS = 1;

    /**
     * Panjang paket 0x3000 .. 0x300F; -1 berarti panjangnya ada di paketnya
     * sendiri (L di offset 2).
     *
     * <p>Yang masih 0: papan pesan (0x3009 boards_show, 0x300A read_post,
     * 0x300C boardpost) — panjangnya di C dihitung dari
     * {@code sizeof(struct ...)}, jadi tidak bisa disalin sebagai angka dan
     * menunggu subsistem papannya diport. Paket ber-panjang 0 <b>diabaikan</b>
     * dispatcher, sama seperti di C.</p>
     */
    private static final int[] PACKET_LEN_TABLE = {
        72, -1, 20, 24, -1, 6, 255, -1, 28,
        0, 0, 4, 0, 4124, 20, 4124};

    /** Panjang tetap balasan 0x3803 sebelum blob: opcode+len+fd. */
    private static final int CHARLOAD_HEADER = 8;

    private Mapif() {
    }

    /** mapif_send(): broadcast a raw inter-server packet to map servers. */
    public static int send(int fromFd, byte[] buf, int len, int type) {
        for (int i = 0; i < mapFifoMax; i++) {
            if (mapFifo[i].fd > 0 && net.session(mapFifo[i].fd) != null) {
                if (type == ALLWOS && mapFifo[i].fd == fromFd) {
                    continue;
                }
                Session s = net.session(mapFifo[i].fd);
                s.wfifoBytes(0, buf, len);
                s.wfifoSet(len);
            }
        }
        return 0;
    }

    /** Panjang ladang tetap pada paket surat 0x300D / 0x300F. */
    private static final int MAIL_NAME_LEN = 16;
    private static final int MAIL_TOPIC_LEN = 52;
    private static final int MAIL_BODY_LEN = 4000;

    /** FLAG_MAIL (common/mmo.h:55) — penanda "ada surat baru". */
    public static final int FLAG_MAIL = 16;

    /**
     * mapif_parse_nmailwrite() (0x300D) / nmailwritecopy() (0x300F) —
     * map server menitipkan surat, char server yang menuliskannya.
     *
     * <p>Tata letaknya tetap: nama pengirim di 4, penerima di 20, judul di
     * 72, isi di 124. ⚠️ Perhatikan <b>lubang</b> antara penerima (16 byte
     * dari offset 20, berakhir di 36) dan judul (offset 72): 36 byte tak
     * terpakai. Itu ada di C — jangan dirapatkan.</p>
     *
     * <p>{@code MalPosition} adalah nomor urut <b>per penerima</b>, bukan
     * kunci utama: yang dipakai adalah tertinggi + 1.</p>
     *
     * @param copy true untuk 0x300F, yang <b>tidak membalas apa pun</b> —
     *             ia salinan untuk arsip pengirim, jadi map server tidak
     *             menunggu jawabannya
     */
    static int parseMailWrite(int fd, boolean copy) {
        Session s = net.session(fd);
        int sfd = s.rfifoW(2);
        String from = s.rfifoString(4, MAIL_NAME_LEN);
        String to = s.rfifoString(20, MAIL_NAME_LEN);
        String topic = s.rfifoString(72, MAIL_TOPIC_LEN);
        String body = s.rfifoString(124, MAIL_BODY_LEN);

        Integer toId = sql.queryInt(
                "SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", to);
        if (toId == null) {
            log.info("[CHAR] surat dari '{}' gagal: penerima '{}' tidak ada", from, to);
            if (!copy) {
                balasSurat(s, sfd, 2);   // 2 = penerima tidak ditemukan
            }
            return 0;
        }

        Integer max = sql.queryInt(
                "SELECT MAX(`MalPosition`) FROM `Mail` WHERE `MalChaNameDestination` = ?", to);
        int pos = (max == null ? 0 : max) + 1;

        int n = sql.update(
                "INSERT INTO `Mail` (`MalChaName`, `MalChaNameDestination`, `MalPosition`,"
                + " `MalSubject`, `MalBody`, `MalMonth`, `MalDay`, `MalNew`)"
                + " VALUES (?, ?, ?, ?, ?, DATE_FORMAT(CURDATE(),'%m'),"
                + " DATE_FORMAT(CURDATE(),'%d'), 1)",
                from, to, pos, topic, body);
        if (n <= 0) {
            if (!copy) {
                balasSurat(s, sfd, 1);   // 1 = gagal di database
            }
            return 0;
        }

        log.info("[CHAR] surat '{}' -> '{}' tersimpan di posisi {}", from, to, pos);
        if (copy) {
            return 0;   // salinan arsip: tidak ada balasan, tidak ada penanda
        }
        balasSurat(s, sfd, 0);
        notifyNewMail(fd, toId, FLAG_MAIL);
        return 0;
    }

    /** Balasan 0x380C: 0 berhasil, 1 gagal database, 2 penerima tak ditemukan. */
    private static void balasSurat(Session s, int sfd, int hasil) {
        s.wfifoW(0, 0x380C);
        s.wfifoW(2, sfd);
        s.wfifoW(6, hasil);
        s.wfifoSet(8);
    }

    /**
     * mapif_send_newmp(): beri tahu <b>semua</b> map server bahwa seorang
     * pemain punya kiriman baru — yang bersangkutan mungkin sedang berada di
     * map server lain.
     */
    static int notifyNewMail(int fromFd, long charId, int flags) {
        byte[] data = new byte[8];
        data[0] = 0x0D;
        data[1] = 0x38;
        data[2] = (byte) (charId & 0xFF);
        data[3] = (byte) ((charId >> 8) & 0xFF);
        data[4] = (byte) ((charId >> 16) & 0xFF);
        data[5] = (byte) ((charId >> 24) & 0xFF);
        data[6] = (byte) (flags & 0xFF);
        data[7] = (byte) ((flags >> 8) & 0xFF);
        return send(fromFd, data, 8, ALL);
    }

    /** mapif_parse_auth(): first packet of an incoming map-server link. */
    public static int parseAuth(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 2) {
            return 0;
        }

        int cmd = s.rfifoW(0);
        if (cmd < 0x3000 || cmd >= 0x3000 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x3000] == 0) {
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x3000];
        if (packetLen == -1) {
            if (s.rfifoRest() < 6) {
                return 2;
            }
            packetLen = s.rfifoL(2);
        }
        if (s.rfifoRest() < packetLen) {
            return 2;
        }

        if (cmd == 0x3000) {
            String id = s.rfifoString(2, 32);
            String pw = s.rfifoString(34, 32);
            // Faithful port note: like intif_auth, the C code only rejects
            // when BOTH credentials mismatch.
            if (!id.equals(charId) && !pw.equals(charPw)) {
                s.wfifoW(0, 0x3800);
                s.wfifoB(2, 0x01);
                s.wfifoB(3, 0x00);
                s.wfifoSet(4);
                s.eof = true;
                s.rfifoSkip(packetLen);
                return 0;
            }

            int i;
            for (i = 0; i < mapFifoMax; i++) {
                if (mapFifo[i].fd == 0) {
                    break;
                }
            }
            if (i >= mapFifoMax) {
                mapFifoMax++;
            }

            mapFifoN++;
            mapFifo[i].fd = fd;
            mapFifo[i].ip = s.rfifoL(66);
            mapFifo[i].port = s.rfifoW(70);
            final int serverIndex = i;
            s.funcParse = f -> mapifParse(f, serverIndex);

            s.wfifoW(0, 0x3800);
            s.wfifoB(2, 0x00);
            s.wfifoB(3, i); // map-server #
            s.wfifoSet(4);

            String ipStr = Session.ipToString(mapFifo[i].ip);
            log.info("Map Server #{} connected.({}:{}).", i, ipStr, mapFifo[i].port);
            ServerLog.addLog("Map Server #%d connected(%s:%d).%n", i, ipStr, mapFifo[i].port);
        } else {
            s.eof = true;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }

    /** mapif_parse_mapset() (0x3001): map server announces its map list. */
    static int parseMapset(int fd, int id) {
        Session s = net.session(fd);
        mapFifo[id].mapN = s.rfifoW(6);
        mapFifo[id].map = new int[mapFifo[id].mapN];
        for (int i = 0; i < mapFifo[id].mapN; i++) {
            mapFifo[id].map[i] = s.rfifoW(i * 2 + 8);
        }

        // warn about duplicate map ids across servers, like the C code
        for (int i = 0; i < mapFifoMax; i++) {
            if (i == id || mapFifo[i].fd <= 0) {
                continue;
            }
            for (int j = 0; j < mapFifo[i].mapN; j++) {
                for (int k = 0; k < mapFifo[id].mapN; k++) {
                    if (mapFifo[i].map[j] == mapFifo[id].map[k]) {
                        log.warn("MAPERR: Same map number(#{}) at server #{} and server #{}",
                                mapFifo[id].map[k], id, i);
                        ServerLog.addLog("MAPERR: Same map number(#%d) at server #%d and server #%d%n",
                                mapFifo[id].map[k], id, i);
                    }
                }
            }
        }

        log.info("Map Server #{}({} map) is ready!", id, mapFifo[id].mapN);
        ServerLog.addLog("Map Server #%d send %d map.%n", id, mapFifo[id].mapN);
        return 0;
    }

    /** mapif_parse_login() (0x3002): map accepted a player — tell login. */
    static int parseLogin(int fd, int id) {
        if (loginFd <= 0 || net.session(loginFd) == null) {
            return 0;
        }
        Session s = net.session(fd);
        Session ls = net.session(loginFd);
        ls.wfifoW(0, 0x2003);
        ls.wfifoW(2, s.rfifoW(2));
        ls.wfifoB(4, 0x00);
        ls.wfifoZero(5, 16);
        ls.wfifoBytes(5, s.rfifoBytes(4, 16));
        ls.wfifoL(21, mapFifo[id].ip);
        ls.wfifoW(25, mapFifo[id].port);
        ls.wfifoSet(27);
        return 0;
    }

    /**
     * mapif_parse_requestchar() (0x3003): map server minta data karakter.
     * Balas dengan 0x3803 berisi blob {@link CharStatusCodec}.
     *
     * Tata letak: W(0)=0x3003, W(2)=fd klien, L(4)=id karakter, nama[16]@8.
     */
    static int parseRequestChar(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        long charId = s.rfifoL(4) & 0xFFFFFFFFL;
        String name = s.rfifoString(8, 16);

        CharStatus c = CharPersistence.load(sql, charId);
        if (c == null) {
            log.error("[CHAR] permintaan karakter id={} ({}) tidak ditemukan di database", charId, name);
            return 0;
        }

        byte[] blob;
        try {
            blob = CharStatusCodec.encode(c);
        } catch (IOException e) {
            log.error("[CHAR] gagal menyusun blob karakter {} ({})", charId, name, e);
            return 0;
        }

        s.wfifoW(0, 0x3803);
        s.wfifoL(2, CHARLOAD_HEADER + blob.length);
        s.wfifoW(6, clientFd);
        s.wfifoBytes(CHARLOAD_HEADER, blob);
        s.wfifoSet(CHARLOAD_HEADER + blob.length);

        log.info("[CHAR] kirim karakter {} (id={}) ke map server, {} byte", c.name, charId, blob.length);
        return 0;
    }

    /**
     * mapif_parse_savechar() (0x3004) dan mapif_parse_savecharlog() (0x3007).
     *
     * Tata letak: W(0)=opcode, L(2)=panjang total, blob@6.
     *
     * Catatan port: versi C membaca panjang blob dengan
     * {@code RFIFOL(fd,1) - 6} padahal dispatcher-nya menulis/membaca panjang
     * di offset 2 — meleset satu byte. Di sini dipakai offset 2 secara
     * konsisten.
     *
     * @param alsoLogout true untuk 0x3007 (simpan lalu tandai offline)
     */
    static int parseSaveChar(int fd, boolean alsoLogout) {
        Session s = net.session(fd);
        int total = s.rfifoL(2);
        int blobLen = total - 6;
        if (blobLen <= 0 || blobLen > s.rfifoRest() - 6) {
            log.error("[CHAR] panjang blob simpan tidak masuk akal: {} byte", blobLen);
            return 0;
        }

        CharStatus c;
        try {
            c = CharStatusCodec.decode(s.rfifoBytes(6, blobLen));
        } catch (IOException e) {
            log.error("[CHAR] blob simpan rusak / tidak dikenal", e);
            return 0;
        }

        boolean ok = CharPersistence.save(sql, c);
        log.info("[CHAR] simpan karakter {} (id={}) {}{}", c.name, c.id,
                ok ? "berhasil" : "GAGAL", alsoLogout ? " + logout" : "");

        if (alsoLogout) {
            CharDb.logindataDel((int) c.id);
        }
        return 0;
    }

    /** mapif_parse_logout() (0x3005). */
    static int parseLogout(int fd) {
        Session s = net.session(fd);
        CharDb.logindataDel(s.rfifoL(2));
        return 0;
    }

    /** mapif_parse(): dispatcher once a map server is authenticated. */
    public static int mapifParse(int fd, int id) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            log.warn("Map Server #{} connection lost.", id);
            ServerLog.addLog("Map Server #%d connection lost.%n", id);
            mapFifo[id].fd = 0;
            mapFifo[id].mapN = 0;
            mapFifoN--;
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 2) {
            return 0;
        }

        int cmd = s.rfifoW(0);
        if (cmd < 0x3000 || cmd >= 0x3000 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x3000] == 0) {
            // Board/mail/charstatus packets from the un-ported part of the
            // protocol: drop what we can measure, otherwise resync is
            // impossible and the link must be closed.
            log.warn("[CHAR] Unhandled inter-server packet {} from map server #{}",
                    String.format("0x%04X", cmd), id);
            s.eof = true;
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x3000];
        if (packetLen == -1) {
            if (s.rfifoRest() < 6) {
                return 2;
            }
            packetLen = s.rfifoL(2);
        }
        if (s.rfifoRest() < packetLen) {
            return 2;
        }

        switch (cmd) {
            case 0x3001: parseMapset(fd, id); break;
            case 0x3002: parseLogin(fd, id); break;
            case 0x3003: parseRequestChar(fd); break;
            case 0x3004: parseSaveChar(fd, false); break;
            case 0x3005: parseLogout(fd); break;
            case 0x3007: parseSaveChar(fd, true); break;
            case 0x300D: parseMailWrite(fd, false); break;
            case 0x300F: parseMailWrite(fd, true); break;
            default:
                log.debug("[CHAR] Packet {} not ported yet (see README roadmap)", String.format("0x%04X", cmd));
                break;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }
}
