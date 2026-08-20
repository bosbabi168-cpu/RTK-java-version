package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;

import static org.rtk.map.MapServer.*;

/**
 * Port of the inter-server part of map/intif.c (char-server link).
 *
 * Only the handshake and login-routing packets are implemented; everything
 * that requires the gameplay port (charstatus blobs 0x3803, boards, mail)
 * is logged and skipped. See the project README for the roadmap.
 */
public final class MapIntif {

    private static final Logger log = LogManager.getLogger(MapIntif.class);

    // packet lengths for 0x3800 .. 0x380E (subset; unknown = 255 like the C table)
    private static final int[] PACKET_LEN_TABLE = {
        4, -1, 38, -1, 6, -1, 255, -1, 5, -1, -1, 6, 6, 8, 6
    };

    private MapIntif() {
    }

    /** intif_parse_accept() (0x3800): char server accepted us. */
    static int parseAccept(int fd) {
        Session s = net.session(fd);
        if (s.rfifoB(2) != 0) {
            log.error("CFG_ERR: Username or password to connect Char Server is Invalid!");
            ServerLog.addLog("CFG_ERR: Username or password to connect Char Server is Invalid!%n");
            return 0;
        }

        log.info("Server ID: {}", s.rfifoB(3));
        ServerLog.addLog("Connected to Char Server.%n");
        log.info("Connected to Char Server.");

        // register our map list (0x3001)
        int mapN = loadedMaps.size();
        s.wfifoW(0, 0x3001);
        s.wfifoL(2, mapN * 2 + 8);
        s.wfifoW(6, mapN);
        for (int i = 0; i < mapN; i++) {
            s.wfifoW(i * 2 + 8, loadedMaps.get(i));
        }
        s.wfifoSet(mapN * 2 + 8);
        return 0;
    }

    /** intif_parse_authadd() (0x3802): a player was routed to this server. */
    static int parseAuthAdd(int fd) {
        Session s = net.session(fd);
        String name = s.rfifoString(8, 16);
        int charIdNum = s.rfifoL(4);
        int ip = s.rfifoL(34);
        log.info("[MAP] Incoming player: {} (id {}) from {}", name, charIdNum, Session.ipToString(ip));

        // acknowledge so the char server tells login to redirect the client
        s.wfifoW(0, 0x3002);
        s.wfifoW(2, s.rfifoW(2));
        s.wfifoZero(4, 16);
        s.wfifoStringFixed(4, name, 16);
        s.wfifoSet(20);
        return 0;
    }

    /** intif_parse_checkonline() (0x3804): kick a double-logged character. */
    static int parseCheckOnline(int fd) {
        Session s = net.session(fd);
        log.info("[MAP] Force-disconnect request for char id {} (no players hosted yet in the skeleton).",
                s.rfifoL(2));
        return 0;
    }

    /** intif_parse(): dispatcher for packets from the char server. */
    public static int intifParse(int fd) {
        Session s = net.session(fd);
        if (s == null || fd != charFd) {
            return 0;
        }
        if (s.eof) {
            log.warn("Char Server connection lost.");
            ServerLog.addLog("Char Server connection lost.%n");
            charFd = 0;
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 2) {
            return 0;
        }

        int cmd = s.rfifoW(0);
        if (cmd < 0x3800 || cmd >= 0x3800 + PACKET_LEN_TABLE.length
                || PACKET_LEN_TABLE[cmd - 0x3800] == 0) {
            return 0;
        }

        int packetLen = PACKET_LEN_TABLE[cmd - 0x3800];
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
            case 0x3800: parseAccept(fd); break;
            case 0x3802: parseAuthAdd(fd); break;
            case 0x3804: parseCheckOnline(fd); break;
            default:
                log.debug("[MAP] Packet {} not ported yet (see README roadmap)", String.format("0x%04X", cmd));
                break;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }
}
