package com.minerva.storage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheManager {
    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);
    private static CacheManager instance;
    private final Path cacheDir;
    private final Map<String, Long> accessTimes;
    private final int MAX_CACHE_SIZE = 100;
    private final long MAX_CACHE_AGE = 24 * 60 * 60 * 1000;
    
    private CacheManager() {
        this.cacheDir = Paths.get("cache", "covers");
        this.accessTimes = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_CACHE_SIZE || 
                       System.currentTimeMillis() - eldest.getValue() > MAX_CACHE_AGE;
            }
        };
        
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            logger.error("Failed to create cache directory", e);
        }
    }
    
    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }
    
    public BufferedImage getAlbumCover(String albumId, String artist, String albumTitle) {
        String cacheKey = generateCacheKey(albumId, artist, albumTitle);
        Path cachedImage = cacheDir.resolve(cacheKey + ".jpg");
        
        if (Files.exists(cachedImage)) {
            accessTimes.put(cacheKey, System.currentTimeMillis());
            try {
                return ImageIO.read(cachedImage.toFile());
            } catch (IOException e) {
                try {
                    Files.delete(cachedImage);
                } catch (IOException ex) {
                    logger.warn("Failed to delete corrupted cache file", ex);
                }
            }
        }
        
        BufferedImage image = downloadAlbumCover(albumId, artist, albumTitle);
        if (image != null) {
            saveToCache(cacheKey, image);
        }
        
        return image;
    }
    
    private BufferedImage downloadAlbumCover(String albumId, String artist, String albumTitle) {
        try {
            return createDefaultCover(artist, albumTitle);
        } catch (Exception e) {
            logger.error("Failed to create default cover", e);
            return null;
        }
    }
    
    private void saveToCache(String cacheKey, BufferedImage image) {
        try {
            Path cacheFile = cacheDir.resolve(cacheKey + ".jpg");
            ImageIO.write(image, "jpg", cacheFile.toFile());
            accessTimes.put(cacheKey, System.currentTimeMillis());
        } catch (IOException e) {
            logger.error("Failed to save image to cache", e);
        }
    }
    
    private String generateCacheKey(String albumId, String artist, String albumTitle) {
        return (albumId != null ? albumId : "") + "_" + 
               (artist != null ? artist.hashCode() : 0) + "_" + 
               (albumTitle != null ? albumTitle.hashCode() : 0);
    }
    
    private BufferedImage createDefaultCover(String artist, String albumTitle) {
        int width = 300;
        int height = 300;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / width;
                int g = (y * 255) / height;
                int b = 128;
                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }
        
        return image;
    }
    
    public void clearCache() {
        try {
            Files.list(cacheDir)
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                     } catch (IOException e) {
                         logger.warn("Failed to delete cache file: {}", path, e);
                     }
                 });
            accessTimes.clear();
        } catch (IOException e) {
            logger.error("Failed to clear cache", e);
        }
    }
}