from http.server import BaseHTTPRequestHandler
import json
import urllib.request
import urllib.parse
from urllib.parse import urlparse, parse_qs
import ipaddress
import socket
import re

def is_safe_public_url(url: str) -> bool:
    if not url:
        return False
    try:
        parsed = urllib.parse.urlparse(url)
        if parsed.scheme.lower() not in ('http', 'https'):
            return False
        hostname = parsed.hostname
        if not hostname:
            return False
        hostname_lower = hostname.lower()
        blocked_hosts = {
            'localhost', '127.0.0.1', '::1', '0.0.0.0',
            '169.254.169.254', 'metadata.google.internal', 'instance-data'
        }
        if hostname_lower in blocked_hosts or hostname_lower.endswith('.local') or hostname_lower.endswith('.internal'):
            return False

        # Resolve IP addresses and verify they are globally routable (not private/loopback/link-local)
        addr_info = socket.getaddrinfo(hostname, None, proto=socket.IPPROTO_TCP)
        for entry in addr_info:
            ip_str = entry[4][0]
            ip = ipaddress.ip_address(ip_str)
            if (ip.is_private or ip.is_loopback or ip.is_link_local or
                ip.is_multicast or ip.is_reserved or ip.is_unspecified):
                return False
        return True
    except Exception:
        return False

def resolve_youtube_fresh_stream(video_id):
    clients = [
        {
            'name': 'IOS',
            'version': '20.03.02',
            'clientNameId': '5',
            'ua': 'com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)',
            'context': {
                'clientName': 'IOS',
                'clientVersion': '20.03.02',
                'deviceMake': 'Apple',
                'deviceModel': 'iPhone16,2',
                'osName': 'iPhone',
                'osVersion': '18.2.1.22C161',
                'gl': 'US',
                'hl': 'en'
            }
        },
        {
            'name': 'ANDROID_VR',
            'version': '1.62.27',
            'clientNameId': '28',
            'ua': 'com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip',
            'context': {
                'clientName': 'ANDROID_VR',
                'clientVersion': '1.62.27',
                'deviceMake': 'Oculus',
                'deviceModel': 'Quest 3',
                'osName': 'Android',
                'osVersion': '12L',
                'androidSdkVersion': 32,
                'gl': 'US',
                'hl': 'en'
            }
        }
    ]

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
                headers=headers,
                method='POST'
            )
            with urllib.request.urlopen(req, timeout=6) as response:
                if response.status == 200:
                    data = json.loads(response.read().decode('utf-8'))
                    raw_formats = data.get('streamingData', {}).get('formats', [])
                    for f in raw_formats:
                        if f.get('url'):
                            return f['url']
                    adaptive = data.get('streamingData', {}).get('adaptiveFormats', [])
                    for af in adaptive:
                        if af.get('url'):
                            return af['url']
        except Exception:
            continue
    return None

class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', '*')
        self.send_header('Access-Control-Max-Age', '86400')
        self.end_headers()

    def do_HEAD(self):
        self.do_GET(is_head=True)

    def do_GET(self, is_head=False):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        target_url = (qs.get('url') or qs.get('token') or [None])[0]
        filename = (qs.get('filename') or ['video.mp4'])[0]
        yt_id = (qs.get('id') or qs.get('ytId') or qs.get('videoId') or [None])[0]

        if not yt_id and target_url:
            m = re.search(r'(?:v=|youtu\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})', target_url)
            if m:
                yt_id = m.group(1)

        if (not target_url or not target_url.startswith('http')) and yt_id:
            fresh_url = resolve_youtube_fresh_stream(yt_id)
            if fresh_url:
                target_url = fresh_url

        if not target_url or not is_safe_public_url(target_url):
            self.send_response(400)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Target URL is missing, invalid, or points to an unauthorized network address'}).encode('utf-8'))
            return

        # Upstream streaming
        referer = ''
        if 'googlevideo' in target_url or 'youtube' in target_url:
            referer = 'https://www.youtube.com/'
        elif 'instagram' in target_url:
            referer = 'https://www.instagram.com/'
        elif 'facebook' in target_url or 'fbcdn' in target_url:
            referer = 'https://www.facebook.com/'
        elif 'reddit' in target_url or 'redd.it' in target_url:
            referer = 'https://www.reddit.com/'
        elif 'pinterest' in target_url or 'pinimg' in target_url:
            referer = 'https://www.pinterest.com/'

        is_yt = 'googlevideo' in target_url or 'youtube' in target_url
        uas_to_try = [
            'com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)',
            'com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip',
            'com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip',
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36'
        ] if is_yt else [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36'
        ]

        range_hdr = self.headers.get('Range')
        upstream_resp = None

        for ua in uas_to_try:
            req_headers = {'User-Agent': ua}
            if referer:
                req_headers['Referer'] = referer
            if range_hdr:
                req_headers['Range'] = range_hdr

            try:
                upstream_req = urllib.request.Request(target_url, headers=req_headers)
                upstream_resp = urllib.request.urlopen(upstream_req, timeout=15)
                if upstream_resp.status in (200, 206):
                    break
            except Exception:
                continue

        if not upstream_resp:
            self.send_response(502)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Upstream stream request failed'}).encode('utf-8'))
            return

        status_code = upstream_resp.status
        self.send_response(status_code)

        # Forward headers with CORS
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', '*')
        self.send_header('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Content-Disposition, Accept-Ranges')

        ct = upstream_resp.headers.get('Content-Type', 'application/octet-stream')
        self.send_header('Content-Type', ct)

        cl = upstream_resp.headers.get('Content-Length')
        if cl:
            self.send_header('Content-Length', cl)

        cr = upstream_resp.headers.get('Content-Range')
        if cr:
            self.send_header('Content-Range', cr)

        ar = upstream_resp.headers.get('Accept-Ranges', 'bytes')
        self.send_header('Accept-Ranges', ar)

        safe_fn = filename.replace('"', '').replace('\r', '').replace('\n', '')
        self.send_header('Content-Disposition', f'attachment; filename="{safe_fn}"')
        self.end_headers()

        if is_head:
            upstream_resp.close()
            return

        try:
            while True:
                chunk = upstream_resp.read(64 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)
        except Exception:
            pass
        finally:
            upstream_resp.close()
