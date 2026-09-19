// functions/api/download.js
// Cloudflare Pages Function that proxies video/audio streams with proper CORS headers
// so browser fetch() never fails with "Failed to fetch".

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
    const upstream = await fetch(targetUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Referer': targetUrl.includes('googlevideo') || targetUrl.includes('youtube')
          ? 'https://www.youtube.com/'
          : targetUrl.includes('instagram')
          ? 'https://www.instagram.com/'
          : targetUrl.includes('facebook') || targetUrl.includes('fbcdn')
          ? 'https://www.facebook.com/'
          : ''
      }
    });

    if (!upstream.ok && upstream.status !== 206) {
      // If proxy fetch fails, redirect the browser to the direct URL as fallback
      return Response.redirect(targetUrl, 302);
    }

    const headers = new Headers(upstream.headers);
    headers.set('Access-Control-Allow-Origin', '*');
    headers.set('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    headers.set('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Content-Type');
    headers.set('Content-Disposition', `attachment; filename="${encodeURIComponent(filename)}"`);

    return new Response(upstream.body, {
      status: upstream.status,
      headers
    });
  } catch (err) {
    // On any proxy error, redirect to targetUrl directly
    return Response.redirect(targetUrl, 302);
  }
}
