from http.server import BaseHTTPRequestHandler
import json
import re
import urllib.request
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse
import yt_dlp

def clean_url(raw_url):
    if not raw_url:
        return ''
    u = raw_url.strip().strip('"\'<>(){}[]`')
    try:
        parsed = urlparse(u)
        tracking = {'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'utm', 'si', 'feature', 'fbclid', 'igsh', 'ref', 'usp', '_ga', '_gl', 'mibextid'}
        qs = parse_qs(parsed.query, keep_blank_values=False)
        cleaned_qs = {k: v for k, v in qs.items() if k.lower() not in tracking and not k.lower().startswith('utm')}
        new_query = urlencode(cleaned_qs, doseq=True)
        return urlunparse((parsed.scheme.lower(), parsed.netloc.lower(), parsed.path, '', new_query, ''))
    except Exception:
        return u

def extract_facebook_direct(url):
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
    }
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')

    hd_match = re.search(r'"browser_native_hd_url"\s*:\s*"([^"]+)"', html) or \
               re.search(r'"playable_url_quality_hd"\s*:\s*"([^"]+)"', html) or \
               re.search(r'hd_src\s*:\s*"([^"]+)"', html)

    sd_match = re.search(r'"browser_native_sd_url"\s*:\s*"([^"]+)"', html) or \
               re.search(r'"playable_url"\s*:\s*"([^"]+)"', html) or \
               re.search(r'sd_src\s*:\s*"([^"]+)"', html)

    title = 'Facebook Video'
    title_match = re.search(r'<title id="pageTitle">([^<]+)</title>', html) or re.search(r'<title>([^<]+)</title>', html)
    if title_match:
        title = title_match.group(1).split('|')[0].strip()

    thumb = ''
    thumb_match = re.search(r'property="og:image"\s+content="([^"]+)"', html) or re.search(r'content="([^"]+)"\s+property="og:image"', html)
    if thumb_match:
        thumb = thumb_match.group(1).replace('&amp;', '&')

    formats = []
    if hd_match:
        formats.append({
            'directUrl': hd_match.group(1).replace('\\/', '/').replace('&amp;', '&'),
            'token': hd_match.group(1).replace('\\/', '/').replace('&amp;', '&'),
            'note': '🎬 1080p FHD (HD MP4)',
            'ext': 'mp4'
        })
    if sd_match:
        formats.append({
            'directUrl': sd_match.group(1).replace('\\/', '/').replace('&amp;', '&'),
            'token': sd_match.group(1).replace('\\/', '/').replace('&amp;', '&'),
            'note': '🎬 480p SD (Standard MP4)',
            'ext': 'mp4'
        })
    return title, thumb, formats

class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps({'status': 'ok', 'service': 'HK Downloader Pro Engine v2'}).encode('utf-8'))

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length)
        try:
            body = json.loads(post_data.decode('utf-8'))
        except Exception:
            body = {}

        raw_url = body.get('inputUrl') or body.get('url')
        if not raw_url:
            self.send_response(400)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Missing inputUrl'}).encode('utf-8'))
            return

        url = clean_url(raw_url)

        # 1. First try yt-dlp
        try:
            ydl_opts = {
                'quiet': True,
                'no_warnings': True,
                'skip_download': True
            }

            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)

            title = info.get('title') or 'Media Video'
            thumbnail = info.get('thumbnail') or ''
            raw_formats = info.get('formats') or []

            formats = []
            seen_heights = set()

            def get_tier_label(h):
                if h >= 2160: return f"🎬 4K UHD ({h}p MP4)"
                if h >= 1440: return f"🎬 2K QHD ({h}p MP4)"
                if h >= 1080: return f"🎬 1080p FHD (MP4)"
                if h >= 720:  return f"🎬 720p HD (MP4)"
                if h >= 480:  return f"🎬 480p SD (MP4)"
                if h >= 360:  return f"🎬 360p (MP4)"
                return f"🎬 {h}p (MP4)"

            for f in raw_formats:
                url_stream = f.get('url')
                ext = f.get('ext') or 'mp4'
                height = f.get('height')
                vcodec = f.get('vcodec') or 'none'
                if not url_stream or vcodec == 'none':
                    continue

                size = f.get('filesize') or f.get('filesize_approx')

                if height and height not in seen_heights:
                    seen_heights.add(height)
                    formats.append({
                        'directUrl': url_stream,
                        'token': url_stream,
                        'note': get_tier_label(height),
                        'ext': ext,
                        'sizeBytes': size,
                        'height': height
                    })

            formats.sort(key=lambda x: x.get('height', 0), reverse=True)

            # Add Audio option
            audios = [f for f in raw_formats if f.get('url') and f.get('vcodec') == 'none' and f.get('acodec') != 'none']
            if audios:
                best_audio = max(audios, key=lambda a: a.get('abr') or 0)
                formats.append({
                    'directUrl': best_audio['url'],
                    'token': best_audio['url'],
                    'note': f"🎵 High Quality Audio ({best_audio.get('ext') or 'm4a'})",
                    'ext': best_audio.get('ext') or 'm4a',
                    'sizeBytes': best_audio.get('filesize') or best_audio.get('filesize_approx')
                })

            if not formats and info.get('url'):
                formats.append({
                    'directUrl': info['url'],
                    'token': info['url'],
                    'note': f"Original Quality ({info.get('ext') or 'mp4'})",
                    'ext': info.get('ext') or 'mp4'
                })

            max_h = max(seen_heights) if seen_heights else 0
            max_badge = '4K' if max_h >= 2160 else ('1080p' if max_h >= 1080 else ('720p' if max_h >= 720 else 'SD'))

            response_data = {
                'title': title,
                'thumbnail': thumbnail,
                'formats': formats,
                'maxResolution': max_badge,
                'type': info.get('extractor') or 'video'
            }

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(response_data).encode('utf-8'))
            return

        except Exception as ytdlp_err:
            # 2. If yt-dlp fails and it's Facebook, use APEX Dual-Engine Direct Extractor
            if 'facebook.com' in url or 'fb.watch' in url:
                try:
                    fb_title, fb_thumb, fb_formats = extract_facebook_direct(url)
                    if fb_formats:
                        response_data = {
                            'title': fb_title,
                            'thumbnail': fb_thumb,
                            'formats': fb_formats,
                            'maxResolution': '1080p',
                            'type': 'facebook'
                        }
                        self.send_response(200)
                        self.send_header('Content-Type', 'application/json')
                        self.send_header('Access-Control-Allow-Origin', '*')
                        self.end_headers()
                        self.wfile.write(json.dumps(response_data).encode('utf-8'))
                        return
                except Exception:
                    pass

            self.send_response(502)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': str(ytdlp_err)}).encode('utf-8'))
