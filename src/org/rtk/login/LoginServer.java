package org.rtk.login;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Config;
import org.rtk.common.Core;
import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Sql;
import org.rtk.common.TimerSystem;

/**
 * Port of login/login.c — the RetroTK login server.
 *
 * Listens for game clients, validates credentials against the char server
 * and hands authenticated clients over to a map server.
 */
public final class LoginServer {

    private static final Logger log = LogManager.getLogger(LoginServer.class);

    // login message indexes (enum in login.h)
    public static final int LGN_ERRSERVER = 0;
    public static final int LGN_WRONGPASS = 1;
    public static final int LGN_WRONGUSER = 2;
    public static final int LGN_ERRDB = 3;
    public static final int LGN_USEREXIST = 4;
    public static final int LGN_ERRPASS = 5;
    public static final int LGN_ERRUSER = 6;
    public static final int LGN_NEWCHAR = 7;
    public static final int LGN_CHGPASS = 8;
    public static final int LGN_DBLLOGIN = 9;
    public static final int LGN_BANNED = 10;
    public static final int MSG_MAX = 11;

    private static final String[] MSG_KEYS = {
        "LGN_ERRSERVER", "LGN_WRONGPASS", "LGN_WRONGUSER", "LGN_ERRDB",
        "LGN_USEREXIST", "LGN_ERRPASS", "LGN_ERRUSER", "LGN_NEWCHAR",
        "LGN_CHGPASS", "LGN_DBLLOGIN", "LGN_BANNED"
    };

    // default from rtk-server.properties; conf/*.conf (login_port) overrides
    public static int loginPort = org.rtk.common.Props.getInt("login.port", 2010);

    private static final long LOCKOUT_MS =
            org.rtk.common.Props.getInt("login.lockout_minutes", 10) * 60L * 1000L;
    public static int loginFd;
    public static int charFd;
    public static int requireReg = 0;
    public static int dumpSave = 0;
    public static final String[] loginMsg = new String[MSG_MAX];

    public static String loginId = "";
    public static String loginPw = "";

    public static String sqlId = "";
    public static String sqlPw = "";
    public static String sqlDb = "";
    public static String sqlIp = "";
    public static int sqlPort;

    public static int nexVersion;
    public static int nexDeep;

    public static final List<String> metaFiles = new ArrayList<>();
    public static final Sql sql = new Sql();

    /** Lapisan jaringan + timer milik login server sendiri. */
    public static final NetServer net = new NetServer("login");
    public static final TimerSystem timers = new TimerSystem();
    private static Core core;

    // bf_lockout: invalid-login counter per client IP
    private static final Map<Integer, Integer> bfLockout = new HashMap<>();

    static final String MASK1 = "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ";
    static final String MASK2 = "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ1234567890";

    private LoginServer() {
    }

    /** Valid(): returns 1 (true) when buf contains a char outside the mask. */
    static boolean invalid(String buf, String mask) {
        for (int i = 0; i < buf.length(); i++) {
            if (mask.indexOf(buf.charAt(i)) < 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean stringCheckAllchars(String s) {
        return invalid(s, MASK1);
    }

    public static boolean stringCheck(String s) {
        return invalid(s, MASK2);
    }

    static void langRead(String cfgFile) {
        for (int i = 0; i < MSG_MAX; i++) {
            loginMsg[i] = "";
        }
        boolean ok = Config.read(cfgFile, (key, value) -> {
            for (int i = 0; i < MSG_MAX; i++) {
                if (key.equalsIgnoreCase(MSG_KEYS[i])) {
                    loginMsg[i] = value;
                    return;
                }
            }
        });
        if (ok) {
            log.info("Language File ({}) reading finished!", cfgFile);
        } else {
            log.error("CFG_ERR: Language file ({}) not found.", cfgFile);
        }
    }

    static void configRead(String cfgFile) {
        Config.read(cfgFile, (key, value) -> {
            switch (key.toLowerCase()) {
                case "login_port": loginPort = Integer.parseInt(value); break;
                case "login_id": loginId = value; break;
                case "login_pw": loginPw = value; break;
                case "login_log": ServerLog.setLogfile(value); break;
                case "dump_log": ServerLog.setDmpfile(value); break;
                case "dump_save": dumpSave = Integer.parseInt(value); break;
                case "meta": metaFiles.add(value); break;
                case "version": nexVersion = Integer.parseInt(value); break;
                case "deep": nexDeep = Integer.parseInt(value); break;
                case "sql_ip": sqlIp = value; break;
                case "sql_port": sqlPort = Integer.parseInt(value); break;
                case "sql_id": sqlId = value; break;
                case "sql_pw": sqlPw = value; break;
                case "sql_db": sqlDb = value; break;
                case "require_reg": requireReg = Integer.parseInt(value); break;
                default: break;
            }
        });
    }

    public static int getInvalidCount(int ip) {
        return bfLockout.getOrDefault(ip, 0);
    }

    public static int setInvalidCount(int ip) {
        int c = getInvalidCount(ip);
        if (c == 0) {
            // clear the counter again after login.lockout_minutes (one-shot in this port)
            timers.insert(LOCKOUT_MS, 0, (a, b) -> {
                bfLockout.remove(a);
                return 1;
            }, ip, 0);
        }
        bfLockout.put(ip, c + 1);
        return c + 1;
    }

    static void doTerm() {
        log.info("RetroTK Login Server Shutdown.");
        ServerLog.addLog("Shutdown.%n");
    }

    private static void helpScreen() {
        log.info("HELP LIST");
        log.info("---------");
        log.info(" --conf [FILENAME]  : set config file");
        log.info(" --inter [FILENAME] : set inter file");
        log.info(" --lang [FILENAME]  : set lang file");
        System.exit(0);
    }

    public static void main(String[] args) {
        String confFile = "conf/login.conf";
        String langFile = "conf/lang.conf";
        String interFile = "conf/inter.conf";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help": case "--h": case "--?": case "/?":
                    helpScreen();
                    break;
                case "--conf": confFile = args[++i]; break;
                case "--inter": interFile = args[++i]; break;
                case "--lang": langFile = args[++i]; break;
                default: break;
            }
        }

        ServerLog.setLogfile("logs/login.log");
        ServerLog.setDmpfile("logs/login_dump.log");

        net.doSocket();

        configRead(confFile);
        configRead(interFile);
        configRead("conf/char.conf");

        if (!sql.connect(sqlId, sqlPw, sqlIp, sqlPort, sqlDb)) {
            log.error("id: {} Port: {}", sqlId, sqlPort);
            System.exit(1);
        }

        langRead(langFile);
        core = new Core(net, timers);
        core.setTermFunc(LoginServer::doTerm);

        ServerLog.addLog("");
        ServerLog.addLog("RetroTK Login Server Started.%n");

        net.setDefaultAccept(LoginClif::clifAccept);
        net.setDefaultParse(LoginClif::clifParse);
        loginFd = net.makeListenPort(loginPort);
        timers.insert(LOCKOUT_MS, LOCKOUT_MS, net::removeThrottle, 0, 0);

        log.info("RetroTK Login Server is ready! Listening at {}.", loginPort);
        ServerLog.addLog("Server Ready! Listening at %d.%n", loginPort);

        core.mainLoop();
    }
}
