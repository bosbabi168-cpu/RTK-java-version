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
 * Gerbang acara Sumo War: dari pendaftaran dibuka sampai satu regu menang.
 *
 * <p>Bentuknya berbeda lagi dari Elixir dan Carnage. Poin di sini didapat
 * dengan <b>MENDORONG lawan sampai tercebur</b>: {@code SumoWarNpc.push}
 * memeriksa petak di belakang sasaran, dan bila petak itu air, regu si
 * pendorong dapat satu poin lalu sasarannya "splash". Karena itu gerbang ini
 * tidak menyentuh angka skornya sama sekali — ia menyiapkan geometri yang
 * benar (pendorong menghadap sasaran, air tepat di belakang sasaran) lalu
 * memanggil {@code push}, dan <b>skripnya</b> yang menghitung.</p>
 *
 * <p>Petak airnya tidak ditebak: daftar id petak diambil dari
 * {@code sumo_war.lua} sendiri, lalu peta arena dipindai untuk mencari
 * tempat yang memenuhi syarat.</p>
 *
 * <p>Yang dipercepat hanya JAMNYA — lihat {@link AcaraUji}.</p>
 *
 * <p>Jalankan: {@code ./run.sh sumotest} — map server lain harus mati.</p>
 */
public final class SumoTest {

    private static final Logger log = LogManager.getLogger(SumoTest.class);

    /** Ruang tunggu tempat pemain mendaftar. */
    private static final int PETA_TUNGGU = 15031;

    /** Arena tempat pertandingannya berlangsung. */
    private static final int PETA_ARENA = 15030;

    /** {@code start()} menuntut LEBIH dari enam pemain. */
    private static final int PEMAIN = 8;

    /** Poin yang dituntut `winnerCheck` untuk menang. */
    private static final int POIN_MENANG = 30;

    /** Id petak air, disalin dari daftar `tile` di `sumo_war.lua:665`. */
    private static final int[] PETAK_AIR = {
        628, 35012, 35013, 17816, 35011, 35010, 35009, 35017, 35015, 35014,
        35025, 35016,
    };

    private static final String[] KUNCI = {
        "sumo_war", "sumo_war_start", "sumo_war_playing", "sumo_war_wait_time",
        "sumo_war_warped_players", "sumo_war_red_point", "sumo_war_blue_point",
        "sumo_war_bridge_point", "sumo_war_end_timer", "sumo_war_winner",
        "sumo_respawn_time", "sumo_war_players",
    };

    private SumoTest() {
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
            check("gerbang sumo berjalan tanpa pengecualian", false);
            log.error("gerbang sumo berhenti", e);
        } finally {
            AcaraUji.bersihkanPemain(pemain);
            AcaraUji.pulihkanRegistry(simpanan);
            check("tidak ada pemain uji yang tertinggal di dunia",
                    jumlahDiPeta(PETA_TUNGGU) == 0 && jumlahDiPeta(PETA_ARENA) == 0);
        }
        AcaraUji.ringkas("SUMO");
    }

    private static List<User> siapkan() {
        check("peta ruang tunggu Sumo (" + PETA_TUNGGU + ") dimuat",
                MapServer.world.get(PETA_TUNGGU) != null);
        check("peta arena Sumo (" + PETA_ARENA + ") dimuat",
                MapServer.world.get(PETA_ARENA) != null);

        List<User> pemain = AcaraUji.isiPeta(PETA_TUNGGU, PEMAIN, "UjiSumo", null);
        // ⚠️ Regu dipilih pemain lewat dialog NPC pendaftaran. Yang disetel
        // di sini adalah HASIL dialog itu — `registry["sumo_war_team"]` —
        // bukan jalan pintas ke logika pertandingannya: pembagian ulang,
        // pemindahan ke arena, dan seluruh penilaian tetap dikerjakan skrip.
        for (int i = 0; i < pemain.size(); i++) {
            pemain.get(i).scriptPlayer().registry.put("sumo_war_team",
                    i % 2 == 0 ? 1 : 2);
        }
        check(PEMAIN + " pemain mendaftar di ruang tunggu",
                jumlahDiPeta(PETA_TUNGGU) == PEMAIN);
        return pemain;
    }

    private static void jalankanPertandingan(List<User> pemain) {
        log.info("=== membuka pendaftaran Sumo War ===");
        for (String k : KUNCI) {
            setReg(k, 0);
        }
        check("SumoWarNpc.open() berjalan",
                MapServer.scriptEngine.doScript("SumoWarNpc", "open"));
        check("pendaftaran terbuka", reg("sumo_war") == 1);
        check("tenggat pendaftaran 15 menit ke depan",
                reg("sumo_war_start") > System.currentTimeMillis() / 1000L + 800);

        log.info("=== pendaftaran ditutup (15 menit dipercepat) ===");
        // Tenggatnya dibandingkan dengan `<`, jadi cukup dimundurkan lewat.
        setReg("sumo_war_start", (int) (System.currentTimeMillis() / 1000L) - 1);
        tik();
        check("pendaftaran ditutup sendiri oleh skrip", reg("sumo_war") == 0);
        check("pemain dipindahkan ke arena oleh `start()`",
                jumlahDiPeta(PETA_ARENA) == PEMAIN);
        check("jeda sebelum bertanding dipasang", reg("sumo_war_wait_time") > 0);

        log.info("=== pertandingan dimulai ===");
        setReg("sumo_war_wait_time", (int) (System.currentTimeMillis() / 1000L) - 1);
        tik();
        check("pertandingan berjalan (`sumo_war_playing`)",
                reg("sumo_war_playing") == 1);

        log.info("=== mencetak poin dengan MENDORONG lawan ke air ===");
        int[] tempat = cariTempatDorong();
        check("ada tempat di arena yang bisa dipakai mendorong ke air",
                tempat != null);
        if (tempat == null) {
            return;
        }
        log.info("[sumo] pendorong ({},{}) arah {} — sasaran jatuh ke ({},{})",
                tempat[0], tempat[1], tempat[2], tempat[3], tempat[4]);

        User pendorong = pemain.get(0);   // regu 1 (merah)
        User sasaran = pemain.get(1);     // regu 2 (biru)
        int poin = doronganNyata(pendorong, sasaran, tempat, 1);
        check("satu dorongan memberi satu poin ke regu merah", poin == 1);
        check("regu biru tidak ikut dapat poin", reg("sumo_war_blue_point") == 0);
        check("sasaran tercebur: nyawanya nol dan keadaannya mati",
                sasaran.status.hp == 0 && sasaran.status.state == 1);

        log.info("=== poin dikumpulkan sampai batas menang ({}) ===", POIN_MENANG);
        // ⚠️ Poin sisanya dikumpulkan lewat dorongan yang SAMA, bukan dengan
        // menyetel angkanya. Yang diuji justru bahwa `winnerCheck` bereaksi
        // pada angka yang dinaikkan skripnya sendiri.
        int aman = 0;
        while (reg("sumo_war_red_point") < POIN_MENANG && aman++ < POIN_MENANG * 3) {
            doronganNyata(pendorong, sasaran, tempat, 0);
        }
        check("poin regu merah mencapai batas menang lewat dorongan sungguhan",
                reg("sumo_war_red_point") == POIN_MENANG);
        check("regu merah dinyatakan menang oleh `winnerCheck`",
                reg("sumo_war_winner") == 1);
        check("penghitung mundur kembali ke arena dipasang",
                reg("sumo_war_end_timer") > 0);

        log.info("=== penutupan ===");
        setReg("sumo_war_end_timer", (int) (System.currentTimeMillis() / 1000L) - 1);
        int batas = 0;
        while (reg("sumo_war_red_point") != 0 && batas++ < 200) {
            AcaraUji.tidur(60);
            tik();
        }
        check("acara membereskan dirinya sendiri: skor kembali nol",
                reg("sumo_war_red_point") == 0 && reg("sumo_war_blue_point") == 0);
        check("pertandingan berhenti", reg("sumo_war") == 0);
    }

    /**
     * Satu dorongan sungguhan lewat {@code SumoWarNpc.push}.
     *
     * @param sebelumnya poin regu merah sebelum dorongan, atau 0 = ambil
     *                   sendiri
     * @return selisih poin regu merah sesudah dorongan
     */
    private static int doronganNyata(User pendorong, User sasaran, int[] tempat,
                                     int laporkan) {
        int sebelum = reg("sumo_war_red_point");
        // Sasaran dihidupkan lagi: `push` menolak sasaran yang sudah mati,
        // persis seperti di dunia nyata (yang sudah tercebur tidak bisa
        // didorong lagi sampai ia bangkit).
        sasaran.status.state = 0;
        sasaran.status.hp = Math.max(sasaran.maxHp, 1);
        AcaraUji.pindahkan(sasaran, PETA_ARENA, tempat[5], tempat[6]);
        AcaraUji.pindahkan(pendorong, PETA_ARENA, tempat[0], tempat[1]);
        pendorong.status.side = tempat[2];
        MapServer.scriptEngine.doScript("SumoWarNpc", "push",
                MapServer.scriptEngine.playerRef(pendorong.scriptPlayer()));
        int sesudah = reg("sumo_war_red_point");
        if (laporkan == 1) {
            log.info("[sumo] poin merah {} -> {}; sasaran terlempar ke ({},{})",
                    sebelum, sesudah, sasaran.x, sasaran.y);
        }
        return sesudah - sebelum;
    }

    /**
     * Cari tempat di arena yang memenuhi syarat dorongan.
     *
     * <p>Syaratnya diambil dari skripnya sendiri: pendorong berdiri di
     * {@code x >= 40} (paruh kanan arena — hanya di sana cabang pemberi
     * poin dipakai), sasaran tepat di depannya, dan petak satu langkah di
     * belakang sasaran adalah salah satu id air.</p>
     *
     * @return {@code {xPendorong, yPendorong, arah, xJatuh, yJatuh,
     *         xSasaran, ySasaran}} atau null
     */
    private static int[] cariTempatDorong() {
        MapData m = MapServer.world.get(PETA_ARENA);
        if (m == null) {
            return null;
        }
        int[][] arah = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        // ⚠️ Cabang yang MEMBERI POIN adalah `else` dari `player.x < 40`
        // (sumo_war.lua:721): pendorong harus berdiri di paruh KANAN arena.
        // Mencari di paruh kiri membuat dorongannya berhasil memindahkan
        // lawan tetapi tidak pernah menghasilkan angka — persis kegagalan
        // pertama gerbang ini.
        for (int y = 1; y < m.ys - 1; y++) {
            for (int x = 40; x < m.xs - 1; x++) {
                for (int a = 0; a < 4; a++) {
                    int sx = x + arah[a][0];
                    int sy = y + arah[a][1];
                    int jx = sx + arah[a][0];
                    int jy = sy + arah[a][1];
                    if (diLuar(m, sx, sy) || diLuar(m, jx, jy)) {
                        continue;
                    }
                    if (!m.walkable(x, y) || !m.walkable(sx, sy)) {
                        continue;
                    }
                    if (!airnya(m.tile(jx, jy))) {
                        continue;
                    }
                    return new int[] {x, y, a, jx, jy, sx, sy};
                }
            }
        }
        return null;
    }

    private static boolean diLuar(MapData m, int x, int y) {
        return x < 0 || y < 0 || x >= m.xs || y >= m.ys;
    }

    private static boolean airnya(int tile) {
        for (int t : PETAK_AIR) {
            if (t == tile) {
                return true;
            }
        }
        return false;
    }

    /** Dipakai gerbang lain untuk memastikan daftar petaknya tidak melenceng. */
    static LuaValue daftarPetakAir() {
        LuaValue t = LuaValue.tableOf();
        for (int i = 0; i < PETAK_AIR.length; i++) {
            t.set(i + 1, LuaValue.valueOf(PETAK_AIR[i]));
        }
        return t;
    }
}
