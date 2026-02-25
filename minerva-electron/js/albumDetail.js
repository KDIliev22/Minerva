// albumDetail.js
import { contentDiv } from './domElements.js';
import { allTracks, refreshAllTracks } from './state.js';
import { formatTime } from './utils.js';
import { setQueue } from './queue.js';
import { playTrackById } from './trackPlayback.js';
import { showTrackMenu } from './trackMenu.js';
import { attachLikeButtonHandlers, updateLikeButtons } from './like.js';

export async function showAlbum(artist, album) {
  try {
    let albumTracks = allTracks.filter(t => t.artist === artist && t.album === album);
    if (albumTracks.length === 0) {
      // allTracks may be stale — re-fetch and try again
      await refreshAllTracks();
      albumTracks = allTracks.filter(t => t.artist === artist && t.album === album);
    }
    if (albumTracks.length === 0) {
      alert('No tracks found for this album');
      return;
    }
    renderAlbumDetail(artist, album, albumTracks);
    setQueue(albumTracks);
  } catch (err) {
    console.error('Failed to load album tracks', err);
    alert('Could not load album details');
  }
}

function renderAlbumDetail(artist, album, tracks) {
  const firstTrack = tracks[0];
  const coverUrl = `http://127.0.0.1:4567/api/cover/${firstTrack.torrentHash}`;

  const trackListHtml = tracks.map((track, idx) => `
    <div class="track-item" data-id="${track.id}">
      <span class="track-number">${idx+1}</span>
      <img class="track-album-art" src="http://127.0.0.1:4567/api/cover/${track.torrentHash}" 
           onerror="this.onerror=null; this.src='default_album.png';">
      <span class="track-title">${track.title}</span>
      <span class="track-artist">${track.artist}</span>
      <span class="track-duration">${formatTime(track.duration)}</span>
      <button class="track-play-btn" data-id="${track.id}">▶</button>
      <button class="track-like-btn" data-id="${track.id}">♡</button>
      <button class="track-menu-btn" data-id="${track.id}">⋮</button>
    </div>
  `).join('');

  contentDiv.innerHTML = `
    <div class="album-detail-container">
      <div class="album-detail-left">
        <img class="album-detail-cover" src="${coverUrl}" onerror="this.src='default_album.png';">
      </div>
      <div class="album-detail-info">
        <h2>${album}</h2>
        <h3>${artist}</h3>
        <p>${firstTrack.year || 'Unknown year'} • ${firstTrack.genre || 'Unknown genre'} • ${tracks.length} tracks</p>
      </div>
      <div class="album-detail-right">
        <div class="track-list">${trackListHtml || '<p style="color:#b3b3b3;">No tracks yet.</p>'}</div>
      </div>
    </div>
  `;

  // Attach event listeners (unchanged)
  document.querySelectorAll('.track-play-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const id = e.target.dataset.id;
      playTrackById(id, true);
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