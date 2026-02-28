let logPollInterval = null;

export function loadSettings() {
  stopLogPolling();

  const content = document.getElementById('content');
  content.innerHTML = `
    <div class="settings-view">
      <h2>Settings</h2>

      <section class="settings-section logs-section">
        <div class="logs-header">
          <h3><i data-feather="terminal"></i> Backend Logs</h3>
          <div class="logs-controls">
            <select id="logLines">
              <option value="100">Last 100 lines</option>
              <option value="200" selected>Last 200 lines</option>
              <option value="500">Last 500 lines</option>
              <option value="1000">Last 1000 lines</option>
            </select>
            <label class="auto-refresh-toggle">
              <input type="checkbox" id="logAutoRefresh" checked>
              Auto-refresh (5s)
            </label>
            <button class="btn-secondary" id="logRefreshBtn">
              <i data-feather="refresh-cw"></i> Refresh
            </button>
          </div>
        </div>
        <pre class="log-output" id="logOutput">Loading logs...</pre>
      </section>
    </div>
  `;

  if (window.feather) feather.replace();

  const logOutput   = document.getElementById('logOutput');
  const logLines    = document.getElementById('logLines');
  const refreshBtn  = document.getElementById('logRefreshBtn');
  const autoRefresh = document.getElementById('logAutoRefresh');

  async function fetchLogs() {
    try {
      const lines = parseInt(logLines.value, 10);
      const text = await window.minerva.fetchLogs(lines);
      logOutput.textContent = text;
      logOutput.scrollTop = logOutput.scrollHeight;
    } catch (err) {
      logOutput.textContent = `Error fetching logs: ${err.message}`;
    }
  }

  refreshBtn.addEventListener('click', fetchLogs);
  logLines.addEventListener('change', fetchLogs);

  autoRefresh.addEventListener('change', () => {
    if (autoRefresh.checked) {
      startLogPolling(fetchLogs);
    } else {
      stopLogPolling();
    }
  });

  fetchLogs();
  startLogPolling(fetchLogs);
}

function startLogPolling(fetchFn) {
  stopLogPolling();
  logPollInterval = setInterval(fetchFn, 5000);
}

function stopLogPolling() {
  if (logPollInterval) {
    clearInterval(logPollInterval);
    logPollInterval = null;
  }
}

export { stopLogPolling };
