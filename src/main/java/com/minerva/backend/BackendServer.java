package com.minerva.backend;

import com.minerva.StringUtils;
import com.minerva.library.LibraryManager;
import com.minerva.network.JLibTorrentManager;
import com.minerva.playlist.PlaylistManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import com.minerva.dht.DHTKeywordManager;
import com.minerva.dht.KeywordSearchServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import bt.dht.DHTService;
import bt.metainfo.TorrentId;

/**
 * Main backend server that bootstraps Javalin and delegates endpoint
 * registration to specialised controller classes.
 */
public class BackendServer {
    private static final Logger logger = LoggerFactory.getLogger(BackendServer.class);

    private final LibraryManager libraryManager;
    private final JLibTorrentManager torrentManager;
    private final PlaylistManager playlistManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DHTKeywordManager dhtKeywordManager;
    private final KeywordSearchServer keywordSearchServer;
    private final Map<String, Map<String, Object>> pendingDownloads = new ConcurrentHashMap<>();

    private final Path projectRoot;
    private final Path libraryPath;
    private final Path torrentsPath;
    private final Path downloadsPath;
    private final Path albumArtPath;

    public BackendServer() {
        String libDir = System.getenv().getOrDefault("LIBRARY_DIR", "library");
        String torrentDir = System.getenv().getOrDefault("TORRENT_DIR", "torrent_files");
        String downloadsDir = System.getenv().getOrDefault("DOWNLOADS_DIR", "downloads");
        String albumArtDir = System.getenv().getOrDefault("ALBUM_ART_DIR", "album_art");
        int searchPort = Integer.parseInt(System.getenv().getOrDefault("SEARCH_PORT", "4568"));
        String crawlerUrl = System.getenv("CRAWLER_URL");

        this.projectRoot = Paths.get(System.getProperty("user.dir"));
        this.libraryPath = projectRoot.resolve(libDir);
        this.torrentsPath = projectRoot.resolve(torrentDir);
        this.downloadsPath = projectRoot.resolve(downloadsDir);
        this.albumArtPath = projectRoot.resolve(albumArtDir);

        try {
            Files.createDirectories(libraryPath);
            Files.createDirectories(torrentsPath);
            Files.createDirectories(downloadsPath);
            Files.createDirectories(albumArtPath);
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
        }

        this.torrentManager = JLibTorrentManager.getInstance(downloadsPath.toFile());
        this.libraryManager = new LibraryManager(torrentManager, libraryPath, torrentsPath);
        this.playlistManager = new PlaylistManager(projectRoot.toString());

        setupDownloadCallback();
        setupDHTAnnouncer();

        this.dhtKeywordManager = new DHTKeywordManager(searchPort, torrentManager, crawlerUrl);
        try {
            this.keywordSearchServer = new KeywordSearchServer(searchPort, libraryManager,
                    torrentManager.getListenPort(), dhtKeywordManager);
            this.keywordSearchServer.start();
            logger.info("Keyword search server started on port {}", searchPort);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start keyword search server", e);
        }
    }

    private void setupDownloadCallback() {
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

    private void setupDHTAnnouncer() {
        String minervaInfohash = "2a242fcb7604ddcf795af2c60546a1b7e2be3f40";
        TorrentId minervaId = TorrentId.fromBytes(StringUtils.hexToBytes(minervaInfohash));

        ScheduledExecutorService announcer = Executors.newSingleThreadScheduledExecutor();
        announcer.scheduleAtFixedRate(() -> {
            try {
                DHTService dhtService = torrentManager.getDHTService();
                if (dhtService != null) {
                    // getPeers triggers a DHT lookup + announce_peer for the
                    // Minerva discovery infohash, making this node visible
                    // to other Minerva peers and the DHT crawler.
                    long count = dhtService.getPeers(minervaId).count();
                    logger.info("DHT announce complete – found {} peers for Minerva infohash", count);
                } else {
                    logger.warn("DHTService not available – cannot announce");
                }
            } catch (Exception e) {
                logger.error("Failed to announce on DHT", e);
            }
        }, 30, 10, TimeUnit.SECONDS);
    }

    public void start(int port) {
        long maxSize = 500_000_000L;

        Javalin app = Javalin.create(cfg -> {
            cfg.maxRequestSize = maxSize;
            cfg.configureServletContextHandler(sch -> sch.setMaxFormContentSize((int) maxSize));
        }).start("127.0.0.1", port);

        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "http://localhost");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });

        // Register controllers
        new SystemController().register(app);
        new LibraryController(libraryManager, projectRoot, albumArtPath, objectMapper).register(app);
        new PlaylistController(playlistManager, objectMapper).register(app);
        new DownloadController(torrentManager, torrentsPath, objectMapper, pendingDownloads, dhtKeywordManager).register(app);
        new SearchController(dhtKeywordManager).register(app);

        logger.info("Javalin started on port {}", port);

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

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "4567"));
        new BackendServer().start(port);
    }
}
