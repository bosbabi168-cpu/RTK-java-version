package org.rtk.common.mmo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of struct mmo_charstatus (common/mmo.h) — seluruh keadaan satu
 * karakter yang berpindah antara char server dan map server.
 *
 * <h3>Perbedaan sengaja dengan versi C</h3>
 * Di C, struct ini berisi array berukuran tetap yang sangat besar
 * ({@code global_regstring[5000]}, {@code global_reg[5000]} tiga buah,
 * {@code legends[1000]}, …) sehingga sizeof-nya <b>3.171.352 byte</b> dan
 * seluruhnya dikirim mentah lewat kabel walau hampir semuanya kosong —
 * 82% ukurannya hanya array registry. Ukuran itu bahkan berbeda antar
 * target kompilasi (32-bit vs 64-bit) karena {@code unsigned long} pada
 * {@link SkillInfo}.
 *
 * Port ini memakai koleksi dinamis dan format serialisasi sendiri
 * ({@link CharStatusCodec}), jadi hanya data yang benar-benar terisi yang
 * dikirim. Kedua ujung paket 0x3003/0x3004 adalah kode Java kita sendiri,
 * sehingga tidak ada pihak luar yang bergantung pada tata letak biner C.
 * Penyimpanan sebenarnya tetap MySQL, sama seperti aslinya.
 *
 * Field scalar mengikuti 67 kolom tabel {@code Character} yang dibaca
 * {@code mmo_char_fromdb()} dan ditulis {@code mmo_char_todb()}.
 */
public final class CharStatus {

    // ---- identitas ----
    public long id;
    public String name = "";
    public String password = "";
    public String f1name = "";
    public String title = "";
    public String clanTitle = "";
    public String lastIp = "";
    public String afkMessage = "";

    // ---- relasi ----
    public long partner;
    public long clan;
    public int clanRank;
    public int classRank;

    // ---- statistik inti ----
    public int level;
    public int charClass;   // 'class' adalah kata kunci Java (ChaPthId)
    public int tier;
    public int mark;
    public int totem;
    public float karma;
    public int alignment;
    public int tutor;

    public long hp;
    public long baseHp;
    public long mp;
    public long baseMp;
    public long exp;
    public long money;
    public long bankMoney;

    public long baseMight;
    public long baseWill;
    public long baseGrace;
    public int baseArmor;
    public long heroes;
    public long miniMapToggle;

    public long expSoldMagic;
    public long expSoldHealth;
    public long expSoldStats;

    // ---- tampilan ----
    public int sex;
    public int country;
    public int face;
    public int faceColor;
    public int hair;
    public int hairColor;
    public int armorColor;
    public int skinColor;
    public int disguise;
    public int disguiseColor;

    // ---- status sosial / permainan ----
    public int gmLevel;
    public int side;
    public int state;
    public int clanChat;
    public int subpathChat;
    public int noviceChat;
    public int mute;
    public long settingFlags;
    public int pk;
    public long killedBy;
    public long killsPk;
    public long pkDuration;
    public long maxSlots;
    public int maxInv;

    // ---- pengaturan profil ----
    public int profileVitaStats;
    public int profileEquipList;
    public int profileLegends;
    public int profileSpells;
    public int profileInventory;
    public int profileBankItems;

    // ---- posisi ----
    public Point lastPos = new Point();
    public Point destPos = new Point();

    // ---- koleksi (di C: array berukuran tetap) ----
    public final List<Item> equip = new ArrayList<>();
    public final List<Item> inventory = new ArrayList<>();
    public final List<Legend> legends = new ArrayList<>();
    public final List<SkillInfo> duraAether = new ArrayList<>();
    public final List<BankItem> banks = new ArrayList<>();

    /** Buku mantra: daftar id spell per slot (di C: {@code skill[MAX_SPELLS]}). */
    public int[] spells = new int[0];

    /** pc_readglobalreg / pc_setglobalreg */
    public final Map<String, Integer> registry = new LinkedHashMap<>();
    /** pc_readglobalregstring */
    public final Map<String, String> registryString = new LinkedHashMap<>();
    /** AccountRegistry */
    public final Map<String, Integer> accountRegistry = new LinkedHashMap<>();
    /** NPCRegistry */
    public final Map<String, Integer> npcRegistry = new LinkedHashMap<>();
    /** QuestRegistry */
    public final Map<String, Integer> questRegistry = new LinkedHashMap<>();
    /** Kills: id mob -> jumlah */
    public final Map<Long, Long> killReg = new LinkedHashMap<>();

    /**
     * pc_isequip(): barang di slot perlengkapan tertentu, atau null.
     *
     * <p>Di C {@code status.equip[]} adalah array berindeks slot; di sini
     * koleksinya berupa daftar, dan slot disimpan pada {@link Item#pos}
     * (kolom {@code EqpSlot}). Pencarian dibuat eksplisit supaya tidak ada
     * yang salah mengira urutan daftar = urutan slot.</p>
     */
    public Item equipAt(int slot) {
        for (Item it : equip) {
            if (it != null && it.pos == slot && it.id > 0) {
                return it;
            }
        }
        return null;
    }

    /**
     * Barang di slot inventaris tertentu, atau null.
     *
     * <p>Sepasang dengan {@link #equipAt}: di C {@code status.inventory[]}
     * berindeks slot, di sini slotnya ada di {@link Item#pos}.</p>
     */
    public Item inventoryAt(int slot) {
        for (Item it : inventory) {
            if (it != null && it.pos == slot && it.id > 0) {
                return it;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "CharStatus{id=" + id + ", name='" + name + "', level=" + level
                + ", pos=" + lastPos
                + ", inv=" + inventory.size() + ", eq=" + equip.size()
                + ", legends=" + legends.size() + ", reg=" + registry.size()
                + ", regStr=" + registryString.size() + ", bank=" + banks.size() + "}";
    }
}
