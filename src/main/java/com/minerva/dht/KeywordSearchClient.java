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

    public static class SearchResult {
        public String title;
        public String artist;
        public String album;
        public String torrentHash;
        public String genre;
        public Integer year;
        public Integer listenPort;
        public List<String> peers;  // list of "host:port" strings of other known Minerva nodes

        // Set by DHTKeywordManager after receiving results
        public transient String peerHost;
    }
}