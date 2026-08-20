package org.rtk.map.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Pemeriksa pembaca peta: memindai seluruh `rtkmaps/Accepted/` dan memastikan
 * setiap berkas `.map` terbaca sesuai format, lalu menampilkan satu peta
 * sebagai teks agar hasilnya bisa dilihat mata.
 *
 * Jalankan: {@code java -jar <jar> maptest [path-rtkmaps]}
 */
public final class MapFileTest {

    private static final Logger log = LogManager.getLogger(MapFileTest.class);

    private MapFileTest() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException e) {
            log.error("Gagal membaca folder peta: {}", e.getMessage());
            System.exit(1);
        }
    }

    /** Baca map_path dari conf/map.conf bila ada, jatuh ke map.path di properties. */
    private static String resolveMapPath() {
        String[] fromConf = {null};
        org.rtk.common.Config.read("conf/map.conf", (k, v) -> {
            if ("map_path".equalsIgnoreCase(k)) {
                fromConf[0] = v;
            }
        });
        return fromConf[0] != null ? fromConf[0] : org.rtk.common.Props.get("map.path", "maps");
    }

    private static void run(String[] args) throws IOException {
        // urutan: argumen CLI > map_path (conf/map.conf) > map.path (properties)
        String root = args.length > 0 ? args[0] : resolveMapPath();
        Path dir = Paths.get(root);
        if (!Files.isDirectory(dir)) {
            log.error("Folder peta tidak ditemukan: {} (berikan path sebagai argumen)", dir);
            System.exit(1);
        }

        log.info("=== Memindai {} ===", dir.toAbsolutePath().normalize());

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".map"))
                .forEach(files::add);
        }
        files.sort(Path::compareTo);

        int ok = 0;
        int failed = 0;
        long tiles = 0;
        long blocked = 0;
        long objects = 0;
        MapFile biggest = null;
        List<String> errors = new ArrayList<>();

        long t0 = System.currentTimeMillis();
        for (Path p : files) {
            try {
                MapFile m = MapFile.load(p);
                ok++;
                tiles += m.tileCount();
                blocked += m.blockedCount();
                for (int y = 0; y < m.height(); y++) {
                    for (int x = 0; x < m.width(); x++) {
                        if (m.obj(x, y) != 0) {
                            objects++;
                        }
                    }
                }
                if (biggest == null || m.tileCount() > biggest.tileCount()) {
                    biggest = m;
                }
            } catch (IOException | RuntimeException e) {
                failed++;
                errors.add(p.getFileName() + ": " + e.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - t0;

        log.info("berkas .map terbaca : {}", ok);
        log.info("gagal               : {}", failed);
        log.info("total petak         : {}", String.format("%,d", tiles));
        log.info("petak terhalang     : {} ({}%)", String.format("%,d", blocked),
                tiles == 0 ? 0 : blocked * 100 / tiles);
        log.info("petak berobjek      : {} ({}%)", String.format("%,d", objects),
                tiles == 0 ? 0 : objects * 100 / tiles);
        log.info("peta terbesar       : {}", biggest);
        log.info("waktu muat          : {} ms ({} berkas/detik)", ms,
                ms == 0 ? "-" : String.format("%,d", ok * 1000L / ms));

        for (String e : errors.subList(0, Math.min(10, errors.size()))) {
            log.error("  {}", e);
        }

        // tampilkan satu peta kota agar hasilnya bisa diperiksa dengan mata
        Path sample = dir.resolve("TK000000.map"); // Kugnae, MapId 0
        if (Files.isRegularFile(sample)) {
            MapFile m = MapFile.load(sample);
            log.info("=== Contoh: {} (Kugnae, MapId 0) ===", m);
            log.info("'.' bisa dilewati   '#' tembok   'o' objek");
            for (String line : m.toAscii(64, 24).split("\n")) {
                log.info("  {}", line);
            }
        }

        if (failed > 0) {
            log.error("{} BERKAS GAGAL DIBACA", failed);
            System.exit(1);
        }
        log.info("SEMUA BERKAS PETA BERHASIL DIBACA");
    }
}
