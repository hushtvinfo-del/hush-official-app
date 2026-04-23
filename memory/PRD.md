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

### Phase 27 — v1.12.3 Movie Collections page (2026-04-23 — completed, deployed)
User asked for a new Home page showcasing the top 20 most iconic movie
box-sets / franchises. Clicking one should open a dedicated results
screen that only lists that franchise's movies from the user's Xtream
library, sorted CHRONOLOGICALLY (oldest → newest release year).

Files added:
- `ui/screens/home/CollectionsData.kt`: `MovieCollection` data class
  (id, displayName, tagline, tmdbCollectionId, accent, backdropUrl)
  + hand-curated list of 20 iconic franchises (Star Wars, Harry Potter,
  Avengers, Fast & Furious, LOTR, John Wick, Mission: Impossible,
  James Bond, Jurassic Park, Terminator, Matrix, Pirates, Back to the
  Future, Godfather, Indiana Jones, Rocky, Hobbit, Toy Story, Shrek,
  Bourne). Each entry has a one-line tagline and an accent colour
  tuned to the franchise. `rememberMovieCollections()` composable uses
  the same cache-first + background-refresh pattern as Genres/Years.
- `ui/screens/home/HomeCollectionsRow.kt`: `LazyRow` of 260 × 156 dp
  landscape tiles. Each tile: full-bleed TMDB backdrop, dark vertical
  veil so the franchise name is always crisp, accent-tinted focus glow,
  franchise name in 20 sp Inter Black at bottom-left, "FRANCHISE" chip
  top-right.
- `ui/screens/home/HomeCollectionsHeroLayer.kt`: full-bleed hero with
  `AnimatedContent` 700 ms crossfade between franchise backdrops.
  Ken-Burns scale pulse (1.06 → 1.12, 22 s). Left-column copy: accent
  "FRANCHISE" eyebrow + massive 52 sp franchise name + two-line
  tagline + "WATCH IN ORDER" chip signalling chronological sort.
- `ui/screens/TVCollectionDetailScreen.kt`: dedicated screen that
  fetches the collection parts from TMDB in parallel with the user's
  Xtream movie library, matches titles via aggressive normalisation
  (lowercase, strips `[TAG]`, lang prefixes, quality tags, trailing
  years, "the", and collapses non-alphanumerics) against the user's
  library, and renders a 6-column grid of posters IN CHRONOLOGICAL
  ORDER. Matched entries play through the normal movie detail flow;
  unmatched entries render as LOCKED posters with a year chip +
  TMDB poster fallback so the user can see what's missing from their
  plan. Includes the unified Top Nav bar for consistency.

Files changed:
- `data/TmdbService.kt`: added `TmdbCollectionDetail`, `TmdbCollectionPart`
  data classes; added `backdropsForCollections(collectionIds)` to
  resolve hero backdrops in parallel; added `getCollectionParts(id)`
  that fetches + sorts a single collection's parts chronologically.
- `data/DiscoveryCache.kt`: added `saveCollectionBackdrop` /
  `loadCollectionBackdrop` keyed by `collection:$id` for cold-start
  paints.
- `TVMainMenuScreen.kt`: added `firstCollectionsFocus` requester,
  extended `pageOrder` with `"collections"` between `discovery` and
  `ss_movies`, added `rememberMovieCollections()` state +
  `focusedCollection` var, added `"collections"` branch to
  AnimatedContent + LaunchedEffect focus handoff + Nav-Down target
  map + indicator label "COLLECT". New private `CollectionsPage`
  helper composable at the file bottom wires the hero + row + click
  routing to the new `collection/...` route.
- `MainActivity.kt`: added `collection/{playlistId}/{collectionId}/{name}`
  route bound to `TVCollectionDetailScreen`.

Page flow: Discovery → Down → Collections → Down → SS Movies → … .
Up reverses. Channel Up/Down walks the whole list.

- Shipped as versionCode=99 / versionName="1.12.3" — APK (md5
  `9bcd1d9ee44abe4b1c0d355c4f7c1889`) live on `https://hushtv.xyz`.

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

### Phase 26 — v1.12.2 Movies by Release Year page (2026-04-23 — completed, deployed)
User asked for a new Home page: "Movies by Release Year" with 3 year
buckets (2026/2025/2024) that deep-link into `MOVIES - YYYY` Xtream
categories. Each card should have a year-appropriate backdrop.

Files added:
- `ui/screens/home/YearsData.kt`: `MovieYear` data class with
  `searchKeyword` matching the Xtream category name EXACTLY (`MOVIES -
  2026` etc), plus accent/gradient palette and short tagline per year.
  `rememberMovieYears()` composable uses cache-first + background
  refresh pattern (same as Discovery/Genres).
- `ui/screens/home/HomeYearsRow.kt`: since there are only 3 cards, I
  used LARGER 280 × 170 dp landscape tiles (vs 210 dp for genres).
  Massive 48 sp year label at bottom-left, "MOVIES" tag chip
  top-right, full-bleed TMDB backdrop, bottom-to-top dark veil,
  accent-tinted focus glow.
- `ui/screens/home/HomeYearsHeroLayer.kt`: full-bleed backdrop with
  700 ms crossfade + Ken-Burns scale, "RELEASE YEAR" eyebrow + big
  "Movies · 2026" title + tagline.

Files changed:
- `data/TmdbService.kt`: added `backdropsForYears(years)` — runs
  `/discover/movie?primary_release_year=YYYY&sort_by=popularity.desc`
  for each year in parallel, returns `{year → w1280 URL}`.
- `data/DiscoveryCache.kt`: added `saveYearBackdrop` /
  `loadYearBackdrop` keyed by `year:$yr`.
- `TVMainMenuScreen.kt`: added `firstYearsMoviesFocus`, added
  `"years_movies"` to `pageOrder`, `rememberMovieYears()` state +
  `focusedMovieYear` var, `"years_movies"` branch in AnimatedContent,
  focus-handoff LaunchedEffect update, nav-down target map update,
  indicator label `"YEARS"`, and private `YearsPage` helper at the
  bottom that wires the hero + row + click routing to
  `browse/$playlistId/movie?category=$encoded`.

Page flow: Genres Series → Down → Years Movies (last page). Up
reverses. Channel Up/Down walks the whole list.

- Shipped as versionCode=98 / versionName="1.12.2" — APK (md5
  `7c20ef3a8356b29b5c9aac7bdad7f445`) live on `https://hushtv.xyz`.

### Phase 25 — v1.12.1 Genres pages (Movies + Series) (2026-04-23 — completed, deployed)
User asked for two new Home pages that list traditional genre buckets
with beautiful per-genre backdrops + deep-link to matching Xtream
categories. Exact movie/series lists provided in the request.

Files added:
- `data/TmdbService.kt`: new `backdropsForGenres(kind, genreIds)` that
  runs `/discover/{movie|tv}?with_genres={id}&sort_by=popularity.desc`
  for each genre ID IN PARALLEL via `async` + `awaitAll`, returns
  `{genreId → w1280 URL}` for genres where TMDB found a backdrop.
- `data/DiscoveryCache.kt`: added `saveGenreBackdrop` /
  `loadGenreBackdrop` keyed by `genre:$kind:$tmdbGenreId` so cold start
  paints each card instantly from cache, fresh fetches happen in
  background.
- `ui/screens/home/GenresData.kt`: `Genre` data class + hand-curated
  `MOVIE_GENRES_BASE` (20 entries) + `SERIES_GENRES_BASE` (13 entries).
  Each genre gets an accent palette (`gradientTop`/`gradientBottom`/
  `accent`) tuned to the mood of the genre, a short evocative tagline,
  and its corresponding `tmdbGenreId` (0 for genres with no TMDB
  equivalent like "Standup" / "Sitcom"). `searchKeyword` field matches
  the Xtream category name exactly so deep-links resolve cleanly.
- `ui/screens/home/HomeGenresRow.kt`: `LazyRow` of 210×118 dp landscape
  cards. Each card: backdrop image filling the card (or genre gradient
  as fallback), bottom-to-top dark veil so the label is always crisp,
  radial accent wash on focus, genre name in all-caps Inter-Black at
  bottom-left. Genre-accent focus border (2.5 dp + shadow glow).
  Column-level `onPreviewKeyEvent` handles Up/Down for pager paging.
- `ui/screens/home/HomeGenresHeroLayer.kt`: full-bleed hero with
  `AnimatedContent` 700 ms crossfade between backdrops. Ken-Burns scale
  pulse (1.06 → 1.12, 22 s, no translation). Left 58% text column with
  52 sp genre name + single-line tagline + accent-dotted "OPEN CATEGORY"
  chip. Fallback to `gradientTop → gradientBottom` linear + radial
  accent glow if TMDB didn't return anything.

Files changed:
- `TVMainMenuScreen.kt`: added `firstGenresMoviesFocus` /
  `firstGenresSeriesFocus` requesters; extended `pageOrder` with
  `"genres_movies"`, `"genres_series"`; added `rememberGenres("movie")`
  + `rememberGenres("series")` state and focused genre state; added
  two branches to the AnimatedContent `when`; updated
  `LaunchedEffect(currentPage)` focus handoff; updated nav-down target
  map; updated indicator labels to include `G·MOV` / `G·SER`; added
  `GenresPage` private composable at file bottom that composes
  `HomeGenresHeroLayer` + `HomeGenresRow` with proper callbacks.

D-pad flow: SS Series → Down → Genres Movies → Down → Genres Series
(last page, no Down). Up reverses. Channel Up/Down shortcut still
walks the whole list.

- Shipped as versionCode=97 / versionName="1.12.1" — APK (md5
  `5d8911d9f565c66b3e485aee9bb10270`) live on `https://hushtv.xyz`.

### Phase 24 — v1.12.0 Unified top nav across browse screens (2026-04-23 — completed, deployed)
User: "Bring the same top-nav + page indicator + hero treatment to the
Movies and Series browse screens — yes, unifying them would give the
whole app one cohesive Netflix-grade design language."

Surgical minimum-viable change: drop the existing `TopNavBar` overlay
onto `TVBrowseScreen` (which powers Movies, Series, Live TV, and Search
via the `type` param). The category sidebar stays untouched (it's a
content filter, not navigation), the grid stays untouched, ONLY the
top nav is added so the primary navigation is consistent with Home.

- `TVBrowseScreen.kt`:
  - Added `padding(top = 72.dp)` to the main `Row` containing the
    category sidebar + content grid so they start below the nav.
  - Added an `Icons.Default.Tv` import (was missing for the Live TV tab
    icon).
  - Inserted the `TopNavBar` overlay at `Alignment.TopStart` of the
    root Box, right before it closes. Tabs mirror the Home tabs: Home →
    `menu/$playlistId`, Live TV / Movies / Series / Search → their own
    `browse/$playlistId/{kind}` routes.
  - `activeKey` is derived from `type` (`live` / `series` / `search` /
    default `movies`) so the underline highlights the current screen.
  - Nav handler guards against re-navigating to the current screen and
    uses `popUpTo("menu/$playlistId") { inclusive = false }` +
    `launchSingleTop = true` so back-stack stays sensible when users
    hop between Movies ↔ Series ↔ Live.
  - Settings gear jumps to `settings/$playlistId` same as Home.
- Shipped as versionCode=96 / versionName="1.12.0" — APK (md5
  `3c88fc879cc0370e1b37f53ae1b07847`) live on `https://hushtv.xyz`.

### Phase 23 — v1.11.3 Custom SS logos + layout cleanup (2026-04-23 — completed, deployed)
User supplied 7 exact logo URLs (AMC+, Apple TV+, CRAVE/STARZ, Disney+,
Netflix, Paramount+, Prime Video) and asked for consistent sizing +
names outside the cards.

- `StreamingServicesData.kt`: added `CUSTOM_LOGO_URLS` map, changed
  `rememberStreamingServices` to apply overrides synchronously on first
  composition (no more TMDB async wait). Coil prefetch warms disk cache
  so first paint is flicker-free. Removed unused imports / stale
  `mutableStateOf` / `getValue`/`setValue`.
- `HomeStreamingServicesRow.kt` → `ServiceCardView`: gutted the internal
  text + wordmark fallback. Card is now a pure 196 × 118 dp
  logo-on-gradient tile. Every logo renders inside an IDENTICAL
  140 × 74 dp box with `ContentScale.Fit` — guarantees every tile
  looks the same visual size regardless of native aspect ratio. Service
  name moved UNDERNEATH the card (outer Column, 10 dp gap) so there's
  zero chance of text / logo overlap. Labels brighten to white on focus.
- Shipped as versionCode=84 / versionName="1.11.3" — APK (md5
  `9071f3194af88a51aa163d6f530ab6aa`) live on `https://hushtv.xyz`.

### Phase 22 — v1.11.2 Page indicator + Channel shortcuts (2026-04-23 — completed, deployed)
User: "Page quick-jump dots pinned to the right edge + Channel Up/Down
shortcuts → YES."

Added:
- `HomePageIndicator.kt` — new composable rendering a vertical stack of
  dots at `Alignment.CenterEnd`, 20 dp end-padding. Inactive dots are
  10 dp translucent-white circles; the active dot expands to an 88 dp
  cyan pill revealing the page label (WATCHING / DISCOVER / MOVIES /
  SERIES) via `animateDpAsState`. Chevron-up glyph above stack when
  there's a page above; chevron-down glyph below when there's one
  below. Discoverability cue without clutter.
- `TVMainMenuScreen.kt`: Hoisted `continueHandle` / `continueEntries` /
  `hasCw` / `currentPage` / `pageOrder` above the root Box so the root
  Box's `onPreviewKeyEvent` can read + mutate `currentPage`. The root
  handler intercepts `Key.ChannelUp`, `Key.ChannelDown`, `Key.PageUp`,
  `Key.PageDown` and walks the `pageOrder` list up or down by one
  page. Works from ANY focus position (nav tab, hero content, etc.).
  Added `indicatorPages` list mapping page keys to display labels and
  rendered the indicator as a sibling Box to the pager + nav.

- Shipped as versionCode=83 / versionName="1.11.2" — APK (md5
  `2e9a6f450cd92a60e8ef80a315b77c4b`) live on `https://hushtv.xyz`.

### Phase 21 — v1.11.0 Streaming Services section (2026-04-23 — completed, deployed)
User asked for a new "Streaming Services" section on Home with two
sub-pages: Streaming Services (Movies) and Streaming Services (Series),
each with 7 brand-coloured tiles (AMC+, Apple TV+, CRAVE/STARZ, Disney+,
Netflix, Paramount+, Prime Video). User decisions:
- Click action: deep-link to Xtream browse screen filtered by a category
  containing that service's name (existing `initialCategoryName`
  contains-match behaviour).
- Logos: fetched live from TMDB's `/watch/providers/movie` + `/tv`
  endpoints (no bundled assets).
- Structure: separate full-screen pages for each.

Files added:
- `data/TmdbService.kt`: new `watchProviderLogos(kind): Map<Int,String>`
  helper — single call to `/watch/providers/{kind}?language=en-US&watch_region=US`,
  returns a stable `provider_id → w154 logo URL` map. In-memory cache so
  subsequent lookups for 7 providers cost at most TWO HTTP hits (one per
  kind).
- `ui/screens/home/StreamingServicesData.kt`: `StreamingService` data
  class + hand-curated palette for the 7 services (brandTop/Bottom,
  accent, searchKeyword, tmdbProviderId). `rememberStreamingServices(kind)`
  composable renders the base list immediately (logoUrl=null for instant
  paint) then enriches with TMDB URLs via `LaunchedEffect`. Warms Coil
  disk cache so logos appear flicker-free.
- `ui/screens/home/HomeStreamingServicesRow.kt`: `LazyRow` of 180×220 dp
  portrait tiles. Each tile: vertical brand gradient + radial accent
  glow + centered `AsyncImage` for the TMDB logo (with wordmark fallback
  while loading) + service name label. Column-level `onPreviewKeyEvent`
  handles Up/Down for pager paging.
- `ui/screens/home/HomeStreamingServicesHeroLayer.kt`: full-bleed hero
  with `AnimatedContent` crossfade (900 ms) between service palettes.
  Diagonal brand-gradient backdrop + bottom-right radial accent glow +
  slow-pulsing translucent watermark logo in the right half. Left
  40% darken veil keeps the title/tagline/badge column crisp.

Files changed:
- `TVMainMenuScreen.kt`: pager upgraded from 2 states
  (CW/Discovery) to 4 pages (`"cw" | "discovery" | "ss_movies" |
  "ss_series"`). `pageOrder` list drives AnimatedContent slide direction
  via `indexOf()` comparison. Added `firstSsMoviesFocus` + `firstSsSeriesFocus`
  requesters. Nav-Down switch statement targets the right requester per
  page. Extracted page composables (`CwPage`, `DiscoveryPage`, `SsPage`)
  to the bottom of the file for readability. Fixed stale
  `showCwPage` → `currentPage`.
- `HomeDiscoveryRow.kt`: added optional `onDownFromRow` param (wires Down
  from Discovery to SS Movies page).

**DEPLOYMENT NOTE — infrastructure**: this job pod was FRESH (no Android
SDK, no JDK, no sshpass installed from previous sessions). Had to:
1. `apt-get install openjdk-17-jdk-headless`
2. Download cmdline-tools, `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
3. Pod runs on aarch64 but Google's build-tools ships x86_64 aapt2 —
   installed `qemu-user-static` + `libc6:amd64 libstdc++6:amd64 zlib1g:amd64`
   and created a shell wrapper at `/opt/aapt2-wrapper/aapt2` that
   execs `qemu-x86_64-static /opt/android-sdk/build-tools/34.0.0/aapt2`.
4. `apt-get install sshpass` for OTA deploy.
Future fork jobs may need these steps if the pod is reset again.

- Shipped as versionCode=81 / versionName="1.11.0" — APK (md5
  `17bed24e6d3b0d5439f6c5d1c5abcae1`) live on `https://hushtv.xyz`.

### Phase 20 — v1.10.5 Static top nav (no more auto-hide) (2026-04-23 — completed, deployed)
User feedback: "When you go back up to the menu it's breaking the image
just like the previous left menu collapse one was. The obvious fix here
and long term solution is to just make it stay static at the top and not
disappear."

- **Removed auto-hide entirely** (`TVMainMenuScreen.kt`): deleted
  `navVisible` state + the `animateDpAsState heroTopOffset` + the
  `AnimatedVisibility` wrapper around the nav + the `onFocusChanged`
  on the nav wrapper. The nav is now rendered directly inside a
  static `Box` at `Alignment.TopStart`, always visible.
- **Hero+content permanently offset 72 dp**: the hero+content container
  Box uses `padding(top = 72.dp)` (constant — no animation). The hero
  sits perfectly below the nav at all times, so the backdrop never
  shifts when the user D-pads between the nav and the content.
- **Focus handoff preserved**: Down from nav still focuses the first
  card; Up from first card still refocuses Home via `showNavAndFocus`
  (simplified to just call `topNavHomeFocus.requestFocus()` with no
  visibility flag to toggle). The nav was always there, so no visual
  break.
- Shipped as versionCode=78 / versionName="1.10.5" — APK (md5
  `ddbce52ca429af059ca0ce472f74af7a`) live on `https://hushtv.xyz`.

### Phase 19 — v1.10.4 Continue Watching + Discovery as separate pages (2026-04-23 — completed, deployed)
User feedback: "The continue watching section and card is overlapping
and in the way of the whole discovery background — each section needs
its own screen so nothing overlaps the other background."

Previous version stacked both sections in a Column sharing the same
hero backdrop — which caused the CW card to sit on top of the
Discovery poster wall and the Discovery titles to bleed through behind
the CW row. Fixed by turning them into two independent full-screen
pages with smooth vertical paging between them.

- **AnimatedContent pager** (`TVMainMenuScreen.kt`): replaced the
  Column-of-rows layout with an `AnimatedContent` pager keyed on a
  `currentPage` state ("cw" or "discovery"). Default = "cw" if CW
  entries exist, else "discovery". Transition spec: 280 ms
  `slideInVertically` + `fadeIn` for the entering page
  (`togetherWith`) `slideOutVertically` + `fadeOut` for the outgoing
  one — direction sign flips based on whether we're going down
  (CW → Discovery) or back up.
- **Each page is self-contained**: inside each `AnimatedContent` branch
  we render a fresh `Box(Modifier.fillMaxSize())` that hosts ONLY its
  own hero layer + its own card row. The CW page uses `HomeHeroLayer`
  + `HomeContinueWatchingRow`; the Discovery page uses
  `HomeDiscoveryHeroLayer` + `HomeDiscoveryRow`. Zero cross-pollination
  of backdrops or focusables.
- **Inter-page D-pad plumbing**:
  - `HomeContinueWatchingRow` gained a new optional
    `onDownFromRow: (() -> Unit)?` param — attached on the Column-level
    `onPreviewKeyEvent` so Down from ANY CW card triggers the slide.
    From `TVMainMenuScreen` we wire it to `currentPage = "discovery"`.
  - `HomeDiscoveryRow.onUpFromFirstItem` now flips `currentPage = "cw"`
    when CW has entries; otherwise pops the nav back in.
- **Auto-focus on page change** (`TVMainMenuScreen`): a
  `LaunchedEffect(currentPage)` with a 320 ms `delay` (lets
  `AnimatedContent` compose the new page + attach its
  `FocusRequester`) calls `firstCwFocus.requestFocus()` or
  `firstDiscoveryFocus.requestFocus()` as appropriate. Without this
  the focus would stay on the old page's (now-disposed) card and
  keyboard nav would break after a transition.
- **Reactive cleanup**: if the user long-press removes their last CW
  entry while on the CW page, a `LaunchedEffect(hasCw)` auto-bounces
  them to the Discovery page so they're never stranded on an empty
  page.
- Shipped as versionCode=77 / versionName="1.10.4" — APK (md5
  `b4e055e61b4f2a9ee1b782c3f382bafe`) live on `https://hushtv.xyz`.

### Phase 18 — v1.10.3 Always-on Discovery + stacked CW (2026-04-23 — completed, deployed)
User feedback: "Where is the Discovery section — it should be under
Continue Watching section. Discovery will always be the main top default
section of main home, but Continue Watching will show above it if there
are movies/series in progress."

- **Both sections now visible** (`TVMainMenuScreen.kt`): replaced the
  old `if (CW) {...} else if (Discovery) {...}` branch with a Column
  pinned `Alignment.BottomStart` containing `HomeContinueWatchingRow`
  (rendered only when `continueEntries.isNotEmpty()`) stacked above
  `HomeDiscoveryRow` (rendered whenever `discoveryCards.isNotEmpty()`,
  which is always true after the TMDB fetch). Discovery is now the
  permanent bottom anchor with CW floating above when present.
- **Smart hero backdrop**: new `heroSection` state ("cw" | "discovery")
  defaults to "discovery". `HomeContinueWatchingRow`'s
  `onFocusedEntryChange` now also flips `heroSection = "cw"`; likewise
  `HomeDiscoveryRow`'s `onFocusedCardChange` flips it to "discovery".
  The layered hero draws `HomeHeroLayer` only when both
  `heroSection == "cw"` AND `heroEntry != null`; otherwise falls back
  to `HomeDiscoveryHeroLayer` so the default state on launch is the
  Discovery rotation (user's explicit preference).
- **Two focus requesters** (renamed `firstCardFocus` → `firstCwFocus`
  + added `firstDiscoveryFocus`): nav's `onPreviewKeyEvent` picks the
  right target based on `navDownTarget` ("cw" if CW has entries, else
  "discovery"). `HomeDiscoveryRow`'s `onUpFromFirstItem` callback now
  routes to `firstCwFocus.requestFocus()` when CW exists, otherwise
  pops the nav back in. CW row's Up-from-first still pops the nav.
- Default Compose 2D focus traversal handles Down between CW → Discovery
  (nearest focusable below) — no explicit handoff needed there.
- Shipped as versionCode=76 / versionName="1.10.3" — APK (md5
  `449923735945c65777c13ce4e9060f19`) live on `https://hushtv.xyz`.

### Phase 17 — v1.10.2 Hero adapts below nav + D-pad Down fix (2026-04-23 — completed, deployed)
User feedback: "The background is still behind the top menu — it needs
to adapt below it and not interfere. Also you can't even scroll down from
the top menu to the main section; it's stuck within the top menu only."

TWO fixes:

1. **Hero adapts below nav** (`TVMainMenuScreen.kt`): wrapped the hero
   layer + interactive content layer in a shared Box that has an
   ANIMATED top padding tied to `navVisible`. When the nav is visible:
   `padding(top = 72.dp)` → hero starts right below the nav container,
   no overlap. When the nav auto-hides: padding animates to 0 → hero
   expands up to fill the full viewport. The animation (`animateDpAsState`,
   220 ms tween) matches the nav's own slide-out, so the two move in
   sync — looks like one unified motion. Backdrop never passes behind
   the nav, never interferes with it, never gets cut off.

2. **D-pad DOWN out of the nav was stuck**
   (`HomeContinueWatchingSection.kt`, `HomeDiscoveryRow.kt`): the bug
   was that the `focusRequester` modifier was placed AFTER the
   `.focusable()` in the chain via `base.then(Modifier.focusRequester(fr))`.
   In Compose, `focusRequester` attaches to the NEXT focusable in the
   chain — anything placed after it. If you chain `.focusable()
   .focusRequester(fr)`, the requester has no focusable to bind to, so
   `fr.requestFocus()` silently no-ops. Moved the requester to the TOP
   of the modifier chain via `if (focusRequester != null)
   Modifier.focusRequester(fr) else Modifier`, then chained the rest
   off that. Now `firstCardFocus.requestFocus()` from the nav's
   `onPreviewKeyEvent` correctly moves focus into the first content
   card, which drops the nav visibility (via `onFocusChanged` seeing
   `hasFocus=false`), which triggers the hero to expand up.
- Shipped as versionCode=75 / versionName="1.10.2" — APK (md5
  `7088032344ff8d482cab8f410ab8525d`) live on `https://hushtv.xyz`.

### Phase 16 — v1.10.1 Top nav framed container + Profile moved to Settings (2026-04-23 — completed, deployed)
User feedback: "The buttons are over the top of the background — the top
menu needs a border like Netflix. Also I don't like the profile button —
just integrate it right into the settings section where they can log out,
add profile etc. Make sure the main screen background is adaptive to it
and doesn't interfere with it or cut off any of the background."

- **Framed nav container** (`TopNavBar.kt`): removed the old top-down
  transparent gradient treatment and replaced with a solid 72 dp
  container painted `Color(0xEB0B1220)` — deep navy at 92% alpha. Gives
  the tabs a proper Netflix-style framed zone so they're never floating
  over a bright patch of backdrop art. Added a 1 dp cyan-tinted bottom
  border (`Color(0x3306B6D4)`) drawn as a child Box aligned
  `BottomCenter` so it sits exactly on the bottom edge regardless of
  row alignment. Subtle, visible, clean separator.
- **Hero stays adaptive** (unchanged but verified): the hero backdrop
  layer still renders `Modifier.fillMaxSize()` edge-to-edge BEHIND the
  nav container thanks to the Box paint order (hero composed first →
  painted underneath; nav composed last → painted on top). No clipping,
  no `padding(top = 72.dp)` on the hero — the whole art shows through
  behind the nav's 92% alpha veil.
- **Profile chip removed** (`TopNavBar.kt`): dropped the `ProfileChip`
  composable + its `profileNickname` / `onProfile` params. Nav is now
  Logo → tab rail → Settings gear only.
- **Settings screen gets PROFILE section** (`TVSettingsScreen.kt`):
  added two new `SettingsCard` entries at the top of the screen under a
  "PROFILE" label: "Switch Profile" (navigates to the profile picker
  `home` route; subtitle shows current nickname) and "Add Another
  Profile" (navigates to the `add` route). Icons: `Icons.Default.Person`
  + `Icons.Default.PersonAdd`, both cyan. Existing parental controls
  UI moved below under a new "PARENTAL CONTROLS" label.
- Shipped as versionCode=74 / versionName="1.10.1" — APK (md5
  `67330ea6d6817af11d1d8e4295291f84`) live on `https://hushtv.xyz`.

### Phase 15 — v1.10.0 Top Navigation Bar (2026-04-23 — completed, deployed)
User feedback: "The left side scroll menu is making the app too dark and
grey with the gradient etc... let's try a top menu instead like Netflix
does... put the main sections in the top — Home, Live TV, Movies, Series,
then Settings gear icon top right and Profile like Netflix does top left.
This would have to be done super smart, clean and responsive."

MAJOR REDESIGN — goodbye 140 dp left sidebar + 300 dp blend veil. Brand
new Netflix / YouTube-TV style top nav.

- **New file** `TopNavBar.kt` (~260 lines): renders a 96 dp tall top bar
  with a top-down dark-to-transparent gradient background
  (`0xB8000000 → 0x00000000`). Layout from left to right: `HushTVLogo`
  (22 sp) → `ProfileChip` (circular cyan initial avatar + nickname) →
  pill-tab rail (Home / Live TV / Movies / Series / Search) → `Spacer
  weight(1f)` → `SettingsIconButton` (circular gear). Active tab fills
  with cyan and flips text to `0xFF0A0F1C`; focused tab gets a 2 dp cyan
  ring + translucent white fill. Profile chip + settings circle both use
  the same focus-ring language for consistency. `TopNavTab` model
  hoisted public so the parent owns tab config.
- **`TVMainMenuScreen.kt` rewrite** (~170 lines net change): deleted
  the whole Sidebar overlay Box + 300 dp blend veil Box. Content layer
  went from `padding(start = 156.dp, end = 32.dp)` to
  `padding(start = 48.dp, end = 32.dp)` — the hero title column now uses
  the same 48 dp left anchor as the card row below. Hero layers' own
  `contentStartPadding` dropped from 156 dp → 80 dp so the title sits
  in a comfortable TV-safe-zone position. Added `firstCardFocus`
  `FocusRequester`, `navVisible` state, and a `showNavAndFocus` callback
  wired into both row composables.
- **Auto-hide logic**: the TopNavBar sits in a wrapper Box aligned
  `TopStart`. That wrapper has `.onFocusChanged { navVisible = it.hasFocus }`
  plus a `.onPreviewKeyEvent` that intercepts `DirectionDown` and calls
  `firstCardFocus.requestFocus()`. When focus lands on a content card →
  wrapper has no focus → navVisible flips to false → `AnimatedVisibility`
  slides + fades the nav up and out (220 ms in / 180 ms out). The first
  `ContinueCard` / `DiscoveryCardView` now accepts an optional
  `focusRequester` + `onUpKey` callback; both watch `DirectionUp` and
  fire `showNavAndFocus` to bring the nav back in + refocus the Home tab.
- **Row signature upgrades** (`HomeContinueWatchingSection.kt`,
  `HomeDiscoveryRow.kt`): added `firstItemFocus` + `onUpFromFirstItem`
  params (both optional — defaults preserve backwards compatibility).
  Switched `LazyRow items(...)` → `itemsIndexed(...)` for the Continue
  Watching row so the `idx == 0` branch can attach the focus requester.
- **Result**: the blue-tinted dark zone on the left third of the screen
  is gone. Hero backdrop is edge-to-edge bright. Top nav auto-fades
  when the user is exploring content, slides back in on Up. All on the
  same D-pad language the rest of the app uses.
- Shipped as versionCode=73 / versionName="1.10.0" — APK (md5
  `2fe6b670489e4db0c6e172cf059354f6`) live on `https://hushtv.xyz`.

### Phase 14 — v1.9.8 CRITICAL: fix player launch lag + ANR crashes (2026-04-23 — completed, deployed)
User feedback: "There is something wrong with the functionality in the
app when you click play on movies or series — it's really dragging and
lagging before it starts the player... I've seen it crashing a lot of
times and exiting the app back to the device home screen."

- **ROOT CAUSE** (`ThumbnailExtractor.kt`): the extractor's constructor
  was calling `MediaMetadataRetriever().setDataSource(url, …)` inline,
  and the extractor itself was instantiated inside `remember {}` in
  `TVPlayerScreen` — which executes on the COMPOSITION THREAD (i.e. the
  main UI thread). `setDataSource` for an HTTP URL opens the full video
  file, reads its container headers, and indexes it; on slow networks
  or large 4K VODs that takes 5–15 seconds. During that time the UI is
  frozen, and Android's watchdog fires an ANR (Application Not
  Responding) which exits the app back to the TV home screen —
  matching the user's crash symptoms exactly.
- **FIX** (`ThumbnailExtractor.kt`): made the constructor a no-op.
  Added `initBlocking(url)` method that performs the `setDataSource`
  handshake; documented that callers MUST invoke it on a background
  dispatcher. `extract()` now returns `null` silently until `ready`.
  Added `isUrlSupported(url)` static helper — HLS (.m3u8), MPEG-TS
  (.ts), and DASH (.mpd) URLs return `false` so the extractor isn't
  instantiated for formats `MediaMetadataRetriever` can't parse (it
  hangs indefinitely on those).
- **FIX** (`TVPlayerScreen.kt`): wrapped the extractor creation with
  `isUrlSupported` + `isLive` gate. Added a `LaunchedEffect` that calls
  `initBlocking()` inside `withContext(Dispatchers.IO) {}` so the
  handshake NEVER touches the composition thread. Player now launches
  instantly; scrubber thumbnails simply "warm up" a few seconds later
  in the background.
- Shipped as versionCode=72 / versionName="1.9.8" — APK (md5
  `767ded374f9a6b4ed5250096e360b4ad`) live on `https://hushtv.xyz`.

### Phase 13 — v1.9.6 Hero title one-line + card proportion tune (2026-04-23 — completed, deployed)
User feedback: "Can you make the latest movies background text say Latest
Movies all in the same line (the same way you have latest series) and
can we move the discovery cards up a bit higher — they should be
perfectly proportioned with the background layout and font text content."

- **Title single-line** (`HomeDiscoveryHeroLayer.kt` → `DiscoveryTitleBlock`):
  widened the title column from `fillMaxWidth(0.45f)` → `0.58f` and
  dropped the title font from 52 sp → 48 sp Inter Black with
  `lineHeight 52 sp`, `maxLines = 1`, ellipsis overflow. "Latest Movies"
  (the wider of the two titles) now comfortably fits on a single line
  matching the "Latest Series" rendering. Also pushed the title column
  down the hero — `top` padding 44 dp → 96 dp — so the massive title
  sits more centred against the card row below.
- **Card row raised** (`HomeDiscoveryRow.kt`): bottom padding increased
  from 24 dp → 72 dp so the two Discovery cards sit noticeably above
  the bottom screen edge. With the title block now dropped to 96 dp
  from the top, the composition reads as one balanced block instead of
  title-at-top + cards-jammed-at-bottom.
- Shipped as versionCode=70 / versionName="1.9.6" — APK (md5
  `1aa46b080c7b17cce307eedae0587436`) live on `https://hushtv.xyz`.

### Phase 12 — v1.9.5 Full-bleed backdrop + scale-only Ken-Burns (2026-04-23 — completed, deployed)
User feedback: "The Ken-Burns effect is still messing it up and stretching
the right side. Our main screen overlay should fill the whole screen to
the right and the Ken-Burns cannot interfere — it needs to be completely
adaptive."

- **Root padding removed** (`TVMainMenuScreen.kt`): the root Box had a
  leftover `padding(end = 32.dp)` (a pre-redesign TV overscan safe zone)
  which pushed ALL children — including the full-bleed hero backdrop —
  32 dp away from the right screen edge. That left the visible vertical
  strip the user screenshotted. Removed from the root Box so the hero
  reaches the actual screen edge; moved the 32 dp `end` padding onto the
  inner interactive content Box so the Browse buttons still stay inside
  the TV safe zone.
- **Ken-Burns: scale-only, no translation** (`HomeDiscoveryHeroLayer.kt`):
  dropped both `translationX` and `translationY` animators. Translation
  — even with a 1.10 base scale buffer — can momentarily shift the image
  off-centre and, combined with the crossfade into a fresh image that
  starts its own animation cycle, looked like the backdrop was
  "stretching" toward one side. Replaced with a pure centred scale pulse
  (1.06 → 1.12 over 22 s linear loop) so the image is mathematically
  centred on every frame and every edge is always covered regardless of
  viewport size. Still cinematic, but now "completely adaptive" — the
  effect cannot interfere with viewport fill.
- Shipped as versionCode=69 / versionName="1.9.5" — APK (md5
  `609cc975515656c6e65d641eeb1888b9`) live on `https://hushtv.xyz`.

### Phase 11 — v1.9.4 Edge fill fix on Ken-Burns (2026-04-23 — completed, deployed)
User feedback: "When the new background pics change there's some effect
making the background not fill the right side of the screen — need it to
just fill the whole main screen without this effect, blend seamlessly
into the right side like you did for the left."

- **Ken-Burns scale floor** (`HomeDiscoveryHeroLayer.kt`): the issue was
  that `animateFloat(initialValue = 1.0f, targetValue = 1.08f)` paired
  with ±25 px translation briefly exposed viewport edges — most visibly
  at the right edge, and worst right after a crossfade when a fresh
  image's scale animation started at 1.0 (exact viewport size, no
  overflow buffer). Changed base scale to a minimum of **1.10** (range
  1.10 → 1.18 linear 20 s loop) and reduced translation to **±12 px x,
  ±8 px y**. At 1920 px TV width, 1.10× gives ~96 px of overflow per
  side, far exceeding the 12 px drift — the image always over-fills the
  viewport, so no edge is ever exposed. The backdrop now blends
  edge-to-edge on both sides exactly like the left-menu blend.
- Shipped as versionCode=68 / versionName="1.9.4" — APK (md5
  `862be21d3a1cefd3ab7c5518c77288ff`) live on `https://hushtv.xyz`.

### Phase 10 — v1.9.3 Seam fix + instant cold start (2026-04-23 — completed, deployed)
User feedback: "There's something wrong with the way the main screen —
there's a gap between the left menu and the right background and it's
making it very ugly; the menu and main screen need to flow and blend into
one." + "Pre-fetch and cache TMDB backdrops on app startup: YES."

- **Seam fix** (`TVMainMenuScreen.kt`): the bug was that the HERO
  backdrop layer rendered inside a Box with `padding(start = 156.dp)`,
  so the backdrop image had a hard edge at x=156 dp — visible as a
  bright vertical line between the sidebar and the content. Restructured
  so the hero layers (`HomeDiscoveryHeroLayer` + `HomeHeroLayer`) now
  render EDGE-TO-EDGE, full-screen width, behind everything. State
  (continue entries, discovery cards, focused entry) was hoisted out of
  the padded Box so both the full-bleed hero and the padded card layer
  share it. The interactive card rows stay in a `Box` with
  `padding(start = 156.dp)` so they never overlap the sidebar. The blend
  veil sits on top of the full-bleed hero, producing one continuous
  Netflix-style canvas with no visible seam.
- **Hero title padding** (`HomeDiscoveryHeroLayer.kt`): added a
  `contentStartPadding` param (default 0.dp); `TVMainMenuScreen` passes
  `156.dp` so the title block is offset past the sidebar while the
  backdrop stays full-bleed.
- **Instant cold start** (`DiscoveryCache.kt`, `HomeDiscoveryData.kt`):
  new `DiscoveryCache` object — SharedPreferences cache keyed by
  `playlistId:kind:(backdrops|posters|count)`. On first composition
  `rememberDiscoveryCards` synchronously reconstructs cards from cache
  via `buildCardsFromCache` so the hero renders hi-res artwork in the
  first frame. `LaunchedEffect` still refreshes from Xtream + TMDB in
  the background and persists the fresh art. If the cache has data the
  user never sees the empty-state flash again.
- **Coil image prefetch** (`HomeDiscoveryData.kt`): after fresh data
  arrives, every backdrop URL is enqueued on `ctx.imageLoader` so the
  disk cache is warm before the 12 s rotation fires. First swap is
  flicker-free.
- Shipped as versionCode=67 / versionName="1.9.3" — APK (md5
  `ea79768f37519927b31662347ad19e36`) live on `https://hushtv.xyz`,
  `version.json` bumped.

### Phase 9 — v1.9.2 TMDB backdrops + Ken-Burns + sidebar blend (2026-04-23 — completed, deployed)
User feedback: "The background images that are rotating are very distorted,
bad quality and rotating too fast — can you use better image quality etc.
Also the left menu gradient is too much, it needs to blend way better
from left menu into the main screen."

- **Hi-res TMDB backdrops** (`TmdbService.kt`, `HomeDiscoveryData.kt`): new
  `TmdbService.backdropsForTitles(titles, kind, limit)` helper — strips
  Xtream-style noise from each title (leading `[TAG]`, country prefixes
  `EN |` / `US -`, `4K/HD/UHD/FHD/HDR/DV/SDR` quality tags, trailing
  years), hits `/search/movie` or `/search/tv`, filters hits that have a
  `backdrop_path`, picks the most popular one and returns a list of
  `w1280` landscape URLs. `rememberDiscoveryCards` now runs movie/series
  fetches in parallel via `async` + resolves the top 18 titles in each
  category for TMDB backdrops. `DiscoveryCard` gained `backdrops` field
  and a `heroArt` property that prefers TMDB backdrops, falling back to
  Xtream posters when TMDB misses. Added `backdrop_path` + `poster_path`
  to `TmdbSearchHit` so we don't need an extra `getMovie()` round trip.
- **Ken-Burns + crossfade** (`HomeDiscoveryHeroLayer.kt`): rotation slowed
  from 5 s to 12 s. Each backdrop now renders in a `KenBurnsBackdrop`
  composable — `rememberInfiniteTransition` drives a 20 s linear scale
  animation (1.0 → 1.08) plus ±25 px x-drift and ±15 px y-drift applied
  via `graphicsLayer`. Direction alternates per index so consecutive
  posters feel distinct. Crossfades between backdrops use a 1.8 s linear
  `AnimatedContent` with `fadeIn togetherWith fadeOut`. Coil requests
  are memory + disk cached to avoid re-download on every rotation tick.
- **Sidebar blend veil** (`TVMainMenuScreen.kt`): removed the sidebar's
  own horizontal gradient background (the 140 dp compressed fade was the
  source of the visible seam). Added a new 300 dp-wide blend veil Box in
  the root Box that sits BETWEEN the content layer and the sidebar,
  painted with a four-stop horizontal gradient (0xFF → 0xE6 → 0x80 → 0x00
  of `#0B1220`). The sidebar column is now fully transparent and rides
  on top of the veil, so the nav fades gently across ~300 dp into the
  content instead of dropping off at the 140 dp edge.
- Shipped as versionCode=66 / versionName="1.9.2" — APK (23,477,264 bytes,
  md5 `6abc28258e471c2ab439e3ad0f464b3f`) live on `https://hushtv.xyz`,
  `version.json` bumped.

### Phase 8 — v1.9.1 Discovery card redesign (2026-04-23 — completed, deployed)
User feedback: "Remove the movie posters from within the cards — with the
current background it's too busy; fill them better with the text etc, and
the background should fit the screen without scrolling."

- **Discovery cards** (`HomeDiscoveryRow.kt`): stripped `PosterQuad` +
  right-half poster mosaic entirely. Replaced with a poster-free,
  typography-forward tile — 360×168 dp, vertical dark gradient fill, 4 dp
  accent stripe on the left, eyebrow row (icon + label), massive
  Inter-Black 26 sp title, 2-line subtitle, and a bottom row with a
  monochrome count chip + accent-filled Browse CTA pill. Cyan accent for
  Movies, violet for Series. Focus lifts the shadow from 6 → 24 dp,
  brightens the fill gradient, thickens the accent border, and flips the
  CTA pill to the accent colour with black text.
- **Discovery hero layer** (`HomeDiscoveryHeroLayer.kt`): removed the
  tilted cascading `PosterMosaic` (3 rotated columns with y-offsets and
  `rotationZ = -6f` that visibly overflowed the viewport). Replaced with
  a single full-bleed `AsyncImage` backdrop that auto-rotates through the
  category's posters every 5 s via `LaunchedEffect` + `delay(5000)`.
  Stronger horizontal darkening veil (92% → 25% opacity) keeps the left
  45% text column legible. Bottom fade protects the pinned card row.
  Everything stays inside the viewport — zero scroll, zero overflow.
- Shipped as versionCode=65 / versionName="1.9.1" — APK (23,477,264 bytes)
  live on `https://hushtv.xyz`, `version.json` bumped.

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
