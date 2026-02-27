package com.minerva.dht;

import com.minerva.network.JLibTorrentManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class DHTKeywordManager {
    private static final Logger logger = LoggerFactory.getLogger(DHTKeywordManager.class);
    private final int localSearchPort;
    private final JLibTorrentManager torrentManager;
    private final String crawlerUrl;

    private final Map<String, Set<String>> torrentPeerEndpoints = new ConcurrentHashMap<>();
    private final Set<InetSocketAddress> discoveryPeers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService crawlerPoller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Hardcoded bootstrap Minerva nodes (you can add your own)
    private static final List<InetSocketAddress> BOOTSTRAP_NODES = Arrays.asList(
                    new InetSocketAddress("87.120.14.80", 4568)
    );

    public DHTKeywordManager(int searchPort, JLibTorrentManager torrentManager) {
        this(searchPort, torrentManager, null);
    }

    public DHTKeywordManager(int searchPort, JLibTorrentManager torrentManager, String crawlerUrl) {
        this.localSearchPort = searchPort;
        this.torrentManager = torrentManager;
        this.crawlerUrl = crawlerUrl;

        // Load cached peers
        Set<InetSocketAddress> cached = PeerCache.load();
        discoveryPeers.addAll(cached);
        logger.info("Loaded {} cached Minerva peers", cached.size());

        // Add hardcoded bootstrap nodes
        discoveryPeers.addAll(BOOTSTRAP_NODES);
        if (!BOOTSTRAP_NODES.isEmpty()) {
            logger.info("Added {} hardcoded bootstrap nodes", BOOTSTRAP_NODES.size());
        }

        // Start polling the crawler if URL is provided
        if (crawlerUrl != null && !crawlerUrl.isEmpty()) {
            this.crawlerPoller = Executors.newSingleThreadScheduledExecutor();
            this.crawlerPoller.scheduleAtFixedRate(this::pollCrawler, 0, 30, TimeUnit.SECONDS);
        } else {
            this.crawlerPoller = null;
        }

        // Register shutdown hook to save peers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Saving {} discovery peers to cache", discoveryPeers.size());
            PeerCache.save(discoveryPeers);
            if (crawlerPoller != null) {
                crawlerPoller.shutdownNow();
            }
        }));

        logger.info("DHTKeywordManager initialized, search port {}", localSearchPort);
    }

    private void pollCrawler() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(crawlerUrl))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                List<String> peerStrings = objectMapper.readValue(response.body(), new TypeReference<List<String>>() {});
                for (String peerStr : peerStrings) {
                    String[] parts = peerStr.split(":");
                    if (parts.length == 2) {
                        try {
                            String host = parts[0];
                            int port = Integer.parseInt(parts[1]);
                            InetSocketAddress addr = new InetSocketAddress(host, port);
                            if (!addr.isUnresolved()) {
                                discoveryPeers.add(addr);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                logger.debug("Fetched {} peers from crawler", peerStrings.size());
            }
        } catch (Exception e) {
            logger.warn("Failed to poll crawler", e);
        }
    }

    public String keywordToSha1Hex(String keyword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(keyword.toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void announceKeyword(String keyword) {
        String minervaKeyword = keyword.toLowerCase().endsWith(".minerva")
                ? keyword : keyword + ".minerva";
        logger.debug("announceKeyword called for '{}' (no-op)", minervaKeyword);
    }

    public List<KeywordSearchClient.SearchResult> searchKeyword(String keyword) {
        String minervaKeyword = keyword.toLowerCase().endsWith(".minerva")
                ? keyword.toLowerCase() : keyword.toLowerCase() + ".minerva";

        Set<InetSocketAddress> dhtPeers = torrentManager.getDiscoveryPeers();
        Set<InetSocketAddress> allPeers = new HashSet<>(discoveryPeers);
        allPeers.addAll(dhtPeers);

        if (allPeers.isEmpty()) {
            logger.debug("No discovery peers known yet");
            return Collections.emptyList();
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, allPeers.size()));
        List<Future<List<KeywordSearchClient.SearchResult>>> futures = new ArrayList<>();

        for (InetSocketAddress addr : allPeers) {
            final String peerHost = addr.getAddress().getHostAddress();
            futures.add(executor.submit(() -> {
                logger.debug("Querying discovered peer {}:{} for keyword '{}'",
                        peerHost, localSearchPort, minervaKeyword);
                List<KeywordSearchClient.SearchResult> results =
                        KeywordSearchClient.queryPeer(peerHost, localSearchPort, minervaKeyword);
                for (KeywordSearchClient.SearchResult r : results) {
                    r.peerHost = peerHost;
                    if (r.torrentHash != null && r.listenPort != null) {
                        torrentPeerEndpoints
                                .computeIfAbsent(r.torrentHash,
                                        k -> ConcurrentHashMap.newKeySet())
                                .add(peerHost + ":" + r.listenPort);
                    }
                    // Add any new peers from the result's peer list
                    if (r.peers != null) {
                        for (String peerStr : r.peers) {
                            String[] parts = peerStr.split(":");
                            if (parts.length == 2) {
                                try {
                                    String host = parts[0];
                                    int port = Integer.parseInt(parts[1]);
                                    InetSocketAddress newPeer = new InetSocketAddress(host, port);
                                    if (!newPeer.isUnresolved()) {
                                        discoveryPeers.add(newPeer);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
                return results;
            }));
        }

        Set<String> seenKeys = new HashSet<>();
        List<KeywordSearchClient.SearchResult> allResults = new ArrayList<>();
        for (Future<List<KeywordSearchClient.SearchResult>> future : futures) {
            try {
                List<KeywordSearchClient.SearchResult> results =
                        future.get(4, TimeUnit.SECONDS);
                for (KeywordSearchClient.SearchResult r : results) {
                    String key = (r.torrentHash != null ? r.torrentHash : "")
                            + "|" + (r.title != null ? r.title : "");
                    if (seenKeys.add(key)) {
                        allResults.add(r);
                    }
                }
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (Exception e) {
                // ignore failed peers
            }
        }
        executor.shutdownNow();
        logger.info("Keyword '{}' search returned {} unique results from {} peers",
                minervaKeyword, allResults.size(), allPeers.size());
        return allResults;
    }

    public Set<String> getPeersForTorrent(String torrentHash) {
        return torrentPeerEndpoints.getOrDefault(torrentHash, Collections.emptySet());
    }

    public Set<InetSocketAddress> getDiscoveryPeers() {
        return Collections.unmodifiableSet(discoveryPeers);
    }

    public void addDiscoveryPeer(InetSocketAddress addr) {
        discoveryPeers.add(addr);
    }
}