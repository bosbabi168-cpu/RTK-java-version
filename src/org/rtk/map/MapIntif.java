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
    // ⚠️ Entri 0x380A (isi satu kiriman papan) BERPANJANG VARIABEL: badannya
    // memuat penulis, topik, dan isi bebas. Panjangnya ditulis di [2..5],
    // bukan di [2..3] yang pada paket lain berisi fd — pelajaran #103.
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
    /**
     * Permintaan data karakter yang sedang menunggu jawaban, per fd klien.
     *
     * <p>⚠️ Ini yang menutup satu lubang serius. Balasan char server hanya
     * membawa <b>nomor fd</b>, dan nomor fd <b>dipakai ulang</b>: begitu
     * sebuah sesi ditutup, {@code newFd()} memberikan slot terkecil yang
     * kosong ke sambungan berikutnya. Kalau pemain memutus tepat setelah
     * memperkenalkan diri, balasannya tiba saat fd itu <b>sudah milik orang
     * lain</b> — dan tanpa penjaga, karakter pemain pertama dipasang ke
     * sambungan pemain kedua.</p>
     *
     * <p>Yang disimpan bukan hanya namanya melainkan <b>objek sesinya</b>:
     * sambungan baru selalu objek {@link Session} yang berbeda, jadi
     * perbandingan identitas menutup kasus "orang yang sama menyambung
     * ulang" sekalipun.</p>
     */
    private record Menunggu(String nama, Session sesi) {
    }

    private static final java.util.Map<Integer, Menunggu> menunggu =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Lupakan permintaan yang menggantung saat pemain memutus.
     *
     * <p>Tanpa ini, entri untuk fd yang sudah mati menumpuk sampai fd itu
     * dipakai ulang. Bukan kebocoran besar — jumlah fd terbatas — tetapi
     * entri basi membuat penjaga di {@code parseCharLoad} menilai keadaan
     * yang sudah tidak ada.</p>
     */
    public static void lupakanPermintaan(int clientFd) {
        menunggu.remove(clientFd);
    }

    public static int requestChar(int clientFd, long charId, String name) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa minta karakter: belum terhubung ke char server");
            return -1;
        }
        menunggu.put(clientFd, new Menunggu(name, net.session(clientFd)));
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

        // ⚠️ Pastikan fd ini MASIH milik sesi yang meminta. Lihat catatan di
        // `menunggu`: fd dipakai ulang, dan balasan yang terlambat bisa
        // memasang karakter orang lain ke sambungan yang sedang hidup.
        // ⚠️ LIHAT dulu, baru hapus — dan hanya hapus kalau memang COCOK.
        //
        // Versi pertama penjaga ini memakai `remove()` di baris pertama, dan
        // itu bug yang lebih halus daripada yang diperbaikinya: balasan BASI
        // untuk fd yang sama (dari sambungan sebelumnya yang sudah mati)
        // ikut menghapus entri milik permintaan yang MASIH HIDUP di fd itu.
        // Balasan basinya ditolak dengan benar, lalu balasan yang sah tiba
        // dan tidak menemukan entri apa pun — sehingga ikut dibuang.
        //
        // Gejalanya: pemain kedua menyambung, server mencatat perkenalannya,
        // dan setelah itu ia tidak pernah menerima satu byte pun. Nol error.
        // Itu Peringatan #96, dan sebabnya ada di sini.
        Menunggu m = menunggu.get(clientFd);
        Session cs0 = net.session(clientFd);
        if (m == null || cs0 == null || cs0 != m.sesi()
                || !c.name.equalsIgnoreCase(m.nama())) {
            log.warn("[MAP] data karakter {} untuk fd {} dibuang: bukan jawaban bagi "
                    + "permintaan yang sedang menunggu di fd itu", c.name, clientFd);
            return 0;
        }
        menunggu.remove(clientFd);

        // bangun pemain runtime lalu tempatkan di dunia (pc_setpos + clif_spawn)
        org.rtk.map.User sd = new org.rtk.map.User(clientFd, c);
        // Protokolnya sudah dipilih saat perkenalan, jauh sebelum datanya
        // tiba dari char server; di sinilah keduanya bertemu.
        sd.rtk2 = MapServer.rtk2Fds.remove(clientFd);
        MapServer.onlineChars.put(clientFd, sd);

        log.info("[MAP] karakter dimuat: {} (level {}, {} barang) untuk fd {}",
                c.name, c.level, c.inventory.size(), clientFd);

        if (!org.rtk.map.Pc.enterWorld(MapServer.world, sd)) {
            MapServer.onlineChars.remove(clientFd);
            if (sd.pindahTertunda) {
                // ⚠️ Sambungannya TIDAK ditutup seketika: paket alihannya
                // masih di outbox, dan `sessionEof` menutup soket tanpa
                // menunggu ia terkirim. Klien yang tidak pernah menerima
                // alamat tujuannya hanya melihat sambungan mati.
                log.info("[MAP] {} dialihkan ke map server lain — sambungan "
                        + "ditutup setelah paketnya terkirim", c.name);
                MapServer.timers.insert(TUTUP_SETELAH_ALIH_MS, 0,
                        (a, b) -> tutupSesi(clientFd), 0, 0);
            } else {
                log.error("[MAP] {} gagal ditempatkan di dunia — koneksi ditutup", c.name);
                tutupSesi(clientFd);
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

    /**
     * boards_showposts() (0x3009) — minta daftar isi papan dari char server.
     *
     * <p>⚠️ Tata letaknya <b>rancangan sendiri</b>, bukan tiruan
     * {@code struct board_show_0}: kedua ujungnya kode Java kita, dan dump
     * struct C bergantung pada padding kompilator. Alasan yang sama dengan
     * blob karakter — lihat {@link org.rtk.map.Boards} dan Peringatan #9.</p>
     *
     * <p>Tata letak: [0..1] opcode, [2..3] fd klien, [4..7] id papan,
     * [8..11] halaman, [12..15] bendera hak akses, [16] penanda popup,
     * [17] panjang nama, [18..] nama pemain.</p>
     */
    public static boolean requestBoard(User sd, int board, int page, int flags,
                                       boolean popup) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa membuka papan: belum terhubung ke char server");
            return false;
        }
        // ⚠️ Panjangnya TETAP 34 byte dengan ladang nama 16 byte — bukan
        // "18 + panjang nama". Tabel panjang di char server tidak bisa
        // menebak ukuran variabel untuk opcode ini (ladang [2] berisi fd,
        // bukan panjang), dan paket sepanjang isi membuat aliran
        // antar-server bergeser lalu sambungannya diputus. Di C strukturnya
        // juga berukuran tetap.
        byte[] nama = sd.status.name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int n = Math.min(nama.length, 16);
        s.wfifoW(0, 0x3009);
        s.wfifoW(2, sd.fd);
        s.wfifoL(4, board);
        s.wfifoL(8, page);
        s.wfifoL(12, flags);
        s.wfifoB(16, popup ? 1 : 0);
        s.wfifoB(17, n);
        for (int i = 0; i < 16; i++) {
            s.wfifoB(18 + i, i < n ? nama[i] : 0);
        }
        s.wfifoSet(org.rtk.charserver.Mapif.BOARD_SHOW_LEN);
        return true;
    }

    /**
     * boards_readpost() (0x300A) — minta ISI satu kiriman (K2-lanjutan).
     *
     * <p>Panjangnya TETAP, dengan alasan yang sama seperti 0x3009: tabel
     * panjang di char server tidak bisa menebak ukuran variabel untuk
     * opcode ini. Tata letak: [0..1] opcode, [2..3] fd klien, [4..7] papan,
     * [8..11] pos, [12..15] bendera hak akses, [16] panjang nama,
     * [17..32] nama pemain.</p>
     */
    public static boolean requestPost(User sd, int board, int pos, int flags) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa membaca pos: belum terhubung ke char server");
            return false;
        }
        byte[] nama = sd.status.name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int n = Math.min(nama.length, 16);
        s.wfifoW(0, 0x300A);
        s.wfifoW(2, sd.fd);
        s.wfifoL(4, board);
        s.wfifoL(8, pos);
        s.wfifoL(12, flags);
        s.wfifoB(16, n);
        for (int i = 0; i < 16; i++) {
            s.wfifoB(17 + i, i < n ? nama[i] : 0);
        }
        s.wfifoSet(org.rtk.charserver.Mapif.BOARD_POST_LEN);
        return true;
    }

    /**
     * boards_delete() (0x300B) — hapus satu kiriman (K2-lanjutan).
     *
     * <p>⚠️ Hak hapus diperiksa di CHAR SERVER, bukan di sini: map server
     * hanya tahu bendera yang ia kirim sendiri, dan bendera yang dikirim
     * klien tidak pernah boleh jadi dasar keputusan. Char server memeriksa
     * ulang bahwa penghapusnya memang penulisnya atau ber-hak-hapus.</p>
     */
    public static boolean deletePost(User sd, int board, int pos, int flags) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa menghapus pos: belum terhubung ke char server");
            return false;
        }
        byte[] nama = sd.status.name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int n = Math.min(nama.length, 16);
        s.wfifoW(0, 0x300B);
        s.wfifoW(2, sd.fd);
        s.wfifoL(4, board);
        s.wfifoL(8, pos);
        s.wfifoL(12, flags);
        s.wfifoB(16, n);
        for (int i = 0; i < 16; i++) {
            s.wfifoB(17 + i, i < n ? nama[i] : 0);
        }
        s.wfifoSet(org.rtk.charserver.Mapif.BOARD_POST_LEN);
        return true;
    }

    /**
     * boards_post() (0x300C) — tulis kiriman baru (K2-lanjutan).
     *
     * <p>Satu-satunya paket papan yang panjangnya <b>variabel</b>, karena
     * isinya memang teks bebas. Karena itu entri tabelnya −1, dan
     * panjangnya ditulis di [2..5] — <b>bukan</b> di [2..3] yang pada
     * paket papan lain berisi fd (Peringatan #103).</p>
     */
    public static boolean writePost(User sd, int board, String topik, String isi) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa menulis pos: belum terhubung ke char server");
            return false;
        }
        byte[] nm = sd.status.name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] tp = topik.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] is = isi.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int nl = Math.min(nm.length, 16);
        int tl = Math.min(tp.length, 250);
        int il = Math.min(is.length, 4000);
        int total = 12 + 16 + 2 + tl + 2 + il;
        s.wfifoW(0, 0x300C);
        s.wfifoL(2, total);
        s.wfifoW(6, sd.fd);
        s.wfifoL(8, board);
        for (int i = 0; i < 16; i++) {
            s.wfifoB(12 + i, i < nl ? nm[i] : 0);
        }
        int off = 28;
        s.wfifoW(off, tl);
        for (int i = 0; i < tl; i++) {
            s.wfifoB(off + 2 + i, tp[i]);
        }
        off += 2 + tl;
        s.wfifoW(off, il);
        for (int i = 0; i < il; i++) {
            s.wfifoB(off + 2 + i, is[i]);
        }
        s.wfifoSet(total);
        return true;
    }

    /** Jeda sebelum menutup sambungan yang sedang dialihkan (R3/C3). */
    private static final long TUTUP_SETELAH_ALIH_MS = 500;

    private static int tutupSesi(int fd) {
        Session cs = net.session(fd);
        if (cs != null) {
            cs.eof = true;
        }
        return 0;
    }

    /**
     * intif_parse_showpostresponse() (0x3809) — daftar isi papan datang;
     * susun jadi paket klien 0x31.
     *
     * <p>Balasan char server: [2..5] panjang total, [6..7] fd klien,
     * [8..11] id papan, [12..15] bendera1, [16..19] bendera2, [20..23]
     * jumlah kiriman, lalu tiap kiriman: [0] warna, [4] id kiriman,
     * [8] id gelar, [12] bulan, [16] hari, [20] panjang nama + nama,
     * lalu panjang judul + judul.</p>
     */
    static int parseBoardList(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(6);
        User sd = MapServer.onlineChars.get(clientFd);
        if (sd == null) {
            return 0;
        }
        int board = s.rfifoL(8);
        int flags1 = s.rfifoL(12);
        int flags2 = s.rfifoL(16);
        int jumlah = s.rfifoL(20);

        java.util.List<Clif.BoardEntry> isi = new java.util.ArrayList<>();
        int off = 24;
        for (int i = 0; i < jumlah; i++) {
            int warna = s.rfifoL(off);
            int postId = s.rfifoL(off + 4);
            int gelar = s.rfifoL(off + 8);
            int bulan = s.rfifoL(off + 12);
            int hari = s.rfifoL(off + 16);
            int nl = s.rfifoB(off + 20) & 0xFF;
            String penulis = s.rfifoString(off + 21, nl);
            off += 21 + nl;
            int tl = s.rfifoB(off) & 0xFF;
            String judul = s.rfifoString(off + 1, tl);
            off += 1 + tl;
            isi.add(new Clif.BoardEntry(warna, postId, gelar, bulan, hari, penulis, judul));
        }
        MapServer.clientView.boardListToPlayer(sd, board, flags1, flags2, isi);
        return 0;
    }

    /**
     * intif_parse_boardpost() (0x380A): isi SATU kiriman papan (K2-lanjutan).
     *
     * <p>Diteruskan apa adanya ke {@code ClientView}; keputusan apa yang
     * boleh dilakukan pemain (tulis / hapus) sudah dibawa char server dalam
     * ladang bendera, karena hanya dialah yang melihat barisnya.</p>
     */
    static int parseBoardPost(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(6);
        User sd = MapServer.onlineChars.get(clientFd);
        if (sd == null) {
            return 0;
        }
        int board = s.rfifoL(8);
        int pos = s.rfifoL(12);
        int bendera = s.rfifoL(16);
        int bulan = s.rfifoB(20) & 0xFF;
        int hari = s.rfifoB(21) & 0xFF;
        int off = 22;
        int nl = s.rfifoB(off) & 0xFF;
        String penulis = s.rfifoString(off + 1, nl);
        off += 1 + nl;
        int tl = s.rfifoB(off) & 0xFF;
        String topik = s.rfifoString(off + 1, tl);
        off += 1 + tl;
        int il = s.rfifoW(off);
        String isi = s.rfifoString(off + 2, il);
        MapServer.clientView.boardPostToPlayer(sd, board, pos, bendera,
                penulis, topik, bulan, hari, isi);
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
            case 0x3809: parseBoardList(fd); break;
            case 0x380A: parseBoardPost(fd); break;
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
