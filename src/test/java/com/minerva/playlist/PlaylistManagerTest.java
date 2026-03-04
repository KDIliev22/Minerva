package com.minerva.playlist;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlaylistManager}.
 * Uses a temporary directory so tests are isolated and leave no side effects.
 */
class PlaylistManagerTest {

    @TempDir
    Path tempDir;

    private PlaylistManager manager;

    @BeforeEach
    void setUp() {
        manager = new PlaylistManager(tempDir.toString());
    }

    // ── Initialization ──────────────────────────────────────────────

    @Test
    @DisplayName("New manager auto-creates 'Liked Songs' system playlist")
    void initCreatesLikedSongsPlaylist() {
        List<Map<String, Object>> playlists = manager.getAllPlaylists();
        assertFalse(playlists.isEmpty(), "Should have at least the system playlist");

        Map<String, Object> likedSongs = playlists.stream()
                .filter(p -> "Liked Songs".equals(p.get("name")))
                .findFirst()
                .orElse(null);

        assertNotNull(likedSongs, "Liked Songs playlist must exist");
        assertEquals(true, likedSongs.get("isSystem"));
    }

    @Test
    @DisplayName("playlists.json file is created on disk")
    void jsonFileCreatedOnDisk() {
        assertTrue(Files.exists(tempDir.resolve("playlists.json")));
    }

    // ── CRUD ────────────────────────────────────────────────────────

    @Test
    @DisplayName("createPlaylist returns unique incremental IDs")
    void createPlaylistReturnsUniqueIds() {
        int id1 = manager.createPlaylist("Rock", "Rock music");
        int id2 = manager.createPlaylist("Jazz", "Jazz music");

        assertNotEquals(id1, id2);
        assertTrue(id2 > id1, "IDs should be incremental");
    }

    @Test
    @DisplayName("getPlaylist returns correct data after creation")
    void getPlaylistReturnsCorrectData() {
        int id = manager.createPlaylist("Test Playlist", "A description");

        Map<String, Object> playlist = manager.getPlaylist(id);

        assertNotNull(playlist);
        assertEquals("Test Playlist", playlist.get("name"));
        assertEquals("A description", playlist.get("description"));
        assertEquals(false, playlist.get("isSystem"));
    }

    @Test
    @DisplayName("getPlaylist returns null for non-existent ID")
    void getPlaylistReturnsNullForMissingId() {
        assertNull(manager.getPlaylist(99999));
    }

    @Test
    @DisplayName("getAllPlaylists includes both system and user playlists")
    void getAllPlaylistsIncludesAll() {
        manager.createPlaylist("My Playlist", "desc");

        List<Map<String, Object>> all = manager.getAllPlaylists();
        // At least Liked Songs + the one we created
        assertTrue(all.size() >= 2);
    }

    @Test
    @DisplayName("updatePlaylist changes name and description")
    void updatePlaylistChangesFields() {
        int id = manager.createPlaylist("Old Name", "Old Desc");

        manager.updatePlaylist(id, "New Name", "New Desc", "/icon.png");

        Map<String, Object> updated = manager.getPlaylist(id);
        assertEquals("New Name", updated.get("name"));
        assertEquals("New Desc", updated.get("description"));
        assertEquals("/icon.png", updated.get("iconPath"));
    }

    @Test
    @DisplayName("updatePlaylist throws when updating system playlist")
    void updatePlaylistThrowsForSystemPlaylist() {
        // Find the Liked Songs playlist ID
        int likedId = manager.getAllPlaylists().stream()
                .filter(p -> Boolean.TRUE.equals(p.get("isSystem")))
                .mapToInt(p -> (int) p.get("id"))
                .findFirst()
                .orElseThrow();

        assertThrows(RuntimeException.class,
                () -> manager.updatePlaylist(likedId, "Renamed", "desc", null));
    }

    @Test
    @DisplayName("deletePlaylist removes user playlist")
    void deletePlaylistRemovesUserPlaylist() {
        int id = manager.createPlaylist("To Delete", "bye");
        manager.deletePlaylist(id);

        assertNull(manager.getPlaylist(id));
    }

    @Test
    @DisplayName("deletePlaylist throws when deleting system playlist")
    void deletePlaylistThrowsForSystemPlaylist() {
        int likedId = manager.getAllPlaylists().stream()
                .filter(p -> Boolean.TRUE.equals(p.get("isSystem")))
                .mapToInt(p -> (int) p.get("id"))
                .findFirst()
                .orElseThrow();

        assertThrows(RuntimeException.class, () -> manager.deletePlaylist(likedId));
    }

    // ── Tracks ──────────────────────────────────────────────────────

    @Test
    @DisplayName("addTrackToPlaylist adds a track and increases count")
    void addTrackToPlaylistAddsTrack() {
        int id = manager.createPlaylist("With Tracks", "");
        manager.addTrackToPlaylist(id, "track-abc-123");

        Map<String, Object> playlist = manager.getPlaylist(id);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tracks = (List<Map<String, String>>) playlist.get("tracks");
        assertEquals(1, tracks.size());
        assertEquals("track-abc-123", tracks.get(0).get("id"));
    }

    @Test
    @DisplayName("addTrackToPlaylist is idempotent (no duplicates)")
    void addTrackToPlaylistIdempotent() {
        int id = manager.createPlaylist("No Dups", "");
        manager.addTrackToPlaylist(id, "track-1");
        manager.addTrackToPlaylist(id, "track-1");

        Map<String, Object> playlist = manager.getPlaylist(id);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tracks = (List<Map<String, String>>) playlist.get("tracks");
        assertEquals(1, tracks.size(), "Should not add duplicate tracks");
    }

    @Test
    @DisplayName("removeTrackFromPlaylist removes the track")
    void removeTrackFromPlaylistRemovesTrack() {
        int id = manager.createPlaylist("Remove Test", "");
        manager.addTrackToPlaylist(id, "track-x");
        manager.removeTrackFromPlaylist(id, "track-x");

        Map<String, Object> playlist = manager.getPlaylist(id);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tracks = (List<Map<String, String>>) playlist.get("tracks");
        assertTrue(tracks.isEmpty());
    }

    @Test
    @DisplayName("reorderPlaylistTracks changes track positions")
    void reorderPlaylistTracks() {
        int id = manager.createPlaylist("Reorder", "");
        manager.addTrackToPlaylist(id, "A");
        manager.addTrackToPlaylist(id, "B");
        manager.addTrackToPlaylist(id, "C");

        // Reverse the order
        manager.reorderPlaylistTracks(id, Arrays.asList("C", "B", "A"));

        Map<String, Object> playlist = manager.getPlaylist(id);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tracks = (List<Map<String, String>>) playlist.get("tracks");
        assertEquals("C", tracks.get(0).get("id"));
        assertEquals("B", tracks.get(1).get("id"));
        assertEquals("A", tracks.get(2).get("id"));
    }

    // ── Persistence ─────────────────────────────────────────────────

    @Test
    @DisplayName("Data survives re-instantiation (persistence)")
    void dataSurvivesReload() {
        int id = manager.createPlaylist("Persistent", "Survives reload");
        manager.addTrackToPlaylist(id, "track-persist");

        // Create a new manager pointing to the same directory
        PlaylistManager reloaded = new PlaylistManager(tempDir.toString());

        Map<String, Object> playlist = reloaded.getPlaylist(id);
        assertNotNull(playlist, "Playlist should survive reload");
        assertEquals("Persistent", playlist.get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> tracks = (List<Map<String, String>>) playlist.get("tracks");
        assertEquals(1, tracks.size());
    }
}
