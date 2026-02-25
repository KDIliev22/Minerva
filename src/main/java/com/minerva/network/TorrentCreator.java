package com.minerva.network;

import com.minerva.model.Album;
import com.minerva.model.MusicFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.turn.ttorrent.common.Torrent;
import java.net.URI;
import java.io.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TorrentCreator {
    private static final Logger logger = LoggerFactory.getLogger(TorrentCreator.class);
    
    private final Path torrentFilesDir;
    private final Path libraryDir;
    
    public TorrentCreator(Path torrentFilesDir, Path libraryDir) {
        this.torrentFilesDir = torrentFilesDir;
        this.libraryDir = libraryDir;
        createDirectories();
    }

    private List<String> getTrackerUrls() {
        return Arrays.asList(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://tracker.coppersurfer.tk:6969/announce",
            "udp://tracker.leechers-paradise.org:6969/announce",
            "udp://9.rarbg.to:2710/announce",
            "udp://p4p.arenabg.com:1337/announce",
            "http://tracker.opentrackr.org:1337/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
            "udp://open.demonii.com:1337/announce"
        );
    }

    private Path findContentInLibrary(TorrentMetadata metadata) throws IOException {
        String artist = sanitizeFileName(metadata.getArtist());
        String album = sanitizeFileName(metadata.getAlbum());
        Path artistDir = libraryDir.resolve(artist);
        if (!Files.exists(artistDir)) return null;
        Path albumDir = artistDir.resolve(album);
        return Files.exists(albumDir) ? albumDir : null;
    }

    private void createTorrentFile(TorrentMetadata metadata, Path outputPath, List<File> albumFiles) throws IOException {
        logger.info("Creating torrent file: {}", outputPath);
        try {
            List<String> trackers = getTrackerUrls();
            if (trackers.isEmpty()) throw new IOException("No trackers configured");
            URI trackerURI = URI.create(trackers.get(0));

            Torrent torrent;
            if (metadata.getTorrentType() == TorrentMetadata.TorrentType.SINGLE) {
                Path contentDir = findContentInLibrary(metadata);
                Path filePath = contentDir.resolve(metadata.getTrackInfo().getFileName());
                logger.info("Single torrent source file: {}", filePath);
                torrent = Torrent.create(filePath.toFile(), trackerURI, "Minerva");
            } else {
                Path contentDir = findContentInLibrary(metadata);
                List<File> normalizedFiles = new ArrayList<>();
                File normalizedParent = contentDir.toRealPath().toFile();
                for (File f : albumFiles) {
                    File normalized = f.toPath().toRealPath().toFile();
                    normalizedFiles.add(normalized);
                }
                logger.info("Multi-file torrent parent: {}", normalizedParent);
                logger.info("Files to include: {}", normalizedFiles.stream().map(File::getName).collect(Collectors.joining(", ")));
                torrent = Torrent.create(normalizedParent, normalizedFiles, trackerURI, "Minerva");
            }

            try (OutputStream os = new FileOutputStream(outputPath.toFile())) {
                torrent.save(os);
            }
            logger.info("Torrent file created successfully");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Torrent creation interrupted", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Hash algorithm unavailable", e);
        }
    }

    private void createDirectories() {
        try {
            Files.createDirectories(torrentFilesDir);
            Files.createDirectories(libraryDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directories", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    public TorrentMetadata createSingleTorrent(MusicFile musicFile, File audioFile,
                                           String uploaderNodeId) throws IOException {
        logger.info("Creating single torrent for file: {}", audioFile.getName());
        TorrentMetadata metadata = new TorrentMetadata();

        String tempId = UUID.randomUUID().toString();
        metadata.setTorrentId(tempId);
        metadata.setTorrentType(TorrentMetadata.TorrentType.SINGLE);
        metadata.setArtist(musicFile.getArtist());
        metadata.setTitle(musicFile.getTitle());
        metadata.setAlbum(musicFile.getAlbum());
        metadata.setYear(musicFile.getYear());
        metadata.setGenre(musicFile.getGenre());
        metadata.setBitrate(musicFile.getBitrate());
        metadata.setFileStructure(TorrentMetadata.FileStructure.SINGLE_TRACK);
        metadata.setUploaderId(uploaderNodeId);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setFileSize(audioFile.length());

        TorrentMetadata.TrackInfo trackInfo = new TorrentMetadata.TrackInfo();
        trackInfo.setTitle(musicFile.getTitle());
        trackInfo.setArtist(musicFile.getArtist());
        trackInfo.setTrackNumber(musicFile.getTrackNumber());
        trackInfo.setDiscNumber(musicFile.getDiscNumber());
        trackInfo.setDuration(musicFile.getDuration());
        trackInfo.setBitrate(musicFile.getBitrate());
        trackInfo.setFileName(generateFileName(musicFile, getFileExtension(audioFile)));
        trackInfo.setFileHash(generateFileHash(audioFile));
        metadata.setTrackInfo(trackInfo);

        byte[] fileContent = Files.readAllBytes(audioFile.toPath());
        metadata.setTorrentHash(generateSha1Hash(fileContent));

        logger.info("Copying file to library");
        copyToLibrary(Collections.singletonList(audioFile), metadata);

        Path tempTorrentPath = torrentFilesDir.resolve(tempId + ".torrent");
        createTorrentFile(metadata, tempTorrentPath, null);
        Torrent torrent;
        try {
            torrent = Torrent.load(tempTorrentPath.toFile());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 algorithm not available", e);
        }
        String infoHash = bytesToHex(torrent.getInfoHash());
        metadata.setTorrentHash(infoHash);

        Path finalTorrentPath = torrentFilesDir.resolve(infoHash + ".torrent");
        Files.move(tempTorrentPath, finalTorrentPath, StandardCopyOption.REPLACE_EXISTING);
        metadata.setTorrentId(infoHash);

        logger.info("Single torrent created, hash: {}", infoHash);
        return metadata;
    }
    
    public TorrentMetadata createAlbumTorrent(Album album, List<File> audioFiles,
                                          String uploaderNodeId) throws IOException {
        logger.info("Creating album torrent with {} files", audioFiles.size());
        String tempId = UUID.randomUUID().toString();
        TorrentMetadata metadata = new TorrentMetadata();
        metadata.setTorrentId(tempId);
        metadata.setTorrentType(TorrentMetadata.TorrentType.ALBUM);
        metadata.setArtist(album.getArtist());
        metadata.setTitle(album.getTitle());
        metadata.setAlbum(album.getTitle());
        metadata.setYear(album.getYear());
        metadata.setGenre(album.getGenre());
        metadata.setFileStructure(TorrentMetadata.FileStructure.ALBUM_DIRECTORY);
        metadata.setUploaderId(uploaderNodeId);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setFileSize(calculateTotalSize(audioFiles));

        List<TorrentMetadata.TrackInfo> tracks = new ArrayList<>();
        for (int i = 0; i < audioFiles.size(); i++) {
            File audioFile = audioFiles.get(i);
            MusicFile musicFile = album.getTracks().get(i);

            TorrentMetadata.TrackInfo trackInfo = new TorrentMetadata.TrackInfo();
            trackInfo.setTitle(musicFile.getTitle());
            trackInfo.setArtist(musicFile.getArtist());
            trackInfo.setTrackNumber(musicFile.getTrackNumber());
            trackInfo.setDiscNumber(musicFile.getDiscNumber());
            trackInfo.setDuration(musicFile.getDuration());
            trackInfo.setBitrate(musicFile.getBitrate());
            trackInfo.setFileName(generateFileName(musicFile, getFileExtension(audioFile)));
            trackInfo.setFileHash(generateFileHash(audioFile));

            tracks.add(trackInfo);
        }
        metadata.setTracks(tracks);

        byte[] combined = String.join("", tracks.stream()
                .map(TorrentMetadata.TrackInfo::getFileHash)
                .collect(Collectors.toList()))
                .getBytes(StandardCharsets.UTF_8);
        metadata.setTorrentHash(generateSha1Hash(combined));

        logger.info("Copying album files to library");
        copyToLibrary(audioFiles, metadata);

        Path albumDir = findContentInLibrary(metadata);
        if (albumDir == null) {
            throw new IOException("Album directory not found after copy");
        }

        List<File> libraryFiles = new ArrayList<>();
        Set<String> audioExtensions = new HashSet<>(Arrays.asList("mp3", "flac", "wav", "m4a", "ogg"));
        Set<String> coverNames = new HashSet<>(Arrays.asList("cover.jpg", "cover.png"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(albumDir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String fileName = entry.getFileName().toString();
                    String ext = getFileExtension(entry.toFile()).toLowerCase();
                    if (audioExtensions.contains(ext) || coverNames.contains(fileName.toLowerCase())) {
                        libraryFiles.add(entry.toFile());
                    }
                }
            }
        }

        if (libraryFiles.isEmpty()) {
            throw new IOException("No audio files found in album directory: " + albumDir);
        }

        libraryFiles.sort(Comparator.comparing(File::getName));
        logger.info("Found {} audio files for album torrent: {}", libraryFiles.size(), albumDir);

        Path tempTorrentPath = torrentFilesDir.resolve(tempId + ".torrent");
        createTorrentFile(metadata, tempTorrentPath, libraryFiles);

        Torrent torrent;
        try {
            torrent = Torrent.load(tempTorrentPath.toFile());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 algorithm not available", e);
        }
        String infoHash = bytesToHex(torrent.getInfoHash());
        metadata.setTorrentHash(infoHash);

        Path finalTorrentPath = torrentFilesDir.resolve(infoHash + ".torrent");
        Files.move(tempTorrentPath, finalTorrentPath, StandardCopyOption.REPLACE_EXISTING);
        metadata.setTorrentId(infoHash);

        logger.info("Album torrent created, hash: {}", infoHash);
        return metadata;
    }

    private String generateSha1Hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }
    
    private void copyToLibrary(List<File> sourceFiles, TorrentMetadata metadata) 
            throws IOException {
        
        Path artistDir = libraryDir.resolve(sanitizeFileName(metadata.getArtist()));
        Files.createDirectories(artistDir);
        
        Path albumDir = artistDir.resolve(sanitizeFileName(metadata.getAlbum()));
        Files.createDirectories(albumDir);
        
        if (metadata.getTorrentType() == TorrentMetadata.TorrentType.SINGLE) {
            File sourceFile = sourceFiles.get(0);
            TorrentMetadata.TrackInfo track = metadata.getTrackInfo();
            
            Integer trackNumber = getTrackNumberInt(track);
            Integer discNumber = getDiscNumberInt(track);
            
            String fileName = String.format("%02d_%s.%s",
                trackNumber != null ? trackNumber : 1,
                sanitizeFileName(track.getTitle()),
                getFileExtension(sourceFile));
            
            Path destFile = albumDir.resolve(fileName);
            Files.copy(sourceFile.toPath(), destFile, StandardCopyOption.REPLACE_EXISTING);
            setFilePermissions(destFile);
            logger.debug("Copied single file to: {}", destFile);
            
        } else {
            for (int i = 0; i < sourceFiles.size(); i++) {
                File sourceFile = sourceFiles.get(i);
                TorrentMetadata.TrackInfo track = metadata.getTracks().get(i);
                
                Integer trackNumber = getTrackNumberInt(track);
                Integer discNumber = getDiscNumberInt(track);
                
                String fileName = String.format("%02d_%02d_%s.%s",
                    discNumber != null ? discNumber : 1,
                    trackNumber != null ? trackNumber : i + 1,
                    sanitizeFileName(track.getTitle()),
                    getFileExtension(sourceFile));
                
                Path destFile = albumDir.resolve(fileName);
                Files.copy(sourceFile.toPath(), destFile, StandardCopyOption.REPLACE_EXISTING);
                setFilePermissions(destFile);
                logger.debug("Copied album file to: {}", destFile);
            }
        }
        
        logger.info("Copied {} files to library: {}/{}", 
            sourceFiles.size(), metadata.getArtist(), metadata.getAlbum());
    }
    
    private Integer getTrackNumberInt(TorrentMetadata.TrackInfo track) {
        return parseStringToInt(track.getTrackNumber());
    }
    
    private Integer getDiscNumberInt(TorrentMetadata.TrackInfo track) {
        return parseStringToInt(track.getDiscNumber());
    }
    
    private Integer parseStringToInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private void setFilePermissions(Path filePath) throws IOException {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(filePath, perms);
        } catch (UnsupportedOperationException e) {
            File file = filePath.toFile();
            file.setReadable(true, false);
            file.setWritable(true, true);
        }
    }
    
    private String generateFileName(MusicFile musicFile, String extension) {
        Integer discNumber = parseStringToInt(musicFile.getDiscNumber());
        Integer trackNumber = parseStringToInt(musicFile.getTrackNumber());
            
        if (discNumber != null && discNumber > 1) {
            return String.format("%02d_%02d_%s.%s",
                discNumber,
                trackNumber != null ? trackNumber : 1,
                sanitizeFileName(musicFile.getTitle()),
                extension);
        } else {
            return String.format("%02d_%s.%s",
                trackNumber != null ? trackNumber : 1,
                sanitizeFileName(musicFile.getTitle()),
                extension);
        }
    }
    
    private String generateFileHash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (InputStream is = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }
    
    private long calculateTotalSize(List<File> files) {
        return files.stream()
            .mapToLong(File::length)
            .sum();
    }
    
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1).toLowerCase() : "";
    }
    
    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .trim();
    }
}