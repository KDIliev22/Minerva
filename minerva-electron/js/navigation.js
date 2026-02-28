import { loadHome } from './home.js';
import { loadLibraryGrid } from './library.js';
import { searchInput } from './domElements.js';
import { loadDiscover } from './discover.js';
import { loadDownloads, stopDownloadPolling } from './downloads.js';
import { loadSettings, stopLogPolling } from './settings.js';

export function initNavigation() {
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      stopDownloadPolling();
      stopLogPolling();
      const view = btn.dataset.view;
      if (view === 'home') {
        loadHome();
      } else if (view === 'library') {
        loadLibraryGrid();
      } else if (view === 'search') {
        searchInput.focus();
      } else if (view === 'discover') {
        loadDiscover();
      } else if (view === 'downloads') {
        loadDownloads();
      } else if (view === 'settings') {
        loadSettings();
      }
    });
  });
}