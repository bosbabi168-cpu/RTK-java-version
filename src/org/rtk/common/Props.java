package org.rtk.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loader for resources/rtk-server.properties — the technical defaults that
 * used to be hardcoded (crypt keys, default ports, pool tuning, ...).
 *
 * Lookup order for the file itself:
 *   1. classpath (/rtk-server.properties — copied there by the "resources"
 *      Ant target, and packaged into the jar)
 *   2. resources/rtk-server.properties relative to the working directory
 *      (useful for plain javac runs)
 *
 * Precedence of values at runtime: these properties are the DEFAULTS; the
 * original C-style conf files (conf/*.conf) are read afterwards during
 * startup and override overlapping settings such as the ports.
 * Every getter takes a fallback so a missing file or key never stops the
 * server.
 */
public final class Props {

    private static final Logger log = LogManager.getLogger(Props.class);
    private static final Properties props = new Properties();

    static {
        boolean loaded = false;
        try (InputStream in = Props.class.getResourceAsStream("/rtk-server.properties")) {
            if (in != null) {
                props.load(in);
                loaded = true;
                log.info("Loaded rtk-server.properties from classpath");
            }
        } catch (IOException e) {
            log.warn("Failed reading rtk-server.properties from classpath: {}", e.getMessage());
        }
        if (!loaded) {
            Path file = Paths.get("resources", "rtk-server.properties");
            if (Files.isRegularFile(file)) {
                try (InputStream in = new FileInputStream(file.toFile())) {
                    props.load(in);
                    loaded = true;
                    log.info("Loaded rtk-server.properties from {}", file);
                } catch (IOException e) {
                    log.warn("Failed reading {}: {}", file, e.getMessage());
                }
            }
        }
        if (!loaded) {
            log.warn("rtk-server.properties not found - using built-in defaults");
        }
    }

    private Props() {
    }

    public static String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    public static int getInt(String key, int def) {
        String v = props.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("Property {} has non-numeric value '{}' - using default {}", key, v, def);
            return def;
        }
    }

    public static long getLong(String key, long def) {
        String v = props.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            log.warn("Property {} has non-numeric value '{}' - using default {}", key, v, def);
            return def;
        }
    }
}
