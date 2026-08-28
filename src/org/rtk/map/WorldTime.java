package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;

/**
 * Kalender <b>dalam permainan</b> — jam, hari, musim, dan tahun dunia
 * (R2). Port {@code get_time_thing()} + {@code change_time_char()} di
 * {@code map.c}.
 *
 * <h2>Kenapa ini bukan jam dinding</h2>
 *
 * <p>Dunia ini punya waktunya sendiri: satu "jam" dunia lewat setiap
 * <b>450 detik</b> nyata, 24 jam jadi satu hari, <b>92 hari</b> jadi satu
 * musim, dan empat musim jadi satu tahun. Nilainya disimpan di tabel
 * {@code Time} sehingga bertahan melintasi restart, dan itulah yang dibaca
 * skrip — bukan tanggal komputer.</p>
 *
 * <p>⚠️ Port ini sempat menjawab pertanyaan kalender dengan
 * {@code java.util.Calendar}: {@code curSeason} dihitung dari bulan
 * sungguhan, sementara {@code curTime} dan {@code curYear} malah tetap 0.
 * Akibatnya skrip musiman menyala di musim yang salah, dan paket waktu ke
 * klien selalu mengabarkan tengah malam tahun nol. Tidak ada yang melempar
 * error — jam dinding selalu punya jawaban.</p>
 *
 * <p>⚠️ Musim bernomor <b>1..4</b> (1 Winter, 2 Spring, 3 Summer, 4 Fall —
 * urutan itu dari {@code clif.c:5668}), bukan 0..3. Legenda karakter
 * menuliskannya sebagai teks, jadi salah nomor terlihat langsung sebagai
 * musim yang keliru di profil pemain.</p>
 */
public final class WorldTime {

    private static final Logger log = LogManager.getLogger(WorldTime.class);

    /** {@code timer_insert(450000, 450000, change_time_char, …)} di map.c. */
    public static final long TICK_MS = 450_000;

    /** Jam dunia 0..23 ({@code cur_time}). */
    public static int hour;
    /** Hari dalam musim, 1..91 ({@code cur_day}). */
    public static int day = 1;
    /** Musim 1..4 ({@code cur_season}). */
    public static int season = 1;
    /** Tahun dunia ({@code cur_year}). */
    public static int year = 1;

    private WorldTime() {
    }

    /** Nama musim seperti yang ditulis {@code clif.c:5668-5671}. */
    public static String seasonName() {
        return switch (season) {
            case 1 -> "Winter";
            case 2 -> "Spring";
            case 3 -> "Summer";
            case 4 -> "Fall";
            default -> "";
        };
    }

    /**
     * get_time_thing(): baca keadaan kalender dari tabel {@code Time}.
     *
     * <p>Barisnya tidak ada berarti dunia baru — nilai bawaan (tahun 1,
     * musim 1, hari 1, jam 0) dipakai apa adanya, sama seperti C yang
     * menyetelnya sebelum memanggil pembacanya.</p>
     */
    public static void load(Sql sql) {
        Object[] row = sql.queryRow(
                "SELECT `TimHour`,`TimDay`,`TimSeason`,`TimYear` FROM `Time`", 4);
        if (row == null) {
            log.warn("[TIME] tabel Time kosong — kalender mulai dari tahun 1");
            return;
        }
        hour = ((Number) row[0]).intValue();
        day = ((Number) row[1]).intValue();
        season = ((Number) row[2]).intValue();
        year = ((Number) row[3]).intValue();
        log.info("[TIME] kalender dunia: jam {}, hari {}, {} tahun {}",
                hour, day, seasonName(), year);
    }

    /**
     * change_time_char(): satu jam dunia berlalu.
     *
     * <p>Urutan naiknya persis seperti C — jam mencapai 24 mengganti hari,
     * hari mencapai 92 mengganti musim, musim mencapai 5 kembali ke 1 dan
     * menambah tahun. Setelah itu seluruh pemain dikabari dan nilainya
     * disimpan.</p>
     */
    public static int tick() {
        hour++;
        if (hour == 24) {
            hour = 0;
            day++;
            if (day == 92) {
                day = 1;
                season++;
                if (season == 5) {
                    season = 1;
                    year++;
                }
            }
        }
        for (User sd : MapServer.onlineChars.values()) {
            MapServer.clientView.worldTimeChanged(sd);
        }
        if (MapServer.sql != null) {
            MapServer.sql.update(
                    "UPDATE `Time` SET `TimHour` = ?, `TimDay` = ?, "
                    + "`TimSeason` = ?, `TimYear` = ?", hour, day, season, year);
        }
        return 0;
    }
}
