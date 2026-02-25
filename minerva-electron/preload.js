const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('minerva', {
  // Existing methods
  playTrack: (id) => ipcRenderer.invoke('play-track', id),
  downloadTrack: (id) => ipcRenderer.invoke('download-track', id),
  searchTracks: (query) => ipcRenderer.invoke('search-tracks', query),
  fetchTracks: () => ipcRenderer.invoke('fetch-tracks'),
  fetchTorrent: (hash, metadata) => ipcRenderer.invoke('fetch-torrent', hash, metadata),
  fetchLogs: (lines) => ipcRenderer.invoke('fetch-logs', lines),
});