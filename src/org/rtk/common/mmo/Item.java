package org.rtk.common.mmo;

/**
 * Port of struct item (common/mmo.h).
 *
 * Di C ini berukuran tetap 880 byte karena {@code trapsTable[100]},
 * {@code note[300]}, dan sederet buffer lain selalu ikut terkirim walau
 * kosong. Di sini panjangnya mengikuti isi.
 */
public final class Item {

    public long id;          // unsigned int
    public long owner;
    public long custom;
    public long time;
    public int dura;
    public int amount;
    public int pos;          // unsigned char
    public long customLook;
    public long customIcon;
    public long customLookColor;
    public long customIconColor;
    public long protectedFlag; // 'protected' adalah kata kunci Java
    public int[] trapsTable = new int[0];
    public String buytext = "";
    public String note = "";
    public int repair;
    public String realName = "";

    /** Slot kosong tidak perlu ikut dikirim/disimpan. */
    public boolean isEmpty() {
        return id == 0 && amount == 0 && (realName == null || realName.isEmpty());
    }

    @Override
    public String toString() {
        return "Item{id=" + id + ", name='" + realName + "', amount=" + amount + ", pos=" + pos + "}";
    }
}
