package com.minerva.backend;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handles system/utility endpoints: health check and log retrieval.
 */
public class SystemController {
    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);

    public void register(Javalin app) {
        app.get("/api/test", ctx -> ctx.result("OK"));

        app.get("/api/logs", ctx -> {
            int lines = 200;
            String linesParam = ctx.queryParam("lines");
            if (linesParam != null) {
                try { lines = Math.min(Integer.parseInt(linesParam), 5000); }
                catch (NumberFormatException ignored) {}
            }
            Path logFile = Paths.get(System.getProperty("user.dir"), "logs/minerva.log");
            if (!Files.exists(logFile)) {
                ctx.result("No log file found");
                return;
            }
            List<String> allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lines);
            List<String> tail = allLines.subList(start, allLines.size());
            ctx.contentType("text/plain").result(String.join("\n", tail));
        });
    }
}
