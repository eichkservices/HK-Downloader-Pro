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

async function resolveInstagramDirect(link) {
  const shortcodeMatch = link.match(/(?:reel|reels|p|tv)\/([a-zA-Z0-9_-]+)/);
  if (!shortcodeMatch) throw new Error('Invalid Instagram URL');
  const shortcode = shortcodeMatch[1];

  // Try direct Instagram oEmbed and API endpoint
  const headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.9',
    'Sec-Fetch-Mode': 'navigate'
  };

  let title = 'Instagram Video';
  let thumbnail = '';
  const formats = [];

  // 1. Fetch metadata via oEmbed
  try {
    const oeResp = await fetch(`https://api.instagram.com/oembed/?url=${encodeURIComponent(link)}`, {
      headers: { 'User-Agent': headers['User-Agent'] },
      signal: AbortSignal.timeout(4000)
    });
    if (oeResp.ok) {
      const oeData = await oeResp.json();
      title = oeData.title || oeData.author_name ? `${oeData.author_name} - Instagram` : title;
      thumbnail = oeData.thumbnail_url || thumbnail;
    }
  } catch (e) {}

  // 2. Fetch page HTML / Embed to extract direct mp4 video stream (which includes full muxed audio)
  try {
    const embedResp = await fetch(`https://www.instagram.com/p/${shortcode}/embed/captioned/`, {
      headers,
      signal: AbortSignal.timeout(6000)
    });
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

        // Dedicated audio track extracted from the video stream
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

async function resolvePinterestDirect(link) {
  const resp = await fetch(link, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    },
    signal: AbortSignal.timeout(6000)
  });
  const html = await resp.text();
  const titleMatch = html.match(/<title>([^<]+)<\/title>/);
  const title = titleMatch ? titleMatch[1].split('|')[0].trim() : 'Pinterest Media';

  const vids = html.match(/https:\/\/v\.pinimg\.com\/[^"']+\.mp4/g) || [];
  const imgs = html.match(/https:\/\/i\.pinimg\.com\/originals\/[^"']+\.(?:jpg|jpeg|png|webp)/g) || [];
  const ogImg = html.match(/property="og:image"\s+content="([^"]+)"/);
  const thumb = ogImg ? ogImg[1] : (imgs[0] || '');

  const formats = [];
  if (vids.length > 0) {
    formats.push({ directUrl: vids[0], note: '🎬 Video (HD MP4)', ext: 'mp4', height: 720, hasAudio: true });
  } else if (imgs.length > 0) {
    const ext = imgs[0].split('?')[0].split('.').pop() || 'jpg';
    formats.push({ directUrl: imgs[0], note: `📸 Original Image (${ext.toUpperCase()})`, ext });
  } else if (thumb) {
    formats.push({ directUrl: thumb, note: '📸 Image Download (JPG)', ext: 'jpg' });
  }

  if (formats.length === 0) throw new Error('No Pinterest media found in page');
  return { title, thumbnail: thumb, type: 'pinterest', formats };
}

function capVideoFormats(formats) {
  if (!Array.isArray(formats)) return [];
  const videos = formats.filter(f => !f.isAudio && f.ext !== 'mp3' && f.ext !== 'm4a');
  const audios = formats.filter(f => f.isAudio || f.ext === 'mp3' || f.ext === 'm4a');
  return [...videos.slice(0, 5), ...audios.slice(0, 1)];
}

async function resolveTikwm(link) {
  const resp = await fetch(`https://www.tikwm.com/api/?url=${encodeURIComponent(link)}`, {
    signal: AbortSignal.timeout(6000)
  });
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

async function resolveYoutubeMetadata(link) {
  let title = null, thumbnail = null;
  const vidId = link.match(/(?:v=|youtu\.be\/|shorts\/)([a-zA-Z0-9_-]{11})/)?.[1];
  if (vidId) thumbnail = `https://i.ytimg.com/vi/${vidId}/maxresdefault.jpg`;
  try {
    const oeResp = await fetch(`https://www.youtube.com/oembed?url=${encodeURIComponent(link)}&format=json`, {
      signal: AbortSignal.timeout(3000)
    });
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

  function buildVariantFormat(vUrl, defaultRank) {
    const dimMatch = vUrl.match(/\/(\d+)x(\d+)\//);
    const w = dimMatch ? parseInt(dimMatch[1], 10) : 0;
    const h = dimMatch ? parseInt(dimMatch[2], 10) : 0;
    const tier = (w > 0 && h > 0) ? Math.min(w, h) : (defaultRank === 0 ? 1080 : defaultRank === 1 ? 720 : defaultRank === 2 ? 480 : 360);
    const dimStr = (w > 0 && h > 0) ? `${w}x${h} ` : '';
    let note = `🎬 ${tier}p (${dimStr}MP4)`;
    if (tier >= 1080) note = `🎬 1080p FHD (${dimStr}MP4)`;
    else if (tier >= 720) note = `🎬 720p HD (${dimStr}MP4)`;
    else if (tier >= 480) note = `🎬 480p SD (${dimStr}MP4)`;
    return {
      directUrl: vUrl,
      token: vUrl,
      note,
      ext: 'mp4',
      height: tier,
      hasAudio: true
    };
  }

  if (tweetId) {
    // 1. Official Twitter Syndication JSON API
    try {
      const synResp = await fetch(`https://cdn.syndication.twimg.com/tweet-result?id=${tweetId}&token=0`, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          'Accept': 'application/json'
        },
        signal: AbortSignal.timeout(6000)
      });
      if (synResp.ok) {
        const synData = await synResp.json();
        const rawText = (synData.text || '').split('https://t.co/')[0].trim();
        const title = (rawText || 'Twitter / X Video').slice(0, 90);
        let thumbnail = '';
        const mp4List = [];
        for (const m of (synData.mediaDetails || [])) {
          if (!thumbnail && m.media_url_https) thumbnail = m.media_url_https;
          for (const v of (m.video_info?.variants || [])) {
            if (v.url && ((v.content_type || '').includes('mp4') || v.url.includes('.mp4'))) {
              mp4List.push({ bitrate: v.bitrate || 0, url: v.url });
            }
          }
        }
        if (mp4List.length > 0) {
          mp4List.sort((a, b) => b.bitrate - a.bitrate);
          const seen = new Set();
          const formats = [];
          for (const item of mp4List) {
            if (seen.has(item.url)) continue;
            seen.add(item.url);
            formats.push(buildVariantFormat(item.url, formats.length));
          }
          formats.push({
            directUrl: formats[0].directUrl,
            token: formats[0].directUrl,
            note: '🎵 High Quality Audio (MP3)',
            ext: 'mp3',
            isAudio: true
          });
          return { title, thumbnail, type: 'twitter', formats, maxResolution: `${formats[0].height || 720}p` };
        }
      }
    } catch (e) {}

    // 2. FxTwitter JSON API
    try {
      const fxResp = await fetch(`https://api.fxtwitter.com/i/status/${tweetId}`, {
        headers: { 'User-Agent': 'HKDownloaderPro/2.0', 'Accept': 'application/json' },
        signal: AbortSignal.timeout(6000)
      });
      if (fxResp.ok) {
        const fxData = await fxResp.json();
        const tweet = fxData.tweet;
        if (tweet) {
          const title = (tweet.text || 'Twitter / X Video').slice(0, 90);
          let thumbnail = '';
          const mp4List = [];
          for (const vid of (tweet.media?.videos || [])) {
            if (!thumbnail && vid.thumbnail_url) thumbnail = vid.thumbnail_url;
            for (const v of (vid.variants || [])) {
              if (v.url && ((v.content_type || '').includes('mp4') || v.url.includes('.mp4'))) {
                mp4List.push({ bitrate: v.bitrate || 0, url: v.url });
              }
            }
            if (mp4List.length === 0 && vid.url) {
              mp4List.push({ bitrate: 2000000, url: vid.url });
            }
          }
          if (mp4List.length > 0) {
            mp4List.sort((a, b) => b.bitrate - a.bitrate);
            const seen = new Set();
            const formats = [];
            for (const item of mp4List) {
              if (seen.has(item.url)) continue;
              seen.add(item.url);
              formats.push(buildVariantFormat(item.url, formats.length));
            }
            formats.push({
              directUrl: formats[0].directUrl,
              token: formats[0].directUrl,
              note: '🎵 High Quality Audio (MP3)',
              ext: 'mp3',
              isAudio: true
            });
            return { title, thumbnail, type: 'twitter', formats, maxResolution: `${formats[0].height || 720}p` };
          }
        }
      }
    } catch (e) {}

    // 3. VxTwitter JSON API
    try {
      const vxResp = await fetch(`https://api.vxtwitter.com/i/status/${tweetId}`, {
        headers: { 'User-Agent': 'HKDownloaderPro/2.0', 'Accept': 'application/json' },
        signal: AbortSignal.timeout(6000)
      });
      if (vxResp.ok) {
        const vxData = await vxResp.json();
        const title = (vxData.text || 'Twitter / X Video').slice(0, 90);
        let thumbnail = '';
        const formats = [];
        for (const m of (vxData.media_extended || [])) {
          if (!thumbnail && m.thumbnail_url) thumbnail = m.thumbnail_url;
          if (m.url && (m.type === 'video' || m.type === 'gif' || m.url.includes('.mp4'))) {
            formats.push(buildVariantFormat(m.url, formats.length));
          }
        }
        if (formats.length > 0) {
          formats.push({
            directUrl: formats[0].directUrl,
            token: formats[0].directUrl,
            note: '🎵 High Quality Audio (MP3)',
            ext: 'mp3',
            isAudio: true
          });
          return { title, thumbnail, type: 'twitter', formats, maxResolution: `${formats[0].height || 720}p` };
        }
      }
    } catch (e) {}
  }

  // 4. TwitSave HTML fallback
  const enc = encodeURIComponent(link);
  const resp = await fetch(`https://twitsave.com/info?url=${enc}`, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    },
    signal: AbortSignal.timeout(8000)
  });
  if (!resp.ok) throw new Error(`TwitSave HTTP ${resp.status}`);
  const html = await resp.text();

  let title = 'Twitter / X Video';
  const titleMatch = html.match(/<div[^>]*class="[^"]*leading-tight[^"]*"[^>]*>.*?<p[^>]*class="[^"]*m-2[^"]*"[^>]*>([^<]+)<\/p>/s) ||
                     html.match(/<div[^>]*class="[^"]*leading-tight[^"]*"[^>]*>.*?<p[^>]*>([^<]+)<\/p>/s) ||
                     html.match(/property="og:title"\s+content="([^"]+)"/);
  if (titleMatch) {
    title = titleMatch[1].replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#039;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').trim();
  }

  let thumbnail = '';
  const thumbMatch = html.match(/(https:\/\/pbs\.twimg\.com\/[^\s"'<>]+\.(?:jpg|jpeg|png|webp))/);
  if (thumbMatch) {
    thumbnail = thumbMatch[1];
  }

  const linkMatches = [...html.matchAll(/href="https:\/\/twitsave\.com\/download\?file=([^"&]+)"[^>]*>(.*?)<\/a>/gs)];
  const formats = [];
  const seenUrls = new Set();

  for (const m of linkMatches) {
    try {
      const b64 = decodeURIComponent(m[1]);
      const directUrl = atob(b64);
      if (!directUrl.startsWith('http') || seenUrls.has(directUrl)) continue;
      seenUrls.add(directUrl);

      const resMatch = m[2].match(/Resolution:\s*(\d+)x(\d+)/);
      const w = resMatch ? parseInt(resMatch[1], 10) : 1280;
      const h = resMatch ? parseInt(resMatch[2], 10) : 720;
      const tier = Math.min(w, h);

      let note = `🎬 ${tier}p (${w}x${h} MP4)`;
      if (tier >= 1080) note = `🎬 1080p FHD (${w}x${h} MP4)`;
      else if (tier >= 720) note = `🎬 720p HD (${w}x${h} MP4)`;
      else if (tier >= 480) note = `🎬 480p SD (${w}x${h} MP4)`;

      formats.push({
        directUrl,
        token: directUrl,
        note,
        ext: 'mp4',
        height: tier,
        hasAudio: true
      });
    } catch (e) {}
  }

  if (formats.length > 0) {
    const bestUrl = formats[0].directUrl;
    formats.push({
      directUrl: bestUrl,
      token: bestUrl,
      note: '🎵 High Quality Audio (MP3)',
      ext: 'mp3',
      isAudio: true
    });
    return { title, thumbnail, type: 'twitter', formats, maxResolution: `${formats[0].height || 720}p` };
  }
  throw new Error('No playable Twitter video streams found');
}

async function fetchYoutubeWatchSession(vidId) {
  const cookiePairs = ['PREF=hl=en&tz=UTC', 'SOCS=CAI'];
  let visitorData = '';
  let sts = 20746;
  try {
    const watchRes = await fetch(`https://www.youtube.com/watch?v=${vidId}&bpctr=9999999999&has_verified=1`, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36',
        'Accept-Language': 'en-US,en;q=0.9',
        'Cookie': 'PREF=hl=en&tz=UTC; SOCS=CAI'
      },
      signal: AbortSignal.timeout(4500)
    });
    const setCookies = typeof watchRes.headers.getSetCookie === 'function' ? watchRes.headers.getSetCookie() : [];
    for (const sc of setCookies) {
      const pair = sc.split(';')[0]?.trim();
      if (pair) cookiePairs.push(pair);
    }
    const html = await watchRes.text();
    const vm = html.match(/"VISITOR_DATA"\s*:\s*"([^"]+)"/);
    if (vm) visitorData = vm[1];
    const sm = html.match(/"STS"\s*:\s*(\d+)/);
    if (sm) sts = parseInt(sm[1], 10);
  } catch (e) {}
  return { visitorData, cookieHeader: [...new Set(cookiePairs)].join('; '), sts };
}

async function resolveYoutubeDirect(link) {
  const vidId = link.match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/)?.[1];
  if (!vidId) throw new Error('Invalid YouTube URL');

  const session = await fetchYoutubeWatchSession(vidId);
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
        'X-YouTube-Client-Version': c.version,
        'Origin': 'https://www.youtube.com',
        'User-Agent': c.ua,
        'Cookie': session.cookieHeader
      };
      if (session.visitorData) {
        headers['X-Goog-Visitor-Id'] = session.visitorData;
      }
      const payload = {
        videoId: vidId,
        context: { client: c.context },
        playbackContext: {
          contentPlaybackContext: {
            html5Preference: 'HTML5_PREF_WANTS',
            signatureTimestamp: session.sts
          }
        },
        contentCheckOk: true,
        racyCheckOk: true
      };

      const resp = await fetch('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(6000)
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

      function parseNormalizedTier(f) {
        const labelMatch = (f.qualityLabel || '').match(/(\d{3,4})p/);
        if (labelMatch) return parseInt(labelMatch[1], 10);
        const w = f.width || 0;
        const h = f.height || 0;
        if (w > 0 && h > 0) return Math.min(w, h);
        return h || 0;
      }

      // Collect all video streams mapped by normalized tier
      const videoMap = {};
      // 1. Progressive streams (muxed audio)
      for (const f of rawFormats) {
        if (f.url) {
          const tier = parseNormalizedTier(f);
          if (tier <= 0) continue;
          const clen = f.contentLength ? parseInt(f.contentLength, 10) : undefined;
          videoMap[tier] = {
            url: f.url,
            height: tier,
            ext: 'mp4',
            hasMuxedAudio: true,
            isAvcMp4: true,
            qualityLabel: f.qualityLabel || `${tier}p`,
            sizeBytes: clen
          };
        }
      }

      // 2. Adaptive streams (video only)
      for (const af of adaptive) {
        const mime = af.mimeType || '';
        if (af.url && mime.includes('video')) {
          const tier = parseNormalizedTier(af);
          if (tier <= 0) continue;
          const isMp4 = mime.includes('mp4');
          const isAvc = mime.includes('avc1');
          const existing = videoMap[tier];
          const shouldReplace = !existing ||
            (!existing.hasMuxedAudio && isMp4 && (!existing.isAvcMp4 && isAvc)) ||
            (!existing.hasMuxedAudio && existing.ext !== 'mp4' && isMp4);
          if (shouldReplace) {
            const clen = af.contentLength ? parseInt(af.contentLength, 10) : undefined;
            videoMap[tier] = {
              url: af.url,
              height: tier,
              ext: isMp4 ? 'mp4' : 'webm',
              hasMuxedAudio: false,
              isAvcMp4: isMp4 && isAvc,
              qualityLabel: af.qualityLabel || `${tier}p`,
              sizeBytes: clen
            };
          }
        }
      }

      const availableTiers = Object.keys(videoMap).map(h => parseInt(h, 10)).sort((a, b) => b - a);
      if (availableTiers.length === 0) continue;

      const maxTier = availableTiers[0];

      // 1. Original Quality (if > 1080p, e.g. 4K 2160p or 2K 1440p)
      if (maxTier > 1080) {
        const vf = videoMap[maxTier];
        const audioU = vf.hasMuxedAudio ? null : bestAudioUrl;
        const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
        const qLabel = maxTier >= 2160 ? '4K UHD' : (maxTier >= 1440 ? '2K QHD' : `${maxTier}p`);
        formats.push({
          directUrl: vf.url,
          audioUrl: audioU,
          token: tok,
          videoId: vidId,
          url: link,
          note: `🎬 ${qLabel} (${maxTier}p Original Quality)`,
          ext: vf.ext,
          height: maxTier,
          hasAudio: true,
          sizeBytes: vf.sizeBytes
        });
      }

      // 2. 1080p FHD
      if (videoMap[1080]) {
        const vf = videoMap[1080];
        const audioU = vf.hasMuxedAudio ? null : bestAudioUrl;
        const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
        const noteStr = (maxTier === 1080) ? '🎬 1080p FHD (Original Quality MP4)' : '🎬 1080p FHD (MP4)';
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
        const audioU = vf.hasMuxedAudio ? null : bestAudioUrl;
        const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
        const noteStr = (maxTier === 720) ? '🎬 720p HD (Original Quality MP4)' : '🎬 720p HD (MP4)';
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

      // 4. 480p SD
      if (videoMap[480]) {
        const vf = videoMap[480];
        const audioU = vf.hasMuxedAudio ? null : bestAudioUrl;
        const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
        const noteStr = (maxTier === 480) ? '🎬 480p SD (Original Quality MP4)' : '🎬 480p SD (MP4)';
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
        const audioU = vf.hasMuxedAudio ? null : bestAudioUrl;
        const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
        const noteStr = (maxTier === 360) ? '🎬 360p (Original Quality MP4)' : '🎬 360p Standard (MP4)';
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
      }

      // 6. 240p Data Saver
      if (videoMap[240] && formats.length < 5) {
        const vf = videoMap[240];
        const audioU = vf.hasMuxedAudio ? null : bestAudioUrl;
        const tok = audioU ? `${vf.url}|${audioU}` : vf.url;
        formats.push({
          directUrl: vf.url,
          audioUrl: audioU,
          token: tok,
          videoId: vidId,
          url: link,
          note: '🎬 240p Data Saver (MP4)',
          ext: vf.ext,
          height: 240,
          hasAudio: true,
          sizeBytes: vf.sizeBytes
        });
      }

      if (formats.length === 0 && availableTiers.length > 0) {
        for (const h of availableTiers.slice(0, 4)) {
          const vf = videoMap[h];
          const audioU = vf.hasMuxedAudio ? null : bestAudioUrl;
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

  if (formats.length === 0) {
    throw new Error(lastErr || 'No playable streams found for this YouTube URL');
  }

  return { title, thumbnail, type: 'youtube', videoId: vidId, url: link, formats };
}

const COBALT_STATIC_FALLBACKS = [];

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
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(4500)
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
  if (!cobaltInstanceUrl) {
    throw new Error(`Platform ${displayType} stream resolution requires backend engine.`);
  }

  const uniqueInstances = [cobaltInstanceUrl];
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

export async function onRequestOptions() {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Max-Age': '86400'
    }
  });
}

export async function onRequestPost(context) {
  const { request, env } = context;
  const cobaltInstanceUrl = env.COBALT_INSTANCE_URL || '';
  const DEFAULT_YTDLP_URL = 'https://hk-downloader-pro.vercel.app';
  const ytdlpApiUrl = env.YTDLP_API_URL || DEFAULT_YTDLP_URL;

  let inputUrl;
  let preset;
  try {
    const body = await request.json();
    inputUrl = body.inputUrl;
    preset = body.preset || { videoQuality: '1080', downloadMode: 'auto' };
  } catch (e) {
    return new Response(JSON.stringify({ error: 'Invalid request body' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  if (!inputUrl) {
    return new Response(JSON.stringify({ error: 'Missing inputUrl' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  inputUrl = cleanUrl(inputUrl);
  const type = identifyLinkType(inputUrl);
  let ytDirectFallback = null;

  // 1. Direct Edge & Universal Resolvers
  if (type === 'youtube') {
    // 1. Fast direct Edge Innertube resolution (sub-second response with 4K/1080p/720p/480p/360p/audio)
    try {
      const ytData = await resolveYoutubeDirect(inputUrl);
      if (ytData && ytData.formats && ytData.formats.length > 0) {
        return new Response(JSON.stringify(ytData), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
          }
        });
      }
    } catch (e) {}

    // 2. Fallback to universal python backend if edge resolver was rate-limited
    if (ytdlpApiUrl) {
      try {
        const cleanApiUrl = ytdlpApiUrl.replace(/\/+$/, '');
        const resp = await fetch(`${cleanApiUrl}/api/analyze`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ inputUrl, preset }),
          signal: AbortSignal.timeout(12000)
        });
        if (resp.ok) {
          const data = await resp.json();
          if (data.formats && Array.isArray(data.formats) && data.formats.length >= 2) {
            data.formats = capVideoFormats(data.formats);
            return new Response(JSON.stringify(data), {
              status: 200,
              headers: {
                'Content-Type': 'application/json',
                'Access-Control-Allow-Origin': '*',
                'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
                'Access-Control-Allow-Headers': 'Content-Type'
              }
            });
          }
        }
      } catch (e) {}
    }
  } else if (type === 'tiktok') {
    try {
      const tkData = await resolveTikwm(inputUrl);
      if (tkData && tkData.formats && tkData.formats.length > 0) {
        return new Response(JSON.stringify(tkData), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
          }
        });
      }
    } catch (e) {}
  } else if (type === 'facebook') {
    try {
      const fbData = await resolveFacebookDirect(inputUrl);
      if (fbData && fbData.formats && fbData.formats.length > 0) {
        return new Response(JSON.stringify(fbData), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
          }
        });
      }
    } catch (e) {}
  } else if (type === 'instagram') {
    // Instagram DASH streams often separate video & audio; try Python backend first for full stream pairing
    if (ytdlpApiUrl) {
      try {
        const cleanApiUrl = ytdlpApiUrl.replace(/\/+$/, '');
        const resp = await fetch(`${cleanApiUrl}/api/analyze`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ inputUrl, preset }),
          signal: AbortSignal.timeout(6000)
        });
        if (resp.ok) {
          const data = await resp.json();
          if (data.formats && Array.isArray(data.formats)) {
            // Guarantee 100% that Instagram downloads have audio (filter out silent DASH video tracks)
            data.formats = data.formats.filter(f => f.hasAudio || f.isAudio || f.ext === 'mp3' || f.ext === 'm4a');
            data.formats = capVideoFormats(data.formats);
            data.formats.forEach(f => {
              if (!f.token && f.directUrl) {
                f.token = f.directUrl;
              } else if (f.token && !f.token.startsWith('http')) {
                f.token = f.directUrl || f.token;
              }
            });
          }
          return new Response(JSON.stringify(data), {
            status: 200,
            headers: {
              'Content-Type': 'application/json',
              'Access-Control-Allow-Origin': '*',
              'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
              'Access-Control-Allow-Headers': 'Content-Type'
            }
          });
        }
      } catch (e) {}
    }
    try {
      const igData = await resolveInstagramDirect(inputUrl);
      if (igData && igData.formats && igData.formats.length > 0) {
        igData.formats = igData.formats.filter(f => f.hasAudio || f.isAudio || f.ext === 'mp3' || f.ext === 'm4a');
        return new Response(JSON.stringify(igData), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
          }
        });
      }
    } catch (e) {}
  } else if (type === 'pinterest') {
    try {
      const pinData = await resolvePinterestDirect(inputUrl);
      if (pinData && pinData.formats && pinData.formats.length > 0) {
        return new Response(JSON.stringify(pinData), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
          }
        });
      }
    } catch (e) {}
  } else if (type === 'twitter') {
    try {
      const twData = await resolveTwitterDirect(inputUrl);
      if (twData && twData.formats && twData.formats.length > 0) {
        return new Response(JSON.stringify(twData), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
          }
        });
      }
    } catch (e) {}
  }

  // 2. Delegate to Python backend for complex/fallback media (Reddit, Twitter, etc.)
  if (ytdlpApiUrl) {
    try {
      const cleanApiUrl = ytdlpApiUrl.replace(/\/+$/, '');
      const resp = await fetch(`${cleanApiUrl}/api/analyze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ inputUrl, preset }),
        signal: AbortSignal.timeout(10000)
      });
      if (resp.ok) {
        const data = await resp.json();
        if (data.formats && Array.isArray(data.formats)) {
          data.formats = capVideoFormats(data.formats);
          data.formats.forEach(f => {
            if (!f.token && f.directUrl) {
              f.token = f.directUrl;
            } else if (f.token && !f.token.startsWith('http')) {
              f.token = f.directUrl || f.token;
            }
          });
        }
        return new Response(JSON.stringify(data), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
          }
        });
      }
    } catch (e) {
      // fallback on error
    }
  }

  const chain = type === 'tiktok'
    ? [
        { name: 'tikwm', run: () => resolveTikwm(inputUrl) },
        { name: 'cobalt', run: () => resolveCobalt(inputUrl, 'TikTok', cobaltInstanceUrl, preset) },
      ]
    : type === 'instagram'
    ? [
        { name: 'instagram-direct', run: () => resolveInstagramDirect(inputUrl) },
        { name: 'cobalt', run: () => resolveCobalt(inputUrl, 'Instagram', cobaltInstanceUrl, preset) },
      ]
    : type === 'facebook'
    ? [
        { name: 'facebook-direct', run: () => resolveFacebookDirect(inputUrl) },
        { name: 'cobalt', run: () => resolveCobalt(inputUrl, 'Facebook', cobaltInstanceUrl, preset) },
      ]
    : type === 'pinterest'
    ? [
        { name: 'pinterest-direct', run: () => resolvePinterestDirect(inputUrl) },
        { name: 'cobalt', run: () => resolveCobalt(inputUrl, 'Pinterest', cobaltInstanceUrl, preset) },
      ]
    : type === 'youtube'
    ? [
        { name: 'youtube-direct', run: () => resolveYoutubeDirect(inputUrl) },
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
      if (result && Array.isArray(result.formats)) {
        result.formats = capVideoFormats(result.formats);
      }
      return new Response(JSON.stringify(result), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    } catch (e) {
      failures.push(`${resolver.name}: ${e.message}`);
    }
  }

  if (ytDirectFallback && ytDirectFallback.formats && ytDirectFallback.formats.length > 0) {
    return new Response(JSON.stringify(ytDirectFallback), {
      status: 200,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type'
      }
    });
  }

  return new Response(JSON.stringify({
    error: `Couldn't resolve this link (tried: ${failures.join('; ')})`
  }), {
    status: 502,
    headers: { 'Content-Type': 'application/json' }
  });
}
