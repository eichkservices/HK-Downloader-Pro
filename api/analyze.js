const https = require('https');

const COBALT_STATIC_FALLBACKS = [
  "https://api.cobalt.liubquanti.click",
  "https://apicobalt.mgytr.top",
  "https://subito-c.meowing.de",
  "https://lime.clxxped.lol"
];

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(body)); } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
}

function postJson(url, payload) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    const postData = JSON.stringify(payload);
    const options = {
      hostname: urlObj.hostname,
      port: 443,
      path: urlObj.pathname + urlObj.search,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    };
    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(body) }); }
        catch (e) { resolve({ status: res.statusCode, raw: body }); }
      });
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

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

async function resolveTikwm(link) {
  const data = await fetchJson(`https://www.tikwm.com/api/?url=${encodeURIComponent(link)}`);
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
  let title = null, thumbnail = null;
  const vidId = link.match(/(?:v=|youtu\.be\/|shorts\/)([a-zA-Z0-9_-]{11})/)?.[1];
  if (vidId) thumbnail = `https://img.youtube.com/vi/${vidId}/hqdefault.jpg`;
  try {
    const oe = await fetchJson(`https://www.youtube.com/oembed?url=${encodeURIComponent(link)}&format=json`);
    title = oe.title || title;
    thumbnail = oe.thumbnail_url || thumbnail;
  } catch (e) {}
  return { title, thumbnail };
}

async function resolveCobaltSingle(link, displayType, instanceUrl, preset) {
  const p = preset || { videoQuality: '1080', downloadMode: 'auto' };
  const payload = { url: link, videoQuality: p.videoQuality, downloadMode: p.downloadMode };
  if (p.downloadMode === 'audio') {
    payload.audioFormat = p.audioFormat;
    if (p.audioBitrate) payload.audioBitrate = p.audioBitrate;
  }
  const res = await postJson(instanceUrl, payload);
  if (res.status !== 200) {
    throw new Error(`HTTP ${res.status}`);
  }
  const data = res.body;
  if (!data) throw new Error('empty response');

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

async function resolveCobalt(link, displayType, cobaltInstanceUrl, preset) {
  const instances = [];
  if (cobaltInstanceUrl) {
    instances.push(cobaltInstanceUrl);
  }

  // Fetch active working endpoints from cobalt.directory dynamically at runtime!
  try {
    const dirData = await fetchJson('https://cobalt.directory/api/working?type=api');
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
  } catch (e) {
    // ignore
  }

  const uniqueInstances = Array.from(new Set(instances));
  if (uniqueInstances.length === 0 || (uniqueInstances.length === 1 && uniqueInstances[0] === cobaltInstanceUrl)) {
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

module.exports = async (req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Credentials', true);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader('Access-Control-Allow-Headers', 'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version');

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  let inputUrl = req.body?.inputUrl || req.body?.url || '';
  let preset = req.body?.preset || { videoQuality: '1080', downloadMode: 'auto' };

  if (!inputUrl) {
    return res.status(400).json({ error: 'Missing inputUrl' });
  }

  const type = identifyLinkType(inputUrl);
  const chain = type === 'tiktok'
    ? [
        { name: 'tikwm', run: () => resolveTikwm(inputUrl) },
        { name: 'cobalt', run: () => resolveCobalt(inputUrl, 'TikTok', null, preset) },
      ]
    : type === 'youtube'
    ? [
        { name: 'cobalt', run: async () => {
            const meta = await resolveYoutubeMetadata(inputUrl).catch(() => ({}));
            const result = await resolveCobalt(inputUrl, 'YouTube', null, preset);
            return { ...result, title: meta.title || result.title, thumbnail: meta.thumbnail || result.thumbnail };
          }
        },
      ]
    : type === 'direct-link'
    ? [{ name: 'direct-link', run: () => resolveDirectLink(inputUrl) }]
    : [{ name: 'cobalt', run: () => resolveCobalt(inputUrl, type.charAt(0).toUpperCase() + type.slice(1), null, preset) }];

  const failures = [];
  for (const resolver of chain) {
    try {
      const result = await resolver.run();
      return res.status(200).json(result);
    } catch (e) {
      failures.push(`${resolver.name}: ${e.message}`);
    }
  }

  return res.status(502).json({
    error: `Couldn't resolve this link (tried: ${failures.join('; ')})`
  });
};
