package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Crypt;
import org.rtk.common.Session;
import org.rtk.map.data.MapData;

/**
 * Port of map/clif.c — paket yang dikirim map server ke klien permainan.
 *
 * <h3>Yang wajib diperhatikan</h3>
 * Berbeda dengan protokol antar-server yang little-endian, protokol klien
 * ini <b>big-endian</b> — pakai {@code wfifoWBE} / {@code wfifoLBE}. Semua
 * paket berbentuk:
 *
 * <pre>
 *   [0]     0xAA        penanda awal
 *   [1..2]  uint16 BE   panjang seluruh isi setelah byte [2]
 *   [3]     opcode
 *   [4]     increment   diisi setPacketIndexes()
 *   [5..]   muatan
 * </pre>
 *
 * Isinya dienkripsi lewat {@link #encrypt}, yang meniru {@code encrypt()} di
 * `clif.c`: sebagian kecil opcode memakai kunci XOR statis, sisanya memakai
 * <b>kunci sesi</b> yang diturunkan dari nama karakter. Salah memilih jalur
 * membuat klien menerima sampah tanpa pesan error apa pun.
 *
 * Karena klien RetroTK asli yang membaca paket-paket ini, tata letak byte-nya
 * tidak boleh berubah sedikit pun.
 */
public final class Clif {

    private static final Logger log = LogManager.getLogger(Clif.class);

    /**
     * Opcode yang memakai kunci XOR statis, bukan kunci sesi.
     * Setara {@code svkey1packets[]} di clif.c.
     */
    private static final int[] STATIC_KEY_PACKETS = {2, 3, 10, 64, 68, 94, 96, 98, 102, 111};

    /**
     * Opcode <b>masuk</b> yang memakai kunci statis.
     * Setara {@code clkey1packets[]} di clif.c.
     */
    private static final int[] STATIC_KEY_PACKETS_IN =
            {2, 3, 4, 11, 21, 38, 58, 66, 67, 75, 80, 87, 98, 113, 115, 123};

    // setting flags (common/mmo.h)
    private static final int FLAG_WEATHER = 32;
    private static final int FLAG_REALM = 64;

    /** Waktu & tahun dalam permainan; nanti diisi kalender dunia (map.c). */
    public static int curTime = 0;
    public static int curYear = 0;

    private Clif() {
    }

    // ------------------------------------------------------------------
    // enkripsi
    // ------------------------------------------------------------------

    /** isKey(): false bila opcode ini memakai kunci statis. */
    static boolean usesSessionKey(int opcode) {
        for (int p : STATIC_KEY_PACKETS) {
            if (opcode == p) {
                return false;
            }
        }
        return true;
    }

    /**
     * encrypt(): sisipkan indeks kunci lalu enkripsi paket yang sudah
     * disusun di buffer tulis.
     *
     * @return panjang total paket termasuk header, untuk {@code wfifoSet}
     */
    static int encrypt(Session s, User sd) {
        byte[] buf = s.wbuf();
        int off = s.woff();

        int total = Crypt.setPacketIndexes(buf, off);
        int opcode = buf[off + 3] & 0xFF;

        if (usesSessionKey(opcode) && sd != null && sd.encHash != null) {
            byte[] key = Crypt.generateKey2(buf, off, sd.encHash, false);
            Crypt.crypt2(buf, off, key);
        } else {
            Crypt.crypt(buf, off);
        }
        return total;
    }

    /** isKey2(): false bila opcode masuk ini memakai kunci statis. */
    static boolean usesSessionKeyIn(int opcode) {
        for (int p : STATIC_KEY_PACKETS_IN) {
            if (opcode == p) {
                return false;
            }
        }
        return true;
    }

    /**
     * decrypt(): balikkan enkripsi paket yang baru diterima dari klien,
     * di tempat pada buffer baca.
     *
     * Perhatikan argumen terakhir {@code generate_key2(..., 1)} — arah
     * "dari klien" memakai konstanta XOR yang berbeda dari arah keluar.
     */
    static void decrypt(Session s, User sd) {
        byte[] buf = s.rbuf();
        int off = s.roff();
        int opcode = buf[off + 3] & 0xFF;

        if (usesSessionKeyIn(opcode) && sd != null && sd.encHash != null) {
            byte[] key = Crypt.generateKey2(buf, off, sd.encHash, true);
            Crypt.crypt2(buf, off, key);
        } else {
            Crypt.crypt(buf, off);
        }
    }

    /** Header bersama: 0xAA + panjang + opcode. */
    private static void head(Session s, int opcode, int payloadLen) {
        s.wfifoB(0, 0xAA);
        s.wfifoWBE(1, payloadLen);
        s.wfifoB(3, opcode);
    }

    private static Session sessionOf(User sd) {
        return MapServer.net.session(sd.fd);
    }

    // ------------------------------------------------------------------
    // paket
    // ------------------------------------------------------------------

    /**
     * clif_sendid() — beri tahu klien id objeknya sendiri. Opcode 0x05.
     * Panjang isi 0x0E, total 17 byte.
     */
    public static void sendId(User sd) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x05, 0x0E);
        s.wfifoLBE(5, (int) sd.status.id);
        s.wfifoWBE(9, 0);
        s.wfifoB(11, 0);
        s.wfifoB(12, 2);
        s.wfifoB(13, 3);
        s.wfifoWBE(14, 0);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendmapinfo() — dimensi peta, judul, dan pencahayaan.
     * Opcode 0x15, panjang isi = 18 + panjang judul.
     */
    public static void sendMapInfo(User sd) {
        Session s = sessionOf(sd);
        MapData map = MapServer.world.get(sd.m);
        if (s == null || map == null) {
            return;
        }
        String title = map.title == null ? "" : map.title;
        int len = title.length();

        head(s, 0x15, 18 + len);
        s.wfifoWBE(5, sd.m);
        s.wfifoWBE(7, map.xs);
        s.wfifoWBE(9, map.ys);

        // 5 = normal; 4 bila pemain mengaktifkan tampilan cuaca
        s.wfifoB(11, (sd.status.settingFlags & FLAG_WEATHER) != 0 ? 4 : 5);
        s.wfifoB(12, (sd.status.settingFlags & FLAG_REALM) != 0 ? 0x01 : 0x00);

        s.wfifoB(13, len);
        s.wfifoStringRaw(14, title);
        // cahaya 0 dianggap belum diatur -> pakai 232 seperti versi C
        s.wfifoWBE(len + 14, map.light != 0 ? map.light : 232);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendxy() — posisi pemain plus offset kamera di layar klien.
     * Opcode 0x04, panjang isi 0x0D, total 16 byte.
     *
     * Offset kamera dihitung persis seperti versi C: bila peta cukup besar,
     * kamera dijepit agar tidak menampilkan luar peta; bila peta lebih kecil
     * dari layar, pemain digeser ke tengah.
     */
    public static void sendXy(User sd) {
        Session s = sessionOf(sd);
        MapData map = MapServer.world.get(sd.m);
        if (s == null || map == null) {
            return;
        }
        head(s, 0x04, 0x0D);
        s.wfifoWBE(5, sd.x);
        s.wfifoWBE(7, sd.y);
        s.wfifoWBE(9, cameraOffsetX(map, sd.x));
        s.wfifoWBE(11, cameraOffsetY(map, sd.y));
        s.wfifoB(13, 0x00);
        s.wfifoSet(encrypt(s, sd));
    }

    /** Perhitungan offset kamera sumbu X (lihat clif_sendxy di C). */
    static int cameraOffsetX(MapData map, int x) {
        if (map.xs >= 16) {
            if (x < 8) {
                return x;
            }
            if (x >= map.xs - 8) {
                return x - map.xs + 17;
            }
            return 8;
        }
        return (16 - map.xs) / 2 + x;
    }

    /** Perhitungan offset kamera sumbu Y (lihat clif_sendxy di C). */
    static int cameraOffsetY(MapData map, int y) {
        if (map.ys >= 14) {
            if (y < 7) {
                return y;
            }
            if (y >= map.ys - 7) {
                return y - map.ys + 15;
            }
            return 7;
        }
        return (14 - map.ys) / 2 + y;
    }

    /**
     * clif_sendtime() — waktu & tahun dalam permainan.
     * Opcode 0x20, panjang isi 4, total 7 byte.
     */
    public static void sendTime(User sd) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x20, 0x04);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, curTime);
        s.wfifoB(6, curYear);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendack() — tanda terima. Opcode 0x1E, panjang isi 6.
     */
    public static void sendAck(User sd) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x1E, 0x06);
        s.wfifoB(5, 0x06);
        s.wfifoB(6, 0x00);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * Bagian akhir clif_refresh() — perintah gambar ulang. Opcode 0x22.
     *
     * Catatan: paket ini di versi C hanya memanggil {@code set_packet_indexes}
     * <b>tanpa</b> enkripsi, lalu mengirim 5+3 byte. Perilaku itu ditiru apa
     * adanya karena klien mengharapkannya demikian.
     */
    public static void sendRefreshTrigger(User sd) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x22, 2);
        s.wfifoB(4, 0x03);
        Crypt.setPacketIndexes(s.wbuf(), s.woff());
        s.wfifoSet(5 + 3);
    }

    /**
     * clif_refresh() versi ringkas: kirim ulang info peta dan posisi.
     *
     * Bagian yang menggambar ulang isi sekitar ({@code clif_sendchararea},
     * {@code clif_getchararea}, {@code clif_mob_look_*}) belum diport —
     * menyusul bersama penyiaran entitas.
     */
    public static void refresh(User sd) {
        sendMapInfo(sd);
        sendXy(sd);
        sendRefreshTrigger(sd);
    }

    /**
     * clif_blockmovement() — kunci/lepas gerak di sisi klien. Opcode 0x51.
     * Dipakai berpasangan (0 lalu 1) saat memaksa klien kembali ke posisi
     * yang dianggap benar server.
     */
    public static void blockMovement(User sd, int flag) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x51, 5);
        s.wfifoB(5, flag);
        s.wfifoB(6, 0);
        s.wfifoB(7, 0);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * Tarik pemain kembali ke posisi menurut server, lalu buka kuncinya.
     * Urutannya (kunci, kirim posisi, buka) ditiru dari `clif_parsewalk`.
     */
    private static void snapBack(User sd) {
        blockMovement(sd, 0);
        sendXy(sd);
        blockMovement(sd, 1);
    }

    /**
     * clif_send(..., AREA_WOS): kirim paket mentah ke seluruh pemain lain
     * dalam area pandang, tanpa si pengirim sendiri.
     *
     * Paket disalin apa adanya lalu dienkripsi <b>per penerima</b>, karena
     * tiap pemain punya kunci sesi sendiri — sama seperti versi C yang
     * memanggil {@code encrypt(i)} untuk tiap fd.
     */
    static void sendToAreaExceptSelf(User from, byte[] packet, int len) {
        MapData map = MapServer.world.get(from.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(from.x, from.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl == from || !(bl instanceof User)) {
                return;
            }
            User to = (User) bl;
            Session ts = MapServer.net.session(to.fd);
            if (ts == null) {
                return;
            }
            ts.wfifoBytes(0, packet, len);
            ts.wfifoSet(encrypt(ts, to));
        });
    }

    // ------------------------------------------------------------------
    // gerakan
    // ------------------------------------------------------------------

    /** Pergeseran per arah: 0 atas, 1 kanan, 2 bawah, 3 kiri. */
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};

    /**
     * clif_parsewalk() — klien meminta melangkah satu petak (opcode 0x06).
     *
     * Tata letak paket masuk: arah di [5], posisi yang diklaim klien di
     * [8..9] (x) dan [10..11] (y), semuanya big-endian.
     *
     * Urutan pemeriksaan ditiru dari versi C: posisi klien harus cocok
     * dengan catatan server, lalu petak tujuan dijepit ke batas peta,
     * diperiksa penghalang statis dan penghuninya. Bila salah satu gagal,
     * pemain ditarik kembali alih-alih ditolak diam-diam.
     */
    public static void parseWalk(User sd) {
        Session s = sessionOf(sd);
        MapData map = MapServer.world.get(sd.m);
        if (s == null || map == null) {
            return;
        }

        int direction = s.rfifoB(5) & 0xFF;
        int claimX = s.rfifoWBE(8);
        int claimY = s.rfifoWBE(10);

        // klien dan server tidak sepakat soal posisi -> tarik kembali
        if (claimX != sd.x || claimY != sd.y) {
            log.debug("[CLIF] {} desinkron: klien mengira ({},{}), server ({},{})",
                    sd.name(), claimX, claimY, sd.x, sd.y);
            snapBack(sd);
            return;
        }

        int oldX = sd.x;
        int oldY = sd.y;
        // Versi C memakai switch tanpa default: arah di luar 0..3 tidak
        // menggeser apa pun. Jangan diganti dengan mask 0x03 — arah 4 akan
        // berubah jadi "ke atas" dan klien cacat bisa berjalan menembus.
        int nx = sd.x + (direction < 4 ? DX[direction] : 0);
        int ny = sd.y + (direction < 4 ? DY[direction] : 0);

        // jepit ke batas peta, seperti versi C
        nx = Math.max(0, Math.min(nx, map.xs - 1));
        ny = Math.max(0, Math.min(ny, map.ys - 1));

        sd.blocked = false;
        if (!sd.isGm()) {
            if (!map.walkable(nx, ny)) {
                sd.blocked = true;
            } else {
                for (org.rtk.map.data.BlockList bl : map.objectsAt(nx, ny)) {
                    if (blocksMovement(sd, bl, map)) {
                        sd.blocked = true;
                        break;
                    }
                }
            }
        }

        if (sd.blocked || sd.paralyzed) {
            snapBack(sd);
            return;
        }

        updateCamera(sd, map, direction, nx, ny);
        sendWalkConfirm(sd, direction, oldX, oldY);

        if (nx == sd.x && ny == sd.y) {
            return; // terjepit di tepi: tidak benar-benar berpindah
        }

        broadcastWalk(sd, direction, oldX, oldY);
        map.moveBlock(sd, nx, ny);
        fireWalkScripts(sd);
        checkWarpTile(sd, map);
    }

    /**
     * Petak yang baru diinjak mungkin portal. Port dari ekor
     * {@code clif_parsewalk}: syarat masuk peta tujuan diperiksa dulu, dan
     * bila gagal pemain didorong mundur disertai pesan penolakan.
     */
    private static void checkWarpTile(User sd, MapData map) {
        // C menjepit koordinat dulu sebelum menelusuri daftar portal
        int fx = Math.min(sd.x, map.xs - 1);
        int fy = Math.min(sd.y, map.ys - 1);
        MapData.Warp w = map.warpAt(fx, fy);
        if (w == null) {
            return;
        }

        MapData dest = MapServer.world.get(w.tm());
        if (dest == null) {
            // portal ke peta milik map server lain — roadmap C3
            log.warn("[CLIF] {} menginjak portal ke peta {} yang tidak dimuat di server ini",
                    sd.name(), w.tm());
            return;
        }

        if (!sd.isGm()) {
            String reject = entryRejection(sd, dest);
            if (reject != null) {
                pushBack(sd);
                sendMiniText(sd, dest.rejectMsg.isEmpty() ? reject : dest.rejectMsg);
                return;
            }
        }
        Pc.warp(MapServer.world, sd, w.tm(), w.tx(), w.ty());
    }

    /**
     * Alasan pemain ditolak masuk sebuah peta, atau null bila boleh masuk.
     * Urutan pemeriksaan dan bunyi pesannya ditiru persis dari C — teks ini
     * dilihat pemain, jadi jangan diparafrasekan.
     */
    private static String entryRejection(User sd, MapData dest) {
        var st = sd.status;
        boolean tooWeak = st.level < dest.reqLvl
                || (st.baseHp < dest.reqVita && st.baseMp < dest.reqMana)
                || st.mark < dest.reqMark
                || (dest.reqPath > 0 && st.charClass != dest.reqPath);
        if (tooWeak) {
            long gap = Math.abs(dest.reqLvl - st.level);
            if (gap >= 10) {
                return "Nightmarish visions of your own death repel you.";
            }
            if (gap >= 5) {
                return "You're not quite ready to enter yet.";
            }
            if (gap < 5) {
                return "You almost understand the secrets to this entrance.";
            }
            // Tiga cabang di bawah TIDAK PERNAH tercapai — sama seperti di C,
            // karena tiga syarat di atas sudah mencakup semua nilai gap.
            // Sengaja dipertahankan: pemain melihat pesan level walau yang
            // kurang sebenarnya mark atau path. Jangan "diperbaiki" tanpa
            // memutuskan bahwa perubahan perilaku itu memang diinginkan.
            if (st.mark < dest.reqMark) {
                return "You do not understand the secrets to enter.";
            }
            if (dest.reqPath > 0 && st.charClass != dest.reqPath) {
                return "Your path forbids it.";
            }
            return "A powerful force repels you.";
        }
        if (st.level > dest.lvlMax
                || (st.baseHp > dest.vitaMax && st.baseMp > dest.manaMax)) {
            // C memakai pesan tetap di sini, mengabaikan MapRejectMsg
            return "A magical barrier prevents you from entering.";
        }
        return null;
    }

    /**
     * clif_pushback(): dorong pemain dua petak ke belakang, berdasarkan
     * arah hadapnya ({@code status.side}).
     */
    public static void pushBack(User sd) {
        switch (sd.status.side) {
            case 0 -> Pc.warp(MapServer.world, sd, sd.m, sd.x, sd.y + 2);
            case 1 -> Pc.warp(MapServer.world, sd, sd.m, sd.x - 2, sd.y);
            case 2 -> Pc.warp(MapServer.world, sd, sd.m, sd.x, sd.y - 2);
            case 3 -> Pc.warp(MapServer.world, sd, sd.m, sd.x + 2, sd.y);
            default -> {
            }
        }
    }

    /** clif_sendminitext(): pesan status baris tunggal (tipe 3). */
    public static void sendMiniText(User sd, String msg) {
        sendMsg(sd, 3, msg);
    }

    /**
     * clif_sendmsg(): paket 0x0A. Tipe: 0 wisp, 3 mini text, 5 sistem,
     * 11 grup/subpath, 12 klan.
     *
     * Catatan kesetiaan: di C ladang tipe ditulis dengan {@code WFIFOW}
     * <i>tanpa</i> SWAP16 — jadi little-endian, berbeda dari ladang lain
     * pada paket klien yang big-endian. Ditiru apa adanya di sini.
     */
    public static void sendMsg(User sd, int type, String msg) {
        Session s = sessionOf(sd);
        if (s == null || msg == null || msg.isEmpty()) {
            return;
        }
        byte[] text = msg.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int len = Math.min(text.length, 255);

        head(s, 0x0A, 5 + len);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, type & 0xFF);         // sengaja little-endian, lihat javadoc
        s.wfifoB(6, (type >> 8) & 0xFF);
        s.wfifoB(7, len);
        for (int i = 0; i < len; i++) {
            s.wfifoB(8 + i, text[i]);
        }
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_canmove_sub(): apakah benda ini menghalangi langkah {@code sd}?
     *
     * Cermin dari versi C, termasuk pengecualiannya:
     * <ul>
     *   <li><b>diri sendiri tidak pernah menghalangi</b> — penting di tepi
     *       peta, karena koordinat tujuan dijepit sehingga bisa sama dengan
     *       posisi sekarang;</li>
     *   <li>hantu hanya menghalangi pemain yang bisa melihatnya;</li>
     *   <li>pemain tersembunyi dan GM ber-stealth tidak menghalangi;</li>
     *   <li>NPC bertipe 2 (dekorasi) tidak menghalangi.</li>
     * </ul>
     */
    private static boolean blocksMovement(User sd, org.rtk.map.data.BlockList bl, MapData map) {
        if (bl.id == sd.id) {
            return false;
        }
        if (bl.type == org.rtk.map.data.BlockList.Type.NPC && bl.subtype == 2) {
            return false;
        }
        if (bl instanceof User tsd) {
            boolean hantuTakTerlihat = map.showGhosts != 0
                    && tsd.status.state == User.STATE_GHOST
                    && sd.status.state != User.STATE_GHOST
                    && (sd.optFlags & User.OPT_GHOSTS) == 0;
            boolean disembunyikan = tsd.status.state == User.STATE_HIDDEN
                    || (tsd.isGm() && (tsd.optFlags & User.OPT_STEALTH) != 0);
            if (hantuTakTerlihat || disembunyikan) {
                return false;
            }
        }
        // TODO(A5): mob yang sudah mati tidak menghalangi (MOB_DEAD di C).
        return true;
    }

    /**
     * Geser kamera klien mengikuti langkah, dengan aturan tepi yang sama
     * seperti versi C: kamera hanya ikut bergerak bila pemain belum berada
     * di zona tepi peta.
     */
    private static void updateCamera(User sd, MapData map, int direction, int nx, int ny) {
        switch (direction) {
            case 0 -> {
                if (ny <= sd.viewY || ((map.ys - 1 - ny) < 7 && sd.viewY > 7)) {
                    sd.viewY--;
                }
            }
            case 1 -> {
                if ((nx < 8 && sd.viewX < 8) || 16 - (map.xs - 1 - nx) <= sd.viewX) {
                    sd.viewX++;
                }
            }
            case 2 -> {
                if ((ny < 7 && sd.viewY < 7) || 14 - (map.ys - 1 - ny) <= sd.viewY) {
                    sd.viewY++;
                }
            }
            case 3 -> {
                if (nx <= sd.viewX || ((map.xs - 1 - nx) < 8 && sd.viewX > 8)) {
                    sd.viewX--;
                }
            }
            // C memakai empat `if (direction == n)` terpisah, jadi arah di
            // luar 0..3 tidak menyentuh kamera sama sekali. Jangan jadikan
            // cabang arah 3 sebagai `default`.
            default -> {
            }
        }
        sd.viewX = Math.max(0, Math.min(sd.viewX, 16));
        sd.viewY = Math.max(0, Math.min(sd.viewY, 14));
    }

    /** Konfirmasi langkah ke pemain yang bergerak. Opcode 0x26. */
    private static void sendWalkConfirm(User sd, int direction, int oldX, int oldY) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x26, 0x0C);
        s.wfifoB(5, direction);
        s.wfifoWBE(6, oldX);
        s.wfifoWBE(8, oldY);
        s.wfifoWBE(10, sd.viewX);
        s.wfifoWBE(12, sd.viewY);
        s.wfifoB(14, 0x00);
        s.wfifoSet(encrypt(s, sd));
    }

    /** Beri tahu pemain lain di sekitar bahwa seseorang melangkah. Opcode 0x0C. */
    private static void broadcastWalk(User sd, int direction, int oldX, int oldY) {
        byte[] buf = new byte[32];
        buf[0] = (byte) 0xAA;
        buf[1] = 0x00;
        buf[2] = 0x0C;
        buf[3] = 0x0C;
        putBE32(buf, 5, (int) sd.status.id);
        putBE16(buf, 9, oldX);
        putBE16(buf, 11, oldY);
        buf[13] = (byte) direction;
        buf[14] = 0x00;
        sendToAreaExceptSelf(sd, buf, 15);
    }

    private static void putBE16(byte[] b, int o, int v) {
        b[o] = (byte) ((v >> 8) & 0xFF);
        b[o + 1] = (byte) (v & 0xFF);
    }

    private static void putBE32(byte[] b, int o, int v) {
        b[o] = (byte) ((v >> 24) & 0xFF);
        b[o + 1] = (byte) ((v >> 16) & 0xFF);
        b[o + 2] = (byte) ((v >> 8) & 0xFF);
        b[o + 3] = (byte) (v & 0xFF);
    }

    /**
     * Kaitan skrip saat melangkah. Versi C juga memanggil {@code on_walk}
     * per perlengkapan dan {@code on_walk_passive} per mantra — menyusul
     * setelah subsistem barang & mantra diport.
     */
    private static void fireWalkScripts(User sd) {
        if (MapServer.scriptEngine == null) {
            return;
        }
        try {
            MapServer.scriptEngine.doScript("onScriptedTile", "onScriptedTile",
                    MapServer.scriptEngine.playerRef(Pc.scriptPlayerOf(sd)));
        } catch (RuntimeException e) {
            log.error("[CLIF] kaitan skrip onScriptedTile gagal untuk {}", sd.name(), e);
        }
    }

    /**
     * Urutan paket saat pemain baru masuk dunia, mengikuti
     * {@code intif_mmo_tosd} + {@code clif_spawn} di versi C.
     */
    public static void sendWorldEntry(User sd) {
        Session s = sessionOf(sd);
        if (s == null) {
            log.error("[CLIF] {} tidak punya sesi aktif — paket masuk dunia dibatalkan", sd.name());
            return;
        }
        sendId(sd);
        sendMapInfo(sd);
        sendXy(sd);
        sendTime(sd);
        sendAck(sd);
        sendRefreshTrigger(sd);
        log.info("[CLIF] paket masuk dunia dikirim ke {} (fd {})", sd.name(), sd.fd);
    }
}
