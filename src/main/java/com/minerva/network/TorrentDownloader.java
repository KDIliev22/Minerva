package com.minerva.network;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.TorrentSessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class TorrentDownloader {
    private static final Logger logger = LoggerFactory.getLogger(TorrentDownloader.class);
    
    private final Map<String, BtClient> activeDownloads;
    private final Map<String, Double> downloadProgress;
    private final Map<String, TorrentSessionState> torrentStates;
    private BtRuntime btRuntime;
    private final Path downloadsDir;
    
    public TorrentDownloader(BtRuntime runtime, Path downlaodPath) {
        this.btRuntime = runtime;
        this.activeDownloads = new ConcurrentHashMap<>();
        this.downloadProgress = new ConcurrentHashMap<>();
        this.torrentStates = new ConcurrentHashMap<>();
        this.downloadsDir = Paths.get("downloads");
    }
    
    public Path download(TorrentMetadata metadata, String magnetUri) throws IOException {
        String torrentHash = metadata.getTorrentHash();
        
        Storage storage = new FileSystemStorage(downloadsDir);
        BtClient client = Bt.client(btRuntime)
            .storage(storage)
            .magnet(magnetUri)
            .build();
        
        AtomicReference<Path> downloadedContent = new AtomicReference<>();
        AtomicReference<Exception> downloadError = new AtomicReference<>();
        
        activeDownloads.put(torrentHash, client);
        downloadProgress.put(torrentHash, 0.0);
        
        Thread downloadThread = new Thread(() -> {
            try {
                client.startAsync(state -> {
                    torrentStates.put(torrentHash, state);
                    
                    long downloaded = state.getDownloaded();
                    long total = state.getPiecesTotal() * 16384L;
                    double progress = total > 0 ? (double) downloaded / total : 0.0;
                    downloadProgress.put(torrentHash, progress);
                    
                    if (state.getPiecesComplete() == state.getPiecesTotal()) {
                        try {
                            downloadedContent.set(findDownloadedContent());
                        } catch (IOException e) {
                            downloadError.set(e);
                        }
                    }
                }, 1000).join();
            } catch (Exception e) {
                downloadError.set(e);
            } finally {
                activeDownloads.remove(torrentHash);
                downloadProgress.remove(torrentHash);
                torrentStates.remove(torrentHash);
            }
        });
        
        downloadThread.start();
        
        try {
            downloadThread.join(300000);
            
            if (downloadError.get() != null) {
                throw new IOException("Download failed", downloadError.get());
            }
            
            if (downloadedContent.get() == null) {
                throw new IOException("Download timeout or incomplete");
            }
            
            return downloadedContent.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }
    
    private Path findDownloadedContent() throws IOException {
        return Files.list(downloadsDir)
            .filter(Files::isDirectory)
            .findFirst()
            .orElse(downloadsDir);
    }
    
    public Double getDownloadProgress(String torrentHash) {
        return downloadProgress.get(torrentHash);
    }
    
    public void cancelDownload(String torrentHash) {
        BtClient client = activeDownloads.get(torrentHash);
        if (client != null) {
            client.stop();
            activeDownloads.remove(torrentHash);
            downloadProgress.remove(torrentHash);
            torrentStates.remove(torrentHash);
        }
    }
    
    public void shutdown() {
        activeDownloads.values().forEach(BtClient::stop);
        activeDownloads.clear();
        downloadProgress.clear();
        torrentStates.clear();
        
        if (btRuntime != null) {
            btRuntime.shutdown();
        }
    }
}