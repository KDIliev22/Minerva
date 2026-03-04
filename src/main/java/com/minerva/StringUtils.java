package com.minerva;

/**
 * Shared utility methods used across the Minerva application.
 * Centralizes common operations like filename sanitization and hex encoding
 * to avoid code duplication.
 */
public final class StringUtils {

    private StringUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Sanitizes a filename by replacing characters that are not allowed
     * in file paths across different operating systems.
     *
     * @param name the raw filename string
     * @return a sanitized filename safe for use in file paths
     */
    public static String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .trim();
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes the byte array to convert
     * @return the hex string representation
     */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * @param hex the hex string (must have even length)
     * @return the decoded byte array
     * @throws IllegalArgumentException if the hex string has odd length
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
