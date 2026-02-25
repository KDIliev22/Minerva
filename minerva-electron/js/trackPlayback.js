// trackPlayback.js (updated sections)

import { audio, playPauseBtn, nowPlayingImg, nowPlayingTitle, nowPlayingArtist } from './domElements.js';
import { allTracks } from './state.js';
import { setQueue, queueTracks, queueIndex } from './queue.js'; // we need queueIndex and queueTracks

export let currentTrackId = null;
export let isPlaying = false;

// Reset player to "Not Playing" state
function clearNowPlaying() {
  nowPlayingTitle.textContent = 'Not Playing';
  nowPlayingArtist.textContent = '—';
  if (nowPlayingImg) {
    // Hide the image (or set to a transparent pixel)
    nowPlayingImg.style.display = 'none';
    // Optionally set src to empty to stop any request
    nowPlayingImg.src = '';
  }
  currentTrackId = null;
  isPlaying = false;
  playPauseBtn.textContent = '▶';
}

export function resetPlayer() {
  nowPlayingTitle.textContent = 'Not Playing';
  nowPlayingArtist.textContent = '—';
  if (nowPlayingImg) {
    nowPlayingImg.style.display = 'none';
    nowPlayingImg.src = '';
  }
  currentTrackId = null;
  isPlaying = false;
  playPauseBtn.textContent = '▶';
}

export async function playTrackById(id, setQueueFromAlbum = false) {
  try {
    const streamUrl = await window.minerva.playTrack(id);
    console.log('Playing track ID:', id, 'URL:', streamUrl);
    audio.src = streamUrl;
    await audio.play();
    isPlaying = true;
    playPauseBtn.textContent = '⏸';
    currentTrackId = id;

    const track = allTracks.find(t => t.id === id);
    if (track) {
      nowPlayingTitle.textContent = track.title || 'Unknown';
      nowPlayingArtist.textContent = track.artist || '—';

      // Show and update album art
      if (nowPlayingImg) {
        nowPlayingImg.style.display = 'block'; // or 'inline-block' depending on layout
        nowPlayingImg.src = `http://127.0.0.1:4567/api/cover/${track.torrentHash}`;
        nowPlayingImg.onerror = () => {
          nowPlayingImg.src = 'default_album.png'; // fallback
        };
      }
    }

    if (setQueueFromAlbum && track) {
      const albumTracks = allTracks.filter(t => t.artist === track.artist && t.album === track.album);
      const idx = albumTracks.findIndex(t => t.id === id);
      if (idx !== -1) {
        setQueue(albumTracks, idx);
      }
    }
  } catch (err) {
    console.error('Playback failed:', err);
    alert('Could not play track. Check console for details.');
  }
}

// Modify playNext in queue.js to call clearNowPlaying when queue ends