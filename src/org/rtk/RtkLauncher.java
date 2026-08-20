package org.rtk;

import org.rtk.charserver.CharServer;
import org.rtk.login.LoginServer;
import org.rtk.map.MapServer;
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
            default -> {
                System.err.println("Server tidak dikenal: " + args[0]);
                usage();
                System.exit(1);
            }
        }
    }

    private static void usage() {
        System.err.println("RTK server launcher");
        System.err.println();
        System.err.println("  java -jar <jar> login       [opsi]  jalankan login server");
        System.err.println("  java -jar <jar> char        [opsi]  jalankan char server");
        System.err.println("  java -jar <jar> map         [opsi]  jalankan map server");
        System.err.println("  java -jar <jar> scripttest  [opsi]  uji regresi scripting Lua");
        System.err.println();
        System.err.println("Opsi diteruskan ke server, mis. --conf conf/login.conf");
        System.err.println("Jalankan dari folder yang berisi conf/, meta/, dan logs/.");
    }
}
