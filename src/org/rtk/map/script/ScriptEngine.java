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
    public final java.util.Map<String, Integer> mapRegistry = new java.util.HashMap<>();
    LuaValue gameRegistryUdata;
    LuaValue mapRegistryUdata;

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
        registerClass(playerClass);

        npcClass.ctor = args -> new GameObject("NPC", args);
        Bindings.defineBlockList(this, npcClass);
        registerClass(npcClass);

        mobClass.ctor = args -> new GameObject("Mob", args);
        Bindings.defineBlockList(this, mobClass);
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

        // server-wide registries (gameregl / mapregl in sl.c). TODO: split
        // mapRegistry per map id once map.c is ported.
        ScriptClass gameReg = Bindings.defineRegistry(this, "Gameregistry",
                (s, k) -> LuaValue.valueOf(gameRegistry.getOrDefault(k, 0)),
                (s, k, v) -> gameRegistry.put(k, v.toint()));
        registerClass(gameReg);
        gameRegistryUdata = newInstance(gameReg, gameRegistry);
        ScriptClass mapReg = Bindings.defineRegistry(this, "Mapregistry",
                (s, k) -> LuaValue.valueOf(mapRegistry.getOrDefault(k, 0)),
                (s, k, v) -> mapRegistry.put(k, v.toint()));
        registerClass(mapReg);
        mapRegistryUdata = newInstance(mapReg, mapRegistry);

        // remaining typel classes: constructible placeholders until their
        // engine subsystems are ported
        for (String name : new String[]{"Item", "FloorItem", "BankItem", "BoundItem",
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
            "getMapRegistry", "getMapTitle", "getMapUsers", "getMapXMax", "getMapYMax",
            "getMobAttributes", "getObject", "getObjectsMap", "getOfflineID", "getPass",
            "getPoems", "getSetItems", "getSpellLevel", "getTile", "getWarp", "getWarps",
            "getWeather", "getWeatherM", "getWisdomStarMultiplier", "guitext",
            "listAuction", "processKanDonations", "removeAuction", "removeClanMember",
            "removeMapModifier", "removeMapModifierId", "removePathMember", "saveMap",
            "selectBulletinBoard", "sendMeta", "setClanBankSlots", "setClanName",
            "setClanTribute", "setKanDonationPoints", "setLight", "setMap",
            "setMapAttribute", "setMapPvP", "setMapRegistry", "setMapTitle", "setObject",
            "setOfflinePlayerRegistry", "setPass", "setPostColor", "setTile", "setWarps",
            "setWeather", "setWeatherM", "setWisdomStarMultiplier",
            "updateClanMemberRank", "updateClanMemberTitle"
        };
        for (String name : stubs) {
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

    private VarArgFunction stub(String name) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (warnedStubs.add(name)) {
                    log.warn("Lua binding not implemented yet: {}()", name);
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
            log.error("script error in {}.{}: {}", root, method, e.getMessage());
            return true;
        }
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
