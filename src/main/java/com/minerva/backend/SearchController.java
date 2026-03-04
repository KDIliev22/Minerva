package com.minerva.backend;

import com.minerva.dht.DHTKeywordManager;
import com.minerva.dht.KeywordSearchClient;
import com.minerva.model.MusicFile;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Handles the DHT keyword search endpoint for discovering tracks across peers.
 */
public class SearchController {
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final DHTKeywordManager dhtKeywordManager;

    public SearchController(DHTKeywordManager dhtKeywordManager) {
        this.dhtKeywordManager = dhtKeywordManager;
    }

    public void register(Javalin app) {
        app.get("/api/dht-search", ctx -> {
            String query = ctx.queryParam("q");
            if (query == null) {
                ctx.status(400).result("Missing query");
                return;
            }
            String[] keywords = query.toLowerCase().split("\\s+");
            Map<String, MusicFile> trackByKey = new ConcurrentHashMap<>();
            Map<String, AtomicInteger> matchCounts = new ConcurrentHashMap<>();
            ExecutorService searchExecutor = Executors.newFixedThreadPool(keywords.length);
            List<Future<?>> searchFutures = new ArrayList<>();

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
                            matchCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
                        }
                    }
                }));
            }

            for (Future<?> f : searchFutures) {
                try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
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
    }
}
