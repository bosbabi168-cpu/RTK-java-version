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

    /** Tabel Lua 1-basis -> daftar String. */
    private static java.util.List<String> luaListString(LuaValue t) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (t == null || !t.istable()) {
            return out;
        }
        for (int i = 1; i <= t.length(); i++) {
            out.add(t.get(i).tojstring());
        }
        return out;
    }

    /** Tabel Lua 1-basis -> daftar Integer. */
    private static java.util.List<Integer> luaListInt(LuaValue t) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (t == null || !t.istable()) {
            return out;
        }
        for (int i = 1; i <= t.length(); i++) {
            out.add(t.get(i).toint());
        }
        return out;
    }

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
                    // Atribut karakter yang tersimpan (money, health, ...)
                    // dijawab pemiliknya; null berarti belum diport, jadi
                    // pencarian lanjut ke prototype / data table.
                    if (p.owner instanceof ScriptPlayer.Owner o) {
                        Long v = o.scriptGetAttr(attr);
                        if (v != null) {
                            return LuaValue.valueOf(v.doubleValue());
                        }
                    }
                    return null;
            }
        };
        player.setter = (self, attr, value) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            switch (attr) {
                case "level":
                    p.level = value.toint();
                    if (p.owner instanceof ScriptPlayer.Owner o) {
                        o.scriptSetLevel(p.level);   // tembus ke CharStatus
                    }
                    return true;
                // Catatan kesetiaan: `pcl_setattr` di C punya 164 atribut yang
                // bisa ditulis, dan x/y/m BUKAN salah satunya. Setter posisi
                // pernah ada di port ini dan salah: menulis koordinat langsung
                // membuat pemain berpindah TANPA memperbarui indeks blok peta,
                // sehingga ia hilang dari pandangan pemain lain. Skrip
                // memindahkan pemain lewat method warp(), bukan atribut.
                default:
                    if (p.owner instanceof ScriptPlayer.Owner o
                            && o.scriptSetAttr(attr, (long) value.todouble())) {
                        return true;
                    }
                    return false; // jatuh ke data table
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
        // Untuk pemain sungguhan, ketiga method barang ini menembus ke
        // CharStatus.inventory lewat ScriptPlayer.Owner — jadi barang yang
        // diberikan skrip ikut tersimpan dan terlihat klien. Peta `p.items`
        // hanya dipakai pemain tiruan di uji.
        /**
         * pcl_buy(dialog, namaBarang, harga, namaAsli): tampilkan daftar
         * jualan lalu tunggu pilihan pemain.
         *
         * <p>Klien membalas dengan <b>nama tampilan</b> barang, bukan
         * indeks — itulah yang dikembalikan ke skrip.</p>
         */
        player.addMethod("buy", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String dialog = args.optjstring(2, "");
            java.util.List<String> nama = luaListString(args.arg(3));
            java.util.List<Integer> harga = luaListInt(args.arg(4));
            var d = new ScriptPlayer.PendingDialog("buy", dialog, nama);
            d.prices = harga;
            return engine.yieldBlocking(p, d);
        });

        /**
         * pcl_sell(dialog, daftarBarang): tampilkan barang pemain yang
         * boleh dijual, lalu tunggu pilihannya.
         *
         * <p>Argumennya nama barang; yang dikirim ke klien adalah
         * <b>nomor slot</b> tempat barang itu berada di inventaris, dan
         * jawabannya juga berupa nomor slot.</p>
         */
        player.addMethod("sell", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String dialog = args.optjstring(2, "");
            java.util.List<String> nama = luaListString(args.arg(3));
            java.util.List<Integer> slots = new java.util.ArrayList<>();
            if (p.owner instanceof ScriptPlayer.Owner o) {
                slots = o.scriptItemSlots(nama);
            }
            var d = new ScriptPlayer.PendingDialog("sell", dialog, nama);
            d.prices = slots;   // dipakai ulang sebagai daftar slot
            return engine.yieldBlocking(p, d);
        });

        player.addMethod("addItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String name = args.optjstring(2, "");
            int qty = Math.max(1, args.optint(3, 1));
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptAddItem(name, qty));
            }
            p.items.merge(name, qty, Integer::sum);
            return LuaValue.TRUE;
        });
        player.addMethod("hasItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String name = args.optjstring(2, "");
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptCountItem(name));
            }
            return LuaValue.valueOf(p.items.getOrDefault(name, 0));
        });
        player.addMethod("removeItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String name = args.optjstring(2, "");
            int qty = Math.max(1, args.optint(3, 1));
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptRemoveItem(name, qty));
            }
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
            // NPC sungguhan: atribut dibaca dari objeknya lewat penyedia.
            // Skrip memakai `npc.yname` 565x dan `npc.name` ribuan kali,
            // jadi ini jalur panas, bukan pelengkap.
            if (self instanceof ScriptAttrs a) {
                LuaValue v = a.scriptAttr(attr);
                if (v != null) {
                    return v;
                }
            }
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
