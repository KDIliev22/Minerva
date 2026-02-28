 
export let allTracks = [];
export let playlists = [];

export function setAllTracks(tracks) {
  allTracks = tracks;
}

export function setPlaylists(newPlaylists) {
  playlists = newPlaylists;
}


export async function refreshAllTracks() {
  try {
    const tracks = await window.minerva.fetchTracks();
    if (tracks) {
      allTracks = tracks;
      console.log(`Refreshed allTracks: ${tracks.length} tracks`);
    }
  } catch (err) {
    console.error('Failed to refresh tracks:', err);
  }
}