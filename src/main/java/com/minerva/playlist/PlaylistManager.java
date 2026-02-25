package com.minerva.playlist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class PlaylistManager {
    private final Path jsonFile;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<Playlist> playlists = new ArrayList<>();

    private static final DateTimeFormatter SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Playlist {
        @JsonProperty
        private int id;

        @JsonProperty
        private String name;

        @JsonProperty
        private String description;

        @JsonProperty
        private String iconPath;

        @JsonProperty
        private LocalDateTime createdAt;

        @JsonProperty("isSystem")
        private boolean isSystem;

        @JsonProperty
        private List<TrackEntry> tracks = new ArrayList<>();

        public Playlist() {}

        public Playlist(int id, String name, String description, boolean isSystem) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.isSystem = isSystem;
            this.createdAt = LocalDateTime.now();
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getIconPath() { return iconPath; }
        public void setIconPath(String iconPath) { this.iconPath = iconPath; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public boolean isSystem() { return isSystem; }
        public void setSystem(boolean system) { isSystem = system; }
        public List<TrackEntry> getTracks() { return tracks; }
        public void setTracks(List<TrackEntry> tracks) { this.tracks = tracks; }

        public String getCreatedAtString() {
            return createdAt.format(SQLITE_DATETIME);
        }
    }

    private static class TrackEntry {
        @JsonProperty("trackId")
        private String trackId;

        @JsonProperty
        private int position;

        @JsonProperty
        private LocalDateTime addedAt;

        public TrackEntry() {}

        public TrackEntry(String trackId, int position) {
            this.trackId = trackId;
            this.position = position;
            this.addedAt = LocalDateTime.now();
        }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
        public LocalDateTime getAddedAt() { return addedAt; }
        public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
    }

    public PlaylistManager(String dataDir) {
        Path dir = Paths.get(dataDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory", e);
        }
        this.jsonFile = dir.resolve("playlists.json");
        this.mapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(SQLITE_DATETIME));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(SQLITE_DATETIME));
        mapper.registerModule(module);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        load();
        ensureLikedSongsPlaylist();
    }

    private void load() {
        lock.writeLock().lock();
        try {
            if (!Files.exists(jsonFile)) {
                playlists.clear();
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(jsonFile)) {
                List<Playlist> loaded = mapper.readValue(reader, new TypeReference<List<Playlist>>() {});
                playlists.clear();
                if (loaded != null) {
                    playlists.addAll(loaded);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load playlists from JSON", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void save() {
        lock.writeLock().lock();
        try {
            Path tempFile = jsonFile.resolveSibling(jsonFile.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                mapper.writeValue(writer, playlists);
            }
            Files.move(tempFile, jsonFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save playlists", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureLikedSongsPlaylist() {
        lock.writeLock().lock();
        try {
            boolean exists = playlists.stream().anyMatch(p -> p.isSystem && "Liked Songs".equals(p.name));
            if (!exists) {
                int newId = generateId();
                Playlist liked = new Playlist(newId, "Liked Songs", "Your favorite tracks", true);
                playlists.add(liked);
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private int generateId() {
        return playlists.stream().mapToInt(p -> p.id).max().orElse(0) + 1;
    }

    public int createPlaylist(String name, String description) {
        lock.writeLock().lock();
        try {
            int newId = generateId();
            Playlist p = new Playlist(newId, name, description, false);
            playlists.add(p);
            save();
            return newId;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Map<String, Object>> getAllPlaylists() {
        lock.readLock().lock();
        try {
            List<Playlist> sorted = new ArrayList<>(playlists);
            sorted.sort((p1, p2) -> {
                if (p1.isSystem != p2.isSystem) {
                    return p2.isSystem ? 1 : -1;
                }
                return p2.createdAt.compareTo(p1.createdAt);
            });

            List<Map<String, Object>> result = new ArrayList<>();
            for (Playlist p : sorted) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", p.id);
                map.put("name", p.name);
                map.put("description", p.description);
                map.put("iconPath", p.iconPath);
                map.put("createdAt", p.getCreatedAtString());
                map.put("isSystem", p.isSystem);
                map.put("trackCount", p.tracks.size());
                result.add(map);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Object> getPlaylist(int id) {
        lock.readLock().lock();
        try {
            for (Playlist p : playlists) {
                if (p.id == id) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", p.id);
                    map.put("name", p.name);
                    map.put("description", p.description);
                    map.put("iconPath", p.iconPath);
                    map.put("createdAt", p.getCreatedAtString());
                    map.put("isSystem", p.isSystem);
                    List<Map<String, String>> tracksList = new ArrayList<>();
                    p.tracks.sort(Comparator.comparingInt(t -> t.position));
                    for (TrackEntry te : p.tracks) {
                        Map<String, String> tm = new LinkedHashMap<>();
                        tm.put("id", te.trackId);
                        tm.put("position", String.valueOf(te.position));
                        tracksList.add(tm);
                    }
                    map.put("tracks", tracksList);
                    return map;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updatePlaylist(int id, String name, String description, String iconPath) {
        lock.writeLock().lock();
        try {
            for (Playlist p : playlists) {
                if (p.id == id && !p.isSystem) {
                    p.name = name;
                    p.description = description;
                    p.iconPath = iconPath;
                    save();
                    return;
                }
            }
            throw new RuntimeException("Playlist not found or is system");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deletePlaylist(int id) {
        lock.writeLock().lock();
        try {
            boolean removed = playlists.removeIf(p -> p.id == id && !p.isSystem);
            if (!removed) {
                throw new RuntimeException("Cannot delete system playlist or playlist not found.");
            }
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addTrackToPlaylist(int playlistId, String trackId) {
        lock.writeLock().lock();
        try {
            for (Playlist p : playlists) {
                if (p.id == playlistId) {
                    boolean exists = p.tracks.stream().anyMatch(te -> te.trackId.equals(trackId));
                    if (!exists) {
                        int maxPos = p.tracks.stream().mapToInt(te -> te.position).max().orElse(0);
                        TrackEntry te = new TrackEntry(trackId, maxPos + 1);
                        p.tracks.add(te);
                        save();
                    }
                    return;
                }
            }
            throw new RuntimeException("Playlist not found");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeTrackFromPlaylist(int playlistId, String trackId) {
        lock.writeLock().lock();
        try {
            for (Playlist p : playlists) {
                if (p.id == playlistId) {
                    boolean removed = p.tracks.removeIf(te -> te.trackId.equals(trackId));
                    if (removed) {
                        save();
                    }
                    return;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void reorderPlaylistTracks(int playlistId, List<String> trackIds) {
        lock.writeLock().lock();
        try {
            for (Playlist p : playlists) {
                if (p.id == playlistId) {
                    List<TrackEntry> newTracks = new ArrayList<>();
                    for (int i = 0; i < trackIds.size(); i++) {
                        String tid = trackIds.get(i);
                        TrackEntry existing = p.tracks.stream()
                                .filter(te -> te.trackId.equals(tid))
                                .findFirst().orElse(null);
                        if (existing != null) {
                            TrackEntry te = new TrackEntry(tid, i);
                            te.addedAt = existing.addedAt;
                            newTracks.add(te);
                        } else {
                            newTracks.add(new TrackEntry(tid, i));
                        }
                    }
                    p.tracks = newTracks;
                    save();
                    return;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}