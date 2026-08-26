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
    public int dispelOf(int id) {
        return dispelById.getOrDefault(id, 0);
    }

    public int count() {
        return idByName.size();
    }

    /**
     * Daftarkan satu mantra. Dipakai {@link #load} dan uji yang berjalan
     * tanpa database — nama pertama menang bila ada id/nama ganda, sama
     * seperti pencarian berurutan di C.
     */
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
        idByName.clear();
        nameById.clear();
        displayById.clear();
        tickerById.clear();
        dispelById.clear();
        int rows = sql.forEachRow(
                "SELECT `SplId`,`SplIdentifier`,`SplDescription`,`SplTicker`,`SplDispel`"
                + " FROM `Spells`",
                rs -> register(rs.getInt("SplId"), rs.getString("SplIdentifier"),
                        rs.getString("SplDescription"), rs.getInt("SplTicker"),
                        rs.getInt("SplDispel")));
        if (rows < 0) {
            log.error("[SPELL] gagal membaca tabel Spells");
            return 0;
        }
        log.info("[SPELL] {} mantra dimuat dalam {} ms",
                idByName.size(), System.currentTimeMillis() - t0);
        return idByName.size();
    }
}
