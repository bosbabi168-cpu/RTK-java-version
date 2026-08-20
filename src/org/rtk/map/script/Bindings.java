package org.rtk.map.script;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * The script API bindings — the Java equivalent of the playerl_* / bll_* /
 * regl_* method families in map/sl.c.
 *
 * Scripts use ~209 distinct player methods; this file implements the
 * highest-traffic ones (dialogs, minitext, items, warp, registries) against
 * the in-memory {@link ScriptPlayer}, and registers warn-once no-ops for the
 * cosmetic packet methods. Everything still missing surfaces in the log as
 * "attempt to call nil" through the dispatcher, exactly like a missing
 * binding would in the C server.
 */
final class Bindings {

    private static final Logger log = LogManager.getLogger(Bindings.class);

    private Bindings() {
    }

    // ------------------------------------------------------------------
    // Player (playerl_staticinit)
    // ------------------------------------------------------------------

    static void definePlayer(ScriptEngine engine, ScriptClass player) {
        player.getter = (self, attr) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            switch (attr) {
                case "id": return LuaValue.valueOf(p.id);
                case "name": return LuaValue.valueOf(p.name);
                case "level": return LuaValue.valueOf(p.level);
                case "sex": return LuaValue.valueOf(p.sex);
                case "class": case "path": return LuaValue.valueOf(p.path);
                case "gm": case "gmLevel": return LuaValue.valueOf(p.gmLevel);
                case "m": case "map": return LuaValue.valueOf(p.m);
                case "x": return LuaValue.valueOf(p.x);
                case "y": return LuaValue.valueOf(p.y);
                case "registry":
                    if (p.registryUdata == null) {
                        p.registryUdata = engine.newInstance(engine.registryClass, p);
                    }
                    return p.registryUdata;
                case "registryString": case "registrystring":
                    if (p.registryStringUdata == null) {
                        p.registryStringUdata = engine.newInstance(engine.registryStringClass, p);
                    }
                    return p.registryStringUdata;
                case "npc": case "npcInt": case "npcint":
                    // exposed as "npc" by pcl_init in the C server
                    if (p.npcIntUdata == null) {
                        p.npcIntUdata = engine.newInstance(engine.npcIntClass, p);
                    }
                    return p.npcIntUdata;
                case "quest":
                    if (p.questUdata == null) {
                        p.questUdata = engine.newInstance(engine.questClass, p);
                    }
                    return p.questUdata;
                case "gameRegistry":
                    return engine.gameRegistryUdata;
                case "mapRegistry":
                    return engine.mapRegistryUdata;
                default:
                    return null; // fall through to prototype / data table
            }
        };
        player.setter = (self, attr, value) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            switch (attr) {
                case "level": p.level = value.toint(); return true;
                case "x": p.x = value.toint(); return true;
                case "y": p.y = value.toint(); return true;
                case "m": case "map": p.m = value.toint(); return true;
                default:
                    return false; // fall through to the data table
            }
        };

        // ---- messaging ----
        player.addMethod("sendMinitext", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String msg = args.optjstring(2, "");
            p.outbox.add(msg);
            log.debug("[minitext->{}] {}", p.name, msg);
            return LuaValue.NONE;
        });
        player.addMethod("talk", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            p.outbox.add("[talk] " + args.optjstring(3, args.optjstring(2, "")));
            return LuaValue.NONE;
        });

        // ---- blocking dialog PRIMITIVES (pcl_menu / pcl_dialog / pcl_input /
        // pcl_menuseq / pcl_inputseq). The content layer in
        // Accepted/player.lua builds menuString/dialogSeq/etc. on top of
        // these, exactly like on the C server:
        //   menu   -> resumed with the selected option INDEX (number)
        //   dialog -> resumed with "next" / "previous" / "quit" / ...
        //   input  -> resumed with the entered string
        player.addMethod("menu", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String topic = args.optjstring(2, "");
            List<String> options = tableToStrings(args.arg(3));
            p.outbox.add("[menu] " + topic + " " + options);
            return engine.yieldBlocking(p,
                    new ScriptPlayer.PendingDialog("menu", topic, options));
        });
        player.addMethod("menuSeq", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String topic = args.optjstring(2, "");
            List<String> options = tableToStrings(args.arg(3));
            p.outbox.add("[menuSeq] " + topic + " " + options);
            return engine.yieldBlocking(p,
                    new ScriptPlayer.PendingDialog("menuSeq", topic, options));
        });
        player.addMethod("dialog", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String message = args.optjstring(2, "");
            List<String> options = tableToStrings(args.arg(3));
            p.outbox.add("[dialog] " + message);
            return engine.yieldBlocking(p,
                    new ScriptPlayer.PendingDialog("dialog", message, options));
        });
        player.addMethod("input", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String message = args.optjstring(2, "");
            return engine.yieldBlocking(p,
                    new ScriptPlayer.PendingDialog("input", message, null));
        });
        player.addMethod("inputSeq", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String message = args.optjstring(2, "");
            List<String> options = tableToStrings(args.arg(5));
            return engine.yieldBlocking(p,
                    new ScriptPlayer.PendingDialog("inputSeq", message, options));
        });

        // ---- world ----
        player.addMethod("warp", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            p.m = args.optint(2, p.m);
            p.x = args.optint(3, p.x);
            p.y = args.optint(4, p.y);
            log.debug("[warp] {} -> map {} ({},{})", p.name, p.m, p.x, p.y);
            return LuaValue.NONE;
        });
        player.addMethod("getObjectsInCell", (self, args) -> new LuaTable());

        // ---- items (in-memory until the item engine is ported) ----
        player.addMethod("addItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            p.items.merge(args.optjstring(2, ""), Math.max(1, args.optint(3, 1)), Integer::sum);
            return LuaValue.TRUE;
        });
        player.addMethod("hasItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            return LuaValue.valueOf(p.items.getOrDefault(args.optjstring(2, ""), 0));
        });
        player.addMethod("removeItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String name = args.optjstring(2, "");
            int qty = Math.max(1, args.optint(3, 1));
            int have = p.items.getOrDefault(name, 0);
            if (have <= qty) {
                p.items.remove(name);
            } else {
                p.items.put(name, have - qty);
            }
            return LuaValue.TRUE;
        });

        // ---- cosmetic packet methods: valid calls, nothing to show yet ----
        for (String name : new String[]{"sendStatus", "sendAnimation", "sendAnimationXY",
                "sendAction", "playSound", "updateState", "setDuration", "setAether",
                "sendSound", "sendHealth", "refresh", "updateStatus"}) {
            player.addMethod(name, (self, args) -> {
                engine.warnStub("player:" + name + "()");
                return LuaValue.NONE;
            });
        }
    }

    // ------------------------------------------------------------------
    // NPC / Mob (bll_* block-list methods)
    // ------------------------------------------------------------------

    static void defineBlockList(ScriptEngine engine, ScriptClass klass) {
        klass.getter = (self, attr) -> {
            if (self instanceof ScriptEngine.GameObject && "id".equals(attr)) {
                ScriptEngine.GameObject o = (ScriptEngine.GameObject) self;
                if (o.ctorArgs.narg() >= 1 && o.ctorArgs.arg1().isnumber()) {
                    return o.ctorArgs.arg1();
                }
            }
            return null;
        };
        klass.addMethod("talk", (self, args) -> {
            log.debug("[{}:talk] {}", klass.name, args.optjstring(3, args.optjstring(2, "")));
            return LuaValue.NONE;
        });
        for (String name : new String[]{"playSound", "sendAnimation", "sendAnimationXY",
                "spawn", "delete", "deliddb"}) {
            klass.addMethod(name, (self, args) -> {
                engine.warnStub(klass.name + ":" + name + "()");
                return LuaValue.NONE;
            });
        }
    }

    // ------------------------------------------------------------------
    // Registries (regl_* / reglstring_* / npcintregl_* / questregl_*)
    // ------------------------------------------------------------------

    interface RegGet {
        LuaValue get(Object self, String key);
    }

    interface RegSet {
        void set(Object self, String key, LuaValue value);
    }

    static ScriptClass defineRegistry(ScriptEngine engine, String name, RegGet get, RegSet set) {
        ScriptClass klass = new ScriptClass(name);
        klass.getter = (self, attr) -> get.get(self, attr);
        klass.setter = (self, attr, value) -> {
            set.set(self, attr, value);
            return true;
        };
        return klass;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Concatenates the string entries of a dialog table (or a bare string). */
    private static String flattenStrings(LuaValue v) {
        if (v.isstring()) {
            return v.tojstring();
        }
        if (!v.istable()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        LuaTable t = v.checktable();
        for (int i = 1; i <= t.length(); i++) {
            LuaValue e = t.get(i);
            if (e.isstring() && !e.isnumber()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(e.tojstring());
            }
        }
        return sb.toString();
    }

    private static List<String> tableToStrings(LuaValue v) {
        List<String> out = new ArrayList<>();
        if (v.istable()) {
            LuaTable t = v.checktable();
            for (int i = 1; i <= t.length(); i++) {
                out.add(t.get(i).tojstring());
            }
        }
        return out;
    }
}
