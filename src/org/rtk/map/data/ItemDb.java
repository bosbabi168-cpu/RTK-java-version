package org.rtk.map.data;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;

/**
 * Port sebagian dari itemdb.c — hanya bagian yang dibutuhkan untuk
 * menggambar karakter: {@code itemdb_look()} dan {@code itemdb_lookcolor()}.
 *
 * <p>Di C, {@code item_db} dulunya berkas teks; sekarang isinya datang dari
 * tabel {@code Items} (konstanta {@code ITEMDB_FILE} di header tinggal
 * peninggalan). Sisa ladang item — harga, syarat, efek — menyusul bersama
 * subsistem barang; yang dimuat di sini sengaja dibatasi supaya paket gambar
 * bisa jalan lebih dulu tanpa menunggu itu.</p>
 */
public final class ItemDb {

    private static final Logger log = LogManager.getLogger(ItemDb.class);

    /** Tampilan satu jenis barang. */
    public record Look(int look, int lookColor, int icon, int iconColor) {
    }

    /** Bonus stat yang diberikan sebuah barang saat dikenakan. */
    public record Stats(int vita, int mana, int might, int will, int grace,
                        int armor, int hit, int dam, int protection, int healing,
                        int minSdam, int maxSdam, int minLdam, int maxLdam) {
    }

    /**
     * Data satu jenis barang yang dibutuhkan skrip dan toko.
     *
     * @param stackAmount berapa banyak muat dalam satu slot; 0 atau 1 berarti
     *                    tidak menumpuk sehingga tiap keping memakan slot
     * @param maxAmount   batas total yang boleh dimiliki; 0 = tanpa batas
     */
    /**
     * Syarat memakai/mengenakan barang, plus dua bendera lempar.
     *
     * <p>Dikumpulkan jadi satu record alih-alih delapan ladang lepas supaya
     * menambah kolom berikutnya tidak memecah <b>setiap</b> titik
     * pendaftaran di uji — itu sudah terjadi berkali-kali (lihat catatan
     * arity di CLAUDE.md).</p>
     *
     * @param level  {@code ItmLevel} — level minimum
     * @param might  {@code ItmMightRequired}
     * @param sex    {@code ItmSex} — 2 berarti untuk siapa saja
     * @param path   {@code ItmPthId} — 0 berarti tanpa syarat jalur
     * @param mark   {@code ItmMark} — tanda minimum
     * @param unequip {@code ItmUnequip} — ⚠️ <b>kebalikan namanya</b>:
     *                nilai 1 berarti barangnya <b>TIDAK bisa dilepas</b>.
     *                Sekeluarga dengan {@code ItmDroppable} (Peringatan #32)
     *                dan {@code ItmExchangeable} (Peringatan #53).
     * @param thrown {@code ItmThrown}
     * @param thrownConfirm {@code ItmThrownConfirm} — perlu konfirmasi
     *                      sebelum dilempar
     */
    public record Reqs(int level, int might, int sex, int path, int mark,
                       int unequip, int thrown, int thrownConfirm) {

        /** Tanpa syarat apa pun — dipakai barang uji dan bawaan. */
        public static final Reqs NONE = new Reqs(0, 0, 2, 0, 0, 0, 0, 0);
    }

    public record Info(long id, String name, String display, String buyText,
                       String text,
                       int type, int breakOnDeath, long expiresAt, int exchangeable,
                       int buyPrice, int sellPrice,
                       int stackAmount, int maxAmount, int sound, int durability,
                       int protectedValue, int droppable,
                       Look look, Stats stats, Reqs reqs) {

        /**
         * Ragam tanpa {@link Reqs} — barang tanpa syarat.
         *
         * <p>Ada supaya penambahan kolom syarat tidak memecah puluhan
         * pendaftaran barang uji yang memang tidak peduli syaratnya.</p>
         */
        public Info(long id, String name, String display, String buyText, String text,
                    int type, int breakOnDeath, long expiresAt, int exchangeable,
                    int buyPrice, int sellPrice, int stackAmount, int maxAmount,
                    int sound, int durability, int protectedValue, int droppable,
                    Look look, Stats stats) {
            this(id, name, display, buyText, text, type, breakOnDeath, expiresAt,
                    exchangeable, buyPrice, sellPrice, stackAmount, maxAmount,
                    sound, durability, protectedValue, droppable, look, stats,
                    Reqs.NONE);
        }

        /** Nama yang dilihat pemain; jatuh ke nama skrip bila kosong. */
        public String tampilan() {
            return display == null || display.isEmpty() ? name : display;
        }
    }

    private static final Look KOSONG = new Look(0, 0, 0, 0);
    private static final Stats TANPA_STAT =
            new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private static final Info TIDAK_DIKENAL =
            new Info(0, "", "", "", "", 0, 0, 0L, 0, 0, 0, 1, 0, 0, 0, 0, 0, KOSONG, TANPA_STAT);

    private final Map<Long, Look> byId = new HashMap<>();
    private final Map<Long, Info> infoById = new HashMap<>();

    /** Nama barang (huruf kecil) -&gt; data; nama di skrip tidak peka besar-kecil. */
    private final Map<String, Info> infoByName = new HashMap<>();

    /**
     * Daftarkan satu jenis barang. Dipakai {@link #load} dan uji yang
     * berjalan tanpa database; nama pertama menang bila ada duplikat, sama
     * seperti pencarian berurutan di C.
     */
    public void register(Info info) {
        byId.put(info.id(), info.look());
        infoById.put(info.id(), info);
        if (!info.name().isEmpty()) {
            infoByName.putIfAbsent(info.name().toLowerCase(), info);
        }
    }

    /** Data barang menurut id, atau entri kosong bila tak dikenal. */
    /** Seluruh jenis barang yang termuat — dipakai uji dan sapuan data. */
    public java.util.Collection<Info> all() {
        return infoById.values();
    }

    public Info info(long itemId) {
        return infoById.getOrDefault(itemId, TIDAK_DIKENAL);
    }

    /** Data barang menurut nama, atau null bila tak dikenal. */
    public Info infoByName(String name) {
        return name == null ? null : infoByName.get(name.toLowerCase());
    }

    /**
     * itemdb_id(): id barang dari namanya, atau 0 bila tak dikenal.
     * Dipakai skrip dan toko, yang menyebut barang dengan nama.
     */
    public long idOf(String name) {
        Info i = infoByName(name);
        return i == null ? 0 : i.id();
    }

    /**
     * Jenis barang (enum di itemdb.h:23). Hanya yang benar-benar dipakai
     * kode ini yang diberi nama.
     *
     * <p>⚠️ Rentang {@code 3..17} adalah <b>perlengkapan</b>: hanya jenis
     * itu yang membawa ketahanan pada paket inventaris. Ini rentang, bukan
     * daftar — jadi jangan diperiksa satu per satu.</p>
     */
    // enum jenis barang (map/itemdb.h). Urutannya bagian dari data:
    // `type - 3` adalah slot perlengkapannya, dan itu dipakai langsung
    // di pc_useitem maupun pc_equipitem.
    public static final int ITM_EAT = 0;
    public static final int ITM_USE = 1;
    public static final int ITM_SMOKE = 2;
    public static final int ITM_WEAP = 3;
    public static final int ITM_ARMOR = 4;
    public static final int ITM_SHIELD = 5;
    public static final int ITM_HELM = 6;
    public static final int ITM_LEFT = 7;
    public static final int ITM_RIGHT = 8;
    public static final int ITM_SUBLEFT = 9;
    public static final int ITM_SUBRIGHT = 10;
    public static final int ITM_FACEACC = 11;
    public static final int ITM_CROWN = 12;
    public static final int ITM_MANTLE = 13;
    public static final int ITM_NECKLACE = 14;
    public static final int ITM_BOOTS = 15;
    public static final int ITM_COAT = 16;
    public static final int ITM_HAND = 17;
    public static final int ITM_ETC = 18;
    public static final int ITM_USESPC = 19;
    public static final int ITM_EQUIP_MIN = 3;
    public static final int ITM_EQUIP_MAX = 17;
    public static final int ITM_TRAPS = 20;
    public static final int ITM_BAG = 21;
    public static final int ITM_MAP = 22;
    public static final int ITM_QUIVER = 23;
    public static final int ITM_MOUNT = 24;
    public static final int ITM_FACE = 25;
    public static final int ITM_SET = 26;
    public static final int ITM_SKIN = 27;
    public static final int ITM_HAIR_DYE = 28;
    public static final int ITM_FACEACCTWO = 29;

    /**
     * itemdb_breakondeath(): barang ini hancur saat pemiliknya mati.
     *
     * <p>Kolomnya {@code ItmBoD} — <b>BoD = Break on Death</b>, bukan
     * singkatan lain. Nama "BOD" muncul lagi di {@code getBODItem} dan
     * {@code checkInvBod}, yang semuanya soal barang hancur ini.</p>
     */
    public boolean breaksOnDeath(long itemId) {
        return info(itemId).breakOnDeath() != 0;
    }

    /**
     * itemdb_time(): waktu kedaluwarsa <b>mutlak</b> bawaan jenis barang
     * (detik epoch), bukan lamanya. 0 = tidak pernah kedaluwarsa.
     */
    public long expiresAt(long itemId) {
        return info(itemId).expiresAt();
    }

    /**
     * itemdb_exchangeable(): <b>namanya kebalikan artinya</b>, sama seperti
     * {@code ItmDroppable}. Nilai bukan-nol berarti barangnya <b>TIDAK
     * bisa</b> ditukar antar pemain (42 dari 2.545 barang di data asli).
     */
    public boolean cannotBeExchanged(long itemId) {
        return info(itemId).exchangeable() != 0;
    }

    /** itemdb_protected(): nilai perlindungan bawaan barang. */
    public int protectedOf(long itemId) {
        return info(itemId).protectedValue();
    }

    /**
     * itemdb_droppable(): <b>namanya menyesatkan.</b> Di
     * {@code pc_getitemscript} nilai bukan-nol berarti barangnya
     * <b>TIDAK boleh dipungut</b> pemain biasa ("That item cannot be picked
     * up"); GM tetap bisa. Jadi bacalah sebagai "terkunci di lantai", bukan
     * "boleh dijatuhkan".
     */
    public boolean cannotBePickedUp(long itemId) {
        return info(itemId).droppable() != 0;
    }

    /**
     * itemdb_unequip(): barang ini <b>tidak bisa dilepas</b> sekali dikenakan.
     *
     * <p>⚠️ Kolomnya {@code ItmUnequip} dan artinya <b>kebalikan namanya</b>
     * — nilai 1 berarti terkunci di badan. Sekeluarga dengan
     * {@code ItmDroppable} (Peringatan #32) dan {@code ItmExchangeable}
     * (Peringatan #53); dibungkus di sini supaya tidak menyesatkan lagi.</p>
     */
    public boolean cannotBeUnequipped(long itemId) {
        return info(itemId).reqs().unequip() == 1;
    }

    /** itemdb_thrownconfirm(): melemparnya butuh konfirmasi pemain dulu. */
    public boolean needsThrowConfirm(long itemId) {
        return info(itemId).reqs().thrownConfirm() == 1;
    }

    /** itemdb_stackamount(): berapa muat dalam satu slot (minimal 1). */
    public int stackAmountOf(long itemId) {
        return Math.max(1, info(itemId).stackAmount());
    }

    /**
     * itemdb_search(): tampilan barang, atau nilai nol bila id tak dikenal.
     * Versi C mengembalikan entri kosong yang baru dibuat, jadi pemanggilnya
     * tidak pernah melihat null — ditiru di sini.
     */
    public Look look(long itemId) {
        return byId.getOrDefault(itemId, KOSONG);
    }

    /** itemdb_look(). */
    public int lookOf(long itemId) {
        return look(itemId).look();
    }

    /** itemdb_lookcolor(). */
    public int lookColorOf(long itemId) {
        return look(itemId).lookColor();
    }

    public int count() {
        return byId.size();
    }

    /** Muat tampilan seluruh barang dari tabel `Items`. */
    public int load(Sql sql) {
        long t0 = System.currentTimeMillis();
        byId.clear();
        infoById.clear();
        infoByName.clear();
        int rows = sql.forEachRow(
                "SELECT `ItmId`,`ItmIdentifier`,`ItmType`,`ItmLook`,`ItmLookColor`,"
                + "`ItmIcon`,`ItmIconColor`,`ItmBuyPrice`,`ItmSellPrice`,"
                + "`ItmStackAmount`,`ItmMaximumAmount`,`ItmDescription`,`ItmBuyText`,"
                + "`ItmText`,`ItmBoD`,`ItmTimer`,`ItmExchangeable`,"
                + "`ItmSound`,`ItmDurability`,`ItmProtected`,`ItmDroppable`,"
                + "`ItmVita`,`ItmMana`,`ItmMight`,`ItmWill`,`ItmGrace`,`ItmArmor`,"
                + "`ItmHit`,`ItmDam`,`ItmProtection`,`ItmHealing`,"
                + "`ItmMinimumSDamage`,`ItmMaximumSDamage`,"
                + "`ItmMinimumLDamage`,`ItmMaximumLDamage`,"
                + "`ItmLevel`,`ItmMightRequired`,`ItmSex`,`ItmPthId`,`ItmMark`,"
                + "`ItmUnequip`,`ItmThrown`,`ItmThrownConfirm` FROM `Items`",
                rs -> {
                    long id = rs.getLong("ItmId");
                    Look look = new Look(rs.getInt("ItmLook"), rs.getInt("ItmLookColor"),
                            rs.getInt("ItmIcon"), rs.getInt("ItmIconColor"));
                    String nama = rs.getString("ItmIdentifier");
                    String tampil = rs.getString("ItmDescription");
                    String buyText = rs.getString("ItmBuyText");
                    String teks = rs.getString("ItmText");
                    Info info = new Info(id, nama == null ? "" : nama,
                            tampil == null ? "" : tampil,
                            buyText == null ? "" : buyText,
                            teks == null ? "" : teks,
                            rs.getInt("ItmType"),
                            rs.getInt("ItmBoD"), rs.getLong("ItmTimer"),
                            rs.getInt("ItmExchangeable"),
                            rs.getInt("ItmBuyPrice"), rs.getInt("ItmSellPrice"),
                            rs.getInt("ItmStackAmount"), rs.getInt("ItmMaximumAmount"),
                            rs.getInt("ItmSound"), rs.getInt("ItmDurability"),
                            rs.getInt("ItmProtected"), rs.getInt("ItmDroppable"),
                            look,
                            new Stats(rs.getInt("ItmVita"), rs.getInt("ItmMana"),
                                    rs.getInt("ItmMight"), rs.getInt("ItmWill"),
                                    rs.getInt("ItmGrace"), rs.getInt("ItmArmor"),
                                    rs.getInt("ItmHit"), rs.getInt("ItmDam"),
                                    rs.getInt("ItmProtection"), rs.getInt("ItmHealing"),
                                    rs.getInt("ItmMinimumSDamage"),
                                    rs.getInt("ItmMaximumSDamage"),
                                    rs.getInt("ItmMinimumLDamage"),
                                    rs.getInt("ItmMaximumLDamage")),
                            new Reqs(rs.getInt("ItmLevel"),
                                    rs.getInt("ItmMightRequired"),
                                    rs.getInt("ItmSex"), rs.getInt("ItmPthId"),
                                    rs.getInt("ItmMark"), rs.getInt("ItmUnequip"),
                                    rs.getInt("ItmThrown"),
                                    rs.getInt("ItmThrownConfirm")));
                    register(info);
                });
        if (rows < 0) {
            log.error("[ITEM] gagal membaca tabel Items");
            return 0;
        }
        log.info("[ITEM] {} barang dimuat ({} nama unik) dalam {} ms",
                byId.size(), infoByName.size(), System.currentTimeMillis() - t0);
        return byId.size();
    }
}
