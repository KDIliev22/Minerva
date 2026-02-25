package com.minerva.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.image.Image;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MusicFile {
    private static final Logger logger = LoggerFactory.getLogger(MusicFile.class);
    
    private String id;
    private String title;
    private String artist;
    private String album;
    private String genre;
    private int year;
    private int bitrate;
    private long duration;
    private long fileSize;
    private String filePath;
    private String torrentHash;
    private String fileHash;
    private int seedCount;
    private int leechCount;
    private java.time.LocalDateTime uploadDate;
    
    private String albumArtist;
    private String albumArtBase64;
    private String albumArtMimeType;
    private String albumArtPath;
    private String trackNumber;
    private String discNumber;
    private String composer;
    private String comment;
    private Map<String, String> additionalMetadata = new HashMap<>();
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    
    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }
    
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    
    public int getBitrate() { return bitrate; }
    public void setBitrate(int bitrate) { this.bitrate = bitrate; }
    
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getTorrentHash() { return torrentHash; }
    public void setTorrentHash(String torrentHash) { this.torrentHash = torrentHash; }
    
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    
    public int getSeedCount() { return seedCount; }
    public void setSeedCount(int seedCount) { this.seedCount = seedCount; }
    
    public int getLeechCount() { return leechCount; }
    public void setLeechCount(int leechCount) { this.leechCount = leechCount; }
    
    public java.time.LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(java.time.LocalDateTime uploadDate) { this.uploadDate = uploadDate; }
    
    public String getAlbumArtist() { return albumArtist; }
    public void setAlbumArtist(String albumArtist) { this.albumArtist = albumArtist; }
    
    public String getAlbumArtBase64() { return albumArtBase64; }
    public void setAlbumArtBase64(String albumArtBase64) { this.albumArtBase64 = albumArtBase64; }
    
    public String getAlbumArtMimeType() { return albumArtMimeType; }
    public void setAlbumArtMimeType(String albumArtMimeType) { this.albumArtMimeType = albumArtMimeType; }
    
    public String getAlbumArtPath() { return albumArtPath; }
    public void setAlbumArtPath(String albumArtPath) { this.albumArtPath = albumArtPath; }
    
    public String getTrackNumber() { return trackNumber; }
    public void setTrackNumber(String trackNumber) { this.trackNumber = trackNumber; }
    
    public String getDiscNumber() { return discNumber; }
    public void setDiscNumber(String discNumber) { this.discNumber = discNumber; }
    
    public String getComposer() { return composer; }
    public void setComposer(String composer) { this.composer = composer; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public Map<String, String> getAdditionalMetadata() { return additionalMetadata; }
    public void setAdditionalMetadata(Map<String, String> additionalMetadata) { 
        this.additionalMetadata = additionalMetadata; 
    }
    
    public void addMetadata(String key, String value) {
        if (additionalMetadata == null) {
            additionalMetadata = new HashMap<>();
        }
        additionalMetadata.put(key, value);
    }
    
    @JsonIgnore
    public Image getAlbumArtImage() {
        if (albumArtBase64 != null && !albumArtBase64.isEmpty()) {
            try {
                byte[] imageData = Base64.getDecoder().decode(albumArtBase64);
                ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                return new Image(bis);
            } catch (Exception e) {
                logger.error("Failed to decode album art", e);
            }
        }
        return null;
    }
    
    @JsonIgnore
    public String getMagnetLink() {
        if (torrentHash == null) return null;
        String displayName = title != null ? title.replace(" ", "+") : "track";
        return "magnet:?xt=urn:btih:" + torrentHash + "&dn=" + displayName;
    }
}