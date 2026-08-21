package org.rtk.map.data;

/**
 * Port of enum EQ_* (map/itemdb.h) — slot perlengkapan.
 *
 * <p>Nilainya <b>bagian dari protokol</b>: indeks ini dipakai sebagai posisi
 * di tabel {@code Equipment}/{@code NPCEquipment} dan menentukan offset mana
 * yang diisi pada paket gambar karakter (0x33). Jangan diurutkan ulang.</p>
 *
 * <p>Skrip Lua juga memakai nama-nama ini sebagai global (di C dipasang lewat
 * makro {@code SL_EXPOSE_ENUM}), jadi {@link #NAMES} dipakai mesin skrip
 * untuk mendaftarkannya.</p>
 */
public final class Equip {

    public static final int WEAP = 0;
    public static final int ARMOR = 1;
    public static final int SHIELD = 2;
    public static final int HELM = 3;
    public static final int LEFT = 4;
    public static final int RIGHT = 5;
    public static final int SUBLEFT = 6;
    public static final int SUBRIGHT = 7;
    public static final int FACEACC = 8;
    public static final int CROWN = 9;
    public static final int MANTLE = 10;
    public static final int NECKLACE = 11;
    public static final int BOOTS = 12;
    public static final int COAT = 13;
    public static final int FACEACCTWO = 14;

    /** Jumlah slot; setara MAX_EQUIP di C. */
    public static final int COUNT = 15;

    /** Nama global yang dilihat skrip Lua, terurut sesuai nilainya. */
    public static final String[] NAMES = {
        "EQ_WEAP", "EQ_ARMOR", "EQ_SHIELD", "EQ_HELM", "EQ_LEFT", "EQ_RIGHT",
        "EQ_SUBLEFT", "EQ_SUBRIGHT", "EQ_FACEACC", "EQ_CROWN", "EQ_MANTLE",
        "EQ_NECKLACE", "EQ_BOOTS", "EQ_COAT", "EQ_FACEACCTWO"
    };

    private Equip() {
    }
}
