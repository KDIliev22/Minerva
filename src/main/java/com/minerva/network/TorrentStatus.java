package com.minerva.network;

public class TorrentStatus {
    private String name;
    private String progress;
    private String status;
    private String hash;
    private double progressValue;
    
    public TorrentStatus() {
    }
    
    public TorrentStatus(String name, String progress, String status, String hash) {
        this.name = name;
        this.progress = progress;
        this.status = status;
        this.hash = hash;
        
        if (progress != null && progress.contains("%")) {
            try {
                this.progressValue = Double.parseDouble(progress.replace("%", "").trim());
            } catch (NumberFormatException e) {
                this.progressValue = 0.0;
            }
        } else {
            this.progressValue = 0.0;
        }
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getProgress() {
        return progress;
    }
    
    public void setProgress(String progress) {
        this.progress = progress;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash;
    }
    
    public double getProgressValue() {
        return progressValue;
    }
    
    public void setProgressValue(double progressValue) {
        this.progressValue = progressValue;
    }
    
    @Override
    public String toString() {
        return String.format("%s - %s (%s)", name, progress, status);
    }
}