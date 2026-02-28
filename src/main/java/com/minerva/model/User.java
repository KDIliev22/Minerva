package com.minerva.model;

public class User {
    private String username;
    private String userId;
    private String ipAddress;
    private int port;
    private long sharedFiles;
    private long downloadedFiles;
    private double shareRatio;
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public long getSharedFiles() { return sharedFiles; }
    public void setSharedFiles(long sharedFiles) { this.sharedFiles = sharedFiles; }
    
    public long getDownloadedFiles() { return downloadedFiles; }
    public void setDownloadedFiles(long downloadedFiles) { this.downloadedFiles = downloadedFiles; }
    
    public double getShareRatio() { return shareRatio; }
    public void setShareRatio(double shareRatio) { this.shareRatio = shareRatio; }
}