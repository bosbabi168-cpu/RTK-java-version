package org.rtk.map;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Config;
import org.rtk.common.Core;
import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;
import org.rtk.common.Sql;
import org.rtk.common.TimerSystem;


/**
 * SKELETON port of map/map.c and map/intif.c.
 *
 * The original map server is ~35.000 lines of C (movement, combat, items,
 * mobs, NPCs, magic, boards, ...) plus an 11.000-line Lua binding (sl.c)
 * that runs the 900+ gameplay scripts in rtklua/. That part is NOT ported
 * yet — see the project README for the roadmap.
 *
 * What this skeleton already does, protocol-compatible with the C servers:
 *  - reads conf/map.conf and conf/inter.conf
 *  - connects and authenticates to the char server (0x3000 / 0x3800)
 *  - registers its map list (0x3001), read from the `Maps` table when a
 *    database is available, otherwise map id 0
 *  - accepts routed player logins (0x3802) and acknowledges them (0x3002)
 *    so the login flow completes end-to-end
 *  - listens on map_port and logs incoming client connections
 */
public final class MapServer {

    private static final Logger log = LogManager.getLogger(MapServer.class);

    public static int serverId = 0;

    // defaults from rtk-server.properties; conf/*.conf overrides
    public static String mapIpS = "127.0.0.1";
    public static int mapIp;
    public static int mapPort = org.rtk.common.Props.getInt("map.port", 2001);

    public static String charIpS = "127.0.0.1";
    public static int charPort = org.rtk.common.Props.getInt("char.port", 2005);

    private static final long RECONNECT_MS =
            org.rtk.common.Props.getInt("inter.reconnect_seconds", 10) * 1000L;
    public static String charId = "";
    public static String charPw = "";

    public static String sqlId = "";
    public static String sqlPw = "";
    public static String sqlDb = "";
    public static String sqlIp = "";
    public static int sqlPort;

    public static int charFd;
    public static int mapFd;

    /** Folder berkas .map; nama dari tabel `Maps` dicari relatif ke sini. */
    public static String mapPath = org.rtk.common.Props.get("map.path", "maps");

    /** Folder berkas data teks (level_db.txt, guide_db.txt). */
    public static String dbPath = org.rtk.common.Props.get("db.path", "db");

    public static int luaEnable = org.rtk.common.Props.getInt("lua.enable", 1);
    public static String luaPath = org.rtk.common.Props.get("lua.path", "luascript");
    public static org.rtk.map.script.ScriptEngine scriptEngine;

    /** Pemain yang sedang online, dikunci fd klien. */
    public static final java.util.Map<Integer, User> onlineChars =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final Sql sql = new Sql();

    /** NPC di server ini + indeks id global (padanan `id_db` di C). */
    public static final NpcRegistry npcs = new NpcRegistry();

    /** Jenis mob + mob yang hidup di server ini. */
    public static final MobRegistry mobs = new MobRegistry();

    /** Tampilan barang (itemdb_look / itemdb_lookcolor). */
    public static final org.rtk.map.data.ItemDb itemDb = new org.rtk.map.data.ItemDb();

    /** Nama mantra -> id (magicdb_id). */
    public static final org.rtk.map.data.SpellDb spellDb = new org.rtk.map.data.SpellDb();

    /** Tabel pengalaman per path, dari db/level_db.txt. */
    public static final org.rtk.map.data.ClassDb classDb = new org.rtk.map.data.ClassDb();

    /**
     * Jembatan logika permainan -&gt; klien. Tukar isinya untuk mengganti
     * protokol; logika permainan tidak perlu diubah sama sekali.
     *
     * <p>⚠️ Kode logika <b>jangan</b> memanggil {@code Clif.*} langsung —
     * lewat sini. Lihat {@link ClientView} untuk alasannya.</p>
     */
    public static ClientView clientView = new RetroTkClientView();

    /** Lapisan jaringan + timer milik map server sendiri. */
    public static final NetServer net = new NetServer("map");
    public static final TimerSystem timers = new TimerSystem();
    private static Core core;
    static final List<Integer> loadedMaps = new ArrayList<>();

    /** Dunia: seluruh peta milik server ini beserta isinya. */
    public static final org.rtk.map.data.MapRegistry world = new org.rtk.map.data.MapRegistry();

    private MapServer() {
    }

    /** inet_addr(): dotted quad to s_addr-style int (first octet lowest). */
    static int inetAddr(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) {
            return 0;
        }
        return (Integer.parseInt(p[0]) & 0xFF)
                | ((Integer.parseInt(p[1]) & 0xFF) << 8)
                | ((Integer.parseInt(p[2]) & 0xFF) << 16)
                | ((Integer.parseInt(p[3]) & 0xFF) << 24);
    }

    static void configRead(String cfgFile) {
        Config.read(cfgFile, (key, value) -> {
            switch (key.toLowerCase()) {
                case "map_ip": mapIpS = value; mapIp = inetAddr(value); break;
                case "map_port": mapPort = Integer.parseInt(value); break;
                case "char_ip": charIpS = value; break;
                case "char_port": charPort = Integer.parseInt(value); break;
                case "char_id": charId = value; break;
                case "char_pw": charPw = value; break;
                case "serverid": serverId = Integer.parseInt(value); break;
                case "map_path": mapPath = value; break;
                case "db_path": dbPath = value; break;
                case "lua_enable": luaEnable = Integer.parseInt(value); break;
                case "lua_path": luaPath = value; break;
                case "map_log": ServerLog.setLogfile(value); break;
                case "dump_log": ServerLog.setDmpfile(value); break;
                case "sql_ip": sqlIp = value; break;
                case "sql_port": sqlPort = Integer.parseInt(value); break;
                case "sql_id": sqlId = value; break;
                case "sql_pw": sqlPw = value; break;
                case "sql_db": sqlDb = value; break;
                default: break;
            }
        });
    }

    /**
     * map_read(): muat seluruh peta milik server ini — metadata dari tabel
     * `Maps` dan geometri dari berkas .map di {@link #mapPath}.
     *
     * Bila database tidak tersedia, server tetap hidup dengan daftar peta
     * kosong; kelemahan itu dilaporkan supaya tidak terlihat seperti sukses.
     */
    static void loadMaps() {
        loadedMaps.clear();
        int n = world.load(sql, serverId, mapPath);
        if (n == 0) {
            log.warn("[MAP] tidak ada peta yang dimuat untuk ServerId {} "
                    + "(database belum siap, atau kolom MapServer tidak cocok)", serverId);
        }
        loadedMaps.addAll(world.mapIds());
        if (world.missingFiles() > 0) {
            log.warn("[MAP] {} berkas peta tidak ditemukan di {}", world.missingFiles(), mapPath);
        }
        if (n > 0) {
            world.loadWarps(sql);
            npcs.load(sql, serverId, world);
            itemDb.load(sql);
            spellDb.load(sql);
            mobs.loadTypes(sql);
            mobs.useIdIndex(npcs);   // mob ikut indeks id global (map_addiddb)
            mobs.loadSpawns(sql, serverId, world);
        }
        classDb.load(dbPath);
    }

    /** map_id2sd(): pemain online dengan id itu, atau null. */
    public static User userById(long id) {
        for (User u : onlineChars.values()) {
            if (u.id == id || u.status.id == id) {
                return u;
            }
        }
        return null;
    }

    /** map_name2sd(): pemain online dengan nama itu (abai besar-kecil), atau null. */
    public static User userByName(String name) {
        if (name == null) {
            return null;
        }
        for (User u : onlineChars.values()) {
            if (u.status.name.equalsIgnoreCase(name)) {
                return u;
            }
        }
        return null;
    }

    /**
     * map_id2bl(): cari benda dari id blok. Indeks idnya kebetulan tinggal
     * di {@link NpcRegistry} — namanya menyesatkan, isinya NPC <b>dan</b>
     * mob. Pemain dicari terpisah karena tidak masuk indeks itu.
     */
    public static org.rtk.map.data.BlockList blockById(long id) {
        if (npcs != null) {
            org.rtk.map.data.BlockList bl = npcs.byId(id);
            if (bl != null) {
                return bl;
            }
        }
        for (User u : onlineChars.values()) {
            if (u.id == id || u.status.id == id) {
                return u;
            }
        }
        return null;
    }

    /**
     * map_foreachinarea(..., SAMEMAP, ...): seluruh benda sejenis di satu
     * peta. Dipakai skrip lewat {@code getObjectsInMap}.
     */
    public static java.util.List<Object> objectsInMap(int m, int type) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        var map = world.get(m);
        if (map == null) {
            return out;
        }
        int flags = type <= 0 ? org.rtk.map.data.BlockList.BL_ALL : type;

        if ((flags & org.rtk.map.data.BlockList.BL_PC) != 0) {
            for (User u : onlineChars.values()) {
                if (u.m == m) {
                    out.add(u);
                }
            }
        }
        if ((flags & org.rtk.map.data.BlockList.BL_NPC) != 0) {
            for (Npc nd : npcs.all()) {
                if (nd.m == m) {
                    out.add(nd);
                }
            }
        }
        if ((flags & org.rtk.map.data.BlockList.BL_MOB) != 0) {
            for (Mob mb : mobs.all()) {
                if (mb.m == m && mb.isAlive()) {
                    out.add(mb);
                }
            }
        }
        return out;
    }

    /** check_connect_char(): (re)establish the char-server link. */
    /**
     * Satu tik durasi mantra untuk <b>semua</b> pemain online.
     *
     * <p>Salinan daftarnya diambil dulu karena kait skrip
     * ({@code uncast}, {@code while_cast}) boleh membuat pemain keluar
     * dunia, dan itu mengubah {@code onlineChars} di tengah sapuan.</p>
     */
    static int duraTick() {
        if (scriptEngine == null) {
            return 0;
        }
        for (User sd : new java.util.ArrayList<>(onlineChars.values())) {
            try {
                Durations.tick(scriptEngine, sd);
            } catch (RuntimeException e) {
                log.error("[DURASI] tik gagal untuk '{}'", sd.status.name, e);
            }
        }
        return 0;
    }

    static int checkConnectChar() {
        if (charFd <= 0 || net.session(charFd) == null) {
            log.info("Attempt to connect to char-server...");
            charFd = net.makeConnection(charIpS, charPort);
            if (charFd <= 0) {
                charFd = 0;
                return 0;
            }
            Session s = net.session(charFd);
            s.funcParse = MapIntif::intifParse;
            s.wfifoW(0, 0x3000);
            s.wfifoStringFixed(2, charId, 32);
            s.wfifoStringFixed(34, charPw, 32);
            s.wfifoL(66, mapIp);
            s.wfifoW(70, mapPort);
            s.wfifoSet(72);
        }
        return 0;
    }

    /**
     * clif_parse(): paket dari klien permainan.
     *
     * Setiap paket berbentuk 0xAA + panjang big-endian + opcode. Sebelum
     * pemain terautentikasi ({@code session_data} masih kosong), hanya
     * opcode 0x10 yang dilayani — persis seperti versi C.
     */
    static int clientParse(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            handleDisconnect(fd);
            net.sessionEof(fd);
            return 0;
        }

        // buang byte sampah sampai penanda paket ditemukan
        if (s.rfifoRest() > 0 && s.rfifoB(0) != 0xAA) {
            log.warn("[MAP] byte tak dikenal dari {} — koneksi ditutup", s.clientIpString());
            s.eof = true;
            return 0;
        }
        if (s.rfifoRest() < 3) {
            return 0;
        }
        int len = s.rfifoWBE(1) + 3;
        if (s.rfifoRest() < len) {
            return 0;
        }

        User sd = onlineChars.get(fd);
        int opcode = s.rfifoB(3);

        if (sd == null) {
            // Paket perkenalan TIDAK didekripsi. Di C cabang !sd menangani
            // 0x10 lalu return, sementara decrypt(fd) baru dipanggil setelah
            // nullpo_ret(0, sd) (clif.c:11304-11317 vs 11359). Masuk akal:
            // kunci sesi berasal dari data karakter, yang belum ada saat
            // klien baru memperkenalkan diri. Mendekripsinya di sini membuat
            // byte mulai offset 5 ter-XOR — opcode tetap 0x10 (crypt mulai
            // dari off+5) tapi panjang nama di offset 15 jadi sampah.
            if (opcode == 0x10) {
                clientAuth(fd, s);
            } else {
                log.debug("[MAP] opcode 0x{} diabaikan: klien belum terautentikasi",
                        String.format("%02X", opcode));
            }
        } else {
            Clif.decrypt(s, sd);
            switch (opcode) {
                case 0x06, 0x32 -> Clif.parseWalk(sd);
                case 0x43 -> Clif.parseClick(sd);
                case 0x39 -> Clif.parseMenuInput(sd);
                case 0x3A -> Clif.parseNpcDialog(sd);
                default -> log.debug("[MAP] opcode 0x{} belum diport (dari {})",
                        String.format("%02X", opcode), sd.name());
            }
        }

        if (net.session(fd) != null && s.rfifoRest() >= len) {
            s.rfifoSkip(len);
        }
        return 0;
    }

    /**
     * clif_accept2(): klien memperkenalkan diri setelah dialihkan dari
     * login server. Namanya dicari di database, lalu datanya diminta ke
     * char server lewat 0x3003.
     */
    private static void clientAuth(int fd, Session s) {
        int nameLen = s.rfifoB(15);
        if (nameLen <= 0 || nameLen > 16) {
            log.warn("[MAP] panjang nama tidak masuk akal ({}) dari {}", nameLen, s.clientIpString());
            s.eof = true;
            return;
        }
        String name = s.rfifoString(16, nameLen);

        Integer charId = sql.queryInt("SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", name);
        if (charId == null || charId == 0) {
            log.warn("[MAP] karakter '{}' dari {} tidak ada di database", name, s.clientIpString());
            s.eof = true;
            return;
        }
        s.name = name;
        log.info("[MAP] klien {} memperkenalkan diri sebagai '{}' (id {})",
                s.clientIpString(), name, charId);
        MapIntif.requestChar(fd, charId, name);
    }

    /** Pemain terputus: simpan lalu lepaskan dari dunia. */
    static void handleDisconnect(int fd) {
        User sd = onlineChars.remove(fd);
        if (sd == null) {
            return;
        }
        Pc.despawn(world, sd);
        // simpan + tandai offline lewat char server (0x3007)
        sd.status.lastPos = new org.rtk.common.mmo.Point(sd.m, sd.x, sd.y);
        MapIntif.saveChar(sd.status, true);
        log.info("[MAP] {} keluar dari dunia", sd.name());
    }

    static int clientAccept(int fd) {
        Session s = net.session(fd);
        if (s != null) {
            log.info("[MAP] Client connected from: {}", s.clientIpString());
        }
        return 0;
    }

    public static void main(String[] args) {
        String confFile = "conf/map.conf";
        String interFile = "conf/inter.conf";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--conf": confFile = args[++i]; break;
                case "--inter": interFile = args[++i]; break;
                default: break;
            }
        }

        ServerLog.setLogfile("logs/map.log");
        ServerLog.setDmpfile("logs/map_dump.log");

        net.doSocket();

        configRead(confFile);
        configRead(interFile);
        configRead("conf/char.conf"); // sql settings

        if (!sql.connect(sqlId, sqlPw, sqlIp, sqlPort, sqlDb)) {
            log.warn("[MAP] WARNING: no database connection; running with map 0 only.");
        }
        loadMaps();

        // Folder berkas .map. Geometri peta belum dimuat (map server masih
        // skeleton), tapi path-nya sudah dikonfigurasi dan divalidasi di sini
        // supaya salah setel ketahuan sejak start, bukan nanti saat dipakai.
        java.nio.file.Path mapDir = java.nio.file.Paths.get(mapPath);
        if (java.nio.file.Files.isDirectory(mapDir)) {
            log.info("[MAP] Folder peta: {}", mapDir.toAbsolutePath().normalize());
        } else {
            log.warn("[MAP] Folder peta tidak ditemukan: {} (setel map_path di conf/map.conf)",
                    mapDir.toAbsolutePath().normalize());
        }

        // scripting subsystem (sl_init): load the original rtklua content
        if (luaEnable != 0) {
            try {
                scriptEngine = new org.rtk.map.script.ScriptEngine();
                // map_id2sd / map_name2sd untuk konstruktor Player(id).
                scriptEngine.setPlayerLookup(
                        new org.rtk.map.script.ScriptEngine.PlayerLookup() {
                    @Override
                    public org.rtk.map.script.ScriptPlayer byId(long id) {
                        for (User u : onlineChars.values()) {
                            if (u.id == id || u.status.id == id) {
                                return u.scriptPlayer();
                            }
                        }
                        return null;
                    }

                    @Override
                    public org.rtk.map.script.ScriptPlayer byName(String name) {
                        for (User u : onlineChars.values()) {
                            if (u.status.name.equalsIgnoreCase(name)) {
                                return u.scriptPlayer();
                            }
                        }
                        return null;
                    }
                });
                int errors = scriptEngine.init(luaPath);
                if (errors < 0) {
                    log.warn("[MAP] Lua scripting disabled: rtklua not found at {} (set lua_path in map.conf)", luaPath);
                    scriptEngine = null;
                } else {
                    log.info("[MAP] Lua scripting ready: {} scripts loaded, {} with errors",
                            scriptEngine.loadedFiles(), scriptEngine.loadErrors());
                }
            } catch (RuntimeException e) {
                log.error("[MAP] Lua scripting failed to start", e);
                scriptEngine = null;
            }
        }

        ServerLog.addLog("");
        ServerLog.addLog("RetroTK Map Server (Java skeleton) Started.%n");

        net.setDefaultAccept(MapServer::clientAccept);
        net.setDefaultParse(MapServer::clientParse);
        mapFd = net.makeListenPort(mapPort);

        timers.insert(1000, RECONNECT_MS, (a, b) -> checkConnectChar(), 0, 0);
        // npc_runtimers(): tik 100 ms untuk kait `move` dan `action` milik NPC
        timers.insert(NpcRegistry.TICK_MS, NpcRegistry.TICK_MS,
                (a, b) -> npcs.runTimers(scriptEngine), 0, 0);
        // mob_timer_spawns(): tik 50 ms untuk AI mob dan kelahiran ulang
        timers.insert(MobRegistry.TICK_MS, MobRegistry.TICK_MS,
                (a, b) -> mobs.runTimers(scriptEngine, world), 0, 0);
        // bl_duratimer(): tik 1 detik untuk durasi & aether mantra, kait
        // `while_passive` / `while_equipped` / `while_cast`, dan `uncast`
        // saat durasinya habis. Di C tiap pemain punya timernya sendiri;
        // di sini satu timer menyapu semua pemain online, karena logika
        // permainan berjalan di satu thread (lihat Peringatan #8).
        timers.insert(1000, 1000, (a, b) -> duraTick(), 0, 0);

        log.info("RetroTK Map Server (Java skeleton) is ready! Listening at {}.", mapPort);
        ServerLog.addLog("Server Ready! Listening at %d.%n", mapPort);

        core = new Core(net, timers);
        core.mainLoop();
    }
}
