import { loadHome } from './home.js';
import { refreshAllTracks } from './state.js';

export let uploadModal = null;
export let uploadFiles = [];
export let uploadStep = 1;
export let uploadMetadata = {};
export let isAlbum = false;
export let tempAlbumArtFile = null;

export function createUploadModal() {
  const modal = document.createElement('div');
  modal.id = 'uploadModal';
  modal.style.cssText = `
    display: none;
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0,0,0,0.8);
    z-index: 1000;
    justify-content: center;
    align-items: center;
  `;
  modal.innerHTML = `
    <div style="background: #282828; width: 700px; max-width: 90%; border-radius: 10px; padding: 20px; color: white;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
        <h2 id="uploadModalTitle">Step 1: Select Files</h2>
        <button id="uploadModalClose" style="background: none; border: none; color: white; font-size: 24px; cursor: pointer;">&times;</button>
      </div>
      <div id="uploadModalContent"></div>
      <div style="display: flex; justify-content: space-between; margin-top: 20px;">
        <button id="uploadModalPrev" style="background: #333; border: none; color: white; padding: 8px 16px; border-radius: 5px; cursor: pointer;" disabled>Previous</button>
        <button id="uploadModalNext" style="background: #b9093a; border: none; color: white; padding: 8px 16px; border-radius: 5px; cursor: pointer;">Next</button>
      </div>
    </div>
  `;
  document.body.appendChild(modal);
  document.getElementById('uploadModalClose').addEventListener('click', closeUploadModal);
  document.getElementById('uploadModalPrev').addEventListener('click', prevUploadStep);
  document.getElementById('uploadModalNext').addEventListener('click', nextUploadStep);
  return modal;
}

export function openUploadModal() {
  if (!uploadModal) uploadModal = createUploadModal();
  uploadModal.style.display = 'flex';
  uploadStep = 1;
  uploadFiles = [];
  uploadMetadata = {};
  isAlbum = false;
  tempAlbumArtFile = null;
  renderUploadStep();
}

export function closeUploadModal() {
  if (uploadModal) uploadModal.style.display = 'none';
  tempAlbumArtFile = null;
  uploadMetadata = {};
}

function prevUploadStep() {
  if (uploadStep > 1) {
    uploadStep--;
    renderUploadStep();
  }
}

function nextUploadStep() {
  if (uploadStep === 1) {
    if (uploadFiles.length === 0) {
      alert('Please select at least one file.');
      return;
    }
    isAlbum = uploadFiles.length > 1;
    uploadStep = 2;
    renderUploadStep();
  } else if (uploadStep === 2) {
    if (isAlbum) {
      const artistInput = document.getElementById('albumArtist');
      const albumInput = document.getElementById('albumTitle');
      if (!artistInput || !artistInput.value.trim()) {
        alert('Artist is required.');
        return;
      }
      if (!albumInput || !albumInput.value.trim()) {
        alert('Album title is required.');
        return;
      }
      uploadMetadata = {
        artist: artistInput.value,
        album: albumInput.value,
        year: parseInt(document.getElementById('albumYear')?.value) || 0,
        genre: document.getElementById('albumGenre')?.value || '',
        tracks: [],
        albumArtFile: tempAlbumArtFile
      };
      const trackNumbers = document.querySelectorAll('.trackNumber');
      const trackTitles = document.querySelectorAll('.trackTitle');
      const trackArtists = document.querySelectorAll('.trackArtist');
      for (let i = 0; i < uploadFiles.length; i++) {
        uploadMetadata.tracks.push({
          trackNumber: trackNumbers[i]?.value || (i+1).toString(),
          title: trackTitles[i]?.value || uploadFiles[i].name.replace(/\.[^/.]+$/, ''),
          artist: trackArtists[i]?.value || uploadMetadata.artist
        });
      }
    } else {
      const artistInput = document.getElementById('metaArtist');
      const titleInput = document.getElementById('metaTitle');
      if (!artistInput || !artistInput.value.trim()) {
        alert('Artist is required.');
        return;
      }
      if (!titleInput || !titleInput.value.trim()) {
        alert('Title is required.');
        return;
      }
      uploadMetadata = {
        title: titleInput.value,
        artist: artistInput.value,
        album: document.getElementById('metaAlbum')?.value || '',
        year: parseInt(document.getElementById('metaYear')?.value) || 0,
        genre: document.getElementById('metaGenre')?.value || '',
        trackNumber: document.getElementById('metaTrackNumber')?.value || '',
        discNumber: document.getElementById('metaDiscNumber')?.value || '',
        albumArtFile: tempAlbumArtFile
      };
    }
    uploadStep = 3;
    renderUploadStep();
  } else if (uploadStep === 3) {
    performUpload();
  }
}

function renderUploadStep() {
  const content = document.getElementById('uploadModalContent');
  const prevBtn = document.getElementById('uploadModalPrev');
  const nextBtn = document.getElementById('uploadModalNext');
  const title = document.getElementById('uploadModalTitle');

  if (uploadStep === 1) {
    title.textContent = 'Step 1: Select Files';
    prevBtn.disabled = true;
    nextBtn.disabled = false;
    nextBtn.textContent = 'Next';
    content.innerHTML = `
      <div style="text-align: center; padding: 20px;">
        <input type="file" id="modalFileInput" multiple accept="audio/*" style="display: none;">
        <button id="modalSelectFiles" style="background: #b9093a; color: white; border: none; padding: 10px 20px; border-radius: 5px; cursor: pointer;">Choose Files</button>
        <div id="modalFileList" style="margin-top: 20px; max-height: 300px; overflow-y: auto;"></div>
      </div>
    `;
    document.getElementById('modalSelectFiles').addEventListener('click', () => {
      document.getElementById('modalFileInput').click();
    });
    document.getElementById('modalFileInput').addEventListener('change', (e) => {
      uploadFiles = Array.from(e.target.files);
      const listDiv = document.getElementById('modalFileList');
      listDiv.innerHTML = uploadFiles.map(f => `<div style="padding: 5px; background: #333; margin: 2px;">${f.name}</div>`).join('');
    });
  } else if (uploadStep === 2) {
    title.textContent = isAlbum ? 'Step 2: Album Metadata' : 'Step 2: Track Metadata';
    prevBtn.disabled = false;
    nextBtn.textContent = 'Next';
    if (isAlbum) {
      renderAlbumMetadataForm(content);
    } else {
      renderSingleMetadataForm(content);
    }
  } else if (uploadStep === 3) {
    title.textContent = 'Step 3: Confirm and Upload';
    prevBtn.disabled = false;
    nextBtn.textContent = 'Upload';
    content.innerHTML = `
      <div style="text-align: center; padding: 20px;">
        <p>You are about to upload ${uploadFiles.length} file(s) as ${isAlbum ? 'an album' : 'a single track'}.</p>
        <p>Metadata will be applied as entered.</p>
        <p>Click Upload to proceed.</p>
      </div>
    `;
  }
}

function renderSingleMetadataForm(container) {
  container.innerHTML = `
    <div style="max-height: 400px; overflow-y: auto;">
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Title:</label>
        <input type="text" id="metaTitle" value="${uploadFiles[0]?.name.replace(/\.[^/.]+$/, '') || ''}">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Artist:</label>
        <input type="text" id="metaArtist" value="Unknown">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Album:</label>
        <input type="text" id="metaAlbum" value="Unknown">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Year:</label>
        <input type="number" id="metaYear" value="0">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Genre:</label>
        <input type="text" id="metaGenre" value="Unknown">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Track Number:</label>
        <input type="text" id="metaTrackNumber" value="">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Disc Number:</label>
        <input type="text" id="metaDiscNumber" value="">
      </div>
      <div style="margin-top: 20px;">
        <label style="display: block; margin-bottom: 10px;">Album Art (optional):</label>
        <div style="display: flex; align-items: center; gap: 15px;">
          <button id="selectAlbumArtBtn" style="background: #b9093a; color: white; border: none; padding: 8px 16px; border-radius: 5px; cursor: pointer;">Choose Image</button>
          <span id="albumArtFileName" style="color: #b3b3b3;"></span>
        </div>
        <div id="albumArtPreview" style="margin-top: 10px; max-width: 150px; max-height: 150px; border: 1px dashed #444; border-radius: 5px; display: flex; align-items: center; justify-content: center; overflow: hidden;">
          <img id="albumArtPreviewImg" style="max-width: 100%; max-height: 100%; object-fit: contain; display: none;">
        </div>
        <input type="file" id="metaAlbumArt" accept="image/*" style="display: none;">
      </div>
    </div>
  `;

  const fileInput = document.getElementById('metaAlbumArt');
  const selectBtn = document.getElementById('selectAlbumArtBtn');
  const fileNameSpan = document.getElementById('albumArtFileName');
  const previewImg = document.getElementById('albumArtPreviewImg');
  const previewDiv = document.getElementById('albumArtPreview');

  selectBtn.addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
      tempAlbumArtFile = file;
      fileNameSpan.textContent = file.name;
      const reader = new FileReader();
      reader.onload = (event) => {
        previewImg.src = event.target.result;
        previewImg.style.display = 'block';
      };
      reader.readAsDataURL(file);
    } else {
      tempAlbumArtFile = null;
      fileNameSpan.textContent = '';
      previewImg.src = '';
      previewImg.style.display = 'none';
    }
  });
}

function renderAlbumMetadataForm(container) {
  container.innerHTML = `
    <div style="max-height: 400px; overflow-y: auto;">
      <h3>Album Info</h3>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Artist:</label>
        <input type="text" id="albumArtist" value="Unknown">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Album Title:</label>
        <input type="text" id="albumTitle" value="Unknown Album">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Year:</label>
        <input type="number" id="albumYear" value="0">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 10px; margin-bottom: 10px;">
        <label>Genre:</label>
        <input type="text" id="albumGenre" value="Unknown">
      </div>
      <h3>Tracks</h3>
      <table style="width:100%; border-collapse: collapse; margin-bottom: 10px;">
        <thead>
          <tr style="background: #333;">
            <th>#</th>
            <th>Title</th>
            <th>Artist (if different)</th>
          </tr>
        </thead>
        <tbody id="trackTableBody">
          ${uploadFiles.map((file, idx) => `
            <tr>
              <td><input type="text" class="trackNumber" data-index="${idx}" style="width:50px;" value="${idx+1}"></td>
              <td><input type="text" class="trackTitle" data-index="${idx}" style="width:100%;" value="${file.name.replace(/\.[^/.]+$/, '')}"></td>
              <td><input type="text" class="trackArtist" data-index="${idx}" style="width:100%;" placeholder="(same as album)"></td>
            </tr>
          `).join('')}
        </tbody>
      </table>
      <div style="margin-top: 20px;">
        <label style="display: block; margin-bottom: 10px;">Album Art (optional):</label>
        <div style="display: flex; align-items: center; gap: 15px;">
          <button id="selectAlbumArtBtn" style="background: #b9093a; color: white; border: none; padding: 8px 16px; border-radius: 5px; cursor: pointer;">Choose Image</button>
          <span id="albumArtFileName" style="color: #b3b3b3;"></span>
        </div>
        <div id="albumArtPreview" style="margin-top: 10px; max-width: 150px; max-height: 150px; border: 1px dashed #444; border-radius: 5px; display: flex; align-items: center; justify-content: center; overflow: hidden;">
          <img id="albumArtPreviewImg" style="max-width: 100%; max-height: 100%; object-fit: contain; display: none;">
        </div>
        <input type="file" id="metaAlbumArt" accept="image/*" style="display: none;">
      </div>
    </div>
  `;

  const fileInput = document.getElementById('metaAlbumArt');
  const selectBtn = document.getElementById('selectAlbumArtBtn');
  const fileNameSpan = document.getElementById('albumArtFileName');
  const previewImg = document.getElementById('albumArtPreviewImg');
  const previewDiv = document.getElementById('albumArtPreview');

  selectBtn.addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
      tempAlbumArtFile = file;
      fileNameSpan.textContent = file.name;
      const reader = new FileReader();
      reader.onload = (event) => {
        previewImg.src = event.target.result;
        previewImg.style.display = 'block';
      };
      reader.readAsDataURL(file);
    } else {
      tempAlbumArtFile = null;
      fileNameSpan.textContent = '';
      previewImg.src = '';
      previewImg.style.display = 'none';
    }
  });
}

async function performUpload() {
  if (isAlbum) {
    await performAlbumUpload();
  } else {
    await performSingleUpload();
  }
}

async function performSingleUpload() {
  const metadata = uploadMetadata;
  const artInput = metadata.albumArtFile;
  if (artInput) {
    const reader = new FileReader();
    reader.onload = async (e) => {
      metadata.albumArtBase64 = e.target.result.split(',')[1];
      metadata.albumArtMimeType = artInput.type;
      await doUpload(metadata);
    };
    reader.readAsDataURL(artInput);
  } else {
    await doUpload(metadata);
  }
}

async function performAlbumUpload() {
  const albumMetadata = uploadMetadata;
  const artInput = albumMetadata.albumArtFile;
  if (artInput) {
    const reader = new FileReader();
    reader.onload = async (e) => {
      albumMetadata.albumArtBase64 = e.target.result.split(',')[1];
      albumMetadata.albumArtMimeType = artInput.type;
      await doAlbumUpload(albumMetadata);
    };
    reader.readAsDataURL(artInput);
  } else {
    await doAlbumUpload(albumMetadata);
  }
}

async function doUpload(metadata) {
  const formData = new FormData();
  uploadFiles.forEach(file => formData.append('files', file));
  formData.append('metadata', JSON.stringify(metadata));

  try {
    const response = await fetch('http://127.0.0.1:4567/api/upload', {
      method: 'POST',
      body: formData
    });
    if (!response.ok) throw new Error('Upload failed');
    alert('Upload successful!');
    closeUploadModal();
    await refreshAllTracks();
    loadHome();
  } catch (err) {
    alert('Upload error: ' + err.message);
  }
}

async function doAlbumUpload(albumMetadata) {
  const formData = new FormData();
  uploadFiles.forEach(file => formData.append('files', file));
  albumMetadata.isAlbum = true;
  formData.append('metadata', JSON.stringify(albumMetadata));

  try {
    const response = await fetch('http://127.0.0.1:4567/api/upload', {
      method: 'POST',
      body: formData
    });
    if (!response.ok) throw new Error('Album upload failed');
    alert('Album upload successful!');
    closeUploadModal();
    await refreshAllTracks();
    loadHome();
  } catch (err) {
    alert('Upload error: ' + err.message);
  }
}