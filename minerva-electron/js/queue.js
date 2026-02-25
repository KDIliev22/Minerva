import { audio, playPauseBtn } from './domElements.js';
import { playTrackById } from './trackPlayback.js';
export let queueTracks = [];
export let queueIndex = -1;

export function addToQueue(track) {
  queueTracks.push(track);
  if (queueIndex === -1) queueIndex = 0;
}

export function removeFromQueue(index) {
  queueTracks.splice(index, 1);
  if (index < queueIndex) queueIndex--;
  else if (index === queueIndex) {
    if (queueTracks.length === 0) {
      audio.pause();
      playPauseBtn.textContent = '▶';
      queueIndex = -1;
    } else {
      queueIndex = Math.min(queueIndex, queueTracks.length - 1);
      playTrackById(queueTracks[queueIndex].id, false);
    }
  }
}

export function clearQueue() {
  queueTracks = [];
  queueIndex = -1;
  audio.pause();
  playPauseBtn.textContent = '▶';
}

export function playPrevious() {
  if (queueIndex > 0) {
    queueIndex--;
    playTrackById(queueTracks[queueIndex].id, false);
  } else {
    console.log('At beginning of queue');
  }
}

export function playNext() {
  if (queueIndex < queueTracks.length - 1) {
    queueIndex++;
    playTrackById(queueTracks[queueIndex].id, false);
  } else {
    console.log('End of queue');
    audio.pause();
    playPauseBtn.textContent = '▶';
    resetPlayer();
  }
}

export function setQueue(tracks, startIndex = 0) {
  queueTracks = tracks;
  queueIndex = startIndex;
}

export function showQueue() {
  if (!queueTracks.length) {
    alert('Queue is empty');
    return;
  }
  const modalHtml = `
    <div id="queueModal" style="position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.8); z-index:2000; display:flex; justify-content:center; align-items:center;">
      <div style="background:#282828; width:500px; max-width:90%; border-radius:10px; padding:20px; color:white;">
        <h3 style="margin-top:0;">Queue</h3>
        <div style="max-height:300px; overflow-y:auto; margin-bottom:20px;">
          ${queueTracks.map((t, i) => `
            <div style="display:flex; align-items:center; gap:10px; padding:5px; border-bottom:1px solid #444;">
              <span>${i === queueIndex ? '▶' : i+1}.</span>
              <img src="http://127.0.0.1:4567/api/cover/${t.torrentHash}" 
                   onerror="this.onerror=null; this.src='default_album.png';" 
                   style="width:30px; height:30px; object-fit:cover; border-radius:3px;">
              <div style="flex:1; overflow:hidden; white-space:nowrap; text-overflow:ellipsis;">${t.title} – ${t.artist}</div>
              <button class="remove-from-queue" data-index="${i}" style="background:none; border:none; color:#b9093a; font-size:16px; cursor:pointer;">✖</button>
            </div>
          `).join('')}
        </div>
        <div style="display:flex; justify-content:flex-end; gap:10px;">
          <button id="clearQueueBtn" style="background:#333; border:none; color:white; padding:8px 16px; border-radius:5px; cursor:pointer;">Clear</button>
          <button id="closeQueueModal" style="background:#b9093a; border:none; color:white; padding:8px 16px; border-radius:5px; cursor:pointer;">Close</button>
        </div>
      </div>
    </div>
  `;
  const modal = document.createElement('div');
  modal.innerHTML = modalHtml;
  document.body.appendChild(modal);

  document.querySelectorAll('.remove-from-queue').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const idx = parseInt(e.target.dataset.index);
      removeFromQueue(idx);
      modal.remove();
      showQueue();
    });
  });
  document.getElementById('clearQueueBtn').addEventListener('click', () => {
    clearQueue();
    modal.remove();
    if (queueTracks.length === 0) alert('Queue cleared');
    else showQueue();
  });
  document.getElementById('closeQueueModal').addEventListener('click', () => modal.remove());
}