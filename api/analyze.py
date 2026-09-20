from http.server import BaseHTTPRequestHandler
import json
import os
import re
import tempfile
import urllib.request
import urllib.parse
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse, unquote
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

def extract_direct_media(url):
    parsed = urlparse(url)
    path = parsed.path.lower()
    media_exts = {'.mp4', '.webm', '.mp3', '.m4a', '.wav', '.mov', '.mkv', '.flv', '.aac', '.ogg', '.opus', '.jpg', '.jpeg', '.png', '.webp', '.gif'}
    for ext in media_exts:
        if path.endswith(ext):
            clean_name = unquote(parsed.path.split('/')[-1])
            file_ext = ext.lstrip('.')
            is_audio = file_ext in {'mp3', 'm4a', 'wav', 'aac', 'ogg', 'opus'}
            is_img = file_ext in {'jpg', 'jpeg', 'png', 'webp', 'gif'}
            
            note = f"🎵 Direct {file_ext.upper()} Audio" if is_audio else (f"📸 Direct {file_ext.upper()} Image" if is_img else f"🎬 Direct {file_ext.upper()} Video")
            
            return {
                'title': clean_name.rsplit('.', 1)[0] or 'Media File',
                'thumbnail': url if is_img else '',
                'formats': [{
                    'directUrl': url,
                    'token': url,
                    'note': note,
                    'ext': file_ext,
                    'isAudio': is_audio
                }],
                'maxResolution': 'Original',
                'type': 'direct'
            }
    return None

def extract_tiktok(url):
    try:
        api_url = f"https://www.tikwm.com/api/?url={urllib.parse.quote(url)}"
        req = urllib.request.Request(api_url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = json.loads(resp.read().decode('utf-8'))
        
        if data.get('code') != 0 or not data.get('data'):
            return None
        
        v = data['data']
        formats = []
        
        # 1. HD 1080p without watermark
        if v.get('hdplay'):
            formats.append({
                'directUrl': v['hdplay'],
                'token': v['hdplay'],
                'note': '🎬 1080p FHD (No Watermark)',
                'ext': 'mp4',
                'height': 1080,
                'hasAudio': True
            })
        
        # 2. Standard video without watermark
        if v.get('play'):
            formats.append({
                'directUrl': v['play'],
                'token': v['play'],
                'note': '🎬 720p HD (No Watermark)',
                'ext': 'mp4',
                'height': 720,
                'hasAudio': True
            })
        
        # 3. Watermarked video
        if v.get('wmplay') and len(formats) < 3:
            formats.append({
                'directUrl': v['wmplay'],
                'token': v['wmplay'],
                'note': '🎬 480p SD (Watermarked)',
                'ext': 'mp4',
                'height': 480,
                'hasAudio': True
            })
        
        # Audio option
        if v.get('music'):
            formats.append({
                'directUrl': v['music'],
                'token': v['music'],
                'note': '🎵 Audio Track (MP3)',
                'ext': 'mp3',
                'isAudio': True
            })
            
        # Slide Photos (if photo album)
        if isinstance(v.get('images'), list) and v['images']:
            for i, img in enumerate(v['images'][:5]):
                formats.append({
                    'directUrl': img,
                    'token': img,
                    'note': f"📸 Slide Photo #{i+1} (JPG)",
                    'ext': 'jpg'
                })
        
        if formats:
            return {
                'title': v.get('title') or 'TikTok Video',
                'thumbnail': v.get('cover') or '',
                'formats': formats,
                'maxResolution': '1080p',
                'type': 'tiktok'
            }
    except Exception:
        pass
    return None

def extract_pinterest(url):
    try:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        }
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            
        title_m = re.search(r'<title>([^<]+)</title>', html)
        title = title_m.group(1).split('|')[0].strip() if title_m else 'Pinterest Media'
        
        og_img = re.search(r'property="og:image"\s+content="([^"]+)"', html)
        thumb = og_img.group(1) if og_img else ''
        
        vids = re.findall(r'https://v\.pinimg\.com/[^"\']+\.mp4', html)
        imgs = re.findall(r'https://i\.pinimg\.com/originals/[^"\']+\.(?:jpg|jpeg|png|webp)', html)
        
        formats = []
        if vids:
            formats.append({
                'directUrl': vids[0],
                'token': vids[0],
                'note': '🎬 Video (HD MP4)',
                'ext': 'mp4',
                'height': 720,
                'hasAudio': True
            })
        elif imgs:
            ext = imgs[0].split('?')[0].split('.')[-1]
            formats.append({
                'directUrl': imgs[0],
                'token': imgs[0],
                'note': f"📸 Original Image ({ext.upper()})",
                'ext': ext
            })
        elif thumb:
            formats.append({
                'directUrl': thumb,
                'token': thumb,
                'note': '📸 Image Download (JPG)',
                'ext': 'jpg'
            })
            
        if formats:
            return {
                'title': title,
                'thumbnail': thumb,
                'formats': formats,
                'maxResolution': 'HD',
                'type': 'pinterest'
            }
    except Exception:
        pass
    return None

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
        hd_url = hd_match.group(1).replace('\\/', '/').replace('&amp;', '&')
        formats.append({
            'directUrl': hd_url,
            'token': hd_url,
            'note': '🎬 1080p FHD (HD MP4)',
            'ext': 'mp4',
            'height': 1080,
            'hasAudio': True
        })
    if sd_match:
        sd_url = sd_match.group(1).replace('\\/', '/').replace('&amp;', '&')
        formats.append({
            'directUrl': sd_url,
            'token': sd_url,
            'note': '🎬 480p SD (Standard MP4)',
            'ext': 'mp4',
            'height': 480,
            'hasAudio': True
        })
    if formats:
        return {
            'title': title,
            'thumbnail': thumb,
            'formats': formats,
            'maxResolution': '1080p' if hd_match else '480p',
            'type': 'facebook'
        }
    return None

def extract_ytdlp(url):
    cookie_file = None
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'socket_timeout': 8,
        'js_runtimes': {'node': {}},
        'http_headers': {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36'
        }
    }

    if 'instagram.com' in url:
        ig_session = os.environ.get('INSTAGRAM_SESSION_ID')
        ig_cookies = os.environ.get('INSTAGRAM_COOKIES')
        if ig_session:
            ydl_opts.setdefault('http_headers', {})['Cookie'] = f"sessionid={ig_session};"
        elif ig_cookies:
            try:
                tf = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
                tf.write(ig_cookies)
                tf.close()
                ydl_opts['cookiefile'] = tf.name
                cookie_file = tf.name
            except Exception:
                pass

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
    finally:
        if cookie_file and os.path.exists(cookie_file):
            try:
                os.unlink(cookie_file)
            except Exception:
                pass

    title = info.get('title') or 'Media Video'
    thumbnail = info.get('thumbnail') or ''
    raw_formats = info.get('formats') or []

    # 1. Filter out all m3u8 and mpd manifests (unsupported by simple players)
    playable_raw = []
    for f in raw_formats:
        url_stream = f.get('url') or ''
        proto = (f.get('protocol') or '').lower()
        if not url_stream or 'm3u8' in proto or '.m3u8' in url_stream or '.mpd' in url_stream:
            continue
        playable_raw.append(f)

    # 2. Separate video streams and audio streams
    video_streams = [f for f in playable_raw if f.get('vcodec') and f.get('vcodec') != 'none' and f.get('height')]
    audio_streams = [f for f in playable_raw if (not f.get('vcodec') or f.get('vcodec') == 'none') and f.get('acodec') and f.get('acodec') != 'none']

    def codec_score(f):
        vc = (f.get('vcodec') or '').lower()
        ac = (f.get('acodec') or '').lower()
        score = 0
        if ac and ac != 'none': score += 100 # Strongly prefer muxed audio!
        if 'avc' in vc or 'h264' in vc: score += 10
        elif 'vp9' in vc: score += 5
        return score

    def get_tier_label(h, has_audio):
        audio_suffix = "" if has_audio else " - Video Only"
        if h >= 2160: return f"🎬 4K UHD ({h}p MP4{audio_suffix})"
        if h >= 1440: return f"🎬 2K QHD ({h}p MP4{audio_suffix})"
        if h >= 1080: return f"🎬 1080p FHD (MP4{audio_suffix})"
        if h >= 720:  return f"🎬 720p HD (MP4{audio_suffix})"
        if h >= 480:  return f"🎬 480p SD (MP4{audio_suffix})"
        if h >= 360:  return f"🎬 360p (MP4{audio_suffix})"
        return f"🎬 {h}p (MP4{audio_suffix})"

    # Select MAX 3-4 video options across standard resolution tiers
    tier1 = [f for f in video_streams if f['height'] >= 1080]
    tier2 = [f for f in video_streams if 720 <= f['height'] < 1080]
    tier3 = [f for f in video_streams if 480 <= f['height'] < 720]
    tier4 = sorted([f for f in video_streams if f['height'] <= 360], key=lambda x: x['height'], reverse=True)

    selected_video = []

    # Tier 1 (Best: 4K/2K/1080p)
    if tier1:
        max_h = max(f['height'] for f in tier1)
        tier1_top = [f for f in tier1 if f['height'] == max_h]
        selected_video.append(max(tier1_top, key=codec_score))

    # Tier 2 (HD 720p)
    if tier2:
        selected_video.append(max(tier2, key=codec_score))

    # Tier 3 (SD 480p)
    if tier3:
        selected_video.append(max(tier3, key=codec_score))

    # Tier 4 (SD 360p) - only if we have room for up to 4 video options
    if tier4 and len(selected_video) < 4:
        selected_video.append(max(tier4, key=codec_score))

    # Fallback if specific tiers were not matched (e.g. non-standard heights)
    if not selected_video and video_streams:
        unique_h = sorted(list(set(f['height'] for f in video_streams)), reverse=True)[:4]
        for h in unique_h:
            matching = [f for f in video_streams if f['height'] == h]
            selected_video.append(max(matching, key=codec_score))

    # Enforce strictly max 4 video options
    selected_video = selected_video[:4]

    formats = []
    for f in selected_video:
        url_stream = f['url']
        ext = f.get('ext') or 'mp4'
        h = f.get('height') or 720
        has_audio = bool(f.get('acodec') and f.get('acodec') != 'none')
        formats.append({
            'directUrl': url_stream,
            'token': url_stream,
            'note': get_tier_label(h, has_audio),
            'ext': ext,
            'sizeBytes': f.get('filesize') or f.get('filesize_approx'),
            'height': h,
            'hasAudio': has_audio
        })

    # Add exactly 1 High Quality Audio option
    if audio_streams:
        best_audio = max(audio_streams, key=lambda a: a.get('abr') or 0)
        formats.append({
            'directUrl': best_audio['url'],
            'token': best_audio['url'],
            'note': f"🎵 High Quality Audio ({best_audio.get('ext') or 'm4a'})",
            'ext': best_audio.get('ext') or 'm4a',
            'sizeBytes': best_audio.get('filesize') or best_audio.get('filesize_approx'),
            'isAudio': True
        })

    # If single direct stream returned by extractor
    if not formats and info.get('url'):
        formats.append({
            'directUrl': info['url'],
            'token': info['url'],
            'note': f"Original Quality ({info.get('ext') or 'mp4'})",
            'ext': info.get('ext') or 'mp4'
        })

    max_h = max([f.get('height', 0) for f in formats if not f.get('isAudio')]) if formats else 720
    max_badge = '4K' if max_h >= 2160 else ('1080p' if max_h >= 1080 else ('720p' if max_h >= 720 else 'SD'))

    return {
        'title': title,
        'thumbnail': thumbnail,
        'formats': formats,
        'maxResolution': max_badge,
        'type': info.get('extractor') or 'video'
    }

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
        self.wfile.write(json.dumps({'status': 'ok', 'service': 'HK Downloader Pro Universal Engine v2.1'}).encode('utf-8'))

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

        # 1. Direct Media File Check (.mp4, .webm, .mp3, etc.)
        direct_result = extract_direct_media(url)
        if direct_result:
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(direct_result).encode('utf-8'))
            return

        # 2. TikTok Direct Engine (TikWM API)
        if 'tiktok.com' in url:
            tt_result = extract_tiktok(url)
            if tt_result:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(tt_result).encode('utf-8'))
                return

        # 3. Facebook Direct Engine
        if 'facebook.com' in url or 'fb.watch' in url:
            fb_result = extract_facebook_direct(url)
            if fb_result:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(fb_result).encode('utf-8'))
                return

        # 4. Pinterest Direct Engine
        if 'pinterest.com' in url or 'pin.it' in url:
            pin_result = extract_pinterest(url)
            if pin_result:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(pin_result).encode('utf-8'))
                return

        # 5. Core yt-dlp Universal Engine (YouTube, Reddit, Vimeo, etc.)
        try:
            ytdlp_result = extract_ytdlp(url)
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(ytdlp_result).encode('utf-8'))
            return
        except Exception as e:
            err_msg = str(e)

            # Fallback for Facebook if yt-dlp was attempted first
            if 'facebook.com' in url or 'fb.watch' in url:
                fb_result = extract_facebook_direct(url)
                if fb_result:
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.send_header('Access-Control-Allow-Origin', '*')
                    self.end_headers()
                    self.wfile.write(json.dumps(fb_result).encode('utf-8'))
                    return

            # Fallback for Pinterest
            if 'pinterest.com' in url or 'pin.it' in url:
                pin_result = extract_pinterest(url)
                if pin_result:
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.send_header('Access-Control-Allow-Origin', '*')
                    self.end_headers()
                    self.wfile.write(json.dumps(pin_result).encode('utf-8'))
                    return

            # User-friendly explanation for Instagram authentication requirement
            if 'instagram.com' in url:
                err_msg = "Instagram restricts unauthenticated server downloads. Use HK Downloader Android App (v2.5.0) or configure an Instagram session cookie in settings."

            self.send_response(502)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': err_msg}).encode('utf-8'))
