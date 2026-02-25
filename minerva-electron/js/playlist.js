import { contentDiv, playlistsContainer } from './domElements.js';
import { allTracks, playlists, setPlaylists } from './state.js';
import { formatTime } from './utils.js';
import { setQueue } from './queue.js';
import { playTrackById } from './trackPlayback.js';
import { showTrackMenu } from './trackMenu.js';
import { attachLikeButtonHandlers, updateLikeButtons } from './like.js';
import { loadHome } from './home.js';

export async function loadPlaylists() {
  try {
    const response = await fetch('http://127.0.0.1:4567/api/playlists');
    if (!response.ok) throw new Error(`HTTP error ${response.status}`);
    const data = await response.json();
    setPlaylists(data);
    renderPlaylistSidebar();
  } catch (err) {
    console.error('Failed to load playlists', err);
  }
}

function renderPlaylistSidebar() {
  if (!playlistsContainer) return;
  playlistsContainer.innerHTML = playlists.map(p => `
    <li data-playlist-id="${p.id}" class="${p.isSystem ? 'system-playlist' : ''}">
      ${p.name} (${p.trackCount || 0})
    </li>
  `).join('');
  document.querySelectorAll('.playlists li').forEach(li => {
    li.addEventListener('click', () => {
      const id = li.dataset.playlistId;
      loadPlaylistDetail(id);
    });
  });
}

// Custom prompt for playlist creation
async function promptForPlaylistDetails() {
  return new Promise((resolve) => {
    const old = document.getElementById('playlistPromptModal');
    if (old) old.remove();

    const modal = document.createElement('div');
    modal.id = 'playlistPromptModal';
    modal.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.8);
      z-index: 5000;
      display: flex;
      justify-content: center;
      align-items: center;
    `;
    modal.innerHTML = `
      <div style="background: #282828; width: 400px; max-width: 90%; border-radius: 10px; padding: 20px; color: white;">
        <h3 style="margin-top:0;">Create Playlist</h3>
        <div style="margin-bottom: 15px;">
          <label style="display: block; margin-bottom: 5px;">Name:</label>
          <input type="text" id="promptPlaylistName" style="width: 100%; padding: 8px; background: #333; border: 1px solid #444; color: white; border-radius: 4px;">
        </div>
        <div style="margin-bottom: 20px;">
          <label style="display: block; margin-bottom: 5px;">Description (optional):</label>
          <input type="text" id="promptPlaylistDesc" style="width: 100%; padding: 8px; background: #333; border: 1px solid #444; color: white; border-radius: 4px;">
        </div>
        <div style="display: flex; justify-content: flex-end; gap: 10px;">
          <button id="promptCancelBtn" style="background: #333; border: none; color: white; padding: 8px 16px; border-radius: 5px; cursor: pointer;">Cancel</button>
          <button id="promptCreateBtn" style="background: #b9093a; border: none; color: white; padding: 8px 16px; border-radius: 5px; cursor: pointer;">Create</button>
        </div>
      </div>
    `;
    document.body.appendChild(modal);

    const nameInput = document.getElementById('promptPlaylistName');
    const descInput = document.getElementById('promptPlaylistDesc');
    const cancelBtn = document.getElementById('promptCancelBtn');
    const createBtn = document.getElementById('promptCreateBtn');

    nameInput.focus();

    cancelBtn.addEventListener('click', () => {
      modal.remove();
      resolve(null);
    });

    createBtn.addEventListener('click', () => {
      const name = nameInput.value.trim();
      if (!name) {
        alert('Playlist name is required.');
        return;
      }
      const description = descInput.value.trim();
      modal.remove();
      resolve({ name, description });
    });

    nameInput.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') createBtn.click();
    });
    descInput.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') createBtn.click();
    });
  });
}

export async function createPlaylist(name, description) {
  if (name === undefined) {
    const details = await promptForPlaylistDetails();
    if (!details) return;
    name = details.name;
    description = details.description;
  }
  try {
    const response = await fetch('http://127.0.0.1:4567/api/playlists', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description })
    });
    if (!response.ok) throw new Error('Failed to create playlist');
    const data = await response.json();
    await loadPlaylists();
    loadPlaylistDetail(data.id);
  } catch (err) {
    alert('Error creating playlist: ' + err.message);
  }
}

export async function loadPlaylistDetail(id) {
  try {
    const response = await fetch(`http://127.0.0.1:4567/api/playlists/${id}`);
    if (!response.ok) throw new Error(`HTTP error ${response.status}`);
    const playlist = await response.json();
    renderPlaylistDetail(playlist);
  } catch (err) {
    console.error('Failed to load playlist', err);
  }
}

function renderPlaylistDetail(playlist) {
  const tracks = (playlist.tracks || [])
    .map(t => allTracks.find(tr => tr.id === t.id))
    .filter(t => t);

  const trackListHtml = tracks.map((track, idx) => `
    <div class="track-item" data-id="${track.id}">
      <span class="track-number">${idx+1}</span>
      <img class="track-album-art" src="http://127.0.0.1:4567/api/cover/${track.id}" 
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

  // Determine cover image URL
  let coverUrl;
  if (playlist.isSystem && playlist.name === 'Liked Songs') {
    coverUrl = 'liked_songs_icon.png'; // special icon for Liked Songs
  } else if (playlist.iconPath) {
    coverUrl = `http://127.0.0.1:4567/api/cover/${playlist.iconPath}`;
  } else {
    coverUrl = 'default_playlist_cover.png';
  }

  // Playlist header with cover image and edit/delete buttons (only for non-system)
  contentDiv.innerHTML = `
    <div class="playlist-header" style="display: flex; gap: 20px; align-items: center;">
      <img src="${coverUrl}" 
           onerror="this.onerror=null; this.src='${playlist.isSystem && playlist.name === 'Liked Songs' ? 'liked_songs_icon.png' : 'default_playlist_cover.png'}';" 
           style="width: 120px; height: 120px; object-fit: cover; border-radius: 5px;">
      <div>
        <h2>${playlist.name}</h2>
        <p>${playlist.description || ''}</p>
        ${!playlist.isSystem ? `
          <div style="display: flex; gap: 10px;">
            <button id="editPlaylistBtn">Edit playlist</button>
            <button id="deletePlaylistBtn">Delete playlist</button>
          </div>
        ` : ''}
      </div>
    </div>
    <div class="track-list">${trackListHtml || '<p style="color:#b3b3b3;">No tracks yet.</p>'}</div>
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
      // Pass current playlist to showTrackMenu so it knows we're inside a playlist
      showTrackMenu(e.clientX, e.clientY, track, playlist);
    });
  });

  attachLikeButtonHandlers(tracks);
  updateLikeButtons(tracks);

  if (!playlist.isSystem) {
    document.getElementById('deletePlaylistBtn')?.addEventListener('click', () => deletePlaylist(playlist.id));
    document.getElementById('editPlaylistBtn')?.addEventListener('click', () => showEditPlaylistModal(playlist));
  }
}

export async function deletePlaylist(id) {
  if (!confirm('Delete this playlist?')) return;
  try {
    const response = await fetch(`http://127.0.0.1:4567/api/playlists/${id}`, { method: 'DELETE' });
    if (!response.ok) throw new Error('Delete failed');
    await loadPlaylists();
    loadHome();
  } catch (err) {
    alert('Error deleting playlist: ' + err.message);
  }
}

// Edit playlist modal
function showEditPlaylistModal(playlist) {
  const old = document.getElementById('editPlaylistModal');
  if (old) old.remove();

  const modal = document.createElement('div');
  modal.id = 'editPlaylistModal';
  modal.style.cssText = `
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0,0,0,0.8);
    z-index: 5000;
    display: flex;
    justify-content: center;
    align-items: center;
  `;
  modal.innerHTML = `
    <div style="background: #282828; width: 400px; border-radius: 10px; padding: 20px; color: white;">
      <h3 style="margin-top:0;">Edit Playlist</h3>
      <div style="margin-bottom: 15px;">
        <label style="display: block; margin-bottom: 5px;">Name:</label>
        <input type="text" id="editPlaylistName" value="${playlist.name.replace(/"/g, '&quot;')}" style="width: 100%; padding: 8px; background: #333; border: 1px solid #444; color: white; border-radius: 4px;">
      </div>
      <div style="margin-bottom: 20px;">
        <label style="display: block; margin-bottom: 5px;">Description:</label>
        <input type="text" id="editPlaylistDesc" value="${(playlist.description || '').replace(/"/g, '&quot;')}" style="width: 100%; padding: 8px; background: #333; border: 1px solid #444; color: white; border-radius: 4px;">
      </div>
      <div style="display: flex; justify-content: flex-end; gap: 10px;">
        <button id="editCancelBtn" style="background: #333; border: none; color: white; padding: 8px 16px; border-radius: 5px; cursor: pointer;">Cancel</button>
        <button id="editSaveBtn" style="background: #b9093a; border: none; color: white; padding: 8px 16px; border-radius: 5px; cursor: pointer;">Save</button>
      </div>
    </div>
  `;
  document.body.appendChild(modal);

  const nameInput = document.getElementById('editPlaylistName');
  const descInput = document.getElementById('editPlaylistDesc');
  const cancelBtn = document.getElementById('editCancelBtn');
  const saveBtn = document.getElementById('editSaveBtn');

  cancelBtn.addEventListener('click', () => modal.remove());

  saveBtn.addEventListener('click', async () => {
    const newName = nameInput.value.trim();
    if (!newName) {
      alert('Playlist name cannot be empty.');
      return;
    }
    const newDesc = descInput.value.trim();
    try {
      const response = await fetch(`http://127.0.0.1:4567/api/playlists/${playlist.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newName, description: newDesc, iconPath: playlist.iconPath })
      });
      if (!response.ok) throw new Error('Update failed');
      modal.remove();
      loadPlaylistDetail(playlist.id);
    } catch (err) {
      alert('Failed to update playlist: ' + err.message);
    }
  });

  nameInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') saveBtn.click();
  });
  descInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') saveBtn.click();
  });
}

// New function: set playlist cover from a track
// Add/update this function in playlist.js
export async function setPlaylistCover(playlistId, trackId) {
  try {
    // Find the track to get its torrent hash
    const track = allTracks.find(t => t.id === trackId);
    if (!track) {
      alert('Track not found');
      return;
    }
    const torrentHash = track.torrentHash; // use torrent hash for album art

    // First fetch current playlist to get existing data
    const getResponse = await fetch(`http://127.0.0.1:4567/api/playlists/${playlistId}`);
    if (!getResponse.ok) throw new Error('Failed to fetch playlist');
    const playlist = await getResponse.json();

    // Update with new iconPath (torrent hash)
    const updateResponse = await fetch(`http://127.0.0.1:4567/api/playlists/${playlistId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: playlist.name,
        description: playlist.description,
        iconPath: torrentHash   // store torrent hash as the cover reference
      })
    });
    if (!updateResponse.ok) throw new Error('Failed to update playlist cover');

    // Reload playlist detail to show new cover
    loadPlaylistDetail(playlistId);
  } catch (err) {
    alert('Error setting playlist cover: ' + err.message);
  }
}