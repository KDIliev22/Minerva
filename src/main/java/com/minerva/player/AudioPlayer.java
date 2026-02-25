package com.minerva.player;

import javazoom.jl.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;

public class AudioPlayer {
    private static final Logger logger = LoggerFactory.getLogger(AudioPlayer.class);
    private Player currentPlayer;
    private Thread playerThread;
    private boolean isPlaying = false;

    public void play(File file) {
        stop();

        playerThread = new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(file)) {
                currentPlayer = new Player(fis);
                isPlaying = true;
                currentPlayer.play();
            } catch (Exception e) {
                logger.error("Playback error", e);
            } finally {
                isPlaying = false;
                currentPlayer = null;
            }
        });
        playerThread.setDaemon(true);
        playerThread.start();
    }

    public void stop() {
        if (currentPlayer != null) {
            currentPlayer.close();
            currentPlayer = null;
        }
        if (playerThread != null && playerThread.isAlive()) {
            playerThread.interrupt();
            try {
                playerThread.join(1000);
            } catch (InterruptedException ignored) {}
        }
        isPlaying = false;
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}