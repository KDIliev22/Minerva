import { audio, playPauseBtn, volumeControl, nowPlayingImg, searchBtn, searchInput, uploadBtn } from './domElements.js';
import { initAudio, togglePlayback, toggleExpand } from './audio.js';
import { playPrevious, playNext, showQueue } from './queue.js';
import { initNavigation } from './navigation.js';
import { loadPlaylists, createPlaylist } from './playlist.js';
import { loadHome } from './home.js';
import { performSearch } from './search.js';
import { openUploadModal } from './upload.js';
import { setAllTracks } from './state.js';
import { resetPlayer } from './trackPlayback.js';

  async function fetchAllTracks(retries = 5, delay = 2000) {
  for (let i = 0; i < retries; i++) {
    try {
      console.log(`Fetch attempt ${i + 1}...`);
      const tracks = await window.minerva.fetchTracks();
      if (tracks) {
        setAllTracks(tracks);
        return tracks;
      }
    } catch (err) {
      console.error(`Attempt ${i + 1} failed:`, err);
    }
    await new Promise(resolve => setTimeout(resolve, delay));
  }
  console.error('Failed to fetch tracks after multiple retries');
  return [];
} 

async function loadPlaylistsWithRetry(retries = 5, delay = 1000) {
  for (let i = 0; i < retries; i++) {
    try {
      await loadPlaylists();
      return;
    } catch (err) {
      console.log(`Playlist load attempt ${i + 1} failed, retrying...`);
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
}

function addCreatePlaylistButton() {
  const playlistsHeader = document.querySelector('.playlists h3');
  if (!playlistsHeader) return;
  if (document.getElementById('createPlaylistBtn')) return;

  const container = document.createElement('div');
  container.style.display = 'flex';
  container.style.alignItems = 'center';
  container.style.justifyContent = 'space-between';
  container.style.marginBottom = '10px';

  playlistsHeader.parentNode.insertBefore(container, playlistsHeader);
  container.appendChild(playlistsHeader);

  const createBtn = document.createElement('button');
  createBtn.id = 'createPlaylistBtn';
  createBtn.textContent = '+';
  createBtn.title = 'Create new playlist';
  createBtn.style.background = 'transparent';
  createBtn.style.border = 'none';
  createBtn.style.color = '#b3b3b3';
  createBtn.style.fontSize = '20px';
  createBtn.style.cursor = 'pointer';
  createBtn.style.padding = '0 8px';
  createBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    createPlaylist();
  });
  container.appendChild(createBtn);
}

async function init() {
  
  const queueBtn = document.createElement('button');
  queueBtn.id = 'queueBtn';
  queueBtn.textContent = '📋';
  queueBtn.style.background = 'transparent';
  queueBtn.style.border = 'none';
  queueBtn.style.color = 'white';
  queueBtn.style.fontSize = '20px';
  queueBtn.style.cursor = 'pointer';
  queueBtn.style.marginRight = '10px';
  volumeControl.insertBefore(queueBtn, volumeControl.firstChild);
  queueBtn.addEventListener('click', showQueue);

  addCreatePlaylistButton();

  await fetchAllTracks();
  await loadPlaylistsWithRetry();
  await loadHome();

  searchBtn.addEventListener('click', performSearch);
  searchInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') performSearch();
  });

  if (uploadBtn) {
    uploadBtn.addEventListener('click', openUploadModal);
  }

  playPauseBtn.addEventListener('click', togglePlayback);
  document.getElementById('prevBtn').addEventListener('click', playPrevious);
  document.getElementById('nextBtn').addEventListener('click', playNext);

  if (nowPlayingImg) {
    nowPlayingImg.addEventListener('click', toggleExpand);
  }

  initAudio();
  initNavigation();
  resetPlayer();
}

init();