package org.rtk.map;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.mmo.CharStatus;
import org.rtk.map.data.MapData;

/**
 * Perkakas bersama gerbang acara ({@code elixirtest}, {@code carnagetest}).
 *
 * <h2>Aturannya satu: jalankan acaranya, jangan menirunya</h2>
 *
 * <p>Gerbang acara memanggil {@link MapServer#boot} yang SAMA dengan
 * {@code ./run.sh map} — peta, NPC, barang, mantra, skrip, registry sedunia,
 * dan kait cron. Yang boleh dipercepat <b>hanya jamnya</b>; tiap peralihan
 * keadaan tetap diputuskan skrip acara.</p>
 *
 * <p>⚠️ Registry sedunia kini TULIS-TERUS ke basis data, jadi setiap gerbang
 * acara wajib menyimpan nilai awalnya dan mengembalikannya — termasuk bila
 * ujinya gagal di tengah. Lihat docs/PERINGATAN.md #137.</p>
 */
final class AcaraUji {

    private static final Logger log = LogManager.getLogger(AcaraUji.class);

    static int gagal;
    static int lulus;

    private AcaraUji() {
    }

    // ------------------------------------------------------------------
    // pemeriksaan
    // ------------------------------------------------------------------

    static void check(String apa, boolean ok) {
        if (ok) {
            lulus++;
            log.info("PASS: {}", apa);
        } else {
            gagal++;
            log.error("FAIL: {}", apa);
        }
    }

    static void ringkas(String nama) {
        log.info("");
        if (gagal == 0) {
            log.info("ALL {} TESTS PASSED ({} pemeriksaan)", nama, lulus);
        } else {
            log.error("{} dari {} pemeriksaan GAGAL", gagal, lulus + gagal);
            System.exit(1);
        }
        System.exit(0);
    }

    // ------------------------------------------------------------------
    // registry sedunia
    // ------------------------------------------------------------------

    static int reg(String nama) {
        return MapServer.scriptEngine.gameRegGet(nama);
    }

    static void setReg(String nama, int nilai) {
        MapServer.scriptEngine.gameRegSet(nama, nilai);
    }

    static java.util.Map<String, Integer> simpanRegistry(String[] kunci) {
        java.util.Map<String, Integer> ke = new java.util.LinkedHashMap<>();
        for (String k : kunci) {
            ke.put(k, reg(k));
        }
        log.info("[acara] registry sebelum uji: {}", ke);
        return ke;
    }

    static void pulihkanRegistry(java.util.Map<String, Integer> simpanan) {
        for (var e : simpanan.entrySet()) {
            setReg(e.getKey(), e.getValue());
        }
        boolean pulih = true;
        for (var e : simpanan.entrySet()) {
            if (reg(e.getKey()) != e.getValue()) {
                pulih = false;
            }
        }
        check("registry sedunia kembali seperti sebelum uji", pulih);
    }

    // ------------------------------------------------------------------
    // pemain uji
    // ------------------------------------------------------------------

    /**
     * Isi satu peta dengan pemain uji.
     *
     * <p>Mereka pemain yang sungguhan bagi dunia — masuk {@code onlineChars}
     * dan indeks petak peta — tetapi tanpa soket. Logika acara bekerja atas
     * benda {@code BL_PC} di peta, dan itulah yang mereka sediakan.</p>
     *
     * @param kelas nomor kelas per pemain (0 = tidak diatur), untuk acara
     *              yang membagi regu menurut jalur
     */
    static List<User> isiPeta(int peta, int berapa, String awalanNama, int[] kelas) {
        MapData m = MapServer.world.get(peta);
        if (m == null) {
            throw new IllegalStateException("peta " + peta + " tidak dimuat");
        }
        List<User> keluar = new ArrayList<>();
        int[] petak = titikAman(m, berapa);
        for (int i = 0; i < berapa; i++) {
            CharStatus st = new CharStatus();
            st.id = 900_001 + i;
            st.name = awalanNama + (i + 1);
            st.level = 50;
            st.maxInv = 20;
            st.baseHp = 1000;
            st.baseMp = 1000;
            if (kelas != null) {
                st.charClass = kelas[i % kelas.length];
            }
            User sd = new User(9000 + i, st);
            sd.m = peta;
            sd.x = petak[i * 2];
            sd.y = petak[i * 2 + 1];
            sd.id = st.id;
            m.addBlock(sd);
            MapServer.onlineChars.put(sd.fd, sd);
            keluar.add(sd);
        }
        return keluar;
    }

    /** Petak yang bisa dilalui di peta itu, sebanyak yang diminta. */
    static int[] titikAman(MapData m, int berapa) {
        int[] keluar = new int[berapa * 2];
        int n = 0;
        for (int y = 1; y < 60 && n < berapa; y++) {
            for (int x = 1; x < 40 && n < berapa; x++) {
                if (m.walkable(x, y)) {
                    keluar[n * 2] = x;
                    keluar[n * 2 + 1] = y;
                    n++;
                }
            }
        }
        if (n < berapa) {
            throw new IllegalStateException("peta tidak punya " + berapa
                    + " petak yang bisa dilalui");
        }
        return keluar;
    }

    static int jumlahDiPeta(int m) {
        int n = 0;
        for (User u : MapServer.onlineChars.values()) {
            if (u.m == m) {
                n++;
            }
        }
        return n;
    }

    static void pindahkan(User sd, int m, int x, int y) {
        MapData lama = MapServer.world.get(sd.m);
        if (lama != null) {
            lama.delBlock(sd);
        }
        MapData baru = MapServer.world.get(m);
        sd.m = m;
        sd.x = x;
        sd.y = y;
        if (baru != null) {
            baru.addBlock(sd);
        }
    }

    /** Pindahkan seluruh pemain ke satu peta, di petak yang bisa dilalui. */
    static void pindahSemua(List<User> pemain, int peta) {
        int[] petak = titikAman(MapServer.world.get(peta), pemain.size());
        for (int i = 0; i < pemain.size(); i++) {
            pindahkan(pemain.get(i), peta, petak[i * 2], petak[i * 2 + 1]);
        }
    }

    static void bersihkanPemain(List<User> pemain) {
        for (User u : pemain) {
            MapData m = MapServer.world.get(u.m);
            if (m != null) {
                m.delBlock(u);
            }
            MapServer.onlineChars.remove(u.fd);
        }
    }

    // ------------------------------------------------------------------
    // jam & tik
    // ------------------------------------------------------------------

    /** Satu tik cronjob — persis yang dipanggil timer server tiap detik. */
    static void tik() {
        MapServer.scriptEngine.doScript("cronJobSec", "cronJobSec");
    }

    static void tidur(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Mundurkan penanda mulai acara sehingga tenggat {@code detik} lewat.
     *
     * <p>⚠️ Hanya JAMNYA yang dimundurkan. Peralihan keadaannya tetap
     * diputuskan skrip acara pada tik berikutnya.</p>
     */
    static void majukanKe(String kunciMulai, int detik) {
        long sekarang = System.currentTimeMillis() / 1000L;
        setReg(kunciMulai, (int) (sekarang - detik));
    }

    /**
     * Tik sampai keadaan yang dituju tercapai, memundurkan jamnya tiap kali
     * keadaannya tidak berubah.
     *
     * <p>⚠️ Jeda antar tik DIPERLUKAN, bukan kemalasan: sebagian fase hanya
     * berjalan pada tik yang jatuh di detik kelipatan tertentu
     * ({@code os.time() % 5 == 0} di Elixir, {@code % 2} dan {@code % 3} di
     * Carnage). Menembakkan ratusan tik dalam satu detik yang sama tidak
     * pernah menyentuh syarat itu, dan ujinya gagal karena JAM, bukan karena
     * logikanya.</p>
     *
     * @return keadaan terakhir yang terlihat
     */
    static int putarSampai(String kunciKeadaan, String kunciMulai,
                           int tujuan, int batasTik) {
        int sebelum = reg(kunciKeadaan);
        for (int i = 0; i < batasTik; i++) {
            tidur(120);
            tik();
            int kini = reg(kunciKeadaan);
            if (kini == tujuan) {
                return kini;
            }
            if (kini == sebelum) {
                majukanKe(kunciMulai, 31 + i);
            } else {
                sebelum = kini;
            }
        }
        return reg(kunciKeadaan);
    }
}
