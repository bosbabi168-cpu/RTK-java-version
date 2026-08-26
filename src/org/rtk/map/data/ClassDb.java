package org.rtk.map.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Port of leveldb_read() / classdb_level() (map/class_db.c) — tabel
 * pengalaman kumulatif per path.
 *
 * <p>Berbeda dari kebanyakan data lain yang sudah pindah ke MySQL, tabel ini
 * <b>masih dibaca dari berkas</b> di versi C: {@code db/level_db.txt}.
 * (Konstanta {@code ITEMDB_FILE}, {@code MOBDB_FILE}, dsb. di header C sudah
 * jadi peninggalan — pembacanya kini memakai database. Hanya level_db dan
 * guide_db yang benar-benar masih berupa berkas.)</p>
 *
 * <h3>Bentuk berkas</h3>
 * Satu baris per path, dipisah koma, baris berawalan {@code //} adalah
 * komentar:
 * <pre>
 *   path,exp_untuk_lv1,exp_untuk_lv2,...,exp_untuk_lv98
 * </pre>
 * Nilainya <b>kumulatif</b>: kolom ke-n adalah total pengalaman yang
 * dibutuhkan untuk mencapai level n, bukan selisihnya.
 *
 * <p>Versi C membaca ke {@code db->level[x]} untuk {@code x} dari 1 sampai
 * 98, jadi indeks 0 tidak pernah diisi — ditiru di sini supaya
 * {@code level(path, 0)} tetap mengembalikan 0 seperti aslinya.</p>
 */
public final class ClassDb {

    private static final Logger log = LogManager.getLogger(ClassDb.class);

    /** Batas atas indeks level, mengikuti {@code x < 99} di C. */
    public static final int MAX_LEVEL = 99;

    /** path -&gt; tabel pengalaman kumulatif, indeks = level. */
    private final Map<Integer, long[]> levels = new HashMap<>();

    /**
     * classdb_level(): total pengalaman untuk mencapai {@code level} pada
     * {@code path}. Mengembalikan 0 bila path atau level di luar tabel —
     * sama seperti versi C yang mengembalikan 0 saat path tidak dikenal.
     */
    public long level(int path, int level) {
        long[] t = levels.get(path);
        if (t == null || level < 0 || level >= t.length) {
            return 0;
        }
        return t[level];
    }

    /**
     * classdb_path(): jalur (path) sebuah kelas, dari tabel {@code Paths}.
     *
     * <p>⚠️ Yang dibaca kolom <b>{@code PthType}</b>, bukan {@code PthId} —
     * di C ia diikat ke ladang bernama {@code path}, sehingga mudah
     * disangka nomor barisnya sendiri. Kelas tak dikenal menjawab 0, dan
     * 0 punya arti khusus di pemanggilnya (dilewati).</p>
     */
    private final java.util.Map<Integer, Integer> pathByClass = new java.util.HashMap<>();

    public int pathOf(int classId) {
        return pathByClass.getOrDefault(classId, 0);
    }

    /** Muat pemetaan kelas -&gt; jalur dari tabel {@code Paths}. */
    public int loadPaths(org.rtk.common.Sql sql) {
        pathByClass.clear();
        int rows = sql.forEachRow("SELECT `PthId`,`PthType` FROM `Paths`",
                rs -> pathByClass.put(rs.getInt("PthId"), rs.getInt("PthType")));
        if (rows < 0) {
            log.error("[CLASS] gagal membaca tabel Paths");
            return 0;
        }
        log.info("[CLASS] {} jalur dimuat dari tabel Paths", pathByClass.size());
        return pathByClass.size();
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    public int pathCount() {
        return levels.size();
    }

    /**
     * leveldb_read(): muat {@code level_db.txt} dari folder yang diberikan.
     *
     * <p>Versi C memanggil {@code exit(1)} bila berkasnya hilang. Di sini
     * kegagalan dilaporkan dan server tetap hidup — tanpa tabel ini yang
     * rusak hanya persentase bar pengalaman, bukan seluruh server.</p>
     *
     * @return jumlah path yang termuat
     */
    public int load(String dbPath) {
        Path p = Paths.get(dbPath).resolve("level_db.txt");
        levels.clear();

        List<String> lines;
        try {
            lines = Files.readAllLines(p, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            log.error("[CLASS] tidak bisa membaca {} — bar pengalaman akan salah: {}",
                    p.toAbsolutePath(), e.getMessage());
            return 0;
        }

        int bad = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("//")) {
                continue;
            }
            String[] col = t.split(",", -1);
            if (col.length < 2) {
                bad++;
                continue;
            }
            int path;
            try {
                path = Integer.parseInt(col[0].trim());
            } catch (NumberFormatException e) {
                bad++;
                continue;
            }

            long[] tab = new long[MAX_LEVEL];
            // mulai dari 1, persis seperti `for (x = 1; x < 99; x++)` di C:
            // indeks 0 sengaja dibiarkan kosong
            for (int x = 1; x < MAX_LEVEL && x < col.length; x++) {
                String v = col[x].trim();
                if (v.isEmpty()) {
                    continue;
                }
                try {
                    tab[x] = Long.parseUnsignedLong(v);
                } catch (NumberFormatException e) {
                    // C memakai strtoul yang mengembalikan 0 pada teks tak
                    // terbaca — biarkan 0, jangan gagalkan seluruh baris
                    bad++;
                }
            }
            levels.put(path, tab);
        }

        if (bad > 0) {
            log.warn("[CLASS] {} nilai tidak terbaca di {}", bad, p);
        }
        log.info("[CLASS] tabel pengalaman {} path dimuat dari {}", levels.size(), p);
        return levels.size();
    }
}
