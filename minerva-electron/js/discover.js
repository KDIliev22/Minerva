// discover.js
import { contentDiv } from './domElements.js';

let currentFilter = 'all'; // 'all', 'albums', 'singles'
let lastResults = [];

export async function loadDiscover() {
  contentDiv.innerHTML = `
    <div class="discover-header">
      <h2>Discover Music on the P2P Network</h2>
    </div>
    <div class="search-container" style="margin-bottom: 20px;">
      <input type="text" id="discoverSearchInput" placeholder="Search artists, albums, tracks...">
      <button id="discoverSearchBtn">Search Network</button>
    </div>
    <div class="discover-filters" style="margin-bottom: 16px; display: none;">
      <button class="filter-btn active" data-filter="all">All</button>
      <button class="filter-btn" data-filter="albums">Albums</button>
      <button class="filter-btn" data-filter="singles">Singles</button>
    </div>
    <div id="discoverResults" class="track-list"></div>
  `;

  document.getElementById('discoverSearchBtn').addEventListener('click', performDiscoverSearch);
  document.getElementById('discoverSearchInput').addEventListener('keypress', (e) => {
    if (e.key === 'Enter') performDiscoverSearch();
  });

  document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentFilter = btn.dataset.filter;
      renderDiscoverResults(lastResults);
    });
  });
}

async function performDiscoverSearch() {
  const query = document.getElementById('discoverSearchInput').value.trim();
  if (!query) return;

  const resultsDiv = document.getElementById('discoverResults');
  resultsDiv.innerHTML = '<p>Searching the network... ⏳</p>';

  try {
    const response = await fetch(`http://127.0.0.1:4567/api/dht-search?q=${encodeURIComponent(query)}`);
    if (!response.ok) throw new Error('Search failed');
    const results = await response.json();
    lastResults = results;
    document.querySelector('.discover-filters').style.display = 'flex';
    renderDiscoverResults(results);
  } catch (err) {
    resultsDiv.innerHTML = `<p style="color: red;">Error: ${err.message}</p>`;
  }
}

function renderDiscoverResults(results) {
  const container = document.getElementById('discoverResults');
  if (!results || results.length === 0) {
    container.innerHTML = '<p>No results found.</p>';
    return;
  }

  // Group tracks by torrentHash (album grouping)
  const albumMap = new Map();
  results.forEach(track => {
    const key = track.torrentHash;
    if (!albumMap.has(key)) {
      albumMap.set(key, { artist: track.artist, album: track.album, torrentHash: track.torrentHash, tracks: [] });
    }
    // Avoid duplicate titles within same album
    const group = albumMap.get(key);
    if (!group.tracks.some(t => t.title === track.title)) {
      group.tracks.push(track);
    }
  });

  const groups = Array.from(albumMap.values());

  // Apply filter
  let filtered;
  if (currentFilter === 'albums') {
    filtered = groups.filter(g => g.tracks.length > 1);
  } else if (currentFilter === 'singles') {
    filtered = groups.filter(g => g.tracks.length === 1);
  } else {
    filtered = groups;
  }

  if (filtered.length === 0) {
    container.innerHTML = `<p>No ${currentFilter === 'all' ? '' : currentFilter + ' '}results found.</p>`;
    return;
  }

  const html = filtered.map(group => {
    if (group.tracks.length === 1) {
      // Single track card
      const track = group.tracks[0];
      return `
        <div class="discover-card discover-single" data-hash="${group.torrentHash}">
          <img class="discover-cover" src="http://127.0.0.1:4567/api/cover/${group.torrentHash}" 
               onerror="this.src='assets/default_album.png';">
          <div class="discover-info">
            <span class="discover-title">${track.title || 'Unknown'}</span>
            <span class="discover-artist">${track.artist || 'Unknown'}</span>
            <span class="discover-type-badge single-badge">Single</span>
          </div>
          <button class="track-download-btn" data-hash="${group.torrentHash}" title="Download">⬇️</button>
        </div>`;
    } else {
      // Album card with track list
      const trackListHtml = group.tracks.map(t => `
        <div class="discover-album-track">
          <span class="discover-album-track-title">${t.title || 'Unknown'}</span>
        </div>
      `).join('');
      return `
        <div class="discover-card discover-album" data-hash="${group.torrentHash}">
          <div class="discover-album-header">
            <img class="discover-cover" src="http://127.0.0.1:4567/api/cover/${group.torrentHash}" 
                 onerror="this.src='assets/default_album.png';">
            <div class="discover-info">
              <span class="discover-title">${group.album || 'Unknown Album'}</span>
              <span class="discover-artist">${group.artist || 'Unknown'}</span>
              <span class="discover-type-badge album-badge">Album · ${group.tracks.length} tracks</span>
            </div>
            <button class="track-download-btn" data-hash="${group.torrentHash}" title="Download Album">⬇️</button>
          </div>
          <div class="discover-album-tracks">${trackListHtml}</div>
        </div>`;
    }
  }).join('');

  container.innerHTML = html;

  // Attach download handlers
  container.querySelectorAll('.track-download-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const hash = btn.dataset.hash;
      // Find the album group to pass metadata along
      const group = albumMap.get(hash);
      const metadata = group ? {
        artist: group.artist,
        album: group.album,
        tracks: group.tracks.map(t => ({ title: t.title, year: t.year ? String(t.year) : null, genre: t.genre || null }))
      } : null;
      btn.disabled = true;
      btn.textContent = '⏳';
      try {
        const result = await window.minerva.fetchTorrent(hash, metadata);
        if (result && result.success) {
          btn.textContent = '✔️';
          btn.title = 'Torrent fetch started!';
        } else {
          btn.textContent = '❌';
          btn.title = result && result.error ? result.error : 'Failed';
        }
      } catch (err) {
        btn.textContent = '❌';
        btn.title = err.message;
      }
      setTimeout(() => {
        btn.disabled = false;
        btn.textContent = '⬇️';
      }, 2000);
    });
  });
}