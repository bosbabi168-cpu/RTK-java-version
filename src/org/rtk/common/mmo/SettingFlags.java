package org.rtk.common.mmo;

/**
 * Port of {@code enum settingFLAGS} (common/mmo.h:37) — setelan pemain yang
 * tersimpan di {@link CharStatus#settingFlags}.
 *
 * <p>Bit-bit ini <b>bukan urusan protokol</b> walau sebagian menentukan apa
 * yang dikirim: ia keputusan pemain tentang apa yang ingin ia terima dan
 * izinkan. Karena itu tempatnya di model karakter, bukan di kelas paket.</p>
 *
 * <p>Sebelum 27 Agustus 2026 nilainya tersalin di tiga tempat —
 * {@code Clif}, {@code MapCommands}, dan sekali lagi di implementasi
 * protokol kedua. Duplikasi seperti itu tidak pernah salah saat ditulis;
 * ia salah saat salah satunya diperbaiki.</p>
 */
public final class SettingFlags {

    /** Menerima bisikan dari pemain lain. */
    public static final int WHISPER = 1;
    /** Menerima ajakan grup. */
    public static final int GROUP = 2;
    /** Menerima teriakan sepeta. */
    public static final int SHOUT = 4;
    /** Menerima saluran nasihat/pemula. */
    public static final int ADVICE = 8;
    /** Menampilkan efek mantra milik orang lain. */
    public static final int MAGIC = 16;
    /** Menampilkan cuaca. */
    public static final int WEATHER = 32;
    /** Menampilkan pengumuman realm. */
    public static final int REALM = 64;
    /** Bersedia diajak bertukar barang. */
    public static final int EXCHANGE = 128;
    /** Gerak cepat. */
    public static final int FASTMOVE = 256;
    /** Membunyikan efek suara. */
    public static final int SOUND = 4096;
    /** Menampilkan helm yang dikenakan. */
    public static final int HELM = 8192;
    /** Menampilkan kalung yang dikenakan. */
    public static final int NECKLACE = 16384;

    private SettingFlags() {
    }

    /** Setelan {@code flags} menyalakan bit {@code bit}. */
    public static boolean on(long flags, int bit) {
        return (flags & bit) != 0;
    }
}
