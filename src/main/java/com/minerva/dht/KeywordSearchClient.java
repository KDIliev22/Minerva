package com.minerva.dht;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class KeywordSearchClient {
    private static final Logger logger = LoggerFactory.getLogger(KeywordSearchClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<SearchResult> queryPeer(String host, int port, String keyword) {
        String minervaKeyword = keyword.toLowerCase().endsWith(".minerva") ? keyword.toLowerCase() : keyword.toLowerCase() + ".minerva";

        try {
            logger.debug("Connecting to {}:{} for keyword '{}'", host, port, minervaKeyword);
            Socket socket = new Socket();
            socket.setSoTimeout(3000);
            socket.connect(new InetSocketAddress(host, port), 2000);
            try (socket;
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                // Minerva handshake
                out.println("MINERVA1");
                String handshakeResp = in.readLine();
                if (!"MINERVA1".equals(handshakeResp)) {
                    logger.debug("Peer at {}:{} did not respond to handshake, closing.", host, port);
                    return Collections.emptyList();
                }
                out.println(minervaKeyword);
                String response = in.readLine();
                if (response == null) {
                    logger.debug("Empty response from {}:{}", host, port);
                    return Collections.emptyList();
                }
                return objectMapper.readValue(response, new TypeReference<List<SearchResult>>() {});
            }
        } catch (Exception e) {
            logger.debug("Failed to connect to {}:{} - {}", host, port, e.getMessage());
        }
        return Collections.emptyList();
    }

    private static boolean isPrivateHost(String host) {
        return host.equals("127.0.0.1") || host.equals("::1") ||
               host.startsWith("10.") ||
               host.startsWith("172.16.") || host.startsWith("172.17.") || host.startsWith("172.18.") || host.startsWith("172.19.") ||
               host.startsWith("172.20.") || host.startsWith("172.21.") || host.startsWith("172.22.") || host.startsWith("172.23.") ||
               host.startsWith("172.24.") || host.startsWith("172.25.") || host.startsWith("172.26.") || host.startsWith("172.27.") ||
               host.startsWith("172.28.") || host.startsWith("172.29.") || host.startsWith("172.30.") || host.startsWith("172.31.") ||
               host.startsWith("192.168.");
    }

    public static class SearchResult {
        public String title;
        public String artist;
        public String album;
        public String torrentHash;
        public String genre;
        public Integer year;
        public Integer listenPort;
        // Set by DHTKeywordManager after receiving results
        public transient String peerHost;
    }
}