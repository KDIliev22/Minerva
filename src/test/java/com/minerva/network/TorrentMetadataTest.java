package com.minerva.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link TorrentMetadata} model and its nested types.
 */
class TorrentMetadataTest {

    // ── TorrentType enum ────────────────────────────────────────────

    @Test
    @DisplayName("TorrentType has exactly SINGLE and ALBUM values")
    void torrentTypeValues() {
        TorrentMetadata.TorrentType[] values = TorrentMetadata.TorrentType.values();
        assertEquals(2, values.length);
        assertNotNull(TorrentMetadata.TorrentType.valueOf("SINGLE"));
        assertNotNull(TorrentMetadata.TorrentType.valueOf("ALBUM"));
    }

    // ── FileStructure enum ──────────────────────────────────────────

    @Test
    @DisplayName("SINGLE_TRACK path pattern contains {artist}/{album}")
    void singleTrackPathPattern() {
        String pattern = TorrentMetadata.FileStructure.SINGLE_TRACK.getPathPattern();
        assertTrue(pattern.contains("{artist}"));
        assertTrue(pattern.contains("{album}"));
        assertTrue(pattern.contains("{title}"));
        assertFalse(pattern.contains("{disc_number}"), "Single track should not include disc number");
    }

    @Test
    @DisplayName("ALBUM_DIRECTORY path pattern includes {disc_number}")
    void albumDirectoryPathPattern() {
        String pattern = TorrentMetadata.FileStructure.ALBUM_DIRECTORY.getPathPattern();
        assertTrue(pattern.contains("{disc_number}"));
        assertTrue(pattern.contains("{track_number}"));
    }

    // ── formatTorrentName ───────────────────────────────────────────

    @Test
    @DisplayName("formatTorrentName produces correct format for SINGLE")
    void formatTorrentNameSingle() {
        String name = TorrentMetadata.formatTorrentName("Pink Floyd", "Money",
                TorrentMetadata.TorrentType.SINGLE);

        assertTrue(name.contains("Pink Floyd"));
        assertTrue(name.contains("Money"));
        assertTrue(name.endsWith("MINERVA"));
    }

    @Test
    @DisplayName("formatTorrentName handles null artist/title")
    void formatTorrentNameNulls() {
        String name = TorrentMetadata.formatTorrentName(null, null,
                TorrentMetadata.TorrentType.ALBUM);

        assertTrue(name.contains("Unknown"));
        assertTrue(name.endsWith("MINERVA"));
    }

    // ── minervaSuffix always true ───────────────────────────────────

    @Test
    @DisplayName("minervaSuffix is always true")
    void minervaSuffixAlwaysTrue() {
        TorrentMetadata metadata = new TorrentMetadata();
        assertTrue(metadata.isMinervaSuffix());
    }

    // ── Default pieceSize ───────────────────────────────────────────

    @Test
    @DisplayName("Default piece size is 256 KB")
    void defaultPieceSize() {
        TorrentMetadata metadata = new TorrentMetadata();
        assertEquals(262144, metadata.getPieceSize());
    }

    // ── TrackInfo ───────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({"1, 1", "03, 3", "10, 10"})
    @DisplayName("TrackInfo.getTrackNumberInt parses numeric strings")
    void trackInfoParsesTrackNumber(String input, int expected) {
        TorrentMetadata.TrackInfo ti = new TorrentMetadata.TrackInfo();
        ti.setTrackNumber(input);
        assertEquals(expected, ti.getTrackNumberInt());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "abc"})
    @DisplayName("TrackInfo.getTrackNumberInt returns null for non-numeric input")
    void trackInfoReturnsNullForBadInput(String input) {
        TorrentMetadata.TrackInfo ti = new TorrentMetadata.TrackInfo();
        ti.setTrackNumber(input);
        assertNull(ti.getTrackNumberInt());
    }

    @Test
    @DisplayName("TrackInfo.getDiscNumberInt works correctly")
    void discNumberParsing() {
        TorrentMetadata.TrackInfo ti = new TorrentMetadata.TrackInfo();
        ti.setDiscNumber("2");
        assertEquals(2, ti.getDiscNumberInt());

        ti.setDiscNumber(null);
        assertNull(ti.getDiscNumberInt());
    }

    // ── JSON serialization ──────────────────────────────────────────

    @Test
    @DisplayName("TorrentMetadata serializes to JSON with correct field names")
    void jsonSerialization() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        TorrentMetadata metadata = new TorrentMetadata();
        metadata.setTorrentId("abc-123");
        metadata.setTorrentType(TorrentMetadata.TorrentType.SINGLE);
        metadata.setArtist("Test Artist");
        metadata.setTitle("Test Title");
        metadata.setAlbum("Test Album");

        String json = mapper.writeValueAsString(metadata);

        assertTrue(json.contains("\"torrent_id\""));
        assertTrue(json.contains("\"torrent_type\""));
        assertTrue(json.contains("\"minerva_suffix\":true"));
        assertTrue(json.contains("\"abc-123\""));
    }

    // ── Full round-trip ─────────────────────────────────────────────

    @Test
    @DisplayName("Metadata with tracks round-trips through setters/getters")
    void fullRoundTrip() {
        TorrentMetadata metadata = new TorrentMetadata();
        metadata.setTorrentId("hash-abc");
        metadata.setTorrentType(TorrentMetadata.TorrentType.ALBUM);
        metadata.setArtist("Pink Floyd");
        metadata.setTitle("The Wall");
        metadata.setAlbum("The Wall");
        metadata.setYear(1979);
        metadata.setGenre("Progressive Rock");
        metadata.setBitrate(320);
        metadata.setFileStructure(TorrentMetadata.FileStructure.ALBUM_DIRECTORY);
        metadata.setFileSize(500_000_000L);

        TorrentMetadata.TrackInfo t1 = new TorrentMetadata.TrackInfo();
        t1.setTitle("In the Flesh?");
        t1.setTrackNumber("1");
        t1.setDiscNumber("1");
        t1.setDuration(198L);

        TorrentMetadata.TrackInfo t2 = new TorrentMetadata.TrackInfo();
        t2.setTitle("The Thin Ice");
        t2.setTrackNumber("2");
        t2.setDiscNumber("1");
        t2.setDuration(151L);

        metadata.setTracks(Arrays.asList(t1, t2));

        assertEquals("hash-abc", metadata.getTorrentId());
        assertEquals(TorrentMetadata.TorrentType.ALBUM, metadata.getTorrentType());
        assertEquals(2, metadata.getTracks().size());
        assertEquals("In the Flesh?", metadata.getTracks().get(0).getTitle());
        assertEquals(1979, metadata.getYear());
        assertEquals(500_000_000L, metadata.getFileSize());
    }
}
