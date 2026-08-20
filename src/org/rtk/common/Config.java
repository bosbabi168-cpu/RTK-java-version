package org.rtk.common;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Port of the "key: value" configuration file reader used by every server
 * (config_read() in login.c / char.c / map.c).
 *
 * Lines starting with "//" are comments. Everything before the first ':' is
 * the key, the remainder (trimmed) is the value.
 */
public final class Config {

    private static final Logger log = LogManager.getLogger(Config.class);

    public interface Handler {
        void set(String key, String value);
    }

    private Config() {
    }

    /** Returns true when the file was read successfully. */
    public static boolean read(String file, Handler handler) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("//")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (key.isEmpty() || value.isEmpty()) {
                    continue;
                }
                handler.set(key, value);
            }
            log.info("Configuration File ({}) reading finished!", file);
            return true;
        } catch (IOException e) {
            log.error("CFG_ERR: Configuration file ({}) not found.", file);
            return false;
        }
    }
}
