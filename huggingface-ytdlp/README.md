---
title: HK Downloader Pro - Backend API
emoji: ⬇️
colorFrom: orange
colorTo: cyan
sdk: docker
pinned: false
license: mit
---

# HK Downloader Pro — yt-dlp Backend API

This Hugging Face Space runs the **full HK Downloader Pro backend** — a Node.js Express server with native Python `yt-dlp` + `ffmpeg` support.

## What it does
- Exposes `/api/analyze` → resolves any YouTube, TikTok, Instagram, Facebook, Twitter/X, Reddit, Vimeo and 1800+ other URLs into downloadable format lists
- Exposes `/api/download?token=TOKEN` → streams the actual video bytes to the client
- Serves the PWA web interface at `/`

## How to use with HK Downloader Pro (Cloudflare Pages)

1. **Deploy this Space** by pushing the repo with this Dockerfile to your HF account.
2. Note your Space URL: `https://<YOUR_USERNAME>-hk-downloader-api.hf.space`
3. In your **Cloudflare Pages** dashboard:
   - Go to **Settings → Environment variables**
   - Add: `YTDLP_API_URL` = `https://<YOUR_USERNAME>-hk-downloader-api.hf.space`
   - Set it for **both Production and Preview**
4. Redeploy. YouTube downloads will now work instantly at full speed!

## Running locally

```bash
cd HK-Downloader-Pro
docker build -t hk-downloader -f huggingface-ytdlp/Dockerfile .
docker run -p 7860:7860 hk-downloader
```

Then open `http://localhost:7860`
