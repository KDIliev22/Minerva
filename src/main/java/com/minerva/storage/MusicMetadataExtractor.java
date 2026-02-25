package com.minerva.storage;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class MusicMetadataExtractor {
    private static final Logger logger = LoggerFactory.getLogger(MusicMetadataExtractor.class);
    
    public static class MusicMetadata {
        public String title;
        public String artist;
        public String album;
        public String albumArtist;
        public String genre;
        public int year;
        public int bitrate;
        public int duration;
        public String trackNumber;
        public String discNumber;
        public String composer;
        public String comment;
        public String lyrics;
        public String albumArtBase64;
        public String albumArtMimeType;
        public String filePath;
        public long fileSize;
        public Map<String, String> additionalTags = new HashMap<>();
        
        @Override
        public String toString() {
            return String.format("MusicMetadata[title=%s, artist=%s, album=%s, year=%d, bitrate=%d, duration=%d]",
                    title, artist, album, year, bitrate, duration);
        }
    }
    
    public static MusicMetadata extractMetadata(File audioFile) {
        MusicMetadata metadata = new MusicMetadata();
        metadata.filePath = audioFile.getAbsolutePath();
        metadata.fileSize = audioFile.length();
        
        try {
            AudioFile file = AudioFileIO.read(audioFile);
            Tag tag = file.getTag();
            
            if (tag != null) {
                metadata.title = getTagValue(tag, FieldKey.TITLE, audioFile.getName());
                metadata.artist = getTagValue(tag, FieldKey.ARTIST, "Unknown Artist");
                metadata.album = getTagValue(tag, FieldKey.ALBUM, "Unknown Album");
                metadata.albumArtist = getTagValue(tag, FieldKey.ALBUM_ARTIST, metadata.artist);
                metadata.genre = getTagValue(tag, FieldKey.GENRE, "Unknown");
                metadata.trackNumber = getTagValue(tag, FieldKey.TRACK, "");
                metadata.discNumber = getTagValue(tag, FieldKey.DISC_NO, "");
                metadata.composer = getTagValue(tag, FieldKey.COMPOSER, "");
                metadata.comment = getTagValue(tag, FieldKey.COMMENT, "");
                metadata.lyrics = getTagValue(tag, FieldKey.LYRICS, "");
                
                String yearStr = getTagValue(tag, FieldKey.YEAR, "");
                if (!yearStr.isEmpty()) {
                    try {
                        String[] parts = yearStr.split("[-/]");
                        metadata.year = Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse year: {}", yearStr);
                        metadata.year = 0;
                    }
                }
                
                extractAlbumArt(tag, metadata);
                extractAdditionalTags(tag, metadata);
            }
            
            if (file.getAudioHeader() != null) {
                metadata.bitrate = (int) file.getAudioHeader().getBitRateAsNumber();
                metadata.duration = (int) file.getAudioHeader().getTrackLength();
                
                logger.debug("Audio format: {}, Sample rate: {} Hz, Channels: {}",
                        file.getAudioHeader().getFormat(),
                        file.getAudioHeader().getSampleRateAsNumber(),
                        file.getAudioHeader().getChannels());
            }
            
            logger.info("Extracted embedded metadata: {} - {} ({})", 
                    metadata.artist, metadata.title, metadata.album);
            
        } catch (CannotReadException e) {
            logger.error("Cannot read audio file: {}", audioFile.getName(), e);
            extractFromFilename(audioFile, metadata);
        } catch (IOException e) {
            logger.error("IO error reading file: {}", audioFile.getName(), e);
            extractFromFilename(audioFile, metadata);
        } catch (TagException e) {
            logger.error("Tag error in file: {}", audioFile.getName(), e);
            extractFromFilename(audioFile, metadata);
        } catch (ReadOnlyFileException e) {
            logger.error("File is read-only: {}", audioFile.getName(), e);
            extractFromFilename(audioFile, metadata);
        } catch (InvalidAudioFrameException e) {
            logger.error("Invalid audio frame in file: {}", audioFile.getName(), e);
            extractFromFilename(audioFile, metadata);
        } catch (Exception e) {
            logger.error("Unexpected error extracting metadata: {}", audioFile.getName(), e);
            extractFromFilename(audioFile, metadata);
        }
        
        return metadata;
    }
    
    private static String getTagValue(Tag tag, FieldKey key, String defaultValue) {
        try {
            String value = tag.getFirst(key);
            return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static void extractAlbumArt(Tag tag, MusicMetadata metadata) {
        try {
            if (tag.getFirstArtwork() != null) {
                Artwork artwork = tag.getFirstArtwork();
                
                if (artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                    metadata.albumArtBase64 = Base64.getEncoder().encodeToString(artwork.getBinaryData());
                    metadata.albumArtMimeType = artwork.getMimeType();
                    
                    logger.debug("Extracted album art: {} bytes, MIME type: {}", 
                            artwork.getBinaryData().length, metadata.albumArtMimeType);
                    
                    saveAlbumArtToFile(artwork, metadata);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not extract album art: {}", e.getMessage());
        }
    }
    
    private static void saveAlbumArtToFile(Artwork artwork, MusicMetadata metadata) {
    try {
        String albumArtDirEnv = System.getenv().getOrDefault("ALBUM_ART_DIR", "album_art");
        Path albumArtDir = Paths.get(albumArtDirEnv);
        if (!Files.exists(albumArtDir)) {
            Files.createDirectories(albumArtDir);
        }
        
        String safeArtist = sanitizeFileName(metadata.artist);
        String safeAlbum = sanitizeFileName(metadata.album);
        String extension = getImageExtension(artwork.getMimeType());
        
        if (!safeArtist.isEmpty() && !safeAlbum.isEmpty()) {
            String filename = String.format("%s_%s%s", safeArtist, safeAlbum, extension);
            Path artPath = albumArtDir.resolve(filename);
            
            Files.write(artPath, artwork.getBinaryData());
            logger.debug("Saved album art to: {}", artPath);
        }
    } catch (Exception e) {
        logger.warn("Could not save album art to file: {}", e.getMessage());
    }
}
    
    private static String getImageExtension(String mimeType) {
        if (mimeType == null) return ".jpg";
        
        switch (mimeType.toLowerCase()) {
            case "image/jpeg":
            case "image/jpg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/gif":
                return ".gif";
            case "image/bmp":
                return ".bmp";
            default:
                return ".jpg";
        }
    }
    
    private static void extractAdditionalTags(Tag tag, MusicMetadata metadata) {
        try {
            String[] additionalKeys = {
                "BPM", "MOOD", "ORIGINALARTIST", "ORIGINALLYRICIST", 
                "URL", "ENCODEDBY", "COPYRIGHT", "ENCODER", "ENCODERSETTINGS"
            };
            
            for (String key : additionalKeys) {
                try {
                    String value = tag.getFirst(FieldKey.valueOf(key));
                    if (value != null && !value.trim().isEmpty()) {
                        metadata.additionalTags.put(key, value.trim());
                    }
                } catch (Exception e) {
                }
            }
            
            for (FieldKey fieldKey : FieldKey.values()) {
                try {
                    String value = tag.getFirst(fieldKey);
                    if (value != null && !value.trim().isEmpty() && 
                        !isStandardField(fieldKey)) {
                        metadata.additionalTags.put(fieldKey.name(), value.trim());
                    }
                } catch (Exception e) {
                }
            }
            
        } catch (Exception e) {
            logger.warn("Could not extract additional tags: {}", e.getMessage());
        }
    }
    
    private static boolean isStandardField(FieldKey fieldKey) {
        FieldKey[] standardFields = {
            FieldKey.TITLE, FieldKey.ARTIST, FieldKey.ALBUM, FieldKey.ALBUM_ARTIST,
            FieldKey.GENRE, FieldKey.YEAR, FieldKey.TRACK, FieldKey.DISC_NO,
            FieldKey.COMPOSER, FieldKey.COMMENT, FieldKey.LYRICS
        };
        
        for (FieldKey standard : standardFields) {
            if (standard.equals(fieldKey)) {
                return true;
            }
        }
        return false;
    }
    
    private static void extractFromFilename(File audioFile, MusicMetadata metadata) {
        String filename = audioFile.getName();
        String nameWithoutExt = filename.replaceFirst("[.][^.]+$", "");
        
        logger.debug("Falling back to filename parsing: {}", filename);
        
        if (nameWithoutExt.contains(" - ")) {
            String[] parts = nameWithoutExt.split(" - ", 2);
            if (metadata.artist == null || metadata.artist.isEmpty()) {
                metadata.artist = parts[0].trim();
            }
            if (metadata.title == null || metadata.title.isEmpty()) {
                metadata.title = parts[1].trim();
            }
        } else if (nameWithoutExt.contains("_")) {
            String[] parts = nameWithoutExt.split("_", 2);
            if (metadata.artist == null || metadata.artist.isEmpty()) {
                metadata.artist = parts[0].trim();
            }
            if (metadata.title == null || metadata.title.isEmpty()) {
                metadata.title = parts[1].trim();
            }
        } else {
            if (metadata.title == null || metadata.title.isEmpty()) {
                metadata.title = nameWithoutExt;
            }
            if (metadata.artist == null || metadata.artist.isEmpty()) {
                metadata.artist = "Unknown Artist";
            }
        }
        
        if (metadata.album == null || metadata.album.isEmpty()) {
            metadata.album = "Unknown Album";
        }
        
        if (metadata.genre == null || metadata.genre.isEmpty()) {
            metadata.genre = "Unknown";
        }
        
        logger.debug("Extracted from filename: {} - {}", metadata.artist, metadata.title);
    }
    
    private static String sanitizeFileName(String name) {
        if (name == null) return "";
        return name
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("\\s+", "_")
            .replaceAll("_{2,}", "_")
            .trim();
    }
    
    public static String extractAndSaveAlbumArt(File audioFile, String outputDir) {
        try {
            AudioFile file = AudioFileIO.read(audioFile);
            Tag tag = file.getTag();
            
            if (tag != null && tag.getFirstArtwork() != null) {
                Artwork artwork = tag.getFirstArtwork();
                
                if (artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                    Path dirPath = Paths.get(outputDir);
                    if (!Files.exists(dirPath)) {
                        Files.createDirectories(dirPath);
                    }
                    
                    String baseName = audioFile.getName().replaceFirst("[.][^.]+$", "");
                    String extension = getImageExtension(artwork.getMimeType());
                    String filename = sanitizeFileName(baseName) + extension;
                    Path outputPath = dirPath.resolve(filename);
                    
                    Files.write(outputPath, artwork.getBinaryData());
                    
                    logger.info("Saved album art to: {}", outputPath);
                    return outputPath.toString();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to extract album art: {}", e.getMessage());
        }
        
        return null;
    }
    
    public static Map<String, Object> getMetadataAsMap(File audioFile) {
        Map<String, Object> metadataMap = new HashMap<>();
        MusicMetadata metadata = extractMetadata(audioFile);
        
        metadataMap.put("title", metadata.title);
        metadataMap.put("artist", metadata.artist);
        metadataMap.put("album", metadata.album);
        metadataMap.put("albumArtist", metadata.albumArtist);
        metadataMap.put("genre", metadata.genre);
        metadataMap.put("year", metadata.year);
        metadataMap.put("bitrate", metadata.bitrate + " kbps");
        metadataMap.put("duration", formatDuration(metadata.duration));
        metadataMap.put("trackNumber", metadata.trackNumber);
        metadataMap.put("discNumber", metadata.discNumber);
        metadataMap.put("composer", metadata.composer);
        metadataMap.put("comment", metadata.comment);
        metadataMap.put("fileSize", formatFileSize(metadata.fileSize));
        metadataMap.put("hasAlbumArt", metadata.albumArtBase64 != null && !metadata.albumArtBase64.isEmpty());
        
        if (!metadata.additionalTags.isEmpty()) {
            metadataMap.put("additionalTags", metadata.additionalTags);
        }
        
        return metadataMap;
    }
    
    private static String formatDuration(int seconds) {
        if (seconds <= 0) return "0:00";
        
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}