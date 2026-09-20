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

export async function onRequestGet(context) {
  const { request } = context;
  const urlObj = new URL(request.url);
  const targetUrl = urlObj.searchParams.get('url') || urlObj.searchParams.get('token');
  const filename = urlObj.searchParams.get('filename') || 'video.mp4';

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
    } else if (targetUrl.includes('redd.it') || targetUrl.includes('reddit')) {
      referer = 'https://www.reddit.com/';
    } else if (targetUrl.includes('pinimg') || targetUrl.includes('pinterest')) {
      referer = 'https://www.pinterest.com/';
    }

    const upstreamHeaders = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
    };
    if (referer) upstreamHeaders['Referer'] = referer;

    const rangeHeader = request.headers.get('Range');
    if (rangeHeader) {
      upstreamHeaders['Range'] = rangeHeader;
    }

    const upstream = await fetch(targetUrl, {
      headers: upstreamHeaders
    });

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
