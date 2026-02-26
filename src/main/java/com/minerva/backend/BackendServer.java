package com.minerva.backend;

import com.minerva.library.LibraryManager;
import com.minerva.model.MusicFile;
import com.minerva.network.JLibTorrentManager;
import com.minerva.playlist.PlaylistManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;

import com.minerva.dht.DHTKeywordManager;
import com.minerva.dht.KeywordSearchClient;
import com.minerva.dht.KeywordSearchServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class BackendServer {
    private final LibraryManager libraryManager;
    private final JLibTorrentManager torrentManager;
    private final PlaylistManager playlistManager;
    private final Path projectRoot;
    private final Path electronRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(BackendServer.class);
    private DHTKeywordManager dhtKeywordManager;
    private KeywordSearchServer keywordSearchServer;
    // Pending download metadata: hash -> {artist, album, tracks}
    private final Map<String, Map<String, Object>> pendingDownloads = new java.util.concurrent.ConcurrentHashMap<>();

    // Fields for directory paths
    private final Path libraryPath;
    private final Path torrentsPath;
    private final Path downloadsPath;
    private final Path albumArtPath;
    private final String libDir;
    private final String torrentDir;
    private final String downloadsDir;
    private final String albumArtDir;

    public BackendServer() {
        // Read directories from environment (with fallbacks)
        this.libDir = System.getenv().getOrDefault("LIBRARY_DIR", "library");
        this.torrentDir = System.getenv().getOrDefault("TORRENT_DIR", "torrent_files");
        this.downloadsDir = System.getenv().getOrDefault("DOWNLOADS_DIR", "downloads");
        this.albumArtDir = System.getenv().getOrDefault("ALBUM_ART_DIR", "album_art");

        this.projectRoot = Paths.get(System.getProperty("user.dir"));
        this.electronRoot = projectRoot.resolve("musicshare-electron");

        this.libraryPath = projectRoot.resolve(libDir);
        this.torrentsPath = projectRoot.resolve(torrentDir);
        this.downloadsPath = projectRoot.resolve(downloadsDir);
        this.albumArtPath = projectRoot.resolve(albumArtDir);

        // Ensure directories exist
        try {
            Files.createDirectories(libraryPath);
            Files.createDirectories(torrentsPath);
            Files.createDirectories(downloadsPath);
            Files.createDirectories(albumArtPath);
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
        }

        // Create torrent manager (starts BT runtime quickly)
        this.torrentManager = JLibTorrentManager.getInstance(downloadsPath.toFile());
        this.libraryManager = new LibraryManager(torrentManager, libraryPath, torrentsPath);
        this.playlistManager = new PlaylistManager(projectRoot.toString());

        // Set up download completion callback to import into library
        this.torrentManager.setDownloadCompleteCallback(hashHex -> {
            Map<String, Object> metadata = pendingDownloads.remove(hashHex);
            if (metadata == null) {
                logger.info("Torrent finished {} but no pending metadata (likely already in library)", hashHex);
                return;
            }
            String artist = (String) metadata.get("artist");
            String album = (String) metadata.get("album");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> tracks = (List<Map<String, String>>) metadata.get("tracks");
            logger.info("Download completed for {} - importing to library as {}/{}", hashHex, artist, album);

            // Import in background thread to avoid blocking the bt callback
            new Thread(() -> {
                try {
                    libraryManager.importCompletedDownload(hashHex, artist, album, tracks);
                    logger.info("Successfully imported and auto-seeding: {}", hashHex);
                } catch (Exception e) {
                    logger.error("Failed to import download {} into library", hashHex, e);
                }
            }, "DownloadImport-" + hashHex.substring(0, Math.min(8, hashHex.length()))).start();
        });
    }

    public void start(int port) {
        long maxSize = 500_000_000L; // 500 MB

        // 1. Start Javalin immediately (before heavy init)
        Javalin app = Javalin.create(cfg -> {
            cfg.maxRequestSize = maxSize;
            cfg.configureServletContextHandler(sch -> sch.setMaxFormContentSize((int) maxSize));
        }).start("127.0.0.1", port);

        // CORS headers
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });

        // Register all endpoints (they will work as soon as libraryManager is ready)
        registerEndpoints(app);

        logger.info("Javalin started on port {}", port);

        // 2. Start keyword search server (depends on libraryManager and torrentManager)
        int searchPort = Integer.parseInt(System.getenv().getOrDefault("SEARCH_PORT", "4568"));
        this.dhtKeywordManager = new DHTKeywordManager(searchPort, torrentManager);
        try {
            this.keywordSearchServer = new KeywordSearchServer(searchPort, libraryManager, torrentManager.getListenPort());
            this.keywordSearchServer.start();
            logger.info("Keyword search server started on port {}", searchPort);
        } catch (IOException e) {
            logger.error("Failed to start keyword search server", e);
        }

        // 3. Heavy initialisation in a background thread (does not block endpoints)
        new Thread(() -> {
            try {
                logger.info("Starting background library initialisation...");
                libraryManager.enrichIncompleteMetadata();
                libraryManager.loadLibraryFromTorrents();

                logger.info("Waiting for DHT bootstrap before announcing keywords...");
                Thread.sleep(5000);
                libraryManager.announceAllKeywords(dhtKeywordManager);
                logger.info("Keyword announcement complete.");

                libraryManager.seedExistingTorrents();
                logger.info("Background initialisation finished.");
            } catch (Exception e) {
                logger.error("Background initialisation failed", e);
            }
        }, "BackendInit").start();

        System.err.println("Backend server started on port " + port + " bound to 127.0.0.1");
    }

    private void registerEndpoints(Javalin app) {
        app.get("/api/test", ctx -> ctx.result("OK"));
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

        app.get("/api/playlists", ctx -> {
            try {
                ctx.json(playlistManager.getAllPlaylists());
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error loading playlists");
            }
        });

        app.get("/api/dht-search", ctx -> {
            String query = ctx.queryParam("q");
            if (query == null) {
                ctx.status(400).result("Missing query");
                return;
            }
            String[] keywords = query.toLowerCase().split("\\s+");
            Map<String, MusicFile> trackByKey = new java.util.concurrent.ConcurrentHashMap<>();
            Map<String, java.util.concurrent.atomic.AtomicInteger> matchCounts = new java.util.concurrent.ConcurrentHashMap<>();
            java.util.concurrent.ExecutorService searchExecutor = java.util.concurrent.Executors.newFixedThreadPool(keywords.length);
            java.util.List<java.util.concurrent.Future<?>> searchFutures = new java.util.ArrayList<>();
            for (String kw : keywords) {
                searchFutures.add(searchExecutor.submit(() -> {
                    List<KeywordSearchClient.SearchResult> peerResults = dhtKeywordManager.searchKeyword(kw);
                    Set<String> seenForThisKeyword = new HashSet<>();
                    for (KeywordSearchClient.SearchResult sr : peerResults) {
                        String key = (sr.torrentHash != null ? sr.torrentHash : "") + "|" + (sr.title != null ? sr.title : "");
                        if (seenForThisKeyword.add(key)) {
                            MusicFile mf = new MusicFile();
                            mf.setTitle(sr.title);
                            mf.setArtist(sr.artist);
                            mf.setAlbum(sr.album);
                            mf.setTorrentHash(sr.torrentHash);
                            if (sr.genre != null) mf.setGenre(sr.genre);
                            if (sr.year != null) mf.setYear(sr.year);
                            trackByKey.putIfAbsent(key, mf);
                            matchCounts.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
                        }
                    }
                }));
            }
            for (java.util.concurrent.Future<?> f : searchFutures) {
                try { f.get(10, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
            searchExecutor.shutdownNow();
            int threshold = Math.max(1, keywords.length / 2);
            List<MusicFile> ranked = trackByKey.entrySet().stream()
                    .filter(e -> matchCounts.get(e.getKey()).get() >= threshold)
                    .sorted((a, b) -> matchCounts.get(b.getKey()).get() - matchCounts.get(a.getKey()).get())
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
            ctx.json(ranked);
        });

        app.post("/api/fetch-torrent/{hash}", ctx -> {
            String hash = ctx.pathParam("hash");
            String magnet = "magnet:?xt=urn:btih:" + hash;
            try {
                String body = ctx.body();
                Map<String, Object> metadata = null;
                if (body != null && !body.isEmpty()) {
                    try {
                        metadata = objectMapper.readValue(body, Map.class);
                    } catch (Exception e) {
                        logger.warn("Could not parse fetch-torrent metadata: {}", e.getMessage());
                    }
                }

                if (metadata != null && metadata.containsKey("artist") && metadata.containsKey("album")) {
                    pendingDownloads.put(hash, metadata);
                    logger.info("Stored pending download metadata for {}: artist={}, album={}",
                            hash, metadata.get("artist"), metadata.get("album"));
                }

                JLibTorrentManager.MagnetResult result = torrentManager.seedMagnet(magnet);
                Path torrentFilePath = torrentsPath.resolve(hash + ".torrent");
                torrentManager.saveTorrentFile(hash, torrentFilePath);

                ctx.status(200).result("Torrent fetch started");
            } catch (Exception e) {
                logger.error("Failed to fetch torrent for hash {}", hash, e);
                pendingDownloads.remove(hash);
                ctx.status(500).result("Failed to fetch torrent: " + e.getMessage());
            }
        });

        app.get("/api/downloads", ctx -> {
            try {
                List<JLibTorrentManager.TorrentStatus> statuses = torrentManager.getTorrentStatuses();
                List<Map<String, Object>> downloads = new ArrayList<>();
                for (JLibTorrentManager.TorrentStatus ts : statuses) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("hash", ts.hash);
                    info.put("name", ts.name);
                    info.put("progress", Math.round(ts.progress * 10000.0) / 100.0);
                    info.put("downloadRate", ts.downloadRate);
                    info.put("uploadRate", ts.uploadRate);
                    info.put("totalSize", ts.totalSize);
                    info.put("totalDone", ts.totalDone);
                    info.put("peers", ts.peers);
                    info.put("seeds", ts.seeds);
                    info.put("state", ts.state);
                    info.put("seeding", ts.seeding);
                    info.put("paused", ts.paused);
                    downloads.add(info);
                }
                ctx.json(downloads);
            } catch (Exception e) {
                logger.error("Failed to get download statuses", e);
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        app.post("/api/downloads/{hash}/pause", ctx -> {
            String hash = ctx.pathParam("hash");
            try {
                torrentManager.pauseTorrent(hash);
                ctx.status(200).result("Paused");
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        app.post("/api/downloads/{hash}/resume", ctx -> {
            String hash = ctx.pathParam("hash");
            try {
                torrentManager.resumeTorrent(hash);
                ctx.status(200).result("Resumed");
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        app.delete("/api/downloads/{hash}", ctx -> {
            String hash = ctx.pathParam("hash");
            try {
                boolean deleteFiles = "true".equals(ctx.queryParam("deleteFiles"));
                torrentManager.removeTorrent(hash, deleteFiles);
                ctx.status(200).result("Removed");
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        app.post("/api/playlists", ctx -> {
            try {
                Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
                String name = (String) body.get("name");
                String description = (String) body.getOrDefault("description", "");
                int id = playlistManager.createPlaylist(name, description);
                ctx.status(201).json(Map.of("id", id));
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error creating playlist");
            }
        });

        app.get("/api/playlists/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                Map<String, Object> playlist = playlistManager.getPlaylist(id);
                if (playlist == null) {
                    ctx.status(404).result("Playlist not found");
                } else {
                    ctx.json(playlist);
                }
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error loading playlist");
            }
        });

        app.put("/api/playlists/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
                String name = (String) body.get("name");
                String description = (String) body.getOrDefault("description", "");
                String iconPath = (String) body.get("iconPath");
                playlistManager.updatePlaylist(id, name, description, iconPath);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error updating playlist");
            }
        });

        app.delete("/api/playlists/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                playlistManager.deletePlaylist(id);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result(e.getMessage());
            }
        });

        app.post("/api/playlists/{id}/tracks", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
                String trackId = (String) body.get("trackId");
                playlistManager.addTrackToPlaylist(id, trackId);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error adding track to playlist");
            }
        });

        app.delete("/api/playlists/{id}/tracks/{trackId}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                String trackId = ctx.pathParam("trackId");
                playlistManager.removeTrackFromPlaylist(id, trackId);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error removing track from playlist");
            }
        });

        app.put("/api/playlists/{id}/tracks/reorder", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                List<String> trackIds = objectMapper.readValue(ctx.body(), List.class);
                playlistManager.reorderPlaylistTracks(id, trackIds);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error reordering tracks");
            }
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
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                ctx.result(data);
            } catch (IOException e) {
                e.printStackTrace();
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
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                ctx.result(data);
            } catch (IOException e) {
                e.printStackTrace();
                ctx.status(500).result("Error reading file");
            }
        });

        app.get("/api/cover/{id}", ctx -> {
            String id = ctx.pathParam("id");
            MusicFile track = libraryManager.getTrackById(id);
            if (track != null) {
                if (track.getArtist() != null && track.getAlbum() != null) {
                    String safeArtist = sanitizeFileName(track.getArtist());
                    String safeAlbum = sanitizeFileName(track.getAlbum());
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
                                e.printStackTrace();
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
                        e.printStackTrace();
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

        // Log viewer endpoint
        app.get("/api/logs", ctx -> {
            int lines = 200;
            String linesParam = ctx.queryParam("lines");
            if (linesParam != null) {
                try { lines = Math.min(Integer.parseInt(linesParam), 5000); }
                catch (NumberFormatException ignored) {}
            }
            Path logFile = Paths.get(System.getProperty("user.dir"), "logs/minerva.log");
            if (!Files.exists(logFile)) {
                ctx.result("No log file found");
                return;
            }
            List<String> allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lines);
            List<String> tail = allLines.subList(start, allLines.size());
            ctx.contentType("text/plain").result(String.join("\n", tail));
        });
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .trim();
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "4567"));
        new BackendServer().start(port);
    }
}