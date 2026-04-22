# HushTV Android TV — Product Requirements Document

## Original problem statement

I have a React web app on GitHub that includes an Android TV interface. I need a
**native Android TV APK** based on it. Users log in with Xtream Codes credentials
and stream IPTV content (Live TV, Movies, Series) using ExoPlayer natively.
Navigation must work with a D-pad remote (arrow keys + ENTER). The UI must be a
dark, Netflix-style, full-screen TV interface with large cards and focus
highlights. Target: Android TV only (not phone/tablet).

**Follow-up 1**: Host the APK on a dedicated Ubuntu server and let users download
it via a public link. Hardcode the Xtream host URL (`https://hushvipnew.ink:443`).

**Follow-up 2 (current)**: Ship a full **UI/layout redesign** per the design-spec
document at `https://play.hushtvwebplayer.com/design-spec` without touching any
backend logic, API calls, auth, streaming, subtitle, EPG, favorites, or data
layer.

## Users

- End user watching IPTV on an Android TV / Fire TV box / Chromecast-with-Google-TV
- One-time sign-in per device, then zero-friction viewing

## Key constraints

- Pure native Android TV (Kotlin + Jetpack Compose, Media3/ExoPlayer)
- D-pad only — no touch gestures
- TV safe zone (96 dp horizontal, 27 dp vertical)
- All D-pad-focusable elements must render the spec-compliant focus state
  (2 dp cyan border + rgba(6,182,212,0.15) fill + scale 1.06 + glow shadow)
- Xtream host is hardcoded — users only supply username / password / nickname

---

## Architecture

```
/app/androidtv/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/hushtv/tv/
│   │   ├── MainActivity.kt
│   │   ├── HushTVApp.kt
│   │   ├── data/
│   │   │   ├── XtreamApi.kt          (Xtream Codes HTTP client)
│   │   │   ├── Models.kt
│   │   │   ├── PlaylistStore.kt      (SharedPrefs)
│   │   │   ├── FavoritesStore.kt
│   │   │   ├── LastChannelStore.kt
│   │   │   ├── PinStore.kt
│   │   │   ├── ReminderStore.kt
│   │   │   ├── NavState.kt
│   │   │   └── EpgService.kt
│   │   ├── notifications/
│   │   │   └── EpgReminderScheduler.kt
│   │   ├── update/
│   │   │   ├── UpdateManager.kt      (OTA via /version.json)
│   │   │   └── UpdateDialog.kt
│   │   └── ui/
│   │       ├── TvComponents.kt       (tvFocusable, HushTVLogo)
│   │       ├── HushSplashScreen.kt   (cinematic splash)
│   │       ├── theme/
│   │       │   ├── Colors.kt         (design-spec tokens)
│   │       │   ├── Typography.kt     (Inter scale)
│   │       │   └── Theme.kt
│   │       ├── player/
│   │       │   ├── AspectMode.kt
│   │       │   ├── PlayerOptionsMenu.kt
│   │       │   └── PinDialog.kt
│   │       └── screens/
│   │           ├── TVHomeScreen.kt         (account picker)
│   │           ├── TVAddAccountScreen.kt   (cinema-grade login)
│   │           ├── TVMainMenuScreen.kt     (Netflix-style home hub)
│   │           ├── TVBrowseScreen.kt       (movies/series/search)
│   │           ├── TVLiveBrowseScreen.kt   (Tivimate-style live TV)
│   │           ├── TVEpgGridScreen.kt
│   │           ├── TVSeriesDetailScreen.kt
│   │           ├── TVPlayerScreen.kt
│   │           ├── TVSettingsScreen.kt     (parental controls)
│   │           └── ClickWithEnter.kt
│   └── res/
│       ├── font/inter.ttf                  (variable font, 400-900)
│       ├── drawable-xxxhdpi/
│       │   ├── ic_launcher_foreground.png  (wordmark, 432×432)
│       │   └── tv_banner.png               (wordmark, 1280×720)
│       ├── mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
│       │   ├── ic_launcher.png
│       │   └── ic_launcher_round.png
│       ├── mipmap-anydpi-v26/
│       │   ├── ic_launcher.xml             (adaptive)
│       │   └── ic_launcher_round.xml
│       └── values/{colors,splash,themes,strings}.xml
└── build.gradle.kts
```

Deployment: `sshpass scp app-debug.apk root@66.163.113.147:/var/www/hushtv/HushTV.apk`
OTA: users get an in-app update dialog when `/version.json` reports a newer
`versionCode` than the installed build.

---

## Implementation history

### Phase 1 — MVP (completed)
- Scaffolded Android project, 5 screens, ExoPlayer playback, Xtream API client
- Deployed to `https://hushtv.xyz` (Nginx + Let's Encrypt)
- In-app OTA updater with changelog

### Phase 2 — Tivimate parity (completed)
- Auto-resume to last-watched channel on cold start
- Tivimate-style category sidebar + channel list + mini preview
- Full EPG grid, Favorites (★) category, Info overlay, Number dial
- Player Options menu: Audio/Subtitle picker, Aspect ratio, Sleep timer
- Parental PIN scaffolding (store, dialog, settings screen)

### Phase 3 — UI redesign v1.0.8 (2026-04-22 — completed)
Zero backend changes. Full visual system rebuilt per design-spec:

- **Branding**: Embedded Inter variable font. New launcher icon + TV banner
  rendered with real Inter Black wordmark ("hush" white + "tv." cyan on pure
  black) using `rsvg-convert`.
- **Design tokens** (`theme/Colors.kt`): `BgBlack` #000, `SurfaceNavy` #0F172A,
  `SurfaceElev` #1E293B, `Cyan` #06B6D4 + `CyanFocusBg` / `CyanRing` / `CyanGlow08`
  variants, `TextPrimary` / `TextSecondary` / `TextMuted` / `TextDim` scale.
- **Typography** (`theme/Typography.kt`): Inter 400/500/600/700/900 across
  displayLarge → labelSmall.
- **Focus system** (`TvComponents.tvFocusable`): 2 dp cyan border + cyan-tinted
  fill + 20 dp elevation glow + 1.06× scale spring animation on focus. All
  callsites keep their existing `shape` param.
- **Cinematic splash** (`HushSplashScreen`): pure-black hold → logo fade + scale
  (600 ms FastOutSlowIn) → "tv." offset 150 ms → cyan 2 dp progress bar sweep
  (1200 ms linear) → tagline fade → fade-to-black to Home.
- **Login** (`TVAddAccountScreen`): centered 720 dp card on pure black, 64 dp
  cyan-glow inputs, step-indicator dots, black-on-cyan Connect button, shake
  animation on error, success → green check + 800 ms hold.
- **Account picker** (`TVHomeScreen`): pure black with soft cyan radial glow,
  64 sp logo, 88 dp account cards, chevron → menu hub.
- **Home Hub** (`TVMainMenuScreen`, ~700 lines): unified Netflix-style canvas
  with sticky top nav (Home / Live TV / Movies / Series / Search / Settings),
  500 dp auto-rotating hero billboard (3 static slides), Live Now row (16:9
  cards + red LIVE badge + cyan progress), Continue Watching (LastChannelStore),
  Trending This Week (ghost rank numbers), New Movies, Featured Series, and
  dynamic genre rows — all fed by real Xtream data.

Released as **1.0.8 / versionCode 9**, pushed to `https://hushtv.xyz` with OTA
auto-update enabled.

### Phase 4 — VOD metadata + performance (1.1.x → 1.3.1 — completed)
- TMDB + RPDB integration for rich Movie/Series detail pages (posters,
  backdrops, cast, trailers, recommendations, ratings).
- Gemini 2.5 Flash AI Search ("find me movies based on true stories").
- Performance: removed `Modifier.blur()`, tuned Coil caching.
- Live TV: sticky sidebar FocusRequester token → fixed "wrong category on back".
- Movie detail: Play/My-List/Trailer moved into info column (no poster cut-off),
  Back button snaps scroll to top.
- OTA updater stall fix.

### Phase 5 — v1.3.2 (2026-04-22 — completed, deployed)
- **Cast filmography fix** (`TVMovieDetailScreen.kt`): clicking an actor now
  reliably matches messy Xtream titles (`[EN] VIP | Den of Thieves (2018)`)
  against clean TMDB cast filmography via a heavy `normaliseTitle()` regex
  (strips brackets, lang prefixes like `US | `, quality tags `4K/HD/FHD`,
  trailing years, and collapses whitespace) + substring/word-boundary matcher.
  Bug that shipped to 1.3.1: `libByExact[t]?.let { addIfNew(it); continue }`
  fails to compile — `continue` isn't legal inside a `let` lambda. Replaced
  with an `if (exactHit != null) { addIfNew(exactHit); continue }` block.
- **AI Search removed** from Movies/Series sidebars per user — quality wasn't
  there yet. Deleted `GeminiService.kt`, removed `GEMINI*` keys from `ApiKeys.kt`,
  stripped `CAT_AI` state + sidebar row + search box + empty-state branches +
  `VodSidebar` AI params from `TVBrowseScreen.kt`. Imports pruned.
- Shipped as versionCode=25 / versionName="1.3.2" — APK uploaded to
  `/var/www/hushtv/HushTV.apk` (23,395,348 bytes), `version.json` bumped.

### Phase 6 — v1.3.3 auto-login / no-back-to-picker (2026-04-22 — completed, deployed)
User feedback: "Every time I click back it goes to the profile picker. It
should only go to the picker if I manually press the Profile button, and it
should auto-log me into my profile on app start."

- **New store** `LastProfileStore.kt` (SharedPreferences, `hushtv_prefs` →
  `last_profile_id`) with save / load / clear / loadValid helpers.
- **Dynamic start destination** (`MainActivity.AppContent`): `remember {}`
  block resolves `LastProfileStore.load()` → if the saved profile still exists
  in `PlaylistStore`, NavHost starts at `menu/{id}` directly. No flash of the
  picker. First-run users still land on `home` as before.
- **TVHomeScreen.AccountCard.onClick**: saves the picked profile as last used,
  then navigates with `popUpTo("home") { inclusive = true }` so the picker
  never sits in the back stack.
- **TVAddAccountScreen**: after `PlaylistStore.add()`, saves the newly-created
  profile as last used and navigates to its menu with the same popUp clause.
- **TVMainMenuScreen**: the Profile button in the left sidebar is now the
  ONLY route back to the picker — swapped `nav.popBackStack()` for
  `nav.navigate("home")`.
- **Live auto-resume** still works: the existing `LastChannelStore` flow in
  `MainActivity` now only fires when the saved channel belongs to the active
  profile (or no profile is saved — legacy behaviour).
- Shipped as versionCode=26 / versionName="1.3.3" — APK (23,395,344 bytes)
  and version.json both live on `https://hushtv.xyz`.

### Phase 7 — v1.3.4 D-pad grid focus fix (2026-04-22 — completed, deployed)
User feedback: "Something is seriously wrong with the scrolling using the d
pad in movies and series — when you scroll over right then down right etc
its scrolling in the wrong direction and not accurate."

Root cause: stock `LazyVerticalGrid` + layout shifts above the grid. Applied
the official Google 2026 TV guidance (Create scrollable layouts for TV):

- **New helper** `ui/tv/FocusedItemPivot.kt` exposes
  `PositionFocusedItemInLazyLayout(parentFraction = 0.3f)` — a
  `BringIntoViewSpec` wrapper that pins the focused row ~30% from the top
  of the viewport, replacing the deprecated `TvLazyVerticalGrid.pivotOffsets`.
- **TVBrowseScreen grid** is now wrapped with the pivot helper. D-pad
  RIGHT/DOWN/LEFT now moves one step deterministically instead of snapping
  to wherever the next lazy-composed item happens to land.
- **Fixed Crossfade height** above the grid to a stable `220.dp` so focus
  moving from the sidebar into the first row no longer shifts the grid down
  by 160 dp mid-transition.
- **Grid container now uses `weight(1f)`** inside the right-pane Column, so
  the grid claims a bounded vertical space (previously it fell back to
  intrinsic sizing, which breaks bring-into-view math).
- **CompactPoster scale** (1.05× on focus) moved to the end of the modifier
  chain — applied AFTER `.focusable()` so layout bounds stay stable. The old
  order nudged focus bounds and caused drift on dense grids.
- Shipped as versionCode=27 / versionName="1.3.4" — APK (23,411,732 bytes)
  live on `https://hushtv.xyz`.

---

## Backlog

### P0 — waiting on user feedback
- Validate v1.3.2 on the user's actual TV: open a Movie detail, click any
  cast member, confirm their other titles from YOUR Xtream library now show up
  (the "zero movies found" bug from 1.3.1)
- Any visual deltas vs the design-spec page

### P1 — outstanding
- **Wire up Series cast click parity**: `TVSeriesDetailScreen.kt` →
  `SeriesCastCard` currently has `.clickableWithEnter { }` (empty). Mirror the
  movie flow: on click, call `TmdbService.personCredits`, run TV-side hits
  through `matchLibraryByTitles()`, open a results grid/dialog.
- EPG reminder "Set reminder" action wired into `TVEpgGridScreen.kt`
  (stores already exist: `ReminderStore`, `EpgReminderScheduler`)
- Picture-in-Picture support (enable `supportsPictureInPicture`, add HOME-key
  listener in `TVPlayerScreen.kt`)
- Stream Catch-up / Archive (Xtream `tv_archive=1` → timeshift URL)

### P2 — nice-to-haves
- Real backdrop art for the Hero billboard (fetch poster URL per curated title)
- Split `TVPlayerScreen.kt` overlays into separate composable files
- Search history / recent searches
- Multiple account quick-switch from the profile chip
- Re-evaluate AI Search once we have a better prompt + library-matching
  strategy (removed in v1.3.2; keys + service file deleted)

---

## Deployment runbook

```bash
# 1. Build
cd /app/androidtv && ./gradlew assembleDebug

# 2. Upload APK (lowercase URL, uppercase filename on disk)
sshpass -p '<password>' scp app/build/outputs/apk/debug/app-debug.apk \
    root@66.163.113.147:/var/www/hushtv/HushTV.apk

# 3. Bump /var/www/hushtv/version.json — increment versionCode + versionName
```

Users on HushTV 1.0.x auto-receive the update dialog ~3 s after launch.
