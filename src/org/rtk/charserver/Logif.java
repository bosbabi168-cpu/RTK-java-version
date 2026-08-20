package org.rtk.charserver;

import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Crypt;
import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;

import static org.rtk.charserver.CharServer.*;

/**
 * Port of char/logif.c — the char-server side of the login link.
 */
public final class Logif {

    private static final Logger log = LogManager.getLogger(Logif.class);

    // packet lengths for 0x1000 .. 0x1006
    private static final int[] PACKET_LEN_TABLE = {3, 20, 43, 40, 52, 0, 0};

    private static final Random rand = new Random();

    private Logif() {
    }

    /** check_connect_login(): (re)establish the connection to the login server. */
    public static int checkConnectLogin() {
        if (loginFd <= 0 || net.session(loginFd) == null) {
            log.info("Attempt to connect to login-server...");
            loginFd = net.makeConnection(loginIpS, loginPort);
            if (loginFd <= 0) {
                loginFd = 0;
                return 0;
            }
            Session s = net.session(loginFd);
            s.funcParse = Logif::logifParse;

            s.wfifoB(0, 0xAA);
            s.wfifoWBE(1, 66);
            s.wfifoB(3, 0xFF);
            s.wfifoB(4, rand.nextInt(256)); // RAND_INC
            s.wfifoStringFixed(5, loginId, 32);
            s.wfifoStringFixed(37, loginPw, 32);
            Crypt.setPacketIndexes(s.wbuf(), s.woff());
            Crypt.crypt(s.wbuf(), s.woff());
            s.wfifoSet(69 + 3);
        }
        return 0;
    }

    /** 0x1000: auth result from the login server. */
    static int parseAccept(int fd) {
        if (net.session(fd).rfifoB(2) != 0) {
            log.error("CFG_ERR: Username or password to connect Login Server is invalid!");
            ServerLog.addLog("CFG_ERR: Username or password to connect Login Server is invalid!%n");
        } else {
            log.info("Connected to Login Server.");
            ServerLog.addLog("Connected to Login Server.%n");
        }
        return 0;
    }

    /** 0x1001: is this character name taken? */
    static int parseUsedName(int fd) {
        Session s = net.session(fd);
        String name = s.rfifoString(4, 16);
        int res = CharDb.isNameUsed(name);
        s.wfifoW(0, 0x2001);
        s.wfifoW(2, s.rfifoW(2));
        s.wfifoB(4, res);
        s.wfifoSet(5);
        return 0;
    }

    /** 0x1002: create a new character. */
    static int parseNewChar(int fd) {
        Session s = net.session(fd);
        int res = CharDb.newChar(
                s.rfifoString(4, 16),   // name
                s.rfifoString(20, 16),  // pass
                s.rfifoB(39),           // totem
                s.rfifoB(37) % 2,       // sex
                s.rfifoB(38),           // country
                s.rfifoB(36),           // face
                s.rfifoB(40),           // hair
                s.rfifoB(42),           // face_color
                s.rfifoB(41));          // hair_color
        s.wfifoW(0, 0x2002);
        s.wfifoW(2, s.rfifoW(2));
        s.wfifoB(4, res);
        s.wfifoSet(5);
        return 0;
    }

    /** 0x1003: login attempt — find the map server for the character. */
    static int parseLogin(int fd) {
        Session s = net.session(fd);
        String name = s.rfifoString(4, 16);
        String pass = s.rfifoString(20, 16);
        int[] id = {0};

        int res = CharDb.mapFifoFromLogin(name, pass, id);

        if (res < 0) {
            s.wfifoW(0, 0x2003);
            s.wfifoW(2, s.rfifoW(2));
            s.wfifoB(4, Math.abs(res));
            s.wfifoSet(27);
            return 0;
        }
        if (CharDb.logindataAdd(id[0], res, name) != 0) {
            // character is already online, force disconnect
            s.wfifoW(0, 0x2003);
            s.wfifoW(2, s.rfifoW(2));
            s.wfifoB(4, 0x06);
            s.wfifoSet(27);

            if (mapFifo[res].fd > 0 && net.session(mapFifo[res].fd) != null) {
                Session ms = net.session(mapFifo[res].fd);
                ms.wfifoW(0, 0x3804);
                ms.wfifoL(2, id[0]);
                ms.wfifoSet(6);
            }
            return 0;
        }

        if (mapFifo[res].fd > 0 && net.session(mapFifo[res].fd) != null) {
            Session ms = net.session(mapFifo[res].fd);
            ms.wfifoW(0, 0x3802);
            ms.wfifoW(2, s.rfifoW(2));
            ms.wfifoL(4, id[0]);
            ms.wfifoZero(8, 16);
            ms.wfifoStringFixed(8, name, 16);
            ms.wfifoL(34, s.rfifoL(36)); // client IP
            ms.wfifoSet(38);
        }
        return 0;
    }

    /** 0x1004: password change. */
    static int parseSetPass(int fd) {
        Session s = net.session(fd);
        int res = CharDb.setPass(
                s.rfifoString(4, 16),
                s.rfifoString(20, 16),
                s.rfifoString(36, 16));
        s.wfifoW(0, 0x2004);
        s.wfifoW(2, s.rfifoW(2));
        s.wfifoB(4, Math.abs(res));
        s.wfifoSet(5);
        return 0;
    }

    /** logif_parse(): dispatcher for packets from the login server. */
    public static int logifParse(int fd) {
        Session s = net.session(fd);
        if (s == null || fd != loginFd) {
            return 0;
        }

        if (s.eof) {
            log.warn("Can't connect to Login Server.");
            ServerLog.addLog("Can't connect to Login Server.%n");
            loginFd = 0;
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 2) {
            return 0;
        }

        if (s.rfifoB(0) == 0xAA) {
            int len = s.rfifoWBE(1) + 3;
            if (len <= s.rfifoRest()) {
                s.rfifoSkip(len);
            }
            return 0;
        }

        int cmd = s.rfifoW(0);
        if (cmd < 0x1000 || cmd >= 0x1000 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x1000] == 0) {
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x1000];
        if (packetLen == -1) {
            if (s.rfifoRest() < 6) {
                return 2;
            }
            packetLen = s.rfifoL(2);
        }
        if (s.rfifoRest() < packetLen) {
            return 2;
        }

        switch (cmd) {
            case 0x1000: parseAccept(fd); break;
            case 0x1001: parseUsedName(fd); break;
            case 0x1002: parseNewChar(fd); break;
            case 0x1003: parseLogin(fd); break;
            case 0x1004: parseSetPass(fd); break;
            default: break;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }
}
