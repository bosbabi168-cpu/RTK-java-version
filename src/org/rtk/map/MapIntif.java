package org.rtk.map;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.mmo.CharStatus;
import org.rtk.common.mmo.CharStatusCodec;

import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;

import static org.rtk.map.MapServer.*;

/**
 * Port of the inter-server part of map/intif.c (char-server link).
 *
 * Only the handshake and login-routing packets are implemented; everything
 * that requires the gameplay port (charstatus blobs 0x3803, boards, mail)
 * is logged and skipped. See the project README for the roadmap.
 */
public final class MapIntif {

    private static final Logger log = LogManager.getLogger(MapIntif.class);

    // packet lengths for 0x3800 .. 0x380E (subset; unknown = 255 like the C table)
    private static final int[] PACKET_LEN_TABLE = {
        4, -1, 38, -1, 6, -1, 255, -1, 5, -1, -1, 6, 6, 8, 6
    };

    private MapIntif() {
    }

    /** intif_parse_accept() (0x3800): char server accepted us. */
    static int parseAccept(int fd) {
        Session s = net.session(fd);
        if (s.rfifoB(2) != 0) {
            log.error("CFG_ERR: Username or password to connect Char Server is Invalid!");
            ServerLog.addLog("CFG_ERR: Username or password to connect Char Server is Invalid!%n");
            return 0;
        }

        log.info("Server ID: {}", s.rfifoB(3));
        ServerLog.addLog("Connected to Char Server.%n");
        log.info("Connected to Char Server.");

        // register our map list (0x3001)
        int mapN = loadedMaps.size();
        s.wfifoW(0, 0x3001);
        s.wfifoL(2, mapN * 2 + 8);
        s.wfifoW(6, mapN);
        for (int i = 0; i < mapN; i++) {
            s.wfifoW(i * 2 + 8, loadedMaps.get(i));
        }
        s.wfifoSet(mapN * 2 + 8);
        return 0;
    }

    /** intif_parse_authadd() (0x3802): a player was routed to this server. */
    static int parseAuthAdd(int fd) {
        Session s = net.session(fd);
        String name = s.rfifoString(8, 16);
        int charIdNum = s.rfifoL(4);
        int ip = s.rfifoL(34);
        log.info("[MAP] Incoming player: {} (id {}) from {}", name, charIdNum, Session.ipToString(ip));

        // acknowledge so the char server tells login to redirect the client
        int clientFd = s.rfifoW(2);
        s.wfifoW(0, 0x3002);
        s.wfifoW(2, clientFd);
        s.wfifoZero(4, 16);
        s.wfifoStringFixed(4, name, 16);
        s.wfifoSet(20);

        // JANGAN minta data karakter di sini. Di C (intif_parse_authadd,
        // intif.c:394) yang dilakukan hanya auth_add() + ack 0x3002; yang
        // memanggil intif_load() adalah clif_accept2() (clif.c:409) SETELAH
        // klien menyambung dan menyebutkan namanya — dengan fd klien yang
        // asli. `clientFd` di paket ini milik ruang fd CHAR SERVER, jadi
        // memakainya di sini salah dua kali: paket masuk dunia dikirim ke
        // sesi yang belum ada, dan User hantu terdaftar di onlineChars[fd]
        // sehingga fd yang sama saat dipakai klien sungguhan membuat paket
        // perkenalan 0x10 tidak pernah sampai ke clientAuth().
        return 0;
    }

    /**
     * intif_load() — minta data karakter ke char server (0x3003).
     *
     * Tata letak: W(0)=0x3003, W(2)=fd klien, L(4)=id karakter, nama[16]@8.
     */
    public static int requestChar(int clientFd, long charId, String name) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa minta karakter: belum terhubung ke char server");
            return -1;
        }
        s.wfifoW(0, 0x3003);
        s.wfifoW(2, clientFd);
        s.wfifoL(4, (int) charId);
        s.wfifoStringFixed(8, name, 16);
        s.wfifoSet(24);
        log.info("[MAP] minta data karakter {} (id={}) ke char server", name, charId);
        return 0;
    }

    /**
     * intif_parse_charload() (0x3803): char server mengirim data karakter.
     * Tata letak: W(0)=0x3803, L(2)=panjang total, W(6)=fd klien, blob@8.
     */
    static int parseCharLoad(int fd) {
        Session s = net.session(fd);
        int total = s.rfifoL(2);
        int clientFd = s.rfifoW(6);
        int blobLen = total - 8;
        if (blobLen <= 0) {
            log.error("[MAP] blob karakter kosong (panjang {})", blobLen);
            return 0;
        }

        CharStatus c;
        try {
            c = CharStatusCodec.decode(s.rfifoBytes(8, blobLen));
        } catch (IOException e) {
            log.error("[MAP] blob karakter rusak / tidak dikenal", e);
            return 0;
        }

        // bangun pemain runtime lalu tempatkan di dunia (pc_setpos + clif_spawn)
        org.rtk.map.User sd = new org.rtk.map.User(clientFd, c);
        MapServer.onlineChars.put(clientFd, sd);

        log.info("[MAP] karakter dimuat: {} (level {}, {} barang) untuk fd {}",
                c.name, c.level, c.inventory.size(), clientFd);

        if (!org.rtk.map.Pc.enterWorld(MapServer.world, sd)) {
            log.error("[MAP] {} gagal ditempatkan di dunia — koneksi ditutup", c.name);
            MapServer.onlineChars.remove(clientFd);
            Session cs = net.session(clientFd);
            if (cs != null) {
                cs.eof = true;
            }
        }
        return 0;
    }

    /**
     * intif_save() / intif_savequit() — simpan pemain yang <b>sedang di
     * dunia</b>: sinkronkan dulu posisi dan samarannya dari objek hidupnya,
     * baru kirim blobnya.
     *
     * <p>Ini yang dipakai {@code forceSave} dan jalur keluar-dunia. Tanpa
     * penyegaran itu, posisi yang tersimpan adalah posisi saat pemain
     * <b>masuk</b> — seluruh perjalanannya hilang.</p>
     *
     * <p>⚠️ Pada ragam {@code quit} ada satu cabang yang mudah terlewat:
     * bila peta tujuan pemain <b>tidak dimuat di server ini</b>, yang
     * disimpan adalah <b>peta tujuan</b>, bukan posisinya sekarang. Itu
     * jalur perpindahan antar-map-server (Trek C3): pemain sudah "berangkat"
     * secara logika meski raganya masih berdiri di peta lama.</p>
     */
    public static int saveChar(User sd, boolean quit) {
        if (sd == null) {
            return -1;
        }
        boolean tujuanDiServerLain = quit && sd.destMap >= 0
                && MapServer.world.get(sd.destMap) == null;
        if (tujuanDiServerLain) {
            sd.status.lastPos = new org.rtk.common.mmo.Point(
                    sd.destMap, sd.destX, sd.destY);
        } else {
            sd.status.lastPos = new org.rtk.common.mmo.Point(sd.m, sd.x, sd.y);
        }
        sd.status.disguise = (int) sd.graphicId;
        sd.status.disguiseColor = (int) sd.graphicColor;
        return saveChar(sd.status, quit);
    }

    /**
     * Bagian kirimnya saja — dipakai bila pemanggilnya sudah menyegarkan
     * posisi sendiri, atau menyimpan karakter yang tidak sedang di dunia.
     * <p>Tata letak: W(0)=opcode, L(2)=panjang total, blob@6.</p>
     *
     * @param quit true memakai 0x3007 (simpan + logout), false 0x3004
     */
    public static int saveChar(CharStatus c, boolean quit) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa menyimpan {}: belum terhubung ke char server", c.name);
            return -1;
        }
        byte[] blob;
        try {
            blob = CharStatusCodec.encode(c);
        } catch (IOException e) {
            log.error("[MAP] gagal menyusun blob karakter {}", c.name, e);
            return -1;
        }
        s.wfifoW(0, quit ? 0x3007 : 0x3004);
        s.wfifoL(2, 6 + blob.length);
        s.wfifoBytes(6, blob);
        s.wfifoSet(6 + blob.length);
        log.info("[MAP] simpan karakter {} ({} byte){}", c.name, blob.length, quit ? " + logout" : "");
        return 0;
    }

    /**
     * nmail_sendmail() (0x300D) — titipkan surat ke char server.
     *
     * <p>Map server <b>tidak</b> menyentuh tabel {@code Mail} sendiri:
     * pemain penerimanya bisa sedang berada di map server lain, dan char
     * server yang memegang nama karakter. Panjangnya tetap 4124 byte.</p>
     *
     * <p>⚠️ Batasnya dijaga di sini persis seperti C: nama &gt; 16, judul
     * &gt; 52, atau isi &gt; 4000 karakter membuat suratnya <b>tidak
     * dikirim sama sekali</b> — bukan dipotong.</p>
     *
     * @param copy true mengirim 0x300F (salinan arsip pengirim), yang tidak
     *             dibalas char server
     */
    public static boolean sendMail(User sd, String to, String topic, String body,
                                   boolean copy) {
        String tujuan = to == null ? "" : to;
        String judul = topic == null ? "" : topic;
        String isi = body == null ? "" : body;
        if (tujuan.length() > 16 || judul.length() > 52 || isi.length() > 4000) {
            log.warn("[MAP] surat dari {} ditolak: ladangnya melebihi batas", sd.name());
            return false;
        }
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa mengirim surat: belum terhubung ke char server");
            return false;
        }
        s.wfifoW(0, copy ? 0x300F : 0x300D);
        s.wfifoW(2, sd.fd);
        s.wfifoStringRaw(4, sd.status.name);
        s.wfifoStringRaw(20, tujuan);
        s.wfifoStringRaw(72, judul);
        s.wfifoStringRaw(124, isi);
        s.wfifoSet(4124);
        return true;
    }

    /**
     * intif_parse (0x380C): hasil penulisan surat.
     * 0 berhasil, 1 gagal database, 2 penerima tidak ditemukan.
     */
    static int parseMailResult(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        int hasil = s.rfifoW(6);
        User sd = MapServer.onlineChars.get(clientFd);
        if (sd == null) {
            return 0;
        }
        String pesan = switch (hasil) {
            case 0 -> "Suratmu terkirim.";
            case 2 -> "Nama penerima tidak ditemukan.";
            default -> "Surat gagal dikirim.";
        };
        MapServer.clientView.messageToPlayer(sd, 5, pesan);
        return 0;
    }

    /**
     * intif_parse (0x380D): seorang pemain punya kiriman baru.
     *
     * <p>Disiarkan char server ke <b>semua</b> map server, karena
     * penerimanya bisa ada di mana saja. Yang tidak menampung pemain itu
     * mengabaikannya.</p>
     */
    static int parseNewMailFlag(int fd) {
        Session s = net.session(fd);
        long charId = s.rfifoL(2) & 0xFFFFFFFFL;
        int flags = s.rfifoW(6);
        for (User sd : MapServer.onlineChars.values()) {
            if (sd.status.id == charId) {
                sd.flags |= flags;
                MapServer.clientView.messageToPlayer(sd, 5, "Kamu punya surat baru.");
                return 0;
            }
        }
        return 0;
    }

    /** intif_parse_checkonline() (0x3804): kick a double-logged character. */
    static int parseCheckOnline(int fd) {
        Session s = net.session(fd);
        log.info("[MAP] Force-disconnect request for char id {} (no players hosted yet in the skeleton).",
                s.rfifoL(2));
        return 0;
    }

    /** intif_parse(): dispatcher for packets from the char server. */
    public static int intifParse(int fd) {
        Session s = net.session(fd);
        if (s == null || fd != charFd) {
            return 0;
        }
        if (s.eof) {
            log.warn("Char Server connection lost.");
            ServerLog.addLog("Char Server connection lost.%n");
            charFd = 0;
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 2) {
            return 0;
        }

        int cmd = s.rfifoW(0);
        if (cmd < 0x3800 || cmd >= 0x3800 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x3800] == 0) {
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x3800];
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
            case 0x3800: parseAccept(fd); break;
            case 0x3802: parseAuthAdd(fd); break;
            case 0x3803: parseCharLoad(fd); break;
            case 0x3804: parseCheckOnline(fd); break;
            case 0x380C: parseMailResult(fd); break;
            case 0x380D: parseNewMailFlag(fd); break;
            default:
                log.debug("[MAP] Packet {} not ported yet (see README roadmap)", String.format("0x%04X", cmd));
                break;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }
}
