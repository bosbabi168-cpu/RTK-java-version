package org.rtk.map;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaValue;

import static org.rtk.map.AcaraUji.check;
import static org.rtk.map.AcaraUji.jumlahDiPeta;
import static org.rtk.map.AcaraUji.putarSampai;
import static org.rtk.map.AcaraUji.reg;
import static org.rtk.map.AcaraUji.setReg;
import static org.rtk.map.AcaraUji.tik;

/**
 * Gerbang acara Carnage: satu pertandingan penuh sampai juara.
 *
 * <p>Pasangan {@link ElixirTest}, tetapi acaranya berbeda bentuk: regunya
 * dibagi menurut <b>jalur kelas</b> (warrior/rogue/mage/poet) lalu diwarnai
 * berselang-seling menjadi empat kubu (hitam 60, putih 61, merah 63, biru
 * 66), dan satu ronde dimenangi bukan dengan menyerahkan barang melainkan
 * dengan menjadi <b>satu-satunya warna yang masih hidup</b> di peta arena
 * (`carnages.lua` keadaan 13).</p>
 *
 * <p>Karena itu ronde di sini dimenangi dengan cara yang sama seperti di
 * dunia nyata: pemain kubu lain <b>mati</b> ({@code state = 1}), dan skrip
 * acara sendiri yang menghitung siapa yang tersisa. Skor, pergantian ronde,
 * dan pengumuman juara seluruhnya keluar dari skripnya.</p>
 *
 * <p>Yang dipercepat hanya JAMNYA — lihat {@link AcaraUji}.</p>
 *
 * <p>Jalankan: {@code ./run.sh carnagetest} — map server lain harus mati
 * (portnya bentrok; Peringatan #127).</p>
 */
public final class CarnageTest {

    private static final Logger log = LogManager.getLogger(CarnageTest.class);

    /** Ruang tunggu Carnage: tempat regu dibentuk. */
    private static final int PETA_TUNGGU = 3010;

    /** Arena tempat rondenya dimainkan (skrip memilihnya sendiri). */
    private static final int PETA_ARENA = 3017;

    /** Jumlah pemain minimum yang dituntut skrip acara. */
    private static final int PEMAIN = 8;

    /** Warna kubu menurut `gfxDye` di `carnages.lua`. */
    private static final int[] WARNA = {60, 61, 63, 66};

    private static final String[] KUNCI = {
        "carnageState", "carnageStart", "carnageMap", "carnageDye",
        "carnageRedScore", "carnageBlackScore", "carnageWhiteScore",
        "carnageBlueScore", "carnageRoundWinner", "carnagePoetDone",
        "carnageRogueDone", "carnageMageDone", "carnageTopMagePicked",
        "carnageMaxHealth", "carnageMaxMagic", "minigameEventId",
    };

    private CarnageTest() {
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
            check("gerbang carnage berjalan tanpa pengecualian", false);
            log.error("gerbang carnage berhenti", e);
        } finally {
            AcaraUji.bersihkanPemain(pemain);
            AcaraUji.pulihkanRegistry(simpanan);
            check("tidak ada pemain uji yang tertinggal di dunia",
                    jumlahDiPeta(PETA_TUNGGU) == 0 && jumlahDiPeta(PETA_ARENA) == 0);
        }
        AcaraUji.ringkas("CARNAGE");
    }

    private static List<User> siapkan() {
        check("peta ruang tunggu Carnage (" + PETA_TUNGGU + ") dimuat",
                MapServer.world.get(PETA_TUNGGU) != null);
        check("peta arena Carnage (" + PETA_ARENA + ") dimuat",
                MapServer.world.get(PETA_ARENA) != null);
        check("NPC F1 ada, jadi `core` di skrip terisi",
                MapServer.npcs.byId(Npc.F1_NPC) != null);

        // ⚠️ Kelasnya BERBEDA-BEDA dengan sengaja: skrip membagi regu menurut
        // jalur (warrior/rogue/mage/poet) lalu mewarnainya berselang-seling.
        // Delapan pemain satu jalur akan membuat tiga dari empat kubu kosong,
        // dan rondenya menang seketika tanpa membuktikan apa pun.
        List<User> pemain = AcaraUji.isiPeta(PETA_TUNGGU, PEMAIN, "UjiCarnage",
                new int[] {1, 2, 3, 4});
        check(PEMAIN + " pemain uji berdiri di ruang tunggu",
                jumlahDiPeta(PETA_TUNGGU) == PEMAIN);
        boolean jalurLengkap = true;
        for (int jalur = 1; jalur <= 4; jalur++) {
            int n = 0;
            for (User u : pemain) {
                if (u.status.charClass == jalur) {
                    n++;
                }
            }
            if (n == 0) {
                jalurLengkap = false;
            }
        }
        check("keempat jalur kelas terwakili", jalurLengkap);
        return pemain;
    }

    private static void jalankanPertandingan(List<User> pemain) {
        log.info("=== membuka acara Carnage ===");
        for (String k : KUNCI) {
            setReg(k, 0);
        }
        check("carnages.init(18) berjalan",
                MapServer.scriptEngine.doScript("carnages", "init",
                        LuaValue.valueOf(18)));
        check("pintu terbuka: keadaan acara jadi 1", reg("carnageState") == 1);
        check("batas nyawa arena disetel dari skrip",
                reg("carnageMaxHealth") == 160000 && reg("carnageMaxMagic") == 80000);

        log.info("=== menunggu pintu ditutup (15 menit dipercepat) ===");
        AcaraUji.majukanKe("carnageStart", 900);
        tik();
        check("regu mulai dibentuk sesudah pintu ditutup: keadaan 2",
                reg("carnageState") == 2);

        log.info("=== pembagian regu menurut jalur kelas ===");
        int keadaan = putarSampai("carnageState", "carnageStart", 5, 300);
        check("keadaan berjalan sampai regu siap (5)", keadaan == 5);
        java.util.Map<Integer, Integer> hitung = new java.util.TreeMap<>();
        for (User u : pemain) {
            int dye = warnaKubu(u);
            hitung.merge(dye, 1, Integer::sum);
        }
        log.info("[carnage] sebaran warna kubu: {}", hitung);
        int berwarna = 0;
        for (int w : WARNA) {
            berwarna += hitung.getOrDefault(w, 0);
        }
        check("seluruh pemain masuk salah satu kubu", berwarna == PEMAIN);
        check("lebih dari satu kubu terisi", hitung.size() >= 2);

        log.info("=== arena disiapkan ===");
        keadaan = putarSampai("carnageState", "carnageStart", 13, 400);
        check("keadaan sampai RONDE BERJALAN (13)", keadaan == 13);
        check("arena yang dipilih skrip adalah peta " + PETA_ARENA,
                reg("carnageMap") == PETA_ARENA);

        // Pemain masuk arena lewat pintu yang baru dibuka.
        AcaraUji.pindahSemua(pemain, PETA_ARENA);
        check("seluruh pemain berada di arena",
                jumlahDiPeta(PETA_ARENA) == PEMAIN);

        log.info("=== ronde 1 ===");
        int pemenang1 = menangkanRonde(pemain);
        check("ronde 1 dimenangi satu kubu lewat perhitungan skrip",
                pemenang1 > 0);
        check("skor kubu pemenang menjadi 1", skor(pemenang1) == 1);

        log.info("=== ronde 2 ===");
        keadaan = putarSampai("carnageState", "carnageStart", 13, 400);
        check("ronde berikutnya dimulai lagi (13)", keadaan == 13);
        // Pemain yang mati pada ronde sebelumnya dihidupkan lagi, persis
        // seperti keadaan 15 melakukannya untuk pemain sungguhan.
        for (User u : pemain) {
            u.status.state = 0;
        }
        AcaraUji.pindahSemua(pemain, PETA_ARENA);
        int pemenang2 = menangkanRonde(pemain, pemenang1);
        check("ronde 2 dimenangi kubu yang sama", pemenang2 == pemenang1);
        check("kubu itu mencapai 2 poin", skor(pemenang1) >= 2);

        log.info("=== pertandingan selesai ===");
        // ⚠️ Keadaan 100 mengumumkan juara lalu LANGSUNG lanjut ke 101 pada
        // tik yang sama, jadi yang ditunggu adalah 101 — menuntut melihat
        // 100 berarti menuntut menangkap satu tik yang tidak pernah menetap.
        keadaan = putarSampai("carnageState", "carnageStart", 101, 400);
        log.info("[carnage] keadaan akhir {} skor hitam/putih/merah/biru {}/{}/{}/{}",
                keadaan, reg("carnageBlackScore"), reg("carnageWhiteScore"),
                reg("carnageRedScore"), reg("carnageBlueScore"));
        check("acara sampai tahap juara (101)", keadaan == 101);
        check("kubu juara memang yang berskor 2", skor(pemenang1) >= 2);
        // Pintu keluar kubu juara dibuka skripnya sendiri: objek 370/371 di
        // baris bawah ruang tunggu. Ini bukti bahwa cabang juaranya benar
        // BERJALAN, bukan sekadar nomor keadaan yang berubah.
        check("pintu keluar kubu juara dibuka di ruang tunggu",
                pintuJuaraTerbuka(pemenang1));

        log.info("=== penutupan ===");
        // ⚠️ Carnage TIDAK menutup dirinya sendiri seperti Elixir: 101 adalah
        // keadaan terakhirnya, dan yang membereskan adalah `closeGame()` —
        // dipanggil skrip hanya saat pesertanya kurang, atau oleh acara
        // berikutnya lewat `init`. Jadi di sini penutupnya dipanggil apa
        // adanya, dan yang diperiksa adalah AKIBATNYA.
        AcaraUji.pindahSemua(pemain, PETA_TUNGGU);
        check("carnages.closeGame() berjalan",
                MapServer.scriptEngine.doScript("carnages", "closeGame"));
        check("keadaan kembali 0", reg("carnageState") == 0);
        check("seluruh skor kembali nol",
                reg("carnageBlackScore") == 0 && reg("carnageWhiteScore") == 0
                        && reg("carnageRedScore") == 0
                        && reg("carnageBlueScore") == 0);
    }

    /**
     * Menangkan satu ronde dengan cara yang sama seperti di dunia nyata:
     * seluruh kubu lain <b>mati</b>, dan skrip acara sendiri yang
     * menghitung siapa yang tersisa lalu menaikkan skornya.
     *
     * @param maunya kubu yang harus menang, atau 0 = terserah yang pertama
     * @return warna kubu pemenang, atau 0 bila skripnya tidak memutuskan
     */
    private static int menangkanRonde(List<User> pemain) {
        return menangkanRonde(pemain, 0);
    }

    private static int menangkanRonde(List<User> pemain, int maunya) {
        int bertahan = maunya;
        if (bertahan == 0) {
            for (User u : pemain) {
                int w = warnaKubu(u);
                if (w > 0) {
                    bertahan = w;
                    break;
                }
            }
        }
        int hidup = 0;
        for (User u : pemain) {
            if (warnaKubu(u) == bertahan) {
                u.status.state = 0;
                hidup++;
            } else {
                // ⚠️ `state = 1` adalah MATI di C; itu yang dihitung skrip
                // (`players[z].state ~= 1`). Skornya tidak pernah disentuh
                // uji ini — skripnya yang memutuskan.
                u.status.state = 1;
            }
        }
        log.info("[carnage] kubu {} tersisa {} pemain hidup", bertahan, hidup);
        int sebelum = skor(bertahan);
        for (int i = 0; i < 200; i++) {
            AcaraUji.tidur(120);
            tik();
            if (skor(bertahan) > sebelum) {
                log.info("[carnage] skrip memberi poin ke kubu {} (skor {})",
                        bertahan, skor(bertahan));
                return bertahan;
            }
        }
        return 0;
    }

    /**
     * Apakah pintu keluar kubu juara sudah dibuka di ruang tunggu.
     *
     * <p>Skrip menyetel objek 370/371 di petak pintu kubu yang menang, dan
     * 370/371 juga di petak "kalah" untuk kubu lain — yang membedakan adalah
     * PASANGAN petaknya. Yang diperiksa di sini petak milik kubu juara.</p>
     */
    private static boolean pintuJuaraTerbuka(int warna) {
        int x = switch (warna) {
            case 63 -> 2;    // merah
            case 61 -> 10;   // putih
            case 60 -> 18;   // hitam
            case 66 -> 26;   // biru
            default -> -1;
        };
        if (x < 0) {
            return false;
        }
        LuaValue getObject = MapServer.scriptEngine.globals().get("getObject");
        LuaValue kiri = getObject.call(LuaValue.valueOf(PETA_TUNGGU),
                LuaValue.valueOf(x), LuaValue.valueOf(32));
        LuaValue kanan = getObject.call(LuaValue.valueOf(PETA_TUNGGU),
                LuaValue.valueOf(x + 1), LuaValue.valueOf(32));
        log.info("[carnage] pintu kubu {} di ({},32): {} {}", warna, x,
                kiri, kanan);
        return kiri.toint() == 370 && kanan.toint() == 371;
    }

    /** Warna kubu pemain menurut `gfxDye` yang disetel skrip. */
    private static int warnaKubu(User u) {
        LuaValue ref = MapServer.scriptEngine.playerRef(u.scriptPlayer());
        LuaValue dye = ref.get("gfxDye");
        return dye.isnumber() ? dye.toint() : 0;
    }

    private static int skor(int warna) {
        return switch (warna) {
            case 60 -> reg("carnageBlackScore");
            case 61 -> reg("carnageWhiteScore");
            case 63 -> reg("carnageRedScore");
            case 66 -> reg("carnageBlueScore");
            default -> 0;
        };
    }
}
