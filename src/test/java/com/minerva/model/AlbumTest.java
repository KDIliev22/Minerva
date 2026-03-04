package com.minerva.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Album} model class.
 */
class AlbumTest {

    @Test
    @DisplayName("Getters and setters work correctly")
    void gettersAndSetters() {
        Album album = new Album();
        album.setId("album-1");
        album.setTitle("Dark Side of the Moon");
        album.setArtist("Pink Floyd");
        album.setYear(1973);
        album.setGenre("Progressive Rock");
        album.setCoverArtPath("/art/cover.jpg");

        assertEquals("album-1", album.getId());
        assertEquals("Dark Side of the Moon", album.getTitle());
        assertEquals("Pink Floyd", album.getArtist());
        assertEquals(1973, album.getYear());
        assertEquals("Progressive Rock", album.getGenre());
        assertEquals("/art/cover.jpg", album.getCoverArtPath());
    }

    @Test
    @DisplayName("addTrack adds to existing list and updates count")
    void addTrackUpdatesListAndCount() {
        Album album = new Album();
        album.setTracks(new ArrayList<>());

        MusicFile track = new MusicFile();
        track.setTitle("Money");
        album.addTrack(track);

        assertEquals(1, album.getTotalTracks());
        assertEquals(1, album.getTracks().size());
        assertEquals("Money", album.getTracks().get(0).getTitle());
    }

    @Test
    @DisplayName("addTrack throws NPE when tracks list is null (known bug)")
    void addTrackThrowsNPEWhenTracksNull() {
        Album album = new Album();
        // tracks list is null by default — this documents the known bug
        MusicFile track = new MusicFile();

        assertThrows(NullPointerException.class, () -> album.addTrack(track));
    }

    @Test
    @DisplayName("setTotalTracks is independent of actual list size")
    void setTotalTracksIsIndependent() {
        Album album = new Album();
        album.setTotalTracks(10);
        assertEquals(10, album.getTotalTracks());
    }
}
