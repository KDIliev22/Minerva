package com.minerva.dht;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PeerCache {
    private static final Logger logger = LoggerFactory.getLogger(PeerCache.class);
    private static final String CACHE_FILE = "minerva_peers.cache";

    public static Set<InetSocketAddress> load() {
        Path path = Paths.get(CACHE_FILE);
        if (!Files.exists(path)) {
            return new HashSet<>();
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.lines()
                    .map(line -> {
                        String[] parts = line.split(":");
                        if (parts.length == 2) {
                            try {
                                String host = parts[0];
                                int port = Integer.parseInt(parts[1]);
                                return new InetSocketAddress(host, port);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        }
                        return null;
                    })
                    .filter(addr -> addr != null && !addr.isUnresolved())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            logger.warn("Failed to load peer cache", e);
            return new HashSet<>();
        }
    }

    public static void save(Set<InetSocketAddress> peers) {
        Path path = Paths.get(CACHE_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (InetSocketAddress addr : peers) {
                writer.write(addr.getAddress().getHostAddress() + ":" + addr.getPort());
                writer.newLine();
            }
        } catch (IOException e) {
            logger.warn("Failed to save peer cache", e);
        }
    }
}