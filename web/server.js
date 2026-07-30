const express = require('express');
const cors    = require('cors');
const axios   = require('axios');
const path    = require('path');
const crypto  = require('crypto');
const { spawn, execFile } = require('child_process');

const app  = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));
app.use(express.static(__dirname));

// Silence favicon 404 log in browser console
app.get('/favicon.ico', (req, res) => res.status(204).end());

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36';

// In-memory session store
const sessions = new Map();

function fmtBytes(b) {
  if (!b || b <= 0) return '?';
  const u = ['B','KB','MB','GB'];
  const i = Math.min(Math.floor(Math.log10(b) / Math.log10(1024)), 3);
  return (b / Math.pow(1024, i)).toFixed(1) + ' ' + u[i];
}

function platformOf(u = '') {
  const l = u.toLowerCase();
  if (l.includes('tiktok.com') || l.includes('vm.tiktok'))  return 'tiktok';
  if (l.includes('youtube.com') || l.includes('youtu.be'))   return 'youtube';
  if (l.includes('instagram.com') || l.includes('cdninstagram')) return 'instagram';
  if (l.includes('facebook.com') || l.includes('fbcdn'))     return 'facebook';
  if (l.includes('reddit.com') || l.includes('redd.it'))     return 'reddit';
  if (l.includes('twitter.com') || l.includes('x.com') || l.includes('twimg')) return 'twitter';
  return 'direct';
}

function refererOf(platform) {
  const m = { youtube:'https://www.youtube.com/', tiktok:'https://www.tiktok.com/',
    instagram:'https://www.instagram.com/', facebook:'https://www.facebook.com/',
    reddit:'https://www.reddit.com/', twitter:'https://www.twitter.com/' };
  return m[platform] || 'https://www.google.com/';
}

function sanitize(s) {
  return (s||'download').replace(/[\\/:*?"<>|]/g,'').replace(/\s+/g,' ').trim().slice(0,100)||'download';
}

function makeToken(info) {
  const t = crypto.randomUUID();
  sessions.set(t, info);
  return t;
}

// ─── yt-dlp metadata extractor ──────────────────────────────────────────────

function ytdlpJson(rawUrl) {
  return new Promise((resolve, reject) => {
    const args = ['-m', 'yt_dlp',
      '-j',
      '--no-playlist',
      '--no-warnings',
      '--socket-timeout', '15',
      rawUrl
    ];
    execFile('python', args, { maxBuffer: 25 * 1024 * 1024, timeout: 45000 }, (err, stdout, stderr) => {
      if (err) return reject(err);
      try {
        const lines = stdout.trim().split('\n');
        resolve(JSON.parse(lines[lines.length - 1]));
      } catch(e) { reject(e); }
    });
  });
}

// ─── ANALYZE endpoint ────────────────────────────────────────────────────────

app.post('/api/analyze', async (req, res) => {
  let inputUrl = req.body?.inputUrl || req.body?.url || '';
  if (!inputUrl) return res.status(400).json({ error: 'URL required' });

  try { inputUrl = decodeURIComponent(inputUrl); } catch(e) {}
  const targetUrl = inputUrl.trim();
  const platform  = platformOf(targetUrl);

  // 1. yt-dlp (YouTube, TikTok, Reddit, IG, Twitter)
  try {
    const meta = await ytdlpJson(targetUrl);
    const title     = meta.title || meta.fulltitle || 'Media Video';
    let   thumbnail = meta.thumbnail || '';
    if (!thumbnail && meta.thumbnails?.length)
      thumbnail = meta.thumbnails[meta.thumbnails.length - 1].url;

    const formats = [];

    if (meta.formats?.length) {
      const mp4s = meta.formats
        .filter(f => f.url && f.ext === 'mp4' && f.vcodec && f.vcodec !== 'none' && f.height)
        .sort((a, b) => (b.height||0) - (a.height||0));

      const seen = new Set();
      for (const f of mp4s) {
        if (seen.has(f.height)) continue;
        seen.add(f.height);
        const label = f.format_note || `${f.height}p`;
        const size  = f.filesize || f.filesize_approx || null;
        formats.push({
          token: makeToken({ sourceUrl: targetUrl, formatId: f.format_id, filename: `${sanitize(title)}.mp4`, platform, ext: 'mp4' }),
          note: `🎬 ${label} MP4 — ${fmtBytes(size)}`, ext: 'mp4', sizeBytes: size
        });
      }

      const audios = meta.formats
        .filter(f => f.url && f.vcodec === 'none' && f.acodec && f.acodec !== 'none')
        .sort((a, b) => (b.abr||0) - (a.abr||0));
      if (audios.length) {
        const a   = audios[0];
        const ext = a.ext || 'm4a';
        const sz  = a.filesize || a.filesize_approx || null;
        formats.push({
          token: makeToken({ sourceUrl: targetUrl, formatId: a.format_id, filename: `${sanitize(title)}.${ext}`, platform, ext }),
          note: `🎵 Audio ${ext.toUpperCase()} ${a.abr ? Math.round(a.abr)+'kbps' : 'HQ'} — ${fmtBytes(sz)}`, ext, sizeBytes: sz
        });
      }
    }

    if (!formats.length && meta.url) {
      const ext  = meta.ext || 'mp4';
      const size = meta.filesize || meta.filesize_approx || null;
      formats.push({
        token: makeToken({ sourceUrl: targetUrl, formatId: meta.format_id, filename: `${sanitize(title)}.${ext}`, platform, ext }),
        note: `🎬 Best Quality ${ext.toUpperCase()} — ${fmtBytes(size)}`, ext, sizeBytes: size
      });
    }

    if (formats.length) {
      return res.json({ title, thumbnail, formats, type: meta.extractor || platform });
    }
  } catch(err) {}

  // 2. TikWM fallback
  if (platform === 'tiktok') {
    try {
      const enc = encodeURIComponent(targetUrl);
      const { data } = await axios.get(`https://www.tikwm.com/api/?url=${enc}`,
        { headers:{ 'User-Agent': UA, 'Referer':'https://www.tikwm.com/' }, timeout: 8000 });
      if (data?.code === 0 && data.data) {
        const v     = data.data;
        const title = v.title?.trim() || 'TikTok Video';
        const thumb = v.cover || '';
        const fmts  = [];
        for (const [dlUrl, note, ext] of [
          [v.play,   `🎬 HD No Watermark — ${fmtBytes(v.size)}`,         'mp4'],
          [v.wmplay, `🎬 Watermarked — ${fmtBytes(v.wm_size)}`,           'mp4'],
          [v.music,  '🎵 Original Audio (MP3)',                            'mp3'],
        ]) {
          if (!dlUrl) continue;
          fmts.push({
            token: makeToken({ sourceUrl: targetUrl, formatId: null, directUrl: dlUrl, filename: `${sanitize(title)}.${ext}`, platform: 'tiktok', ext }),
            note, ext, sizeBytes: null
          });
        }
        if (fmts.length) return res.json({ title, thumbnail: thumb, formats: fmts, type: 'tiktok' });
      }
    } catch(e) {}
  }

  // 3. YouTube oEmbed fallback
  if (platform === 'youtube') {
    let title = 'YouTube Video';
    let thumbnail = '';
    try {
      const oe = await axios.get(`https://www.youtube.com/oembed?url=${encodeURIComponent(targetUrl)}&format=json`,
        { headers:{ 'User-Agent': UA }, timeout: 5000 });
      title     = oe.data.title     || title;
      thumbnail = oe.data.thumbnail_url || thumbnail;
    } catch(e) {}

    const vidId = targetUrl.match(/(?:v=|youtu\.be\/|shorts\/)([a-zA-Z0-9_-]{11})/)?.[1];
    if (!thumbnail && vidId) thumbnail = `https://img.youtube.com/vi/${vidId}/hqdefault.jpg`;

    const fmts = [
      { token: makeToken({ sourceUrl: targetUrl, formatId: 'bestvideo[ext=mp4]+bestaudio/best[ext=mp4]/best', filename: `${sanitize(title)}.mp4`, platform: 'youtube', ext: 'mp4' }),
        note: '🎬 Best Available Quality (MP4)', ext: 'mp4', sizeBytes: null },
      { token: makeToken({ sourceUrl: targetUrl, formatId: 'bestaudio[ext=m4a]/bestaudio', filename: `${sanitize(title)}.m4a`, platform: 'youtube', ext: 'm4a' }),
        note: '🎵 Audio Only (M4A)', ext: 'm4a', sizeBytes: null },
    ];
    return res.json({ title, thumbnail, formats: fmts, type: 'youtube' });
  }

  // 4. Direct link fallback
  let sizeBytes = null;
  try {
    const r = await axios.head(targetUrl, { headers:{ 'User-Agent': UA }, timeout: 5000 });
    if (r.headers['content-length']) sizeBytes = +r.headers['content-length'];
  } catch(e) {}

  let fname = targetUrl.split('/').pop().split('?')[0] || 'Media Video';
  if (['watch','video','index','shorts',''].includes(fname) || fname.length < 3) fname = 'Media Video';
  const ext = fname.includes('.') ? fname.split('.').pop().toLowerCase() : 'mp4';

  return res.json({
    title: fname, thumbnail: '', type: 'direct',
    formats: [{
      token: makeToken({ sourceUrl: targetUrl, formatId: null, directUrl: targetUrl, filename: `${sanitize(fname)}.${ext}`, platform, ext }),
      note: `📁 ${ext.toUpperCase()} — ${fmtBytes(sizeBytes)}`, ext, sizeBytes
    }]
  });
});

// ─── DOWNLOAD endpoint ───────────────────────────────────────────────────────

app.get('/api/download', (req, res) => {
  const { token } = req.query;
  if (!token || !sessions.has(token))
    return res.status(404).json({ error: 'Session expired — please analyse the link again.' });

  const sess = sessions.get(token);
  sessions.delete(token);

  const { sourceUrl, formatId, filename, platform, directUrl, ext } = sess;
  const mimes = { mp4:'video/mp4', m4a:'audio/mp4', mp3:'audio/mpeg', webm:'video/webm', mkv:'video/x-matroska' };

  res.setHeader('Content-Disposition', `attachment; filename="${encodeURIComponent(filename)}"`);
  res.setHeader('Content-Type', mimes[ext] || 'application/octet-stream');
  res.setHeader('X-Content-Type-Options', 'nosniff');

  const useYtDlp = sourceUrl && formatId && ['youtube','tiktok','instagram','facebook','reddit','twitter'].includes(platform);

  if (useYtDlp) {
    const args = ['-m','yt_dlp',
      '--no-playlist', '--no-warnings',
      '--socket-timeout', '15',
      '-f', formatId,
      '-o', '-',
      sourceUrl
    ];
    const proc = spawn('python', args, { windowsHide: true });
    proc.stdout.pipe(res);
    proc.on('error', err => { if (!res.headersSent) res.status(500).end(); });
    res.on('close', () => { try { proc.kill(); } catch(e){} });
  } else {
    const dlUrl   = directUrl || sourceUrl;
    const referer = refererOf(platform);
    axios({ method:'get', url: dlUrl, headers:{ 'User-Agent': UA, 'Referer': referer },
      responseType:'stream', timeout: 120_000 })
    .then(r => {
      if (r.headers['content-length']) res.setHeader('Content-Length', r.headers['content-length']);
      r.data.pipe(res);
    })
    .catch(err => { if (!res.headersSent) res.status(502).send('Upstream error: '+err.message); });
  }
});

app.listen(PORT, () => {
  console.log(`\n${'═'.repeat(54)}`);
  console.log(`  ⚡ HK Downloader Pro  →  http://localhost:${PORT}`);
  console.log(`  🔧 Engine: yt-dlp pipe + axios proxy`);
  console.log(`${'═'.repeat(54)}\n`);
});
