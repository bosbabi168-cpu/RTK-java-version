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

    public static void main(String[] args) {
        String luaRoot = args.length > 0 ? args[0] : "../RTK-Server/rtklua";

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

        if (failures == 0) {
            log.info("ALL TESTS PASSED");
        } else {
            log.error("{} TEST(S) FAILED", failures);
            System.exit(1);
        }
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

    private static void check(String what, boolean ok) {
        if (ok) {
            log.info("PASS: {}", what);
        } else {
            log.error("FAIL: {}", what);
            failures++;
        }
    }
}
