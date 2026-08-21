package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Crypt;
import org.rtk.common.Session;
import org.rtk.map.data.Equip;
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

    // bendera isi paket status (SFLAG_* di map.h)
    /** Selalu menyala; menentukan blok ekor yang selalu ikut dikirim. */
    public static final int SFLAG_ALWAYSON = 0x08;
    public static final int SFLAG_XPMONEY = 0x10;
    public static final int SFLAG_HPMP = 0x20;
    public static final int SFLAG_FULLSTATS = 0x40;
    public static final int SFLAG_GMON = 0x80;

    /** Kombinasi yang dikirim saat pemain baru masuk dunia. */
    public static final int SFLAG_ALL = SFLAG_FULLSTATS | SFLAG_HPMP | SFLAG_XPMONEY;

    // setting flags (common/mmo.h)
    private static final int FLAG_WEATHER = 32;
    private static final int FLAG_REALM = 64;
    private static final int FLAG_HELM = 8192;
    private static final int FLAG_NECKLACE = 16384;

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
        sendCharArea(sd);   // tampilkan diri kita ke pemain sekitar
        getCharArea(sd);    // dan gambarkan mereka di layar kita
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

        int opcode = s.rfifoB(3) & 0xFF;
        int direction = s.rfifoB(5) & 0xFF;

        // Hanya paket 0x06 yang membawa permintaan gambar ulang; 0x32 tidak.
        int redrawX0 = 0;
        int redrawY0 = 0;
        int redrawW = 0;
        int redrawH = 0;
        int redrawCheck = 0;
        if (opcode == 0x06) {
            redrawX0 = s.rfifoWBE(12);
            redrawY0 = s.rfifoWBE(14);
            redrawW = s.rfifoB(16) & 0xFF;
            redrawH = s.rfifoB(17) & 0xFF;
            redrawCheck = s.rfifoWBE(18);
        }
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

        if (opcode == 0x06) {
            redrawArea(sd, map, redrawX0, redrawY0, redrawW, redrawH, redrawCheck);
        }

        fireWalkScripts(sd);
        checkWarpTile(sd, map);
    }

    /**
     * Cabang gambar ulang pada paket langkah 0x06: kirim petak petanya,
     * lalu gambarkan kembali benda-benda di dalam petak itu.
     *
     * <p>Urutannya ditiru dari {@code clif_parsewalk}: peta dulu, lalu
     * pemain, NPC, mob, dan terakhir menampilkan diri kita kepada pemain
     * lain di petak yang sama.</p>
     */
    private static void redrawArea(User sd, MapData map,
                                   int x0, int y0, int w, int h, int check) {
        sendMapData(sd, x0, y0, w, h, check);

        if (w <= 0 || h <= 0) {
            return;
        }
        int x1 = x0 + (w - 1);
        int y1 = y0 + (h - 1);

        map.foreachInBlock(x0, y0, x1, y1, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User other && other != sd) {
                sendCharLook(sd, other);
            }
        });
        map.foreachInBlock(x0, y0, x1, y1, org.rtk.map.data.BlockList.Type.NPC, bl -> {
            if (bl instanceof Npc nd) {
                sendNpcLook(sd, nd);
            }
        });
        // TODO(A5): sapuan BL_MOB -> clif_cmoblook_sub

        // LOOK_SEND: tampilkan diri kita kepada pemain lain di petak itu
        map.foreachInBlock(x0, y0, x1, y1, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User other && other != sd) {
                sendCharLook(other, sd);
            }
        });
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

    // ------------------------------------------------------------------
    // dialog NPC (clif_scriptmes / clif_scriptmenuseq)
    // ------------------------------------------------------------------

    /**
     * clif_scriptmes(): kotak dialog NPC — opcode 0x30, ragam
     * {@code dialogtype == 0} (dialog bergrafik).
     *
     * <p>Grafik yang dipakai bukan milik NPC-nya melainkan
     * {@link User#npcGraphic} yang disetel skrip, sehingga satu NPC bisa
     * menampilkan wajah berbeda-beda. Byte [11] mengelompokkan grafiknya:
     * 0 = tanpa gambar, 2 = id besar (&ge; 49152, wujud benda), 1 = sisanya.</p>
     *
     * @param id       id blok lawan bicara (biasanya {@link User#lastClick})
     * @param previous tampilkan tombol "sebelumnya"
     * @param next     tampilkan tombol "berikutnya"
     */
    public static void sendScriptMes(User sd, long id, String msg,
                                     boolean previous, boolean next) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        String text = msg == null ? "" : msg;
        byte[] raw = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int len = raw.length;
        int g = sd.npcGraphic;

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x30);
        s.wfifoWBE(5, 1);
        s.wfifoLBE(7, (int) id);

        if (sd.dialogType == 1) {
            // Ragam "NPC berwujud karakter": kotak dialog menampilkan
            // penampilan NPC lengkap, jadi tata letaknya jauh lebih panjang
            // dan teksnya bergeser ke offset 66.
            Npc nd = MapServer.npcs.byId(id) instanceof Npc n ? n : null;
            if (nd == null) {
                log.debug("[CLIF] dialogType 1 tapi id {} bukan NPC — pakai ragam biasa", id);
            } else {
                npcPortrait(s, nd);
                s.wfifoB(54, 1);
                s.wfifoWBE(55, g);
                s.wfifoB(57, sd.npcGraphicColor);
                s.wfifoLBE(58, 1);
                s.wfifoB(62, previous ? 1 : 0);
                s.wfifoB(63, next ? 1 : 0);
                s.wfifoWBE(64, len);
                for (int i = 0; i < len; i++) {
                    s.wfifoB(66 + i, raw[i]);
                }
                s.wfifoWBE(1, len + 63);
                s.wfifoSet(encrypt(s, sd));
                return;
            }
        }

        s.wfifoB(11, g == 0 ? 0 : (g >= 49152 ? 2 : 1));
        s.wfifoB(12, 1);
        s.wfifoWBE(13, g);
        s.wfifoB(15, sd.npcGraphicColor);
        s.wfifoB(16, 1);
        s.wfifoWBE(17, g);
        s.wfifoB(19, sd.npcGraphicColor);
        s.wfifoLBE(20, 1);
        s.wfifoB(24, previous ? 1 : 0);
        s.wfifoB(25, next ? 1 : 0);
        s.wfifoWBE(26, len);
        for (int i = 0; i < len; i++) {
            s.wfifoB(28 + i, raw[i]);
        }
        s.wfifoWBE(1, len + 25);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * Penampilan NPC di dalam kotak dialog ({@code dialogtype == 1}),
     * offset 11..53.
     *
     * <p><b>Offsetnya berbeda dari paket gambar 0x33</b> meski isinya mirip:
     * di sini helm mulai di [33], di sana [35]; sepatu di [51] vs [53].
     * Jangan disatukan dengan {@link #sendNpcLook} — pergeserannya tidak
     * seragam.</p>
     */
    private static void npcPortrait(Session s, Npc nd) {
        s.wfifoB(11, 1);
        s.wfifoWBE(12, nd.sex);
        s.wfifoB(14, nd.state);
        s.wfifoB(15, 0);
        s.wfifoWBE(16, nd.hasEquip(Equip.ARMOR) ? (int) nd.equip[Equip.ARMOR].id : 0);
        s.wfifoB(18, 0);
        s.wfifoB(19, nd.face);
        s.wfifoB(20, nd.hair);
        s.wfifoB(21, nd.hairColor);
        s.wfifoB(22, nd.faceColor);
        s.wfifoB(23, nd.skinColor);

        if (!nd.hasEquip(Equip.ARMOR)) {
            s.wfifoWBE(24, 0xFFFF);
            s.wfifoB(26, 0);
        } else {
            s.wfifoWBE(24, (int) nd.equip[Equip.ARMOR].id);
            s.wfifoB(26, nd.armorColor != 0
                    ? nd.armorColor : (int) nd.equip[Equip.ARMOR].customLookColor);
        }
        if (nd.hasEquip(Equip.COAT)) {
            s.wfifoWBE(24, (int) nd.equip[Equip.COAT].id);
            s.wfifoB(26, (int) nd.equip[Equip.COAT].customLookColor);
        }

        npcSlotAt(s, nd, Equip.WEAP, 27, 29);
        npcSlotAt(s, nd, Equip.SHIELD, 30, 32);

        if (!nd.hasEquip(Equip.HELM)) {
            s.wfifoB(33, 0);
            s.wfifoB(34, 0xFF);
            s.wfifoB(35, 0);
        } else {
            s.wfifoB(33, 1);
            s.wfifoB(34, (int) nd.equip[Equip.HELM].id);
            s.wfifoB(35, (int) nd.equip[Equip.HELM].customLookColor);
        }

        npcSlotAt(s, nd, Equip.FACEACC, 36, 38);

        if (!nd.hasEquip(Equip.CROWN)) {
            s.wfifoWBE(39, 0xFFFF);
            s.wfifoB(41, 0);
        } else {
            s.wfifoB(33, 0);   // mahkota mematikan penanda helm
            s.wfifoWBE(39, (int) nd.equip[Equip.CROWN].id);
            s.wfifoB(41, (int) nd.equip[Equip.CROWN].customLookColor);
        }

        npcSlotAt(s, nd, Equip.FACEACCTWO, 42, 44);
        npcSlotAt(s, nd, Equip.MANTLE, 45, 47);
        npcSlotAt(s, nd, Equip.NECKLACE, 48, 50);

        if (!nd.hasEquip(Equip.BOOTS)) {
            s.wfifoWBE(51, nd.sex);
            s.wfifoB(53, 0);
        } else {
            s.wfifoWBE(51, (int) nd.equip[Equip.BOOTS].id);
            s.wfifoB(53, (int) nd.equip[Equip.BOOTS].customLookColor);
        }
    }

    /** Slot NPC pada offset bebas, pola kosong 0xFFFF/0. */
    private static void npcSlotAt(Session s, Npc nd, int slot, int idOff, int colorOff) {
        if (!nd.hasEquip(slot)) {
            s.wfifoWBE(idOff, 0xFFFF);
            s.wfifoB(colorOff, 0);
        } else {
            s.wfifoWBE(idOff, (int) nd.equip[slot].id);
            s.wfifoB(colorOff, (int) nd.equip[slot].customLookColor);
        }
    }

    /**
     * clif_scriptmenuseq(): dialog berisi daftar pilihan.
     *
     * <p><b>Opcode-nya 0x30 — sama dengan kotak dialog biasa.</b> Yang
     * membedakan keduanya bagi klien adalah byte [5] dan [6]: dialog
     * memakai 0x00/0x01, menu memakai 0x02/0x02. Jangan tergoda memberi
     * menu opcode tersendiri; 0x2F itu paket <i>input</i>, bukan menu.</p>
     *
     * <p>Dipakai oleh primitif {@code menu} maupun {@code menuSeq} — di C
     * keduanya memanggil fungsi yang sama.</p>
     */
    public static void sendScriptMenu(User sd, long id, String dialog,
                                      java.util.List<String> options,
                                      boolean previous, boolean next) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        byte[] head = (dialog == null ? "" : dialog)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        java.util.List<String> opts = options == null ? java.util.List.of() : options;
        int g = sd.npcGraphic;

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x30);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, 0x02);   // penanda: ini menu, bukan dialog
        s.wfifoB(6, 0x02);
        s.wfifoLBE(7, (int) id);

        s.wfifoB(11, g == 0 ? 0 : (g >= 49152 ? 2 : 1));
        s.wfifoB(12, 1);
        s.wfifoWBE(13, g);
        s.wfifoB(15, sd.npcGraphicColor);
        s.wfifoB(16, 1);
        s.wfifoWBE(17, g);
        s.wfifoB(19, sd.npcGraphicColor);
        s.wfifoLBE(20, 1);
        s.wfifoB(24, previous ? 1 : 0);
        s.wfifoB(25, next ? 1 : 0);

        s.wfifoWBE(26, head.length);
        for (int i = 0; i < head.length; i++) {
            s.wfifoB(28 + i, head[i]);
        }

        // Penghitung `len` di C dimulai dari strlen(dialog)+1 lalu tumbuh
        // per opsi; offsetnya len+27 / len+28. Ditiru apa adanya supaya
        // tata letaknya sama byte per byte.
        int len = head.length + 1;
        s.wfifoB(len + 27, opts.size());
        len += 1;
        for (String o : opts) {
            byte[] ob = (o == null ? "" : o)
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            int ol = Math.min(ob.length, 255);
            s.wfifoB(len + 27, ol);
            for (int i = 0; i < ol; i++) {
                s.wfifoB(len + 28 + i, ob[i]);
            }
            len += ol + 1;
        }

        s.wfifoWBE(1, len + 24);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_input(): kotak isian teks — opcode <b>0x2F</b> dengan byte
     * [5] = 3.
     *
     * <p>Di C paket ini dipakai ulang dari jalur "beli", karena itu
     * komentarnya menyebut buy packet; byte [5] yang membedakan ragamnya.</p>
     */
    public static void sendInput(User sd, long id, String prompt, String name) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        byte[] msg = (prompt == null ? "" : prompt)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] nm = (name == null ? "" : name)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int g = sd.npcGraphic;

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x2F);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, 3);      // ragam: isian jumlah/teks
        s.wfifoLBE(6, (int) id);

        s.wfifoB(10, g == 0 ? 0 : (g >= 49152 ? 2 : 1));
        s.wfifoWBE(11, g);
        s.wfifoB(13, sd.npcGraphicColor);

        int pos = 14;
        s.wfifoB(pos++, nm.length);
        for (byte b : nm) {
            s.wfifoB(pos++, b);
        }
        s.wfifoWBE(pos, msg.length);
        pos += 2;
        for (byte b : msg) {
            s.wfifoB(pos++, b);
        }

        s.wfifoWBE(1, pos - 3);
        s.wfifoSet(encrypt(s, sd));
    }

    // ------------------------------------------------------------------
    // toko (clif_buydialog / clif_selldialog)
    // ------------------------------------------------------------------

    /**
     * clif_buydialog(): daftar barang yang dijual NPC — opcode 0x2F,
     * [5] = 4, [6] = 2.
     *
     * <p>Tata letaknya tumbuh per barang, dan penghitung {@code len} di C
     * <b>tidak seragam</b>: ikon memakan 3 byte, harga 4 byte, lalu nama dan
     * keterangan masing-masing "panjang + isi + 1". Ditiru apa adanya —
     * jangan dirapikan jadi satu pola.</p>
     *
     * @param names nama skrip barang; harga diambil dari {@code prices}
     *              pada indeks yang sama
     */
    public static void sendBuyDialog(User sd, long id, String dialog,
                                     java.util.List<String> names,
                                     java.util.List<Integer> prices) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        var db = MapServer.itemDb;
        byte[] head = (dialog == null ? "" : dialog)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int g = sd.npcGraphic;
        int count = names == null ? 0 : names.size();

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x2F);
        s.wfifoB(5, 4);      // ragam: daftar beli
        s.wfifoB(6, 2);
        s.wfifoLBE(7, (int) id);

        s.wfifoB(11, g > 49152 ? 2 : 1);
        s.wfifoB(12, 1);
        s.wfifoWBE(13, g);
        s.wfifoB(15, sd.npcGraphicColor);
        s.wfifoB(16, 1);
        s.wfifoWBE(17, g);
        s.wfifoB(19, sd.npcGraphicColor);

        s.wfifoWBE(20, head.length);
        for (int i = 0; i < head.length; i++) {
            s.wfifoB(22 + i, head[i]);
        }

        int len = head.length;
        // C menulis panjang dialog dua kali: sekali BE di [20], sekali lagi
        // little-endian di [len+22]. Ditiru apa adanya.
        s.wfifoB(len + 22, head.length & 0xFF);
        s.wfifoB(len + 23, (head.length >> 8) & 0xFF);
        len += 2;
        s.wfifoWBE(len + 22, count);
        len += 2;

        for (int x = 0; x < count; x++) {
            var info = db.infoByName(names.get(x));
            if (info == null) {
                info = db.info(0);
            }
            s.wfifoWBE(len + 22, info.look().icon());
            s.wfifoB(len + 24, info.look().iconColor());
            len += 3;

            int harga = prices != null && x < prices.size()
                    ? prices.get(x) : info.buyPrice();
            s.wfifoLBE(len + 22, harga);
            len += 4;

            byte[] nm = info.tampilan()
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            int nl = Math.min(nm.length, 255);
            s.wfifoB(len + 22, nl);
            for (int i = 0; i < nl; i++) {
                s.wfifoB(len + 23 + i, nm[i]);
            }
            len += nl + 1;

            byte[] ket = info.buyText()
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            int kl = Math.min(ket.length, 255);
            s.wfifoB(len + 22, kl);
            for (int i = 0; i < kl; i++) {
                s.wfifoB(len + 23 + i, ket[i]);
            }
            len += kl + 1;
        }

        s.wfifoWBE(1, len + 19);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_selldialog(): daftar barang milik pemain yang boleh dijual —
     * opcode 0x2F, [5] = 5, [6] = 4.
     *
     * <p>Isinya <b>nomor slot inventaris</b>, bukan nama barang: klien
     * menampilkan sendiri barang di slot itu. Nomornya dikirim
     * <b>1-basis</b> ({@code item[i] + 1} di C), sedangkan jawabannya
     * kembali apa adanya lewat 0x39 ragam 4 — jadi konversinya hanya di
     * satu arah, dan itu memang begitu di C.</p>
     *
     * @param slots indeks slot inventaris 0-basis
     */
    public static void sendSellDialog(User sd, long id, String dialog,
                                      java.util.List<Integer> slots) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        byte[] head = (dialog == null ? "" : dialog)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        java.util.List<Integer> list = slots == null ? java.util.List.of() : slots;
        int g = sd.npcGraphic;

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x2F);
        s.wfifoB(5, 5);      // ragam: daftar jual
        s.wfifoB(6, 4);
        s.wfifoLBE(7, (int) id);

        s.wfifoB(11, g > 49152 ? 2 : 1);
        s.wfifoB(12, 1);
        s.wfifoWBE(13, g);
        s.wfifoB(15, sd.npcGraphicColor);
        s.wfifoB(16, 1);
        s.wfifoWBE(17, g);
        s.wfifoB(19, sd.npcGraphicColor);

        s.wfifoWBE(20, head.length);
        for (int i = 0; i < head.length; i++) {
            s.wfifoB(22 + i, head[i]);
        }

        // Perhatikan: di sini `len` mulai dari panjang+2 (beda dari daftar
        // beli yang mulai dari panjang saja), dan panjang dialog ditulis
        // ulang big-endian. Ditiru apa adanya.
        int len = head.length + 2;
        s.wfifoWBE(len + 20, head.length);
        len += 2;
        s.wfifoB(len + 20, list.size());
        len += 1;
        for (int slot : list) {
            s.wfifoB(len + 20, slot + 1);   // 1-basis ke klien
            len += 1;
        }

        s.wfifoWBE(1, len + 17);
        s.wfifoSet(encrypt(s, sd));
    }

    // ------------------------------------------------------------------
    // klik objek (clif_handle_clickgetinfo)
    // ------------------------------------------------------------------

    /**
     * clif_handle_clickgetinfo(): pemain mengklik sesuatu — opcode 0x43.
     *
     * <p>Klien mengirim <b>id blok</b> benda yang diklik di offset 6. Nilai
     * 0 berarti "yang tadi" — klien memakainya untuk melanjutkan interaksi
     * yang sedang berjalan, jadi id terakhir disimpan di
     * {@link User#lastClick}.</p>
     */
    public static void parseClick(User sd) {
        Session s = sessionOf(sd);
        MapData map = MapServer.world.get(sd.m);
        if (s == null || map == null) {
            return;
        }
        long id = s.rfifoLBE(6) & 0xFFFFFFFFL;
        var bl = id == 0 ? MapServer.npcs.byId(sd.lastClick) : MapServer.npcs.byId(id);
        if (bl == null) {
            log.debug("[CLIF] {} mengklik id {} yang tidak dikenal", sd.name(), id);
            return;
        }
        if (bl.m != sd.m) {
            return;
        }

        sd.lastClick = bl.id;
        if (bl instanceof Npc nd) {
            clickNpc(sd, nd);
        }
        // TODO(A5): klik mob -> onLook + skrip "click" milik mob
    }

    /**
     * Jalankan skrip {@code click} milik NPC.
     *
     * <p>Penjaga karma ditiru dari C: pemain berkarma sangat buruk ditolak
     * bicara oleh hampir semua NPC — kecuali NPC F1 dan NPC totem, karena
     * keduanya jalur keluar yang tidak boleh ikut tertutup.</p>
     */
    private static void clickNpc(User sd, Npc nd) {
        if (MapServer.scriptEngine == null) {
            return;
        }
        if (sd.status.karma <= -3.0f
                && !"F1Npc".equals(nd.name) && !"TotemNpc".equals(nd.name)) {
            sendScriptMes(sd, nd.id, "Go away scum!", false, false);
            return;
        }

        sd.npcGraphic = (int) nd.graphicId;
        sd.npcGraphicColor = (int) nd.graphicColor;
        sd.dialogType = 0;

        var p = sd.scriptPlayer();
        // Setiap klik memulai interaksi dari nol (sl_async_freeco di C):
        // tanpa ini, dialog lama yang menggantung membuat klik berikutnya
        // ditolak dengan alasan "sedang sibuk".
        MapServer.scriptEngine.cancel(p);
        try {
            // C memanggil dengan DUA argumen: pemain dan NPC-nya
            // (`sl_doscript_blargs(nd->name, "click", 2, &sd->bl, &nd->bl)`).
            // Banyak skrip memakai parameter kedua untuk membaca
            // npc.yname / npc.bankNPC — tanpa ini mereka menerima nil.
            MapServer.scriptEngine.doScript(nd.name, "click",
                    MapServer.scriptEngine.playerRef(p),
                    MapServer.scriptEngine.objectRef(nd));
            flushDialog(sd, p);
        } catch (RuntimeException e) {
            log.error("[CLIF] skrip click '{}' gagal untuk {}", nd.name, sd.name(), e);
        }
    }

    /**
     * Kirim paket untuk dialog yang sedang ditunggu skrip, bila ada.
     *
     * <p>Skrip berhenti di tengah lewat coroutine dan menitipkan apa yang
     * ingin ditampilkan; fungsi inilah yang menerjemahkannya jadi paket.
     * Setelah dikirim, dialognya tetap tergantung sampai klien menjawab.</p>
     */
    static void flushDialog(User sd, org.rtk.map.script.ScriptPlayer p) {
        var d = p.pendingDialog;
        if (d == null) {
            return;
        }
        // Nama ragam ini datang dari primitif di Bindings — bukan dari
        // lapisan `player.lua` di atasnya (menuString/dialogSeq dibangun
        // dari primitif ini, bukan sebaliknya).
        switch (d.kind) {
            case "menu", "menuSeq" ->
                    sendScriptMenu(sd, sd.lastClick, d.message, d.options, false, false);
            case "dialog" ->
                    sendScriptMes(sd, sd.lastClick, d.message,
                            hasOpt(d, "previous"), hasOpt(d, "next"));
            case "input", "inputSeq" ->
                    sendInput(sd, sd.lastClick, d.message, "");
            case "buy" ->
                    sendBuyDialog(sd, sd.lastClick, d.message, d.options, d.prices);
            case "sell" ->
                    sendSellDialog(sd, sd.lastClick, d.message, d.prices);
            default -> log.warn("[CLIF] dialog jenis '{}' belum punya paket", d.kind);
        }
    }

    /**
     * clif_handle_menuinput(): jawaban pemain untuk {@code menu} dan
     * {@code input} — opcode <b>0x39</b>.
     *
     * <p>Perhatikan pembagiannya, karena mudah tertukar dengan 0x3A:
     * primitif {@code menu} dan {@code input} dijawab di sini, sedangkan
     * {@code dialog}, {@code menuSeq}, dan {@code inputSeq} dijawab lewat
     * {@link #parseNpcDialog} (0x3A). Di C keduanya memang memakai fungsi
     * resume yang berbeda ({@code sl_resumemenu} vs
     * {@code sl_resumemenuseq}).</p>
     *
     * <p>Ragam di [5]: 0 = kotak ditutup, 1 = pilihan menu, 2 = beli,
     * 3 = isian teks, 4 = jual. Ragam beli/jual menyusul bersama toko.</p>
     */
    public static void parseMenuInput(User sd) {
        Session s = sessionOf(sd);
        if (s == null || MapServer.scriptEngine == null) {
            return;
        }
        var p = sd.scriptPlayer();
        if (p.coroutine == null) {
            return;   // hasCoref() di C: tidak ada yang menunggu
        }

        int ragam = s.rfifoB(5) & 0xFF;
        try {
            switch (ragam) {
                case 1 -> {
                    // clif_parsemenu: id di [6] BE32, pilihan di [10] BE16
                    int pilihan = s.rfifoWBE(10);
                    resumeWith(sd, p, org.luaj.vm2.LuaValue.valueOf(pilihan));
                }
                case 3 -> {
                    // clif_parseinput: panjang di [12], teks di [13];
                    // di C ada string kedua setelahnya — belum dipakai
                    // primitif mana pun, jadi cukup yang pertama.
                    int len = s.rfifoB(12) & 0xFF;
                    StringBuilder sb = new StringBuilder(len);
                    for (int i = 0; i < len; i++) {
                        sb.append((char) (s.rfifoB(13 + i) & 0xFF));
                    }
                    resumeWith(sd, p, org.luaj.vm2.LuaValue.valueOf(sb.toString()));
                }
                case 2 -> {
                    // clif_parsebuy: NAMA barang di [13], panjangnya di [12]
                    int len = s.rfifoB(12) & 0xFF;
                    StringBuilder sb = new StringBuilder(len);
                    for (int i = 0; i < len; i++) {
                        sb.append((char) (s.rfifoB(13 + i) & 0xFF));
                    }
                    String nama = sb.toString();
                    if (nama.isEmpty()) {
                        MapServer.scriptEngine.cancel(p);
                        return;
                    }
                    resumeWith(sd, p, org.luaj.vm2.LuaValue.valueOf(nama));
                }
                case 4 -> {
                    // clif_parsesell: nomor slot inventaris di [12]
                    int slot = s.rfifoB(12) & 0xFF;
                    resumeWith(sd, p, org.luaj.vm2.LuaValue.valueOf(slot));
                }
                default -> MapServer.scriptEngine.cancel(p);   // 0 dan lainnya: batal
            }
        } catch (RuntimeException e) {
            log.error("[CLIF] melanjutkan menu/input gagal untuk {}", sd.name(), e);
        }
    }

    /**
     * clif_parsenpcdialog(): jawaban pemain atas dialog NPC — opcode 0x3A.
     *
     * <p>Ragam jawabannya ada di [5]:
     * {@code 0x01} lanjut dialog, {@code 0x02} pilihan menu, {@code 0x04}
     * teks yang diketik. Nomor pilihan ada di [13], pilihan menu di [15],
     * dan teks di [16] dengan panjangnya di [15].</p>
     *
     * <p>Pada ragam teks, C membatalkan seluruh coroutine bila [13] bukan
     * 0x02 — itu cara klien mengabarkan pemain menutup kotak input.</p>
     */
    public static void parseNpcDialog(User sd) {
        Session s = sessionOf(sd);
        if (s == null || MapServer.scriptEngine == null) {
            return;
        }
        var p = sd.scriptPlayer();
        if (p.coroutine == null) {
            log.debug("[CLIF] {} menjawab dialog padahal tidak ada yang menunggu", sd.name());
            return;
        }

        int ragam = s.rfifoB(5) & 0xFF;
        int pilihan = s.rfifoB(13) & 0xFF;

        try {
            switch (ragam) {
                case 0x01 -> resumeWith(sd, p, org.luaj.vm2.LuaValue.valueOf(pilihan));
                case 0x02 -> {
                    int menu = s.rfifoB(15) & 0xFF;
                    resumeWith(sd, p, org.luaj.vm2.LuaValue.valueOf(menu));
                }
                case 0x04 -> {
                    if (pilihan != 0x02) {
                        // pemain menutup kotak input: buang coroutine-nya
                        MapServer.scriptEngine.cancel(p);
                        return;
                    }
                    int len = s.rfifoB(15) & 0xFF;
                    StringBuilder sb = new StringBuilder(len);
                    for (int i = 0; i < len; i++) {
                        sb.append((char) (s.rfifoB(16 + i) & 0xFF));
                    }
                    resumeWith(sd, p, org.luaj.vm2.LuaValue.valueOf(sb.toString()));
                }
                default -> log.debug("[CLIF] ragam dialog 0x{} belum ditangani",
                        String.format("%02X", ragam));
            }
        } catch (RuntimeException e) {
            log.error("[CLIF] melanjutkan dialog gagal untuk {}", sd.name(), e);
        }
    }

    /**
     * Apakah daftar opsi dialog memuat penanda tertentu.
     * {@code pcl_dialog} di C menelusuri tabel opsi mencari "previous" dan
     * "next" untuk menentukan tombol mana yang muncul.
     */
    private static boolean hasOpt(org.rtk.map.script.ScriptPlayer.PendingDialog d, String key) {
        return d.options != null && d.options.contains(key);
    }

    /** Lanjutkan skrip dengan jawaban pemain, lalu kirim dialog berikutnya. */
    private static void resumeWith(User sd, org.rtk.map.script.ScriptPlayer p,
                                   org.luaj.vm2.LuaValue answer) {
        p.pendingDialog = null;
        MapServer.scriptEngine.resume(p, answer);
        flushDialog(sd, p);
    }

    // ------------------------------------------------------------------
    // gambar ulang petak peta (clif_sendmapdata)
    // ------------------------------------------------------------------

    /**
     * Tabel CRC-16/CCITT yang dipakai {@code nexCRCC()} di clif.c.
     * Nilainya bagian dari protokol: klien menghitung checksum yang sama
     * dan mengirimkannya balik, jadi satu angka meleset membuat server
     * mengirim ulang petak peta terus-menerus.
     */
    private static final int[] CRC_TABLE = {
        0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50A5, 0x60C6, 0x70E7,
        0x8108, 0x9129, 0xA14A, 0xB16B, 0xC18C, 0xD1AD, 0xE1CE, 0xF1EF,
        0x1231, 0x0210, 0x3273, 0x2252, 0x52B5, 0x4294, 0x72F7, 0x62D6,
        0x9339, 0x8318, 0xB37B, 0xA35A, 0xD3BD, 0xC39C, 0xF3FF, 0xE3DE,
        0x2462, 0x3443, 0x0420, 0x1401, 0x64E6, 0x74C7, 0x44A4, 0x5485,
        0xA56A, 0xB54B, 0x8528, 0x9509, 0xE5EE, 0xF5CF, 0xC5AC, 0xD58D,
        0x3653, 0x2672, 0x1611, 0x0630, 0x76D7, 0x66F6, 0x5695, 0x46B4,
        0xB75B, 0xA77A, 0x9719, 0x8738, 0xF7DF, 0xE7FE, 0xD79D, 0xC7BC,
        0x48C4, 0x58E5, 0x6886, 0x78A7, 0x0840, 0x1861, 0x2802, 0x3823,
        0xC9CC, 0xD9ED, 0xE98E, 0xF9AF, 0x8948, 0x9969, 0xA90A, 0xB92B,
        0x5AF5, 0x4AD4, 0x7AB7, 0x6A96, 0x1A71, 0x0A50, 0x3A33, 0x2A12,
        0xDBFD, 0xCBDC, 0xFBBF, 0xEB9E, 0x9B79, 0x8B58, 0xBB3B, 0xAB1A,
        0x6CA6, 0x7C87, 0x4CE4, 0x5CC5, 0x2C22, 0x3C03, 0x0C60, 0x1C41,
        0xEDAE, 0xFD8F, 0xCDEC, 0xDDCD, 0xAD2A, 0xBD0B, 0x8D68, 0x9D49,
        0x7E97, 0x6EB6, 0x5ED5, 0x4EF4, 0x3E13, 0x2E32, 0x1E51, 0x0E70,
        0xFF9F, 0xEFBE, 0xDFDD, 0xCFFC, 0xBF1B, 0xAF3A, 0x9F59, 0x8F78,
        0x9188, 0x81A9, 0xB1CA, 0xA1EB, 0xD10C, 0xC12D, 0xF14E, 0xE16F,
        0x1080, 0x00A1, 0x30C2, 0x20E3, 0x5004, 0x4025, 0x7046, 0x6067,
        0x83B9, 0x9398, 0xA3FB, 0xB3DA, 0xC33D, 0xD31C, 0xE37F, 0xF35E,
        0x02B1, 0x1290, 0x22F3, 0x32D2, 0x4235, 0x5214, 0x6277, 0x7256,
        0xB5EA, 0xA5CB, 0x95A8, 0x8589, 0xF56E, 0xE54F, 0xD52C, 0xC50D,
        0x34E2, 0x24C3, 0x14A0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
        0xA7DB, 0xB7FA, 0x8799, 0x97B8, 0xE75F, 0xF77E, 0xC71D, 0xD73C,
        0x26D3, 0x36F2, 0x0691, 0x16B0, 0x6657, 0x7676, 0x4615, 0x5634,
        0xD94C, 0xC96D, 0xF90E, 0xE92F, 0x99C8, 0x89E9, 0xB98A, 0xA9AB,
        0x5844, 0x4865, 0x7806, 0x6827, 0x18C0, 0x08E1, 0x3882, 0x28A3,
        0xCB7D, 0xDB5C, 0xEB3F, 0xFB1E, 0x8BF9, 0x9BD8, 0xABBB, 0xBB9A,
        0x4A75, 0x5A54, 0x6A37, 0x7A16, 0x0AF1, 0x1AD0, 0x2AB3, 0x3A92,
        0xFD2E, 0xED0F, 0xDD6C, 0xCD4D, 0xBDAA, 0xAD8B, 0x9DE8, 0x8DC9,
        0x7C26, 0x6C07, 0x5C64, 0x4C45, 0x3CA2, 0x2C83, 0x1CE0, 0x0CC1,
        0xEF1F, 0xFF3E, 0xCF5D, 0xDF7C, 0xAF9B, 0xBFBA, 0x8FD9, 0x9FF8,
        0x6E17, 0x7E36, 0x4E55, 0x5E74, 0x2E93, 0x3EB2, 0x0ED1, 0x1EF0
    };

    /**
     * nexCRCC(): checksum atas tiga nilai per petak (tile, pass, obj).
     *
     * <p>Ditulis mengikuti versi C yang memproses <b>tiga short sekaligus</b>
     * per putaran, bukan satu per satu — urutan operasinya menentukan hasil,
     * jadi jangan disederhanakan jadi loop CRC biasa.</p>
     *
     * @param buf nilai mentah, kelipatan 3 (tile, pass, obj per petak)
     * @param len panjang dalam <b>byte</b> (6 per petak), sama seperti C
     */
    static int nexCrc(int[] buf, int len) {
        int crc = 0;
        int i = 0;
        while (len != 0) {
            crc = (CRC_TABLE[(crc >> 8) & 0xFF] ^ (crc << 8)) ^ buf[i];
            crc &= 0xFFFF;
            int temp = (CRC_TABLE[(crc >> 8) & 0xFF] ^ buf[i + 1]) & 0xFFFF;
            crc = ((temp << 8) ^ CRC_TABLE[((crc & 0xFF) ^ (temp >> 8)) & 0xFF]) ^ buf[i + 2];
            crc &= 0xFFFF;
            i += 3;
            len -= 6;
        }
        return crc & 0xFFFF;
    }

    /**
     * clif_sendmapdata(): kirim ulang petak peta yang diminta klien —
     * opcode 0x06.
     *
     * <p>Klien ikut mengirim checksum petak yang sedang dipegangnya. Bila
     * cocok, server <b>tidak mengirim apa pun</b>: itulah gunanya checksum,
     * menghemat kiriman saat pemain berjalan di wilayah yang sudah digambar.
     * Karena itu "tidak ada paket keluar" adalah hasil yang benar, bukan
     * kegagalan.</p>
     *
     * @param check checksum yang dikirim klien
     * @return true bila petaknya benar-benar dikirim
     */
    public static boolean sendMapData(User sd, int x0, int y0, int x1, int y1, int check) {
        Session s = sessionOf(sd);
        MapData map = MapServer.world.get(sd.m);
        if (s == null || map == null) {
            return false;
        }

        // penjaga yang sama dengan versi C terhadap permintaan kelewat besar
        if (x1 * y1 > 323) {
            log.warn("[CLIF] {} meminta petak peta kelewat besar: {}x{} di ({},{})",
                    sd.name(), x1, y1, x0, y0);
            return false;
        }
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 > map.xs) {
            x1 = map.xs;
        }
        if (y1 > map.ys) {
            y1 = map.ys;
        }

        int[] raw = new int[Math.max(x1 * y1 * 3, 3)];
        int a = 0;
        int len = 0;
        int pos = 12;

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x06);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, 0);
        s.wfifoWBE(6, x0);
        s.wfifoWBE(8, y0);
        s.wfifoB(10, x1);
        s.wfifoB(11, y1);

        for (int y = 0; y < y1; y++) {
            if (y + y0 >= map.ys) {
                break;
            }
            for (int x = 0; x < x1; x++) {
                if (x + x0 >= map.xs) {
                    break;
                }
                int tile = map.tile(x0 + x, y0 + y);
                int pass = map.pass(x0 + x, y0 + y);
                int obj = map.obj(x0 + x, y0 + y);

                raw[a] = tile;
                raw[a + 1] = pass;
                raw[a + 2] = obj;
                a += 3;
                len += 6;

                s.wfifoWBE(pos, tile);
                s.wfifoWBE(pos + 2, pass);
                s.wfifoWBE(pos + 4, obj);
                pos += 6;
            }
        }

        if (pos <= 12) {
            return false;   // tidak ada petak yang masuk
        }
        if (nexCrc(raw, len) == check) {
            return false;   // klien sudah punya petak yang sama
        }

        s.wfifoWBE(1, pos - 3);
        s.wfifoSet(encrypt(s, sd));
        return true;
    }

    // ------------------------------------------------------------------
    // penggambaran benda di area pandang (clif_*look_sub)
    // ------------------------------------------------------------------

    /**
     * clif_getchararea(): gambarkan benda di area pandang ke layar
     * {@code sd} — dipanggil saat pemain masuk dunia dan saat dunia
     * digambar ulang.
     *
     * <p>Urutannya ditiru dari C: pemain, lalu NPC, lalu mob. Bagian mob
     * masih kosong karena mob belum diport (roadmap A5) — itu disengaja,
     * bukan terlupa.</p>
     */
    public static void getCharArea(User sd) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(sd.x, sd.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User other && other != sd) {
                sendCharLook(sd, other);
            }
        });
        map.foreachInArea(sd.x, sd.y, org.rtk.map.data.BlockList.Type.NPC, bl -> {
            if (bl instanceof Npc nd) {
                sendNpcLook(sd, nd);
            }
        });
        // TODO(A5): sapuan BL_MOB -> clif_cmoblook_sub
    }

    /**
     * clif_sendchararea(): kebalikannya — tampilkan {@code sd} kepada
     * pemain lain di area pandangnya (LOOK_SEND di C).
     */
    public static void sendCharArea(User sd) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(sd.x, sd.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User other && other != sd) {
                sendCharLook(other, sd);
            }
        });
    }

    /**
     * clif_cnpclook_sub(): gambarkan satu NPC di layar {@code sd}.
     *
     * <p>Opcode 0x33 — sama dengan paket pemain, karena bagi klien NPC
     * berwujud karakter digambar dengan cara yang sama. Hanya NPC dengan
     * {@code NpcIsChar == 1} yang dikirim; sisanya sudah tergambar dari
     * grafik peta.</p>
     */
    public static void sendNpcLook(User sd, Npc nd) {
        Session s = sessionOf(sd);
        if (s == null || nd.m != sd.m || nd.npcType != 1) {
            return;
        }

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x33);
        s.wfifoWBE(5, nd.x);
        s.wfifoWBE(7, nd.y);
        s.wfifoB(9, nd.side);
        s.wfifoLBE(10, (int) nd.id);

        if (nd.state < 4) {
            s.wfifoWBE(14, nd.sex);
        } else {
            s.wfifoB(14, 1);
            s.wfifoB(15, 15);
        }

        // keadaan 2 = tak terlihat; GM tetap boleh melihat
        s.wfifoB(16, nd.state == 2 && sd.isGm() ? 5 : nd.state);
        s.wfifoB(19, 80);

        if (nd.state == 3) {
            s.wfifoWBE(17, (int) nd.graphicId);
        } else if (nd.state == 4) {
            s.wfifoWBE(17, (int) nd.graphicId + 32768);
            s.wfifoB(19, (int) nd.graphicColor);
        } else {
            s.wfifoWBE(17, 0);
        }

        s.wfifoB(20, 0);
        s.wfifoB(21, nd.face);
        s.wfifoB(22, nd.hair);
        s.wfifoB(23, nd.hairColor);
        s.wfifoB(24, nd.faceColor);
        s.wfifoB(25, nd.skinColor);

        // tanpa zirah, klien memakai jenis kelamin sebagai grafik dasar
        if (!nd.hasEquip(Equip.ARMOR)) {
            s.wfifoWBE(26, nd.sex);
            s.wfifoB(28, 0);
        } else {
            s.wfifoWBE(26, (int) nd.equip[Equip.ARMOR].id);
            s.wfifoB(28, nd.armorColor > 0
                    ? nd.armorColor : (int) nd.equip[Equip.ARMOR].customLookColor);
        }
        // mantel menimpa zirah di offset yang sama
        if (nd.hasEquip(Equip.COAT)) {
            s.wfifoWBE(26, (int) nd.equip[Equip.COAT].id);
            s.wfifoB(28, (int) nd.equip[Equip.COAT].customLookColor);
        }

        npcSlot(s, nd, Equip.WEAP, 29, 31);
        npcSlot(s, nd, Equip.SHIELD, 32, 34);

        // helm memakai penanda + nomor, bukan pola 0xFFFF seperti slot lain
        if (!nd.hasEquip(Equip.HELM)) {
            s.wfifoB(35, 0);
            s.wfifoWBE(36, 0xFF);
        } else {
            s.wfifoB(35, 1);
            s.wfifoB(36, (int) nd.equip[Equip.HELM].id);
            s.wfifoB(37, (int) nd.equip[Equip.HELM].customLookColor);
        }

        npcSlot(s, nd, Equip.FACEACC, 38, 40);

        if (!nd.hasEquip(Equip.CROWN)) {
            s.wfifoWBE(41, 0xFFFF);
            s.wfifoB(43, 0);
        } else {
            s.wfifoB(35, 0);   // mahkota mematikan penanda helm
            s.wfifoWBE(41, (int) nd.equip[Equip.CROWN].id);
            s.wfifoB(43, (int) nd.equip[Equip.CROWN].customLookColor);
        }

        npcSlot(s, nd, Equip.FACEACCTWO, 44, 46);

        // mantel: warna kosongnya 0xFF, bukan 0
        if (!nd.hasEquip(Equip.MANTLE)) {
            s.wfifoWBE(47, 0xFFFF);
            s.wfifoB(49, 0xFF);
        } else {
            s.wfifoWBE(47, (int) nd.equip[Equip.MANTLE].id);
            s.wfifoB(49, (int) nd.equip[Equip.MANTLE].customLookColor);
        }

        npcSlot(s, nd, Equip.NECKLACE, 50, 52);

        // sepatu kosong juga jatuh ke grafik dasar jenis kelamin
        if (!nd.hasEquip(Equip.BOOTS)) {
            s.wfifoWBE(53, nd.sex);
            s.wfifoB(55, 0);
        } else {
            s.wfifoWBE(53, (int) nd.equip[Equip.BOOTS].id);
            s.wfifoB(55, (int) nd.equip[Equip.BOOTS].customLookColor);
        }

        s.wfifoB(56, 0);
        s.wfifoB(57, 128);
        s.wfifoB(58, 0);

        int len;
        if (nd.state != 2) {
            String nama = nd.displayName == null ? "" : nd.displayName;
            len = Math.min(nama.length(), 255);
            s.wfifoB(59, len);
            s.wfifoStringRaw(60, nama.substring(0, len));
        } else {
            s.wfifoB(59, 0);
            len = 1;   // nama disembunyikan, tapi C tetap memakai panjang 1
        }

        // Catatan kesetiaan: paket NPC memakai `len + 60` sedangkan paket
        // pemain memakai `len + 60 + 3` untuk tata letak yang sama. Selisih
        // 3 byte itu ada di versi C dan ditiru apa adanya — jangan
        // "diseragamkan" tanpa menguji dengan klien sungguhan.
        s.wfifoWBE(1, len + 60);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_charlook_sub(): gambarkan pemain {@code who} di layar
     * {@code viewer}. Opcode 0x33, sama seperti NPC.
     *
     * <p>Aturan siapa boleh melihat siapa ditiru dari C: GM ber-stealth
     * tersembunyi dari non-GM, dan hantu hanya terlihat oleh sesama hantu
     * atau pemain yang mengaktifkan penglihatan hantu.</p>
     *
     * <p>Grafik perlengkapan diambil dari {@link org.rtk.map.data.ItemDb}
     * kecuali bila barangnya punya {@code customLook} sendiri — persis
     * urutan pemeriksaan di C.</p>
     */
    public static void sendCharLook(User viewer, User who) {
        Session s = sessionOf(viewer);
        MapData map = MapServer.world.get(who.m);
        if (s == null || map == null || who.m != viewer.m) {
            return;
        }
        // GM ber-stealth: hanya GM lain (dan dirinya sendiri) yang melihat
        if ((who.optFlags & User.OPT_STEALTH) != 0 && !viewer.isGm()
                && who.status.id != viewer.status.id) {
            return;
        }
        // hantu: hanya terlihat oleh sesama hantu atau yang punya OPT_GHOSTS
        if (map.showGhosts != 0 && who.status.state == User.STATE_GHOST
                && who.id != viewer.id
                && viewer.status.state != User.STATE_GHOST
                && (viewer.optFlags & User.OPT_GHOSTS) == 0) {
            return;
        }

        var st = who.status;
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x33);
        s.wfifoWBE(5, who.x);
        s.wfifoWBE(7, who.y);
        s.wfifoB(9, st.side);
        s.wfifoLBE(10, (int) st.id);

        if (st.state < 4) {
            s.wfifoWBE(14, st.sex);
        } else {
            s.wfifoB(14, 1);
            s.wfifoB(15, 15);
        }

        boolean stealth = (who.optFlags & User.OPT_STEALTH) != 0;
        if ((st.state == 2 || stealth) && who.id != viewer.id && viewer.isGm()) {
            s.wfifoB(16, 5);            // GM tetap melihat yang tak terlihat
        } else {
            s.wfifoB(16, st.state);
        }
        if (stealth && st.state == 0 && (!viewer.isGm() || who.id == viewer.id)) {
            s.wfifoB(16, 2);
        }

        s.wfifoB(19, who.speed);
        if (st.state == 3) {
            s.wfifoWBE(17, st.disguise);
        } else if (st.state == 4) {
            s.wfifoWBE(17, st.disguise + 32768);
            s.wfifoB(19, st.disguiseColor);
        } else {
            s.wfifoWBE(17, 0);
        }

        s.wfifoB(20, 0);
        s.wfifoB(21, st.face);
        s.wfifoB(22, st.hair);
        s.wfifoB(23, st.hairColor);
        s.wfifoB(24, st.faceColor);
        s.wfifoB(25, st.skinColor);

        // zirah
        var armor = st.equipAt(Equip.ARMOR);
        if (armor == null) {
            s.wfifoWBE(26, st.sex);
            s.wfifoB(28, 0);
        } else {
            s.wfifoWBE(26, armor.customLook != 0
                    ? (int) armor.customLook : MapServer.itemDb.lookOf(armor.id));
            s.wfifoB(28, st.armorColor > 0 ? st.armorColor
                    : (armor.customLook != 0 ? (int) armor.customLookColor
                                             : MapServer.itemDb.lookColorOf(armor.id)));
        }
        // mantel menimpa zirah di offset yang sama
        var coat = st.equipAt(Equip.COAT);
        if (coat != null) {
            s.wfifoWBE(26, MapServer.itemDb.lookOf(coat.id));
            s.wfifoB(28, st.armorColor > 0 ? st.armorColor
                    : MapServer.itemDb.lookColorOf(coat.id));
        }

        pcSlot(s, st, Equip.WEAP, 29, 31, true);
        pcSlot(s, st, Equip.SHIELD, 32, 34, true);

        // helm bisa disembunyikan lewat setelan pemain (FLAG_HELM)
        var helm = st.equipAt(Equip.HELM);
        boolean helmOn = helm != null && (st.settingFlags & FLAG_HELM) != 0
                && MapServer.itemDb.lookOf(helm.id) != -1;
        if (!helmOn) {
            s.wfifoB(35, 0);
            s.wfifoWBE(36, 0xFFFF);
        } else {
            s.wfifoB(35, 1);
            if (helm.customLook != 0) {
                s.wfifoB(36, (int) helm.customLook);
                s.wfifoB(37, (int) helm.customLookColor);
            } else {
                s.wfifoB(36, MapServer.itemDb.lookOf(helm.id));
                s.wfifoB(37, MapServer.itemDb.lookColorOf(helm.id));
            }
        }

        pcSlot(s, st, Equip.FACEACC, 38, 40, false);

        var crown = st.equipAt(Equip.CROWN);
        if (crown == null) {
            s.wfifoWBE(41, 0xFFFF);
            s.wfifoB(43, 0);
        } else {
            s.wfifoB(35, 0xFF);   // di paket pemain nilainya 0xFF, bukan 0
            s.wfifoWBE(41, crown.customLook != 0
                    ? (int) crown.customLook : MapServer.itemDb.lookOf(crown.id));
            s.wfifoB(43, crown.customLook != 0
                    ? (int) crown.customLookColor : MapServer.itemDb.lookColorOf(crown.id));
        }

        pcSlot(s, st, Equip.FACEACCTWO, 44, 46, false);

        var mantle = st.equipAt(Equip.MANTLE);
        if (mantle == null) {
            s.wfifoWBE(47, 0xFFFF);
            s.wfifoB(49, 0xFF);
        } else {
            s.wfifoWBE(47, MapServer.itemDb.lookOf(mantle.id));
            s.wfifoB(49, MapServer.itemDb.lookColorOf(mantle.id));
        }

        var neck = st.equipAt(Equip.NECKLACE);
        boolean neckOn = neck != null && (st.settingFlags & FLAG_NECKLACE) != 0
                && MapServer.itemDb.lookOf(neck.id) != -1;
        if (!neckOn) {
            s.wfifoWBE(50, 0xFFFF);
            s.wfifoB(52, 0);
        } else {
            s.wfifoWBE(50, MapServer.itemDb.lookOf(neck.id));
            s.wfifoB(52, MapServer.itemDb.lookColorOf(neck.id));
        }

        var boots = st.equipAt(Equip.BOOTS);
        if (boots == null) {
            s.wfifoWBE(53, st.sex);
            s.wfifoB(55, 0);
        } else {
            s.wfifoWBE(53, boots.customLook != 0
                    ? (int) boots.customLook : MapServer.itemDb.lookOf(boots.id));
            s.wfifoB(55, boots.customLook != 0
                    ? (int) boots.customLookColor : MapServer.itemDb.lookColorOf(boots.id));
        }

        s.wfifoB(56, 0);    // warna nama
        s.wfifoB(57, 128);  // warna garis tepi; 128 = hitam
        s.wfifoB(58, 0);    // 1 = PK, 2 = segrup, 3 = seklan

        if (viewer.status.clan == st.clan && st.clan > 0 && viewer.status.id != st.id) {
            s.wfifoB(58, 3);
        }
        if (st.pk > 0) {
            s.wfifoB(58, 1);
        }

        int len;
        String nama = st.name == null ? "" : st.name;
        if (st.state != 2 && st.state != 5) {
            len = Math.min(nama.length(), 255);
            s.wfifoB(59, len);
            s.wfifoStringRaw(60, nama.substring(0, len));
        } else {
            s.wfifoB(59, 0);
            len = 0;
        }

        s.wfifoWBE(1, len + 60 + 3);
        s.wfifoSet(encrypt(s, viewer));
    }

    /**
     * Slot pemain dengan pola kosong 0xFFFF/0.
     *
     * @param pakaiCustom true bila slot ini menghormati {@code customLook}
     *                    (senjata, perisai); di C sebagian slot lain langsung
     *                    memakai itemdb tanpa memeriksanya
     */
    private static void pcSlot(Session s, org.rtk.common.mmo.CharStatus st,
                               int slot, int idOff, int colorOff, boolean pakaiCustom) {
        var it = st.equipAt(slot);
        if (it == null) {
            s.wfifoWBE(idOff, 0xFFFF);
            s.wfifoB(colorOff, 0);
            return;
        }
        if (pakaiCustom && it.customLook != 0) {
            s.wfifoWBE(idOff, (int) it.customLook);
            s.wfifoB(colorOff, (int) it.customLookColor);
        } else {
            s.wfifoWBE(idOff, MapServer.itemDb.lookOf(it.id));
            s.wfifoB(colorOff, MapServer.itemDb.lookColorOf(it.id));
        }
    }

    /** Slot NPC yang kosongnya ditandai id 0xFFFF dan warna 0. */
    private static void npcSlot(Session s, Npc nd, int slot, int idOff, int colorOff) {
        if (!nd.hasEquip(slot)) {
            s.wfifoWBE(idOff, 0xFFFF);
            s.wfifoB(colorOff, 0);
        } else {
            s.wfifoWBE(idOff, (int) nd.equip[slot].id);
            s.wfifoB(colorOff, (int) nd.equip[slot].customLookColor);
        }
    }

    /**
     * clif_sendstatus(): panel status pemain — opcode 0x08.
     *
     * <p>Panjangnya <b>berubah-ubah</b>: isi paket ditentukan {@code flags},
     * dan ladang panjang di [1..2] baru ditulis di akhir setelah diketahui
     * berapa byte terpakai. Penghitung {@code len} di sini meniru versi C
     * persis — jangan diganti panjang tetap.</p>
     *
     * @param flags kombinasi SFLAG_*; {@link #SFLAG_ALWAYSON} selalu
     *              ditambahkan sendiri, sama seperti di C
     */
    public static void sendStatus(User sd, int flags) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        int f = flags | SFLAG_ALWAYSON;
        if (sd.isGm() && (sd.optFlags & User.OPT_WALKTHROUGH) != 0) {
            f |= SFLAG_GMON;
        }

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x08);
        s.wfifoB(5, f);

        int len = 0;
        if ((f & SFLAG_FULLSTATS) != 0) {
            s.wfifoB(6, 0);
            s.wfifoB(7, sd.status.country);
            s.wfifoB(8, sd.status.totem);
            s.wfifoB(9, 0);
            s.wfifoB(10, sd.status.level);
            s.wfifoLBE(11, (int) sd.maxHp);
            s.wfifoLBE(15, (int) sd.maxMp);
            s.wfifoB(19, sd.might);
            s.wfifoB(20, sd.will);
            s.wfifoB(21, 0x03);
            s.wfifoB(22, 0x03);
            s.wfifoB(23, sd.grace);
            s.wfifoB(24, 0);
            s.wfifoB(25, 0);
            s.wfifoB(26, sd.armor);
            for (int i = 27; i <= 33; i++) {
                s.wfifoB(i, 0);
            }
            s.wfifoB(34, sd.status.maxInv);
            len += 29;
        }

        if ((f & SFLAG_HPMP) != 0) {
            s.wfifoLBE(len + 6, (int) sd.status.hp);
            s.wfifoLBE(len + 10, (int) sd.status.mp);
            len += 8;
        }

        if ((f & SFLAG_XPMONEY) != 0) {
            s.wfifoLBE(len + 6, (int) sd.status.exp);
            s.wfifoLBE(len + 10, (int) sd.status.money);
            s.wfifoB(len + 14, (int) xpBarPercent(sd));
            len += 9;
        }

        s.wfifoB(len + 6, sd.drunk ? 1 : 0);
        s.wfifoB(len + 7, sd.blind ? 1 : 0);
        s.wfifoB(len + 8, 0);
        s.wfifoB(len + 9, 0);
        s.wfifoB(len + 10, 0);
        s.wfifoB(len + 11, sd.flags);
        s.wfifoB(len + 12, 0);
        s.wfifoLBE(len + 13, (int) sd.status.settingFlags);
        len += 11;

        s.wfifoWBE(1, len + 3);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_getXPBarPercent(): persentase bar pengalaman, 0..100.
     *
     * <p>Meniru versi C termasuk kejanggalannya: bila pengalaman pemain ada
     * di <i>bawah</i> ambang level sebelumnya — bisa terjadi setelah penalti
     * mati — rumusnya berganti. Tanpa tabel level (db/level_db.txt hilang)
     * hasilnya 0, jadi barnya kosong, bukan angka ngawur.</p>
     */
    static float xpBarPercent(User sd) {
        int level = sd.status.level;
        int path = sd.status.charClass;

        if (level >= 99) {
            return (float) ((double) sd.status.exp / 4294967295.0 * 100.0);
        }

        long thisLevel = MapServer.classDb.level(path, level);
        long prevLevel = MapServer.classDb.level(path, level - 1);
        long expInLevel = thisLevel - prevLevel;
        if (expInLevel <= 0) {
            return 0f;
        }
        if (sd.status.exp < prevLevel) {
            return thisLevel == 0 ? 0f
                    : (float) ((double) sd.status.exp / thisLevel * 100.0);
        }
        long tnl = thisLevel - sd.status.exp;
        return (float) ((double) (expInLevel - tnl) / expInLevel * 100.0);
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
        // Urutan ini diambil persis dari intif.c (parse_char_data): klien
        // RetroTK peka terhadap urutannya, jadi jangan dirapikan sendiri.
        sendAck(sd);
        sendTime(sd);
        sendId(sd);
        sendMapInfo(sd);
        sendStatus(sd, SFLAG_ALL);
        refresh(sd);
        sendXy(sd);
        getCharArea(sd);
        log.info("[CLIF] paket masuk dunia dikirim ke {} (fd {})", sd.name(), sd.fd);
    }
}
