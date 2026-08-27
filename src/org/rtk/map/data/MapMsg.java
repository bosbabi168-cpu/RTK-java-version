package org.rtk.map.data;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Config;

/**
 * Port of {@code lang_read()} + {@code map_msg[]} (map/map.c:1417) — pesan
 * penolakan map server, dibaca dari {@code conf/lang.conf}.
 *
 * <p>Login server sudah lama memakai berkas yang sama
 * ({@code LoginServer.langRead}); bagian {@code MAP_*}-nya yang belum
 * pernah diport, sehingga pesan-pesan ini sebelumnya tidak punya tempat
 * tinggal sama sekali.</p>
 *
 * <p>⚠️ <b>Ada bawaan untuk tiap pesan, dan itu bukan kemalasan.</b> Tanpa
 * bawaan, server yang berjalan tanpa {@code conf/lang.conf} — termasuk
 * seluruh gerbang regresi — akan menolak pemain dengan <b>pesan kosong</b>,
 * yang di layar pemain terlihat persis seperti "tidak terjadi apa-apa".
 * Kegagalan yang tak terlihat justru yang paling mahal dicari.</p>
 *
 * <p>Bawaannya disalin apa adanya dari {@code conf/lang.conf} milik server
 * C; menerjemahkannya ke bahasa Indonesia dikerjakan di berkas itu, bukan
 * di sini.</p>
 */
public final class MapMsg {

    private static final Logger log = LogManager.getLogger(MapMsg.class);

    private static final Map<String, String> PESAN = new HashMap<>();

    /** Bawaan, dari {@code conf/lang.conf} versi C. */
    private static final String[][] BAWAAN = {
        {"MAP_WHISPFAIL", "That character is not online."},
        {"MAP_ERRGHOST", "Spirits can't do that."},
        {"MAP_ERRITMLEVEL", "You need more experience."},
        {"MAP_ERRITMMIGHT", "You can't lift it above your waist, let alone wield it."},
        {"MAP_ERRITMGRACE", "You are not cunning enough to use this properly."},
        {"MAP_ERRITMWILL", "You do not understand how to use this."},
        {"MAP_ERRITMSEX", "This doesn't fit you."},
        {"MAP_ERRITMFULL", "You can't have more."},
        {"MAP_ERRITMMAX", "(%s). You can't have more than (max stack)."},
        {"MAP_ERRITMPATH", "Your path forbids it."},
        {"MAP_ERRITMMARK", "You need more experience."},
        {"MAP_ERRITM2H", "You cannot equip a 2-handed weapon with a shield."},
        {"MAP_ERRMOUNT", "You can't do that while riding a mount."},
        {"MAP_ERRVITA", "You lack the health required to wield that."},
        {"MAP_ERRMANA", "You lack the wisdom required to wield that."},
        {"MAP_ERRSUMMON", "You cannot summon another to you."},
        // Format baris "sudah dikenakan" — %s diisi nama barangnya.
        {"MAP_EQHELM", "H:%s"},
        {"MAP_EQWEAP", "W:%s"},
        {"MAP_EQARMOR", "A:%s"},
        {"MAP_EQSHIELD", "S:%s"},
        {"MAP_EQLEFT", "L:%s"},
        {"MAP_EQRIGHT", "R:%s"},
        {"MAP_EQSUBLEFT", "Sl:%s"},
        {"MAP_EQSUBRIGHT", "Sr:%s"},
        {"MAP_EQFACEACC", "Fa:%s"},
        {"MAP_EQCROWN", "Cr:%s"},
        {"MAP_EQMANTLE", "M:%s"},
        {"MAP_EQNECKLACE", "N:%s"},
        {"MAP_EQBOOTS", "F:%s"},
        {"MAP_EQCOAT", "C:%s"},
    };

    static {
        for (String[] b : BAWAAN) {
            PESAN.put(b[0], b[1]);
        }
    }

    private MapMsg() {
    }

    /** Pesan menurut kuncinya; bawaan bila berkasnya tidak memuatnya. */
    public static String of(String key) {
        return PESAN.getOrDefault(key, "");
    }

    /**
     * lang_read(): timpa bawaan dengan isi berkas.
     *
     * <p>Hanya kunci berawalan {@code MAP_} yang diambil — berkasnya juga
     * memuat pesan login server, dan keduanya memang berbagi satu berkas.</p>
     */
    public static void load(String cfgFile) {
        boolean ok = Config.read(cfgFile, (key, value) -> {
            String k = key.trim().toUpperCase();
            if (k.startsWith("MAP_")) {
                PESAN.put(k, value);
            }
        });
        if (ok) {
            log.info("[MSG] pesan map dibaca dari {}", cfgFile);
        } else {
            log.warn("[MSG] {} tidak ditemukan — memakai pesan bawaan", cfgFile);
        }
    }
}
