import { addToQueue } from './queue.js';
import { playlists } from './state.js';
import { createPlaylist, setPlaylistCover } from './playlist.js';
import { loadPlaylists } from './playlist.js';

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

export function showTrackMenu(x, y, track, currentPlaylist) {
  const old = document.getElementById('trackMenu');
  if (old) old.remove();

  const menu = document.createElement('div');
  menu.id = 'trackMenu';
  menu.style.cssText = `
    position: fixed;
    background: #282828;
    border: 1px solid #444;
    border-radius: 5px;
    padding: 5px 0;
    z-index: 3000;
    min-width: 160px;
  `;

  let menuHtml = `
    <div class="menu-item" data-action="add-to-queue">Add to queue</div>
    <div class="menu-item" data-action="add-to-playlist">Add to playlist...</div>
  `;

  if (currentPlaylist && !currentPlaylist.isSystem) {
    menuHtml += `<div class="menu-item" data-action="set-playlist-cover">Set as playlist cover</div>`;
  }

  menu.innerHTML = menuHtml;

  document.body.appendChild(menu);

  const menuWidth = menu.offsetWidth;
  const menuHeight = menu.offsetHeight;
  const winWidth = window.innerWidth;
  const winHeight = window.innerHeight;

  let left = x;
  let top = y;

  if (left + menuWidth > winWidth) {
    left = winWidth - menuWidth - 10;
  }
  if (top + menuHeight > winHeight) {
    top = winHeight - menuHeight - 10;
  }
  left = Math.max(5, left);
  top = Math.max(5, top);

  menu.style.left = left + 'px';
  menu.style.top = top + 'px';

  menu.querySelectorAll('.menu-item').forEach(item => {
    item.addEventListener('click', (e) => {
      const action = e.target.dataset.action;
      if (action === 'add-to-queue') {
        addToQueue(track);
      } else if (action === 'add-to-playlist') {
        showPlaylistSelector(track);
      } else if (action === 'set-playlist-cover') {
        setPlaylistCover(currentPlaylist.id, track.id);
      }
      menu.remove();
    });
  });

  setTimeout(() => {
    const closeHandler = (e) => {
      if (!menu.contains(e.target)) {
        menu.remove();
        document.removeEventListener('click', closeHandler);
      }
    };
    document.addEventListener('click', closeHandler);
  }, 100);
}

export function showPlaylistSelector(track) {
  const old = document.getElementById('playlistSelector');
  if (old) old.remove();

  const selector = document.createElement('div');
  selector.id = 'playlistSelector';
  selector.style.cssText = `
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    background: #282828;
    border-radius: 10px;
    padding: 20px;
    width: 300px;
    max-width: 90%;
    z-index: 4000;
  `;
  selector.innerHTML = `
    <h3 style="margin-top:0;">Add to playlist</h3>
    <div style="max-height:200px; overflow-y:auto; margin-bottom:20px;">
      ${playlists.filter(p => !p.isSystem).map(p => `
        <div class="playlist-option" data-id="${p.id}" style="padding:8px; cursor:pointer; border-bottom:1px solid #444;">${p.name}</div>
      `).join('')}
      <div id="createNewPlaylistOption" style="padding:8px; cursor:pointer; border-bottom:1px solid #444; color:#b9093a;">
        + Create new playlist
      </div>
    </div>
    <button id="cancelPlaylistSelector" style="background:#333; border:none; color:white; padding:8px 16px; border-radius:5px; cursor:pointer; width:100%;">Cancel</button>
  `;
  document.body.appendChild(selector);

  document.querySelectorAll('.playlist-option').forEach(opt => {
    opt.addEventListener('click', async () => {
      const playlistId = opt.dataset.id;
      try {
        await fetch(`http://127.0.0.1:4567/api/playlists/${playlistId}/tracks`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ trackId: track.id })
        });
        alert('Track added to playlist');
      } catch (err) {
        alert('Failed to add track');
      }
      selector.remove();
    });
  });

  document.getElementById('createNewPlaylistOption').addEventListener('click', async () => {
    selector.remove();
    const details = await promptForPlaylistDetails();
    if (!details) return;
    const { name, description } = details;
    try {
      const response = await fetch('http://127.0.0.1:4567/api/playlists', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description })
      });
      if (!response.ok) throw new Error('Failed to create playlist');
      const newPlaylist = await response.json();

      await fetch(`http://127.0.0.1:4567/api/playlists/${newPlaylist.id}/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ trackId: track.id })
      });

      alert(`Track added to new playlist "${name}"`);
      await loadPlaylists();
    } catch (err) {
      alert('Error: ' + err.message);
    }
  });

  document.getElementById('cancelPlaylistSelector').addEventListener('click', () => selector.remove());
}