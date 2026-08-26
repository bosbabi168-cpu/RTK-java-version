package org.rtk.map;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.mmo.CharStatus;
import org.rtk.common.mmo.CharStatusCodec;

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
        int clientFd = s.rfifoW(2);
        s.wfifoW(0, 0x3002);
        s.wfifoW(2, clientFd);
        s.wfifoZero(4, 16);
        s.wfifoStringFixed(4, name, 16);
        s.wfifoSet(20);

        // JANGAN minta data karakter di sini. Di C (intif_parse_authadd,
        // intif.c:394) yang dilakukan hanya auth_add() + ack 0x3002; yang
        // memanggil intif_load() adalah clif_accept2() (clif.c:409) SETELAH
        // klien menyambung dan menyebutkan namanya — dengan fd klien yang
        // asli. `clientFd` di paket ini milik ruang fd CHAR SERVER, jadi
        // memakainya di sini salah dua kali: paket masuk dunia dikirim ke
        // sesi yang belum ada, dan User hantu terdaftar di onlineChars[fd]
        // sehingga fd yang sama saat dipakai klien sungguhan membuat paket
        // perkenalan 0x10 tidak pernah sampai ke clientAuth().
        return 0;
    }

    /**
     * intif_load() — minta data karakter ke char server (0x3003).
     *
     * Tata letak: W(0)=0x3003, W(2)=fd klien, L(4)=id karakter, nama[16]@8.
     */
    public static int requestChar(int clientFd, long charId, String name) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa minta karakter: belum terhubung ke char server");
            return -1;
        }
        s.wfifoW(0, 0x3003);
        s.wfifoW(2, clientFd);
        s.wfifoL(4, (int) charId);
        s.wfifoStringFixed(8, name, 16);
        s.wfifoSet(24);
        log.info("[MAP] minta data karakter {} (id={}) ke char server", name, charId);
        return 0;
    }

    /**
     * intif_parse_charload() (0x3803): char server mengirim data karakter.
     * Tata letak: W(0)=0x3803, L(2)=panjang total, W(6)=fd klien, blob@8.
     */
    static int parseCharLoad(int fd) {
        Session s = net.session(fd);
        int total = s.rfifoL(2);
        int clientFd = s.rfifoW(6);
        int blobLen = total - 8;
        if (blobLen <= 0) {
            log.error("[MAP] blob karakter kosong (panjang {})", blobLen);
            return 0;
        }

        CharStatus c;
        try {
            c = CharStatusCodec.decode(s.rfifoBytes(8, blobLen));
        } catch (IOException e) {
            log.error("[MAP] blob karakter rusak / tidak dikenal", e);
            return 0;
        }

        // bangun pemain runtime lalu tempatkan di dunia (pc_setpos + clif_spawn)
        org.rtk.map.User sd = new org.rtk.map.User(clientFd, c);
        MapServer.onlineChars.put(clientFd, sd);

        log.info("[MAP] karakter dimuat: {} (level {}, {} barang) untuk fd {}",
                c.name, c.level, c.inventory.size(), clientFd);

        if (!org.rtk.map.Pc.enterWorld(MapServer.world, sd)) {
            log.error("[MAP] {} gagal ditempatkan di dunia — koneksi ditutup", c.name);
            MapServer.onlineChars.remove(clientFd);
            Session cs = net.session(clientFd);
            if (cs != null) {
                cs.eof = true;
            }
        }
        return 0;
    }

    /**
     * intif_save() / intif_savequit() — kirim karakter untuk disimpan.
     * Tata letak: W(0)=opcode, L(2)=panjang total, blob@6.
     *
     * @param quit true memakai 0x3007 (simpan + logout), false 0x3004
     */
    public static int saveChar(CharStatus c, boolean quit) {
        Session s = net.session(charFd);
        if (s == null) {
            log.error("[MAP] tidak bisa menyimpan {}: belum terhubung ke char server", c.name);
            return -1;
        }
        byte[] blob;
        try {
            blob = CharStatusCodec.encode(c);
        } catch (IOException e) {
            log.error("[MAP] gagal menyusun blob karakter {}", c.name, e);
            return -1;
        }
        s.wfifoW(0, quit ? 0x3007 : 0x3004);
        s.wfifoL(2, 6 + blob.length);
        s.wfifoBytes(6, blob);
        s.wfifoSet(6 + blob.length);
        log.info("[MAP] simpan karakter {} ({} byte){}", c.name, blob.length, quit ? " + logout" : "");
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
            case 0x3803: parseCharLoad(fd); break;
            case 0x3804: parseCheckOnline(fd); break;
            default:
                log.debug("[MAP] Packet {} not ported yet (see README roadmap)", String.format("0x%04X", cmd));
                break;
        }

        s.rfifoSkip(packetLen);
        return 0;
    }
}
