const { app, BrowserWindow, ipcMain, shell } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const fsPromises = require('fs').promises;
const http = require('http');

// Fix SUID sandbox issue when running as AppImage on Linux
if (process.platform === 'linux' && process.env.APPIMAGE) {
  app.commandLine.appendSwitch('no-sandbox');
}

let javaProcess;
let mainWindow;
let authServer = null;

// ── Helpers for packaged vs. dev mode ──

const isPackaged = app.isPackaged;

function backendDir() {
  return isPackaged
    ? path.join(process.resourcesPath, 'backend')
    : path.join(__dirname, 'backend');
}

function dataRoot() {
  return isPackaged ? app.getPath('userData') : process.cwd();
}

function javaExe() {
  const ext = process.platform === 'win32' ? '.exe' : '';
  const bundled = path.join(backendDir(), 'jre', 'bin', `java${ext}`);
  if (fs.existsSync(bundled)) {
    console.log('Using bundled JRE:', bundled);
    return bundled;
  }
  console.log('Bundled JRE not found, falling back to system java');
  return 'java';
}

const API_PORT = 4567;

// ── Check if backend is already running (e.g. as a system service) ──

async function isBackendRunning() {
  try {
    const res = await fetch(`http://127.0.0.1:${API_PORT}/api/test`);
    return res.ok;
  } catch (_) {
    return false;
  }
}

// ── Start / stop Java backend ──

function startJavaBackend() {
  const jar = path.join(backendDir(), 'minerva-backend.jar');
  if (!fs.existsSync(jar)) {
    console.error('Backend JAR not found at:', jar);
    return;
  }

  const root = dataRoot();
  for (const sub of ['library', 'torrent_files', 'downloads', 'album_art', 'uploads', 'torrents']) {
    const dir = path.join(root, sub);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  }

  const env = {
    ...process.env,
    API_PORT: String(API_PORT),
    SEARCH_PORT: process.env.SEARCH_PORT || '4568',
    LISTEN_PORT: process.env.LISTEN_PORT || '6881',
    LIBRARY_DIR: path.join(root, 'library'),
    TORRENT_DIR: path.join(root, 'torrent_files'),
    DOWNLOADS_DIR: path.join(root, 'downloads'),
    ALBUM_ART_DIR: path.join(root, 'album_art'),
  };
  if (process.env.DHT_BOOTSTRAP_NODES) {
    env.DHT_BOOTSTRAP_NODES = process.env.DHT_BOOTSTRAP_NODES;
  }

  const java = javaExe();
  const args = ['-jar', jar];
  console.log(`Starting backend: ${java} ${args.join(' ')}`);
  console.log('Data root:', root);

  const isWin = process.platform === 'win32';
  javaProcess = spawn(java, args, {
    cwd: root,
    env,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: isWin,         // Use cmd.exe on Windows (bypasses SRP for whitelisted shells)
    windowsHide: true,    // No console window flash
  });

  javaProcess.stdout.on('data', (d) => console.log(`[Java] ${d}`));
  javaProcess.stderr.on('data', (d) => console.error(`[Java] ${d}`));
  javaProcess.on('error', (err) => console.error('Failed to start Java backend:', err));
  javaProcess.on('close', (code) => {
    console.log(`Java backend exited (code ${code})`);
    javaProcess = null;
  });
}

function stopJavaBackend() {
  if (!javaProcess) return;
  console.log('Stopping Java backend...');
  if (process.platform === 'win32') {
    spawn('taskkill', ['/pid', String(javaProcess.pid), '/f', '/t']);
  } else {
    javaProcess.kill('SIGTERM');
    setTimeout(() => {
      if (javaProcess) {
        try { javaProcess.kill('SIGKILL'); } catch (_) {}
      }
    }, 5000);
  }
}

// ── Window ──

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    icon: path.join(__dirname, 'assets', process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  mainWindow.loadFile('index.html');
}

// ── App lifecycle ──

// On Linux packaged mode, backend runs as a systemd service.
// On Windows (and dev mode), backend is a child process of Electron.
const useServiceMode = isPackaged && process.platform === 'linux';

app.whenReady().then(async () => {
  if (useServiceMode) {
    const running = await isBackendRunning();
    if (running) {
      console.log('Backend service already running – skipping manual start');
    } else {
      console.log('Backend service not detected – starting manually as fallback');
      //startJavaBackend();
    }
  } else {
    // Windows packaged + dev mode – always manage the backend from Electron
    //startJavaBackend();
  }

  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (!useServiceMode) {
    stopJavaBackend();
  }
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => {
  if (!useServiceMode) {
    stopJavaBackend();
  }
});

// ── IPC Handlers ──

ipcMain.handle('wait-for-backend', async () => {
  const maxAttempts = 60;
  const delay = 1000;
  for (let i = 0; i < maxAttempts; i++) {
    try {
      const response = await fetch(`http://127.0.0.1:${API_PORT}/api/test`);
      if (response.ok) {
        console.log('Backend is ready');
        return true;
      }
    } catch (_) { /* retry */ }
    await new Promise((r) => setTimeout(r, delay));
  }
  throw new Error(`Backend not responding after ${maxAttempts} seconds`);
});

ipcMain.handle('open-external', async (_event, url) => {
  await shell.openExternal(url);
});

ipcMain.handle('fetch-tracks', async () => {
  try {
    const response = await fetch(`http://127.0.0.1:${API_PORT}/api/tracks`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } catch (err) {
    console.error('fetch-tracks failed:', err);
    return [];
  }
});

ipcMain.handle('play-track', async (_event, trackId) => {
  return `http://127.0.0.1:${API_PORT}/api/stream/${trackId}`;
});

ipcMain.handle('download-track', async (_event, trackId) => {
  return `http://127.0.0.1:${API_PORT}/api/download/${trackId}`;
});

ipcMain.handle('search-tracks', async (_event, query) => {
  try {
    const response = await fetch(`http://127.0.0.1:${API_PORT}/api/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } catch (err) {
    console.error('search failed:', err);
    return [];
  }
});

ipcMain.handle('upload-files', async (_event, filePaths) => {
  const formData = new FormData();
  for (const filePath of filePaths) {
    const buffer = await fsPromises.readFile(filePath);
    const blob = new Blob([buffer]);
    formData.append('files', blob, path.basename(filePath));
  }
  try {
    const res = await fetch(`http://127.0.0.1:${API_PORT}/api/upload`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
    return { success: true };
  } catch (err) {
    console.error('upload error:', err);
    return { success: false, error: err.message };
  }
});

ipcMain.handle('fetch-torrent', async (_event, hash, metadata) => {
  try {
    const res = await fetch(`http://127.0.0.1:${API_PORT}/api/fetch-torrent/${hash}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: metadata ? JSON.stringify(metadata) : '{}',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (err) {
    console.error('fetch-torrent failed:', err);
    return { success: false, error: err.message };
  }
});

ipcMain.handle('fetch-logs', async (_event, lines = 200) => {
  try {
    const response = await fetch(`http://127.0.0.1:${API_PORT}/api/logs?lines=${lines}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.text();
  } catch (err) {
    console.error('fetch-logs failed:', err);
    return `Failed to fetch logs: ${err.message}`;
  }
});

// ── Last.fm Auth Server ──

ipcMain.handle('start-auth-server', async () => {
  if (authServer) {
    await new Promise((resolve) => {
      authServer.close(() => { authServer = null; resolve(); });
    });
  }
  return new Promise((resolve, reject) => {
    let resolved = false;
    const timeout = setTimeout(() => {
      if (!resolved) {
        if (authServer) authServer.close(() => { authServer = null; });
        reject(new Error('Authorization timed out after 5 minutes.'));
      }
    }, 5 * 60 * 1000);

    const server = http.createServer((req, res) => {
      try {
        const url = new URL(`http://localhost:9876${req.url}`);
        const token = url.searchParams.get('token');
        if (token) {
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end('<html><body><h1>Authorization successful!</h1><p>You can close this window.</p></body></html>');
          server.close(() => { authServer = null; });
          clearTimeout(timeout);
          resolved = true;
          resolve(token);
        } else {
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end('<html><body><p>Waiting for authorization...</p></body></html>');
        }
      } catch (err) {
        if (!resolved) { res.writeHead(500); res.end('Error'); }
      }
    });

    server.listen(9876, () => { authServer = server; });
    server.on('error', (err) => {
      if (!resolved) { clearTimeout(timeout); resolved = true; reject(err); }
      authServer = null;
    });
  });
});