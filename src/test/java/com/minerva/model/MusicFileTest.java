package com.minerva.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link MusicFile} model class.
 */
class MusicFileTest {

    // ── getMagnetLink ───────────────────────────────────────────────

    @Test
    @DisplayName("getMagnetLink returns null when torrentHash is null")
    void getMagnetLink_nullHash() {
        MusicFile mf = new MusicFile();
        assertNull(mf.getMagnetLink());
    }

    @Test
    @DisplayName("getMagnetLink returns valid magnet URI")
    void getMagnetLink_validHash() {
        MusicFile mf = new MusicFile();
        mf.setTorrentHash("abc123def456");
        mf.setTitle("My Track");

        String magnet = mf.getMagnetLink();
        assertNotNull(magnet);
        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:abc123def456"));
        assertTrue(magnet.contains("dn=My+Track"));
    }

    @Test
    @DisplayName("getMagnetLink uses 'track' when title is null")
    void getMagnetLink_nullTitle() {
        MusicFile mf = new MusicFile();
        mf.setTorrentHash("abc123");

        String magnet = mf.getMagnetLink();
        assertTrue(magnet.contains("dn=track"));
    }

    // ── addMetadata ─────────────────────────────────────────────────

    @Test
    @DisplayName("addMetadata stores custom key-value pair")
    void addMetadata_storesEntry() {
        MusicFile mf = new MusicFile();
        mf.addMetadata("BPM", "128");

        assertEquals("128", mf.getAdditionalMetadata().get("BPM"));
    }

    @Test
    @DisplayName("addMetadata initializes map if null")
    void addMetadata_initializesNullMap() {
        MusicFile mf = new MusicFile();
        mf.setAdditionalMetadata(null);

        // Should not throw NPE
        mf.addMetadata("key", "value");
        assertEquals("value", mf.getAdditionalMetadata().get("key"));
    }

    // ── Getters/Setters ─────────────────────────────────────────────

    @Test
    @DisplayName("All basic getters and setters work correctly")
    void gettersAndSetters() {
        MusicFile mf = new MusicFile();
        mf.setId("id-1");
        mf.setTitle("Song Title");
        mf.setArtist("Artist Name");
        mf.setAlbum("Album Name");
        mf.setGenre("Rock");
        mf.setYear(2024);
        mf.setBitrate(320);
        mf.setDuration(240L);
        mf.setFileSize(5_000_000L);
        mf.setFilePath("/music/song.mp3");
        mf.setTrackNumber("3");
        mf.setDiscNumber("1");

        assertEquals("id-1", mf.getId());
        assertEquals("Song Title", mf.getTitle());
        assertEquals("Artist Name", mf.getArtist());
        assertEquals("Album Name", mf.getAlbum());
        assertEquals("Rock", mf.getGenre());
        assertEquals(2024, mf.getYear());
        assertEquals(320, mf.getBitrate());
        assertEquals(240L, mf.getDuration());
        assertEquals(5_000_000L, mf.getFileSize());
        assertEquals("/music/song.mp3", mf.getFilePath());
        assertEquals("3", mf.getTrackNumber());
        assertEquals("1", mf.getDiscNumber());
    }
}
