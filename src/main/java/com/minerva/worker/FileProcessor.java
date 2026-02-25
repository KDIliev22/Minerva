package com.minerva.worker;

import com.minerva.library.LibraryManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class FileProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FileProcessor.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java FileProcessor <json-file>");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> request = mapper.readValue(new File(args[0]), Map.class);

        String type = (String) request.get("type");
        List<String> tempFilePaths = (List<String>) request.get("tempFiles");
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path libPath = projectRoot.resolve("library");
        Path torrentPath = projectRoot.resolve("torrent_files");
        LibraryManager libraryManager = new LibraryManager(null, libPath, torrentPath);

        List<File> tempFiles = new ArrayList<>();
        for (String path : tempFilePaths) {
            tempFiles.add(new File(path));
        }

        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};
        final String[] torrentHash = {null};

        if ("album".equals(type)) {
            libraryManager.uploadAlbum(tempFiles,
                () -> {
                    logger.info("Album upload completed successfully");
                    success[0] = true;
                    torrentHash[0] = libraryManager.getLastTorrentHash();
                    latch.countDown();
                },
                (error) -> {
                    logger.error("Album upload failed: " + error);
                    latch.countDown();
                },
                metadata
            );
        } else {
            libraryManager.uploadSingleFile(tempFiles.get(0),
                () -> {
                    logger.info("Single upload completed successfully");
                    success[0] = true;
                    torrentHash[0] = libraryManager.getLastTorrentHash();
                    latch.countDown();
                },
                (error) -> {
                    logger.error("Single upload failed: " + error);
                    latch.countDown();
                },
                metadata
            );
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (success[0] && torrentHash[0] != null) {
            File resultFile = new File("upload-result.txt");
            try (FileWriter fw = new FileWriter(resultFile)) {
                fw.write(torrentHash[0]);
            } catch (Exception e) {
                logger.error("Failed to write result file", e);
            }
        }

        System.exit(success[0] ? 0 : 1);
    }
}