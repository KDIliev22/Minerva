package com.minerva.network;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.Config;
import bt.torrent.TorrentSessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Torrent manager backed by the pure-Java bt library (atomashpolskiy/bt).
 * Replaces the old jlibtorrent-based manager to eliminate SIGSEGV crashes
 * that were caused by concurrent SWIG/JNI native calls.
 *
 * Class name kept as JLibTorrentManager to minimize changes across callers.
 */
public class JLibTorrentManager {
    private static final Logger logger = LoggerFactory.getLogger(JLibTorrentManager.class);
    private static JLibTorrentManager instance;

    private final BtRuntime runtime;
    private final File saveDirectory;
    private final int listenPort;
    private GatewayDevice upnpGateway; // non-null if UPnP mapping succeeded

    // Active torrent clients keyed by hex info-hash
    private final Map<String, ClientInfo> activeClients = new ConcurrentHashMap<>();
    // Raw .torrent bytes for saving later (populated when seeding from file)
    private final Map<String, byte[]> torrentBytesCache = new ConcurrentHashMap<>();
    // Callback fired when a downloading torrent completes
    private Consumer<String> downloadCompleteCallback;

    // ────────────────────── Inner types ──────────────────────

    /** Per-torrent bookkeeping. */
    private static class ClientInfo {
        final BtClient client;
        volatile String name;
        volatile long totalSize;
        volatile List<String> fileNames;  // relative paths inside the torrent
        volatile TorrentSessionState lastState;
        volatile boolean paused;
        volatile boolean seeding;
        volatile boolean completeFired;
        // For rate calculation
        volatile long lastDownloaded;
        volatile long lastUploaded;
        volatile long lastRateTime;
        volatile long downloadRate;
        volatile long uploadRate;

        ClientInfo(BtClient client, String name, long totalSize, List<String> fileNames) {
            this.client = client;
            this.name = name;
            this.totalSize = totalSize;
            this.fileNames = fileNames != null ? fileNames : new ArrayList<>();
            this.lastRateTime = System.currentTimeMillis();
        }
    }

    /** Lightweight status DTO – replaces the old TorrentHandle + TorrentStatus. */
    public static class TorrentStatus {
        public String hash;
        public String name;
        public double progress;   // 0.0 – 1.0
        public long downloadRate; // bytes/s
        public long uploadRate;   // bytes/s
        public long totalSize;
        public long totalDone;
        public int peers;
        public int seeds;
        public String state;      // STARTING, DOWNLOADING, SEEDING, PAUSED
        public boolean seeding;
        public boolean paused;
    }

    /** Result returned by {@link #seedMagnet(String)}. */
    public static class MagnetResult {
        public final String hash;
        public MagnetResult(String hash) { this.hash = hash; }
    }

    // ────────────────────── Construction ──────────────────────

    public int getListenPort() { return listenPort; }

    private JLibTorrentManager(File saveDirectory) {
        this.saveDirectory = saveDirectory;

        int searchPort = Integer.parseInt(
                System.getenv().getOrDefault("SEARCH_PORT", "4568"));
        this.listenPort = Integer.parseInt(
                System.getenv().getOrDefault("LISTEN_PORT", "6881"));

        if (this.listenPort == searchPort) {
            logger.warn("LISTEN_PORT equals SEARCH_PORT ({}). "
                    + "Set LISTEN_PORT to a separate value for proper peer connections.", searchPort);
        }

        InetAddress localAddr = findLocalAddress();
        logger.info("Detected local address for BT: {}", localAddr.getHostAddress());

        Config config = new Config() {
            @Override public int getAcceptorPort() { return listenPort; }
            @Override public InetAddress getAcceptorAddress() { return localAddr; }

            // ── Tuned defaults (bt's out-of-box values are too conservative) ──
            // Default 3 → 20: request more pieces concurrently for better throughput
            @Override public int getMaxSimultaneouslyAssignedPieces() { return 20; }
            // Default 5s → 30s: avoid falsely timing out pieces during normal transfers
            @Override public java.time.Duration getMaxPieceReceivingTime() {
                return java.time.Duration.ofSeconds(30);
            }
            // Default 1 min → 5s: don't ban the only seeder for a full minute on timeout
            @Override public java.time.Duration getTimeoutedAssignmentPeerBanDuration() {
                return java.time.Duration.ofSeconds(5);
            }
            // Default 30 min → 30s: unreachable peers get a second chance sooner
            @Override public java.time.Duration getUnreachablePeerBanDuration() {
                return java.time.Duration.ofSeconds(30);
            }
            // Default 1 → 2: parallel piece hash verification
            @Override public int getNumOfHashingThreads() { return 2; }
        };

        DHTModule dhtModule = new DHTModule(new DHTConfig() {
            @Override public boolean shouldUseRouterBootstrap() { return true; }
        });

        this.runtime = BtRuntime.builder(config)
                .module(dhtModule)
                .autoLoadModules()
                .build();

        logger.info("BT runtime started (pure-Java). Listen port: {}", listenPort);

        // ── UPnP port mapping via weupnp ──
        try {
            GatewayDiscover discover = new GatewayDiscover();
            discover.discover();
            GatewayDevice gw = discover.getValidGateway();
            if (gw != null) {
                String gwLocalAddr = gw.getLocalAddress().getHostAddress();
                boolean tcp = gw.addPortMapping(listenPort, listenPort, gwLocalAddr, "TCP", "Minerva BT");
                boolean udp = gw.addPortMapping(listenPort, listenPort, gwLocalAddr, "UDP", "Minerva BT DHT");
                if (tcp || udp) {
                    this.upnpGateway = gw;
                    logger.info("UPnP port {} mapped via {} (TCP={}, UDP={})",
                            listenPort, gw.getFriendlyName(), tcp, udp);
                } else {
                    logger.warn("UPnP gateway found ({}) but port mapping failed", gw.getFriendlyName());
                }
            } else {
                logger.info("No UPnP gateway found – port {} not mapped (manual forwarding may be needed)", listenPort);
            }
        } catch (Exception e) {
            logger.warn("UPnP discovery failed: {}", e.getMessage());
        }
    }

    public static synchronized JLibTorrentManager getInstance(File saveDirectory) {
        if (instance == null) {
            instance = new JLibTorrentManager(saveDirectory);
        }
        return instance;
    }

    public static synchronized JLibTorrentManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "JLibTorrentManager not initialized. Call getInstance(File) first.");
        }
        return instance;
    }

    public void setDownloadCompleteCallback(Consumer<String> callback) {
        this.downloadCompleteCallback = callback;
    }

    // ────────────────────── Seeding ──────────────────────

    /** Seed a torrent file using the default downloads directory. */
    public String seedTorrent(File torrentFile) {
        return seedTorrent(torrentFile, saveDirectory);
    }

    /** Seed a torrent file using a specific content directory. */
    public String seedTorrent(File torrentFile, File contentDirectory) {
        logger.info("seedTorrent: torrentFile={}, contentDir={}",
                torrentFile.getAbsolutePath(), contentDirectory.getAbsolutePath());
        try {
            // ── Parse metadata via ttorrent (pure Java, no SWIG) ──
            com.turn.ttorrent.common.Torrent ttTorrent =
                    com.turn.ttorrent.common.Torrent.load(torrentFile);
            String hashHex = ttTorrent.getHexInfoHash().toLowerCase();
            String name = ttTorrent.getName();
            long totalSize = ttTorrent.getSize();
            List<String> fileNames = new ArrayList<>(ttTorrent.getFilenames());

            if (activeClients.containsKey(hashHex)) {
                logger.info("Torrent already active: {} ({})", name, hashHex);
                return hashHex;
            }

            // Cache raw bytes so we can save the .torrent later
            torrentBytesCache.put(hashHex, Files.readAllBytes(torrentFile.toPath()));

            // ── Create bt client and start seeding ──
            Storage storage = new FileSystemStorage(contentDirectory.toPath());
            BtClient client = Bt.client(runtime)
                    .storage(storage)
                    .torrent(torrentFile.toURI().toURL())
                    .build();

            ClientInfo info = new ClientInfo(client, name, totalSize, fileNames);
            activeClients.put(hashHex, info);

            client.startAsync(state -> updateState(info, state, hashHex), 1000);

            logger.info("Seeding: {} (hash: {}) from {}", name, hashHex, contentDirectory);
            return hashHex;

        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Failed to seed torrent: {}", torrentFile, e);
            throw new RuntimeException("Failed to seed torrent", e);
        }
    }

    // ────────────────────── Magnet Downloads ──────────────────────

    /** Start downloading a magnet link. Returns immediately; progress is async. */
    public MagnetResult seedMagnet(String magnetUri) {
        logger.info("seedMagnet: {}", magnetUri);
        try {
            String hashHex = extractHashFromMagnet(magnetUri).toLowerCase();

            if (activeClients.containsKey(hashHex)) {
                logger.info("Magnet already active: {}", hashHex);
                return new MagnetResult(hashHex);
            }

            Storage storage = new FileSystemStorage(saveDirectory.toPath());

            // Create the client; afterTorrentFetched lets us grab metadata
            // once the info-dict exchange completes.
            ClientInfo info = new ClientInfo(
                    null, "magnet:" + hashHex, 0, new ArrayList<>());

            BtClient client = Bt.client(runtime)
                    .storage(storage)
                    .magnet(magnetUri)
                    .afterTorrentFetched(torrent -> {
                        info.name = torrent.getName();
                        info.totalSize = torrent.getSize();
                        List<String> fNames = new ArrayList<>();
                        for (TorrentFile tf : torrent.getFiles()) {
                            fNames.add(String.join("/", tf.getPathElements()));
                        }
                        info.fileNames = fNames;
                        logger.info("Magnet metadata resolved: {} ({} files, {} bytes)",
                                info.name, fNames.size(), info.totalSize);
                    })
                    .build();

            // Patch the client reference into info (can't pass it to constructor
            // because the builder emits it after afterTorrentFetched is registered)
            ClientInfo realInfo = new ClientInfo(client, info.name, info.totalSize, info.fileNames);
            activeClients.put(hashHex, realInfo);

            client.startAsync(state -> updateState(realInfo, state, hashHex), 1000);

            logger.info("Started magnet download: {}", hashHex);
            return new MagnetResult(hashHex);

        } catch (Exception e) {
            logger.error("Failed to start magnet download: {}", magnetUri, e);
            throw new RuntimeException("Failed to start magnet download", e);
        }
    }

    // ────────────────────── State / Progress ──────────────────────

    private void updateState(ClientInfo info, TorrentSessionState state, String hashHex) {
        info.lastState = state;

        // ── Rate calculation (every ~1 s) ──
        long now = System.currentTimeMillis();
        long elapsed = now - info.lastRateTime;
        if (elapsed >= 900) {
            long dl = state.getDownloaded();
            long ul = state.getUploaded();
            info.downloadRate = elapsed > 0
                    ? (dl - info.lastDownloaded) * 1000 / elapsed : 0;
            info.uploadRate = elapsed > 0
                    ? (ul - info.lastUploaded) * 1000 / elapsed : 0;
            info.lastDownloaded = dl;
            info.lastUploaded = ul;
            info.lastRateTime = now;
        }

        // ── Completion detection ──
        if (state.getPiecesRemaining() == 0 && state.getPiecesTotal() > 0) {
            if (!info.seeding) {
                info.seeding = true;
                logger.info("Torrent {} is now seeding", hashHex);
            }
            if (!info.completeFired && downloadCompleteCallback != null) {
                info.completeFired = true;
                logger.info("Download complete, firing callback: {}", hashHex);
                new Thread(() -> downloadCompleteCallback.accept(hashHex),
                        "DownloadComplete-" + hashHex.substring(0, Math.min(8, hashHex.length())))
                        .start();
            }
        }
    }

    /** Status of all active torrents. */
    public List<TorrentStatus> getTorrentStatuses() {
        List<TorrentStatus> list = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> e : activeClients.entrySet()) {
            list.add(buildStatus(e.getKey(), e.getValue()));
        }
        return list;
    }

    /** Status of one torrent (or null). */
    public TorrentStatus getTorrentStatus(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        return info != null ? buildStatus(hashHex, info) : null;
    }

    private TorrentStatus buildStatus(String hashHex, ClientInfo info) {
        TorrentStatus s = new TorrentStatus();
        s.hash = hashHex;
        s.name = info.name;
        s.paused = info.paused;
        s.seeding = info.seeding;
        s.downloadRate = info.downloadRate;
        s.uploadRate = info.uploadRate;
        s.totalSize = info.totalSize;

        TorrentSessionState state = info.lastState;
        if (state != null) {
            int total = state.getPiecesTotal();
            int complete = total - state.getPiecesRemaining();
            s.progress = total > 0 ? (double) complete / total : 0.0;
            s.totalDone = (long) (s.progress * info.totalSize);
            s.peers = state.getConnectedPeers().size();
            s.seeds = 0;
            s.state = info.seeding ? "SEEDING"
                    : info.paused ? "PAUSED" : "DOWNLOADING";
        } else {
            s.state = "STARTING";
            s.progress = 0.0;
        }
        return s;
    }

    /** Progress as a percentage (0–100). */
    public double getProgress(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        if (info == null || info.lastState == null) return 0.0;
        TorrentSessionState state = info.lastState;
        int total = state.getPiecesTotal();
        int remaining = state.getPiecesRemaining();
        return total > 0 ? ((double) (total - remaining) / total) * 100.0 : 0.0;
    }

    // ────────────────────── Pause / Resume / Remove ──────────────────────

    public void pauseTorrent(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        if (info != null && !info.paused) {
            info.client.stop();
            info.paused = true;
            logger.info("Paused torrent: {}", hashHex);
        }
    }

    public void resumeTorrent(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        if (info != null && info.paused) {
            info.client.startAsync(
                    state -> updateState(info, state, hashHex), 1000);
            info.paused = false;
            logger.info("Resumed torrent: {}", hashHex);
        }
    }

    public void removeTorrent(String hashHex, boolean deleteFiles) {
        ClientInfo info = activeClients.remove(hashHex);
        torrentBytesCache.remove(hashHex);
        if (info != null) {
            try {
                if (info.client.isStarted()) {
                    info.client.stop();
                }
            } catch (Exception e) {
                logger.warn("Error stopping client for {}: {}", hashHex, e.getMessage());
            }
            logger.info("Removed torrent: {}", hashHex);
        }
    }

    // ────────────────────── Metadata accessors ──────────────────────

    /** Torrent display name (may be "magnet:hash" while metadata is still being fetched). */
    public String getTorrentName(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        return info != null ? info.name : null;
    }

    /** Relative file paths inside the torrent. Empty list if metadata unknown. */
    public List<String> getTorrentFileNames(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        return info != null ? info.fileNames : Collections.emptyList();
    }

    /** Number of files in the torrent. */
    public int getTorrentNumFiles(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        return info != null ? info.fileNames.size() : 0;
    }

    // ────────────────────── .torrent file persistence ──────────────────────

    /**
     * Save cached .torrent bytes to disk. Works for torrents that were seeded
     * from a file; returns false for magnet downloads (bytes not available).
     */
    public boolean saveTorrentFile(String hashHex, Path outputPath) {
        byte[] bytes = torrentBytesCache.get(hashHex);
        if (bytes == null) {
            logger.warn("No cached torrent bytes for hash: {}", hashHex);
            return false;
        }
        try {
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, bytes);
            logger.info("Saved .torrent for {} to {} ({} bytes)",
                    hashHex, outputPath, bytes.length);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save .torrent for {}", hashHex, e);
            return false;
        }
    }

    // ────────────────────── Lifecycle ──────────────────────

    public void shutdown() {
        logger.info("Shutting down BT runtime...");
        for (Map.Entry<String, ClientInfo> e : activeClients.entrySet()) {
            try {
                if (e.getValue().client.isStarted()) {
                    e.getValue().client.stop();
                }
            } catch (Exception ex) {
                logger.warn("Error stopping client {}: {}", e.getKey(), ex.getMessage());
            }
        }
        activeClients.clear();
        torrentBytesCache.clear();
        try {
            runtime.shutdown();
        } catch (Exception e) {
            logger.warn("Error shutting down BT runtime: {}", e.getMessage());
        }
        // Remove UPnP port mapping
        if (upnpGateway != null) {
            try {
                upnpGateway.deletePortMapping(listenPort, "TCP");
                upnpGateway.deletePortMapping(listenPort, "UDP");
                logger.info("UPnP port {} unmapped", listenPort);
            } catch (Exception e) {
                logger.warn("Failed to remove UPnP mapping: {}", e.getMessage());
            }
        }
        logger.info("BT runtime shut down.");
    }

    // ────────────────────── Utilities ──────────────────────

    /**
     * Auto-detect the best local IPv4 address for bt to bind to.
     * Prefers a real LAN/WiFi interface over Docker bridges.
     * Falls back to 0.0.0.0 if nothing suitable is found.
     */
    private static InetAddress findLocalAddress() {
        try {
            InetAddress fallback = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        String host = addr.getHostAddress();
                        // Prefer real LAN/WiFi (192.168.x.x, 10.x.x.x) over Docker bridge (172.17.x.x)
                        if (!host.startsWith("172.17.")) {
                            return addr;
                        }
                        if (fallback == null) fallback = addr;
                    }
                }
            }
            if (fallback != null) return fallback;
        } catch (Exception e) {
            LoggerFactory.getLogger(JLibTorrentManager.class)
                    .warn("Failed to detect local address: {}", e.getMessage());
        }
        try {
            return InetAddress.getByName("0.0.0.0");
        } catch (Exception e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    static String extractHashFromMagnet(String magnetUri) {
        String prefix = "urn:btih:";
        int start = magnetUri.indexOf(prefix);
        if (start < 0) throw new IllegalArgumentException("Invalid magnet URI: " + magnetUri);
        start += prefix.length();
        int end = magnetUri.indexOf('&', start);
        return (end > 0 ? magnetUri.substring(start, end)
                        : magnetUri.substring(start)).toLowerCase();
    }
}
