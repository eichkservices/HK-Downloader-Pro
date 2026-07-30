# HK Downloader Pro

> Premium dual-platform media downloader — Web App + Android Native App

---

## 📁 Project Structure

```
HK-Downloader-Pro/
├── web/                          ← Web App (Node.js + PWA)
│   ├── server.js                 ← Express backend (yt-dlp + axios)
│   ├── package.json
│   ├── index.html                ← PWA shell
│   ├── app.js                    ← Full frontend engine
│   ├── style.css                 ← Dark neon UI theme
│   ├── manifest.json             ← PWA manifest
│   ├── hk-downloader-pro.apk    ← Android APK served from web
│   ├── logo.jpg                  ← App logo
│   ├── CHANGELOG.md              ← Web app version history
│   └── functions/                ← Cloudflare Pages functions (optional)
├── android/                      ← Android Native App (Kotlin/Compose)
│   ├── app/
│   ├── build.gradle.kts
│   └── ...
├── BACKEND-SETUP-GUIDE.md        ← Server setup instructions
└── README.md                     ← This file
```

---

## 🚀 Running the Web App

### Requirements
- Node.js 18+
- Python 3 + yt-dlp (`pip install yt-dlp`)

### Start Server
```bash
cd web
npm install
npm start
```
Then open: **http://localhost:3000**

---

## 📱 Android App

Open the `android/` folder in **Android Studio** and build/run.

- **Min SDK**: 26 (Android 8.0)
- **Language**: Kotlin + Jetpack Compose
- **Package**: `com.example.apexdownloader`

---

## 🎨 Design System

| Token | Value |
|-------|-------|
| Background | `#090b10` |
| Primary | Orange `#ff6b00` |
| Secondary | Cyan `#00f0ff` |
| Success | Green `#00ff87` |
| Danger | Red `#ff3366` |
| Font | Outfit + JetBrains Mono |

---

## ✨ Features

### Web App
- yt-dlp powered backend (YouTube, TikTok, Instagram, Facebook, Reddit, Twitter)
- TikWM API fallback for TikTok
- Quality selector pills (Best / 1080p / 720p / 480p / Audio MP3 / Opus)
- Ambient animated background reacting to download state
- Toast notifications for success/failure
- MX Player-style in-app video player (speed, lock, double-tap ±10s, fullscreen)
- Real-time download progress with speed + ETA
- 2-column Media Library grid
- Settings tab (Cobalt instance URL)
- PWA installable (Add to Home Screen)
- APK download banner

### Android App
- Share intent URL auto-detect
- Background download service (WorkManager)
- Real-time speed tracking
- Platform resolvers (TikTok, YouTube, direct links)
- Room database for download history
- Jetpack Compose UI with Material3
