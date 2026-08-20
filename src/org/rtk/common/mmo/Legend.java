package org.rtk.common.mmo;

/** Port of struct legend (common/mmo.h): satu baris riwayat karakter. */
public final class Legend {
    public int icon;      // unsigned short
    public int color;
    public String text = "";
    public String name = "";
    public long tchaid;   // unsigned int

    public boolean isEmpty() {
        return (text == null || text.isEmpty()) && (name == null || name.isEmpty());
    }

    @Override
    public String toString() {
        return "Legend{'" + name + "': " + text + "}";
    }
}
