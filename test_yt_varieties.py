import urllib.request, json, sys
sys.stdout.reconfigure(encoding='utf-8')

test_urls = [
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'https://www.youtube.com/shorts/51J32lE8x7k',
    'https://www.youtube.com/watch?v=JGwWNGJdvx8',
    'https://youtu.be/kJQP7kiw5Fk'
]

for u in test_urls:
    print(f"\n--- Testing YouTube URL: {u} ---")
    req = urllib.request.Request('https://hk-downloader-pro.pages.dev/api/analyze',
                                 data=json.dumps({'inputUrl': u}).encode('utf-8'),
                                 headers={'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            print("Title:", data.get('title'))
            formats = data.get('formats', [])
            print(f"Formats count: {len(formats)}")
            for f in formats:
                print(f"  {f.get('note')} (ext: {f.get('ext')}, hasAudio: {f.get('hasAudio')})")
    except Exception as e:
        print("Analyze Error:", e)
