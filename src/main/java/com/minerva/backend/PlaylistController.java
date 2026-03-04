package com.minerva.backend;

import com.minerva.playlist.PlaylistManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Handles all playlist CRUD endpoints and track management within playlists.
 */
public class PlaylistController {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistController.class);
    private final PlaylistManager playlistManager;
    private final ObjectMapper objectMapper;

    public PlaylistController(PlaylistManager playlistManager, ObjectMapper objectMapper) {
        this.playlistManager = playlistManager;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public void register(Javalin app) {
        app.get("/api/playlists", ctx -> {
            try {
                ctx.json(playlistManager.getAllPlaylists());
            } catch (Exception e) {
                logger.error("Error loading playlists", e);
                ctx.status(500).result("Error loading playlists");
            }
        });

        app.post("/api/playlists", ctx -> {
            try {
                Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
                String name = (String) body.get("name");
                String description = (String) body.getOrDefault("description", "");
                int id = playlistManager.createPlaylist(name, description);
                ctx.status(201).json(Map.of("id", id));
            } catch (Exception e) {
                logger.error("Error creating playlist", e);
                ctx.status(500).result("Error creating playlist");
            }
        });

        app.get("/api/playlists/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                Map<String, Object> playlist = playlistManager.getPlaylist(id);
                if (playlist == null) {
                    ctx.status(404).result("Playlist not found");
                } else {
                    ctx.json(playlist);
                }
            } catch (Exception e) {
                logger.error("Error loading playlist", e);
                ctx.status(500).result("Error loading playlist");
            }
        });

        app.put("/api/playlists/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
                String name = (String) body.get("name");
                String description = (String) body.getOrDefault("description", "");
                String iconPath = (String) body.get("iconPath");
                playlistManager.updatePlaylist(id, name, description, iconPath);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                logger.error("Error updating playlist", e);
                ctx.status(500).result("Error updating playlist");
            }
        });

        app.delete("/api/playlists/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                playlistManager.deletePlaylist(id);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                logger.error("Error deleting playlist", e);
                ctx.status(500).result(e.getMessage());
            }
        });

        app.post("/api/playlists/{id}/tracks", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
                String trackId = (String) body.get("trackId");
                playlistManager.addTrackToPlaylist(id, trackId);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                logger.error("Error adding track to playlist", e);
                ctx.status(500).result("Error adding track to playlist");
            }
        });

        app.delete("/api/playlists/{id}/tracks/{trackId}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                String trackId = ctx.pathParam("trackId");
                playlistManager.removeTrackFromPlaylist(id, trackId);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                logger.error("Error removing track from playlist", e);
                ctx.status(500).result("Error removing track from playlist");
            }
        });

        app.put("/api/playlists/{id}/tracks/reorder", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                List<String> trackIds = objectMapper.readValue(ctx.body(), List.class);
                playlistManager.reorderPlaylistTracks(id, trackIds);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                logger.error("Error reordering tracks", e);
                ctx.status(500).result("Error reordering tracks");
            }
        });
    }
}
