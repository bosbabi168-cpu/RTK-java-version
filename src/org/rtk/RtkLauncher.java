package org.rtk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.charserver.CharServer;
import org.rtk.login.LoginServer;
import org.rtk.map.MapServer;
import org.rtk.common.mmo.CharStatusTest;
import org.rtk.map.data.MapFileTest;
import org.rtk.map.ClifTest;
import org.rtk.map.data.MapWorldTest;
import org.rtk.map.script.ScriptTest;

/**
 * Entry point tunggal untuk jar hasil build NetBeans.
 *
 * Sebuah jar hanya bisa punya satu Main-Class di manifest, sedangkan RTK
 * terdiri dari tiga server terpisah. Kelas ini menjadi dispatcher supaya
 * satu jar yang sama bisa menjalankan semuanya:
 *
 *   java -jar RTK-java-version.jar login   &
 *   java -jar RTK-java-version.jar char    &
 *   java -jar RTK-java-version.jar map     &
 *   java -jar RTK-java-version.jar scripttest
 *
 * Argumen selanjutnya diteruskan apa adanya ke server yang dipilih,
 * jadi opsi asli seperti --conf/--inter/--lang tetap berfungsi:
 *
 *   java -jar RTK-java-version.jar login --conf conf/login.conf
 *
 * Set kelas ini sebagai Main Class di NetBeans:
 * Project Properties > Run > Main Class = org.rtk.RtkLauncher
 */
public final class RtkLauncher {

    private static final Logger log = LogManager.getLogger(RtkLauncher.class);

    private RtkLauncher() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }

        String server = args[0].toLowerCase();
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        switch (server) {
            case "login" -> LoginServer.main(rest);
            case "char" -> CharServer.main(rest);
            case "map" -> MapServer.main(rest);
            case "scripttest" -> ScriptTest.main(rest);
            case "maptest" -> MapFileTest.main(rest);
            case "chartest" -> CharStatusTest.main(rest);
            case "worldtest" -> MapWorldTest.main(rest);
            case "cliftest" -> ClifTest.main(rest);
            default -> {
                log.error("Server tidak dikenal: {}", args[0]);
                usage();
                System.exit(1);
            }
        }
    }

    /**
     * Tampilkan cara pakai. Sengaja lewat Log4j2, bukan System.err, supaya
     * salah panggil (mis. unit systemd dengan argumen keliru) ikut tercatat
     * di `logs/common.log` — bukan cuma hilang di stderr.
     */
    private static void usage() {
        for (String line : new String[]{
            "RTK server launcher",
            "  java -jar <jar> login       [opsi]  jalankan login server",
            "  java -jar <jar> char        [opsi]  jalankan char server",
            "  java -jar <jar> map         [opsi]  jalankan map server",
            "  java -jar <jar> scripttest  [opsi]  uji regresi scripting Lua",
            "  java -jar <jar> maptest     [path]  baca & periksa seluruh berkas .map",
            "  java -jar <jar> chartest            uji serialisasi status karakter",
            "  java -jar <jar> worldtest   [path]  uji indeks spasial dunia peta",
            "  java -jar <jar> cliftest    [path]  uji tata letak byte paket klien",
            "Opsi diteruskan ke server, mis. --conf conf/login.conf",
            "Jalankan dari folder yang berisi conf/, meta/, dan logs/.",
        }) {
            log.error(line);
        }
    }
}
