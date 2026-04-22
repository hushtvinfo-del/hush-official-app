# HushTV Android TV — Native APK

Kotlin + Jetpack Compose + Media3/ExoPlayer native Android TV app, built as a 1:1 port of the React TV interface in `src/pages/tv/` from the web repo.

## Output

* **APK:** `dist/HushTV-android-tv-v1.0.0-debug.apk` (22 MB, debug-signed)
* **Package:** `com.hushtv.tv.debug`
* **Version:** 1.0.0-debug (versionCode 1)
* **minSdk:** 24 (Android 7.0) — widest Android TV coverage
* **targetSdk:** 34 (Android 14)

## What's included

* All 5 screens from `src/pages/tv/` ported to Kotlin/Compose:
  * `TVHomeScreen` — saved accounts list + "Add Account" tile
  * `TVAddAccountScreen` — username + password login (host pre-configured)
  * `TVMainMenuScreen` — Live TV / Movies / Series / Favorites / Search grid, account expiry badge
  * `TVBrowseScreen` — category rows of poster cards, integrated search
  * `TVSeriesDetailScreen` — seasons/episodes list
  * `TVPlayerScreen` — ExoPlayer with play/pause, ±10s seek, volume, D-pad back
* Xtream Codes API client (`XtreamApi.kt`) — calls `/player_api.php` directly (no proxy needed on native)
* Account persistence via SharedPreferences (`PlaylistStore.kt`)
* D-pad focus handling — cyan outline + scale-up + glow shadow, matching the `.tv-card` / `.tv-focus` CSS in the web code
* `LEANBACK_LAUNCHER` + `LAUNCHER` intent filters → appears on both Android TV home row **and** regular Android launchers (sideloadable on phones)
* Hardcoded Xtream host: `https://hushvipnew.ink:443` (same as `HUSH_HOST` in `TVAddAccount.jsx`)

## Install on Android TV

**Via ADB (from a computer on the same network):**

```bash
adb connect <TV-IP-ADDRESS>:5555
adb install dist/HushTV-android-tv-v1.0.0-debug.apk
```

**Via USB stick / Downloader app:**

1. Enable "Unknown sources" under Settings → Device → Developer options
2. Copy the APK to a USB stick (or host it and open with [Downloader](https://play.google.com/store/apps/details?id=com.esaba.downloader))
3. Select the APK and tap **Install**

Launch "HushTV" from the Apps row on your TV home screen.

## Project structure

```
androidtv/
├── app/
│   ├── build.gradle.kts          (AGP 8.5, Compose 1.5, Media3 1.4)
│   └── src/main/
│       ├── AndroidManifest.xml   (LEANBACK_LAUNCHER + LAUNCHER)
│       ├── kotlin/com/hushtv/tv/
│       │   ├── MainActivity.kt
│       │   ├── data/             (XtreamApi, Models, PlaylistStore)
│       │   └── ui/
│       │       ├── TvComponents.kt   (tvFocusable modifier, HushTVLogo)
│       │       ├── theme/Theme.kt
│       │       └── screens/          (the 6 screens)
│       └── res/                  (TV banner, adaptive icon, themes)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradle/wrapper/
```

## Rebuild from source

Requirements: JDK 17, Android SDK (platforms;android-34, build-tools;34.0.0)

```bash
cd androidtv
./gradlew assembleDebug
# APK is at app/build/outputs/apk/debug/app-debug.apk
```

On Linux **aarch64** hosts (e.g. Apple Silicon containers), the Google-shipped `aapt2` native binary is x86_64-only. Install `qemu-user-static`, `libc6:amd64`, wrap `aapt2` with a qemu shim, and set `android.aapt2FromMavenOverride=/path/to/wrapper/aapt2` in `gradle.properties`. (Already configured in this project.)

## Key differences from the React web app

| Aspect | Web (`src/pages/tv/`) | Native APK |
|--------|-----------------------|------------|
| Xtream API calls | Routed through Base44 `xtreamProxy` Deno function (to bypass browser CORS) | Called **directly** from the device — no proxy needed |
| Video playback | Video.js + HLS.js in browser | **Media3 ExoPlayer** (hardware-accelerated, HLS + MP4 + MKV) |
| Navigation | `react-router-dom` | `androidx.navigation:navigation-compose` |
| Focus system | CSS `:focus` + manual `onKeyDown` arrow handlers | Compose `Modifier.focusable()` + `onFocusChanged` + `focusRequester` (system-level D-pad navigation) |
| Storage | `localStorage` | `SharedPreferences` (Moshi-serialised) |

Visual parity with the React UI is preserved (Inter font, cyan `#06B6D4` accent, radial navy→black gradient, "hush" + "tv." wordmark, 3dp cyan focus outline with scale-up and glow).

## Upstream React repo

This APK is built from — but **does not modify** — the React source at:
`https://github.com/hushtvinfo-del/hush-official-app`
(cloned into `/app/hushtv-repo` for reference only, untouched.)
