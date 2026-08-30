package org.rtk.map;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaValue;

import org.rtk.map.data.MapData;

import static org.rtk.map.AcaraUji.check;
import static org.rtk.map.AcaraUji.jumlahDiPeta;
import static org.rtk.map.AcaraUji.reg;
import static org.rtk.map.AcaraUji.setReg;
import static org.rtk.map.AcaraUji.tik;

/**
 * Gerbang acara Elixir: satu pertandingan penuh, dari pintu dibuka sampai
 * hadiah dan penutupan.
 *
 * <h2>Kenapa gerbangnya memakai penyalaan server SUNGGUHAN</h2>
 *
 * <p>Acara ini menyentuh hampir semua lapisan sekaligus: peta 3042/3036 dan
 * objek pintunya, NPC F1 (pemegang {@code core.gameRegistry}), NPC gawang
 * {@code ElixirGameNpc}, registry sedunia yang tulis-terus ke basis data,
 * kait {@code cronJobSec}, dan skrip acara 1.111 baris. Gerbang yang
 * menyusun dunianya sendiri hanya akan menguji dunia buatannya sendiri —
 * karena itu kelas ini memanggil {@link MapServer#boot} yang sama persis
 * dengan {@code ./run.sh map}, lalu menjalankan pertandingannya sendiri
 * tanpa loop soket.</p>
 *
 * <h2>Yang dipercepat, dan yang TIDAK</h2>
 *
 * <p>Pertandingan sungguhan memakan lebih dari 20 menit: 15 menit pintu
 * terbuka, lalu belasan fase berjeda. Yang dipercepat di sini <b>hanya
 * jamnya</b> — {@code elixirStart} dimundurkan sehingga tenggat fase
 * berikutnya terlampaui. Logikanya sendiri tidak disentuh: tiap peralihan
 * keadaan tetap diputuskan skrip acara, dan urutan keadaannya diperiksa
 * satu per satu.</p>
 *
 * <p>Kemenangan ronde <b>tidak dipalsukan</b>: pemain benar-benar
 * menyerahkan {@code acorn} ke NPC gawang lewat {@code ElixirGameNpc
 * .handItem} — jalur yang sama dengan yang dipakai pemain sungguhan.</p>
 *
 * <p>⚠️ Registry sedunia sekarang TULIS-TERUS ke basis data, jadi gerbang
 * ini mengubah tabel {@code GameRegistry0}. Nilai awalnya disimpan di awal
 * dan dikembalikan di akhir, termasuk bila ujinya gagal.</p>
 *
 * <p>Jalankan: {@code ./run.sh elixirtest} — map server LAIN harus mati
 * (portnya bentrok; lihat Peringatan #127).</p>
 */
public final class ElixirTest {

    private static final Logger log = LogManager.getLogger(ElixirTest.class);

    /** Aula Elixir: tempat pemain menunggu dan regu dibentuk. */
    private static final int PETA_AULA = 3042;

    /** Arena tempat rondenya dimainkan (skrip memilihnya sendiri). */
    private static final int PETA_ARENA = 3036;

    /** Jumlah pemain minimum yang dituntut skrip acara. */
    private static final int PEMAIN = 6;

    private ElixirTest() {
    }

    public static void main(String[] args) {
        MapServer.boot(args);

        java.util.Map<String, Integer> simpanan = java.util.Map.of();
        List<User> pemain = List.of();
        try {
            simpanan = AcaraUji.simpanRegistry(KUNCI);
            pemain = isiAula();
            jalankanPertandingan(pemain);
        } catch (RuntimeException e) {
            check("gerbang elixir berjalan tanpa pengecualian", false);
            log.error("gerbang elixir berhenti", e);
        } finally {
            AcaraUji.bersihkanPemain(pemain);
            AcaraUji.pulihkanRegistry(simpanan);
            check("tidak ada pemain uji yang tertinggal di dunia",
                    jumlahDiPeta(PETA_AULA) == 0 && jumlahDiPeta(PETA_ARENA) == 0);
        }
        AcaraUji.ringkas("ELIXIR");
    }

    // ------------------------------------------------------------------
    // penyiapan
    // ------------------------------------------------------------------

    /** Kunci registry yang disentuh acara ini; dikembalikan di akhir. */
    private static final String[] KUNCI = {
        "elixirState", "elixirStart", "elixirMap", "elixirDye",
        "elixirRedScore", "elixirBlueScore", "elixirRound",
        "elixirRoundWinner", "elixirWinner", "elixirGameClose",
        "minigameEventId", "stalemateCounter",
    };

    /**
     * Isi Aula Elixir dengan pemain uji.
     *
     * <p>Mereka pemain yang sungguhan bagi dunia — masuk {@code onlineChars}
     * dan indeks petak peta — tetapi tanpa soket. Yang diuji acara ini
     * adalah logikanya terhadap benda {@code BL_PC} di peta, dan itulah yang
     * mereka sediakan.</p>
     */
    private static List<User> isiAula() {
        MapData aula = MapServer.world.get(PETA_AULA);
        check("peta Aula Elixir (" + PETA_AULA + ") dimuat", aula != null);
        check("peta arena Elixir (" + PETA_ARENA + ") dimuat",
                MapServer.world.get(PETA_ARENA) != null);
        check("NPC F1 ada, jadi `core` di skrip terisi",
                MapServer.npcs.byId(Npc.F1_NPC) != null);
        check("NPC gawang ElixirGameNpc ada di dunia",
                MapServer.npcs.byName("Elixir NPC") != null);
        if (aula == null) {
            throw new IllegalStateException("peta " + PETA_AULA + " tidak dimuat");
        }

        List<User> keluar = AcaraUji.isiPeta(PETA_AULA, PEMAIN, "UjiElixir", null);
        check(PEMAIN + " pemain uji berdiri di Aula Elixir",
                jumlahDiPeta(PETA_AULA) == PEMAIN);
        return keluar;
    }

    // ------------------------------------------------------------------
    // pertandingan
    // ------------------------------------------------------------------

    private static void jalankanPertandingan(List<User> pemain) {
        log.info("=== membuka acara Elixir ===");
        // Bersihkan sisa jalan sebelumnya supaya ujinya berangkat dari nol.
        for (String k : KUNCI) {
            setReg(k, 0);
        }
        boolean adaInit = MapServer.scriptEngine.doScript("elixir", "init",
                LuaValue.valueOf(30));
        check("elixir.init(30) benar-benar ada dan berjalan", adaInit);
        check("pintu terbuka: keadaan acara jadi 1", reg("elixirState") == 1);
        check("id acara tercatat di registry", reg("minigameEventId") == 30);

        log.info("=== menunggu pintu ditutup (15 menit dipercepat) ===");
        log.info("[elixir] diagnosa: elixirStart={} epoch={} keadaan={}",
                reg("elixirStart"), System.currentTimeMillis() / 1000L,
                reg("elixirState"));
        // ⚠️ Tenggatnya diperiksa dengan `os.time() == mulai + 900` — SAMA
        // DENGAN, bukan lebih besar. Memundurkan jamnya harus mendarat tepat
        // di detik itu, jadi nilainya dihitung dari waktu sekarang.
        AcaraUji.majukanKe("elixirStart", 900);
        tik();
        log.info("[elixir] sesudah satu tik: elixirStart={} epoch={} keadaan={}",
                reg("elixirStart"), System.currentTimeMillis() / 1000L,
                reg("elixirState"));
        check("regu dibentuk sesudah pintu ditutup: keadaan 2",
                reg("elixirState") == 2);

        log.info("=== pembentukan regu ===");
        log.info("[elixir] diagnosa: objectsInMap(3042,BL_PC)={} onlineChars={}",
                MapServer.objectsInMap(PETA_AULA,
                        org.rtk.map.data.BlockList.BL_PC).size(),
                MapServer.onlineChars.size());
        int keadaan = AcaraUji.putarSampai("elixirState", "elixirStart", 5, 200);
        check("keadaan berjalan sampai regu siap (5)", keadaan == 5);
        int merah = 0;
        int biru = 0;
        for (User u : pemain) {
            int t = u.scriptPlayer().registry.getOrDefault("elixirTeam", 0);
            if (t == 1) {
                merah++;
            } else if (t == 2) {
                biru++;
            }
        }
        log.info("[elixir] regu: merah {} biru {}", merah, biru);
        check("seluruh pemain dapat regu", merah + biru == PEMAIN);
        check("regunya seimbang", Math.abs(merah - biru) <= 1);

        log.info("=== arena disiapkan ===");
        keadaan = AcaraUji.putarSampai("elixirState", "elixirStart", 13, 400);
        check("keadaan sampai RONDE BERJALAN (13)", keadaan == 13);
        check("arena yang dipilih skrip adalah peta " + PETA_ARENA,
                reg("elixirMap") == PETA_ARENA);

        // Pemain masuk arena lewat pintu yang baru dibuka — di dunia nyata
        // mereka berjalan sendiri. ⚠️ Tanpa langkah ini tahap hadiah
        // menutup seketika: keadaan 101 berakhir begitu `#pcs == 0` di peta
        // arena, dan ujinya hanya sempat melihat acara yang sudah bubar.
        AcaraUji.pindahSemua(pemain, PETA_ARENA);
        check("seluruh pemain berada di arena", jumlahDiPeta(PETA_ARENA) == PEMAIN);

        log.info("=== ronde 1: satu pemain menyerahkan acorn ke gawang ===");
        int skorSebelum = reg("elixirRedScore") + reg("elixirBlueScore");
        cetakGol(pemain);
        check("skor bertambah lewat NPC gawang, bukan dipalsukan",
                reg("elixirRedScore") + reg("elixirBlueScore") == skorSebelum + 1);
        check("pemenang ronde tercatat", reg("elixirRoundWinner") != 0);
        check("nomor ronde naik jadi 2", reg("elixirRound") == 2);
        check("keadaan pindah ke penutupan ronde (14)", reg("elixirState") == 14);

        log.info("=== ronde 2 ===");
        keadaan = AcaraUji.putarSampai("elixirState", "elixirStart", 13, 400);
        check("ronde berikutnya dimulai lagi (13)", keadaan == 13);
        cetakGol(pemain);
        check("satu regu mencapai 2 poin",
                reg("elixirRedScore") >= 2 || reg("elixirBlueScore") >= 2);

        log.info("=== pertandingan selesai ===");
        keadaan = AcaraUji.putarSampai("elixirState", "elixirStart", 101, 400);
        log.info("[elixir] keadaan akhir {} skor {}/{} pemenang {} peta {}",
                keadaan, reg("elixirRedScore"), reg("elixirBlueScore"),
                reg("elixirWinner"), reg("elixirMap"));
        check("acara masuk tahap hadiah (101)", keadaan == 101);
        check("pemenangnya tercatat", reg("elixirWinner") != 0);
        boolean adaHadiah = false;
        for (Object bl : MapServer.objectsInMap(PETA_ARENA,
                org.rtk.map.data.BlockList.BL_NPC)) {
            if (bl instanceof Npc nd && "elixirPrizeNPC".equalsIgnoreCase(nd.name)) {
                adaHadiah = true;
            }
        }
        check("NPC hadiah muncul di arena", adaHadiah);

        log.info("=== penutupan ===");
        // Skrip menutup sendiri begitu tak ada pemain lagi di arena.
        AcaraUji.pindahSemua(pemain, PETA_AULA);
        int batas = 0;
        while (reg("elixirState") != 0 && batas++ < 400) {
            AcaraUji.tidur(60);
            tik();
        }
        check("acara menutup dirinya sendiri: keadaan kembali 0",
                reg("elixirState") == 0);
        check("skor dan ronde ikut disetel ulang",
                reg("elixirRedScore") == 0 && reg("elixirBlueScore") == 0
                        && reg("elixirRound") == 1);
    }

    /**
     * Satu pemain regu penyerang menyerahkan acorn ke NPC gawang.
     *
     * <p>Ini jalur yang sama dengan pemain sungguhan: {@code handItem} pada
     * {@code ElixirGameNpc}. Barang yang dituntutnya diberikan lebih dulu —
     * acorn di tangan, dan elixir lawan di kantong.</p>
     */
    private static void cetakGol(List<User> pemain) {
        Npc gawang = MapServer.npcs.byName("Elixir NPC");
        if (gawang == null) {
            check("NPC gawang ditemukan untuk mencetak gol", false);
            return;
        }
        for (User u : pemain) {
            int regu = u.scriptPlayer().registry.getOrDefault("elixirTeam", 0);
            if (regu != 1 && regu != 2) {
                continue;
            }
            u.status.inventory.clear();
            check("acorn masuk kantong " + u.name(),
                    u.scriptAddItem("acorn", 1));
            check("elixir lawan masuk kantong " + u.name(),
                    u.scriptAddItem(regu == 1 ? "blue_elixir" : "red_elixir", 1));
            // NPC gawang membaca barang di `invSlot` — slot yang sedang
            // diserahkan pemain.
            u.invSlot = 0;
            log.info("[elixir] gol oleh {} (regu {})", u.name(), regu);
            boolean jalan = MapServer.scriptEngine.doScript("ElixirGameNpc",
                    "handItem",
                    MapServer.scriptEngine.playerRef(u.scriptPlayer()),
                    MapServer.scriptEngine.objectRef(gawang));
            log.info("[elixir] skor sesudah gol: merah {} biru {} (pemenang ronde {})",
                    reg("elixirRedScore"), reg("elixirBlueScore"),
                    reg("elixirRoundWinner"));
            check("ElixirGameNpc.handItem dipanggil untuk " + u.name(), jalan);
            return;
        }
        check("ada pemain berregu yang bisa mencetak gol", false);
    }

    // ------------------------------------------------------------------
    // alat
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // pembersihan
    // ------------------------------------------------------------------
}
