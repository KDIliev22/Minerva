package com.minerva.library;

import com.minerva.dht.DHTKeywordManager;
import com.minerva.model.Album;
import com.minerva.model.MusicFile;
import com.minerva.network.JLibTorrentManager;
import com.minerva.network.TorrentCreator;
import com.minerva.network.TorrentMetadata;
import com.minerva.storage.MusicMetadataExtractor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class LibraryManager {
    private static final Logger logger = LoggerFactory.getLogger(LibraryManager.class);

    private final JLibTorrentManager torrentManager;
    private final TorrentCreator torrentCreator;
    private String lastTorrentHash;
    private final Path libraryDir;
    private final Path torrentFilesDir;
    private final Path metadataDir;
    private final List<MusicFile> tracks = Collections.synchronizedList(new ArrayList<>());

    private final Map<String, TorrentMetadata> metadataMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getLastTorrentHash() {
        return lastTorrentHash;
    }

    public LibraryManager(JLibTorrentManager torrentManager, Path libraryDir, Path torrentFilesDir) {
        this.torrentManager = torrentManager;
        this.torrentCreator = new TorrentCreator(torrentFilesDir, libraryDir);
        this.libraryDir = libraryDir;
        this.torrentFilesDir = torrentFilesDir;
        this.metadataDir = torrentFilesDir.getParent().resolve("torrents");
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        loadMetadata();
    }

    public List<MusicFile> getTracks() {
        return tracks;
    }

    private static class TorrentMetadata {
    @JsonProperty("torrent_id")
    public String torrentId;

    @JsonProperty("torrent_hash")
    public String torrentHash;

    @JsonProperty("title")
    public String title;

    @JsonProperty("artist")
    public String artist;

    @JsonProperty("album")
    public String album;

    @JsonProperty("genre")
    public String genre;

    @JsonProperty("year")
    public Integer year;

    @JsonProperty("bitrate")
    public Integer bitrate;

    @JsonProperty("tracks")
    public List<TrackInfo> tracks = new ArrayList<>();

    // TrackInfo inner class also needs annotations if it has mismatched keys
    static class TrackInfo {
        @JsonProperty("title")
        public String title;

        @JsonProperty("artist")
        public String artist;

        @JsonProperty("trackNumber")
        public String trackNumber;   // check: does JSON use "trackNumber" or "track_number"? From earlier output it was "trackNumber" (camelCase). Keep as is or annotate accordingly.

        @JsonProperty("discNumber")
        public String discNumber;

        @JsonProperty("duration")
        public Long duration;

        @JsonProperty("bitrate")
        public Integer bitrate;

        @JsonProperty("fileName")
        public String fileName;
    }
}

    private void loadMetadata() {
        try {
            Files.createDirectories(metadataDir);
            Files.list(metadataDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            TorrentMetadata meta = objectMapper.readValue(p.toFile(), TorrentMetadata.class);
                            if (meta.torrentHash != null) {
                                metadataMap.put(meta.torrentHash, meta);
                            }
                            logger.info("Loaded metadata from {}", p);
                        } catch (IOException e) {
                            logger.warn("Failed to load metadata from {}", p, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to load metadata directory", e);
        }
    }

    private void saveMetadata(String infoHash, TorrentMetadata meta) {
        try {
            Files.createDirectories(metadataDir);
            Path file = metadataDir.resolve(infoHash + ".json");
            objectMapper.writeValue(file.toFile(), meta);
            logger.info("Saved metadata for {}", infoHash);
        } catch (IOException e) {
            logger.error("Failed to save metadata for {}", infoHash, e);
        }
    }

    private Path findAudioFile(Path dir) throws IOException {
        return Files.list(dir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".mp3") || name.endsWith(".flac") ||
                            name.endsWith(".wav") || name.endsWith(".m4a") ||
                            name.endsWith(".ogg");
                })
                .findFirst()
                .orElse(null);
    }

    private Path findAudioFileByTitle(Path dir, String title) throws IOException {
        String normalizedTitle = title.toLowerCase().replaceAll("\\s+", "");
        return Files.list(dir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    String nameWithoutExt = name.contains(".") ?
                            name.substring(0, name.lastIndexOf('.')) : name;
                    String normalizedName = nameWithoutExt.replaceAll("[\\s_-]", "");
                    return normalizedName.contains(normalizedTitle) ||
                            normalizedTitle.contains(normalizedName);
                })
                .findFirst()
                .orElse(null);
    }

    public void loadLibraryFromTorrents() {
        tracks.clear();
        for (Map.Entry<String, TorrentMetadata> entry : metadataMap.entrySet()) {
            String hash = entry.getKey();
            TorrentMetadata meta = entry.getValue();

            String safeArtist = sanitize(meta.artist);
            String safeAlbum = sanitize(meta.album);
            Path albumDir = libraryDir.resolve(safeArtist).resolve(safeAlbum);

            if (!Files.exists(albumDir)) {
                logger.warn("Album directory not found: {}", albumDir);
                continue;
            }

            if (meta.tracks == null || meta.tracks.isEmpty()) {
                MusicFile mf = new MusicFile();
                mf.setId(hash);
                mf.setTitle(meta.title);
                mf.setArtist(meta.artist);
                mf.setAlbum(meta.album);
                mf.setGenre(meta.genre);
                if (meta.year != null) mf.setYear(meta.year);
                if (meta.bitrate != null) mf.setBitrate(meta.bitrate);
                mf.setTorrentHash(hash);

                try {
                    Path audioFile = findAudioFile(albumDir);
                    if (audioFile != null) {
                        mf.setFilePath(audioFile.toString());
                        logger.debug("Found single track file: {}", audioFile);
                    } else {
                        logger.warn("No audio file found in {}", albumDir);
                    }
                } catch (IOException e) {
                    logger.error("Error scanning directory {}", albumDir, e);
                }
                tracks.add(mf);
            } else {
                for (int i = 0; i < meta.tracks.size(); i++) {
                    TorrentMetadata.TrackInfo t = meta.tracks.get(i);
                    MusicFile mf = new MusicFile();
                    mf.setId(hash + "_" + i);
                    mf.setTitle(t.title);
                    mf.setArtist(t.artist != null ? t.artist : meta.artist);
                    mf.setAlbum(meta.album);
                    mf.setGenre(meta.genre);
                    if (meta.year != null) mf.setYear(meta.year);
                    if (t.bitrate != null) mf.setBitrate(t.bitrate);
                    mf.setTrackNumber(t.trackNumber);
                    mf.setDiscNumber(t.discNumber);
                    mf.setDuration(t.duration);
                    mf.setTorrentHash(hash);

                    if (t.fileName != null) {
                        Path fullPath = albumDir.resolve(t.fileName);
                        if (Files.exists(fullPath)) {
                            mf.setFilePath(fullPath.toString());
                            logger.debug("Found album track file: {}", fullPath);
                        } else {
                            logger.warn("Track file not found: {}", fullPath);
                            try {
                                Path audioFile = findAudioFileByTitle(albumDir, t.title);
                                if (audioFile != null) {
                                    mf.setFilePath(audioFile.toString());
                                }
                            } catch (IOException e) {
                                logger.error("Error scanning for track {}", t.title, e);
                            }
                        }
                    } else {
                        try {
                            Path audioFile = findAudioFileByTitle(albumDir, t.title);
                            if (audioFile != null) {
                                mf.setFilePath(audioFile.toString());
                            }
                        } catch (IOException e) {
                            logger.error("Error scanning for track {}", t.title, e);
                        }
                    }
                    tracks.add(mf);
                }
            }
        }
        logger.info("Loaded {} tracks from metadata", tracks.size());
    }

    /**
     * Enrich incomplete metadata by reading ID3 tags from audio files.
     * Called at startup before torrents are actively seeded.
     */
    public void enrichIncompleteMetadata() {
        int enriched = 0;
        for (Map.Entry<String, TorrentMetadata> entry : metadataMap.entrySet()) {
            String hash = entry.getKey();
            TorrentMetadata meta = entry.getValue();

            // Check if metadata needs enrichment
            boolean needsEnrichment = "Unknown".equals(meta.genre) || meta.year == null 
                    || meta.bitrate == null;
            if (meta.tracks != null) {
                for (TorrentMetadata.TrackInfo t : meta.tracks) {
                    if (t.duration == null || t.duration == 0L || t.bitrate == null) {
                        needsEnrichment = true;
                        break;
                    }
                }
            }
            if (!needsEnrichment) continue;

            String safeArtist = sanitize(meta.artist);
            String safeAlbum = sanitize(meta.album);
            Path albumDir = libraryDir.resolve(safeArtist).resolve(safeAlbum);
            if (!Files.exists(albumDir)) continue;

            logger.info("Enriching metadata for {} - {}", meta.artist, meta.album);
            boolean changed = false;
            boolean coverSaved = Files.exists(albumDir.resolve("cover.jpg")) 
                    || Files.exists(albumDir.resolve("cover.png"));

            if (meta.tracks != null) {
                for (int i = 0; i < meta.tracks.size(); i++) {
                    TorrentMetadata.TrackInfo tInfo = meta.tracks.get(i);
                    if (tInfo.fileName == null) continue;

                    Path audioFilePath = albumDir.resolve(tInfo.fileName);
                    if (!Files.exists(audioFilePath)) continue;

                    try {
                        MusicMetadataExtractor.MusicMetadata extracted =
                                MusicMetadataExtractor.extractMetadata(audioFilePath.toFile());
                        if (extracted == null) continue;

                        // Enrich track-level data
                        if (extracted.title != null && !extracted.title.isEmpty()
                                && !extracted.title.equals(audioFilePath.getFileName().toString())
                                && !"Unknown".equals(extracted.title)) {
                            tInfo.title = extracted.title;
                            changed = true;
                        }
                        if (extracted.artist != null && !extracted.artist.isEmpty()
                                && !"Unknown Artist".equals(extracted.artist)) {
                            tInfo.artist = extracted.artist;
                            changed = true;
                        }
                        if ((tInfo.duration == null || tInfo.duration == 0L) && extracted.duration > 0) {
                            tInfo.duration = (long) extracted.duration;
                            changed = true;
                        }
                        if (tInfo.bitrate == null && extracted.bitrate > 0) {
                            tInfo.bitrate = extracted.bitrate;
                            changed = true;
                        }
                        if ((tInfo.trackNumber == null || tInfo.trackNumber.isEmpty()) 
                                && extracted.trackNumber != null && !extracted.trackNumber.isEmpty()) {
                            tInfo.trackNumber = extracted.trackNumber;
                            changed = true;
                        }
                        if ((tInfo.discNumber == null || tInfo.discNumber.isEmpty())
                                && extracted.discNumber != null && !extracted.discNumber.isEmpty()) {
                            tInfo.discNumber = extracted.discNumber;
                            changed = true;
                        }

                        // Enrich album-level data from first track
                        if (i == 0) {
                            if (("Unknown".equals(meta.genre) || meta.genre == null)
                                    && extracted.genre != null && !extracted.genre.isEmpty()
                                    && !"Unknown".equals(extracted.genre)) {
                                meta.genre = extracted.genre;
                                changed = true;
                            }
                            if (meta.year == null && extracted.year > 0) {
                                meta.year = extracted.year;
                                changed = true;
                            }
                            if (meta.bitrate == null && extracted.bitrate > 0) {
                                meta.bitrate = extracted.bitrate;
                                changed = true;
                            }
                        }

                        // Save cover art if not already present
                        if (!coverSaved && extracted.albumArtBase64 != null && !extracted.albumArtBase64.isEmpty()) {
                            try {
                                byte[] imageData = Base64.getDecoder().decode(extracted.albumArtBase64);
                                String artExt = extracted.albumArtMimeType != null
                                        && extracted.albumArtMimeType.contains("png") ? ".png" : ".jpg";
                                Path artFile = albumDir.resolve("cover" + artExt);
                                Files.write(artFile, imageData);
                                logger.info("Extracted and saved album art to {}", artFile);
                                coverSaved = true;
                                changed = true;
                            } catch (Exception e) {
                                logger.warn("Failed to save extracted album art", e);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to extract metadata from {}: {}", tInfo.fileName, e.getMessage());
                    }
                }
            }

            if (changed) {
                saveMetadata(hash, meta);
                enriched++;
                logger.info("Enriched metadata for {} - {} (hash: {})", meta.artist, meta.album, hash);
            }
        }
        if (enriched > 0) {
            logger.info("Enriched metadata for {} albums", enriched);
        }
    }

    /**
     * Import a completed download into the library.
     * Moves files from downloads dir into library/Artist/Album/, creates .json metadata,
     * and reloads the library.
     */
    public void importCompletedDownload(String hash, String artist, String album,
                                         List<Map<String, String>> trackList) {
        logger.info("Importing completed download: hash={}, artist={}, album={}", hash, artist, album);
        try {
            // Get torrent metadata from the bt-based manager
            String torrentName = torrentManager.getTorrentName(hash);
            List<String> torrentFileNames = torrentManager.getTorrentFileNames(hash);
            int numFiles = torrentFileNames.size();
            boolean isMultiFile = numFiles > 1;

            if (torrentName == null || torrentName.startsWith("magnet:")) {
                // Metadata not yet resolved — try to discover from downloads dir
                logger.warn("Torrent metadata not resolved for {} — scanning downloads dir", hash);
                Path downloadsDir = Paths.get(System.getProperty("user.dir"))
                        .resolve(System.getenv().getOrDefault("DOWNLOADS_DIR", "downloads"));
                File[] entries = downloadsDir.toFile().listFiles();
                if (entries != null) {
                    for (File entry : entries) {
                        if (entry.isDirectory()) {
                            torrentName = entry.getName();
                            isMultiFile = true;
                            File[] innerFiles = entry.listFiles();
                            torrentFileNames = new ArrayList<>();
                            if (innerFiles != null) {
                                for (File f : innerFiles) torrentFileNames.add(f.getName());
                            }
                            numFiles = torrentFileNames.size();
                            break;
                        }
                    }
                    if (torrentName == null || torrentName.startsWith("magnet:")) {
                        for (File entry : entries) {
                            if (entry.isFile()) {
                                torrentName = entry.getName();
                                torrentFileNames = new ArrayList<>();
                                torrentFileNames.add(entry.getName());
                                numFiles = 1;
                                isMultiFile = false;
                                break;
                            }
                        }
                    }
                }
            }

            if (torrentName == null) {
                logger.error("Could not determine torrent name for hash {} — cannot import", hash);
                return;
            }

            // Determine source path in downloads directory
            Path downloadsDir = Paths.get(System.getProperty("user.dir"))
                    .resolve(System.getenv().getOrDefault("DOWNLOADS_DIR", "downloads"));

            // Build target library directory
            String safeArtist = sanitize(artist);
            String safeAlbum = sanitize(album);
            Path albumDir = libraryDir.resolve(safeArtist).resolve(safeAlbum);
            Files.createDirectories(albumDir);
            logger.info("Target album directory: {}", albumDir);

            // Collect file names
            List<String> fileNames = new ArrayList<>();
            for (String filePath : torrentFileNames) {
                String fileName = Paths.get(filePath).getFileName().toString();
                fileNames.add(fileName);
            }

            // Remove the torrent handle first so bt releases the files
            try {
                torrentManager.removeTorrent(hash, false);
                logger.info("Removed download torrent handle before moving files");
                Thread.sleep(1000); // Give time for file handles to be released
            } catch (Exception e) {
                logger.warn("Could not remove torrent handle: {}", e.getMessage());
            }

            // Move files from downloads to library
            for (String fileName : fileNames) {
                Path src = isMultiFile ? downloadsDir.resolve(torrentName).resolve(fileName)
                                       : downloadsDir.resolve(fileName);
                Path dest = albumDir.resolve(fileName);
                if (Files.exists(src)) {
                    Files.move(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Moved {} -> {}", src, dest);
                } else {
                    logger.warn("Source file not found: {}", src);
                }
            }

            // Clean up empty source directory for multi-file torrents
            if (isMultiFile) {
                Path srcDir = downloadsDir.resolve(torrentName);
                if (Files.exists(srcDir)) {
                    try (var entries = Files.list(srcDir)) {
                        if (entries.findFirst().isEmpty()) {
                            Files.delete(srcDir);
                            logger.info("Removed empty download directory: {}", srcDir);
                        }
                    }
                }
            }

            // Create .json metadata from filenames and search result data.
            // Metadata enrichment (year, genre, bitrate, duration) happens on next
            // startup via enrichIncompleteMetadata().
            TorrentMetadata meta = new TorrentMetadata();
            meta.torrentId = hash;
            meta.torrentHash = hash;
            meta.title = album;
            meta.artist = artist;
            meta.album = album;
            meta.genre = "Unknown";
            meta.year = null;
            meta.bitrate = null;
            meta.tracks = new ArrayList<>();

            // Extract year/genre from search metadata if provided
            if (trackList != null && !trackList.isEmpty()) {
                Map<String, String> firstTrack = trackList.get(0);
                if (firstTrack.get("year") != null) {
                    try { meta.year = Integer.parseInt(firstTrack.get("year")); } catch (NumberFormatException ignored) {}
                }
                if (firstTrack.get("genre") != null && !"Unknown".equals(firstTrack.get("genre"))) {
                    meta.genre = firstTrack.get("genre");
                }
            }

            // Filter to audio-only file names (skip cover.jpg etc.)
            Set<String> audioExts = new HashSet<>(Arrays.asList("mp3", "flac", "wav", "m4a", "ogg"));
            List<String> audioFileNames = new ArrayList<>();
            for (String fn : fileNames) {
                String ext = fn.contains(".") ? fn.substring(fn.lastIndexOf('.') + 1).toLowerCase() : "";
                if (audioExts.contains(ext)) {
                    audioFileNames.add(fn);
                }
            }

            for (int i = 0; i < audioFileNames.size(); i++) {
                String fileName = audioFileNames.get(i);
                TorrentMetadata.TrackInfo tInfo = new TorrentMetadata.TrackInfo();
                tInfo.fileName = fileName;
                tInfo.artist = artist;

                // Always use filename-derived title. Search results are unordered
                // and can't be matched by index. Real ID3 titles will be filled in
                // by enrichIncompleteMetadata() on next startup.
                tInfo.title = extractTitleFromFileName(fileName);
                String[] parts = extractDiscTrackFromFileName(fileName);
                tInfo.discNumber = parts[0];
                tInfo.trackNumber = parts[1];
                tInfo.duration = 0L;
                tInfo.bitrate = null;

                meta.tracks.add(tInfo);
            }

            // Sort tracks by disc number, then track number
            meta.tracks.sort(Comparator
                    .comparingInt((TorrentMetadata.TrackInfo t) -> {
                        try { return Integer.parseInt(t.discNumber); } catch (Exception e) { return 0; }
                    })
                    .thenComparingInt(t -> {
                        try { return Integer.parseInt(t.trackNumber); } catch (Exception e) { return 0; }
                    }));

            metadataMap.put(hash, meta);
            saveMetadata(hash, meta);
            logger.info("Saved metadata for imported download: {}", hash);

            // Enrich metadata from audio tags immediately (duration, bitrate, genre, year, cover art)
            enrichIncompleteMetadata();

            // Reload library so tracks are immediately available in the UI
            loadLibraryFromTorrents();

            // ── Create .torrent and auto-seed ──
            try {
                List<File> libraryAudioFiles = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(albumDir)) {
                    for (Path entry : stream) {
                        if (Files.isRegularFile(entry)) {
                            String fn = entry.getFileName().toString().toLowerCase();
                            String ext = fn.contains(".") ? fn.substring(fn.lastIndexOf('.') + 1) : "";
                            if (audioExts.contains(ext) || fn.equals("cover.jpg") || fn.equals("cover.png")) {
                                libraryAudioFiles.add(entry.toFile());
                            }
                        }
                    }
                }
                libraryAudioFiles.sort(Comparator.comparing(File::getName));

                if (!libraryAudioFiles.isEmpty()) {
                    URI trackerURI = URI.create("udp://tracker.opentrackr.org:1337/announce");
                    com.turn.ttorrent.common.Torrent torrent;
                    if (libraryAudioFiles.size() == 1) {
                        torrent = com.turn.ttorrent.common.Torrent.create(
                                libraryAudioFiles.get(0), trackerURI, "Minerva");
                    } else {
                        torrent = com.turn.ttorrent.common.Torrent.create(
                                albumDir.toRealPath().toFile(), libraryAudioFiles, trackerURI, "Minerva");
                    }

                    String newHash = bytesToHex(torrent.getInfoHash());
                    Path torrentFilePath = torrentFilesDir.resolve(newHash + ".torrent");
                    try (OutputStream os = new FileOutputStream(torrentFilePath.toFile())) {
                        torrent.save(os);
                    }
                    logger.info("Created .torrent file: {} (hash: {})", torrentFilePath, newHash);

                    // Update metadata if hash changed (expected for magnet downloads)
                    if (!newHash.equals(hash)) {
                        metadataMap.remove(hash);
                        Files.deleteIfExists(metadataDir.resolve(hash + ".json"));
                        meta.torrentId = newHash;
                        meta.torrentHash = newHash;
                        metadataMap.put(newHash, meta);
                        saveMetadata(newHash, meta);
                        loadLibraryFromTorrents();
                        logger.info("Torrent re-hashed: {} -> {}", hash, newHash);
                    }

                    // Seed immediately
                    boolean isAlbumTorrent = meta.tracks != null && meta.tracks.size() > 1;
                    File savePath = isAlbumTorrent ? albumDir.getParent().toFile() : albumDir.toFile();
                    torrentManager.seedTorrent(torrentFilePath.toFile(), savePath);
                    logger.info("Auto-seeding started for {} (hash: {})", album, newHash);
                }
            } catch (Exception torrentEx) {
                logger.warn("Failed to create torrent and auto-seed: {}", torrentEx.getMessage());
                logger.info("Torrent will be seeded on next restart.");
            }

            logger.info("Import complete for {}/{}", artist, album);

        } catch (Exception e) {
            logger.error("Failed to import completed download: {}", hash, e);
        }
    }

    private String extractTitleFromFileName(String fileName) {
        // Format: DD_TT_title.ext or just title.ext
        String nameWithoutExt = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        // Try to match DD_TT_title pattern
        if (nameWithoutExt.matches("\\d{2}_\\d{2}_.*")) {
            return nameWithoutExt.substring(6).replace("_", " ");
        } else if (nameWithoutExt.matches("\\d{2}_.*")) {
            return nameWithoutExt.substring(3).replace("_", " ");
        }
        return nameWithoutExt.replace("_", " ");
    }

    private String[] extractDiscTrackFromFileName(String fileName) {
        String nameWithoutExt = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        // Try DD_TT_title pattern
        if (nameWithoutExt.matches("\\d{2}_\\d{2}_.*")) {
            return new String[]{nameWithoutExt.substring(0, 2), nameWithoutExt.substring(3, 5)};
        } else if (nameWithoutExt.matches("\\d{2}_.*")) {
            return new String[]{"1", nameWithoutExt.substring(0, 2)};
        }
        return new String[]{"1", String.valueOf(1)};
    }

    public void seedExistingTorrents() {
        if (torrentManager == null) {
            logger.info("TorrentManager not available – skipping seeding");
            return;
        }
        logger.info("Seeding existing torrents...");
        for (Map.Entry<String, TorrentMetadata> entry : metadataMap.entrySet()) {
            String hash = entry.getKey();
            TorrentMetadata meta = entry.getValue();
            try {
                if (meta.torrentId == null) {
                    logger.warn("Torrent metadata missing torrentId for hash: {}", hash);
                    continue;
                }
                Path torrentFilePath = torrentFilesDir.resolve(meta.torrentId + ".torrent");
                File torrentFile = torrentFilePath.toFile();
                if (!torrentFile.exists()) {
                    logger.warn("Torrent file not found: {}", torrentFilePath);
                    continue;
                }
                if (meta.artist == null || meta.album == null) {
                    logger.warn("Missing artist/album for torrent: {}", hash);
                    continue;
                }

                String safeArtist = sanitize(meta.artist);
                String safeAlbum = sanitize(meta.album);
                Path albumDir = libraryDir.resolve(safeArtist).resolve(safeAlbum);
                if (!Files.exists(albumDir)) {
                    logger.warn("Album directory not found: {}", albumDir);
                    continue;
                }

                logger.info("Attempting to seed torrent: {} (hash: {})", meta.title, hash);
                // For multi-file torrents (albums), bt expects storage/torrentName/files
                // so save_path must be the PARENT of the album dir (artist dir).
                // For single-file torrents, save_path = albumDir is correct.
                boolean isAlbum = meta.tracks != null && meta.tracks.size() > 1;
                File savePath = isAlbum ? albumDir.getParent().toFile() : albumDir.toFile();
                torrentManager.seedTorrent(torrentFile, savePath);
                logger.info("Seeded existing torrent: {} from {} (album={})", meta.title, savePath, isAlbum);

                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            } catch (Throwable t) {
                logger.error("Failed to seed torrent for hash: " + hash, t);
            }
        }
    }

    public byte[] getAlbumArtByTorrentHash(String hash) {
        TorrentMetadata meta = metadataMap.get(hash);
        if (meta == null) return null;
        String safeArtist = sanitize(meta.artist);
        String safeAlbum = sanitize(meta.album);
        Path albumDir = libraryDir.resolve(safeArtist).resolve(safeAlbum);
        String[] extensions = {".jpg", ".png"};
        for (String ext : extensions) {
            Path artFile = albumDir.resolve("cover" + ext);
            if (Files.exists(artFile)) {
                try {
                    return Files.readAllBytes(artFile);
                } catch (IOException e) {
                    logger.error("Failed to read album art", e);
                }
            }
        }
        return null;
    }

    public void uploadSingleFile(File file, Runnable onComplete, Consumer<String> onError, Map<String, Object> overrideMetadata) {
        logger.info("=== Starting single upload for: {} ===", file.getName());
        try {
            // Extract metadata from audio file ID3 tags first
            MusicMetadataExtractor.MusicMetadata extracted = MusicMetadataExtractor.extractMetadata(file);
            MusicMetadataExtractor.MusicMetadata metadata = new MusicMetadataExtractor.MusicMetadata();

            if (extracted != null) {
                metadata.title = (extracted.title != null && !extracted.title.isEmpty()
                        && !extracted.title.equals(file.getName()))
                        ? extracted.title
                        : file.getName().replaceFirst("\\.[^.]+$", "");
                metadata.artist = (extracted.artist != null && !extracted.artist.isEmpty()
                        && !"Unknown Artist".equals(extracted.artist))
                        ? extracted.artist : "Unknown";
                metadata.album = (extracted.album != null && !extracted.album.isEmpty()
                        && !"Unknown Album".equals(extracted.album))
                        ? extracted.album : "Unknown";
                metadata.albumArtist = extracted.albumArtist;
                metadata.genre = extracted.genre;
                metadata.year = extracted.year;
                metadata.bitrate = extracted.bitrate;
                metadata.duration = extracted.duration;
                metadata.trackNumber = extracted.trackNumber;
                metadata.discNumber = extracted.discNumber;
                metadata.albumArtBase64 = extracted.albumArtBase64;
                metadata.albumArtMimeType = extracted.albumArtMimeType;
                logger.info("Extracted ID3 tags: title={}, artist={}, album={}", metadata.title, metadata.artist, metadata.album);
            } else {
                metadata.title = file.getName().replaceFirst("\\.[^.]+$", "");
                metadata.artist = "Unknown";
                metadata.album = "Unknown";
            }

            // Apply explicit overrides on top of extracted metadata
            if (overrideMetadata != null) {
                logger.info("Applying metadata overrides");
                if (overrideMetadata.containsKey("title") && overrideMetadata.get("title") != null) 
                    metadata.title = (String) overrideMetadata.get("title");
                if (overrideMetadata.containsKey("artist") && overrideMetadata.get("artist") != null) 
                    metadata.artist = (String) overrideMetadata.get("artist");
                if (overrideMetadata.containsKey("album") && overrideMetadata.get("album") != null) 
                    metadata.album = (String) overrideMetadata.get("album");
                if (overrideMetadata.containsKey("albumArtist") && overrideMetadata.get("albumArtist") != null) 
                    metadata.albumArtist = (String) overrideMetadata.get("albumArtist");
                if (overrideMetadata.containsKey("genre") && overrideMetadata.get("genre") != null) 
                    metadata.genre = (String) overrideMetadata.get("genre");
                if (overrideMetadata.containsKey("year") && overrideMetadata.get("year") != null) 
                    metadata.year = (Integer) overrideMetadata.get("year");
                if (overrideMetadata.containsKey("trackNumber") && overrideMetadata.get("trackNumber") != null) 
                    metadata.trackNumber = (String) overrideMetadata.get("trackNumber");
                if (overrideMetadata.containsKey("discNumber") && overrideMetadata.get("discNumber") != null) 
                    metadata.discNumber = (String) overrideMetadata.get("discNumber");
                if (overrideMetadata.containsKey("albumArtBase64") && overrideMetadata.get("albumArtBase64") != null) 
                    metadata.albumArtBase64 = (String) overrideMetadata.get("albumArtBase64");
                if (overrideMetadata.containsKey("albumArtMimeType") && overrideMetadata.get("albumArtMimeType") != null) 
                    metadata.albumArtMimeType = (String) overrideMetadata.get("albumArtMimeType");
            }
            logger.info("Final metadata: title={}, artist={}, album={}", metadata.title, metadata.artist, metadata.album);

            MusicFile musicFile = convertMetadataToMusicFile(metadata);
            String nodeId = UUID.randomUUID().toString().substring(0, 8);

            logger.info("Step 2: Creating torrent via TorrentCreator");
            com.minerva.network.TorrentMetadata torrentMeta = null;
            try {
                torrentMeta = torrentCreator.createSingleTorrent(musicFile, file, nodeId);
                logger.info("Torrent created, hash: {}", torrentMeta.getTorrentHash());
            } catch (Exception e) {
                logger.error("Torrent creation failed", e);
                onError.accept("Torrent creation failed: " + e.getMessage());
                return;
            }

            Path contentDir = libraryDir
                    .resolve(sanitize(metadata.artist))
                    .resolve(sanitize(metadata.album));
            logger.info("Content directory: {}", contentDir);

            // Save album art as cover.jpg in library album dir
            if (metadata.albumArtBase64 != null && !metadata.albumArtBase64.isEmpty()) {
                try {
                    byte[] imageData = Base64.getDecoder().decode(metadata.albumArtBase64);
                    String ext = metadata.albumArtMimeType != null && metadata.albumArtMimeType.contains("png") ? ".png" : ".jpg";
                    Path artFile = contentDir.resolve("cover" + ext);
                    Files.write(artFile, imageData);
                    logger.info("Saved album art to library: {}", artFile);
                } catch (Exception e) {
                    logger.warn("Failed to save album art to library dir", e);
                }
            }

            String hash = torrentMeta.getTorrentHash();
            String fileName = torrentMeta.getTrackInfo().getFileName();

            TorrentMetadata meta = new TorrentMetadata();
            meta.torrentId = torrentMeta.getTorrentId();
            meta.torrentHash = hash;
            meta.title = metadata.title;
            meta.artist = metadata.artist;
            meta.album = metadata.album;
            meta.genre = metadata.genre;
            meta.year = metadata.year;
            meta.bitrate = metadata.bitrate;

            // Populate tracks list with a single TrackInfo entry
            TorrentMetadata.TrackInfo ti = new TorrentMetadata.TrackInfo();
            ti.title = metadata.title;
            ti.artist = metadata.artist;
            ti.trackNumber = (metadata.trackNumber != null && !metadata.trackNumber.isEmpty())
                    ? metadata.trackNumber : "01";
            ti.discNumber = (metadata.discNumber != null && !metadata.discNumber.isEmpty())
                    ? metadata.discNumber : "01";
            ti.duration = (long) metadata.duration;
            ti.bitrate = metadata.bitrate;
            ti.fileName = fileName;
            meta.tracks.add(ti);

            metadataMap.put(hash, meta);
            saveMetadata(hash, meta);

            MusicFile newTrack = new MusicFile();
            newTrack.setId(hash + "_0");
            newTrack.setTitle(metadata.title);
            newTrack.setArtist(metadata.artist);
            newTrack.setAlbum(metadata.album);
            newTrack.setGenre(metadata.genre);
            newTrack.setYear(metadata.year);
            newTrack.setBitrate(metadata.bitrate);
            newTrack.setDuration((long) metadata.duration);
            newTrack.setTrackNumber(ti.trackNumber);
            newTrack.setDiscNumber(ti.discNumber);
            newTrack.setTorrentHash(hash);
            newTrack.setFilePath(contentDir.resolve(fileName).toString());
            tracks.add(newTrack);
            logger.info("Added new track to library: {} - {} ({})", metadata.artist, metadata.title, metadata.album);

            lastTorrentHash = hash;

            // Enrich metadata from audio tags (duration, bitrate, genre, year)
            enrichIncompleteMetadata();

            // Reload library so tracks are immediately visible
            loadLibraryFromTorrents();

            // Auto-seed the new torrent
            if (torrentManager != null) {
                try {
                    Path torrentFilePath = torrentFilesDir.resolve(hash + ".torrent");
                    if (Files.exists(torrentFilePath)) {
                        File savePath = contentDir.toFile();
                        torrentManager.seedTorrent(torrentFilePath.toFile(), savePath);
                        logger.info("Auto-seeding single upload: {} (hash: {})", metadata.title, hash);
                    }
                } catch (Exception seedEx) {
                    logger.warn("Failed to auto-seed single upload: {}", seedEx.getMessage());
                }
            }

            logger.info("Upload completed successfully");
            onComplete.run();
        } catch (Exception e) {
            logger.error("Unexpected error in uploadSingleFile", e);
            onError.accept("Unexpected error: " + e.getMessage());
        }
    }

    public void uploadAlbum(List<File> files, Runnable onComplete, Consumer<String> onError, Map<String, Object> albumOverride) {
        logger.info("=== Starting album upload with {} files ===", files.size());
        try {
            if (albumOverride == null) {
                onError.accept("Missing album metadata");
                return;
            }
            String artist = (String) albumOverride.get("artist");
            String albumTitle = (String) albumOverride.get("album");
            Integer year = (Integer) albumOverride.get("year");
            String genre = (String) albumOverride.get("genre");
            String albumArtBase64 = (String) albumOverride.get("albumArtBase64");
            String albumArtMimeType = (String) albumOverride.get("albumArtMimeType");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tracksOverride = (List<Map<String, Object>>) albumOverride.get("tracks");
            if (tracksOverride == null || tracksOverride.size() != files.size()) {
                logger.error("Track list mismatch: expected {} tracks, got {}", files.size(),
                        tracksOverride == null ? 0 : tracksOverride.size());
                onError.accept("Track list mismatch");
                return;
            }

            Album album = new Album();
            album.setArtist(artist);
            album.setTitle(albumTitle);
            album.setYear(year != null ? year : 0);
            album.setGenre(genre != null ? genre : "Unknown");
            List<MusicFile> musicFiles = new ArrayList<>();

            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                logger.debug("Processing track {}: {}", i + 1, file.getName());
                MusicMetadataExtractor.MusicMetadata metadata = new MusicMetadataExtractor.MusicMetadata();
                Map<String, Object> trackData = tracksOverride.get(i);
                metadata.title = (String) trackData.getOrDefault("title", metadata.title);
                metadata.artist = (String) trackData.getOrDefault("artist", metadata.artist);
                if (metadata.artist == null || metadata.artist.trim().isEmpty()) {
                    metadata.artist = artist;
                }
                metadata.album = albumTitle;
                metadata.year = year != null ? year : 0;
                metadata.genre = genre != null ? genre : "Unknown";
                metadata.trackNumber = (String) trackData.getOrDefault("trackNumber", String.valueOf(i + 1));

                MusicFile musicFile = convertMetadataToMusicFile(metadata);
                musicFile.setId(UUID.randomUUID().toString());
                musicFiles.add(musicFile);
            }
            album.setTracks(musicFiles);

            Path albumDir = libraryDir.resolve(sanitize(artist)).resolve(sanitize(albumTitle));
            if (Files.exists(albumDir)) {
                logger.info("Cleaning existing audio files from {}", albumDir);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(albumDir, "*.{mp3,flac,wav,m4a,ogg}")) {
                    for (Path p : stream) {
                        Files.delete(p);
                        logger.debug("Deleted old file: {}", p);
                    }
                } catch (IOException e) {
                    logger.warn("Could not clean old files: {}", e.getMessage());
                }
            }

            // Save album art as cover.jpg BEFORE torrent creation so it gets included
            Files.createDirectories(albumDir);
            if (albumArtBase64 != null && !albumArtBase64.isEmpty()) {
                try {
                    byte[] imageData = Base64.getDecoder().decode(albumArtBase64);
                    String ext = albumArtMimeType != null && albumArtMimeType.contains("png") ? ".png" : ".jpg";
                    Path artFile = albumDir.resolve("cover" + ext);
                    Files.write(artFile, imageData);
                    logger.info("Saved album art to {} (before torrent creation)", artFile);
                } catch (Exception e) {
                    logger.error("Failed to save album art", e);
                }
            }

            String nodeId = UUID.randomUUID().toString().substring(0, 8);
            com.minerva.network.TorrentMetadata torrentMeta;
            try {
                torrentMeta = torrentCreator.createAlbumTorrent(album, files, nodeId);
                logger.info("Album torrent created, ID: {}", torrentMeta.getTorrentId());
            } catch (Exception e) {
                logger.error("Torrent creation failed", e);
                onError.accept("Torrent creation failed: " + e.getMessage());
                return;
            }

            albumDir = libraryDir.resolve(sanitize(artist)).resolve(sanitize(albumTitle));
            logger.info("Album directory: {}", albumDir);

            for (int i = 0; i < musicFiles.size(); i++) {
                MusicFile mf = musicFiles.get(i);
                String safeTitle = sanitize(mf.getTitle() != null ? mf.getTitle() : "Track" + (i + 1));
                int disc = 1;
                if (mf.getDiscNumber() != null && !mf.getDiscNumber().trim().isEmpty()) {
                    try { disc = Integer.parseInt(mf.getDiscNumber().trim()); } catch (NumberFormatException ignored) {}
                }
                int track = i + 1;
                if (mf.getTrackNumber() != null && !mf.getTrackNumber().trim().isEmpty()) {
                    try { track = Integer.parseInt(mf.getTrackNumber().trim()); } catch (NumberFormatException ignored) {}
                }
                String fileName = String.format("%02d_%02d_%s.%s", disc, track, safeTitle, getExtension(files.get(i)));
                Path filePath = albumDir.resolve(fileName);
                if (!Files.exists(filePath)) {
                    logger.error("Could not find copied file for track {}: expected {}", i, fileName);
                    onError.accept("File copy mismatch for track " + (i + 1));
                    return;
                }
                mf.setFilePath(filePath.toString());
                logger.debug("Track {} file path set to: {}", i, filePath);
            }

            String hash = torrentMeta.getTorrentHash();
            TorrentMetadata meta = new TorrentMetadata();
            meta.torrentId = torrentMeta.getTorrentId();
            meta.torrentHash = hash;
            meta.title = albumTitle;
            meta.artist = artist;
            meta.album = albumTitle;
            meta.genre = genre;
            meta.year = year;
            meta.tracks = new ArrayList<>();
            for (int i = 0; i < musicFiles.size(); i++) {
                MusicFile mf = musicFiles.get(i);
                TorrentMetadata.TrackInfo ti = new TorrentMetadata.TrackInfo();
                ti.title = mf.getTitle();
                ti.artist = mf.getArtist();
                ti.trackNumber = mf.getTrackNumber();
                ti.discNumber = mf.getDiscNumber();
                ti.duration = mf.getDuration();
                ti.bitrate = mf.getBitrate();
                ti.fileName = albumDir.relativize(Paths.get(mf.getFilePath())).toString();
                meta.tracks.add(ti);
            }

            metadataMap.put(hash, meta);
            saveMetadata(hash, meta);

            for (int i = 0; i < musicFiles.size(); i++) {
                MusicFile mf = musicFiles.get(i);
                mf.setId(torrentMeta.getTorrentHash() + "_" + i);
                mf.setTorrentHash(torrentMeta.getTorrentHash());
                tracks.add(mf);
                logger.debug("Added track: {}", mf.getTitle());
            }

            lastTorrentHash = hash;

            // Enrich metadata from audio tags (duration, bitrate, genre, year)
            enrichIncompleteMetadata();

            // Reload library so tracks are immediately visible
            loadLibraryFromTorrents();

            // Auto-seed the new torrent
            if (torrentManager != null) {
                try {
                    Path torrentFilePath = torrentFilesDir.resolve(hash + ".torrent");
                    if (Files.exists(torrentFilePath)) {
                        boolean isAlbumTorrent = meta.tracks != null && meta.tracks.size() > 1;
                        File savePath = isAlbumTorrent ? albumDir.getParent().toFile() : albumDir.toFile();
                        torrentManager.seedTorrent(torrentFilePath.toFile(), savePath);
                        logger.info("Auto-seeding album upload: {} (hash: {})", albumTitle, hash);
                    }
                } catch (Exception seedEx) {
                    logger.warn("Failed to auto-seed album upload: {}", seedEx.getMessage());
                }
            }

            logger.info("Album upload completed successfully");
            onComplete.run();
        } catch (Exception e) {
            logger.error("Unexpected error in uploadAlbum", e);
            onError.accept("Unexpected error: " + e.getMessage());
        }
    }

    private Integer parseStringToInt(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public MusicFile getTrackById(String id) {
        int underscore = id.indexOf('_');
        if (underscore != -1) {
            String torrentHash = id.substring(0, underscore);
            String indexStr = id.substring(underscore + 1);
            int trackIndex;
            try {
                trackIndex = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                return null;
            }
            for (MusicFile track : tracks) {
                if (torrentHash.equals(track.getTorrentHash())) {
                    if (track.getId() != null && track.getId().equals(id)) {
                        return track;
                    }
                }
            }
            return null;
        } else {
            for (MusicFile track : tracks) {
                if (id.equals(track.getId())) {
                    return track;
                }
            }
            return null;
        }
    }

    public List<MusicFile> search(String query) {
        String lower = query.toLowerCase();
        return tracks.stream()
            .filter(t -> t.getTitle().toLowerCase().contains(lower) ||
                         t.getArtist().toLowerCase().contains(lower))
            .collect(Collectors.toList());
    }

    private MusicFile convertMetadataToMusicFile(MusicMetadataExtractor.MusicMetadata metadata) {
        MusicFile musicFile = new MusicFile();
        musicFile.setId(UUID.randomUUID().toString());
        musicFile.setTitle(metadata.title);
        musicFile.setArtist(metadata.artist);
        musicFile.setAlbum(metadata.album);
        musicFile.setAlbumArtist(metadata.albumArtist);
        musicFile.setGenre(metadata.genre);
        musicFile.setYear(metadata.year);
        musicFile.setBitrate(metadata.bitrate);
        musicFile.setDuration((long) metadata.duration);
        musicFile.setTrackNumber(metadata.trackNumber);
        musicFile.setDiscNumber(metadata.discNumber);
        musicFile.setComposer(metadata.composer);
        musicFile.setComment(metadata.comment);
        musicFile.setFileSize(metadata.fileSize);
        musicFile.setAlbumArtBase64(metadata.albumArtBase64);
        musicFile.setAlbumArtMimeType(metadata.albumArtMimeType);
        return musicFile;
    }

    private String sanitize(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_").trim();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    public List<MusicFile> searchLocal(String keyword) {
        String lower = keyword.toLowerCase();
        return tracks.stream()
                .filter(t -> t.getTitle().toLowerCase().contains(lower) ||
                             t.getArtist().toLowerCase().contains(lower) ||
                             t.getAlbum().toLowerCase().contains(lower) ||
                             (t.getGenre() != null && t.getGenre().toLowerCase().contains(lower)))
                .collect(Collectors.toList());
    }

    public void announceAllKeywords(DHTKeywordManager dht) {
        for (MusicFile track : tracks) {
            String[] keywords = extractKeywords(track);
            for (String kw : keywords) {
                dht.announceKeyword(kw);
            }
        }
    }

    private String[] extractKeywords(MusicFile track) {
    List<String> list = new ArrayList<>();
    
    // Add original fields
    if (track.getArtist() != null) {
        list.add(track.getArtist());
        // Add each word from artist
        for (String word : track.getArtist().toLowerCase().split("\\s+")) {
            if (!word.isEmpty()) list.add(word);
        }
    }
    if (track.getAlbum() != null) {
        list.add(track.getAlbum());
        for (String word : track.getAlbum().toLowerCase().split("\\s+")) {
            if (!word.isEmpty()) list.add(word);
        }
    }
    if (track.getTitle() != null) {
        list.add(track.getTitle());
        for (String word : track.getTitle().toLowerCase().split("\\s+")) {
            if (!word.isEmpty()) list.add(word);
        }
    }
    if (track.getGenre() != null) {
        list.add(track.getGenre());
        for (String word : track.getGenre().toLowerCase().split("\\s+")) {
            if (!word.isEmpty()) list.add(word);
        }
    }
    
    // Remove duplicates (optional, but good)
    return list.stream().distinct().toArray(String[]::new);
}
}