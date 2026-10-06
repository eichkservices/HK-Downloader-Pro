import urllib.request, json

test_ids = ['51J32lE8x7k', 'dQw4w9WgXcQ', 'JGwWNGJdvx8']

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
    },
    {
        'name': 'IOS',
        'version': '19.45.4',
        'clientNameId': '5',
        'ua': 'com.google.ios.youtube/19.45.4 (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X; en_US)',
        'context': {'clientName': 'IOS', 'clientVersion': '19.45.4', 'deviceMake': 'Apple', 'deviceModel': 'iPhone14,3', 'osName': 'iPhone', 'osVersion': '17.5.1.21F90', 'hl': 'en', 'gl': 'US'}
    },
    {
        'name': 'MWEB',
        'version': '2.20240101.01.00',
        'clientNameId': '62',
        'ua': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
        'context': {'clientName': 'MWEB', 'clientVersion': '2.20240101.01.00'}
    }
]

for vid in test_ids:
    print(f"\n=== Video ID: {vid} ===")
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
                'videoId': vid,
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
            reason = data.get('playabilityStatus', {}).get('reason')
            streaming = data.get('streamingData', {})
            raw_f = [f for f in streaming.get('formats', []) if f.get('url')]
            adap_f = [f for f in streaming.get('adaptiveFormats', []) if f.get('url')]
            cipher_f = [f for f in streaming.get('adaptiveFormats', []) if f.get('signatureCipher') or f.get('cipher')]
            print(f"  {c['name']}: status={status}, reason={reason}, direct_formats={len(raw_f)}, direct_adaptive={len(adap_f)}, cipher_formats={len(cipher_f)}")
        except Exception as e:
            print(f"  {c['name']}: error={e}")
