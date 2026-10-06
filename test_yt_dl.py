import urllib.request, json, urllib.parse, sys
sys.stdout.reconfigure(encoding='utf-8')

req = urllib.request.Request('https://hk-downloader-pro.pages.dev/api/analyze',
                             data=json.dumps({'inputUrl': 'https://www.youtube.com/watch?v=dQw4w9WgXcQ'}).encode('utf-8'),
                             headers={'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0'})
resp = urllib.request.urlopen(req)
data = json.loads(resp.read().decode('utf-8'))

for f in data['formats']:
    note = f['note']
    mainUrl = f['directUrl']
    url = f"https://hk-downloader-pro.pages.dev/api/download?type=youtube&id=dQw4w9WgXcQ&url={urllib.parse.quote(mainUrl)}&filename=test.mp4"
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'}))
        chunk = r.read(1024*32)
        print(f"[OK] {note} -> Status: {r.status}, Content-Length: {r.headers.get('Content-Length')}, Content-Type: {r.headers.get('Content-Type')}, Chunk: {len(chunk)}")
    except Exception as e:
        print(f"[ERR] {note} -> ERROR: {e}")
