package com.minerva.network;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TorrentMetadata {
    
    public enum TorrentType {
        SINGLE,
        ALBUM
    }
    
    public enum FileStructure {
        SINGLE_TRACK {
            @Override
            public String getPathPattern() {
                return "{artist}/{album}/{track_number}_{title}.{extension}";
            }
        },
        ALBUM_DIRECTORY {
            @Override
            public String getPathPattern() {
                return "{artist}/{album}/{disc_number}_{track_number}_{title}.{extension}";
            }
        };
        
        public abstract String getPathPattern();
    }
    
    @JsonProperty("torrent_id")
    private String torrentId;
    
    @JsonProperty("torrent_type")
    private TorrentType torrentType;
    
    @JsonProperty("artist")
    private String artist;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("album")
    private String album;
    
    @JsonProperty("year")
    private Integer year;
    
    @JsonProperty("genre")
    private String genre;
    
    @JsonProperty("bitrate")
    private Integer bitrate;
    
    @JsonProperty("file_structure")
    private FileStructure fileStructure;
    
    @JsonProperty("tracks")
    private List<TrackInfo> tracks;
    
    @JsonProperty("track_info")
    private TrackInfo trackInfo;
    
    @JsonProperty("torrent_hash")
    private String torrentHash;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("uploader_id")
    private String uploaderId;
    
    @JsonProperty("file_size")
    private Long fileSize;
    
    @JsonProperty("piece_size")
    private Integer pieceSize = 262144;
    
    @JsonProperty("minerva_suffix")
    private final boolean minervaSuffix = true;
    
    // Getters and setters
    public String getTorrentId() { return torrentId; }
    public void setTorrentId(String torrentId) { this.torrentId = torrentId; }
    
    public TorrentType getTorrentType() { return torrentType; }
    public void setTorrentType(TorrentType torrentType) { this.torrentType = torrentType; }
    
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }
    
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    
    public Integer getBitrate() { return bitrate; }
    public void setBitrate(Integer bitrate) { this.bitrate = bitrate; }
    
    public FileStructure getFileStructure() { return fileStructure; }
    public void setFileStructure(FileStructure fileStructure) { this.fileStructure = fileStructure; }
    
    public List<TrackInfo> getTracks() { return tracks; }
    public void setTracks(List<TrackInfo> tracks) { this.tracks = tracks; }
    
    public TrackInfo getTrackInfo() { return trackInfo; }
    public void setTrackInfo(TrackInfo trackInfo) { this.trackInfo = trackInfo; }
    
    public String getTorrentHash() { return torrentHash; }
    public void setTorrentHash(String torrentHash) { this.torrentHash = torrentHash; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public String getUploaderId() { return uploaderId; }
    public void setUploaderId(String uploaderId) { this.uploaderId = uploaderId; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public Integer getPieceSize() { return pieceSize; }
    public void setPieceSize(Integer pieceSize) { this.pieceSize = pieceSize; }
    
    public boolean isMinervaSuffix() { return minervaSuffix; }
    
    public static String formatTorrentName(String artist, String title, TorrentType type) {
        String typeStr = type == TorrentType.SINGLE ? "SINGLE" : "ALBUM";
        return String.format("%s - %s(%s) - %s - MINERVA", 
            artist != null ? artist : "Unknown",
            typeStr,
            title != null ? title : "Unknown",
            typeStr);
    }
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrackInfo {
        @JsonProperty("track_number")
        private String trackNumber;
        
        @JsonProperty("disc_number")
        private String discNumber;
        
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("artist")
        private String artist;
        
        @JsonProperty("duration")
        private Long duration;
        
        @JsonProperty("file_name")
        private String fileName;
        
        @JsonProperty("file_hash")
        private String fileHash;
        
        @JsonProperty("bitrate")
        private Integer bitrate;
        
        public String getTrackNumber() { return trackNumber; }
        public void setTrackNumber(String trackNumber) { this.trackNumber = trackNumber; }
        
        public String getDiscNumber() { return discNumber; }
        public void setDiscNumber(String discNumber) { this.discNumber = discNumber; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        
        public Long getDuration() { return duration; }
        public void setDuration(Long duration) { this.duration = duration; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }
        
        public Integer getBitrate() { return bitrate; }
        public void setBitrate(Integer bitrate) { this.bitrate = bitrate; }
        
        public Integer getTrackNumberInt() {
            return parseStringToInt(trackNumber);
        }
        
        public Integer getDiscNumberInt() {
            return parseStringToInt(discNumber);
        }
        
        private Integer parseStringToInt(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}