package com.minerva.storage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileValidator {
    private static final Logger logger = LoggerFactory.getLogger(FileValidator.class);
    private static final List<String> ALLOWED_AUDIO_EXTENSIONS = Arrays.asList(
        ".mp3", ".flac", ".wav", ".aac", ".ogg", ".m4a"
    );
    
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(
        ".jpg", ".jpeg", ".png", ".gif"
    );
    
    public static ValidationResult validateMusicDirectory(Path directory) {
        ValidationResult result = new ValidationResult();
        
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            result.addError("Path does not exist or is not a directory");
            return result;
        }
        
        File[] files = directory.toFile().listFiles();
        if (files == null || files.length == 0) {
            result.addError("Directory is empty");
            return result;
        }
        
        boolean hasCoverArt = false;
        boolean hasAudioFiles = false;
        int audioFileCount = 0;
        
        for (File file : files) {
            String fileName = file.getName().toLowerCase();
            
            if (isCoverArtFile(fileName)) {
                hasCoverArt = true;
                result.coverArtPath = file.getAbsolutePath();
            }
            
            if (isAudioFile(fileName)) {
                hasAudioFiles = true;
                audioFileCount++;
                result.addAudioFile(file);
            }
            
            if (fileName.equals("metadata.json")) {
                result.hasMetadata = true;
            }
        }
        
        if (!hasCoverArt) {
            result.addWarning("No cover art found (looking for files named 'cover', 'album', or 'folder')");
        }
        
        if (!hasAudioFiles) {
            result.addError("No audio files found in directory");
        } else {
            result.audioFileCount = audioFileCount;
        }
        
        return result;
    }
    
    private static boolean isCoverArtFile(String fileName) {
        String[] coverKeywords = {"cover", "album", "folder", "front", "artwork"};
        for (String keyword : coverKeywords) {
            if (fileName.contains(keyword)) {
                for (String ext : ALLOWED_IMAGE_EXTENSIONS) {
                    if (fileName.endsWith(ext)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private static boolean isAudioFile(String fileName) {
        for (String ext : ALLOWED_AUDIO_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    public static class ValidationResult {
        private boolean valid;
        public String coverArtPath;
        public boolean hasMetadata;
        public int audioFileCount;
        public List<File> audioFiles;
        private List<String> errors;
        private List<String> warnings;
        
        public ValidationResult() {
            this.valid = true;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.audioFiles = new ArrayList<>();
            this.coverArtPath = "";
        }
        
        public boolean isValid() { 
            return valid && errors.isEmpty(); 
        }
        
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addAudioFile(File file) {
            audioFiles.add(file);
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public List<File> getAudioFiles() {
            return new ArrayList<>(audioFiles);
        }
    }
}