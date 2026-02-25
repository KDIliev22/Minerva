package com.minerva.network;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class TorrentValidator {
    private static final Logger logger = LoggerFactory.getLogger(TorrentValidator.class);
    
    public boolean validateTorrentStructure(Path torrentContentDir) {
        try {
            if (!Files.isDirectory(torrentContentDir)) {
                logger.error("Torrent content is not a directory: {}", torrentContentDir);
                return false;
            }
            
            Path metadataFile = findMetadataFile(torrentContentDir);
            if (metadataFile == null) {
                logger.error("No metadata.json found in torrent");
                return false;
            }
            
            TorrentMetadata metadata = parseMetadata(metadataFile);
            if (metadata == null) {
                logger.error("Failed to parse metadata");
                return false;
            }
            
            if (!metadata.isMinervaSuffix()) {
                logger.error("Torrent missing MINERVA suffix");
                return false;
            }
            
            if (metadata.getTorrentType() == TorrentMetadata.TorrentType.SINGLE) {
                return validateSingleStructure(torrentContentDir, metadata);
            } else {
                return validateAlbumStructure(torrentContentDir, metadata);
            }
            
        } catch (Exception e) {
            logger.error("Failed to validate torrent structure", e);
            return false;
        }
    }
    
    private boolean validateSingleStructure(Path contentDir, TorrentMetadata metadata) {
        try {
            Path artistDir = findArtistDirectory(contentDir, metadata.getArtist());
            if (artistDir == null) return false;
            
            Path albumDir = findAlbumDirectory(artistDir, metadata.getAlbum());
            if (albumDir == null) return false;
            
            List<Path> audioFiles = findAudioFiles(albumDir);
            if (audioFiles.size() != 1) {
                logger.error("Single torrent should have exactly 1 audio file, found: {}", audioFiles.size());
                return false;
            }
            
            Path audioFile = audioFiles.get(0);
            String expectedPattern = String.format("^\\d{2}_%s\\.\\w+$", 
                sanitizeFileName(metadata.getTrackInfo().getTitle()));
            
            if (!audioFile.getFileName().toString().matches(expectedPattern)) {
                logger.error("Audio file name doesn't match pattern: {}", audioFile);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to validate single structure", e);
            return false;
        }
    }
    
    private boolean validateAlbumStructure(Path contentDir, TorrentMetadata metadata) {
        try {
            Path artistDir = findArtistDirectory(contentDir, metadata.getArtist());
            if (artistDir == null) return false;
            
            Path albumDir = findAlbumDirectory(artistDir, metadata.getAlbum());
            if (albumDir == null) return false;
            
            List<Path> audioFiles = findAudioFiles(albumDir);
            if (audioFiles.size() != metadata.getTracks().size()) {
                logger.error("Album torrent should have {} audio files, found: {}", 
                    metadata.getTracks().size(), audioFiles.size());
                return false;
            }
            
            for (TorrentMetadata.TrackInfo track : metadata.getTracks()) {
                String expectedPattern = String.format("^\\d{2}_\\d{2}_%s\\.\\w+$", 
                    sanitizeFileName(track.getTitle()));
                
                boolean found = audioFiles.stream()
                    .anyMatch(file -> file.getFileName().toString().matches(expectedPattern));
                
                if (!found) {
                    logger.error("Missing track: {}", track.getTitle());
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to validate album structure", e);
            return false;
        }
    }
    
    private Path findMetadataFile(Path dir) throws IOException {
        return Files.walk(dir)
            .filter(path -> path.getFileName().toString().equals("metadata.json"))
            .findFirst()
            .orElse(null);
    }
    
    private TorrentMetadata parseMetadata(Path metadataFile) {
        return null;
    }
    
    private Path findArtistDirectory(Path rootDir, String artistName) throws IOException {
        return Files.list(rootDir)
            .filter(Files::isDirectory)
            .filter(dir -> dir.getFileName().toString().equals(sanitizeFileName(artistName)))
            .findFirst()
            .orElse(null);
    }
    
    private Path findAlbumDirectory(Path artistDir, String albumName) throws IOException {
        return Files.list(artistDir)
            .filter(Files::isDirectory)
            .filter(dir -> dir.getFileName().toString().equals(sanitizeFileName(albumName)))
            .findFirst()
            .orElse(null);
    }
    
    private List<Path> findAudioFiles(Path dir) throws IOException {
        return Files.list(dir)
            .filter(path -> !Files.isDirectory(path))
            .filter(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".mp3") || name.endsWith(".flac") || 
                       name.endsWith(".wav") || name.endsWith(".m4a") || 
                       name.endsWith(".ogg");
            })
            .collect(Collectors.toList());
    }
    
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_")
                      .replaceAll("\\s+", "_")
                      .toLowerCase();
    }
}