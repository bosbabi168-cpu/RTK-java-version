package org.rtk.map.data;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;

/**
 * Port sebagian dari magicdb (map/magic_db.c) — pemetaan nama mantra ke id.
 *
 * <p>Dibutuhkan {@code pcl_addspell}, yang menerima nama <i>atau</i> nomor:
 * bila argumennya teks, C memanggil {@code magicdb_id(nama)}. Ladang mantra
 * selebihnya (biaya, syarat, animasi) menyusul bersama subsistem mantra.</p>
 */
public final class SpellDb {

    private static final Logger log = LogManager.getLogger(SpellDb.class);

    private final Map<String, Integer> idByName = new HashMap<>();
    private final Map<Integer, String> nameById = new HashMap<>();
    private final Map<Integer, String> displayById = new HashMap<>();
    private final Map<Integer, Integer> tickerById = new HashMap<>();
    private final Map<Integer, Integer> dispelById = new HashMap<>();
    private final Map<Integer, Integer> levelById = new HashMap<>();

    /** magicdb_id(): id mantra dari namanya, atau 0 bila tak dikenal. */
    public int idOf(String name) {
        return name == null ? 0 : idByName.getOrDefault(name.toLowerCase(), 0);
    }

    /** magicdb_yname(): nama skrip mantra dari idnya, atau "" bila tak dikenal. */
    public String nameOf(int id) {
        return nameById.getOrDefault(id, "");
    }

    /**
     * magicdb_name(): nama <b>tampilan</b> mantra (SplDescription).
     *
     * <p>⚠️ Berbeda dari {@link #nameOf}, yang mengembalikan nama skrip
     * (SplIdentifier). Pembagiannya sama dengan mob: identifier untuk
     * mencari kelas Lua, description untuk ditampilkan ke pemain. Paket
     * durasi memakai yang ini; dispatch skrip memakai yang itu.</p>
     */
    public String displayNameOf(int id) {
        return displayById.getOrDefault(id, "");
    }

    /**
     * magicdb_ticker(): apakah mantra ini muncul di bilah durasi klien.
     * Mantra ber-ticker 0 durasinya tetap berjalan, hanya tidak terlihat —
     * {@code clif_send_duration} langsung return untuknya.
     */
    public boolean hasTicker(int id) {
        return tickerById.getOrDefault(id, 0) != 0;
    }

    /**
     * magicdb_dispel(): tingkat kekebalan mantra terhadap penghapusan.
     * {@code flushDuration(dis, ...)} melewati mantra yang nilainya lebih
     * besar dari {@code dis} — makin tinggi, makin sulit dihapus.
     */
    /**
     * magicdb_level(): level mantra menurut <b>nama skripnya</b>.
     *
     * <p>Mengembalikan 0 bila namanya tak dikenal — di C juga begitu, dan
     * skrip memakainya sebagai "mantra ini tidak ada".</p>
     */
    public int levelOf(String yname) {
        Integer id = idByName.get(yname == null ? "" : yname.toLowerCase());
        return id == null ? 0 : levelById.getOrDefault(id, 0);
    }

    /**
     * {@code magicdb_type()}: bentuk mantra, yang menentukan muatan apa yang
     * dibawa perintah merapal.
     *
     * <ul>
     *   <li><b>1</b> — mantra bertanya: klien mengirim teks jawaban;</li>
     *   <li><b>2</b> — mantra bersasaran: klien mengirim id sasaran;</li>
     *   <li><b>5</b> — mantra ke diri sendiri: tanpa muatan.</li>
     * </ul>
     *
     * <p>⚠️ Ragam lain di C langsung {@code return 0} — mantranya tidak
     * dirapal sama sekali. Itu ditiru, bukan "diperbaiki" jadi menerima
     * semuanya: ragam yang tidak dikenal berarti datanya salah, dan merapal
     * dengan muatan yang salah bentuk lebih buruk daripada tidak merapal.</p>
     */
    public int typeOf(int id) {
        return typeById.getOrDefault(id, 0);
    }

    /** {@code magicdb_mute()}: ambang bisu yang membungkam mantra ini. */
    public int muteOf(int id) {
        return muteById.getOrDefault(id, 0);
    }

    /** {@code magicdb_canfail()}: mantra ini bisa ditangkis sasarannya. */
    public boolean canFail(int id) {
        return canFailById.getOrDefault(id, 0) != 0;
    }

    /** {@code magicdb_aether()}: jeda aether bawaan mantra ini, milidetik. */
    public int aetherOf(int id) {
        return aetherById.getOrDefault(id, 0);
    }

    /** Mantra yang dinonaktifkan tidak boleh dirapal sama sekali. */
    public boolean isActive(int id) {
        return activeById.getOrDefault(id, 0) != 0;
    }

    public int dispelOf(int id) {
        return dispelById.getOrDefault(id, 0);
    }

    public int count() {
        return idByName.size();
    }

    /**
     * Id mantra yang <b>bukan mantra</b>: penanda bagian di buku mantra.
     *
     * <p>Di C daftar ini muncul sebagai deretan {@code case ... continue;}
     * di {@code pcl_getunknownspells} dan {@code pcl_getallclassspells} —
     * bukan kolom di database, jadi tidak bisa disaring lewat SQL.
     * Menyertakannya membuat baris kosong muncul di daftar mantra pemain.</p>
     */
    private static final java.util.Set<Integer> PENANDA_BAGIAN = java.util.Set.of(
            0, 100, 200, 300, 400, 500, 1000, 1500, 2000, 2500,
            3000, 3500, 4000, 4500, 5000, 5500, 7000, 10000);

    public static boolean isSectionMarker(int id) {
        return PENANDA_BAGIAN.contains(id);
    }

    /**
     * pcl_getunknownspells(): mantra yang syaratnya terpenuhi untuk pemain
     * dengan tanda dan keselarasan tertentu.
     *
     * <p>Kueri ini menembak database tiap kali, sama seperti di C — daftar
     * mantra bergantung pada tanda dan keselarasan pemain, jadi tidak bisa
     * di-cache per jalur saja.</p>
     *
     * <p>{@code SplAlignment = -1} berarti "untuk semua keselarasan".</p>
     */
    public java.util.List<Integer> candidateSpells(Sql sql, int class1, int class2,
                                                   int mark, int alignment) {
        return kueriMantra(sql,
                "SELECT `SplId` FROM `Spells` WHERE `SplPthId` IN (0, ?, ?)"
                + " AND `SplMark` <= ? AND `SplActive` = 1"
                + " AND (`SplAlignment` = ? OR `SplAlignment` = -1)",
                class1, class2, mark, alignment);
    }

    /** pcl_getallclassspells(): seluruh mantra aktif milik satu jalur. */
    public java.util.List<Integer> classSpells(Sql sql, int pathId) {
        return kueriMantra(sql,
                "SELECT `SplId` FROM `Spells` WHERE `SplActive` = 1 AND `SplPthId` = ?",
                pathId);
    }

    /**
     * Bagian bersama: jalankan kueri, buang penanda bagian, dan batasi 255
     * seperti larik {@code idlist[255]} di C.
     */
    private java.util.List<Integer> kueriMantra(Sql sql, String q, Object... params) {
        java.util.List<Integer> hasil = new java.util.ArrayList<>();
        int rows = sql.forEachRow(q, rs -> {
            int id = rs.getInt("SplId");
            if (!isSectionMarker(id) && hasil.size() < 255) {
                hasil.add(id);
            }
        }, params);
        if (rows < 0) {
            log.error("[SPELL] gagal membaca daftar mantra");
        }
        return hasil;
    }

    /**
     * Daftarkan satu mantra. Dipakai {@link #load} dan uji yang berjalan
     * tanpa database — nama pertama menang bila ada id/nama ganda, sama
     * seperti pencarian berurutan di C.
     */
    private final java.util.Map<Integer, Integer> typeById = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> muteById = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> canFailById = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> aetherById = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> activeById = new java.util.HashMap<>();

    public void register(int id, String yname, String display, int ticker, int dispel) {
        if (yname == null || yname.isEmpty()) {
            return;
        }
        idByName.putIfAbsent(yname.toLowerCase(), id);
        nameById.putIfAbsent(id, yname);
        displayById.putIfAbsent(id, display == null || display.isEmpty() ? yname : display);
        tickerById.putIfAbsent(id, ticker);
        dispelById.putIfAbsent(id, dispel);
    }

    public int load(Sql sql) {
        long t0 = System.currentTimeMillis();
        typeById.clear();
        muteById.clear();
        canFailById.clear();
        aetherById.clear();
        activeById.clear();
        idByName.clear();
        nameById.clear();
        displayById.clear();
        tickerById.clear();
        dispelById.clear();
        levelById.clear();
        int rows = sql.forEachRow(
                "SELECT `SplId`,`SplIdentifier`,`SplDescription`,`SplTicker`,"
                + "`SplDispel`,`SplLevel`,`SplType`,`SplMute`,`SplCanFail`,"
                + "`SplAether`,`SplActive` FROM `Spells`",
                rs -> {
                    int id = rs.getInt("SplId");
                    register(id, rs.getString("SplIdentifier"),
                            rs.getString("SplDescription"), rs.getInt("SplTicker"),
                            rs.getInt("SplDispel"));
                    levelById.put(id, rs.getInt("SplLevel"));
                    typeById.put(id, rs.getInt("SplType"));
                    muteById.put(id, rs.getInt("SplMute"));
                    canFailById.put(id, rs.getInt("SplCanFail"));
                    aetherById.put(id, rs.getInt("SplAether"));
                    activeById.put(id, rs.getInt("SplActive"));
                });
        if (rows < 0) {
            log.error("[SPELL] gagal membaca tabel Spells");
            return 0;
        }
        log.info("[SPELL] {} mantra dimuat dalam {} ms",
                idByName.size(), System.currentTimeMillis() - t0);
        return idByName.size();
    }
}
