package org.rtk.charserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Config;
import org.rtk.common.Core;
import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Sql;
import org.rtk.common.TimerSystem;

/**
 * Port of char/char.c — the RetroTK character server.
 *
 * Sits between the login server and the map servers: verifies characters,
 * creates new ones and routes authenticated players to the map server that
 * hosts their current map.
 */
public final class CharServer {

    private static final Logger log = LogManager.getLogger(CharServer.class);

    public static final int MAX_MAP_SERVER = 30;

    /** Port of struct map_fifo_data. */
    public static final class MapFifo {
        public int fd;
        public int ip;   // s_addr layout (first octet in lowest byte)
        public int port;
        public int mapN;
        public int[] map = new int[0];
    }

    // default from rtk-server.properties; conf/*.conf (char_port) overrides
    public static int charPort = org.rtk.common.Props.getInt("char.port", 2005);

    private static final long RECONNECT_MS =
            org.rtk.common.Props.getInt("inter.reconnect_seconds", 10) * 1000L;

    public static final MapFifo[] mapFifo = new MapFifo[MAX_MAP_SERVER];
    public static int mapFifoN = 0;
    public static int mapFifoMax = 0;

    public static int charFd;
    public static int loginFd;

    public static int startMoney = 0;
    public static int startM, startX, startY; // start_pos

    public static String charId = "";
    public static String charPw = "";

    public static String sqlId = "";
    public static String sqlPw = "";
    public static String sqlDb = "";
    public static String sqlIp = "";
    public static int sqlPort;

    public static int dumpSave = 0;

    public static String loginId = "";
    public static String loginPw = "";
    public static String loginIpS = "";
    public static int loginPort;

    public static String saveId = "";
    public static String savePw = "";
    public static String saveIpS = "";
    public static int savePort;

    public static final Sql sql = new Sql();

    /** Lapisan jaringan + timer milik char server sendiri. */
    public static final NetServer net = new NetServer("char");
    public static final TimerSystem timers = new TimerSystem();
    private static Core core;

    static {
        for (int i = 0; i < MAX_MAP_SERVER; i++) {
            mapFifo[i] = new MapFifo();
        }
    }

    private CharServer() {
    }

    /** mapfifo_from_mapid(): which map server hosts the given map id? */
    public static int mapfifoFromMapid(int map) {
        for (int i = 0; i < mapFifoMax; i++) {
            if (mapFifo[i].fd > 0) {
                for (int j = 0; j < mapFifo[i].mapN; j++) {
                    if (mapFifo[i].map[j] == map) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    static void configRead(String cfgFile) {
        boolean ok = Config.read(cfgFile, (key, value) -> {
            switch (key.toLowerCase()) {
                case "char_port": charPort = Integer.parseInt(value); break;
                case "char_id": charId = value; break;
                case "char_pw": charPw = value; break;
                case "login_ip": loginIpS = value; break;
                case "login_port": loginPort = Integer.parseInt(value); break;
                case "login_id": loginId = value; break;
                case "login_pw": loginPw = value; break;
                case "save_ip": saveIpS = value; break;
                case "save_port": savePort = Integer.parseInt(value); break;
                case "save_id": saveId = value; break;
                case "save_pw": savePw = value; break;
                case "sql_ip": sqlIp = value; break;
                case "sql_port": sqlPort = Integer.parseInt(value); break;
                case "sql_id": sqlId = value; break;
                case "sql_pw": sqlPw = value; break;
                case "sql_db": sqlDb = value; break;
                case "char_log": ServerLog.setLogfile(value); break;
                case "dump_log": ServerLog.setDmpfile(value); break;
                case "dump_save": dumpSave = Integer.parseInt(value); break;
                // Note: the C source compares strcmpi(...) == 1 for these two,
                // which is a bug that disables them; the intended behavior is
                // restored here.
                case "start_money": startMoney = Integer.parseInt(value); break;
                case "start_point": {
                    String[] parts = value.split(",");
                    if (parts.length == 3) {
                        startM = Integer.parseInt(parts[0].trim());
                        startX = Integer.parseInt(parts[1].trim());
                        startY = Integer.parseInt(parts[2].trim());
                    }
                    break;
                }
                default: break;
            }
        });
        if (!ok) {
            ServerLog.addLog("CFG_ERR: Configuration file (%s) not found.%n", cfgFile);
            log.error("Char server berhenti: berkas konfigurasi {} tidak ditemukan", cfgFile);
            System.exit(1);
        }
    }

    static void doTerm() {
        CharDb.term();
        net.sessionEof(loginFd);
        log.info("RetroTK Char Server Shutdown.");
        ServerLog.addLog("Shutdown.%n");
    }

    private static void helpScreen() {
        log.info("HELP LIST");
        log.info("---------");
        log.info(" --conf [FILENAME]  : set config file");
        log.info(" --inter [FILENAME] : set inter file");
        System.exit(0);
    }

    public static void main(String[] args) {
        String confFile = "conf/char.conf";
        String interFile = "conf/inter.conf";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help": case "--h": case "--?": case "/?":
                    helpScreen();
                    break;
                case "--conf": confFile = args[++i]; break;
                case "--inter": interFile = args[++i]; break;
                default: break;
            }
        }

        ServerLog.setLogfile("logs/char.log");
        ServerLog.setDmpfile("logs/char_dump.log");

        net.doSocket();

        configRead(confFile);
        configRead(interFile);
        core = new Core(net, timers);
        core.setTermFunc(CharServer::doTerm);

        ServerLog.addLog("");
        ServerLog.addLog("RetroTK Char Server Started.%n");

        CharDb.init();

        net.setDefaultParse(Mapif::parseAuth);
        charFd = net.makeListenPort(charPort);

        timers.insert(1000, RECONNECT_MS, (a, b) -> Logif.checkConnectLogin(), 0, 0);

        log.info("RetroTK Char Server is ready! Listening at {}.", charPort);
        ServerLog.addLog("Server Ready! Listening at %d.%n", charPort);

        core.mainLoop();
    }
}
