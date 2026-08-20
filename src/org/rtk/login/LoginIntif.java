package org.rtk.login;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Crypt;
import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;

import static org.rtk.login.LoginServer.*;

/**
 * Port of login/intif.c — the login&lt;-&gt;char inter-server link.
 */
public final class LoginIntif {

    private static final Logger log = LogManager.getLogger(LoginIntif.class);

    // packet lengths for 0x2000 .. 0x2005
    private static final int[] PACKET_LEN_TABLE = {69, 5, 5, 27, 5, 0};

    // wrong-password attempts before the IP is locked out
    private static final int LOCKOUT_ATTEMPTS =
            org.rtk.common.Props.getInt("login.lockout_attempts", 10);

    private LoginIntif() {
    }

    /** intif_auth(): a char server introduces itself (opcode 0xFF). */
    public static int intifAuth(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (charFd > 0) {
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 69) {
            return 0;
        }

        String id = s.rfifoString(5, 32);
        String pw = s.rfifoString(37, 32);

        // Faithful port note: the C code only rejects when BOTH credentials
        // mismatch (strcmp(a) && strcmp(b)).
        if (!id.equals(loginId) && !pw.equals(loginPw)) {
            s.wfifoW(0, 0x1000);
            s.wfifoB(2, 0x01);
            s.wfifoSet(3);
            s.eof = true;
            return 0;
        }

        charFd = fd;
        s.funcParse = LoginIntif::intifParse;
        s.wfifoW(0, 0x1000);
        s.wfifoB(2, 0x00);
        s.wfifoSet(3);

        ServerLog.addLog("Connection from Char Server accepted.%n");
        log.info("Connection from Char Server accepted.");
        return 0;
    }

    /** 0x2001: name-check result during character creation. */
    static int parse2001(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        if (clientFd <= 0 || clientFd >= NetServer.FD_SETSIZE || net.session(clientFd) == null) {
            return 0;
        }
        int code = s.rfifoB(4);
        if (code == 0x01) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_USEREXIST]);
        } else if (code != 0) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_ERRDB]);
        } else {
            LoginClif.clifMessage(clientFd, 0x00, "");
        }
        return 0;
    }

    /** 0x2002: character creation result. */
    static int parse2002(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        if (clientFd <= 0 || clientFd >= NetServer.FD_SETSIZE || net.session(clientFd) == null) {
            return 0;
        }
        int code = s.rfifoB(4);
        if (code == 0x01) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_USEREXIST]);
        } else if (code != 0) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_ERRDB]);
        } else {
            LoginClif.clifMessage(clientFd, 0x00, loginMsg[LGN_NEWCHAR]);
        }
        return 0;
    }

    /** 0x2003: login confirmation — redirect the client to its map server. */
    static int parseConnectConfirm(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        if (clientFd <= 0 || clientFd >= NetServer.FD_SETSIZE || net.session(clientFd) == null) {
            return 0;
        }
        Session client = net.session(clientFd);
        LoginSessionData sd = client.sessionData instanceof LoginSessionData
                ? (LoginSessionData) client.sessionData : new LoginSessionData();
        int code = s.rfifoB(4);

        if (code == 0) {
            ServerLog.logAdd("validlogin", "<%02d:%02d> User: %s IP: %s   Password used: %s%n",
                    ServerLog.getHour(), ServerLog.getMinute(), sd.name,
                    client.clientIpString(), sd.pass);

            // track the player's IP while online (added 06-29-2017 in the C source)
            sql.update("UPDATE `Character` SET `ChaLastIP` = ? WHERE `ChaName` = ?",
                    client.clientIpString(), sd.name);

            // small "login ok" packet
            client.wfifoB(0, 0xAA);
            client.wfifoB(1, 0x00);
            client.wfifoB(2, 0x05);
            client.wfifoB(3, 0x02);
            client.wfifoB(4, 0x17);
            client.wfifoB(5, 0x00);
            client.wfifoB(6, 0x00);
            client.wfifoB(7, 0x00);
            Crypt.setPacketIndexes(client.wbuf(), client.woff());
            Crypt.crypt(client.wbuf(), client.woff());
            client.wfifoSet(8 + 3);

            // redirect packet: map server address + session key name
            String key = Crypt.handshakeKey(); // "KruIn7inc"
            String thing = s.rfifoString(5, 16);
            int len = thing.length() + key.length() + 7; // == thing + 16 with the 9-char key

            client.wfifoB(0, 0xAA);
            client.wfifoB(3, 0x03);
            client.wfifoLBE(4, s.rfifoL(21)); // map server IP (s_addr), byte-swapped like SWAP32
            client.wfifoWBE(8, s.rfifoW(25)); // map server port
            client.wfifoB(10, len);
            client.wfifoWBE(11, key.length());
            client.wfifoString(13, key);
            client.wfifoB(13 + key.length(), thing.length());
            client.wfifoString(14 + key.length(), thing);
            client.wfifoLBE(14 + key.length() + thing.length(), clientFd);
            client.wfifoWBE(1, len + 8);
            Crypt.setPacketIndexes(client.wbuf(), client.woff());
            client.wfifoSet(11 + len + 3);
        } else if (code == 0x01) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_ERRDB]);
        } else if (code == 0x02) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_WRONGUSER]);
        } else if (code == 0x03) {
            ServerLog.logAdd("invalidlogin", "<%02d:%02d> Login:%s IP:%s Password used:%s%n",
                    ServerLog.getHour(), ServerLog.getMinute(), sd.name,
                    client.clientIpString(), sd.pass);
            if (setInvalidCount(client.clientIp()) >= LOCKOUT_ATTEMPTS) {
                net.addIpLockout(client.clientIp());
                client.eof = true;
            }
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_WRONGPASS]);
        } else if (code == 0x04) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_BANNED]);
        } else if (code == 0x05) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_ERRSERVER]);
        } else if (code == 0x06) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_DBLLOGIN]);
        } else {
            log.error("Serious error!");
        }
        return 0;
    }

    /** 0x2004: password change result. */
    static int parseChangePass(int fd) {
        Session s = net.session(fd);
        int clientFd = s.rfifoW(2);
        if (clientFd <= 0 || clientFd >= NetServer.FD_SETSIZE || net.session(clientFd) == null) {
            return 0;
        }
        Session client = net.session(clientFd);
        int code = s.rfifoB(4);
        if (code == 0) {
            ServerLog.logAdd("validchange", "<%d:%d> IP: %s%n",
                    ServerLog.getHour(), ServerLog.getMinute(), client.clientIpString());
            LoginClif.clifMessage(clientFd, 0x00, loginMsg[LGN_CHGPASS]);
        } else if (code == 0x01) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_ERRDB]);
        } else if (code == 0x02) {
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_WRONGUSER]);
        } else if (code == 0x03) {
            ServerLog.logAdd("invalidchange", "<%02d:%02d> IP:%s%n",
                    ServerLog.getHour(), ServerLog.getMinute(), client.clientIpString());
            if (setInvalidCount(client.clientIp()) >= LOCKOUT_ATTEMPTS) {
                net.addIpLockout(client.clientIp());
                client.eof = true;
            }
            LoginClif.clifMessage(clientFd, 0x03, loginMsg[LGN_WRONGPASS]);
        }
        return 0;
    }

    /** intif_parse(): dispatcher for packets from the char server. */
    public static int intifParse(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            ServerLog.addLog("Char Server connection lost.%n");
            log.warn("Char Server connection lost.");
            charFd = 0;
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
        if (cmd < 0x2000 || cmd >= 0x2000 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x2000] == 0) {
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x2000];
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
            case 0x2001: parse2001(fd); break;
            case 0x2002: parse2002(fd); break;
            case 0x2003: parseConnectConfirm(fd); break;
            case 0x2004: parseChangePass(fd); break;
            default: break;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }
}
