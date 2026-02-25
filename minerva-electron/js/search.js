import { searchInput, contentDiv } from './domElements.js';
import { formatTime } from './utils.js';
import { setQueue } from './queue.js';
import { playTrackById } from './trackPlayback.js';
import { showTrackMenu } from './trackMenu.js';
import { attachLikeButtonHandlers, updateLikeButtons } from './like.js';

export async function performSearch() {
  const query = searchInput.value.trim();
  if (!query) return;
  try {
    const results = await window.minerva.searchTracks(query);
    renderTrackList(results);
  } catch (err) {
    console.error('Search error', err);
  }
}

function renderTrackList(tracks) {
  const trackListHtml = tracks.map((track, idx) => `
    <div class="track-item" data-id="${track.id}">
      <span class="track-number">${idx+1}</span>
      <img class="track-album-art" src="http://127.0.0.1:4567/api/cover/${track.torrentHash}" 
     onerror="this.onerror=null; this.src='default_album.png';" 
     style="width:40px; height:40px; object-fit:cover; border-radius:3px;">
      <span class="track-title">${track.title}</span>
      <span class="track-artist">${track.artist}</span>
      <span class="track-duration">${formatTime(track.duration)}</span>
      <button class="track-play-btn" data-id="${track.id}">▶</button>
      <button class="track-like-btn" data-id="${track.id}">♡</button>
      <button class="track-menu-btn" data-id="${track.id}" style="background:none; border:none; color:#b3b3b3; font-size:18px; cursor:pointer;">⋮</button>
    </div>
  `).join('');

  contentDiv.innerHTML = `
    <div class="search-header">
      <h2>Search Results</h2>
    </div>
    <div class="track-list">${trackListHtml}</div>
  `;

  document.querySelectorAll('.track-play-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const id = e.target.dataset.id;
      setQueue(tracks, tracks.findIndex(t => t.id === id));
      playTrackById(id, false);
    });
  });

  document.querySelectorAll('.track-menu-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const id = e.target.dataset.id;
      const track = tracks.find(t => t.id === id);
      showTrackMenu(e.clientX, e.clientY, track);
    });
  });

  attachLikeButtonHandlers(tracks);
  updateLikeButtons(tracks);
}