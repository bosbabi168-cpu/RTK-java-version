package org.rtk.map.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaValue;

/**
 * Standalone harness for the scripting subsystem (ant run-scripttest).
 *
 * Phase 1 boots the engine against the real rtklua tree and reports how many
 * of the original scripts load. Phase 2 runs an embedded NPC-style script and
 * drives a full blocking-dialog interaction (async -> menu yield -> resume ->
 * dialog yield -> resume -> registry write) with a fake player, asserting
 * each step.
 */
public final class ScriptTest {

    private static final Logger log = LogManager.getLogger(ScriptTest.class);
    private static int failures;

    private ScriptTest() {
    }

    /** Baca lua_path dari conf/map.conf bila ada, jatuh ke lua.path di properties. */
    private static String resolveLuaPath() {
        String[] fromConf = {null};
        org.rtk.common.Config.read("conf/map.conf", (k, v) -> {
            if ("lua_path".equalsIgnoreCase(k)) {
                fromConf[0] = v;
            }
        });
        return fromConf[0] != null ? fromConf[0] : org.rtk.common.Props.get("lua.path", "luascript");
    }

    public static void main(String[] args) {
        String luaRoot = args.length > 0 ? args[0] : resolveLuaPath();

        log.info("=== Phase 1: loading original rtklua scripts from {} ===", luaRoot);
        ScriptEngine engine = new ScriptEngine();
        int errors = engine.init(luaRoot);
        if (errors < 0) {
            log.error("rtklua tree not found at {} - pass the path as first argument", luaRoot);
            System.exit(1);
        }
        log.info("Result: {} files loaded OK, {} files with errors",
                engine.loadedFiles(), engine.loadErrors());
        int shown = 0;
        for (String err : engine.loadErrorList()) {
            if (shown++ >= 10) {
                log.info("  ... and {} more (see debug log)", engine.loadErrors() - 10);
                break;
            }
            log.info("  ERR {}", err);
        }
        check("all rtklua files load without errors", engine.loadErrors() == 0);

        log.info("=== Phase 2: blocking dialog round-trip (through Accepted/player.lua) ===");
        runDialogTest(engine);

        log.info("=== Phase 3: kalender dunia & binding bernilai tetap (R2) ===");
        runWorldTest(engine);

        log.info("=== Phase 4: kait GLOBAL yang dipanggil server (R5) ===");
        runGlobalHookTest(engine);

        if (failures == 0) {
            log.info("ALL TESTS PASSED");
        } else {
            log.error("{} TEST(S) FAILED", failures);
            System.exit(1);
        }
    }

    /**
     * R2: binding yang dulu menjawab dengan nilai TETAP atau dengan jam
     * dinding.
     *
     * <p>⚠️ Nilai tetap tidak pernah muncul di laporan stub {@code luaaudit}
     * — bagi audit ia "terdefinisi". Karena itu ia harus diuji di sini,
     * dengan nilai yang <b>hanya bisa benar kalau sumbernya nyata</b>.</p>
     */
    private static void runWorldTest(ScriptEngine engine) {
        // getXPforLevel membaca tabel pengalaman; gerbang ini tidak
        // menyalakan map server, jadi tabelnya dimuat sendiri di sini.
        int baris = org.rtk.map.MapServer.classDb.load(
                org.rtk.map.MapServer.dbPath);
        check("tabel pengalaman (db/level_db.txt) terbaca", baris > 0);

        int jamLama = org.rtk.map.WorldTime.hour;
        int hariLama = org.rtk.map.WorldTime.day;
        int musimLama = org.rtk.map.WorldTime.season;
        int tahunLama = org.rtk.map.WorldTime.year;

        // ⚠️ Diuji dengan DUA nilai yang berbeda, bukan satu.
        // Kontrol negatif membuktikan kenapa: dengan satu nilai saja, versi
        // lama yang menghitung musim dari bulan komputer LULUS — bulan
        // Agustus kebetulan menghasilkan angka yang sama. Sebuah jawaban
        // tetap atau jawaban jam dinding tidak bisa mengikuti dua nilai
        // yang berbeda.
        for (int[] uji : new int[][] {{13, 40, 3, 6}, {2, 7, 1, 91}}) {
            org.rtk.map.WorldTime.hour = uji[0];
            org.rtk.map.WorldTime.day = uji[1];
            org.rtk.map.WorldTime.season = uji[2];
            org.rtk.map.WorldTime.year = uji[3];
            check("curTime menjawab jam DUNIA (" + uji[0] + ")",
                    engine.globals().load("return curTime()").call().toint() == uji[0]);
            check("curDay menjawab hari dunia (" + uji[1] + ")",
                    engine.globals().load("return curDay()").call().toint() == uji[1]);
            check("curSeason menjawab musim dunia (" + uji[2] + ")",
                    engine.globals().load("return curSeason()").call().toint() == uji[2]);
            check("curYear menjawab tahun dunia (" + uji[3] + ")",
                    engine.globals().load("return curYear()").call().toint() == uji[3]);
        }
        // Kontrol: keluarga real* TETAP memakai jam sungguhan.
        int jamNyata = java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY);
        check("realHour tetap memakai jam sungguhan",
                engine.globals().load("return realHour()").call().toint() == jamNyata);

        org.rtk.map.WorldTime.hour = jamLama;
        org.rtk.map.WorldTime.day = hariLama;
        org.rtk.map.WorldTime.season = musimLama;
        org.rtk.map.WorldTime.year = tahunLama;

        check("curServer menjawab id server dari conf, bukan 0 tetap",
                engine.globals().load("return curServer()").call().toint()
                        == org.rtk.map.MapServer.serverId);

        // getXPforLevel: tabelnya dari db/level_db.txt. Tingkat 1 pasti
        // punya ambang > 0; kalau bindingnya masih 0 tetap, ini merah.
        long xp = engine.globals().load("return getXPforLevel(1, 5)").call().tolong();
        check("getXPforLevel membaca tabel pengalaman, bukan 0 tetap", xp > 0);
        long xpTinggi = engine.globals().load("return getXPforLevel(1, 20)").call().tolong();
        check("ambang tingkat 20 lebih besar daripada tingkat 5", xpTinggi > xp);
        // ⚠️ path > 5 adalah KELAS dan harus diterjemahkan lebih dulu.
        check("kelas (>5) diterjemahkan ke jalur, bukan dipakai mentah",
                engine.globals().load("return getXPforLevel(6, 5)").call().tolong() > 0);
    }

    private static void runDialogTest(ScriptEngine engine) {
        // An NPC script in the exact style of rtklua content. menuString and
        // dialogSeq here are the REAL implementations from
        // Accepted/player.lua, running on top of the Java menu/dialog
        // primitives — the same layering as the C server.
        String script =
            "TestNpc = {}\n"
          + "TestNpc.click = async(function(player)\n"
          + "    player:sendMinitext(\"Welcome, \" .. player.name .. \"!\")\n"
          + "    local choice = player:menuString(\"How can I help you?\", {\"Buy\", \"Sell\", \"Leave\"})\n"
          + "    if choice == \"Buy\" then\n"
          + "        player:dialogSeq({0, \"A fine choice.\", \"Here you go.\"}, 0)\n"
          + "        player:addItem(\"amber\", 3)\n"
          + "        player.registry[\"testFlag\"] = player:hasItem(\"amber\") + 39\n"
          + "        player:sendMinitext(\"Done: \" .. player.registry[\"testFlag\"])\n"
          + "    end\n"
          + "end)\n";
        engine.globals().load(script, "@test-npc").call();

        ScriptPlayer p = new ScriptPlayer(1, "Tester");

        // click the NPC: script runs until the menu primitive and yields
        boolean dispatched = engine.doScript("TestNpc", "click", engine.playerRef(p));
        check("dispatch reached TestNpc.click", dispatched);
        check("script is now waiting on a menu", p.pendingDialog != null
                && "menu".equals(p.pendingDialog.kind));
        check("menu offers 3 options", p.pendingDialog != null
                && p.pendingDialog.options != null && p.pendingDialog.options.size() == 3);
        check("welcome minitext was sent", p.outbox.stream().anyMatch(s -> s.contains("Welcome, Tester!")));

        // the client picks option 1 ("Buy"): player.lua's menuString maps the
        // index to the option string, then dialogSeq shows page 1 of 2
        engine.resume(p, LuaValue.valueOf(1));
        check("script is now waiting on a dialog page", p.pendingDialog != null
                && "dialog".equals(p.pendingDialog.kind));
        check("dialog shows the first page", p.pendingDialog != null
                && p.pendingDialog.message.contains("A fine choice."));

        // the client clicks "next" twice to page through and finish
        engine.resume(p, LuaValue.valueOf("next"));
        check("dialog advanced to the second page", p.pendingDialog != null
                && p.pendingDialog.message.contains("Here you go."));
        engine.resume(p, LuaValue.valueOf("next"));

        check("coroutine finished and was freed", p.coroutine == null);
        check("item was granted", p.items.getOrDefault("amber", 0) == 3);
        check("registry write persisted (3 + 39 = 42)", p.registry.getOrDefault("testFlag", 0) == 42);
        check("final minitext used the registry value",
                p.outbox.stream().anyMatch(s -> s.contains("Done: 42")));

        // second click while idle must work again (coroutine slot was freed)
        engine.doScript("TestNpc", "click", engine.playerRef(p));
        check("player can start a new interaction", p.pendingDialog != null);
        engine.resume(p, LuaValue.valueOf(3)); // "Leave"
        check("choosing Leave ends the script cleanly", p.coroutine == null);
    }

    /**
     * Kait GLOBAL yang dipanggil langsung oleh server, bukan lewat NPC.
     *
     * <p>⚠️ {@code ScriptEngine.doScript()} mengembalikan <b>false tanpa
     * suara</b> bila globalnya tidak ada — persis seperti kait yang berjalan
     * lalu tidak melakukan apa-apa. Satu nama yang salah eja di sisi Java
     * karena itu membuat sebuah fitur mati total tanpa satu baris log pun.
     * Nama-nama di bawah dipanggil dari {@code MapCommands}; kalau salah
     * satunya hilang, di sinilah ia harus merah.</p>
     */
    private static void runGlobalHookTest(ScriptEngine engine) {
        String[] kait = {"onPickUp", "onLook", "onTurn", "onGetExp"};
        for (String nama : kait) {
            check("kait global '" + nama + "' ada sebagai fungsi Lua",
                    !engine.globals().get(nama).isnil());
        }
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            log.info("PASS: {}", what);
        } else {
            log.error("FAIL: {}", what);
            failures++;
        }
    }
}
