package org.rtk.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Crypt;
import org.rtk.common.mmo.CharStatus;
import org.rtk.common.mmo.Point;
import org.rtk.map.data.MapData;
import org.rtk.map.data.MapFile;
import org.rtk.map.data.MapRegistry;

/**
 * Uji tata letak byte paket klien ({@link Clif}).
 *
 * Paket-paket ini dibaca klien RetroTK asli, jadi satu byte meleset sudah
 * cukup membuat klien menampilkan sampah tanpa pesan error. Karena klien
 * nyata tidak tersedia di sini, yang diuji adalah:
 *
 * <ol>
 *   <li>paket dibangun dengan header, opcode, dan panjang yang benar;</li>
 *   <li>hasil enkripsi bisa <b>didekripsi balik</b> menjadi muatan yang
 *       sama persis — membuktikan jalur kunci (statis vs sesi) dipilih
 *       dengan benar;</li>
 *   <li>nilai di setiap offset cocok dengan tata letak `clif.c`.</li>
 * </ol>
 *
 * Jalankan: {@code ./run.sh cliftest}
 */
public final class ClifTest {

    private static final Logger log = LogManager.getLogger(ClifTest.class);
    private static int failures;

    private ClifTest() {
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            log.info("PASS: {}", what);
        } else {
            log.error("FAIL: {}", what);
            failures++;
        }
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException e) {
            log.error("gagal", e);
            System.exit(1);
        }
        if (failures == 0) {
            log.info("ALL CLIF TESTS PASSED");
        } else {
            log.error("{} TEST GAGAL", failures);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        String root = args.length > 0 ? args[0] : "maps";
        if (!Files.isRegularFile(Paths.get(root, "TK000000.map"))) {
            log.error("peta contoh tidak ditemukan di {}", root);
            System.exit(1);
        }

        // --- siapkan dunia + pemain, tanpa jaringan sungguhan ---
        // Clif membaca MapServer.world, jadi registry itu yang harus diisi
        MapServer.world.loadFromFiles(root, List.of("TK000000.map", "TK000001.map"));
        MapData map = MapServer.world.get(0);
        map.title = "Kugnae";
        map.light = 0; // 0 harus berubah jadi 232 di paket

        CharStatus st = new CharStatus();
        st.id = 0x01020304;
        st.name = "Tester";
        st.baseHp = 100;
        st.lastPos = new Point(0, 100, 100);
        User sd = new User(7, st);
        sd.m = 0;
        sd.x = 100;
        sd.y = 100;

        check("tabel kunci sesi terbentuk dari nama (1056 karakter)",
                sd.encHash != null && sd.encHash.length() == 1056);

        // --- pemilihan jalur kunci ---
        log.info("=== pemilihan kunci enkripsi ===");
        check("opcode 0x05 (sendId) pakai kunci sesi", Clif.usesSessionKey(0x05));
        check("opcode 0x15 (mapInfo) pakai kunci sesi", Clif.usesSessionKey(0x15));
        check("opcode 0x0A pakai kunci statis", !Clif.usesSessionKey(0x0A));
        check("opcode 0x02 pakai kunci statis", !Clif.usesSessionKey(0x02));

        // --- offset kamera (rumus dari clif_sendxy) ---
        log.info("=== offset kamera ===");
        check("dekat tepi kiri: offset = x", Clif.cameraOffsetX(map, 3) == 3);
        check("di tengah peta: offset = 8", Clif.cameraOffsetX(map, 100) == 8);
        check("dekat tepi kanan: offset digeser",
                Clif.cameraOffsetX(map, map.xs - 1) == map.xs - 1 - map.xs + 17);
        check("sumbu Y dekat atas: offset = y", Clif.cameraOffsetY(map, 2) == 2);
        check("sumbu Y di tengah: offset = 7", Clif.cameraOffsetY(map, 100) == 7);

        // --- tata letak byte tiap paket ---
        log.info("=== tata letak byte ===");
        byte[] p;

        p = build(sd, u -> Clif.sendId(u));
        check("sendId: header 0xAA", (p[0] & 0xFF) == 0xAA);
        // setPacketIndexes menambah 3 byte indeks kunci di ekor DAN menulis
        // ulang ladang panjang: 0x0E -> 0x11, total = 0x11 + 3 = 20 byte
        check("sendId: panjang jadi 0x11 setelah indeks kunci", be16(p, 1) == 0x0E + 3);
        check("sendId: opcode 0x05", (p[3] & 0xFF) == 0x05);
        check("sendId: total 20 byte (14 isi + 3 header + 3 kunci)", p.length == 20);
        byte[] d = decrypt(p, sd);
        check("sendId: id karakter utuh setelah dekripsi", be32(d, 5) == 0x01020304);
        check("sendId: byte tetap [12]=2 [13]=3", (d[12] & 0xFF) == 2 && (d[13] & 0xFF) == 3);

        p = build(sd, u -> Clif.sendXy(u));
        check("sendXy: opcode 0x04", (p[3] & 0xFF) == 0x04);
        check("sendXy: panjang jadi 0x10 setelah indeks kunci", be16(p, 1) == 0x0D + 3);
        d = decrypt(p, sd);
        check("sendXy: koordinat x", be16(d, 5) == 100);
        check("sendXy: koordinat y", be16(d, 7) == 100);
        check("sendXy: offset kamera x", be16(d, 9) == Clif.cameraOffsetX(map, 100));
        check("sendXy: offset kamera y", be16(d, 11) == Clif.cameraOffsetY(map, 100));

        p = build(sd, u -> Clif.sendMapInfo(u));
        check("mapInfo: opcode 0x15", (p[3] & 0xFF) == 0x15);
        check("mapInfo: panjang = 18 + judul + 3 kunci",
                be16(p, 1) == 18 + "Kugnae".length() + 3);
        d = decrypt(p, sd);
        check("mapInfo: id peta", be16(d, 5) == 0);
        check("mapInfo: lebar peta", be16(d, 7) == map.xs);
        check("mapInfo: tinggi peta", be16(d, 9) == map.ys);
        check("mapInfo: penanda cuaca 5 (flag mati)", (d[11] & 0xFF) == 5);
        check("mapInfo: panjang judul", (d[13] & 0xFF) == 6);
        check("mapInfo: judul terbaca", new String(d, 14, 6, java.nio.charset.StandardCharsets.ISO_8859_1)
                .equals("Kugnae"));
        check("mapInfo: cahaya 0 diganti 232", be16(d, 6 + 14) == 232);

        // flag cuaca menyala -> penanda berubah jadi 4
        sd.status.settingFlags = 32; // FLAG_WEATHER
        d = decrypt(build(sd, u -> Clif.sendMapInfo(u)), sd);
        check("mapInfo: flag cuaca menyala -> penanda 4", (d[11] & 0xFF) == 4);
        sd.status.settingFlags = 0;

        p = build(sd, u -> Clif.sendTime(u));
        check("sendTime: opcode 0x20", (p[3] & 0xFF) == 0x20);
        check("sendTime: total 10 byte (4 isi + 3 header + 3 kunci)", p.length == 10);

        p = build(sd, u -> Clif.sendAck(u));
        check("sendAck: opcode 0x1E", (p[3] & 0xFF) == 0x1E);
        check("sendAck: panjang jadi 9 setelah indeks kunci", be16(p, 1) == 6 + 3);

        // --- refresh trigger: sengaja TIDAK dienkripsi (meniru versi C) ---
        // catatan: C memakai WFIFOSET(5+3) tetap, bukan nilai kembalian
        // set_packet_indexes — perilaku itu ditiru apa adanya
        p = build(sd, u -> Clif.sendRefreshTrigger(u));
        check("refresh: opcode 0x22", (p[3] & 0xFF) == 0x22);
        check("refresh: total 8 byte (C mengirim 5+3 tetap)", p.length == 8);
        check("refresh: tidak dienkripsi — byte [4] tetap 0x03", (p[4] & 0xFF) == 0x03);

        // --- urutan lengkap saat masuk dunia ---
        log.info("=== urutan paket masuk dunia ===");
        org.rtk.common.Session s = fakeSession(sd);
        Clif.sendWorldEntry(sd);
        List<int[]> seq = splitPackets(drain(s));
        check("enam paket terkirim", seq.size() == 6);
        int[] want = {0x05, 0x15, 0x04, 0x20, 0x1E, 0x22};
        boolean order = seq.size() == want.length;
        for (int i = 0; order && i < want.length; i++) {
            if (seq.get(i)[0] != want[i]) {
                order = false;
            }
        }
        check("urutan opcode 05,15,04,20,1E,22", order);

        walkTest(map, sd);
    }

    /** Uji gerakan: tabrakan, desinkron, siaran, dan kamera. */
    private static void walkTest(MapData map, User sd) {
        log.info("=== gerakan (clif_parsewalk) ===");

        check("opcode masuk 0x06 pakai kunci sesi", Clif.usesSessionKeyIn(0x06));
        check("opcode masuk 0x04 pakai kunci statis", !Clif.usesSessionKeyIn(0x04));

        // cari petak terbuka dengan tetangga kanan yang juga terbuka
        int px = -1;
        int py = -1;
        for (int y = 1; y < map.ys - 1 && px < 0; y++) {
            for (int x = 1; x < map.xs - 2; x++) {
                if (map.walkable(x, y) && map.walkable(x + 1, y)) {
                    px = x;
                    py = y;
                    break;
                }
            }
        }
        MapServer.onlineChars.clear();
        map.delBlock(sd);
        sd.x = px;
        sd.y = py;
        sd.m = 0;
        sd.status.gmLevel = 0;
        map.addBlock(sd);
        MapServer.onlineChars.put(sd.fd, sd);

        // --- langkah sah ke kanan ---
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("langkah sah: posisi bertambah satu petak", sd.x == px + 1 && sd.y == py);
        check("indeks peta ikut pindah", map.objectsAt(px + 1, py).contains(sd));
        List<int[]> out = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("klien menerima konfirmasi 0x26",
                out.stream().anyMatch(o -> o[0] == 0x26));

        // --- desinkron: klien mengaku di tempat lain ---
        int beforeX = sd.x;
        feedWalk(sd, 1, px + 50, py + 50);
        Clif.parseWalk(sd);
        check("desinkron: pemain tidak berpindah", sd.x == beforeX);
        out = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("desinkron: klien ditarik kembali (0x51 + 0x04 + 0x51)",
                out.size() == 3 && out.get(0)[0] == 0x51 && out.get(1)[0] == 0x04
                        && out.get(2)[0] == 0x51);

        // --- menabrak tembok ---
        int wx = -1;
        int wy = -1;
        for (int y = 1; y < map.ys - 1 && wx < 0; y++) {
            for (int x = 1; x < map.xs - 2; x++) {
                if (map.walkable(x, y) && !map.walkable(x + 1, y)) {
                    wx = x;
                    wy = y;
                    break;
                }
            }
        }
        map.delBlock(sd);
        sd.x = wx;
        sd.y = wy;
        map.addBlock(sd);
        feedWalk(sd, 1, wx, wy);
        Clif.parseWalk(sd);
        check("tembok: pemain tetap di tempat", sd.x == wx && sd.y == wy);
        out = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("tembok: klien ditarik kembali", out.size() == 3 && out.get(1)[0] == 0x04);

        // --- GM menembus tembok ---
        sd.status.gmLevel = 99;
        feedWalk(sd, 1, wx, wy);
        Clif.parseWalk(sd);
        check("GM menembus tembok", sd.x == wx + 1);
        sd.status.gmLevel = 0;

        // --- pemain lain menghalangi ---
        map.delBlock(sd);
        sd.x = px;
        sd.y = py;
        map.addBlock(sd);

        CharStatus st2 = new CharStatus();
        st2.id = 999;
        st2.name = "Penghalang";
        st2.baseHp = 10;
        st2.lastPos = new Point(0, px + 1, py);
        User other = new User(8, st2);
        other.m = 0;
        other.x = px + 1;
        other.y = py;
        map.addBlock(other);
        MapServer.onlineChars.put(other.fd, other);
        MapServer.net.openTestSession(other.fd);

        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("pemain lain menghalangi langkah", sd.x == px);

        // --- siaran ke pemain lain di sekitar ---
        map.delBlock(other);
        other.x = px + 3;
        other.y = py;
        map.addBlock(other);
        MapServer.net.openTestSession(other.fd);
        drain(MapServer.net.session(sd.fd));

        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        List<int[]> seen = splitPackets(drain(MapServer.net.session(other.fd)));
        check("pemain sekitar menerima siaran 0x0C",
                seen.stream().anyMatch(o -> o[0] == 0x0C));
        List<int[]> mine = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("pengirim tidak menerima siarannya sendiri",
                mine.stream().noneMatch(o -> o[0] == 0x0C));

        // --- arah tidak sah tidak menggeser pemain ---
        map.delBlock(sd);
        sd.x = px;
        sd.y = py;
        map.addBlock(sd);
        int camX = sd.viewX;
        int camY = sd.viewY;
        feedWalk(sd, 7, px, py);
        Clif.parseWalk(sd);
        check("arah 7 (tidak sah) tidak menggeser pemain", sd.x == px && sd.y == py);
        check("arah tidak sah tidak menyentuh kamera",
                sd.viewX == camX && sd.viewY == camY);

        // --- kamera bergerak sesuai arah ---
        map.delBlock(sd);
        sd.x = px;
        sd.y = py;
        map.addBlock(sd);
        sd.viewX = 4;
        int camBefore = sd.viewX;
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("kamera geser ke kanan saat viewX < 8", sd.viewX == camBefore + 1);
        check("kamera x tetap dalam 0..16", sd.viewX >= 0 && sd.viewX <= 16);
        check("kamera y tetap dalam 0..14", sd.viewY >= 0 && sd.viewY <= 14);

        edgeAndVisibilityTest(map, sd);
        warpTest(map, sd);
        msgTest(sd);
    }

    /**
     * Kasus tepi peta dan benda yang tidak terlihat.
     *
     * Di tepi, koordinat tujuan dijepit sehingga sama dengan posisi
     * sekarang — pemain menemukan DIRINYA SENDIRI di petak itu. Kalau cek
     * "bukan diri sendiri" hilang, pemain akan tertahan di seluruh tepi peta.
     */
    private static void edgeAndVisibilityTest(MapData map, User sd) {
        log.info("=== tepi peta & benda tak terlihat ===");

        // cari petak terbuka di kolom 0 (tepi kiri)
        int ey = -1;
        for (int y = 0; y < map.ys; y++) {
            if (map.walkable(0, y)) {
                ey = y;
                break;
            }
        }
        check("ada petak terbuka di tepi kiri", ey >= 0);

        placeAt(map, sd, 0, ey);
        sd.status.gmLevel = 0;
        drain(MapServer.net.session(sd.fd));
        feedWalk(sd, 3, 0, ey); // ke kiri, keluar peta
        Clif.parseWalk(sd);
        check("tepi peta: pemain tetap di x=0", sd.x == 0 && sd.y == ey);
        List<int[]> out = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("tepi peta: dapat konfirmasi 0x26, bukan tarik-balik 0x51",
                out.stream().anyMatch(o -> o[0] == 0x26)
                        && out.stream().noneMatch(o -> o[0] == 0x51));

        // --- pemain tersembunyi tidak menghalangi ---
        int px = -1;
        int py = -1;
        for (int y = 1; y < map.ys - 1 && px < 0; y++) {
            for (int x = 1; x < map.xs - 2; x++) {
                if (map.walkable(x, y) && map.walkable(x + 1, y)) {
                    px = x;
                    py = y;
                    break;
                }
            }
        }
        CharStatus gs = new CharStatus();
        gs.id = 777;
        gs.name = "Hantu";
        gs.baseHp = 10;
        gs.lastPos = new Point(0, px + 1, py);
        User ghost = new User(9, gs);
        ghost.m = 0;
        ghost.x = px + 1;
        ghost.y = py;
        map.addBlock(ghost);
        MapServer.net.openTestSession(ghost.fd);

        gs.state = User.STATE_HIDDEN;
        placeAt(map, sd, px, py);
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("pemain tersembunyi tidak menghalangi", sd.x == px + 1);

        // --- GM ber-stealth tidak menghalangi ---
        gs.state = 0;
        gs.gmLevel = 5;
        ghost.optFlags = User.OPT_STEALTH;
        placeAt(map, sd, px, py);
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("GM ber-stealth tidak menghalangi", sd.x == px + 1);

        // --- hantu menghalangi hanya bila peta menampilkannya ---
        gs.gmLevel = 0;
        ghost.optFlags = 0;
        gs.state = User.STATE_GHOST;
        map.showGhosts = 1;
        placeAt(map, sd, px, py);
        sd.status.state = 0;
        sd.optFlags = 0;
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("hantu tak terlihat tidak menghalangi", sd.x == px + 1);

        placeAt(map, sd, px, py);
        sd.optFlags = User.OPT_GHOSTS; // pemain ini bisa melihat hantu
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("hantu menghalangi pemain yang bisa melihatnya", sd.x == px);

        // bersihkan agar tidak mengganggu uji berikutnya
        map.delBlock(ghost);
        map.showGhosts = 0;
        sd.optFlags = 0;
        sd.status.state = 0;
        placeAt(map, sd, px, py);
    }

    /** Uji petak portal: pemindahan, syarat masuk, dan pesan penolakan. */
    private static void warpTest(MapData map, User sd) {
        log.info("=== portal (Warps) ===");

        MapData dest = MapServer.world.get(1);
        check("peta tujuan termuat", dest != null);
        check("peta tanpa portal mengembalikan null", map.warpAt(3, 3) == null);

        // cari sepasang petak terbuka bersebelahan; yang kanan jadi portal
        int px = -1;
        int py = -1;
        for (int y = 1; y < map.ys - 1 && px < 0; y++) {
            for (int x = 1; x < map.xs - 2; x++) {
                if (map.walkable(x, y) && map.walkable(x + 1, y)) {
                    px = x;
                    py = y;
                    break;
                }
            }
        }
        int[] dtile = firstWalkable(dest);
        map.addWarp(px + 1, py, 1, dtile[0], dtile[1]);
        check("portal terdaftar", map.warpCount() == 1);
        check("portal ditemukan di petaknya", map.warpAt(px + 1, py) != null);
        check("portal tidak bocor ke petak tetangga", map.warpAt(px + 2, py) == null);
        check("portal di luar batas diabaikan diam-diam",
                warpCountAfterBadAdd(map) == 1);

        // --- syarat terpenuhi: pemain berpindah peta ---
        dest.reqLvl = 0;
        dest.lvlMax = 999;
        dest.vitaMax = Long.MAX_VALUE;
        dest.manaMax = Long.MAX_VALUE;
        dest.rejectMsg = "";
        placeAt(map, sd, px, py);
        sd.status.level = 50;
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("menginjak portal memindahkan pemain ke peta tujuan", sd.m == 1);
        check("pemain mendarat di koordinat tujuan",
                sd.x == dtile[0] && sd.y == dtile[1]);
        check("pemain terdaftar di indeks peta tujuan",
                dest.objectsAt(dtile[0], dtile[1]).contains(sd));
        check("pemain tidak lagi ada di peta asal",
                !map.objectsAt(px + 1, py).contains(sd));

        // --- level kurang jauh: ditolak + didorong mundur ---
        dest.delBlock(sd);
        sd.m = 0;
        placeAt(map, sd, px, py);
        dest.reqLvl = 99;
        sd.status.level = 1;
        sd.status.side = 0; // dorong ke y+2
        drain(MapServer.net.session(sd.fd));
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("syarat level kurang: pemain tetap di peta asal", sd.m == 0);
        List<int[]> out = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("penolakan mengirim pesan 0x0A",
                out.stream().anyMatch(o -> o[0] == 0x0A));

        // --- MapRejectMsg menimpa pesan bawaan ---
        dest.delBlock(sd);
        sd.m = 0;
        placeAt(map, sd, px, py);
        dest.rejectMsg = "Gerbang ini tertutup.";
        drain(MapServer.net.session(sd.fd));
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("MapRejectMsg dipakai bila diisi",
                textOfFirstMsg(sd).equals("Gerbang ini tertutup."));

        // --- GM menembus syarat masuk ---
        placeAt(map, sd, px, py);
        sd.m = 0;
        sd.status.gmLevel = 99;
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("GM menembus syarat masuk peta", sd.m == 1);
        sd.status.gmLevel = 0;

        dest.delBlock(sd);
        sd.m = 0;
        placeAt(map, sd, px, py);
    }

    /** Uji tata letak paket pesan 0x0A. */
    private static void msgTest(User sd) {
        log.info("=== pesan (clif_sendmsg 0x0A) ===");

        drain(MapServer.net.session(sd.fd));
        Clif.sendMsg(sd, 3, "Halo");
        byte[] raw = drain(MapServer.net.session(sd.fd));
        check("paket pesan terkirim", raw.length > 0);

        byte[] plain = decrypt(raw, sd);
        check("opcode pesan 0x0A", (plain[3] & 0xFF) == 0x0A);
        check("byte 4 = 0x03", (plain[4] & 0xFF) == 0x03);
        // ladang tipe ditulis little-endian di C (tanpa SWAP16)
        check("tipe little-endian: [5]=3, [6]=0",
                (plain[5] & 0xFF) == 3 && (plain[6] & 0xFF) == 0);
        check("panjang teks di [7]", (plain[7] & 0xFF) == 4);
        check("isi teks di [8..]", new String(plain, 8, 4,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Halo"));

        drain(MapServer.net.session(sd.fd));
        Clif.sendMsg(sd, 3, "");
        check("pesan kosong tidak mengirim apa pun",
                drain(MapServer.net.session(sd.fd)).length == 0);
    }

    private static int warpCountAfterBadAdd(MapData map) {
        map.addWarp(map.xs + 50, map.ys + 50, 1, 0, 0);
        return map.warpCount();
    }

    private static int[] firstWalkable(MapData m) {
        for (int y = 1; y < m.ys - 1; y++) {
            for (int x = 1; x < m.xs - 1; x++) {
                if (m.walkable(x, y)) {
                    return new int[]{x, y};
                }
            }
        }
        return new int[]{0, 0};
    }

    private static void placeAt(MapData map, User sd, int x, int y) {
        map.delBlock(sd);
        sd.m = 0;
        sd.x = x;
        sd.y = y;
        map.addBlock(sd);
    }

    /** Teks dari paket 0x0A pertama yang menunggu di sesi pemain. */
    private static String textOfFirstMsg(User sd) {
        byte[] raw = drain(MapServer.net.session(sd.fd));
        int off = 0;
        while (off < raw.length) {
            int payload = ((raw[off + 1] & 0xFF) << 8) | (raw[off + 2] & 0xFF);
            int total = payload + 3;
            byte[] one = java.util.Arrays.copyOfRange(raw, off, off + total);
            byte[] plain = decrypt(one, sd);
            if ((plain[3] & 0xFF) == 0x0A) {
                int len = plain[7] & 0xFF;
                return new String(plain, 8, len,
                        java.nio.charset.StandardCharsets.ISO_8859_1);
            }
            off += total;
        }
        return "";
    }

    /** Susun paket langkah 0x06 ke buffer baca sesi, seperti kiriman klien. */
    private static void feedWalk(User sd, int direction, int claimX, int claimY) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] pkt = new byte[15];
        pkt[0] = (byte) 0xAA;
        pkt[1] = 0x00;
        pkt[2] = 0x0C;
        pkt[3] = 0x06;
        pkt[5] = (byte) direction;
        pkt[8] = (byte) ((claimX >> 8) & 0xFF);
        pkt[9] = (byte) (claimX & 0xFF);
        pkt[10] = (byte) ((claimY >> 8) & 0xFF);
        pkt[11] = (byte) (claimY & 0xFF);
        s.feedInbound(pkt);
    }

    // ------------------------------------------------------------------
    // pembantu
    // ------------------------------------------------------------------

    private interface Packet {
        void send(User sd);
    }

    /** Bangun satu paket lalu ambil byte mentahnya dari buffer sesi. */
    private static byte[] build(User sd, Packet p) {
        org.rtk.common.Session s = fakeSession(sd);
        p.send(sd);
        return drain(s);
    }

    /** Sesi tanpa socket, cukup untuk menampung buffer tulis. */
    private static org.rtk.common.Session fakeSession(User sd) {
        return MapServer.net.openTestSession(sd.fd);
    }

    private static byte[] drain(org.rtk.common.Session s) {
        return s.takeOutbound();
    }

    /** Balikkan enkripsi memakai jalur kunci yang sama dengan pengirim. */
    private static byte[] decrypt(byte[] pkt, User sd) {
        byte[] copy = pkt.clone();
        int opcode = copy[3] & 0xFF;
        if (Clif.usesSessionKey(opcode)) {
            byte[] key = Crypt.generateKey2(copy, 0, sd.encHash, false);
            Crypt.crypt2(copy, 0, key);
        } else {
            Crypt.crypt(copy, 0);
        }
        return copy;
    }

    /** Pecah aliran byte menjadi paket-paket: {opcode, panjang total}. */
    private static List<int[]> splitPackets(byte[] data) {
        List<int[]> out = new java.util.ArrayList<>();
        int i = 0;
        while (i + 3 <= data.length) {
            if ((data[i] & 0xFF) != 0xAA) {
                break;
            }
            int len = be16(data, i + 1) + 3;
            if (len <= 0 || i + len > data.length) {
                break;
            }
            out.add(new int[]{data[i + 3] & 0xFF, len});
            i += len;
        }
        return out;
    }

    private static int be16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16)
                | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }
}
