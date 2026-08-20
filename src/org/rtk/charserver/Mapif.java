package org.rtk.charserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;

import static org.rtk.charserver.CharServer.*;

/**
 * Port of char/mapif.c — the char-server side of the map-server link.
 *
 * The handshake (0x3000), map registration (0x3001), login routing (0x3002),
 * logout (0x3005) and broadcast plumbing are fully ported. The handlers that
 * move the compressed mmo_charstatus blob (0x3003 request / 0x3004 save) and
 * the message-board / mail traffic depend on the map-server gameplay port and
 * currently log a warning; see the project README for the porting roadmap.
 */
public final class Mapif {

    private static final Logger log = LogManager.getLogger(Mapif.class);

    public static final int ALL = 0;
    public static final int ALLWOS = 1;

    // packet lengths for 0x3000 .. 0x3008 (board/mail packets 0x3009+ are
    // part of the not-yet-ported gameplay protocol)
    private static final int[] PACKET_LEN_TABLE = {72, -1, 20, 24, -1, 6, 255, -1, 28};

    private Mapif() {
    }

    /** mapif_send(): broadcast a raw inter-server packet to map servers. */
    public static int send(int fromFd, byte[] buf, int len, int type) {
        for (int i = 0; i < mapFifoMax; i++) {
            if (mapFifo[i].fd > 0 && net.session(mapFifo[i].fd) != null) {
                if (type == ALLWOS && mapFifo[i].fd == fromFd) {
                    continue;
                }
                Session s = net.session(mapFifo[i].fd);
                s.wfifoBytes(0, buf, len);
                s.wfifoSet(len);
            }
        }
        return 0;
    }

    /** mapif_parse_auth(): first packet of an incoming map-server link. */
    public static int parseAuth(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 2) {
            return 0;
        }

        int cmd = s.rfifoW(0);
        if (cmd < 0x3000 || cmd >= 0x3000 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x3000] == 0) {
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x3000];
        if (packetLen == -1) {
            if (s.rfifoRest() < 6) {
                return 2;
            }
            packetLen = s.rfifoL(2);
        }
        if (s.rfifoRest() < packetLen) {
            return 2;
        }

        if (cmd == 0x3000) {
            String id = s.rfifoString(2, 32);
            String pw = s.rfifoString(34, 32);
            // Faithful port note: like intif_auth, the C code only rejects
            // when BOTH credentials mismatch.
            if (!id.equals(charId) && !pw.equals(charPw)) {
                s.wfifoW(0, 0x3800);
                s.wfifoB(2, 0x01);
                s.wfifoB(3, 0x00);
                s.wfifoSet(4);
                s.eof = true;
                s.rfifoSkip(packetLen);
                return 0;
            }

            int i;
            for (i = 0; i < mapFifoMax; i++) {
                if (mapFifo[i].fd == 0) {
                    break;
                }
            }
            if (i >= mapFifoMax) {
                mapFifoMax++;
            }

            mapFifoN++;
            mapFifo[i].fd = fd;
            mapFifo[i].ip = s.rfifoL(66);
            mapFifo[i].port = s.rfifoW(70);
            final int serverIndex = i;
            s.funcParse = f -> mapifParse(f, serverIndex);

            s.wfifoW(0, 0x3800);
            s.wfifoB(2, 0x00);
            s.wfifoB(3, i); // map-server #
            s.wfifoSet(4);

            String ipStr = Session.ipToString(mapFifo[i].ip);
            log.info("Map Server #{} connected.({}:{}).", i, ipStr, mapFifo[i].port);
            ServerLog.addLog("Map Server #%d connected(%s:%d).%n", i, ipStr, mapFifo[i].port);
        } else {
            s.eof = true;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }

    /** mapif_parse_mapset() (0x3001): map server announces its map list. */
    static int parseMapset(int fd, int id) {
        Session s = net.session(fd);
        mapFifo[id].mapN = s.rfifoW(6);
        mapFifo[id].map = new int[mapFifo[id].mapN];
        for (int i = 0; i < mapFifo[id].mapN; i++) {
            mapFifo[id].map[i] = s.rfifoW(i * 2 + 8);
        }

        // warn about duplicate map ids across servers, like the C code
        for (int i = 0; i < mapFifoMax; i++) {
            if (i == id || mapFifo[i].fd <= 0) {
                continue;
            }
            for (int j = 0; j < mapFifo[i].mapN; j++) {
                for (int k = 0; k < mapFifo[id].mapN; k++) {
                    if (mapFifo[i].map[j] == mapFifo[id].map[k]) {
                        log.warn("MAPERR: Same map number(#{}) at server #{} and server #{}",
                                mapFifo[id].map[k], id, i);
                        ServerLog.addLog("MAPERR: Same map number(#%d) at server #%d and server #%d%n",
                                mapFifo[id].map[k], id, i);
                    }
                }
            }
        }

        log.info("Map Server #{}({} map) is ready!", id, mapFifo[id].mapN);
        ServerLog.addLog("Map Server #%d send %d map.%n", id, mapFifo[id].mapN);
        return 0;
    }

    /** mapif_parse_login() (0x3002): map accepted a player — tell login. */
    static int parseLogin(int fd, int id) {
        if (loginFd <= 0 || net.session(loginFd) == null) {
            return 0;
        }
        Session s = net.session(fd);
        Session ls = net.session(loginFd);
        ls.wfifoW(0, 0x2003);
        ls.wfifoW(2, s.rfifoW(2));
        ls.wfifoB(4, 0x00);
        ls.wfifoZero(5, 16);
        ls.wfifoBytes(5, s.rfifoBytes(4, 16));
        ls.wfifoL(21, mapFifo[id].ip);
        ls.wfifoW(25, mapFifo[id].port);
        ls.wfifoSet(27);
        return 0;
    }

    /** mapif_parse_logout() (0x3005). */
    static int parseLogout(int fd) {
        Session s = net.session(fd);
        CharDb.logindataDel(s.rfifoL(2));
        return 0;
    }

    /** mapif_parse(): dispatcher once a map server is authenticated. */
    public static int mapifParse(int fd, int id) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            log.warn("Map Server #{} connection lost.", id);
            ServerLog.addLog("Map Server #%d connection lost.%n", id);
            mapFifo[id].fd = 0;
            mapFifo[id].mapN = 0;
            mapFifoN--;
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 2) {
            return 0;
        }

        int cmd = s.rfifoW(0);
        if (cmd < 0x3000 || cmd >= 0x3000 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x3000] == 0) {
            // Board/mail/charstatus packets from the un-ported part of the
            // protocol: drop what we can measure, otherwise resync is
            // impossible and the link must be closed.
            log.warn("[CHAR] Unhandled inter-server packet {} from map server #{}", String.format("0x%04X", cmd), id);
            s.eof = true;
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x3000];
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
            case 0x3001: parseMapset(fd, id); break;
            case 0x3002: parseLogin(fd, id); break;
            case 0x3005: parseLogout(fd); break;
            case 0x3003: // request char (compressed mmo_charstatus blob)
            case 0x3004: // save char
            default:
                log.debug("[CHAR] Packet {} not ported yet (see README roadmap)", String.format("0x%04X", cmd));
                break;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }
}
