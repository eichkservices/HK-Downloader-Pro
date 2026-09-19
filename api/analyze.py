from http.server import BaseHTTPRequestHandler
import json
import yt_dlp

class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length)
        try:
            body = json.loads(post_data.decode('utf-8'))
        except Exception:
            body = {}

        url = body.get('inputUrl') or body.get('url')
        if not url:
            self.send_response(400)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Missing inputUrl'}).encode('utf-8'))
            return

        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'skip_download': True
        }

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)

            title = info.get('title') or 'Media Video'
            thumbnail = info.get('thumbnail') or ''
            raw_formats = info.get('formats') or []

            formats = []
            seen_heights = set()

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
                        'note': f"🎬 {height}p ({ext.upper()})",
                        'ext': ext,
                        'sizeBytes': size
                    })

            formats.sort(key=lambda x: int((x['note'].split('p')[0].split()[-1])) if 'p' in x['note'] and x['note'].split('p')[0].split()[-1].isdigit() else 0, reverse=True)

            audios = [f for f in raw_formats if f.get('url') and f.get('vcodec') == 'none' and f.get('acodec') != 'none']
            if audios:
                best_audio = max(audios, key=lambda a: a.get('abr') or 0)
                formats.append({
                    'directUrl': best_audio['url'],
                    'token': best_audio['url'],
                    'note': f"🎵 Audio ({best_audio.get('ext') or 'm4a'})",
                    'ext': best_audio.get('ext') or 'm4a',
                    'sizeBytes': best_audio.get('filesize') or best_audio.get('filesize_approx')
                })

            if not formats and info.get('url'):
                formats.append({
                    'directUrl': info['url'],
                    'token': info['url'],
                    'note': f"Best Quality ({info.get('ext') or 'mp4'})",
                    'ext': info.get('ext') or 'mp4'
                })

            response_data = {
                'title': title,
                'thumbnail': thumbnail,
                'formats': formats,
                'type': info.get('extractor') or 'video'
            }

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(response_data).encode('utf-8'))

        except Exception as e:
            self.send_response(502)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({'error': str(e)}).encode('utf-8'))
