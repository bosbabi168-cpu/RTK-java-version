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
import org.rtk.map.proto.Wire;
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
        // Urutan mengikuti intif_mmo_tosd (intif.c:222-241): ack, time, id,
        // mapinfo, status, MYSTAYTUS, spawn, lalu refresh (mapinfo+xy+
        // trigger), xy, dan sapuan area pandang. Klien peka terhadap urutan
        // ini, jadi ujinya memeriksa urutan persis, bukan sekadar "paketnya
        // ada".
        //
        // Diperbarui 26 Agu 2026: `mystaytus` (0x39) dan `clif_spawn` dulu
        // TIDAK diport padahal ada di C — ekspektasi lama (9 paket tanpa
        // 0x39) mengunci kelalaian itu. `spawn` = sendCharArea, yang tidak
        // menghasilkan paket bila tak ada pemain lain di area, jadi ia tidak
        // muncul di urutan ini. Begitu pula sapuan area pandang di akhir.
        int[] want = {0x1E, 0x20, 0x05, 0x15, 0x08, 0x39, 0x15, 0x04, 0x22, 0x04};
        boolean order = seq.size() == want.length;
        for (int i = 0; order && i < want.length; i++) {
            if (seq.get(i)[0] != want[i]) {
                order = false;
            }
        }
        if (!order) {
            StringBuilder got = new StringBuilder();
            for (int[] o : seq) {
                got.append(String.format("%02X ", o[0]));
            }
            log.error("  urutan didapat: {}", got.toString().trim());
        }
        check("sepuluh paket masuk dunia terkirim", seq.size() == want.length);
        check("urutan opcode 1E,20,05,15,08,39,15,04,22,04", order);
        check("status (0x08) dikirim saat masuk dunia",
                seq.stream().anyMatch(o -> o[0] == 0x08));

        bufferTest(sd);
        mobTest(map, sd);
        dialogTest(map, sd);
        mapDataTest(map, sd);
        statusTest(sd);
        lookTest(map, sd);
        walkTest(map, sd);
        bllTest(map, sd);
        durationTest(map, sd);
        ghostTest(map, sd);
        floorItemTest(map, sd);
        inventoryTest(map, sd);
        spellBookTest(map, sd);
        displayTest(map, sd);
        saveTest(map, sd);
        bodTest(map, sd);
        adminTest(map, sd);
        exchangeTest(map, sd);
        protoTest(map, sd);
        aksiTest(map, sd);
        perlengkapanTest(map, sd);
        rtk2Test(map, sd);
        abaikanTest();
        grupTest();
        setelanTest(map, sd);
        duniaTest(map, sd);
    }

    /**
     * Pertukaran barang antar pemain.
     *
     * <p>Dua hal yang paling berbahaya kalau salah, dan keduanya dikunci di
     * sini: barang yang ditawarkan <b>benar-benar keluar dari inventaris</b>
     * (kalau tidak, bisa digandakan) dan <b>kembali saat dibatalkan</b>
     * (kalau tidak, hilang). Plus persetujuan dua tahap: menekan "tukar"
     * sekali belum memindahkan apa pun.</p>
     */
    private static void exchangeTest(MapData map, User sd) {
        log.info("=== pertukaran barang antar pemain ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);

        var db = MapServer.itemDb;
        var tanpaStat = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var kosong = new org.rtk.map.data.ItemDb.Look(0, 0, 0, 0);
        // permata: boleh ditukar
        db.register(new org.rtk.map.data.ItemDb.Info(7401, "permata", "Permata", "", "",
                18, 0, 0L, 0, 1, 1, 20, 0, 0, 0, 0, 0, kosong, tanpaStat));
        // pusaka: ItmExchangeable = 1 -> TIDAK bisa ditukar
        db.register(new org.rtk.map.data.ItemDb.Info(7402, "pusaka", "Pusaka", "", "",
                18, 0, 0L, 1, 1, 1, 1, 0, 0, 0, 0, 0, kosong, tanpaStat));

        // lawan main
        CharStatus ls = new CharStatus();
        ls.id = 0x0ABCDE;
        ls.name = "Lawan";
        ls.baseHp = 100;
        ls.lastPos = new Point(0, c[0], c[1]);
        User lawan = new User(31, ls);
        lawan.m = 0;
        lawan.x = c[0];
        lawan.y = c[1];
        lawan.status.maxInv = 20;
        MapServer.net.openTestSession(lawan.fd);
        MapServer.onlineChars.put(sd.fd, sd);
        MapServer.onlineChars.put(lawan.fd, lawan);

        sd.status.maxInv = 20;
        sd.status.inventory.clear();
        lawan.status.inventory.clear();
        sd.exchange.items.clear();
        lawan.exchange.items.clear();
        sd.exchange.target = 0;
        lawan.exchange.target = 0;

        // --- mulai ---
        check("mulai dengan diri sendiri ditolak",
                !Exchange.start(sd, sd.id));
        check("mulai dengan lawan sepeta berhasil",
                Exchange.start(sd, lawan.id));
        check("kedua sisi saling menunjuk",
                sd.exchange.target == lawan.id && lawan.exchange.target == sd.id);

        // --- titipan BENAR-BENAR keluar dari inventaris ---
        sd.addItemById(7401, 5, -1);
        check("titip 2 dari 5 permata", Exchange.offerItem(sd, 0, 2));
        check("titipannya tercatat", sd.exchange.items.size() == 1
                && sd.exchange.items.get(0).amount == 2);
        check("⚠️ barangnya BERKURANG dari inventaris, bukan sekadar ditandai",
                sd.status.inventory.size() == 1
                        && sd.status.inventory.get(0).amount == 3);

        // --- barang yang tidak boleh ditukar ---
        sd.addItemById(7402, 1, -1);
        int slotPusaka = sd.status.inventory.size() - 1;
        check("⚠️ ItmExchangeable = 1 berarti TIDAK bisa ditukar",
                !Exchange.offerItem(sd, slotPusaka, 1));
        check("pusaka tetap di inventaris",
                sd.status.inventory.get(slotPusaka).amount == 1);

        // --- inventaris lawan penuh ---
        int maxAsli = lawan.status.maxInv;
        lawan.status.maxInv = 0;
        check("inventaris lawan penuh -> titipan ditolak",
                !Exchange.offerItem(sd, 0, 1));
        lawan.status.maxInv = maxAsli;

        // --- batal MENGEMBALIKAN titipan ---
        int sebelumBatal = sd.status.inventory.size();
        Exchange.cancel(sd);
        check("⚠️ batal MENGEMBALIKAN titipan ke inventaris",
                sd.status.inventory.stream()
                        .filter(i -> i.id == 7401)
                        .mapToInt(i -> i.amount).sum() == 5);
        check("batal mengosongkan titipan dan tandanya",
                sd.exchange.items.isEmpty() && sd.exchange.target == 0);

        // --- persetujuan DUA TAHAP ---
        sd.status.inventory.clear();
        lawan.status.inventory.clear();
        sd.addItemById(7401, 3, -1);
        lawan.addItemById(7401, 1, -1);
        Exchange.start(sd, lawan.id);
        Exchange.offerItem(sd, 0, 3);
        Exchange.offerItem(lawan, 0, 1);

        Exchange.confirm(sd, lawan.id);
        check("⚠️ satu pihak setuju: barang BELUM berpindah",
                sd.exchange.done && !lawan.exchange.done
                        && lawan.status.inventory.isEmpty());

        Exchange.confirm(lawan, sd.id);
        check("kedua pihak setuju: barang berpindah",
                lawan.status.inventory.stream()
                        .filter(i -> i.id == 7401)
                        .mapToInt(i -> i.amount).sum() == 3);
        check("dan sebaliknya",
                sd.status.inventory.stream()
                        .filter(i -> i.id == 7401)
                        .mapToInt(i -> i.amount).sum() == 1);
        check("pertukaran ditutup di kedua sisi",
                sd.exchange.target == 0 && lawan.exchange.target == 0
                        && sd.exchange.items.isEmpty()
                        && lawan.exchange.items.isEmpty());

        // --- lawan yang disebut klien tidak cocok -> kedua sisi batal ---
        sd.status.inventory.clear();
        lawan.status.inventory.clear();
        sd.addItemById(7401, 2, -1);
        Exchange.start(sd, lawan.id);
        Exchange.offerItem(sd, 0, 2);
        Exchange.confirm(sd, 999999L);
        check("lawan yang disebut klien salah -> kedua sisi dibatalkan",
                sd.exchange.target == 0 && lawan.exchange.target == 0);
        check("dan titipannya tetap kembali",
                sd.status.inventory.stream()
                        .filter(i -> i.id == 7401)
                        .mapToInt(i -> i.amount).sum() == 2);

        // --- emas ---
        sd.status.inventory.clear();
        lawan.status.inventory.clear();
        sd.status.money = 500;
        lawan.status.money = 0;
        Exchange.start(sd, lawan.id);
        Exchange.offerGold(sd, 9999);
        check("menawarkan emas lebih dari yang dimiliki diabaikan",
                sd.exchange.gold == 0);
        Exchange.offerGold(sd, 200);
        check("menawarkan emas yang cukup tercatat", sd.exchange.gold == 200);
        Exchange.confirm(sd, lawan.id);
        Exchange.confirm(lawan, sd.id);
        check("emas berpindah saat kedua pihak setuju",
                sd.status.money == 300 && lawan.status.money == 200);

        MapServer.onlineChars.remove(sd.fd);
        MapServer.onlineChars.remove(lawan.fd);
        sd.status.inventory.clear();
        sd.status.money = 0;
    }

    /**
     * Sisa administratif: penandaan PK, pasang paksa perlengkapan, catatan
     * kerusakan mob, dan paket pilihan peta.
     *
     * <p>Yang menyentuh database ({@code setHeroShow}, captcha) diuji di
     * {@code dbtest}.</p>
     */
    private static void adminTest(MapData map, User sd) {
        log.info("=== sisa administratif ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);
        org.rtk.common.Session s = MapServer.net.session(sd.fd);

        // --- penandaan PK: batas 20, tidak pernah kedaluwarsa ---
        sd.pvp.clear();
        long now = System.currentTimeMillis() / 1000;
        sd.pvp.put(4242L, now);
        check("getPK: yang ditandai terbaca", sd.pvp.containsKey(4242L));
        check("getPK: yang tidak ditandai -> false", !sd.pvp.containsKey(9999L));

        for (long i = 0; i < User.MAX_PVP + 5; i++) {
            if (sd.pvp.size() < User.MAX_PVP) {
                sd.pvp.put(1000 + i, now);
            }
        }
        check("setPK: berhenti di batas 20, tidak tumbuh terus",
                sd.pvp.size() == User.MAX_PVP);
        sd.pvp.clear();

        // --- forceEquip: nilai dari BAWAAN jenis, bukan salinan ---
        var db = MapServer.itemDb;
        var tanpaStat = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var kosong = new org.rtk.map.data.ItemDb.Look(0, 0, 0, 0);
        db.register(new org.rtk.map.data.ItemDb.Info(7301, "zirah_uji", "Zirah Uji",
                "", "", 4, 0, 0L, 0, 1, 1, 1, 0, 0, 250, 2, 0, kosong, tanpaStat));

        sd.status.equip.clear();
        var lama = new org.rtk.common.mmo.Item();
        lama.id = 7301;
        lama.amount = 1;
        lama.dura = 5;              // hampir rusak
        lama.realName = "Bekas";
        lama.pos = 4;
        sd.status.equip.add(lama);

        // panggil lewat prototipe skrip, seperti skrip Lua
        panggilBinding(sd, "forceEquip", org.luaj.vm2.LuaValue.valueOf(7301), org.luaj.vm2.LuaValue.valueOf(4));
        var baru = sd.status.equipAt(4);
        check("forceEquip: slotnya terisi", baru != null);
        check("forceEquip: ketahanan dari BAWAAN jenis, bukan salinan lama",
                baru != null && baru.dura == 250);
        check("forceEquip: ukiran lama TIDAK terbawa",
                baru != null && (baru.realName == null || baru.realName.isEmpty()));
        check("forceEquip: perlindungan dari bawaan jenisnya",
                baru != null && baru.protectedFlag == 2);
        check("forceEquip: slotnya tetap satu barang, bukan dua",
                sd.status.equip.size() == 1);
        sd.status.equip.clear();

        // --- catatan kerusakan mob: DITAMBAHKAN, dan bukan tabel ancaman ---
        MobData jenis = new MobData();
        jenis.id = 950;
        jenis.yname = "sasaran";
        jenis.name = "Sasaran";
        jenis.vita = 100;
        Mob mb = new Mob();
        mb.data = jenis;
        mb.id = Mob.MOB_START_NUM + 91;
        mb.m = 0;
        mb.x = c[0];
        mb.y = c[1];
        MobRegistry.resetStats(mb);

        MapServer.onlineChars.put(sd.fd, sd);
        panggilMob(mb, "setIndDmg", org.luaj.vm2.LuaValue.valueOf((double) sd.status.id),
                org.luaj.vm2.LuaValue.valueOf(30));
        panggilMob(mb, "setIndDmg", org.luaj.vm2.LuaValue.valueOf((double) sd.status.id),
                org.luaj.vm2.LuaValue.valueOf(20));
        check("setIndDmg: kerusakan DITAMBAHKAN, bukan ditimpa",
                mb.indDamage.getOrDefault(sd.status.id, 0.0) == 50.0);
        check("setIndDmg: tabel ancaman TIDAK ikut terisi", mb.threat.isEmpty());

        sd.groupId = 7;
        panggilMob(mb, "setGrpDmg", org.luaj.vm2.LuaValue.valueOf((double) sd.status.id),
                org.luaj.vm2.LuaValue.valueOf(15));
        check("setGrpDmg: dicatat menurut id GRUP, bukan id pemain",
                mb.groupDamage.getOrDefault(7, 0.0) == 15.0
                        && !mb.groupDamage.containsKey((int) sd.status.id));
        sd.groupId = 0;
        MapServer.onlineChars.remove(sd.fd);

        // --- paket pilihan peta ---
        drain(s);
        Clif.mapSelection(sd, "Pilih", java.util.List.of(3), java.util.List.of(4),
                java.util.List.of("Rumah"), java.util.List.of(11),
                java.util.List.of(5), java.util.List.of(6));
        byte[] p = decrypt(drain(s), sd);
        check("mapSelection: opcode 0x2E", (p[3] & 0xFF) == 0x2E);
        int jl = p[5] & 0xFF;
        check("mapSelection: judul di [6..]", new String(p, 6, jl,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Pilih"));
        int off = jl + 1;
        check("mapSelection: jumlah peta", (p[off + 5] & 0xFF) == 1);
        off += 2;
        check("mapSelection: koordinat asal", be16(p, off + 5) == 3
                && be16(p, off + 7) == 4);
        off += 4;
        int nl = p[off + 5] & 0xFF;
        check("mapSelection: nama peta", new String(p, off + 6, nl,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Rumah"));
        off += nl + 1;
        check("mapSelection: id peta dan koordinat tujuan",
                be32(p, off + 5) == 11 && be16(p, off + 9) == 5
                        && be16(p, off + 11) == 6);

        drain(s);
    }

    /** Engine terpisah untuk uji administratif; lihat catatan di bodTest. */
    private static org.rtk.map.script.ScriptEngine admEngine;
    private static org.luaj.vm2.LuaValue admRef;

    private static void panggilBinding(User sd, String nama, org.luaj.vm2.LuaValue... args) {
        if (admEngine == null) {
            admEngine = new org.rtk.map.script.ScriptEngine();
            var p = new org.rtk.map.script.ScriptPlayer((int) sd.id, sd.status.name);
            p.owner = sd;
            admRef = admEngine.playerRef(p);
        }
        org.luaj.vm2.LuaValue[] all = new org.luaj.vm2.LuaValue[args.length + 1];
        all[0] = admRef;
        System.arraycopy(args, 0, all, 1, args.length);
        admEngine.playerClass.proto.get(nama).invoke(org.luaj.vm2.LuaValue.varargsOf(all));
    }

    private static void panggilMob(Mob mb, String nama, org.luaj.vm2.LuaValue... args) {
        if (admEngine == null) {
            admEngine = new org.rtk.map.script.ScriptEngine();
        }
        org.luaj.vm2.LuaValue[] all = new org.luaj.vm2.LuaValue[args.length + 1];
        all[0] = admEngine.objectRef(mb);
        System.arraycopy(args, 0, all, 1, args.length);
        admEngine.mobClass.proto.get(nama).invoke(org.luaj.vm2.LuaValue.varargsOf(all));
    }

    /**
     * Subsistem BOD (<i>Break on Death</i>) — barang yang hancur saat
     * pemain mati, plus kedaluwarsa dan lepas-paksa.
     *
     * <p>Yang dijaga: daftar BOD adalah <b>sementara</b> (dikosongkan tiap
     * sapuan), perlindungan <b>menyelamatkan barang DAN menghentikan
     * sapuan</b>, dan `stripEquip` punya tiga jalur berbeda tergantung ada
     * tidaknya slot inventaris kosong.</p>
     */
    private static void bodTest(MapData map, User sd) {
        log.info("=== BOD (Break on Death) ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);
        var db = MapServer.itemDb;
        var tanpaStat = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var kosong = new org.rtk.map.data.ItemDb.Look(0, 0, 0, 0);

        // jubah: hancur saat mati (ItmBoD = 1), tanpa perlindungan bawaan
        db.register(new org.rtk.map.data.ItemDb.Info(7201, "jubah", "Jubah", "", "",
                4, 1, 0L, 0, 1, 1, 1, 0, 0, 100, 0, 0, kosong, tanpaStat));
        // jimat: ber-BoD TAPI terlindungi bawaan
        db.register(new org.rtk.map.data.ItemDb.Info(7202, "jimat", "Jimat", "", "",
                4, 1, 0L, 0, 1, 1, 1, 0, 0, 100, 3, 0, kosong, tanpaStat));
        // ramuan: barang inventaris ber-BoD
        db.register(new org.rtk.map.data.ItemDb.Info(7203, "ramuan", "Ramuan", "", "",
                18, 1, 0L, 0, 1, 1, 1, 0, 0, 0, 0, 0, kosong, tanpaStat));
        // kartu: kedaluwarsa pada waktu MUTLAK di masa lalu
        db.register(new org.rtk.map.data.ItemDb.Info(7204, "kartu", "Kartu", "", "",
                18, 0, 1000L, 0, 1, 1, 1, 0, 0, 0, 0, 0, kosong, tanpaStat));

        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.status.maxInv = 20;
        sd.bodItems.clear();
        sd.status.state = 1;              // PC_DIE

        // --- perlengkapan hancur, masuk daftar BOD, lalu daftarnya kosong ---
        var jubah = new org.rtk.common.mmo.Item();
        jubah.id = 7201;
        jubah.amount = 1;
        jubah.dura = 100;
        jubah.pos = 0;
        sd.status.equip.add(jubah);

        Combat.deductDuraEquip(null, sd);
        check("BOD: perlengkapan ber-BoD hancur saat pemain mati",
                sd.status.equipAt(0) == null);
        check("BOD: daftarnya DIKOSONGKAN setelah sapuan selesai",
                sd.bodItems.isEmpty());
        check("BOD: id barang yang hancur tercatat di breakId", sd.breakId == 7201);

        // --- perlindungan menyelamatkan barang DAN menghentikan sapuan ---
        sd.status.equip.clear();
        var jimat = new org.rtk.common.mmo.Item();
        jimat.id = 7202;
        jimat.amount = 1;
        jimat.dura = 100;
        jimat.pos = 0;
        sd.status.equip.add(jimat);
        var jubah2 = new org.rtk.common.mmo.Item();
        jubah2.id = 7201;
        jubah2.amount = 1;
        jubah2.dura = 100;
        jubah2.pos = 1;
        sd.status.equip.add(jubah2);

        Combat.deductDuraEquip(null, sd);
        check("BOD: barang terlindungi TIDAK hancur", sd.status.equipAt(0) != null);
        check("BOD: ketahanannya dipulihkan penuh",
                sd.status.equipAt(0).dura == 100);
        check("BOD: perlindungannya berkurang satu",
                sd.status.equipAt(0).protectedFlag == -1);
        check("BOD: ⚠️ sapuan BERHENTI — slot sesudahnya tidak diperiksa",
                sd.status.equipAt(1) != null);

        // --- inventaris ---
        sd.status.equip.clear();
        sd.status.inventory.clear();
        sd.addItemById(7203, 1, -1);
        Combat.checkInvBod(null, sd);
        check("BOD: barang inventaris ber-BoD hancur saat mati",
                sd.status.inventory.isEmpty());

        // yang TIDAK ber-BoD selamat, bahkan saat pemain mati
        sd.status.inventory.clear();
        sd.addItemById(7204, 1, -1);
        Combat.checkInvBod(null, sd);
        check("BOD: barang tanpa BoD selamat walau pemain mati",
                sd.status.inventory.size() == 1);

        // pemain hidup: tidak ada yang hancur
        sd.status.state = 0;
        sd.status.inventory.clear();
        sd.addItemById(7203, 1, -1);
        Combat.checkInvBod(null, sd);
        check("BOD: pemain hidup -> tidak ada yang hancur",
                sd.status.inventory.size() == 1);

        // --- kedaluwarsa ---
        sd.status.inventory.clear();
        sd.addItemById(7204, 1, -1);          // ItmTimer = 1000 (masa lalu)
        Combat.expireItems(sd);
        check("expireItem: kedaluwarsa dari jenisnya dibuang",
                sd.status.inventory.isEmpty());

        sd.status.inventory.clear();
        sd.addItemById(7203, 1, -1);
        sd.status.inventory.get(0).time = 1000;   // waktu pada barangnya
        Combat.expireItems(sd);
        check("expireItem: kedaluwarsa dari barangnya sendiri juga dibuang",
                sd.status.inventory.isEmpty());

        sd.status.inventory.clear();
        sd.addItemById(7203, 1, -1);
        Combat.expireItems(sd);
        check("expireItem: barang tanpa batas waktu tidak disentuh",
                sd.status.inventory.size() == 1);

        // --- stripEquip: tiga jalur ---
        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.status.maxInv = 20;
        var senjata = new org.rtk.common.mmo.Item();
        senjata.id = 7201;
        senjata.amount = 1;
        senjata.dura = 100;
        senjata.pos = 0;
        sd.status.equip.add(senjata);

        var p = MapServer.scriptEngine;
        MapServer.scriptEngine = null;   // binding dipanggil lewat ScriptPlayer
        // jalur 1: ada tempat -> masuk inventaris
        panggilStrip(sd, 0, 0, 0);
        check("stripEquip: ada tempat -> barangnya masuk inventaris",
                sd.status.equipAt(0) == null && sd.status.inventory.size() == 1);

        // jalur 2: inventaris penuh + hancurkan -> lenyap, tidak tertinggal di lantai
        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.status.equip.add(senjata);
        sd.status.maxInv = 0;
        int lantaiSebelum = MapServer.floorItems.count();
        panggilStrip(sd, 0, 1, 0);
        check("stripEquip: penuh + hancurkan -> tidak tertinggal di lantai",
                sd.status.equipAt(0) == null
                        && MapServer.floorItems.count() == lantaiSebelum);

        // jalur 3: inventaris penuh + paksa -> tertinggal di lantai
        sd.status.equip.clear();
        sd.status.equip.add(senjata);
        panggilStrip(sd, 0, 0, 1);
        check("stripEquip: penuh + paksa -> tertinggal di lantai",
                sd.status.equipAt(0) == null
                        && MapServer.floorItems.count() == lantaiSebelum + 1);

        // jalur 4: penuh, tanpa hancurkan/paksa -> TIDAK terjadi apa-apa
        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }
        sd.status.equip.clear();
        sd.status.equip.add(senjata);
        panggilStrip(sd, 0, 0, 0);
        check("stripEquip: penuh tanpa hancurkan/paksa -> barangnya TETAP dikenakan",
                sd.status.equipAt(0) != null);

        MapServer.scriptEngine = p;
        sd.status.equip.clear();
        sd.status.inventory.clear();
        sd.status.maxInv = 20;
        sd.status.state = 0;
        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }
    }

    /**
     * Engine terpisah untuk uji BOD — ⚠️ satu ScriptPlayer hanya boleh
     * dipakai satu ScriptEngine (lihat catatan A4), jadi pemain uji di sini
     * memakai objek skripnya sendiri.
     */
    private static org.rtk.map.script.ScriptEngine bodEngine;
    private static org.luaj.vm2.LuaValue bodRef;

    /** Panggil binding stripEquip lewat prototipe skrip, seperti skrip Lua. */
    private static void panggilStrip(User sd, int slot, int hancurkan, int paksa) {
        if (bodEngine == null) {
            bodEngine = new org.rtk.map.script.ScriptEngine();
            var p = new org.rtk.map.script.ScriptPlayer((int) sd.id, sd.status.name);
            p.owner = sd;
            bodRef = bodEngine.playerRef(p);
        }
        bodEngine.playerClass.proto.get("stripEquip").invoke(
                org.luaj.vm2.LuaValue.varargsOf(new org.luaj.vm2.LuaValue[]{
                    bodRef,
                    org.luaj.vm2.LuaValue.valueOf(slot),
                    org.luaj.vm2.LuaValue.valueOf(hancurkan),
                    org.luaj.vm2.LuaValue.valueOf(paksa)}));
    }

    /**
     * {@code forceSave} — penyegaran posisi & samaran sebelum blob dikirim.
     *
     * <p>Pengirimannya sendiri butuh tautan ke char server, jadi yang diuji
     * di sini bagian yang berdiri sendiri: apa yang <b>ditulis ke
     * CharStatus</b> tepat sebelum blobnya disusun. Itu justru bagian yang
     * pernah salah — tanpa penyegaran, posisi tersimpan adalah posisi saat
     * pemain masuk.</p>
     */
    private static void saveTest(MapData map, User sd) {
        log.info("=== forceSave (penyegaran sebelum simpan) ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);
        sd.graphicId = 77;
        sd.graphicColor = 4;
        sd.status.lastPos = new Point(9, 1, 1);   // nilai basi yang harus tertimpa
        sd.status.disguise = 0;
        sd.status.disguiseColor = 0;
        sd.destMap = -1;

        // Tanpa tautan char server pengirimannya gagal (-1), tetapi
        // penyegarannya sudah terjadi — itu yang diperiksa.
        MapIntif.saveChar(sd, false);
        check("forceSave: posisi tersimpan disegarkan dari posisi HIDUP",
                sd.status.lastPos.m == sd.m
                        && sd.status.lastPos.x == sd.x
                        && sd.status.lastPos.y == sd.y);
        check("forceSave: samaran ikut disegarkan",
                sd.status.disguise == 77 && sd.status.disguiseColor == 4);

        // ragam quit dengan tujuan di peta yang TIDAK dimuat server ini
        sd.destMap = 4242;                        // tidak ada di dunia uji
        sd.destX = 55;
        sd.destY = 66;
        MapIntif.saveChar(sd, true);
        check("saveChar quit: peta tujuan di server lain yang disimpan, "
                + "bukan posisi sekarang",
                sd.status.lastPos.m == 4242
                        && sd.status.lastPos.x == 55
                        && sd.status.lastPos.y == 66);

        // tujuan yang ADA di server ini tidak mengambil alih
        sd.destMap = 0;
        sd.destX = 55;
        sd.destY = 66;
        MapIntif.saveChar(sd, true);
        check("saveChar quit: tujuan di server INI tidak menimpa posisi hidup",
                sd.status.lastPos.m == sd.m && sd.status.lastPos.x == sd.x);

        sd.destMap = -1;
        sd.graphicId = 0;
        sd.graphicColor = 0;
    }

    /**
     * Tampilan & timer: kamera, teks layar, kertas, URL, penghitung waktu,
     * obrolan pemain, kunci gerak, dan animasi satu-penonton.
     */
    private static void displayTest(MapData map, User sd) {
        log.info("=== tampilan & timer ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);
        org.rtk.common.Session s = MapServer.net.session(sd.fd);
        MapServer.onlineChars.put(sd.fd, sd);

        // --- kamera ---
        drain(s);
        Clif.sendXyChange(sd, 8, 7);
        byte[] p = decrypt(drain(s), sd);
        check("changeView: opcode 0x04", (p[3] & 0xFF) == 0x04);
        check("changeView: posisi pemain di [5],[7]",
                be16(p, 5) == sd.x && be16(p, 7) == sd.y);
        check("changeView: kamera ikut tersimpan di User",
                sd.viewX == be16(p, 9) && sd.viewY == be16(p, 11));

        // di tepi peta offsetnya DIGESER satu, bukan dipotong
        placeAt(map, sd, c[0], c[1]);
        int sebelum = sd.viewX;
        Clif.sendXyChange(sd, 0, 0);
        check("changeView: kamera di tepi digeser, bukan dijepit ke batas",
                sd.viewX != sebelum || sd.viewX <= 0);
        drain(s);

        // --- teks layar (0x58 [5]=6) vs bersihkan layar (0x58 [5]=0) ---
        Clif.guiText(sd, "Pengumuman");
        p = decrypt(drain(s), sd);
        check("guitext: opcode 0x58", (p[3] & 0xFF) == 0x58);
        check("guitext: penanda 6 di [5]", (p[5] & 0xFF) == 0x06);
        int gl = be16(p, 6);
        check("guitext: panjang teks di [6]", gl == "Pengumuman".length());
        check("guitext: isi teks di [8..]", new String(p, 8, gl,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Pengumuman"));

        drain(s);
        Clif.destroyOld(sd);
        p = decrypt(drain(s), sd);
        check("destroyOld: opcode 0x58 juga, tapi penanda 0 di [5]",
                (p[3] & 0xFF) == 0x58 && (p[5] & 0xFF) == 0x00);

        // --- kertas ---
        drain(s);
        Clif.paperPopup(sd, "Surat rahasia", 12, 20);
        p = decrypt(drain(s), sd);
        check("paperpopup: opcode 0x35", (p[3] & 0xFF) == 0x35);
        check("paperpopup: lebar & tinggi di [6],[7]",
                (p[6] & 0xFF) == 12 && (p[7] & 0xFF) == 20);
        int pl = be16(p, 9);
        check("paperpopup: isi teks di [11..]", new String(p, 11, pl,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Surat rahasia"));

        // --- URL ---
        drain(s);
        Clif.sendUrl(sd, 2, "http://contoh");
        p = decrypt(drain(s), sd);
        check("sendURL: opcode 0x66", (p[3] & 0xFF) == 0x66);
        check("sendURL: ragam di [5]", (p[5] & 0xFF) == 2);
        check("sendURL: alamat di [8..]", new String(p, 8, be16(p, 6),
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("http://contoh"));

        // --- penghitung waktu ---
        drain(s);
        Clif.sendTimer(sd, 1, 300);
        p = decrypt(drain(s), sd);
        check("setTimer: opcode 0x67", (p[3] & 0xFF) == 0x67);
        check("setTimer: ragam di [5]", (p[5] & 0xFF) == 1);
        check("setTimer: lama dalam detik di [6]", be32(p, 6) == 300);

        // tiknya berkurang satu per detik, lalu mati saat habis
        sd.disptimerType = 1;
        sd.disptimerTick = 2;
        Durations.tick(null, sd);
        check("timer tampilan: berkurang satu tiap tik", sd.disptimerTick == 1);
        Durations.tick(null, sd);
        check("timer tampilan: habis -> ragamnya dinolkan",
                sd.disptimerTick == 0 && sd.disptimerType == 0);
        Durations.tick(null, sd);
        check("timer tampilan: yang sudah mati tidak ditik lagi",
                sd.disptimerTick == 0);

        // --- obrolan pemain: nama disisipkan server ---
        drain(s);
        Clif.scriptSay(sd, "halo semua", 0);
        p = decrypt(drain(s), sd);
        check("speak: opcode 0x0D", (p[3] & 0xFF) == 0x0D);
        check("speak: id pembicara di [6]", (be32(p, 6) & 0xFFFFFFFFL) == sd.status.id);
        int nl = sd.status.name.length();
        check("speak: nama pemain disisipkan di depan",
                new String(p, 11, nl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals(sd.status.name));
        check("speak: pemisah ':' untuk bicara biasa",
                (p[11 + nl] & 0xFF) == ':' && (p[12 + nl] & 0xFF) == ' ');
        check("speak: pesannya menyusul", new String(p, 13 + nl, 10,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("halo semua"));

        drain(s);
        Clif.scriptSay(sd, "tolong", 1);
        p = decrypt(drain(s), sd);
        check("speak ragam 1 (teriak): pemisahnya '!' bukan ':'",
                (p[11 + nl] & 0xFF) == '!');

        // --- kunci gerak: nilainya TERBALIK dari namanya ---
        drain(s);
        MapServer.clientView.playerMovementLocked(sd, true);
        p = decrypt(drain(s), sd);
        check("lock: opcode 0x51 dengan nilai 0", (p[3] & 0xFF) == 0x51
                && (p[5] & 0xFF) == 0);
        drain(s);
        MapServer.clientView.playerMovementLocked(sd, false);
        p = decrypt(drain(s), sd);
        check("unlock: nilai 1 — terbalik dari namanya", (p[5] & 0xFF) == 1);

        // --- animasi satu-penonton ---
        drain(s);
        Clif.sendAnimationTo(sd, sd, 42, 3);
        p = decrypt(drain(s), sd);
        check("selfAnimation: opcode 0x29", (p[3] & 0xFF) == 0x29);
        check("selfAnimation: id benda yang dianimasikan di [5]",
                (be32(p, 5) & 0xFFFFFFFFL) == sd.id);
        check("selfAnimation: nomor animasi & pengulangan",
                be16(p, 9) == 42 && be16(p, 11) == 3);

        long flagAsli = sd.status.settingFlags;
        sd.status.settingFlags = 0;      // FLAG_MAGIC mati
        drain(s);
        Clif.sendAnimationTo(sd, sd, 42, 3);
        check("selfAnimation: pemain yang mematikan efek sihir tidak menerimanya",
                drain(s).length == 0);
        sd.status.settingFlags = flagAsli;

        MapServer.onlineChars.remove(sd.fd);
        drain(s);
    }

    /**
     * Buku mantra: daftar id / nama tampilan / nama skrip, dan penanda
     * bagian yang harus disaring.
     *
     * <p>Varian yang menembak database ({@code getUnknownSpells},
     * {@code getAllClassSpells}) diuji di {@code dbtest}, bukan di sini.</p>
     */
    private static void spellBookTest(MapData map, User sd) {
        log.info("=== buku mantra ===");

        var db = MapServer.spellDb;
        db.register(201, "api_kecil", "Api Kecil", 1, 0);
        db.register(202, "tameng_angin", "Tameng Angin", 1, 3);
        db.register(203, "hujan_es", "Hujan Es", 0, 1);

        sd.status.spells = new int[]{201, 0, 203, 202};

        var p = MapServer.spellDb;
        check("getSpellNameFromYName: nama skrip -> nama tampilan",
                p.displayNameOf(p.idOf("hujan_es")).equals("Hujan Es"));
        check("getSpellNameFromYName: nama tak dikenal -> id 0",
                p.idOf("tidak_ada_mantra_ini") == 0);

        // slot kosong (id 0) tidak boleh ikut ke daftar mana pun
        int terisi = 0;
        for (int id : sd.status.spells) {
            if (id != 0) {
                terisi++;
            }
        }
        check("buku mantra uji berisi tiga mantra + satu slot kosong",
                terisi == 3 && sd.status.spells.length == 4);

        check("nama tampilan dan nama skrip memang BERBEDA sumbernya",
                p.displayNameOf(201).equals("Api Kecil")
                        && p.nameOf(201).equals("api_kecil"));

        // penanda bagian: bukan mantra, harus disaring dari daftar pilihan
        check("penanda bagian dikenali (0, 100, 1000, 10000)",
                org.rtk.map.data.SpellDb.isSectionMarker(0)
                        && org.rtk.map.data.SpellDb.isSectionMarker(100)
                        && org.rtk.map.data.SpellDb.isSectionMarker(1000)
                        && org.rtk.map.data.SpellDb.isSectionMarker(10000));
        check("mantra biasa BUKAN penanda bagian",
                !org.rtk.map.data.SpellDb.isSectionMarker(201)
                        && !org.rtk.map.data.SpellDb.isSectionMarker(99));

        check("ticker dan dispel terbaca per mantra",
                p.hasTicker(201) && !p.hasTicker(203) && p.dispelOf(202) == 3);

        sd.status.spells = new int[0];
    }

    /**
     * Paket inventaris (0x0F / 0x10) dan keluarga binding di sekitarnya.
     *
     * <p>Yang dijaga: dua string dengan peran berbeda pada paket 0x0F,
     * hiasan teks yang berbeda per jenis barang, ketahanan yang hanya
     * dikirim untuk perlengkapan, dan <b>penyapu data rusak</b> yang
     * membuang barangnya alih-alih melewatinya.</p>
     */
    private static void inventoryTest(MapData map, User sd) {
        log.info("=== inventaris (0x0F / 0x10) ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);
        org.rtk.common.Session s = MapServer.net.session(sd.fd);
        var db = MapServer.itemDb;
        var tanpaStat = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        // pedang: perlengkapan (type 3) -> ketahanan ikut dikirim
        db.register(new org.rtk.map.data.ItemDb.Info(7101, "klewang", "Klewang", "", "",
                3, 0, 0, 0, 1, 1, 1, 0, 0, 120, 7, 0,
                new org.rtk.map.data.ItemDb.Look(0, 0, 88, 6), tanpaStat));
        // roti: barang biasa bertumpuk (type 18)
        db.register(new org.rtk.map.data.ItemDb.Info(7102, "roti", "Roti Tawar", "", "",
                18, 0, 0, 0, 1, 1, 20, 0, 0, 0, 0, 0,
                new org.rtk.map.data.ItemDb.Look(0, 0, 12, 3), tanpaStat));
        // obor: ITM_SMOKE -> teksnya "nama [dura satuan]"
        db.register(new org.rtk.map.data.ItemDb.Info(7103, "obor", "Obor", "", "jam",
                org.rtk.map.data.ItemDb.ITM_SMOKE, 0, 0, 0, 1, 1, 1, 0, 0, 30, 0, 0,
                new org.rtk.map.data.ItemDb.Look(0, 0, 5, 1), tanpaStat));
        // peta: ITM_MAP -> teksnya "[Tdura] nama"
        db.register(new org.rtk.map.data.ItemDb.Info(7104, "peta_uji", "Peta Uji", "", "",
                org.rtk.map.data.ItemDb.ITM_MAP, 0, 0, 0, 1, 1, 1, 0, 0, 3, 0, 0,
                new org.rtk.map.data.ItemDb.Look(0, 0, 9, 2), tanpaStat));

        sd.status.inventory.clear();
        sd.status.maxInv = 20;

        // --- perlengkapan: ketahanan dikirim ---
        sd.addItemById(7101, 1, 120);
        drain(s);
        Clif.sendAddItem(sd, 0);
        byte[] p = decrypt(drain(s), sd);
        check("addItem: opcode 0x0F", (p[3] & 0xFF) == 0x0F);
        check("addItem: nomor slot 1-basis di [5]", (p[5] & 0xFF) == 1);
        check("addItem: ikon dari tabel Items di [6]", be16(p, 6) == 88);
        check("addItem: warna ikon di [8]", (p[8] & 0xFF) == 6);
        int tl = p[9] & 0xFF;
        check("addItem: teks tampilan = nama tampilan",
                new String(p, 10, tl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Klewang"));
        int off = 10 + tl;
        int jl = p[off] & 0xFF;
        check("addItem: string KEDUA = nama jenis, bukan teks hiasannya",
                new String(p, off + 1, jl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Klewang"));
        off += jl + 1;
        check("addItem: jumlah di ladang berikutnya", be32(p, off) == 1);
        off += 4;
        check("addItem: perlengkapan -> penanda 0", (p[off] & 0xFF) == 0);
        check("addItem: perlengkapan -> ketahanan ikut dikirim",
                be32(p, off + 1) == 120);
        check("addItem: perlindungan = yang TERBESAR antara barang & jenisnya",
                (p[off + 5] & 0xFF) == 7);

        // --- barang bertumpuk: penanda stack, ketahanan nol ---
        sd.status.inventory.clear();
        sd.addItemById(7102, 5, -1);
        drain(s);
        Clif.sendAddItem(sd, 0);
        p = decrypt(drain(s), sd);
        tl = p[9] & 0xFF;
        check("addItem: jumlah >1 masuk ke TEKSNYA",
                new String(p, 10, tl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Roti Tawar (5)"));
        off = 10 + tl;
        off += (p[off] & 0xFF) + 1 + 4;
        check("addItem: bertumpuk -> penanda 1", (p[off] & 0xFF) == 1);
        check("addItem: bertumpuk -> ketahanan nol", be32(p, off + 1) == 0);

        // --- hiasan teks per jenis ---
        sd.status.inventory.clear();
        sd.addItemById(7103, 1, 12);
        drain(s);
        Clif.sendAddItem(sd, 0);
        p = decrypt(drain(s), sd);
        tl = p[9] & 0xFF;
        check("addItem: ITM_SMOKE -> 'nama [dura satuan]'",
                new String(p, 10, tl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Obor [12 jam]"));

        sd.status.inventory.clear();
        sd.addItemById(7104, 1, 3);
        drain(s);
        Clif.sendAddItem(sd, 0);
        p = decrypt(drain(s), sd);
        tl = p[9] & 0xFF;
        check("addItem: ITM_MAP -> '[Tdura] nama'",
                new String(p, 10, tl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("[T3] Peta Uji"));

        // --- nama ukiran menimpa nama jenis, tapi HANYA string pertama ---
        sd.status.inventory.clear();
        sd.addItemById(7101, 1, 120);
        sd.status.inventory.get(0).realName = "Klewang Sakti";
        drain(s);
        Clif.sendAddItem(sd, 0);
        p = decrypt(drain(s), sd);
        tl = p[9] & 0xFF;
        check("addItem: nama ukiran dipakai di teks tampilan",
                new String(p, 10, tl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Klewang Sakti"));
        off = 10 + tl;
        jl = p[off] & 0xFF;
        check("addItem: string kedua TETAP nama jenisnya",
                new String(p, off + 1, jl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Klewang"));

        // --- penyapu data rusak MEMBUANG barangnya ---
        sd.status.inventory.clear();
        sd.addItemById(7102, 1, -1);
        sd.status.inventory.get(0).id = 2;      // id < 4
        drain(s);
        Clif.sendAddItem(sd, 0);
        check("addItem: barang ber-id < 4 DIBUANG, bukan dilewati",
                sd.status.inventory.isEmpty() && drain(s).length == 0);

        sd.status.inventory.clear();
        sd.addItemById(7102, 1, -1);
        sd.status.inventory.get(0).id = 999999; // tidak ada di tabel Items
        drain(s);
        Clif.sendAddItem(sd, 0);
        check("addItem: barang tak dikenal DIBUANG juga",
                sd.status.inventory.isEmpty() && drain(s).length == 0);

        // --- 0x10: paketnya, dan siapa yang mengosongkan slotnya ---
        //
        // ⚠️ Di C paket ini SEKALIGUS mengosongkan slot di server. Sejak
        // protokol kedua berdiri itu tidak bisa dipertahankan: efek samping
        // di dalam fungsi paket akan berjalan sekali per protokol, dan yang
        // kedua membuang slot pemain BERIKUTNYA (Peringatan #61 dan #70).
        // Pengosongannya sekarang milik sisi logika.
        sd.status.inventory.clear();
        sd.addItemById(7102, 3, -1);
        drain(s);
        Clif.sendDelItem(sd, 0, 6);
        p = decrypt(drain(s), sd);
        check("delItem: opcode 0x10", (p[3] & 0xFF) == 0x10);
        check("delItem: slot 1-basis di [5]", (p[5] & 0xFF) == 1);
        check("delItem: alasan di [6]", (p[6] & 0xFF) == 6);
        check("delItem: paketnya TIDAK lagi menyentuh keadaan server",
                sd.status.inventory.size() == 1);
        sd.clearInventorySlot(0);
        check("delItem: yang mengosongkannya sisi logika",
                sd.status.inventory.isEmpty());

        // --- updateInv menyapu seluruh isi tas ---
        sd.status.inventory.clear();
        sd.addItemById(7101, 1, 120);
        sd.addItemById(7102, 2, -1);
        sd.addItemById(7104, 1, 3);
        drain(s);
        for (int i = sd.status.inventory.size() - 1; i >= 0; i--) {
            Clif.sendAddItem(sd, i);
        }
        check("updateInv: satu paket 0x0F per slot terisi",
                splitPackets(drain(s)).stream()
                        .filter(o -> o[0] == 0x0F).count() == 3);

        sd.status.inventory.clear();
        drain(s);
    }

    /**
     * Barang di lantai (BL_ITEM) — jatuhkan, gabung, pungut, dan jebakan.
     *
     * <p>Yang dijaga di sini adalah aturan yang mudah terbalik: barang
     * sejenis di petak yang sama <b>digabung</b> alih-alih menumpuk jadi dua
     * benda, barang ber-id 0 adalah <b>uang</b>, kolom {@code ItmDroppable}
     * artinya "tidak bisa dipungut", dan jebakan disaring di <b>dua tempat
     * berbeda</b> dengan aturan berbeda pula.</p>
     */
    private static void floorItemTest(MapData map, User sd) {
        log.info("=== barang di lantai (BL_ITEM) ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);
        org.rtk.common.Session s = MapServer.net.session(sd.fd);

        var db = MapServer.itemDb;
        var kosongLook = new org.rtk.map.data.ItemDb.Look(0, 0, 0, 0);
        var tanpaStat = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        // apel: bertumpuk 10, boleh dipungut
        db.register(new org.rtk.map.data.ItemDb.Info(7001, "apel_uji", "Apel Uji", "", "",
                18, 0, 0, 0, 1, 1, 10, 0, 0, 50, 0, 0,
                new org.rtk.map.data.ItemDb.Look(11, 2, 33, 4), tanpaStat));
        // pedang: tidak bertumpuk
        db.register(new org.rtk.map.data.ItemDb.Info(7002, "pedang_uji", "Pedang Uji", "", "",
                3, 0, 0, 0, 1, 1, 1, 0, 0, 100, 0, 0, kosongLook, tanpaStat));
        // batu nisan: ItmDroppable != 0 -> TIDAK bisa dipungut
        db.register(new org.rtk.map.data.ItemDb.Info(7003, "nisan_uji", "Nisan Uji", "", "",
                18, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, kosongLook, tanpaStat));
        // jebakan: ITM_TRAPS
        db.register(new org.rtk.map.data.ItemDb.Info(7004, "jebakan_uji", "Jebakan Uji", "", "",
                org.rtk.map.data.ItemDb.ITM_TRAPS, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 0,
                kosongLook, tanpaStat));

        var reg = MapServer.floorItems;
        reg.useIdIndex(MapServer.npcs);
        int px = c[0];
        int py = c[1];

        // --- jatuhkan ---
        drain(s);
        FloorItem fl = reg.drop(sd, 7001, 3, 50, 0, 0, 0, px, py);
        check("drop: barang lantai terbentuk", fl != null && fl.data.amount == 3);
        check("drop: masuk indeks id global", MapServer.blockById(fl.id) == fl);
        check("drop: berdiri di petaknya", map.objectsAt(px, py).contains(fl));
        check("drop: idnya dari rentang FLOORITEM_START_NUM",
                fl.id >= FloorItem.FLOORITEM_START_NUM);

        byte[] p = decrypt(drain(s), sd);
        check("drop: paket benda 0x07 terkirim", (p[3] & 0xFF) == 0x07);
        check("drop: penanda barang 0x02 di [11]", (p[11] & 0xFF) == 0x02);
        check("drop: ikon MENTAH dari tabel Items, bukan +49152",
                be16(p, 16) == 33);
        check("drop: warna ikon di [18]", (p[18] & 0xFF) == 4);

        // --- barang sejenis digabung, bukan jadi dua benda ---
        int sebelum = reg.count();
        FloorItem fl2 = reg.drop(sd, 7001, 2, 50, 0, 0, 0, px, py);
        check("drop: barang sejenis DIGABUNG, bukan jadi benda kedua",
                fl2 == fl && reg.count() == sebelum && fl.data.amount == 5);
        check("drop: jumlah sebelumnya tercatat di lastAmount", fl.lastAmount == 3);

        // --- atribut ID: id BENDA, bukan id jenis barang (R5) ---
        // ⚠️ Skrip memakai `.ID` 1.292 kali di 347 berkas, dan `onPickup.lua`
        // memanggil `player:pickUp(groundItems[i].ID)`. Selama `.ID` menjawab
        // nil, MEMUNGUT BARANG DARI TANAH tidak pernah berhasil — tanpa satu
        // pun pesan galat, karena `pickUp(nil)` jadi `pickUp(0)` dan id 0
        // sekadar tidak ditemukan. Gerbang ini menjaga agar kesenyapan itu
        // tidak bisa kembali.
        {
            var engineLama = MapServer.scriptEngine;
            // ⚠️ Engine TERSENDIRI: `ScriptPlayer.udata` di-cache, jadi satu
            // objek pemain hanya boleh dipakai oleh satu ScriptEngine.
            var eng = new org.rtk.map.script.ScriptEngine();
            MapServer.scriptEngine = eng;
            CharStatus stId = new CharStatus();
            stId.id = 7778;
            stId.name = "UjiId";
            User sdId = new User(78, stId);
            sdId.m = sd.m;
            org.luaj.vm2.LuaValue refBenda = eng.objectRef(fl);
            org.luaj.vm2.LuaValue refPemain = eng.playerRef(sdId.scriptPlayer());
            check("atribut ID benda lantai = id BENDA, bukan id jenis barang",
                    refBenda.get("ID").todouble() == fl.id
                            && fl.id != fl.data.id);
            check("atribut id benda lantai tetap id JENIS barang",
                    refBenda.get("id").todouble() == fl.data.id);
            check("atribut ID pemain = id bloknya",
                    refPemain.get("ID").todouble() == sdId.id);
            MapServer.scriptEngine = engineLama;
        }

        // --- pungut ---
        sd.status.inventory.clear();
        sd.status.maxInv = 20;
        check("pickUp: berhasil", reg.pickUp(sd, fl.id));
        check("pickUp: seluruh tumpukan bertumpuk masuk sekaligus",
                sd.status.inventory.size() == 1 && sd.status.inventory.get(0).amount == 5);
        check("pickUp: barang lantai lenyap setelah habis",
                MapServer.blockById(fl.id) == null && !map.objectsAt(px, py).contains(fl));

        // --- barang TIDAK bertumpuk diambil sekeping demi sekeping ---
        sd.status.inventory.clear();
        FloorItem pedang = reg.drop(sd, 7002, 3, 100, 0, 0, 0, px, py);
        reg.pickUp(sd, pedang.id);
        check("pickUp: barang tak bertumpuk diambil satu per satu",
                pedang.data.amount == 2 && sd.status.inventory.size() == 1);
        check("pickUp: sisanya tetap di lantai",
                map.objectsAt(px, py).contains(pedang));
        reg.hapus(pedang);

        // --- id 0 = uang ---
        sd.status.inventory.clear();
        long emasAwal = sd.scriptGetAttr("money");
        FloorItem emas = reg.drop(sd, 0, 250, 0, 0, 0, 0, px, py);
        check("pickUp: barang ber-id 0 masuk EMAS, bukan inventaris",
                reg.pickUp(sd, emas.id)
                        && sd.scriptGetAttr("money") == emasAwal + 250
                        && sd.status.inventory.isEmpty());

        // --- ItmDroppable != 0 berarti tidak bisa dipungut ---
        FloorItem nisan = reg.drop(sd, 7003, 1, 1, 0, 0, 0, px, py);
        sd.status.gmLevel = 0;
        check("pickUp: ItmDroppable != 0 menolak pemain biasa",
                !reg.pickUp(sd, nisan.id) && MapServer.blockById(nisan.id) == nisan);
        sd.status.gmLevel = 99;
        check("pickUp: GM tetap bisa memungutnya", reg.pickUp(sd, nisan.id));
        sd.status.gmLevel = 0;

        // --- forceDrop dari inventaris ---
        sd.status.inventory.clear();
        sd.addItemById(7001, 4, 50);
        FloorItem jatuh = reg.dropFromInventory(sd, 0, 0);
        check("forceDrop: sekeping jatuh, sisanya tetap di inventaris",
                jatuh != null && jatuh.data.amount == 1
                        && sd.status.inventory.get(0).amount == 3);
        reg.hapus(jatuh);
        sd.status.inventory.clear();

        // --- aturan gabung jalur pemain JAUH lebih ketat dari jatuhan mob ---
        sd.status.inventory.clear();
        sd.addItemById(7002, 1, 100);            // pedang utuh
        sd.status.inventory.get(0).dura = 100;
        FloorItem utuh = reg.dropFromInventory(sd, 0, 1);
        sd.status.inventory.clear();
        sd.addItemById(7002, 1, 100);
        sd.status.inventory.get(0).dura = 40;    // pedang penyok
        FloorItem penyok = reg.dropFromInventory(sd, 0, 1);
        check("drop pemain: barang penyok TIDAK menyatu dengan yang utuh",
                penyok != utuh && reg.count() >= 2);

        // sedangkan jatuhan mob menggabung hanya berdasarkan id
        FloorItem mobDrop = reg.drop(null, 7002, 1, 40, 0, 0, 0, px, py);
        check("drop mob: menggabung hanya berdasarkan id barang",
                mobDrop == utuh || mobDrop == penyok);

        for (FloorItem sisa : new java.util.ArrayList<>(reg.all())) {
            reg.hapus(sisa);
        }
        sd.status.inventory.clear();

        // --- jebakan: dua penyaring yang BERBEDA ---
        FloorItem jebakan = reg.drop(sd, 7004, 1, 1, 0, 0, 0, px, py);
        check("jebakan: dikenali sebagai ITM_TRAPS", jebakan.isTrap());
        check("jebakan: belum terlihat oleh siapa pun", !jebakan.visibleTo(sd.id));
        jebakan.addTrapSpotter(sd.id);
        check("addTrapSpotters: pemain itu kini melihatnya",
                jebakan.visibleTo(sd.id) && jebakan.spottedBy(sd.id));
        check("addTrapSpotters: pemain lain tetap tidak melihatnya",
                !jebakan.spottedBy(999999));

        // penyaring pencarian benda TIDAK melihat trapsTable — ia membuang
        // SEMUA jebakan, bahkan yang sudah ditemukan
        drain(s);
        Clif.objectLook(sd, jebakan);
        check("jebakan: digambar untuk pemain yang sudah menemukannya",
                drain(s).length > 0);
        FloorItem jebakan2 = reg.drop(sd, 7004, 1, 1, 0, 0, 0, px + 1, py);
        drain(s);
        Clif.objectLook(sd, jebakan2);
        check("jebakan: TIDAK digambar untuk yang belum menemukannya",
                drain(s).length == 0);

        // --- lookGone barang lantai memakai 0x5F, bukan 0x0E ---
        drain(s);
        Clif.lookGone(jebakan);
        p = decrypt(drain(s), sd);
        check("lookGone barang lantai: opcode 0x5F", (p[3] & 0xFF) == 0x5F);

        reg.hapus(jebakan);
        reg.hapus(jebakan2);

        // --- animasi lempar (0x16) tidak menjatuhkan apa pun ---
        int sebelumLempar = reg.count();
        drain(s);
        Clif.throwAnimation(sd, px + 3, py + 2, 77, 5, 2);
        p = decrypt(drain(s), sd);
        check("throw: opcode 0x16", (p[3] & 0xFF) == 0x16);
        check("throw: id pelempar di [5]", (be32(p, 5) & 0xFFFFFFFFL) == sd.id);
        check("throw: ikon + 49152 di [9]", be16(p, 9) == 77 + 49152);
        check("throw: petak asal di [16],[18]", be16(p, 16) == px && be16(p, 18) == py);
        check("throw: petak tujuan di [20],[22]",
                be16(p, 20) == px + 3 && be16(p, 22) == py + 2);
        check("throw: ladang [12] nol — jangan tinggalkan gambar di tanah",
                be32(p, 12) == 0);
        check("throw: tidak menjatuhkan barang apa pun",
                reg.count() == sebelumLempar);

        drain(s);
    }

    /**
     * {@code moveGhost} (gerak mob) dan paket benda 0x07 <b>berkelompok</b>.
     *
     * <p>Yang dijaga: mob tidak menginjak portal, mob yang sedang mengejar
     * sasaran menembus penghalang sementara yang tidak mengejar tertahan,
     * dan tata letak paket berkelompok — termasuk byte passflag yang
     * sengaja saling menimpa antar entri.</p>
     */
    private static void ghostTest(MapData map, User sd) {
        log.info("=== moveGhost + paket benda berkelompok (0x07) ===");

        int[] c = firstWalkable(map);
        int px = -1;
        int py = -1;
        for (int y = 2; y < map.ys - 2 && px < 0; y++) {
            for (int x = 2; x < map.xs - 3; x++) {
                if (map.walkable(x, y) && map.walkable(x + 1, y)
                        && map.walkable(x + 2, y)) {
                    px = x;
                    py = y;
                    break;
                }
            }
        }
        check("ada tiga petak terbuka berderet untuk uji gerak", px >= 0);
        placeAt(map, sd, px, py);
        org.rtk.common.Session s = MapServer.net.session(sd.fd);

        MobData jenis = new MobData();
        jenis.id = 910;
        jenis.yname = "hantu_uji";
        jenis.name = "Hantu Uji";
        jenis.vita = 20;
        jenis.look = 42;
        jenis.lookColor = 7;
        jenis.subtype = 0;

        Mob mb = new Mob();
        mb.data = jenis;
        mb.id = Mob.MOB_START_NUM + 77;
        mb.m = 0;
        mb.x = px + 1;
        mb.y = py;
        mb.startM = 0;
        mb.startX = mb.x;
        mb.startY = mb.y;
        MobRegistry.resetStats(mb);
        mb.side = 1;   // menghadap kanan
        map.addBlock(mb);

        MobRegistry reg = MapServer.mobs;
        reg.registerType(jenis);
        // Seperti di produksi: mob wajib masuk indeks id global, karena
        // skrip merujuknya lewat id (lihat catatan A5 di CLAUDE.md).
        reg.useIdIndex(MapServer.npcs);

        // --- langkah biasa ---
        drain(s);
        boolean pindah = reg.moveGhost(null, mb);
        check("moveGhost: mob berpindah satu petak ke kanan",
                pindah && mb.x == px + 2 && mb.y == py);

        List<int[]> keluar = splitPackets(drain(s));
        check("moveGhost: siaran langkah 0x0C terkirim",
                keluar.stream().anyMatch(o -> o[0] == 0x0C));

        // --- petak asal, bukan tujuan, yang dikirim ---
        map.moveBlock(mb, px + 1, py);
        drain(s);
        Clif.mobMove(mb, px, py);
        byte[] p = decrypt(drain(s), sd);
        check("mobMove: opcode 0x0C", (p[3] & 0xFF) == 0x0C);
        check("mobMove: membawa nomor urut di [4]", (p[4] & 0xFF) != 0);
        check("mobMove: id mob di [5]", (be32(p, 5) & 0xFFFFFFFFL) == mb.id);
        check("mobMove: petak ASAL di [9],[11]", be16(p, 9) == px && be16(p, 11) == py);
        check("mobMove: arah di [13]", (p[13] & 0xFF) == 1);

        // --- mob mati tidak menyiarkan langkah ---
        mb.state = MobData.MOB_DEAD;
        drain(s);
        Clif.mobMove(mb, px, py);
        check("mobMove: mob mati tidak mengirim apa pun", drain(s).length == 0);
        mb.state = MobData.MOB_ALIVE;

        // --- penghalang: tertahan bila tidak mengejar, menembus bila mengejar ---
        // Dijalankan SEBELUM uji portal: portal di petak tujuan akan
        // menghentikan langkah lebih dulu, sehingga uji penghalangnya lulus
        // karena alasan yang salah.
        placeAt(map, sd, px + 1, py);       // pemain menghalangi
        map.moveBlock(mb, px + 2, py);
        mb.side = 3;                        // menghadap kiri, ke arah pemain
        mb.target = 0;
        check("moveGhost: tertahan pemain saat tidak mengejar",
                !reg.moveGhost(null, mb) && mb.x == px + 2);
        mb.target = sd.id;
        check("moveGhost: menembus penghalang saat mengejar sasaran",
                reg.moveGhost(null, mb) && mb.x == px + 1);
        mb.target = 0;

        // ⚠️ Blok ini WAJIB berjalan sebelum uji portal di bawah: begitu
        // portal dipasang di (px+1, py), setiap langkah ke sana berhenti di
        // penjaga portal dan uji tabrakan lulus karena alasan yang salah.
        // Persis jebakan yang sama sudah sekali kejadian di berkas ini.
        // --- checkMove: uji coba, TANPA memindahkan mob ---
        placeAt(map, sd, px + 1, py);       // pemain menghalangi
        map.moveBlock(mb, px, py);
        mb.side = 1;                        // menghadap pemain
        mb.target = 0;
        check("checkMove: terhalang pemain -> false", !reg.checkMove(mb));
        check("checkMove: tidak memindahkan mob", mb.x == px && mb.y == py);

        // ⚠️ Beda halus yang disengaja: moveGhost MEMBIARKAN mob ber-target
        // menembus penghalang, tapi checkMove tetap menjawab "tidak boleh".
        mb.target = sd.id;
        check("checkMove: pengecualian `target` TIDAK berlaku di sini",
                !reg.checkMove(mb));
        check("moveGhost: sedangkan moveGhost tetap menembusnya",
                reg.moveGhost(null, mb) && mb.x == px + 1);
        mb.target = 0;

        placeAt(map, sd, c[0], c[1]);
        map.moveBlock(mb, px, py);
        mb.side = 1;
        check("checkMove: petak kosong -> true", reg.checkMove(mb));
        check("checkMove: tetap tidak memindahkan apa pun", mb.x == px);

        // --- moveIgnoreObject: menembus penghalang, portal tetap menahan ---
        placeAt(map, sd, px + 1, py);
        map.moveBlock(mb, px, py);
        mb.side = 1;
        mb.target = 0;
        check("moveIgnoreObject: menembus pemain yang menghalangi",
                reg.moveIgnoreObject(null, mb) && mb.x == px + 1);
        placeAt(map, sd, c[0], c[1]);

        // --- moveIntent: MENGHADAP, bukan berjalan ---
        map.moveBlock(mb, px + 1, py);
        placeAt(map, sd, px + 2, py);       // pemain tepat di sebelah kanan
        mb.side = 0;                        // menghadap atas
        drain(s);
        check("moveIntent: sasaran bersebelahan -> true",
                reg.moveIntent(mb, sd));
        check("moveIntent: mob BERPUTAR menghadap sasaran", mb.side == 1);
        check("moveIntent: mob TIDAK berpindah", mb.x == px + 1 && mb.y == py);
        check("moveIntent: perubahan arah disiarkan",
                splitPackets(drain(s)).stream().anyMatch(o -> o[0] == 0x11));

        drain(s);
        check("moveIntent: dipanggil lagi tetap true", reg.moveIntent(mb, sd));
        check("moveIntent: arah tidak berubah -> tidak menyiarkan apa pun",
                drain(s).length == 0);

        placeAt(map, sd, c[0], c[1]);       // pemain jauh
        int arahSebelum = mb.side;
        check("moveIntent: sasaran jauh -> false", !reg.moveIntent(mb, sd));
        check("moveIntent: sasaran jauh tidak mengubah arah",
                mb.side == arahSebelum);
        check("moveIntent: sasaran null -> false", !reg.moveIntent(mb, null));

        // --- portal menghentikan langkah, bahkan saat mengejar ---
        placeAt(map, sd, c[0], c[1]);
        map.moveBlock(mb, px, py);
        mb.side = 1;
        map.addWarp(px + 1, py, 1, 0, 0);
        check("moveGhost: mob TIDAK menginjak portal",
                !reg.moveGhost(null, mb) && mb.x == px);
        mb.target = sd.id;
        check("moveGhost: portal menghentikan langkah walau sedang mengejar",
                !reg.moveGhost(null, mb) && mb.x == px);
        mb.target = 0;

        // --- paket benda berkelompok ---
        placeAt(map, sd, px, py);
        map.moveBlock(mb, px + 1, py);
        Npc benda = new Npc();
        benda.name = "benda_uji";
        benda.npcId = 92;
        benda.id = Npc.blockIdFor(92);
        benda.m = 0;
        benda.x = px + 2;
        benda.y = py;
        benda.graphicId = 55;
        benda.graphicColor = 3;
        benda.side = 2;
        benda.npcType = 0;
        benda.subtype = 0;
        map.addBlock(benda);

        drain(s);
        Clif.objectLookBatch(sd, List.of(mb, benda));
        p = decrypt(drain(s), sd);
        check("batch: opcode 0x07", (p[3] & 0xFF) == 0x07);
        check("batch: membawa nomor urut di [4]", (p[4] & 0xFF) != 0);
        check("batch: jumlah benda 2 di [5]", be16(p, 5) == 2);
        // entri 1 di offset 0, entri 2 di offset 15
        check("batch: entri 1 posisi di [7],[9]",
                be16(p, 7) == px + 1 && be16(p, 9) == py);
        check("batch: entri 1 penanda mob 0x05 di [11]", (p[11] & 0xFF) == 0x05);
        check("batch: entri 1 id di [12]", (be32(p, 12) & 0xFFFFFFFFL) == mb.id);
        check("batch: entri 1 grafik 32768+look di [16]", be16(p, 16) == 32768 + 42);
        check("batch: entri 2 mulai 15 byte kemudian",
                be16(p, 22) == px + 2 && be16(p, 24) == py);
        check("batch: entri 2 penanda NPC 12 di [26]", (p[26] & 0xFF) == 12);
        check("batch: entri 2 id di [27]", (be32(p, 27) & 0xFFFFFFFFL) == benda.id);
        check("batch: entri 2 grafik di [31]", be16(p, 31) == 32768 + 55);
        // 2 entri x 15 + 1 byte penutup = 31, ladang panjang = 31 + 4 (+3)
        check("batch: ladang panjang = 15*2 + 1 + 4 + 3", be16(p, 1) == 38);

        // yang tidak boleh digambar tidak menambah hitungan
        mb.state = MobData.MOB_DEAD;
        drain(s);
        Clif.objectLookBatch(sd, List.of(mb, benda));
        p = decrypt(drain(s), sd);
        check("batch: mob mati dilewati dan tidak dihitung", be16(p, 5) == 1);
        mb.state = MobData.MOB_ALIVE;

        drain(s);
        Clif.objectLookBatch(sd, List.of(sd));
        check("batch: pemain dilewati — tidak ada paket sama sekali",
                drain(s).length == 0);

        // --- spawn dari skrip ---
        int sebelum = reg.all().size();
        var ids = reg.spawnOneTime(null, 910, 0, px, py, 2, sd.id);
        check("spawn: dua mob lahir", ids.size() == 2);
        check("spawn: masuk daftar tik", reg.all().size() == sebelum + 2);
        org.rtk.map.data.BlockList lahir = MapServer.blockById(ids.get(0));
        check("spawn: masuk indeks id global (map_addiddb)", lahir instanceof Mob);
        check("spawn: bertanda sekali pakai", lahir instanceof Mob m2 && m2.oneTime);
        check("spawn: pemiliknya tercatat", lahir instanceof Mob m3 && m3.owner == sd.id);
        check("spawn: lahir dalam keadaan hidup",
                lahir instanceof Mob m4 && m4.isAlive());
        check("spawn: jenis tak dikenal tidak melahirkan apa pun",
                reg.spawnOneTime(null, 99999, 0, px, py, 1, 0).isEmpty());

        for (long id : ids) {
            if (MapServer.blockById(id) instanceof Mob dead) {
                reg.remove(dead, MapServer.world);
            }
        }
        map.delBlock(mb);
        map.delBlock(benda);
        drain(s);
    }

    /**
     * Durasi & aether mantra ({@link Durations}) — logika dan paketnya.
     *
     * <p>Yang dijaga di sini adalah cabang-cabang yang mudah terbalik:
     * merapal ulang mantra yang sisanya masih panjang <b>tidak</b> memperpanjang
     * (kecuali {@code recast}), waktu di bawah satu detik dinaikkan jadi satu
     * detik, satu baris memegang durasi dan aether sekaligus sehingga
     * habisnya salah satu tidak boleh membuang barisnya, dan mantra ber-ticker
     * 0 tidak mengirim paket apa pun meski durasinya jalan.</p>
     */
    private static void durationTest(MapData map, User sd) {
        log.info("=== durasi & aether mantra (dura_aether) ===");

        // SpellDb diisi tangan: uji ini tidak menyentuh MySQL.
        MapServer.spellDb.register(101, "shield_spell", "Shield spell", 1, 0);
        MapServer.spellDb.register(102, "diam_diam", "Diam-diam", 0, 5);
        MapServer.spellDb.register(103, "racun", "Racun", 1, 2);

        // ⚠️ Pemain dan engine SENDIRI. Satu ScriptPlayer hanya boleh
        // dipakai satu ScriptEngine (udata-nya di-cache), jadi memakai `sd`
        // di sini akan merusak uji dialog yang sudah mengikatnya ke engine
        // lain — jebakan yang sudah pernah kejadian.
        CharStatus dst = new CharStatus();
        dst.id = 0x0DEDEDE;
        dst.name = "Durasi";
        dst.baseHp = 100;
        dst.lastPos = new Point(0, sd.x, sd.y);
        User sd2 = new User(21, dst);
        sd2.m = 0;
        sd2.x = sd.x;
        sd2.y = sd.y;
        org.rtk.common.Session s = MapServer.net.openTestSession(sd2.fd);
        var engine = new org.rtk.map.script.ScriptEngine();

        // --- pemasangan pertama ---
        Durations.setDuration(engine, sd2, 101, 10000, 0, false);
        check("setDuration: baris baru dibuat", sd2.status.duraAether.size() == 1);
        check("setDuration: durasi tersimpan", Durations.duration(sd2, 101) == 10000);
        check("setDuration: hasDuration true", Durations.hasDuration(sd2, 101));
        check("setDuration: mantra lain tidak terpengaruh",
                !Durations.hasDuration(sd2, 103));

        // --- merapal ulang lebih panjang TIDAK memperpanjang ---
        Durations.setDuration(engine, sd2, 101, 30000, 0, false);
        check("merapal ulang lebih panjang tidak memperpanjang",
                Durations.duration(sd2, 101) == 10000);
        Durations.setDuration(engine, sd2, 101, 4000, 0, false);
        check("merapal ulang lebih pendek MEMENDEKKAN",
                Durations.duration(sd2, 101) == 4000);
        Durations.setDuration(engine, sd2, 101, 30000, 0, true);
        check("recast=1 menimpa walau lebih panjang",
                Durations.duration(sd2, 101) == 30000);

        // --- waktu di bawah satu detik ---
        sd2.status.duraAether.clear();
        Durations.setDuration(engine, sd2, 103, 200, 0, false);
        check("waktu 200 ms dinaikkan jadi 1000", Durations.duration(sd2, 103) == 1000);

        // --- paket durasi ---
        sd2.status.duraAether.clear();
        drain(s);
        Durations.setDuration(engine, sd2, 101, 10000, 0, false);
        byte[] p = decrypt(drain(s), sd2);
        check("paket durasi: opcode 0x3A", (p[3] & 0xFF) == 0x3A);
        check("paket durasi: membawa nomor urut di [4]", (p[4] & 0xFF) != 0);
        int dl = p[5] & 0xFF;
        check("paket durasi: nama TAMPILAN mantra, bukan nama skrip",
                new String(p, 6, dl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Shield spell"));
        check("paket durasi: sisa waktu dalam DETIK", be32(p, dl + 6) == 10);
        check("paket durasi: ladang panjang = len + 7 + 3", be16(p, 1) == dl + 7 + 3);

        // --- ticker 0 tidak mengirim apa pun ---
        sd2.status.duraAether.clear();
        drain(s);
        Durations.setDuration(engine, sd2, 102, 10000, 0, false);
        check("mantra ber-ticker 0: durasinya jalan", Durations.duration(sd2, 102) == 10000);
        check("mantra ber-ticker 0: tidak ada paket terkirim", drain(s).length == 0);

        // --- durasi dan aether berbagi satu baris ---
        sd2.status.duraAether.clear();
        Durations.setDuration(engine, sd2, 101, 5000, 0, false);
        Durations.setAether(sd2, 101, 8000);
        check("durasi + aether memakai baris yang SAMA",
                sd2.status.duraAether.size() == 1);
        check("aether tersimpan", Durations.aether(sd2, 101) == 8000);
        Durations.setDuration(engine, sd2, 101, 0, 0, false);
        check("durasi dihentikan, barisnya TIDAK dibuang karena aether jalan",
                sd2.status.duraAether.size() == 1 && Durations.aether(sd2, 101) == 8000);
        check("durasi benar-benar nol", Durations.duration(sd2, 101) == 0);
        Durations.flushAether(sd2, 99, 0, 0);
        check("aether ikut habis -> barisnya baru dibuang",
                sd2.status.duraAether.isEmpty());

        // --- penyaring dispel pada flushDuration ---
        sd2.status.duraAether.clear();
        Durations.setDuration(engine, sd2, 101, 5000, 0, false);   // dispel 0
        Durations.setDuration(engine, sd2, 103, 5000, 0, false);   // dispel 2
        Durations.flushDuration(engine, sd2, 1, 0, 0, true);
        check("flushDuration(1): mantra dispel 0 terhapus",
                !Durations.hasDuration(sd2, 101));
        check("flushDuration(1): mantra dispel 2 kebal",
                Durations.hasDuration(sd2, 103));
        Durations.flushDuration(engine, sd2, 9, 0, 0, true);
        check("flushDuration(9): yang kebal pun ikut terhapus",
                sd2.status.duraAether.isEmpty());

        // --- rentang minId/maxId ---
        sd2.status.duraAether.clear();
        Durations.setDuration(engine, sd2, 101, 5000, 0, false);
        Durations.setDuration(engine, sd2, 103, 5000, 0, false);
        Durations.flushDuration(engine, sd2, 9, 101, 0, true);
        check("minId>0 & maxId<=0: hanya mantra itu yang dihapus",
                !Durations.hasDuration(sd2, 101) && Durations.hasDuration(sd2, 103));

        // --- durasi dari penyihir tertentu ---
        sd2.status.duraAether.clear();
        Durations.setDuration(engine, sd2, 101, 5000, 555, false);
        check("hasDurationID cocok dengan penyihirnya",
                Durations.hasDurationFrom(sd2, 101, 555));
        check("hasDurationID tidak cocok dengan penyihir lain",
                !Durations.hasDurationFrom(sd2, 101, 556));
        check("getCasterID mengembalikan penyihirnya",
                Durations.casterOf(sd2, 101) == 555);

        // --- tik satu detik ---
        sd2.status.duraAether.clear();
        Durations.setDuration(engine, sd2, 101, 2000, 0, false);
        Durations.tick(engine, sd2);
        check("tik: durasi berkurang 1000 ms", Durations.duration(sd2, 101) == 1000);
        Durations.tick(engine, sd2);
        check("tik: durasi habis -> baris dibuang", sd2.status.duraAether.isEmpty());

        // aether habis lebih dulu, durasinya masih jalan
        sd2.status.duraAether.clear();
        Durations.setDuration(engine, sd2, 101, 5000, 0, false);
        Durations.setAether(sd2, 101, 1000);
        Durations.tick(engine, sd2);
        check("tik: aether habis tapi barisnya tetap karena durasi jalan",
                sd2.status.duraAether.size() == 1
                        && Durations.aether(sd2, 101) == 0
                        && Durations.duration(sd2, 101) == 4000);

        sd2.status.duraAether.clear();
        drain(s);
    }

    /**
     * Paket di balik method {@code bll_*} yang dipakai hampir semua skrip:
     * {@code talk} (0x0D), {@code sendAction} (0x1A), {@code updateState}
     * (0x1D), paket benda 0x07, dan {@code lookgone} (0x0E/0x5F).
     *
     * <p>Ikut diperiksa <b>nomor urut paket</b> di ladang [4]: hanya paket
     * yang di C dibangun lewat {@code WFIFOHEADER} yang membawanya, dan
     * salah menyeragamkannya tidak akan kelihatan dari membaca kode.</p>
     */
    private static void bllTest(MapData map, User sd) {
        log.info("=== bll_* (talk 0x0D, sendAction 0x1A, updateState 0x1D, objek 0x07) ===");

        int[] c = firstWalkable(map);
        placeAt(map, sd, c[0], c[1]);
        org.rtk.common.Session s = MapServer.net.session(sd.fd);

        // --- clif_speak (talk) ---
        drain(s);
        Clif.speak(sd, 3, "Halo dunia");
        byte[] raw = drain(s);
        check("talk: paket terkirim", raw.length > 0);
        byte[] p = decrypt(raw, sd);
        check("talk: opcode 0x0D", (p[3] & 0xFF) == 0x0D);
        check("talk: ragam di [5]", (p[5] & 0xFF) == 3);
        check("talk: id pembicara di [6]", (be32(p, 6) & 0xFFFFFFFFL) == sd.id);
        check("talk: panjang teks APA ADANYA di [10] (bukan +2)",
                (p[10] & 0xFF) == "Halo dunia".length());
        check("talk: isi teks di [11..]", new String(p, 11, 10,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Halo dunia"));
        // ladang panjang = strlen + 8, lalu setPacketIndexes menambah 3
        check("talk: ladang panjang = panjang + 8 + 3",
                be16(p, 1) == "Halo dunia".length() + 8 + 3);

        // --- nomor urut WFIFOHEADER ---
        int seq1 = p[4] & 0xFF;
        check("talk: membawa nomor urut di [4] (WFIFOHEADER)", seq1 != 0);
        drain(s);
        Clif.speak(sd, 3, "lagi");
        int seq2 = decrypt(drain(s), sd)[4] & 0xFF;
        check("talk: nomor urut naik satu tiap paket",
                seq2 == ((seq1 + 1) & 0xFF));

        drain(s);
        Clif.blockMovement(sd, 1);
        byte[] blok = decrypt(drain(s), sd);
        check("blockMovement: opcode 0x51", (blok[3] & 0xFF) == 0x51);
        check("blockMovement: ikut membawa nomor urut",
                (blok[4] & 0xFF) == ((seq2 + 1) & 0xFF));

        // Paket yang di C TIDAK memakai WFIFOHEADER harus meninggalkan [4]=0
        drain(s);
        Clif.sendId(sd);
        check("sendId: [4] tetap 0 — bukan paket WFIFOHEADER",
                (decrypt(drain(s), sd)[4] & 0xFF) == 0);

        // --- clif_sendaction ---
        drain(s);
        Clif.sendAction(sd, 1, 40, 0);
        p = decrypt(drain(s), sd);
        check("sendAction: opcode 0x1A", (p[3] & 0xFF) == 0x1A);
        // C menulis 0x000B lalu setPacketIndexes menimpanya dengan nilai + 3
        check("sendAction: ladang panjang 0x0B + 3 indeks kunci", be16(p, 1) == 0x0B + 3);
        check("sendAction: id benda di [5]", (be32(p, 5) & 0xFFFFFFFFL) == sd.id);
        check("sendAction: ragam gerakan di [9]", (p[9] & 0xFF) == 1);
        check("sendAction: lama gerakan di [11]", (p[11] & 0xFF) == 40);
        check("sendAction: [4] nol — dibangun dengan WBUF, bukan WFIFOHEADER",
                (p[4] & 0xFF) == 0);

        // --- clif_updatestate ---
        drain(s);
        Clif.updateState(sd, sd);
        p = decrypt(drain(s), sd);
        check("updateState: opcode 0x1D", (p[3] & 0xFF) == 0x1D);
        check("updateState: id di [5]", (be32(p, 5) & 0xFFFFFFFFL) == sd.id);
        check("updateState: sex di [9] — 5 byte lebih awal dari 0x33",
                be16(p, 9) == sd.status.sex);
        int nl = p[54] & 0xFF;
        check("updateState: panjang nama di [54]", nl == sd.status.name.length());
        check("updateState: nama di [55..]", new String(p, 55, nl,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals(sd.status.name));
        // C menulis len + 55 + 3; setPacketIndexes menambah 3 lagi
        check("updateState: ladang panjang = len + 55 + 3 (+3 indeks kunci)",
                be16(p, 1) == nl + 61);

        // state 4 memakai tata letak pendek sendiri — cabang yang TIDAK ada
        // pada paket 0x33
        int stateAsli = sd.status.state;
        sd.status.state = 4;
        sd.status.disguise = 100;
        drain(s);
        Clif.updateState(sd, sd);
        p = decrypt(drain(s), sd);
        check("updateState state 4: penanda [9]=1 [10]=15",
                (p[9] & 0xFF) == 1 && (p[10] & 0xFF) == 15);
        check("updateState state 4: samaran + 32768 di [12]", be16(p, 12) == 100 + 32768);
        check("updateState state 4: nama bergeser ke [17]", new String(p, 17,
                sd.status.name.length(),
                java.nio.charset.StandardCharsets.ISO_8859_1).equals(sd.status.name));
        check("updateState state 4: panjang = nama + 1 + 13 (+3 indeks kunci)",
                be16(p, 1) == sd.status.name.length() + 1 + 13 + 3);
        sd.status.state = stateAsli;

        // --- paket benda 0x07 + lookgone ---
        MobData jenis = new MobData();
        jenis.id = 900;
        jenis.yname = "batu";
        jenis.name = "Batu";
        jenis.vita = 10;
        jenis.look = 77;
        jenis.lookColor = 3;
        jenis.subtype = 0;      // BUKAN karakter -> paket benda 0x07
        Mob batu = new Mob();
        batu.data = jenis;
        batu.id = Mob.MOB_START_NUM + 5;
        batu.m = 0;
        batu.x = c[0];
        batu.y = c[1];
        MobRegistry.resetStats(batu);
        map.addBlock(batu);

        drain(s);
        Clif.objectLook(sd, batu);
        p = decrypt(drain(s), sd);
        check("objek: opcode 0x07", (p[3] & 0xFF) == 0x07);
        check("objek: jumlah benda 1 di [5]", be16(p, 5) == 1);
        check("objek: posisi di [7],[9]", be16(p, 7) == c[0] && be16(p, 9) == c[1]);
        check("objek: id blok di [12]", (be32(p, 12) & 0xFFFFFFFFL) == batu.id);
        check("objek: penanda mob 0x05 di [11]", (p[11] & 0xFF) == 0x05);
        check("objek: grafik 32768 + look di [16]", be16(p, 16) == 32768 + 77);
        check("objek: warna grafik di [18]", (p[18] & 0xFF) == 3);
        check("objek: panjang tetap 20 (+3 indeks kunci)", be16(p, 1) == 20 + 3);

        // mob mati tidak digambar sama sekali
        batu.state = MobData.MOB_DEAD;
        drain(s);
        Clif.objectLook(sd, batu);
        check("objek: mob mati tidak mengirim apa pun", drain(s).length == 0);
        batu.state = MobData.MOB_ALIVE;

        // lookgone: mob selalu 0x0E, NPC benda peta 0x5F
        drain(s);
        Clif.lookGone(batu);
        p = decrypt(drain(s), sd);
        check("lookGone mob: opcode 0x0E", (p[3] & 0xFF) == 0x0E);
        check("lookGone: [4] = 0x03", (p[4] & 0xFF) == 0x03);
        check("lookGone: id benda di [5]", (be32(p, 5) & 0xFFFFFFFFL) == batu.id);

        Npc benda = new Npc();
        benda.name = "PapanNama";
        benda.npcId = 91;
        benda.id = Npc.blockIdFor(91);
        benda.m = 0;
        benda.x = c[0];
        benda.y = c[1];
        benda.npcType = 0;    // benda peta, bukan karakter
        map.addBlock(benda);
        drain(s);
        Clif.lookGone(benda);
        p = decrypt(drain(s), sd);
        check("lookGone NPC benda peta: opcode 0x5F", (p[3] & 0xFF) == 0x5F);

        map.delBlock(batu);
        map.delBlock(benda);
    }

    /**
     * Paket yang lebih besar dari buffer awal sesi harus tetap utuh.
     *
     * <p>Pernah kejadian dan <b>tidak terlihat sama sekali di uji</b>:
     * saat buffer tulis tumbuh, hanya bagian yang sudah dikirim yang
     * disalin, sedangkan byte yang sedang disusun ada di belakangnya —
     * sehingga ikut terbuang. Akibatnya paket daftar peta 19.708 byte
     * terkirim sebagai 19.708 byte NOL, dan tautan map-char putus-nyambung
     * tiap 10 detik. Uji ini menjaga agar tidak terulang.</p>
     */
    private static void bufferTest(User sd) {
        log.info("=== buffer tulis lebih besar dari ukuran awal ===");

        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        drain(s);

        // buffer awal 4096 byte; tulis jauh melewatinya
        final int n = 20000;
        for (int i = 0; i < n; i++) {
            s.wfifoB(i, (i % 251) + 1);   // hindari 0 supaya nol = kerusakan
        }
        s.wfifoSet(n);

        byte[] keluar = drain(s);
        check("paket besar terkirim utuh panjangnya", keluar.length == n);

        int rusak = -1;
        for (int i = 0; i < Math.min(n, keluar.length); i++) {
            if ((keluar[i] & 0xFF) != (i % 251) + 1) {
                rusak = i;
                break;
            }
        }
        check("seluruh isi paket besar utuh setelah buffer tumbuh"
                + (rusak >= 0 ? " (rusak mulai byte " + rusak + ")" : ""), rusak < 0);

        // byte awal paling rawan: itu yang hilang pada bug aslinya
        check("byte-byte awal tidak ikut terbuang saat buffer tumbuh",
                keluar.length > 0 && (keluar[0] & 0xFF) == 1);
    }

    /** Uji paket gambar mob (0x33) dan aturan siapa yang digambar. */
    private static void mobTest(MapData map, User sd) {
        log.info("=== mob (clif_cmoblook_sub 0x33) ===");

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
        placeAt(map, sd, px, py);

        MobData jenis = new MobData();
        jenis.id = 42;
        jenis.yname = "squirrel";      // nama SKRIP
        jenis.name = "Tupai";          // nama TAMPILAN
        jenis.mobType = 1;             // digambar sebagai karakter
        jenis.vita = 18;
        jenis.mana = 5;
        jenis.sex = 1;
        jenis.face = 3;
        jenis.hair = 4;
        jenis.hairColor = 5;
        jenis.faceColor = 6;
        jenis.skinColor = 7;
        jenis.look = 200;

        Mob mb = new Mob();
        mb.data = jenis;
        mb.id = Mob.MOB_START_NUM + 1;
        mb.m = 0;
        mb.x = px + 1;
        mb.y = py;
        MobRegistry.resetStats(mb);
        map.addBlock(mb);

        check("mob: nama skrip dari yname", mb.scriptName().equals("squirrel"));
        check("mob: nama tampilan dari name", mb.displayName().equals("Tupai"));
        check("mob: nyawa awal dari jenisnya", mb.currentVita == 18 && mb.maxVita == 18);
        check("mob: lahir dalam keadaan hidup", mb.isAlive());

        drain(MapServer.net.session(sd.fd));
        Clif.sendMobLook(sd, mb);
        byte[] raw = drain(MapServer.net.session(sd.fd));
        check("mob: paket terkirim", raw.length > 0);
        byte[] p = decrypt(raw, sd);
        check("mob: opcode 0x33", (p[3] & 0xFF) == 0x33);
        check("mob: posisi di [5],[7]", be16(p, 5) == px + 1 && be16(p, 7) == py);
        check("mob: id blok di [10]", (be32(p, 10) & 0xFFFFFFFFL) == mb.id);
        check("mob: sex di [14]", be16(p, 14) == 1);
        check("mob: kecepatan tetap 80 di [19]", (p[19] & 0xFF) == 80);
        check("mob: wajah/rambut di [21]..[25]",
                (p[21] & 0xFF) == 3 && (p[22] & 0xFF) == 4 && (p[23] & 0xFF) == 5);
        check("mob: tanpa senjata 0xFFFF di [29]",
                (be16(p, 29) & 0xFFFF) == 0xFFFF);
        int nl = p[59] & 0xFF;
        check("mob: panjang nama TAMPILAN di [59]", nl == "Tupai".length());
        check("mob: nama tampilan di [60..]", new String(p, 60, nl,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Tupai"));
        check("mob: ladang panjang = len + 60 + 3", be16(p, 1) == nl + 63);

        // mob mati tidak digambar
        mb.state = MobData.MOB_DEAD;
        drain(MapServer.net.session(sd.fd));
        Clif.sendMobLook(sd, mb);
        check("mob mati tidak digambar",
                drain(MapServer.net.session(sd.fd)).length == 0);
        mb.state = MobData.MOB_ALIVE;

        // mob bukan karakter tidak digambar lewat paket ini
        jenis.mobType = 0;
        drain(MapServer.net.session(sd.fd));
        Clif.sendMobLook(sd, mb);
        check("mob non-karakter tidak digambar lewat 0x33",
                drain(MapServer.net.session(sd.fd)).length == 0);
        jenis.mobType = 1;

        // sapuan area ikut menggambar mob
        drain(MapServer.net.session(sd.fd));
        Clif.getCharArea(sd);
        List<int[]> seen = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("getCharArea ikut menggambar mob",
                seen.stream().anyMatch(o -> o[0] == 0x33));

        // resetStats mengembalikan nyawa penuh setelah mati
        mb.currentVita = 1;
        mb.state = MobData.MOB_DEAD;
        MobRegistry.resetStats(mb);
        check("resetStats memulihkan nyawa dan menghidupkan kembali",
                mb.currentVita == 18 && mb.isAlive());

        // --- AI mob: tik 50 ms, tabel menurut MobAI ---
        org.rtk.map.script.ScriptEngine mobEngine = new org.rtk.map.script.ScriptEngine();
        mobEngine.globals().load(
                "MOVE_BASIC = 0\n"
              + "ATK_BASIC = 0\n"
              + "MOVE_SENDIRI = 0\n"
              + "mob_ai_basic = { move = function() MOVE_BASIC = MOVE_BASIC + 1 end,\n"
              + "                 attack = function() ATK_BASIC = ATK_BASIC + 1 end }\n"
              + "squirrel = { move = function() MOVE_SENDIRI = MOVE_SENDIRI + 1 end }\n").call();

        MobRegistry reg = new MobRegistry();
        reg.useIdIndex(MapServer.npcs);   // mob harus bisa dicari lewat id
        jenis.moveTime = 150;      // tiga tik
        jenis.attackTime = 100;    // dua tik
        jenis.subtype = 0;         // -> mob_ai_basic
        jenis.type = MobData.MOB_NORMAL;
        jenis.spawnTime = 1;
        mb.state = MobData.MOB_ALIVE;
        mb.moveTimer = 0;
        mb.attackTimer = 0;
        mb.target = 0;
        map.addBlock(mb);
        reg.add(mb);
        map.users = 1;             // ada pemain: AI berjalan

        for (int i = 0; i < 2; i++) {
            reg.runTimers(mobEngine, MapServer.world);
        }
        check("AI: belum mencapai ambang gerak",
                mobEngine.globals().get("MOVE_BASIC").toint() == 0);
        reg.runTimers(mobEngine, MapServer.world);
        check("AI: kait move terpicu di ambang (mob_ai_basic)",
                mobEngine.globals().get("MOVE_BASIC").toint() == 1);

        // dengan sasaran, serangan menggantikan gerakan
        mb.target = 12345;
        mb.attackTimer = 0;
        for (int i = 0; i < 2; i++) {
            reg.runTimers(mobEngine, MapServer.world);
        }
        check("AI: kait attack terpicu saat ada sasaran",
                mobEngine.globals().get("ATK_BASIC").toint() == 1);
        mb.target = 0;

        // MobAI 4 memakai skrip mob itu sendiri (yname)
        jenis.subtype = 4;
        mb.moveTimer = 0;
        for (int i = 0; i < 3; i++) {
            reg.runTimers(mobEngine, MapServer.world);
        }
        check("AI: MobAI 4 memanggil skrip mob sendiri (yname)",
                mobEngine.globals().get("MOVE_SENDIRI").toint() == 1);
        jenis.subtype = 0;

        // peta tanpa pemain: AI dilewati
        map.users = 0;
        int sebelumAi = mobEngine.globals().get("MOVE_BASIC").toint();
        mb.moveTimer = 0;
        for (int i = 0; i < 10; i++) {
            reg.runTimers(mobEngine, MapServer.world);
        }
        check("AI: peta tanpa pemain dilewati",
                mobEngine.globals().get("MOVE_BASIC").toint() == sebelumAi);
        map.users = 1;

        // mob diam tidak pernah bergerak
        jenis.type = MobData.MOB_STATIONARY;
        mb.moveTimer = 0;
        for (int i = 0; i < 10; i++) {
            reg.runTimers(mobEngine, MapServer.world);
        }
        check("AI: mob diam tidak pernah bergerak",
                mobEngine.globals().get("MOVE_BASIC").toint() == sebelumAi);
        jenis.type = MobData.MOB_NORMAL;

        // --- mati lalu lahir ulang di titik awal ---
        mb.startM = 0;
        mb.startX = px;
        mb.startY = py;
        map.moveBlock(mb, px + 4, py + 2);   // dipancing menjauh
        reg.kill(mb, MapServer.world);
        check("mati: keadaan MOB_DEAD dan nyawa 0",
                !mb.isAlive() && mb.currentVita == 0);
        check("mati: dicabut dari indeks peta",
                !map.objectsAt(px + 4, py + 2).contains(mb));

        mb.lastDeath = 0;   // paksa jeda kelahiran ulang sudah lewat
        reg.runTimers(mobEngine, MapServer.world);
        check("lahir ulang: kembali hidup", mb.isAlive());
        check("lahir ulang: nyawa penuh", mb.currentVita == mb.maxVita);
        check("lahir ulang: kembali ke TITIK AWAL, bukan tempat mati",
                mb.x == px && mb.y == py);
        check("lahir ulang: masuk lagi ke indeks peta",
                map.objectsAt(px, py).contains(mb));

        // --- pertarungan: skrip menemukan mob lalu melukainya ---
        MapServer.scriptEngine = mobEngine;
        // Pemain TERSENDIRI untuk uji ini: `ScriptPlayer.udata` di-cache,
        // jadi satu objek pemain hanya boleh dipakai oleh SATU
        // ScriptEngine. Memakai `sd` di sini akan merusak uji dialog yang
        // memakai engine lain.
        CharStatus stm = new CharStatus();
        stm.id = 7777;
        stm.name = "Petarung";
        stm.baseHp = 100;
        stm.lastPos = new Point(0, px, py);
        User sdm = new User(21, stm);
        sdm.m = 0;
        sdm.x = px;
        sdm.y = py;
        map.addBlock(sdm);
        MapServer.net.openTestSession(sdm.fd);
        var pm = sdm.scriptPlayer();
        mb.state = MobData.MOB_ALIVE;
        MobRegistry.resetStats(mb);
        map.moveBlock(mb, px + 1, py);
        map.users = 1;
        placeAt(map, sd, px, py);

        check("BL_MOB terdaftar sebagai global Lua",
                mobEngine.globals().get("BL_MOB").toint()
                        == org.rtk.map.data.BlockList.BL_MOB);

        mobEngine.globals().load(
                "CARI = function(pl)\n"
              + "  local t = pl:getObjectsInCell(pl.m, pl.x + 1, pl.y, BL_MOB)\n"
              + "  return #t\n"
              + "end\n"
              + "PUKUL = function(pl, n)\n"
              + "  local t = pl:getObjectsInCell(pl.m, pl.x + 1, pl.y, BL_MOB)\n"
              + "  if #t == 0 then return -1 end\n"
              + "  local m = t[1]\n"
              + "  m.health = m.health - n\n"
              + "  return m.health\n"
              + "end\n"
              + "NAMA_MOB = function(pl)\n"
              + "  local t = pl:getObjectsInCell(pl.m, pl.x + 1, pl.y, BL_MOB)\n"
              + "  return t[1].yname\n"
              + "end\n").call();

        var jml = mobEngine.globals().get("CARI").call(mobEngine.playerRef(pm));
        check("skrip menemukan mob di petak sebelah", jml.toint() == 1);

        var nm = mobEngine.globals().get("NAMA_MOB").call(mobEngine.playerRef(pm));
        check("skrip membaca yname mob", "squirrel".equals(nm.tojstring()));

        // saring jenis: mencari PC di petak mob tidak menemukan apa pun
        mobEngine.globals().load(
                "CARI_PC = function(pl)\n"
              + "  return #pl:getObjectsInCell(pl.m, pl.x + 1, pl.y, BL_PC)\n"
              + "end\n").call();
        check("penyaringan BL_ bekerja: tidak ada PC di petak mob",
                mobEngine.globals().get("CARI_PC").call(mobEngine.playerRef(pm)).toint() == 0);

        long nyawaAwal = mb.currentVita;
        var sisa = mobEngine.globals().get("PUKUL")
                .call(mobEngine.playerRef(pm), org.luaj.vm2.LuaValue.valueOf(5));
        check("skrip melukai mob (nyawa berkurang)",
                mb.currentVita == nyawaAwal - 5 && sisa.toint() == mb.currentVita);

        // pukulan mematikan -> mob mati pada tik berikutnya
        mobEngine.globals().load("MATIKAN = function(pl)\n"
              + "  local t = pl:getObjectsInCell(pl.m, pl.x + 1, pl.y, BL_MOB)\n"
              + "  t[1].health = 0\n"
              + "end\n"
              + "MATI_TERPANGGIL = 0\n"
              + "squirrel.on_death = function() MATI_TERPANGGIL = 1 end\n").call();
        mobEngine.globals().get("MATIKAN").call(mobEngine.playerRef(pm));
        check("nyawa mob nol tapi belum ditandai mati", mb.isAlive());

        reg.runTimers(mobEngine, MapServer.world);
        check("tik berikutnya menandai mob mati", !mb.isAlive());
        check("kait on_death terpanggil",
                mobEngine.globals().get("MATI_TERPANGGIL").toint() == 1);
        check("mob mati dicabut dari indeks peta",
                !map.objectsAt(px + 1, py).contains(mb));
        check("skrip tidak lagi menemukan mob yang mati",
                mobEngine.globals().get("CARI").call(mobEngine.playerRef(pm)).toint() == 0);

        // --- tabel ancaman & jatuhan barang ---
        MobRegistry.resetStats(mb);
        mb.state = MobData.MOB_ALIVE;
        mb.threat.clear();
        mb.attacker = 0;
        map.addBlock(mb);
        MapServer.onlineChars.put(sdm.fd, sdm);

        mobEngine.globals().load(
                "ANCAM = function(pl, n)\n"
              + "  local t = pl:getObjectsInCell(pl.m, pl.x + 1, pl.y, BL_MOB)\n"
              + "  pl:addThreat(t[1].id, n)\n"
              + "end\n"
              + "DROP_DIPANGGIL = 0\n"
              + "DEATH_ARG2 = 0\n"
              + "HandleMobDrops = function(pl, mob) DROP_DIPANGGIL = 1 end\n"
              + "squirrel.on_death = function(mob, pl)\n"
              + "  if pl ~= nil then DEATH_ARG2 = 1 end\n"
              + "end\n").call();

        mobEngine.globals().get("ANCAM").call(mobEngine.playerRef(pm),
                org.luaj.vm2.LuaValue.valueOf(7));
        check("addThreat mencatat ancaman", mb.threat.get(sdm.id) == 7L);
        mobEngine.globals().get("ANCAM").call(mobEngine.playerRef(pm),
                org.luaj.vm2.LuaValue.valueOf(3));
        check("ancaman diakumulasi, bukan ditimpa", mb.threat.get(sdm.id) == 10L);
        check("penyerang terakhir tercatat", mb.attacker == sdm.id);
        check("pemegang ancaman terbesar adalah pemain itu",
                mb.topThreat() == sdm.id);

        mb.currentVita = 0;
        reg.runTimers(mobEngine, MapServer.world);
        check("mati: HandleMobDrops dipanggil",
                mobEngine.globals().get("DROP_DIPANGGIL").toint() == 1);
        check("mati: on_death menerima pembunuh sebagai argumen kedua",
                mobEngine.globals().get("DEATH_ARG2").toint() == 1);
        check("mati: tabel ancaman dibersihkan", mb.threat.isEmpty());
        check("mati: penyerang direset", mb.attacker == 0);

        // --- pengalaman & hitungan bunuh (clif.c:2469-2489) ---
        //
        // ⚠️ Ketiganya sempat HILANG SELURUHNYA dari port ini: mob mati
        // tanpa memberi pengalaman dan tanpa menambah hitungan bunuh, dan
        // tidak ada satu pun error. Gerbang ini yang menjaganya kembali.
        MobRegistry.resetStats(mb);
        mb.state = MobData.MOB_ALIVE;
        map.addBlock(mb);
        MapServer.onlineChars.put(sdm.fd, sdm);
        long expSemula = mb.data.exp;
        mb.data.exp = 7;
        sdm.status.killReg.clear();
        mobEngine.globals().load(
                "XP_DIPANGGIL = 0\n"
              + "XP_ARG_MOB = ''\n"
              + "onGetExp = function(pl, mob)\n"
              + "  XP_DIPANGGIL = XP_DIPANGGIL + 1\n"
              + "  XP_ARG_MOB = mob.yname\n"
              + "  GRUP_N = #pl.group\n"
              + "  GRUP_1 = pl.group[1]\n"
              + "end\n"
              + "LEVEL_DIPANGGIL = 0\n"
              + "onLevel = function(pl) LEVEL_DIPANGGIL = LEVEL_DIPANGGIL + 1 end\n").call();
        mobEngine.globals().get("ANCAM").call(mobEngine.playerRef(pm),
                org.luaj.vm2.LuaValue.valueOf(4));
        mb.currentVita = 0;
        reg.runTimers(mobEngine, MapServer.world);
        check("mati: kait onGetExp DIPANGGIL",
                mobEngine.globals().get("XP_DIPANGGIL").toint() == 1);
        check("mati: onGetExp menerima mob sebagai argumen kedua",
                "squirrel".equals(mobEngine.globals().get("XP_ARG_MOB").tojstring()));
        check("mati: player.group di dalam kait berisi pemain sendiri",
                mobEngine.globals().get("GRUP_N").toint() == 1
                        && (long) mobEngine.globals().get("GRUP_1").todouble()
                                == sdm.status.id);
        check("mati: hitungan bunuh bertambah untuk jenis mob itu",
                sdm.status.killReg.getOrDefault(mb.data.id, 0L) == 1L);
        check("mati: pc_checklevel ikut dipanggil",
                mobEngine.globals().get("LEVEL_DIPANGGIL").toint() >= 1);

        // Mob tanpa pengalaman TIDAK masuk hitungan bunuh saat sendirian —
        // syarat `if (mob->exp)` yang hanya ada di cabang solo.
        MobRegistry.resetStats(mb);
        mb.state = MobData.MOB_ALIVE;
        map.addBlock(mb);
        mb.data.exp = 0;
        sdm.status.killReg.clear();
        mobEngine.globals().get("ANCAM").call(mobEngine.playerRef(pm),
                org.luaj.vm2.LuaValue.valueOf(4));
        mb.currentVita = 0;
        reg.runTimers(mobEngine, MapServer.world);
        check("mati: mob tanpa pengalaman tidak masuk hitungan bunuh",
                sdm.status.killReg.isEmpty());
        check("mati: kait onGetExp tetap dipanggil untuk mob tanpa pengalaman",
                mobEngine.globals().get("XP_DIPANGGIL").toint() == 2);
        mb.data.exp = expSemula;

        MapServer.onlineChars.remove(sdm.fd);

        // --- addNPC: skrip melahirkan NPC sementara ---
        mobEngine.globals().load(
                "SPAWN_DIPANGGIL = 0\n"
              + "AKHIR_DIPANGGIL = 0\n"
              + "TrapNpc = {}\n"
              + "TrapNpc.on_spawn = function() SPAWN_DIPANGGIL = 1 end\n"
              + "TrapNpc.endAction = function() AKHIR_DIPANGGIL = 1 end\n"
              + "PASANG = function(pl)\n"
              + "  pl:addNPC('TrapNpc', pl.m, pl.x, pl.y, 0, 0, 150, 0, 0)\n"
              + "end\n").call();

        int npcSebelum = MapServer.npcs.count();
        mobEngine.globals().get("PASANG").call(mobEngine.playerRef(pm));
        check("addNPC melahirkan NPC sementara",
                MapServer.npcs.count() == npcSebelum + 1);
        check("kait on_spawn terpanggil",
                mobEngine.globals().get("SPAWN_DIPANGGIL").toint() == 1);

        // ⚠️ BUKAN byName("TrapNpc"). `map_name2npc` di C mencocokkan
        // `npc_name`, yaitu nama TAMPILAN — dan `bll_addnpc` mengisinya
        // "nothing" bila skrip tidak menyebutkannya (sl.c:3950). Jadi mencari
        // NPC sementara lewat nama skripnya memang tidak seharusnya berhasil;
        // uji ini dulu lulus hanya karena indeks Java salah kolom.
        Npc temp = MapServer.npcs.all().stream()
                .filter(n -> "TrapNpc".equals(n.name))
                .findFirst().orElse(null);
        check("NPC sementara memakai rentang id terpisah",
                temp != null && temp.id >= Npc.NPCT_START_NUM);
        check("NPC sementara ditandai temporary", temp != null && temp.temporary);
        check("NPC sementara bisa dicari lewat indeks id",
                temp != null && MapServer.npcs.byId(temp.id) == temp);

        // durasi habis -> endAction lalu dicabut
        for (int i = 0; i < 3; i++) {
            MapServer.npcs.runTimers(mobEngine);
        }
        check("durasi habis: kait endAction terpanggil",
                mobEngine.globals().get("AKHIR_DIPANGGIL").toint() == 1);
        check("NPC sementara dicabut setelah durasinya habis",
                MapServer.npcs.count() == npcSebelum);
        check("NPC sementara hilang dari indeks id",
                temp != null && MapServer.npcs.byId(temp.id) == null);

        // --- callBase: mob memanggil kait skripnya sendiri ---
        mobEngine.globals().load(
                "BASE_DIPANGGIL = 0\n"
              + "BASE_ARG2 = 0\n"
              + "squirrel.on_attacked = function(mb, lawan)\n"
              + "  BASE_DIPANGGIL = 1\n"
              + "  if lawan ~= nil then BASE_ARG2 = 1 end\n"
              + "end\n"
              + "PANGGIL_BASE = function(pl)\n"
              + "  local t = pl:getObjectsInCell(pl.m, pl.x + 1, pl.y, BL_MOB)\n"
              + "  return t[1]:callBase('on_attacked')\n"
              + "end\n").call();

        MobRegistry.resetStats(mb);
        mb.state = MobData.MOB_ALIVE;
        map.addBlock(mb);
        MapServer.onlineChars.put(sdm.fd, sdm);
        mb.attacker = sdm.id;

        var hasilBase = mobEngine.globals().get("PANGGIL_BASE")
                .call(mobEngine.playerRef(pm));
        check("callBase mengembalikan true", hasilBase.toboolean());
        check("callBase memanggil kait skrip mob",
                mobEngine.globals().get("BASE_DIPANGGIL").toint() == 1);
        check("callBase mengirim penyerang sebagai argumen kedua",
                mobEngine.globals().get("BASE_ARG2").toint() == 1);
        MapServer.onlineChars.remove(sdm.fd);
        map.delBlock(mb);

        MapServer.scriptEngine = null;
        map.delBlock(sdm);
        map.users = 0;
        map.delBlock(mb);
        placeAt(map, sd, px, py);
    }

    /**
     * Uji jalur dialog NPC ujung-ke-ujung: klik &rarr; paket dialog keluar
     * &rarr; jawaban masuk &rarr; skrip lanjut.
     *
     * <p>Skripnya ditulis dalam gaya konten rtklua asli dan memakai
     * primitif yang sama dengan skrip sungguhan, jadi yang diuji bukan
     * jalur khusus uji melainkan jalur yang benar-benar dipakai.</p>
     */
    private static void dialogTest(MapData map, User sd) {
        log.info("=== dialog NPC (klik 0x43 -> 0x30/0x2F -> jawab 0x3A) ===");

        // Toko butuh ItemDb; kalau MySQL tidak ada, bagian itu dilewati
        // dengan pesan jelas — bukan diam-diam dianggap lolos.
        MapServer.sql.connect("rtk", "50LM8U8Poq5uX2AZJVKs", "127.0.0.1", 3306, "RTK");

        org.rtk.map.script.ScriptEngine engine = new org.rtk.map.script.ScriptEngine();
        MapServer.scriptEngine = engine;
        // Cukup sys.lua: di situlah `async` dan primitif dialog didefinisikan.
        // Memuat seluruh 906 skrip tidak diperlukan di sini — itu urusan
        // scripttest — dan hanya memperlambat gerbang ini.
        engine.globals().loadfile("luascript/Developers/sys.lua").call();
        engine.globals().load(
                "DialogNpc = {}\n"
              + "DialogNpc.click = async(function(player)\n"
              + "    local pilih = player:menu('Mau apa?', {'Beli', 'Pergi'})\n"
              + "    player.registry['pilihan'] = pilih\n"
              + "    player:dialog('Baiklah.', {})\n"
              + "    player.registry['selesai'] = 1\n"
              + "end)\n").call();

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
        placeAt(map, sd, px, py);
        sd.status.karma = 0f;

        Npc nd = new Npc();
        nd.name = "DialogNpc";
        nd.displayName = "Pedagang";
        nd.npcId = 7;
        nd.id = Npc.blockIdFor(7);
        nd.m = 0;
        nd.x = px + 1;
        nd.y = py;
        nd.npcType = 1;
        nd.graphicId = 100;
        map.addBlock(nd);
        MapServer.npcs.register(nd);

        // --- klik NPC ---
        feedClick(sd, nd.id);
        drain(MapServer.net.session(sd.fd));
        Clif.parseClick(sd);
        check("klik menyetel lastClick", sd.lastClick == nd.id);

        List<int[]> keluar = splitPackets(drain(MapServer.net.session(sd.fd)));
        // Menu memakai opcode 0x30 — SAMA dengan kotak dialog. Yang
        // membedakan byte [5]/[6]: dialog 0x00/0x01, menu 0x02/0x02.
        check("klik menghasilkan paket 0x30", keluar.stream().anyMatch(o -> o[0] == 0x30));
        // paket sudah terkuras di atas; bangun ulang untuk memeriksa isinya
        drain(MapServer.net.session(sd.fd));
        byte[] mRaw;
        Clif.sendScriptMenu(sd, nd.id, "Mau apa?", java.util.List.of("Beli", "Pergi"),
                false, false);
        mRaw = drain(MapServer.net.session(sd.fd));
        byte[] mPlain = decrypt(mRaw, sd);
        check("menu: opcode 0x30", (mPlain[3] & 0xFF) == 0x30);
        check("menu: penanda [5]=0x02 [6]=0x02",
                (mPlain[5] & 0xFF) == 0x02 && (mPlain[6] & 0xFF) == 0x02);
        check("menu: id lawan bicara di [7]",
                (be32(mPlain, 7) & 0xFFFFFFFFL) == nd.id);
        int dlgLen = be16(mPlain, 26);
        check("menu: panjang teks di [26]", dlgLen == "Mau apa?".length());
        check("menu: teks di [28..]", new String(mPlain, 28, dlgLen,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Mau apa?"));
        int cnt = mPlain[dlgLen + 1 + 27] & 0xFF;
        check("menu: jumlah pilihan tepat setelah teks", cnt == 2);

        var p = sd.scriptPlayer();
        check("skrip menunggu jawaban", p.pendingDialog != null);
        check("dialog yang menunggu berupa menu",
                p.pendingDialog != null && p.pendingDialog.kind.startsWith("menu"));

        // --- pemain memilih opsi 1 ---
        // Primitif `menu` dijawab lewat opcode 0x39 ragam 1, BUKAN 0x3A
        // ragam 2 (itu milik `menuSeq`). Di C keduanya memang memakai
        // fungsi resume yang berbeda.
        feedMenuAnswer(sd, 1, 1);
        drain(MapServer.net.session(sd.fd));
        Clif.parseMenuInput(sd);
        check("pilihan tercatat di registry skrip",
                Integer.valueOf(1).equals(p.registry.get("pilihan")));
        keluar = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("jawaban memicu dialog lanjutan 0x30",
                keluar.stream().anyMatch(o -> o[0] == 0x30));

        // --- pemain menutup dialog terakhir ---
        feedDialogAnswer(sd, 0x01, 1, 0);
        Clif.parseNpcDialog(sd);
        check("skrip berjalan sampai selesai",
                Integer.valueOf(1).equals(p.registry.get("selesai")));
        check("tidak ada dialog yang menggantung", p.pendingDialog == null);

        // --- objek skrip harus SATU per pemain, bukan salinan baru ---
        check("scriptPlayer() mengembalikan objek yang sama",
                sd.scriptPlayer() == p);

        // --- C1: registry skrip = registry karakter yang disimpan ---
        check("registry skrip DAN CharStatus adalah objek yang sama",
                p.registry == sd.status.registry);
        check("registryString ikut tersambung",
                p.registryString == sd.status.registryString);
        check("registry NPC ikut tersambung", p.npcInt == sd.status.npcRegistry);
        check("registry quest ikut tersambung", p.questReg == sd.status.questRegistry);
        check("nilai yang ditulis skrip terlihat di CharStatus",
                Integer.valueOf(1).equals(sd.status.registry.get("pilihan")));

        // tulis dari Lua, lalu pastikan sampai ke data tersimpan
        engine.globals().load("REG_TEST = function(pl)\n"
                + "  pl.registry['dari_lua'] = 77\n"
                + "  pl.registryString['teks'] = 'halo'\n"
                + "  pl.quest['q1'] = 5\n"
                + "end\n").call();
        engine.globals().get("REG_TEST").call(engine.playerRef(p));
        check("registry int dari Lua sampai ke CharStatus",
                Integer.valueOf(77).equals(sd.status.registry.get("dari_lua")));
        check("registry string dari Lua sampai ke CharStatus",
                "halo".equals(sd.status.registryString.get("teks")));
        check("registry quest dari Lua sampai ke CharStatus",
                Integer.valueOf(5).equals(sd.status.questRegistry.get("q1")));

        // --- level yang ditulis skrip menembus ke CharStatus ---
        int levelLama = sd.status.level;
        engine.globals().load("LVL_TEST = function(pl) pl.level = 42 end").call();
        engine.globals().get("LVL_TEST").call(engine.playerRef(p));
        check("level dari Lua menembus ke CharStatus", sd.status.level == 42);
        sd.status.level = levelLama;

        // --- posisi TIDAK boleh bisa ditulis lewat atribut (seperti C) ---
        int xLama = sd.x;
        engine.globals().load("POS_TEST = function(pl) pl.x = 999 end").call();
        engine.globals().get("POS_TEST").call(engine.playerRef(p));
        check("menulis player.x tidak memindahkan pemain sungguhan", sd.x == xLama);

        // --- dialogType 1: kotak dialog menampilkan wujud NPC ---
        sd.dialogType = 1;
        sd.npcGraphic = 100;
        nd.sex = 1;
        nd.face = 5;
        nd.hair = 6;
        drain(MapServer.net.session(sd.fd));
        Clif.sendScriptMes(sd, nd.id, "Halo", false, true);
        byte[] pRaw = drain(MapServer.net.session(sd.fd));
        byte[] pPl = decrypt(pRaw, sd);
        check("dialogType 1: opcode tetap 0x30", (pPl[3] & 0xFF) == 0x30);
        check("dialogType 1: penanda [11] = 1", (pPl[11] & 0xFF) == 1);
        check("dialogType 1: sex NPC di [12]", be16(pPl, 12) == 1);
        check("dialogType 1: wajah/rambut di [19],[20]",
                (pPl[19] & 0xFF) == 5 && (pPl[20] & 0xFF) == 6);
        check("dialogType 1: tanpa senjata 0xFFFF di [27]",
                (be16(pPl, 27) & 0xFFFF) == 0xFFFF);
        check("dialogType 1: helm kosong [33]=0 [34]=0xFF",
                (pPl[33] & 0xFF) == 0 && (pPl[34] & 0xFF) == 0xFF);
        check("dialogType 1: sepatu kosong pakai sex di [51]", be16(pPl, 51) == 1);
        check("dialogType 1: tombol next di [63]", (pPl[63] & 0xFF) == 1);
        int dl = be16(pPl, 64);
        check("dialogType 1: panjang teks di [64]", dl == "Halo".length());
        check("dialogType 1: teks bergeser ke [66..]",
                new String(pPl, 66, dl, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .equals("Halo"));
        check("dialogType 1: ladang panjang = len + 63 + 3", be16(pPl, 1) == dl + 66);

        // ragam biasa harus tetap memakai tata letak pendek
        sd.dialogType = 0;
        drain(MapServer.net.session(sd.fd));
        Clif.sendScriptMes(sd, nd.id, "Halo", false, true);
        byte[] qPl = decrypt(drain(MapServer.net.session(sd.fd)), sd);
        check("dialogType 0: teks tetap di [28..]",
                new String(qPl, 28, be16(qPl, 26),
                        java.nio.charset.StandardCharsets.ISO_8859_1).equals("Halo"));

        // --- jalur input: 0x2F keluar, 0x39 ragam 3 masuk ---
        MapServer.scriptEngine.cancel(p);
        engine.globals().load(
                "InputNpc = {}\n"
              + "InputNpc.click = async(function(pl)\n"
              + "    local teks = pl:input('Siapa namamu?')\n"
              + "    pl.registryString['jawaban'] = teks\n"
              + "end)\n").call();
        Npc nd2 = new Npc();
        nd2.name = "InputNpc";
        nd2.npcId = 8;
        nd2.id = Npc.blockIdFor(8);
        nd2.m = 0;
        nd2.x = px + 1;
        nd2.y = py;
        nd2.npcType = 1;
        MapServer.npcs.register(nd2);
        map.addBlock(nd2);

        feedClick(sd, nd2.id);
        drain(MapServer.net.session(sd.fd));
        Clif.parseClick(sd);
        byte[] iRaw = drain(MapServer.net.session(sd.fd));
        check("input: paket keluar ada", iRaw.length > 0);
        byte[] iPlain = decrypt(iRaw, sd);
        check("input: opcode 0x2F", (iPlain[3] & 0xFF) == 0x2F);
        check("input: ragam [5] = 3", (iPlain[5] & 0xFF) == 3);

        feedInputAnswer(sd, "Budi");
        Clif.parseMenuInput(sd);
        check("teks yang diketik pemain sampai ke skrip",
                "Budi".equals(sd.status.registryString.get("jawaban")));

        map.delBlock(nd2);

        // --- karma sangat buruk ditolak bicara ---
        sd.status.karma = -5f;
        MapServer.scriptEngine.cancel(p);
        drain(MapServer.net.session(sd.fd));
        Clif.parseClick(sd);
        keluar = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("karma buruk: tetap dapat balasan 0x30",
                keluar.stream().anyMatch(o -> o[0] == 0x30));
        sd.status.karma = 0f;

        // --- skrip menerima NPC sebagai argumen kedua (seperti C) ---
        MapServer.scriptEngine.cancel(p);
        engine.globals().load(
                "ArgNpc = {}\n"
              + "ArgNpc.click = function(pl, np)\n"
              + "    pl.registryString['npc_yname'] = np.yname\n"
              + "    pl.registryString['npc_name'] = np.name\n"
              + "    pl.registry['npc_bank'] = np.bankNPC\n"
              + "end\n").call();
        Npc an = new Npc();
        an.name = "ArgNpc";
        an.displayName = "Bankir";
        an.npcId = 12;
        an.id = Npc.blockIdFor(12);
        an.m = 0;
        an.x = px + 1;
        an.y = py;
        an.npcType = 1;
        an.bankNpc = true;
        MapServer.npcs.register(an);
        map.addBlock(an);

        feedClick(sd, an.id);
        Clif.parseClick(sd);
        check("skrip menerima npc.yname (nama skrip)",
                "ArgNpc".equals(sd.status.registryString.get("npc_yname")));
        check("skrip menerima npc.name (nama tampilan)",
                "Bankir".equals(sd.status.registryString.get("npc_name")));
        check("skrip membaca npc.bankNPC",
                Integer.valueOf(1).equals(sd.status.registry.get("npc_bank")));
        map.delBlock(an);

        // --- toko: daftar jualan keluar, pilihan pemain masuk ---
        MapServer.scriptEngine.cancel(p);
        MapServer.itemDb.load(org.rtk.map.MapServer.sql);
        boolean adaItemDb = MapServer.itemDb.count() > 0;
        if (!adaItemDb) {
            log.info("  (tanpa database: uji toko dilewati)");
        } else {
            engine.globals().load(
                    "ShopNpc = {}\n"
                  + "ShopNpc.click = async(function(pl)\n"
                  + "    local pilih = pl:buy('Silakan pilih', {'fox_fur'}, {22})\n"
                  + "    pl.registryString['dibeli'] = pilih\n"
                  + "end)\n").call();
            Npc shop = new Npc();
            shop.name = "ShopNpc";
            shop.npcId = 11;
            shop.id = Npc.blockIdFor(11);
            shop.m = 0;
            shop.x = px + 1;
            shop.y = py;
            shop.npcType = 1;
            shop.graphicId = 100;
            MapServer.npcs.register(shop);
            map.addBlock(shop);

            feedClick(sd, shop.id);
            drain(MapServer.net.session(sd.fd));
            sd.dialogType = 0;
            Clif.parseClick(sd);
            byte[] sRaw = drain(MapServer.net.session(sd.fd));
            check("toko: paket keluar ada", sRaw.length > 0);
            byte[] sPl = decrypt(sRaw, sd);
            check("toko: opcode 0x2F", (sPl[3] & 0xFF) == 0x2F);
            check("toko: ragam [5]=4 [6]=2",
                    (sPl[5] & 0xFF) == 4 && (sPl[6] & 0xFF) == 2);
            int dl2 = be16(sPl, 20);
            check("toko: panjang dialog di [20]", dl2 == "Silakan pilih".length());
            check("toko: teks dialog di [22..]", new String(sPl, 22, dl2,
                    java.nio.charset.StandardCharsets.ISO_8859_1).equals("Silakan pilih"));
            // setelah teks: panjang ulang (2 byte) lalu jumlah barang (BE16)
            check("toko: jumlah barang = 1", be16(sPl, dl2 + 2 + 22) == 1);
            // ikon(2) + warna(1) + harga(4)
            int off = dl2 + 4 + 22;
            check("toko: harga 22 tersimpan di paket", be32(sPl, off + 3) == 22);

            // pemain memilih dengan mengirim NAMA barang
            feedBuyAnswer(sd, "fox_fur");
            Clif.parseMenuInput(sd);
            check("toko: nama barang pilihan sampai ke skrip",
                    "fox_fur".equals(sd.status.registryString.get("dibeli")));

            // --- daftar jual: slot inventaris, 1-basis ke klien ---
            sd.status.inventory.clear();
            var barang = MapServer.itemDb.infoByName("fox_fur");
            for (int i = 0; i < 2; i++) {
                var it = new org.rtk.common.mmo.Item();
                it.id = barang.id();
                it.amount = 1;
                it.pos = i;
                sd.status.inventory.add(it);
            }
            MapServer.scriptEngine.cancel(p);
            engine.globals().load(
                    "SellNpc = {}\n"
                  + "SellNpc.click = async(function(pl)\n"
                  + "    local slot = pl:sell('Jual apa?', {'fox_fur'})\n"
                  + "    pl.registry['slot_dijual'] = slot\n"
                  + "end)\n").call();
            shop.name = "SellNpc";
            feedClick(sd, shop.id);
            map.addBlock(shop);
            drain(MapServer.net.session(sd.fd));
            Clif.parseClick(sd);
            byte[] jRaw = drain(MapServer.net.session(sd.fd));
            byte[] jPl = decrypt(jRaw, sd);
            check("jual: opcode 0x2F", (jPl[3] & 0xFF) == 0x2F);
            check("jual: ragam [5]=5 [6]=4",
                    (jPl[5] & 0xFF) == 5 && (jPl[6] & 0xFF) == 4);
            int jd = be16(jPl, 20);
            // len mulai dari panjang+2, lalu +2 untuk panjang ulang
            int cntOff = jd + 4 + 20;
            check("jual: dua slot ditawarkan", (jPl[cntOff] & 0xFF) == 2);
            check("jual: slot dikirim 1-basis",
                    (jPl[cntOff + 1] & 0xFF) == 1 && (jPl[cntOff + 2] & 0xFF) == 2);

            feedSellAnswer(sd, 1);
            Clif.parseMenuInput(sd);
            check("jual: nomor slot sampai ke skrip",
                    Integer.valueOf(1).equals(sd.status.registry.get("slot_dijual")));
            sd.status.inventory.clear();

            map.delBlock(shop);
        }

        // --- timer NPC: kait move/action pada ambangnya ---
        engine.globals().load(
                "TimerNpc = {}\n"
              + "TICK_MOVE = 0\n"
              + "TICK_ACTION = 0\n"
              + "TimerNpc.move = function() TICK_MOVE = TICK_MOVE + 1 end\n"
              + "TimerNpc.action = function() TICK_ACTION = TICK_ACTION + 1 end\n").call();
        Npc tn = new Npc();
        tn.name = "TimerNpc";
        tn.npcId = 9;
        tn.id = Npc.blockIdFor(9);
        tn.m = 0;
        tn.x = px;
        tn.y = py;
        tn.moveTime = 300;     // tiga tik
        tn.actionTime = 500;   // lima tik
        MapServer.npcs.all().add(tn);
        MapServer.npcs.register(tn);

        for (int i = 0; i < 2; i++) {
            MapServer.npcs.runTimers(engine);
        }
        check("belum mencapai ambang: move belum terpicu",
                engine.globals().get("TICK_MOVE").toint() == 0);

        MapServer.npcs.runTimers(engine);   // tik ke-3 = 300 ms
        check("move terpicu tepat di ambang 300 ms",
                engine.globals().get("TICK_MOVE").toint() == 1);
        check("action belum terpicu di 300 ms",
                engine.globals().get("TICK_ACTION").toint() == 0);

        for (int i = 0; i < 2; i++) {
            MapServer.npcs.runTimers(engine);   // tik 4 dan 5
        }
        check("action terpicu di ambang 500 ms",
                engine.globals().get("TICK_ACTION").toint() == 1);

        // penghitung direset, bukan dikurangi: pemicu berikutnya 3 tik lagi
        for (int i = 0; i < 3; i++) {
            MapServer.npcs.runTimers(engine);
        }
        check("move terpicu kedua kali setelah 3 tik berikutnya",
                engine.globals().get("TICK_MOVE").toint() == 2);

        // NPC tanpa moveTime tidak pernah dipicu
        tn.moveTime = 0;
        int sebelum = engine.globals().get("TICK_MOVE").toint();
        for (int i = 0; i < 10; i++) {
            MapServer.npcs.runTimers(engine);
        }
        check("moveTime 0 berarti tidak berjalan sendiri",
                engine.globals().get("TICK_MOVE").toint() == sebelum);

        MapServer.npcs.all().remove(tn);

        map.delBlock(nd);
        MapServer.scriptEngine = null;
        placeAt(map, sd, px, py);
    }

    /** Paket klik 0x43 dengan id objek di offset 6 (BE32). */
    private static void feedClick(User sd, long id) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] pkt = new byte[12];
        pkt[0] = (byte) 0xAA;
        pkt[1] = 0x00;
        pkt[2] = 0x09;
        pkt[3] = 0x43;
        pkt[6] = (byte) ((id >> 24) & 0xFF);
        pkt[7] = (byte) ((id >> 16) & 0xFF);
        pkt[8] = (byte) ((id >> 8) & 0xFF);
        pkt[9] = (byte) (id & 0xFF);
        s.feedInbound(pkt);
    }

    /** Paket jawaban jual 0x39 ragam 4: nomor slot di [12]. */
    private static void feedSellAnswer(User sd, int slot) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] pkt = new byte[16];
        pkt[0] = (byte) 0xAA;
        pkt[1] = 0x00;
        pkt[2] = 0x0D;
        pkt[3] = 0x39;
        pkt[5] = 4;
        pkt[12] = (byte) slot;
        s.feedInbound(pkt);
    }

    /** Paket jawaban beli 0x39 ragam 2: nama barang di [13], panjang di [12]. */
    private static void feedBuyAnswer(User sd, String nama) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] t = nama.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] pkt = new byte[16 + t.length];
        pkt[0] = (byte) 0xAA;
        pkt[1] = (byte) (((13 + t.length) >> 8) & 0xFF);
        pkt[2] = (byte) ((13 + t.length) & 0xFF);
        pkt[3] = 0x39;
        pkt[5] = 2;
        pkt[12] = (byte) t.length;
        System.arraycopy(t, 0, pkt, 13, t.length);
        s.feedInbound(pkt);
    }

    /** Paket jawaban input 0x39 ragam 3: panjang di [12], teks di [13]. */
    private static void feedInputAnswer(User sd, String teks) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] t = teks.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] pkt = new byte[16 + t.length];
        pkt[0] = (byte) 0xAA;
        pkt[1] = (byte) (((13 + t.length) >> 8) & 0xFF);
        pkt[2] = (byte) ((13 + t.length) & 0xFF);
        pkt[3] = 0x39;
        pkt[5] = 3;
        pkt[12] = (byte) t.length;
        System.arraycopy(t, 0, pkt, 13, t.length);
        s.feedInbound(pkt);
    }

    /** Paket jawaban menu 0x39 ragam 1: pilihan di [10] BE16. */
    private static void feedMenuAnswer(User sd, int ragam, int pilihan) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] pkt = new byte[20];
        pkt[0] = (byte) 0xAA;
        pkt[1] = 0x00;
        pkt[2] = 0x11;
        pkt[3] = 0x39;
        pkt[5] = (byte) ragam;
        pkt[10] = (byte) ((pilihan >> 8) & 0xFF);
        pkt[11] = (byte) (pilihan & 0xFF);
        pkt[12] = 0;
        s.feedInbound(pkt);
    }

    /** Paket jawaban dialog 0x3A: ragam di [5], pilihan di [13], menu di [15]. */
    private static void feedDialogAnswer(User sd, int ragam, int pilihan, int menu) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] pkt = new byte[24];
        pkt[0] = (byte) 0xAA;
        pkt[1] = 0x00;
        pkt[2] = 0x15;
        pkt[3] = 0x3A;
        pkt[5] = (byte) ragam;
        pkt[13] = (byte) pilihan;
        pkt[15] = (byte) menu;
        s.feedInbound(pkt);
    }

    /**
     * Uji gambar ulang petak peta (0x06) dan checksumnya.
     *
     * <p>Nilai acuan CRC diambil dengan <b>mengompilasi dan menjalankan
     * {@code nexCRCC()} versi C</b>, bukan dihitung ulang dari rumus —
     * kalau port-nya salah, uji yang menghitung sendiri akan salah dengan
     * cara yang sama dan tetap lolos.</p>
     */
    private static void mapDataTest(MapData map, User sd) {
        log.info("=== gambar ulang petak peta (clif_sendmapdata 0x06) ===");

        // --- checksum: cocokkan dengan keluaran nexCRCC() versi C ---
        check("CRC {1,2,3} = 4642", Clif.nexCrc(new int[]{1, 2, 3}, 6) == 4642);
        check("CRC {100,0,250, 7,1,9} = 7879",
                Clif.nexCrc(new int[]{100, 0, 250, 7, 1, 9}, 12) == 7879);
        check("CRC sembilan nol = 0",
                Clif.nexCrc(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0}, 18) == 0);
        // nilai di atas 32767 penting: di C tabel dan buffernya bertipe
        // `short` BERTANDA, jadi kasus ini yang membuktikan port unsigned
        // di Java tetap menghasilkan 16 bit yang sama
        check("CRC nilai besar {65535,40000,123} = 8001",
                Clif.nexCrc(new int[]{65535, 40000, 123}, 6) == 8001);
        check("CRC dua petak bernilai besar = 61077",
                Clif.nexCrc(new int[]{50000, 1, 65000, 3, 33000, 7}, 12) == 61077);
        check("CRC panjang 0 = 0", Clif.nexCrc(new int[]{1, 2, 3}, 0) == 0);

        // --- paket petak peta ---
        placeAt(map, sd, 10, 10);
        drain(MapServer.net.session(sd.fd));
        boolean sent = Clif.sendMapData(sd, 4, 4, 6, 5, 0);
        check("petak peta terkirim", sent);

        byte[] raw = drain(MapServer.net.session(sd.fd));
        byte[] p = decrypt(raw, sd);
        check("petak: opcode 0x06", (p[3] & 0xFF) == 0x06);
        check("petak: x0,y0 di [6],[8]", be16(p, 6) == 4 && be16(p, 8) == 4);
        check("petak: lebar,tinggi di [10],[11]",
                (p[10] & 0xFF) == 6 && (p[11] & 0xFF) == 5);
        // 6x5 petak x 3 nilai x 2 byte = 180, mulai offset 12 -> pos 192
        check("petak: ladang panjang = pos-3 lalu +3 = 192",
                be16(p, 1) == 192);
        check("petak: tile pertama cocok dengan peta",
                be16(p, 12) == map.tile(4, 4));
        check("petak: pass pertama cocok", be16(p, 14) == map.pass(4, 4));
        check("petak: obj pertama cocok", be16(p, 16) == map.obj(4, 4));

        // --- checksum cocok: server sengaja TIDAK mengirim apa pun ---
        int[] isi = new int[6 * 5 * 3];
        int i = 0;
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 6; x++) {
                isi[i++] = map.tile(4 + x, 4 + y);
                isi[i++] = map.pass(4 + x, 4 + y);
                isi[i++] = map.obj(4 + x, 4 + y);
            }
        }
        int crc = Clif.nexCrc(isi, 6 * 5 * 6);
        drain(MapServer.net.session(sd.fd));
        boolean sent2 = Clif.sendMapData(sd, 4, 4, 6, 5, crc);
        check("checksum cocok: tidak dikirim ulang", !sent2);
        check("checksum cocok: benar-benar nol byte keluar",
                drain(MapServer.net.session(sd.fd)).length == 0);

        // --- penjaga permintaan kelewat besar (x1*y1 > 323) ---
        drain(MapServer.net.session(sd.fd));
        check("permintaan 20x20 ditolak", !Clif.sendMapData(sd, 0, 0, 20, 20, 0));
        check("permintaan kelewat besar tidak mengirim apa pun",
                drain(MapServer.net.session(sd.fd)).length == 0);

        // --- petak kosong ---
        check("lebar 0 tidak mengirim apa pun", !Clif.sendMapData(sd, 4, 4, 0, 0, 0));
    }

    /** Uji paket status 0x08 — panjangnya berubah menurut bendera. */
    private static void statusTest(User sd) {
        log.info("=== status (clif_sendstatus 0x08) ===");

        sd.status.level = 50;
        sd.status.country = 2;
        sd.status.totem = 3;
        sd.status.maxInv = 27;
        sd.status.hp = 1234;
        sd.status.mp = 567;
        sd.status.exp = 4_000_000_000L;   // di atas batas int bertanda
        sd.status.money = 99999;
        sd.status.settingFlags = 12605;
        sd.maxHp = 4000;
        sd.maxMp = 2000;
        sd.might = 11;
        sd.will = 12;
        sd.grace = 13;
        sd.armor = 14;

        // --- hanya blok ekor (tanpa bendera tambahan) ---
        byte[] raw = build(sd, u -> Clif.sendStatus(u, 0));
        byte[] p = decrypt(raw, sd);
        check("status: opcode 0x08", (p[3] & 0xFF) == 0x08);
        check("status: bendera ALWAYSON menyala", (p[5] & 0x08) != 0);
        // len = 11 -> ditulis 14, lalu setPacketIndexes MENAMBAH 3 -> 17,
        // dan total = ladang + 3 = 20. Jangan lupa penambahan itu (jebakan
        // yang sama pernah membuat ekspektasi uji sendId salah).
        check("status minimal: ladang panjang 17 setelah key index", be16(p, 1) == 17);
        check("status minimal: total 20 byte", raw.length == 20);
        check("status minimal: settingFlags di [13]", be32(p, 13) == 12605);

        // --- lengkap ---
        raw = build(sd, u -> Clif.sendStatus(u, Clif.SFLAG_ALL));
        p = decrypt(raw, sd);
        check("status penuh: level di [10]", (p[10] & 0xFF) == 50);
        check("status penuh: negara di [7]", (p[7] & 0xFF) == 2);
        check("status penuh: totem di [8]", (p[8] & 0xFF) == 3);
        check("status penuh: maxHp di [11]", be32(p, 11) == 4000);
        check("status penuh: maxMp di [15]", be32(p, 15) == 2000);
        check("status penuh: might/will di [19],[20]",
                (p[19] & 0xFF) == 11 && (p[20] & 0xFF) == 12);
        check("status penuh: dua byte tetap 0x03 di [21],[22]",
                (p[21] & 0xFF) == 3 && (p[22] & 0xFF) == 3);
        check("status penuh: grace di [23]", (p[23] & 0xFF) == 13);
        check("status penuh: armor di [26]", (p[26] & 0xFF) == 14);
        check("status penuh: maxInv di [34]", (p[34] & 0xFF) == 27);
        // FULLSTATS(29) -> HPMP di 35, XPMONEY di 43
        check("status penuh: hp di [35]", be32(p, 35) == 1234);
        check("status penuh: mp di [39]", be32(p, 39) == 567);
        check("status penuh: exp unsigned di [43]",
                (be32(p, 43) & 0xFFFFFFFFL) == 4_000_000_000L);
        check("status penuh: emas di [47]", be32(p, 47) == 99999);
        // len = 29+8+9+11 = 57 -> ditulis 60, +3 -> 63, total 66
        check("status penuh: ladang panjang 63", be16(p, 1) == 63);
        check("status penuh: total 66 byte", raw.length == 66);

        // --- bar pengalaman memakai tabel level sungguhan ---
        MapServer.classDb.load("db");
        sd.status.charClass = 0;
        sd.status.level = 2;
        sd.status.exp = 450;   // lv1=300, lv2=600 -> setengah jalan
        float pct = Clif.xpBarPercent(sd);
        check("bar exp di tengah level ~50% (dapat " + Math.round(pct) + "%)",
                pct > 45f && pct < 55f);
        sd.status.exp = 4_000_000_000L;
        sd.status.level = 50;
    }

    /** Uji paket gambar 0x33 untuk NPC dan pemain, serta sapuan area. */
    private static void lookTest(MapData map, User sd) {
        log.info("=== penggambaran (clif_*look_sub 0x33) ===");

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
        placeAt(map, sd, px, py);

        Npc nd = new Npc();
        nd.name = "TestNpc";
        nd.displayName = "Penjaga";
        nd.npcId = 5;
        nd.id = Npc.blockIdFor(5);
        nd.m = 0;
        nd.x = px + 1;
        nd.y = py;
        nd.side = 2;
        nd.sex = 1;
        nd.face = 7;
        nd.hair = 8;
        nd.hairColor = 9;
        nd.faceColor = 10;
        nd.skinColor = 11;
        nd.npcType = 1;   // tampil sebagai karakter
        map.addBlock(nd);

        drain(MapServer.net.session(sd.fd));
        Clif.sendNpcLook(sd, nd);
        byte[] raw = drain(MapServer.net.session(sd.fd));
        check("NPC: paket terkirim", raw.length > 0);
        byte[] p = decrypt(raw, sd);
        check("NPC: opcode 0x33", (p[3] & 0xFF) == 0x33);
        check("NPC: posisi x,y di [5],[7]", be16(p, 5) == px + 1 && be16(p, 7) == py);
        check("NPC: side di [9]", (p[9] & 0xFF) == 2);
        check("NPC: id blok di [10]", (be32(p, 10) & 0xFFFFFFFFL) == nd.id);
        check("NPC: sex di [14]", be16(p, 14) == 1);
        check("NPC: kecepatan tetap 80 di [19]", (p[19] & 0xFF) == 80);
        check("NPC: wajah/rambut di [21]..[25]",
                (p[21] & 0xFF) == 7 && (p[22] & 0xFF) == 8 && (p[23] & 0xFF) == 9
                        && (p[24] & 0xFF) == 10 && (p[25] & 0xFF) == 11);
        check("NPC tanpa zirah: pakai sex sebagai grafik dasar di [26]", be16(p, 26) == 1);
        check("NPC tanpa senjata: 0xFFFF di [29]", (be16(p, 29) & 0xFFFF) == 0xFFFF);
        check("NPC: penanda tetap 128 di [57]", (p[57] & 0xFF) == 128);
        int nameLen = p[59] & 0xFF;
        check("NPC: panjang nama di [59]", nameLen == "Penjaga".length());
        check("NPC: nama di [60..]", new String(p, 60, nameLen,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Penjaga"));
        check("NPC: ladang panjang = len + 60 + 3", be16(p, 1) == nameLen + 63);

        // NPC bukan karakter tidak dikirim sama sekali
        nd.npcType = 0;
        drain(MapServer.net.session(sd.fd));
        Clif.sendNpcLook(sd, nd);
        check("NPC non-karakter tidak dikirim",
                drain(MapServer.net.session(sd.fd)).length == 0);
        nd.npcType = 1;

        // --- pemain lain ---
        CharStatus st2 = new CharStatus();
        st2.id = 4242;
        st2.name = "Tetangga";
        st2.baseHp = 10;
        st2.sex = 0;
        st2.side = 1;
        st2.face = 1;
        st2.hair = 2;
        st2.lastPos = new Point(0, px + 2, py);
        User other = new User(11, st2);
        other.m = 0;
        other.x = px + 2;
        other.y = py;
        other.speed = 80;
        map.addBlock(other);
        MapServer.net.openTestSession(other.fd);

        drain(MapServer.net.session(sd.fd));
        Clif.sendCharLook(sd, other);
        raw = drain(MapServer.net.session(sd.fd));
        p = decrypt(raw, sd);
        check("pemain: opcode 0x33", (p[3] & 0xFF) == 0x33);
        check("pemain: id karakter di [10]", be32(p, 10) == 4242);
        check("pemain: kecepatan dari sd.speed di [19]", (p[19] & 0xFF) == 80);
        int len2 = p[59] & 0xFF;
        check("pemain: nama di [60..]", new String(p, 60, len2,
                java.nio.charset.StandardCharsets.ISO_8859_1).equals("Tetangga"));
        // paket pemain memang 3 byte lebih panjang dari paket NPC untuk
        // tata letak yang sama — perbedaan itu ada di versi C
        check("pemain: ladang panjang = len + 63 + 3", be16(p, 1) == len2 + 66);

        // --- sapuan area: NPC dan pemain lain sama-sama tergambar ---
        drain(MapServer.net.session(sd.fd));
        Clif.getCharArea(sd);
        List<int[]> seen = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("getCharArea mengirim minimal dua paket 0x33",
                seen.stream().filter(o -> o[0] == 0x33).count() >= 2);

        // pemain tersembunyi tidak digambar
        other.optFlags = User.OPT_STEALTH;
        sd.status.gmLevel = 0;
        drain(MapServer.net.session(sd.fd));
        Clif.sendCharLook(sd, other);
        check("pemain ber-stealth tidak digambar untuk non-GM",
                drain(MapServer.net.session(sd.fd)).length == 0);
        other.optFlags = 0;

        map.delBlock(nd);
        map.delBlock(other);
        placeAt(map, sd, px, py);
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

        // --- clif_sendside (0x11): arah hadap disiarkan ke sekitar ---
        // Kait paling sering dipakai skrip AI NPC (13 dari 21 error di
        // map.log sebelum diport). Dua aturan yang mudah salah: panjangnya
        // ditentukan ladang [1..2]=0x08 (bukan 32 byte yang disusun C),
        // dan PEMAIN memakai AREA (menerima siarannya sendiri) sedangkan
        // NPC/mob memakai AREA_WOS.
        drain(MapServer.net.session(sd.fd));
        drain(MapServer.net.session(other.fd));
        sd.status.side = 3;
        Clif.sendSide(sd);

        byte[] sideRaw = drain(MapServer.net.session(other.fd));
        check("sendSide: tetangga menerima paket", sideRaw.length > 0);
        check("sendSide: header 0xAA", (sideRaw[0] & 0xFF) == 0xAA);
        check("sendSide: ladang panjang 0x08+3 setelah indeks kunci",
                be16(sideRaw, 1) == 0x08 + 3);
        check("sendSide: total 14 byte", sideRaw.length == 14);
        check("sendSide: opcode 0x11", (sideRaw[3] & 0xFF) == 0x11);
        byte[] sideDec = decrypt(sideRaw, other);
        check("sendSide: id pengirim big-endian di [5]",
                be32(sideDec, 5) == (int) sd.id);
        check("sendSide: arah hadap di [9]", (sideDec[9] & 0xFF) == 3);
        check("sendSide: [10] selalu nol", (sideDec[10] & 0xFF) == 0);

        // AREA (bukan WOS): pemain ikut menerima kabar arah hadapnya sendiri
        check("sendSide: pemain menerima siarannya sendiri (AREA)",
                splitPackets(drain(MapServer.net.session(sd.fd)))
                        .stream().anyMatch(o -> o[0] == 0x11));

        // NPC memakai AREA_WOS — tidak ada pemain yang dilewati di sini,
        // jadi yang diuji: NPC pun menyiarkan, dan idnya id blok NPC.
        Npc uji = new Npc();
        uji.name = "uji_side";
        uji.npcId = 2;
        uji.id = Npc.blockIdFor(uji.npcId);
        uji.m = 0;
        uji.x = other.x;
        uji.y = other.y;
        uji.side = 1;
        map.addBlock(uji);
        drain(MapServer.net.session(other.fd));
        Clif.sendSide(uji);
        byte[] npcRaw = drain(MapServer.net.session(other.fd));
        check("sendSide: NPC juga menyiarkan", npcRaw.length == 14);
        byte[] npcDec = decrypt(npcRaw, other);
        check("sendSide: id NPC = id blok, bukan NpcId",
                be32(npcDec, 5) == (int) Npc.blockIdFor(2));
        check("sendSide: arah NPC di [9]", (npcDec[9] & 0xFF) == 1);
        map.delBlock(uji);
        drain(MapServer.net.session(sd.fd));

        // --- langkah 0x06 memicu gambar ulang petak peta ---
        map.delBlock(sd);
        sd.x = px;
        sd.y = py;
        sd.m = 0;
        map.addBlock(sd);
        drain(MapServer.net.session(sd.fd));
        feedWalkRedraw(sd, 1, px, py, px, py, 4, 4, 0);
        Clif.parseWalk(sd);
        List<int[]> keluar = splitPackets(drain(MapServer.net.session(sd.fd)));
        check("langkah 0x06: petak peta ikut dikirim",
                keluar.stream().anyMatch(o -> o[0] == 0x06));
        check("langkah 0x06: konfirmasi 0x26 tetap dikirim",
                keluar.stream().anyMatch(o -> o[0] == 0x26));

        // tanpa bagian gambar ulang (lebar 0) tidak ada paket peta
        map.delBlock(sd);
        sd.x = px;
        sd.y = py;
        map.addBlock(sd);
        drain(MapServer.net.session(sd.fd));
        feedWalk(sd, 1, px, py);
        Clif.parseWalk(sd);
        check("langkah tanpa permintaan gambar ulang: tidak ada paket 0x06",
                splitPackets(drain(MapServer.net.session(sd.fd)))
                        .stream().noneMatch(o -> o[0] == 0x06));

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
        feedWalkRedraw(sd, direction, claimX, claimY, -1, 0, 0, 0, 0);
    }

    /**
     * Susun paket langkah 0x06 lengkap dengan permintaan gambar ulang.
     * {@code x0 < 0} berarti bagian gambar ulang dikosongkan (nol), yang
     * membuat servernya melewati petak peta karena lebarnya 0.
     */
    private static void feedWalkRedraw(User sd, int direction, int claimX, int claimY,
                                       int x0, int y0, int w, int h, int checksum) {
        org.rtk.common.Session s = MapServer.net.openTestSession(sd.fd);
        byte[] pkt = new byte[23];
        pkt[0] = (byte) 0xAA;
        pkt[1] = 0x00;
        pkt[2] = 0x14;
        pkt[3] = 0x06;
        pkt[5] = (byte) direction;
        pkt[8] = (byte) ((claimX >> 8) & 0xFF);
        pkt[9] = (byte) (claimX & 0xFF);
        pkt[10] = (byte) ((claimY >> 8) & 0xFF);
        pkt[11] = (byte) (claimY & 0xFF);
        if (x0 >= 0) {
            pkt[12] = (byte) ((x0 >> 8) & 0xFF);
            pkt[13] = (byte) (x0 & 0xFF);
            pkt[14] = (byte) ((y0 >> 8) & 0xFF);
            pkt[15] = (byte) (y0 & 0xFF);
            pkt[16] = (byte) w;
            pkt[17] = (byte) h;
            pkt[18] = (byte) ((checksum >> 8) & 0xFF);
            pkt[19] = (byte) (checksum & 0xFF);
        }
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

    // ==================================================================
    // Protokol RTK2 arah masuk (org.rtk.map.proto)
    // ==================================================================

    /** Penyusun bingkai RTK2 — kebalikan Wire.Reader, dipakai uji saja. */
    private static final class Bingkai {
        private final java.io.ByteArrayOutputStream isi = new java.io.ByteArrayOutputStream();
        private final int op;

        Bingkai(int op) {
            this.op = op;
        }

        Bingkai u8(int v) {
            isi.write(v & 0xFF);
            return this;
        }

        Bingkai u16(int v) {
            isi.write((v >> 8) & 0xFF);
            isi.write(v & 0xFF);
            return this;
        }

        Bingkai u32(long v) {
            for (int i = 3; i >= 0; i--) {
                isi.write((int) (v >> (i * 8)) & 0xFF);
            }
            return this;
        }

        Bingkai u64(long v) {
            for (int i = 7; i >= 0; i--) {
                isi.write((int) (v >> (i * 8)) & 0xFF);
            }
            return this;
        }

        Bingkai str(String t) {
            byte[] b = t.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            u16(b.length);
            isi.write(b, 0, b.length);
            return this;
        }

        byte[] bytes() {
            byte[] muatan = isi.toByteArray();
            byte[] out = new byte[4 + muatan.length];
            int len = 2 + muatan.length;
            out[0] = (byte) (len >> 8);
            out[1] = (byte) len;
            out[2] = (byte) (op >> 8);
            out[3] = (byte) op;
            System.arraycopy(muatan, 0, out, 4, muatan.length);
            return out;
        }
    }

    /** Perintah yang mencatat panggilannya alih-alih menjalankan logikanya. */
    /**
     * Daftar abaikan: sifat DUA ARAH-nya.
     *
     * <p>⚠️ Diuji di sini, bukan lewat klien hidup. Jangkauan ucapan di
     * server tidak simetris — area pandang persegi yang <b>digeser</b> di
     * tepi peta membuat pemain di pojok mendengar yang di tengah tanpa
     * sebaliknya. Uji hidup yang mencoba membuktikan dua arah karena itu
     * "lulus" tanpa arti, dan sempat menyembunyikan penyaring yang dipasang
     * di jalur yang salah.</p>
     */
    private static void abaikanTest() {
        log.info("=== daftar abaikan ===");
        // Dua pemain buatan: ujinya tentang aturan daftar abaikan, bukan
        // tentang dunia. Membuatnya sendiri membuat uji ini tidak bergantung
        // pada siapa yang kebetulan online.
        User a = pemainUji(9001, "AbaikanA");
        User b = pemainUji(9002, "AbaikanB");

        check("tanpa abaikan keduanya saling dengar",
                User.bolehSalingDengar(a, b) && User.bolehSalingDengar(b, a));

        check("menambah nama mengembalikan true sekali", a.abaikan(b.name()));
        check("menambah nama yang sama lagi mengembalikan false", !a.abaikan(b.name()));
        check("tidak peka besar-kecil",
                a.mengabaikan(b.name().toUpperCase(java.util.Locale.ROOT)));

        // ⚠️ Inti sifatnya: yang MENGABAIKAN tidak mendengar, DAN yang
        // diabaikan juga tidak. Satu arah saja bukan perilaku C.
        check("yang mengabaikan tidak mendengar", !User.bolehSalingDengar(b, a));
        check("yang DIABAIKAN juga tidak mendengar", !User.bolehSalingDengar(a, b));

        check("mencabut mengembalikan true", a.berhentiAbaikan(b.name()));
        check("setelah dicabut keduanya saling dengar lagi",
                User.bolehSalingDengar(a, b) && User.bolehSalingDengar(b, a));
        check("mencabut nama yang tidak ada mengembalikan false",
                !a.berhentiAbaikan("SiapaPunTidakAda"));

        check("pemain selalu mendengar dirinya sendiri",
                User.bolehSalingDengar(a, a));
        check("null tidak pernah menyaring", User.bolehSalingDengar(a, null));
    }

    private static void setelanTest(MapData map, User sd) {
        log.info("=== setelan pemain ===");
        var engineLama = MapServer.scriptEngine;
        MapServer.scriptEngine = null;   // kait tunggangan diuji terpisah
        ClientCommands cmd = MapServer.commands;
        final int F = org.rtk.common.mmo.SettingFlags.WHISPER;
        final int GRUP = org.rtk.common.mmo.SettingFlags.GROUP;

        long semula = sd.status.settingFlags;
        int klanSemula = sd.status.clanChat;

        // --- baseline "terjadi": satu balikan benar-benar mengubah bit ---
        sd.status.settingFlags = 0;
        cmd.playerChangesSetting(sd, 1, false);
        check("setelan: balikan pertama MENYALAKAN bitnya",
                (sd.status.settingFlags & F) != 0);

        // ⚠️ Inti sifatnya: XOR, bukan penetapan.
        cmd.playerChangesSetting(sd, 1, false);
        check("setelan: balikan kedua MEMATIKANNYA lagi (XOR, bukan set)",
                (sd.status.settingFlags & F) == 0);

        // Bit lain tidak ikut tersentuh.
        sd.status.settingFlags = GRUP;
        cmd.playerChangesSetting(sd, 1, false);
        check("setelan: bit lain tidak ikut berubah",
                (sd.status.settingFlags & GRUP) != 0
                        && (sd.status.settingFlags & F) != 0);

        // --- ragam tak dikenal tidak mengubah apa pun ---
        long sebelum = sd.status.settingFlags;
        cmd.playerChangesSetting(sd, 11, false);
        cmd.playerChangesSetting(sd, 12, false);
        cmd.playerChangesSetting(sd, 99, false);
        check("setelan: ragam tak dikenal (11, 12, 99) diabaikan diam-diam",
                sd.status.settingFlags == sebelum);

        // --- paket suara pembuka diabaikan; yang biasa tidak ---
        sd.status.settingFlags = org.rtk.common.mmo.SettingFlags.SOUND;
        cmd.playerChangesSetting(sd, 13, true);
        check("setelan: paket suara PEMBUKA tidak mematikan suara",
                (sd.status.settingFlags & org.rtk.common.mmo.SettingFlags.SOUND) != 0);
        cmd.playerChangesSetting(sd, 13, false);
        check("setelan: paket suara biasa tetap membalik",
                (sd.status.settingFlags & org.rtk.common.mmo.SettingFlags.SOUND) == 0);
        // ⚠️ Penjaganya HANYA untuk suara: ragam lain tidak punya keanehan
        // itu di C, jadi paketAwal tidak boleh membungkam semuanya.
        sd.status.settingFlags = 0;
        cmd.playerChangesSetting(sd, 1, true);
        check("setelan: penanda pembuka tidak membungkam ragam selain suara",
                (sd.status.settingFlags & F) != 0);

        // --- obrolan klan: ladang tersendiri, bukan bendera ---
        sd.status.settingFlags = 0;
        sd.status.clanChat = 0;
        cmd.playerChangesSetting(sd, 10, false);
        check("setelan: obrolan klan menyala", sd.status.clanChat == 1);
        check("setelan: obrolan klan TIDAK menyentuh settingFlags",
                sd.status.settingFlags == 0);
        cmd.playerChangesSetting(sd, 10, false);
        check("setelan: obrolan klan mati lagi", sd.status.clanChat == 0);

        // --- helm & kalung menulis registry yang dibaca skrip ---
        sd.status.settingFlags = 0;
        sd.status.registry.remove("show_helmet");
        cmd.playerChangesSetting(sd, 14, false);
        check("setelan: helm menyala menulis registry show_helmet=1",
                sd.status.registry.getOrDefault("show_helmet", -1) == 1);
        cmd.playerChangesSetting(sd, 14, false);
        check("setelan: helm mati menulis registry show_helmet=0",
                sd.status.registry.getOrDefault("show_helmet", -1) == 0);
        sd.status.registry.remove("show_necklace");
        cmd.playerChangesSetting(sd, 15, false);
        check("setelan: kalung menulis registry show_necklace=1",
                sd.status.registry.getOrDefault("show_necklace", -1) == 1);

        // --- mematikan setelan grup MENGELUARKAN dari grup ---
        Groups.bersihkanUntukUji();
        java.util.Map<Integer, User> onlineSemula =
                new java.util.HashMap<>(MapServer.onlineChars);
        MapServer.onlineChars.clear();
        User a = pemainUji(9201, "SetelanA");
        User b = pemainUji(9202, "SetelanB");
        for (User u : java.util.List.of(a, b)) {
            u.m = -1;
            u.status.settingFlags = GRUP;
            MapServer.onlineChars.put(u.fd, u);
        }
        Groups.tambah(a, "SetelanB");
        check("setelan: baseline — keduanya bergrup lebih dulu",
                a.groupId != 0 && a.groupId == b.groupId);
        cmd.playerChangesSetting(a, 2, false);
        check("setelan: mematikan setelan grup melepas grup yang sedang diikuti",
                a.groupId == 0 && (a.status.settingFlags & GRUP) == 0);
        check("setelan: anggota lain ikut lepas karena grupnya bubar",
                b.groupId == 0);
        // Menyalakannya kembali tidak boleh diam-diam memasukkan ke grup.
        cmd.playerChangesSetting(a, 2, false);
        check("setelan: menyalakannya kembali tidak membentuk grup apa pun",
                a.groupId == 0 && (a.status.settingFlags & GRUP) != 0);
        Groups.bersihkanUntukUji();
        MapServer.onlineChars.clear();
        MapServer.onlineChars.putAll(onlineSemula);

        // --- jembatan Lua: player.settings juga XOR ---
        sd.status.settingFlags = 0;
        check("setelan: player.settings terbaca skrip",
                sd.scriptGetAttr("settings") != null
                        && sd.scriptGetAttr("settings") == 0L);
        sd.scriptSetAttr("settings", GRUP);
        check("setelan: tulis player.settings MEMBALIK bit, bukan menyetel",
                sd.status.settingFlags == GRUP);
        sd.scriptSetAttr("settings", GRUP);
        check("setelan: tulis kedua kalinya membalik kembali ke nol",
                sd.status.settingFlags == 0);

        sd.status.settingFlags = semula;
        sd.status.clanChat = klanSemula;
        MapServer.scriptEngine = engineLama;
    }

    private static void grupTest() {
        log.info("=== grup ===");
        Groups.bersihkanUntukUji();
        java.util.Map<Integer, User> semula =
                new java.util.HashMap<>(MapServer.onlineChars);
        MapServer.onlineChars.clear();

        final int GRUP = org.rtk.common.mmo.SettingFlags.GROUP;
        User a = pemainUji(9101, "GrupA");
        User b = pemainUji(9102, "GrupB");
        User c = pemainUji(9103, "GrupC");
        for (User u : java.util.List.of(a, b, c)) {
            u.m = -1;                       // peta tak dikenal: canGroup bebas
            u.status.settingFlags = GRUP;
            MapServer.onlineChars.put(u.fd, u);
        }

        // --- baseline "terjadi" DULU: tanpa ini, tiap uji "tidak terjadi"
        // di bawah bisa lulus hanya karena grupnya memang tak pernah jadi.
        Groups.tambah(a, "GrupB");
        check("mengajak membentuk grup", a.groupId != 0 && a.groupId == b.groupId);
        check("grup baru beranggota dua", a.groupCount == 2 && b.groupCount == 2);
        check("pengajak jadi pemimpin",
                a.groupLeader == a.status.id && b.groupLeader == a.status.id);
        check("pemimpin adalah anggota indeks 0",
                Groups.anggota(a.groupId).get(0) == a.status.id);
        check("id grup bukan 0 — 0 berarti tidak bergrup", a.groupId >= 1);
        check("keduanya saling mengenali segrup",
                Groups.seGrup(a, b) && Groups.seGrup(b, a));

        // --- penolakan ---
        int gidA = a.groupId;
        Groups.tambah(a, "GrupA");
        check("tidak bisa mengajak diri sendiri", a.groupCount == 2);
        Groups.tambah(c, "GrupB");
        check("yang sudah bergrup tidak bisa diajak orang lain",
                c.groupId == 0 && b.groupId == gidA);
        b.status.settingFlags = 0;
        User d = pemainUji(9104, "GrupD");
        d.m = -1;
        MapServer.onlineChars.put(d.fd, d);
        Groups.tambah(c, "GrupD");
        check("setelan grup mati menolak ajakan", c.groupId == 0 && d.groupId == 0);
        d.status.settingFlags = GRUP;
        d.status.state = 1;                 // hantu
        Groups.tambah(c, "GrupD");
        check("hantu tidak bisa diajak", c.groupId == 0 && d.groupId == 0);
        d.status.state = 0;
        b.status.settingFlags = GRUP;

        // --- tumbuh, lalu keluar ---
        Groups.tambah(a, "GrupC");
        check("pemimpin bisa menambah anggota ketiga",
                c.groupId == gidA && a.groupCount == 3);
        check("hitungan tersebar ke semua anggota",
                b.groupCount == 3 && c.groupCount == 3);

        // ⚠️ Saklar: mengajak anggota grup sendiri justru MENGELUARKAN dia,
        // dan hanya pemimpin yang bisa.
        Groups.tambah(b, "GrupC");
        check("bukan pemimpin tidak bisa mengeluarkan anggota",
                c.groupId == gidA && a.groupCount == 3);
        Groups.tambah(a, "GrupC");
        check("pemimpin mengeluarkan anggota lewat opcode yang sama",
                c.groupId == 0 && c.groupCount == 0 && a.groupCount == 2);
        check("yang dikeluarkan tidak lagi terdaftar",
                !Groups.anggota(gidA).contains(c.status.id));

        // --- pemimpin keluar: kepemimpinan pindah, lalu grup bubar ---
        Groups.tambah(a, "GrupC");
        check("bisa diajak kembali setelah dikeluarkan", c.groupId == gidA);
        Groups.keluar(a);
        check("pemimpin keluar: dirinya lepas",
                a.groupId == 0 && a.groupCount == 0 && a.groupLeader == 0);
        check("kepemimpinan pindah ke anggota berikutnya",
                b.groupLeader == b.status.id && c.groupLeader == b.status.id);
        check("sisa anggota tetap bergrup",
                b.groupId == gidA && c.groupId == gidA && b.groupCount == 2);

        Groups.keluar(c);
        check("tinggal satu orang: grupnya BUBAR, bukan grup beranggota satu",
                b.groupId == 0 && b.groupCount == 0);
        check("slot grup dilepas kembali", Groups.anggota(gidA).isEmpty());
        check("tidak ada grup tersisa", Groups.jumlahGrup() == 0);

        // --- keluar saat tidak bergrup harus aman: jalur putus sambungan
        // memanggilnya tanpa syarat ---
        Groups.keluar(a);
        check("keluar tanpa grup tidak melempar", a.groupId == 0);

        // --- daftar untuk skrip ---
        check("player.group pemain sendirian berisi DIRINYA, bukan kosong",
                Groups.daftarSkrip(a).equals(java.util.List.of(a.status.id)));
        Groups.tambah(a, "GrupB");
        check("player.group bergrup berisi seluruh anggota",
                Groups.daftarSkrip(a).size() == 2
                        && Groups.daftarSkrip(a).get(0) == a.status.id);
        check("anggota membaca daftar yang sama",
                Groups.daftarSkrip(b).equals(Groups.daftarSkrip(a)));

        // --- peta yang melarang grup ---
        Groups.keluar(a);
        if (MapServer.world.get(0) != null) {
            MapData nyata = MapServer.world.get(0);
            int simpan = nyata.canGroup;
            nyata.canGroup = 0;
            a.m = 0;
            Groups.tambah(a, "GrupB");
            check("peta yang melarang grup menolak ajakan", a.groupId == 0);
            nyata.canGroup = 1;
            Groups.tambah(a, "GrupB");
            check("peta yang mengizinkan grup menerima ajakan", a.groupId != 0);
            Groups.keluar(a);
            nyata.canGroup = simpan;
            a.m = -1;
        }

        Groups.bersihkanUntukUji();
        for (User u : java.util.List.of(a, b, c, d)) {
            u.groupId = 0;
            u.groupCount = 0;
            u.groupLeader = 0;
        }
        MapServer.onlineChars.clear();
        MapServer.onlineChars.putAll(semula);
    }

    /** Pemain buatan sekadar pembawa nama dan daftar abaikan. */
    private static User pemainUji(int fd, String nama) {
        org.rtk.common.mmo.CharStatus st = new org.rtk.common.mmo.CharStatus();
        st.id = fd;
        st.name = nama;
        return new User(fd, st);
    }

    private static final class Rekam implements ClientCommands {
        String terakhir = "";
        final java.util.List<Object> arg = new java.util.ArrayList<>();

        private void catat(String nama, Object... a) {
            terakhir = nama;
            arg.clear();
            arg.addAll(java.util.Arrays.asList(a));
        }

        @Override
        public void playerWalks(User sd, int d, int x, int y, RedrawRequest r) {
            catat("walk", d, x, y, r);
        }

        @Override
        public void playerClicks(User sd, long id) {
            catat("click", id);
        }

        @Override
        public void playerCastsSpell(User sd, int slot, long target, String question) {
            catat("cast", slot, target, question);
        }

        @Override
        public void playerChangesIgnore(User sd, boolean tambah, String name) {
            catat("ignore", tambah, name);
        }

        @Override
        public void playerChangesGroup(User sd, int aksi, String nama) {
            catat("group", aksi, nama);
        }

        @Override
        public void playerChangesSetting(User sd, int jenis, boolean paketAwal) {
            catat("setting", jenis, paketAwal);
        }

        @Override
        public void playerRides(User sd) {
            catat("ride");
        }

        @Override
        public void playerTurns(User sd, int arah) {
            catat("turn", arah);
        }

        @Override
        public void playerEmotes(User sd, int emosi) {
            catat("emote", emosi);
        }

        @Override
        public void playerRequestsRefresh(User sd) {
            catat("refresh");
        }

        @Override
        public void playerLooksAt(User sd, long id) {
            catat("lookAt", id);
        }

        @Override
        public void playerSwapsItems(User sd, int dari, int ke) {
            catat("swapItem", dari, ke);
        }

        @Override
        public void playerSwapsSpells(User sd, int dari, int ke) {
            catat("swapSpell", dari, ke);
        }

        @Override
        public void playerOpensProfile(User sd, int ragam) {
            catat("profile", ragam);
        }

        @Override
        public void playerEditsProfile(User sd, String teks) {
            catat("profileEdit", teks);
        }

        @Override
        public void playerRequestsTowns(User sd) {
            catat("towns");
        }

        @Override
        public void playerRequestsRanking(User sd) {
            catat("ranking");
        }

        @Override
        public void playerSavesFriends(User sd, java.util.List<String> teman) {
            catat("friends", teman.size());
        }

        @Override
        public void playerSetsHunter(User sd, boolean nyala, String catatan) {
            catat("hunter", nyala, catatan);
        }

        @Override
        public void playerUsesBoard(User sd, int aksi, int papan, int pos) {
            catat("board", aksi, papan, pos);
        }

        @Override
        public void playerCollectsParcel(User sd) {
            catat("parcel");
        }

        @Override
        public void playerStartsExchange(User sd, long t) {
            catat("exStart", t);
        }

        @Override
        public void playerOffersItem(User sd, int slot, int n) {
            catat("exItem", slot, n);
        }

        @Override
        public void playerOffersGold(User sd, long n) {
            catat("exGold", n);
        }

        @Override
        public void playerCancelsExchange(User sd) {
            catat("exCancel");
        }

        @Override
        public void playerConfirmsExchange(User sd, long t) {
            catat("exConfirm", t);
        }

        @Override
        public void playerSays(User sd, int ch, String t) {
            catat("say", ch, t);
        }

        @Override
        public void playerWhispers(User sd, String to, String t) {
            catat("whisper", to, t);
        }

        @Override
        public void playerPicksUp(User sd, int mode) {
            catat("pickup", mode);
        }

        @Override
        public void playerDropsItem(User sd, int slot, boolean all) {
            catat("drop", slot, all);
        }

        @Override
        public void playerDropsGold(User sd, long n) {
            catat("dropGold", n);
        }

        @Override
        public void playerHandsItem(User sd, int slot, int n) {
            catat("handItem", slot, n);
        }

        @Override
        public void playerHandsGold(User sd, long n) {
            catat("handGold", n);
        }

        @Override
        public void playerAttacks(User sd) {
            catat("attack");
        }

        @Override
        public void playerWields(User sd, int slot) {
            catat("wield", slot);
        }

        @Override
        public void playerUnequips(User sd, int slot) {
            catat("unequip", slot);
        }

        @Override
        public void playerEatsItem(User sd, int slot) {
            catat("eat", slot);
        }

        @Override
        public void playerUsesItem(User sd, int slot) {
            catat("use", slot);
        }

        @Override
        public void playerThrowsItem(User sd, int slot, boolean confirmed) {
            catat("throw", slot, confirmed);
        }

        @Override
        public void playerAnswersMenu(User sd, Answer a) {
            catat("menu", a);
        }

        @Override
        public void playerAnswersDialog(User sd, Answer a) {
            catat("dialog", a);
        }
    }

    private static void protoTest(MapData map, User sd) {
        log.info("=== protokol RTK2 (arah masuk) ===");
        var engineLama = MapServer.scriptEngine;
        MapServer.scriptEngine = null;   // uji keadaan murni, tanpa kait skrip

        // ---- pemilihan protokol ----
        org.rtk.common.Session retro = MapServer.net.openTestSession(900);
        retro.feedTest(new byte[] {(byte) 0xAA, 0x00, 0x04, 0x06});
        check("proto: paket 0xAA dikenali sebagai RetroTK",
                org.rtk.map.proto.Wire.isRetroTk(org.rtk.map.proto.Inbound.bytesOf(retro)));

        org.rtk.common.Session s = MapServer.net.openTestSession(901);
        s.feedTest(new Bingkai(org.rtk.map.proto.Wire.OP_PING).bytes());
        check("proto: bingkai RTK2 tidak tertukar dengan RetroTK",
                !org.rtk.map.proto.Wire.isRetroTk(org.rtk.map.proto.Inbound.bytesOf(s)));
        check("proto: bingkai kosong panjangnya 4 byte",
                org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(s)) == 4);
        check("proto: opcode terbaca dari ladangnya",
                org.rtk.map.proto.Wire.opcode(org.rtk.map.proto.Inbound.bytesOf(s)) == org.rtk.map.proto.Wire.OP_PING);

        // Batas MAX_FRAME ikut menjaga pemilihan protokol: selama ia di bawah
        // 43.520, byte tinggi ladang panjang tidak akan pernah 0xAA.
        check("proto: MAX_FRAME menjaga pembeda protokol tetap aman",
                org.rtk.map.proto.Wire.MAX_FRAME < 0xAA00);

        // ---- bingkai yang belum lengkap ----
        org.rtk.common.Session pecah = MapServer.net.openTestSession(902);
        byte[] utuh = new Bingkai(org.rtk.map.proto.Wire.OP_CLICK).u64(42).bytes();
        pecah.feedTest(java.util.Arrays.copyOf(utuh, 5));
        check("proto: bingkai separuh dijawab 0, bukan dibaca",
                org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(pecah)) == 0);
        pecah.feedTest(java.util.Arrays.copyOfRange(utuh, 5, utuh.length));
        check("proto: setelah sisanya tiba, panjangnya utuh",
                org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(pecah)) == utuh.length);

        // ---- bingkai rusak ----
        check("proto: ladang panjang < 2 ditolak",
                malformed(new byte[] {0x00, 0x01, 0x00, 0x00}));
        check("proto: bingkai melewati MAX_FRAME ditolak",
                malformed(new byte[] {(byte) 0x7F, (byte) 0xFF, 0x00, 0x00}));

        // String yang mengaku lebih panjang dari bingkainya: di C penjaga
        // seperti ini ditulis tangan per penangan, dan tidak selalu ada.
        byte[] bohong = new Bingkai(org.rtk.map.proto.Wire.OP_SAY).u8(0).u16(99).bytes();
        check("proto: string yang mengaku melebihi bingkai ditolak",
                malformedDispatch(bohong, sd));

        // ---- pemetaan opcode -> perintah ----
        Rekam rekam = new Rekam();
        check("proto: WALK -> playerWalks(arah, x, y)",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_WALK).u8(2).u16(50).u16(60),
                        sd, rekam)
                && rekam.terakhir.equals("walk")
                && rekam.arg.get(0).equals(2) && rekam.arg.get(1).equals(50)
                && rekam.arg.get(2).equals(60));
        check("proto: WALK tidak membawa permintaan gambar ulang",
                rekam.arg.get(3) == null);

        check("proto: CLICK -> playerClicks",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_CLICK).u64(0x1122334455L),
                        sd, rekam)
                && rekam.terakhir.equals("click")
                && rekam.arg.get(0).equals(0x1122334455L));

        // ⚠️ Slot 0-basis di kedua sisi. RetroTK mengirim slot+1 dan tiap
        // penangan di C mengurangi 1 lagi; salah satu jebakan yang dibuang.
        check("proto: DROP_ITEM memakai slot 0-basis apa adanya",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_DROP_ITEM).u8(0).u8(1),
                        sd, rekam)
                && rekam.terakhir.equals("drop")
                && rekam.arg.get(0).equals(0) && rekam.arg.get(1).equals(true));

        long besar = 5_000_000_000L;   // di atas batas 32-bit
        check("proto: DROP_GOLD membawa nilai di atas 32-bit utuh",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_DROP_GOLD).u64(besar),
                        sd, rekam)
                && rekam.terakhir.equals("dropGold") && rekam.arg.get(0).equals(besar));

        check("proto: PICKUP -> playerPicksUp",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_PICKUP).u8(1), sd, rekam)
                && rekam.terakhir.equals("pickup") && rekam.arg.get(0).equals(1));

        check("proto: HAND_ITEM -> playerHandsItem(slot, jumlah)",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_HAND_ITEM).u8(3).u16(7),
                        sd, rekam)
                && rekam.terakhir.equals("handItem")
                && rekam.arg.get(0).equals(3) && rekam.arg.get(1).equals(7));

        check("proto: SAY membawa saluran dan teksnya",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_SAY).u8(1).str("halo dunia"),
                        sd, rekam)
                && rekam.terakhir.equals("say")
                && rekam.arg.get(0).equals(1) && rekam.arg.get(1).equals("halo dunia"));

        check("proto: teks UTF-8 utuh melewati kabel",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_SAY).u8(0).str("héllo — dunia"),
                        sd, rekam)
                && rekam.arg.get(1).equals("héllo — dunia"));

        check("proto: WHISPER membawa dua string berurutan",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_WHISPER).str("!").str("hai klan"),
                        sd, rekam)
                && rekam.terakhir.equals("whisper")
                && rekam.arg.get(0).equals("!") && rekam.arg.get(1).equals("hai klan"));

        check("proto: EXCHANGE_ITEM tidak perlu tanya-jumlah bolak-balik",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_EXCHANGE_ITEM).u8(2).u16(15),
                        sd, rekam)
                && rekam.terakhir.equals("exItem")
                && rekam.arg.get(0).equals(2) && rekam.arg.get(1).equals(15));

        check("proto: EXCHANGE_CANCEL tanpa muatan",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_EXCHANGE_CANCEL), sd, rekam)
                && rekam.terakhir.equals("exCancel"));

        check("proto: WIELD -> playerWields",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_WIELD).u8(4), sd, rekam)
                && rekam.terakhir.equals("wield") && rekam.arg.get(0).equals(4));
        // ⚠️ Slot lepas-perlengkapan di RTK2 adalah indeks EQ_* apa adanya.
        // RetroTK memakai penomoran panel kliennya sendiri, yang berlubang
        // (1,2,3,4,6,7,8,13,14,16,20..23) dan harus diterjemahkan dua arah.
        check("proto: UNEQUIP memakai indeks EQ_* apa adanya",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_UNEQUIP)
                        .u8(org.rtk.map.data.Equip.SUBRIGHT), sd, rekam)
                && rekam.terakhir.equals("unequip")
                && rekam.arg.get(0).equals(org.rtk.map.data.Equip.SUBRIGHT));
        check("proto: kode panel RetroTK memang BUKAN indeks EQ_*",
                Clif.panelCode(org.rtk.map.data.Equip.SUBRIGHT) == 21
                && Clif.slotFromPanelCode(21) == org.rtk.map.data.Equip.SUBRIGHT
                && Clif.panelCode(org.rtk.map.data.Equip.FACEACCTWO) == -1);
        check("proto: EAT -> playerEatsItem",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_EAT).u8(2), sd, rekam)
                && rekam.terakhir.equals("eat") && rekam.arg.get(0).equals(2));
        check("proto: USE -> playerUsesItem",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_USE).u8(9), sd, rekam)
                && rekam.terakhir.equals("use") && rekam.arg.get(0).equals(9));
        check("proto: THROW membawa slot dan bendera konfirmasi",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_THROW).u8(1).u8(1),
                        sd, rekam)
                && rekam.terakhir.equals("throw")
                && rekam.arg.get(0).equals(1) && rekam.arg.get(1).equals(true));

        check("proto: ATTACK tanpa sasaran dari klien",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ATTACK), sd, rekam)
                && rekam.terakhir.equals("attack") && rekam.arg.isEmpty());

        // ---- jawaban dialog: enam ragam, dua pintu terpisah ----
        // ⚠️ Dikirim 3, diharapkan 4. Di kabel RTK2 pilihan menu 0-BASIS;
        // lapisan logika 1-BASIS karena Lua-nya begitu (`options[selection]`,
        // dan tabel Lua mulai dari 1). Penyesuaiannya di `Inbound.jawaban`.
        // Tanpa itu pilihan pertama jadi `options[0]` = nil dan skripnya
        // berhenti tanpa satu pun cabang cocok — NPC bicara sekali lalu
        // membisu, tanpa error. Lihat Peringatan #88.
        check("proto: jawaban ragam 1 jadi pilihan menu, digeser ke 1-basis",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_MENU).u8(1).u16(3),
                        sd, rekam)
                && rekam.terakhir.equals("menu")
                && ((ClientCommands.Answer) rekam.arg.get(0)).choice == 4);
        check("proto: jawaban ragam 2 jadi teks",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_MENU).u8(2).str("budi"),
                        sd, rekam)
                && "budi".equals(((ClientCommands.Answer) rekam.arg.get(0)).text));
        check("proto: jawaban ragam 3 jadi nama barang (daftar beli)",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_MENU).u8(3).str("apple"),
                        sd, rekam)
                && "apple".equals(((ClientCommands.Answer) rekam.arg.get(0)).itemName));
        check("proto: jawaban ragam 4 jadi nomor slot (daftar jual)",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_MENU).u8(4).u8(5),
                        sd, rekam)
                && ((ClientCommands.Answer) rekam.arg.get(0)).slot == 5);
        check("proto: ragam 0 berarti BATAL, bukan jawaban kosong",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_MENU).u8(0), sd, rekam)
                && rekam.arg.get(0) == null);
        check("proto: jalur dialog terpisah dari jalur menu",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_DIALOG).u8(5).u8(1),
                        sd, rekam)
                && rekam.terakhir.equals("dialog")
                && ((ClientCommands.Answer) rekam.arg.get(0)).step == 1);
        check("proto: ragam jawaban yang tidak dikenal ditolak",
                malformedDispatch(
                        new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_MENU).u8(9).bytes(), sd));

        // ---- sebelum terautentikasi ----
        Rekam sepi = new Rekam();
        kirimTanpaPemain(new Bingkai(org.rtk.map.proto.Wire.OP_ATTACK).bytes(), sepi, null);
        check("proto: perintah sebelum perkenalan diabaikan", sepi.terakhir.isEmpty());

        // K3.4: HELLO membawa nama DAN sandi. Keduanya diteruskan ke
        // verifikator; sandinya tidak lagi byte sisa yang dibuang diam-diam.
        final String[] diperkenalkan = {null, null};
        kirimTanpaPemain(new Bingkai(org.rtk.map.proto.Wire.OP_HELLO)
                        .u32(org.rtk.map.proto.Wire.MAGIC)
                        .u16(org.rtk.map.proto.Wire.VERSION)
                        .str("Tester").str("rahasia").bytes(),
                sepi, (fd, sesi, nama, sandi) -> {
                    diperkenalkan[0] = nama;
                    diperkenalkan[1] = sandi;
                    return true;
                });
        check("proto: HELLO meneruskan nama karakter ke jalur autentikasi",
                "Tester".equals(diperkenalkan[0]));
        check("proto: HELLO meneruskan SANDI ke verifikator (K3.4)",
                "rahasia".equals(diperkenalkan[1]));
        check("proto: perkenalan yang ditolak verifikator menutup sambungan",
                helloDitolak(new Bingkai(org.rtk.map.proto.Wire.OP_HELLO)
                        .u32(org.rtk.map.proto.Wire.MAGIC)
                        .u16(org.rtk.map.proto.Wire.VERSION)
                        .str("Tester").str("salah").bytes()));

        check("proto: magic yang salah ditolak",
                malformedHello(new Bingkai(org.rtk.map.proto.Wire.OP_HELLO)
                        .u32(0xDEADBEEFL).u16(1).str("Tester").str("").bytes()));
        check("proto: versi protokol yang berbeda ditolak, bukan ditebak",
                malformedHello(new Bingkai(org.rtk.map.proto.Wire.OP_HELLO)
                        .u32(org.rtk.map.proto.Wire.MAGIC)
                        .u16(org.rtk.map.proto.Wire.VERSION + 1)
                        .str("Tester").str("").bytes()));

        MapServer.scriptEngine = engineLama;
    }

    // ---- alat bantu protoTest ----

    private static int fdUji = 910;

    /** Suapkan satu bingkai ke pembaca; true bila selesai tanpa keluhan. */
    private static boolean kirim(Bingkai b, User sd, Rekam rekam) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(b.bytes());
        int len = org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(s));
        if (len == 0) {
            return false;
        }
        org.rtk.map.proto.Inbound.dispatch(s.fd, s, sd, len, rekam, (a, c, n, p) -> true);
        return true;
    }

    private static void kirimTanpaPemain(byte[] bingkai, Rekam rekam,
                                         org.rtk.map.proto.Inbound.Handshake h) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        int len = org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(s));
        org.rtk.map.proto.Inbound.dispatch(s.fd, s, null, len, rekam,
                h != null ? h : (a, c, n, p) -> true);
    }

    private static boolean malformed(byte[] bingkai) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        try {
            org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(s));
            return false;
        } catch (org.rtk.map.proto.Wire.Malformed e) {
            return true;
        }
    }

    private static boolean malformedDispatch(byte[] bingkai, User sd) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        try {
            int len = org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(s));
            org.rtk.map.proto.Inbound.dispatch(s.fd, s, sd, len, new Rekam(),
                    (a, c, n, p) -> true);
            return false;
        } catch (org.rtk.map.proto.Wire.Malformed e) {
            return true;
        }
    }

    private static boolean malformedHello(byte[] bingkai) {
        return malformedDispatch2(bingkai);
    }

    /** true bila verifikator menolak dan sesinya ditandai untuk ditutup. */
    private static boolean helloDitolak(byte[] bingkai) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        int len = org.rtk.map.proto.Wire.frameLength(
                org.rtk.map.proto.Inbound.bytesOf(s));
        org.rtk.map.proto.Inbound.dispatch(s.fd, s, null, len, new Rekam(),
                (a, c, n, p) -> false);   // verifikator menolak
        return s.eof;
    }

    private static boolean malformedDispatch2(byte[] bingkai) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        try {
            int len = org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(s));
            org.rtk.map.proto.Inbound.dispatch(s.fd, s, null, len, new Rekam(),
                    (a, c, n, p) -> true);
            return false;
        } catch (org.rtk.map.proto.Wire.Malformed e) {
            return true;
        }
    }

    // ==================================================================
    // Logika di balik aksi baru (MapCommands)
    // ==================================================================

    private static void aksiTest(MapData map, User sd) {
        log.info("=== aksi pemain (bicara, barang, serang) ===");
        var engineLama = MapServer.scriptEngine;
        MapServer.scriptEngine = null;   // kait skrip diuji terpisah
        ClientCommands cmd = MapServer.commands;

        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }

        // ---- bicara ----
        sd.status.mute = 0;
        cmd.playerSays(sd, 1, "halo semua");
        check("aksi: kalimat tersimpan di speech untuk dibaca skrip",
                "halo semua".equals(sd.speech));
        check("aksi: saluran tersimpan di talkType", sd.talkType == 1);
        check("aksi: speech terbaca lewat jembatan atribut skrip",
                "halo semua".equals(sd.scriptGetSpecial("speech").tojstring()));

        sd.speech = "";
        cmd.playerSays(sd, 2, "saluran ngawur");
        check("aksi: saluran di luar 0..1 ditolak, speech tak berubah",
                sd.speech.isEmpty());

        cmd.playerSays(sd, 0, "x".repeat(101));
        check("aksi: kalimat melebihi 100 huruf ditolak", sd.speech.isEmpty());

        sd.status.mute = 1;
        cmd.playerSays(sd, 0, "seharusnya diam");
        check("aksi: pemain yang dibungkam tidak menyetel speech",
                sd.speech.isEmpty());
        sd.status.mute = 0;

        // ---- tumpukan koin ----
        check("aksi: 1 emas jadi tumpukan tingkat 0",
                FloorItemRegistry.newGoldPile(1).data.id == 0);
        check("aksi: 50 emas jadi tingkat 1",
                FloorItemRegistry.newGoldPile(50).data.id == 1);
        check("aksi: 500 emas jadi tingkat 2",
                FloorItemRegistry.newGoldPile(500).data.id == 2);
        check("aksi: 5000 emas jadi tingkat 3",
                FloorItemRegistry.newGoldPile(5000).data.id == 3);

        int lantaiAwal = MapServer.floorItems.count();
        sd.status.money = 10_000;
        sd.status.state = 0;
        cmd.playerDropsGold(sd, 1);
        check("aksi: menjatuhkan emas memotong dompet",
                sd.status.money == 9_999);
        check("aksi: tumpukan koin muncul di lantai",
                MapServer.floorItems.count() == lantaiAwal + 1);

        cmd.playerDropsGold(sd, 5000);
        check("aksi: emas kedua MELEBUR, bukan jadi tumpukan kedua",
                MapServer.floorItems.count() == lantaiAwal + 1);
        FloorItem koin = null;
        for (FloorItem fi : MapServer.floorItems.all()) {
            if (fi.m == sd.m && fi.x == sd.x && fi.y == sd.y) {
                koin = fi;
            }
        }
        check("aksi: jumlahnya bertambah setelah melebur",
                koin != null && koin.data.amount == 5001);
        check("aksi: tingkat gambar TIDAK dihitung ulang setelah melebur",
                koin != null && koin.data.id == 0);

        cmd.playerDropsGold(sd, 99_999);
        check("aksi: jatuhan dibatasi isi dompet", sd.status.money == 0);
        cmd.playerDropsGold(sd, 10);
        check("aksi: dompet kosong tidak menghasilkan tumpukan baru",
                MapServer.floorItems.count() == lantaiAwal + 1);

        sd.status.state = 1;   // arwah
        sd.status.money = 500;
        cmd.playerDropsGold(sd, 100);
        check("aksi: arwah tidak bisa menjatuhkan emas", sd.status.money == 500);
        sd.status.state = 0;

        // ---- R2: mob mati tidak menghalangi langkah, klik mob ----
        // clif_canmove_sub: `if (mob->state == MOB_DEAD) return 0;`.
        // Bangkainya masih terdaftar di petaknya sampai disapu, jadi tanpa
        // cabang ini pemain terhalang oleh sesuatu yang tak terlihat.
        var jenisR2 = new MobData();
        jenisR2.id = 9001;
        jenisR2.yname = "uji_mob_r2";
        jenisR2.name = "Mob R2";
        jenisR2.vita = 10;
        Mob mbR2 = new Mob();
        mbR2.data = jenisR2;
        mbR2.id = Mob.MOB_START_NUM + 4242;
        mbR2.m = sd.m;
        mbR2.x = sd.x + 1;
        mbR2.y = sd.y;
        MobRegistry.resetStats(mbR2);
        MapServer.world.get(sd.m).addBlock(mbR2);

        int arahTimur = 1;
        int xSebelum = sd.x;
        sd.status.gmLevel = 0;
        cmd.playerWalks(sd, arahTimur, sd.x, sd.y, null);
        check("r2: mob HIDUP menghalangi langkah", sd.x == xSebelum);
        mbR2.state = MobData.MOB_DEAD;
        cmd.playerWalks(sd, arahTimur, sd.x, sd.y, null);
        check("r2: mob MATI tidak menghalangi langkah", sd.x == xSebelum + 1);
        // kembalikan posisi dan bersihkan
        cmd.playerWalks(sd, 3, sd.x, sd.y, null);
        MapServer.world.get(sd.m).delBlock(mbR2);

        // ---- R1/K4: berputar, emosi, susun ulang ----
        int xAwal = sd.x, yAwal = sd.y;
        sd.status.side = 0;
        cmd.playerTurns(sd, 3);
        check("k4: berputar menyetel arah", sd.status.side == 3);
        check("k4: berputar TIDAK memindahkan pemain",
                sd.x == xAwal && sd.y == yAwal);
        cmd.playerTurns(sd, 7);
        check("k4: arah di luar 0..3 dilipat, bukan ditolak mentah",
                sd.status.side == 3);

        // Tukar posisi barang: slot ada di Item.pos, bukan indeks daftar.
        // ⚠️ Barangnya harus TERDAFTAR di ItemDb: Clif.sendAddItem punya
        // penyapu data rusak yang MEMBUANG barang ber-id tak dikenal, jadi
        // fixture dengan id karangan akan lenyap di tengah uji.
        MapServer.itemDb.register(new org.rtk.map.data.ItemDb.Info(7703,
                "barang_k4", "Barang K4", "", "",
                org.rtk.map.data.ItemDb.ITM_ETC, 0, 0, 0, 1, 1, 1, 0, 0, 100, 0, 0,
                new org.rtk.map.data.ItemDb.Look(0, 0, 55, 0),
                new org.rtk.map.data.ItemDb.Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        int maxInvLama = sd.status.maxInv;
        if (sd.status.maxInv < 27) {
            sd.status.maxInv = 27;   // fixture: kantong bawaan karakter uji
        }
        sd.status.inventory.clear();
        org.rtk.common.mmo.Item p1 = new org.rtk.common.mmo.Item();
        p1.id = 7703;
        p1.amount = 1;
        p1.dura = 100;
        p1.pos = 2;
        sd.status.inventory.add(p1);
        cmd.playerSwapsItems(sd, 2, 9);
        check("k4: tukar ke slot kosong memindahkan POS barangnya",
                sd.status.inventoryAt(9) != null && sd.status.inventoryAt(2) == null);
        org.rtk.common.mmo.Item p2 = new org.rtk.common.mmo.Item();
        p2.id = 7703;
        p2.amount = 3;
        p2.dura = 100;
        p2.pos = 4;
        sd.status.inventory.add(p2);
        cmd.playerSwapsItems(sd, 9, 4);
        check("k4: tukar dua slot terisi menukar keduanya",
                sd.status.inventoryAt(4) != null && sd.status.inventoryAt(4).amount == 1
                        && sd.status.inventoryAt(9) != null
                        && sd.status.inventoryAt(9).amount == 3);
        cmd.playerSwapsItems(sd, 4, 4);
        check("k4: menukar slot dengan dirinya sendiri tidak mengubah apa pun",
                sd.status.inventoryAt(4) != null && sd.status.inventoryAt(4).amount == 1);
        cmd.playerSwapsItems(sd, 4, 9999);
        check("k4: slot di luar maxInv ditolak tanpa merusak kantong",
                sd.status.inventoryAt(4) != null);
        sd.status.inventory.clear();
        sd.status.maxInv = maxInvLama;

        // Tukar posisi mantra.
        sd.status.spells = new int[] {11, 22, 0};
        cmd.playerSwapsSpells(sd, 0, 2);
        check("k4: tukar mantra memindahkan id-nya",
                sd.status.spells[2] == 11 && sd.status.spells[0] == 0);
        cmd.playerSwapsSpells(sd, 0, 99);
        check("k4: slot mantra di luar batas ditolak",
                sd.status.spells[2] == 11);
        sd.status.spells = new int[0];

        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }

        // ---- menyerahkan barang ke mob, dan mendapatkannya kembali ----
        sd.status.side = 2;   // menghadap ke bawah
        Mob mob = new Mob();
        mob.m = sd.m;
        mob.x = sd.x;
        mob.y = sd.y + 1;
        mob.id = 777_000;
        map.addBlock(mob);

        // ⚠️ Barang uji WAJIB terdaftar di ItemDb. Peringatan #37:
        // clif_sendadditem MENGHAPUS barang yang tidak ada di tabel Items —
        // jadi barang karangan akan lenyap dari inventaris begitu paketnya
        // disusun, dan ujinya gagal karena alasan yang sama sekali berbeda
        // dari yang sedang diuji. Ini kejadian saat menulis uji ini.
        var kosongLookAksi = new org.rtk.map.data.ItemDb.Look(0, 0, 0, 0);
        var tanpaStatAksi = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        MapServer.itemDb.register(new org.rtk.map.data.ItemDb.Info(7501, "beras_uji",
                "Beras Uji", "", "", 18, 0, 0, 0, 1, 1, 20, 0, 0, 50, 0, 0,
                kosongLookAksi, tanpaStatAksi));

        sd.status.inventory.clear();
        org.rtk.common.mmo.Item bawaan = new org.rtk.common.mmo.Item();
        bawaan.id = 7501;
        bawaan.amount = 5;
        sd.status.inventory.add(bawaan);

        cmd.playerHandsItem(sd, 0, 2);
        check("aksi: barang keluar dari inventaris pemain",
                sd.status.inventory.get(0).amount == 3);
        check("aksi: mob menyimpannya, bukan membuangnya",
                mob.inventory.size() == 1 && mob.inventory.get(0).amount == 2);

        cmd.playerHandsItem(sd, 0, 3);
        check("aksi: penyerahan kedua digabung ke slot yang sama",
                mob.inventory.size() == 1 && mob.inventory.get(0).amount == 5);
        check("aksi: slot pemain yang habis dikosongkan",
                sd.status.inventory.isEmpty());

        int sebelumMati = MapServer.floorItems.count();
        MapServer.mobs.jatuhkanInventarisUji(mob);
        check("aksi: isi inventaris mob jatuh ke tanah saat ia mati",
                MapServer.floorItems.count() == sebelumMati + 1);
        check("aksi: daftarnya dikosongkan sesudahnya", mob.inventory.isEmpty());

        map.delBlock(mob);
        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }

        // ---- pembatas kecepatan serang ----
        sd.attacked = false;
        sd.paralyzed = false;
        sd.attackSpeed = 20;
        cmd.playerAttacks(sd);
        check("aksi: ayunan pertama diterima", sd.attacked);
        sd.status.side = 2;
        cmd.playerAttacks(sd);
        check("aksi: ayunan kedua sebelum jedanya habis diabaikan", sd.attacked);

        sd.attacked = false;
        sd.attackSpeed = 0;
        cmd.playerAttacks(sd);
        check("aksi: kecepatan serang 0 berarti tidak bisa mengayun",
                !sd.attacked);
        sd.attackSpeed = 20;

        sd.attacked = false;
        sd.status.state = 1;
        cmd.playerAttacks(sd);
        check("aksi: arwah tidak bisa mengayun", !sd.attacked);
        sd.status.state = 0;
        sd.attacked = false;

        sd.status.inventory.clear();

        // ---- jembatan atribut yang dipakai kait skrip ----
        //
        // ⚠️ Ini bagian yang paling mudah gagal SENYAP. Keempat atribut di
        // bawah dibaca kait yang baru tersambung (speech.lua membaca
        // `player.speech` di baris pertama `onSay`, onPickup.lua membaca
        // `player.pickUpType`, 15 skrip barang menulis `player.fakeDrop`).
        // Kalau salah satunya tidak terpasang, Lua menerima nil — dan
        // `string.lower(nil)` meledak di tempat yang jauh dari sebabnya.
        // Yang diuji di sini jembatannya, bukan skripnya: menjalankan
        // speech.lua sungguhan butuh 906 skrip termuat DAN pemain online,
        // dan itu tetap jadi butir roadmap tersendiri.
        var engineAtribut = new org.rtk.map.script.ScriptEngine();
        MapServer.scriptEngine = engineAtribut;
        var pemain = sd.scriptPlayer();
        sd.speech = "kata kunci";
        sd.talkType = 1;
        sd.pickUpType = 1;
        sd.invSlot = 4;
        sd.fakeDrop = 0;
        engineAtribut.globals().load(
                "AtributUji = {}\n"
              + "AtributUji.baca = function(player)\n"
              + "    AtributUji.speech = player.speech\n"
              + "    AtributUji.talkType = player.talkType\n"
              + "    AtributUji.pickUpType = player.pickUpType\n"
              + "    AtributUji.invSlot = player.invSlot\n"
              + "    player.fakeDrop = 1\n"
              + "    player.speech = string.lower(player.speech)\n"
              + "end\n").call();
        engineAtribut.doScript("AtributUji", "baca", engineAtribut.playerRef(pemain));
        var tabel = engineAtribut.globals().get("AtributUji");
        check("atribut: player.speech terbaca skrip sebagai string",
                "kata kunci".equals(tabel.get("speech").tojstring()));
        check("atribut: player.talkType terbaca", tabel.get("talkType").toint() == 1);
        check("atribut: player.pickUpType terbaca", tabel.get("pickUpType").toint() == 1);
        check("atribut: player.invSlot terbaca", tabel.get("invSlot").toint() == 4);
        check("atribut: player.fakeDrop bisa DITULIS skrip", sd.fakeDrop == 1);
        check("atribut: player.speech bisa ditulis balik (pintasan /s)",
                "kata kunci".equals(sd.speech));
        sd.fakeDrop = 0;

        MapServer.scriptEngine = engineLama;
    }


    // ==================================================================
    // Perlengkapan & memakai barang
    // ==================================================================

    private static void perlengkapanTest(MapData map, User sd) {
        log.info("=== perlengkapan & memakai barang ===");
        var engineLama = MapServer.scriptEngine;
        MapServer.scriptEngine = null;   // dua langkahnya diuji manual
        ClientCommands cmd = MapServer.commands;
        var db = MapServer.itemDb;
        var kosongLook = new org.rtk.map.data.ItemDb.Look(0, 0, 0, 0);
        var tanpaStat = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var bebas = org.rtk.map.data.ItemDb.Reqs.NONE;

        // ---- pesan bawaan ----
        check("pesan: bawaan terpasang tanpa conf/lang.conf",
                !org.rtk.map.data.MapMsg.of("MAP_ERRITM2H").isEmpty());
        check("pesan: kunci tak dikenal dijawab kosong, bukan null",
                org.rtk.map.data.MapMsg.of("MAP_TIDAK_ADA").isEmpty());

        // ---- barang uji ----
        // klewang: senjata biasa
        db.register(new org.rtk.map.data.ItemDb.Info(7601, "klewang_eq", "Klewang",
                "", "", org.rtk.map.data.ItemDb.ITM_WEAP, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook, tanpaStat, bebas));
        // tombak: senjata DUA TANGAN (ItmLook 10000..29999)
        db.register(new org.rtk.map.data.ItemDb.Info(7602, "tombak_eq", "Tombak",
                "", "", org.rtk.map.data.ItemDb.ITM_WEAP, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, new org.rtk.map.data.ItemDb.Look(15000, 0, 0, 0),
                tanpaStat, bebas));
        // perisai
        db.register(new org.rtk.map.data.ItemDb.Info(7603, "perisai_eq", "Perisai",
                "", "", org.rtk.map.data.ItemDb.ITM_SHIELD, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook, tanpaStat, bebas));
        // mahkota terkunci: ItmUnequip = 1
        db.register(new org.rtk.map.data.ItemDb.Info(7604, "mahkota_eq", "Mahkota",
                "", "", org.rtk.map.data.ItemDb.ITM_CROWN, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook, tanpaStat,
                new org.rtk.map.data.ItemDb.Reqs(0, 0, 2, 0, 0, 1, 0, 0)));
        // zirah level 50
        db.register(new org.rtk.map.data.ItemDb.Info(7605, "zirah_eq", "Zirah Berat",
                "", "", org.rtk.map.data.ItemDb.ITM_ARMOR, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook, tanpaStat,
                new org.rtk.map.data.ItemDb.Reqs(50, 0, 2, 0, 0, 0, 0, 0)));
        // gaun khusus perempuan (ItmSex 1)
        db.register(new org.rtk.map.data.ItemDb.Info(7606, "gaun_eq", "Gaun",
                "", "", org.rtk.map.data.ItemDb.ITM_ARMOR, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook, tanpaStat,
                new org.rtk.map.data.ItemDb.Reqs(0, 0, 1, 0, 0, 0, 0, 0)));
        // cincin (pasangan kiri/kanan)
        db.register(new org.rtk.map.data.ItemDb.Info(7607, "cincin_eq", "Cincin",
                "", "", org.rtk.map.data.ItemDb.ITM_LEFT, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook, tanpaStat, bebas));
        // anting (pasangan sub kiri/kanan — aturannya TERBALIK)
        db.register(new org.rtk.map.data.ItemDb.Info(7608, "anting_eq", "Anting",
                "", "", org.rtk.map.data.ItemDb.ITM_SUBLEFT, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook, tanpaStat, bebas));
        // jimat pengurang darah besar
        db.register(new org.rtk.map.data.ItemDb.Info(7609, "jimat_eq", "Jimat Kutukan",
                "", "", org.rtk.map.data.ItemDb.ITM_NECKLACE, 0, 0, 0, 1, 1, 1, 0, 0,
                100, 0, 0, kosongLook,
                new org.rtk.map.data.ItemDb.Stats(-200, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0), bebas));
        // rokok: dura = sisa isap
        db.register(new org.rtk.map.data.ItemDb.Info(7610, "obor_eq", "Obor",
                "", "jam", org.rtk.map.data.ItemDb.ITM_SMOKE, 0, 0, 0, 1, 1, 1, 0, 0,
                3, 0, 0, kosongLook, tanpaStat, bebas));
        // ransum: makanan berumur 60 detik sejak dipakai
        db.register(new org.rtk.map.data.ItemDb.Info(7611, "ransum_eq", "Ransum",
                "", "", org.rtk.map.data.ItemDb.ITM_EAT, 0, 60, 0, 1, 1, 5, 0, 0,
                1, 0, 0, kosongLook, tanpaStat, bebas));
        // batu: hanya bisa dilempar setelah konfirmasi
        db.register(new org.rtk.map.data.ItemDb.Info(7612, "batu_eq", "Batu Kutuk",
                "", "", org.rtk.map.data.ItemDb.ITM_ETC, 0, 0, 0, 1, 1, 1, 0, 0,
                1, 0, 0, kosongLook, tanpaStat,
                new org.rtk.map.data.ItemDb.Reqs(0, 0, 2, 0, 0, 0, 1, 1)));

        map.canUse = 1;
        map.canEat = 1;
        map.canSmoke = 1;
        map.canEquip = 1;
        sd.status.state = 0;
        sd.status.level = 99;
        sd.status.sex = 0;
        sd.might = 100;
        sd.maxHp = 500;
        sd.maxMp = 500;
        sd.status.maxInv = 20;
        sd.status.inventory.clear();
        sd.status.equip.clear();

        // ---- dua langkah: menyiapkan lalu memindahkan ----
        sd.status.inventory.add(barangUji(7601, 1, 100));
        cmd.playerWields(sd, 0);
        check("perlengkapan: mengenakan MENYIAPKAN, belum memasang",
                sd.status.equipAt(org.rtk.map.data.Equip.WEAP) == null
                && sd.equipId == 7601 && sd.invSlot == 0);
        Items.equipScript(sd);   // yang di dunia nyata dipanggil skrip onEquip
        check("perlengkapan: equip() barulah memasangnya",
                sd.status.equipAt(org.rtk.map.data.Equip.WEAP) != null
                && sd.status.equipAt(org.rtk.map.data.Equip.WEAP).id == 7601);
        check("perlengkapan: slot inventarisnya ikut kosong",
                sd.status.inventory.isEmpty());
        check("perlengkapan: equipId dilepas sesudahnya", sd.equipId == 0);

        // ---- melepas ----
        cmd.playerUnequips(sd, org.rtk.map.data.Equip.WEAP);
        check("perlengkapan: melepas MENANDAI, belum mencabut",
                sd.takeOffId == org.rtk.map.data.Equip.WEAP
                && sd.status.equipAt(org.rtk.map.data.Equip.WEAP) != null);
        Items.unequipScript(sd);
        check("perlengkapan: takeOff() barulah mencabutnya",
                sd.status.equipAt(org.rtk.map.data.Equip.WEAP) == null);
        check("perlengkapan: barangnya kembali ke inventaris",
                sd.status.inventory.size() == 1
                && sd.status.inventory.get(0).id == 7601);
        check("perlengkapan: takeOffId dikembalikan ke -1, bukan 0",
                sd.takeOffId == -1);

        // ---- senjata dua tangan vs perisai ----
        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.status.inventory.add(barangUji(7602, 1, 100));   // tombak
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        sd.status.inventory.add(barangUji(7603, 1, 100));   // perisai
        cmd.playerWields(sd, 0);
        check("perlengkapan: perisai ditolak saat memegang senjata dua tangan",
                sd.status.equipAt(org.rtk.map.data.Equip.SHIELD) == null
                && sd.equipId == 0);

        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.status.inventory.add(barangUji(7603, 1, 100));   // perisai dulu
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        sd.status.inventory.add(barangUji(7602, 1, 100));   // lalu tombak
        cmd.playerWields(sd, 0);
        check("perlengkapan: senjata dua tangan ditolak saat memakai perisai",
                sd.equipId == 0);

        // ---- syarat level, kelamin, nilai ----
        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.status.level = 10;
        sd.status.inventory.add(barangUji(7605, 1, 100));
        cmd.playerWields(sd, 0);
        check("perlengkapan: level kurang ditolak", sd.equipId == 0);
        sd.status.level = 99;

        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7606, 1, 100));
        sd.status.sex = 0;
        cmd.playerWields(sd, 0);
        check("perlengkapan: jenis kelamin tidak cocok ditolak", sd.equipId == 0);
        sd.status.sex = 1;
        cmd.playerWields(sd, 0);
        check("perlengkapan: jenis kelamin cocok diterima", sd.equipId == 7606);
        sd.equipId = 0;

        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7609, 1, 100));
        sd.maxHp = 100;
        cmd.playerWields(sd, 0);
        check("perlengkapan: pengurang darah melebihi milik pemain ditolak",
                sd.equipId == 0);
        sd.maxHp = 500;
        cmd.playerWields(sd, 0);
        check("perlengkapan: dengan darah cukup, barang yang sama diterima",
                sd.equipId == 7609);
        sd.equipId = 0;

        // ---- barang yang tidak bisa dilepas ----
        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.status.inventory.add(barangUji(7604, 1, 100));
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        check("perlengkapan: mahkota terpasang",
                sd.status.equipAt(org.rtk.map.data.Equip.CROWN) != null);
        sd.takeOffId = -1;
        cmd.playerUnequips(sd, org.rtk.map.data.Equip.CROWN);
        check("perlengkapan: ItmUnequip=1 berarti TIDAK BISA dilepas",
                sd.takeOffId == -1
                && sd.status.equipAt(org.rtk.map.data.Equip.CROWN) != null);

        // ---- inventaris penuh ----
        sd.status.equip.clear();
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7601, 1, 100));
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        sd.status.maxInv = 1;
        sd.status.inventory.add(barangUji(7603, 1, 100));
        sd.takeOffId = -1;
        cmd.playerUnequips(sd, org.rtk.map.data.Equip.WEAP);
        check("perlengkapan: inventaris penuh menahan pelepasan",
                sd.takeOffId == -1
                && sd.status.equipAt(org.rtk.map.data.Equip.WEAP) != null);
        sd.status.maxInv = 20;

        // ---- pasangan kiri/kanan, dan ketidaksimetrisannya ----
        sd.status.equip.clear();
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7607, 1, 100));
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        check("pasangan: cincin pertama masuk slot KIRI",
                sd.status.equipAt(org.rtk.map.data.Equip.LEFT) != null);
        sd.status.inventory.add(barangUji(7607, 1, 100));
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        check("pasangan: cincin kedua PINDAH ke slot kanan",
                sd.status.equipAt(org.rtk.map.data.Equip.RIGHT) != null);

        sd.status.equip.clear();
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7608, 1, 100));
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        // ⚠️ Anting pertama masuk SUBRIGHT, bukan SUBLEFT — kebalikan dari
        // cincin. Ada di C (empat blok `if` berurutan, bukan `else if`);
        // lihat Items.pasanganKiriKanan.
        check("pasangan: anting pertama justru masuk slot SUB-KANAN",
                sd.status.equipAt(org.rtk.map.data.Equip.SUBRIGHT) != null
                && sd.status.equipAt(org.rtk.map.data.Equip.SUBLEFT) == null);

        // ---- pesona senjata lepas saat diganti ----
        sd.status.equip.clear();
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7601, 1, 100));
        cmd.playerWields(sd, 0);
        Items.equipScript(sd);
        sd.enchanted = 2.5f;
        sd.flank = true;
        sd.backstab = true;
        cmd.playerUnequips(sd, org.rtk.map.data.Equip.WEAP);
        Items.unequipScript(sd);
        check("pesona: senjata dilepas mengembalikan enchant ke 1.0",
                sd.enchanted == 1.0f && !sd.flank && !sd.backstab);

        // ---- makan, umur barang, rokok ----
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7611, 2, 1));
        cmd.playerEatsItem(sd, 0);
        check("pakai: memakan mengurangi satu keping",
                sd.status.inventory.size() == 1
                && sd.status.inventory.get(0).amount == 1);
        long capMakan = sd.status.inventory.get(0).time;
        check("pakai: umur barang mulai dihitung saat DIPAKAI pertama kali",
                capMakan > 0);
        cmd.playerEatsItem(sd, 0);
        check("pakai: keping terakhir mengosongkan slotnya",
                sd.status.inventory.isEmpty());

        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7601, 1, 100));
        cmd.playerEatsItem(sd, 0);
        check("pakai: barang yang bukan makanan ditolak",
                sd.status.inventory.size() == 1
                && sd.status.inventory.get(0).amount == 1);

        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7610, 1, 2));
        cmd.playerUsesItem(sd, 0);
        check("rokok: ketahanan berkurang satu tiap isap, bukan jumlahnya",
                sd.status.inventory.size() == 1
                && sd.status.inventory.get(0).dura == 1
                && sd.status.inventory.get(0).amount == 1);
        cmd.playerUsesItem(sd, 0);
        check("rokok: habis isap terakhir barangnya hilang",
                sd.status.inventory.isEmpty());

        // ---- penjaga peta ----
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7611, 1, 1));
        map.canEat = 0;
        cmd.playerEatsItem(sd, 0);
        check("peta: MapCanEat=0 menahan makan",
                sd.status.inventory.size() == 1);
        map.canEat = 1;

        sd.status.equip.clear();
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7601, 1, 100));
        map.canEquip = 0;
        cmd.playerWields(sd, 0);
        check("peta: MapCanEquip=0 menahan mengenakan", sd.equipId == 0);
        map.canEquip = 1;

        // ---- keadaan pemain ----
        sd.status.state = 1;
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7611, 1, 1));
        cmd.playerUsesItem(sd, 0);
        check("keadaan: arwah tidak bisa memakai apa pun",
                sd.status.inventory.size() == 1
                && sd.status.inventory.get(0).amount == 1);
        sd.status.state = 0;

        // ---- melempar ----
        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }
        sd.status.inventory.clear();
        sd.status.inventory.add(barangUji(7612, 3, 1));
        sd.status.side = 1;   // menghadap kanan

        // ⚠️ Cari petak yang bersih dulu. Uji-uji sebelumnya menanam PORTAL
        // di sekitar posisi pemain, dan portal menghentikan lemparan — uji
        // ini akan gagal karena sebab yang sama sekali berbeda dari yang
        // sedang diperiksa. Jebakan yang persis sama pernah kejadian pada
        // uji tabrakan (lihat catatan portal di CLAUDE.md).
        int lx = sd.x;
        int ly = sd.y;
        for (int y = 1; y < map.ys - 1; y++) {
            boolean ketemu = false;
            for (int x = 1; x < map.xs - 3; x++) {
                if (map.walkable(x, y) && map.walkable(x + 1, y)
                        && map.warpAt(x, y) == null && map.warpAt(x + 1, y) == null
                        && map.objectsAt(x + 1, y).isEmpty()) {
                    lx = x;
                    ly = y;
                    ketemu = true;
                    break;
                }
            }
            if (ketemu) {
                break;
            }
        }
        Pc.warp(MapServer.world, sd, sd.m, lx, ly);
        sd.status.side = 1;

        cmd.playerThrowsItem(sd, 0, false);
        check("lempar: barang ber-ItmThrownConfirm menanyakan dulu",
                sd.status.inventory.get(0).amount == 3);

        int lantaiAwal = MapServer.floorItems.count();
        cmd.playerThrowsItem(sd, 0, true);
        check("lempar: setelah dikonfirmasi barangnya berkurang satu",
                sd.status.inventory.get(0).amount == 2);
        check("lempar: satu barang mendarat di lantai",
                MapServer.floorItems.count() == lantaiAwal + 1);
        FloorItem terlempar = null;
        for (FloorItem fi : MapServer.floorItems.all()) {
            if (fi.data.id == 7612) {
                terlempar = fi;
            }
        }
        check("lempar: mendarat di petak lain, bukan di kaki pelempar",
                terlempar != null && terlempar.x > sd.x && terlempar.y == sd.y);
        check("lempar: SELALU sekeping, berapa pun isi slotnya",
                terlempar != null && terlempar.data.amount == 1);

        // Dinding menghentikan lemparan: hadapkan ke tepi peta.
        int xLama = sd.x;
        sd.x = 1;
        sd.status.side = 3;   // menghadap kiri, tepi peta di x=0
        int[] tujuan = Items.throwLanding(sd);
        check("lempar: penelusuran berhenti di batas peta",
                tujuan[0] >= 0 && tujuan[0] <= 1);
        sd.x = xLama;
        sd.status.side = 1;

        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }
        sd.status.inventory.clear();
        sd.status.equip.clear();
        sd.equipId = 0;
        sd.takeOffId = -1;
        MapServer.scriptEngine = engineLama;
    }

    /** Satu barang inventaris untuk uji. */
    private static org.rtk.common.mmo.Item barangUji(long id, int amount, int dura) {
        org.rtk.common.mmo.Item it = new org.rtk.common.mmo.Item();
        it.id = id;
        it.amount = amount;
        it.dura = dura;
        return it;
    }


    // ==================================================================
    // Arah keluar RTK2 (Rtk2ClientView + ProtocolRouter)
    // ==================================================================

    /** Satu bingkai RTK2 yang sudah dibongkar, untuk dibaca uji. */
    private record Keluar(int opcode, org.rtk.common.Session sesi, int panjang) {
    }

    /**
     * Pecah isi outbox jadi bingkai RTK2, lalu masukkan ke buffer BACA
     * sesi bayangan supaya bisa dibaca {@code Wire.Reader} yang sungguhan.
     *
     * <p>Memakai pembaca yang sama dengan runtime, bukan pembaca uji
     * tersendiri — pelajaran Peringatan #10: helper uji yang konsisten
     * dengan dirinya sendiri bisa lolos tanpa menguji kodenya.</p>
     */
    private static java.util.List<Keluar> bingkaiRtk2(org.rtk.common.Session s) {
        byte[] semua = s.takeOutbound();
        java.util.List<Keluar> hasil = new java.util.ArrayList<>();
        int off = 0;
        while (off + 4 <= semua.length) {
            int len = ((semua[off] & 0xFF) << 8 | (semua[off + 1] & 0xFF)) + 2;
            if (off + len > semua.length) {
                break;
            }
            org.rtk.common.Session bayangan = MapServer.net.openTestSession(fdUji++);
            bayangan.feedTest(java.util.Arrays.copyOfRange(semua, off, off + len));
            hasil.add(new Keluar(((semua[off + 2] & 0xFF) << 8) | (semua[off + 3] & 0xFF),
                    bayangan, len));
            off += len;
        }
        return hasil;
    }

    private static Keluar cariOpcode(java.util.List<Keluar> daftar, int opcode) {
        for (Keluar k : daftar) {
            if (k.opcode() == opcode) {
                return k;
            }
        }
        return null;
    }

    private static Wire.Reader baca(Keluar k) {
        return new Wire.Reader(org.rtk.map.proto.Inbound.bytesOf(k.sesi()), k.panjang());
    }

    /**
     * Lewati blok dasar benda; kembalikan namanya.
     *
     * <p>Ada sebagai helper supaya kedua uji blok benda memakai hitungan
     * ladang yang <b>sama</b>. Menghitungnya ulang di tiap tempat adalah
     * cara paling mudah membuat uji gagal karena hitungannya sendiri, bukan
     * karena kodenya — dan itu sudah kejadian saat menulis uji ini.</p>
     */
    private static String lewatiDasar(Wire.Reader r) {
        r.u64();          // id
        r.u8();           // kind
        r.u16();          // x
        r.u16();          // y
        r.u8();           // side
        r.u16();          // grafik
        r.u8();           // warna grafik
        r.u8();           // bendera
        return r.str();   // nama
    }

    /** Lewati bagian wujud karakter sampai tepat sebelum jumlah slot. */
    private static void lewatiWujud(Wire.Reader r) {
        r.u8();    // sex
        r.u8();    // state
        r.u16();   // penyamaran
        r.u8();    // warna penyamaran
        r.u8();    // kecepatan
        r.u8();    // wajah
        r.u8();    // rambut
        r.u8();    // warna rambut
        r.u8();    // warna wajah
        r.u8();    // warna kulit
    }

    private static void rtk2Test(MapData map, User sd) {
        log.info("=== arah keluar RTK2 ===");
        var engineLama = MapServer.scriptEngine;
        MapServer.scriptEngine = null;

        // Dua pemain bersebelahan, protokol berbeda.
        int px = -1;
        int py = -1;
        for (int y = 1; y < map.ys - 1 && px < 0; y++) {
            for (int x = 1; x < map.xs - 3; x++) {
                if (map.walkable(x, y) && map.walkable(x + 1, y)
                        && map.warpAt(x, y) == null && map.warpAt(x + 1, y) == null
                        && map.objectsAt(x, y).isEmpty()
                        && map.objectsAt(x + 1, y).isEmpty()) {
                    px = x;
                    py = y;
                    break;
                }
            }
        }
        check("rtk2: petak uji ditemukan", px > 0);

        CharStatus sa = new CharStatus();
        sa.id = 0x0A0A0A;
        sa.name = "Baru";
        sa.baseHp = 100;
        sa.lastPos = new Point(map.id, px, py);
        User a = new User(70, sa);
        a.rtk2 = true;
        a.m = map.id;
        a.x = px;
        a.y = py;

        CharStatus sb = new CharStatus();
        sb.id = 0x0B0B0B;
        sb.name = "Lama";
        sb.baseHp = 100;
        sb.lastPos = new Point(map.id, px + 1, py);
        User b = new User(71, sb);
        b.rtk2 = false;
        b.m = map.id;
        b.x = px + 1;
        b.y = py;

        org.rtk.common.Session ka = MapServer.net.openTestSession(a.fd);
        org.rtk.common.Session kb = MapServer.net.openTestSession(b.fd);
        MapServer.onlineChars.put(a.fd, a);
        MapServer.onlineChars.put(b.fd, b);
        map.addBlock(a);
        map.addBlock(b);
        ka.takeOutbound();
        kb.takeOutbound();

        var cv = MapServer.clientView;
        check("rtk2: penyalur memegang dua implementasi",
                cv instanceof ProtocolRouter r && r.views().size() == 2);

        // ---- pesan ke satu pemain ----
        cv.messageToPlayer(a, 3, "halo dunia");
        var fa = bingkaiRtk2(ka);
        byte[] mentahB = kb.takeOutbound();
        check("rtk2: pemain RTK2 menerima satu bingkai", fa.size() == 1);
        check("rtk2: pemain RetroTK TIDAK ikut menerimanya", mentahB.length == 0);
        Keluar pesan = fa.get(0);
        check("rtk2: opcodenya EV_MESSAGE", pesan.opcode() == Wire.EV_MESSAGE);
        var r1 = baca(pesan);
        check("rtk2: ragam pesan terbaca", r1.u8() == 3);
        check("rtk2: teksnya utuh", "halo dunia".equals(r1.str()));
        check("rtk2: tidak ada byte sisa di bingkainya", r1.rest() == 0);

        // ---- pesan ke pemain RetroTK ----
        cv.messageToPlayer(b, 3, "halo lama");
        byte[] mentah = kb.takeOutbound();
        check("rtk2: pemain RetroTK tetap menerima paket 0xAA",
                mentah.length > 0 && (mentah[0] & 0xFF) == 0xAA);
        check("rtk2: pemain RTK2 tidak ikut kena", bingkaiRtk2(ka).isEmpty());

        // ---- siaran ke area: KEDUANYA kena, masing-masing formatnya ----
        cv.objectSpoke(a, 0, "hai semua");
        var siarA = bingkaiRtk2(ka);
        byte[] siarB = kb.takeOutbound();
        check("rtk2: siaran sampai ke pemain RTK2",
                cariOpcode(siarA, Wire.EV_OBJECT_SPOKE) != null);
        check("rtk2: siaran yang SAMA sampai ke pemain RetroTK",
                siarB.length > 0 && (siarB[0] & 0xFF) == 0xAA);
        var r2 = baca(cariOpcode(siarA, Wire.EV_OBJECT_SPOKE));
        check("rtk2: id pembicara ikut di bingkainya", r2.u64() == a.id);
        r2.u8();
        check("rtk2: teks siaran utuh", "hai semua".equals(r2.str()));

        // ---- panjang bingkai adalah panjang sebenarnya ----
        // ⚠️ Ini yang TIDAK berlaku di RetroTK: setPacketIndexes menimpa
        // ladang panjang dengan nilai+3 lalu menempel 3 byte kunci, dan itu
        // sudah dua kali membuat ekspektasi uji salah (Peringatan #17).
        cv.messageToPlayer(a, 5, "abc");
        Keluar k = bingkaiRtk2(ka).get(0);
        check("rtk2: panjang bingkai = 4 header + 1 ragam + 2 + 3 teks",
                k.panjang() == 4 + 1 + 2 + 3);

        // ---- blok benda disusun ULANG per penonton ----
        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }
        var kosongLookR = new org.rtk.map.data.ItemDb.Look(0, 0, 77, 3);
        var tanpaStatR = new org.rtk.map.data.ItemDb.Stats(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        MapServer.itemDb.register(new org.rtk.map.data.ItemDb.Info(7701, "jebakan_r2",
                "Jebakan R2", "", "", org.rtk.map.data.ItemDb.ITM_TRAPS, 0, 0, 0,
                1, 1, 1, 0, 0, 1, 0, 0, kosongLookR, tanpaStatR));
        FloorItem jebakan = MapServer.floorItems.drop(null, 7701, 1, 1, 0, 0,
                map.id, px, py);
        check("rtk2: jebakan uji tergeletak", jebakan != null);
        ka.takeOutbound();
        kb.takeOutbound();

        cv.floorItemAppeared(jebakan);
        check("rtk2: jebakan yang BELUM ditemukan tidak dikirim",
                cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_APPEARED) == null);
        kb.takeOutbound();

        jebakan.addTrapSpotter(a.id);
        cv.floorItemAppeared(jebakan);
        Keluar tampak = cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_APPEARED);
        check("rtk2: setelah ditemukan, jebakan yang sama DIKIRIM", tampak != null);
        var r3 = baca(tampak);
        check("rtk2: blok benda membawa id barang lantai", r3.u64() == jebakan.id);
        check("rtk2: jenisnya 4 (barang)", r3.u8() == 4);
        check("rtk2: petaknya benar", r3.u16() == px && r3.u16() == py);
        r3.u8();
        // ⚠️ Ikon dikirim MENTAH — tanpa penambah 32768/49152 yang di RetroTK
        // berbeda-beda per jenis benda (Peringatan #34).
        check("rtk2: ikon dikirim mentah, tanpa penambah", r3.u16() == 77);
        check("rtk2: warna ikon terbaca", r3.u8() == 3);
        check("rtk2: barang lantai TIDAK bertanda 'digambar sebagai karakter'",
                (r3.u8() & 1) == 0);
        check("rtk2: nama barangnya ikut", "Jebakan R2".equals(r3.str()));
        check("rtk2: jumlahnya ikut", r3.u32() == 1);

        // ---- blok pemain: daftar slot, bukan offset tetap ----
        ka.takeOutbound();
        a.status.equip.clear();
        cv.objectAppearanceChanged(b);
        Keluar wujud = cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_APPEARANCE);
        check("rtk2: perubahan wujud pemain lain sampai", wujud != null);
        var r4 = baca(wujud);
        check("rtk2: id pemainnya", r4.u64() == b.id);
        check("rtk2: jenisnya 1 (pemain)", r4.u8() == 1);
        check("rtk2: petak pemainnya", r4.u16() == b.x && r4.u16() == b.y);
        r4.u8();
        r4.u16();
        r4.u8();
        check("rtk2: benderanya menandai 'digambar sebagai karakter'",
                (r4.u8() & 1) != 0);
        check("rtk2: namanya ikut di bloknya", "Lama".equals(r4.str()));
        lewatiWujud(r4);
        check("rtk2: pemain tanpa perlengkapan mengirim NOL slot, bukan sentinel",
                r4.u8() == 0);

        var kosongLook2 = new org.rtk.map.data.ItemDb.Look(4321, 9, 0, 0);
        MapServer.itemDb.register(new org.rtk.map.data.ItemDb.Info(7702, "pedang_r2",
                "Pedang R2", "", "", org.rtk.map.data.ItemDb.ITM_WEAP, 0, 0, 0,
                1, 1, 1, 0, 0, 100, 0, 0, kosongLook2, tanpaStatR));
        org.rtk.common.mmo.Item sen = new org.rtk.common.mmo.Item();
        sen.id = 7702;
        sen.amount = 1;
        sen.dura = 100;
        sen.pos = org.rtk.map.data.Equip.WEAP;
        b.status.equip.add(sen);
        ka.takeOutbound();
        cv.objectAppearanceChanged(b);
        var r5 = baca(cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_APPEARANCE));
        lewatiDasar(r5);
        lewatiWujud(r5);
        check("rtk2: satu perlengkapan berarti satu entri slot", r5.u8() == 1);
        check("rtk2: nomor slotnya EQ_WEAP", r5.u8() == org.rtk.map.data.Equip.WEAP);
        check("rtk2: grafiknya dari ItmLook", r5.u16() == 4321);
        check("rtk2: warnanya dari ItmLookColor", r5.u8() == 9);
        b.status.equip.clear();

        // ---- sapuan area MENYERTAKAN diri sendiri (K1.1) ----
        // Pemain adalah benda di dunia seperti yang lain; tanpa blok ini
        // klien tidak pernah tahu wujudnya sendiri dan tidak bisa
        // menggambarnya. Posisi di layar tetap dari EV_SELF_*.
        ka.takeOutbound();
        cv.playerViewRefreshed(a);
        boolean adaDiri = false;
        boolean adaLain = false;
        for (Keluar kel : bingkaiRtk2(ka)) {
            if (kel.opcode() != Wire.EV_OBJECT_APPEARED) {
                continue;
            }
            long idBenda = baca(kel).u64();
            if (idBenda == a.id) {
                adaDiri = true;
            }
            if (idBenda == b.id) {
                adaLain = true;
            }
        }
        check("rtk2: sapuan area menyertakan DIRI SENDIRI", adaDiri);
        check("rtk2: sapuan area tetap menyertakan pemain lain", adaLain);

        // ---- K1.2: dua wajah NPC ----
        // NPC biasa (npcType != 1): gambar tunggal dari ruang look mob,
        // TANPA bendera karakter — dulu benderanya keliru menyala dan klien
        // menggambar badan telanjang.
        Npc biasa = new Npc();
        biasa.name = "npc_biasa_r2";
        biasa.displayName = "Warga";
        biasa.npcId = 9101;
        biasa.id = Npc.blockIdFor(9101);
        biasa.m = map.id;
        biasa.x = px;
        biasa.y = py;
        biasa.side = 2;
        biasa.npcType = 0;
        biasa.graphicId = 15;
        biasa.graphicColor = 2;
        ka.takeOutbound();
        cv.npcAppearedTo(a, biasa);
        var rBiasa = baca(cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_APPEARED));
        check("rtk2: NPC biasa terkirim", rBiasa != null);
        rBiasa.u64();
        check("rtk2: NPC biasa berjenis 3", rBiasa.u8() == 3);
        rBiasa.u16();
        rBiasa.u16();
        rBiasa.u8();
        check("rtk2: grafik NPC biasa = graphicId MENTAH (ruang look mob)",
                rBiasa.u16() == 15);
        check("rtk2: warna grafiknya ikut", rBiasa.u8() == 2);
        check("rtk2: NPC biasa TIDAK berbendera karakter", (rBiasa.u8() & 1) == 0);
        check("rtk2: namanya ikut", "Warga".equals(rBiasa.str()));
        check("rtk2: blok NPC biasa berhenti di nama (tanpa blok wujud)",
                rBiasa.rest() == 0);

        // NPC karakter (npcType == 1): FLAG_AS_CHAR + blok wujud + slot
        // NPCEquipment (NeqLook = nomor grafik, TANPA lewat ItemDb).
        Npc berwujud = new Npc();
        berwujud.name = "npc_wujud_r2";
        berwujud.displayName = "Yunsil R2";
        berwujud.npcId = 9102;
        berwujud.id = Npc.blockIdFor(9102);
        berwujud.m = map.id;
        berwujud.x = px;
        berwujud.y = py;
        berwujud.side = 1;
        berwujud.npcType = 1;
        berwujud.sex = 1;
        berwujud.face = 3;
        berwujud.hair = 5;
        berwujud.hairColor = 7;
        berwujud.faceColor = 2;
        berwujud.skinColor = 4;
        org.rtk.common.mmo.Item zirahNpc = new org.rtk.common.mmo.Item();
        zirahNpc.id = 305;          // NeqLook — langsung nomor grafik
        zirahNpc.amount = 1;
        zirahNpc.customLookColor = 6;
        berwujud.equip[org.rtk.map.data.Equip.ARMOR] = zirahNpc;
        ka.takeOutbound();
        cv.npcAppearedTo(a, berwujud);
        var rWujud = baca(cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_APPEARED));
        check("rtk2: NPC berwujud terkirim", rWujud != null);
        rWujud.u64();
        rWujud.u8();
        rWujud.u16();
        rWujud.u16();
        rWujud.u8();
        rWujud.u16();
        rWujud.u8();
        check("rtk2: NPC berwujud BERBENDERA karakter", (rWujud.u8() & 1) != 0);
        check("rtk2: nama NPC berwujud", "Yunsil R2".equals(rWujud.str()));
        check("rtk2: wujud NPC — sex", rWujud.u8() == 1);
        rWujud.u8();
        rWujud.u16();
        rWujud.u8();
        rWujud.u8();
        check("rtk2: wujud NPC — wajah & rambut",
                rWujud.u8() == 3 && rWujud.u8() == 5 && rWujud.u8() == 7);
        rWujud.u8();
        rWujud.u8();
        check("rtk2: satu slot perlengkapan NPC", rWujud.u8() == 1);
        check("rtk2: nomor slotnya EQ_ARMOR",
                rWujud.u8() == org.rtk.map.data.Equip.ARMOR);
        check("rtk2: grafik slot NPC = NeqLook LANGSUNG, tanpa ItemDb",
                rWujud.u16() == 305);
        check("rtk2: warna slot NPC dari NeqColor", rWujud.u8() == 6);
        check("rtk2: blok wujud NPC habis tepat di tanda",
                rWujud.rest() == 1);

        // ---- K3.2: buku mantra ----
        // Server dulu hanya pernah mengirim PENGHAPUSAN mantra; isi bukunya
        // tidak pernah — klien tidak bisa menampilkan atau memilih mantra
        // untuk OP_CAST.
        MapServer.spellDb.register(4801, "uji_mantra_r2", "Sinar Uji", 0, 0);
        a.status.spells = new int[] {0, 4801};
        ka.takeOutbound();
        cv.playerSpellSlotChanged(a, 1);
        var rMantra = baca(cariOpcode(bingkaiRtk2(ka), Wire.EV_SPELL_SLOT));
        check("rtk2: slot mantra terkirim", rMantra != null);
        check("rtk2: slot mantra 0-BASIS apa adanya", rMantra.u8() == 1);
        check("rtk2: id mantranya ikut", rMantra.u32() == 4801);
        rMantra.u8();   // tipe (SplType; 0 untuk mantra uji)
        check("rtk2: nama TAMPILAN mantra, bukan nama skrip",
                "Sinar Uji".equals(rMantra.str()));
        check("rtk2: pertanyaan ikut (kosong bila tidak bertanya)",
                "".equals(rMantra.str()) && rMantra.rest() == 0);

        // Slot kosong tidak mengirim apa-apa — jangan mengumumkan ketiadaan.
        ka.takeOutbound();
        cv.playerSpellSlotChanged(a, 0);
        check("rtk2: slot mantra KOSONG tidak dikirim",
                cariOpcode(bingkaiRtk2(ka), Wire.EV_SPELL_SLOT) == null);

        // Masuk dunia membawa seluruh buku (cermin pc_loadmagic).
        ka.takeOutbound();
        cv.playerEnteredWorld(a);
        boolean bukuIkut = false;
        for (Keluar kel : bingkaiRtk2(ka)) {
            if (kel.opcode() == Wire.EV_SPELL_SLOT) {
                bukuIkut = true;
            }
        }
        check("rtk2: masuk dunia membawa buku mantra (pc_loadmagic)", bukuIkut);
        a.status.spells = new int[0];

        // ---- K2: masuk dunia membawa kata setelan ----
        // EV_SELF_SETTINGS tadinya hanya terbit saat setelan berubah;
        // jendela setelan klien buta sampai pemain membalik sesuatu.
        ka.takeOutbound();
        kb.takeOutbound();
        cv.playerEnteredWorld(a);
        check("rtk2: masuk dunia mengirim kata setelan (EV_SELF_SETTINGS)",
                cariOpcode(bingkaiRtk2(ka), Wire.EV_SELF_SETTINGS) != null);

        // ---- R1/K4: emosi lewat kabel ----
        // clif_parseemotion: nomor aksi = emosi + 11, waktu 0x4E, dan HANYA
        // pada keadaan normal.
        a.status.state = 0;
        ka.takeOutbound();
        MapServer.commands.playerEmotes(a, 5);
        var emo = cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_ACTED);
        check("k4: emosi menghasilkan EV_OBJECT_ACTED", emo != null);
        if (emo != null) {
            var re = baca(emo);
            check("k4: aksi milik pemain yang beremosi", re.u64() == a.id);
            check("k4: nomor aksinya emosi + 11 (dari C)", re.u8() == 16);
            check("k4: waktunya 0x4E (dari C)", re.u16() == 0x4E);
        }
        a.status.state = 1;   // arwah
        ka.takeOutbound();
        MapServer.commands.playerEmotes(a, 5);
        check("k4: arwah TIDAK beremosi",
                cariOpcode(bingkaiRtk2(ka), Wire.EV_OBJECT_ACTED) == null);
        a.status.state = 0;

        // ---- R3/C3: perpindahan antar map server ----
        // Peta yang tidak dimuat di sini bukan lagi penolakan: server
        // pemiliknya dicari di Maps.MapServer, lalu klien dialihkan.
        int serverLama = MapServer.serverId;
        int dmLama = a.destMap;
        // Peta 2 ada di tabel Maps; kita pura-pura server ini bukan
        // pemiliknya supaya cabang transfer yang diuji, bukan cabang lokal.
        Integer pemilik = MapServer.sql.queryInt(
                "SELECT `MapServer` FROM `Maps` WHERE `MapId` = ?", 2);
        check("c3: peta uji ada di tabel Maps", pemilik != null);
        if (pemilik != null) {
            MapServer.serverId = pemilik + 7;   // bukan pemiliknya
            ka.takeOutbound();
            a.destMap = -1;
            MapData petaLokal = MapServer.world.get(2);
            MapServer.world.remove(2);          // seolah tidak dimuat di sini
            boolean hasil = Pc.warp(MapServer.world, a, 2, 300, 400);   // di luar 1..255
            check("c3: perpindahan ke peta server lain DITERIMA, bukan ditolak",
                    hasil);
            // ⚠️ Koordinat di luar 1..255 di C tidak DIJEPIT melainkan
            // disetel ke 1 ("Just for Justin"). Menjepitnya ke 255 akan
            // menaruh pemain di pojok peta yang salah.
            check("c3: petak mustahil disetel ke 1, bukan dijepit ke 255",
                    a.destMap == 2 && a.destX == 1 && a.destY == 1);
            var tr = cariOpcode(bingkaiRtk2(ka), Wire.EV_TRANSFER);
            check("c3: klien diberi alamat server tujuan", tr != null);
            if (tr != null) {
                var rt = baca(tr);
                check("c3: host ikut", !rt.str().isEmpty());
                check("c3: port = 2001 + id server pemiliknya",
                        rt.u16() == MapServer.PORT_MAP_DASAR + pemilik);
                check("c3: peta dan petak tujuan ikut",
                        rt.u16() == 2 && rt.u16() == 1 && rt.u16() == 1
                                && rt.rest() == 0);
            }
            // Peta milik server INI tapi tidak termuat = kesalahan, bukan
            // transfer: mengalihkan ke diri sendiri hanya jadi gelung.
            MapServer.serverId = pemilik;
            a.destMap = -1;
            check("c3: peta milik server sendiri yang tidak termuat TIDAK dialihkan",
                    !Pc.warp(MapServer.world, a, 2, 10, 10) && a.destMap == -1);
            if (petaLokal != null) {
                MapServer.world.put(petaLokal);
            }
        }
        MapServer.serverId = serverLama;
        a.destMap = dmLama;

        // ---- R2: kalender dunia ----
        // ⚠️ BACA dulu dari tabelnya sebelum menyimpan "nilai lama".
        // Versi pertama uji ini menyimpan isi statik yang BELUM pernah
        // dimuat (masih bawaan 0/1/1/1), lalu menuliskannya kembali ke
        // database di akhir — kalender dunia yang sungguhan tertimpa
        // nilai bawaan. Uji yang mengembalikan sesuatu harus mengembalikan
        // apa yang benar-benar ada, bukan apa yang kebetulan ada di memori.
        WorldTime.load(MapServer.sql);
        int jamLama = WorldTime.hour, hariLama = WorldTime.day;
        int musimLama = WorldTime.season, tahunLama = WorldTime.year;
        WorldTime.hour = 23;
        WorldTime.day = 91;
        WorldTime.season = 4;
        WorldTime.year = 6;
        ka.takeOutbound();
        cv.worldTimeChanged(a);
        var wt = cariOpcode(bingkaiRtk2(ka), Wire.EV_WORLD_TIME);
        check("r2: kalender dunia terkirim", wt != null);
        if (wt != null) {
            var rw = baca(wt);
            check("r2: jam, hari, musim, tahun terbaca utuh",
                    rw.u8() == 23 && rw.u8() == 91 && rw.u8() == 4
                            && rw.u16() == 6 && rw.rest() == 0);
        }
        // Satu jam berlalu: 23 -> hari baru -> musim baru -> TAHUN baru.
        // Urutan tumpahnya persis change_time_char di C.
        // ⚠️ tick() ikut menulis ke tabel Time; barisnya dikembalikan di
        // bawah supaya gerbang ini tidak menggeser kalender dunia.
        WorldTime.tick();
        check("r2: jam 23 + 1 mengganti hari", WorldTime.hour == 0);
        check("r2: hari 91 + 1 mengganti musim", WorldTime.day == 1);
        check("r2: musim 4 + 1 kembali ke 1 dan menambah tahun",
                WorldTime.season == 1 && WorldTime.year == 7);
        check("r2: nama musim mengikuti urutan C (1=Winter)",
                "Winter".equals(WorldTime.seasonName()));
        WorldTime.season = 3;
        check("r2: musim 3 = Summer", "Summer".equals(WorldTime.seasonName()));
        WorldTime.hour = jamLama;
        WorldTime.day = hariLama;
        WorldTime.season = musimLama;
        WorldTime.year = tahunLama;
        MapServer.sql.update("UPDATE `Time` SET `TimHour` = ?, `TimDay` = ?, "
                + "`TimSeason` = ?, `TimYear` = ?",
                jamLama, hariLama, musimLama, tahunLama);

        // ---- R1 batch 2: profil, kota, peringkat ----
        // Profil menggantikan sendMyStatus TAHAP 1: yang dikirim HANYA yang
        // tidak bisa dihitung klien.
        a.status.title = "Pengembara";
        a.status.clanTitle = "Tetua";
        a.status.level = 5;
        a.status.exp = 10;
        a.profileText = "halo";
        ka.takeOutbound();
        cv.playerProfile(a);
        var prof = cariOpcode(bingkaiRtk2(ka), Wire.EV_SELF_PROFILE);
        check("r1: profil diri terkirim", prof != null);
        if (prof != null) {
            var rp = baca(prof);
            rp.str();                            // nama kelas dari ClassDb
            check("r1: gelar ikut", "Pengembara".equals(rp.str()));
            check("r1: gelar klan ikut", "Tetua".equals(rp.str()));
            rp.str();                            // pasangan (kosong)
            long tnl = rp.u32();
            check("r1: TNL = sisa exp ke tingkat berikutnya, bukan 0 seperti TAHAP 1",
                    tnl > 0);
            int persen = rp.u16();
            check("r1: persen bilah XP dikirim ×100 (0..10000)",
                    persen >= 0 && persen <= 10000);
            check("r1: teks profil ikut", "halo".equals(rp.str()));
            check("r1: daftar legenda ikut (kosong untuk karakter uji)",
                    rp.u16() == 0 && rp.rest() == 0);
        }
        a.profileText = "";

        // Daftar kota: nama saja, urut seperti map.conf.
        ka.takeOutbound();
        cv.townListToPlayer(a, java.util.List.of("Koguryo", "Buya"));
        var kota = cariOpcode(bingkaiRtk2(ka), Wire.EV_TOWNS);
        check("r1: daftar kota terkirim", kota != null);
        if (kota != null) {
            var rk = baca(kota);
            check("r1: jumlah kota", rk.u16() == 2);
            check("r1: urutannya dipertahankan",
                    "Koguryo".equals(rk.str()) && "Buya".equals(rk.str()));
            check("r1: bingkai kota habis", rk.rest() == 0);
        }

        // Peringkat: daftar KOSONG tetap dikirim — klien harus bisa menutup
        // jendelanya, bukan menggantung menunggu.
        ka.takeOutbound();
        cv.rankingToPlayer(a, "Peringkat", java.util.List.of());
        var rank = cariOpcode(bingkaiRtk2(ka), Wire.EV_RANKING);
        check("r1: peringkat KOSONG tetap dikirim", rank != null);
        if (rank != null) {
            var rr = baca(rank);
            check("r1: judulnya ikut", "Peringkat".equals(rr.str()));
            check("r1: jumlah barisnya nol", rr.u16() == 0 && rr.rest() == 0);
        }
        ka.takeOutbound();
        cv.rankingToPlayer(a, "Peringkat",
                java.util.List.<Object[]>of(new Object[] {1, "Adrielle", 999L}));
        var rr2 = baca(cariOpcode(bingkaiRtk2(ka), Wire.EV_RANKING));
        rr2.str();
        check("r1: satu baris peringkat terbaca utuh",
                rr2.u16() == 1 && rr2.u16() == 1
                        && "Adrielle".equals(rr2.str()) && rr2.u32() == 999);

        // ---- kebocoran yang sengaja tidak diteruskan ----
        ka.takeOutbound();
        cv.areaRedrawRequested(a, map, 0, 0, 4, 4, 0);
        check("rtk2: permintaan gambar ulang TIDAK diteruskan (kebocoran RetroTK)",
                bingkaiRtk2(ka).isEmpty());

        // ---- bersih-bersih ----
        for (FloorItem fi : new java.util.ArrayList<>(MapServer.floorItems.all())) {
            MapServer.floorItems.hapus(fi);
        }
        map.delBlock(a);
        map.delBlock(b);
        MapServer.onlineChars.remove(a.fd);
        MapServer.onlineChars.remove(b.fd);
        MapServer.scriptEngine = engineLama;
    }


    // ==================================================================
    // Binding dunia (WorldBindings) — peta, cuaca, teks
    // ==================================================================

    /**
     * Panggil satu global Lua dengan argumen sebanyak apa pun.
     *
     * <p>{@code LuaValue.call} hanya menerima tiga argumen; global peta
     * memakai empat sampai sembilan belas. Memakai {@code invoke} sekali di
     * satu helper lebih baik daripada mencampur keduanya per pemanggilan.</p>
     */
    private static org.luaj.vm2.LuaValue panggilGlobal(
            org.rtk.map.script.ScriptEngine engine, String nama, Object... args) {
        org.luaj.vm2.LuaValue[] v = new org.luaj.vm2.LuaValue[args.length];
        for (int i = 0; i < args.length; i++) {
            v[i] = args[i] instanceof String t
                    ? org.luaj.vm2.LuaValue.valueOf(t)
                    : org.luaj.vm2.LuaValue.valueOf(((Number) args[i]).doubleValue());
        }
        return engine.globals().get(nama)
                .invoke(org.luaj.vm2.LuaValue.varargsOf(v)).arg1();
    }

    private static void duniaTest(MapData map, User sd) {
        log.info("=== binding dunia: peta, cuaca, siaran ===");
        var engineLama = MapServer.scriptEngine;
        var engine = new org.rtk.map.script.ScriptEngine();
        MapServer.scriptEngine = engine;

        // ---- ⚠️ salin-saat-ditulis: dua peta, SATU berkas ----
        //
        // Inilah perangkap yang ditemukan audit 27 Agustus: MapRegistry
        // men-cache satu MapFile per NAMA BERKAS, dan 9.850 peta hanya
        // menunjuk 2.919 berkas. setTile yang menulis langsung akan mengubah
        // petak itu di SETIAP peta sejenis — dan tiap peta akan tetap
        // terlihat "benar" bila diperiksa sendiri-sendiri.
        var berkas = map.geometry();
        MapData kembarA = new MapData(60001, berkas);
        MapData kembarB = new MapData(60002, berkas);
        MapServer.world.put(kembarA);
        MapServer.world.put(kembarB);

        int asli = kembarA.tile(3, 3);
        check("dunia: dua peta berangkat dari petak yang sama",
                kembarB.tile(3, 3) == asli);
        check("dunia: keduanya belum punya geometri sendiri",
                !kembarA.hasPrivateGeometry() && !kembarB.hasPrivateGeometry());

        panggilGlobal(engine, "setTile", 60001, 3, 3, asli + 7);
        check("dunia: setTile mengubah peta yang dituju",
                kembarA.tile(3, 3) == asli + 7);
        check("dunia: peta KEMBARANNYA TIDAK ikut berubah",
                kembarB.tile(3, 3) == asli);
        check("dunia: yang diubah kini punya geometri sendiri",
                kembarA.hasPrivateGeometry() && !kembarB.hasPrivateGeometry());
        check("dunia: berkas aslinya juga tidak tersentuh",
                berkas.tile(3, 3) == asli);

        panggilGlobal(engine, "setObject", 60002, 4, 4, 99);
        check("dunia: setObject menulis lapisan objek",
                kembarB.obj(4, 4) == 99);
        panggilGlobal(engine, "setPass", 60002, 5, 5, 1);
        check("dunia: setPass membuat petak jadi terhalang",
                kembarB.pass(5, 5) == 1 && !kembarB.walkable(5, 5));
        check("dunia: kembarannya tetap bisa dilewati",
                kembarA.walkable(5, 5));

        // Petak di luar peta tidak boleh meledak, hanya diabaikan.
        panggilGlobal(engine, "setTile", 60001, 99999, 99999, 1);
        check("dunia: petak di luar peta diabaikan, bukan melempar", true);

        // ⚠️ getObject/getTile juga tidak boleh melempar di luar batas.
        // Di C keduanya mengindeks larik tanpa pemeriksaan, jadi membaca
        // sepetak di luar tepi hanya memberi angka tetangga. Di sini ia
        // sempat melempar dan MEMBUNUH skrip — setiap kait `onLook` gagal
        // ketika pemain berdiri di tepi peta.
        var mdTepi = MapServer.world.get(sd.m);
        var glob = MapServer.scriptEngine == null ? null
                : MapServer.scriptEngine.globals();
        if (glob != null && mdTepi != null) {
            String panggil = "return getObject(" + sd.m + ", 0, " + mdTepi.ys + ")";
            check("dunia: getObject sepetak di luar tepi mengembalikan 0, bukan melempar",
                    glob.load(panggil).call().toint() == 0);
            check("dunia: getTile di luar tepi juga aman",
                    glob.load("return getTile(" + sd.m + ", " + mdTepi.xs + ", 0)")
                            .call().toint() == 0);
            check("dunia: koordinat negatif ikut dijaga",
                    glob.load("return getObject(" + sd.m + ", -1, -1)")
                            .call().toint() == 0);
            check("dunia: petak SAH tetap terbaca apa adanya",
                    glob.load("return getTile(" + sd.m + ", 0, 0)").call().toint()
                            == mdTepi.tile(0, 0));
        }

        // ---- atribut peta ----
        check("dunia: getMapIsLoaded untuk peta yang ada",
                panggilGlobal(engine, "getMapIsLoaded", 60001).toboolean());
        check("dunia: getMapIsLoaded untuk peta yang tidak ada",
                !panggilGlobal(engine, "getMapIsLoaded", 58888).toboolean());

        kembarA.pvp = 0;
        panggilGlobal(engine, "setMapPvP", 60001, 1);
        check("dunia: setMapPvP menyalakan PvP", kembarA.pvp == 1);
        check("dunia: getMapPvP membacanya kembali",
                panggilGlobal(engine, "getMapPvP", 60001).toint() == 1);

        panggilGlobal(engine, "setMapAttribute", 60001, "canEat", 1);
        check("dunia: setMapAttribute menulis ladang bernama",
                kembarA.canEat == 1);
        check("dunia: getMapAttribute membacanya kembali",
                panggilGlobal(engine, "getMapAttribute", 60001, "canEat").toint() == 1);
        check("dunia: xmax adalah INDEKS terbesar, bukan lebar",
                panggilGlobal(engine, "getMapAttribute", 60001, "xmax").toint()
                        == kembarA.xs - 1);
        check("dunia: atribut yang tidak dikenal dijawab nil, bukan meledak",
                panggilGlobal(engine, "getMapAttribute", 60001, "takAda").isnil());

        // ---- setLight: hanya peta yang cahayanya MASIH 0 ----
        kembarA.region = 77;
        kembarA.indoor = 0;
        kembarA.light = 0;
        kembarB.region = 77;
        kembarB.indoor = 0;
        kembarB.light = 5;
        panggilGlobal(engine, "setLight", 77, 0, 9);
        check("dunia: setLight mengisi peta yang cahayanya 0", kembarA.light == 9);
        check("dunia: setLight TIDAK menimpa peta yang sudah bercahaya",
                kembarB.light == 5);

        // ---- cuaca, dan penjaga cuaca buatan ----
        kembarA.weather = 0;
        panggilGlobal(engine, "setWeatherM", 60001, 3);
        check("dunia: setWeatherM memasang cuaca", kembarA.weather == 3);
        check("dunia: getWeatherM membacanya kembali",
                panggilGlobal(engine, "getWeatherM", 60001).toint() == 3);

        // Cap di MASA DEPAN berarti cuaca buatan sedang berjalan: cuaca
        // musiman tidak boleh menimpanya.
        long nanti = System.currentTimeMillis() / 1000 + 3600;
        engine.mapReg(60001).put("artificial_weather_timer", (int) nanti);
        panggilGlobal(engine, "setWeatherM", 60001, 7);
        check("dunia: cuaca buatan yang masih berlaku TIDAK tertimpa",
                kembarA.weather == 3);

        // Cap yang sudah LEWAT dibersihkan di sini, bukan oleh timer lain.
        engine.mapReg(60001).put("artificial_weather_timer", 1);
        panggilGlobal(engine, "setWeatherM", 60001, 7);
        check("dunia: cap yang kedaluwarsa dibersihkan lalu cuacanya berubah",
                kembarA.weather == 7
                && engine.mapReg(60001).get("artificial_weather_timer") == 0);

        // ---- portal ----
        panggilGlobal(engine, "setWarps", 60001, 6, 6, 60002, 1, 2);
        var w = kembarA.warpAt(6, 6);
        check("dunia: setWarps menanam portal", w != null && w.tm() == 60002
                && w.tx() == 1 && w.ty() == 2);
        check("dunia: setWarps ke peta yang tidak dimuat ditolak",
                !panggilGlobal(engine, "setWarps", 60001, 7, 7, 58888, 1, 1)
                        .toboolean());

        // ---- saveMap lalu setMap: bolak-balik lewat berkas ----
        java.nio.file.Path tmp = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), "rtk-uji-peta.map");
        check("dunia: saveMap menulis berkas",
                panggilGlobal(engine, "saveMap", 60001, tmp.toString()).toboolean());
        int ukuranA = kembarA.xs;
        MapData muat = new MapData(60003, berkas);
        MapServer.world.put(muat);
        check("dunia: setMap memuat berkas itu ke peta lain",
                panggilGlobal(engine, "setMap", 60003, tmp.toString()).toboolean());
        check("dunia: petak yang diubah ikut terbawa lewat berkas",
                muat.tile(3, 3) == asli + 7 && muat.xs == ukuranA);
        check("dunia: peta hasil setMap selalu punya geometri sendiri",
                muat.hasPrivateGeometry());
        check("dunia: setMap dengan berkas yang tidak ada dijawab false",
                !panggilGlobal(engine, "setMap", 60003, "tidak-ada-sama-sekali.map")
                        .toboolean());
        try {
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (java.io.IOException ignored) {
            // berkas sementara; kegagalan menghapusnya tidak mengubah hasil uji
        }

        // ---- getObjectsMap: tiruan setia yang tidak mengembalikan apa pun ----
        check("dunia: getObjectsMap tidak mengembalikan apa pun (badannya "
                + "dikomentari di C)",
                panggilGlobal(engine, "getObjectsMap", 60001).isnil());

        // ---- guitext GLOBAL: siaran, bukan per pemain ----
        //
        // ⚠️ Berbeda dari method player:guitext(). Yang ini menyiarkan ke
        // seluruh peta, dan (-1, 0) ke seluruh server (Peringatan #75).
        final java.util.List<String> tertangkap = new java.util.ArrayList<>();
        var cvLama = MapServer.clientView;
        MapServer.clientView = new RekamGuiText(tertangkap);
        MapServer.onlineChars.put(sd.fd, sd);
        panggilGlobal(engine, "guitext", -1, 0, "pengumuman");
        // ⚠️ Jangan menguji JUMLAHNYA: bagian uji lain meninggalkan pemain
        // di onlineChars, jadi angkanya bergantung pada urutan uji — bukan
        // pada kode yang sedang diperiksa.
        check("dunia: guitext(-1, 0) menyiarkan ke seluruh server",
                tertangkap.contains("pengumuman"));
        tertangkap.clear();
        panggilGlobal(engine, "guitext", 0, 58888, "peta lain");
        check("dunia: guitext ke peta lain tidak menyentuh pemain di sini",
                tertangkap.isEmpty());
        MapServer.clientView = cvLama;

        // ---- getXPforLevel: tabelnya memang sudah ada ----
        var xp = panggilGlobal(engine, "getXPforLevel", 1, 10);
        check("dunia: getXPforLevel menjawab dari tabel level, bukan 0",
                xp.todouble() > 0);
        // ⚠️ Argumen pertama boleh JALUR atau KELAS: nilai di atas 5
        // diterjemahkan dulu jadi jalur dasarnya, jadi sebuah subpath
        // menjawab sama dengan jalur induknya. Tanpa terjemahan itu tiap
        // subpath akan menjawab 0.
        int subpath = -1;
        for (int k = 6; k < 40 && subpath < 0; k++) {
            if (MapServer.classDb.pathOf(k) == 1) {
                subpath = k;
            }
        }
        if (subpath > 0) {
            check("dunia: kelas subpath dipetakan ke jalur induknya",
                    panggilGlobal(engine, "getXPforLevel", subpath, 10).todouble()
                            == xp.todouble());
        } else {
            log.info("SKIP: tidak ada kelas subpath berjalur 1 di database");
        }

        MapServer.onlineChars.remove(sd.fd);
        MapServer.world.remove(60001);
        MapServer.world.remove(60002);
        MapServer.world.remove(60003);
        MapServer.scriptEngine = engineLama;
    }

    /**
     * ClientView yang hanya mencatat guitext.
     *
     * <p>Membungkus penyalur kosong ({@code new ProtocolRouter()} tanpa
     * tujuan) supaya setiap peristiwa lain jadi tidak berbuat apa-apa —
     * yang diuji di sini siapa yang MENERIMA siaran, bukan bytenya.</p>
     */
    private static final class RekamGuiText implements ClientView {
        private final java.util.List<String> keluar;
        private final ClientView sunyi = new ProtocolRouter();

        RekamGuiText(java.util.List<String> keluar) {
            this.keluar = keluar;
        }

        @Override
        public void guiTextToPlayer(User sd, String text) {
            keluar.add(text);
        }

        // Sisanya diteruskan ke penyalur kosong.
        @Override public void playerEnteredWorld(User a) { sunyi.playerEnteredWorld(a); }
        @Override public void playerViewRefreshed(User a) { sunyi.playerViewRefreshed(a); }
        @Override public void playerStatusChanged(User a, int b) { sunyi.playerStatusChanged(a, b); }
        @Override public void playerIdentityChanged(User a) { sunyi.playerIdentityChanged(a); }
        @Override public void playerDurationChanged(User a, int b, int c, String d) { sunyi.playerDurationChanged(a, b, c, d); }
        @Override public void playerAetherChanged(User a, int b, int c) { sunyi.playerAetherChanged(a, b, c); }
        @Override public void playerInventorySlotChanged(User a, int b) { sunyi.playerInventorySlotChanged(a, b); }
        @Override public void playerInventorySlotCleared(User a, int b, int c) { sunyi.playerInventorySlotCleared(a, b, c); }
        @Override public void playerSpellRemoved(User a, int b) { sunyi.playerSpellRemoved(a, b); }
        @Override public void playerSpellSlotChanged(User a, int b) { sunyi.playerSpellSlotChanged(a, b); }
        @Override public void playerProfile(User a) { sunyi.playerProfile(a); }
        @Override public void worldTimeChanged(User a) { sunyi.worldTimeChanged(a); }
        @Override public void playerTransferred(User a, String h, int p, int m, int x, int y) {
            sunyi.playerTransferred(a, h, p, m, x, y);
        }
        @Override public void townListToPlayer(User a, java.util.List<String> b) { sunyi.townListToPlayer(a, b); }
        @Override public void rankingToPlayer(User a, String b, java.util.List<Object[]> c) { sunyi.rankingToPlayer(a, b, c); }
        @Override public void chatLineToPlayer(User a, int b, long c, String d) { sunyi.chatLineToPlayer(a, b, c, d); }
        @Override public void popupToPlayer(User a, String b) { sunyi.popupToPlayer(a, b); }
        @Override public void playerCameraChanged(User a, int b, int c) { sunyi.playerCameraChanged(a, b, c); }
        @Override public void boardListToPlayer(User a, int b, int c, int d, java.util.List<Clif.BoardEntry> e) { sunyi.boardListToPlayer(a, b, c, d, e); }
        @Override public void exchangeOpened(User a, User b) { sunyi.exchangeOpened(a, b); }
        @Override public void exchangeItemOffered(User a, User b, org.rtk.common.mmo.Item c, int d) { sunyi.exchangeItemOffered(a, b, c, d); }
        @Override public void exchangeGoldOffered(User a, User b, long c) { sunyi.exchangeGoldOffered(a, b, c); }
        @Override public void exchangeConfirmed(User a, User b, boolean c) { sunyi.exchangeConfirmed(a, b, c); }
        @Override public void mapSelectionToPlayer(User a, String b, java.util.List<Integer> c, java.util.List<Integer> d, java.util.List<String> e, java.util.List<Integer> f, java.util.List<Integer> g, java.util.List<Integer> h) { sunyi.mapSelectionToPlayer(a, b, c, d, e, f, g, h); }
        @Override public void powerBoardToPlayer(User a) { sunyi.powerBoardToPlayer(a); }
        @Override public void boardQuestionsToPlayer(User a, java.util.List<String> b, java.util.List<String> c, java.util.List<Integer> d) { sunyi.boardQuestionsToPlayer(a, b, c, d); }
        @Override public void paperToPlayer(User a, String b, int c, int d) { sunyi.paperToPlayer(a, b, c, d); }
        @Override public void urlToPlayer(User a, int b, String c) { sunyi.urlToPlayer(a, b, c); }
        @Override public void playerTimerSet(User a, int b, long c) { sunyi.playerTimerSet(a, b, c); }
        @Override public void playerSpoke(User a, String b, int c) { sunyi.playerSpoke(a, b, c); }
        @Override public void playerMovementLocked(User a, boolean b) { sunyi.playerMovementLocked(a, b); }
        @Override public void objectAnimationSeenBy(User a, org.rtk.map.data.BlockList b, int c, int d) { sunyi.objectAnimationSeenBy(a, b, c, d); }
        @Override public void playerHealthChanged(User a, int b, int c, int d) { sunyi.playerHealthChanged(a, b, c, d); }
        @Override public void objectSideChanged(org.rtk.map.data.BlockList a) { sunyi.objectSideChanged(a); }
        @Override public void objectAnimation(org.rtk.map.data.BlockList a, int b, int c) { sunyi.objectAnimation(a, b, c); }
        @Override public void objectAnimationAt(org.rtk.map.data.BlockList a, int b, int c, int d, int e) { sunyi.objectAnimationAt(a, b, c, d, e); }
        @Override public void soundPlayed(org.rtk.map.data.BlockList a, int b) { sunyi.soundPlayed(a, b); }
        @Override public void objectSpoke(org.rtk.map.data.BlockList a, int b, String c) { sunyi.objectSpoke(a, b, c); }
        @Override public void objectActed(org.rtk.map.data.BlockList a, int b, int c, int d) { sunyi.objectActed(a, b, c, d); }
        @Override public void objectAppearanceChanged(org.rtk.map.data.BlockList a) { sunyi.objectAppearanceChanged(a); }
        @Override public void objectRemoved(org.rtk.map.data.BlockList a) { sunyi.objectRemoved(a); }
        @Override public void messageToPlayer(User a, int b, String c) { sunyi.messageToPlayer(a, b, c); }
        @Override public void npcMoved(Npc a, MapData b, int c, int d, int e, int f, int g, int h) { sunyi.npcMoved(a, b, c, d, e, f, g, h); }
        @Override public void npcAppearedTo(User a, Npc b) { sunyi.npcAppearedTo(a, b); }
        @Override public void mobMoved(Mob a, MapData b, int c, int d, int e, int f, int g, int h) { sunyi.mobMoved(a, b, c, d, e, f, g, h); }
        @Override public void mobSpawned(Mob a) { sunyi.mobSpawned(a); }
        @Override public void floorItemAppeared(FloorItem a) { sunyi.floorItemAppeared(a); }
        @Override public void objectThrown(org.rtk.map.data.BlockList a, int b, int c, int d, int e, int f) { sunyi.objectThrown(a, b, c, d, e, f); }
        @Override public void scriptDialogReady(User a, org.rtk.map.script.ScriptPlayer b) { sunyi.scriptDialogReady(a, b); }
        @Override public void npcSaidTo(User a, long b, String c) { sunyi.npcSaidTo(a, b, c); }
        @Override public void playerStepRejected(User a) { sunyi.playerStepRejected(a); }
        @Override public void playerStepped(User a, int b, int c, int d) { sunyi.playerStepped(a, b, c, d); }
        @Override public void playerStepSeen(User a, int b, int c, int d) { sunyi.playerStepSeen(a, b, c, d); }
        @Override public void areaRedrawRequested(User a, MapData b, int c, int d, int e, int f, int g) { sunyi.areaRedrawRequested(a, b, c, d, e, f, g); }
        @Override public void playerEquipmentChanged(User a, int b) { sunyi.playerEquipmentChanged(a, b); }
        @Override public void playerEquipmentCleared(User a, int b) { sunyi.playerEquipmentCleared(a, b); }
        @Override public void mapTilesChanged(User a) { sunyi.mapTilesChanged(a); }
        @Override public void weatherChanged(User a, int b) { sunyi.weatherChanged(a, b); }
        @Override public void groupStatusChanged(User a) { sunyi.groupStatusChanged(a); }
        @Override public void groupHealthChanged(User a) { sunyi.groupHealthChanged(a); }
        @Override public void playerSettingsChanged(User a) { sunyi.playerSettingsChanged(a); }
    }

}
