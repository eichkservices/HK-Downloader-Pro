# Backend Setup Guide — Cobalt + yt-dlp
### For HK Downloader Pro (Android + Web) — written for both Hassan and Antigravity

This is the complete, standalone runbook for getting real video resolution
working, end to end. You shouldn't need to dig through chat history to follow
this — everything required is here.

---

## 0. The short version

Your app already has a working resolver chain (both platforms) that tries
multiple backends in order and falls back automatically. Right now, it has
real code for **Cobalt** and **tikwm** (TikTok only), but no working backend
is actually *running* anywhere yet — the app is correctly built, just not
pointed at a live server. This guide gets you a live Cobalt server (Part 1),
optionally a yt-dlp fallback server for broader coverage (Part 2), and shows
exactly where to plug each one in (Part 3).

**Recommended path:** do Part 1 first. It alone unlocks 21 platforms
(YouTube, Instagram, Twitter, Pinterest, Vimeo, SoundCloud, and more — see
the app's own `VideoResolver.kt`/`identifyLinkType()` for the full list).
Part 2 is only worth doing once Part 1 is live and you want even broader
coverage than Cobalt supports.

---

## Part 1 — Self-hosting Cobalt (do this first)

### 1.1 Get a free server (Oracle Cloud Always Free tier)

1. Sign up at oracle.com/cloud/free — requires a card for identity
   verification, you will not be charged unless you explicitly upgrade.
2. Create a Compute instance:
   - Shape: **Ampere A1 (ARM)** — this is the free-forever tier.
   - Image: **Ubuntu 22.04** or later.
   - If you get an "out of capacity" error, try a different region — this is
     a known, common Oracle free-tier issue, not something wrong with your
     account.
3. Note the VM's public IP address once it's running.
4. Open the firewall: in Oracle's console, add an **Ingress Rule** to the
   VM's security list allowing TCP port `9000` (Cobalt's default port) from
   `0.0.0.0/0`. Also run this on the VM itself (Ubuntu's own firewall blocks
   by default too):
   ```bash
   sudo iptables -I INPUT -p tcp --dport 9000 -j ACCEPT
   sudo netfilter-persistent save
   ```

### 1.2 Install Docker and run Cobalt

SSH into the VM, then:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# log out and back in for the group change to take effect

mkdir -p ~/cobalt && cd ~/cobalt
cat > docker-compose.yml << 'EOF'
services:
  cobalt-api:
    image: ghcr.io/imputnet/cobalt:10
    restart: unless-stopped
    container_name: cobalt-api
    init: true
    ports:
      - 9000:9000
    environment:
      API_URL: "http://<YOUR_VM_PUBLIC_IP>:9000/"
      CORS_WILDCARD: "0"
      CORS_URL: "https://your-actual-app-domain.com"
    labels:
      - com.centurylinklabs.watchtower.scope=cobalt
EOF
docker compose up -d
```

Replace `<YOUR_VM_PUBLIC_IP>` and `CORS_URL` with your real values.
**`CORS_URL` matters** — it's what lets your web app's browser-side calls
actually reach this server; without the right value here you'll get a CORS
error identical to the tikwm one already documented in the web app's
`CHANGELOG.md`.

Test it's alive:
```bash
curl http://localhost:9000/
```
You should get back a JSON response describing the Cobalt instance.

### 1.3 Add cookies for Instagram/YouTube reliability (do this — it's not optional in practice)

Cobalt can access public content without login for some platforms, but
Instagram and YouTube increasingly require a real session even for public
posts. Without this step, expect intermittent failures specifically on those
two platforms even with everything else configured correctly.

1. On a browser where you're logged into Instagram/YouTube, export cookies
   using a browser extension (e.g. "Get cookies.txt LOCALLY" for Chrome/Firefox).
2. Save the exported file as `cookies.json` in `~/cobalt/` on the VM, in the
   format Cobalt expects — see Cobalt's own docs
   (`docs/run-an-instance.md` in the imputnet/cobalt GitHub repo) for the
   exact schema, since this can change between Cobalt versions.
3. Mount it into the container by adding this to `docker-compose.yml` under
   `cobalt-api`:
   ```yaml
       volumes:
         - ./cookies.json:/cookies.json
     environment:
       COOKIE_PATH: "/cookies.json"
   ```
4. `docker compose up -d` again to apply.

**Use a secondary/throwaway account for this, not your primary one** —
automated access patterns can occasionally trigger platform security checks.

### 1.4 Put it behind your existing Cloudflare domain (recommended)

Instead of exposing the raw VM IP:

1. Cloudflare dashboard → your domain → DNS → add an `A` record:
   `cobalt.yourdomain.online` → your VM's public IP.
2. Turn the proxy ("orange cloud") **on** — this gets you free TLS and hides
   the real IP.
3. Your Cobalt instance URL is now `https://cobalt.yourdomain.online`
   instead of `http://VM_IP:9000`.

---

## Part 2 — The yt-dlp fallback service (optional, broader coverage)

Cobalt covers 21 platforms well. If you want coverage closer to yt-dlp's
~1800 supported sites, run this too as an additional fallback in the chain.

**Important correction from earlier discussion**: a naive version of this
(redirect-only, just handing back yt-dlp's extracted URL) breaks on YouTube
specifically, because YouTube's CDN URLs are cryptographically signed to the
IP that requested them — the server gets the URL, but the browser downloading
it has a different IP, so YouTube's CDN rejects it with a 403. The code below
fixes this: it tunnels the actual video bytes through the server for
platforms that need it, and redirects directly for everything else.

### 2.1 The corrected service code

`requirements.txt`:
```
fastapi
uvicorn[standard]
yt-dlp
httpx
```

`main.py`:
```python
import json
from urllib.parse import quote
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse
import yt_dlp
import httpx

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# Platforms/CDNs known to sign URLs to the requesting IP -- these need the
# tunnel path, not a direct redirect.
IP_LOCKED_HOSTS = ["googlevideo.com"]

def needs_tunnel(direct_url: str) -> bool:
    return direct_url is None or any(host in direct_url for host in IP_LOCKED_HOSTS)

def quality_to_format(video_quality: str, download_mode: str) -> str:
    if download_mode == "audio":
        return "bestaudio/best"
    if video_quality in ("max", "best"):
        return "best"
    return f"best[height<={video_quality}]/best"

@app.post("/resolve")
async def resolve(request: Request):
    body = await request.json()
    url = body.get("url")
    video_quality = body.get("videoQuality", "1080")
    download_mode = body.get("downloadMode", "auto")

    if not url:
        return JSONResponse({"error": "Missing url"}, status_code=400)

    fmt = quality_to_format(video_quality, download_mode)
    ydl_opts = {"format": fmt, "quiet": True, "noplaylist": True}

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
    except Exception as e:
        return JSONResponse({"error": str(e)}, status_code=502)

    direct_url = info.get("url")
    ext = info.get("ext", "mp4")
    title = info.get("title", "Video")
    filesize = info.get("filesize") or info.get("filesize_approx")
    format_id = info.get("format_id", "best")

    if needs_tunnel(direct_url):
        # Route through our own /stream endpoint -- the server proxies the
        # bytes, so the IP requesting the CDN URL matches the IP that
        # extracted it (this server), not the end user's browser.
        format_url = f"/stream?url={quote(url)}&format_id={quote(format_id)}"
    else:
        format_url = direct_url

    return {
        "title": title,
        "thumbnail": info.get("thumbnail", ""),
        "formats": [{
            "directUrl": format_url,
            "note": f"{video_quality} ({ext})",
            "ext": ext,
            "sizeBytes": filesize
        }]
    }

@app.get("/stream")
def stream(url: str, format_id: str = "best"):
    """Proxies the actual video bytes through this server -- used for
    IP-locked sources (YouTube) where handing back a raw URL doesn't work."""
    ydl_opts = {"format": format_id, "quiet": True, "noplaylist": True}
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)
        direct_url = info.get("url")

    def iterfile():
        with httpx.stream("GET", direct_url, timeout=None) as r:
            for chunk in r.iter_bytes(chunk_size=65536):
                yield chunk

    return StreamingResponse(iterfile(), media_type="video/mp4")
```

**A real cost/behavior trade-off to understand**: the `/stream` tunnel path
means video bytes pass through your server, which uses your hosting
provider's bandwidth allowance (this is the same "egress cost" trade-off
discussed earlier for Cloudflare Containers — it applies here too,
regardless of host). The `/resolve` redirect path for non-IP-locked
platforms costs almost nothing. Realistically: most of your traffic (all the
Cobalt-covered platforms, handled by Part 1) never touches this at all; this
service is specifically for the long tail beyond Cobalt's 21 platforms, plus
a redundant path for YouTube if Cobalt's own YouTube access is temporarily
blocked (a known, ongoing issue — see the app's `CHANGELOG.md`).

### 2.2 Deploy it

**Render (free tier, sleeps after 15 min idle, ~30-60s cold start):**
1. Push the two files above to a new GitHub repo.
2. Render.com → New → Web Service → connect the repo.
3. Build command: (leave default, Render auto-detects `requirements.txt`)
4. Start command: `uvicorn main:app --host 0.0.0.0 --port 10000`
5. **Add a build step for ffmpeg** (needed for merging separate audio/video
   streams) — under "Build Command" use:
   ```
   apt-get update && apt-get install -y ffmpeg && pip install -r requirements.txt
   ```
6. Deploy on the Free tier.

**Or: same Oracle VM as Part 1** (no cold starts, still free):
```bash
sudo apt-get install -y python3-pip ffmpeg
pip3 install fastapi "uvicorn[standard]" yt-dlp httpx --break-system-packages
# copy main.py to the VM, then:
python3 -m uvicorn main:app --host 0.0.0.0 --port 8000 &
```
Add a Cloudflare DNS record for this too (e.g. `ytdlp.yourdomain.online`),
same pattern as 1.4.

---

## Part 3 — Wiring both into the app

### Android

Settings screen already has both fields:
- **Desktop Sync connection** → point this at the yt-dlp service if you're
  using it as your primary (it already expects a `/api/info` shape close to
  the yt-dlp service's `/resolve` — you may need to add an `/api/info` alias
  route in `main.py` matching the exact field names `DesktopServerResolver`
  in `VideoResolver.kt` expects: `title`, `thumbnail`, `formats[].formatId`,
  not `directUrl` — check `DesktopServerResolver.resolve()` for the exact
  shape before wiring this one).
- **Cobalt Instance URL** → point this at your Part 1 Cobalt server. This
  one's shape already matches exactly, no changes needed.

**To add the yt-dlp service as its own proper resolver in the chain** (recommended
over reusing the Desktop Sync field, since it's semantically a different thing):
add a new `Resolver` implementation in `VideoResolver.kt` (there's a clear
extension point documented in the file itself — see the class's own comment
"To add a new backend... create a new Resolver implementation, add it to
`chainFor()`"), following the same shape as `CobaltResolver` but posting to
`/resolve` on your yt-dlp service instead.

### Web

- **Settings tab** → Cobalt Instance URL field, same as before — point it at
  your Part 1 server. Works immediately, no code changes.
- **Cloudflare Pages env var** (this is the one that matters most — it's what
  makes resolution invisible/automatic for real users): Cloudflare dashboard
  → your Pages project → Settings → Environment variables → add
  `COBALT_INSTANCE_URL` = `https://cobalt.yourdomain.online`, for both
  Production and Preview. This is read by `functions/api/analyze.js`.
- **To add the yt-dlp service to the web resolver chain**: same pattern,
  add a new named resolver to `RESOLVER_CHAINS` in `app.js` AND the matching
  chain construction in `functions/api/analyze.js` (both files need the same
  new resolver — they're deliberately parallel implementations).

---

## Part 4 — Notes for Antigravity specifically

- Both `CHANGELOG.md` files (Android and web project roots) document
  everything Claude has changed this cycle, tagged `[Claude / Sonnet]` — read
  those before touching resolver/download code, so nothing gets silently
  reverted.
- **Unresolved from last handoff**: the Android project in this zip
  (`apex-downloader-android/`) was built entirely from scratch by Claude and
  is a different codebase from whatever produced the earlier
  `apex-downloader-pro.apk` (which had `downloadM3u8`/`downloadParallel`
  methods that don't exist in this version). These two need to be reconciled
  — decide which is authoritative, or merge deliberately. Don't assume this
  zip's Android code already includes Antigravity's own prior work.
- **Also unresolved**: whether Android gets a native in-app video player
  built from scratch, or stays on external "Open With" apps (web has an
  in-app player, Android doesn't, as of this handoff).
- **Neither project has been through a real compiler.** Web has been checked
  with an actual headless browser (real, not simulated). Android has only
  been statically verified (brace balance, cross-referenced function calls)
  — a real Gradle build should be the very first thing done with this code,
  not assumed to already pass.

---

## Part 5 — Antigravity Change Log & Setup Status

### 5.1 Project Folder Consolidation & Merging
- Consolidated all separate workspace iterations (`apex-web-downloader`, `hk-downloader-pro-redesigned-web`, `hk-downloader-pro-complete`) into a clean, unified project directory at `C:\Users\H.A.R\Desktop\AI PROJECTS\HK-Downloader-Pro`.
- Clean folder layout:
  - `web/`: Node.js Express server + PWA frontend + Cloudflare Pages functions (`functions/api/analyze.js`).
  - `android/`: Native Kotlin/Jetpack Compose app.
- Updated `server.js` static directory binding (`express.static(__dirname)`), syntax-checked with Node.js.

### 5.2 Resolution Architecture Status (For Hassan & Future AI sessions)
1. **Cobalt API Integration**:
   - **Web (Cloudflare Pages)**: `functions/api/analyze.js` automatically calls `COBALT_INSTANCE_URL` (set as an environment variable in Cloudflare dashboard). Every website visitor gets automatic multi-platform resolution without configuring anything in their browser.
   - **Web (Client fallback & Settings)**: `app.js` allows setting a custom Cobalt URL in Settings for power users/local testing.
   - **Android**: `VideoResolver.kt` includes `CobaltResolver` which sends standard JSON payload to the configured Cobalt endpoint.
2. **yt-dlp Integration**:
   - **Node.js Local / Oracle VM Server**: `server.js` spawns `yt-dlp` python process directly to extract metadata & stream video/audio format tokens.
   - **FastAPI yt-dlp Microservice** (as documented in Part 2 of this guide): Ready to deploy on Oracle Cloud / Render with `/resolve` and `/stream` proxy routes to handle IP-locked sources (e.g. YouTube).

### 5.3 What Needs to be Done Next
### 5.4 Free Alternative: Hugging Face Spaces Deployment (No Credit Card Required)
If Oracle Cloud sign-up fails or declines your credit card, deploy to Hugging Face Spaces for 100% free Docker hosting (16GB RAM / 2 vCPU):
1. Sign up at [huggingface.co](https://huggingface.co).
2. Click **New Space** → Name it `hk-cobalt-api` → Select **Docker** (Blank).
3. Set visibility to **Public**.
4. Create a `Dockerfile` with the following content (also saved at `huggingface-cobalt/Dockerfile` in this repo):
   ```dockerfile
   FROM ghcr.io/imputnet/cobalt:10
   ENV PORT=7860
   ENV CORS_WILDCARD=1
   ENV API_URL="https://<YOUR_HF_USERNAME>-hk-cobalt-api.hf.space/"
   EXPOSE 7860
   ```
5. Click **Commit Dockerfile**. Hugging Face builds and launches your live API endpoint at `https://<YOUR_HF_USERNAME>-hk-cobalt-api.hf.space/`.
6. Add `COBALT_INSTANCE_URL = https://<YOUR_HF_USERNAME>-hk-cobalt-api.hf.space/` to Cloudflare Pages Environment Variables!


