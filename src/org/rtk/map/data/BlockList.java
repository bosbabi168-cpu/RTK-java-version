package org.rtk.map.data;

/**
 * Port of struct block_list (map/map.h) — dasar setiap benda yang menempati
 * petak di peta: pemain, mob, NPC, dan barang di lantai.
 *
 * Di C ini simpul linked-list yang disisipkan ke indeks blok 8x8 milik peta
 * ({@code map[m].block[bx + by*bxs]}). Di sini penyimpanannya ditangani
 * {@link MapData}, jadi kelas ini cukup membawa datanya saja.
 */
public class BlockList {

    /**
     * Bendera BL_* dari map.h — <b>bit</b>, bukan nomor urut, sehingga
     * bisa digabung ({@code BL_ALL = 0x0F}). Skrip memakainya sebagai
     * global saat menyaring pencarian benda.
     */
    public static final int BL_NUL = 0x00;
    public static final int BL_PC = 0x01;
    public static final int BL_MOB = 0x02;
    public static final int BL_NPC = 0x04;
    public static final int BL_ITEM = 0x08;
    public static final int BL_ALL = 0x0F;

    /** Nama global BL_* untuk didaftarkan ke Lua, dengan nilainya. */
    public static final String[] BL_NAMES = {
        "BL_NUL", "BL_PC", "BL_MOB", "BL_NPC", "BL_ITEM", "BL_ALL"
    };
    public static final int[] BL_VALUES = {
        BL_NUL, BL_PC, BL_MOB, BL_NPC, BL_ITEM, BL_ALL
    };

    /** Bendera BL_* yang cocok dengan jenis ini. */
    public int blFlag() {
        return switch (type) {
            case PC -> BL_PC;
            case MOB -> BL_MOB;
            case NPC -> BL_NPC;
            case ITEM -> BL_ITEM;
        };
    }

    /** Nilai {@link #type} — setara BL_PC / BL_MOB / BL_NPC / BL_ITEM di C. */
    public enum Type {
        PC, MOB, NPC, ITEM
    }

    public long id;
    public Type type = Type.PC;
    public int subtype;

    /** Peta tempat benda ini berada (indeks {@code MapId}). */
    public int m = -1;
    public int x;
    public int y;

    /** Koordinat blok 8x8; dihitung ulang oleh {@link MapData} saat ditempatkan. */
    public int bx;
    public int by;

    public long graphicId;
    public long graphicColor;

    /** true bila sedang terdaftar pada indeks blok sebuah peta. */
    public boolean onMap;

    @Override
    public String toString() {
        return type + "#" + id + "@" + m + ":" + x + "," + y;
    }
}
