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
