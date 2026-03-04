package com.minerva;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link StringUtils} utility class.
 */
class StringUtilsTest {

    // ── sanitizeFileName ────────────────────────────────────────────

    @Test
    @DisplayName("sanitizeFileName replaces illegal path characters with underscores")
    void sanitizeFileName_replacesIllegalCharacters() {
        assertEquals("file_name", StringUtils.sanitizeFileName("file:name"));
        assertEquals("path_to_file", StringUtils.sanitizeFileName("path/to\\file"));
        assertEquals("no_quotes", StringUtils.sanitizeFileName("no\"quotes"));
        assertEquals("pipe_test", StringUtils.sanitizeFileName("pipe|test"));
        assertEquals("star_question", StringUtils.sanitizeFileName("star*question"));
        assertEquals("angle_brackets_", StringUtils.sanitizeFileName("angle<brackets>"));
    }

    @Test
    @DisplayName("sanitizeFileName replaces whitespace with underscores")
    void sanitizeFileName_replacesWhitespace() {
        assertEquals("hello_world", StringUtils.sanitizeFileName("hello world"));
        assertEquals("tab_here", StringUtils.sanitizeFileName("tab\there"));
        assertEquals("multiple_spaces", StringUtils.sanitizeFileName("multiple   spaces"));
    }

    @Test
    @DisplayName("sanitizeFileName returns 'unknown' for null input")
    void sanitizeFileName_nullReturnsUnknown() {
        assertEquals("unknown", StringUtils.sanitizeFileName(null));
    }

    @Test
    @DisplayName("sanitizeFileName preserves clean filenames")
    void sanitizeFileName_preservesCleanNames() {
        assertEquals("MyAlbum-2024", StringUtils.sanitizeFileName("MyAlbum-2024"));
        assertEquals("track_01.mp3", StringUtils.sanitizeFileName("track_01.mp3"));
    }

    // ── toHex ───────────────────────────────────────────────────────

    @Test
    @DisplayName("toHex converts bytes to lowercase hex string")
    void toHex_correctConversion() {
        byte[] bytes = {0x0a, (byte) 0xff, 0x00, 0x7f};
        assertEquals("0aff007f", StringUtils.toHex(bytes));
    }

    @Test
    @DisplayName("toHex handles empty array")
    void toHex_emptyArray() {
        assertEquals("", StringUtils.toHex(new byte[0]));
    }

    @Test
    @DisplayName("toHex produces SHA-1 length hex for 20 bytes")
    void toHex_sha1Length() {
        byte[] sha1 = new byte[20];
        String hex = StringUtils.toHex(sha1);
        assertEquals(40, hex.length());
    }

    // ── hexToBytes ──────────────────────────────────────────────────

    @Test
    @DisplayName("hexToBytes correctly decodes hex string")
    void hexToBytes_correctDecoding() {
        byte[] expected = {0x0a, (byte) 0xff, 0x00, 0x7f};
        assertArrayEquals(expected, StringUtils.hexToBytes("0aff007f"));
    }

    @Test
    @DisplayName("hexToBytes handles empty string")
    void hexToBytes_emptyString() {
        assertArrayEquals(new byte[0], StringUtils.hexToBytes(""));
    }

    @Test
    @DisplayName("hexToBytes throws on odd-length string")
    void hexToBytes_oddLengthThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> StringUtils.hexToBytes("abc"));
    }

    // ── Round-trip ──────────────────────────────────────────────────

    @Test
    @DisplayName("toHex and hexToBytes are inverse operations")
    void hexRoundTrip() {
        byte[] original = {1, 2, 3, (byte) 200, (byte) 255};
        byte[] roundTripped = StringUtils.hexToBytes(StringUtils.toHex(original));
        assertArrayEquals(original, roundTripped);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deadbeef", "0000000000000000000000000000000000000000", "abcdef1234567890"})
    @DisplayName("hexToBytes -> toHex round-trip preserves hex string")
    void hexStringRoundTrip(String hex) {
        assertEquals(hex, StringUtils.toHex(StringUtils.hexToBytes(hex)));
    }
}
