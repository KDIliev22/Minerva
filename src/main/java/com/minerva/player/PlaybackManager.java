package com.minerva.player;

import javafx.application.Platform;
import java.io.File;
import java.util.function.Consumer;

public class PlaybackManager {
    private final AudioPlayer audioPlayer = new AudioPlayer();
    private String currentTitle;
    private String currentArtist;

    private Consumer<String> onNowPlayingChange;
    private Runnable onPlayStateChange;
    private Consumer<String> onError;

    public void setCallbacks(Consumer<String> onNowPlayingChange,
                             Runnable onPlayStateChange,
                             Consumer<String> onError) {
        this.onNowPlayingChange = onNowPlayingChange;
        this.onPlayStateChange = onPlayStateChange;
        this.onError = onError;
    }

    public void play(File file, String artist, String title) {
        audioPlayer.play(file);
        currentTitle = title;
        currentArtist = artist;
        if (onNowPlayingChange != null) {
            onNowPlayingChange.accept(artist + " - " + title);
        }
        if (onPlayStateChange != null) {
            onPlayStateChange.run();
        }
    }

    public void stop() {
        audioPlayer.stop();
        if (onPlayStateChange != null) {
            onPlayStateChange.run();
        }
    }

    public boolean isPlaying() {
        return audioPlayer.isPlaying();
    }
}