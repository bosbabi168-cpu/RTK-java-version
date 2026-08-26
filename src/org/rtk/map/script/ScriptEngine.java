package org.rtk.map.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Port of map/sl.c on top of LuaJ (extLib/luaj-jse-3.0.1.jar): the scripting
 * subsystem that runs the original rtklua content unchanged.
 *
 * Mirrors the C architecture:
 *  - one global Lua state; Developers/sys.lua is loaded first, then every
 *    .lua file under Accepted/ and Developers/ (sl_reload / sl_loaddir)
 *  - the "typel" object system: game objects (Player, NPC, Mob, ...) are
 *    userdata with a shared metatable whose __index resolves
 *    getattr -> prototype methods -> instance data table
 *  - events dispatched by global-table name: doScript("blood", "click", ...)
 *    == sl_doscript_blargs, guarded by the Lua _errhandler
 *  - per-player coroutines for blocking dialogs: _async() starts one
 *    (sd->coref), dialog methods yield, and the engine resumes the coroutine
 *    with the client's answer (sl_async / sl_async_resume)
 *
 * Engine methods that are not implemented yet are registered as warn-once
 * stubs, so the 900+ scripts load and every missing binding is reported
 * instead of crashing the loader.
 */
public final class ScriptEngine {

    private static final Logger log = LogManager.getLogger(ScriptEngine.class);

    private final Globals globals;
    private final LuaTable instanceMeta = new LuaTable();
    private final Set<String> warnedStubs = new HashSet<>();
    private final List<String> loadErrorList = new ArrayList<>();
    private String luaRoot;
    private int loadedFiles;
    private int loadErrors;

    public final ScriptClass playerClass = new ScriptClass("Player");
    public final ScriptClass npcClass = new ScriptClass("NPC");
    public final ScriptClass mobClass = new ScriptClass("Mob");
    public ScriptClass registryClass;
    public ScriptClass registryStringClass;
    public ScriptClass npcIntClass;
    public ScriptClass questClass;

    /** Server-wide registries (game / map scope). */
    public final java.util.Map<String, Integer> gameRegistry = new java.util.HashMap<>();
    /**
     * map[m].registry: registry <b>per peta</b>, bukan satu untuk seluruh
     * dunia. Kuncinya dicocokkan {@code strcmpi} di C, jadi di sini
     * dinormalkan ke huruf kecil.
     */
    public final java.util.Map<Integer, java.util.Map<String, Integer>> mapRegistries =
            new java.util.HashMap<>();

    /** map_readglobalreg/map_setglobalreg: registry milik satu peta. */
    public java.util.Map<String, Integer> mapReg(int m) {
        return mapRegistries.computeIfAbsent(m, k -> new java.util.HashMap<>());
    }

    /** Peta tempat sebuah benda skrip berada, untuk registry per-peta. */
    static int mapIdOf(Object self) {
        if (self instanceof ScriptPlayer p) {
            return p.owner instanceof org.rtk.map.data.BlockList bl ? bl.m : p.m;
        }
        if (self instanceof org.rtk.map.data.BlockList bl) {
            return bl.m;
        }
        return -1;
    }
    LuaValue gameRegistryUdata;
    public ScriptClass mapRegistryClass;
    /** biteml_type: barang milik pemain (inventaris/perlengkapan/bank). */
    public ScriptClass boundItemClass;

    /** Generic Java object behind script types with no engine backing yet. */
    public static final class GameObject {
        public final String type;
        public final Varargs ctorArgs;

        GameObject(String type, Varargs ctorArgs) {
            this.type = type;
            this.ctorArgs = ctorArgs;
        }
    }

    public ScriptEngine() {
        globals = JsePlatform.standardGlobals();

        // Pustaka debug tidak ikut standardGlobals(), padahal
        // Developers/sys.lua memakai debug.traceback() di _errhandler —
        // tanpa ini penangan error justru ikut meledak.
        globals.load(new org.luaj.vm2.lib.DebugLib());

        // Shim kompatibilitas Lua 5.1: skrip aslinya ditulis untuk 5.1,
        // sedangkan LuaJ 3.0.1 mengikuti 5.2.
        //   unpack     -> table.unpack
        //   loadstring -> load   (dipakai 12x, antara lain oleh daftar
        //                 syarat mantra dan TOKO NPC di checkShop.lua;
        //                 tanpa shim ini toko NPC langsung error)
        globals.load("unpack = unpack or table.unpack").call();
        globals.load("loadstring = loadstring or load").call();
        buildInstanceMeta();
        registerClasses();
        registerGlobals();
    }

    public Globals globals() {
        return globals;
    }

    // ------------------------------------------------------------------
    // typel: shared instance metatable (typel_mtindex / typel_mtnewindex)
    // ------------------------------------------------------------------

    private void buildInstanceMeta() {
        instanceMeta.set("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                ScriptInstance inst = (ScriptInstance) args.arg1();
                String attr = args.checkjstring(2);
                if (inst.self == null) {
                    return NIL;
                }
                if (inst.klass.getter != null) {
                    LuaValue v = inst.klass.getter.get(inst.self, attr);
                    if (v != null) {
                        return v;
                    }
                }
                LuaValue proto = inst.klass.proto.get(attr);
                if (!proto.isnil()) {
                    return proto;
                }
                return inst.data.get(attr);
            }
        });
        instanceMeta.set("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                ScriptInstance inst = (ScriptInstance) args.arg1();
                String attr = args.checkjstring(2);
                LuaValue value = args.arg(3);
                if (inst.self == null) {
                    return NONE;
                }
                if (inst.klass.setter != null && inst.klass.setter.set(inst.self, attr, value)) {
                    return NONE;
                }
                inst.data.set(attr, value);
                return NONE;
            }
        });
    }

    /**
     * map_id2sd() / map_name2sd(): cara mesin skrip menemukan pemain online.
     * Disuntik oleh map server; pada uji unit dibiarkan null sehingga
     * {@code Player(id)} mengembalikan nil, bukan meledak.
     */
    public interface PlayerLookup {
        ScriptPlayer byId(long id);

        ScriptPlayer byName(String name);
    }

    private PlayerLookup playerLookup;

    public void setPlayerLookup(PlayerLookup lookup) {
        this.playerLookup = lookup;
    }

    /** typel_pushinst(): wrap a Java object for Lua. */
    public ScriptInstance newInstance(ScriptClass klass, Object self) {
        return new ScriptInstance(klass, self, instanceMeta);
    }

    /** typel_new(): register a class; its prototype becomes a callable global. */
    private void registerClass(ScriptClass klass) {
        if (klass.ctor != null) {
            LuaTable mt = new LuaTable();
            mt.set("__call", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    // args: (classTable, ctorArgs...)
                    Object self = klass.ctor.create(args.subargs(2));
                    // C mengembalikan nil bila id/nama tidak ketemu
                    // (`lua_pushnil` di pcl_ctor & npcl_ctor), bukan error —
                    // skrip mengandalkan itu untuk mengecek `if p then`.
                    if (self == null) {
                        return LuaValue.NIL;
                    }
                    if (self instanceof ScriptPlayer sp) {
                        return playerRef(sp);
                    }
                    return newInstance(klass, self);
                }
            });
            klass.proto.setmetatable(mt);
        }
        globals.set(klass.name, klass.proto);
    }

    // ------------------------------------------------------------------
    // script classes (the *_staticinit functions in sl.c)
    // ------------------------------------------------------------------

    private void registerClasses() {
        Bindings.definePlayer(this, playerClass);
        // pcl_ctor: Player(id) / Player(nama) mencari pemain yang SEDANG
        // online (map_id2sd / map_name2sd). Dipakai antara lain oleh
        // `bladestorm_trap.lua` untuk menemukan pemilik jebakan lewat
        // `npc.owner`. Tanpa ini Lua gagal dengan "attempt to call table".
        playerClass.ctor = args -> {
            if (playerLookup == null) {
                return null;
            }
            if (args.isnumber(1)) {
                return playerLookup.byId((long) args.todouble(1));
            }
            if (args.isstring(1)) {
                return playerLookup.byName(args.tojstring(1));
            }
            return null;
        };
        registerClass(playerClass);

        npcClass.ctor = args -> new GameObject("NPC", args);
        Bindings.defineBlockList(this, npcClass);
        registerClass(npcClass);

        mobClass.ctor = args -> new GameObject("Mob", args);
        Bindings.defineBlockList(this, mobClass);
        Bindings.defineMob(this, mobClass);
        registerClass(mobClass);

        // registry views over the player (regl / reglstring / npcintregl / questregl)
        registryClass = Bindings.defineRegistry(this, "Registry",
                (p, k) -> LuaValue.valueOf(((ScriptPlayer) p).registry.getOrDefault(k, 0)),
                (p, k, v) -> ((ScriptPlayer) p).registry.put(k, v.toint()));
        registerClass(registryClass);
        registryStringClass = Bindings.defineRegistry(this, "RegistryString",
                (p, k) -> LuaValue.valueOf(((ScriptPlayer) p).registryString.getOrDefault(k, "")),
                (p, k, v) -> ((ScriptPlayer) p).registryString.put(k, v.tojstring()));
        registerClass(registryStringClass);
        npcIntClass = Bindings.defineRegistry(this, "NpcInt",
                (p, k) -> LuaValue.valueOf(((ScriptPlayer) p).npcInt.getOrDefault(k, 0)),
                (p, k, v) -> ((ScriptPlayer) p).npcInt.put(k, v.toint()));
        registerClass(npcIntClass);
        questClass = Bindings.defineRegistry(this, "Quest",
                (p, k) -> LuaValue.valueOf(((ScriptPlayer) p).questReg.getOrDefault(k, 0)),
                (p, k, v) -> ((ScriptPlayer) p).questReg.put(k, v.toint()));
        registerClass(questClass);

        // Registry sisi server: gameregl global, mapregl PER PETA
        // (map_readglobalreg(m, ...) di C) — bukan satu tabel bersama.
        ScriptClass gameReg = Bindings.defineRegistry(this, "Gameregistry",
                (s, k) -> LuaValue.valueOf(gameRegistry.getOrDefault(k, 0)),
                (s, k, v) -> gameRegistry.put(k, v.toint()));
        registerClass(gameReg);
        gameRegistryUdata = newInstance(gameReg, gameRegistry);
        mapRegistryClass = Bindings.defineRegistry(this, "Mapregistry",
                (s, k) -> LuaValue.valueOf(
                        mapReg(mapIdOf(s)).getOrDefault(k.toLowerCase(), 0)),
                (s, k, v) -> mapReg(mapIdOf(s)).put(k.toLowerCase(), v.toint()));
        registerClass(mapRegistryClass);

        // biteml_type ("BoundItem"): satu barang milik pemain. Getter/setter
        // diteruskan ke ScriptItem, yang meniru biteml_getattr — ladang
        // instance dijawab sendiri, sisanya jatuh ke data jenis barang.
        boundItemClass = new ScriptClass("BoundItem");
        boundItemClass.getter = (self, attr) ->
                self instanceof ScriptAttrs a ? a.scriptAttr(attr) : null;
        boundItemClass.setter = (self, attr, value) ->
                self instanceof ScriptAttrs a && a.scriptSetAttr(attr, value);
        registerClass(boundItemClass);

        // remaining typel classes: constructible placeholders until their
        // engine subsystems are ported
        for (String name : new String[]{"Item", "FloorItem", "BankItem",
                "Parcel", "Recipe", "Accountregistry"}) {
            ScriptClass klass = new ScriptClass(name);
            klass.ctor = args -> new GameObject(name, args);
            registerClass(klass);
        }
    }

    // ------------------------------------------------------------------
    // global functions (lua_register calls in sl_init)
    // ------------------------------------------------------------------

    private void registerGlobals() {
        // Konstanta slot perlengkapan. Di C dipasang lewat makro
        // SL_EXPOSE_ENUM; skrip memakainya sebagai global biasa
        // (EQ_ARMOR, EQ_WEAP, ...) di ratusan tempat.
        for (int i = 0; i < org.rtk.map.data.Equip.NAMES.length; i++) {
            globals.set(org.rtk.map.data.Equip.NAMES[i], LuaValue.valueOf(i));
        }
        // Keadaan mob (enum di map/mob.h). Skrip AI membandingkan
        // `mob.state` dengan konstanta ini 44x; tanpa mereka perbandingan
        // selalu dengan nil dan tidak pernah benar — mob "mati" tidak
        // pernah terdeteksi mati oleh skrip.
        String[] mobState = {"MOB_ALIVE", "MOB_DEAD", "MOB_PARA",
            "MOB_BLIND", "MOB_HIT", "MOB_ESCAPE"};
        for (int i = 0; i < mobState.length; i++) {
            globals.set(mobState[i], LuaValue.valueOf(i));
        }
        // Id NPC F1 — satu-satunya yang tidak mengikuti rumus id blok biasa.
        globals.set("F1_NPC", LuaValue.valueOf((double) org.rtk.map.Npc.F1_NPC));

        // Bendera BL_* — dipakai skrip 1.500+ kali untuk menyaring
        // pencarian benda. Nilainya BIT, bukan nomor urut.
        for (int i = 0; i < org.rtk.map.data.BlockList.BL_NAMES.length; i++) {
            globals.set(org.rtk.map.data.BlockList.BL_NAMES[i],
                    LuaValue.valueOf(org.rtk.map.data.BlockList.BL_VALUES[i]));
        }

        set("_async", args -> {
            ScriptPlayer p = toPlayer(args.arg1());
            if (p == null || !args.arg(2).isfunction()) {
                return LuaValue.NONE;
            }
            async(p, args.arg(2));
            return LuaValue.NONE;
        });
        set("_asyncDone", args -> {
            ScriptPlayer p = toPlayer(args.arg1());
            if (p != null) {
                p.coroutine = null;
            }
            return LuaValue.NONE;
        });

        set("realSecond", args -> LuaValue.valueOf(Calendar.getInstance().get(Calendar.SECOND)));
        set("realMinute", args -> LuaValue.valueOf(Calendar.getInstance().get(Calendar.MINUTE)));
        set("realHour", args -> LuaValue.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));
        set("realDay", args -> LuaValue.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)));
        set("curDay", args -> LuaValue.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)));
        set("curYear", args -> LuaValue.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
        set("curTime", args -> {
            Calendar c = Calendar.getInstance();
            return LuaValue.valueOf(c.get(Calendar.HOUR_OF_DAY) * 100 + c.get(Calendar.MINUTE));
        });
        // TODO: game seasons come from the in-game calendar once map.c is
        // ported; approximated with real months for now
        set("curSeason", args -> {
            int month = Calendar.getInstance().get(Calendar.MONTH); // 0..11
            return LuaValue.valueOf((month / 3) % 4 + 1);
        });
        set("curServer", args -> LuaValue.valueOf(0));

        set("timeMS", args -> LuaValue.valueOf(System.currentTimeMillis()));
        // getWarp(m, x, y): apakah petak ini portal? Mengembalikan
        // boolean, bukan tujuannya. Koordinat dijepit ke batas peta dulu,
        // persis seperti di C.
        set("getWarp", args -> {
            var md = org.rtk.map.MapServer.world.get(args.optint(1, -1));
            if (md == null) {
                return LuaValue.FALSE;
            }
            int x = Math.max(0, Math.min(args.optint(2, 0), md.xs - 1));
            int y = Math.max(0, Math.min(args.optint(3, 0), md.ys - 1));
            return LuaValue.valueOf(md.warpAt(x, y) != null);
        });

        // getPass / getObject / getTile: baca geometri peta apa adanya.
        // getPass mengembalikan 1 (= terhalang) untuk koordinat di luar
        // peta, seperti di C — bukan 0, supaya skrip kelahiran mob tidak
        // menaruh apa pun di luar batas.
        set("getPass", args -> {
            var md = org.rtk.map.MapServer.world.get(args.optint(1, -1));
            if (md == null) {
                return LuaValue.NONE;
            }
            int x = args.optint(2, 0);
            int y = args.optint(3, 0);
            if (x > md.xs - 1 || y > md.ys - 1) {
                return LuaValue.valueOf(1);
            }
            return LuaValue.valueOf(md.pass(x, y));
        });
        set("getObject", args -> {
            var md = org.rtk.map.MapServer.world.get(args.optint(1, -1));
            if (md == null) {
                return LuaValue.NONE;
            }
            return LuaValue.valueOf(md.obj(args.optint(2, 0), args.optint(3, 0)));
        });
        set("getTile", args -> {
            var md = org.rtk.map.MapServer.world.get(args.optint(1, -1));
            if (md == null) {
                return LuaValue.NONE;
            }
            return LuaValue.valueOf(md.tile(args.optint(2, 0), args.optint(3, 0)));
        });

        // sl_getMapRegistry / sl_setMapRegistry: registry per peta.
        // Peta yang tidak termuat membuat C `return 0` tanpa mendorong
        // nilai apa pun — di Lua itu terbaca nil, dan skrip yang langsung
        // menjumlahkannya akan gagal. Ditiru apa adanya.
        set("getMapRegistry", args -> {
            int m = args.optint(1, -1);
            if (org.rtk.map.MapServer.world.get(m) == null) {
                return LuaValue.NONE;
            }
            return LuaValue.valueOf(
                    mapReg(m).getOrDefault(args.optjstring(2, "").toLowerCase(), 0));
        });
        set("setMapRegistry", args -> {
            int m = args.optint(1, -1);
            if (org.rtk.map.MapServer.world.get(m) == null) {
                return LuaValue.NONE;
            }
            mapReg(m).put(args.optjstring(2, "").toLowerCase(), args.optint(3, 0));
            return LuaValue.NONE;
        });

        // getMapXMax/getMapYMax: indeks petak TERBESAR yang sah, bukan
        // ukuran peta — C mengembalikan `map[m].xs - 1`. Peta yang tidak
        // termuat menghasilkan 0, bukan error.
        set("getMapXMax", args -> {
            var md = org.rtk.map.MapServer.world.get(args.optint(1, -1));
            return LuaValue.valueOf(md == null ? 0 : md.xs - 1);
        });
        set("getMapYMax", args -> {
            var md = org.rtk.map.MapServer.world.get(args.optint(1, -1));
            return LuaValue.valueOf(md == null ? 0 : md.ys - 1);
        });

        set("getTick", args -> LuaValue.valueOf(org.rtk.common.TimerSystem.gettick()));
        set("msleep", args -> {
            try {
                Thread.sleep(args.optlong(1, 0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return LuaValue.NONE;
        });

        set("broadcast", args -> {
            log.info("[broadcast] {}", args.optjstring(1, ""));
            return LuaValue.NONE;
        });
        set("gmbroadcast", args -> {
            log.info("[gmbroadcast] {}", args.optjstring(1, ""));
            return LuaValue.NONE;
        });
        set("checkOnline", args -> LuaValue.valueOf(0));
        set("throw", args -> {
            throw new LuaError(args.optjstring(1, "script error"));
        });
        set("luaReload", args -> LuaValue.valueOf(reload()));
        // TODO: read rtk/db/level_db.txt once the level tables are ported
        set("getXPforLevel", args -> LuaValue.valueOf(0));

        // every remaining engine function becomes a warn-once stub so the
        // scripts load and missing bindings surface in the log
        String[] stubs = {
            "addClanMember", "addClanTribute", "addKanDonationPoints", "addMapModifier",
            "addMob", "addPathMember", "addToBoard", "clearPoems", "copyPoemToPoetry",
            "getAuctions", "getClanBankSlots", "getClanName", "getClanRoster",
            "getClanTribute", "getFreeMapModifierId", "getKanDonationPoints",
            "getMapAttribute", "getMapIsLoaded", "getMapModifiers", "getMapPvP",
            "getMapTitle", "getMapUsers",
            "getMobAttributes", "getObjectsMap", "getOfflineID",
            "getPoems", "getSetItems", "getSpellLevel", "getWarps",
            "getWeather", "getWeatherM", "getWisdomStarMultiplier", "guitext",
            "listAuction", "processKanDonations", "removeAuction", "removeClanMember",
            "removeMapModifier", "removeMapModifierId", "removePathMember", "saveMap",
            "selectBulletinBoard", "sendMeta", "setClanBankSlots", "setClanName",
            "setClanTribute", "setKanDonationPoints", "setLight", "setMap",
            "setMapAttribute", "setMapPvP", "setMapTitle", "setObject",
            "setOfflinePlayerRegistry", "setPass", "setPostColor", "setTile", "setWarps",
            "setWeather", "setWeatherM", "setWisdomStarMultiplier",
            "updateClanMemberRank", "updateClanMemberTitle"
        };
        // Loop ini berjalan SETELAH binding sungguhan didaftarkan, jadi
        // tanpa penjaga di bawah sebuah nama yang baru diport akan
        // tertimpa stub-nya sendiri dan tetap mengembalikan nil — persis
        // yang sempat terjadi pada getMapXMax. Jangan hapus penjaganya.
        for (String name : stubs) {
            if (!globals.get(name).isnil()) {
                log.warn("[LUA] '{}' sudah diport tetapi masih terdaftar "
                        + "sebagai stub — hapus dari daftar stubs", name);
                continue;
            }
            globals.set(name, stub(name));
        }
    }

    private interface JavaFunc {
        Varargs invoke(Varargs args);
    }

    private void set(String name, JavaFunc f) {
        globals.set(name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return f.invoke(args);
            }
        });
    }

    /**
     * Stub warn-once untuk global yang belum diport.
     *
     * <p>⚠️ <b>Namanya WAJIB disalin ke variabel lain.</b> {@code LibFunction}
     * milik LuaJ punya field {@code protected String name}, dan di dalam
     * subclass anonim {@code name} merujuk <b>field warisan itu</b>, bukan
     * parameter method ini — javac bahkan tidak membuat penangkapnya.
     * Akibatnya sebelum diperbaiki: pesan log selalu berbunyi
     * {@code null()} sehingga binding yang hilang tidak bisa dikenali,
     * <b>dan</b> {@code warnedStubs.add(null)} berhasil sekali saja
     * sehingga <b>hanya global pertama yang pernah dilaporkan</b> — sisanya
     * gagal diam-diam. Ditemukan 24 Agustus 2026 dari `map.log`.</p>
     */
    private VarArgFunction stub(String namaBinding) {
        java.util.Objects.requireNonNull(namaBinding, "nama stub null");
        return new VarArgFunction() {
            {
                // Isi field nama milik LuaJ (protected, hanya bisa disentuh
                // dari dalam subclass) supaya pesan error Lua menyebut nama
                // fungsinya, bukan "?".
                this.name = namaBinding;
            }

            @Override
            public Varargs invoke(Varargs args) {
                if (warnedStubs.add(namaBinding)) {
                    log.warn("Lua binding not implemented yet: {}()", namaBinding);
                }
                return NIL;
            }
        };
    }

    /** Warn-once helper for method-level stubs in the bindings. */
    void warnStub(String name) {
        if (warnedStubs.add(name)) {
            log.warn("Lua binding not implemented yet: {}", name);
        }
    }

    // ------------------------------------------------------------------
    // loading (sl_init / sl_reload / sl_loaddir)
    // ------------------------------------------------------------------

    /**
     * Loads sys.lua and then every script, like sl_init.
     * @return number of files that failed to load
     */
    public int init(String luaRootDir) {
        this.luaRoot = luaRootDir;
        Path sys = Paths.get(luaRootDir, "Developers", "sys.lua");
        if (!Files.isRegularFile(sys)) {
            log.error("sys.lua not found at {} - scripting disabled", sys);
            return -1;
        }
        try {
            globals.loadfile(sys.toString()).call();
            log.info("Loaded {}", sys);
        } catch (LuaError e) {
            log.error("Failed to load sys.lua: {}", e.getMessage());
            return -1;
        }
        return reload();
    }

    /** sl_reload(): (re)load Accepted/ then Developers/, skipping sys.lua. */
    public int reload() {
        loadedFiles = 0;
        loadErrors = 0;
        loadErrorList.clear();
        loadDir(Paths.get(luaRoot, "Accepted"));
        loadDir(Paths.get(luaRoot, "Developers"));
        log.info("Lua scripts loaded: {} ok, {} with errors", loadedFiles, loadErrors);
        for (String err : loadErrorList) {
            log.debug("script load error: {}", err);
        }
        return loadErrors;
    }

    private void loadDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path p : entries.sorted().collect(Collectors.toList())) {
                String fn = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    if (!fn.equals(".svn")) {
                        loadDir(p);
                    }
                } else if (fn.endsWith(".lua") && !fn.equals("sys.lua")) {
                    loadFile(p);
                }
            }
        } catch (IOException e) {
            log.error("Cannot read script directory {}: {}", dir, e.getMessage());
        }
    }

    private void loadFile(Path p) {
        try {
            globals.loadfile(p.toString()).call();
            loadedFiles++;
        } catch (RuntimeException e) {
            loadErrors++;
            String msg = String.valueOf(e.getMessage());
            loadErrorList.add(p + ": " + firstLine(msg));
            log.warn("script error in {}: {}", p.getFileName(), firstLine(msg));
        }
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    public int loadedFiles() {
        return loadedFiles;
    }

    public int loadErrors() {
        return loadErrors;
    }

    public List<String> loadErrorList() {
        return loadErrorList;
    }

    // ------------------------------------------------------------------
    // dispatch (sl_doscript_blargs)
    // ------------------------------------------------------------------

    /**
     * Calls the global {@code root.method(args...)}, guarded like the C
     * dispatcher. Returns false when no such handler exists.
     */
    public boolean doScript(String root, String method, LuaValue... args) {
        LuaValue rootVal = globals.get(root);
        if (rootVal.isnil()) {
            return false;
        }
        LuaValue f = rootVal.istable() ? rootVal.get(method) : rootVal;
        if (f.isnil()) {
            return false;
        }
        try {
            f.invoke(LuaValue.varargsOf(args));
            return true;
        } catch (LuaError e) {
            // sys.lua's _errhandler adds a traceback; LuaJ already includes
            // one in the message when the debug lib is loaded
            reportScriptError(root, method, e);
            return true;
        }
    }

    /** Sudah berapa kali tiap kesalahan skrip yang sama muncul. */
    private final java.util.Map<String, Integer> scriptErrorCount =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Catat kesalahan skrip, tetapi <b>hanya sekali per kesalahan unik</b>.
     *
     * <p>Kait skrip NPC dan mob dipanggil tiap tik timer (100 ms dan 50 ms),
     * jadi satu binding yang belum diport menghasilkan ribuan baris log
     * identik: 74.809 baris dalam beberapa menit pernah terjadi, dan log
     * jadi tidak terpakai justru saat paling dibutuhkan. Kesalahan pertama
     * dicatat lengkap; sisanya hanya dihitung.</p>
     *
     * <p>Jumlah totalnya bisa dilihat lewat {@link #scriptErrorSummary()},
     * jadi tidak ada informasi yang hilang — hanya pengulangannya yang
     * ditekan.</p>
     */
    private void reportScriptError(String root, String method, LuaError e) {
        String kunci = root + "." + method + ": " + firstLine(String.valueOf(e.getMessage()));
        int n = scriptErrorCount.merge(kunci, 1, Integer::sum);
        if (n == 1) {
            log.error("script error in {}.{}: {}", root, method, e.getMessage());
        } else if (n == 100 || n == 1000 || n % 10000 == 0) {
            // sesekali beri tanda bahwa ini masih berulang, tanpa membanjiri
            log.warn("script error in {}.{} sudah terjadi {}x (pesan sama, "
                    + "hanya dicatat sekali)", root, method, n);
        }
    }

    /**
     * Ringkasan kesalahan skrip yang pernah terjadi, terurut dari yang
     * paling sering. Dipakai saat mematikan server dan oleh alat diagnosa.
     */
    public java.util.List<String> scriptErrorSummary() {
        return scriptErrorCount.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> e.getValue() + "x  " + e.getKey())
                .toList();
    }

    // ------------------------------------------------------------------
    // coroutines (sl_async / sl_async_resume / blocking dialogs)
    // ------------------------------------------------------------------

    /** sl_async(): run func in a fresh coroutine owned by the player. */
    public void async(ScriptPlayer p, LuaValue func) {
        if (p.coroutine != null) {
            // C: "You are busy." - one interaction at a time
            return;
        }
        LuaThread thread = new LuaThread(globals, func);
        p.coroutine = thread;
        handleResume(p, thread.resume(LuaValue.NONE));
    }

    /**
     * sl_async_freeco(): buang coroutine yang sedang menunggu.
     *
     * <p>Dipakai saat pemain menutup kotak dialog, dan saat ia mengklik
     * sesuatu yang baru — di C setiap klik memulai interaksi dari nol,
     * jadi sisa dialog lama harus dilepas dulu supaya {@link #async} tidak
     * menolaknya dengan alasan "sedang sibuk".</p>
     */
    public void cancel(ScriptPlayer p) {
        p.coroutine = null;
        p.pendingDialog = null;
    }

    /** sl_async_resume(): the client answered - continue the script. */
    public void resume(ScriptPlayer p, Varargs answer) {
        LuaThread thread = p.coroutine;
        if (thread == null) {
            return;
        }
        p.pendingDialog = null;
        handleResume(p, thread.resume(answer));
    }

    private void handleResume(ScriptPlayer p, Varargs result) {
        if (!result.arg1().toboolean()) {
            log.error("script coroutine error for {}: {}", p.name, result.arg(2).tojstring());
            p.coroutine = null;
            p.pendingDialog = null;
            return;
        }
        if (p.coroutine != null && "dead".equals(p.coroutine.getStatus())) {
            p.coroutine = null;
            p.pendingDialog = null;
        }
    }

    /**
     * Used by blocking dialog bindings: park the coroutine until the client
     * answers. Returns the answer passed to {@link #resume}.
     */
    Varargs yieldBlocking(ScriptPlayer p, ScriptPlayer.PendingDialog dialog) {
        LuaThread running = globals.running;
        if (running == null || running.isMainThread()) {
            throw new LuaError("this function must be called from a coroutine; use async()");
        }
        p.pendingDialog = dialog;
        return globals.yield(LuaValue.NONE);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Bungkus benda permainan (NPC, nanti mob) jadi objek Lua.
     *
     * <p>Berbeda dari {@link #playerRef}, identitasnya tidak di-cache di
     * objeknya sendiri — NPC tidak menyimpan keadaan skrip seperti pemain,
     * jadi membuat pembungkus baru tiap panggilan aman dan menghindari
     * ladang tambahan di kelas peta.</p>
     */
    public LuaValue objectRef(Object obj) {
        if (obj == null) {
            return LuaValue.NIL;
        }
        // Bungkus menurut jenis benda sesungguhnya. Sebelumnya SEMUA benda
        // dibungkus `npcClass`; itu tidak terasa untuk mob (prototipe NPC
        // dan Mob memang berisi method yang sama) tetapi **salah** untuk
        // pemain — skrip yang menerima hasil `getObjectsInCell(..., BL_PC)`
        // memanggil method pemain dan mendapat "attempt to call nil".
        if (obj instanceof org.rtk.map.User u) {
            return playerRef(u.scriptPlayer());
        }
        if (obj instanceof ScriptPlayer p) {
            return playerRef(p);
        }
        if (obj instanceof org.rtk.map.Mob) {
            return newInstance(mobClass, obj);
        }
        return newInstance(npcClass, obj);
    }

    /** The stable userdata for a player (identity matters to scripts). */
    public LuaValue playerRef(ScriptPlayer p) {
        if (p.udata == null) {
            p.udata = newInstance(playerClass, p);
        }
        return p.udata;
    }

    static ScriptPlayer toPlayer(LuaValue v) {
        if (v instanceof ScriptInstance && ((ScriptInstance) v).self instanceof ScriptPlayer) {
            return (ScriptPlayer) ((ScriptInstance) v).self;
        }
        return null;
    }
}
