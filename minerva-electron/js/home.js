import { contentDiv } from './domElements.js';
import { allTracks } from './state.js';
import { showAlbum } from './albumDetail.js';

export async function loadHome() {
  try {
    const hour = new Date().getHours();
    let greeting = "Good evening";
    if (hour < 12) greeting = "Good morning";
    else if (hour < 18) greeting = "Good afternoon";

    if (!allTracks || allTracks.length === 0) {
      contentDiv.innerHTML = `
        <div class="home-header">
          <h1>${greeting}</h1>
        </div>
        <p style="color: #b3b3b3; padding: 20px; text-align: center; font-size: 16px;">
          No torrents downloaded! You can search in the "Discover" page for albums or singles.
        </p>
      `;
      return;
    }

    const albumMap = new Map();
    allTracks.forEach(track => {
      const key = `${track.artist}::${track.album}`;
      if (!albumMap.has(key)) {
        albumMap.set(key, {
          artist: track.artist,
          album: track.album,
          year: track.year,
          genre: track.genre,
          coverTrackId: track.torrentHash,
          trackCount: 1
        });
      } else {
        const album = albumMap.get(key);
        album.trackCount++;
      }
    });
    const albums = Array.from(albumMap.values());

    const shuffled = [...albums].sort(() => 0.5 - Math.random());

    const sections = [
      { title: "Recently played", items: shuffled.slice(0, 6) },
      { title: "Your top mixes", items: shuffled.slice(6, 12) },
      { title: "Recommended for you", items: shuffled.slice(12, 18) }
    ];

    const sectionsHtml = sections.map(section => `
      <div class="home-section">
        <h2 class="section-title">${section.title}</h2>
        <div class="horizontal-scroll">
          ${section.items.map(album => `
            <div class="album-card" data-artist="${album.artist}" data-album="${album.album}" data-cover-id="${album.coverTrackId}">
              <img class="album-card-cover" src="http://127.0.0.1:4567/api/cover/${album.coverTrackId}" 
                   onerror="this.onerror=null; this.src='default_album.png';">
              <div class="album-card-info">
                <div class="album-card-title">${album.album || 'Unknown'}</div>
                <div class="album-card-artist">${album.artist || 'Unknown'}</div>
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    `).join('');

    contentDiv.innerHTML = `
      <div class="home-header">
        <h1>${greeting}</h1>
      </div>
      ${sectionsHtml}
    `;

    document.querySelectorAll('.album-card').forEach(card => {
      card.addEventListener('click', () => {
        const artist = card.dataset.artist;
        const album = card.dataset.album;
        showAlbum(artist, album);
      });
    });

  } catch (err) {
    console.error('Failed to load home', err);
    contentDiv.innerHTML = '<p style="color: red;">Error loading home</p>';
  }
}