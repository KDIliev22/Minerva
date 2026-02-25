package com.minerva.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;

public class AudioConverter {
    private static final Logger logger = LoggerFactory.getLogger(AudioConverter.class);
    
    public static File convertToWav(File inputFile, Path outputDir) throws IOException {
        String baseName = inputFile.getName().replaceFirst("[.][^.]+$", "");
        Path outputPath = outputDir.resolve(baseName + ".wav");
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", inputFile.getAbsolutePath(),
                "-acodec", "pcm_s16le",
                "-ar", "44100",
                outputPath.toString()
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && Files.exists(outputPath)) {
                logger.info("Converted {} to WAV format", inputFile.getName());
                return outputPath.toFile();
            }
        } catch (Exception e) {
            logger.warn("ffmpeg conversion failed: {}", e.getMessage());
        }
        
        logger.warn("Could not convert file, using original with .wav extension");
        Files.copy(inputFile.toPath(), outputPath, StandardCopyOption.REPLACE_EXISTING);
        return outputPath.toFile();
    }
    
    public static boolean hasFfmpeg() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}