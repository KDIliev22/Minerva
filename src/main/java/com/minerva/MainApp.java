package com.minerva;

import com.minerva.backend.BackendServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        // Read API port from environment variable, default to 4567
        int port = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "4567"));

        // Start the backend server
        BackendServer server = new BackendServer();
        server.start(port);

        logger.info("Backend server started on port {}. Press Ctrl+C to stop.", port);

        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}