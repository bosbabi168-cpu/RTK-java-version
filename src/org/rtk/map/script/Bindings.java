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

    /**
     * Beberapa binding di C menerima <b>nama ATAU angka</b> di posisi yang
     * sama ({@code lua_isnumber} dulu, kalau bukan baru {@code lua_tostring}).
     * Helper ini menyeragamkannya jadi String; sisi User yang memilah.
     * Mengembalikan "" bila argumennya nil — di C itu berarti "semua"
     * untuk {@code flushKills}.
     */
    /**
     * Bongkar benda permainan dari argumen Lua — padanan
     * {@code typel_topointer()} di C.
     *
     * <p>Pemain terbungkus dua lapis (ScriptInstance -&gt; ScriptPlayer -&gt;
     * User), NPC dan mob hanya satu lapis. Mengembalikan null bila
     * argumennya bukan benda.</p>
     */
    private static Object bendaDari(LuaValue v) {
        if (!(v instanceof ScriptInstance si)) {
            return null;
        }
        Object o = si.self;
        if (o instanceof ScriptPlayer p) {
            return p.owner;
        }
        return o;
    }

    private static String namaAtauAngka(Varargs args, int i) {
        LuaValue v = args.arg(i);
        if (v.isnil()) {
            return "";
        }
        return v.isnumber() ? String.valueOf(v.tolong()) : v.tojstring();
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
        // bll_extendproto dipasang juga ke prototipe Player di C, jadi
        // pemain punya keluarga getObjects* yang sama persis dengan NPC & mob.
        defineObjectQueries(engine, player);
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
                    // mapregl_pushinst(state, sd): terikat ke PEMAINNYA,
                    // sehingga registry yang terbaca milik peta tempat ia
                    // berdiri sekarang.
                    if (p.mapRegistryUdata == null) {
                        p.mapRegistryUdata =
                                engine.newInstance(engine.mapRegistryClass, p);
                    }
                    return p.mapRegistryUdata;
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
        /**
         * pcl_getobjectsincell(m, x, y, tipe): benda di satu petak.
         *
         * <p>Inilah cara skrip pertarungan menemukan lawan
         * ({@code getObjectsInCell(..., BL_MOB)} di `swing.lua`), jadi
         * mengembalikan tabel kosong berarti tidak ada yang bisa dipukul.
         * Hasilnya tabel Lua 1-basis berisi objek aslinya, bukan salinan —
         * skrip menulis balik ke situ.</p>
         */
        /**
         * pcl_addthreat(idMob, kerusakan): catat ancaman pemain pada mob.
         *
         * <p>Dipakai skrip pertarungan setiap kali pukulan mendarat. Yang
         * dicatat <b>akumulasi</b> kerusakan, bukan pukulan terakhir —
         * itulah yang membuat mob mengejar penyerang terbesar, bukan yang
         * kebetulan memukul paling akhir.</p>
         */
        /**
         * pcl_calcstat(): hitung ulang nilai turunan pemain.
         *
         * <p>Dipanggil skrip **249 kali** — setiap kali perlengkapan atau
         * buff berubah. Isinya: kembalikan nilai turunan ke nilai dasar
         * lalu jumlahkan bonus seluruh perlengkapan yang dikenakan.</p>
         */
        /**
         * bll_addnpc(nama, m, x, y, subtype, timer, durasi, owner, moveTime):
         * lahirkan NPC sementara.
         *
         * <p>Argumen ke-9 di C dipakai <b>dua kali</b> — sebagai
         * {@code movetime} bila angka, dan sebagai nama tampilan bila teks
         * ({@code lua_tonumber} pada teks menghasilkan 0). Kelonggaran itu
         * ditiru di sini supaya skrip lama tetap berjalan.</p>
         */
        player.addMethod("addNPC", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (!(p.owner instanceof ScriptPlayer.Owner o)) {
                return LuaValue.NONE;
            }
            var arg9 = args.arg(10);   // arg(1) = self
            long moveTime = arg9.isnumber() ? (long) arg9.todouble() : 0;
            String namaTampilan = arg9.isstring() && !arg9.isnumber()
                    ? arg9.tojstring() : "";
            o.scriptAddNpc(args.optjstring(2, ""),
                    args.optint(3, 0), args.optint(4, 0), args.optint(5, 0),
                    args.optint(6, 0),
                    (long) args.optdouble(7, 0), (long) args.optdouble(8, 0),
                    (long) args.optdouble(9, 0), moveTime, namaTampilan);
            return LuaValue.NONE;
        });

        // ---- mantra ----
        player.addMethod("addSpell", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String v = args.arg(2).isnumber()
                    ? String.valueOf(args.optint(2, 0)) : args.optjstring(2, "");
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptAddSpell(v));
            }
            return LuaValue.FALSE;
        });
        player.addMethod("hasSpell", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            String v = args.arg(2).isnumber()
                    ? String.valueOf(args.optint(2, 0)) : args.optjstring(2, "");
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptHasSpell(v));
            }
            return LuaValue.FALSE;
        });

        // ---- serangan jarak dekat ----
        // pcl_swingtarget(sasaran): seluruh pipeline pukulan. Sasarannya
        // objek Lua (mob atau pemain), bukan id — di C:
        // typel_topointer(state, sl_memberarg(1)).
        player.addMethod("swingTarget", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (!(p.owner instanceof org.rtk.map.User sd)) {
                return LuaValue.NONE;
            }
            Object sasaran = bendaDari(args.arg(2));
            if (sasaran instanceof org.rtk.map.data.BlockList bl) {
                org.rtk.map.Combat.swingAt(sd, bl);
            }
            return LuaValue.NONE;
        });

        // ---- ruang inventaris ----
        player.addMethod("hasSpace", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptHasSpace(
                        namaAtauAngka(args, 2), args.optint(3, 0), args.optlong(4, 0)));
            }
            return LuaValue.FALSE;
        });

        // ---- ancaman & mantra ----
        player.addMethod("setThreat", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                o.scriptSetThreat(args.optlong(2, 0), args.optlong(3, 0));
            }
            return LuaValue.NIL;
        });
        player.addMethod("removeSpell", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                o.scriptRemoveSpell(namaAtauAngka(args, 2));
            }
            return LuaValue.NIL;
        });

        // ---- barang di slot (biteml_pushinst) ----
        // C mengembalikan objek BoundItem bila slotnya terisi, nil bila
        // kosong. Skrip mengandalkan pembedaan itu (`if item then ...`),
        // jadi jangan pernah mengembalikan objek kosong sebagai ganti nil.
        player.addMethod("getEquippedItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                org.rtk.common.mmo.Item it = o.scriptEquippedItem(args.optint(2, 0));
                if (it != null) {
                    return engine.newInstance(engine.boundItemClass, new ScriptItem(it));
                }
            }
            return LuaValue.NIL;
        });
        player.addMethod("getInventoryItem", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                org.rtk.common.mmo.Item it = o.scriptInventoryItem(args.optint(2, 0));
                if (it != null) {
                    return engine.newInstance(engine.boundItemClass, new ScriptItem(it));
                }
            }
            return LuaValue.NIL;
        });

        // ---- durasi mantra ----
        player.addMethod("hasDuration", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptHasDuration(args.optjstring(2, "")));
            }
            return LuaValue.FALSE;
        });

        // ---- hitungan bunuh mob ----
        // C menerima nama ATAU angka di argumen pertama; keduanya dilewatkan
        // sebagai String lalu dipilah di User.mobTypeId().
        player.addMethod("killCount", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptKillCount(namaAtauAngka(args, 2)));
            }
            return LuaValue.valueOf(0);
        });
        player.addMethod("setKillCount", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                o.scriptSetKillCount(namaAtauAngka(args, 2), args.optlong(3, 0));
            }
            return LuaValue.NIL;
        });
        player.addMethod("flushKills", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                // tanpa argumen = hapus semua (lua_isnil di C)
                o.scriptFlushKills(namaAtauAngka(args, 2));
            }
            return LuaValue.NIL;
        });

        // ---- legenda ----
        // Urutan argumen di C: addLegend(text, name, icon, color, tchaid).
        player.addMethod("addLegend", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                o.scriptAddLegend(args.optjstring(2, ""), args.optjstring(3, ""),
                        args.optint(4, 0), args.optint(5, 0), args.optlong(6, 0));
            }
            return LuaValue.NIL;
        });
        player.addMethod("hasLegend", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptHasLegend(args.optjstring(2, "")));
            }
            return LuaValue.FALSE;
        });
        player.addMethod("removeLegendbyName", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                o.scriptRemoveLegendByName(args.optjstring(2, ""));
            }
            return LuaValue.NIL;
        });

        // ---- bank ----
        player.addMethod("bankDeposit", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptBankDeposit(
                        args.optjstring(2, ""), Math.max(1, args.optint(3, 1))));
            }
            return LuaValue.FALSE;
        });
        player.addMethod("bankWithdraw", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                return LuaValue.valueOf(o.scriptBankWithdraw(
                        args.optjstring(2, ""), Math.max(1, args.optint(3, 1))));
            }
            return LuaValue.valueOf(0);
        });

        player.addMethod("calcStat", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof ScriptPlayer.Owner o) {
                o.scriptCalcStat();
            }
            return LuaValue.NONE;
        });

        player.addMethod("addThreat", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            long mobId = (long) args.optdouble(2, 0);
            long dmg = (long) args.optdouble(3, 0);
            if (p.owner instanceof ScriptPlayer.Owner o) {
                o.scriptAddThreat(mobId, dmg);
            }
            return LuaValue.NONE;
        });

        player.addMethod("sendSide", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.data.BlockList bl) {
                org.rtk.map.MapServer.clientView.objectSideChanged(bl);
            }
            return LuaValue.NONE;
        });


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
        player.addMethod("sendAnimation", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.data.BlockList bl) {
                org.rtk.map.MapServer.clientView.objectAnimation(bl,
                        args.optint(2, 0), args.optint(3, 0));
            }
            return LuaValue.NONE;
        });
        player.addMethod("sendAnimationXY", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.data.BlockList bl) {
                org.rtk.map.MapServer.clientView.objectAnimationAt(bl, args.optint(2, 0),
                        args.optint(5, 0), args.optint(3, 0), args.optint(4, 0));
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_sendstatus(): kirim ulang panel status ke klien.
         *
         * <p>Method skrip yang paling sering dipakai (1.100+ pemakaian) —
         * setiap kali nyawa, emas, atau nilai apa pun berubah, skrip
         * memanggil ini supaya angkanya ikut berganti di layar. Bendera
         * yang dipakai sama dengan C:
         * {@code SFLAG_FULLSTATS | SFLAG_HPMP | SFLAG_XPMONEY}.</p>
         */
        player.addMethod("sendStatus", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.User u) {
                org.rtk.map.MapServer.clientView.playerStatusChanged(u,
                        org.rtk.map.Clif.SFLAG_ALL);
            }
            return LuaValue.NONE;
        });

        for (String name : new String[]{
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
        defineObjectQueries(engine, klass);
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
            // mapregl_pushinst / gameregl_pushinst di npcl_getattr &
            // mobl_getattr: NPC dan mob juga membaca registry peta tempat
            // mereka berdiri (`npc.mapRegistry[...]` dipakai skrip event).
            if ("mapRegistry".equals(attr)) {
                return engine.newInstance(engine.mapRegistryClass, self);
            }
            if ("gameRegistry".equals(attr)) {
                return engine.gameRegistryUdata;
            }
            if (self instanceof ScriptEngine.GameObject && "id".equals(attr)) {
                ScriptEngine.GameObject o = (ScriptEngine.GameObject) self;
                if (o.ctorArgs.narg() >= 1 && o.ctorArgs.arg1().isnumber()) {
                    return o.ctorArgs.arg1();
                }
            }
            return null;
        };
        klass.setter = (self, attr, value) -> {
            // Mob mengubah nyawanya sendiri lewat sini — itulah cara skrip
            // pertarungan melukai musuh.
            return self instanceof ScriptAttrs a && a.scriptSetAttr(attr, value);
        };
        /**
         * mobl_callbase(namaKait): panggil kait skrip milik mob ini,
         * dengan mob dan <b>penyerangnya</b> sebagai argumen.
         *
         * <p>Dipakai `Accepted/Mobs/mob.lua` untuk MobAI tipe 4 — mob yang
         * memakai skripnya sendiri sebagai AI. Bila tidak ada penyerang,
         * C mengirim <b>mob itu sendiri</b> sebagai argumen kedua, bukan
         * nil — ditiru di sini supaya skrip tidak perlu mengeceknya.</p>
         */
        /**
         * npcl_move() -> npc_move(): NPC melangkah satu petak ke arah
         * hadapnya. Mengembalikan 1 bila jadi berpindah, 0 bila tertahan —
         * skrip AI memakai nilai itu untuk memutuskan berbelok.
         */
        klass.addMethod("move", (self, args) -> {
            if (self instanceof org.rtk.map.Npc nd) {
                return LuaValue.valueOf(org.rtk.map.NpcRegistry.move(nd) ? 1 : 0);
            }
            return LuaValue.valueOf(0);
        });

        /** npcl_warp(m, x, y) -> npc_warp(): pindahkan NPC seketika. */
        klass.addMethod("warp", (self, args) -> {
            if (self instanceof org.rtk.map.Npc nd) {
                org.rtk.map.NpcRegistry.warp(nd, args.optint(2, nd.m),
                        args.optint(3, nd.x), args.optint(4, nd.y));
            }
            return LuaValue.NONE;
        });

        /**
         * clif_sendside(): benda ini berganti arah hadap — beri tahu
         * sekitar. Dipanggil skrip AI NPC setiap kali berbelok.
         */
        klass.addMethod("sendSide", (self, args) -> {
            if (self instanceof org.rtk.map.data.BlockList bl) {
                org.rtk.map.MapServer.clientView.objectSideChanged(bl);
            }
            return LuaValue.NONE;
        });

        /**
         * mobl_checkthreat(idPemain): berapa ancaman pemain itu pada mob ini.
         *
         * <p>⚠️ C mengembalikan <b>0</b> — bukan nil — bila pemainnya tidak
         * ada atau tidak ada di tabel. Skrip AI membandingkannya dengan
         * angka, jadi nil akan meledak.</p>
         */
        klass.addMethod("checkThreat", (self, args) -> {
            if (self instanceof org.rtk.map.Mob mb) {
                return LuaValue.valueOf((double) mb.checkThreat(args.optlong(2, 0)));
            }
            return LuaValue.valueOf(0);
        });

        klass.addMethod("callBase", (self, args) -> {
            if (!(self instanceof ScriptCallBase cb)) {
                return LuaValue.FALSE;
            }
            return LuaValue.valueOf(cb.scriptCallBase(args.optjstring(2, "")));
        });
        klass.addMethod("talk", (self, args) -> {
            log.debug("[{}:talk] {}", klass.name, args.optjstring(3, args.optjstring(2, "")));
            return LuaValue.NONE;
        });
        /**
         * bll_sendanim(anim, kali): mainkan animasi pada benda ini untuk
         * pemain di sekitarnya.
         */
        klass.addMethod("sendAnimation", (self, args) -> {
            if (self instanceof org.rtk.map.data.BlockList bl) {
                org.rtk.map.MapServer.clientView.objectAnimation(bl,
                        args.optint(2, 0), args.optint(3, 0));
            }
            return LuaValue.NONE;
        });

        /** bll_sendanimxy(anim, x, y, kali): animasi pada sebuah petak. */
        klass.addMethod("sendAnimationXY", (self, args) -> {
            if (self instanceof org.rtk.map.data.BlockList bl) {
                org.rtk.map.MapServer.clientView.objectAnimationAt(bl, args.optint(2, 0),
                        args.optint(5, 0), args.optint(3, 0), args.optint(4, 0));
            }
            return LuaValue.NONE;
        });

        for (String name : new String[]{"playSound",
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

    /**
     * map_foreachincell() + bll_getobjects_helper: benda pada satu petak,
     * sebagai tabel Lua 1-basis.
     *
     * <p><b>Bedanya varian "Alive" bukan soal mob.</b> Kedua varian sama-sama
     * melewati mob yang sudah mati dan pemain ber-stealth; yang <b>hanya</b>
     * ada di {@code getAliveObjectsInCell} adalah melewati pemain yang
     * sedang mati ({@code status.state == 1}) — lihat
     * {@code bll_getaliveobjects_helper} di sl.c. Sempat salah dibaca
     * sebagai penyaring mob.</p>
     *
     * @param hidupSaja true untuk varian {@code getAliveObjects*}
     */
    private static LuaValue objectsAtCell(ScriptEngine engine, int m, int x, int y,
                                          int tipe, boolean hidupSaja) {
        var map = org.rtk.map.MapServer.world.get(m);
        if (map == null) {
            return new LuaTable();
        }
        return collect(engine, map.objectsAt(x, y), tipe, hidupSaja);
    }

    /**
     * bll_getobjects_helper / bll_getaliveobjects_helper: susun benda jadi
     * tabel Lua 1-basis, dengan penyaring yang sama di semua varian
     * {@code getObjects*}.
     */
    private static LuaValue collect(ScriptEngine engine,
                                    Iterable<? extends org.rtk.map.data.BlockList> src,
                                    int tipe, boolean hidupSaja) {
        LuaTable t = new LuaTable();
        int flags = tipe <= 0 ? org.rtk.map.data.BlockList.BL_ALL : tipe;
        int i = 1;
        for (org.rtk.map.data.BlockList bl : src) {
            if ((flags & bl.blFlag()) == 0) {
                continue;
            }
            if (bl instanceof org.rtk.map.Mob mb && !mb.isAlive()) {
                continue;
            }
            if (bl instanceof org.rtk.map.User u) {
                if ((u.optFlags & org.rtk.map.User.OPT_STEALTH) != 0) {
                    continue;
                }
                if (hidupSaja && u.status.state == 1) {
                    continue;
                }
            }
            t.set(i++, engine.objectRef(bl));
        }
        return t;
    }

    /** map_foreachinarea(..., AREA, ...): benda di area pandang sebuah benda. */
    private static LuaValue objectsInArea(ScriptEngine engine, Object self,
                                          int tipe, boolean hidupSaja) {
        org.rtk.map.data.BlockList src = blockOf(self);
        if (src == null) {
            return new LuaTable();
        }
        var map = org.rtk.map.MapServer.world.get(src.m);
        if (map == null) {
            return new LuaTable();
        }
        java.util.List<org.rtk.map.data.BlockList> hasil = new java.util.ArrayList<>();
        for (org.rtk.map.data.BlockList.Type t : org.rtk.map.data.BlockList.Type.values()) {
            map.foreachInArea(src.x, src.y, t, hasil::add);
        }
        return collect(engine, hasil, tipe, hidupSaja);
    }

    /** Benda skrip -&gt; benda peta; pemain lewat pemiliknya. */
    private static org.rtk.map.data.BlockList blockOf(Object self) {
        if (self instanceof org.rtk.map.data.BlockList bl) {
            return bl;
        }
        if (self instanceof ScriptPlayer p
                && p.owner instanceof org.rtk.map.data.BlockList bl) {
            return bl;
        }
        return null;
    }

    /**
     * Method yang di C dipasang lewat {@code bll_extendproto} — artinya
     * <b>Player, NPC, dan Mob memiliki set yang sama persis</b>.
     *
     * <p>Ini pernah salah diasumsikan sebagai milik pemain saja, sehingga
     * {@code npc:addNPC(...)} dan {@code npc:getObjectsInCell(...)} gagal
     * dengan "attempt to call nil" — ditemukan dari `map.log` server yang
     * berjalan. Kalau menambah binding, periksa dulu apakah namanya ada di
     * {@code bll_extendproto} (sl.c ±4285) sebelum menaruhnya di
     * {@code definePlayer}.</p>
     */
    static void defineObjectQueries(ScriptEngine engine, ScriptClass klass) {
        /**
         * bll_addnpc(): lahirkan NPC sementara. Isinya tidak ada yang
         * khusus pemain — penangan kelahiran NPC memanggilnya dari NPC.
         */
        klass.addMethod("addNPC", (self, args) -> {
            var arg9 = args.arg(10);   // arg(1) = self
            long moveTime = arg9.isnumber() ? (long) arg9.todouble() : 0;
            String namaTampilan = arg9.isstring() && !arg9.isnumber()
                    ? arg9.tojstring() : "";
            org.rtk.map.MapServer.npcs.addTemp(engine, org.rtk.map.MapServer.world,
                    args.optjstring(2, ""), args.optint(3, 0), args.optint(4, 0),
                    args.optint(5, 0), args.optint(6, 0),
                    (long) args.optdouble(7, 0), (long) args.optdouble(8, 0),
                    (long) args.optdouble(9, 0), moveTime, namaTampilan);
            return LuaValue.NONE;
        });

        /**
         * bll_objectcanmove() / bll_objectcanmovefrom() ->
         * {@code clif_object_canmove()}.
         *
         * <p><b>Selalu false, dan itu benar.</b> Keduanya membaca
         * {@code objectFlags[]}, dan baris yang mengisi tabel itu
         * ({@code objectFlags[z] = flag;} di {@code object_flag_init},
         * map.c:1766) <b>dikomentari</b> di sumber C — jadi pada build
         * aslinya keduanya juga selalu mengembalikan 0. Mengembalikan
         * nilai lain akan membuat perilaku port ini berbeda dari server
         * yang ditirunya.</p>
         */
        klass.addMethod("objectCanMove", (self, args) -> LuaValue.valueOf(0));
        klass.addMethod("objectCanMoveFrom", (self, args) -> LuaValue.valueOf(0));

        klass.addMethod("getObjectsInCell", (self, args) -> objectsAtCell(engine,
                args.optint(2, -1), args.optint(3, -1), args.optint(4, -1),
                args.optint(5, -1), false));
        klass.addMethod("getAliveObjectsInCell", (self, args) -> objectsAtCell(engine,
                args.optint(2, -1), args.optint(3, -1), args.optint(4, -1),
                args.optint(5, -1), true));
        // Beda ...WithTraps hanya pada barang di lantai bertipe ITM_TRAPS;
        // subsistem BL_ITEM belum diport, jadi untuk sekarang identik.
        klass.addMethod("getObjectsInCellWithTraps", (self, args) -> objectsAtCell(engine,
                args.optint(2, -1), args.optint(3, -1), args.optint(4, -1),
                args.optint(5, -1), false));
        klass.addMethod("getAliveObjectsInCellWithTraps", (self, args) -> objectsAtCell(engine,
                args.optint(2, -1), args.optint(3, -1), args.optint(4, -1),
                args.optint(5, -1), true));

        klass.addMethod("getObjectsInArea", (self, args) ->
                objectsInArea(engine, self, args.optint(2, -1), false));
        klass.addMethod("getAliveObjectsInArea", (self, args) ->
                objectsInArea(engine, self, args.optint(2, -1), true));

        // SAMEMAP: seluruh peta tempat benda ini berada.
        klass.addMethod("getObjectsInSameMap", (self, args) -> {
            org.rtk.map.data.BlockList src = blockOf(self);
            return src == null ? new LuaTable()
                    : objectsOfMap(engine, src.m, args.optint(2, -1), false);
        });
        klass.addMethod("getAliveObjectsInSameMap", (self, args) -> {
            org.rtk.map.data.BlockList src = blockOf(self);
            return src == null ? new LuaTable()
                    : objectsOfMap(engine, src.m, args.optint(2, -1), true);
        });

        klass.addMethod("getObjectsInMap", (self, args) ->
                objectsOfMap(engine, args.optint(2, -1), args.optint(3, -1), false));
        klass.addMethod("getAliveObjectsInMap", (self, args) ->
                objectsOfMap(engine, args.optint(2, -1), args.optint(3, -1), true));

        /** bll_getblock(id) -> map_id2bl(): cari benda dari id blok. */
        klass.addMethod("getBlock", (self, args) -> {
            org.rtk.map.data.BlockList bl =
                    org.rtk.map.MapServer.blockById((long) args.optdouble(2, 0));
            return bl == null ? LuaValue.NIL : engine.objectRef(bl);
        });

        /** bll_getusers(): SEMUA pemain online, bukan hanya sepeta. */
        klass.addMethod("getUsers", (self, args) -> {
            java.util.List<org.rtk.map.data.BlockList> semua =
                    new java.util.ArrayList<>(org.rtk.map.MapServer.onlineChars.values());
            return collect(engine, semua, org.rtk.map.data.BlockList.BL_PC, false);
        });
    }

    private static LuaValue objectsOfMap(ScriptEngine engine, int m, int tipe,
                                         boolean hidupSaja) {
        java.util.List<org.rtk.map.data.BlockList> hasil = new java.util.ArrayList<>();
        for (Object o : org.rtk.map.MapServer.objectsInMap(m, tipe)) {
            if (o instanceof org.rtk.map.data.BlockList bl) {
                hasil.add(bl);
            }
        }
        return collect(engine, hasil, tipe, hidupSaja);
    }

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
