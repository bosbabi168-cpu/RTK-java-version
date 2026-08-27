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
     * bll_talkcolor(): pesan berwarna ke <b>satu pemain</b>.
     *
     * <p>⚠️ <b>Kuirk C yang sengaja ditiru.</b> Sumbernya begini:</p>
     * <pre>
     * if (lua_isnumber(arg3)) tsd = map_id2sd(...); else tsd = map_name2sd(...);
     * if (lua_tonumber(arg3) == 0) { /* disiarkan ke area — DIKOMENTARI *&#47; }
     * else if (!tsd) return;
     * else clif_sendmsg(tsd, color, msg);
     * </pre>
     * <p>Untuk argumen ke-3 berupa <b>nama</b>, {@code lua_tonumber} bernilai
     * 0, sehingga cabang pertama yang diambil — dan cabang itu kosong. Jadi
     * di server asli {@code msg(warna, teks, "Nama")} <b>tidak mengirim apa
     * pun</b>, walau nama itu sudah terlanjur dicari. Hal yang sama berlaku
     * bila argumen ke-3 tidak diisi. Hanya bentuk <b>id angka bukan-nol</b>
     * yang benar-benar bekerja — dan itulah yang dipakai 33 dari 38
     * pemanggilan di korpus ({@code player.ID}, {@code target.ID}).</p>
     */
    private static void kirimMsg(Varargs args) {
        LuaValue sasaran = args.arg(4);
        // Cabang yang menghasilkan kiriman HANYA bila arg3 angka bukan-nol.
        if (!sasaran.isnumber() || sasaran.tolong() == 0) {
            return;
        }
        org.rtk.map.User tsd = org.rtk.map.MapServer.userById(sasaran.tolong());
        if (tsd == null) {
            return;
        }
        org.rtk.map.MapServer.clientView.messageToPlayer(tsd,
                args.optint(2, 0), args.optjstring(3, ""));
    }

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
        // `talk` didefinisikan di defineObjectQueries (bll_extendproto),
        // bukan di sini — Player, NPC, dan Mob memakai yang sama persis.

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

        // ---- nyawa & layar ----
        player.addMethod("removeHealth", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.User sd) {
                org.rtk.map.Combat.removeHealth(sd, args.optint(2, 0), args.optlong(3, 0));
            }
            return LuaValue.NONE;
        });
        player.addMethod("popUp", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.User sd) {
                org.rtk.map.MapServer.clientView.popupToPlayer(sd, args.optjstring(2, ""));
            }
            return LuaValue.NONE;
        });
        /**
         * pcl_freeasync(): batalkan coroutine dialog yang sedang menggantung.
         * Dipakai skrip untuk keluar dari percakapan di tengah jalan.
         */
        player.addMethod("freeAsync", (self, args) -> {
            engine.cancel((ScriptPlayer) self);
            return LuaValue.NONE;
        });

        // ---- obrolan ----
        // pcl_talkself(type, teks, id): satu baris obrolan yang HANYA pemain
        // ini melihatnya. Argumen ke-3 opsional; 0 = seolah dirinya sendiri.
        player.addMethod("talkSelf", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.User sd) {
                org.rtk.map.MapServer.clientView.chatLineToPlayer(sd,
                        args.optint(2, 0), args.optlong(4, 0), args.optjstring(3, ""));
            }
            return LuaValue.NONE;
        });
        player.addMethod("msg", (self, args) -> {
            kirimMsg(args);
            return LuaValue.NONE;
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

        /**
         * pcl_removeitemslot(slot, jumlah, ragam): buang barang dari slot
         * tertentu. Lihat {@link org.rtk.map.User#scriptRemoveItemSlot} —
         * ada satu cabang yang mengembalikan false setelah barangnya
         * telanjur berkurang, dan itu memang perilaku C.
         */
        player.addMethod("removeItemSlot", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (!(p.owner instanceof org.rtk.map.User u)) {
                return LuaValue.FALSE;
            }
            return LuaValue.valueOf(u.scriptRemoveItemSlot(
                    args.optint(2, -1), args.optint(3, 0), args.optint(4, 0)));
        });

        /**
         * pcl_pickup(idBarangLantai) -&gt; {@code pc_getitemscript()}:
         * pemain memungut barang yang tergeletak.
         *
         * <p>Yang dikirim skrip adalah <b>id blok barang lantai</b>, bukan id
         * jenis barangnya — biasanya diperoleh dari
         * {@code getObjectsInCell(..., BL_ITEM)}.</p>
         */
        player.addMethod("pickUp", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null && org.rtk.map.MapServer.floorItems
                    .pickUp(u, (long) args.optdouble(2, 0)));
        });

        /**
         * pcl_forcedrop(slot) -&gt; {@code pc_dropitemmap(sd, slot, 0)}:
         * jatuhkan isi satu slot inventaris ke petak tempat pemain berdiri.
         *
         * <p>C memanggilnya dengan {@code type = 0}, yang berarti
         * <b>sekeping</b>, bukan seluruh tumpukan.</p>
         */
        player.addMethod("forceDrop", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.floorItems.dropFromInventory(u,
                        args.optint(2, -1), 0);
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_updateinv() -&gt; {@code pc_loaditem()}: gambar ulang seluruh
         * inventaris di layar pemain. Dipakai 24x, biasanya setelah skrip
         * mengubah isi tas lewat jalur yang tidak mengirim paket sendiri.
         */
        player.addMethod("updateInv", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                kirimInventaris(u);
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_refreshInventory(): sama, tapi C menyapu <b>52 slot tetap</b>
         * alih-alih {@code maxinv}. Di sini keduanya bertemu di titik yang
         * sama karena inventarisnya daftar, bukan larik berlubang.
         */
        player.addMethod("refreshInventory", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                kirimInventaris(u);
            }
            return LuaValue.NONE;
        });

        /** pcl_hasequipped(barang): apakah barang itu sedang dikenakan. */
        player.addMethod("hasEquipped", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            long id = barangId(args.arg(2));
            if (id <= 0) {
                return LuaValue.FALSE;
            }
            for (var it : u.status.equip) {
                if (it != null && it.id == id) {
                    return LuaValue.TRUE;
                }
            }
            return LuaValue.FALSE;
        });

        /**
         * pcl_hasitemdura(barang, jumlah [, dura]): punya sekian barang itu
         * <b>dengan ketahanan persis</b> segitu?
         *
         * <p>{@code dura} 0 berarti "ketahanan penuh bawaan jenisnya".
         * Barangnya <b>tidak</b> dibuang — di C baris {@code pc_delitem}
         * dikomentari, jadi ini murni pemeriksaan.</p>
         */
        player.addMethod("hasItemDura", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            long id = barangId(args.arg(2));
            int perlu = Math.abs(args.optint(3, 0));
            int dura = args.optint(4, 0);
            if (id <= 0 || perlu <= 0) {
                return LuaValue.FALSE;
            }
            if (dura == 0) {
                dura = org.rtk.map.MapServer.itemDb.info(id).durability();
            }
            int sisa = perlu;
            for (var it : u.status.inventory) {
                if (it.id != id || it.dura != dura) {
                    continue;
                }
                if (it.amount >= sisa) {
                    return LuaValue.TRUE;
                }
                sisa -= it.amount;
            }
            return LuaValue.FALSE;
        });

        /** pcl_deductdura(slot, jumlah): ausi satu slot perlengkapan. */
        player.addMethod("deductDura", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Combat.deductDura(u, args.optint(2, -1), args.optint(3, 0));
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_deductdurainv(slot, jumlah): ausi barang di <b>inventaris</b>.
         * Berbeda dari {@code deductDura}, ia tidak memeriksa peta PvP dan
         * tidak memicu peringatan ketahanan — di C memang menulis langsung.
         */
        player.addMethod("deductDuraInv", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            int slot = args.optint(2, -1);
            if (u != null && slot >= 0 && slot < u.status.inventory.size()) {
                u.status.inventory.get(slot).dura -= args.optint(3, 0);
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_deductarmor(kerusakan) -&gt; {@code clif_deductarmor()}:
         * setiap perlengkapan yang dikenakan punya <b>peluang 50%</b> ikut
         * aus. C menulisnya sebagai 14 blok {@code if} yang identik; di sini
         * satu perulangan, karena daftarnya persis slot 0..13.
         */
        player.addMethod("deductArmor", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Combat.deductArmor(u, args.optint(2, 0));
            }
            return LuaValue.NONE;
        });

        /** pcl_deductweapon(kerusakan): hanya senjata, peluang 50% juga. */
        player.addMethod("deductWeapon", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Combat.deductWeapon(u, args.optint(2, 0));
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_forcesave() -&gt; {@code intif_save()}: simpan karakter
         * <b>sekarang juga</b>, tanpa menunggu pemain keluar.
         *
         * <p>Dipakai 15x oleh quest yang tidak boleh kehilangan kemajuannya
         * kalau server mati. Posisi dan samaran disegarkan lebih dulu dari
         * objek hidupnya — lihat {@code MapIntif.saveChar}.</p>
         */
        player.addMethod("forceSave", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null
                    && org.rtk.map.MapIntif.saveChar(u, false) == 0);
        });

        // ---- tampilan & timer ----

        /**
         * pcl_changeview(x, y): geser kamera pemain tanpa memindahkannya,
         * lalu gambar ulang seluruh isi pandangannya. Dipakai 22x, hampir
         * semuanya oleh cutscene.
         */
        player.addMethod("changeView", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.clientView.playerCameraChanged(u,
                        args.optint(2, 0), args.optint(3, 0));
            }
            return LuaValue.TRUE;
        });

        /** pcl_guitext(teks): teks besar melayang di tengah layar. */
        player.addMethod("guitext", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.clientView.guiTextToPlayer(u,
                        args.optjstring(2, ""));
            }
            return LuaValue.NONE;
        });

        /** pcl_paperpopup(lebar, tinggi, teks): kotak kertas berisi teks panjang. */
        player.addMethod("paperpopup", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.clientView.paperToPlayer(u,
                        args.optjstring(4, ""), args.optint(2, 0), args.optint(3, 0));
            }
            return LuaValue.NONE;
        });

        /** pcl_sendurl(ragam, alamat): buka alamat web di klien. */
        player.addMethod("sendURL", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.clientView.urlToPlayer(u,
                        args.optint(2, 0), args.optjstring(3, ""));
            }
            return LuaValue.TRUE;
        });

        /**
         * pcl_speak(pesan, ragam): pemain berbicara, namanya disisipkan
         * server. Ragam 1 = berteriak (seluruh peta).
         *
         * <p>⚠️ Tiga penjaga di C <b>belum</b> diport karena subsistemnya
         * belum ada: peta yang melarang bicara ({@code map.cantalk}),
         * penguraian perintah GM ({@code is_command}), dan bendera
         * dibungkam ({@code uFlag_silenced}). Tambahkan bersama subsistem
         * masing-masing.</p>
         */
        player.addMethod("speak", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.NONE;
            }
            String pesan = args.optjstring(2, "");
            org.rtk.map.MapServer.clientView.playerSpoke(u, pesan, args.optint(3, 0));
            u.speech = pesan;
            return LuaValue.NONE;
        });

        /**
         * pcl_settimer(ragam, detik): pasang penghitung waktu di layar.
         *
         * <p>Ragam 1 dan 2 menghitung mundur dan menyetel sisa waktunya;
         * ragam lain <b>ditolak</b> bila masih ada timer berjalan. Nilai
         * negatif ditolak. Batas atasnya nilai unsigned 32-bit penuh.</p>
         */
        player.addMethod("setTimer", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            int ragam = args.optint(2, 0);
            long lama = (long) args.optdouble(3, 0);
            if (ragam == 1 || ragam == 2) {
                if (lama < 0) {
                    return LuaValue.FALSE;
                }
                u.disptimerTick = Math.min(lama, 4294967295L);
            } else if (u.disptimerType > 0) {
                return LuaValue.FALSE;
            }
            u.disptimerType = ragam;
            org.rtk.map.MapServer.clientView.playerTimerSet(u, ragam, lama);
            return LuaValue.TRUE;
        });

        /**
         * pcl_lock() / pcl_unlock(): kunci atau lepas gerak pemain.
         *
         * <p>⚠️ Nilai paketnya <b>terbalik dari namanya</b>: `lock` mengirim
         * 0 dan `unlock` mengirim 1. Itu di C.</p>
         */
        player.addMethod("lock", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.clientView.playerMovementLocked(u, true);
            }
            return LuaValue.NONE;
        });
        player.addMethod("unlock", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.clientView.playerMovementLocked(u, false);
            }
            return LuaValue.NONE;
        });

        // ---- buku mantra ----

        /** pcl_getspells(): id seluruh mantra yang dikuasai pemain. */
        player.addMethod("getSpells", (self, args) ->
                daftarMantra(pemainDari(self), 0));

        /** pcl_getspellname(): nama TAMPILAN mantra yang dikuasai pemain. */
        player.addMethod("getSpellName", (self, args) ->
                daftarMantra(pemainDari(self), 1));

        /** pcl_getspellyname(): nama SKRIP mantra yang dikuasai pemain. */
        player.addMethod("getSpellYName", (self, args) ->
                daftarMantra(pemainDari(self), 2));

        /**
         * pcl_getspellnamefromyname(namaSkrip): terjemahkan nama skrip
         * mantra jadi nama tampilannya. Nama tak dikenal jadi id 0, dan
         * nama tampilan id 0 memang kosong.
         */
        player.addMethod("getSpellNameFromYName", (self, args) ->
                LuaValue.valueOf(org.rtk.map.MapServer.spellDb.displayNameOf(
                        org.rtk.map.MapServer.spellDb.idOf(args.optjstring(2, "")))));

        /**
         * pcl_getunknownspells(jalur1, jalur2): mantra yang <b>boleh</b>
         * dipelajari pemain tapi belum dikuasainya.
         *
         * <p>Syaratnya diambil apa adanya dari C: jalurnya 0 (umum) atau
         * salah satu dari dua yang diminta, tandanya tidak melebihi tanda
         * pemain, mantranya aktif, dan keselarasannya cocok — atau
         * {@code -1}, yang berarti untuk semua keselarasan.</p>
         */
        player.addMethod("getUnknownSpells", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return new LuaTable();
            }
            java.util.List<Integer> ids = org.rtk.map.MapServer.spellDb.candidateSpells(
                    org.rtk.map.MapServer.sql, args.optint(2, 0), args.optint(3, 0),
                    u.status.mark, u.status.alignment);
            java.util.Set<Integer> dikuasai = new java.util.HashSet<>();
            for (int id : u.status.spells) {
                dikuasai.add(id);
            }
            ids.removeIf(dikuasai::contains);
            return pasanganNamaMantra(ids);
        });

        /**
         * pcl_getallclassspells(jalur): SELURUH mantra aktif milik satu
         * jalur, termasuk yang sudah dikuasai pemain.
         *
         * <p>⚠️ Di C ada penjaga {@code if (found == 0)} di sini juga, tetapi
         * {@code found} tidak pernah disetel di fungsi ini — jadi penjaganya
         * mati dan semua mantra ikut. Ditiru apa adanya.</p>
         */
        player.addMethod("getAllClassSpells", (self, args) ->
                pasanganNamaMantra(org.rtk.map.MapServer.spellDb.classSpells(
                        org.rtk.map.MapServer.sql, args.optint(2, 0))));

        /**
         * pcl_addhealth(jumlah): pemain dipulihkan.
         *
         * <p>Kerusakannya dikirim <b>negatif</b> ke jalur yang sama dengan
         * luka ({@code clif_send_pc_healthscript(sd, -damage, 0)}) — itu
         * yang membuat angkanya muncul hijau di layar. Kait
         * {@code player_combat.on_healed} hanya dipanggil bila jumlahnya
         * positif.</p>
         */
        player.addMethod("addHealth", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.NONE;
            }
            int jumlah = args.optint(2, 0);
            if (jumlah > 0) {
                org.rtk.map.data.BlockList penyerang = u.attacker > 0
                        ? org.rtk.map.MapServer.blockById(u.attacker) : null;
                var ref = engine.playerRef(u.scriptPlayer());
                if (penyerang != null) {
                    engine.doScript("player_combat", "on_healed", ref,
                            engine.objectRef(penyerang));
                } else {
                    engine.doScript("player_combat", "on_healed", ref);
                }
            }
            org.rtk.map.Combat.takeDamage(u, -jumlah, 0);
            org.rtk.map.MapServer.clientView.playerStatusChanged(u,
                    org.rtk.map.Clif.SFLAG_HPMP);
            return LuaValue.NONE;
        });

        /**
         * pcl_getexchangeitem(n): satu barang dari titipan pertukaran
         * pemain, atau nil bila indeksnya kosong.
         *
         * <p>Yang dibaca titipan <b>miliknya sendiri</b> — barang yang ia
         * tawarkan, bukan yang ditawarkan lawannya.</p>
         */
        player.addMethod("getExchangeItem", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            int n = args.optint(2, -1);
            if (u == null || n < 0 || n >= u.exchange.items.size()) {
                return LuaValue.NIL;
            }
            return engine.newInstance(engine.boundItemClass,
                    new ScriptItem(u.exchange.items.get(n)));
        });

        // ---- sisa administratif ----

        /**
         * pcl_setpk(id) / pcl_getpk(id): tandai pemain lain sebagai lawan PK.
         *
         * <p>⚠️ Batasnya <b>20</b> dan tanda itu <b>tidak pernah
         * kedaluwarsa sendiri</b> — waktunya dicatat tapi server tidak
         * pernah membacanya. Penuh berarti penandaan baru diabaikan
         * diam-diam, seperti di C.</p>
         *
         * <p>Menandai lawan BARU menggambar ulang area pandang pemain, agar
         * warna namanya langsung berubah; menyegarkan tanda yang sudah ada
         * tidak.</p>
         */
        player.addMethod("setPK", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.NONE;
            }
            long id = (long) args.optdouble(2, 0);
            long sekarang = System.currentTimeMillis() / 1000;
            if (u.pvp.containsKey(id)) {
                u.pvp.put(id, sekarang);
            } else if (u.pvp.size() < org.rtk.map.User.MAX_PVP) {
                u.pvp.put(id, sekarang);
                org.rtk.map.MapServer.clientView.playerViewRefreshed(u);
            }
            return LuaValue.NONE;
        });
        player.addMethod("getPK", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null
                    && u.pvp.containsKey((long) args.optdouble(2, 0)));
        });

        /**
         * pcl_checklevel() -&gt; {@code pc_checklevel()}: periksa apakah
         * pengalaman pemain sudah cukup untuk naik level.
         *
         * <p>⚠️ Kenaikan levelnya <b>seluruhnya sisi skrip</b>: C hanya
         * memanggil kait {@code onLevel} sekali per ambang yang terlampaui,
         * dan skripnya yang menaikkan levelnya. Karena itu perulangannya
         * berjalan dari level sekarang sampai 99 dan bisa memanggil kait
         * itu <b>berkali-kali</b> dalam satu panggilan.</p>
         */
        player.addMethod("checkLevel", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.NONE;
            }
            int path = u.status.charClass;
            if (path > 5) {
                path = org.rtk.map.MapServer.classDb.pathOf(path);
            }
            var ref = engine.playerRef(u.scriptPlayer());
            for (int lv = u.status.level; lv < org.rtk.map.data.ClassDb.MAX_LEVEL; lv++) {
                if (u.status.exp >= org.rtk.map.MapServer.classDb.level(path, lv)) {
                    engine.doScript("onLevel", null, ref);
                }
            }
            return LuaValue.NONE;
        });

        /** pcl_setHeroShow(n): tampilkan lencana pahlawan; langsung ke database. */
        player.addMethod("setHeroShow", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            u.status.heroes = args.optint(2, 0);
            org.rtk.map.MapServer.sql.update(
                    "UPDATE `Character` SET `ChaHeroes` = ? WHERE `ChaName` = ?",
                    u.status.heroes, u.status.name);
            return LuaValue.TRUE;
        });

        /**
         * pcl_setAccountBan(banned): larang karakter ini masuk.
         *
         * <p>⚠️ <b>Menendangnya keluar SEKARANG</b> bila nilainya 1 — bukan
         * sekadar menandai. Port ini menutup sesinya lewat
         * {@code Session.eof}, sama seperti C.</p>
         *
         * <p>Cabang "akun terdaftar" di C melarang <b>seluruh karakter di
         * akun itu</b>; itu butuh tabel akun yang belum diport, jadi di sini
         * baru karakternya sendiri yang tercakup.</p>
         */
        player.addMethod("setAccountBan", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            int banned = args.optint(2, 0);
            org.rtk.map.MapServer.sql.update(
                    "UPDATE `Character` SET `ChaBanned` = ? WHERE `ChaName` = ?",
                    banned, u.status.name);
            if (banned == 1) {
                var sesi = org.rtk.map.MapServer.net.session(u.fd);
                if (sesi != null) {
                    sesi.eof = true;
                }
            }
            return LuaValue.TRUE;
        });

        /**
         * pcl_setCaptchaKey(kunci): simpan kunci captcha pemain.
         *
         * <p>Yang disimpan <b>ringkasan MD5</b>-nya, bukan kuncinya sendiri —
         * jadi {@code getCaptchaKey} mengembalikan ringkasan itu, dan skrip
         * membandingkannya dengan ringkasan jawaban pemain.</p>
         */
        player.addMethod("setCaptchaKey", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            int n = org.rtk.map.MapServer.sql.update(
                    "UPDATE `Character` SET `ChaCaptchaKey` = ? WHERE `ChaId` = ?",
                    md5(args.optjstring(2, "")), u.status.id);
            return LuaValue.valueOf(n >= 0);
        });

        /** pcl_getCaptchaKey(): ringkasan MD5 kunci captcha pemain. */
        player.addMethod("getCaptchaKey", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.valueOf("");
            }
            String k = org.rtk.map.MapServer.sql.queryString(
                    "SELECT `ChaCaptchaKey` FROM `Character` WHERE `ChaId` = ?",
                    u.status.id);
            return LuaValue.valueOf(k == null ? "" : k);
        });

        /**
         * pcl_forceequip(barang, slot): pasang barang langsung ke slot
         * perlengkapan, tanpa lewat inventaris dan tanpa syarat apa pun.
         *
         * <p>Nilainya diisi <b>bawaan jenis barang</b>, bukan disalin dari
         * barang yang sudah ada: ketahanan penuh, tanpa ukiran, tanpa
         * pemilik, tanpa wujud kustom.</p>
         */
        player.addMethod("forceEquip", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            long id = barangId(args.arg(2));
            int slot = args.optint(3, -1);
            if (u == null || id <= 0 || slot < 0
                    || org.rtk.map.MapServer.itemDb.info(id).id() == 0) {
                return LuaValue.FALSE;
            }
            var lama = u.status.equipAt(slot);
            if (lama != null) {
                u.status.equip.remove(lama);
            }
            var it = new org.rtk.common.mmo.Item();
            it.id = id;
            it.amount = 1;
            it.pos = slot;
            it.dura = org.rtk.map.MapServer.itemDb.info(id).durability();
            it.protectedFlag = org.rtk.map.MapServer.itemDb.protectedOf(id);
            u.status.equip.add(it);
            org.rtk.map.MapServer.clientView.objectAppearanceChanged(u);
            return LuaValue.TRUE;
        });

        /**
         * pcl_mapselection(judul, x0[], y0[], nama[], id[], x1[], y1[]):
         * daftar peta yang bisa dipilih pemain (peta rumah, teleporter).
         */
        player.addMethod("mapSelection", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.NONE;
            }
            org.rtk.map.MapServer.clientView.mapSelectionToPlayer(u,
                    args.optjstring(2, ""),
                    angkaDari(args.arg(3)), angkaDari(args.arg(4)),
                    tableToStrings(args.arg(5)),
                    angkaDari(args.arg(6)),
                    angkaDari(args.arg(7)), angkaDari(args.arg(8)));
            return LuaValue.NONE;
        });

        // ---- bank pribadi, klan, dan subpath ----

        /**
         * pcl_getbankitems(): seluruh isi bank <b>pribadi</b> pemain.
         *
         * <p>Bentuk keluarannya <b>datar berselang sepuluh</b>: tiap barang
         * memakai sepuluh indeks berurutan (id, jumlah, pemilik, waktu,
         * ukiran, perlindungan, ikon, warna ikon, wujud, warna wujud).
         * Skrip bank membacanya dengan langkah 10.</p>
         */
        player.addMethod("getBankItems", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return u == null ? new LuaTable() : bankTable(u.status.banks, 10);
        });

        /**
         * pcl_getclanbankitems(): isi bank klan pemain, sepuluh ladang per
         * barang.
         */
        player.addMethod("getClanBankItems", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null || u.status.clan == 0) {
                return new LuaTable();
            }
            return bankTable(org.rtk.map.MapServer.clanDb.bank(
                    org.rtk.map.MapServer.sql, (int) u.status.clan), 10);
        });

        /**
         * pcl_getsubpathbankitems(): ⚠️ <b>penyimpanan yang SAMA</b> dengan
         * bank klan — yang berbeda hanya bentuknya, <b>lima</b> ladang per
         * barang (id, jumlah, pemilik, ukiran, waktu) alih-alih sepuluh.
         * Lihat catatan di {@link org.rtk.map.data.ClanDb}.
         */
        player.addMethod("getSubpathBankItems", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null || u.status.clan == 0) {
                return new LuaTable();
            }
            return bankTable(org.rtk.map.MapServer.clanDb.bank(
                    org.rtk.map.MapServer.sql, (int) u.status.clan), 5);
        });

        /** pcl_clanbankdeposit(barang, jumlah, pemilik, waktu, ukiran, …). */
        player.addMethod("clanBankDeposit", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null || u.status.clan == 0) {
                return LuaValue.FALSE;
            }
            return LuaValue.valueOf(org.rtk.map.MapServer.clanDb.deposit(
                    org.rtk.map.MapServer.sql, (int) u.status.clan, bankItemDari(args)));
        });

        /** pcl_clanbankwithdraw(barang, jumlah, …): gagal bila kurang. */
        player.addMethod("clanBankWithdraw", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null || u.status.clan == 0) {
                return LuaValue.FALSE;
            }
            var cari = bankItemDari(args);
            return LuaValue.valueOf(org.rtk.map.MapServer.clanDb.withdraw(
                    org.rtk.map.MapServer.sql, (int) u.status.clan, cari, cari.amount));
        });

        // ---- papan pesan (Trek C4) ----

        /**
         * pcl_showboard(papan): buka daftar isi papan.
         *
         * <p>Argumennya boleh <b>nama atau nomor</b> papan. Papan <b>0</b>
         * bukan papan melainkan <b>kotak surat</b> pemain — hak tulis dan
         * hapusnya selalu penuh.</p>
         */
        player.addMethod("showBoard", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.NONE;
            }
            LuaValue arg = args.arg(2);
            int id = arg.isnumber() ? (int) arg.todouble()
                    : org.rtk.map.MapServer.boardDb.idOf(arg.optjstring(""));
            org.rtk.map.Boards.show(engine, u, id, 0, true);
            return LuaValue.NONE;
        });

        /**
         * pcl_powerboard(): daftar pemain di peta ini.
         *
         * <p>⚠️ <b>Bukan papan pesan sama sekali</b>, meski namanya begitu
         * dan letaknya di kelompok yang sama — ia daftar pemain online
         * beserta "power rating" ({@code baseHealth + baseMagic}).</p>
         */
        player.addMethod("powerBoard", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.MapServer.clientView.powerBoardToPlayer(u);
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_sendboardquestions(judul[], pertanyaan[], barisJawaban[]):
         * formulir bertanya di papan.
         *
         * <p>⚠️ Di C fungsi ini diakhiri {@code lua_yield} — skripnya
         * <b>berhenti</b> sampai pemain menjawab. Jawabannya kembali lewat
         * jalur dialog yang sama dengan {@code inputSeq}, jadi di sini ia
         * ikut {@link ScriptEngine#yieldBlocking}.</p>
         */
        player.addMethod("sendBoardQuestions", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            java.util.List<String> judul = tableToStrings(args.arg(2));
            java.util.List<String> tanya = tableToStrings(args.arg(3));
            java.util.List<Integer> baris = new java.util.ArrayList<>();
            LuaValue t = args.arg(4);
            if (t.istable()) {
                LuaTable tb = t.checktable();
                for (int i = 1; i <= tb.length(); i++) {
                    baris.add(tb.get(i).optint(1));
                }
            }
            if (p.owner instanceof org.rtk.map.User u) {
                org.rtk.map.MapServer.clientView.boardQuestionsToPlayer(u,
                        judul, tanya, baris);
            }
            return engine.yieldBlocking(p,
                    new ScriptPlayer.PendingDialog("boardQuestions", "", judul));
        });

        // ---- kiriman & surat (Trek C4) ----

        /**
         * pcl_getparcel(): kiriman pertama yang menunggu pemain, atau nil.
         *
         * <p>Pesan "tidak ada kiriman" dikirim di sini, bukan di
         * {@link org.rtk.map.Parcels}, supaya lapisan datanya tetap bebas
         * protokol.</p>
         */
        player.addMethod("getParcel", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            var p = org.rtk.map.Parcels.first(org.rtk.map.MapServer.sql, u.status.id);
            if (p == null) {
                org.rtk.map.MapServer.clientView.messageToPlayer(u, 5,
                        "Tidak ada kiriman yang menunggumu.");
                return LuaValue.FALSE;
            }
            return engine.newInstance(engine.parcelClass, p);
        });

        /** pcl_getparcellist(): seluruh kiriman yang menunggu pemain. */
        player.addMethod("getParcelList", (self, args) -> {
            LuaTable t = new LuaTable();
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return t;
            }
            int i = 1;
            for (var p : org.rtk.map.Parcels.list(org.rtk.map.MapServer.sql, u.status.id)) {
                t.set(i++, engine.newInstance(engine.parcelClass, p));
            }
            return t;
        });

        /**
         * pcl_removeparcel(pengirim, barang, jumlah, pos, …): buang satu
         * kiriman.
         *
         * <p>⚠️ Dari sebelas argumen yang diminta, <b>hanya nomor urutnya
         * (argumen ke-4) yang benar-benar dipakai</b> — sisanya peninggalan
         * pencatatan log yang dikomentari di C. Jangan menjadikannya
         * penyaring tambahan; skrip mengirim nilai seadanya ke sana.</p>
         */
        player.addMethod("removeParcel", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null
                    && org.rtk.map.Parcels.remove(org.rtk.map.MapServer.sql, u.status.id, args.optint(5, -1)));
        });

        /**
         * pcl_sendmail(penerima, judul, isi): titipkan surat lewat char
         * server. Hasilnya datang belakangan lewat paket 0x380C, jadi
         * nilai baliknya hanya "permintaannya terkirim".
         */
        player.addMethod("sendMail", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null && org.rtk.map.MapIntif.sendMail(u,
                    args.optjstring(2, ""), args.optjstring(3, ""),
                    args.optjstring(4, ""), false));
        });

        /**
         * pcl_updateMail(namaLama): pindahkan seluruh surat yang ditujukan
         * ke nama lama menjadi ke nama pemain sekarang.
         *
         * <p>Dipakai saat karakter berganti nama — tanpa ini suratnya
         * tertinggal di nama yang sudah tidak ada.</p>
         */
        player.addMethod("updateMail", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.FALSE;
            }
            int n = org.rtk.map.MapServer.sql.update(
                    "UPDATE `Mail` SET `MalChaNameDestination` = ?"
                    + " WHERE `MalChaNameDestination` = ?",
                    u.status.name, args.optjstring(2, ""));
            return LuaValue.valueOf(n >= 0);
        });

        /**
         * pcl_addGift() / pcl_retrieveGift() — <b>seluruh badannya
         * dikomentari di C</b>, jadi keduanya tidak melakukan apa pun dan
         * tidak mengembalikan nilai yang berarti.
         *
         * <p>Diport sebagai tiruan yang setia: skrip mana pun yang
         * mengandalkannya sudah rusak di server aslinya. Mengisinya dengan
         * tebakan justru mengubah perilaku.</p>
         */
        player.addMethod("addGift", (self, args) -> LuaValue.NIL);
        player.addMethod("retrieveGift", (self, args) -> LuaValue.NIL);

        // ---- BOD (Break on Death) ----

        /**
         * pcl_deductduraequip(): ausi seluruh perlengkapan 10%, hancurkan
         * yang habis, lalu serahkan daftarnya ke {@code characterLog.bodLog}.
         */
        player.addMethod("deductDuraEquip", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Combat.deductDuraEquip(engine, u);
            }
            return LuaValue.NONE;
        });

        /** pcl_checkinvbod(): hancurkan barang inventaris ber-BoD saat pemain mati. */
        player.addMethod("checkInvBod", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Combat.checkInvBod(engine, u);
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_getboditem(n): satu barang dari daftar yang hancur pada sapuan
         * ini. Mengembalikan <b>nil</b> bila indeksnya kosong — skrip
         * mengandalkan pembedaan itu.
         */
        player.addMethod("getBODItem", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            int n = args.optint(2, -1);
            if (u == null || n < 0 || n >= u.bodItems.size()) {
                return LuaValue.NIL;
            }
            return engine.newInstance(engine.boundItemClass,
                    new ScriptItem(u.bodItems.get(n)));
        });

        /** pcl_expireitem(): buang barang yang masa berlakunya habis. */
        player.addMethod("expireItem", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Combat.expireItems(u);
            }
            return LuaValue.NONE;
        });

        /**
         * pcl_stripequip(slot, hancurkan, paksa): lepas paksa satu slot
         * perlengkapan.
         *
         * <p>Tiga jalur berbeda, dan yang menentukan adalah <b>ada tidaknya
         * slot inventaris kosong</b>:</p>
         * <ul>
         *   <li>Ada tempat — barangnya masuk inventaris. {@code hancurkan}
         *       membuatnya dibuang alih-alih disimpan.</li>
         *   <li>Tidak ada tempat, {@code hancurkan} — barangnya jatuh ke
         *       lantai lalu <b>langsung dihapus dari lantai</b>.</li>
         *   <li>Tidak ada tempat, {@code paksa} — barangnya ditinggalkan di
         *       lantai.</li>
         * </ul>
         *
         * <p>Tanpa {@code hancurkan} maupun {@code paksa}, dan tanpa tempat
         * di inventaris, <b>tidak terjadi apa-apa</b> — barangnya tetap
         * dikenakan. Itu di C.</p>
         */
        player.addMethod("stripEquip", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            int slot = args.optint(2, -1);
            if (u == null || slot < 0) {
                return LuaValue.NONE;
            }
            var it = u.status.equipAt(slot);
            if (it == null || it.id <= 0) {
                return LuaValue.NONE;
            }
            boolean hancurkan = args.optint(3, 0) == 1;
            boolean paksa = args.optint(4, 0) == 1;
            String nama = org.rtk.map.MapServer.itemDb.info(it.id).tampilan();

            boolean adaTempat = u.status.inventory.size() < u.status.maxInv;
            if (adaTempat) {
                u.status.equip.remove(it);
                if (hancurkan) {
                    org.rtk.map.MapServer.clientView.messageToPlayer(u, 5,
                            nama + " milikmu hancur.");
                } else {
                    u.status.inventory.add(it);
                    org.rtk.map.MapServer.clientView.playerInventorySlotChanged(u,
                            u.status.inventory.size() - 1);
                    org.rtk.map.MapServer.clientView.messageToPlayer(u, 5,
                            nama + " milikmu terlepas paksa.");
                }
                org.rtk.map.MapServer.clientView.objectAppearanceChanged(u);
                return LuaValue.NONE;
            }

            if (!hancurkan && !paksa) {
                return LuaValue.NONE;   // C: tidak melakukan apa pun
            }

            // inventaris penuh: barangnya jatuh ke lantai
            u.status.equip.remove(it);
            var fl = org.rtk.map.MapServer.floorItems.drop(u, it.id, Math.max(1, it.amount),
                    it.dura, it.protectedFlag, it.owner, u.m, u.x, u.y);
            if (hancurkan && fl != null) {
                org.rtk.map.MapServer.floorItems.hapus(fl);
                org.rtk.map.MapServer.clientView.messageToPlayer(u, 5,
                        nama + " milikmu hancur.");
            } else {
                org.rtk.map.MapServer.clientView.messageToPlayer(u, 5,
                        nama + " milikmu terlepas paksa.");
            }
            org.rtk.map.MapServer.clientView.objectAppearanceChanged(u);
            return LuaValue.NONE;
        });

        /** pcl_updatePath(jalur, tanda): naik jalur; langsung ditulis ke database. */
        player.addMethod("updatePath", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.User u) {
                u.scriptUpdatePath(args.optint(2, 0), args.optint(3, 0));
            }
            return LuaValue.TRUE;
        });

        /** pcl_updateCountry(negara): pindah negara; langsung ke database juga. */
        player.addMethod("updateCountry", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.User u) {
                u.scriptUpdateCountry(args.optint(2, 0));
            }
            return LuaValue.TRUE;
        });

        /**
         * pcl_sendhealth(kerusakan, kritis) -> {@code clif_send_pc_healthscript()}:
         * pemain menerima kerusakan, dan angkanya muncul di atas kepalanya.
         *
         * <p>Dua penyesuaian yang diambil apa adanya dari C: kerusakan
         * dibulatkan <b>menjauhi nol</b> (0,5 ke atas untuk positif, ke bawah
         * untuk negatif), dan nomor kritis 1/2 diterjemahkan jadi 33/255 —
         * itu nomor warna angkanya di klien, bukan besaran.</p>
         */
        player.addMethod("sendHealth", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (!(p.owner instanceof org.rtk.map.User u)) {
                return LuaValue.NONE;
            }
            double dmg = args.optdouble(2, 0);
            int damage = dmg > 0 ? (int) (dmg + 0.5) : (dmg < 0 ? (int) (dmg - 0.5) : 0);
            int critical = args.optint(3, 0);
            if (critical == 1) {
                critical = 33;
            } else if (critical == 2) {
                critical = 255;
            }
            org.rtk.map.Combat.takeDamage(u, damage, critical);
            return LuaValue.NONE;
        });

        /**
         * pcl_refresh() — gambar ulang seluruh layar pemain.
         *
         * <p>C memanggil {@code pc_setpos()} lebih dulu; itu <b>tidak</b>
         * menyentuh indeks blok (lihat Peringatan #11), hanya menyetel ulang
         * posisi yang dianggap benar sebelum layar digambar ulang.</p>
         */
        player.addMethod("refresh", (self, args) -> {
            ScriptPlayer p = (ScriptPlayer) self;
            if (p.owner instanceof org.rtk.map.User u) {
                org.rtk.map.Pc.setPos(u, u.m, u.x, u.y);
                org.rtk.map.MapServer.clientView.playerViewRefreshed(u);
            }
            return LuaValue.NONE;
        });

        defineDurations(engine, player);

        // `sendSound` dan `updateStatus` tidak ada di sl.c sama sekali —
        // dibiarkan terdaftar supaya skrip yang memanggilnya tidak meledak.
        for (String name : new String[]{"sendSound", "updateStatus"}) {
            player.addMethod(name, (self, args) -> {
                engine.warnStub("player:" + name + "()");
                return LuaValue.NONE;
            });
        }
    }

    /**
     * Keluarga durasi & aether mantra — {@link org.rtk.map.Durations}.
     *
     * <p>Semuanya menyebut mantra dengan <b>nama skrip</b> (SplIdentifier),
     * yang diterjemahkan jadi id lewat {@code magicdb_id}. Nama yang tidak
     * dikenal jadi id 0, dan di C id 0 punya arti khusus pada paket durasi
     * (teksnya dipaksa "Shield") — jadi jangan meloloskan nama kosong.</p>
     */
    static void defineDurations(ScriptEngine engine, ScriptClass player) {
        // ---- setter ----
        player.addMethod("setDuration", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return LuaValue.NONE;
            }
            org.rtk.map.Durations.setDuration(engine, u,
                    mantraId(args.optjstring(2, "")), args.optint(3, 0),
                    (long) args.optdouble(4, 0), args.optint(5, 0) == 1);
            return LuaValue.NONE;
        });
        player.addMethod("setAether", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Durations.setAether(u,
                        mantraId(args.optjstring(2, "")), args.optint(3, 0));
            }
            return LuaValue.NONE;
        });

        // ---- pemeriksa ----
        player.addMethod("hasDuration", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null && org.rtk.map.Durations.hasDuration(
                    u, mantraId(args.optjstring(2, ""))));
        });
        player.addMethod("hasDurationID", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null && org.rtk.map.Durations.hasDurationFrom(
                    u, mantraId(args.optjstring(2, "")), (long) args.optdouble(3, 0)));
        });
        player.addMethod("hasAether", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u != null && org.rtk.map.Durations.hasAether(
                    u, mantraId(args.optjstring(2, ""))));
        });

        // ---- pembaca ----
        // getDuration dan durationAmount di C dua fungsi terpisah dengan isi
        // yang sama persis — keduanya mengembalikan sisa dalam MILIDETIK.
        ScriptClass.Method sisa = (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u == null ? 0
                    : org.rtk.map.Durations.duration(u, mantraId(args.optjstring(2, ""))));
        };
        player.addMethod("getDuration", sisa);
        player.addMethod("durationAmount", sisa);
        player.addMethod("getDurationID", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u == null ? 0
                    : org.rtk.map.Durations.durationFrom(u,
                            mantraId(args.optjstring(2, "")), (long) args.optdouble(3, 0)));
        });
        player.addMethod("getAether", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u == null ? 0
                    : org.rtk.map.Durations.aether(u, mantraId(args.optjstring(2, ""))));
        });
        player.addMethod("getCasterID", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            return LuaValue.valueOf(u == null ? 0
                    : (double) org.rtk.map.Durations.casterOf(u,
                            mantraId(args.optjstring(2, ""))));
        });

        /**
         * pcl_getalldurations(): daftar NAMA SKRIP mantra yang sedang
         * berjalan. Berbeda dari getAllAethers, yang berselang-seling
         * sisa-waktu lalu nama — bentuk itu ada di C dan skrip
         * mengandalkannya.
         */
        player.addMethod("getAllDurations", (self, args) -> {
            LuaTable t = new LuaTable();
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return t;
            }
            int i = 1;
            for (var s : u.status.duraAether) {
                if (s.id > 0 && s.duration > 0) {
                    t.set(i++, LuaValue.valueOf(
                            org.rtk.map.MapServer.spellDb.nameOf(s.id)));
                }
            }
            return t;
        });
        player.addMethod("getAllAethers", (self, args) -> {
            LuaTable t = new LuaTable();
            org.rtk.map.User u = pemainDari(self);
            if (u == null) {
                return t;
            }
            int i = 1;
            for (var s : u.status.duraAether) {
                if (s.id > 0 && s.aether > 0) {
                    t.set(i++, LuaValue.valueOf(s.aether));
                    t.set(i++, LuaValue.valueOf(
                            org.rtk.map.MapServer.spellDb.nameOf(s.id)));
                }
            }
            return t;
        });

        // ---- penghapus ----
        player.addMethod("flushDuration", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Durations.flushDuration(engine, u, args.optint(2, 0),
                        args.optint(3, 0), args.optint(4, 0), true);
            }
            return LuaValue.NONE;
        });
        player.addMethod("flushDurationNoUncast", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Durations.flushDuration(engine, u, args.optint(2, 0),
                        args.optint(3, 0), args.optint(4, 0), false);
            }
            return LuaValue.NONE;
        });
        player.addMethod("flushAether", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Durations.flushAether(u, args.optint(2, 0),
                        args.optint(3, 0), args.optint(4, 0));
            }
            return LuaValue.NONE;
        });
        player.addMethod("refreshDurations", (self, args) -> {
            org.rtk.map.User u = pemainDari(self);
            if (u != null) {
                org.rtk.map.Durations.refresh(u);
            }
            return LuaValue.TRUE;
        });
    }

    /**
     * Bagian bersama {@code bll_dropitem} / {@code bll_dropitemxy}: bongkar
     * argumen lalu serahkan ke {@code mobdb_dropitem}.
     *
     * @param argPeta indeks argumen Lua untuk id pemain penerima jatuhan
     *                (argumen terakhir di C, dan opsional)
     */
    private static void jatuhkan(org.rtk.map.data.BlockList src, Varargs args,
                                 int m, int x, int y,
                                 int argDura, int argProt, int argPemain) {
        LuaValue jenis = args.arg(2);
        long itemId = jenis.isnumber()
                ? (long) jenis.todouble()
                : org.rtk.map.MapServer.itemDb.idOf(jenis.optjstring(""));
        if (itemId <= 0) {
            return;
        }
        int jumlah = args.optint(3, 0);
        int dura = args.optint(4, 0);
        long prot = (long) args.optdouble(5, 0);
        // 0 berarti "pakai bawaan jenis barangnya", bukan nol
        if (dura == 0) {
            dura = org.rtk.map.MapServer.itemDb.info(itemId).durability();
        }
        if (prot == 0) {
            prot = org.rtk.map.MapServer.itemDb.protectedOf(itemId);
        }
        org.rtk.map.MapServer.floorItems.drop(src, itemId, jumlah, dura, prot, 0, m, x, y);
    }

    /**
     * Isi buku mantra pemain sebagai tabel Lua 1-basis.
     *
     * @param ragam 0 = id, 1 = nama tampilan, 2 = nama skrip. Ketiganya
     *              fungsi terpisah di C dengan badan yang identik kecuali
     *              satu baris.
     */
    private static LuaValue daftarMantra(org.rtk.map.User u, int ragam) {
        LuaTable t = new LuaTable();
        if (u == null) {
            return t;
        }
        var db = org.rtk.map.MapServer.spellDb;
        int i = 1;
        for (int id : u.status.spells) {
            if (id == 0) {
                continue;
            }
            t.set(i++, switch (ragam) {
                case 1 -> LuaValue.valueOf(db.displayNameOf(id));
                case 2 -> LuaValue.valueOf(db.nameOf(id));
                default -> LuaValue.valueOf(id);
            });
        }
        return t;
    }

    /**
     * Bentuk keluaran {@code getUnknownSpells} dan {@code getAllClassSpells}:
     * tabel <b>berselang-seling</b> nama tampilan lalu nama skrip
     * (indeks 1 nama, 2 yname, 3 nama, 4 yname, …).
     *
     * <p>Bentuk yang tidak biasa ini ada di C dan skrip mengandalkannya —
     * mengubahnya jadi tabel pasangan akan memutus daftar mantra di menu.</p>
     */
    private static LuaValue pasanganNamaMantra(java.util.List<Integer> ids) {
        LuaTable t = new LuaTable();
        var db = org.rtk.map.MapServer.spellDb;
        int x = 1;
        for (int id : ids) {
            t.set(x++, LuaValue.valueOf(db.displayNameOf(id)));
            t.set(x++, LuaValue.valueOf(db.nameOf(id)));
        }
        return t;
    }

    /**
     * map_id2sd() / map_name2sd(): pemain dari argumen Lua yang boleh berupa
     * <b>id atau nama</b>. Dipakai binding yang menyebut pemain lain.
     */
    private static org.rtk.map.User pemainDariArg(LuaValue v) {
        if (v.isnumber()) {
            return org.rtk.map.MapServer.userById((long) v.todouble());
        }
        String nama = v.optjstring("");
        if (nama.isEmpty()) {
            return null;
        }
        for (org.rtk.map.User u : org.rtk.map.MapServer.onlineChars.values()) {
            if (nama.equalsIgnoreCase(u.status.name)) {
                return u;
            }
        }
        return null;
    }

    /**
     * Isi bank sebagai tabel Lua <b>datar</b>.
     *
     * @param stride 10 untuk bentuk bank penuh, 5 untuk bentuk subpath.
     *               Keduanya membaca daftar yang sama; hanya ladang yang
     *               ikut yang berbeda.
     */
    private static LuaValue bankTable(java.util.List<org.rtk.common.mmo.BankItem> isi,
                                      int stride) {
        LuaTable t = new LuaTable();
        int x = 1;
        for (var b : isi) {
            if (b.itemId <= 0) {
                continue;
            }
            if (stride == 5) {
                t.set(x, LuaValue.valueOf((double) b.itemId));
                t.set(x + 1, LuaValue.valueOf((double) b.amount));
                t.set(x + 2, LuaValue.valueOf((double) b.owner));
                t.set(x + 3, LuaValue.valueOf(b.realName == null ? "" : b.realName));
                t.set(x + 4, LuaValue.valueOf((double) b.time));
            } else {
                t.set(x, LuaValue.valueOf((double) b.itemId));
                t.set(x + 1, LuaValue.valueOf((double) b.amount));
                t.set(x + 2, LuaValue.valueOf((double) b.owner));
                t.set(x + 3, LuaValue.valueOf((double) b.time));
                t.set(x + 4, LuaValue.valueOf(b.realName == null ? "" : b.realName));
                t.set(x + 5, LuaValue.valueOf((double) b.protectedFlag));
                t.set(x + 6, LuaValue.valueOf((double) b.customIcon));
                t.set(x + 7, LuaValue.valueOf((double) b.customIconColor));
                t.set(x + 8, LuaValue.valueOf((double) b.customLook));
                t.set(x + 9, LuaValue.valueOf((double) b.customLookColor));
            }
            x += stride;
        }
        return t;
    }

    /** Bongkar sepuluh argumen bank klan jadi satu barang bank. */
    private static org.rtk.common.mmo.BankItem bankItemDari(Varargs args) {
        var b = new org.rtk.common.mmo.BankItem();
        b.itemId = (long) args.optdouble(2, 0);
        b.amount = (long) args.optdouble(3, 0);
        b.owner = (long) args.optdouble(4, 0);
        b.time = (long) args.optdouble(5, 0);
        b.realName = args.optjstring(6, "");
        b.protectedFlag = (long) args.optdouble(7, 0);
        b.customIcon = (long) args.optdouble(8, 0);
        b.customIconColor = (long) args.optdouble(9, 0);
        b.customLook = (long) args.optdouble(10, 0);
        b.customLookColor = (long) args.optdouble(11, 0);
        return b;
    }

    /** Tabel Lua berisi angka -&gt; daftar Java, 1-basis. */
    private static java.util.List<Integer> angkaDari(LuaValue v) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (v.istable()) {
            LuaTable t = v.checktable();
            for (int i = 1; i <= t.length(); i++) {
                out.add(t.get(i).optint(0));
            }
        }
        return out;
    }

    /** MD5_String(): ringkasan heksadesimal huruf kecil, seperti di C. */
    private static String md5(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 tidak tersedia", e);
        }
    }

    /** itemdb_id(): argumen Lua yang boleh berupa nama ATAU nomor barang. */
    private static long barangId(LuaValue v) {
        return v.isnumber() ? (long) v.todouble()
                : org.rtk.map.MapServer.itemDb.idOf(v.optjstring(""));
    }

    /**
     * pc_loaditem(): kirim ulang seluruh slot inventaris.
     *
     * <p>Disapu dari belakang karena {@code sendAddItem} <b>membuang</b>
     * barang rusak dari daftar (id &lt; 4 atau tidak ada di tabel `Items`) —
     * menyapu maju akan melewati satu slot setiap kali itu terjadi.</p>
     */
    private static void kirimInventaris(org.rtk.map.User u) {
        for (int i = u.status.inventory.size() - 1; i >= 0; i--) {
            org.rtk.map.MapServer.clientView.playerInventorySlotChanged(u, i);
        }
    }

    /** magicdb_id() dari nama skrip mantra. */
    private static int mantraId(String nama) {
        return org.rtk.map.MapServer.spellDb.idOf(nama);
    }

    /** Pemain sungguhan di balik objek skrip, atau null bila pemain tiruan uji. */
    private static org.rtk.map.User pemainDari(Object self) {
        return self instanceof ScriptPlayer p && p.owner instanceof org.rtk.map.User u
                ? u : null;
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
        // bll_talkcolor: di C dipasang ke prototipe BERSAMA, jadi NPC dan mob
        // punya `msg` yang sama persis dengan pemain.
        klass.addMethod("msg", (self, args) -> {
            kirimMsg(args);
            return LuaValue.NONE;
        });

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
        // `talk` ada di defineObjectQueries (bll_extendproto) — sebelumnya di
        // sini hanya menulis log, dan definisi itu MENIMPA yang bersama
        // karena dijalankan belakangan.
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

        /**
         * bll_spawn(mob, x, y, jumlah [, peta [, pemilik]]) — lahirkan mob
         * dari skrip; mengembalikan <b>tabel mob yang lahir</b>.
         *
         * <p>Argumen pertama boleh <b>nama skrip atau nomor</b> mob, sama
         * seperti {@code addSpell} — C memilih cabangnya dengan
         * {@code lua_isnumber}.</p>
         *
         * <p>Argumen ke-5 (peta) yang bernilai <b>0 berarti "peta benda
         * ini"</b>, bukan peta nomor 0. Itu cabang eksplisit di C
         * ({@code if (m != 0)}), jadi peta 0 memang tidak bisa disebut
         * langsung — konsekuensi yang ditiru apa adanya.</p>
         */
        klass.addMethod("spawn", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl == null) {
                return new LuaTable();
            }
            LuaValue jenis = args.arg(2);
            long mobId;
            if (jenis.isnumber()) {
                mobId = (long) jenis.todouble();
            } else {
                var d = org.rtk.map.MapServer.mobs.typeByName(jenis.optjstring(""));
                if (d == null) {
                    return new LuaTable();
                }
                mobId = d.id;
            }
            int m = args.optint(6, 0);
            java.util.List<Long> ids = org.rtk.map.MapServer.mobs.spawnOneTime(engine,
                    mobId, m != 0 ? m : bl.m, args.optint(3, 0), args.optint(4, 0),
                    args.optint(5, 0), (long) args.optdouble(7, 0));

            LuaTable t = new LuaTable();
            int i = 1;
            for (long id : ids) {
                org.rtk.map.data.BlockList lahir = org.rtk.map.MapServer.blockById(id);
                if (lahir != null) {
                    t.set(i++, engine.objectRef(lahir));
                }
            }
            return t;
        });
    }

    /**
     * Method yang hanya dimiliki <b>Mob</b> — di C didaftarkan langsung ke
     * {@code mobl_type}, bukan lewat {@code bll_extendproto}, jadi NPC dan
     * pemain memang tidak punya.
     */
    static void defineMob(ScriptEngine engine, ScriptClass klass) {
        /**
         * mobl_moveghost() -&gt; {@code moveghost_mob()}: mob melangkah satu
         * petak ke arah yang dihadapinya. Dipakai 84x, hampir seluruhnya
         * oleh AI mob di {@code Accepted/Mobs/mob.lua}.
         */
        /**
         * mobl_setinddmg(idPemain, kerusakan) / mobl_setgrpdmg(...):
         * catat sumbangan kerusakan per pemain dan per grup.
         *
         * <p>⚠️ <b>Bukan tabel ancaman.</b> Ancaman menentukan siapa yang
         * dikejar mob; dua tabel ini hanya catatan siapa menyumbang berapa,
         * dipakai skrip membagi jatuhan dan pengalaman. Angkanya
         * <b>ditambahkan</b>, dan tabelnya penuh pada 50 entri — setelah itu
         * pencatatan baru gagal, mengembalikan false.</p>
         */
        klass.addMethod("setIndDmg", (self, args) -> {
            if (!(self instanceof org.rtk.map.Mob mb)) {
                return LuaValue.FALSE;
            }
            org.rtk.map.User t = org.rtk.map.MapServer.userById(
                    (long) args.optdouble(2, 0));
            if (t == null) {
                return LuaValue.FALSE;
            }
            if (!mb.indDamage.containsKey(t.status.id)
                    && mb.indDamage.size() >= org.rtk.map.Mob.MAX_DAMAGE_ENTRIES) {
                return LuaValue.FALSE;
            }
            mb.indDamage.merge(t.status.id, args.optdouble(3, 0), Double::sum);
            return LuaValue.TRUE;
        });
        klass.addMethod("setGrpDmg", (self, args) -> {
            if (!(self instanceof org.rtk.map.Mob mb)) {
                return LuaValue.FALSE;
            }
            org.rtk.map.User t = org.rtk.map.MapServer.userById(
                    (long) args.optdouble(2, 0));
            if (t == null) {
                return LuaValue.FALSE;
            }
            if (!mb.groupDamage.containsKey(t.groupId)
                    && mb.groupDamage.size() >= org.rtk.map.Mob.MAX_DAMAGE_ENTRIES) {
                return LuaValue.FALSE;
            }
            mb.groupDamage.merge(t.groupId, args.optdouble(3, 0), Double::sum);
            return LuaValue.TRUE;
        });

        klass.addMethod("moveGhost", (self, args) -> {
            if (!(self instanceof org.rtk.map.Mob mb)) {
                return LuaValue.valueOf(0);
            }
            boolean pindah = org.rtk.map.MapServer.mobs.moveGhost(engine, mb);
            return LuaValue.valueOf(pindah ? 1 : 0);
        });

        /**
         * mobl_move_ignore_object() — melangkah sambil menembus tembok dan
         * benda. Portal <b>tetap</b> menghentikannya.
         */
        klass.addMethod("moveIgnoreObject", (self, args) ->
                LuaValue.valueOf(self instanceof org.rtk.map.Mob mb
                        && org.rtk.map.MapServer.mobs.moveIgnoreObject(engine, mb)));

        /**
         * mobl_checkmove() — bolehkah mob maju satu petak ke arah yang
         * dihadapinya? Tidak menggerakkan apa pun.
         */
        klass.addMethod("checkMove", (self, args) ->
                LuaValue.valueOf(self instanceof org.rtk.map.Mob mb
                        && org.rtk.map.MapServer.mobs.checkMove(mb)));

        /**
         * mobl_moveintent(idSasaran) — hadapkan mob ke sasaran bila
         * bersebelahan. <b>Tidak memindahkan mob</b>; lihat catatan di
         * {@code MobRegistry.moveIntent}.
         */
        klass.addMethod("moveIntent", (self, args) -> {
            if (!(self instanceof org.rtk.map.Mob mb)) {
                return LuaValue.valueOf(0);
            }
            org.rtk.map.data.BlockList sasaran =
                    org.rtk.map.MapServer.blockById((long) args.optdouble(2, 0));
            return LuaValue.valueOf(
                    org.rtk.map.MapServer.mobs.moveIntent(mb, sasaran) ? 1 : 0);
        });
    }

    /**
     * Method yang hanya dimiliki <b>FloorItem</b> ({@code fll_type} di sl.c).
     *
     * <p>Keduanya menyentuh {@code trapsTable}: daftar pemain yang sudah
     * <b>menemukan</b> jebakan ini. Itu penyaring <b>penggambaran</b> —
     * jebakan yang belum ditemukan tidak digambar untuk pemain itu, tetapi
     * tetap ada di petaknya. Jangan tertukar dengan {@code looters}, yang
     * mengatur siapa berhak memungut.</p>
     */
    static void defineFloorItem(ScriptEngine engine, ScriptClass klass) {
        klass.getter = (self, attr) ->
                self instanceof ScriptAttrs a ? a.scriptAttr(attr) : null;
        klass.setter = (self, attr, value) ->
                self instanceof ScriptAttrs a && a.scriptSetAttr(attr, value);

        /** fll_addTrapSpotters(idPemain): pemain ini menemukan jebakannya. */
        klass.addMethod("addTrapSpotters", (self, args) -> {
            if (self instanceof org.rtk.map.FloorItem fl) {
                fl.addTrapSpotter((long) args.optdouble(2, 0));
            }
            return LuaValue.TRUE;
        });

        /** fll_getTrapSpotters(): daftar id pemain yang sudah menemukannya. */
        klass.addMethod("getTrapSpotters", (self, args) -> {
            LuaTable t = new LuaTable();
            if (self instanceof org.rtk.map.FloorItem fl) {
                int i = 1;
                for (int id : fl.data.trapsTable) {
                    if (id != 0) {
                        t.set(i++, LuaValue.valueOf(id));
                    }
                }
            }
            return t;
        });
    }

    /**
     * parcell_type: kiriman yang menunggu pemain.
     *
     * <p>Atributnya menggabungkan dua hal: sifat <b>barangnya</b> (id,
     * jumlah, ukiran, wujud kustom) dan sifat <b>kirimannya</b> (pengirim,
     * nomor urut, penanda NPC). Skrip pos membacanya untuk menyusun menu
     * "kiriman menunggu".</p>
     */
    static void defineParcel(ScriptEngine engine, ScriptClass klass) {
        klass.getter = (self, attr) -> {
            if (!(self instanceof org.rtk.map.Parcels.Parcel p)) {
                return null;
            }
            return switch (attr) {
                case "id" -> LuaValue.valueOf((double) p.data.id);
                case "amount" -> LuaValue.valueOf(p.data.amount);
                case "owner" -> LuaValue.valueOf((double) p.data.owner);
                case "realName" -> LuaValue.valueOf(p.data.realName);
                case "engrave" -> LuaValue.valueOf(p.data.realName);
                case "dura" -> LuaValue.valueOf(p.data.dura);
                case "protected" -> LuaValue.valueOf((double) p.data.protectedFlag);
                case "customLook" -> LuaValue.valueOf((double) p.data.customLook);
                case "customLookC" -> LuaValue.valueOf((double) p.data.customLookColor);
                case "customIcon" -> LuaValue.valueOf((double) p.data.customIcon);
                case "customIconC" -> LuaValue.valueOf((double) p.data.customIconColor);
                case "sender" -> LuaValue.valueOf((double) p.sender);
                case "pos" -> LuaValue.valueOf(p.pos);
                case "npcflag" -> LuaValue.valueOf(p.npcFlag);
                case "name" -> LuaValue.valueOf(
                        org.rtk.map.MapServer.itemDb.info(p.data.id).tampilan());
                default -> null;
            };
        };
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
                                          int tipe, boolean hidupSaja, boolean denganJebakan) {
        var map = org.rtk.map.MapServer.world.get(m);
        if (map == null) {
            return new LuaTable();
        }
        return collect(engine, map.objectsAt(x, y), tipe, hidupSaja, denganJebakan);
    }

    /**
     * bll_getobjects_helper / bll_getaliveobjects_helper: susun benda jadi
     * tabel Lua 1-basis, dengan penyaring yang sama di semua varian
     * {@code getObjects*}.
     */
    private static LuaValue collect(ScriptEngine engine,
                                    Iterable<? extends org.rtk.map.data.BlockList> src,
                                    int tipe, boolean hidupSaja) {
        return collect(engine, src, tipe, hidupSaja, true);
    }

    /**
     * @param denganJebakan false = padanan {@code map_foreachincell}, yang
     *        melewati SELURUH barang lantai bertipe {@code ITM_TRAPS};
     *        true = {@code map_foreachincellwithtraps}, yang menyertakannya.
     *        ⚠️ Penyaring di sini <b>tidak</b> melihat {@code trapsTable} —
     *        itu penyaring penggambaran, bukan penyaring pencarian benda.
     *        Sapuan area ({@code map_foreachinarea}) di C tidak punya
     *        penyaring ini sama sekali, jadi hanya varian per-petak yang
     *        membedakannya.
     */
    private static LuaValue collect(ScriptEngine engine,
                                    Iterable<? extends org.rtk.map.data.BlockList> src,
                                    int tipe, boolean hidupSaja, boolean denganJebakan) {
        LuaTable t = new LuaTable();
        int flags = tipe <= 0 ? org.rtk.map.data.BlockList.BL_ALL : tipe;
        int i = 1;
        for (org.rtk.map.data.BlockList bl : src) {
            if ((flags & bl.blFlag()) == 0) {
                continue;
            }
            if (!denganJebakan && bl instanceof org.rtk.map.FloorItem fl && fl.isTrap()) {
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
                args.optint(5, -1), false, false));
        klass.addMethod("getAliveObjectsInCell", (self, args) -> objectsAtCell(engine,
                args.optint(2, -1), args.optint(3, -1), args.optint(4, -1),
                args.optint(5, -1), true, false));
        // Beda ...WithTraps HANYA pada barang lantai bertipe ITM_TRAPS:
        // varian biasa melewatinya, varian ini menyertakannya.
        klass.addMethod("getObjectsInCellWithTraps", (self, args) -> objectsAtCell(engine,
                args.optint(2, -1), args.optint(3, -1), args.optint(4, -1),
                args.optint(5, -1), false, true));
        klass.addMethod("getAliveObjectsInCellWithTraps", (self, args) -> objectsAtCell(engine,
                args.optint(2, -1), args.optint(3, -1), args.optint(4, -1),
                args.optint(5, -1), true, true));

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

        /**
         * bll_talk(ragam, teks) -> {@code clif_speak()}: benda ini berbicara
         * dan <b>semua pemain di sekitarnya mendengar</b>.
         *
         * <p>Method skrip terbanyak kedua (698x). Tanpa ini NPC dan mob
         * tidak pernah benar-benar bersuara di layar — quest tetap berjalan
         * di sisi server, tapi pemain tidak melihat sepatah kata pun.</p>
         *
         * <p>{@code outbox} tetap diisi untuk pemain karena uji memakai
         * pemain tiruan yang tidak punya sesi jaringan.</p>
         */
        klass.addMethod("talk", (self, args) -> {
            if (self instanceof ScriptPlayer p) {
                p.outbox.add("[talk] " + args.optjstring(3, args.optjstring(2, "")));
            }
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl != null) {
                org.rtk.map.MapServer.clientView.objectSpoke(bl,
                        args.optint(2, 0), args.optjstring(3, ""));
            }
            return LuaValue.NONE;
        });

        /**
         * bll_sendaction(gerakan, lama) -> {@code clif_sendaction()}:
         * benda ini menyerang, melempar, duduk, merapal, atau makan.
         *
         * <p>Method skrip terbanyak (905x). Kait {@code onAction} hanya
         * dipanggil bila bendanya pemain — persis seperti di C.</p>
         */
        klass.addMethod("sendAction", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl == null) {
                return LuaValue.NONE;
            }
            int action = args.optint(2, 0);
            org.rtk.map.MapServer.clientView.objectActed(bl, action, args.optint(3, 0), 0);
            if (bl instanceof org.rtk.map.User u) {
                u.action = action;
                engine.doScript("onAction", null, engine.playerRef(u.scriptPlayer()));
            }
            return LuaValue.NONE;
        });

        /**
         * bll_selfanimation(pemain, animasi, kali): animasi dimainkan pada
         * <b>benda ini</b>, tetapi hanya pemain yang disebut yang melihatnya.
         *
         * <p>⚠️ Pembagian perannya mudah tertukar: argumen pertama adalah
         * <b>penonton</b>, sedangkan yang dianimasikan adalah bendanya
         * sendiri. Pemainnya boleh disebut dengan id atau nama.</p>
         */
        klass.addMethod("selfAnimation", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            org.rtk.map.User penonton = pemainDariArg(args.arg(2));
            if (bl != null && penonton != null) {
                org.rtk.map.MapServer.clientView.objectAnimationSeenBy(penonton, bl,
                        args.optint(3, 0), args.optint(4, 0));
            }
            return LuaValue.NONE;
        });

        /** bll_selfanimationxy(pemain, animasi, x, y, kali): pada sebuah petak. */
        klass.addMethod("selfAnimationXY", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            org.rtk.map.User penonton = pemainDariArg(args.arg(2));
            if (bl != null && penonton != null) {
                // Animasi petak tidak punya varian satu-penonton di Java;
                // jalur areanya dipakai, sama seperti sendAnimationXY biasa.
                org.rtk.map.MapServer.clientView.objectAnimationAt(bl,
                        args.optint(3, 0), args.optint(6, 0),
                        args.optint(4, 0), args.optint(5, 0));
            }
            return LuaValue.NONE;
        });

        /**
         * bll_sendparcel(penerima, pengirim, barang, jumlah, …): titipkan
         * barang untuk pemain lain lewat tabel {@code Parcels}.
         *
         * <p>Ada di {@code bll_extendproto}, jadi NPC pos pun bisa
         * mengirimkannya — dan memang itu pemakaian utamanya.</p>
         */
        klass.addMethod("sendParcel", (self, args) ->
                LuaValue.valueOf(org.rtk.map.Parcels.send(
                        org.rtk.map.MapServer.sql,
                        (long) args.optdouble(2, 0), (long) args.optdouble(3, 0),
                        (long) args.optdouble(4, 0), args.optint(5, 0),
                        (long) args.optdouble(6, 0), args.optjstring(7, ""),
                        args.optint(8, 0),
                        (long) args.optdouble(9, 0), (long) args.optdouble(10, 0),
                        (long) args.optdouble(11, 0), (long) args.optdouble(12, 0),
                        (long) args.optdouble(13, 0), args.optint(14, 0))));

        /** bll_playsound(bunyi) -> {@code clif_playsound()}. */
        klass.addMethod("playSound", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl != null) {
                org.rtk.map.MapServer.clientView.soundPlayed(bl, args.optint(2, 0));
            }
            return LuaValue.NONE;
        });

        /**
         * bll_updatestate() -> gambar ulang benda ini pada pemain di
         * sekitarnya, di tempatnya sekarang.
         *
         * <p>Dipakai skrip setiap kali wujud berubah tanpa berpindah:
         * menyamar, menghilang, mati jadi hantu, berganti perlengkapan.</p>
         */
        klass.addMethod("updateState", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl == null) {
                return LuaValue.FALSE;
            }
            org.rtk.map.MapServer.clientView.objectAppearanceChanged(bl);
            return LuaValue.TRUE;
        });

        /**
         * bll_delete(): cabut benda ini dari dunia untuk selamanya.
         *
         * <p>Pemain <b>dilewati</b> — di C {@code bll_delete} langsung
         * return untuk BL_PC, karena benda pemain tidak boleh dibebaskan
         * dari bawah sesinya.</p>
         */
        klass.addMethod("delete", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl == null || bl instanceof org.rtk.map.User) {
                return LuaValue.NONE;
            }
            if (bl instanceof org.rtk.map.Mob mb) {
                org.rtk.map.MapServer.mobs.remove(mb, org.rtk.map.MapServer.world);
            } else if (bl instanceof org.rtk.map.Npc nd) {
                org.rtk.map.MapServer.npcs.remove(nd, org.rtk.map.MapServer.world);
            }
            if (bl.id > 0) {
                org.rtk.map.MapServer.clientView.objectRemoved(bl);
            }
            return LuaValue.NONE;
        });

        /**
         * bll_dropitem(barang, jumlah [, dura, protected, idPemain]) —
         * jatuhkan barang ke petak tempat benda ini berdiri.
         *
         * <p>Nama barang boleh <b>teks atau nomor</b>. {@code dura} dan
         * {@code protected} bernilai 0 berarti "pakai bawaan jenisnya" —
         * bukan "nol"; itu cabang eksplisit di C.</p>
         */
        klass.addMethod("dropItem", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl == null) {
                return LuaValue.NONE;
            }
            jatuhkan(bl, args, bl.m, bl.x, bl.y, 5, 6, 7);
            return LuaValue.NONE;
        });

        /** bll_dropitemxy(...): sama, tapi peta dan petaknya disebut sendiri. */
        klass.addMethod("dropItemXY", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl == null) {
                return LuaValue.NONE;
            }
            jatuhkan(bl, args, args.optint(6, bl.m), args.optint(7, bl.x),
                    args.optint(8, bl.y), 8, 9, 10);
            return LuaValue.NONE;
        });

        /**
         * bll_throw(x, y, ikon, warna, gerakan) — animasi benda terlempar
         * dari sini ke petak tujuan (opcode 0x16).
         *
         * <p>⚠️ <b>Tidak menjatuhkan barang apa pun.</b> Namanya menyesatkan:
         * ini murni animasi, dan ladang [12] sengaja 0 supaya klien
         * <b>tidak</b> meninggalkan gambar di tanah. Yang benar-benar
         * menjatuhkan barang adalah {@code dropItemXY}, yang biasanya
         * dipanggil skrip tepat sesudahnya.</p>
         */
        klass.addMethod("throw", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl != null) {
                org.rtk.map.MapServer.clientView.objectThrown(bl,
                        args.optint(2, 0), args.optint(3, 0), args.optint(4, 0),
                        args.optint(5, 0), args.optint(6, 0));
            }
            return LuaValue.NONE;
        });

        /** bll_deliddb(): cabut benda dari indeks id saja, tanpa menghapusnya. */
        klass.addMethod("delFromIDDB", (self, args) -> {
            org.rtk.map.data.BlockList bl = blockOf(self);
            if (bl != null) {
                org.rtk.map.MapServer.npcs.unregister(bl);
            }
            return LuaValue.NONE;
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
