# CHANGELOG — Apex Downloader / HK Downloader Pro (Android)

This file tracks changes made by AI coding assistants working on this project,
so whichever tool picks it up next (Antigravity, Claude, or a human) knows
what changed, why, and doesn't accidentally revert a deliberate fix. Every
entry is tagged with which model made the change.

Format: newest changes at the top. Each entry states the change, the reason,
and which files it touched.

---

## [Claude / Sonnet] — Full app audit: Android findings
**What:** Systematic recheck across the whole Android project:
- Grepped for TODO/FIXME/unimplemented stubs -- none found.
- Cross-referenced every `viewModel.X()` call made from `DownloaderScreen.kt`/
  `SettingsScreen.kt` against `MainScreenViewModel.kt`'s actual functions --
  all 7 (`analyzeUrl`, `clearCompletedDownloads`, `deleteDownloads`,
  `resetAnalysis`, `saveCobaltInstanceUrl`, `saveDesktopServerUrl`,
  `triggerDownload`) exist exactly once, no undefined references.
- Full brace/paren balance sweep across every `.kt` file -- clean.
- Confirmed Android already has real quality presets (this was the reference
  implementation the web app's equivalent feature was missing and just got
  added to match -- see the web CHANGELOG's audit entry).
**Note:** this is a static review, not a compile -- I still can't build this
project in this sandbox (no Android SDK/network access to Google's Maven).
A real build + on-device test pass is still the right next step before
calling anything here bulletproof.

## [Claude / Sonnet] — Ambient glow refined to match reference more precisely
**What:** Initial version scattered the three blobs to different corners
(top, bottom, center). Re-examined the Gemini reference image and corrected:
all three blobs now anchor toward the top of the screen (mostly shifted
above the visible viewport, so only a soft glow spills down from the top
edge), plus a vertical gradient fade mask ensures it dissolves to the solid
dark background by roughly mid-screen — matching the reference's top-anchored
wash rather than glow scattered across all edges.
**Files:** `ui/main/AmbientGlowBackground.kt`

## [Claude / Sonnet] — Ambient animated glow background
**What:** Added `AmbientGlowBackground.kt` — three blurred, softly moving,
color-shifting circles rendered behind the main content (Compose equivalent
of the web app's version, itself inspired by the moving gradient background
in the Gemini app). Reacts to real state: purple while analyzing, orange
while a download is actively in progress, a brief green/red flash on
completion/failure, drifting back to the brand orange/blue/purple blend at
idle. This is a page-wide mood, not a per-download-item indicator — individual
download cards already have their own status-colored glow for that.
**Why:** Direct user request, referencing the Gemini app's background effect.
**How the state is derived:** `rememberAmbientState()` diffs the downloads
list's (id, status) pairs against the previous composition to detect new
completions/failures (so it fires once per transition, not continuously for
as long as a completed item sits in the list), using a separate
`LaunchedEffect` keyed on the flash state itself (not the downloads list) so
a progress-percentage tick on an unrelated item can't cancel an in-progress
flash timer.
**Note on a design choice:** ported the "three blobs at different animation
durations" approach from the web CSS version, but NOT the "negative
animation-delay" phase-offset trick — Compose's `tween(delayMillis)` doesn't
behave like CSS's negative delay, so that specific mechanism doesn't
translate. Differing durations alone are enough to keep the blobs organically
out of phase with each other.
**Files:** `ui/main/AmbientGlowBackground.kt` (new), `ui/main/MainScreen.kt`
(wired in — collects `analysisState`/`downloads`, renders the background as
the first child so it draws behind the actual content)

## [Claude / Sonnet] — Multi-backend resolver chain
**What:** Replaced the hardcoded if/else resolution logic (Desktop Server →
Cobalt → error) in `VideoResolver.kt` with an extensible chain-of-resolvers
architecture. Each backend (TikTok's tikwm.com API, Desktop Server, Cobalt,
direct-link, cloud storage) is now a self-contained `Resolver` implementation
tried in priority order per link type, falling through to the next on
failure. Remembers which resolver won last time for each platform type
in-memory (resets on app restart) and tries that one first next time.
**Why:** User wants resilience against any single backend being flaky —
"try most reliable first, on failure switch to another, keep going until
finished." Also added the TikTok-specific `tikwm.com` resolver to Android,
which the web app already had but Android didn't (inconsistent reliability
between platforms before this).
**Files:** `engine/VideoResolver.kt` (full rewrite, same public API —
`resolveVideoInfo()`, `identifyLinkType()`, `videoPlatformTypes` signatures
unchanged, so no other file needed to change).
**To add a new backend (e.g. a yt-dlp service):** create a new `Resolver`
implementation, add it to the relevant chain(s) in `chainFor()`. Nothing
else needs to change.

## [Claude / Sonnet] — Expanded platform detection (5 → 21 platforms)
**What:** `identifyLinkType()` and `videoPlatformTypes` previously only
recognized youtube/tiktok/reddit/facebook/instagram. Expanded to all 21
platforms Cobalt actually supports (verified against a live instance's own
service registry): added twitter/x, bilibili, bluesky, dailymotion, loom,
ok.ru, pinterest, rutube, snapchat, soundcloud, streamable, tumblr, twitch,
vimeo, vk, xiaohongshu.
**Why:** User wanted broad "any platform" support; this list is the honest,
verified ceiling of what the Cobalt-based architecture actually covers (not
literally unlimited — see conversation notes on yt-dlp as a path to broader
coverage).
**Files:** `engine/VideoResolver.kt`

## [Claude / Sonnet] — AndroidManifest share/Open-With intent-filter
**What:** Added a `SEND`/`text/plain` intent-filter to `MainActivity` so the
app actually appears in Android's share sheet when a link is shared from
another app.
**Why:** `MainActivity.kt`'s `handleIncomingIntent()` was already written to
receive shared links, but nothing told Android this app was a valid share
target — the manifest declaration was missing, so the existing code never
ran.
**Files:** `AndroidManifest.xml`

## [Claude / Sonnet] — App icon / branding: dual-chevron mark
**What:** Replaced the original arrow-into-tray adaptive icon (cyan/orange
was not yet the palette at that point) with the current two-chevron download
mark (cyan chevron over orange chevron), matching the standalone logo. Flat
vector paths only — adaptive icons can't render blur/shadow, that detail
lives in the separate raster logo files. Regenerated every legacy raster
mipmap density (mdpi–xxxhdpi, square + round) to match.
**Why:** User-provided reference image (AI-generated concept art) established
this as the target brand mark; adapted it into a real, verified, scalable
icon rather than trying to literally replicate the photorealistic reference.
**Files:** `res/drawable/ic_launcher_foreground.xml`, all
`res/mipmap-*/ic_launcher*.webp`

## [Claude / Sonnet] — Release signing + CI
**What:** Generated a real release keystore (valid to 2053), wired signing
config into `build.gradle.kts` reading from a git-ignored
`keystore.properties`, added `.github/workflows/build.yml` so GitHub Actions
builds and signs a release APK automatically on push.
**Why:** This sandbox can't run Android builds directly (no SDK/network
access to Google's Maven) — this is the path to actually getting an
installable APK without a local dev machine.
**Files:** `app/build.gradle.kts`, `keystore/`, `keystore.properties`,
`.github/workflows/build.yml`, `.gitignore`

## [Claude / Sonnet] — Download reliability: WorkManager migration
**What:** Downloads previously ran on a bare `CoroutineScope(Dispatchers.IO)`
with no lifecycle — Android's background execution limits would silently
kill them the moment the app backgrounded. Migrated to WorkManager
(`DownloadWorker`, foreground service notification) so downloads survive
backgrounding. Added `DownloadRepositoryProvider` as a process-wide singleton
— necessary because a Worker and the ViewModel each constructing their own
`DownloadRepository` would hold separate in-memory state, silently breaking
UI progress updates.
**Why:** Core reliability requirement for any download manager.
**Files:** `engine/DownloadWorker.kt` (new), `engine/DownloadExecutor.kt`
(new — actual download logic, extracted so it can run inside the Worker),
`engine/DownloadNotifications.kt` (new), `data/DownloadRepositoryProvider.kt`
(new), `engine/DownloadEngine.kt` (rewritten to a thin WorkManager façade)

## [Claude / Sonnet] — Real HTTP Range resume + `.part` file discipline
**What:** Downloads now write to a `<name>.part` file during transfer and
only rename to the final filename on success. Resume uses `Range:
bytes=N-`, with clean fallback if the server ignores it (200 instead of 206)
or the partial data is stale (416).
**Why:** Previously a half-downloaded file sat at its final filename the
whole time — indistinguishable from a complete one if touched externally.
Also: "resume" previously always restarted from zero.
**Files:** `engine/DownloadExecutor.kt`, `data/DownloadRepository.kt`
(cleanup now also deletes orphaned `.part` files)

## [Claude / Sonnet] — Fixed dead Cobalt API + missing safety guard (original bug)
**What:** The app's fallback path called Cobalt's old public API
(`api.cobalt.tools/api/json`), which was permanently shut down Nov 2024.
Combined with a missing check, this meant YouTube/TikTok/etc. links with no
working resolver configured would silently download the raw webpage HTML and
label it as a fake video (the original "1.2MB fake file" bug). Removed the
dead endpoint, added self-hosted Cobalt instance support (Settings), and
added an explicit error message instead of a silent fake download when
nothing can resolve a link.
**Why:** This was the root cause investigation that started the whole
session — confirmed via bytecode analysis of a compiled build, then fixed in
source.
**Files:** `engine/VideoResolver.kt`, `ui/screens/SettingsScreen.kt`

---
*Earlier foundational work (project structure, Compose UI, quality presets,
notifications, etc.) predates this changelog's creation — see conversation
history for full detail if needed.*
