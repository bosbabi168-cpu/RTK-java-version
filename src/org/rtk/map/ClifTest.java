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

        // --- 0x10 mengosongkan slot di SERVER, bukan cuma di klien ---
        sd.status.inventory.clear();
        sd.addItemById(7102, 3, -1);
        drain(s);
        Clif.sendDelItem(sd, 0, 6);
        p = decrypt(drain(s), sd);
        check("delItem: opcode 0x10", (p[3] & 0xFF) == 0x10);
        check("delItem: slot 1-basis di [5]", (p[5] & 0xFF) == 1);
        check("delItem: alasan di [6]", (p[6] & 0xFF) == 6);
        check("delItem: slotnya ikut kosong di server",
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

        Npc temp = MapServer.npcs.byName("TrapNpc");
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
                org.rtk.map.proto.Wire.isRetroTk(retro));

        org.rtk.common.Session s = MapServer.net.openTestSession(901);
        s.feedTest(new Bingkai(org.rtk.map.proto.Wire.OP_PING).bytes());
        check("proto: bingkai RTK2 tidak tertukar dengan RetroTK",
                !org.rtk.map.proto.Wire.isRetroTk(s));
        check("proto: bingkai kosong panjangnya 4 byte",
                org.rtk.map.proto.Wire.frameLength(s) == 4);
        check("proto: opcode terbaca dari ladangnya",
                org.rtk.map.proto.Wire.opcode(s) == org.rtk.map.proto.Wire.OP_PING);

        // Batas MAX_FRAME ikut menjaga pemilihan protokol: selama ia di bawah
        // 43.520, byte tinggi ladang panjang tidak akan pernah 0xAA.
        check("proto: MAX_FRAME menjaga pembeda protokol tetap aman",
                org.rtk.map.proto.Wire.MAX_FRAME < 0xAA00);

        // ---- bingkai yang belum lengkap ----
        org.rtk.common.Session pecah = MapServer.net.openTestSession(902);
        byte[] utuh = new Bingkai(org.rtk.map.proto.Wire.OP_CLICK).u64(42).bytes();
        pecah.feedTest(java.util.Arrays.copyOf(utuh, 5));
        check("proto: bingkai separuh dijawab 0, bukan dibaca",
                org.rtk.map.proto.Wire.frameLength(pecah) == 0);
        pecah.feedTest(java.util.Arrays.copyOfRange(utuh, 5, utuh.length));
        check("proto: setelah sisanya tiba, panjangnya utuh",
                org.rtk.map.proto.Wire.frameLength(pecah) == utuh.length);

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

        check("proto: ATTACK tanpa sasaran dari klien",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ATTACK), sd, rekam)
                && rekam.terakhir.equals("attack") && rekam.arg.isEmpty());

        // ---- jawaban dialog: enam ragam, dua pintu terpisah ----
        check("proto: jawaban ragam 1 jadi pilihan menu",
                kirim(new Bingkai(org.rtk.map.proto.Wire.OP_ANSWER_MENU).u8(1).u16(3),
                        sd, rekam)
                && rekam.terakhir.equals("menu")
                && ((ClientCommands.Answer) rekam.arg.get(0)).choice == 3);
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

        final String[] diperkenalkan = {null};
        kirimTanpaPemain(new Bingkai(org.rtk.map.proto.Wire.OP_HELLO)
                        .u32(org.rtk.map.proto.Wire.MAGIC)
                        .u16(org.rtk.map.proto.Wire.VERSION)
                        .str("Tester").bytes(),
                sepi, (fd, sesi, nama) -> {
                    diperkenalkan[0] = nama;
                    return true;
                });
        check("proto: HELLO meneruskan nama karakter ke jalur autentikasi",
                "Tester".equals(diperkenalkan[0]));

        check("proto: magic yang salah ditolak",
                malformedHello(new Bingkai(org.rtk.map.proto.Wire.OP_HELLO)
                        .u32(0xDEADBEEFL).u16(1).str("Tester").bytes()));
        check("proto: versi protokol yang berbeda ditolak, bukan ditebak",
                malformedHello(new Bingkai(org.rtk.map.proto.Wire.OP_HELLO)
                        .u32(org.rtk.map.proto.Wire.MAGIC)
                        .u16(org.rtk.map.proto.Wire.VERSION + 1)
                        .str("Tester").bytes()));

        MapServer.scriptEngine = engineLama;
    }

    // ---- alat bantu protoTest ----

    private static int fdUji = 910;

    /** Suapkan satu bingkai ke pembaca; true bila selesai tanpa keluhan. */
    private static boolean kirim(Bingkai b, User sd, Rekam rekam) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(b.bytes());
        int len = org.rtk.map.proto.Wire.frameLength(s);
        if (len == 0) {
            return false;
        }
        org.rtk.map.proto.Inbound.dispatch(s.fd, s, sd, len, rekam, (a, c, n) -> true);
        return true;
    }

    private static void kirimTanpaPemain(byte[] bingkai, Rekam rekam,
                                         org.rtk.map.proto.Inbound.Handshake h) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        int len = org.rtk.map.proto.Wire.frameLength(s);
        org.rtk.map.proto.Inbound.dispatch(s.fd, s, null, len, rekam,
                h != null ? h : (a, c, n) -> true);
    }

    private static boolean malformed(byte[] bingkai) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        try {
            org.rtk.map.proto.Wire.frameLength(s);
            return false;
        } catch (org.rtk.map.proto.Wire.Malformed e) {
            return true;
        }
    }

    private static boolean malformedDispatch(byte[] bingkai, User sd) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        try {
            int len = org.rtk.map.proto.Wire.frameLength(s);
            org.rtk.map.proto.Inbound.dispatch(s.fd, s, sd, len, new Rekam(),
                    (a, c, n) -> true);
            return false;
        } catch (org.rtk.map.proto.Wire.Malformed e) {
            return true;
        }
    }

    private static boolean malformedHello(byte[] bingkai) {
        return malformedDispatch2(bingkai);
    }

    private static boolean malformedDispatch2(byte[] bingkai) {
        org.rtk.common.Session s = MapServer.net.openTestSession(fdUji++);
        s.feedTest(bingkai);
        try {
            int len = org.rtk.map.proto.Wire.frameLength(s);
            org.rtk.map.proto.Inbound.dispatch(s.fd, s, null, len, new Rekam(),
                    (a, c, n) -> true);
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
                "halo semua".equals(sd.scriptGetSpeech()));

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

}
