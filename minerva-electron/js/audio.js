// js/audio.js
import { audio, playPauseBtn, progressFill, currentTimeSpan, totalTimeSpan, volumeSlider, nowPlayingImg, nowPlayingTitle, nowPlayingArtist } from './domElements.js';
import { formatTime } from './utils.js';
import { playNext } from './queue.js';
import { allTracks } from './state.js';  // ✅ Correct import

export let isPlaying = false;
export let currentTrackId = null;
export let expanded = false;

export function initAudio() {
  audio.onerror = (e) => {
    console.error('Audio element error:', e);
    if (audio.error) {
      alert('Playback error: ' + audio.error.message);
    }
  };

  audio.addEventListener('loadedmetadata', () => {
    console.log('Audio duration:', audio.duration);
    if (audio.duration && isFinite(audio.duration)) {
      totalTimeSpan.textContent = formatTime(audio.duration);
    } else {
      totalTimeSpan.textContent = '0:00';
    }
  });

  audio.addEventListener('timeupdate', () => {
    if (audio.duration && isFinite(audio.duration)) {
      const percent = (audio.currentTime / audio.duration) * 100;
      progressFill.style.width = percent + '%';
      currentTimeSpan.textContent = formatTime(audio.currentTime);
    }
  });

  audio.addEventListener('ended', () => {
    playNext();
  });

  volumeSlider.addEventListener('input', (e) => {
    audio.volume = e.target.value / 100;
  });

  document.getElementById('progressBar').addEventListener('click', seek);
}

export function togglePlayback() {
  if (audio.paused) {
    audio.play();
    playPauseBtn.textContent = '⏸';
    isPlaying = true;
  } else {
    audio.pause();
    playPauseBtn.textContent = '▶';
    isPlaying = false;
  }
}

export function seek(e) {
  const rect = e.currentTarget.getBoundingClientRect();
  const clickX = e.clientX - rect.left;
  const width = rect.width;
  const percent = Math.max(0, Math.min(1, clickX / width));
  if (audio.duration && isFinite(audio.duration)) {
    audio.currentTime = percent * audio.duration;
  }
}

export function toggleExpand() {
  expanded = !expanded;
  if (expanded) {
    nowPlayingImg.classList.add('expanded');
    document.querySelector('.now-playing').classList.add('expanded-text');
  } else {
    nowPlayingImg.classList.remove('expanded');
    document.querySelector('.now-playing').classList.remove('expanded-text');
  }
}

// Add these functions
async function sendNowPlaying(trackId) {
  try {
    await fetch('http://127.0.0.1:4567/api/lastfm/now-playing', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ trackId })
    });
  } catch (err) {
    console.log('Now playing update failed (maybe not connected)');
  }
}

async function sendScrobble(trackId) {
  try {
    await fetch('http://127.0.0.1:4567/api/lastfm/scrobble', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        trackId,
        timestamp: Math.floor(Date.now() / 1000) // current Unix time
      })
    });
  } catch (err) {
    console.log('Scrobble failed (maybe not connected)');
  }
}

audio.addEventListener('play', () => {
  if (currentTrackId) sendNowPlaying(currentTrackId);
});

audio.addEventListener('ended', () => {
  if (currentTrackId) sendScrobble(currentTrackId);
});