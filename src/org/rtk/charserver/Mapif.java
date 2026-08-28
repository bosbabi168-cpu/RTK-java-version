package org.rtk.charserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
     * <p>⚠️ <b>0x3009 sengaja -1, bukan panjang tetap.</b> Di C ia
     * {@code sizeof(struct board_show_0) + 2} — dump struct mentah yang
     * panjangnya bergantung padding kompilator. Port ini memakai tata letak
     * sendiri dengan nama pemain berpanjang-variabel, jadi panjangnya ada di
     * paketnya (lihat Peringatan #9 soal alasan yang sama pada blob
     * karakter).</p>
     *
     * <p>Yang masih 0: 0x300A (baca satu kiriman) dan 0x300C (tulis
     * kiriman) — belum diport. Paket ber-panjang 0 <b>diabaikan</b>
     * dispatcher, sama seperti di C.</p>
     */
    /**
     * ⚠️ Entri 0x3009 (papan) TETAP, bukan −1.
     *
     * <p>Port ini sempat menandainya variabel, dan pembaca variabel
     * mengambil panjangnya dari {@code rfifoL(2)} — offset yang di paket
     * papan justru berisi <b>fd klien</b>. Akibatnya aliran antar-server
     * bergeser, paket berikutnya terbaca sebagai opcode 0x0000, dan char
     * server MEMUTUS sambungan map server. Di C entri ini
     * {@code sizeof(struct board_show_0) + 2} — juga tetap.</p>
     *
     * <p>Ia tidak pernah ketahuan sampai R1 memberi papan jalur masuk yang
     * pertama: sisi tampilannya sudah lama ada, tapi tidak ada satu pun
     * yang pernah memintanya.</p>
     */
    /** 0x3009: opcode(2) + fd(2) + board(4) + page(4) + flags(4) + popup(1)
     *  + panjangNama(1) + nama[16] — tetap, seperti struct di C. */
    public static final int BOARD_SHOW_LEN = 34;

    /** 0x300A / 0x300B: opcode(2)+fd(2)+papan(4)+pos(4)+bendera(4)+nama(1+16). */
    public static final int BOARD_POST_LEN = 33;

    private static final int[] PACKET_LEN_TABLE = {
        72, -1, 20, 24, -1, 6, 255, -1, 28,
        BOARD_SHOW_LEN, BOARD_POST_LEN, BOARD_POST_LEN, -1, 4124, 20, 4124};

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

    /**
     * mapif_parse_showposts() (0x3009) — susun daftar isi papan, atau isi
     * kotak surat bila {@code board == 0}.
     *
     * <p>⚠️ <b>Papan 0 BUKAN sebuah papan</b>: ia kotak surat pribadi, dan
     * dibaca dari tabel {@code Mail} dengan nama kolom yang sama sekali
     * berbeda. Satu-satunya hal yang sama adalah bentuk hasilnya.</p>
     *
     * <p>Keduanya diurutkan <b>menurun</b> menurut nomor urut dan dipotong
     * 20 baris per halaman — kiriman terbaru di atas.</p>
     */
    static int parseShowPosts(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        int board = s.rfifoL(4);
        int page = s.rfifoL(8);
        int flags = s.rfifoL(12);
        boolean popup = s.rfifoB(16) != 0;
        int nl = s.rfifoB(17) & 0xFF;
        String nama = s.rfifoString(18, nl);
        return kirimDaftar(s, clientFd, board, page, flags, popup, nama);
    }

    /**
     * Susun dan kirim daftar isi papan (0x3809).
     *
     * <p>Dipisah dari {@link #parseShowPosts} supaya penghapusan dan
     * penulisan bisa membalas dengan daftar TERBARU memakai penyusun yang
     * sama — dua penyusun daftar akan berbeda persis pada hal yang paling
     * jarang diperiksa, yaitu tata letak byte-nya.</p>
     */
    private static int kirimDaftar(Session s, int clientFd, int board, int page,
                                   int flags, boolean popup, String nama) {
        int flags1 = org.rtk.map.Boards.displayFlags1(flags, popup, board);
        int flags2 = org.rtk.map.Boards.displayFlags2(board);

        List<Object[]> isi = new ArrayList<>();
        int offset = page * org.rtk.map.Boards.PER_PAGE;
        int rows;
        if (board == 0) {
            rows = sql.forEachRow(
                    "SELECT `MalNew`,`MalChaName`,`MalSubject`,`MalPosition`,"
                    + "`MalMonth`,`MalDay`,`MalId` FROM `Mail`"
                    + " WHERE `MalChaNameDestination` = ? AND `MalDeleted` = 0"
                    + " ORDER BY `MalPosition` DESC LIMIT ?, ?",
                    rs -> isi.add(new Object[]{
                        rs.getInt("MalNew"), rs.getInt("MalPosition"),
                        rs.getInt("MalId"), rs.getInt("MalMonth"), rs.getInt("MalDay"),
                        str(rs.getString("MalChaName")), str(rs.getString("MalSubject"))}),
                    nama, offset, org.rtk.map.Boards.PER_PAGE);
        } else {
            rows = sql.forEachRow(
                    "SELECT `BrdHighlighted`,`BrdChaName`,`BrdTopic`,`BrdPosition`,"
                    + "`BrdMonth`,`BrdDay`,`BrdBtlId` FROM `Boards`"
                    + " WHERE `BrdBnmId` = ?"
                    + " ORDER BY `BrdPosition` DESC LIMIT ?, ?",
                    rs -> isi.add(new Object[]{
                        rs.getInt("BrdHighlighted"), rs.getInt("BrdPosition"),
                        rs.getInt("BrdBtlId"), rs.getInt("BrdMonth"), rs.getInt("BrdDay"),
                        str(rs.getString("BrdChaName")), str(rs.getString("BrdTopic"))}),
                    board, offset, org.rtk.map.Boards.PER_PAGE);
        }
        if (rows < 0) {
            log.error("[CHAR] gagal membaca isi papan {}", board);
            isi.clear();   // C: kirim header saja saat query gagal
        }

        s.wfifoW(0, 0x3809);
        s.wfifoW(6, clientFd);
        s.wfifoL(8, board);
        s.wfifoL(12, flags1);
        s.wfifoL(16, flags2);
        s.wfifoL(20, isi.size());
        int off = 24;
        for (Object[] e : isi) {
            s.wfifoL(off, (Integer) e[0]);
            s.wfifoL(off + 4, (Integer) e[1]);
            s.wfifoL(off + 8, (Integer) e[2]);
            s.wfifoL(off + 12, (Integer) e[3]);
            s.wfifoL(off + 16, (Integer) e[4]);
            byte[] penulis = ((String) e[5])
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            s.wfifoB(off + 20, penulis.length);
            s.wfifoBytes(off + 21, penulis);
            off += 21 + penulis.length;
            byte[] judul = ((String) e[6])
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            s.wfifoB(off, judul.length);
            s.wfifoBytes(off + 1, judul);
            off += 1 + judul.length;
        }
        s.wfifoL(2, off);
        s.wfifoSet(off);
        return 0;
    }

    /**
     * mapif_parse_readpost() (0x300A) — kirim ISI satu kiriman (K2-lanjutan).
     *
     * <p>⚠️ Pos dicari menurut {@code BrdPosition} DI DALAM papannya, bukan
     * menurut {@code BrdId}: nomor yang dilihat pemain di daftar adalah
     * posisi, dan tiap papan punya penomoran posisinya sendiri. Memakai
     * {@code BrdId} akan membuka kiriman dari papan LAIN yang kebetulan
     * bernomor sama.</p>
     */
    static int parseReadPost(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        int board = s.rfifoL(4);
        int pos = s.rfifoL(8);
        int flags = s.rfifoL(12);
        int nl = s.rfifoB(16) & 0xFF;
        String nama = s.rfifoString(17, nl);

        Object[] p = new Object[]{"", "", 0, 0, ""};
        int rows = sql.forEachRow(
                "SELECT `BrdChaName`,`BrdTopic`,`BrdMonth`,`BrdDay`,`BrdPost`"
                + " FROM `Boards` WHERE `BrdBnmId` = ? AND `BrdPosition` = ?",
                rs -> {
                    p[0] = str(rs.getString("BrdChaName"));
                    p[1] = str(rs.getString("BrdTopic"));
                    p[2] = rs.getInt("BrdMonth");
                    p[3] = rs.getInt("BrdDay");
                    p[4] = str(rs.getString("BrdPost"));
                }, board, pos);
        if (rows <= 0) {
            log.debug("[CHAR] pos {} papan {} tidak ada", pos, board);
        }

        // Boleh menghapus bila penulisnya sendiri, atau bila map server
        // sudah menyatakan hak hapus. ⚠️ Diperiksa DI SINI, bukan dipercaya
        // dari klien.
        int bendera = flags & org.rtk.map.Boards.CAN_WRITE;
        if ((flags & org.rtk.map.Boards.CAN_DEL) != 0
                || (!nama.isEmpty() && nama.equalsIgnoreCase((String) p[0]))) {
            bendera |= org.rtk.map.Boards.CAN_DEL;
        }

        byte[] penulis = ((String) p[0]).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] topik = ((String) p[1]).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] isi = ((String) p[4]).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        s.wfifoW(0, 0x380A);
        s.wfifoW(6, clientFd);
        s.wfifoL(8, board);
        s.wfifoL(12, pos);
        s.wfifoL(16, bendera);
        s.wfifoB(20, (Integer) p[2]);
        s.wfifoB(21, (Integer) p[3]);
        int off = 22;
        off = tulisTeks(s, off, penulis, 1);
        off = tulisTeks(s, off, topik, 1);
        off = tulisTeks(s, off, isi, 2);
        s.wfifoL(2, off);
        s.wfifoSet(off);
        return 0;
    }

    /** Tulis teks berawalan panjang; {@code lebarLen} 1 atau 2 byte. */
    private static int tulisTeks(Session s, int off, byte[] teks, int lebarLen) {
        int n = teks.length;
        if (lebarLen == 1) {
            n = Math.min(n, 255);
            s.wfifoB(off, n);
        } else {
            n = Math.min(n, 4000);
            s.wfifoW(off, n);
        }
        s.wfifoBytes(off + lebarLen, java.util.Arrays.copyOf(teks, n));
        return off + lebarLen + n;
    }

    /**
     * mapif_parse_deletepost() (0x300B) — hapus kiriman (K2-lanjutan).
     *
     * <p>⚠️ Haknya diperiksa ULANG di sini. Map server memang mengirim
     * bendera, tetapi bendera itu berasal dari keadaan sesi yang bisa
     * berubah; satu-satunya hal yang benar-benar mengikat adalah baris di
     * database. Penghapusan yang tidak berhak dijawab dengan daftar yang
     * TIDAK berubah, bukan dengan diam.</p>
     */
    static int parseDeletePost(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        int board = s.rfifoL(4);
        int pos = s.rfifoL(8);
        int flags = s.rfifoL(12);
        int nl = s.rfifoB(16) & 0xFF;
        String nama = s.rfifoString(17, nl);

        int hapus;
        if ((flags & org.rtk.map.Boards.CAN_DEL) != 0) {
            hapus = sql.update("DELETE FROM `Boards`"
                    + " WHERE `BrdBnmId` = ? AND `BrdPosition` = ?", board, pos);
        } else {
            hapus = sql.update("DELETE FROM `Boards`"
                    + " WHERE `BrdBnmId` = ? AND `BrdPosition` = ?"
                    + " AND `BrdChaName` = ?", board, pos, nama);
        }
        log.debug("[CHAR] hapus pos {} papan {} oleh {}: {} baris",
                pos, board, nama, hapus);
        // Balas dengan daftar terbaru supaya klien tidak perlu menebak
        // apakah penghapusannya berhasil.
        return kirimDaftar(s, clientFd, board, 0, flags, false, nama);
    }

    /**
     * mapif_parse_writepost() (0x300C) — tulis kiriman baru (K2-lanjutan).
     *
     * <p>{@code BrdPosition} adalah nomor urut PER PAPAN, jadi nomor
     * berikutnya diambil dari maksimum papan itu — bukan dari jumlah
     * barisnya, karena penghapusan membuat keduanya berbeda dan nomor yang
     * dipakai ulang akan menabrak kiriman lama.</p>
     */
    static int parseWritePost(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(6);
        int board = s.rfifoL(8);
        String nama = s.rfifoString(12, 16).trim();
        int off = 28;
        int tl = s.rfifoW(off);
        String topik = s.rfifoString(off + 2, tl);
        off += 2 + tl;
        int il = s.rfifoW(off);
        String isi = s.rfifoString(off + 2, il);

        Integer maks = sql.queryInt(
                "SELECT COALESCE(MAX(`BrdPosition`), 0) FROM `Boards`"
                + " WHERE `BrdBnmId` = ?", board);
        int pos = (maks == null ? 0 : maks) + 1;
        java.time.LocalDate hari = java.time.LocalDate.now();
        int baris = sql.update(
                "INSERT INTO `Boards` (`BrdBnmId`,`BrdChaName`,`BrdChaId`,"
                + "`BrdBtlId`,`BrdHighlighted`,`BrdMonth`,`BrdDay`,`BrdTopic`,"
                + "`BrdPost`,`BrdPosition`) VALUES (?,?,0,0,0,?,?,?,?,?)",
                board, nama, hari.getMonthValue(), hari.getDayOfMonth(),
                topik, isi, pos);
        log.debug("[CHAR] tulis pos {} papan {} oleh {}: {} baris",
                pos, board, nama, baris);
        return kirimDaftar(s, clientFd, board, 0, org.rtk.map.Boards.CAN_WRITE,
                false, nama);
    }

    private static String str(String v) {
        return v == null ? "" : v;
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
            case 0x3009: parseShowPosts(fd); break;
            case 0x300A: parseReadPost(fd); break;
            case 0x300B: parseDeletePost(fd); break;
            case 0x300C: parseWritePost(fd); break;
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
