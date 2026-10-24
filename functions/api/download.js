// functions/api/download.js
// Cloudflare Pages Function that proxies video/audio streams with proper CORS headers
// so browser fetch() and streaming downloads never fail with CORS or network errors.

export async function onRequestOptions() {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Max-Age': '86400'
    }
  });
}

export async function onRequestHead(context) {
  return onRequestGet(context);
}

function isSafePublicUrl(urlStr) {
  try {
    const parsed = new URL(urlStr);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false;
    const host = parsed.hostname.toLowerCase();
    if (
      host === 'localhost' ||
      host === '127.0.0.1' ||
      host === '::1' ||
      host === '0.0.0.0' ||
      host === '169.254.169.254' ||
      host === 'metadata.google.internal' ||
      host.endsWith('.local') ||
      host.endsWith('.internal')
    ) {
      return false;
    }
    // Block private IPv4 ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16
    const ipv4Match = host.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
    if (ipv4Match) {
      const octets = ipv4Match.slice(1).map(Number);
      if (octets.some(o => o > 255)) return false;
      const [o1, o2] = octets;
      if (o1 === 10) return false;
      if (o1 === 127) return false;
      if (o1 === 169 && o2 === 254) return false;
      if (o1 === 172 && (o2 >= 16 && o2 <= 31)) return false;
      if (o1 === 192 && o2 === 168) return false;
      if (o1 === 0) return false;
    }
    return true;
  } catch (e) {
    return false;
  }
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

async function resolveYoutubeFreshStream(videoId, targetItag = null) {
  const session = await fetchYoutubeWatchSession(videoId);
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
        videoId: videoId,
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
      const rawFormats = data?.streamingData?.formats || [];
      const adaptive = data?.streamingData?.adaptiveFormats || [];

      if (targetItag) {
        const itagNum = parseInt(targetItag, 10);
        for (const af of adaptive) {
          if (af.itag === itagNum && af.url) return af.url;
        }
        for (const f of rawFormats) {
          if (f.itag === itagNum && f.url) return f.url;
        }
      }

      for (const f of rawFormats) {
        if (f.url) return f.url;
      }
      for (const af of adaptive) {
        if (af.url && (af.mimeType || '').includes('avc1')) return af.url;
      }
      for (const af of adaptive) {
        if (af.url) return af.url;
      }
    } catch (e) {}
  }
  return null;
}

export async function onRequestGet(context) {
  const { request } = context;
  const urlObj = new URL(request.url);
  let targetUrl = urlObj.searchParams.get('url') || urlObj.searchParams.get('token');
  const filename = urlObj.searchParams.get('filename') || 'video.mp4';
  const type = urlObj.searchParams.get('type') || '';
  const ytId = urlObj.searchParams.get('id') || urlObj.searchParams.get('ytId') || urlObj.searchParams.get('videoId');

  let resolvedYtId = ytId;
  if (!resolvedYtId && targetUrl) {
    const m = targetUrl.match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/);
    if (m) resolvedYtId = m[1];
  }
  const targetItag = targetUrl ? (targetUrl.match(/[?&]itag=(\d+)/)?.[1] || null) : null;

  // If no direct targetUrl provided, resolve freshly on this Cloudflare node
  if ((!targetUrl || !targetUrl.startsWith('http')) && resolvedYtId) {
    try {
      const freshUrl = await resolveYoutubeFreshStream(resolvedYtId, targetItag);
      if (freshUrl) {
        targetUrl = freshUrl;
      }
    } catch (e) {}
  }

  if (!targetUrl || !isSafePublicUrl(targetUrl)) {
    return new Response(JSON.stringify({ error: 'Target URL is missing, invalid, or points to an unauthorized network address' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  try {
    let referer = '';
    if (targetUrl.includes('googlevideo') || targetUrl.includes('youtube')) {
      if (!targetUrl.includes('c=IOS') && !targetUrl.includes('c=ANDROID')) {
        referer = 'https://www.youtube.com/';
      }
    } else if (targetUrl.includes('instagram')) {
      referer = 'https://www.instagram.com/';
    } else if (targetUrl.includes('facebook') || targetUrl.includes('fbcdn')) {
      referer = 'https://www.facebook.com/';
    } else if (targetUrl.includes('twimg') || targetUrl.includes('twitter') || targetUrl.includes('x.com')) {
      referer = 'https://x.com/';
    } else if (targetUrl.includes('rapidsave')) {
      referer = 'https://rapidsave.com/';
    } else if (targetUrl.includes('redd.it') || targetUrl.includes('reddit')) {
      referer = 'https://www.reddit.com/';
    } else if (targetUrl.includes('pinimg') || targetUrl.includes('pinterest')) {
      referer = 'https://www.pinterest.com/';
    }

    const isYt = targetUrl.includes('googlevideo') || targetUrl.includes('youtube');
    const uasToTry = isYt ? [
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15',
      'com.google.android.youtube/20.10.38 (Linux; U; Android 14; en_US) gzip',
      'com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)',
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36'
    ] : [
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36'
    ];

    const rangeHeader = request.headers.get('Range');
    let upstream = null;

    for (const ua of uasToTry) {
      const upstreamHeaders = { 'User-Agent': ua };
      if (referer) upstreamHeaders['Referer'] = referer;
      if (rangeHeader) upstreamHeaders['Range'] = rangeHeader;

      try {
        upstream = await fetch(targetUrl, { headers: upstreamHeaders });
        if (upstream.ok || upstream.status === 206) break;
      } catch (e) {}
    }

    // If googlevideo returned 403 due to IP mismatch, attempt on-demand re-resolution
    if ((!upstream || upstream.status === 403) && resolvedYtId) {
      const freshUrl = await resolveYoutubeFreshStream(resolvedYtId, targetItag);
      if (freshUrl && freshUrl !== targetUrl) {
        targetUrl = freshUrl;
        for (const ua of uasToTry) {
          const upstreamHeaders = { 'User-Agent': ua };
          if (referer) upstreamHeaders['Referer'] = referer;
          if (rangeHeader) upstreamHeaders['Range'] = rangeHeader;
          try {
            upstream = await fetch(targetUrl, { headers: upstreamHeaders });
            if (upstream.ok || upstream.status === 206) break;
          } catch (e) {}
        }
      }
    }

    if (!upstream.ok && upstream.status !== 206) {
      return new Response(JSON.stringify({ error: `Upstream returned status ${upstream.status}` }), {
        status: 502,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    const headers = new Headers();
    const contentType = upstream.headers.get('content-type') || 'application/octet-stream';
    const contentLength = upstream.headers.get('content-length');
    const contentRange = upstream.headers.get('content-range');
    const acceptRanges = upstream.headers.get('accept-ranges');

    headers.set('Content-Type', contentType);
    if (contentLength) headers.set('Content-Length', contentLength);
    if (contentRange) headers.set('Content-Range', contentRange);
    if (acceptRanges) headers.set('Accept-Ranges', acceptRanges);

    headers.set('Access-Control-Allow-Origin', '*');
    headers.set('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    headers.set('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Content-Type, Accept-Ranges');
    headers.set('Content-Disposition', `attachment; filename="${encodeURIComponent(filename)}"`);
    headers.set('Cache-Control', 'public, max-age=3600');

    return new Response(upstream.body, {
      status: upstream.status,
      headers
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: err.message || 'Stream proxy failed' }), {
      status: 502,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }
}
