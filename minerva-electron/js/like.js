export async function isTrackLiked(trackId) {
  try {
    const response = await fetch('http://127.0.0.1:4567/api/playlists/1');
    const data = await response.json();
    return data.tracks && data.tracks.some(t => t.id === trackId);
  } catch {
    return false;
  }
}

export async function toggleLike(track) {
  const likedPlaylistId = 1;
  try {
    const response = await fetch(`http://127.0.0.1:4567/api/playlists/${likedPlaylistId}`);
    const data = await response.json();
    const isLiked = data.tracks && data.tracks.some(t => t.id === track.id);
    if (isLiked) {
      await fetch(`http://127.0.0.1:4567/api/playlists/${likedPlaylistId}/tracks/${track.id}`, {
        method: 'DELETE'
      });
    } else {
      await fetch(`http://127.0.0.1:4567/api/playlists/${likedPlaylistId}/tracks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ trackId: track.id })
      });
    }
  } catch (err) {
    console.error('Failed to toggle like', err);
  }
}

export async function updateLikeButtons(tracks) {
  try {
    const response = await fetch('http://127.0.0.1:4567/api/playlists/1');
    if (!response.ok) throw new Error('Failed to fetch liked songs');
    const likedPlaylist = await response.json();
    const likedTrackIds = new Set(likedPlaylist.tracks.map(t => t.id));

    document.querySelectorAll('.track-like-btn').forEach(btn => {
      const trackId = btn.dataset.id;
      btn.textContent = likedTrackIds.has(trackId) ? '♥' : '♡';
    });
  } catch (err) {
    console.error('Could not load like status', err);
  }
}

export function attachLikeButtonHandlers(tracks) {
  document.querySelectorAll('.track-like-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const trackId = btn.dataset.id;
      const track = tracks.find(t => t.id === trackId);
      if (!track) return;

      await toggleLike(track);
      const liked = await isTrackLiked(trackId);
      btn.textContent = liked ? '♥' : '♡';
    });
  });
}