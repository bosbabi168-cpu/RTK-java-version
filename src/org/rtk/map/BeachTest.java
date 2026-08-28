package org.rtk.map;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.rtk.map.AcaraUji.check;
import static org.rtk.map.AcaraUji.jumlahDiPeta;
import static org.rtk.map.AcaraUji.reg;
import static org.rtk.map.AcaraUji.setReg;
import static org.rtk.map.AcaraUji.tik;

/**
 * Gerbang acara Beach War: dari pendaftaran sampai satu regu memenangi ronde.
 *
 * <p>Bentuk keempat, dan lagi-lagi berbeda: poinnya didapat dengan
 * <b>menembak lawan sampai basah</b>. {@code BeachWarNpc.hit(penembak,
 * sasaran)} menambah poin regu penembak, memindahkan sasaran ke titik
 * kembalinya, lalu memanggil {@code winnerCheck} — 50 poin memenangi ronde.
 * Gerbang ini tidak menyentuh angka skornya: ia memanggil {@code hit} dan
 * <b>skripnya</b> yang menghitung.</p>
 *
 * <p>⚠️ Arena Beach War <b>dipilih acak</b> oleh skripnya
 * ({@code getRandomMap}), jadi peta yang diperiksa dibaca dari registry
 * {@code beach_war_current_map} — bukan ditulis di sini.</p>
 *
 * <p>Yang dipercepat hanya JAMNYA — lihat {@link AcaraUji}.</p>
 *
 * <p>Jalankan: {@code ./run.sh beachtest} — map server lain harus mati.</p>
 */
public final class BeachTest {

    private static final Logger log = LogManager.getLogger(BeachTest.class);

    /** Ruang tunggu tempat pemain mendaftar. */
    private static final int PETA_TUNGGU = 15021;

    /** {@code start()} menuntut LEBIH dari enam pemain. */
    private static final int PEMAIN = 8;

    /** Poin yang dituntut `winnerCheck` untuk memenangi ronde. */
    private static final int POIN_MENANG = 50;

    private static final String[] KUNCI = {
        "beach_war_open", "beach_war_start_time", "beach_war_started",
        "beach_war_wait_time", "beach_war_warped_players",
        "beach_war_current_map", "beach_war_red_point", "beach_war_blue_point",
        "beach_war_red_wins", "beach_war_blue_wins", "beach_war_round",
        "beach_war_hits_to_kill", "beach_war_end_timer", "beach_war_players",
    };

    private BeachTest() {
    }

    public static void main(String[] args) {
        MapServer.boot(args);

        java.util.Map<String, Integer> simpanan = java.util.Map.of();
        List<User> pemain = List.of();
        try {
            simpanan = AcaraUji.simpanRegistry(KUNCI);
            pemain = siapkan();
            jalankanPertandingan(pemain);
        } catch (RuntimeException e) {
            check("gerbang beach berjalan tanpa pengecualian", false);
            log.error("gerbang beach berhenti", e);
        } finally {
            AcaraUji.bersihkanPemain(pemain);
            AcaraUji.pulihkanRegistry(simpanan);
            check("tidak ada pemain uji yang tertinggal di ruang tunggu",
                    jumlahDiPeta(PETA_TUNGGU) == 0);
        }
        AcaraUji.ringkas("BEACH");
    }

    private static List<User> siapkan() {
        check("peta ruang tunggu Beach (" + PETA_TUNGGU + ") dimuat",
                MapServer.world.get(PETA_TUNGGU) != null);
        List<User> pemain = AcaraUji.isiPeta(PETA_TUNGGU, PEMAIN, "UjiBeach", null);
        // Hasil dialog pendaftaran; sisanya tetap dikerjakan skrip.
        for (int i = 0; i < pemain.size(); i++) {
            pemain.get(i).scriptPlayer().registry.put("beach_war_team",
                    i % 2 == 0 ? 1 : 2);
        }
        check(PEMAIN + " pemain mendaftar di ruang tunggu",
                jumlahDiPeta(PETA_TUNGGU) == PEMAIN);
        return pemain;
    }

    private static void jalankanPertandingan(List<User> pemain) {
        log.info("=== membuka pendaftaran Beach War ===");
        for (String k : KUNCI) {
            setReg(k, 0);
        }
        check("BeachWarNpc.open() berjalan",
                MapServer.scriptEngine.doScript("BeachWarNpc", "open"));
        check("pendaftaran terbuka", reg("beach_war_open") == 1);
        check("tenggat pendaftaran 15 menit ke depan",
                reg("beach_war_start_time") > System.currentTimeMillis() / 1000L + 800);
        // ⚠️ `open()` MEMBALIK aturan "berapa tembakan untuk membasahi"
        // (1 atau 2) tiap kali dipanggil. Ronde satu-tembakan yang diuji di
        // sini karena itu dipastikan lebih dulu, bukan diasumsikan.
        if (reg("beach_war_hits_to_kill") != 1) {
            setReg("beach_war_hits_to_kill", 1);
        }
        check("ronde ini satu tembakan untuk membasahi",
                reg("beach_war_hits_to_kill") == 1);

        log.info("=== pendaftaran ditutup (15 menit dipercepat) ===");
        setReg("beach_war_start_time", (int) (System.currentTimeMillis() / 1000L) - 1);
        tik();
        check("pendaftaran ditutup sendiri oleh skrip",
                reg("beach_war_open") == 0);
        int arena = reg("beach_war_current_map");
        log.info("[beach] arena yang dipilih skrip: peta {}", arena);
        check("skrip memilih satu peta arena", arena > 0);
        check("peta arena itu benar-benar dimuat",
                MapServer.world.get(arena) != null);
        check("pemain dipindahkan ke arena oleh `start()`",
                jumlahDiPeta(arena) == PEMAIN);

        log.info("=== masuk arena & mulai ===");
        // ⚠️ `enterArena` dan `begin` memakai perbandingan SAMA DENGAN
        // (`diff == 30`, `diff == 0`), bukan lebih besar — jadi jamnya harus
        // mendarat tepat, bukan sekadar dilewati.
        long sekarang = System.currentTimeMillis() / 1000L;
        setReg("beach_war_wait_time", (int) (sekarang + 30));
        tik();
        sekarang = System.currentTimeMillis() / 1000L;
        setReg("beach_war_wait_time", (int) sekarang);
        tik();
        check("pertandingan berjalan (`beach_war_started`)",
                reg("beach_war_started") == 1);

        log.info("=== mencetak poin dengan MENEMBAK lawan ===");
        User penembak = pemain.get(0);   // regu 1 (merah)
        User sasaran = pemain.get(1);    // regu 2 (biru)
        int poin = tembakanNyata(penembak, sasaran, arena);
        check("satu tembakan memberi satu poin ke regu merah", poin == 1);
        check("regu biru tidak ikut dapat poin",
                reg("beach_war_blue_point") == 0);

        log.info("=== poin dikumpulkan sampai ronde dimenangi ===");
        // ⚠️ Berhenti pada KEMENANGAN RONDE, bukan pada angka 50: `roundWin`
        // menyetel ulang skornya untuk ronde berikutnya, jadi menunggu
        // "skor = 50" akan berputar lagi dan memenangi ronde KEDUA tanpa
        // disadari — persis yang terjadi pada percobaan pertama gerbang ini.
        int tembakan = 1;   // satu tembakan sudah dilepas di atas
        while (reg("beach_war_red_wins") == 0 && tembakan < POIN_MENANG * 3) {
            tembakanNyata(penembak, sasaran, arena);
            tembakan++;
        }
        log.info("[beach] {} tembakan, menang {} ronde, nomor ronde {}, skor {}",
                tembakan, reg("beach_war_red_wins"), reg("beach_war_round"),
                reg("beach_war_red_point"));
        check("ronde dimenangi regu merah oleh `winnerCheck`",
                reg("beach_war_red_wins") == 1);
        check("tepat " + POIN_MENANG + " tembakan dibutuhkan",
                tembakan == POIN_MENANG);
        check("skor ronde disetel ulang untuk ronde berikutnya",
                reg("beach_war_red_point") == 0);
        check("nomor ronde naik jadi 1", reg("beach_war_round") == 1);

        log.info("=== penutupan ===");
        check("BeachWarNpc.stop() berjalan",
                MapServer.scriptEngine.doScript("BeachWarNpc", "stop"));
        check("skor tetap nol sesudah dihentikan",
                reg("beach_war_red_point") == 0 && reg("beach_war_blue_point") == 0);
        check("pertandingan berhenti", reg("beach_war_started") == 0);
    }

    /**
     * Satu tembakan sungguhan lewat {@code BeachWarNpc.hit}.
     *
     * @return selisih poin regu merah sesudah tembakan
     */
    private static int tembakanNyata(User penembak, User sasaran, int arena) {
        int sebelum = reg("beach_war_red_point");
        // Sasaran dikembalikan ke arena: `hit` memindahkannya ke titik
        // kembali tiap kali kena, persis seperti di dunia nyata.
        AcaraUji.pindahkan(sasaran, arena, sasaran.x, sasaran.y);
        MapServer.scriptEngine.doScript("BeachWarNpc", "hit",
                MapServer.scriptEngine.playerRef(penembak.scriptPlayer()),
                MapServer.scriptEngine.playerRef(sasaran.scriptPlayer()));
        return reg("beach_war_red_point") - sebelum;
    }
}
