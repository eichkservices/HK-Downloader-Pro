document.addEventListener('DOMContentLoaded', () => {
  const downloadForm       = document.getElementById('downloadForm');
  const urlInput           = document.getElementById('urlInput');
  const analyzeBtn         = document.getElementById('analyzeBtn');
  const btnText            = document.getElementById('btnText');
  const btnSpinner         = document.getElementById('btnSpinner');

  const formatModal        = document.getElementById('formatModal');
  const closeModalBtn      = document.getElementById('closeModalBtn');
  const modalThumb         = document.getElementById('modalThumb');
  const modalThumbFallback = document.getElementById('modalThumbFallback');
  const modalTitle         = document.getElementById('modalTitle');
  const modalTypeBadge     = document.getElementById('modalTypeBadge');
  const formatList         = document.getElementById('formatList');

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
  const videoWrap          = document.getElementById('videoWrap');
  const videoPlayer        = document.getElementById('videoPlayer');
  const closePlayerBtn     = document.getElementById('closePlayerBtn');

  const btnPlayPause       = document.getElementById('btnPlayPause');
  const playerSeek         = document.getElementById('playerSeek');
  const playerProgress     = document.getElementById('playerProgress');
  const playerTime         = document.getElementById('playerTime');
  const btnAspectToggle    = document.getElementById('btnAspectToggle');
  const btnOpenWithPlayer  = document.getElementById('btnOpenWithPlayer');
  const btnSharePlayer     = document.getElementById('btnSharePlayer');
  const btnFullscreen      = document.getElementById('btnFullscreen');

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

  // PWA 1-Tap Home Screen App Installer
  let deferredPrompt;
  const btnPwaInstall = document.getElementById('btnPwaInstall');

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    if (btnPwaInstall) btnPwaInstall.classList.remove('hidden');
  });

  if (btnPwaInstall) {
    btnPwaInstall.addEventListener('click', async () => {
      if (deferredPrompt) {
        deferredPrompt.prompt();
        const { outcome } = await deferredPrompt.userChoice;
        if (outcome === 'accepted') {
          btnPwaInstall.classList.add('hidden');
        }
        deferredPrompt = null;
      }
    });
  }

  // 1. Dynamic Button Text Toggle (Paste Link ↔ Download)
  urlInput.addEventListener('input', updateButtonText);
  function updateButtonText() {
    const text = urlInput.value.trim();
    btnText.textContent = text.length > 0 ? 'Download' : 'Paste Link';
  }
  updateButtonText();

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
  async function analyzeUrl(link) {
    setAnalyzing(true);
    try {
      const type = identifyLinkType(link);
      
      // For YouTube and Instagram, resolve multiple qualities in parallel!
      if (type === 'youtube' || type === 'instagram') {
        const presetsToFetch = [
          { key: '1080', preset: QUALITY_PRESETS['1080'] || { videoQuality: '1080', downloadMode: 'auto' }, label: 'Video (1080p)', ext: 'mp4' },
          { key: '720',  preset: QUALITY_PRESETS['720'] || { videoQuality: '720', downloadMode: 'auto' },  label: 'Video (720p)',  ext: 'mp4' },
          { key: '480',  preset: QUALITY_PRESETS['480'] || { videoQuality: '480', downloadMode: 'auto' },  label: 'Video (480p)',  ext: 'mp4' },
          { key: 'audio-mp3', preset: QUALITY_PRESETS['audio-mp3'] || { videoQuality: 'max', downloadMode: 'audio', audioFormat: 'mp3' }, label: 'Audio Track (MP3)', ext: 'mp3' }
        ];

        const promises = presetsToFetch.map(async (item) => {
          // 1. Try Backend Node API first
          try {
            const resp = await fetch('/api/analyze', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ inputUrl: link, preset: item.preset })
            });
            const contentType = resp.headers.get('content-type') || '';
            if (resp.ok && contentType.includes('application/json')) {
              const data = await resp.json();
              if (data.formats && data.formats.length > 0) {
                const format = data.formats[0];
                return {
                  ...format,
                  note: item.label,
                  ext: item.ext,
                  title: data.title,
                  thumbnail: data.thumbnail
                };
              }
            }
          } catch (e) {}

          // 2. Client-side Cloudflare Pages Fallback
          try {
            const clientData = await resolveClientSide(link, item.preset);
            if (clientData.formats && clientData.formats.length > 0) {
              const format = clientData.formats[0];
              return {
                ...format,
                note: item.label,
                ext: item.ext,
                title: clientData.title,
                thumbnail: clientData.thumbnail
              };
            }
          } catch (e) {}

          return null;
        });

        const results = await Promise.all(promises);
        const validFormats = results.filter(r => r !== null);

        if (validFormats.length === 0) {
          throw new Error('All download formats failed to resolve.');
        }

        // Combine them into a single formats object
        const combinedData = {
          title: validFormats[0].title || 'Media Video',
          thumbnail: validFormats[0].thumbnail || '',
          type: type,
          formats: validFormats.map(f => ({
            directUrl: f.directUrl,
            note: f.note,
            ext: f.ext,
            sizeBytes: f.sizeBytes || f.size || 0
          }))
        };
        openFormatModal(combinedData);
        return;
      }

      // Standard single resolution for other links (TikTok, direct links, etc.)
      try {
        const resp = await fetch('/api/analyze', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ inputUrl: link, preset: selectedPreset })
        });
        const contentType = resp.headers.get('content-type') || '';
        if (resp.ok && contentType.includes('application/json')) {
          const data = await resp.json();
          openFormatModal(data);
          return;
        }
      } catch (e) {}

      const clientData = await resolveClientSide(link, selectedPreset);
      openFormatModal(clientData);

    } catch (err) {
      alert('Analysis Error: ' + (err.message || 'Unable to resolve link'));
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
    'dailymotion.com': 'Dailymotion',
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
  };

  function identifyLinkType(link) {
    const l = link.toLowerCase();
    if (l.includes('tiktok.com')) return 'tiktok';
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
    if (v.play) formats.push({ directUrl: v.play, note: 'HD No Watermark (MP4)', ext: 'mp4' });
    if (v.wmplay) formats.push({ directUrl: v.wmplay, note: 'Watermarked (MP4)', ext: 'mp4' });
    if (v.music) formats.push({ directUrl: v.music, note: 'Audio Only (MP3)', ext: 'mp3' });
    if (formats.length === 0) throw new Error('no playable formats in tikwm response');
    return { title: v.title || 'TikTok Video', thumbnail: v.cover || '', formats, type: 'tiktok' };
  }

  async function resolveYoutubeMetadata(link) {
    // oEmbed only gives title/thumbnail, never a download -- used to enrich
    // whatever the actual download resolver (Cobalt) returns.
    let title = null, thumbnail = null;
    const vidId = link.match(/(?:v=|youtu\.be\/|shorts\/)([a-zA-Z0-9_-]{11})/)?.[1];
    if (vidId) thumbnail = `https://img.youtube.com/vi/${vidId}/hqdefault.jpg`;
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

  const COBALT_STATIC_FALLBACKS = [
    "https://api.cobalt.liubquanti.click",
    "https://cobaltapi.cjs.nz",
    "https://rue-cobalt.xenon.zone",
    "https://lime.clxxped.lol"
  ];

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
    const instances = [];
    if (instanceUrl) {
      instances.push(instanceUrl);
    }

    try {
      const dirResp = await fetch('https://cobalt.directory/api/working?type=api', { signal: AbortSignal.timeout(3000) });
      if (dirResp.ok) {
        const dirData = await dirResp.json();
        const service = displayType.toLowerCase();
        const serviceEndpoints = dirData.data?.[service] || [];
        instances.push(...serviceEndpoints);
        if (dirData.data) {
          for (const s in dirData.data) {
            if (s !== service) {
              instances.push(...dirData.data[s]);
            }
          }
        }
      }
    } catch (e) {
      // fallback
    }

    const uniqueInstances = Array.from(new Set(instances));
    if (uniqueInstances.length === 0 || (uniqueInstances.length === 1 && uniqueInstances[0] === instanceUrl)) {
      uniqueInstances.push(...COBALT_STATIC_FALLBACKS);
    }

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
    'youtube': [
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
    btnText.textContent = on ? 'Analyzing…' : (urlInput.value.trim().length > 0 ? 'Download' : 'Paste Link');
    btnSpinner.classList.toggle('hidden', !on);
    analyzeBtn.disabled = on;
    if (on) {
      setAmbientState('state-analyzing');
    } else {
      refreshAmbientFromActivity();
    }
  }

  // 4. Format Popup Modal
  function openFormatModal(data) {
    modalTitle.textContent = data.title || 'Media Video';
    const platform = (data.type || 'direct').toUpperCase();

    // Determine Quality Tag (4K, HD, or SD) based on max file size or resolution
    let maxBytes = 0;
    data.formats.forEach(f => {
      const sz = f.sizeBytes || f.size || 0;
      if (sz > maxBytes) maxBytes = sz;
    });
    let qualityTag = 'HD';
    if (maxBytes > 80 * 1024 * 1024) qualityTag = '4K';
    else if (maxBytes < 5 * 1024 * 1024 && maxBytes > 0) qualityTag = 'SD';

    modalTypeBadge.textContent = `${qualityTag} • ${platform}`;

    if (data.thumbnail) {
      modalThumb.src = data.thumbnail;
      modalThumb.classList.remove('hidden');
      modalThumbFallback.classList.add('hidden');
    } else {
      modalThumb.classList.add('hidden');
      modalThumbFallback.classList.remove('hidden');
    }

    formatList.innerHTML = '';
    data.formats.forEach(f => {
      const cleanTitle = (data.title || 'download').replace(/[\\/:*?"<>|]/g, '').trim() || 'download';
      const filename   = `${cleanTitle}.${f.ext || 'mp4'}`;

      // User requested exact wording: Video (No watermark) / Video (Watermarked)
      let cleanNote = f.note || 'Video Download';
      if (cleanNote.toLowerCase().includes('no watermark') || cleanNote.toLowerCase().includes('hd no watermark')) {
        cleanNote = 'Video (No watermark)';
      } else if (cleanNote.toLowerCase().includes('watermark')) {
        cleanNote = 'Video (Watermarked)';
      } else if (cleanNote.toLowerCase().includes('audio')) {
        cleanNote = 'Audio Track (MP3)';
      }

      const thumbMarkup = data.thumbnail
        ? `<img src="${data.thumbnail}" class="option-thumb" alt="thumb">`
        : `<div class="option-thumb-fallback">${f.ext === 'mp3' ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>` : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>`}</div>`;

      const sizeText = f.sizeBytes ? fmtBytes(f.sizeBytes) : (f.size ? fmtBytes(f.size) : '');

      const item = document.createElement('div');
      item.className = 'format-item-card';
      item.innerHTML = `
        <div class="option-left">
          ${thumbMarkup}
          <div class="option-details">
            <div class="option-name">${cleanNote}</div>
            ${sizeText ? `<div class="option-size">${sizeText}</div>` : ''}
          </div>
        </div>
        <div class="option-actions">
          <a href="${f.directUrl || f.token}" class="btn-fast-dl" target="_blank" rel="noopener">Save to Device</a>
          <button class="btn-dl-item">Save to Library</button>
        </div>
      `;

      item.querySelector('.btn-dl-item').addEventListener('click', () => {
        startDownload(f.token || f.directUrl, filename, data.title, data.thumbnail, f.sizeBytes, f);
        formatModal.classList.add('hidden');
        urlInput.value = '';
        updateButtonText();
      });

      // Clear search when clicking Save to Device too
      item.querySelector('.btn-fast-dl').addEventListener('click', () => {
        formatModal.classList.add('hidden');
        urlInput.value = '';
        updateButtonText();
      });

      formatList.appendChild(item);
    });

    formatModal.classList.remove('hidden');
  }

  closeModalBtn.addEventListener('click', () => formatModal.classList.add('hidden'));
  formatModal.addEventListener('click', e => { if (e.target === formatModal) formatModal.classList.add('hidden'); });

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

  let isLocked = false;
  let controlsTimeout = null;

  function playVideoInCustomPlayer(title, blobUrl) {
    currentActiveTitle = title || 'Playing Media';
    currentActiveBlobUrl = blobUrl;

    playerTitle.textContent = currentActiveTitle;
    videoPlayer.src = blobUrl;
    playerModal.classList.remove('hidden');

    videoPlayer.play().catch(() => {});
    if (playSvg && pauseSvg) {
      playSvg.classList.add('hidden');
      pauseSvg.classList.remove('hidden');
    }
    resetControlsTimeout();
  }

  // Aspect Ratio Fit Auto Switcher (Contain, Cover, Stretch)
  videoPlayer.addEventListener('loadedmetadata', () => {
    const w = videoPlayer.videoWidth;
    const h = videoPlayer.videoHeight;
    if (w && h) {
      const isVertical = h > w;
      videoWrap.className = 'mx-video-wrapper fit-contain';
      btnAspectToggle.textContent = isVertical ? 'Fit (9:16)' : 'Fit (16:9)';
    }
  });

  const aspectModes = ['fit-contain', 'fit-cover', 'fit-stretch'];
  let currentAspectIdx = 0;
  btnAspectToggle.addEventListener('click', () => {
    currentAspectIdx = (currentAspectIdx + 1) % aspectModes.length;
    const mode = aspectModes[currentAspectIdx];
    videoWrap.className = `mx-video-wrapper ${mode}`;
    const labels = { 'fit-contain': 'Fit Auto', 'fit-cover': 'Fill Screen', 'fit-stretch': 'Stretch' };
    btnAspectToggle.textContent = labels[mode];
  });

  // Playback Speed Switcher (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
  const speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
  let currentSpeedIdx = 2;
  btnSpeed.addEventListener('click', () => {
    currentSpeedIdx = (currentSpeedIdx + 1) % speeds.length;
    const speed = speeds[currentSpeedIdx];
    videoPlayer.playbackRate = speed;
    btnSpeed.textContent = `${speed}x`;
  });

  // Mute / Unmute
  const volumeIcon = document.getElementById('volumeIcon');
  const mutedIcon  = document.getElementById('mutedIcon');
  btnMute.addEventListener('click', () => {
    videoPlayer.muted = !videoPlayer.muted;
    if (volumeIcon && mutedIcon) {
      volumeIcon.classList.toggle('hidden', videoPlayer.muted);
      mutedIcon.classList.toggle('hidden', !videoPlayer.muted);
    }
  });

  // Lock Screen Touch Toggle
  const lockIcon   = document.getElementById('lockIcon');
  const unlockIcon = document.getElementById('unlockIcon');
  btnLock.addEventListener('click', () => {
    isLocked = !isLocked;
    if (lockIcon && unlockIcon) {
      lockIcon.classList.toggle('hidden', !isLocked);
      unlockIcon.classList.toggle('hidden', isLocked);
    }
    mxControls.classList.toggle('locked', isLocked);
  });

  const playSvg  = document.getElementById('playSvg');
  const pauseSvg = document.getElementById('pauseSvg');
  const fsEnterSvg = document.getElementById('fsEnterSvg');
  const fsExitSvg  = document.getElementById('fsExitSvg');

  // Play / Pause Toggle
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

  // Rewind & Forward (-10s / +10s)
  btnRewind.addEventListener('click', () => { if (!isLocked) videoPlayer.currentTime = Math.max(0, videoPlayer.currentTime - 10); });
  btnForward.addEventListener('click', () => { if (!isLocked) videoPlayer.currentTime = Math.min(videoPlayer.duration || 0, videoPlayer.currentTime + 10); });

  // Double Tap Touch Zones -- with real visual feedback (ripple + icon flash),
  // not a silent seek. This is the pattern every mainstream mobile video
  // player uses so a tap has an obvious, satisfying response.
  const REWIND_ICON = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 19 2 12 11 5 11 19"></polygon><polygon points="22 19 13 12 22 5 22 19"></polygon></svg>';
  const FORWARD_ICON = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 19 22 12 13 5 13 19"></polygon><polygon points="2 19 11 12 2 5 2 19"></polygon></svg>';

  function showSeekFeedback(zone, iconSvg, label) {
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
  videoWrap.addEventListener('mousemove', showControlsTemporarily);
  videoWrap.addEventListener('touchstart', showControlsTemporarily);

  function showControlsTemporarily() {
    mxControls.classList.remove('fade-out');
    resetControlsTimeout();
  }

  function resetControlsTimeout() {
    clearTimeout(controlsTimeout);
    controlsTimeout = setTimeout(() => {
      if (!videoPlayer.paused && !isLocked) {
        mxControls.classList.add('fade-out');
      }
    }, 3500);
  }

  videoPlayer.addEventListener('timeupdate', () => {
    if (!isNaN(videoPlayer.duration)) {
      const pct = (videoPlayer.currentTime / videoPlayer.duration) * 100;
      playerSeek.value = pct;
      playerProgress.style.width = `${pct}%`;
      playerTime.textContent = `${fmtTime(videoPlayer.currentTime)} / ${fmtTime(videoPlayer.duration)}`;
    }
  });

  // Buffered-range indicator on the seekbar -- shows how much has actually
  // downloaded/decoded ahead of playback, not just current position.
  videoPlayer.addEventListener('progress', () => {
    if (!bufferedFill || !videoPlayer.duration || isNaN(videoPlayer.duration)) return;
    const buffered = videoPlayer.buffered;
    if (buffered.length > 0) {
      const end = buffered.end(buffered.length - 1);
      bufferedFill.style.width = `${(end / videoPlayer.duration) * 100}%`;
    }
  });

  // Buffering spinner -- real loading feedback instead of a frozen frame
  // while the video is fetching data or seeking.
  if (bufferSpinner) {
    videoPlayer.addEventListener('waiting', () => bufferSpinner.classList.remove('hidden'));
    videoPlayer.addEventListener('playing', () => bufferSpinner.classList.add('hidden'));
    videoPlayer.addEventListener('canplay', () => bufferSpinner.classList.add('hidden'));
    videoPlayer.addEventListener('seeking', () => bufferSpinner.classList.remove('hidden'));
    videoPlayer.addEventListener('seeked', () => bufferSpinner.classList.add('hidden'));
  }

  playerSeek.addEventListener('input', () => {
    if (!isNaN(videoPlayer.duration)) {
      const seekTo = (playerSeek.value / 100) * videoPlayer.duration;
      videoPlayer.currentTime = seekTo;
    }
  });

  // Fullscreen & Exit Fullscreen Toggle (Cross-Browser Supported)
  btnFullscreen.addEventListener('click', () => {
    const isFS = document.fullscreenElement || document.webkitFullscreenElement;
    if (isFS) {
      if (document.exitFullscreen) document.exitFullscreen();
      else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
    } else {
      if (videoWrap.requestFullscreen) videoWrap.requestFullscreen();
      else if (videoWrap.webkitRequestFullscreen) videoWrap.webkitRequestFullscreen();
      else if (videoPlayer.requestFullscreen) videoPlayer.requestFullscreen();
    }
  });

  document.addEventListener('fullscreenchange', handleFSChange);
  document.addEventListener('webkitfullscreenchange', handleFSChange);

  function handleFSChange() {
    const isFS = document.fullscreenElement || document.webkitFullscreenElement;
    if (fsEnterSvg && fsExitSvg) {
      fsEnterSvg.classList.toggle('hidden', isFS);
      fsExitSvg.classList.toggle('hidden', !isFS);
    }
  }

  btnSharePlayer.addEventListener('click', () => shareMedia(currentActiveTitle, currentActiveBlobUrl));
  btnOpenWithPlayer.addEventListener('click', () => openWithMedia(currentActiveBlobUrl));

  closePlayerBtn.addEventListener('click', closeCustomPlayer);
  playerModal.addEventListener('click', e => { if (e.target === playerModal) closeCustomPlayer(); });

  function closeCustomPlayer() {
    videoPlayer.pause();
    videoPlayer.src = '';
    playerModal.classList.add('hidden');
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
    a.click();
  }

  // 7. Delete Confirmation Modal Popup
  function promptDeleteConfirmation(cardElem, libItem = null) {
    pendingDeleteCard = cardElem;
    pendingDeleteLibItem = libItem;
    deleteModal.classList.remove('hidden');
  }

  closeDeleteModalBtn.addEventListener('click', () => deleteModal.classList.add('hidden'));
  deleteModal.addEventListener('click', e => { if (e.target === deleteModal) deleteModal.classList.add('hidden'); });

  btnDelTaskOnly.addEventListener('click', () => {
    if (pendingDeleteCard) pendingDeleteCard.remove();
    deleteModal.classList.add('hidden');
  });

  btnDelTaskAndFile.addEventListener('click', () => {
    if (pendingDeleteCard) pendingDeleteCard.remove();
    if (pendingDeleteLibItem) {
      const idx = libraryItems.indexOf(pendingDeleteLibItem);
      if (idx > -1) libraryItems.splice(idx, 1);
      renderLibraryGrid();
    }
    deleteModal.classList.add('hidden');
  });

  // 8. Download Execution Engine (Supports Node Server Token + Client Direct Streams)
  async function startDownload(tokenOrUrl, filename, title, thumbnail, knownSize, formatObj) {
    const card = addDownloadCard(title, filename, thumbnail);
    const { cardElem, bar, percent, meta, statusText, btnCancel, actionsContainer } = card;

    const controller = new AbortController();

    activeDownloadCount++;
    refreshAmbientFromActivity();
    let countReleased = false;
    const releaseCount = () => {
      if (countReleased) return;
      countReleased = true;
      activeDownloadCount = Math.max(0, activeDownloadCount - 1);
    };

    btnCancel.addEventListener('click', () => {
      controller.abort();
      cardElem.classList.add('failed');
      percent.textContent = 'Cancelled';
      statusText.textContent = 'Cancelled';
      meta.textContent = 'Download cancelled';
      releaseCount();
      refreshAmbientFromActivity();
    });

    try {
      let downloadEndpoint = tokenOrUrl.startsWith('http') ? tokenOrUrl : `/api/download?token=${tokenOrUrl}`;
      const resp = await fetch(downloadEndpoint, { signal: controller.signal });
      if (!resp.ok) throw new Error(`Server status ${resp.status}`);

      const totalSize = knownSize || parseInt(resp.headers.get('content-length') || '0', 10) || 0;
      const reader    = resp.body.getReader();
      const chunks    = [];
      let   received  = 0;
      let   startTime = Date.now();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        received += value.length;

        const pct     = totalSize ? Math.round((received / totalSize) * 100) : null;
        const elapsed = (Date.now() - startTime) / 1000;
        const speed   = received / elapsed;
        const eta     = totalSize && speed ? Math.round((totalSize - received) / speed) : null;

        bar.style.width      = pct != null ? `${pct}%` : '50%';
        percent.textContent  = pct != null ? `${pct}%` : '…';
        meta.textContent     = `${fmtBytes(received)}${totalSize ? ' / ' + fmtBytes(totalSize) : ''}    ${fmtSpeed(speed)}${eta ? '    ' + fmtEta(eta) : ''}`;
      }

      // Complete download
      const blob    = new Blob(chunks, { type: 'video/mp4' });
      const blobUrl = URL.createObjectURL(blob);
      const a       = document.createElement('a');
      a.href     = blobUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);

      cardElem.classList.add('completed');
      bar.style.width = '100%';
      percent.textContent = '100%';
      statusText.textContent = 'Finished';
      meta.textContent = 'Saved to Downloads';
      showToast('Download complete', title || filename);
      releaseCount();
      flashAmbientState('state-completed');

      actionsContainer.innerHTML = `
        <button class="btn-card-action btn-play" title="Play Video">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          <span>Play</span>
        </button>
        <button class="btn-card-action btn-openwith" title="Open With App">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg>
        </button>
        <button class="btn-card-action btn-share" title="Share Video">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
        </button>
        <button class="btn-card-action btn-del" title="Delete Video">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
        </button>
      `;

      const libObj = { title: title || filename, filename, thumbnail, blobUrl, size: received };

      actionsContainer.querySelector('.btn-play').addEventListener('click', () => playVideoInCustomPlayer(title || filename, blobUrl));
      actionsContainer.querySelector('.btn-openwith').addEventListener('click', () => openWithMedia(blobUrl));
      actionsContainer.querySelector('.btn-share').addEventListener('click', () => shareMedia(title || filename, blobUrl));
      actionsContainer.querySelector('.btn-del').addEventListener('click', () => promptDeleteConfirmation(cardElem, libObj));

      // Add to Library Grid
      libraryItems.unshift(libObj);
      renderLibraryGrid();

    } catch (err) {
      if (err.name !== 'AbortError') {
        cardElem.classList.add('failed');
        percent.textContent = 'Failed';
        statusText.textContent = 'Failed';
        meta.textContent = err.message;
        showToast('Download failed', err.message, true);
        releaseCount();
        flashAmbientState('state-failed');
      }
    }
  }

  function addDownloadCard(title, filename, thumbnail) {
    if (emptyState) emptyState.style.display = 'none';

    const cardElem = document.createElement('div');
    cardElem.className = 'dl-card';
    const thumbHtml = thumbnail
      ? `<img src="${thumbnail}" alt="thumb" onerror="this.parentElement.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>'">`
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
      actionsContainer: cardElem.querySelector('.dl-actions')
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
        ? `<img src="${item.thumbnail}" alt="" onerror="this.parentElement.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>'">`
        : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>`;

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

      card.querySelector('.lib-thumb').addEventListener('click', () => playVideoInCustomPlayer(item.title, item.blobUrl));
      card.querySelector('.btn-lib-open').addEventListener('click', (e) => { e.stopPropagation(); openWithMedia(item.blobUrl); });
      card.querySelector('.btn-lib-share').addEventListener('click', (e) => { e.stopPropagation(); shareMedia(item.title, item.blobUrl); });
      card.querySelector('.btn-lib-del').addEventListener('click', (e) => { e.stopPropagation(); promptDeleteConfirmation(card, item); });

      libraryGrid.appendChild(card);
    });
  }

  clearHistoryBtn.addEventListener('click', () => {
    downloadsList.innerHTML = '';
    downloadsList.appendChild(emptyState);
    if (emptyState) emptyState.style.display = 'flex';
  });

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
});
