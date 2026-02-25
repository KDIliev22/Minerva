// downloads.js - Downloads tab with live torrent status
import { contentDiv } from './domElements.js';

const API_PORT = 4567;
let pollInterval = null;

export function loadDownloads() {
  // Clear any previous polling
  if (pollInterval) { clearInterval(pollInterval); pollInterval = null; }

  contentDiv.innerHTML = `
    <div class="downloads-header">
      <h2>Downloads & Transfers</h2>
      <div class="downloads-actions">
        <button class="dl-action-btn" id="dlRefreshBtn" title="Refresh">
          <i data-feather="refresh-cw"></i>
        </button>
      </div>
    </div>
    <div class="downloads-stats" id="dlStats"></div>
    <div class="downloads-list" id="downloadsList">
      <p class="dl-loading">Loading transfers...</p>
    </div>
  `;
  if (window.feather) feather.replace();

  document.getElementById('dlRefreshBtn').addEventListener('click', fetchDownloads);

  // Initial fetch + polling every 2s
  fetchDownloads();
  pollInterval = setInterval(fetchDownloads, 2000);
}

export function stopDownloadPolling() {
  if (pollInterval) { clearInterval(pollInterval); pollInterval = null; }
}

async function fetchDownloads() {
  try {
    const res = await fetch(`http://127.0.0.1:${API_PORT}/api/downloads`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const downloads = await res.json();
    renderDownloads(downloads);
  } catch (err) {
    const list = document.getElementById('downloadsList');
    if (list) list.innerHTML = `<p class="dl-error">Failed to fetch transfers: ${err.message}</p>`;
  }
}

function formatBytes(bytes) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatSpeed(bytesPerSec) {
  return formatBytes(bytesPerSec) + '/s';
}

function stateLabel(item) {
  if (item.seeding) return 'Seeding';
  if (item.paused) return 'Paused';
  const s = item.state;
  if (s === 'CHECKING_FILES' || s === 'CHECKING_RESUME_DATA') return 'Checking';
  if (s === 'DOWNLOADING_METADATA') return 'Fetching metadata';
  if (s === 'DOWNLOADING') return 'Downloading';
  if (s === 'FINISHED') return 'Complete';
  if (s === 'SEEDING') return 'Seeding';
  if (s === 'ALLOCATING') return 'Allocating';
  return s || 'Unknown';
}

function stateClass(item) {
  if (item.seeding) return 'state-seeding';
  if (item.paused) return 'state-paused';
  if (item.state === 'DOWNLOADING') return 'state-downloading';
  if (item.state === 'CHECKING_FILES' || item.state === 'CHECKING_RESUME_DATA') return 'state-checking';
  return 'state-other';
}

function renderDownloads(downloads) {
  const list = document.getElementById('downloadsList');
  const stats = document.getElementById('dlStats');
  if (!list) return;

  // Stats summary
  const downloading = downloads.filter(d => !d.seeding && !d.paused && d.state === 'DOWNLOADING');
  const seeding = downloads.filter(d => d.seeding);
  const totalDown = downloading.reduce((s, d) => s + d.downloadRate, 0);
  const totalUp = downloads.reduce((s, d) => s + d.uploadRate, 0);

  if (stats) {
    stats.innerHTML = `
      <div class="dl-stat"><span class="dl-stat-val">${downloads.length}</span><span class="dl-stat-label">Total</span></div>
      <div class="dl-stat"><span class="dl-stat-val">${downloading.length}</span><span class="dl-stat-label">Downloading</span></div>
      <div class="dl-stat"><span class="dl-stat-val">${seeding.length}</span><span class="dl-stat-label">Seeding</span></div>
      <div class="dl-stat"><span class="dl-stat-val">↓ ${formatSpeed(totalDown)}</span><span class="dl-stat-label">Down</span></div>
      <div class="dl-stat"><span class="dl-stat-val">↑ ${formatSpeed(totalUp)}</span><span class="dl-stat-label">Up</span></div>
    `;
  }

  if (downloads.length === 0) {
    list.innerHTML = '<p class="dl-empty">No active transfers. Download music from the Discover tab!</p>';
    return;
  }

  // Sort: downloading first, then checking, then seeding, then paused
  const order = { DOWNLOADING: 0, DOWNLOADING_METADATA: 1, CHECKING_FILES: 2, CHECKING_RESUME_DATA: 2, ALLOCATING: 3, FINISHED: 4, SEEDING: 5 };
  downloads.sort((a, b) => {
    if (a.paused && !b.paused) return 1;
    if (!a.paused && b.paused) return -1;
    return (order[a.state] ?? 6) - (order[b.state] ?? 6);
  });

  list.innerHTML = downloads.map(d => {
    const pct = d.progress;
    const label = stateLabel(d);
    const cls = stateClass(d);
    const isPaused = d.paused;
    const isActive = !d.seeding && !d.paused;

    return `
      <div class="dl-item ${cls}" data-hash="${d.hash}">
        <div class="dl-item-top">
          <div class="dl-name" title="${d.name}">${d.name}</div>
          <div class="dl-controls">
            ${isActive ? `<button class="dl-ctrl-btn" data-action="pause" data-hash="${d.hash}" title="Pause">⏸</button>` : ''}
            ${isPaused ? `<button class="dl-ctrl-btn" data-action="resume" data-hash="${d.hash}" title="Resume">▶️</button>` : ''}
            <button class="dl-ctrl-btn dl-remove-btn" data-action="remove" data-hash="${d.hash}" title="Remove">✕</button>
          </div>
        </div>
        <div class="dl-progress-row">
          <div class="dl-progress-bar">
            <div class="dl-progress-fill ${cls}" style="width: ${pct}%"></div>
          </div>
          <span class="dl-pct">${pct.toFixed(1)}%</span>
        </div>
        <div class="dl-details">
          <span class="dl-badge ${cls}">${label}</span>
          <span class="dl-size">${formatBytes(d.totalDone)} / ${formatBytes(d.totalSize)}</span>
          ${d.downloadRate > 0 ? `<span class="dl-speed">↓ ${formatSpeed(d.downloadRate)}</span>` : ''}
          ${d.uploadRate > 0 ? `<span class="dl-speed">↑ ${formatSpeed(d.uploadRate)}</span>` : ''}
          <span class="dl-peers">${d.peers} peer${d.peers !== 1 ? 's' : ''} · ${d.seeds} seed${d.seeds !== 1 ? 's' : ''}</span>
        </div>
      </div>`;
  }).join('');

  // Attach control handlers
  list.querySelectorAll('.dl-ctrl-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const hash = btn.dataset.hash;
      const action = btn.dataset.action;
      try {
        if (action === 'pause') {
          await fetch(`http://127.0.0.1:${API_PORT}/api/downloads/${hash}/pause`, { method: 'POST' });
        } else if (action === 'resume') {
          await fetch(`http://127.0.0.1:${API_PORT}/api/downloads/${hash}/resume`, { method: 'POST' });
        } else if (action === 'remove') {
          if (confirm('Remove this torrent?')) {
            await fetch(`http://127.0.0.1:${API_PORT}/api/downloads/${hash}`, { method: 'DELETE' });
          }
        }
        fetchDownloads();
      } catch (err) {
        console.error('Download action failed:', err);
      }
    });
  });
}
