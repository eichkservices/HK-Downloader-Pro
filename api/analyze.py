from http.server import BaseHTTPRequestHandler
import http.cookiejar
import json
import os
import re
import tempfile
import urllib.request
import urllib.parse
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse, unquote
import yt_dlp
import html

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

LAST_DIRECT_ERR = None

def extract_youtube_direct(url):
    global LAST_DIRECT_ERR
    try:
        video_id = None
        m = re.search(r'(?:v=|youtu\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})', url)
        if m:
            video_id = m.group(1)
        if not video_id:
            LAST_DIRECT_ERR = f"No 11-char video_id in {url}"
            return None

        clients = [
            {
                'name': 'ANDROID_VR',
                'version': '1.61.48',
                'clientNameId': '28',
                'ua': 'Mozilla/5.0 (Linux; Android 10; Quest 2) AppleWebKit/537.36',
                'context': {'clientName': 'ANDROID_VR', 'clientVersion': '1.61.48', 'deviceMake': 'Oculus', 'deviceModel': 'Quest 2', 'gl': 'US', 'hl': 'en'}
            },
            {
                'name': 'ANDROID',
                'version': '21.26.364',
                'clientNameId': '3',
                'ua': 'com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip',
                'context': {'clientName': 'ANDROID', 'clientVersion': '21.26.364', 'androidSdkVersion': 30}
            }
        ]

        thumb = f'https://img.youtube.com/vi/{video_id}/hqdefault.jpg'
        formats = []
        title = 'YouTube Video'

        for c in clients:
            try:
                headers = {
                    'Content-Type': 'application/json',
                    'X-YouTube-Client-Name': c['clientNameId'],
                    'X-YouTube-Client-Version': c['version'],
                    'Origin': 'https://www.youtube.com',
                    'User-Agent': c['ua']
                }
                payload = {
                    'videoId': video_id,
                    'context': {'client': c['context']},
                    'playbackContext': {
                        'contentPlaybackContext': {
                            'html5Preference': 'HTML5_PREF_WANTS',
                            'signatureTimestamp': 20717
                        }
                    },
                    'contentCheckOk': True,
                    'racyCheckOk': True
                }
                req = urllib.request.Request(
                    'https://www.youtube.com/youtubei/v1/player',
                    data=json.dumps(payload).encode('utf-8'),
                    headers=headers
                )
                with urllib.request.urlopen(req, timeout=8) as r:
                    data = json.loads(r.read().decode('utf-8'))

                status = data.get('playabilityStatus', {}).get('status')
                if status != 'OK':
                    reason = data.get('playabilityStatus', {}).get('reason') or 'status not OK'
                    LAST_DIRECT_ERR = f"Innertube status: {status} ({reason})"
                    continue

                details = data.get('videoDetails', {})
                title = details.get('title') or title
                thumbs_list = details.get('thumbnail', {}).get('thumbnails', [])
                if thumbs_list:
                    thumb = thumbs_list[-1].get('url') or thumb
                elif video_id:
                    thumb = f'https://i.ytimg.com/vi/{video_id}/maxresdefault.jpg'

                streamingData = data.get('streamingData', {})
                raw_formats = streamingData.get('formats', [])
                adaptive = streamingData.get('adaptiveFormats', [])

                audio_streams = [af for af in adaptive if af.get('url') and 'audio' in (af.get('mimeType') or '')]
                best_audio = None
                if audio_streams:
                    best_audio = max(audio_streams, key=lambda a: int(a.get('bitrate') or a.get('averageBitrate') or 0))
                best_audio_url = best_audio['url'] if best_audio else None

                # Collect all video streams mapped by height
                video_map = {}
                # 1. Progressive streams (muxed audio)
                for f in raw_formats:
                    u = f.get('url')
                    h = f.get('height')
                    if u and h:
                        clen = f.get('contentLength')
                        video_map[h] = {
                            'url': u,
                            'height': h,
                            'ext': 'mp4',
                            'hasMuxedAudio': True,
                            'qualityLabel': f.get('qualityLabel') or f'{h}p',
                            'sizeBytes': int(clen) if clen and str(clen).isdigit() else None
                        }

                # 2. Adaptive streams (video only)
                for af in adaptive:
                    u = af.get('url')
                    h = af.get('height')
                    if u and h:
                        is_mp4 = 'mp4' in (af.get('mimeType') or '')
                        existing = video_map.get(h)
                        if not existing or (not existing.get('hasMuxedAudio') and is_mp4):
                            clen = af.get('contentLength')
                            video_map[h] = {
                                'url': u,
                                'height': h,
                                'ext': 'mp4' if is_mp4 else 'webm',
                                'hasMuxedAudio': False,
                                'qualityLabel': af.get('qualityLabel') or f'{h}p',
                                'sizeBytes': int(clen) if clen and str(clen).isdigit() else None
                            }

                available_heights = sorted(video_map.keys(), reverse=True)
                if not available_heights:
                    continue

                max_h = available_heights[0]

                # 1. Original Quality (if > 1080p, e.g. 4K 2160p or 2K 1440p)
                if max_h > 1080:
                    vf = video_map[max_h]
                    has_muxed = vf['hasMuxedAudio']
                    audio_u = None if has_muxed else best_audio_url
                    tok = f"{vf['url']}|{audio_u}" if audio_u else vf['url']
                    q_label = '4K UHD' if max_h >= 2160 else ('2K QHD' if max_h >= 1440 else f'{max_h}p')
                    formats.append({
                        'directUrl': vf['url'],
                        'audioUrl': audio_u,
                        'token': tok,
                        'videoId': video_id,
                        'url': url,
                        'note': f"🎬 {q_label} ({max_h}p Original Quality)",
                        'ext': vf['ext'],
                        'height': max_h,
                        'hasAudio': True,
                        'sizeBytes': vf['sizeBytes']
                    })

                # 2. 1080p FHD
                if 1080 in video_map:
                    vf = video_map[1080]
                    has_muxed = vf['hasMuxedAudio']
                    audio_u = None if has_muxed else best_audio_url
                    tok = f"{vf['url']}|{audio_u}" if audio_u else vf['url']
                    is_orig = (max_h == 1080)
                    note_str = "🎬 1080p FHD (Original Quality MP4)" if is_orig else "🎬 1080p FHD (MP4)"
                    formats.append({
                        'directUrl': vf['url'],
                        'audioUrl': audio_u,
                        'token': tok,
                        'videoId': video_id,
                        'url': url,
                        'note': note_str,
                        'ext': vf['ext'],
                        'height': 1080,
                        'hasAudio': True,
                        'sizeBytes': vf['sizeBytes']
                    })

                # 3. 720p HD
                if 720 in video_map:
                    vf = video_map[720]
                    has_muxed = vf['hasMuxedAudio']
                    audio_u = None if has_muxed else best_audio_url
                    tok = f"{vf['url']}|{audio_u}" if audio_u else vf['url']
                    is_orig = (max_h == 720)
                    note_str = "🎬 720p HD (Original Quality MP4)" if is_orig else "🎬 720p HD (MP4)"
                    formats.append({
                        'directUrl': vf['url'],
                        'audioUrl': audio_u,
                        'token': tok,
                        'videoId': video_id,
                        'url': url,
                        'note': note_str,
                        'ext': vf['ext'],
                        'height': 720,
                        'hasAudio': True,
                        'sizeBytes': vf['sizeBytes']
                    })

                # 4. 480p SD (if available and needed)
                if 480 in video_map and (len(formats) < 2 or max_h == 480):
                    vf = video_map[480]
                    has_muxed = vf['hasMuxedAudio']
                    audio_u = None if has_muxed else best_audio_url
                    tok = f"{vf['url']}|{audio_u}" if audio_u else vf['url']
                    is_orig = (max_h == 480)
                    note_str = "🎬 480p SD (Original Quality MP4)" if is_orig else "🎬 480p SD (MP4)"
                    formats.append({
                        'directUrl': vf['url'],
                        'audioUrl': audio_u,
                        'token': tok,
                        'videoId': video_id,
                        'url': url,
                        'note': note_str,
                        'ext': vf['ext'],
                        'height': 480,
                        'hasAudio': True,
                        'sizeBytes': vf['sizeBytes']
                    })

                # 5. 360p Standard
                if 360 in video_map:
                    vf = video_map[360]
                    has_muxed = vf['hasMuxedAudio']
                    audio_u = None if has_muxed else best_audio_url
                    tok = f"{vf['url']}|{audio_u}" if audio_u else vf['url']
                    is_orig = (max_h == 360)
                    note_str = "🎬 360p (Original Quality MP4)" if is_orig else "🎬 360p (Standard MP4 with Audio)"
                    formats.append({
                        'directUrl': vf['url'],
                        'audioUrl': audio_u,
                        'token': tok,
                        'videoId': video_id,
                        'url': url,
                        'note': note_str,
                        'ext': vf['ext'],
                        'height': 360,
                        'hasAudio': True,
                        'sizeBytes': vf['sizeBytes']
                    })
                elif not formats and available_heights:
                    for h in available_heights[:3]:
                        vf = video_map[h]
                        has_muxed = vf['hasMuxedAudio']
                        audio_u = None if has_muxed else best_audio_url
                        tok = f"{vf['url']}|{audio_u}" if audio_u else vf['url']
                        formats.append({
                            'directUrl': vf['url'],
                            'audioUrl': audio_u,
                            'token': tok,
                            'videoId': video_id,
                            'url': url,
                            'note': f"🎬 {h}p (MP4)",
                            'ext': vf['ext'],
                            'height': h,
                            'hasAudio': True,
                            'sizeBytes': vf['sizeBytes']
                        })

                # 6. High Quality Audio Track
                if best_audio:
                    clen = best_audio.get('contentLength')
                    size_bytes = int(clen) if clen and str(clen).isdigit() else None
                    formats.append({
                        'directUrl': best_audio['url'],
                        'token': best_audio['url'],
                        'videoId': video_id,
                        'url': url,
                        'note': '🎵 High Quality Audio (M4A / MP3)',
                        'ext': 'm4a',
                        'isAudio': True,
                        'sizeBytes': size_bytes
                    })

                if formats:
                    break
            except Exception as e:
                LAST_DIRECT_ERR = f"Innertube exception ({c['name']}): {str(e)}"

        if formats:
            return {
                'title': title,
                'thumbnail': thumb,
                'videoId': video_id,
                'url': url,
                'formats': formats,
                'maxResolution': formats[0].get('note', '1080p'),
                'type': 'youtube'
            }
    except Exception as e:
        LAST_DIRECT_ERR = f"Innertube exception: {str(e)}"
    return None

def extract_ytdlp(url):
    cookie_file = None
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'socket_timeout': 8,
        'http_headers': {
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15'
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
    elif 'youtube.com' in url or 'youtu.be' in url:
        yt_cookies = os.environ.get('YOUTUBE_COOKIES') or os.environ.get('COOKIES')
        if yt_cookies:
            try:
                tf = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
                tf.write(yt_cookies)
                tf.close()
                ydl_opts['cookiefile'] = tf.name
                cookie_file = tf.name
            except Exception:
                pass
    elif 'reddit.com' in url or 'redd.it' in url:
        rd_cookies = os.environ.get('REDDIT_COOKIES') or os.environ.get('COOKIES')
        if rd_cookies:
            try:
                tf = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt')
                tf.write(rd_cookies)
                tf.close()
                ydl_opts['cookiefile'] = tf.name
                cookie_file = tf.name
            except Exception:
                pass

    try:
        if 'youtube.com' in url or 'youtu.be' in url:
            client_candidates = [
                ['visionos'],
                ['android_creator'],
                ['tv_embedded'],
                ['android_vr'],
                ['web_embedded']
            ]
            client_log = {}
            info = None
            last_err = None
            for client_list in client_candidates:
                key = "+".join(client_list) if client_list else "default"
                if client_list:
                    ydl_opts['extractor_args'] = {'youtube': {'player_client': client_list}}
                else:
                    ydl_opts.pop('extractor_args', None)
                try:
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        cand_info = ydl.extract_info(url, download=False)
                        if cand_info and cand_info.get('formats'):
                            cand_fmts = cand_info.get('formats', [])
                            cand_v_fmts = [f for f in cand_fmts if f.get('height') and f.get('vcodec') != 'none']
                            if len(cand_v_fmts) >= 2:
                                info = cand_info
                                break
                            elif not info:
                                info = cand_info
                except Exception as e:
                    client_log[key] = str(e)[:120]
                    last_err = e
                    continue
            if not info:
                raise Exception(f"YouTube extraction failed: {json.dumps(client_log)}")
        else:
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

    # 2. Separate video streams, progressive streams, and audio streams
    progressive_streams = [
        f for f in playable_raw
        if f.get('ext') == 'mp4' and (
            'xpv_progressive' in (f.get('url') or '') or
            f.get('format_id') in ['1', '2', '3'] or
            (f.get('vcodec') and f.get('vcodec') != 'none' and f.get('acodec') and f.get('acodec') != 'none')
        )
    ]
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

    best_audio_stream = max(audio_streams, key=lambda a: a.get('abr') or 0) if audio_streams else None
    best_audio_url = best_audio_stream['url'] if best_audio_stream else None

    is_instagram = 'instagram.com' in url

    formats = []

    # If progressive stream exists (e.g. Instagram Reels standard progressive MP4 with built-in audio)
    if progressive_streams:
        prog = progressive_streams[0]
        formats.append({
            'directUrl': prog['url'],
            'audioUrl': None,
            'token': prog['url'],
            'note': '🎬 720p HD (Standard MP4 with Audio)',
            'ext': 'mp4',
            'height': 720,
            'hasAudio': True
        })

    # Only include DASH video streams if NOT Instagram (since Instagram DASH video streams are silent VP9 without audio on direct download)
    if not is_instagram:
        for f in selected_video:
            url_stream = f['url']
            ext = f.get('ext') or 'mp4'
            h = f.get('height') or 720
            has_muxed_audio = bool(f.get('acodec') and f.get('acodec') != 'none')
            audio_url = None if has_muxed_audio else best_audio_url
            token_val = f"{url_stream}|{audio_url}" if audio_url else url_stream
            has_any_audio = has_muxed_audio or bool(best_audio_url)
            # Don't add duplicate 720p if progressive already added
            if progressive_streams and h == 720 and not has_muxed_audio:
                continue
            formats.append({
                'directUrl': url_stream,
                'audioUrl': audio_url,
                'token': token_val,
                'note': get_tier_label(h, has_any_audio),
                'ext': ext,
                'sizeBytes': f.get('filesize') or f.get('filesize_approx'),
                'height': h,
                'hasAudio': has_any_audio
            })

    # Add exactly 1 High Quality Audio option
    if best_audio_stream:
        formats.append({
            'directUrl': best_audio_stream['url'],
            'token': best_audio_stream['url'],
            'note': f"🎵 High Quality Audio ({best_audio_stream.get('ext') or 'm4a'})",
            'ext': best_audio_stream.get('ext') or 'm4a',
            'sizeBytes': best_audio_stream.get('filesize') or best_audio_stream.get('filesize_approx'),
            'isAudio': True
        })
    elif formats and any(f.get('hasAudio') for f in formats):
        audio_source = next(f for f in formats if f.get('hasAudio'))
        formats.append({
            'directUrl': audio_source['directUrl'],
            'token': audio_source['token'],
            'note': "🎵 Audio Track (MP3 / Audio)",
            'ext': 'mp3',
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

def extract_reddit(url):
    try:
        # 1. If directly a v.redd.it URL, yt-dlp can handle directly without auth
        if 'v.redd.it' in url:
            return extract_ytdlp(url)

        # 2. RapidSave resolver (Fastest and highly reliable across datacenter IPs)
        try:
            req_rs = urllib.request.Request(
                f'https://rapidsave.com/info?url={url}',
                headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
            )
            with urllib.request.urlopen(req_rs, timeout=8) as r:
                page = r.read().decode('utf-8', errors='ignore')

            t_match = re.search(r'<h2[^>]*class="[^"]*text-truncate[^"]*"[^>]*>\s*([^<]+)\s*</h2>', page)
            title = html.unescape(t_match.group(1).strip()) if t_match else 'Reddit Video'

            merged_m = re.search(r'href=[\'"](https://sd\.rapidsave\.com/download\.php[^\'"]+)[\'"]', page)
            merged_url = merged_m.group(1).replace('&amp;', '&') if merged_m else None

            v_match = re.search(r'https?://v\.redd\.it/([a-zA-Z0-9]+)', page)
            if v_match:
                v_id = v_match.group(1)
                formats = []
                # 1) Merged video with audio
                if merged_url:
                    formats.append({
                        'directUrl': merged_url,
                        'token': merged_url,
                        'note': '🎬 720p HD (Merged Audio + Video MP4)',
                        'ext': 'mp4',
                        'height': 720,
                        'hasAudio': True
                    })

                # 2) Progressive streams from v.redd.it via yt-dlp
                try:
                    ytdl_res = extract_ytdlp(f"https://v.redd.it/{v_id}")
                    if ytdl_res and ytdl_res.get('formats'):
                        for f in ytdl_res['formats']:
                            if len(formats) < 4:
                                formats.append(f)
                except Exception:
                    pass

                # 3) Ensure dedicated audio option
                if not any(f.get('isAudio') for f in formats):
                    formats.append({
                        'directUrl': f"https://v.redd.it/{v_id}/CMAF_AUDIO_128.mp4",
                        'token': f"https://v.redd.it/{v_id}/CMAF_AUDIO_128.mp4",
                        'note': '🎵 Audio Track (M4A / MP3)',
                        'ext': 'm4a',
                        'isAudio': True
                    })

                if formats:
                    return {
                        'title': title,
                        'thumbnail': '',
                        'formats': formats,
                        'maxResolution': '720p',
                        'type': 'reddit'
                    }
        except Exception:
            pass

        # 3. Direct Reddit API / Challenge Fallback (For images, galleries, or if RapidSave fails)
        id_m = re.search(r'comments/([a-zA-Z0-9]+)', url)
        if not id_m:
            return None
        post_id = id_m.group(1)

        cj = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'en-US,en;q=0.9',
        }
        reddit_cookies = os.environ.get('REDDIT_COOKIES')
        if reddit_cookies:
            headers['Cookie'] = reddit_cookies

        req = urllib.request.Request(url, headers=headers)
        html_page = ''
        try:
            with opener.open(req, timeout=8) as r:
                html_page = r.read().decode('utf-8', errors='ignore')
        except urllib.error.HTTPError as e:
            html_page = e.read().decode('utf-8', errors='ignore')
        except Exception:
            pass

        if 'js_challenge' in html_page:
            token_m = re.search(r'await\(async e=>e\+e\)\("([0-9a-fA-F]+)"\)', html_page)
            jsc_m = re.search(r'name="jsc_token"\s+value="([^"]+)"', html_page)
            action_m = re.search(r'<form hidden method="GET" action="([^"]+)"', html_page)
            if token_m and jsc_m and action_m:
                token = token_m.group(1)
                solution = token + token
                jsc = jsc_m.group(1)
                action = action_m.group(1)
                query = urllib.parse.urlencode({
                    'solution': solution,
                    'js_challenge': '1',
                    'jsc_token': jsc,
                    'jsc_orig_r': ''
                })
                solve_url = urllib.parse.urljoin(url, action) + '?' + query
                req2 = urllib.request.Request(solve_url, headers=headers)
                try:
                    with opener.open(req2, timeout=8) as r2:
                        r2.read()
                except urllib.error.HTTPError as e2:
                    e2.read()
                except Exception:
                    pass

        json_url = f'https://www.reddit.com/comments/{post_id}/.json'
        req_json = urllib.request.Request(json_url, headers=headers)
        data = None
        try:
            with opener.open(req_json, timeout=8) as r_json:
                data = json.loads(r_json.read().decode('utf-8'))
        except urllib.error.HTTPError as e_json:
            try:
                data = json.loads(e_json.read().decode('utf-8'))
            except Exception:
                pass
        except Exception:
            pass

        if not data:
            v_matches = re.findall(r'https?://v\.redd\.it/([a-zA-Z0-9]+)', html_page)
            if v_matches:
                v_url = f"https://v.redd.it/{v_matches[0]}"
                v_res = extract_ytdlp(v_url)
                if v_res and v_res.get('formats'):
                    title_m = re.search(r'<title>([^<]+)</title>', html_page)
                    if title_m:
                        v_res['title'] = title_m.group(1).split('|')[0].strip()
                    v_res['type'] = 'reddit'
                    return v_res

        if not data or not isinstance(data, list) or len(data) == 0:
            return None

        post = data[0]['data']['children'][0]['data']
        title = post.get('title') or 'Reddit Media'
        thumb = post.get('thumbnail') if post.get('thumbnail') and post.get('thumbnail').startswith('http') else ''

        media = post.get('media') or post.get('secure_media')
        v_url = post.get('url') or ''

        # If native Reddit video
        if (media and 'reddit_video' in media) or 'v.redd.it' in v_url:
            if 'v.redd.it' in v_url:
                v_res = extract_ytdlp(v_url)
                if v_res and v_res.get('formats'):
                    v_res['title'] = title
                    if thumb: v_res['thumbnail'] = thumb
                    v_res['type'] = 'reddit'
                    return v_res

            if media and 'reddit_video' in media:
                rv = media['reddit_video']
                fallback_url = rv.get('fallback_url')
                h = rv.get('height') or 720
                if fallback_url:
                    formats = [{
                        'directUrl': fallback_url,
                        'token': fallback_url,
                        'note': f"🎬 {h}p HD (MP4)" if h >= 720 else f"🎬 {h}p SD (MP4)",
                        'ext': 'mp4',
                        'height': h,
                        'hasAudio': True
                    }]
                    return {
                        'title': title,
                        'thumbnail': thumb,
                        'formats': formats,
                        'maxResolution': f"{h}p",
                        'type': 'reddit'
                    }

        # Check for image post
        if post.get('post_hint') == 'image' or v_url.endswith(('.jpg', '.jpeg', '.png', '.webp', '.gif')):
            ext = v_url.split('?')[0].split('.')[-1]
            return {
                'title': title,
                'thumbnail': thumb or v_url,
                'formats': [{
                    'directUrl': v_url,
                    'token': v_url,
                    'note': f"📸 High Res Image ({ext.upper()})",
                    'ext': ext
                }],
                'maxResolution': 'Original',
                'type': 'reddit'
            }

        # Check gallery
        if post.get('is_gallery') and 'media_metadata' in post:
            formats = []
            for item_id, item_val in list(post['media_metadata'].items())[:5]:
                s = item_val.get('s', {})
                img_url = s.get('u') or s.get('gif')
                if img_url:
                    clean_img = img_url.replace('&amp;', '&')
                    formats.append({
                        'directUrl': clean_img,
                        'token': clean_img,
                        'note': f"📸 Gallery Photo #{len(formats)+1} (JPG)",
                        'ext': 'jpg'
                    })
            if formats:
                return {
                    'title': title,
                    'thumbnail': thumb or formats[0]['directUrl'],
                    'formats': formats,
                    'maxResolution': 'Original',
                    'type': 'reddit'
                }
    except Exception:
        pass
    return None


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
        try:
            import yt_dlp.version
            yver = getattr(yt_dlp.version, '__version__', 'unknown')
        except Exception:
            yver = 'unknown'
        self.wfile.write(json.dumps({
            'status': 'ok',
            'service': 'HK Downloader Pro Universal Engine v2.6.5',
            'ytdlp_version': yver,
            'build': 'visionos-v2.6.5'
        }).encode('utf-8'))

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

        # 5. Reddit Direct Engine (RapidSave + direct extraction)
        if 'reddit.com' in url or 'redd.it' in url:
            reddit_result = extract_reddit(url)
            if reddit_result:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(reddit_result).encode('utf-8'))
                return
            # If rapid extraction fails, fall through to core yt-dlp universal engine below

        # 6. YouTube Universal Engine (visionos multi-quality resolution for 4K/1080p/720p/480p/360p/audio)
        if 'youtube.com' in url or 'youtu.be' in url:
            try:
                ytdlp_result = extract_ytdlp(url)
                if ytdlp_result and len(ytdlp_result.get('formats', [])) >= 2:
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.send_header('Access-Control-Allow-Origin', '*')
                    self.end_headers()
                    self.wfile.write(json.dumps(ytdlp_result).encode('utf-8'))
                    return
            except Exception:
                pass

            # Fast direct Innertube fallback if yt-dlp was blocked
            yt_result = extract_youtube_direct(url)
            if yt_result and len(yt_result.get('formats', [])) > 0:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(yt_result).encode('utf-8'))
                return

        # 7. Core yt-dlp Universal Engine (Vimeo, SoundCloud, and other platforms)
        if 'youtube.com' not in url and 'youtu.be' not in url:
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
                return

        self.send_response(502)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps({'error': 'Unable to resolve streams for this YouTube video. Please try another link or download via HK Downloader Android app.'}).encode('utf-8'))
