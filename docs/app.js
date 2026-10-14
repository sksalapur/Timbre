// Timbre Dynamic Release Fetcher & Interactive Elements
const GITHUB_REPO = 'sksalapur/Timbre';
const FALLBACK_VERSION = 'v1.8-2';
const FALLBACK_DOWNLOAD_URL = `https://github.com/${GITHUB_REPO}/releases/latest`;

async function fetchLatestRelease() {
  const btnArm64 = document.getElementById('btn-download-arm64');
  const btnUniversal = document.getElementById('btn-download-universal');
  const tagBadges = document.querySelectorAll('.release-tag');
  const dateBadge = document.getElementById('release-date');

  try {
    const response = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    
    const release = await response.json();
    const tagName = release.tag_name || FALLBACK_VERSION;
    
    // Update version badges
    tagBadges.forEach(el => el.textContent = tagName);

    // Format publish date
    if (release.published_at && dateBadge) {
      const date = new Date(release.published_at);
      dateBadge.textContent = date.toLocaleDateString(undefined, { month: 'short', year: 'numeric', day: 'numeric' });
    }

    // Match assets
    if (release.assets && release.assets.length > 0) {
      const arm64Asset = release.assets.find(a => 
        (a.name.toLowerCase().includes('arm64') || a.name.toLowerCase().includes('v8a')) && a.name.endsWith('.apk')
      );
      const universalAsset = release.assets.find(a => 
        a.name.toLowerCase().includes('universal') && a.name.endsWith('.apk')
      ) || release.assets.find(a => a.name.endsWith('.apk') && !a.name.toLowerCase().includes('arm'));

      if (arm64Asset && btnArm64) {
        btnArm64.href = arm64Asset.browser_download_url;
        const sizeMb = (arm64Asset.size / (1024 * 1024)).toFixed(1);
        const labelEl = btnArm64.querySelector('.btn-label');
        const subEl = btnArm64.querySelector('.btn-sub');
        if (labelEl) labelEl.textContent = `Download Now (${tagName})`;
        if (subEl) subEl.textContent = `ARM64 · ${sizeMb} MB · Recommended`;
      } else if (release.assets.some(a => a.name.endsWith('.apk')) && btnArm64) {
        const firstApk = release.assets.find(a => a.name.endsWith('.apk'));
        btnArm64.href = firstApk.browser_download_url;
        const sizeMb = (firstApk.size / (1024 * 1024)).toFixed(1);
        const labelEl = btnArm64.querySelector('.btn-label');
        const subEl = btnArm64.querySelector('.btn-sub');
        if (labelEl) labelEl.textContent = `Download Now (${tagName})`;
        if (subEl) subEl.textContent = `${sizeMb} MB · Android 8.0+`;
      }

      if (universalAsset && btnUniversal) {
        btnUniversal.href = universalAsset.browser_download_url;
        const sizeMb = (universalAsset.size / (1024 * 1024)).toFixed(1);
        const labelEl = btnUniversal.querySelector('.btn-label');
        if (labelEl) labelEl.textContent = `Universal APK (${sizeMb} MB)`;
      }
    }
  } catch (err) {
    console.warn('Live release fetch fallback:', err);
    if (btnArm64) btnArm64.href = FALLBACK_DOWNLOAD_URL;
    if (btnUniversal) btnUniversal.href = FALLBACK_DOWNLOAD_URL;
    tagBadges.forEach(el => el.textContent = FALLBACK_VERSION);
  }
}

// Generate animated waveform bars in the hero mockup
function initWaveform() {
  const container = document.getElementById('waveform-bars');
  if (!container) return;
  
  const barCount = 36;
  container.innerHTML = '';
  
  for (let i = 0; i < barCount; i++) {
    const bar = document.createElement('div');
    bar.className = 'wave-bar';
    bar.style.animationDelay = `${(i * 0.08) % 1.5}s`;
    bar.style.animationDuration = `${0.9 + Math.random() * 0.8}s`;
    container.appendChild(bar);
  }
}

// Live interactive slider simulator
function initSliderSimulator() {
  const slider = document.getElementById('demo-timeout-slider');
  const readout = document.getElementById('demo-timeout-val');
  if (!slider || !readout) return;

  slider.addEventListener('input', (e) => {
    readout.textContent = `${parseFloat(e.target.value).toFixed(1)}s`;
  });
}

document.addEventListener('DOMContentLoaded', () => {
  fetchLatestRelease();
  initWaveform();
  initSliderSimulator();
});
