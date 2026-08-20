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

    public static int luaEnable = org.rtk.common.Props.getInt("lua.enable", 1);
    public static String luaPath = org.rtk.common.Props.get("lua.path", "luascript");
    public static org.rtk.map.script.ScriptEngine scriptEngine;

    public static final Sql sql = new Sql();

    /** Lapisan jaringan + timer milik map server sendiri. */
    public static final NetServer net = new NetServer("map");
    public static final TimerSystem timers = new TimerSystem();
    private static Core core;
    static final List<Integer> loadedMaps = new ArrayList<>();

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

    /** map_load: which maps does this server host? */
    static void loadMaps() {
        loadedMaps.clear();
        // Read the map list from the `Maps` table like map_db_load() does.
        List<Integer> ids = queryMapIds();
        if (ids.isEmpty()) {
            log.warn("[MAP] No maps found for ServerId {} - registering map 0 only.", serverId);
            loadedMaps.add(0);
        } else {
            loadedMaps.addAll(ids);
        }
        log.info("[MAP] Hosting {} map(s).", loadedMaps.size());
    }

    private static List<Integer> queryMapIds() {
        List<Integer> ids = new ArrayList<>();
        Integer probe = sql.queryInt("SELECT COUNT(*) FROM `Maps` WHERE `MapServer` = ?", serverId);
        if (probe == null || probe == 0) {
            return ids;
        }
        Object[] row;
        int offset = 0;
        while ((row = sql.queryRow("SELECT `MapId` FROM `Maps` WHERE `MapServer` = ? ORDER BY `MapId` LIMIT 1 OFFSET " + offset, 1, serverId)) != null) {
            ids.add(((Number) row[0]).intValue());
            offset++;
        }
        return ids;
    }

    /** check_connect_char(): (re)establish the char-server link. */
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
     * Client connections on map_port. Gameplay is not ported yet, so this
     * only logs and keeps the socket open.
     */
    static int clientParse(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() > 0) {
            log.debug("[MAP] Received {} byte(s) from client {} - gameplay protocol not ported yet, discarding.",
                    s.rfifoRest(), s.clientIpString());
            s.rfifoSkip(s.rfifoRest());
        }
        return 0;
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

        log.info("RetroTK Map Server (Java skeleton) is ready! Listening at {}.", mapPort);
        ServerLog.addLog("Server Ready! Listening at %d.%n", mapPort);

        core = new Core(net, timers);
        core.mainLoop();
    }
}
