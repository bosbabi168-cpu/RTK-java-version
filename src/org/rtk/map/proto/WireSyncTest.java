package org.rtk.map.proto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Gerbang penyelaras {@code Wire.java} antar repo.
 *
 * <h2>Kenapa gerbang ini ada</h2>
 *
 * <p>Klien dikerjakan di repo terpisah (keputusan 27 Agustus 2026), jadi
 * kontrak protokol RTK2 hidup sebagai <b>dua salinan</b>. Duplikasi itu
 * dipilih sadar — riwayat git repo klien harus bersih dari aset pihak lain —
 * tetapi konsekuensinya nyata dan mahal.</p>
 *
 * <p>⚠️ <b>Drift di sini tidak melempar error.</b> Kalau server menambah satu
 * ladang di sebuah bingkai dan klien belum tahu, klien tidak gagal: ia
 * membaca ladang berikutnya di offset yang salah dan menafsirkan byte yang
 * salah, lalu gagal di tempat yang jauh dari sebabnya. Itu kegagalan yang
 * paling mahal dicari di seluruh project ini — dan satu-satunya yang tidak
 * pernah bisa ditangkap oleh uji di salah satu repo saja.</p>
 *
 * <h2>Apa yang dibandingkan</h2>
 *
 * <p>Kedua berkas harus identik <b>kecuali baris {@code package}-nya</b>:
 * server memakai {@code org.rtk.map.proto}, klien {@code org.rtk.proto}.
 * Selain itu satu byte pun tidak boleh berbeda — termasuk komentar, karena
 * komentar di berkas itu memuat alasan tiap keputusan bentuk kabelnya, dan
 * salinan yang komentarnya tertinggal adalah salinan yang sudah mulai
 * melenceng.</p>
 *
 * <h2>Kalau repo kliennya tidak ada</h2>
 *
 * <p>Gerbang ini <b>melewati dirinya sendiri dengan pesan jelas</b>, bukan
 * gagal — pola yang sama dengan {@code dbtest} tanpa MySQL. Repo klien belum
 * di-commit dan tidak selalu ada di mesin yang sama; membuat gerbang ini
 * merah di mesin build akan membuatnya diabaikan, dan gerbang yang diabaikan
 * tidak menjaga apa pun.</p>
 */
public final class WireSyncTest {

    private static final Logger log = LogManager.getLogger(WireSyncTest.class);

    /** Salinan milik repo ini. */
    private static final String SERVER_WIRE = "src/org/rtk/map/proto/Wire.java";

    /**
     * Tempat mencari salinan klien, berurutan.
     *
     * <p>Bisa ditimpa lewat {@code -Drtk.client.path=...} atau argumen
     * pertama, supaya mesin dengan tata letak folder berbeda tidak perlu
     * mengubah kode.</p>
     */
    private static final String[] KANDIDAT = {
        "../RTK-client/src/org/rtk/proto/Wire.java",
        "../RTK-client/src/org/rtk/map/proto/Wire.java",
    };

    private static int failures;

    private WireSyncTest() {
    }

    public static void main(String[] args) {
        Path klien = cariSalinanKlien(args);
        if (klien == null) {
            log.info("LEWATI: repo klien tidak ditemukan — gerbang ini hanya berjalan "
                    + "bila RTK-client ada di mesin yang sama");
            log.info("        (dicari di: {})", String.join(", ", KANDIDAT));
            log.info("        setel lewat -Drtk.client.path=/path/ke/Wire.java bila perlu");
            return;
        }

        Path server = Paths.get(SERVER_WIRE);
        if (!Files.isRegularFile(server)) {
            log.error("FAIL: {} tidak ditemukan — jalankan dari root project", server);
            System.exit(1);
        }

        log.info("membandingkan:");
        log.info("  server : {}", server.toAbsolutePath().normalize());
        log.info("  klien  : {}", klien.toAbsolutePath().normalize());

        try {
            bandingkan(baca(server), baca(klien));
        } catch (IOException e) {
            log.error("FAIL: gagal membaca salah satu berkas", e);
            System.exit(1);
        }

        if (failures == 0) {
            log.info("WIRE SYNC OK — kedua salinan identik (kecuali package)");
        } else {
            log.error("{} PERBEDAAN DITEMUKAN", failures);
            log.error("Wire.java adalah KONTRAK: salin utuh dari satu sisi ke sisi lain, "
                    + "lalu naikkan Wire.VERSION di KEDUANYA.");
            System.exit(1);
        }
    }

    private static Path cariSalinanKlien(String[] args) {
        String paksa = args.length > 0 ? args[0] : System.getProperty("rtk.client.path");
        if (paksa != null && !paksa.isBlank()) {
            Path p = Paths.get(paksa);
            return Files.isRegularFile(p) ? p : null;
        }
        for (String k : KANDIDAT) {
            Path p = Paths.get(k);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static List<String> baca(Path p) throws IOException {
        return Files.readAllLines(p, StandardCharsets.UTF_8);
    }

    /**
     * Bandingkan baris per baris, dengan baris {@code package} dikecualikan.
     *
     * <p>Melaporkan <b>seluruh</b> perbedaan, bukan hanya yang pertama:
     * salinan yang melenceng biasanya melenceng di beberapa tempat sekaligus,
     * dan memperbaikinya satu per satu berarti menjalankan gerbang ini
     * berulang kali tanpa alasan.</p>
     */
    private static void bandingkan(List<String> server, List<String> klien) {
        int n = Math.max(server.size(), klien.size());
        for (int i = 0; i < n; i++) {
            String a = i < server.size() ? server.get(i) : null;
            String b = i < klien.size() ? klien.get(i) : null;

            if (a != null && b != null && a.equals(b)) {
                continue;
            }
            if (isPackage(a) && isPackage(b)) {
                continue;   // satu-satunya perbedaan yang memang diharapkan
            }

            failures++;
            if (a == null) {
                log.error("FAIL: baris {} hanya ada di KLIEN:  {}", i + 1, b);
            } else if (b == null) {
                log.error("FAIL: baris {} hanya ada di SERVER: {}", i + 1, a);
            } else {
                log.error("FAIL: baris {} berbeda", i + 1);
                log.error("        server : {}", a);
                log.error("        klien  : {}", b);
            }
        }
        if (server.size() != klien.size()) {
            log.error("FAIL: panjang berkas berbeda — server {} baris, klien {} baris",
                    server.size(), klien.size());
            failures++;
        }
    }

    private static boolean isPackage(String s) {
        return s != null && s.strip().startsWith("package ") && s.strip().endsWith(";");
    }
}
