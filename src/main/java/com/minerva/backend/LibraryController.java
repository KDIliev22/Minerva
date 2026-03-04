package com.minerva.backend;

import com.minerva.StringUtils;
import com.minerva.library.LibraryManager;
import com.minerva.model.MusicFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Handles library-related endpoints: tracks, albums, streaming, downloads,
 * cover art, search, and file uploads.
 */
public class LibraryController {
    private static final Logger logger = LoggerFactory.getLogger(LibraryController.class);
    private final LibraryManager libraryManager;
    private final Path projectRoot;
    private final Path albumArtPath;
    private final ObjectMapper objectMapper;

    public LibraryController(LibraryManager libraryManager, Path projectRoot,
                             Path albumArtPath, ObjectMapper objectMapper) {
        this.libraryManager = libraryManager;
        this.projectRoot = projectRoot;
        this.albumArtPath = albumArtPath;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public void register(Javalin app) {
        app.get("/api/tracks", ctx -> ctx.json(libraryManager.getTracks()));

        app.get("/api/albums", ctx -> {
            Map<String, Map<String, Object>> albumMap = new LinkedHashMap<>();
            for (MusicFile track : libraryManager.getTracks()) {
                String key = track.getArtist() + "::" + track.getAlbum();
                if (!albumMap.containsKey(key)) {
                    Map<String, Object> albumInfo = new LinkedHashMap<>();
                    albumInfo.put("artist", track.getArtist());
                    albumInfo.put("album", track.getAlbum());
                    albumInfo.put("year", track.getYear());
                    albumInfo.put("genre", track.getGenre());
                    albumInfo.put("coverTrackId", track.getTorrentHash());
                    albumInfo.put("trackCount", 1);
                    albumMap.put(key, albumInfo);
                } else {
                    Map<String, Object> albumInfo = albumMap.get(key);
                    albumInfo.put("trackCount", (int) albumInfo.get("trackCount") + 1);
                }
            }
            ctx.json(new ArrayList<>(albumMap.values()));
        });

        app.get("/api/stream/{id}", ctx -> {
            String id = ctx.pathParam("id");
            MusicFile track = libraryManager.getTrackById(id);
            if (track == null) {
                ctx.status(404).result("Track not found");
                return;
            }
            File file = projectRoot.resolve(track.getFilePath()).toFile();
            if (!file.exists()) {
                ctx.status(404).result("File not found");
                return;
            }
            ctx.contentType("audio/mpeg");
            ctx.header("Content-Length", String.valueOf(file.length()));
            try {
                ctx.result(new FileInputStream(file));
            } catch (IOException e) {
                logger.error("Error streaming file: {}", file.getName(), e);
                ctx.status(500).result("Error reading file");
            }
        });

        app.get("/api/download/{id}", ctx -> {
            String id = ctx.pathParam("id");
            MusicFile track = libraryManager.getTrackById(id);
            if (track == null) {
                ctx.status(404).result("Track not found");
                return;
            }
            File file = projectRoot.resolve(track.getFilePath()).toFile();
            if (!file.exists()) {
                ctx.status(404).result("File not found");
                return;
            }
            ctx.contentType("audio/mpeg");
            ctx.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            ctx.header("Content-Length", String.valueOf(file.length()));
            try {
                ctx.result(new FileInputStream(file));
            } catch (IOException e) {
                logger.error("Error downloading file: {}", file.getName(), e);
                ctx.status(500).result("Error reading file");
            }
        });

        app.get("/api/cover/{id}", ctx -> {
            String id = ctx.pathParam("id");
            MusicFile track = libraryManager.getTrackById(id);
            if (track != null) {
                if (track.getArtist() != null && track.getAlbum() != null) {
                    String safeArtist = StringUtils.sanitizeFileName(track.getArtist());
                    String safeAlbum = StringUtils.sanitizeFileName(track.getAlbum());
                    String[] extensions = {".jpg", ".png"};
                    for (String ext : extensions) {
                        File candidate = albumArtPath.resolve(safeArtist + "_" + safeAlbum + ext).toFile();
                        if (candidate.exists()) {
                            try {
                                byte[] data = Files.readAllBytes(candidate.toPath());
                                ctx.contentType("image/jpeg".equals(ext) ? "image/jpeg" : "image/png");
                                ctx.result(data);
                                return;
                            } catch (IOException e) {
                                logger.error("Error reading album art for {}", candidate.getName(), e);
                                ctx.status(500).result("Error reading album art");
                            }
                        }
                    }
                }
                String base64 = track.getAlbumArtBase64();
                if (base64 != null && !base64.isEmpty()) {
                    try {
                        byte[] imageData = Base64.getDecoder().decode(base64);
                        String mime = track.getAlbumArtMimeType();
                        if (mime == null) mime = "image/jpeg";
                        ctx.contentType(mime);
                        ctx.result(imageData);
                        return;
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid base64 album art data", e);
                    }
                }
            } else {
                byte[] artData = libraryManager.getAlbumArtByTorrentHash(id);
                if (artData != null) {
                    ctx.contentType("image/jpeg");
                    ctx.result(artData);
                    return;
                }
            }
            ctx.status(404).result("No album art");
        });

        app.post("/api/upload", ctx -> {
            List<File> tempFiles = new ArrayList<>();
            try {
                String metadataJson = ctx.formParam("metadata");
                Map<String, Object> overrideMetadata = null;
                if (metadataJson != null && !metadataJson.isEmpty()) {
                    overrideMetadata = objectMapper.readValue(metadataJson, Map.class);
                }

                for (UploadedFile upload : ctx.uploadedFiles("files")) {
                    File tempFile = new File("uploads/" + upload.getFilename());
                    tempFile.getParentFile().mkdirs();
                    Files.copy(upload.getContent(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Received upload: {}", upload.getFilename());
                    tempFiles.add(tempFile);
                }

                boolean isAlbum = overrideMetadata != null && Boolean.TRUE.equals(overrideMetadata.get("isAlbum"));
                final boolean[] success = {false};
                final String[] errorMsg = {null};
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

                if (isAlbum) {
                    final Map<String, Object> meta = overrideMetadata;
                    libraryManager.uploadAlbum(tempFiles,
                        () -> { success[0] = true; latch.countDown(); },
                        err -> { errorMsg[0] = err; latch.countDown(); },
                        meta);
                } else {
                    final Map<String, Object> meta = overrideMetadata;
                    libraryManager.uploadSingleFile(tempFiles.get(0),
                        () -> { success[0] = true; latch.countDown(); },
                        err -> { errorMsg[0] = err; latch.countDown(); },
                        meta);
                }

                latch.await();

                if (success[0]) {
                    logger.info("Upload successful — library reloaded, torrent seeding.");
                    ctx.status(200).result("Upload successful");
                } else {
                    ctx.status(500).result("Upload failed: " + errorMsg[0]);
                }

            } catch (Exception e) {
                logger.error("Error during upload handling", e);
                ctx.status(500).result("Upload failed: " + e.getMessage());
            } finally {
                for (File f : tempFiles) {
                    try { if (f.exists()) f.delete(); } catch (Exception ignored) {}
                }
            }
        });

        app.get("/api/search", ctx -> {
            String query = ctx.queryParam("q");
            if (query == null) {
                ctx.status(400).result("Missing query");
                return;
            }
            ctx.json(libraryManager.search(query));
        });
    }
}
