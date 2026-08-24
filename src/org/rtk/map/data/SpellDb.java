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

    /** magicdb_id(): id mantra dari namanya, atau 0 bila tak dikenal. */
    public int idOf(String name) {
        return name == null ? 0 : idByName.getOrDefault(name.toLowerCase(), 0);
    }

    /** magicdb_yname(): nama skrip mantra dari idnya, atau "" bila tak dikenal. */
    public String nameOf(int id) {
        return nameById.getOrDefault(id, "");
    }

    public int count() {
        return idByName.size();
    }

    public int load(Sql sql) {
        long t0 = System.currentTimeMillis();
        idByName.clear();
        nameById.clear();
        int rows = sql.forEachRow("SELECT `SplId`,`SplIdentifier` FROM `Spells`",
                rs -> {
                    int id = rs.getInt("SplId");
                    String nama = rs.getString("SplIdentifier");
                    if (nama == null || nama.isEmpty()) {
                        return;
                    }
                    // nama pertama menang bila ada duplikat, seperti
                    // pencarian berurutan di C
                    idByName.putIfAbsent(nama.toLowerCase(), id);
                    nameById.putIfAbsent(id, nama);
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
