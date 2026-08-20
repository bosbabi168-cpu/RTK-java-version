package org.rtk.common;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

/**
 * Port of the logging helpers scattered through core.c / showmsg.c
 * (set_logfile, add_log, Log_Add, getHour, getMinute).
 */
public final class ServerLog {

    private static String logFilename = "logs/server.log";
    private static String dmpFilename = "logs/server_dump.log";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ServerLog() {
    }

    public static void setLogfile(String name) {
        logFilename = name;
    }

    public static void setDmpfile(String name) {
        dmpFilename = name;
    }

    /** add_log(): appends a timestamped line to the configured log file. */
    public static void addLog(String fmt, Object... args) {
        writeTo(logFilename, fmt, args);
    }

    /** Log_Add(name, ...): appends to logs/&lt;name&gt;.log. */
    public static void logAdd(String name, String fmt, Object... args) {
        writeTo("logs/" + name + ".log", fmt, args);
    }

    private static void writeTo(String file, String fmt, Object... args) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
            String msg = args.length == 0 ? fmt : String.format(fmt, args);
            if (msg.trim().isEmpty()) {
                pw.println();
            } else {
                pw.print("[" + LocalDateTime.now().format(STAMP) + "] " + msg);
                if (!msg.endsWith("\n")) {
                    pw.println();
                }
            }
        } catch (IOException ignored) {
            // Same as the C code: logging never brings the server down.
        }
    }

    public static int getHour() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    public static int getMinute() {
        return Calendar.getInstance().get(Calendar.MINUTE);
    }
}
