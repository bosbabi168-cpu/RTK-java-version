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
        // Urutan mengikuti intif.c: ack, time, id, mapinfo, status, lalu
        // refresh (mapinfo+xy+area+trigger), xy, dan sapuan area pandang.
        // Klien peka terhadap urutan ini, jadi ujinya memeriksa urutan
        // persis, bukan sekadar "paketnya ada".
        int[] want = {0x1E, 0x20, 0x05, 0x15, 0x08, 0x15, 0x04, 0x22, 0x04};
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
        check("sembilan paket masuk dunia terkirim", seq.size() == want.length);
        check("urutan opcode 1E,20,05,15,08,15,04,22,04", order);
        check("status (0x08) dikirim saat masuk dunia",
                seq.stream().anyMatch(o -> o[0] == 0x08));

        dialogTest(map, sd);
        mapDataTest(map, sd);
        statusTest(sd);
        lookTest(map, sd);

        walkTest(map, sd);
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
}
