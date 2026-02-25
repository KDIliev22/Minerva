package com.minerva.dht;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages keyword-based peer discovery for the Minerva P2P search protocol.
 *
 * After the migration from jlibtorrent to bt (pure Java), standalone DHT
 * announce / get_peers operations are no longer used.  Peer discovery for
 * keyword searches now relies exclusively on direct TCP queries to known
 * Minerva peers (parsed from DHT_BOOTSTRAP_NODES).
 *
 * bt's built-in DHT still runs and handles automatic peer discovery for
 * active torrents — this class only deals with the Minerva keyword overlay.
 */
public class DHTKeywordManager {
    private static final Logger logger = LoggerFactory.getLogger(DHTKeywordManager.class);
    private final int localSearchPort;
    private final List<PeerAddress> knownPeers;
    // Maps torrentHash -> set of "host:listenPort" endpoints that have the torrent
    private final Map<String, Set<String>> torrentPeerEndpoints = new ConcurrentHashMap<>();

    private static class PeerAddress {
        final String host;
        final int port;
        PeerAddress(String host, int port) { this.host = host; this.port = port; }
        @Override public String toString() { return host + ":" + port; }
    }

    public DHTKeywordManager(int searchPort) {
        this.localSearchPort = searchPort;
        // Parse known Minerva peers from DHT_BOOTSTRAP_NODES (host:port pairs)
        String bootstrapNodes = System.getenv().getOrDefault("DHT_BOOTSTRAP_NODES", "");
        this.knownPeers = new ArrayList<>();
        for (String node : bootstrapNodes.split(",")) {
            node = node.trim();
            if (!node.isEmpty() && node.contains(":")) {
                String host = node.substring(0, node.lastIndexOf(':'));
                String portStr = node.substring(node.lastIndexOf(':') + 1);
                try {
                    int port = Integer.parseInt(portStr);
                    // Skip self (localhost pointing to our own search port)
                    if (isLocalHost(host) && port == searchPort) {
                        logger.info("Skipping self in known peers: {}", node);
                        continue;
                    }
                    knownPeers.add(new PeerAddress(host, port));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid port in bootstrap node: {}", node);
                }
            }
        }
        logger.info("Known Minerva peers: {}", knownPeers);
    }

    private boolean isLocalHost(String host) {
        return "127.0.0.1".equals(host) || "localhost".equals(host) || "0.0.0.0".equals(host);
    }

    /**
     * Convert a keyword to a SHA-1 hex string (used for keyword-to-infohash mapping).
     */
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

    /**
     * Announce a keyword to the network.
     * Currently a no-op — bt's built-in DHT handles torrent-level announce
     * automatically.  Standalone keyword announce requires a custom DHT
     * overlay which is not yet implemented for the bt migration.
     */
    public void announceKeyword(String keyword) {
        String minervaKeyword = keyword.toLowerCase().endsWith(".minerva")
                ? keyword : keyword + ".minerva";
        logger.debug("announceKeyword called for '{}' (no-op in bt migration)", minervaKeyword);
    }

    /**
     * Search for peers that have the given keyword.
     * Queries known Minerva peers directly via TCP.
     */
    public List<KeywordSearchClient.SearchResult> searchKeyword(String keyword) {
        String minervaKeyword = keyword.toLowerCase().endsWith(".minerva")
                ? keyword.toLowerCase() : keyword.toLowerCase() + ".minerva";

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(4, knownPeers.size()));
        List<Future<List<KeywordSearchClient.SearchResult>>> futures = new ArrayList<>();

        // Query known Minerva peers directly using THEIR search port
        for (PeerAddress peer : knownPeers) {
            final String peerHost = peer.host;
            futures.add(executor.submit(() -> {
                logger.debug("Querying known Minerva peer {} for keyword '{}'",
                        peer, minervaKeyword);
                List<KeywordSearchClient.SearchResult> results =
                        KeywordSearchClient.queryPeer(peerHost, peer.port, minervaKeyword);
                // Tag results with peer address and cache listen port mapping
                for (KeywordSearchClient.SearchResult r : results) {
                    r.peerHost = peerHost;
                    if (r.torrentHash != null && r.listenPort != null) {
                        torrentPeerEndpoints
                                .computeIfAbsent(r.torrentHash,
                                        k -> ConcurrentHashMap.newKeySet())
                                .add(peerHost + ":" + r.listenPort);
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
        logger.info("Keyword '{}' search returned {} unique results",
                minervaKeyword, allResults.size());
        return allResults;
    }

    /**
     * Returns the set of "host:listenPort" endpoints known to have the
     * given torrent.  Populated from search results that included
     * listenPort in the response.
     */
    public Set<String> getPeersForTorrent(String torrentHash) {
        return torrentPeerEndpoints.getOrDefault(torrentHash, Collections.emptySet());
    }
}
