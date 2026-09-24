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

async function resolveYoutubeFreshStream(videoId) {
  try {
    const headers = {
      'Content-Type': 'application/json',
      'X-YouTube-Client-Name': '3',
      'X-YouTube-Client-Version': '21.26.364',
      'Origin': 'https://www.youtube.com',
      'User-Agent': 'com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip'
    };
    const payload = {
      videoId: videoId,
      context: {
        client: {
          clientName: 'ANDROID',
          clientVersion: '21.26.364',
          androidSdkVersion: 30
        }
      },
      playbackContext: {
        contentPlaybackContext: {
          html5Preference: 'HTML5_PREF_WANTS',
          signatureTimestamp: 20717
        }
      },
      contentCheckOk: true,
      racyCheckOk: true
    };
    const resp = await fetch('https://www.youtube.com/youtubei/v1/player', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(6000)
    });
    if (!resp.ok) return null;
    const data = await resp.json();
    const rawFormats = data?.streamingData?.formats || [];
    for (const f of rawFormats) {
      if (f.url) return f.url;
    }
  } catch (e) {}
  return null;
}

export async function onRequestGet(context) {
  const { request } = context;
  const urlObj = new URL(request.url);
  let targetUrl = urlObj.searchParams.get('url') || urlObj.searchParams.get('token');
  const filename = urlObj.searchParams.get('filename') || 'video.mp4';
  const type = urlObj.searchParams.get('type') || '';
  const ytId = urlObj.searchParams.get('id') || urlObj.searchParams.get('ytId') || urlObj.searchParams.get('videoId');

  // If this is a YouTube request (or targetUrl points to googlevideo/youtube), resolve freshly on this Cloudflare node
  // to ensure Google Video IP-binding matches this worker instance's outbound IP!
  let resolvedYtId = ytId;
  if (!resolvedYtId && targetUrl) {
    const m = targetUrl.match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/);
    if (m) resolvedYtId = m[1];
  }

  if (type === 'youtube' || resolvedYtId || (targetUrl && (targetUrl.includes('googlevideo') || targetUrl.includes('youtube')))) {
    if (resolvedYtId) {
      const freshUrl = await resolveYoutubeFreshStream(resolvedYtId);
      if (freshUrl) {
        targetUrl = freshUrl;
      }
    }
  }

  if (!targetUrl || !targetUrl.startsWith('http')) {
    return new Response(JSON.stringify({ error: 'Missing or invalid target url' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }

  try {
    let referer = '';
    if (targetUrl.includes('googlevideo') || targetUrl.includes('youtube')) {
      referer = 'https://www.youtube.com/';
    } else if (targetUrl.includes('instagram')) {
      referer = 'https://www.instagram.com/';
    } else if (targetUrl.includes('facebook') || targetUrl.includes('fbcdn')) {
      referer = 'https://www.facebook.com/';
    } else if (targetUrl.includes('rapidsave')) {
      referer = 'https://rapidsave.com/';
    } else if (targetUrl.includes('redd.it') || targetUrl.includes('reddit')) {
      referer = 'https://www.reddit.com/';
    } else if (targetUrl.includes('pinimg') || targetUrl.includes('pinterest')) {
      referer = 'https://www.pinterest.com/';
    }

    const upstreamHeaders = {
      'User-Agent': targetUrl.includes('googlevideo')
        ? 'com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip'
        : 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
    };
    if (referer) upstreamHeaders['Referer'] = referer;

    const rangeHeader = request.headers.get('Range');
    if (rangeHeader) {
      upstreamHeaders['Range'] = rangeHeader;
    }

    let upstream = await fetch(targetUrl, {
      headers: upstreamHeaders
    });

    // If googlevideo returned 403 due to IP mismatch, attempt on-demand re-resolution
    if (upstream.status === 403 && resolvedYtId) {
      const freshUrl = await resolveYoutubeFreshStream(resolvedYtId);
      if (freshUrl && freshUrl !== targetUrl) {
        targetUrl = freshUrl;
        upstream = await fetch(targetUrl, { headers: upstreamHeaders });
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
