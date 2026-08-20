package org.rtk.common;

/**
 * Port of common/crypt.c — the NexusTK/RetroTK packet obfuscation layer.
 *
 * Wire format of a client packet:
 *   [0]    0xAA  header marker
 *   [1..2] big-endian length of everything after byte [2]
 *   [3]    packet opcode
 *   [4]    packet increment (also part of the XOR key)
 *   [5..]  payload
 *   the last 3 bytes (appended by setPacketIndexes) carry the key indexes.
 */
public final class Crypt {

    // static XOR key (enckey[] in crypt.c); configurable via
    // resources/rtk-server.properties, must match the RetroTK client
    private static final byte[] ENCKEY = Props.get("crypt.enckey", "Urk#nI7ni")
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    // handshake key sent to clients on version-check / map-server redirect
    private static final String HANDSHAKE_KEY = Props.get("crypt.handshake_key", "KruIn7inc");

    private Crypt() {
    }

    /** The 9-character handshake key ("KruIn7inc" on the original server). */
    public static String handshakeKey() {
        return HANDSHAKE_KEY;
    }

    // The C source compiles without USE_RANDOM_INDEXES, so rnd() is the
    // constant 0x1337. Kept identical here so the byte stream matches.
    private static int rnd() {
        return 0x1337;
    }

    /**
     * Port of set_packet_indexes(): appends the 3 key-index bytes to the
     * packet and fixes up the big-endian length header.
     *
     * @return total packet length including header (psize + 3)
     */
    public static int setPacketIndexes(byte[] packet, int off) {
        int k1 = (((rnd() & 0x7FFF) % 0x9B + 0x64) ^ 0x21) & 0xFF;
        int k2 = (((rnd() & 0x7FFF) + 0x100) ^ 0x7424) & 0xFFFF;
        int psize = ((packet[off + 1] & 0xFF) << 8) + (packet[off + 2] & 0xFF);

        psize += 3;
        packet[off + psize] = (byte) (k2 & 0xFF);
        packet[off + psize + 1] = (byte) k1;
        packet[off + psize + 2] = (byte) ((k2 >> 8) & 0xFF);
        packet[off + 1] = (byte) ((psize >> 8) & 0xFF);
        packet[off + 2] = (byte) (psize & 0xFF);

        return psize + 3;
    }

    /** Port of crypt(): XOR obfuscation with the static key. Symmetric. */
    public static void crypt(byte[] buf, int off) {
        cryptWithKey(buf, off, ENCKEY);
    }

    /** Port of crypt2(): XOR obfuscation with a session key (9 chars). */
    public static void crypt2(byte[] buf, int off, byte[] key) {
        cryptWithKey(buf, off, key);
    }

    private static void cryptWithKey(byte[] buf, int off, byte[] key) {
        int packetLen = (((buf[off + 1] & 0xFF) << 8) + (buf[off + 2] & 0xFF)) - 5;
        int packetInc = buf[off + 4] & 0xFF;
        int data = off + 5;

        if (packetLen < 0 || packetLen > 65535) {
            return;
        }

        int group = 0;
        int groupCount = 0;
        int keyLen = key.length; // 9 on the original server
        for (int i = 0; i < packetLen; i++) {
            int b = buf[data + i] & 0xFF;
            b ^= key[i % keyLen] & 0xFF;

            int keyVal = group & 0xFF; // second stage: group % 256
            if (keyVal != packetInc) {
                b ^= keyVal;
            }

            b ^= packetInc;
            buf[data + i] = (byte) b;

            groupCount++;
            if (groupCount == 9) {
                group++;
                groupCount = 0;
            }
        }
    }

    /** Port of generate_hashvalues(): MD5 hex string of the name. */
    public static String generateHashValues(String name) {
        return Md5.hex(name);
    }

    /**
     * Port of populate_table(): builds the 1056-character key table used by
     * generateKey2 by repeatedly hashing and appending.
     */
    public static String populateTable(String name) {
        String table = Md5.hex(name);
        table = Md5.hex(table);
        StringBuilder sb = new StringBuilder(table);
        for (int i = 0; i < 32; i++) {
            sb.append(Md5.hex(sb.toString()));
        }
        return sb.toString();
    }

    /**
     * Port of generate_key2(): derives the 9-byte session key from the key
     * indexes stored in the last 3 bytes of the packet.
     */
    public static byte[] generateKey2(byte[] packet, int off, String table, boolean fromClient) {
        int psize = ((packet[off + 1] & 0xFF) << 8) + (packet[off + 2] & 0xFF);
        int k1 = packet[off + psize + 1] & 0xFF;
        int k2 = ((packet[off + psize + 2] & 0xFF) << 8) + (packet[off + psize] & 0xFF);

        if (fromClient) {
            k1 ^= 0x25;
            k2 ^= 0x2361;
        } else {
            k1 ^= 0x21;
            k2 ^= 0x7424;
        }

        k1 = k1 * k1;

        byte[] keyOut = new byte[9];
        for (int i = 0; i < 9; i++) {
            keyOut[i] = (byte) table.charAt((k1 * i + k2) & 0x3FF);
            k1 += 3;
        }
        return keyOut;
    }
}
