package com.minerva.backend;

import com.minerva.dht.DHTKeywordManager;
import com.minerva.network.JLibTorrentManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles torrent download endpoints: fetching magnets, listing active downloads,
 * pausing, resuming and removing torrents.
 */
public class DownloadController {
    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);
    private final JLibTorrentManager torrentManager;
    private final Path torrentsPath;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Object>> pendingDownloads;
    private final DHTKeywordManager dhtKeywordManager;

    public DownloadController(JLibTorrentManager torrentManager, Path torrentsPath,
                              ObjectMapper objectMapper,
                              Map<String, Map<String, Object>> pendingDownloads,
                              DHTKeywordManager dhtKeywordManager) {
        this.torrentManager = torrentManager;
        this.torrentsPath = torrentsPath;
        this.objectMapper = objectMapper;
        this.pendingDownloads = pendingDownloads;
        this.dhtKeywordManager = dhtKeywordManager;
    }

    @SuppressWarnings("unchecked")
    public void register(Javalin app) {
        app.post("/api/fetch-torrent/{hash}", ctx -> {
            String hash = ctx.pathParam("hash");
            StringBuilder magnetBuilder = new StringBuilder("magnet:?xt=urn:btih:").append(hash);

            // Append known peers as x.pe hints for immediate connection
            Set<String> knownPeers = dhtKeywordManager.getPeersForTorrent(hash);
            if (!knownPeers.isEmpty()) {
                for (String peerEndpoint : knownPeers) {
                    magnetBuilder.append("&x.pe=").append(peerEndpoint);
                }
                logger.info("Added {} known peer(s) to magnet for {}: {}",
                        knownPeers.size(), hash, knownPeers);
            }

            String magnet = magnetBuilder.toString();
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

                torrentManager.seedMagnet(magnet);
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
    }
}
