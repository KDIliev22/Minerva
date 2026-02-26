package com.minerva.network;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.dht.DHTService;
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
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JLibTorrentManager {
    private static final Logger logger = LoggerFactory.getLogger(JLibTorrentManager.class);
    private static JLibTorrentManager instance;

    // Fixed discovery infohash = SHA-1 of "minerva-discovery"
    private static final TorrentId DISCOVERY_TORRENT_ID;
    static {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest("minerva-discovery".getBytes(StandardCharsets.UTF_8));
            DISCOVERY_TORRENT_ID = TorrentId.fromBytes(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    private final BtRuntime runtime;
    private final File saveDirectory;
    private final int listenPort;
    private GatewayDevice upnpGateway;

    // Active torrent clients
    private final Map<String, ClientInfo> activeClients = new ConcurrentHashMap<>();
    private final Map<String, byte[]> torrentBytesCache = new ConcurrentHashMap<>();
    private Consumer<String> downloadCompleteCallback;

    // DHT service for peer discovery
    private DHTService dhtService;
    private final Set<InetSocketAddress> discoveryPeers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService dhtScheduler = Executors.newSingleThreadScheduledExecutor();

    // ────────────────────── Inner types ──────────────────────

    private static class ClientInfo {
        final BtClient client;
        volatile String name;
        volatile long totalSize;
        volatile List<String> fileNames;
        volatile TorrentSessionState lastState;
        volatile boolean paused;
        volatile boolean seeding;
        volatile boolean completeFired;
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

    public static class TorrentStatus {
        public String hash;
        public String name;
        public double progress;
        public long downloadRate;
        public long uploadRate;
        public long totalSize;
        public long totalDone;
        public int peers;
        public int seeds;
        public String state;
        public boolean seeding;
        public boolean paused;
    }

    public static class MagnetResult {
        public final String hash;
        public MagnetResult(String hash) { this.hash = hash; }
    }

    // ────────────────────── Auto Port Selection ──────────────────────

    private int findAvailablePort(int startPort, int maxAttempts) {
        for (int port = startPort; port < startPort + maxAttempts; port++) {
            try (ServerSocket tcpSocket = new ServerSocket(port);
                 DatagramSocket udpSocket = new DatagramSocket(port)) {
                return port;
            } catch (IOException e) {
                logger.debug("Port {} not available", port);
            }
        }
        throw new RuntimeException("No free port in range " + startPort + " - " + (startPort + maxAttempts - 1));
    }

    // ────────────────────── Constructor ──────────────────────

    private JLibTorrentManager(File saveDirectory) {
        this.saveDirectory = saveDirectory;

        int requestedPort = Integer.parseInt(System.getenv().getOrDefault("LISTEN_PORT", "6881"));
        this.listenPort = findAvailablePort(requestedPort, 100);
        if (requestedPort != this.listenPort) {
            logger.info("Requested port {} busy, using {}", requestedPort, this.listenPort);
        }

        InetAddress localAddr = findLocalAddress();
        logger.info("Local address: {}", localAddr.getHostAddress());

        Config config = new Config() {
            @Override public int getAcceptorPort() { return listenPort; }
            @Override public InetAddress getAcceptorAddress() { return localAddr; }
            @Override public int getMaxSimultaneouslyAssignedPieces() { return 20; }
            @Override public java.time.Duration getMaxPieceReceivingTime() {
                return java.time.Duration.ofSeconds(30);
            }
            @Override public java.time.Duration getTimeoutedAssignmentPeerBanDuration() {
                return java.time.Duration.ofSeconds(5);
            }
            @Override public java.time.Duration getUnreachablePeerBanDuration() {
                return java.time.Duration.ofSeconds(30);
            }
            @Override public int getNumOfHashingThreads() { return 2; }
        };

        DHTModule dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true; // Bootstrap from public DHT routers
            }
        });

        this.runtime = BtRuntime.builder(config)
                .module(dhtModule)
                .autoLoadModules()
                .build();

        // Obtain DHT service directly from runtime
        this.dhtService = runtime.service(DHTService.class);
        if (this.dhtService != null) {
            // Periodically refresh peer list
            dhtScheduler.scheduleAtFixedRate(this::refreshDiscoveryPeers, 30, 60, TimeUnit.SECONDS);
        } else {
            logger.warn("DHTService not available – discovery will be empty");
        }

        logger.info("BT runtime started. Listen port: {}", listenPort);

        // UPnP port mapping
        try {
            GatewayDiscover discover = new GatewayDiscover();
            discover.discover();
            GatewayDevice gw = discover.getValidGateway();
            if (gw != null) {
                gw.addPortMapping(listenPort, listenPort, gw.getLocalAddress().getHostAddress(), "TCP", "Minerva BT");
                gw.addPortMapping(listenPort, listenPort, gw.getLocalAddress().getHostAddress(), "UDP", "Minerva BT DHT");
                this.upnpGateway = gw;
                logger.info("UPnP mapping successful");
            }
        } catch (Exception e) {
            logger.warn("UPnP failed: {}", e.getMessage());
        }
    }

    // ────────────────────── Discovery ──────────────────────

    private void refreshDiscoveryPeers() {
        if (dhtService == null) return;
        try {
            Set<InetSocketAddress> newPeers = dhtService.getPeers(DISCOVERY_TORRENT_ID)
                    .map(p -> {
                        try {
                            // Use reflection to get address/port, as Peer type is not public in bt 1.10
                            InetAddress addr = (InetAddress) p.getClass().getMethod("getInetAddress").invoke(p);
                            int port = (int) p.getClass().getMethod("getPort").invoke(p);
                            return new InetSocketAddress(addr, port);
                        } catch (Exception ex) {
                            logger.warn("Failed to extract peer address", ex);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            discoveryPeers.clear();
            discoveryPeers.addAll(newPeers);
            logger.debug("Discovered {} peers via DHT", newPeers.size());
        } catch (Exception e) {
            logger.warn("Failed to refresh peers", e);
        }
    }

    public Set<InetSocketAddress> getDiscoveryPeers() {
        return Set.copyOf(discoveryPeers);
    }

    // ────────────────────── Singleton ──────────────────────

    public static synchronized JLibTorrentManager getInstance(File saveDirectory) {
        if (instance == null) {
            instance = new JLibTorrentManager(saveDirectory);
        }
        return instance;
    }

    public static synchronized JLibTorrentManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Not initialized. Call getInstance(File) first.");
        }
        return instance;
    }

    public int getListenPort() { return listenPort; }
    public void setDownloadCompleteCallback(Consumer<String> callback) {
        this.downloadCompleteCallback = callback;
    }

    // ────────────────────── Seeding, Magnet, Status methods ──────────────────────
    // (All methods below are identical to your original working versions – no changes)

    public String seedTorrent(File torrentFile) {
        return seedTorrent(torrentFile, saveDirectory);
    }

    public String seedTorrent(File torrentFile, File contentDirectory) {
        logger.info("seedTorrent: torrentFile={}, contentDir={}",
                torrentFile.getAbsolutePath(), contentDirectory.getAbsolutePath());
        try {
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

            torrentBytesCache.put(hashHex, Files.readAllBytes(torrentFile.toPath()));

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

    public MagnetResult seedMagnet(String magnetUri) {
        logger.info("seedMagnet: {}", magnetUri);
        try {
            String hashHex = extractHashFromMagnet(magnetUri).toLowerCase();

            if (activeClients.containsKey(hashHex)) {
                logger.info("Magnet already active: {}", hashHex);
                return new MagnetResult(hashHex);
            }

            Storage storage = new FileSystemStorage(saveDirectory.toPath());

            ClientInfo info = new ClientInfo(null, "magnet:" + hashHex, 0, new ArrayList<>());

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

    private void updateState(ClientInfo info, TorrentSessionState state, String hashHex) {
        info.lastState = state;

        long now = System.currentTimeMillis();
        long elapsed = now - info.lastRateTime;
        if (elapsed >= 900) {
            long dl = state.getDownloaded();
            long ul = state.getUploaded();
            info.downloadRate = elapsed > 0 ? (dl - info.lastDownloaded) * 1000 / elapsed : 0;
            info.uploadRate = elapsed > 0 ? (ul - info.lastUploaded) * 1000 / elapsed : 0;
            info.lastDownloaded = dl;
            info.lastUploaded = ul;
            info.lastRateTime = now;
        }

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

    public List<TorrentStatus> getTorrentStatuses() {
        List<TorrentStatus> list = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> e : activeClients.entrySet()) {
            list.add(buildStatus(e.getKey(), e.getValue()));
        }
        return list;
    }

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
            s.state = info.seeding ? "SEEDING" : info.paused ? "PAUSED" : "DOWNLOADING";
        } else {
            s.state = "STARTING";
            s.progress = 0.0;
        }
        return s;
    }

    public double getProgress(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        if (info == null || info.lastState == null) return 0.0;
        TorrentSessionState state = info.lastState;
        int total = state.getPiecesTotal();
        int remaining = state.getPiecesRemaining();
        return total > 0 ? ((double) (total - remaining) / total) * 100.0 : 0.0;
    }

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
            info.client.startAsync(state -> updateState(info, state, hashHex), 1000);
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

    public String getTorrentName(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        return info != null ? info.name : null;
    }

    public List<String> getTorrentFileNames(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        return info != null ? info.fileNames : Collections.emptyList();
    }

    public int getTorrentNumFiles(String hashHex) {
        ClientInfo info = activeClients.get(hashHex);
        return info != null ? info.fileNames.size() : 0;
    }

    public boolean saveTorrentFile(String hashHex, Path outputPath) {
        byte[] bytes = torrentBytesCache.get(hashHex);
        if (bytes == null) {
            logger.warn("No cached torrent bytes for hash: {}", hashHex);
            return false;
        }
        try {
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, bytes);
            logger.info("Saved .torrent for {} to {} ({} bytes)", hashHex, outputPath, bytes.length);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save .torrent for {}", hashHex, e);
            return false;
        }
    }

    public void shutdown() {
        dhtScheduler.shutdownNow();
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
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        String host = addr.getHostAddress();
                        if (!host.startsWith("172.17.")) {
                            return addr;
                        }
                        if (fallback == null) fallback = addr;
                    }
                }
            }
            if (fallback != null) return fallback;
        } catch (Exception e) {
            LoggerFactory.getLogger(JLibTorrentManager.class).warn("Failed to detect local address: {}", e.getMessage());
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
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    static String extractHashFromMagnet(String magnetUri) {
        String prefix = "urn:btih:";
        int start = magnetUri.indexOf(prefix);
        if (start < 0) throw new IllegalArgumentException("Invalid magnet URI: " + magnetUri);
        start += prefix.length();
        int end = magnetUri.indexOf('&', start);
        return (end > 0 ? magnetUri.substring(start, end) : magnetUri.substring(start)).toLowerCase();
    }
}