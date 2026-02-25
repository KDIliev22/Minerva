import { contentDiv } from './domElements.js';
import { showAlbum } from './albumDetail.js';

export async function loadLibraryGrid() {
  try {
    const response = await fetch('http://127.0.0.1:4567/api/albums');
    if (!response.ok) throw new Error(`HTTP error ${response.status}`);
    const albums = await response.json();
    renderAlbumGrid(albums);
  } catch (err) {
    console.error('Failed to load albums', err);
    contentDiv.innerHTML = '<p style="color: red;">Error loading library</p>';
  }
}

function renderAlbumGrid(albums) {
  if (!albums || albums.length === 0) {
    contentDiv.innerHTML = '<p style="color: #b3b3b3; padding: 20px; text-align: center; font-size: 16px;">No torrents downloaded! You can search in the "Discover" page for albums or singles.</p>';
    return;
  }

  const gridHtml = albums.map(album => `
    <div class="album-grid-item" data-artist="${album.artist}" data-album="${album.album}" data-cover-id="${album.coverTrackId}">
      <img class="album-cover" src="http://127.0.0.1:4567/api/cover/${album.coverTrackId}" 
           onerror="this.onerror=null; this.src='default_album.png';">
      <div class="album-info">
        <div class="album-title">${album.album || 'Unknown Album'}</div>
        <div class="album-artist">${album.artist || 'Unknown Artist'} • ${album.year || '?'}</div>
        <div class="album-track-count">${album.trackCount} tracks</div>
      </div>
    </div>
  `).join('');

  contentDiv.innerHTML = `
    <div class="library-header">
      <h2>Your Library</h2>
    </div>
    <div class="album-grid">${gridHtml}</div>
  `;

  document.querySelectorAll('.album-grid-item').forEach(item => {
    item.addEventListener('click', () => {
      const artist = item.dataset.artist;
      const album = item.dataset.album;
      showAlbum(artist, album);
    });
  });
}