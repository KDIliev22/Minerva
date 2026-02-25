package com.minerva.model;

import java.util.List;

public class Album {
    private String id;
    private String title;
    private String artist;
    private int year;
    private String genre;
    private String coverArtPath;
    private List<MusicFile> tracks;
    private int totalTracks;
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    
    public String getCoverArtPath() { return coverArtPath; }
    public void setCoverArtPath(String coverArtPath) { this.coverArtPath = coverArtPath; }
    
    public List<MusicFile> getTracks() { return tracks; }
    public void setTracks(List<MusicFile> tracks) { this.tracks = tracks; }
    
    public int getTotalTracks() { return totalTracks; }
    public void setTotalTracks(int totalTracks) { this.totalTracks = totalTracks; }
    
    public void addTrack(MusicFile track) {
        tracks.add(track);
        totalTracks = tracks.size();
    }
}