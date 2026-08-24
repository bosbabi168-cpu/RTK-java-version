package org.rtk.login;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Crypt;
import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;

import static org.rtk.login.LoginServer.*;

/**
 * Port of login/clif.c — client interface of the login server.
 */
public final class LoginClif {

    private static final Logger log = LogManager.getLogger(LoginClif.class);
    private static final Random rand = new Random();
    private static final String patchUrl =
            org.rtk.common.Props.get("login.patch_url", "http://files.kru.com/NexusTK/patch/");

    private LoginClif() {
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    static boolean bannedIpCheck(String ip) {
        int rows = sql.rowCount("SELECT `BndId` FROM `BannedIP` WHERE `BndIP` = ?", ip);
        return rows > 0;
    }

    private static void packetLog(String line) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("packetlog.txt", true))) {
            pw.println(line);
        } catch (IOException e) {
            log.warn("Cannot open packet log");
        }
    }

    /** clif_accept(): greets a fresh connection. */
    public static int clifAccept(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        String ip = s.clientIpString();
        boolean banned = bannedIpCheck(ip);

        log.info("[LOGIN] Client connected from: {}", ip);
        packetLog("Ip address connected: " + ip);

        if (banned) {
            ServerLog.logAdd("BANNED", "<%02d:%02d> Banned IP detected from %s - ip throttled.%n",
                    ServerLog.getHour(), ServerLog.getMinute(), ip);
            log.warn("(BANNED)- Closing all connections from {}", ip);
            int myIp = s.clientIp();
            for (int p = 1; p < net.fdMax(); p++) {
                if (net.session(p) == null || p == fd) {
                    continue;
                }
                if (net.session(p).clientIp() == myIp) {
                    net.session(p).eof = true;
                    net.sessionEof(p);
                }
            }
            s.eof = true;
            net.sessionEof(fd);
        } else {
            byte[] hello = {
                (byte) 0xAA, 0x00, 0x13, 0x7E, 0x1B,
                'C', 'O', 'N', 'N', 'E', 'C', 'T', 'E', 'D', ' ',
                'S', 'E', 'R', 'V', 'E', 'R', '\n'
            };
            s.wfifoBytes(0, hello);
            s.wfifoSet(22);
        }
        return 0;
    }

    /** clif_message(): sends a status/text popup to the client. */
    public static int clifMessage(int fd, int code, String buff) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (buff == null) {
            buff = "";
        }
        int packetLen = buff.length() + 6;
        s.wfifoB(0, 0xAA);
        s.wfifoWBE(1, packetLen);
        s.wfifoB(3, 0x02);
        s.wfifoB(4, 0x02);
        s.wfifoB(5, code);
        s.wfifoB(6, buff.length());
        s.wfifoString(7, buff);
        s.wfifoW(packetLen + 8, 0);
        Crypt.setPacketIndexes(s.wbuf(), s.woff());
        Crypt.crypt(s.wbuf(), s.woff());
        s.wfifoSet(packetLen + 6);
        return 0;
    }

    /** clif_sendurl() */
    public static int clifSendUrl(int fd, int type, String url) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x66);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, type); // 0 = in-game browser, 1 = popup + close client, 2 = popup
        s.wfifoWBE(6, url.length());
        s.wfifoStringRaw(8, url);
        s.wfifoWBE(1, url.length() + 8);
        Crypt.setPacketIndexes(s.wbuf(), s.woff());
        Crypt.crypt(s.wbuf(), s.woff());
        s.wfifoSet(url.length() + 8);
        return 0;
    }

    /** reg_check(): the character must be attached to an account. */
    static int regCheck(String name) {
        Integer id = sql.queryInt("SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", name);
        if (id == null || id == 0) {
            return 0;
        }
        Integer accountId = sql.queryInt(
                "SELECT `AccountId` FROM `Accounts` WHERE `AccountCharId1` = ? OR `AccountCharId2` = ? "
                + "OR `AccountCharId3` = ? OR `AccountCharId4` = ? OR `AccountCharId5` = ? OR `AccountCharId6` = ?",
                id, id, id, id, id, id);
        return accountId == null ? 0 : accountId;
    }

    static int maintenanceMode() {
        Integer flag = sql.queryInt("SELECT `MaintenanceMode` FROM `Maintenance`");
        return flag == null ? 0 : flag;
    }

    static int maintenanceOverride(String name) {
        Integer gm = sql.queryInt("SELECT `ChaGMLevel` FROM `Character` WHERE `ChaName` = ?", name);
        return gm == null ? 0 : gm;
    }

    /** clif_debug(): hex dump of a packet. */
    public static void clifDebug(byte[] data, int off, int len) {
        StringBuilder hex = new StringBuilder();
        StringBuilder chr = new StringBuilder();
        for (int i = 0; i < len && off + i < data.length; i++) {
            int b = data[off + i] & 0xFF;
            hex.append(String.format("%02X ", b));
            chr.append(b <= 32 || b > 126 ? "   " : String.format("%2c ", (char) b));
        }
        log.debug("{}", hex);
        log.debug("{}", chr);
    }

    // ------------------------------------------------------------------
    // main packet parser
    // ------------------------------------------------------------------

    /** clif_parse() */
    public static int clifParse(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            net.sessionEof(fd);
            return 0;
        }

        if (!(s.sessionData instanceof LoginSessionData)) {
            s.sessionData = new LoginSessionData();
        }
        LoginSessionData sd = (LoginSessionData) s.sessionData;

        if (s.rfifoRest() > 0 && s.rfifoB(0) != 0xAA) {
            int headErr = 0;
            while (s.rfifoRest() > 0 && s.rfifoB(0) != 0xAA) {
                s.rfifoSkip(1);
                if (headErr++ > 30) {
                    s.eof = true;
                    return 0;
                }
            }
        }

        if (s.rfifoRest() < 3) {
            return 0;
        }

        int len = s.rfifoWBE(1) + 3;
        String ip = s.clientIpString();

        StringBuilder out = new StringBuilder("Packet (IP" + ip + " L" + len + "): ");
        for (int l = 0; l < Math.min(len, s.rfifoRest()); l++) {
            out.append(String.format("%X ", s.rfifoB(l)));
        }
        packetLog(out.toString());

        if (s.rfifoRest() < len) {
            return 0;
        }

        Crypt.crypt(s.rbuf(), s.roff());

        switch (s.rfifoB(3)) {
            case 0x00: {
                Crypt.crypt(s.rbuf(), s.roff()); // reverse the decryption: this packet is sent in clear
                int ver = s.rfifoWBE(4);
                if (ver == nexVersion) {
                    String key = Crypt.handshakeKey(); // "KruIn7inc"
                    s.wfifoB(0, 0xAA);
                    s.wfifoWBE(1, 8 + key.length()); // 0x11 with the 9-char key
                    s.wfifoB(3, 0x00);
                    s.wfifoB(4, 0x00);
                    s.wfifoB(5, 0x27);
                    s.wfifoB(6, 0x4F);
                    s.wfifoB(7, 0x8A);
                    s.wfifoB(8, 0x4A);
                    s.wfifoB(9, 0x00);
                    s.wfifoB(10, key.length());
                    s.wfifoString(11, key);
                    s.wfifoSet(11 + key.length());
                } else {
                    log.info("patching — versi klien {} != versi server {}", ver, nexVersion);
                    String url = patchUrl;
                    s.wfifoB(0, 0xAA);
                    s.wfifoWBE(1, 6 + url.length()); // 0x29 with the original URL
                    s.wfifoB(3, 0);
                    s.wfifoB(4, 2);
                    s.wfifoWBE(5, nexVersion);
                    s.wfifoB(7, 1);
                    s.wfifoB(8, url.length());
                    s.wfifoString(9, url);
                    Crypt.setPacketIndexes(s.wbuf(), s.woff());
                    s.wfifoSet(9 + url.length() + 3);
                }
                break;
            }
            case 0x02: { // check name availability (first step of character creation)
                sd.name = "";
                sd.pass = "";

                int nameLen = s.rfifoB(5);
                if (nameLen > 12 || nameLen < 3
                        || stringCheckAllchars(s.rfifoString(6, nameLen))) {
                    clifMessage(fd, 0x03, loginMsg[LGN_ERRUSER]);
                    break;
                }
                int passLen = s.rfifoB(6 + nameLen);
                if (passLen > 8 || passLen < 3
                        || stringCheck(s.rfifoString(7 + nameLen, passLen))) {
                    clifMessage(fd, 0x05, loginMsg[LGN_ERRPASS]);
                    break;
                }

                sd.name = s.rfifoString(6, nameLen);
                sd.pass = s.rfifoString(7 + nameLen, passLen);

                if (sd.pass.length() > 8 || sd.pass.length() < 3) {
                    clifMessage(fd, 0x05, loginMsg[LGN_ERRPASS]);
                    break;
                }
                if (charFd > 0 && net.session(charFd) != null) {
                    Session cs = net.session(charFd);
                    cs.wfifoW(0, 0x1001);
                    cs.wfifoW(2, fd);
                    cs.wfifoStringFixed(4, sd.name, 16);
                    cs.wfifoSet(20);
                } else {
                    clifMessage(fd, 0x03, loginMsg[LGN_ERRDB]);
                }
                break;
            }
            case 0x03: { // login
                int nameLen = s.rfifoB(5);
                String name = s.rfifoString(6, nameLen);
                if (stringCheck(name)) {
                    clifMessage(fd, 0x03, loginMsg[LGN_ERRUSER]);
                    break;
                }
                int passLen = s.rfifoB(6 + nameLen);
                String pass = s.rfifoString(7 + nameLen, passLen);
                if (stringCheck(pass)) {
                    clifMessage(fd, 0x05, loginMsg[LGN_ERRPASS]);
                    break;
                }

                if (maintenanceMode() != 0 && maintenanceOverride(name) == 0) {
                    clifMessage(fd, 0x03, "Server is undergoing maintenance. Please visit www.RetroTK.com "
                            + "or the RetroTK facebook group for more details.");
                    break;
                }

                if (requireReg != 0 && regCheck(name) == 0) {
                    ServerLog.logAdd("regreject",
                            "<%02d:%02d> Character %s attempted to login from IP %s - unregistered character/login rejected.%n",
                            ServerLog.getHour(), ServerLog.getMinute(), name, ip);
                    log.warn("Character {} attempted to login from IP {} - unregistered character/login rejected.",
                            name, ip);
                    clifMessage(fd, 0x03, "You must attach your character to an account to play.\n\n"
                            + "Please visit www.RetroTK.com to attach your character to an account. If you have issues, "
                            + "please visit https://www.RetroTK.com/helpdesk or visit our Discord (link on website).");
                    break;
                }

                sd.name = name;
                sd.pass = pass;

                if (charFd > 0 && net.session(charFd) != null) {
                    Session cs = net.session(charFd);
                    cs.wfifoW(0, 0x1003);
                    cs.wfifoW(2, fd);
                    cs.wfifoZero(4, 32);
                    cs.wfifoStringFixed(4, name, 16);
                    cs.wfifoStringFixed(20, pass, 16);
                    cs.wfifoL(36, s.clientIp());
                    cs.wfifoSet(40);
                } else {
                    clifMessage(fd, 0x03, loginMsg[LGN_ERRDB]);
                }
                break;
            }
            case 0x04: { // finalize character creation
                if (sd.name.isEmpty() || sd.pass.isEmpty()) {
                    s.eof = true;
                    break;
                }
                sd.face = s.rfifoB(6);
                sd.sex = s.rfifoB(10);
                sd.country = rand.nextInt(2);
                sd.totem = s.rfifoB(12);
                sd.hair = s.rfifoB(7);
                sd.faceColor = s.rfifoB(8);
                sd.hairColor = s.rfifoB(9);

                if (charFd > 0 && net.session(charFd) != null) {
                    Session cs = net.session(charFd);
                    cs.wfifoW(0, 0x1002);
                    cs.wfifoW(2, fd);
                    cs.wfifoStringFixed(4, sd.name, 16);
                    cs.wfifoStringFixed(20, sd.pass, 16);
                    cs.wfifoB(36, sd.face);
                    cs.wfifoB(37, sd.sex);
                    cs.wfifoB(38, sd.country);
                    cs.wfifoB(39, sd.totem);
                    cs.wfifoB(40, sd.hair);
                    cs.wfifoB(41, sd.hairColor);
                    cs.wfifoB(42, sd.faceColor);
                    cs.wfifoSet(43);
                } else {
                    clifMessage(fd, 0x03, loginMsg[LGN_ERRDB]);
                }
                break;
            }
            case 0x10: {
                s.wfifoB(0, 0xAA);
                s.wfifoWBE(1, 0x07);
                s.wfifoB(3, 0x60);
                s.wfifoB(4, 0x00);
                s.wfifoB(5, 0x55);
                s.wfifoB(6, 0xE0);
                s.wfifoB(7, 0xD8);
                s.wfifoB(8, 0xA2);
                s.wfifoB(9, 0xA0);
                Crypt.setPacketIndexes(s.wbuf(), s.woff());
                s.wfifoSet(13);
                break;
            }
            case 0x26: { // change password
                int nameLen = s.rfifoB(5);
                if (stringCheck(s.rfifoString(6, nameLen))) {
                    clifMessage(fd, 0x03, loginMsg[LGN_ERRUSER]);
                    break;
                }
                int oldLen = s.rfifoB(6 + nameLen);
                int newLen = s.rfifoB(7 + nameLen + oldLen);
                if (newLen > 8 || newLen < 3) {
                    clifMessage(fd, 0x05, loginMsg[LGN_ERRPASS]);
                    break;
                }
                if (nameLen > 16 || oldLen > 16) {
                    return 0;
                }
                if (stringCheck(s.rfifoString(7 + nameLen, oldLen))
                        || stringCheck(s.rfifoString(8 + nameLen + oldLen, newLen))) {
                    clifMessage(fd, 0x05, loginMsg[LGN_ERRPASS]);
                    break;
                }
                if (charFd > 0 && net.session(charFd) != null) {
                    Session cs = net.session(charFd);
                    cs.wfifoW(0, 0x1004);
                    cs.wfifoW(2, fd);
                    cs.wfifoZero(4, 48);
                    cs.wfifoStringFixed(4, s.rfifoString(6, nameLen), 16);
                    cs.wfifoStringFixed(20, s.rfifoString(7 + nameLen, oldLen), 16);
                    cs.wfifoStringFixed(36, s.rfifoString(8 + nameLen + oldLen, newLen), 16);
                    cs.wfifoSet(52);
                } else {
                    clifMessage(fd, 0x03, loginMsg[LGN_ERRDB]);
                }
                break;
            }
            case 0x57: // multi-server (unused)
            case 0x62: // ??
            case 0x71: // ping
                break;
            case 0x7B: { // meta file requests
                switch (s.rfifoB(5)) {
                    case 0:
                        sendMeta(fd);
                        break;
                    case 1:
                        sendMetalist(fd);
                        break;
                    default:
                        break;
                }
                break;
            }
            case 0xFF: {
                LoginIntif.intifAuth(fd);
                break;
            }
            default:
                log.warn("[LOGIN] Unknown Packet ID: {} Packet from {}:", String.format("%02X", s.rfifoB(3)), ip);
                clifDebug(s.rbuf(), s.roff(), s.rfifoWBE(1));
                break;
        }

        if (net.session(fd) != null && s.rfifoRest() >= len) {
            s.rfifoSkip(len);
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // meta file delivery (zlib-compressed data files)
    // ------------------------------------------------------------------

    /** metacrc(): CRC32 checksum of a file, 0 when unreadable. */
    static int metacrc(String file) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(file));
            CRC32 crc = new CRC32();
            crc.update(data);
            return (int) crc.getValue();
        } catch (IOException e) {
            return 0;
        }
    }

    private static byte[] zlibCompress(byte[] data) {
        Deflater d = new Deflater(); // default level, zlib wrapper: same as compress()
        d.setInput(data);
        d.finish();
        byte[] buf = new byte[data.length + 64];
        int total = 0;
        while (!d.finished()) {
            int n = d.deflate(buf, total, buf.length - total);
            total += n;
            if (total == buf.length && !d.finished()) {
                byte[] grown = new byte[buf.length * 2];
                System.arraycopy(buf, 0, grown, 0, total);
                buf = grown;
            }
        }
        d.end();
        byte[] out = new byte[total];
        System.arraycopy(buf, 0, out, 0, total);
        return out;
    }

    /** send_metafile() */
    static int sendMetafile(int fd, String file) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        Path path = Paths.get("meta", file);
        byte[] udata;
        try {
            udata = Files.readAllBytes(path);
        } catch (IOException e) {
            return 0;
        }
        int checksum = metacrc(path.toString());
        byte[] cdata = zlibCompress(udata);
        int clen = cdata.length;

        int len = 0;
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x6F);
        s.wfifoB(5, 0); // this is file data
        s.wfifoB(6, file.length());
        s.wfifoString(7, file);
        len += file.length() + 1;
        s.wfifoLBE(len + 6, checksum);
        len += 4;
        s.wfifoWBE(len + 6, clen);
        len += 2;
        s.wfifoBytes(len + 6, cdata);
        len += clen;
        s.wfifoB(len + 6, 0);
        len += 1;
        s.wfifoWBE(1, len + 3);
        Crypt.setPacketIndexes(s.wbuf(), s.woff());
        Crypt.crypt(s.wbuf(), s.woff());
        s.wfifoSet(len + 6 + 3);
        return 0;
    }

    /** send_meta(): client asked for one meta file by name. */
    static int sendMeta(int fd) {
        Session s = net.session(fd);
        String name = s.rfifoString(7, s.rfifoB(6));
        return sendMetafile(fd, name);
    }

    /** send_metalist(): list of meta files with checksums. */
    static int sendMetalist(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        int len = 0;
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x6F);
        s.wfifoB(5, 1);
        s.wfifoWBE(6, metaFiles.size());
        len += 2;
        for (String file : metaFiles) {
            s.wfifoB(len + 6, file.length());
            s.wfifoStringRaw(len + 7, file);
            len += file.length() + 1;
            int checksum = metacrc("meta/" + file);
            s.wfifoLBE(len + 6, checksum);
            len += 4;
        }
        s.wfifoWBE(1, len + 4);
        Crypt.setPacketIndexes(s.wbuf(), s.woff());
        Crypt.crypt(s.wbuf(), s.woff());
        s.wfifoSet(len + 7 + 3);
        return 0;
    }
}
