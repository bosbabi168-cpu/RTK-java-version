package org.rtk.map;

/**
 * Port of struct mobdb_data (map/map.h) — <b>jenis</b> mob, bukan mob yang
 * sedang hidup di peta. Satu baris tabel {@code Mobs} = satu MobData, dan
 * banyak {@link Mob} bisa berbagi satu MobData.
 *
 * <h3>Nama: kebalikan dari NPC</h3>
 * Untuk mob, kolom {@code MobIdentifier} jadi {@link #yname} (nama skrip)
 * dan {@code MobDescription} jadi {@link #name} (nama tampilan). Pada NPC
 * pemetaannya justru terbalik — di sana {@code NpcIdentifier} yang jadi
 * "name". Versi C memang begitu: dispatch skrip mob memakai
 * {@code mob->data->yname}, sedangkan NPC memakai {@code nd->name}.
 * Salah menukar keduanya membuat seluruh skrip mob tidak pernah terpanggil.
 */
public final class MobData {

    /** Nilai {@link Mob#state} — setara enum MOB_* di mob.h. */
    public static final int MOB_ALIVE = 0;
    public static final int MOB_DEAD = 1;
    public static final int MOB_PARA = 2;
    public static final int MOB_BLIND = 3;
    public static final int MOB_HIT = 4;
    public static final int MOB_ESCAPE = 5;

    /** Nilai {@link #type} — perilaku dasar (MOB_NORMAL/AGGRESSIVE/STATIONARY). */
    public static final int MOB_NORMAL = 0;
    public static final int MOB_AGGRESSIVE = 1;
    public static final int MOB_STATIONARY = 2;

    public long id;

    /** {@code MobDescription} — nama yang dilihat pemain. */
    public String name = "";

    /** {@code MobIdentifier} — nama kelas skrip Lua. */
    public String yname = "";

    /** {@code MobBehavior}: normal, agresif, atau diam. */
    public int type;

    /** {@code MobAI}: jenis AI; menentukan tabel `mob_ai_*` mana yang dipakai. */
    public int subtype;

    public int look;
    public int lookColor;
    public long vita;
    public long mana;
    public long exp;
    public int hit;
    public int level;
    public int might;
    public int grace;
    public int will;
    public int moveTime;
    public int attackTime;
    public int spawnTime;
    public int baseArmor;
    public int protection;
    public int sound;
    public int returnDistance;
    public int minDam;
    public int maxDam;
    public int mark;

    /** {@code MobIsChar}: 1 = digambar sebagai karakter lewat paket 0x33. */
    public int mobType;
    public boolean isNpc;
    public boolean isBoss;

    // tampilan bila digambar sebagai karakter
    public int sex;
    public int face;
    public int faceColor;
    public int hair;
    public int hairColor;
    public int skinColor;
    public int state;

    @Override
    public String toString() {
        return "MobData{" + yname + " \"" + name + "\" id=" + id
                + " lv" + level + " vita=" + vita + "}";
    }
}
