package com.minerva.dht;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minerva.library.LibraryManager;
import com.minerva.model.MusicFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeywordSearchServer {
    private static final Logger logger = LoggerFactory.getLogger(KeywordSearchServer.class);
    private final int port;
    private final int listenPort;
    private final LibraryManager libraryManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = false;

    public KeywordSearchServer(int port, LibraryManager libraryManager, int listenPort) {
        this.port = port;
        this.libraryManager = libraryManager;
        this.listenPort = listenPort;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        executor = Executors.newCachedThreadPool();
        running = true;
        logger.info("Keyword search server started on port {}", port);
        new Thread(this::acceptConnections).start();
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (executor != null) executor.shutdown();
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                executor.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running) logger.error("Error accepting connection", e);
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            // Minerva handshake
            String handshake = in.readLine();
            if (!"MINERVA1".equals(handshake)) {
                logger.debug("Received non-Minerva handshake: {}", handshake);
                return;
            }
            out.println("MINERVA1");
            String keyword = in.readLine();
            if (keyword == null) return;
            logger.debug("Received keyword query: {}", keyword);
            // Only respond to queries with .minerva suffix
            if (!keyword.toLowerCase().endsWith(".minerva")) {
                logger.debug("Ignoring non-minerva keyword query: {}", keyword);
                out.println("[]");
                return;
            }
            // Remove .minerva suffix for local search
            String searchKeyword = keyword.substring(0, keyword.length() - ".minerva".length());
            List<MusicFile> tracks = libraryManager.searchLocal(searchKeyword);
            List<SearchResult> results = tracks.stream()
                    .map(t -> new SearchResult(t.getTitle(), t.getArtist(), t.getAlbum(), t.getTorrentHash(), t.getGenre(), t.getYear(), listenPort))
                    .toList();
            String json = objectMapper.writeValueAsString(results);
            out.println(json);
        } catch (Exception e) {
            logger.error("Error handling keyword query", e);
        }
    }

    private static class SearchResult {
        public String title;
        public String artist;
        public String album;
        public String torrentHash;
        public String genre;
        public Integer year;
        public Integer listenPort;

        public SearchResult(String title, String artist, String album, String torrentHash, String genre, Integer year, int listenPort) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.torrentHash = torrentHash;
            this.genre = genre;
            this.year = year;
            this.listenPort = listenPort;
        }
    }
}