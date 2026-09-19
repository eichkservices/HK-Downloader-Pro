// functions/api/analyze.js
//
// Cloudflare Pages Function -- deploys automatically alongside the static
// site on the SAME domain (no separate Render/Node hosting needed). Handles
// POST /api/analyze { inputUrl } and does the actual video resolution
// server-side, so normal users never see a "configure Cobalt" prompt.
//
// [Claude / Sonnet] Added this file. Mirrors the client-side resolver chain
// in app.js (same platform list, same Cobalt API contract), but runs on
// Cloudflare's servers where the Cobalt instance URL lives as a secret
// environment variable -- never sent to or configured by the browser.
//
// ---- ONE-TIME SETUP REQUIRED (on the Cloudflare dashboard, not in code) ----
// Cloudflare Pages project -> Settings -> Environment variables -> add:
//   COBALT_INSTANCE_URL = https://your-self-hosted-cobalt-instance.example.com
// Do this for both "Production" and "Preview" environments.
// That's the ONLY thing that needs configuring outside this codebase.
// -----------------------------------------------------------------------

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

function cleanUrl(rawUrl) {
  if (!rawUrl) return '';
  let u = rawUrl.trim().replace(/^["'<(\[{`\s]+|["'>)\]}`.,;:\s]+$/g, '');
  try {
    const parsed = new URL(u);
    const trackingParams = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'utm', 'si', 'feature', 'fbclid', 'igsh', 'ref', 'usp', '_ga', '_gl', 'mibextid'];
    for (const p of trackingParams) {
      parsed.searchParams.delete(p);
    }
    return parsed.toString();
  } catch (e) {
    return u;
  }
}

async function resolveFacebookDirect(link) {
  const resp = await fetch(link, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
      'Upgrade-Insecure-Requests': '1'
    }
  });

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
    title = titleMatch[1].replace(/\\u[\dA-F]{4}/gi, m => String.fromCharCode(parseInt(m.replace(/\\u/g, ''), 16)))
                         .replace(/\s*\|\s*Facebook.*$/i, '')
                         .replace(/\\/g, '')
                         .trim();
  }

  let thumbnail = '';
  const thumbMatch = body.match(/"preferred_thumbnail"\s*:\s*\{\s*"image"\s*:\s*\{\s*"uri"\s*:\s*"([^"]+)"/) ||
                     body.match(/property="og:image"\s+content="([^"]+)"/) ||
                     body.match(/content="([^"]+)"\s+property="og:image"/);
  if (thumbMatch) {
    thumbnail = thumbMatch[1].replace(/\\/g, '');
  }

  const cleanHd = hdMatch ? hdMatch[1].replace(/\\/g, '') : null;
  const cleanSd = sdMatch ? sdMatch[1].replace(/\\/g, '') : null;

  const formats = [];
  if (cleanHd) formats.push({ directUrl: cleanHd, note: '🎬 HD (1080p MP4)', ext: 'mp4' });
  if (cleanSd) formats.push({ directUrl: cleanSd, note: '🎬 SD (480p MP4)', ext: 'mp4' });

  if (formats.length === 0) throw new Error('No direct streams found in Facebook page');

  return { title, thumbnail, type: 'facebook', formats };
}

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
  "https://apicobalt.mgytr.top",
  "https://subito-c.meowing.de",
  "https://lime.clxxped.lol"
];

async function resolveCobaltSingle(link, displayType, instanceUrl, preset) {
  const p = preset || { videoQuality: '1080', downloadMode: 'auto' };
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

async function resolveCobalt(link, displayType, cobaltInstanceUrl, preset) {
  const instances = [];
  if (cobaltInstanceUrl) {
    instances.push(cobaltInstanceUrl);
  }

  // Fetch active working endpoints from cobalt.directory dynamically at runtime!
  try {
    const dirResp = await fetch('https://cobalt.directory/api/working?type=api', { signal: AbortSignal.timeout(3000) });
    if (dirResp.ok) {
      const dirData = await dirResp.json();
      const service = displayType.toLowerCase();
      // Try endpoints for the specific service first
      const serviceEndpoints = dirData.data?.[service] || [];
      instances.push(...serviceEndpoints);
      // Fallback: add all other working endpoints to maximize chance of success
      if (dirData.data) {
        for (const s in dirData.data) {
          if (s !== service) {
            instances.push(...dirData.data[s]);
          }
        }
      }
    }
  } catch (e) {
    // If cobalt.directory is down or times out, fallback to hardcoded list
  }

  // De-duplicate endpoints while preserving order
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

export async function onRequestPost(context) {
  const { request, env } = context;
  const cobaltInstanceUrl = env.COBALT_INSTANCE_URL || '';

  let inputUrl;
  let preset;
  try {
    const body = await request.json();
    inputUrl = body.inputUrl;
    preset = body.preset || { videoQuality: '1080', downloadMode: 'auto' };
  } catch (e) {
    return new Response(JSON.stringify({ error: 'Invalid request body' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  const ytdlpApiUrl = env.YTDLP_API_URL || '';
  if (ytdlpApiUrl) {
    try {
      const cleanApiUrl = ytdlpApiUrl.replace(/\/+$/, '');
      const resp = await fetch(`${cleanApiUrl}/api/analyze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ inputUrl, preset })
      });
      if (resp.ok) {
        const data = await resp.json();
        if (data.formats && Array.isArray(data.formats)) {
          data.formats.forEach(f => {
            if (f.token && !f.token.startsWith('http')) {
              f.token = `${cleanApiUrl}/api/download?token=${f.token}`;
            }
          });
        }
        return new Response(JSON.stringify(data), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
    } catch (e) {
      // fallback on error
    }
  }

  if (!inputUrl) {
    return new Response(JSON.stringify({ error: 'Missing inputUrl' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  inputUrl = cleanUrl(inputUrl);

  const type = identifyLinkType(inputUrl);
  const chain = type === 'tiktok'
    ? [
        { name: 'tikwm', run: () => resolveTikwm(inputUrl) },
        { name: 'cobalt', run: () => resolveCobalt(inputUrl, 'TikTok', cobaltInstanceUrl, preset) },
      ]
    : type === 'facebook'
    ? [
        { name: 'facebook-direct', run: () => resolveFacebookDirect(inputUrl) },
        { name: 'cobalt', run: () => resolveCobalt(inputUrl, 'Facebook', cobaltInstanceUrl, preset) },
      ]
    : type === 'youtube'
    ? [
        { name: 'cobalt', run: async () => {
            const meta = await resolveYoutubeMetadata(inputUrl).catch(() => ({}));
            const result = await resolveCobalt(inputUrl, 'YouTube', cobaltInstanceUrl, preset);
            return { ...result, title: meta.title || result.title, thumbnail: meta.thumbnail || result.thumbnail };
          }
        },
      ]
    : type === 'direct-link'
    ? [{ name: 'direct-link', run: () => resolveDirectLink(inputUrl) }]
    : [{ name: 'cobalt', run: () => resolveCobalt(inputUrl, type.charAt(0).toUpperCase() + type.slice(1), cobaltInstanceUrl, preset) }];

  const failures = [];
  for (const resolver of chain) {
    try {
      const result = await resolver.run();
      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (e) {
      failures.push(`${resolver.name}: ${e.message}`);
    }
  }

  return new Response(JSON.stringify({
    error: `Couldn't resolve this link (tried: ${failures.join('; ')})`
  }), {
    status: 502,
    headers: { 'Content-Type': 'application/json' }
  });
}
