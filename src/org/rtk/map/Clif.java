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
    /** mmo.h: FLAG_GROUP = 2, FLAG_EXCHANGE = 128. */
    private static final int FLAG_GROUP = 2;
    private static final int FLAG_EXCHANGE = 128;
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
    /** Diagnostik sementara: cetak paket keluar sebelum dienkripsi. */
    private static final boolean DIAG_DUMP =
            Boolean.getBoolean("rtk.diag.dumpOut");

    private static String hexDump(byte[] buf, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", buf[off + i]));
        }
        return sb.toString().trim();
    }

    static int encrypt(Session s, User sd) {
        byte[] buf = s.wbuf();
        int off = s.woff();

        int total = Crypt.setPacketIndexes(buf, off);
        int opcode = buf[off + 3] & 0xFF;

        // -Drtk.diag.dumpOut=true mencetak paket keluar SEBELUM dienkripsi.
        // Mati secara default. Dipertahankan sebagai alat: uji klien 26 Agu
        // 2026 membuktikan dump inilah yang menemukan byte sampah di ekor
        // paket (lihat Session.wfifoSet) — sesuatu yang tidak terlihat dari
        // membaca kode maupun dari 294 assertion cliftest.
        if (DIAG_DUMP) {
            log.info("[DUMP] keluar 0x{} ({} byte): {}",
                    String.format("%02X", opcode), total, hexDump(buf, off, total));
        }

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

    /** Header bersama: 0xAA + panjang + opcode. Ladang [4] dibiarkan 0. */
    private static void head(Session s, int opcode, int payloadLen) {
        s.wfifoB(0, 0xAA);
        s.wfifoWBE(1, payloadLen);
        s.wfifoB(3, opcode);
    }

    /**
     * Header versi {@code WFIFOHEADER()} — sama dengan {@link #head} plus
     * <b>nomor urut paket</b> di ladang [4].
     *
     * <p>Di C hanya sebagian paket dibangun lewat makro itu, dan sisanya
     * meninggalkan [4] bernilai 0 karena buffernya {@code CALLOC}. Jadi
     * <b>jangan diseragamkan</b>: pilih {@code head} atau {@code headSeq}
     * menurut makro yang dipakai fungsi C-nya. Yang memakai
     * {@code WFIFOHEADER}: 0x07, 0x0C ({@code clif_mob_move}), 0x0D
     * ({@code clif_speak}), 0x13 ({@code clif_send_mob_health_sub}), 0x1F,
     * 0x37, 0x3A, 0x3F, dan 0x51.</p>
     *
     * <p>Nomornya milik <b>sesi penerima</b>, bukan sesi pengirim — pada
     * paket siaran tiap penerima punya hitungannya sendiri.</p>
     */
    private static void headSeq(Session s, int opcode, int payloadLen) {
        head(s, opcode, payloadLen);
        s.wfifoB(4, s.nextIncrement());
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
     * clif_popup() — kotak teks menimpa layar. Opcode 0x0A ragam 0x08.
     */
    public static void popup(User sd, String text) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        String msg = text == null ? "" : text;
        byte[] raw = msg.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        head(s, 0x0A, raw.length + 5);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, 0x08);
        s.wfifoWBE(6, raw.length);
        s.wfifoBytes(8, raw);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_send_pc_healthscript() — bagian <b>paketnya</b>: bilah nyawa dan
     * angka kerusakan di atas pemain. Opcode 0x13, panjang isi 12.
     *
     * <p>⚠️ Disiarkan ke seluruh area, <b>kecuali</b> bila pemain sedang
     * ber-{@code state == 2} (tak terlihat) — saat itu hanya dirinya yang
     * melihat. Itu ada di C dan bukan kelalaian.</p>
     */
    public static void sendPcHealth(User sd, int critical, int percent, int damage) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        java.util.function.Consumer<User> tulis = to -> {
            Session ts = MapServer.net.session(to.fd);
            if (ts == null) {
                return;
            }
            ts.wfifoB(0, 0xAA);
            ts.wfifoWBE(1, 12);
            ts.wfifoB(3, 0x13);
            ts.wfifoLBE(5, (int) sd.id);
            ts.wfifoB(9, critical);
            ts.wfifoB(10, percent);
            ts.wfifoLBE(11, damage);
            ts.wfifoSet(encrypt(ts, to));
        };
        if (sd.status.state == 2) {
            tulis.accept(sd);
            return;
        }
        map.foreachInArea(sd.x, sd.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User to) {
                tulis.accept(to);
            }
        });
    }

    /**
     * Paket obrolan (opcode 0x0D) yang dikirim <b>hanya ke pemain ini</b> —
     * bagian kirim dari {@code pcl_talkself}.
     *
     * <p>Ladang [10] berisi {@code panjang + 2}, bukan panjang teksnya.
     * Itu memang begitu di C; jangan "dibetulkan".</p>
     *
     * @param speakerId id benda yang dianggap berbicara; 0 = pemain sendiri
     */
    public static void chatSelf(User sd, int type, long speakerId, String text) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        String msg = text == null ? "" : text;
        byte[] raw = msg.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int len = raw.length;

        head(s, 0x0D, 10 + len);
        s.wfifoB(5, type);
        s.wfifoLBE(6, (int) (speakerId == 0 ? sd.status.id : speakerId));
        s.wfifoB(10, len + 2);
        s.wfifoBytes(11, raw);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_speak() — satu baris obrolan yang <b>terdengar di sekitar</b>
     * sebuah benda. Opcode 0x0D, disiarkan ke seluruh pemain di area
     * (AREA, jadi pembicara sendiri ikut mendengar bila ia pemain).
     *
     * <p>Inilah yang membuat NPC dan mob benar-benar bersuara di layar —
     * jalur di balik {@code talk}, method skrip terbanyak kedua (698x).</p>
     *
     * <p>⚠️ <b>Bedanya dengan {@link #chatSelf} halus dan mudah tertukar</b>,
     * padahal opcodenya sama. C membangun paket ini lewat
     * {@code WFIFOHEADER} sehingga ia membawa nomor urut di [4], ladang
     * panjangnya {@code panjang + 8} (bukan {@code 10 + panjang}), dan [10]
     * berisi panjang teks apa adanya (bukan {@code panjang + 2}).
     * Ketiganya diambil dari C; jangan diseragamkan.</p>
     */
    public static void speak(org.rtk.map.data.BlockList from, int type, String text) {
        MapData map = MapServer.world.get(from.m);
        if (map == null) {
            return;
        }
        String msg = text == null ? "" : text;
        byte[] raw = msg.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        map.foreachInArea(from.x, from.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (!(bl instanceof User to)) {
                return;
            }
            Session ts = MapServer.net.session(to.fd);
            if (ts == null) {
                return;
            }
            headSeq(ts, 0x0D, raw.length + 8);
            ts.wfifoB(5, type);
            ts.wfifoLBE(6, (int) from.id);
            ts.wfifoB(10, raw.length);
            ts.wfifoBytes(11, raw);
            ts.wfifoSet(encrypt(ts, to));
        });
    }

    /**
     * clif_send_duration() — perbarui bilah durasi mantra di klien.
     * Opcode 0x3A, dibangun lewat {@code WFIFOHEADER}.
     *
     * <p>Yang dikirim adalah <b>nama tampilan</b> mantra, dan bila
     * penyihirnya orang lain namanya ikut dalam kurung:
     * {@code "Kawlana's guard (Tester)"}. Waktu dalam <b>detik</b>.</p>
     *
     * <p>Mantra dengan {@code SplTicker = 0} sengaja tidak dikirim sama
     * sekali — durasinya tetap berjalan di server, hanya tidak terlihat.
     * Itu penyaring di C, bukan kelalaian.</p>
     *
     * <p>⚠️ Ada satu keanehan yang ditiru apa adanya: bila {@code id == 0}
     * teksnya dipaksa menjadi {@code "Shield"} dengan panjang 6.</p>
     */
    public static void sendDuration(User sd, int id, int seconds, String casterName) {
        Session s = sessionOf(sd);
        if (s == null || !MapServer.spellDb.hasTicker(id)) {
            return;
        }
        String teks;
        if (id == 0) {
            teks = "Shield";
        } else if (casterName != null && !casterName.isEmpty()) {
            teks = MapServer.spellDb.displayNameOf(id) + " (" + casterName + ")";
        } else {
            teks = MapServer.spellDb.displayNameOf(id);
        }
        byte[] raw = teks.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int len = raw.length;

        headSeq(s, 0x3A, len + 7);
        s.wfifoB(5, len);
        s.wfifoBytes(6, raw);
        s.wfifoLBE(len + 6, seconds);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_send_aether() — sisa aether sebuah mantra. Opcode 0x3F,
     * juga lewat {@code WFIFOHEADER}.
     *
     * <p>Yang dikirim <b>nomor slot mantra di buku mantra</b> (1-basis),
     * bukan id mantranya — klien menandai barisnya sendiri. Mantra yang
     * tidak ada di buku pemain tidak dikirim sama sekali
     * ({@code clif_findspell_pos} mengembalikan -1).</p>
     */
    public static void sendAether(User sd, int id, int seconds) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        int pos = -1;
        for (int i = 0; i < sd.status.spells.length; i++) {
            if (sd.status.spells[i] == id) {
                pos = i;
                break;
            }
        }
        if (pos < 0) {
            return;
        }
        headSeq(s, 0x3F, 8);
        s.wfifoWBE(5, pos + 1);
        s.wfifoLBE(7, seconds);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendaction() — benda memainkan gerakan (serang, lempar, duduk,
     * sihir, makan). Opcode 0x1A, disiarkan ke SAMEAREA.
     *
     * <p>Ragam gerakan menurut komentar di C: 0 diam, 1 serang, 2 lempar,
     * 3 tembak, 4 dan 5 duduk, 6 sihir, 7 dan 8 makan.</p>
     *
     * <p>Bunyi menyusul paketnya bila {@code sound > 0}; kait skrip
     * {@code onAction} dipanggil pemanggilnya, bukan di sini.</p>
     */
    public static void sendAction(org.rtk.map.data.BlockList bl, int type, int time, int sound) {
        byte[] buf = new byte[32];
        buf[0] = (byte) 0xAA;
        buf[1] = 0x00;
        buf[2] = 0x0B;
        buf[3] = 0x1A;
        putBE32(buf, 5, (int) bl.id);
        buf[9] = (byte) type;
        buf[10] = 0x00;
        buf[11] = (byte) time;
        putBE16(buf, 12, 0);
        sendToArea(bl, buf, buf.length, true);

        if (sound > 0) {
            playSound(bl, sound);
        }
    }

    /**
     * bll_throw() — animasi benda terlempar dari sebuah benda ke petak
     * tujuan. Opcode 0x16, disiarkan ke SAMEAREA.
     *
     * <p>⚠️ Ladang [12] sengaja <b>nol</b>: itu yang membuat klien tidak
     * meninggalkan gambar barang di tanah setelah animasinya selesai. Paket
     * ini murni tampilan — barang yang benar-benar jatuh dibuat terpisah
     * lewat {@code dropItemXY}.</p>
     *
     * <p>Ikonnya memakai penambah <b>49152</b>, rentang grafik barang, sama
     * seperti ikon kustom barang lantai.</p>
     */
    public static void throwAnimation(org.rtk.map.data.BlockList from, int toX, int toY,
                                      int icon, int color, int action) {
        byte[] buf = new byte[32];
        buf[0] = (byte) 0xAA;
        putBE16(buf, 1, 0x1B);
        buf[3] = 0x16;
        buf[4] = 0x03;
        putBE32(buf, 5, (int) from.id);
        putBE16(buf, 9, icon + 49152);
        buf[11] = (byte) color;
        putBE32(buf, 12, 0);       // 0 = jangan tinggalkan gambar di tanah
        putBE16(buf, 16, from.x);
        putBE16(buf, 18, from.y);
        putBE16(buf, 20, toX);
        putBE16(buf, 22, toY);
        putBE32(buf, 24, 0);
        buf[28] = (byte) action;
        buf[29] = 0x00;
        sendToArea(from, buf, 30, true);
    }

    /**
     * clif_sendadditem() — isi satu slot inventaris di layar pemain.
     * Opcode 0x0F, panjangnya berubah-ubah menurut teksnya.
     *
     * <p>Jalur di balik {@code updateInv} (24x) dan setiap kali pemain
     * mendapat barang. Nomor slot dikirim <b>1-basis</b>.</p>
     *
     * <p><b>Dua string, dan keduanya berbeda peran:</b> yang pertama adalah
     * teks yang <b>dilihat pemain</b> — nama asli barang bila diukir, plus
     * hiasan menurut jenisnya ({@code "Apel (5)"}, {@code "Obor [12 jam]"},
     * {@code "[T3] Peta"}); yang kedua nama <b>jenis</b>-nya apa adanya,
     * dipakai klien untuk mencocokkan gambar dan tooltip.</p>
     *
     * <p>⚠️ <b>Dua penjaga yang MENGHAPUS barangnya, bukan sekadar melewati:</b>
     * barang ber-id di bawah 4, dan barang yang tidak ada di tabel `Items`,
     * dikosongkan dari inventaris pemain. Itu penyapu data rusak di C —
     * ditiru apa adanya, karena kalau tidak, barang hantu akan menumpuk di
     * slot yang tidak bisa dipakai.</p>
     *
     * <p>⚠️ Ketahanan hanya dikirim untuk jenis <b>3..17</b> (perlengkapan).
     * Jenis lain memakai ladang yang sama untuk penanda "bertumpuk".</p>
     */
    public static void sendAddItem(User sd, int slot) {
        Session s = sessionOf(sd);
        if (s == null || slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        org.rtk.common.mmo.Item it = sd.status.inventory.get(slot);
        var info = MapServer.itemDb.info(it.id);

        // Penyapu data rusak: barangnya dibuang, bukan dilewati.
        if (it.id < 4 || info.id() == 0) {
            sd.status.inventory.remove(slot);
            return;
        }

        String nama = it.realName != null && !it.realName.isEmpty()
                ? it.realName : info.display();
        String tampil;
        if (it.amount > 1) {
            tampil = nama + " (" + it.amount + ")";
        } else if (info.type() == org.rtk.map.data.ItemDb.ITM_SMOKE) {
            tampil = nama + " [" + it.dura + " " + info.text() + "]";
        } else if (info.type() == org.rtk.map.data.ItemDb.ITM_BAG
                || info.type() == org.rtk.map.data.ItemDb.ITM_QUIVER) {
            tampil = nama + " [" + it.dura + "]";
        } else if (info.type() == org.rtk.map.data.ItemDb.ITM_MAP) {
            tampil = "[T" + it.dura + "] " + nama;
        } else {
            tampil = nama;
        }

        byte[] teks = tampil.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] jenis = info.display().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x0F);
        s.wfifoB(5, slot + 1);

        if (it.customIcon != 0) {
            s.wfifoWBE(6, (int) (it.customIcon + 49152));
            s.wfifoB(8, (int) it.customIconColor);
        } else {
            s.wfifoWBE(6, info.look().icon());
            s.wfifoB(8, info.look().iconColor());
        }

        s.wfifoB(9, teks.length);
        s.wfifoBytes(10, teks);
        int len = teks.length + 10;

        s.wfifoB(len, jenis.length);
        s.wfifoBytes(len + 1, jenis);
        len += jenis.length + 1;

        s.wfifoLBE(len, it.amount);
        len += 4;

        boolean perlengkapan = info.type() >= org.rtk.map.data.ItemDb.ITM_EQUIP_MIN
                && info.type() <= org.rtk.map.data.ItemDb.ITM_EQUIP_MAX;
        if (perlengkapan) {
            s.wfifoB(len, 0);
            s.wfifoLBE(len + 1, it.dura);
        } else {
            s.wfifoB(len, MapServer.itemDb.stackAmountOf(it.id) > 1 ? 1 : 0);
            s.wfifoLBE(len + 1, 0);
        }
        // Perlindungan: yang TERBESAR antara milik barangnya dan bawaan
        // jenisnya yang menang — dua baris di C yang saling menimpa.
        s.wfifoB(len + 5, (int) Math.max(it.protectedFlag,
                MapServer.itemDb.protectedOf(it.id)));
        len += 6;

        String pemilik = it.owner != 0 ? MapServer.charName(it.owner) : "";
        if (!pemilik.isEmpty()) {
            byte[] o = pemilik.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            s.wfifoB(len, o.length);
            s.wfifoBytes(len + 1, o);
            len += o.length + 1;
        } else {
            s.wfifoB(len, 0);
            len += 1;
        }
        s.wfifoWBE(len, 0);
        len += 2;
        s.wfifoB(len, 0);
        len += 1;

        s.wfifoWBE(1, len);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_senddelitem() — kosongkan satu slot inventaris di layar pemain.
     * Opcode 0x10, panjang tetap.
     *
     * <p>⚠️ <b>Paket ini juga mengosongkan slotnya di server</b>, bukan
     * sekadar memberi tahu klien — begitu di C, dan skrip mengandalkannya.</p>
     *
     * @param type alasan hilangnya, yang menentukan kalimat di layar klien:
     *             0 dibuang, 1 dijatuhkan, 2 dimakan, 3 dihisap, 4 dilempar,
     *             5 ditembakkan, 6 dipakai, 7 dikirim, 8 lapuk, 9 diberikan,
     *             10 dijual, 11 dicabut, 12 nama barangnya, 13 patah
     */
    public static void sendDelItem(User sd, int slot, int type) {
        if (slot >= 0 && slot < sd.status.inventory.size()) {
            sd.status.inventory.remove(slot);
        }
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x10, 0x06);
        s.wfifoB(5, slot + 1);
        s.wfifoB(6, type);
        s.wfifoB(7, 0);
        s.wfifoB(8, 0);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendxychange() — geser kamera klien tanpa memindahkan pemain.
     * Opcode 0x04, tata letak sama dengan {@link #sendXy}.
     *
     * <p>⚠️ <b>Offset kamera dijepit dengan cara yang aneh dan itu
     * disengaja:</b> bila kamera akan menembus tepi peta, nilainya
     * <b>dinaikkan atau diturunkan satu</b>, bukan dipotong ke batas. Angka
     * 16 dan 14 adalah lebar dan tinggi layar dikurangi satu.</p>
     */
    public static void sendXyChange(User sd, int dx, int dy) {
        Session s = sessionOf(sd);
        MapData map = MapServer.world.get(sd.m);
        if (s == null || map == null) {
            return;
        }
        s.wfifoB(0, 0xAA);
        s.wfifoWBE(1, 0x0A);
        s.wfifoB(3, 0x04);
        s.wfifoWBE(5, sd.x);
        s.wfifoWBE(7, sd.y);

        int kx = dx;
        if (sd.x - kx < 0) {
            kx--;
        } else if (sd.x + (16 - kx) >= map.xs) {
            kx++;
        }
        s.wfifoWBE(9, kx);
        sd.viewX = kx;

        int ky = dy;
        if (sd.y - ky < 0) {
            ky--;
        } else if (sd.y + (14 - ky) >= map.ys) {
            ky++;
        }
        s.wfifoWBE(11, ky);
        sd.viewY = ky;

        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_destroyold() — suruh klien membuang benda yang masih tergambar
     * dari gambaran sebelumnya. Opcode 0x58 dengan [5] = 0.
     *
     * <p>Opcode yang sama dipakai {@link #guiText}; yang membedakan hanya
     * ladang [5] — 0 membersihkan layar, 6 menampilkan teks.</p>
     */
    public static void destroyOld(User sd) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x58, 3);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, 0x00);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_guitextsd() — teks besar melayang di tengah layar pemain
     * (pengumuman event). Opcode 0x58 dengan [5] = 6.
     *
     * <p>⚠️ Ladang panjangnya ditulis {@code 8 + panjang + 3} — sudah
     * memasukkan 3 byte indeks kunci yang biasanya ditambahkan
     * {@code setPacketIndexes} sesudahnya. Jadi paket ini <b>menghitung
     * ganda</b> dibanding paket lain. Itu ada di C dan ditiru apa adanya;
     * lihat Peringatan #17 soal jebakan panjang paket.</p>
     */
    public static void guiText(User sd, String text) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        byte[] raw = (text == null ? "" : text)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x58);
        s.wfifoB(5, 0x06);
        s.wfifoWBE(6, raw.length);
        s.wfifoBytes(8, raw);
        s.wfifoWBE(1, 8 + raw.length + 3);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_paperpopup() — kotak "kertas" berisi teks panjang, ukurannya
     * ditentukan skrip. Opcode 0x35.
     */
    public static void paperPopup(User sd, String text, int width, int height) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        byte[] raw = (text == null ? "" : text)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        head(s, 0x35, raw.length + 11);
        s.wfifoB(5, 0);
        s.wfifoB(6, width);
        s.wfifoB(7, height);
        s.wfifoB(8, 0);
        s.wfifoWBE(9, raw.length);
        s.wfifoBytes(11, raw);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendurl() — buka alamat web di klien. Opcode 0x66.
     *
     * @param type 0 = peramban dalam permainan, 1 = buka peramban luar lalu
     *             tutup klien, 2 = jendela sembul
     */
    public static void sendUrl(User sd, int type, String url) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        byte[] raw = (url == null ? "" : url)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x66);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, type);
        s.wfifoWBE(6, raw.length);
        s.wfifoBytes(8, raw);
        s.wfifoWBE(1, raw.length + 8);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_send_timer() — pasang penghitung waktu di layar pemain.
     * Opcode 0x67, panjang isi 7.
     *
     * @param type   1 dan 2 menghitung mundur; nilai lain hanya menampilkan
     * @param length lama dalam <b>detik</b>
     */
    public static void sendTimer(User sd, int type, long length) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x67, 7);
        s.wfifoB(5, type);
        s.wfifoLBE(6, (int) length);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendscriptsay() — pemain berbicara, dengan <b>namanya sendiri
     * disisipkan di depan</b>. Opcode 0x0D.
     *
     * <p>Berbeda dari {@link #speak}, yang mengirim teks apa adanya atas nama
     * sebuah benda. Di sini server yang menyusun {@code "Nama: pesan"}.</p>
     *
     * <p>⚠️ <b>Ragam 1 adalah berteriak</b>, dan tiga hal berubah sekaligus:
     * pemisahnya {@code '!'} bukan {@code ':'}, dan siarannya ke
     * <b>seluruh peta</b>, bukan hanya area pandang.</p>
     *
     * <p>Ragam &gt;= 10 menandai bahasa dan menambahkan awalan seperti
     * {@code "EN[Nama]"}; itu <b>belum</b> diport — daftar bahasanya ada di
     * C tapi tidak satu pun skrip memakainya.</p>
     */
    public static void scriptSay(User sd, String text, int type) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        String msg = text == null ? "" : text;
        byte[] nama = sd.status.name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] raw = msg.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        byte[] buf = new byte[16 + nama.length + raw.length];
        buf[0] = (byte) 0xAA;
        putBE16(buf, 1, 10 + nama.length + raw.length);
        buf[3] = 0x0D;
        buf[5] = (byte) type;
        putBE32(buf, 6, (int) sd.status.id);
        buf[10] = (byte) (nama.length + raw.length + 2);
        System.arraycopy(nama, 0, buf, 11, nama.length);
        buf[11 + nama.length] = (byte) (type == 1 ? '!' : ':');
        buf[12 + nama.length] = (byte) ' ';
        System.arraycopy(raw, 0, buf, 13 + nama.length, raw.length);

        if (type == 1) {
            sendToMap(sd, buf, buf.length);
        } else {
            sendToArea(sd, buf, buf.length, true);
        }
    }

    /**
     * Bagian gambar-ulang {@code pcl_changeview()}: seluruh isi area pandang
     * digambar ulang di layar pemain.
     *
     * <p>Urutannya diambil dari C dan <b>bukan sembarang</b>: benda
     * bukan-karakter dulu (satu paket berkelompok), lalu sisa gambar lama
     * dibuang, baru pemain, NPC, dan mob digambar. Membalik urutannya
     * membuat benda yang baru digambar ikut terhapus {@code destroyOld}.</p>
     */
    public static void redrawSurroundings(User sd) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        java.util.List<org.rtk.map.data.BlockList> benda = new java.util.ArrayList<>();
        map.foreachInArea(sd.x, sd.y, null, bl -> {
            if (!(bl instanceof User)) {
                benda.add(bl);
            }
        });
        objectLookBatch(sd, benda);
        destroyOld(sd);

        map.foreachInArea(sd.x, sd.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User other && other != sd) {
                sendCharLook(sd, other);
                sendCharLook(other, sd);
            }
        });
        map.foreachInArea(sd.x, sd.y, null, bl -> {
            if (bl instanceof Npc nd) {
                sendNpcLook(sd, nd);
            } else if (bl instanceof Mob mb) {
                sendMobLook(sd, mb);
            }
        });
    }

    /** clif_send(..., SAMEMAP): siarkan ke seluruh pemain di peta yang sama. */
    static void sendToMap(org.rtk.map.data.BlockList from, byte[] packet, int len) {
        for (User to : MapServer.onlineChars.values()) {
            if (to.m != from.m) {
                continue;
            }
            Session ts = MapServer.net.session(to.fd);
            if (ts == null) {
                continue;
            }
            ts.wfifoBytes(0, packet, len);
            ts.wfifoSet(encrypt(ts, to));
        }
    }

    /** Satu baris pada daftar isi papan. */
    public record BoardEntry(int color, int postId, int titleId,
                             int month, int day, String author, String topic) {
    }

    /**
     * intif_parse_showpostresponse() bagian kliennya — daftar isi papan.
     * Opcode 0x31.
     *
     * <p>⚠️ Opcode 0x31 dipakai <b>dua hal</b>: daftar ini dan formulir
     * pertanyaan ({@link #sendBoardQuestions}, penanda 0x09 di [5]). Yang
     * membedakan hanya ladang itu.</p>
     *
     * <p>Nama penulis digabung dengan <b>gelarnya</b> bila papannya bukan
     * kotak surat dan kirimannya memang punya gelar: {@code "Prajurit Budi"}.
     * Gelar diambil dari tabel {@code BoardTitles}.</p>
     *
     * <p>⚠️ Perhitungan offsetnya di C berjalan lewat satu penghitung
     * {@code len} yang <b>maju tidak seragam</b> — bertambah panjang nama
     * + 4 setelah nama, lalu 2 setelah tanggal. Ditiru apa adanya karena
     * itulah yang menentukan letak tiap ladang.</p>
     */
    public static void sendBoardList(User sd, int board, int flags1, int flags2,
                                     java.util.List<BoardEntry> isi) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        headSeq(s, 0x31, 0);
        s.wfifoB(5, flags2);
        s.wfifoB(6, flags1);
        s.wfifoWBE(7, board);

        byte[] namaPapan = MapServer.boardDb.nameOf(board)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        s.wfifoB(9, namaPapan.length);
        s.wfifoBytes(10, namaPapan);
        int len = namaPapan.length + 1;
        int pos = len + 9;

        if (isi.isEmpty()) {
            s.wfifoB(pos, 0);
        } else {
            s.wfifoB(pos, isi.size());
            len++;
            for (BoardEntry e : isi) {
                s.wfifoB(len + 9, e.color());
                s.wfifoWBE(len + 10, e.postId());

                String penulis = board != 0 && e.titleId() != 0
                        ? MapServer.boardDb.titleOf(e.titleId()) + " " + e.author()
                        : e.author();
                byte[] pb = penulis.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                s.wfifoB(len + 12, pb.length);
                s.wfifoBytes(len + 13, pb);
                len += pb.length + 4;

                s.wfifoB(len + 9, e.month());
                s.wfifoB(len + 10, e.day());
                len += 2;

                byte[] jb = e.topic().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                s.wfifoB(len + 9, jb.length);
                s.wfifoBytes(len + 10, jb);
                len += jb.length + 1;
            }
        }
        s.wfifoWBE(1, len + 9);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_mapselect() — daftar peta yang bisa dipilih pemain (peta rumah,
     * teleporter). Opcode 0x2E.
     *
     * <p>⚠️ <b>Ekor tiap entri aneh dan disengaja:</b> setelah koordinat,
     * C menulis jumlah entri lagi lalu <b>deretan 0..n-1</b> — di dalam
     * perulangan, jadi seluruh deret itu berulang untuk <b>setiap</b> peta.
     * Isinya tidak pernah dipakai server, tetapi klien mengharapkan
     * panjangnya. Ditiru apa adanya.</p>
     */
    public static void mapSelection(User sd, String title,
                                    java.util.List<Integer> x0,
                                    java.util.List<Integer> y0,
                                    java.util.List<String> names,
                                    java.util.List<Integer> ids,
                                    java.util.List<Integer> x1,
                                    java.util.List<Integer> y1) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        byte[] judul = (title == null ? "" : title)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int n = names.size();

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x2E);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, judul.length);
        s.wfifoBytes(6, judul);
        int len = judul.length + 1;
        s.wfifoB(len + 5, n);
        s.wfifoB(len + 6, 0);
        len += 2;

        for (int i = 0; i < n; i++) {
            s.wfifoWBE(len + 5, at(x0, i));
            s.wfifoWBE(len + 7, at(y0, i));
            len += 4;
            byte[] nama = names.get(i)
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            s.wfifoB(len + 5, nama.length);
            s.wfifoBytes(len + 6, nama);
            len += nama.length + 1;
            s.wfifoLBE(len + 5, at(ids, i));
            s.wfifoWBE(len + 9, at(x1, i));
            s.wfifoWBE(len + 11, at(y1, i));
            len += 8;
            s.wfifoWBE(len + 5, n);
            len += 2;
            for (int y = 0; y < n; y++) {
                s.wfifoWBE(len + 5, y);
                len += 2;
            }
        }
        s.wfifoWBE(1, len + 3);
        s.wfifoSet(encrypt(s, sd));
    }

    private static int at(java.util.List<Integer> l, int i) {
        return i < l.size() ? l.get(i) : 0;
    }

    /**
     * clif_sendpowerboard() — daftar pemain di peta ini beserta "power
     * rating"-nya. Opcode 0x46.
     *
     * <p>⚠️ <b>Namanya menyesatkan: ini bukan papan pesan.</b> Meski
     * bindingnya bernama {@code powerBoard} dan berada di kelompok yang sama
     * dengan {@code showBoard}, isinya daftar pemain online — tidak
     * menyentuh tabel {@code Boards} sama sekali.</p>
     *
     * <p>Dua penyaring yang ditiru: pemain ber-<b>path 0 atau 50</b>
     * dilewati (belum berjalur / jalur khusus), dan path 5 dilaporkan
     * sebagai 2. "Power rating" hanyalah {@code baseHp + baseMp}.</p>
     */
    public static void sendPowerBoard(User sd) {
        Session s = sessionOf(sd);
        MapData map = MapServer.world.get(sd.m);
        if (s == null || map == null) {
            return;
        }
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x46);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, 1);

        int[] len = new int[2];   // [0] byte terpakai, [1] jumlah pemain
        for (User to : MapServer.onlineChars.values()) {
            if (to.m != sd.m) {
                continue;
            }
            int path = MapServer.classDb.pathOf(to.status.charClass);
            if (path == 5) {
                path = 2;
            }
            if (path == 0 || path == 50) {
                continue;
            }
            byte[] nama = to.status.name
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            s.wfifoLBE(len[0] + 8, (int) to.id);
            s.wfifoB(len[0] + 12, path);
            s.wfifoLBE(len[0] + 13, (int) (to.status.baseHp + to.status.baseMp));
            s.wfifoB(len[0] + 17, to.status.armorColor);
            s.wfifoB(len[0] + 18, nama.length);
            s.wfifoBytes(len[0] + 19, nama);
            len[0] += nama.length + 11;
            len[1]++;
        }
        s.wfifoWBE(6, len[1]);
        s.wfifoWBE(1, len[0] + 5);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_sendBoardQuestionaire() — formulir bertanya di papan: daftar
     * pertanyaan dengan jumlah baris jawaban masing-masing. Opcode 0x31
     * dengan penanda 0x09 di [5].
     *
     * <p>Opcode 0x31 dipakai <b>dua hal berbeda</b>: penanda 0x09 formulir
     * ini, sedangkan daftar isi papan memakai penanda lain. Yang membedakan
     * hanya ladang [5].</p>
     *
     * <p>Byte tetap 1/2 di antara judul dan pertanyaan, serta 0x6B di
     * ekornya, diambil apa adanya dari C — artinya tidak terdokumentasi.</p>
     */
    public static void sendBoardQuestions(User sd, java.util.List<String> headers,
                                          java.util.List<String> questions,
                                          java.util.List<Integer> inputLines) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        int count = headers.size();
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x31);
        s.wfifoB(5, 0x09);
        s.wfifoB(6, count);

        int len = 7;
        for (int i = 0; i < count; i++) {
            byte[] judul = headers.get(i)
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            byte[] tanya = (i < questions.size() ? questions.get(i) : "")
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            int baris = i < inputLines.size() ? inputLines.get(i) : 1;

            s.wfifoB(len, judul.length);
            len += 1;
            s.wfifoBytes(len, judul);
            len += judul.length;
            s.wfifoB(len, 1);
            s.wfifoB(len + 1, 2);
            len += 2;
            s.wfifoB(len, baris);
            len += 1;
            s.wfifoB(len, tanya.length);
            len += 1;
            s.wfifoBytes(len, tanya);
            len += tanya.length;
            s.wfifoB(len, 1);
            len += 1;
        }
        s.wfifoB(len, 0);
        s.wfifoB(len + 1, 0x6B);
        len += 2;

        s.wfifoWBE(1, len + 3);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_playsound() — mainkan bunyi di sekitar sebuah benda.
     * Opcode 0x19, panjang isi 0x14, disiarkan ke SAMEAREA.
     *
     * <p>Ladang tetapnya diambil apa adanya dari C; artinya tidak
     * terdokumentasi di sumber, jadi jangan "dirapikan".</p>
     */
    public static void playSound(org.rtk.map.data.BlockList src, int sound) {
        MapData map = MapServer.world.get(src.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(src.x, src.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (!(bl instanceof User to)) {
                return;
            }
            Session ts = MapServer.net.session(to.fd);
            if (ts == null) {
                return;
            }
            ts.wfifoB(0, 0xAA);
            ts.wfifoWBE(1, 0x14);
            ts.wfifoB(3, 0x19);
            ts.wfifoB(4, 0x03);
            ts.wfifoWBE(5, 3);
            ts.wfifoWBE(7, sound);
            ts.wfifoB(9, 100);
            ts.wfifoWBE(10, 4);
            ts.wfifoLBE(12, (int) src.id);
            ts.wfifoB(16, 1);
            ts.wfifoB(17, 0);
            ts.wfifoB(18, 2);
            ts.wfifoB(19, 2);
            ts.wfifoWBE(20, 4);
            ts.wfifoB(22, 0);
            ts.wfifoSet(encrypt(ts, to));
        });
    }

    /**
     * clif_removespell() — hapus satu mantra dari buku mantra klien.
     * Opcode 0x18, panjang isi 3.
     *
     * <p>⚠️ Nomor slot dikirim <b>+1</b> (klien memakai indeks mulai 1).</p>
     */
    public static void removeSpell(User sd, int slot) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        head(s, 0x18, 3);
        s.wfifoB(4, 0x03);
        s.wfifoB(5, slot + 1);
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
        headSeq(s, 0x51, 5);
        s.wfifoB(5, flag);
        s.wfifoB(6, 0);
        s.wfifoB(7, 0);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * Tarik pemain kembali ke posisi menurut server, lalu buka kuncinya.
     * Urutannya (kunci, kirim posisi, buka) ditiru dari `clif_parsewalk`.
     */
    static void snapBack(User sd) {
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
        sendToArea(from, packet, len, false);
    }

    /**
     * clif_send(..., AREA) / (..., AREA_WOS): siarkan ke pemain di area
     * pandang sebuah benda. Sumbernya <b>benda apa pun</b> — NPC dan mob
     * juga menyiarkan, bukan hanya pemain.
     *
     * @param includeSelf true = AREA (sumber ikut menerima bila ia pemain),
     *                    false = AREA_WOS (sumber dilewati)
     */
    static void sendToArea(org.rtk.map.data.BlockList from, byte[] packet,
                           int len, boolean includeSelf) {
        MapData map = MapServer.world.get(from.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(from.x, from.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (!(bl instanceof User to)) {
                return;
            }
            if (!includeSelf && bl == from) {
                return;
            }
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
        if (s == null) {
            return;
        }
        int opcode = s.rfifoB(3) & 0xFF;
        int direction = s.rfifoB(5) & 0xFF;
        int claimX = s.rfifoWBE(8);
        int claimY = s.rfifoWBE(10);

        // Hanya paket 0x06 yang menitipkan permintaan gambar ulang; 0x32
        // tidak. Lihat catatan kebocoran di ClientCommands.playerWalks.
        ClientCommands.RedrawRequest redraw = null;
        if (opcode == 0x06) {
            redraw = new ClientCommands.RedrawRequest(
                    s.rfifoWBE(12), s.rfifoWBE(14),
                    s.rfifoB(16) & 0xFF, s.rfifoB(17) & 0xFF, s.rfifoWBE(18));
        }
        MapServer.commands.playerWalks(sd, direction, claimX, claimY, redraw);
    }

    /**
     * Cabang gambar ulang pada paket langkah 0x06: kirim petak petanya,
     * lalu gambarkan kembali benda-benda di dalam petak itu.
     *
     * <p>Urutannya ditiru dari {@code clif_parsewalk}: peta dulu, lalu
     * pemain, NPC, mob, dan terakhir menampilkan diri kita kepada pemain
     * lain di petak yang sama.</p>
     */
    static void redrawArea(User sd, MapData map,
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
        map.foreachInBlock(x0, y0, x1, y1, org.rtk.map.data.BlockList.Type.MOB, bl -> {
            if (bl instanceof Mob mb) {
                sendMobLook(sd, mb);
            }
        });

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
    static void checkWarpTile(User sd, MapData map) {
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

    /**
     * clif_sendside(): beri tahu sekitar bahwa sebuah benda berganti arah
     * hadap — opcode 0x11.
     *
     * <p>Dipakai skrip AI NPC setiap kali NPC berbelok, jadi ini salah satu
     * kait yang paling sering dipanggil. Perhatikan bedanya: untuk
     * <b>pemain</b> paket dikirim ke seluruh area <b>termasuk dirinya</b>
     * (AREA), sedangkan NPC dan mob memakai AREA_WOS — mereka tidak perlu
     * memberi tahu diri sendiri.</p>
     */
    public static void sendSide(org.rtk.map.data.BlockList bl) {
        int side;
        if (bl instanceof User u) {
            side = u.status.side;
        } else if (bl instanceof Npc nd) {
            side = nd.side;
        } else if (bl instanceof Mob mb) {
            side = mb.side;
        } else {
            return;
        }

        byte[] buf = new byte[32];
        buf[0] = (byte) 0xAA;
        buf[1] = 0x00;
        buf[2] = 0x08;
        buf[3] = 0x11;
        putBE32(buf, 5, (int) bl.id);
        buf[9] = (byte) side;
        buf[10] = 0;

        // C menyusun 32 byte penuh (`clif_send(buf, 32, ...)`); yang
        // benar-benar terkirim dipotong `encrypt()` menurut ladang panjang
        // di [1..2] — 0x0008 + 3 = 14 byte.
        // Pemain memakai AREA (ikut menerima), NPC & mob AREA_WOS.
        sendToArea(bl, buf, buf.length, bl instanceof User);
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
        if (s == null) {
            return;
        }
        MapServer.commands.playerClicks(sd, s.rfifoLBE(6) & 0xFFFFFFFFL);
    }

    /**
     * Jalankan skrip {@code click} milik NPC.
     *
     * <p>Penjaga karma ditiru dari C: pemain berkarma sangat buruk ditolak
     * bicara oleh hampir semua NPC — kecuali NPC F1 dan NPC totem, karena
     * keduanya jalur keluar yang tidak boleh ikut tertutup.</p>
     */
    static void clickNpc(User sd, Npc nd) {
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
        if (s == null) {
            return;
        }
        int ragam = s.rfifoB(5) & 0xFF;
        ClientCommands.Answer jawab = switch (ragam) {
            // clif_parsemenu: id di [6] BE32, pilihan di [10] BE16
            case 1 -> ClientCommands.Answer.choice(s.rfifoWBE(10));
            // clif_parseinput: panjang di [12], teks di [13]. Di C ada
            // string kedua setelahnya — belum dipakai primitif mana pun.
            case 3 -> ClientCommands.Answer.text(teks(s, 12, 13));
            // clif_parsebuy: NAMA barang, bukan indeksnya
            case 2 -> ClientCommands.Answer.itemName(teks(s, 12, 13));
            // clif_parsesell: nomor slot inventaris
            case 4 -> ClientCommands.Answer.slot(s.rfifoB(12) & 0xFF);
            // 0 dan lainnya: pemain membatalkan
            default -> null;
        };
        MapServer.commands.playerAnswersMenu(sd, jawab);
    }

    /** Baca string berprefiks panjang: panjang di posLen, isi mulai posIsi. */
    private static String teks(Session s, int posLen, int posIsi) {
        int len = s.rfifoB(posLen) & 0xFF;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) (s.rfifoB(posIsi + i) & 0xFF));
        }
        return sb.toString();
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
        if (s == null) {
            return;
        }
        int ragam = s.rfifoB(5) & 0xFF;
        int pilihan = s.rfifoB(13) & 0xFF;

        ClientCommands.Answer jawab;
        switch (ragam) {
            case 0x01 -> jawab = ClientCommands.Answer.step(pilihan);
            case 0x02 -> jawab = ClientCommands.Answer.choice(s.rfifoB(15) & 0xFF);
            case 0x04 -> {
                // [13] != 2 berarti pemain menutup kotak isian, bukan menjawab
                jawab = pilihan != 0x02 ? null
                        : ClientCommands.Answer.text(teks(s, 15, 16));
            }
            default -> {
                log.debug("[CLIF] ragam dialog 0x{} belum ditangani",
                        String.format("%02X", ragam));
                return;
            }
        }
        MapServer.commands.playerAnswersDialog(sd, jawab);
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
        map.foreachInArea(sd.x, sd.y, org.rtk.map.data.BlockList.Type.MOB, bl -> {
            if (bl instanceof Mob mb) {
                sendMobLook(sd, mb);
            }
        });
    }

    /**
     * clif_cmoblook_sub(): gambarkan satu mob di layar {@code sd} —
     * opcode 0x33, sama seperti pemain dan NPC.
     *
     * <p>Hanya mob ber-{@code MobIsChar == 1} yang dikirim, dan mob yang
     * sudah mati dilewati. Tata letaknya mengikuti paket NPC; perbedaannya
     * mob tidak punya perlengkapan, jadi slot-slotnya selalu kosong.</p>
     */
    public static void sendMobLook(User sd, Mob mb) {
        Session s = sessionOf(sd);
        if (s == null || mb.m != sd.m || !mb.drawsAsCharacter()
                || mb.state == org.rtk.map.MobData.MOB_DEAD) {
            return;
        }
        var d = mb.data;

        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x33);
        s.wfifoWBE(5, mb.x);
        s.wfifoWBE(7, mb.y);
        s.wfifoB(9, mb.side);
        s.wfifoLBE(10, (int) mb.id);

        if (mb.charState < 4) {
            s.wfifoWBE(14, d.sex);
        } else {
            s.wfifoB(14, 1);
            s.wfifoB(15, 15);
        }

        s.wfifoB(16, mb.charState == 2 && sd.isGm() ? 5 : mb.charState);
        s.wfifoB(19, 80);

        if (mb.charState == 3) {
            s.wfifoWBE(17, mb.look);
        } else if (mb.charState == 4) {
            s.wfifoWBE(17, mb.look + 32768);
            s.wfifoB(19, mb.lookColor);
        } else {
            s.wfifoWBE(17, 0);
        }

        s.wfifoB(20, 0);
        s.wfifoB(21, d.face);
        s.wfifoB(22, d.hair);
        s.wfifoB(23, d.hairColor);
        s.wfifoB(24, d.faceColor);
        s.wfifoB(25, d.skinColor);

        // mob tidak berperlengkapan: zirah jatuh ke grafik dasar jenis
        // kelamin, slot lain diisi penanda kosong
        s.wfifoWBE(26, d.sex);
        s.wfifoB(28, 0);
        kosong(s, 29, 31);
        kosong(s, 32, 34);
        s.wfifoB(35, 0);
        s.wfifoWBE(36, 0xFF);
        kosong(s, 38, 40);
        kosong(s, 41, 43);
        kosong(s, 44, 46);
        s.wfifoWBE(47, 0xFFFF);
        s.wfifoB(49, 0xFF);
        kosong(s, 50, 52);
        s.wfifoWBE(53, d.sex);
        s.wfifoB(55, 0);

        s.wfifoB(56, 0);
        s.wfifoB(57, 128);
        s.wfifoB(58, 0);

        String nama = mb.displayName();
        int len = Math.min(nama.length(), 255);
        s.wfifoB(59, len);
        s.wfifoStringRaw(60, nama.substring(0, len));

        s.wfifoWBE(1, len + 60);
        s.wfifoSet(encrypt(s, sd));
    }

    /** Slot perlengkapan kosong: id 0xFFFF, warna 0. */
    private static void kosong(Session s, int idOff, int colorOff) {
        s.wfifoWBE(idOff, 0xFFFF);
        s.wfifoB(colorOff, 0);
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

        int len = charBody(s, viewer, who, 5);
        s.wfifoWBE(1, len + 60 + 3);
        s.wfifoSet(encrypt(s, viewer));
    }

    /**
     * Badan gambar pemain — bagian yang <b>sama persis</b> pada paket 0x33
     * (menggambar pemain di petaknya) dan 0x1D (memperbarui wujudnya di
     * tempat). Isinya identik di C; yang berbeda hanya asal offsetnya, 5 byte,
     * karena 0x33 membawa x/y/side lebih dulu sedangkan 0x1D tidak.
     *
     * <p>⚠️ Yang <b>tidak</b> sama: pada {@code state == 4} (menyamar penuh)
     * paket 0x1D memakai tata letak pendek sendiri dan tidak memanggil badan
     * ini sama sekali. Lihat {@link #updateState}.</p>
     *
     * @param base offset ladang pertama badan ini — 5 untuk 0x33, 0 untuk 0x1D
     * @return panjang nama yang ditulis; dipakai memilih ladang panjang paket
     */
    private static int charBody(Session s, User viewer, User who, int base) {
        var st = who.status;
        if (st.state < 4) {
            s.wfifoWBE(base + 9, st.sex);
        } else {
            s.wfifoB(base + 9, 1);
            s.wfifoB(base + 10, 15);
        }

        boolean stealth = (who.optFlags & User.OPT_STEALTH) != 0;
        if ((st.state == 2 || stealth) && who.id != viewer.id && viewer.isGm()) {
            s.wfifoB(base + 11, 5);            // GM tetap melihat yang tak terlihat
        } else {
            s.wfifoB(base + 11, st.state);
        }
        if (stealth && st.state == 0 && (!viewer.isGm() || who.id == viewer.id)) {
            s.wfifoB(base + 11, 2);
        }

        s.wfifoB(base + 14, who.speed);
        if (st.state == 3) {
            s.wfifoWBE(base + 12, st.disguise);
        } else if (st.state == 4) {
            s.wfifoWBE(base + 12, st.disguise + 32768);
            s.wfifoB(base + 14, st.disguiseColor);
        } else {
            s.wfifoWBE(base + 12, 0);
        }

        s.wfifoB(base + 15, 0);
        s.wfifoB(base + 16, st.face);
        s.wfifoB(base + 17, st.hair);
        s.wfifoB(base + 18, st.hairColor);
        s.wfifoB(base + 19, st.faceColor);
        s.wfifoB(base + 20, st.skinColor);

        // zirah
        var armor = st.equipAt(Equip.ARMOR);
        if (armor == null) {
            s.wfifoWBE(base + 21, st.sex);
            s.wfifoB(base + 23, 0);
        } else {
            s.wfifoWBE(base + 21, armor.customLook != 0
                    ? (int) armor.customLook : MapServer.itemDb.lookOf(armor.id));
            s.wfifoB(base + 23, st.armorColor > 0 ? st.armorColor
                    : (armor.customLook != 0 ? (int) armor.customLookColor
                                             : MapServer.itemDb.lookColorOf(armor.id)));
        }
        // mantel menimpa zirah di offset yang sama
        var coat = st.equipAt(Equip.COAT);
        if (coat != null) {
            s.wfifoWBE(base + 21, MapServer.itemDb.lookOf(coat.id));
            s.wfifoB(base + 23, st.armorColor > 0 ? st.armorColor
                    : MapServer.itemDb.lookColorOf(coat.id));
        }

        pcSlot(s, st, Equip.WEAP, base + 24, base + 26, true);
        pcSlot(s, st, Equip.SHIELD, base + 27, base + 29, true);

        // helm bisa disembunyikan lewat setelan pemain (FLAG_HELM)
        var helm = st.equipAt(Equip.HELM);
        boolean helmOn = helm != null && (st.settingFlags & FLAG_HELM) != 0
                && MapServer.itemDb.lookOf(helm.id) != -1;
        if (!helmOn) {
            s.wfifoB(base + 30, 0);
            s.wfifoWBE(base + 31, 0xFFFF);
        } else {
            s.wfifoB(base + 30, 1);
            if (helm.customLook != 0) {
                s.wfifoB(base + 31, (int) helm.customLook);
                s.wfifoB(base + 32, (int) helm.customLookColor);
            } else {
                s.wfifoB(base + 31, MapServer.itemDb.lookOf(helm.id));
                s.wfifoB(base + 32, MapServer.itemDb.lookColorOf(helm.id));
            }
        }

        pcSlot(s, st, Equip.FACEACC, base + 33, base + 35, false);

        var crown = st.equipAt(Equip.CROWN);
        if (crown == null) {
            s.wfifoWBE(base + 36, 0xFFFF);
            s.wfifoB(base + 38, 0);
        } else {
            s.wfifoB(base + 30, 0xFF);   // di paket pemain nilainya 0xFF, bukan 0
            s.wfifoWBE(base + 36, crown.customLook != 0
                    ? (int) crown.customLook : MapServer.itemDb.lookOf(crown.id));
            s.wfifoB(base + 38, crown.customLook != 0
                    ? (int) crown.customLookColor : MapServer.itemDb.lookColorOf(crown.id));
        }

        pcSlot(s, st, Equip.FACEACCTWO, base + 39, base + 41, false);

        var mantle = st.equipAt(Equip.MANTLE);
        if (mantle == null) {
            s.wfifoWBE(base + 42, 0xFFFF);
            s.wfifoB(base + 44, 0xFF);
        } else {
            s.wfifoWBE(base + 42, MapServer.itemDb.lookOf(mantle.id));
            s.wfifoB(base + 44, MapServer.itemDb.lookColorOf(mantle.id));
        }

        var neck = st.equipAt(Equip.NECKLACE);
        boolean neckOn = neck != null && (st.settingFlags & FLAG_NECKLACE) != 0
                && MapServer.itemDb.lookOf(neck.id) != -1;
        if (!neckOn) {
            s.wfifoWBE(base + 45, 0xFFFF);
            s.wfifoB(base + 47, 0);
        } else {
            s.wfifoWBE(base + 45, MapServer.itemDb.lookOf(neck.id));
            s.wfifoB(base + 47, MapServer.itemDb.lookColorOf(neck.id));
        }

        var boots = st.equipAt(Equip.BOOTS);
        if (boots == null) {
            s.wfifoWBE(base + 48, st.sex);
            s.wfifoB(base + 50, 0);
        } else {
            s.wfifoWBE(base + 48, boots.customLook != 0
                    ? (int) boots.customLook : MapServer.itemDb.lookOf(boots.id));
            s.wfifoB(base + 50, boots.customLook != 0
                    ? (int) boots.customLookColor : MapServer.itemDb.lookColorOf(boots.id));
        }

        s.wfifoB(base + 51, 0);    // warna nama
        s.wfifoB(base + 52, 128);  // warna garis tepi; 128 = hitam
        s.wfifoB(base + 53, 0);    // 1 = PK, 2 = segrup, 3 = seklan

        if (viewer.status.clan == st.clan && st.clan > 0 && viewer.status.id != st.id) {
            s.wfifoB(base + 53, 3);
        }
        if (st.pk > 0) {
            s.wfifoB(base + 53, 1);
        }

        int len;
        String nama = st.name == null ? "" : st.name;
        if (st.state != 2 && st.state != 5) {
            len = Math.min(nama.length(), 255);
            s.wfifoB(base + 54, len);
            s.wfifoStringRaw(base + 55, nama.substring(0, len));
        } else {
            s.wfifoB(base + 54, 0);
            len = 0;
        }
        return len;
    }

    /**
     * clif_updatestate(): gambar ulang wujud pemain {@code who} di layar
     * {@code viewer} <b>tanpa memindahkannya</b>. Opcode 0x1D.
     *
     * <p>Isinya sama dengan paket 0x33 (lihat {@link #charBody}) hanya tanpa
     * x/y/side di depan — dipakai saat pemain menyamar, menghilang, mati jadi
     * hantu, atau berganti perlengkapan.</p>
     *
     * <p>⚠️ Pada {@code state == 4} paket ini memakai <b>tata letak pendek
     * sendiri</b>: hanya wujud samaran dan nama, panjang {@code len + 13}.
     * Paket 0x33 tidak punya cabang itu — di sana state 4 tetap menulis badan
     * penuh. Perbedaan itu ada di C dan bukan kelalaian.</p>
     */
    public static void updateState(User viewer, User who) {
        Session s = sessionOf(viewer);
        if (s == null) {
            return;
        }
        var st = who.status;
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x1D);
        s.wfifoLBE(5, (int) who.id);

        if (st.state == 4) {
            s.wfifoB(9, 1);
            s.wfifoB(10, 15);
            s.wfifoB(11, st.state);
            s.wfifoWBE(12, st.disguise + 32768);
            s.wfifoB(14, st.disguiseColor);

            String nama = st.name == null ? "" : st.name;
            int n = Math.min(nama.length(), 255);
            s.wfifoB(16, n);
            s.wfifoStringRaw(17, nama.substring(0, n));
            // C: len += strlen(name) + 1, dari len = 0
            s.wfifoWBE(1, n + 1 + 13);
            s.wfifoSet(encrypt(s, viewer));
            return;
        }

        int len = charBody(s, viewer, who, 0);
        s.wfifoWBE(1, len + 55 + 3);
        s.wfifoSet(encrypt(s, viewer));
    }

    /**
     * clif_object_look_sub2(): gambarkan <b>satu benda bukan-karakter</b> di
     * layar {@code sd} — mob biasa dan NPC yang sebenarnya benda peta.
     * Opcode 0x07 dengan jumlah benda tetap 1.
     *
     * <p>Pasangannya {@link #sendMobLook}/{@link #sendNpcLook} (0x33) untuk
     * yang <b>berwujud karakter</b>. Pembagiannya bukan menurut jenis benda
     * melainkan menurut cara ia digambar: {@code MobIsChar}/{@code npcType}.</p>
     *
     * <p>Grafiknya dikirim {@code 32768 + look} — bit tinggi itu yang
     * membedakan gambar benda dari gambar petak.</p>
     *
     * <p><b>Belum lengkap:</b> daftar animasi berdurasi milik mob
     * ({@code mob->da[]}, ladang [21] dan seterusnya) ikut kosong karena
     * subsistem durasi belum diport; ladang jumlahnya ditulis 0 sehingga
     * panjang paket tetap 20. Lengkapi bersama {@code setDuration}.
     * Cabang BL_ITEM juga belum ada — menunggu subsistem barang di lantai.</p>
     */
    public static void objectLook(User sd, org.rtk.map.data.BlockList b) {
        Session s = sessionOf(sd);
        if (s == null || b instanceof User) {
            return;
        }
        s.wfifoB(0, 0xAA);
        s.wfifoB(3, 0x07);
        s.wfifoWBE(5, 1);
        s.wfifoWBE(7, b.x);
        s.wfifoWBE(9, b.y);
        s.wfifoLBE(12, (int) b.id);

        if (b instanceof Mob mb) {
            if (mb.state == MobData.MOB_DEAD || mb.data.mobType == 1) {
                return;
            }
            if (!mb.data.isNpc) {
                s.wfifoB(11, 0x05);
                s.wfifoWBE(16, 32768 + mb.look);
                s.wfifoB(18, mb.lookColor);
                s.wfifoB(19, mb.side);
                s.wfifoB(20, 0);
                s.wfifoB(21, 0);   // jumlah animasi berdurasi
                s.wfifoB(22, 0);   // passflag
            } else {
                s.wfifoB(11, 12);
                s.wfifoWBE(16, 32768 + mb.look);
                s.wfifoB(18, mb.lookColor);
                s.wfifoB(19, mb.side);
                s.wfifoWBE(20, 0);
                s.wfifoB(22, 0);
            }
        } else if (b instanceof Npc nd) {
            if (nd.subtype != 0 || nd.npcType == 1) {
                return;
            }
            s.wfifoB(11, 12);
            s.wfifoWBE(16, (int) (32768 + nd.graphicId));
            s.wfifoB(18, (int) nd.graphicColor);
            s.wfifoB(19, nd.side);
            s.wfifoWBE(20, 0);
            s.wfifoB(22, 0);
        } else if (b instanceof FloorItem fl) {
            // Jebakan hanya digambar untuk pemain yang sudah menemukannya.
            if (!fl.visibleTo(sd.id)) {
                return;
            }
            s.wfifoB(11, 0x02);
            // ⚠️ Hanya ikon KUSTOM yang ditambah 49152; ikon dari tabel
            // `Items` dikirim MENTAH. Mudah salah karena benda lain
            // (mob, NPC) selalu memakai penambah 32768.
            if (fl.data.customIcon != 0) {
                s.wfifoWBE(16, (int) (fl.data.customIcon + 49152));
                s.wfifoB(18, (int) fl.data.customIconColor);
            } else {
                var look = MapServer.itemDb.look(fl.data.id);
                s.wfifoWBE(16, look.icon());
                s.wfifoB(18, look.iconColor());
            }
            s.wfifoB(19, 0);
            s.wfifoWBE(20, 0);
            s.wfifoB(22, 0);
        } else {
            return;
        }

        s.wfifoWBE(1, 20);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * Bagian penyiaran {@code bll_updatestate()}: gambar ulang sebuah benda
     * pada pemain di sekitarnya, di tempatnya sekarang.
     *
     * <p>Paket yang dipakai ditentukan oleh <b>cara benda itu digambar</b>,
     * bukan jenisnya: pemain lewat 0x1D, mob dan NPC yang berwujud karakter
     * ({@code subtype}/{@code npcType == 1}) lewat 0x33, sisanya lewat paket
     * benda 0x07. Pembagian itu diambil dari {@code bll_updatestate} di
     * {@code sl.c}.</p>
     */
    public static void broadcastLook(org.rtk.map.data.BlockList bl) {
        MapData map = MapServer.world.get(bl.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(bl.x, bl.y, org.rtk.map.data.BlockList.Type.PC, o -> {
            if (!(o instanceof User to)) {
                return;
            }
            if (bl instanceof User who) {
                updateState(to, who);
            } else if (bl instanceof Mob mb) {
                if (mb.data.subtype == 1) {
                    sendMobLook(to, mb);
                } else {
                    objectLook(to, mb);
                }
            } else if (bl instanceof Npc nd) {
                if (nd.npcType == 1) {
                    sendNpcLook(to, nd);
                } else {
                    objectLook(to, nd);
                }
            } else if (bl instanceof FloorItem) {
                objectLook(to, bl);
            }
        });
    }

    /**
     * clif_mob_look_start / clif_object_look_sub / clif_mob_look_close:
     * satu paket 0x07 berisi <b>beberapa benda sekaligus</b>.
     *
     * <p>Bedanya dengan {@link #objectLook} halus tapi nyata: yang ini
     * dibangun lewat {@code WFIFOHEADER} sehingga membawa nomor urut di [4],
     * dan sanggup memuat banyak benda. Untuk satu mob biasa isinya berakhir
     * <b>byte-identik</b> dengan versi satu-benda kecuali ladang [4] itu —
     * C memang menyediakan dua jalan ke paket yang sama.</p>
     *
     * <p>⚠️ <b>Entri saling menimpa satu byte, dan itu disengaja.</b> Tiap
     * entri panjangnya 15 byte tetapi menulis sampai {@code len+22}; entri
     * berikutnya mulai di {@code len+15}, jadi byte "passflag" di
     * {@code len+22} persis ditimpa oleh ladang x entri sesudahnya. Hanya
     * entri terakhir yang passflag-nya bertahan — karena itu penutupnya
     * menambah satu byte lagi. Menyusun ulang jadi entri rapi 15 byte akan
     * menggeser seluruh paket.</p>
     *
     * <p>Mob mati, mob berwujud karakter ({@code mobType == 1}), NPC
     * berwujud karakter, dan pemain <b>dilewati</b> — mereka digambar lewat
     * 0x33. Benda yang dilewati tidak menambah hitungan.</p>
     */
    public static void objectLookBatch(User sd, java.util.List<
            ? extends org.rtk.map.data.BlockList> objects) {
        Session s = sessionOf(sd);
        if (s == null || objects == null || objects.isEmpty()) {
            return;
        }
        int len = 0;      // sd->mob_len
        int count = 0;    // sd->mob_count
        boolean adaBarang = false;   // sd->mob_item

        for (org.rtk.map.data.BlockList b : objects) {
            if (b instanceof User) {
                continue;
            }
            int tipe;
            int look = 0;
            int grafik = -1;   // nilai yang benar-benar ditulis; -1 = pakai 32768 + look
            int lookColor;
            int side;
            if (b instanceof Mob mb) {
                if (mb.state == MobData.MOB_DEAD || mb.data.mobType == 1) {
                    continue;
                }
                tipe = mb.data.isNpc ? 12 : 0x05;
                look = mb.look;
                lookColor = mb.lookColor;
                side = mb.side;
            } else if (b instanceof Npc nd) {
                if (nd.subtype != 0 || nd.npcType == 1) {
                    continue;
                }
                tipe = 12;
                look = (int) nd.graphicId;
                lookColor = (int) nd.graphicColor;
                side = nd.side;
            } else if (b instanceof FloorItem fl) {
                if (!fl.visibleTo(sd.id)) {
                    continue;
                }
                tipe = 0x02;
                // Lihat catatan di objectLook: hanya ikon KUSTOM yang
                // ditambah 49152, sisanya mentah dari tabel `Items`.
                if (fl.data.customIcon != 0) {
                    grafik = (int) (fl.data.customIcon + 49152);
                    lookColor = (int) fl.data.customIconColor;
                } else {
                    var l = MapServer.itemDb.look(fl.data.id);
                    grafik = l.icon();
                    lookColor = l.iconColor();
                }
                side = 0;
                adaBarang = true;   // sd->mob_item: penutupnya tidak menambah byte
            } else {
                continue;
            }

            s.wfifoWBE(len + 7, b.x);
            s.wfifoWBE(len + 9, b.y);
            s.wfifoB(len + 11, tipe);
            s.wfifoLBE(len + 12, (int) b.id);
            s.wfifoWBE(len + 16, grafik >= 0 ? grafik : 32768 + look);
            s.wfifoB(len + 18, lookColor);
            s.wfifoB(len + 19, side);
            if (tipe == 0x05) {
                s.wfifoB(len + 20, 0);
                s.wfifoB(len + 21, 0);   // jumlah animasi berdurasi mob
            } else {
                s.wfifoWBE(len + 20, 0);
            }
            s.wfifoB(len + 22, 0);       // passflag; ditimpa entri berikutnya
            len += 15;
            count++;
        }

        if (count == 0) {
            return;
        }
        if (!adaBarang) {
            s.wfifoB(len + 7, 0);
            len++;
        }
        headSeq(s, 0x07, len + 4);
        s.wfifoWBE(5, count);
        s.wfifoSet(encrypt(s, sd));
    }

    /**
     * clif_mob_move(): mob melangkah — beri tahu pemain di sekitarnya.
     * Opcode 0x0C, sama dengan langkah pemain dan NPC.
     *
     * <p>Yang dikirim <b>petak asal</b> ({@code mob->bx}/{@code by} di C,
     * disetel tepat sebelum {@code map_moveblock}) plus arahnya — klien yang
     * memainkan animasi langkahnya sendiri.</p>
     *
     * <p>Berbeda dari siaran langkah pemain, paket ini dibangun per-sesi
     * lewat {@code WFIFOHEADER}, jadi tiap penerima mendapat nomor urutnya
     * sendiri. Mob mati tidak dikirim sama sekali.</p>
     */
    public static void mobMove(Mob mb, int fromX, int fromY) {
        if (mb.state == MobData.MOB_DEAD) {
            return;
        }
        MapData map = MapServer.world.get(mb.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(mb.x, mb.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (!(bl instanceof User to)) {
                return;
            }
            Session ts = MapServer.net.session(to.fd);
            if (ts == null) {
                return;
            }
            headSeq(ts, 0x0C, 11);
            ts.wfifoLBE(5, (int) mb.id);
            ts.wfifoWBE(9, fromX);
            ts.wfifoWBE(11, fromY);
            ts.wfifoB(13, mb.side);
            ts.wfifoSet(encrypt(ts, to));
        });
    }

    /**
     * Gambar ulang jalur petak yang baru terbuka saat <b>mob</b> melangkah
     * ({@code map_foreachinblock(...)} di {@code moveghost_mob}).
     *
     * <p>Mob berwujud karakter memakai 0x33, sisanya paket benda 0x07
     * berkelompok — pembagian yang sama dengan {@link #broadcastLook}.</p>
     */
    static void mobRevealStrip(Mob mb, MapData map, int x0, int y0, int x1, int y1) {
        map.foreachInBlock(x0, y0, x1, y1, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (!(bl instanceof User sd)) {
                return;
            }
            if (mb.data.mobType == 1) {
                sendMobLook(sd, mb);
            } else {
                objectLookBatch(sd, java.util.List.of(mb));
            }
        });
    }

    /**
     * clif_lookgone(): sebuah benda lenyap — pemain di sekitarnya (AREA_WOS)
     * harus berhenti menggambarnya.
     *
     * <p>Dua opcode berbeda, lagi-lagi menurut cara benda itu digambar: yang
     * berwujud karakter (pemain, mob, NPC dengan {@code npcType == 1})
     * dihapus dengan 0x0E, sisanya — NPC yang sebenarnya benda peta — dengan
     * 0x5F.</p>
     */
    public static void lookGone(org.rtk.map.data.BlockList bl) {
        boolean asChar = !(bl instanceof FloorItem)
                && (!(bl instanceof Npc nd) || nd.npcType == 1);
        byte[] buf = new byte[16];
        buf[0] = (byte) 0xAA;
        putBE16(buf, 1, 6);
        buf[3] = (byte) (asChar ? 0x0E : 0x5F);
        buf[4] = 0x03;
        putBE32(buf, 5, (int) bl.id);
        sendToArea(bl, buf, buf.length, false);
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
    static boolean blocksMovement(User sd, org.rtk.map.data.BlockList bl, MapData map) {
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
    static void updateCamera(User sd, MapData map, int direction, int nx, int ny) {
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
    static void sendWalkConfirm(User sd, int direction, int oldX, int oldY) {
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
    static void broadcastWalk(User sd, int direction, int oldX, int oldY) {
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

    /**
     * FLAG_MAGIC (mmo.h): bendera setelan pemain "tampilkan efek sihir".
     * Pemain yang mematikannya tidak menerima paket animasi sama sekali.
     */
    private static final int FLAG_MAGIC = 16;

    /**
     * clif_sendanimation(): mainkan animasi <b>pada sebuah benda</b>
     * (opcode 0x29) untuk pemain di sekitarnya.
     *
     * <p>Penyaringnya ada di sisi <b>penerima</b>, bukan pengirim: tiap
     * pemain yang mematikan FLAG_MAGIC dilewati satu per satu.</p>
     */
    public static void sendAnimation(org.rtk.map.data.BlockList src, int anim, int times) {
        MapData map = MapServer.world.get(src.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(src.x, src.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User to) {
                sendAnimationTo(to, src, anim, times);
            }
        });
    }

    /**
     * Badan {@code clif_sendanimation()} untuk <b>satu penonton</b>.
     *
     * <p>Perhatikan pembagian perannya, karena mudah tertukar: {@code viewer}
     * adalah pemain yang <b>melihat</b>, {@code target} adalah benda tempat
     * animasinya <b>dimainkan</b>. Di C keduanya dua argumen terpisah
     * ({@code bl} dan {@code t}), dan {@code bll_selfanimation} memakainya
     * untuk memainkan animasi pada dirinya sendiri tetapi hanya terlihat oleh
     * satu orang.</p>
     *
     * <p>Penyaring FLAG_MAGIC ada di sisi <b>penerima</b>: pemain yang
     * mematikan efek sihir tidak menerima paketnya sama sekali.</p>
     */
    public static void sendAnimationTo(User viewer, org.rtk.map.data.BlockList target,
                                       int anim, int times) {
        if ((viewer.status.settingFlags & FLAG_MAGIC) == 0) {
            return;
        }
        Session ts = MapServer.net.session(viewer.fd);
        if (ts == null) {
            return;
        }
        ts.wfifoB(0, 0xAA);
        ts.wfifoWBE(1, 0x0A);
        ts.wfifoB(3, 0x29);
        ts.wfifoLBE(5, (int) target.id);
        ts.wfifoWBE(9, anim);
        ts.wfifoWBE(11, times);
        ts.wfifoSet(encrypt(ts, viewer));
    }

    /**
     * clif_sendanimation_xy(): mainkan animasi <b>pada sebuah petak</b>
     * (opcode 0x29 dengan id 0 dan koordinat di ekor).
     *
     * <p>Berbeda dari {@link #sendAnimation}, versi ini <b>tidak</b>
     * memeriksa FLAG_MAGIC — jebakan dan efek area tetap terlihat. Itu
     * memang begitu di C, bukan kelalaian.</p>
     */
    public static void sendAnimationXy(org.rtk.map.data.BlockList src,
                                       int anim, int times, int x, int y) {
        MapData map = MapServer.world.get(src.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(src.x, src.y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (!(bl instanceof User to)) {
                return;
            }
            Session ts = MapServer.net.session(to.fd);
            if (ts == null) {
                return;
            }
            ts.wfifoB(0, 0xAA);
            ts.wfifoWBE(1, 14);
            ts.wfifoB(3, 0x29);
            ts.wfifoLBE(5, 0);
            ts.wfifoWBE(9, anim);
            ts.wfifoWBE(11, times);
            ts.wfifoWBE(13, x);
            ts.wfifoWBE(15, y);
            ts.wfifoSet(encrypt(ts, to));
        });
    }

    /**
     * clif_npc_move(): NPC melangkah — siarkan ke pemain di sekitarnya.
     *
     * <p>Isinya <b>petak asal</b> plus arah, bukan petak tujuan: klien yang
     * memainkan animasi langkahnya. Sama persis dengan siaran 0x0C untuk
     * pemain, hanya idnya id blok NPC.</p>
     *
     * <p><b>Penyimpangan yang disengaja:</b> versi C memanggil fungsi ini
     * lewat {@code map_foreachinarea(..., BL_PC, ...)}, padahal isinya
     * sendiri sudah menyiarkan ke seluruh area — jadi paket yang sama
     * terkirim N x N kali bila ada N pemain berdekatan. Paketnya membawa
     * posisi mutlak sehingga pengulangan tidak mengubah apa yang dilihat
     * klien; di sini cukup satu kali siaran.</p>
     */
    public static void npcMove(Npc nd, int oldX, int oldY) {
        byte[] buf = new byte[32];
        buf[0] = (byte) 0xAA;
        buf[1] = 0x00;
        buf[2] = 0x0C;
        buf[3] = 0x0C;
        putBE32(buf, 5, (int) nd.id);
        putBE16(buf, 9, oldX);
        putBE16(buf, 11, oldY);
        buf[13] = (byte) nd.side;
        buf[14] = 0x00;
        sendToArea(nd, buf, buf.length, false);
    }

    /**
     * Gambar ulang jalur petak yang baru terbuka saat NPC melangkah
     * ({@code map_foreachinblock(clif_cnpclook_sub / clif_object_look_sub,
     * ...)} di {@code npc_move}).
     */
    static void npcRevealStrip(Npc nd, MapData map, int x0, int y0, int x1, int y1) {
        map.foreachInBlock(x0, y0, x1, y1, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User sd) {
                sendNpcLook(sd, nd);
            }
        });
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
    static void fireWalkScripts(User sd) {
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
     * clif_mystaytus() — panel profil milik pemain sendiri. Opcode 0x39.
     *
     * <p>⚠️ TAHAP 1 (26 Agu 2026): strukturnya lengkap dan mengikuti
     * clif.c:2898-3104 ladang demi ladang, tapi isinya masih minimal —
     * clan/gelar/partner/nama kelas dikirim kosong, 14 slot perlengkapan
     * dikirim sebagai slot kosong, dan daftar legenda 0 entri. Tujuannya
     * menguji apakah KETIADAAN paket ini yang membuat klien asli crash,
     * sebelum menanam ClanDb + classdb_name + clif_getName + itemdb_protected
     * yang semuanya belum ada. Begitu hipotesisnya terbukti, isi ladangnya
     * dilengkapi — JANGAN biarkan versi minimal ini menetap.
     *
     * <p>Tata letak panjang variabel: {@code len} menghitung byte payload
     * setelah offset 8, persis seperti variabel {@code len} di C.
     */
    public static void sendMyStatus(User sd) {
        Session s = sessionOf(sd);
        if (s == null) {
            return;
        }
        // C menjepit armor ke rentang signed byte sebelum dikirim
        int armor = Math.max(-127, Math.min(127, sd.armor));
        s.wfifoB(5, armor & 0xFF);
        s.wfifoB(6, sd.dam & 0xFF);
        s.wfifoB(7, sd.hit & 0xFF);

        int len = 0;
        // clan, gelar clan, gelar — TAHAP 1: semuanya kosong (panjang 0)
        s.wfifoB(8, 0);
        len += 1;
        s.wfifoB(len + 8, 0);
        len += 1;
        s.wfifoB(len + 8, 0);
        len += 1;
        // partner — TAHAP 1: kosong (butuh clif_getName yang belum diport)
        s.wfifoB(len + 8, 0);
        len += 1;

        s.wfifoB(len + 8, (sd.status.settingFlags & FLAG_GROUP) != 0 ? 1 : 0);
        s.wfifoLBE(len + 9, 0); // TNL — TAHAP 1: 0 (butuh clif_getLevelTNL)
        len += 5;

        // nama kelas — TAHAP 1: kosong (butuh classdb_name)
        s.wfifoB(len + 8, 0);
        len += 1;

        // 14 slot perlengkapan. Cabang kosong di C menulis 10 byte nol
        // (WFIFOL di len+13 lalu WFIFOB di len+14 sengaja tumpang tindih —
        // hasilnya tetap 10 byte nol, jadi ditiru sebagai nol semua).
        for (int i = 0; i < 14; i++) {
            for (int b = 0; b < 10; b++) {
                s.wfifoB(len + 8 + b, 0);
            }
            len += 10;
        }

        s.wfifoB(len + 8, (sd.status.settingFlags & FLAG_EXCHANGE) != 0 ? 1 : 0);
        s.wfifoB(len + 9, (sd.status.settingFlags & FLAG_GROUP) != 0 ? 1 : 0);
        len += 1;

        // daftar legenda — TAHAP 1: 0 entri
        s.wfifoB(len + 8, 0);
        s.wfifoWBE(len + 9, 0);
        len += 3;

        head(s, 0x39, len + 5);
        s.wfifoSet(encrypt(s, sd));
        log.debug("[CLIF] mystaytus (TAHAP 1, minimal) ke {} — payload {} byte", sd.name(), len + 5);
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
        // clif_mystaytus + clif_spawn: di C keduanya ADA di antara status dan
        // refresh (intif.c:229-230). Port ini sempat melewatkan keduanya.
        // map_addblock sudah dikerjakan Pc.spawn() sebelum sendWorldEntry,
        // jadi sisa clif_spawn tinggal siarannya = sendCharArea.
        sendMyStatus(sd);
        sendCharArea(sd);
        refresh(sd);
        sendXy(sd);
        getCharArea(sd);
        log.info("[CLIF] paket masuk dunia dikirim ke {} (fd {})", sd.name(), sd.fd);
    }
}
