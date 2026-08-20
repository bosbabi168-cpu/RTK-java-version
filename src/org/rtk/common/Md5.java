package org.rtk.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Port of md5calc.c (MD5_String) using the Java SE MessageDigest API.
 */
public final class Md5 {

    private Md5() {
    }

    /** Equivalent of MD5_String(): lower-case hex digest of the input. */
    public static String hex(String input) {
        return hex(input.getBytes(StandardCharsets.ISO_8859_1));
    }

    public static String hex(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed to exist on every Java SE platform.
            throw new IllegalStateException(e);
        }
    }
}
