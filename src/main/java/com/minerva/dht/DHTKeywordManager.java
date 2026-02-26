package com.minerva.dht;

import com.minerva.network.JLibTorrentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

public class DHTKeywordManager {
    private static final Logger logger = LoggerFactory.getLogger(DHTKeywordManager.class);
    private final int localSearchPort;
    private final JLibTorrentManager torrentManager;
    // Maps torrentHash -> set of "host:listenPort" endpoints that have the torrent
    private final Map<String, Set<String>> torrentPeerEndpoints = new ConcurrentHashMap<>();
    // Set of known Minerva peers (for gossip)
    private final Set<InetSocketAddress> discoveryPeers = ConcurrentHashMap.newKeySet();

    public DHTKeywordManager(int searchPort, JLibTorrentManager torrentManager) {
        this.localSearchPort = searchPort;
        this.torrentManager = torrentManager;
        logger.info("DHTKeywordManager initialized, search port {}", localSearchPort);
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

        Set<InetSocketAddress> peers = torrentManager.getDiscoveryPeers();  // from DHT
        if (peers.isEmpty()) {
            logger.debug("No discovery peers known yet");
            return Collections.emptyList();
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(4, peers.size()));
        List<Future<List<KeywordSearchClient.SearchResult>>> futures = new ArrayList<>();

        for (InetSocketAddress addr : peers) {
            final String peerHost = addr.getAddress().getHostAddress();
            futures.add(executor.submit(() -> {
                logger.debug("Querying discovered peer {}:{} for keyword '{}'",
                        peerHost, localSearchPort, minervaKeyword);
                List<KeywordSearchClient.SearchResult> results =
                        KeywordSearchClient.queryPeer(peerHost, localSearchPort, minervaKeyword);
                // Process each result: add new peers and cache torrent endpoints
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
                                    discoveryPeers.add(new InetSocketAddress(host, port));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
                return results;
            }));
        }

        // Collect results with timeout
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
                minervaKeyword, allResults.size(), peers.size());
        return allResults;
    }

    public Set<String> getPeersForTorrent(String torrentHash) {
        return torrentPeerEndpoints.getOrDefault(torrentHash, Collections.emptySet());
    }

    // New method to expose discovery peers (for gossip)
    public Set<InetSocketAddress> getDiscoveryPeers() {
        return Collections.unmodifiableSet(discoveryPeers);
    }

    // Method to add a discovered peer (called from search)
    public void addDiscoveryPeer(InetSocketAddress addr) {
        discoveryPeers.add(addr);
    }
}