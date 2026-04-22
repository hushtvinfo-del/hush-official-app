# HushTV — Native Android TV APK

## Original problem statement

Build a native Android TV APK based on the React TV interface (`src/pages/tv/`) in
`https://github.com/hushtvinfo-del/hush-official-app`. Reproduce the Netflix-style
dark UI, D-pad navigation, Xtream Codes login flow, Live/Movies/Series browsing,
and HLS/MP4 video playback natively using Kotlin + ExoPlayer.

Constraint: the React web repo must **not** be modified.

## User choices (explicit)

* **Delivery:** Option 1 — full native Kotlin rewrite → debug-signed APK
* **Language:** Kotlin
* **minSdk:** 24 (Android 7.0)
* **Xtream server:** Hardcoded (`https://hushvipnew.ink:443` — taken from `TVAddAccount.jsx`);
  users only enter username + password at login.

## Architecture (native app)

* **Location:** `/app/androidtv/` (separate project; `/app/hushtv-repo/` is untouched)
* **Stack:** Kotlin 1.9.24 · AGP 8.5.2 · Gradle 8.7 · Jetpack Compose (compiler 1.5.14) · Navigation-Compose 2.8.1 · Media3/ExoPlayer 1.4.1 · OkHttp · Moshi · Coil
* **Package:** `com.hushtv.tv` (debug-suffixed to `com.hushtv.tv.debug`)
* **Screens (mirrors React `src/pages/tv/`):**
  * `TVHomeScreen` ← TVHome.jsx
  * `TVAddAccountScreen` ← TVAddAccount.jsx
  * `TVMainMenuScreen` ← TVMainMenu.jsx
  * `TVBrowseScreen` ← TVBrowse.jsx
  * `TVSeriesDetailScreen` ← (series detail path from TVBrowse)
  * `TVPlayerScreen` ← TVPlayer.jsx
* **API layer:** `XtreamApi.kt` talks **directly** to `/player_api.php` (no proxy needed on native — CORS only applies in browsers).
* **Storage:** `PlaylistStore.kt` → SharedPreferences (replaces web's `localStorage['playlists']`).
* **Focus:** `Modifier.tvFocusable()` — Compose `focusable()` + cyan 3dp outline + `scale(1.06)` + cyan glow shadow.
* **TV launcher:** `LEANBACK_LAUNCHER` + `LAUNCHER` intent filters, 320×180 vector banner, adaptive icon, landscape locked.

## What's been implemented (Apr 22, 2026)

* Full Kotlin native Android TV app, all 6 screens wired with navigation.
* Xtream Codes auth/categories/streams/series-info calls, with malformed-JSON repair logic matching the Deno `xtreamProxy`.
* HLS + MP4 + TS playback via Media3 ExoPlayer; ±10 s D-pad seeking, volume, play/pause, back.
* Debug APK successfully built for the first time (22 MB) and signed (v2 APK signature verified).
* APK emitted to `/app/dist/HushTV-android-tv-v1.0.0-debug.apk`.
* Build system workaround for aarch64 hosts: qemu-x86_64 wrapper for Google's x86_64-only `aapt2`, configured via `android.aapt2FromMavenOverride`.

### Public distribution — Apr 22, 2026
* APK SCP'd to user's dedicated server `66.163.113.147` (Ubuntu 24.04).
* nginx installed and configured to serve:
  * `http://66.163.113.147/` → HushTV-branded landing page with install instructions for Android TV (via the Downloader app) and a direct-download button.
  * `http://66.163.113.147/hushtv.apk` → APK with `Content-Type: application/vnd.android.package-archive` + `Content-Disposition: attachment; filename="HushTV.apk"` so Android devices trigger a proper download.
  * `http://66.163.113.147/tv` → 302 → `/`.
* nginx site file at `/etc/nginx/sites-available/hushtv`, APK served from `/var/www/hushtv/HushTV.apk`.
* End-to-end verified: external `curl` download MD5 matches the source APK byte-for-byte.
* **Security debt:** root password for that server was shared in plain chat — user should rotate immediately and switch to SSH keys.

## Verification done

* `aapt2 dump badging` — confirms package name, version, minSdk 24, targetSdk 34,
  INTERNET permission, banner, leanback-launchable-activity + launchable-activity.
* `apksigner verify --verbose` — signature v2 valid, 1 signer.
* DEX inspection — 9 dex files present with compiled Compose/Kotlin code.

## Not done / future

* **No runtime test on real Android TV hardware** — requires physical TV or emulator to sideload. The APK is structurally valid and installable; behaviour identical to the React reference unless the Xtream server is down.
* **Favorites**: screen placeholder only ("coming soon") — the React code also had no storage logic for favorites yet.
* **EPG** (TV guide overlay) — not in the React reference.
* **Release signing** — APK is debug-signed; needs a production keystore + `signingConfig` for Play Store / release distribution.
* **Launcher PNG fallback** for mipmap-xhdpi / mipmap-xxhdpi — current icon uses vector drawable in `mipmap-anydpi`, which works API 21+ but some launchers prefer PNG.

## Prioritised backlog

* **P1** Launch once on a real Android TV (or emulator) + test Xtream login with a real account.
* **P1** Add a proper release `signingConfig` + keystore, produce release APK.
* **P2** Implement Favorites (persist to SharedPreferences same shape as React would).
* **P2** Continue watching / resume position for VOD.
* **P2** EPG timeline for Live TV (call `/xmltv.php`, render as overlay).
* **P3** Parental PIN, per-profile settings, multi-language.
