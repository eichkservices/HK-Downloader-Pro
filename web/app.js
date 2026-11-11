document.addEventListener('DOMContentLoaded', () => {

  // ── Fair-Use & Abuse Prevention: Daily Download Quota & Policy Modal ──
  const DAILY_DOWNLOAD_LIMIT = 30;
  function checkDailyDownloadQuota() {
    const today = new Date().toISOString().slice(0, 10);
    try {
      const raw = localStorage.getItem('hk_dl_quota');
      let data = raw ? JSON.parse(raw) : null;
      if (!data || data.date !== today) {
        data = { date: today, count: 0 };
      }
      if (data.count >= DAILY_DOWNLOAD_LIMIT) {
        return false;
      }
      data.count += 1;
      localStorage.setItem('hk_dl_quota', JSON.stringify(data));
      return true;
    } catch (e) {
      return true;
    }
  }

  const policyModal = document.getElementById('policyModal');
  const btnPolicyModal = document.getElementById('btnPolicyModal');
  const closePolicyModal = document.getElementById('closePolicyModal');
  const btnUnderstandPolicy = document.getElementById('btnUnderstandPolicy');

  if (btnPolicyModal && policyModal) {
    btnPolicyModal.addEventListener('click', (e) => {
      e.preventDefault();
      policyModal.classList.remove('hidden');
    });
  }
  if (closePolicyModal && policyModal) {
    closePolicyModal.addEventListener('click', () => policyModal.classList.add('hidden'));
  }
  if (btnUnderstandPolicy && policyModal) {
    btnUnderstandPolicy.addEventListener('click', () => policyModal.classList.add('hidden'));
  }
  if (policyModal) {
    policyModal.addEventListener('click', (e) => {
      if (e.target === policyModal) policyModal.classList.add('hidden');
    });
  }

  // Safe Element Helper
  const getEl = (id) => document.getElementById(id) || document.createElement('div');
  const getBtn = (id) => document.getElementById(id) || document.createElement('button');

  // Futuristic UI Elements (matching 3.html & hk-reference-markup.html)
  const flowRoot           = document.getElementById('flow');
  const fbx                = document.getElementById('fbx');
  const fbtn               = document.getElementById('fbtn');
  const fst                = document.getElementById('fst');
  const pv                 = document.getElementById('pv');
  const pvThumb            = document.getElementById('pvThumb');
  const pvTitle            = document.getElementById('pvTitle');
  const pvSubtitle         = document.getElementById('pvSubtitle');
  const pvQualities        = document.getElementById('pvQualities');
  const dbtn               = document.getElementById('dbtn');

  // Slide-Up Progress Sheet Elements
  const sheet              = document.getElementById('sheet');
  const scrim              = document.getElementById('scrim');
  const sttl               = document.getElementById('sttl');
  const stt                = document.getElementById('stt');
  const sheetMediaTitle    = document.getElementById('sheetMediaTitle');
  const sheetThumb         = document.getElementById('sheetThumb');
  const sheetTags          = document.getElementById('sheetTags');
  const fl                 = document.getElementById('fl');
  const pp                 = document.getElementById('pp');
  const sz                 = document.getElementById('sz');
  const sp                 = document.getElementById('sp');
  const eta                = document.getElementById('eta');
  const btnMinimizeSheet   = document.getElementById('btnMinimizeSheet');
  const btnCloseSheet      = document.getElementById('btnCloseSheet');
  const btnCancelSheet     = document.getElementById('btnCancelSheet');

  // Complete Screen Modal Elements
  const completeView       = document.getElementById('completeView');
  const compPercent        = document.getElementById('compPercent');
  const compSavedText      = document.getElementById('compSavedText');
  const compTitle          = document.getElementById('compTitle');
  const compThumb          = document.getElementById('compThumb');
  const compTags           = document.getElementById('compTags');
  const btnPlayPlayer      = document.getElementById('btnPlayPlayer');
  const btnSaveDevice      = document.getElementById('btnSaveDevice');
  const btnShareComp       = document.getElementById('btnShareComp');
  const btnNavLibComp      = document.getElementById('btnNavLibComp');
  const btnNewLinkComp     = document.getElementById('btnNewLinkComp');

  // Bottom Dock Navigation
  const dockNavDownloader  = document.getElementById('dockNavDownloader');
  const dockNavPlus        = document.getElementById('dockNavPlus');
  const dockNavLibrary     = document.getElementById('dockNavLibrary');

  // Core Inputs & Handlers
  const urlInput           = document.getElementById('urlInput') || getEl('urlInput');
  const downloadForm       = document.getElementById('downloadForm') || getEl('downloadForm');
  const analyzeBtn         = document.getElementById('analyzeBtn') || fbtn || getBtn('analyzeBtn');
  const btnText            = document.getElementById('btnText') || getEl('btnText');
  const btnSpinner         = document.getElementById('btnSpinner') || getEl('btnSpinner');

  // Legacy & Fallback elements (null-safe)
  const formatModal        = getEl('formatModal');
  const closeModalBtn      = getBtn('closeModalBtn');
  const modalThumb         = getEl('modalThumb');
  const modalThumbFallback = getEl('modalThumbFallback');
  const modalTitle         = getEl('modalTitle');
  const modalTypeBadge     = getEl('modalTypeBadge');
  const formatList         = getEl('formatList');

  const taskDownloadModal    = getEl('taskDownloadModal');
  const taskStatusHeading    = getEl('taskStatusHeading');
  const btnMinimizeTask      = getBtn('btnMinimizeTask');
  const btnCancelTaskModal   = getBtn('btnCancelTaskModal');
  const taskThumb            = getEl('taskThumb');
  const taskThumbFallback    = getEl('taskThumbFallback');
  const taskTitle            = getEl('taskTitle');
  const taskQualityBadge     = getEl('taskQualityBadge');
  const taskSpeedEtaBadge    = getEl('taskSpeedEtaBadge');
  const taskProgressPct      = getEl('taskProgressPct');
  const taskProgressSize     = getEl('taskProgressSize');
  const taskProgressBar      = getEl('taskProgressBar');
  const taskAdCountdown      = getEl('taskAdCountdown');
  const taskAdContainer      = getEl('taskAdContainer');
  const taskAdVideo          = document.getElementById('taskAdVideo');
  const btnTaskAdAudio       = getBtn('btnTaskAdAudio');
  const adAudioMutedIcon     = getEl('adAudioMutedIcon');
  const adAudioUnmutedIcon   = getEl('adAudioUnmutedIcon');
  const taskAdVideoTitle     = getEl('taskAdVideoTitle');
  const taskAdVideoDomain    = getEl('taskAdVideoDomain');
  const taskAdVideoCta       = getEl('taskAdVideoCta');
  const taskAdCtaText        = getEl('taskAdCtaText');
  const taskCompleteActions  = getEl('taskCompleteActions');
  const btnTaskPlay          = getBtn('btnTaskPlay');
  const btnTaskSave          = getBtn('btnTaskSave');
  const btnTaskCloseDone     = getBtn('btnTaskCloseDone');

  if (btnTaskAdAudio && taskAdVideo) {
    btnTaskAdAudio.addEventListener('click', (e) => {
      e.stopPropagation();
      taskAdVideo.muted = !taskAdVideo.muted;
      if (adAudioMutedIcon && adAudioUnmutedIcon) {
        adAudioMutedIcon.classList.toggle('hidden', !taskAdVideo.muted);
        adAudioUnmutedIcon.classList.toggle('hidden', taskAdVideo.muted);
      }
    });
  }

  if (taskDownloadModal) {
    taskDownloadModal.addEventListener('click', (e) => {
      if (e.target === taskDownloadModal) {
        taskDownloadModal.classList.add('hidden');
        if (taskAdVideo) taskAdVideo.pause();
      }
    });
  }

  // ── APEX Engine: Rolling Speed Sampler (800ms window → accurate, smooth readouts)
  class SpeedSampler {
    constructor(windowMs = 800) {
      this.windowMs = windowMs;
      this.samples = [];
    }
    add(bytes) {
      const now = Date.now();
      this.samples.push({ ts: now, bytes });
      const cutoff = now - this.windowMs;
      while (this.samples.length > 0 && this.samples[0].ts < cutoff) {
        this.samples.shift();
      }
    }
    get speed() {
      if (this.samples.length < 2) return 0;
      const total = this.samples.reduce((s, x) => s + x.bytes, 0);
      const span = this.samples[this.samples.length - 1].ts - this.samples[0].ts;
      return span > 0 ? (total / span) * 1000 : 0;
    }
  }

  function cleanUrl(rawUrl) {
    if (!rawUrl) return '';
    let u = rawUrl.trim().replace(/^["'<(\[{`\s]+|["'>)\]}`.,;:\s]+$/g, '');
    try {
      const parsed = new URL(u);
      const tracking = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'utm', 'si', 'feature', 'fbclid', 'igsh', 'ref', 'usp', '_ga', '_gl', 'mibextid'];
      for (const p of tracking) {
        parsed.searchParams.delete(p);
      }
      return parsed.toString();
    } catch(e) {
      return u;
    }
  }

  // Bold toast banner for completion/failure -- appears at the top of the
  // app shell, auto-dismisses. isError swaps to the red/failure treatment.
  const toastContainer = document.getElementById('toastContainer');
  function showToast(title, subtitle, isError) {
    if (!toastContainer) return;
    const toast = document.createElement('div');
    toast.className = 'toast-banner' + (isError ? ' toast-error' : '');
    const iconSvg = isError
      ? '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>'
      : '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><polyline points="20 6 9 17 4 12"></polyline></svg>';
    toast.innerHTML = `
      <div class="toast-icon">${iconSvg}</div>
      <div>
        <div class="toast-title">${title}</div>
        <div class="toast-subtitle">${subtitle}</div>
      </div>
    `;
    toastContainer.appendChild(toast);
    requestAnimationFrame(() => requestAnimationFrame(() => toast.classList.add('show')));
    setTimeout(() => {
      toast.classList.remove('show');
      setTimeout(() => toast.remove(), 400);
    }, 3200);
  }

  // Very tiny, subtle 140ms two-note crystal completion chime via Web Audio API
  function playTinyCompletionChime() {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const now = ctx.currentTime;

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, now);          // A5 (55ms)
      osc.frequency.setValueAtTime(1318.5, now + 0.055); // E6 (85ms)

      gain.gain.setValueAtTime(0.001, now);
      gain.gain.linearRampToValueAtTime(0.055, now + 0.008);
      gain.gain.exponentialRampToValueAtTime(0.0008, now + 0.145);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now);
      osc.stop(now + 0.15);
      setTimeout(() => { try { ctx.close(); } catch (e) {} }, 250);
    } catch (e) {}
  }

  // Persistent Live Active Download Notification Toast Pill
  let activeDlToastEl = null;
  function updateActiveDownloadToast(title, pct, speedText, onClickOpen) {
    if (!toastContainer) return;
    if (!activeDlToastEl) {
      activeDlToastEl = document.createElement('div');
      activeDlToastEl.className = 'toast-banner active-dl-toast show';
      activeDlToastEl.style.cursor = 'pointer';
      activeDlToastEl.style.pointerEvents = 'auto';
      activeDlToastEl.innerHTML = `
        <div class="toast-icon" style="background: rgba(255,138,61,0.18); color: #ff8a3d;">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
        </div>
        <div style="flex:1; min-width:0;">
          <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
            <div class="toast-title" id="activeToastTitle" style="color:#ff8a3d; font-size:11px;">Active Download</div>
            <span id="activeToastPct" style="color:#22d3ee; font-size:11px; font-weight:800;">0%</span>
          </div>
          <div class="toast-subtitle" id="activeToastSub" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">Downloading...</div>
          <div style="width:100%; height:4px; background:rgba(255,255,255,0.12); border-radius:99px; margin-top:5px; overflow:hidden;">
            <div id="activeToastBar" style="height:100%; width:5%; background:linear-gradient(90deg, #ff8a3d, #22d3ee); border-radius:99px; transition:width 0.2s ease;"></div>
          </div>
        </div>
      `;
      toastContainer.prepend(activeDlToastEl);
    }
    if (onClickOpen) activeDlToastEl.onclick = onClickOpen;
    const pctEl = activeDlToastEl.querySelector('#activeToastPct');
    const subEl = activeDlToastEl.querySelector('#activeToastSub');
    const barEl = activeDlToastEl.querySelector('#activeToastBar');
    if (pctEl) pctEl.textContent = pct != null ? `${pct}%` : 'LIVE';
    if (subEl) subEl.textContent = `${title || 'Media'} • ${speedText || 'Downloading...'}`;
    if (barEl) barEl.style.width = pct != null ? `${Math.max(4, pct)}%` : '35%';
  }

  function hideActiveDownloadToast() {
    if (activeDlToastEl) {
      activeDlToastEl.remove();
      activeDlToastEl = null;
    }
  }

  // Ambient animated background reacts to overall app activity. Only one
  // state at a time (this is a page-wide mood, not per-download-item), with
  // simple priority: analyzing > actively downloading > a brief flash for
  // the most recent complete/fail event > idle default.
  const ambientBg = document.getElementById('ambientBg');
  const AMBIENT_STATES = ['state-analyzing', 'state-downloading', 'state-completed', 'state-failed'];
  let activeDownloadCount = 0;
  let ambientFlashTimeout = null;

  function setAmbientState(state) {
    if (!ambientBg) return;
    ambientBg.classList.remove(...AMBIENT_STATES);
    if (state) ambientBg.classList.add(state);
  }

  function refreshAmbientFromActivity() {
    // Called whenever activeDownloadCount changes, or a flash finishes --
    // falls back to "downloading" if anything is still active, else idle.
    if (ambientFlashTimeout) return; // a completion/failure flash is still showing, let it finish
    setAmbientState(activeDownloadCount > 0 ? 'state-downloading' : null);
  }

  function flashAmbientState(state) {
    setAmbientState(state);
    clearTimeout(ambientFlashTimeout);
    ambientFlashTimeout = setTimeout(() => {
      ambientFlashTimeout = null;
      refreshAmbientFromActivity();
    }, 2500);
  }

  // Custom Branded Media Player Modal
  const playerModal        = document.getElementById('playerModal');
  const playerTitle        = document.getElementById('playerTitle');
  const videoWrap          = document.getElementById('videoWrap') || document.querySelector('.video-container');
  const videoPlayer        = document.getElementById('videoPlayer') || document.getElementById('customVideo');
  const closePlayerBtn     = document.getElementById('closePlayerBtn');

  const btnPlayPause       = document.getElementById('btnPlayPause') || document.getElementById('mxPlayPause');
  const playerSeek         = document.getElementById('playerSeek') || document.getElementById('mxSeekBar');
  const playerProgress     = document.getElementById('playerProgress');
  const playerTime         = document.getElementById('playerTime') || document.getElementById('mxCurrentTime');
  const btnAspectToggle    = document.getElementById('btnAspectToggle');
  const btnOpenWithPlayer  = document.getElementById('btnOpenWithPlayer');
  const btnSharePlayer     = document.getElementById('btnSharePlayer');
  const btnFullscreen      = document.getElementById('btnFullscreen') || document.getElementById('mxFullscreen');

  // Delete Options Modal
  const deleteModal        = document.getElementById('deleteModal');
  const closeDeleteModalBtn= document.getElementById('closeDeleteModalBtn');
  const btnDelTaskOnly     = document.getElementById('btnDelTaskOnly');
  const btnDelTaskAndFile  = document.getElementById('btnDelTaskAndFile');

  const downloadsList      = document.getElementById('downloadsList');
  const emptyState         = document.getElementById('emptyState');
  const clearHistoryBtn    = document.getElementById('clearHistoryBtn');
  
  const libraryGrid        = document.getElementById('libraryGrid');
  const emptyLibrary       = document.getElementById('emptyLibrary');
  const libraryCount       = document.getElementById('libraryCount');

  const navItems           = document.querySelectorAll('.nav-item');
  const tabPages           = {
    downloader: document.getElementById('tabDownloader'),
    library:    document.getElementById('tabLibrary')
  };

  const libraryItems = [];
  let currentActiveBlobUrl = '';
  let currentActiveTitle   = '';
  let pendingDeleteCard    = null;
  let pendingDeleteLibItem = null;

  // Unified Install App Modal & PWA Installer
  let deferredPrompt;
  const btnHeaderInstall  = document.getElementById('btnHeaderInstall');
  const installModal      = document.getElementById('installModal');
  const closeInstallModal = document.getElementById('closeInstallModal');
  const btnModalApk       = document.getElementById('btnModalApk');
  const btnModalPwa       = document.getElementById('btnModalPwa');

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
  });

  if (btnHeaderInstall && installModal) {
    btnHeaderInstall.addEventListener('click', () => {
      installModal.classList.remove('hidden');
    });
  }

  if (closeInstallModal && installModal) {
    closeInstallModal.addEventListener('click', () => {
      installModal.classList.add('hidden');
    });
  }

  if (installModal) {
    installModal.addEventListener('click', (e) => {
      if (e.target === installModal) {
        installModal.classList.add('hidden');
      }
    });
  }

  if (btnModalApk && installModal) {
    btnModalApk.addEventListener('click', () => {
      showToast('⬇ Starting Android APK download...', 'success');
      setTimeout(() => installModal.classList.add('hidden'), 400);
    });
  }

  if (btnModalPwa) {
    btnModalPwa.addEventListener('click', async () => {
      if (installModal) installModal.classList.add('hidden');
      if (deferredPrompt) {
        deferredPrompt.prompt();
        const { outcome } = await deferredPrompt.userChoice;
        if (outcome === 'accepted') {
          showToast('✓ HK Downloader Pro added to your Home Screen!', 'success');
        }
        deferredPrompt = null;
      } else {
        const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
        if (isIOS) {
          showToast('📲 Tap Safari Share button (⎋), then select "Add to Home Screen"', 'info', 6000);
        } else {
          showToast('📲 Tap browser menu (⋮) in the top-right, then select "Add to Home screen"', 'info', 6000);
        }
      }
    });
  }

  // 1. Dynamic Button Text Toggle (Paste Link ↔ Download)
  if (urlInput) {
    urlInput.addEventListener('input', updateButtonText);
    urlInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        handlePasteOrAnalyze();
      }
    });
  }

  function updateButtonText() {
    if (!urlInput) return;
    const text = urlInput.value.trim();
    if (fbtn) fbtn.textContent = text.length > 0 ? 'GO' : 'PASTE';
    if (btnText) btnText.textContent = text.length > 0 ? 'Download' : 'Paste Link';
    if (fbx) {
      if (text.length > 0) fbx.classList.add('flash');
      else fbx.classList.remove('flash');
    }
  }
  updateButtonText();

  // Public Global Downloads Counter Engine
  let currentDownloadsCount = parseInt(localStorage.getItem('hk_global_downloads') || '14892', 10);
  const counterEl = document.getElementById('statGlobalDownloads');

  function renderDownloadsCount(num, animate = true) {
    if (!counterEl) return;
    if (animate && window.HKUI && window.HKUI.countUp) {
      window.HKUI.countUp(counterEl, num, '', 1200);
    } else {
      counterEl.textContent = num.toLocaleString();
    }
  }

  async function fetchGlobalDownloadsCount() {
    try {
      const res = await fetch('/api/counter');
      if (res.ok) {
        const data = await res.json();
        if (data && typeof data.count === 'number') {
          currentDownloadsCount = Math.max(currentDownloadsCount, data.count);
          localStorage.setItem('hk_global_downloads', currentDownloadsCount.toString());
          renderDownloadsCount(currentDownloadsCount, true);
          return;
        }
      }
    } catch (e) {}
    renderDownloadsCount(currentDownloadsCount, true);
  }

  window.incrementDownloadsCounter = async function () {
    currentDownloadsCount++;
    localStorage.setItem('hk_global_downloads', currentDownloadsCount.toString());
    renderDownloadsCount(currentDownloadsCount, false);
    if (counterEl) {
      counterEl.style.transform = 'scale(1.08)';
      counterEl.style.color = 'var(--accent-cyan)';
      setTimeout(() => {
        counterEl.style.transform = 'none';
        counterEl.style.color = '';
      }, 400);
    }
    try {
      fetch('/api/counter', { method: 'POST' }).catch(() => {});
    } catch (e) {}
  };

  fetchGlobalDownloadsCount();
  checkRecentEmpty();

  async function handlePasteOrAnalyze() {
    if (!urlInput) return;
    const link = urlInput.value.trim();
    if (!link) {
      try {
        const text = await navigator.clipboard.readText();
        if (text) {
          urlInput.value = text;
          updateButtonText();
          if (text.startsWith('http')) {
            analyzeUrl(text);
          }
        }
      } catch (err) {
        urlInput.focus();
      }
      return;
    }
    analyzeUrl(link);
  }

  if (fbtn) {
    fbtn.addEventListener('click', handlePasteOrAnalyze);
  }

  // Auto-analyze URL if passed via share query string (?url=...)
  const urlParams = new URLSearchParams(window.location.search);
  const sharedUrlParam = urlParams.get('url');
  if (sharedUrlParam) {
    urlInput.value = sharedUrlParam;
    updateButtonText();
    analyzeUrl(sharedUrlParam);
  }

  // 2. Tab Navigation
  navItems.forEach(item => {
    item.addEventListener('click', () => {
      const targetTab = item.getAttribute('data-tab');
      navItems.forEach(n => n.classList.remove('active'));
      item.classList.add('active');

      Object.keys(tabPages).forEach(key => {
        if (tabPages[key]) {
          tabPages[key].classList.toggle('hidden', key !== targetTab);
          tabPages[key].classList.toggle('active', key === targetTab);
        }
      });

      if (targetTab === 'library') renderLibraryGrid();
    });
  });

  // Bottom Dock Navigation Listeners
  if (dockNavDownloader) {
    dockNavDownloader.addEventListener('click', () => {
      dockNavDownloader.classList.add('on');
      if (dockNavLibrary) dockNavLibrary.classList.remove('on');
      const dlTab = document.getElementById('tabDownloader');
      const libTab = document.getElementById('tabLibrary');
      if (dlTab) { dlTab.classList.remove('hidden'); dlTab.classList.add('active'); }
      if (libTab) { libTab.classList.add('hidden'); libTab.classList.remove('active'); }
    });
  }

  if (dockNavLibrary) {
    dockNavLibrary.addEventListener('click', () => {
      dockNavLibrary.classList.add('on');
      if (dockNavDownloader) dockNavDownloader.classList.remove('on');
      const dlTab = document.getElementById('tabDownloader');
      const libTab = document.getElementById('tabLibrary');
      if (dlTab) { dlTab.classList.add('hidden'); dlTab.classList.remove('active'); }
      if (libTab) {
        libTab.classList.remove('hidden');
        libTab.classList.add('active');
        renderLibraryGrid();
      }
    });
  }

  if (dockNavPlus) {
    dockNavPlus.addEventListener('click', () => {
      if (dockNavDownloader) dockNavDownloader.click();
      handlePasteOrAnalyze();
    });
  }

  // Quality presets -- matches the Android app's QualityPreset values exactly,
  // so both platforms request the same thing from the same Cobalt instance.
  const QUALITY_PRESETS = {
    'best':       { videoQuality: 'max',  downloadMode: 'auto' },
    '1080':       { videoQuality: '1080', downloadMode: 'auto' },
    '720':        { videoQuality: '720',  downloadMode: 'auto' },
    '480':        { videoQuality: '480',  downloadMode: 'auto' },
    'audio-mp3':  { videoQuality: 'max',  downloadMode: 'audio', audioFormat: 'mp3', audioBitrate: '320' },
    'audio-opus': { videoQuality: 'max',  downloadMode: 'audio', audioFormat: 'opus' },
  };
  let selectedPreset = QUALITY_PRESETS['1080'];
  document.querySelectorAll('.quality-pill').forEach(pill => {
    pill.addEventListener('click', () => {
      document.querySelectorAll('.quality-pill').forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      selectedPreset = QUALITY_PRESETS[pill.getAttribute('data-preset')] || QUALITY_PRESETS['1080'];
    });
  });

  // 3. Form Submit / Paste Button Click (0 Popup Clipboard Paste!)
  downloadForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const link = urlInput.value.trim();

    if (!link) {
      try {
        const text = await navigator.clipboard.readText();
        if (text) {
          urlInput.value = text;
          updateButtonText();
          if (text.startsWith('http')) {
            analyzeUrl(text);
          }
        }
      } catch (err) {
        urlInput.focus();
      }
      return;
    }

    analyzeUrl(link);
  });

  // Universal Video Resolver (Works on Node Backend AND Static Cloudflare Pages!)
  async function analyzeUrl(rawLink) {
    const link = cleanUrl(rawLink);
    if (urlInput) urlInput.value = link;
    setAnalyzing(true);
    try {
      const type = identifyLinkType(link);
      
      // 1. Try backend POST request to /api/analyze
      try {
        const resp = await fetch('/api/analyze', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ inputUrl: link, preset: selectedPreset })
        });
        const contentType = resp.headers.get('content-type') || '';
        if (resp.ok && contentType.includes('application/json')) {
          const data = await resp.json();
          if (data.formats && data.formats.length > 0) {
            openFormatModal(data);
            return;
          }
        }
      } catch (e) {}

      // 2. Direct Vercel Cloud Backend Fallback (CORS-enabled)
      try {
        const vercelResp = await fetch('https://hk-downloader-pro.vercel.app/api/analyze', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ inputUrl: link, preset: selectedPreset })
        });
        if (vercelResp.ok) {
          const vData = await vercelResp.json();
          if (vData.formats && vData.formats.length > 0) {
            openFormatModal(vData);
            return;
          }
        }
      } catch (ve) {}

      // 3. Client-side Fallback (for direct links or custom cobalt)
      try {
        const clientData = await resolveClientSide(link, selectedPreset);
        if (clientData && clientData.formats && clientData.formats.length > 0) {
          openFormatModal(clientData);
          return;
        }
      } catch (clientErr) {
        console.warn('Client-side resolver fallback failed:', clientErr);
      }

      showToast('Download Unavailable', 'This link is restricted on web by the platform. Please use the HK Downloader Android App or retry.');
    } catch (err) {
      showToast('Analysis Error', err.message || 'Unable to resolve link');
    } finally {
      setAnalyzing(false);
    }
  }

  // Self-hosted Cobalt instance URL, configured in Settings. Cobalt's old
  // public API (api.cobalt.tools/api/json) was shut down in Nov 2024 and
  // there is no public pre-hosted API anymore -- self-hosting is required.
  function getCobaltInstanceUrl() {
    return (localStorage.getItem('cobaltInstanceUrl') || '').trim();
  }

  // Same platform list as the Android app -- verified against a live Cobalt
  // instance's own service registry, not a guess.
  const COBALT_PLATFORMS = {
    'youtube.com': 'YouTube', 'youtu.be': 'YouTube',
    'instagram.com': 'Instagram',
    'twitter.com': 'Twitter', 'x.com': 'Twitter',
    'facebook.com': 'Facebook', 'fb.watch': 'Facebook',
    'reddit.com': 'Reddit',
    'bilibili.com': 'Bilibili', 'bilibili.tv': 'Bilibili',
    'bsky.app': 'Bluesky',
    'dailymotion.com': 'Dailymotion', 'dai.ly': 'Dailymotion',
    'loom.com': 'Loom',
    'ok.ru': 'OK',
    'pinterest.com': 'Pinterest', 'pin.it': 'Pinterest',
    'rutube.ru': 'Rutube',
    'snapchat.com': 'Snapchat',
    'soundcloud.com': 'SoundCloud',
    'streamable.com': 'Streamable',
    'tumblr.com': 'Tumblr',
    'twitch.tv': 'Twitch',
    'vimeo.com': 'Vimeo',
    'vk.com': 'VK', 'vk.ru': 'VK',
    'xiaohongshu.com': 'Xiaohongshu', 'xhslink.com': 'Xiaohongshu',
    'drive.google.com': 'GoogleDrive',
    'dropbox.com': 'Dropbox',
    'mega.nz': 'Mega', 'mega.co.nz': 'Mega',
    'threads.net': 'Threads',
    'rumble.com': 'Rumble',
    'kick.com': 'Kick',
    'mediafire.com': 'MediaFire',
    'bandcamp.com': 'Bandcamp',
    'mixcloud.com': 'Mixcloud',
    'audiomack.com': 'Audiomack',
    'linkedin.com': 'LinkedIn',
    'likee.video': 'Likee',
    'kwai.com': 'Kuaishou', 'kuaishou.com': 'Kuaishou',
    'douyin.com': 'Douyin',
    'weibo.com': 'Weibo',
    '9gag.com': '9GAG',
    'ifunny.co': 'iFunny',
    'imgur.com': 'Imgur',
    'flickr.com': 'Flickr',
    'archive.org': 'Archive',
    'ted.com': 'TED'
  };

  function identifyLinkType(link) {
    const l = link.toLowerCase();
    if (l.includes('tiktok.com')) return 'tiktok';
    if (l.includes('drive.google.com')) return 'googledrive';
    if (l.includes('dropbox.com')) return 'dropbox';
    if (l.includes('mega.nz') || l.includes('mega.co.nz')) return 'mega';
    if (l.includes('dailymotion.com') || l.includes('dai.ly')) return 'dailymotion';
    for (const domain of Object.keys(COBALT_PLATFORMS)) {
      if (l.includes(domain)) return COBALT_PLATFORMS[domain].toLowerCase();
    }
    return 'direct-link';
  }

  // Remembers which resolver most recently succeeded for a given link type,
  // so next time we try that one FIRST instead of always starting from the
  // top of the static priority list. In-memory only (resets on page reload)
  // -- same design choice as the Android app, for the same reason: a
  // persisted version needs more plumbing than this warrants right now.
  const lastSuccessfulResolver = {};

  // ---- Individual resolvers -- each either returns {title, thumbnail,
  // formats, type} on success, or throws (caught and recorded as a failure
  // in the chain below). ----

  async function resolveTikwm(link) {
    const resp = await fetch(`https://www.tikwm.com/api/?url=${encodeURIComponent(link)}`);
    const data = await resp.json();
    if (data?.code !== 0 || !data.data) throw new Error('tikwm returned no usable data');
    const v = data.data;
    const formats = [];

    // 1. HD No Watermark (best quality if available)
    if (v.hdplay) formats.push({ directUrl: v.hdplay, note: '🎬 HD 1080p (No watermark)', ext: 'mp4', hasAudio: true });
    // 2. Standard No Watermark
    if (v.play) formats.push({ directUrl: v.play, note: '🎬 Video (No watermark)', ext: 'mp4', hasAudio: true });
    // 3. Watermarked
    if (v.wmplay) formats.push({ directUrl: v.wmplay, note: '🎬 Video (Watermarked)', ext: 'mp4', hasAudio: true });
    // 4. Music / Audio
    if (v.music) formats.push({ directUrl: v.music, note: '🎵 Audio Track (MP3)', ext: 'mp3', isAudio: true });
    // 5. TikTok Photo Slideshow album (if present)
    if (Array.isArray(v.images) && v.images.length > 0) {
      v.images.forEach((imgUrl, i) => {
        formats.push({
          directUrl: imgUrl,
          note: `📸 Slide Photo #${i + 1} (JPG)`,
          ext: 'jpg'
        });
      });
    }

    if (formats.length === 0) throw new Error('no playable formats in tikwm response');
    return { title: v.title || 'TikTok Video', thumbnail: v.cover || '', formats, type: 'tiktok' };
  }

  async function resolveInstagramDirect(link) {
    const shortcodeMatch = link.match(/(?:reel|reels|p|tv)\/([a-zA-Z0-9_-]+)/);
    if (!shortcodeMatch) throw new Error('Invalid Instagram URL');
    const shortcode = shortcodeMatch[1];

    let title = 'Instagram Video';
    let thumbnail = '';
    const formats = [];

    // 1. Fetch metadata via oEmbed
    try {
      const oeResp = await fetch(`https://api.instagram.com/oembed/?url=${encodeURIComponent(link)}`);
      if (oeResp.ok) {
        const oeData = await oeResp.json();
        title = oeData.title || oeData.author_name ? `${oeData.author_name} - Instagram` : title;
        thumbnail = oeData.thumbnail_url || thumbnail;
      }
    } catch (e) {}

    // 2. Fetch page HTML / Embed to extract direct mp4 video stream with muxed audio
    try {
      const embedResp = await fetch(`https://www.instagram.com/p/${shortcode}/embed/captioned/`);
      if (embedResp.ok) {
        const html = await embedResp.text();
        const videoMatch = html.match(/"video_url"\s*:\s*"([^"]+)"/) ||
                           html.match(/video_url&quot;:&quot;([^&]+)&quot;/) ||
                           html.match(/src="([^"]+\.mp4[^"]*)"/);
        const thumbMatch = html.match(/"display_url"\s*:\s*"([^"]+)"/) ||
                           html.match(/property="og:image"\s+content="([^"]+)"/);

        if (thumbMatch && !thumbnail) {
          thumbnail = thumbMatch[1].replace(/\\u0026/g, '&').replace(/&amp;/g, '&').replace(/\\/g, '');
        }

        if (videoMatch) {
          const videoUrl = videoMatch[1].replace(/\\u0026/g, '&').replace(/&amp;/g, '&').replace(/\\/g, '');
          formats.push({
            directUrl: videoUrl,
            token: videoUrl,
            note: '🎬 HD Video (with Audio MP4)',
            ext: 'mp4',
            height: 1080,
            hasAudio: true
          });

          formats.push({
            directUrl: videoUrl,
            token: videoUrl,
            note: '🎵 Audio Track (MP3 / Audio)',
            ext: 'mp3',
            isAudio: true
          });
        }
      }
    } catch (e) {}

    if (formats.length > 0) {
      return { title, thumbnail, type: 'instagram', formats };
    }
    throw new Error('No direct Instagram streams available');
  }

  async function resolveFacebookDirect(link) {
    const resp = await fetch(link);
    const body = await resp.text();
    const hdMatch = body.match(/"browser_native_hd_url"\s*:\s*"([^"]+)"/) ||
                    body.match(/"playable_url_quality_hd"\s*:\s*"([^"]+)"/) ||
                    body.match(/hd_src\s*:\s*"([^"]+)"/);

    const sdMatch = body.match(/"browser_native_sd_url"\s*:\s*"([^"]+)"/) ||
                    body.match(/"playable_url"\s*:\s*"([^"]+)"/) ||
                    body.match(/sd_src\s*:\s*"([^"]+)"/);

    let title = 'Facebook Video';
    const titleMatch = body.match(/<title id="pageTitle">([^<]+)<\/title>/) ||
                       body.match(/<title>([^<]+)<\/title>/);
    if (titleMatch) {
      title = titleMatch[1].replace(/\s*\|\s*Facebook.*$/i, '').trim();
    }

    let thumbnail = '';
    const thumbMatch = body.match(/"preferred_thumbnail"\s*:\s*\{\s*"image"\s*:\s*\{\s*"uri"\s*:\s*"([^"]+)"/) ||
                       body.match(/property="og:image"\s+content="([^"]+)"/);
    if (thumbMatch) thumbnail = thumbMatch[1].replace(/\\/g, '');

    const cleanHd = hdMatch ? hdMatch[1].replace(/\\/g, '') : null;
    const cleanSd = sdMatch ? sdMatch[1].replace(/\\/g, '') : null;

    const formats = [];
    if (cleanHd) formats.push({ directUrl: cleanHd, note: '🎬 HD (1080p MP4)', ext: 'mp4', hasAudio: true });
    if (cleanSd) formats.push({ directUrl: cleanSd, note: '🎬 SD (480p MP4)', ext: 'mp4', hasAudio: true });

    if (formats.length === 0) throw new Error('No direct streams found in Facebook page');
    return { title, thumbnail, type: 'facebook', formats };
  }

  async function resolveYoutubeMetadata(link) {
    // oEmbed only gives title/thumbnail, never a download -- used to enrich
    // whatever the actual download resolver (Cobalt) returns.
    let title = null, thumbnail = null;
    const vidId = link.match(/(?:v=|youtu\.be\/|shorts\/)([a-zA-Z0-9_-]{11})/)?.[1];
    if (vidId) thumbnail = `https://i.ytimg.com/vi/${vidId}/maxresdefault.jpg`;
    try {
      const oeResp = await fetch(`https://www.youtube.com/oembed?url=${encodeURIComponent(link)}&format=json`);
      if (oeResp.ok) {
        const oe = await oeResp.json();
        title = oe.title || title;
        thumbnail = oe.thumbnail_url || thumbnail;
      }
    } catch (e) {}
    return { title, thumbnail };
  }

  async function resolveTwitterDirect(link) {
    const tweetId = link.match(/\/status(?:es)?\/(\d+)/)?.[1];
    if (!tweetId) throw new Error('Invalid Twitter/X URL');

    for (const ep of [
      `https://api.fxtwitter.com/i/status/${tweetId}`,
      `https://api.vxtwitter.com/i/status/${tweetId}`
    ]) {
      try {
        const r = await fetch(ep, { headers: { 'Accept': 'application/json' } });
        if (!r.ok) continue;
        const d = await r.json();
        const tweet = d.tweet || d;
        const title = (tweet.text || 'Twitter / X Video').slice(0, 90);
        let thumbnail = '';
        const mp4List = [];

        if (tweet.media?.videos) {
          for (const vid of tweet.media.videos) {
            if (!thumbnail && vid.thumbnail_url) thumbnail = vid.thumbnail_url;
            for (const v of (vid.variants || [])) {
              if (v.url && ((v.content_type || '').includes('mp4') || v.url.includes('.mp4'))) {
                mp4List.push({ bitrate: v.bitrate || 0, url: v.url });
              }
            }
            if (mp4List.length === 0 && vid.url) mp4List.push({ bitrate: 2000000, url: vid.url });
          }
        } else if (Array.isArray(d.media_extended)) {
          for (const m of d.media_extended) {
            if (!thumbnail && m.thumbnail_url) thumbnail = m.thumbnail_url;
            if (m.url && m.url.includes('.mp4')) mp4List.push({ bitrate: 2000000, url: m.url });
          }
        }

        if (mp4List.length > 0) {
          mp4List.sort((a, b) => b.bitrate - a.bitrate);
          const formats = [];
          const seen = new Set();
          for (const item of mp4List) {
            if (seen.has(item.url)) continue;
            seen.add(item.url);
            const dimMatch = item.url.match(/\/(\d+)x(\d+)\//);
            const w = dimMatch ? parseInt(dimMatch[1], 10) : 0;
            const h = dimMatch ? parseInt(dimMatch[2], 10) : 0;
            const tier = (w > 0 && h > 0) ? Math.min(w, h) : (formats.length === 0 ? 1080 : 720);
            const dimStr = (w > 0 && h > 0) ? `${w}x${h} ` : '';
            const note = tier >= 1080 ? `🎬 1080p FHD (${dimStr}MP4)` : tier >= 720 ? `🎬 720p HD (${dimStr}MP4)` : `🎬 ${tier}p (${dimStr}MP4)`;
            formats.push({ directUrl: item.url, token: item.url, note, ext: 'mp4', height: tier, hasAudio: true });
          }
          formats.push({ directUrl: formats[0].directUrl, token: formats[0].directUrl, note: '🎵 High Quality Audio (MP3)', ext: 'mp3', isAudio: true });
          return { title, thumbnail, type: 'twitter', formats };
        }
      } catch (e) {}
    }
    throw new Error('Twitter direct client resolution failed');
  }

  async function resolveYoutubeDirect(link) {
    const vidId = link.match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/)?.[1];
    if (!vidId) throw new Error('Invalid YouTube URL');

    const visionUa = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15';
    const clients = [
      {
        name: 'VISIONOS',
        version: '1.02',
        clientNameId: '101',
        ua: visionUa,
        context: {
          clientName: 'VISIONOS',
          clientVersion: '1.02',
          deviceMake: 'Apple',
          deviceModel: 'RealityDevice17,1',
          userAgent: visionUa,
          osName: 'visionOS',
          osVersion: '26.5.23O471',
          hl: 'en',
          timeZone: 'UTC',
          utcOffsetMinutes: 0
        }
      },
      {
        name: 'ANDROID',
        version: '20.10.38',
        clientNameId: '3',
        ua: 'com.google.android.youtube/20.10.38 (Linux; U; Android 14; en_US) gzip',
        context: {
          clientName: 'ANDROID',
          clientVersion: '20.10.38',
          androidSdkVersion: 34,
          osName: 'Android',
          osVersion: '14',
          gl: 'US',
          hl: 'en'
        }
      },
      {
        name: 'IOS',
        version: '20.03.02',
        clientNameId: '5',
        ua: 'com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)',
        context: {
          clientName: 'IOS',
          clientVersion: '20.03.02',
          deviceMake: 'Apple',
          deviceModel: 'iPhone16,2',
          osName: 'iPhone',
          osVersion: '18.2.1.22C161',
          gl: 'US',
          hl: 'en'
        }
      }
    ];

    let lastErr = null;
    let title = 'YouTube Video';
    let thumbnail = `https://i.ytimg.com/vi/${vidId}/maxresdefault.jpg`;
    const formats = [];

    for (const c of clients) {
      try {
        const headers = {
          'Content-Type': 'application/json',
          'X-YouTube-Client-Name': c.clientNameId,
          'X-YouTube-Client-Version': c.version
        };
        const payload = {
          videoId: vidId,
          context: { client: c.context },
          playbackContext: {
            contentPlaybackContext: {
              html5Preference: 'HTML5_PREF_WANTS',
              signatureTimestamp: 20746
            }
          },
          contentCheckOk: true,
          racyCheckOk: true
        };

        const resp = await fetch('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', {
          method: 'POST',
          headers,
          body: JSON.stringify(payload)
        });
        if (!resp.ok) continue;
        const data = await resp.json();
        if (data?.playabilityStatus?.status !== 'OK') {
          lastErr = data?.playabilityStatus?.reason || 'Video unplayable';
          continue;
        }

        title = data.videoDetails?.title || title;
        const thumbsList = data.videoDetails?.thumbnail?.thumbnails || [];
        if (thumbsList.length > 0) {
          thumbnail = thumbsList[thumbsList.length - 1].url || thumbnail;
        }

        const streamingData = data.streamingData || {};
        const rawFormats = streamingData.formats || [];
        const adaptive = streamingData.adaptiveFormats || [];

        const audioStreams = adaptive.filter(af => af.url && (af.mimeType || '').includes('audio'));
        let bestAudio = null;
        if (audioStreams.length > 0) {
          bestAudio = audioStreams.reduce((prev, curr) => {
            const prevScore = (prev.bitrate || prev.averageBitrate || 0) + ((prev.mimeType || '').includes('mp4') ? 1000000 : 0) + (!prev.isDrc ? 100000 : 0);
            const currScore = (curr.bitrate || curr.averageBitrate || 0) + ((curr.mimeType || '').includes('mp4') ? 1000000 : 0) + (!curr.isDrc ? 100000 : 0);
            return currScore > prevScore ? curr : prev;
          }, audioStreams[0]);
        }
        const bestAudioUrl = bestAudio ? bestAudio.url : null;

        // Collect all video streams mapped by height
        const videoMap = {};
        // 1. Progressive streams (muxed audio)
        for (const f of rawFormats) {
          if (f.url && f.height) {
            const clen = f.contentLength ? parseInt(f.contentLength, 10) : undefined;
            videoMap[f.height] = {
              url: f.url,
              height: f.height,
              ext: 'mp4',
              hasMuxedAudio: true,
              isAvcMp4: true,
              qualityLabel: f.qualityLabel || `${f.height}p`,
              sizeBytes: clen
            };
          }
        }

        // 2. Adaptive streams (video only)
        for (const af of adaptive) {
          const mime = af.mimeType || '';
          if (af.url && af.height && mime.includes('video')) {
            const isMp4 = mime.includes('mp4');
            const isAvc = mime.includes('avc1');
            const existing = videoMap[af.height];
            const shouldReplace = !existing ||
              (!existing.hasMuxedAudio && isMp4 && (!existing.isAvcMp4 && isAvc)) ||
              (!existing.hasMuxedAudio && existing.ext !== 'mp4' && isMp4);
            if (shouldReplace) {
              const clen = af.contentLength ? parseInt(af.contentLength, 10) : undefined;
              videoMap[af.height] = {
                url: af.url,
                height: af.height,
                ext: isMp4 ? 'mp4' : 'webm',
                hasMuxedAudio: false,
                isAvcMp4: isMp4 && isAvc,
                qualityLabel: af.qualityLabel || `${af.height}p`,
                sizeBytes: clen
              };
            }
          }
        }

        const availableHeights = Object.keys(videoMap).map(h => parseInt(h, 10)).sort((a, b) => b - a);
        if (availableHeights.length === 0) continue;

        const maxH = availableHeights[0];

        // 1. Original Quality (if > 1080p, e.g. 4K 2160p or 2K 1440p)
        if (maxH > 1080) {
          const vf = videoMap[maxH];
          const hasMuxed = vf.hasMuxedAudio;
          const audioU = hasMuxed ? null : bestAudioUrl;
          const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
          const qLabel = maxH >= 2160 ? '4K UHD' : (maxH >= 1440 ? '2K QHD' : `${maxH}p`);
          formats.push({
            directUrl: vf.url,
            audioUrl: audioU,
            token: tok,
            videoId: vidId,
            url: link,
            note: `🎬 ${qLabel} (${maxH}p Original Quality)`,
            ext: vf.ext,
            height: maxH,
            hasAudio: true,
            sizeBytes: vf.sizeBytes
          });
        }

        // 2. 1080p FHD
        if (videoMap[1080]) {
          const vf = videoMap[1080];
          const hasMuxed = vf.hasMuxedAudio;
          const audioU = hasMuxed ? null : bestAudioUrl;
          const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
          const isOrig = (maxH === 1080);
          const noteStr = isOrig ? '🎬 1080p FHD (Original Quality MP4)' : '🎬 1080p FHD (MP4)';
          formats.push({
            directUrl: vf.url,
            audioUrl: audioU,
            token: tok,
            videoId: vidId,
            url: link,
            note: noteStr,
            ext: vf.ext,
            height: 1080,
            hasAudio: true,
            sizeBytes: vf.sizeBytes
          });
        }

        // 3. 720p HD
        if (videoMap[720]) {
          const vf = videoMap[720];
          const hasMuxed = vf.hasMuxedAudio;
          const audioU = hasMuxed ? null : bestAudioUrl;
          const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
          const isOrig = (maxH === 720);
          const noteStr = isOrig ? '🎬 720p HD (Original Quality MP4)' : '🎬 720p HD (MP4)';
          formats.push({
            directUrl: vf.url,
            audioUrl: audioU,
            token: tok,
            videoId: vidId,
            url: link,
            note: noteStr,
            ext: vf.ext,
            height: 720,
            hasAudio: true,
            sizeBytes: vf.sizeBytes
          });
        }

        // 4. 480p SD (if available and needed)
        if (videoMap[480] && (formats.length < 2 || maxH === 480)) {
          const vf = videoMap[480];
          const hasMuxed = vf.hasMuxedAudio;
          const audioU = hasMuxed ? null : bestAudioUrl;
          const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
          const isOrig = (maxH === 480);
          const noteStr = isOrig ? '🎬 480p SD (Original Quality MP4)' : '🎬 480p SD (MP4)';
          formats.push({
            directUrl: vf.url,
            audioUrl: audioU,
            token: tok,
            videoId: vidId,
            url: link,
            note: noteStr,
            ext: vf.ext,
            height: 480,
            hasAudio: true,
            sizeBytes: vf.sizeBytes
          });
        }

        // 5. 360p Standard
        if (videoMap[360]) {
          const vf = videoMap[360];
          const hasMuxed = vf.hasMuxedAudio;
          const audioU = hasMuxed ? null : bestAudioUrl;
          const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
          const isOrig = (maxH === 360);
          const noteStr = isOrig ? '🎬 360p (Original Quality MP4)' : '🎬 360p (Standard MP4 with Audio)';
          formats.push({
            directUrl: vf.url,
            audioUrl: audioU,
            token: tok,
            videoId: vidId,
            url: link,
            note: noteStr,
            ext: vf.ext,
            height: 360,
            hasAudio: true,
            sizeBytes: vf.sizeBytes
          });
        } else if (formats.length === 0 && availableHeights.length > 0) {
          for (const h of availableHeights.slice(0, 3)) {
            const vf = videoMap[h];
            const hasMuxed = vf.hasMuxedAudio;
            const audioU = hasMuxed ? null : bestAudioUrl;
            const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
            formats.push({
              directUrl: vf.url,
              audioUrl: audioU,
              token: tok,
              videoId: vidId,
              url: link,
              note: `🎬 ${h}p (MP4)`,
              ext: vf.ext,
              height: h,
              hasAudio: true,
              sizeBytes: vf.sizeBytes
            });
          }
        }

        // 6. High Quality Audio
        if (bestAudio) {
          const clen = bestAudio.contentLength ? parseInt(bestAudio.contentLength, 10) : undefined;
          formats.push({
            directUrl: bestAudio.url,
            token: bestAudio.url,
            videoId: vidId,
            url: link,
            note: '🎵 High Quality Audio (M4A / MP3)',
            ext: 'm4a',
            isAudio: true,
            sizeBytes: clen
          });
        }

        if (formats.length > 0) break;
      } catch (e) {
        lastErr = e.message;
      }
    }

    if (formats.length === 0) throw new Error(lastErr || 'No progressive streams available');
    return { title, thumbnail, type: 'youtube', videoId: vidId, url: link, formats };
  }

  const COBALT_STATIC_FALLBACKS = [];

  async function resolveCobaltSingle(link, displayType, instanceUrl, preset) {
    const p = preset || QUALITY_PRESETS['1080'];
    const payload = { url: link, videoQuality: p.videoQuality, downloadMode: p.downloadMode };
    if (p.downloadMode === 'audio') {
      payload.audioFormat = p.audioFormat;
      if (p.audioBitrate) payload.audioBitrate = p.audioBitrate;
    }
    const resp = await fetch(instanceUrl.replace(/\/+$/, '') + '/', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status}`);
    }
    const data = await resp.json();

    if (data.status === 'tunnel' || data.status === 'redirect') {
      const filename = data.filename || `${displayType}-download.mp4`;
      const ext = filename.includes('.') ? filename.split('.').pop() : 'mp4';
      return {
        title: filename.replace(/\.[^/.]+$/, ''),
        thumbnail: '',
        type: displayType.toLowerCase(),
        formats: [{ directUrl: data.url, note: `Download (${ext})`, ext }]
      };
    }
    if (data.status === 'picker' && Array.isArray(data.picker)) {
      const formats = data.picker
        .filter(item => item.url)
        .map((item, i) => ({
          directUrl: item.url,
          note: `Item ${i + 1} (${item.type || 'video'})`,
          ext: item.type === 'photo' ? 'jpg' : 'mp4'
        }));
      if (formats.length > 0) {
        return { title: `Media set - ${displayType}`, thumbnail: '', type: displayType.toLowerCase(), formats };
      }
    }
    if (data.status === 'error') {
      throw new Error(`Cobalt error (${data.error?.code || 'unknown'})`);
    }
    throw new Error('unexpected response');
  }

  async function resolveCobalt(link, displayType, preset) {
    const instanceUrl = getCobaltInstanceUrl();
    if (!instanceUrl) {
      throw new Error(`Platform ${displayType} stream resolution requires backend engine.`);
    }

    const uniqueInstances = [instanceUrl];

    const errors = [];
    for (const instance of uniqueInstances) {
      try {
        return await resolveCobaltSingle(link, displayType, instance, preset);
      } catch (e) {
        errors.push(`${instance}: ${e.message}`);
      }
    }
    throw new Error(`All Cobalt instances failed: ${errors.join('; ')}`);
  }

  function resolveDirectLink(link) {
    const fname = link.split('/').pop().split('?')[0] || 'Media Video';
    const ext = fname.includes('.') ? fname.split('.').pop().toLowerCase() : 'mp4';
    return {
      title: fname,
      thumbnail: '',
      type: 'direct',
      formats: [{ directUrl: link, note: `Direct ${ext.toUpperCase()} Download`, ext }]
    };
  }

  // ---- The chain itself: named resolvers in static priority order per type,
  // tried in sequence until one succeeds, reordered by whatever won last
  // time for this specific type. Each resolver's run() receives (link, preset). ----
  const RESOLVER_CHAINS = {
    'tiktok': [
      { name: 'tikwm', run: (link) => resolveTikwm(link) },
      { name: 'cobalt', run: (link, preset) => resolveCobalt(link, 'TikTok', preset) },
    ],
    'instagram': [
      { name: 'instagram-direct', run: (link) => resolveInstagramDirect(link) },
      { name: 'cobalt', run: (link, preset) => resolveCobalt(link, 'Instagram', preset) },
    ],
    'facebook': [
      { name: 'facebook-direct', run: (link) => resolveFacebookDirect(link) },
      { name: 'cobalt', run: (link, preset) => resolveCobalt(link, 'Facebook', preset) },
    ],
    'twitter': [
      { name: 'twitter-direct', run: (link) => resolveTwitterDirect(link) },
      { name: 'cobalt', run: (link, preset) => resolveCobalt(link, 'Twitter', preset) },
    ],
    'youtube': [
      { name: 'youtube-direct', run: (link) => resolveYoutubeDirect(link) },
      { name: 'cobalt', run: async (link, preset) => {
          const meta = await resolveYoutubeMetadata(link).catch(() => ({}));
          const result = await resolveCobalt(link, 'YouTube', preset);
          return {
            ...result,
            title: meta.title || result.title,
            thumbnail: meta.thumbnail || result.thumbnail
          };
        }
      },
    ],
    'direct-link': [
      { name: 'direct-link', run: async (link) => resolveDirectLink(link) },
    ],
  };

  function chainFor(type) {
    const base = RESOLVER_CHAINS[type] || [
      { name: 'cobalt', run: (link, preset) => resolveCobalt(link, type.charAt(0).toUpperCase() + type.slice(1), preset) },
    ];
    const lastGood = lastSuccessfulResolver[type];
    if (!lastGood) return base;
    const winnerIdx = base.findIndex(r => r.name === lastGood);
    if (winnerIdx <= 0) return base;
    return [base[winnerIdx], ...base.filter((_, i) => i !== winnerIdx)];
  }

  async function resolveClientSide(link, preset) {
    const type = identifyLinkType(link);
    const chain = chainFor(type);
    const failures = [];

    for (const resolver of chain) {
      try {
        const result = await resolver.run(link, preset);
        lastSuccessfulResolver[type] = resolver.name;
        return result;
      } catch (e) {
        failures.push(`${resolver.name}: ${e.message}`);
      }
    }

    throw new Error(
      `Couldn't resolve this link (tried: ${failures.join('; ')}). ` +
      `Add a Desktop Sync connection or a self-hosted Cobalt instance URL in Settings, then try again.`
    );
  }


  function setAnalyzing(on) {
    if (fbtn) fbtn.textContent = on ? '...' : (urlInput.value.trim().length > 0 ? 'GO' : 'PASTE');
    if (btnText) btnText.textContent = on ? 'Analyzing…' : (urlInput.value.trim().length > 0 ? 'Download' : 'Paste Link');
    if (btnSpinner) btnSpinner.classList.toggle('hidden', !on);
    if (analyzeBtn) analyzeBtn.disabled = on;

    if (on) {
      if (window.HKUI) window.HKUI.status('READING LINK...', false);
      setTimeout(() => {
        if (fbtn && fbtn.textContent === '...' && window.HKUI) {
          window.HKUI.status('FINDING BEST QUALITY...', false);
        }
      }, 1100);
      setAmbientState('state-analyzing');
    } else {
      refreshAmbientFromActivity();
    }
  }

  // 4. Format Popup Modal
    let currentResolvedData = null;

  function openFormatModal(data) {
    currentResolvedData = data;
    if (window.HKUI) window.HKUI.status('VIDEO FOUND', true);
    if (fbx) fbx.classList.remove('flash');
    if (fbtn) fbtn.textContent = 'DONE';

    // Curate formats into clean, human-readable tiers:
    // 1. Original (for best available quality) with resolution (e.g. Original (1080p), Original (4K))
    // 2. HD 1080p if 2nd after original
    // 3. 720p, 480p, 360p (cleanly filtered, no 240p/144p clutter)
    // 4. MP3 Audio
    function curateFormats(rawFormats) {
      if (!rawFormats || rawFormats.length === 0) return { formats: [], qualities: [] };

      const videoFormats = [];
      let bestAudioFormat = null;

      rawFormats.forEach(f => {
        const isAud = f.ext === 'mp3' || f.isAudio || (f.note && f.note.toLowerCase().includes('audio')) || (f.type && f.type === 'audio');
        if (isAud) {
          if (!bestAudioFormat) bestAudioFormat = f;
          return;
        }

        let res = 0;
        if (f.quality && !isNaN(parseInt(f.quality, 10))) {
          res = parseInt(f.quality, 10);
        } else if (f.height && !isNaN(parseInt(f.height, 10))) {
          res = parseInt(f.height, 10);
        } else if (f.note) {
          const match = f.note.match(/(\d{3,4})p/i);
          if (match) res = parseInt(match[1], 10);
          else if (/4K|2160/i.test(f.note)) res = 2160;
          else if (/1440/i.test(f.note)) res = 1440;
          else if (/1080|FHD/i.test(f.note)) res = 1080;
          else if (/720|HD/i.test(f.note)) res = 720;
          else if (/480|SD/i.test(f.note)) res = 480;
          else if (/360/i.test(f.note)) res = 360;
          else if (/320/i.test(f.note)) res = 320;
          else if (/240/i.test(f.note)) res = 240;
        }

        if (res === 0 && f.note) {
          if (/original|best|fhd|1080/i.test(f.note)) res = 1080;
          else if (/hd|720/i.test(f.note)) res = 720;
          else if (/sd|480/i.test(f.note)) res = 480;
          else if (/no watermark/i.test(f.note)) res = 1080;
        }

        videoFormats.push({ format: f, res: res });
      });

      videoFormats.sort((a, b) => b.res - a.res);

      const seenRes = new Set();
      const uniqueVideos = [];
      videoFormats.forEach(item => {
        if (item.res > 0) {
          if (!seenRes.has(item.res)) {
            seenRes.add(item.res);
            uniqueVideos.push(item);
          }
        } else if (uniqueVideos.length === 0) {
          uniqueVideos.push(item);
        }
      });

      const curatedVideos = [];
      if (uniqueVideos.length > 0) {
        const top = uniqueVideos[0];
        let topLabel = 'Original';
        if (top.res >= 2160) topLabel = 'Original (4K)';
        else if (top.res >= 1440) topLabel = 'Original (1440p)';
        else if (top.res >= 1080) topLabel = 'Original (1080p)';
        else if (top.res > 0) topLabel = `Original (${top.res}p)`;
        else topLabel = 'Original';

        curatedVideos.push({ format: top.format, label: topLabel, res: top.res });

        for (let i = 1; i < uniqueVideos.length; i++) {
          const v = uniqueVideos[i];
          let label = '';
          if (v.res >= 1080) {
            label = 'HD 1080p';
          } else if (v.res >= 720) {
            label = '720p HD';
          } else if (v.res >= 480) {
            label = '480p';
          } else if (v.res >= 360) {
            label = '360p';
          } else if (v.res >= 320) {
            label = '320p';
          } else if (curatedVideos.length < 3 && v.res > 0) {
            label = `${v.res}p`;
          } else {
            continue; // drop clutter like 240p/144p when better tiers exist
          }

          if (curatedVideos.length < 4) {
            curatedVideos.push({ format: v.format, label: label, res: v.res });
          }
        }
      }

      const finalFormats = [];
      const finalQualities = [];

      curatedVideos.forEach(item => {
        finalFormats.push(item.format);
        finalQualities.push(item.label);
      });

      if (bestAudioFormat) {
        finalFormats.push(bestAudioFormat);
        finalQualities.push('MP3 Audio');
      }

      if (finalFormats.length === 0) {
        return {
          formats: rawFormats.slice(0, 4),
          qualities: rawFormats.slice(0, 4).map(f => f.note || 'Video')
        };
      }

      return { formats: finalFormats, qualities: finalQualities };
    }

    const { formats: curatedFormats, qualities: curatedQualities } = curateFormats(data.formats);

    const primaryFormat = curatedFormats[0] || {};
    const primarySizeMB = primaryFormat.sizeBytes ? (primaryFormat.sizeBytes / 1048576).toFixed(1) : (primaryFormat.size ? (primaryFormat.size / 1048576).toFixed(1) : '5.3');

    if (window.HKUI) {
      window.HKUI.showPreview({
        title: data.title || 'Video',
        sizeMB: primarySizeMB,
        thumbnail: data.thumbnail || '',
        formats: curatedFormats,
        qualities: curatedQualities,
        selected: 0
      });
    }

    // Connect preview DOWNLOAD button
    if (dbtn) {
      dbtn.onclick = () => {
        dbtn.classList.add('tap');
        setTimeout(() => dbtn.classList.remove('tap'), 220);
        const selIdx = (window.HKUI && window.HKUI.selectedFormatIndex) ? window.HKUI.selectedFormatIndex : 0;
        const selFormat = curatedFormats[selIdx] || curatedFormats[0] || data.formats[0];
        const cleanTitle = (data.title || 'download').replace(/[\\/:*?"<>|]/g, '').trim() || 'download';
        const filename = `${cleanTitle}.${selFormat.ext || 'mp4'}`;
        const ytVidId = data.videoId || (data.url || urlInput.value || '').match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/)?.[1] || '';

        startDownload(selFormat.token || selFormat.directUrl, filename, data.title, data.thumbnail, selFormat.sizeBytes || selFormat.size, {
          ...selFormat,
          type: data.type,
          videoId: ytVidId
        });
      };
    }
  }

  if (closeModalBtn && formatModal) closeModalBtn.addEventListener('click', () => formatModal.classList.add('hidden'));
  if (formatModal) formatModal.addEventListener('click', e => { if (e.target === formatModal) formatModal.classList.add('hidden'); });

  // 5. Custom Branded MX Player Engine (Speed, Lock, Double-Tap Seek, Exit Fullscreen)
  const mxControls = document.getElementById('mxControls');
  const btnRewind  = document.getElementById('btnRewind');
  const btnForward = document.getElementById('btnForward');
  const btnSpeed   = document.getElementById('btnSpeed');
  const btnMute    = document.getElementById('btnMute');
  const btnLock    = document.getElementById('btnLock');
  const touchLeft  = document.getElementById('touchLeft');
  const touchRight = document.getElementById('touchRight');
  const bufferSpinner = document.getElementById('bufferSpinner');
  const bufferedFill  = document.getElementById('bufferedFill');
  const audioView          = document.getElementById('audioView');
  const audioTrackTitle    = document.getElementById('audioTrackTitle');
  const playerErrorOverlay = document.getElementById('playerErrorOverlay');
  const btnErrOpenExternal = document.getElementById('btnErrOpenExternal');
  const btnErrSaveDirect   = document.getElementById('btnErrSaveDirect');

  let isLocked = false;
  let controlsTimeout = null;
  let currentActiveFilename = '';
  let currentActiveExt = '';

  function getMimeType(filename, ext) {
    const cleanExt = (ext || (filename ? filename.split('.').pop() : '') || '').toLowerCase();
    switch (cleanExt) {
      case 'mp3': return 'audio/mpeg';
      case 'm4a': return 'audio/mp4';
      case 'wav': return 'audio/wav';
      case 'ogg':
      case 'opus': return 'audio/ogg';
      case 'webm': return 'video/webm';
      case 'jpg':
      case 'jpeg': return 'image/jpeg';
      case 'png': return 'image/png';
      default: return 'video/mp4';
    }
  }

  let activePlayerAudio = null;

  function playVideoInCustomPlayer(title, blobUrl, ext, filename, audioUrl = null) {
    currentActiveTitle = title || 'Playing Media';
    currentActiveBlobUrl = blobUrl;
    currentActiveExt = (ext || (filename ? filename.split('.').pop() : '') || 'mp4').toLowerCase();
    currentActiveFilename = filename || `${currentActiveTitle}.${currentActiveExt}`;

    playerTitle.textContent = currentActiveTitle;
    if (playerErrorOverlay) playerErrorOverlay.classList.add('hidden');

    if (activePlayerAudio) {
      try { activePlayerAudio.pause(); activePlayerAudio.src = ''; } catch(e) {}
      activePlayerAudio = null;
    }

    const isAudio = ['mp3', 'm4a', 'opus', 'wav', 'ogg', 'aac'].includes(currentActiveExt);

    if (audioView) {
      if (isAudio) {
        audioView.classList.remove('hidden');
        if (audioTrackTitle) audioTrackTitle.textContent = currentActiveTitle;
        if (btnAspectToggle) btnAspectToggle.classList.add('hidden');
      } else {
        audioView.classList.add('hidden');
        if (btnAspectToggle) btnAspectToggle.classList.remove('hidden');
      }
    }

    videoPlayer.src = blobUrl;

    if (audioUrl && !isAudio) {
      try {
        activePlayerAudio = new Audio(audioUrl);
        activePlayerAudio.volume = videoPlayer.volume;
        activePlayerAudio.muted = videoPlayer.muted;
        videoPlayer.onplay = () => activePlayerAudio?.play().catch(() => {});
        videoPlayer.onpause = () => activePlayerAudio?.pause();
        videoPlayer.onseeking = () => { if (activePlayerAudio) activePlayerAudio.currentTime = videoPlayer.currentTime; };
        videoPlayer.onvolumechange = () => {
          if (activePlayerAudio) {
            activePlayerAudio.volume = videoPlayer.volume;
            activePlayerAudio.muted = videoPlayer.muted;
          }
        };
      } catch(e) {}
    }

    playerModal.classList.remove('hidden');

    videoPlayer.play().catch(err => {
      console.warn('Playback error / autoplay prevented:', err);
    });
    if (playSvg && pauseSvg) {
      playSvg.classList.add('hidden');
      pauseSvg.classList.remove('hidden');
    }
    resetControlsTimeout();
  }

  if (videoPlayer) {
    videoPlayer.addEventListener('error', () => {
      console.warn('videoPlayer error event triggered, showing fallback overlay');
      if (playerErrorOverlay) {
        playerErrorOverlay.classList.remove('hidden');
      }
    });

    if (btnErrOpenExternal) {
      btnErrOpenExternal.addEventListener('click', () => {
        openWithMedia(currentActiveBlobUrl);
      });
    }

    if (btnErrSaveDirect) {
      btnErrSaveDirect.addEventListener('click', () => {
        if (!currentActiveBlobUrl) return;
        const a = document.createElement('a');
        a.href = currentActiveBlobUrl;
        a.download = currentActiveFilename || 'media.mp4';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
      });
    }

    // Aspect Ratio Fit Auto Switcher (Contain, Cover, Stretch)
    videoPlayer.addEventListener('loadedmetadata', () => {
      const w = videoPlayer.videoWidth;
      const h = videoPlayer.videoHeight;
      if (w && h && videoWrap) {
        const isVertical = h > w;
        videoWrap.className = 'mx-video-wrapper fit-contain';
        if (btnAspectToggle) btnAspectToggle.textContent = isVertical ? 'Fit (9:16)' : 'Fit (16:9)';
      }
    });

    const aspectModes = ['fit-contain', 'fit-cover', 'fit-stretch'];
    let currentAspectIdx = 0;
    if (btnAspectToggle) {
      btnAspectToggle.addEventListener('click', () => {
        currentAspectIdx = (currentAspectIdx + 1) % aspectModes.length;
        const mode = aspectModes[currentAspectIdx];
        if (videoWrap) videoWrap.className = `mx-video-wrapper ${mode}`;
        const labels = { 'fit-contain': 'Fit Auto', 'fit-cover': 'Fill Screen', 'fit-stretch': 'Stretch' };
        btnAspectToggle.textContent = labels[mode];
      });
    }

    // Playback Speed Switcher (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
    const speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    let currentSpeedIdx = 2;
    if (btnSpeed) {
      btnSpeed.addEventListener('click', () => {
        currentSpeedIdx = (currentSpeedIdx + 1) % speeds.length;
        const speed = speeds[currentSpeedIdx];
        videoPlayer.playbackRate = speed;
        btnSpeed.textContent = `${speed}x`;
      });
    }

    // Mute / Unmute
    const volumeIcon = document.getElementById('volumeIcon');
    const mutedIcon  = document.getElementById('mutedIcon');
    if (btnMute) {
      btnMute.addEventListener('click', () => {
        videoPlayer.muted = !videoPlayer.muted;
        if (volumeIcon && mutedIcon) {
          volumeIcon.classList.toggle('hidden', videoPlayer.muted);
          mutedIcon.classList.toggle('hidden', !videoPlayer.muted);
        }
      });
    }

    // Lock Screen Touch Toggle
    const lockIcon   = document.getElementById('lockIcon');
    const unlockIcon = document.getElementById('unlockIcon');
    if (btnLock) {
      btnLock.addEventListener('click', () => {
        isLocked = !isLocked;
        if (lockIcon && unlockIcon) {
          lockIcon.classList.toggle('hidden', !isLocked);
          unlockIcon.classList.toggle('hidden', isLocked);
        }
        if (mxControls) mxControls.classList.toggle('locked', isLocked);
      });
    }

    const playSvg  = document.getElementById('playSvg');
    const pauseSvg = document.getElementById('pauseSvg');
    const fsEnterSvg = document.getElementById('fsEnterSvg');
    const fsExitSvg  = document.getElementById('fsExitSvg');

    // Play / Pause Toggle
    if (btnPlayPause) {
      btnPlayPause.addEventListener('click', () => {
        if (isLocked) return;
        if (videoPlayer.paused) {
          videoPlayer.play();
          if (playSvg && pauseSvg) {
            playSvg.classList.add('hidden');
            pauseSvg.classList.remove('hidden');
          }
        } else {
          videoPlayer.pause();
          if (playSvg && pauseSvg) {
            playSvg.classList.remove('hidden');
            pauseSvg.classList.add('hidden');
          }
        }
      });
    }

    // Rewind & Forward (-10s / +10s)
    if (btnRewind) {
      btnRewind.addEventListener('click', () => { if (!isLocked) videoPlayer.currentTime = Math.max(0, videoPlayer.currentTime - 10); });
    }
    if (btnForward) {
      btnForward.addEventListener('click', () => { if (!isLocked) videoPlayer.currentTime = Math.min(videoPlayer.duration || 0, videoPlayer.currentTime + 10); });
    }

    // Double Tap Touch Zones
    const REWIND_ICON = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 19 2 12 11 5 11 19"></polygon><polygon points="22 19 13 12 22 5 22 19"></polygon></svg>';
    const FORWARD_ICON = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 19 22 12 13 5 13 19"></polygon><polygon points="2 19 11 12 2 5 2 19"></polygon></svg>';

    function showSeekFeedback(zone, iconSvg, label) {
      if (!zone) return;
      const ripple = document.createElement('div');
      ripple.className = 'seek-ripple';
      zone.appendChild(ripple);
      setTimeout(() => ripple.remove(), 600);

      const flash = document.createElement('div');
      flash.className = 'seek-flash';
      flash.innerHTML = iconSvg + `<span>${label}</span>`;
      zone.appendChild(flash);
      setTimeout(() => flash.remove(), 700);
    }

    if (touchLeft) touchLeft.addEventListener('dblclick', () => {
      videoPlayer.currentTime = Math.max(0, videoPlayer.currentTime - 10);
      showSeekFeedback(touchLeft, REWIND_ICON, '10 seconds');
    });
    if (touchRight) touchRight.addEventListener('dblclick', () => {
      videoPlayer.currentTime = Math.min(videoPlayer.duration || 0, videoPlayer.currentTime + 10);
      showSeekFeedback(touchRight, FORWARD_ICON, '10 seconds');
    });

    // Auto-Hide Controls Overlay
    if (videoWrap) {
      videoWrap.addEventListener('mousemove', showControlsTemporarily);
      videoWrap.addEventListener('touchstart', showControlsTemporarily);
    }

    function showControlsTemporarily() {
      if (mxControls) mxControls.classList.remove('fade-out');
      resetControlsTimeout();
    }

    function resetControlsTimeout() {
      clearTimeout(controlsTimeout);
      controlsTimeout = setTimeout(() => {
        if (!videoPlayer.paused && !isLocked && mxControls) {
          mxControls.classList.add('fade-out');
        }
      }, 3500);
    }

    videoPlayer.addEventListener('timeupdate', () => {
      if (!isNaN(videoPlayer.duration)) {
        const pct = (videoPlayer.currentTime / videoPlayer.duration) * 100;
        if (playerSeek) playerSeek.value = pct;
        if (playerProgress) playerProgress.style.width = `${pct}%`;
        if (playerTime) playerTime.textContent = `${fmtTime(videoPlayer.currentTime)} / ${fmtTime(videoPlayer.duration)}`;
      }
    });

    // Buffered-range indicator on the seekbar
    videoPlayer.addEventListener('progress', () => {
      if (!bufferedFill || !videoPlayer.duration || isNaN(videoPlayer.duration)) return;
      const buffered = videoPlayer.buffered;
      if (buffered.length > 0) {
        const end = buffered.end(buffered.length - 1);
        bufferedFill.style.width = `${(end / videoPlayer.duration) * 100}%`;
      }
    });

    // Buffering spinner
    if (bufferSpinner) {
      videoPlayer.addEventListener('waiting', () => bufferSpinner.classList.remove('hidden'));
      videoPlayer.addEventListener('playing', () => bufferSpinner.classList.add('hidden'));
      videoPlayer.addEventListener('canplay', () => bufferSpinner.classList.add('hidden'));
      videoPlayer.addEventListener('seeking', () => bufferSpinner.classList.remove('hidden'));
      videoPlayer.addEventListener('seeked', () => bufferSpinner.classList.add('hidden'));
    }

    if (playerSeek) {
      playerSeek.addEventListener('input', () => {
        if (!isNaN(videoPlayer.duration)) {
          const seekTo = (playerSeek.value / 100) * videoPlayer.duration;
          videoPlayer.currentTime = seekTo;
        }
      });
    }

    // Fullscreen & Exit Fullscreen Toggle
    if (btnFullscreen) {
      btnFullscreen.addEventListener('click', () => {
        const isFS = document.fullscreenElement || document.webkitFullscreenElement;
        if (isFS) {
          if (document.exitFullscreen) document.exitFullscreen();
          else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
        } else {
          if (videoWrap && videoWrap.requestFullscreen) videoWrap.requestFullscreen();
          else if (videoWrap && videoWrap.webkitRequestFullscreen) videoWrap.webkitRequestFullscreen();
          else if (videoPlayer.requestFullscreen) videoPlayer.requestFullscreen();
        }
      });
    }

    document.addEventListener('fullscreenchange', handleFSChange);
    document.addEventListener('webkitfullscreenchange', handleFSChange);

    function handleFSChange() {
      const isFS = document.fullscreenElement || document.webkitFullscreenElement;
      if (fsEnterSvg && fsExitSvg) {
        fsEnterSvg.classList.toggle('hidden', isFS);
        fsExitSvg.classList.toggle('hidden', !isFS);
      }
    }

    if (btnSharePlayer) btnSharePlayer.addEventListener('click', () => shareMedia(currentActiveTitle, currentActiveBlobUrl));
    if (btnOpenWithPlayer) btnOpenWithPlayer.addEventListener('click', () => openWithMedia(currentActiveBlobUrl));

    if (closePlayerBtn) closePlayerBtn.addEventListener('click', closeCustomPlayer);
    if (playerModal) playerModal.addEventListener('click', e => { if (e.target === playerModal) closeCustomPlayer(); });

    function closeCustomPlayer() {
      videoPlayer.pause();
      videoPlayer.src = '';
      if (playerErrorOverlay) playerErrorOverlay.classList.add('hidden');
      if (audioView) audioView.classList.add('hidden');
      if (playerModal) playerModal.classList.add('hidden');
    }
  }

  // 6. Share & Open-With Helper Functions
  async function shareMedia(title, blobUrl) {
    if (navigator.share) {
      try {
        await navigator.share({ title, text: `Watch ${title}`, url: blobUrl });
      } catch (err) {}
    } else {
      try {
        await navigator.clipboard.writeText(blobUrl || window.location.href);
        alert('Media link copied to clipboard!');
      } catch(e) {}
    }
  }

  function openWithMedia(blobUrl) {
    if (!blobUrl) return;
    const a = document.createElement('a');
    a.href = blobUrl;
    a.target = '_blank';
    a.rel = 'noreferrer noopener';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  // 7. Delete Confirmation Modal Popup
  function promptDeleteConfirmation(cardElem, libItem = null) {
    pendingDeleteCard = cardElem;
    pendingDeleteLibItem = libItem;
    deleteModal.classList.remove('hidden');
  }

  if (closeDeleteModalBtn && deleteModal) closeDeleteModalBtn.addEventListener('click', () => deleteModal.classList.add('hidden'));
  if (deleteModal) deleteModal.addEventListener('click', e => { if (e.target === deleteModal) deleteModal.classList.add('hidden'); });

  function checkRecentEmpty() {
    const recentWrap = document.getElementById('recentWrap');
    if (recentWrap && downloadsList) {
      if (downloadsList.querySelectorAll('.dl-card').length === 0) {
        recentWrap.style.display = 'none';
      } else {
        recentWrap.style.display = 'block';
      }
    }
  }

  if (btnDelTaskOnly) {
    btnDelTaskOnly.addEventListener('click', () => {
      if (pendingDeleteCard) pendingDeleteCard.remove();
      checkRecentEmpty();
      if (deleteModal) deleteModal.classList.add('hidden');
    });
  }

  if (btnDelTaskAndFile) {
    btnDelTaskAndFile.addEventListener('click', () => {
      if (pendingDeleteCard) pendingDeleteCard.remove();
      checkRecentEmpty();
      if (pendingDeleteLibItem) {
        const idx = libraryItems.indexOf(pendingDeleteLibItem);
        if (idx > -1) libraryItems.splice(idx, 1);
        renderLibraryGrid();
      }
      if (deleteModal) deleteModal.classList.add('hidden');
    });
  }

  // 8. Download Execution Engine with Task-Based Ad Wait-Time Station
  async function startDownload(tokenOrUrl, filename, title, thumbnail, knownSize, formatObj) {
    let isCompleted = false;
    const qNote = formatObj?.note || (formatObj?.ext ? formatObj.ext.toUpperCase() : '1080p HD');
    const totalSizeMB = knownSize ? (knownSize / 1048576) : 5.3;

    // Open Futuristic Progress Sheet
    if (window.HKUI) {
      window.HKUI.openSheet({
        title: title || filename,
        quality: qNote,
        totalMB: totalSizeMB,
        thumbnail: thumbnail
      });
    }

    // Increment public downloads counter immediately on request
    if (window.incrementDownloadsCounter) {
      window.incrementDownloadsCounter();
    }
    // 🛡️ Abuse Prevention: Daily Fair-Use Quota Check
    if (!checkDailyDownloadQuota()) {
      showToast('Daily Limit Reached (30/30)', 'You have reached today\'s fair-use limit. Quotas reset at 00:00 UTC.', true);
      if (policyModal) policyModal.classList.remove('hidden');
      return;
    }

    const card = addDownloadCard(title, filename, thumbnail);
    const { cardElem, bar, percent, meta, statusText, btnCancel, actionsContainer, adStrip } = card;

    const controller = new AbortController();

    activeDownloadCount++;
    refreshAmbientFromActivity();
    let countReleased = false;
    const releaseCount = () => {
      if (countReleased) return;
      countReleased = true;
      activeDownloadCount = Math.max(0, activeDownloadCount - 1);
    };

    if (taskTitle) taskTitle.textContent = title || filename;
    if (taskThumb && taskThumbFallback) {
      if (thumbnail) {
        taskThumb.src = thumbnail;
        taskThumb.classList.remove('hidden');
        taskThumbFallback.classList.add('hidden');
      } else {
        taskThumb.classList.add('hidden');
        taskThumbFallback.classList.remove('hidden');
      }
    }

    if (taskQualityBadge) taskQualityBadge.textContent = qNote;
    if (taskSpeedEtaBadge) taskSpeedEtaBadge.textContent = 'Preparing stream…';
    if (taskProgressPct) {
      taskProgressPct.textContent = '0%';
      taskProgressPct.style.color = 'var(--accent-orange)';
    }
    if (taskProgressSize) taskProgressSize.textContent = 'Connecting…';
    if (taskProgressBar) {
      taskProgressBar.style.width = '0%';
      taskProgressBar.style.background = 'linear-gradient(90deg, var(--accent-orange), #ff6a1f)';
    }
    if (taskStatusHeading) taskStatusHeading.textContent = 'Downloading Media…';
    if (taskAdCountdown) taskAdCountdown.textContent = 'Ad finishes with download';
    if (taskCompleteActions) taskCompleteActions.classList.add('hidden');

    if (taskDownloadModal) taskDownloadModal.classList.remove('hidden');

    if (btnMinimizeTask) {
      btnMinimizeTask.onclick = () => {
        if (taskDownloadModal) taskDownloadModal.classList.add('hidden');
        if (taskAdVideo) taskAdVideo.pause();
        updateActiveDownloadToast(title || filename, 0, 'Running in background • Tap to open', () => {
          hideActiveDownloadToast();
          if (taskDownloadModal) taskDownloadModal.classList.remove('hidden');
        });
      };
    }

    const cancelDownload = () => {
      if (isCompleted) return; // Bug Fix: Never mark a completed download as cancelled!
      controller.abort();
      hideActiveDownloadToast();
      if (taskDownloadModal) taskDownloadModal.classList.add('hidden');
      if (taskAdVideo) taskAdVideo.pause();
      if (window.HKUI) {
        window.HKUI.fail('Download cancelled');
        setTimeout(() => window.HKUI.closeSheet(), 700);
      }
      showToast('Download Cancelled', title || filename, false);
      cardElem.classList.add('failed');
      percent.textContent = 'Cancelled';
      statusText.textContent = 'Cancelled';
      meta.textContent = 'Download cancelled';
      if (adStrip) adStrip.remove();
      releaseCount();
      refreshAmbientFromActivity();
    };

    window.cancelCurrentDownload = cancelDownload;
    window.onSheetMinimized = () => {
      if (!isCompleted) {
        updateActiveDownloadToast(title || filename, 0, 'Running in background • Tap to open', () => {
          hideActiveDownloadToast();
          if (window.HKUI) {
            window.HKUI.openSheet({
              title: title || filename,
              quality: qNote,
              totalMB: totalSizeMB,
              thumbnail: thumbnail
            });
          }
        });
      }
    };

    btnCancel.addEventListener('click', cancelDownload);
    if (btnCancelTaskModal) {
      btnCancelTaskModal.onclick = cancelDownload;
    }

    // Allow user to click active card to re-open Task Modal if minimized
    cardElem.addEventListener('click', (e) => {
      if (e.target.closest('button') || e.target.closest('a')) return;
      if (!cardElem.classList.contains('completed') && !cardElem.classList.contains('failed')) {
        hideActiveDownloadToast();
        if (taskDownloadModal) taskDownloadModal.classList.remove('hidden');
      }
    });

    try {
      let mainUrl = tokenOrUrl;
      let extraAudioUrl = formatObj?.audioUrl || null;
      if (typeof tokenOrUrl === 'string' && tokenOrUrl.includes('|http')) {
        mainUrl = tokenOrUrl.split('|')[0];
        extraAudioUrl = tokenOrUrl.split('|')[1];
      }

      let downloadEndpoint;
      const isYtStream = (formatObj?.type || '').toLowerCase() === 'youtube' ||
                         (mainUrl || '').includes('googlevideo') ||
                         (mainUrl || '').includes('youtube') ||
                         Boolean(formatObj?.videoId);

      if (isYtStream) {
        const vidId = formatObj?.videoId || (mainUrl || '').match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/)?.[1] || '';
        const qualityParam = formatObj?.height || formatObj?.quality || '';
        downloadEndpoint = `/api/download?type=youtube&id=${encodeURIComponent(vidId)}&url=${encodeURIComponent(mainUrl)}&filename=${encodeURIComponent(filename)}&quality=${encodeURIComponent(qualityParam)}`;
        if (extraAudioUrl && extraAudioUrl.startsWith('http')) {
          extraAudioUrl = `/api/download?type=youtube&id=${encodeURIComponent(vidId)}&url=${encodeURIComponent(extraAudioUrl)}&filename=audio.m4a`;
        }
      } else if (mainUrl.startsWith('http')) {
        // If cross-origin, route through Cloudflare Pages proxy to attach CORS headers
        if (!mainUrl.includes(window.location.hostname)) {
          downloadEndpoint = `/api/download?url=${encodeURIComponent(mainUrl)}&filename=${encodeURIComponent(filename)}`;
        } else {
          downloadEndpoint = mainUrl;
        }
      } else {
        downloadEndpoint = `/api/download?token=${mainUrl}&filename=${encodeURIComponent(filename)}`;
      }

      let resp;
      try {
        resp = await fetch(downloadEndpoint, { signal: controller.signal });
      } catch (fetchErr) {
        // If proxy or direct fetch encounters a CORS network error, try direct URL
        if (downloadEndpoint !== mainUrl && mainUrl.startsWith('http')) {
          resp = await fetch(mainUrl, { signal: controller.signal });
        } else {
          throw fetchErr;
        }
      }

      if (!resp.ok) {
        if (downloadEndpoint !== mainUrl && mainUrl.startsWith('http')) {
          try {
            resp = await fetch(mainUrl, { signal: controller.signal });
          } catch (e) {}
        }
        if (!resp || !resp.ok) throw new Error(`Server status ${resp ? resp.status : 'error'}`);
      }

      const totalSize = knownSize || parseInt(resp.headers.get('content-length') || '0', 10) || 0;
      const reader    = resp.body.getReader();
      const chunks    = [];
      let   received  = 0;
      let   startTime = Date.now();
      const speedSampler = new SpeedSampler(800);

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        received += value.length;
        speedSampler.add(value.length);

        const currentSpeed = speedSampler.speed || (received / Math.max(0.1, (Date.now() - startTime) / 1000));
        const pct          = totalSize ? Math.round((received / totalSize) * 100) : null;
        const etaVal       = totalSize && currentSpeed > 0 ? Math.round((totalSize - received) / currentSpeed) : null;

        // Drive Futuristic Progress Sheet
        if (window.HKUI) {
          window.HKUI.progress({
            percent: pct != null ? pct : Math.min(95, (received / (totalSize || 5242880)) * 100),
            doneMB: received / 1048576,
            speedMB: currentSpeed / 1048576,
            etaSec: etaVal
          });
        }

        // Update list card
        const eta = etaVal;
        bar.style.width      = pct != null ? `${pct}%` : '50%';
        percent.textContent  = pct != null ? `${pct}%` : '…';
        meta.textContent     = `${fmtBytes(received)}${totalSize ? ' / ' + fmtBytes(totalSize) : ''}    ${fmtSpeed(currentSpeed)}${eta ? '    ' + fmtEta(eta) : ''}`;

        // Sync with Task Modal
        if (taskProgressBar) taskProgressBar.style.width = pct != null ? `${pct}%` : '50%';
        if (taskProgressPct) taskProgressPct.textContent = pct != null ? `${pct}%` : '…';
        if (taskProgressSize) taskProgressSize.textContent = `${fmtBytes(received)}${totalSize ? ' / ' + fmtBytes(totalSize) : ''}`;
        if (taskSpeedEtaBadge) {
          if (eta != null && eta > 0) {
            taskSpeedEtaBadge.textContent = `${fmtSpeed(currentSpeed)} • ~${eta}s left`;
            if (taskAdCountdown) taskAdCountdown.textContent = `Ad finishes with download (~${eta}s)`;
          } else {
            taskSpeedEtaBadge.textContent = fmtSpeed(currentSpeed);
          }
        }

        // Update floating Active Download Toast when Task Modal is minimized
        if (!taskDownloadModal || taskDownloadModal.classList.contains('hidden')) {
          updateActiveDownloadToast(
            title || filename,
            pct,
            `${fmtSpeed(currentSpeed)}${eta ? ' • ' + fmtEta(eta) : ''}`,
            () => {
              hideActiveDownloadToast();
              if (taskDownloadModal) taskDownloadModal.classList.remove('hidden');
            }
          );
        }
      }

      // Complete download with dynamic MIME type
      const ext      = (formatObj?.ext || filename.split('.').pop() || 'mp4').toLowerCase();
      const mimeType = getMimeType(filename, ext);
      const blob     = new Blob(chunks, { type: mimeType });
      const blobUrl  = URL.createObjectURL(blob);
      const a        = document.createElement('a');
      a.href     = blobUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);

      hideActiveDownloadToast();
      playTinyCompletionChime();
      cardElem.classList.add('completed');
      bar.style.width = '100%';
      percent.textContent = '100%';
      statusText.textContent = 'Finished';
      meta.textContent = 'Saved to Downloads';
      if (adStrip) adStrip.remove();
      showToast('Download complete', title || filename);
      releaseCount();
      flashAmbientState('state-completed');

      // Update HK Futuristic Bottom Sheet to Complete State & Wire Play/Share/Save
      if (window.HKUI) {
        window.HKUI.complete({
          title: title || filename,
          filename,
          ext,
          blobUrl,
          sizeMB: received / 1048576,
          quality: qNote,
          thumbnail
        });
      }

      window.onSheetPlay = () => {
        playVideoInCustomPlayer(title || filename, blobUrl, ext, filename, extraAudioUrl);
      };
      window.onSheetShare = () => {
        shareMedia(title || filename, blobUrl);
      };
      window.onSheetSave = () => {
        const dlLink = document.createElement('a');
        dlLink.href = blobUrl;
        dlLink.download = filename;
        document.body.appendChild(dlLink);
        dlLink.click();
        document.body.removeChild(dlLink);
      };

      // Wire Complete Screen Modal (if opened)
      if (btnPlayPlayer) {
        btnPlayPlayer.onclick = () => {
          if (completeView) completeView.classList.add('hidden');
          playVideoInCustomPlayer(title || filename, blobUrl, ext, filename, extraAudioUrl);
        };
      }
      if (btnSaveDevice) {
        btnSaveDevice.onclick = () => {
          const dlLink = document.createElement('a');
          dlLink.href = blobUrl;
          dlLink.download = filename;
          document.body.appendChild(dlLink);
          dlLink.click();
          document.body.removeChild(dlLink);
        };
      }
      if (btnShareComp) {
        btnShareComp.onclick = () => {
          shareMedia(title || filename, blobUrl);
        };
      }
      if (btnNavLibComp) {
        btnNavLibComp.onclick = () => {
          if (completeView) completeView.classList.add('hidden');
          if (window.HKUI) window.HKUI.closeSheet();
          const tabDownloader = document.getElementById('tabDownloader');
          const tabLibrary = document.getElementById('tabLibrary');
          const navDownloader = document.getElementById('navDownloader');
          const navLibrary = document.getElementById('navLibrary');
          if (tabDownloader && tabLibrary) {
            tabDownloader.classList.add('hidden');
            tabLibrary.classList.remove('hidden');
            if (navDownloader) navDownloader.classList.remove('active');
            if (navLibrary) navLibrary.classList.add('active');
          }
        };
      }
      if (btnNewLinkComp) {
        btnNewLinkComp.onclick = () => {
          if (completeView) completeView.classList.add('hidden');
          if (window.HKUI) {
            window.HKUI.closeSheet();
            window.HKUI.hidePreview();
          }
          if (urlInput) {
            urlInput.value = '';
            urlInput.focus();
          }
        };
      }

      // Update Task Modal completion state
      if (taskProgressBar) {
        taskProgressBar.style.width = '100%';
        taskProgressBar.style.background = 'var(--accent-green)';
      }
      if (taskProgressPct) {
        taskProgressPct.textContent = '100%';
        taskProgressPct.style.color = 'var(--accent-green)';
      }
      if (taskStatusHeading) taskStatusHeading.textContent = 'Download Complete!';
      if (taskSpeedEtaBadge) taskSpeedEtaBadge.textContent = 'Saved to Device';
      if (taskAdCountdown) taskAdCountdown.textContent = 'Task Completed';
      if (taskCompleteActions) taskCompleteActions.classList.remove('hidden');
      if (taskAdVideo) taskAdVideo.pause();

      if (btnTaskPlay) {
        btnTaskPlay.onclick = () => {
          if (taskDownloadModal) taskDownloadModal.classList.add('hidden');
          playVideoInCustomPlayer(title || filename, blobUrl, ext, filename, extraAudioUrl);
        };
      }
      if (btnTaskSave) {
        btnTaskSave.onclick = () => {
          const dlLink = document.createElement('a');
          dlLink.href = blobUrl;
          dlLink.download = filename;
          document.body.appendChild(dlLink);
          dlLink.click();
          document.body.removeChild(dlLink);
        };
      }
      if (btnTaskCloseDone) {
        btnTaskCloseDone.onclick = () => {
          if (taskDownloadModal) taskDownloadModal.classList.add('hidden');
        };
      }

      actionsContainer.innerHTML = `
        <button class="btn-card-action btn-play" title="Play Media">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          <span>Play</span>
        </button>
        <button class="btn-card-action btn-openwith" title="Open With App">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg>
        </button>
        <button class="btn-card-action btn-share" title="Share Media">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
        </button>
        <button class="btn-card-action btn-del" title="Delete">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
        </button>
      `;

      const isAudio = ['mp3', 'm4a', 'opus', 'wav', 'ogg', 'aac'].includes(ext) || formatObj?.isAudio;
      const libObj = { title: title || filename, filename, thumbnail, blobUrl, size: received, ext, isAudio, audioUrl: extraAudioUrl };

      actionsContainer.querySelector('.btn-play').addEventListener('click', () => playVideoInCustomPlayer(title || filename, blobUrl, ext, filename, extraAudioUrl));
      actionsContainer.querySelector('.btn-openwith').addEventListener('click', () => openWithMedia(blobUrl));
      actionsContainer.querySelector('.btn-share').addEventListener('click', () => shareMedia(title || filename, blobUrl));
      actionsContainer.querySelector('.btn-del').addEventListener('click', () => promptDeleteConfirmation(cardElem, libObj));

      // Add to Library Grid
      libraryItems.unshift(libObj);
      renderLibraryGrid();

    } catch (err) {
      hideActiveDownloadToast();
      if (err.name !== 'AbortError') {
        // Fallback: If browser fetch encounters CORS or memory error, trigger native browser download!
        if (tokenOrUrl && tokenOrUrl.startsWith('http')) {
          statusText.textContent = 'Downloading...';
          meta.textContent = 'Saving via browser download...';

          const a = document.createElement('a');
          a.href = (downloadEndpoint && downloadEndpoint.startsWith('/api/download')) ? downloadEndpoint : tokenOrUrl;
          a.download = filename || 'video.mp4';
          a.target = '_blank';
          a.rel = 'noreferrer noopener';
          a.referrerPolicy = 'no-referrer';
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);

          setTimeout(() => {
            playTinyCompletionChime();
            cardElem.classList.remove('failed');
            cardElem.classList.add('completed');
            bar.style.width = '100%';
            percent.textContent = '100%';
            statusText.textContent = 'Finished';
            meta.textContent = 'Saved to Device Downloads';
            if (adStrip) adStrip.remove();
            showToast('Download started', title || filename);
            releaseCount();
            flashAmbientState('state-completed');

            if (taskProgressBar) taskProgressBar.style.width = '100%';
            if (taskProgressPct) taskProgressPct.textContent = '100%';
            if (taskStatusHeading) taskStatusHeading.textContent = 'Download Started!';
            if (taskCompleteActions) taskCompleteActions.classList.remove('hidden');
          }, 1200);
          return;
        }

        cardElem.classList.add('failed');
        percent.textContent = 'Failed';
        statusText.textContent = 'Failed';
        meta.textContent = err.message;
        if (adStrip) adStrip.remove();
        showToast('Download failed', err.message, true);
        releaseCount();
        flashAmbientState('state-failed');

        if (taskStatusHeading) taskStatusHeading.textContent = 'Download Failed';
        if (taskAdCountdown) taskAdCountdown.textContent = 'Error occurred';
      }
    }
  }

  function addDownloadCard(title, filename, thumbnail) {
    const recentWrap = document.getElementById('recentWrap');
    if (recentWrap) recentWrap.style.display = 'block';
    if (emptyState) emptyState.style.display = 'none';

    const cardElem = document.createElement('div');
    cardElem.className = 'dl-card';
    cardElem.style.cursor = 'pointer';
    const thumbHtml = thumbnail
      ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18" style="position:absolute;z-index:0;opacity:0.35"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg><img src="${thumbnail}" alt="thumb" style="position:relative;z-index:1" onerror="this.style.display='none'">`
      : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>`;

    cardElem.innerHTML = `
      <div class="dl-thumb-box">${thumbHtml}</div>
      <div class="dl-info">
        <div class="dl-title-row">
          <div class="dl-title">${title || filename}</div>
          <div class="dl-percent">0%</div>
        </div>
        <div class="dl-progress-wrap"><div class="dl-progress-bg">
          <div class="dl-progress-bar" style="width:0%"></div>
        </div></div>
        <div class="dl-meta-row">
          <span class="dl-meta">Connecting…</span>
          <div class="dl-actions">
            <button class="btn-card-action btn-cancel" title="Cancel Download">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
          </div>
        </div>
        <div class="dl-card-ad-strip">
          <span class="ad-pill">SPONSORED</span>
          <span style="color:var(--text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">Verified Sponsor • High Speed Downloads</span>
          <a href="/home#sponsor" class="ad-cta-link">Learn More</a>
        </div>
      </div>
    `;

    downloadsList.prepend(cardElem);

    return {
      cardElem,
      bar:              cardElem.querySelector('.dl-progress-bar'),
      percent:          cardElem.querySelector('.dl-percent'),
      meta:             cardElem.querySelector('.dl-meta'),
      statusText:       cardElem.querySelector('.dl-percent'),
      btnCancel:        cardElem.querySelector('.btn-cancel'),
      actionsContainer: cardElem.querySelector('.dl-actions'),
      adStrip:          cardElem.querySelector('.dl-card-ad-strip')
    };
  }

  // 9. 2-Column Grid View Media Library
  function renderLibraryGrid() {
    if (!libraryGrid) return;
    libraryGrid.innerHTML = '';
    libraryCount.textContent = `${libraryItems.length} Files`;

    if (libraryItems.length === 0) {
      emptyLibrary.style.display = 'flex';
      return;
    }

    emptyLibrary.style.display = 'none';
    libraryItems.forEach(item => {
      const card = document.createElement('div');
      card.className = 'library-card';
      const thumbHtml = item.thumbnail
        ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22" style="position:absolute;z-index:0;opacity:0.35"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg><img src="${item.thumbnail}" alt="" style="position:relative;z-index:1" onerror="this.style.display='none'">`
        : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>`;

      card.innerHTML = `
        <div class="lib-thumb">
          ${thumbHtml}
          <div class="lib-play-overlay">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          </div>
        </div>
        <div class="lib-details">
          <div class="lib-title">${item.title}</div>
          <div class="lib-bottom-row">
            <div class="lib-size">${fmtBytes(item.size)}</div>
            <div class="lib-actions">
              <button class="btn-card-action btn-lib-open" title="Open With System App">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg>
              </button>
              <button class="btn-card-action btn-lib-share" title="Share Video">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
              </button>
              <button class="btn-card-action btn-lib-del btn-del" title="Delete">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
              </button>
            </div>
          </div>
        </div>
      `;

      card.querySelector('.lib-thumb').addEventListener('click', () => playVideoInCustomPlayer(item.title, item.blobUrl, item.ext, item.filename));
      card.querySelector('.btn-lib-open').addEventListener('click', (e) => { e.stopPropagation(); openWithMedia(item.blobUrl); });
      card.querySelector('.btn-lib-share').addEventListener('click', (e) => { e.stopPropagation(); shareMedia(item.title, item.blobUrl); });
      card.querySelector('.btn-lib-del').addEventListener('click', (e) => { e.stopPropagation(); promptDeleteConfirmation(card, item); });

      libraryGrid.appendChild(card);
    });
  }

  if (clearHistoryBtn) {
    clearHistoryBtn.addEventListener('click', () => {
      downloadsList.innerHTML = '';
      checkRecentEmpty();
    });
  }

  function fmtBytes(b) {
    if (!b) return '0 B';
    const u = ['B','KB','MB','GB'];
    const i = Math.floor(Math.log10(b) / Math.log10(1024));
    return (b / Math.pow(1024, i)).toFixed(1) + ' ' + u[Math.min(i, 3)];
  }
  function fmtSpeed(bps) { return fmtBytes(bps) + '/s'; }
  function fmtEta(s) {
    if (s < 60) return `${s}s ETA`;
    if (s < 3600) return `${Math.floor(s/60)}m ETA`;
    return `${Math.floor(s/3600)}h ETA`;
  }
  function fmtTime(sec) {
    if (isNaN(sec)) return '00:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m < 10 ? '0' + m : m}:${s < 10 ? '0' + s : s}`;
  }

  // Client-Side Adult / NSFW Ad Guard (filters out unwanted ad networks / creatives)
  const ADULT_KEYWORDS = [
    'erotic', 'adult', 'dating', 'sex', 'porn', 'xxx', '18+', 'nsfw', 'hookup', 
    'strip', 'camgirl', 'onlyfans', 'casino', 'betting', 'gamble', 'singles'
  ];

  function sanitizeAdNodes() {
    const adContainers = document.querySelectorAll('.ad-slot-container, .fixed-ad-slot, .ad-fixed-container, iframe');
    adContainers.forEach(container => {
      try {
        const text = (container.innerText || container.textContent || '').toLowerCase();
        const html = (container.innerHTML || '').toLowerCase();
        const isBad = ADULT_KEYWORDS.some(kw => text.includes(kw) || html.includes(kw));
        if (isBad) {
          container.style.display = 'none';
          container.innerHTML = '';
        }
      } catch (e) {}
    });
  }

  const adObserver = new MutationObserver(() => {
    sanitizeAdNodes();
  });
  adObserver.observe(document.body, { childList: true, subtree: true });
  setTimeout(sanitizeAdNodes, 2000);
  setTimeout(sanitizeAdNodes, 5000);
});
