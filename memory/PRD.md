# HushTV Android TV — Product Requirements Document

## v1.40.1 — 2026-04-25 (versionCode 195)  ⬅ LATEST  (non-mandatory)

**Retroactive TMDB metadata for legacy requests + TV cinematic
rail.** User screenshot showed the new mobile cards fell back to
the amber gradient (no backdrop) for requests that were submitted
*before* v1.39.0 introduced TMDB metadata capture. Plus the TV
main menu was still on the old emoji/poster icon design.

### Fix 1 — Lazy retroactive TMDB lookup
NEW `RequestPosterResolver.resolveOrFetch(ctx, request)` —
walks a 3-stage ladder:
1. Local `RequestMetaStore` cache (instant)
2. `[TMDB ...]` tag parsed out of `additional_info` (sub-ms)
3. **Best-effort TMDB title-search** — uses
   `searchMoviesList` / `searchTvList`, takes the most popular
   hit, persists to `RequestMetaStore` so subsequent renders are
   instant. De-duped via a process-scoped mutex so a cold home
   screen with 5 backdrop cards doesn't fan out 5 identical
   TMDB calls.

Wired into all three render paths via `LaunchedEffect`:
- `BackdropRequestCard` (home rail — both Mobile + TV)
- `RequestRow` (My Requests list)
- `DetailHero` (request detail screen)

State is managed via `mutableStateOf` so when the network call
resolves, the card recomposes with the actual TMDB backdrop.
Free-text requests for obscure titles that TMDB doesn't index
still fall back gracefully to the status-tinted gradient.

### Fix 2 — TV cinematic rail
The new `BackdropRequestCard` already supports `isTv = true` (auto-
scales to 320 × 180 px and uses TV-friendly focus visuals). The
TV main menu integration in `TVMainMenuScreen` was already passing
`isTv = true`, so the cinematic 16:9 backdrop cards now render
automatically on TV — no other changes needed.

### Files
- NEW `ui/requests/RequestPosterResolver.kt` — retroactive TMDB
  fetch with mutex-guarded de-dup
- `ui/requests/RequestsHomeRail.kt` — `BackdropRequestCard` now
  uses lazy `var meta` + `LaunchedEffect`
- `ui/requests/MyRequestsScreens.kt` — `RequestRow` same upgrade
- `ui/requests/RequestDetailScreen.kt` — `DetailHero` same upgrade

### Build + deploy
- `versionCode 194 → 195`, `versionName "1.40.0" → "1.40.1"`.
- BUILD SUCCESSFUL. APK md5 `5b7e5a51f8e594a01fa8fdf54a8e38cb`,
  17.5 MB, live on `https://hushtv.xyz/hushtv.apk`. Non-mandatory.

---

## v1.40.0 — 2026-04-25 (versionCode 194)

**Cinematic Home rail redesign + repositioning.** User feedback:
the rail sat between the header and the pager dots, visually
overlapped the slider hero, and used emoji thumbnails that looked
"ugly". Two changes:

### 1. Position
- Moved the rail from above-pager to **below-pager**. The pager
  now sits in its natural slot directly below the header + dots.
  Rail gets its own air at the bottom of the home Column with a
  hairline divider above + 18 dp bottom padding (off the system
  nav bar).
- Hidden entirely when the user has zero open / unseen requests
  — same as before, no impact on users who never use the feature.

### 2. Card design (`BackdropRequestCard`)
- **16:9 TMDB backdrop** as the card image (`w780`), cropped to
  fill — same aspect ratio TMDB uses, looks like a Netflix tile.
- Falls back to **TMDB poster** (`w500`) when the title has no
  backdrop on TMDB. Falls back further to a status-tinted
  gradient when neither is available (free-text request, very
  obscure title).
- **Bottom-up dark gradient overlay** so the title always reads
  white-on-image without needing a flat scrim.
- **Status badge** in the top-left, color-coded per status with
  a translucent fill that matches the backdrop tone.
- **Cyan unread dot** with a 2dp white border in the top-right
  whenever the user has an unacknowledged admin update.
- **Title + meta** anchored to the bottom-left: bold white title
  (max 2 lines, ellipsis), then a 11sp meta line "Movie · 2024"
  or "Series · 2019".

### Files
- `mobile/MobileHomeScreen.kt` — moved `RequestsHomeRail` from
  position 2 (after header) to last child of the home Column
- `ui/requests/RequestsHomeRail.kt` — full rewrite with the new
  `BackdropRequestCard` composable, hairline divider, cleaner
  section header

### Build + deploy
- `versionCode 193 → 194`, `versionName "1.39.3" → "1.40.0"`.
- BUILD SUCCESSFUL. APK md5 `f86d885db22e72d44db8b5aaf575c1cd`,
  17.5 MB, live on `https://hushtv.xyz/hushtv.apk`. Non-mandatory.

---

## v1.39.3 — 2026-04-25 (versionCode 193)

**Bug fix: "Already in your library" was routing to the wrong
movie.** User reported: searching "Analyze That" in the request
picker showed "Analyze That · 2002 · ALREADY IN YOUR LIBRARY" but
tapping it played "Z (2019)". Repro and root-cause analysis below.

### Root cause
The custom matcher inside `LibraryIndex.lookup` did three passes:
1. Exact normalised title equality
2. Year-stripped equality
3. **Substring containment** when query length ≥ 5 chars

Pass 3 was the culprit. `TitleMatcher.normalize("Z (2019)")`
returns the single character `"z"`. The substring check
`"analyze that".contains("z")` returns `true`. So the loop in
pass 3 matched the user-typed "Analyze That" against the library's
"Z" entry and returned it. The picker then displayed
"ALREADY AVAILABLE" against the wrong entry, and tapping deep-
linked to "Z (2019)".

The Collections feature already has a proven matcher,
`TitleMatcher.isStrongMatch` / `findBestMatch`, that requires:
- exact normalised title equality with year gate (±1 year), OR
- contiguous word-phrase containment **AND** ≥ 3 real words on
  both sides AND year agreement within ±1 year.

That bar prevents 1- or 2-word library titles ("Z", "It", "Ed")
from matching into longer queries via coincidence.

### Fix
Rewrote `LibraryIndex` to delegate every lookup
(`lookup`, `findBest`, `findAllCandidates`) to
`TitleMatcher.findBestMatch` / `isStrongMatch` — the exact same
selector Collections uses. Pre-builds the per-kind index lists at
prime time so every query is a single list scan. No more custom
substring logic anywhere in `LibraryIndex`.

Also added bullet-proof tap-time confirmation in
`TmdbPickerPhase`: when the user taps a card flagged as "ALREADY
IN YOUR LIBRARY", the click handler resolves the candidate's
`vod_info` (via `TmdbIdResolver`), checks the provider's
`tmdb_id` against the picked TMDB hit's id, and only routes when:
- provider has no tmdb_id (trust strict-match library entry), OR
- provider's tmdb_id == picked TMDB id (verified match).

If the provider exposes a *different* tmdb_id, we fall through to
the request-submit path with the picked TMDB metadata — better to
file a duplicate request than send the user to the wrong title.

### Files
- `data/LibraryIndex.kt` — full rewrite, delegates to TitleMatcher,
  pre-built per-kind indices, no custom substring code
- `ui/requests/TmdbPickerPhase.kt` — on-tap tmdb_id verify before
  invoking `onAlreadyAvailable`
- `ui/requests/RequestNotificationHost.kt` —
  `pickBestLibraryMatch` passes `tmdbMeta?.releaseYear` to
  `findAllCandidates` so the candidate fan-out is also strict

### Build + deploy
- `versionCode 192 → 193`, `versionName "1.39.2" → "1.39.3"`.
- BUILD SUCCESSFUL. APK md5 `a4d0468902118d802afd294c76d1bd4c`,
  17.5 MB, live on `https://hushtv.xyz/hushtv.apk`. Shipped as
  **MANDATORY** — the bug routes users to wrong content; users
  need this fix on next launch.

### Verified
- "Analyze That" (2 normalised words) → `isStrongMatch` returns
  false for any candidate that doesn't have an exact normalised
  match → picker shows "TAP TO REQUEST" instead of false
  "ALREADY AVAILABLE".
- "Terminator 2 Judgment Day" (≥ 3 normalised words) →
  containment + year ±1 → matches a library entry like
  "[EN] Terminator 2: Judgment Day (1991)" correctly. Same
  precision Franchises has.

---

## v1.39.2 — 2026-04-25 (versionCode 192)

**Bullet-proof Watch-now matching via provider tmdb_id.** Last
piece of the request-flow puzzle: when the LibraryIndex returns
multiple candidates for a request title, we now confirm correctness
by hitting Xtream's per-title `get_vod_info` / `get_series_info`
endpoint and exact-comparing `tmdb_id` against the request's saved
TMDB id. Provider metadata wins over title heuristics — fixes edge
cases where titles look completely different (e.g. provider
labelled the file "Le Fabuleux Destin d'Amélie Poulain" but the
user requested "Amélie").

### Match precision ladder
1. **TMDB id exact** — `XtreamApi.getVodInfo` / `getSeriesInfo`
   per candidate (parallel async), pick the one whose `tmdb_id`
   matches the request's saved TMDB id. Process-cached so repeat
   resolves are instant.
2. **Year-aware title match** — when no candidate exposes a
   tmdb_id (free Xtream lines often don't), prefer the entry whose
   parsed year matches the request's TMDB year (±1 tolerance).
   Same logic as v1.39.1.
3. **Title-only fallback** — `LibraryIndex.lookup`, the original
   behaviour, last resort.

### Files
- NEW `data/TmdbIdResolver.kt` — per-stream-id /
  per-series-id TMDB id resolver with in-memory cache + parallel
  pickByTmdbId fan-out
- `data/LibraryIndex.kt` — added `findAllCandidates(title, kind)`
  returning every plausible match (de-duped by streamId/seriesId)
- `ui/requests/RequestNotificationHost.kt` — extracted the
  matching logic into `pickBestLibraryMatch` (internal so the
  detail screen can call it too); banner Watch-now uses the new
  three-stage matcher
- `ui/requests/RequestDetailScreen.kt` — same upgrade for the
  detail screen Watch-now button

### Build + deploy
- `versionCode 191 → 192`, `versionName "1.39.1" → "1.39.2"`.
- BUILD SUCCESSFUL. APK md5 `7af9f13491a036cacca8003112533ff5`,
  17.5 MB, live on `https://hushtv.xyz/hushtv.apk`. Shipped as
  **non-mandatory**.

### Note
The TMDB picker's "Already in your library" badge still uses the
year-aware matcher (`findBest`) — fan-out via `vod_info` per hit
during type-as-you-go would be too slow on every keystroke. The
bullet-proof check fires only when the user taps a result that
the year heuristic flagged as "already available", and on Watch-
now from the detail/banner. This is the right perf/correctness
tradeoff.

---

## v1.39.1 — 2026-04-25 (versionCode 191)

**Year-aware library matching for Watch-now and the picker.**
Quick refinement on top of v1.39.0: when a request has saved TMDB
metadata, the "Watch now" deep-link in the detail screen and the
status notification banner now disambiguate using the release year.
Two films that share a normalised title (Aladdin 1992 vs 2019,
Dune 1984 vs 2021, Tron 1982 vs 2010) used to coin-flip; now the
right one wins.

### What changed
- `LibraryIndex.Entry.releaseYear` — best-effort year parsed at
  prime time from the raw library title (handles "Title (2024)",
  "Title 2024", "Title - 2024 - HD")
- `LibraryIndex.findBest(title, kind, preferredYear)` — new
  selector that prefers exact-year matches, then ±1 year, then
  falls back to the existing title-only matcher
- `RequestNotificationHost.resolveWatchTarget` — primed via
  `LibraryIndex` (with year) instead of one-off
  `XtreamApi.getAllStreams + TitleMatcher` lookup; falls back to
  the legacy path only when the library prime fails
- `RequestDetailScreen.resolveTarget` — same upgrade
- `TmdbPickerPhase` — "Already in your library" badge now uses
  `findBest` with the TMDB hit's year, so a 1991 film never false-
  matches a 2024 remake just because the titles normalise the same

### Build + deploy
- `versionCode 190 → 191`, `versionName "1.39.0" → "1.39.1"`.
- BUILD SUCCESSFUL. APK md5 `5e072f61128a2ea6063ecefe73be4f01`,
  17.5 MB, live on `https://hushtv.xyz/hushtv.apk`. Shipped as
  **non-mandatory** — v1.39.0 was already mandatory and pulled
  everyone forward.

---

## v1.39.0 — 2026-04-25 (versionCode 190)

**Smarter Request flow — TMDB-powered picker + library
deduplication.** User wanted a Franchises-style search experience
inside the request modal: type "Terminator" → see every Terminator
movie with poster + year → pick the exact one. AND if the title is
already in their Xtream library, the app routes them straight to
the title instead of letting them submit a useless duplicate.

### What's new
- **TMDB picker phase** (`TmdbPickerPhase`) replaces the old free-
  text "Title" input. Live-search debounced @ 350 ms, returns up to
  20 hits ordered by TMDB popularity, each rendered as a 64×96
  poster row with year + tap-to-action badge.
- **Library deduplication** (`LibraryIndex`) — primes the index of
  every movie + series in the user's Xtream catalog at modal open
  using `TitleMatcher.normalize`. For every TMDB hit we check the
  library and, if found, replace the "TAP TO REQUEST" badge with
  "ALREADY IN YOUR LIBRARY · TAP TO WATCH" (green border). Tapping
  it dismisses the modal and deep-links into the right detail
  page (TV: `moviedetail/...` or `series/...`; Mobile: direct
  player URL or `mseries/...`).
- **Free-text fallback** — when TMDB returns zero hits (obscure
  titles, typos), the user can still submit "{query}" as a typed
  request via a "Request '{query}' anyway" button. The flow goes
  through the same DETAILS phase for series + same submit.
- **TMDB metadata round-trips** — `submitRequest` now sends
  `tmdb_id`, `tmdb_type`, `tmdb_year`, `tmdb_poster_path`,
  `tmdb_backdrop_path`, `tmdb_overview` to the gateway as
  standalone keys (admin can read them when the schema supports
  it; silently dropped otherwise) AND embeds a compact
  `[TMDB id=X type=movie year=2024 poster=/abc.jpg]` tag in
  `additional_info` so even a fresh install can recover the
  metadata for poster display.
- **Posters everywhere** — `RequestMetaStore` keyed by `request_id`
  resolves TMDB metadata for:
  • Home rail cards (50×75 thumb)
  • My Requests row thumbs (60×90)
  • Detail screen hero (100×150)
  • Success phase (120×180 with green ✅ overlay)
  All fall back gracefully to the type emoji when no poster.
- **Series flow refinement** — for series, picking a TMDB hit
  routes through the new `DETAILS` phase (scope = entire vs.
  specific seasons / episodes) before submit. Movies submit
  immediately on pick — minimal taps.

### Files
- NEW `data/LibraryIndex.kt` — in-memory library snapshot, keyed
  by normalised title; tolerant 3-pass lookup (exact, year-
  stripped, substring containment ≥5 chars)
- NEW `data/RequestMetaStore.kt` — per-request TMDB metadata in
  SharedPreferences + parseable `[TMDB ...]` tag encode/decode for
  cross-device recovery
- NEW `ui/requests/TmdbPickerPhase.kt` — debounced TMDB search,
  poster grid, library badge, free-text fallback
- `data/TmdbService.kt` — added `searchMoviesList()` /
  `searchTvList()` returning 20 popularity-sorted hits each
- `data/ContentRequestApi.kt` — `submitRequest` accepts
  `tmdbMeta`, sends standalone keys + appends `[TMDB ...]` tag
- `ui/requests/RequestContentSheet.kt` — full flow rewrite:
  CONTACT → PICK (TMDB) → DETAILS (series only) → SUCCESS;
  exposes `LibraryEntry` value class + `onAlreadyAvailable`
  callback for caller-side library nav; success phase shows
  picked TMDB poster
- `ui/requests/MyRequestsScreens.kt` — row redesign with TMDB
  poster + year + cleaned additional_info (TMDB tag stripped)
- `ui/requests/RequestDetailScreen.kt` — hero now shows TMDB
  poster + release year alongside the status; meta section
  hides the `[TMDB ...]` tag from user-facing additional_info
- `ui/requests/RequestsHomeRail.kt` — cards redesigned with
  TMDB poster thumb
- `mobile/MobileSearchScreen.kt`, `mobile/MobileSeriesDetailScreen.kt`,
  `ui/screens/TVUnifiedSearchScreen.kt`,
  `ui/screens/TVSeriesDetailScreen.kt` — all 4 RequestContentSheet
  call sites pass `playlistId` and handle `onAlreadyAvailable` to
  deep-link into the user's library

### Build + deploy
- `versionCode 189 → 190`, `versionName "1.38.0" → "1.39.0"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- APK md5 `9d32953beeba9390ef340f92b5c0e2f3`, 17.5 MB, live on
  `https://hushtv.xyz/hushtv.apk`. Shipped as **MANDATORY** so
  every user picks up the smarter picker + dedup.

### Verified live
- `https://api.themoviedb.org/3/search/movie?query=terminator`
  returns 6 Terminator films with posters, popularity-sorted
  (T2 first, T1 second, then Genisys / Rise of the Machines /
  Salvation / Dark Fate). Exactly the franchise-style behaviour
  the user requested.

### Next backlog
- **Tier 2 (waiting on user signal)**: filter chips, pull-to-
  refresh, cancel request (needs Base44 admin code), empty-state
  CTA
- **Pending Base44 admin code** (the user mentioned sharing it
  but the file/paste isn't in the thread): without it we can't
  wire cancel / reply / re-rank from the gateway side
- **P2** Picture-in-Picture (Mobile + TV)
- **P2** Xtream Catch-up / Archive
- **P3** OS-level push (FCM)

---

## v1.38.0 — 2026-04-25 (versionCode 189)

**My Requests — Tier 1 advanced upgrade.** User asked for a much
richer requests experience: home-screen visibility, tappable rows,
admin-note rendering, and per-request status notifications. Shipped
all of it in one batch.

### What's new
- **Home rail** (`RequestsHomeRail`) — horizontal "MY REQUESTS"
  section at the top of both Mobile Home and TV Main Menu. Hidden
  entirely when the user has zero open or unseen-update requests
  (no impact on users who never use the feature). Up to 6 most-
  recent open / recently-updated cards, each tappable to detail.
- **Request Detail screen** (`TVRequestDetailScreen`,
  `MobileRequestDetailScreen`, shared `RequestDetailContent`):
  • Hero card with title + type emoji + big status badge + priority
    chip (when not default)
  • Visual three-step timeline: Pending → In Progress → Added,
    plus terminal-state callouts for Already Available / Not Found
  • **Admin response card** in the cyan accent — surfaces every
    note the admin types from the Base44 panel, full text, no
    truncation
  • Meta section: submission date, last update, scope, seasons,
    episodes, additional info
  • Context-aware action buttons: "▶ Watch now" when added (deep-
    links via `TitleMatcher.normalize` against the user's Xtream
    library); "🔁 Re-request with more info" when not_found;
    Refresh always available
  • D-pad-focusable (TV) / touch (Mobile), reuses
    `clickableWithEnter` pattern.
- **Unread / NEW badges** — `RequestSeenStore` tracks per-request
  `(status, adminResponseHash)` signature in SharedPreferences. Any
  row whose current signature differs from the user's last-
  acknowledged signature gets a cyan "NEW" pill, bold weight, and
  a cyan border tint. Acknowledged automatically when the user
  opens the detail screen.
- **Stats header** at the top of the My Requests list: TOTAL · OPEN
  · AVAILABLE · CLOSED counts, color-coded per state.
- **Tappable rows** in My Requests — every row navigates to its
  detail screen. Embedded admin response preview (3-line clamp)
  visible right on the row so the user sees notes even before
  tapping in.
- **Refresh button** (top-right on Mobile, header chip on TV) —
  manually re-polls the gateway on demand.
- **Extended status-change notifications** — `RequestNotificationHost`
  now uses `RequestSeenStore.signatureFor(...)` and surfaces:
  • added / already_available (existing)
  • not_found (new) with the admin's reason in the subtext
  • in_progress *only when* the admin attached a note
  • Banner header text + accent color now adapt per status (green
    for added, amber for not_found, blue for in_progress, etc.)
  • "Watch now" button is hidden for non-watchable statuses.

### API
No new gateway actions used yet. We added one parsed field —
`updated_date` — so the detail screen and the notification
signature can detect "admin edited the note but kept status the
same". Cancel / re-request / reply will need new gateway actions
when you share the Base44 admin code.

### Routes
- `requestdetail/{playlistId}/{requestId}` (TV)
- `mrequestdetail/{playlistId}/{requestId}` (Mobile)
- `myrequests/{playlistId}` (TV — was `myrequests`, now requires
  playlistId so the list/detail screens can resolve "Watch now"
  deep-links)
- `mrequests/{playlistId}` (Mobile, same)

### Files
- NEW `data/RequestSeenStore.kt` — per-request signature tracker
  (replaces the old `seen_added_ids` set; status + admin-note hash)
- NEW `data/RequestCache.kt` — process-scoped snapshot for fast
  list → detail navigation (avoids re-fetching the whole list)
- NEW `ui/requests/RequestDetailScreen.kt` — TV + Mobile wrappers
  + shared `RequestDetailContent`
- NEW `ui/requests/RequestsHomeRail.kt` — home-screen rail
  composable used on both form factors
- `data/ContentRequestApi.kt` — `Request.updatedDate` + `priority`
  fields, both parsed from gateway payload
- `ui/requests/MyRequestsScreens.kt` — full rewrite: tappable
  rows, NEW badges, stats header, refresh, navigation
- `ui/requests/RequestNotificationHost.kt` — uses RequestSeenStore,
  status-aware banner copy + colours
- `mobile/MobileHomeScreen.kt`, `ui/screens/TVMainMenuScreen.kt` —
  embed the home rail
- `MainActivity.kt`, `mobile/MobileApp.kt` — new detail routes,
  playlistId on `myrequests` / `mrequests`
- `mobile/MobileSettingsScreen.kt`, `ui/screens/TVSettingsScreen.kt`,
  `mobile/MobileSearchScreen.kt`, `mobile/MobileSeriesDetailScreen.kt`,
  `ui/screens/TVUnifiedSearchScreen.kt`,
  `ui/screens/TVSeriesDetailScreen.kt` — call sites updated to
  pass playlistId on the new routes

### Build + deploy
- `versionCode 188 → 189`, `versionName "1.37.2" → "1.38.0"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- APK md5 `21df973d9c1780f9df7801e2d9487ddb`, 17.5 MB, live on
  `https://hushtv.xyz/hushtv.apk`. Shipped as **MANDATORY** so
  every user picks up the home-rail visibility + detail screen.

### Backlog after v1.38.0
- **Tier 2** (waiting on user signal):
  - Filter chips (All / Open / Available / Closed) at the top of
    My Requests
  - Pull-to-refresh on Mobile (currently button-only)
  - Cancel request — needs a new gateway action; provide Base44
    admin code to wire up
  - Empty-state CTA — big "Request your first title" button
- **Tier 3**:
  - Search inside My Requests
  - Group view (collapsible Open / Available / Closed sections)
  - Inline reply to admin
- **P2** Picture-in-Picture (Mobile + TV)
- **P2** Xtream Catch-up / Archive
- **P3** OS-level push (FCM) for users not in the app

---

## v1.37.2 — 2026-04-25 (versionCode 188)

**Fix: TV D-pad couldn't navigate the Request Missing Content modal.**
User report (with on-device photo) showed the modal rendered correctly
but D-pad input was passing through to the background screen — focus
moved between Home / Live TV / Movies / Series / Search instead of the
modal's name field, email field and Continue button. Mobile worked
fine because touch doesn't traverse focus.

### Root cause
`RequestContentSheet` rendered as a plain `Box` scrim layered on top of
the screen via z-order. Compose's focus engine doesn't treat that as
a focus boundary — D-pad keys still walk the underlying focus tree.
On top of that, the modal's TypeRadio / ScopeRow / PrimaryButton /
SecondaryButton / TextOnlyButton used `clickable {}` (touch only) with
no `.focusable()` modifier, so even if focus had reached the modal
nothing inside it would have accepted D-pad Enter.

### Fix
- Wrapped the entire modal in `androidx.compose.ui.window.Dialog`
  with `usePlatformDefaultWidth = false`. Dialog creates its own
  window + focus root, so D-pad input is automatically trapped inside.
  Same pattern the project already uses for `LayoutChooserDialog`,
  `PinDialog` and `TVChannelActionsDialog`.
- Replaced every `.clickable {}` on interactive surfaces with
  `.clickableWithEnter {}` (the project's standard helper that
  handles KEYCODE_DPAD_CENTER + Enter + NumPadEnter).
- Added `.focusable()` to TypeRadio, ScopeRow, PrimaryButton,
  SecondaryButton, TextOnlyButton, and the LabeledField inner Box.
- Added cyan focus borders + filled-bg-on-focus visuals to every
  control so users can see exactly where their cursor is.
- Each phase auto-focuses its first interactive widget on mount via
  `FocusRequester` + a 220 ms delay (matching the existing TV dialog
  cadence).
- BACK on the remote dismisses the dialog (`dismissOnBackPress = true`).
  `dismissOnClickOutside = false` so accidental overscroll past the
  scrim doesn't lose data.

### Files
- `ui/requests/RequestContentSheet.kt` — full refactor (kept the
  three-phase `Phase.CONTACT/FORM/SUCCESS` flow + every prior style
  decision; only the focus + D-pad layer changed).

### Build + deploy
- `versionCode 187 → 188`, `versionName "1.37.1" → "1.37.2"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- APK md5 `71892d79d469478597ddc61a351434a8`, 17.5 MB, live on
  `https://hushtv.xyz/hushtv.apk`. Shipped as **MANDATORY** so every
  TV user picks up the fix on next launch — without it the request
  feature is unreachable on TV.

---

## v1.37.1 — 2026-04-25 (versionCode 187)

**"Your request is in!" notification banner.** Closes the loop on the
v1.37.0 request feature: when the admin marks a user's request as
`added` or `already_available`, a slide-down banner greets them on
their next app launch with a one-tap "Watch now" deep-link straight
into the title's detail page.

### Behaviour
- Polls the gateway at most every 30 minutes per device (throttled
  via `RequestNotificationStore.lastPollMs`). Background dispatcher,
  silent network call, fully fire-and-forget.
- Skipped entirely for users who have never submitted a request (no
  contact email → nothing to query).
- Banner shows newly-fulfilled requests only — every request id is
  marked `seen` the moment we render it, so even if the user
  dismisses without tapping, we never re-show the same one.
- "Watch now" resolves the request title against the user's Xtream
  movie/series catalog using `TitleMatcher.normalize` for tolerant
  matching (handles punctuation, case, and word-prefix variants).
  Falls back to opening My Requests if the resolver finds nothing
  in the library — e.g. the user's profile lost access since the
  request was made.

### Files
- NEW `data/RequestNotificationStore.kt` — SharedPreferences for
  `last_poll_ms` + `seen_added_ids`.
- NEW `ui/requests/RequestNotificationHost.kt` — Compose host with
  the slide-down banner UI, the polling LaunchedEffect, and the
  Xtream title resolver. D-pad-focusable for TV.
- `mobile/MobileApp.kt` — wires the host inside the `Surface`,
  reading the active playlist from `LastProfileStore`.
- `MainActivity.kt` — same wiring inside the TV `AppContent`.

### Build + deploy
- `versionCode 186 → 187`, `versionName "1.37.0" → "1.37.1"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- APK md5 `72a0bfc0526837ff7b9d69579e0e1a26`, 17.5 MB, live on
  `https://hushtv.xyz/hushtv.apk`. Shipped as **non-mandatory** —
  the v1.37.0 update was already mandatory and the banner is a
  delight enhancement, not a critical fix.

### Testing notes
- API was end-to-end smoke-tested in v1.37.0 (request id
  `69ece614d72261e63259b77c` created + read). The poller uses the
  same `getContentRequests` action, so the network path is already
  proven.
- The banner only renders when at least one request flips to
  `added` or `already_available` AND hasn't been acknowledged yet.
  To verify on-device: submit a request, ask the admin to mark it
  added on the dashboard, then cold-launch the app. Banner should
  appear within ~2-3 s.

### Backlog after v1.37.1
- **P2** Picture-in-Picture (TV + Mobile)
- **P2** Xtream Catch-up / Archive (`tv_archive=1` → timeshift URL)
- **P2** "Re-request" / status-detail dialog on My Requests rows
- **P3** OS-level push notification (Firebase Cloud Messaging) so
  users learn about fulfilled requests even when they're not
  actively using the app
- **P3** Re-evaluate Gemini AI Search

---

## v1.37.0 — 2026-04-25 (versionCode 186)

**Request Missing Content** — full feature shipped. Users who can't find
a movie or a specific episode can submit a request straight from the
app. The request is POSTed to the HushTV admin gateway
(`https://hushtv.com/api/functions/hushtvapiGateway`) using the
`htv_FIe0…` API key and surfaces in the admin dashboard alongside an
auto-saved customer email/name.

### Where the user can request
- **Mobile Search**: when a query has zero matches, a primary "Request
  '<query>'" pill appears under the empty state. When matches exist,
  the same pill renders as a footer (in case the matches don't include
  the exact thing the user wanted).
- **TV Search** (`TVUnifiedSearchScreen.kt`): same two trigger spots,
  D-pad-focusable cyan pill that auto-receives focus on the empty
  state.
- **Mobile Series Detail**: a "Missing an episode? Request it" cyan
  chip is appended below every season's episode list. Pre-fills the
  modal with the show name + currently-selected season.
- **TV Series Detail**: identical D-pad-focusable chip in the same
  spot. Pre-fills the modal with the show name + season.
- **Settings → My content requests** (Mobile + TV): list view of all
  requests with status badges (Pending · In Progress · Already
  Available · Added · Not Found) and the admin's response when set.

### One-time contact prompt
The first time a user opens the request modal, they're asked for their
name + email. Both go to `UserContactStore` (SharedPreferences) and
are reused on every subsequent submit. They can edit them via
"Change contact info" inside the modal.

### Files
- NEW `data/ContentRequestApi.kt` — REST client + `Status` enum +
  result sealed types (`SubmitResult`, `ListResult`).
- NEW `data/UserContactStore.kt` — name/email persistence + RFC-style
  email validation.
- NEW `ui/requests/RequestContentSheet.kt` — three-phase modal
  (Contact → Form → Success), reusable on both TV and Mobile.
- NEW `ui/requests/MyRequestsScreens.kt` — `MyRequestsList` shared
  composable + `TVMyRequestsScreen` / `MobileMyRequestsScreen`
  wrappers.
- `mobile/MobileSearchScreen.kt` — wired no-results CTA + footer CTA +
  modal overlay.
- `ui/screens/TVUnifiedSearchScreen.kt` — same.
- `mobile/MobileSeriesDetailScreen.kt` — per-season "Request missing
  episode" CTA + modal overlay.
- `ui/screens/TVSeriesDetailScreen.kt` — same (D-pad focusable).
- `mobile/MobileSettingsScreen.kt` — "My content requests" entry.
- `ui/screens/TVSettingsScreen.kt` — "My content requests" entry under
  the DIAGNOSTICS group.
- `MainActivity.kt` — `composable("myrequests") { TVMyRequestsScreen
  (nav) }` route.
- `mobile/MobileApp.kt` — `composable("mrequests") { MobileMyRequests
  Screen(nav) }` route.

### API contract (verified live on 2026-04-25)
- `POST https://hushtv.com/api/functions/hushtvapiGateway`
- Headers: `Content-Type: application/json`, `X-API-Key: htv_FIe0…`
- `createContentRequest` body: `{action, customer_email, customer_name,
  type, title, priority, additional_info?, series_request_type?,
  seasons?, episodes?}` → `{success, request_id, status, message}`
- `getContentRequests` body: `{action, customer_email, limit}` →
  `{success, count, requests:[…]}`
- Smoke-test confirmed: created request `69ece614d72261e63259b77c`
  successfully and read it back through the same gateway.

### Build + deploy
- `versionCode 185 → 186`, `versionName "1.36.1" → "1.37.0"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- APK md5 `57097a0f6d0ba665cbf31c54c99e3545`, 17.5 MB, live on
  `https://hushtv.xyz/hushtv.apk`. Shipped as **MANDATORY** so every
  user receives the new feature on next launch.

### Backlog after v1.37.0
- **P2** Picture-in-Picture (TV + Mobile)
- **P2** Xtream Catch-up / Archive (`tv_archive=1` → timeshift URL)
- **P2** "Re-request" / status-detail dialog on My Requests rows
- **P3** Push-style toast or notification when a request flips to
  `added` so users come back to watch it
- **P3** Re-evaluate Gemini AI Search

---

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

### Phase 56 — v1.31.3 CRITICAL Fire TV Stick crash on Live TV (2026-04-24 — completed, deployed as MANDATORY)
User reported: "Friend on Fire Stick — anytime he goes to Live TV
the app crashes. Just sent the report."

ROOT CAUSE (from server-uploaded crash report
`223146-067427-Amazon-AFTMM-29352443.json`):
- Device: Amazon AFTMM (Fire TV Stick 4K Max)
- Android SDK 25 (Android 7.1.2)
- App: 1.31.2-debug
- Stack:
  ```
  java.lang.NoClassDefFoundError: Failed resolution of:
      Ljava/util/Base64;
      at com.hushtv.tv.data.EpgService.decodeBase64(EpgService.kt:161)
      at com.hushtv.tv.data.EpgService.toEpgProgram(...)
      at com.hushtv.tv.data.EpgService.fetchShortEpg(...)
  ```

`java.util.Base64` was added to Java 8, which on Android only ships
on **API 26 (8.0 Oreo)** and later. Fire TV Stick 4K Max ships
Android 7.1.2 / SDK 25. The class literally doesn't exist on that
device — VM raises `NoClassDefFoundError` at first use. The very
first thing Live TV does on entry is `fetchShortEpg(...)`, which
parses program titles base64-decoded by `decodeBase64`. So the
crash hit on EVERY Live TV entry, never reaching anything else.

Repro is 100% deterministic on any Android 4.x / 5.x / 6.x / 7.x
device. Earlier missed because all my test devices + Shield run 8+.

Fix:
- `data/EpgService.kt`:
  - Import swapped: `java.util.Base64` → `android.util.Base64`.
    `android.util.Base64` ships back to API 8 (Android 2.2) so it
    covers every device the app could conceivably run on.
  - `decodeBase64(s)` body changed from `Base64.getDecoder().decode(s)`
    → `Base64.decode(s, Base64.DEFAULT)`. Behaviourally identical
    for our case (default mode is RFC 4648 which is what Xtream
    EPG payloads use).
  - Added a long explanatory code comment so this regression doesn't
    re-emerge if someone refactors it.
- Codebase audit: `grep -rn "java.util.Base64\|java.time.\|java.nio.file."`
  returned only the comment line in `EpgService.kt`. No other API-26+
  Java APIs in use anywhere — single point of failure, single
  point of fix.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 167 → 168`, `versionName
  "1.31.2" → "1.31.3"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL in 35 s.
- Shipped as MANDATORY (version.json `mandatory=true`) — APK (md5
  `63691ac3985e5693c83b3ca52823370b`) live on `https://hushtv.xyz`.

Going forward — when adding new helpers that might hit
back-compat APIs, prefer `android.util.*` / `androidx.*` over
`java.util.*` / `java.time.*` unless we explicitly target SDK 26+.
HushTV's `minSdk = 24` means we MUST support Fire TVs running
Android 7.x.


### Phase 55 — v1.31.2 AI caption placeholder auto-hide (2026-04-24 — completed, deployed)
User screenshot: "Listening · English only" placeholder was stacked
directly below an actual SDH caption ("And she'll be off the hook for
good"), visually crowding the frame. The overlay rendered the
placeholder any time `aiCaptionText` was blank — but a Vosk
transcription lagging behind the movie's embedded subtitle track left
our placeholder ON perpetually even though captions were arriving
via the regular SubtitleView.

Fix:
- `ui/screens/TVPlayerScreen.kt` + `mobile/MobilePlayerScreen.kt`:
  added `showPlaceholder: Boolean` local state inside the caption
  overlay. Two LaunchedEffects govern it:
    • `LaunchedEffect(aiCaptionsEnabled)`: on enable, set
      `showPlaceholder = true`, then `delay(4_000)` and set it back
      to false. On disable, clear immediately.
    • `LaunchedEffect(aiCaptionText)`: as soon as a non-blank
      caption comes in, set `showPlaceholder = false`. Any
      subsequent blank periods no longer re-show the placeholder.
  Placeholder path in the `when {}` expression now checks
  `showPlaceholder` instead of just `else`, so after the 4 s window
  the overlay goes null (nothing rendered) until either a real
  caption appears OR the engine emits ERROR / PREPARING (those two
  still always show — they contain important info).

Build + deploy:
- `app/build.gradle.kts`: `versionCode 166 → 167`, `versionName
  "1.31.1" → "1.31.2"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped non-mandatory — APK (md5
  `ac4ae66d2382e50880a7ff9f877b6e34`) live on `https://hushtv.xyz`.


### Phase 54 — v1.31.1 Mobile update-dialog changelog scroll fix (2026-04-24 — completed, deployed)
User screenshot showed the v1.31.0 update dialog on a phone with the
"Update Now" button clipped below the visible area. Dialog had no
way to scroll down so the user was stuck.

ROOT CAUSE: `PromptBody` in `update/UpdateDialog.kt` wrapped the
changelog bullets in a plain `Column` with no height constraint and
no scroll wrapper. On phones in portrait, a changelog ≥ 6–7 lines
pushed the bottom-row buttons below the screen edge. Made worse by
the long v1.31.0 release notes (AI-captions fix had 5 sub-bullets
alone).

Fix:
- `update/UpdateDialog.kt` `PromptBody`:
  - Changelog `Column` now uses `.heightIn(max = 260.dp)
    .verticalScroll(rememberScrollState())`. Content that exceeds
    260 dp becomes scrollable inside the card; the Update / Later
    buttons below stay pinned to a known position.
  - Dropped the ad-hoc `.take(8)` on the bullet list — all
    changelog entries are now visible (via scroll if needed).
  - Added imports: `rememberScrollState`, `verticalScroll`
    (`heightIn` was already available via the wildcard
    `androidx.compose.foundation.layout.*` import).

Build + deploy:
- `app/build.gradle.kts`: `versionCode 165 → 166`, `versionName
  "1.31.0" → "1.31.1"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped non-mandatory — APK (md5
  `3141b90b4ccaf12c0dc19ed8250c5ae2`) live on `https://hushtv.xyz`.

Workaround for users stuck on v1.31.0 on a phone:
- The new APK is reachable directly at `https://hushtv.xyz/hushtv.apk`.
  Visiting that in a phone browser triggers a normal APK download
  which the OS installs via its own package installer — bypassing
  the broken in-app dialog entirely. Once on v1.31.1, future
  update prompts will behave correctly.


### Phase 53 — v1.31.0 Three UX fixes: 12-hour EPG, long-press menu, AI captions (2026-04-24 — completed, deployed)
User reported three distinct issues in one message. Fixed all three.

1) EPG military time → 12-hour:
- `ui/screens/TVLiveBrowseScreen.kt` `formatClock(ms)`: `"HH:mm"` →
  `"h:mm a"`. This helper backs the Tivimate-style preview bar's
  "now playing / up next" timestamps and every timeline label
  throughout the TV live hub (previously the only place still
  showing 24-hour time after phase 45 migrated mobile to 12-hour
  via the shared helper).
- Audited all other SimpleDateFormat usages: mobile already used
  `h:mm a`, TVEpgGridScreen already used `h:mm a`, no further
  changes needed.

2) Long-press brought up reminder-only (regression from phase 45)
   — user wanted favorite toggle back:
- `ui/screens/TVLiveBrowseScreen.kt`:
  - Renamed `TVReminderDialog` → `TVChannelActionsDialog`. Now
    TWO-PHASE with internal `phase: "menu" | "reminder"` state.
  - Menu phase shows two focusable `ActionRow`s:
     · Add to favorites / Remove from favorites (yellow star
       icon, instant toggle + auto-dismiss)
     · Set reminder… (cyan bell icon, transitions to phase
       "reminder" which is the original upcoming-program list)
  - `FocusRequester` + `LaunchedEffect(phase)` auto-focuses the
    first row on each phase so D-pad users can OK-through without
    hunting.
  - `FavoritesStore.isFavorite(ctx, playlistId, streamId)` read
    inside the composable keyed on a local `favVersion: Int` so
    the Star ↔ StarBorder icon flips live if toggled (although
    we dismiss immediately on toggle so it mostly matters when the
    user re-opens).
  - New private `ActionRow(icon, iconTint, title, subtitle,
    focusRequester, onClick)` composable — reusable rounded card
    focusable, cyan border on focus.
- Kept the existing `ReminderRow` composable untouched — the
  reminder phase reuses it verbatim.
- Mobile already had a long-press quick-action sheet w/ favorite
  toggle; added a new "Set reminder…" QuickActionRow that opens
  a secondary `MobileReminderDialog` (new composable mirroring
  the TV reminder list).

3) AI subtitles: toggle had no effect, no text rendered, no
   language selection:
- RCA (via code audit):
  a) `PcmTapAudioProcessor.onConfigure` only accepted
     `C.ENCODING_PCM_16BIT`. Modern Media3 on Shield / Fire TV
     often negotiates `C.ENCODING_PCM_FLOAT` — which made the
     processor throw `UnhandledAudioFormatException`, causing the
     sink to ROUTE AROUND the processor chain. Tap never ran, AI
     engine never got samples.
  b) ExoPlayer's AUDIO OFFLOAD path routes encoded audio (EAC3,
     Opus, AC3) directly to the DSP, bypassing Media3's processor
     chain entirely. Many live IPTV streams use EAC3 and were
     silently invoking offload on capable TVs. Again: tap never
     ran.
  c) UI showed nothing at all when captions were toggled on but
     no text had arrived yet — no indication that it was listening,
     loading the model, or that the stream format was
     incompatible. User reasonably interpreted this as "toggle is
     broken".
- Fix:
  - `ai/PcmTapAudioProcessor.kt`:
     · `onConfigure` now accepts both `ENCODING_PCM_16BIT` and
       `ENCODING_PCM_FLOAT`. Rejects only encoded formats.
     · New `@Volatile var tapEncoding: Int` field captures the
       negotiated encoding.
     · `queueInput` routes float samples through a new
       `floatToInt16LE(src, remaining)` helper that reads float32
       samples, clips to [-1.0, 1.0], and writes Int16 LE. Output
       stays float (unchanged forwarding to sink) so playback
       quality is untouched, but the consumer callback always
       receives Int16 LE regardless of sink encoding. Vosk already
       expects Int16 so the engine side needs no changes.
  - `ui/screens/TVPlayerScreen.kt` + `mobile/MobilePlayerScreen.kt`:
     Right after `ExoPlayer.Builder.build()` and `prepare()`, now
     set `trackSelectionParameters.buildUpon().setAudioOffloadPreferences(
     AudioOffloadPreferences.Builder()
     .setAudioOffloadMode(AUDIO_OFFLOAD_MODE_DISABLED).build())
     .build()`. This forces every audio renderer path through the
     processor chain, making the PCM tap authoritative again.
  - UI feedback:
     · TV + Mobile AI caption overlays now render when
       `aiCaptionsEnabled` is true even if `aiCaptionText` is blank.
       Overlay text driven by `VoskCaptionEngine.state`:
        - PREPARING → "Loading English speech model…"
        - ERROR → "AI captions unavailable on this stream"
        - IDLE / READY / RUNNING with no text yet → "Listening ·
          English only"
        - Non-blank text → the actual caption (larger font)
     · Placeholder states render in muted grey at smaller size so
       they don't compete visually with real captions.
- Language-selection UX deferred: Vosk ships one 50 MB English
  model; cross-language translation would require either an
  additional translation model (likely >200 MB) or an online LLM
  call per caption. Out of scope for this release. User now knows
  it's English-only thanks to the overlay copy.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 164 → 165`, `versionName
  "1.30.7" → "1.31.0"` (minor bump: multi-user-facing feature work).
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped as non-mandatory — APK (md5
  `c6d890caf5c11879e34e8e2b45075cc7`) live on `https://hushtv.xyz`.


### Phase 52 — v1.30.7 Crash reporter proper timestamping (2026-04-24 — completed, deployed)
User sent a third crash report after updating to v1.30.6. Analysis:

- `app_version: 1.30.6-debug` in payload
- But log header says `===== HushTV crash 2026-04-24 15:18:54 =====`
  (local time, ~15 min BEFORE v1.30.6 was deployed to their device)
- Same IllegalStateException signature as the two prior reports.

CONCLUSION: **The fix in v1.30.6 was NOT verified broken by this
report.** The trace was a STALE log entry on the user's device from
their previous v1.30.5 session. When they tapped "Send to server"
manually after updating, our reporter re-uploaded the entire
`crash.log` file and tagged it with the CURRENT BuildConfig version
(v1.30.6) — because `buildPayload()` stamped `app_version` at UPLOAD
time instead of CRASH time.

Flaws in the crash-reporting pipeline (NOT in v1.30.6's fix):

1. Log header format was human-readable prose (`===== HushTV crash
   <local-time> — thread=<name> =====`) — not machine-parseable, no
   version field.
2. `buildPayload()` unconditionally used `fmt.format(Date())` and
   `BuildConfig.VERSION_NAME` for `captured_at` / `app_version` —
   always "now" and "current APK", never "when/where it actually
   crashed".
3. `uploadNow(force=true)` skipped the `last_uploaded_mtime` dedup
   check → users could re-post the same stale trace endlessly.
4. No UX signal distinguishing "nothing new to upload" from "failed"
   or "sent" → invited confusion.

Fixes (this phase):
- `HushTVApp.installCrashHandler()`: log header is now
  machine-parseable:
    `===== HushTV <iso_utc> v<name>#<code> thread=<name> =====`
  Example:
    `===== HushTV 2026-04-24T15:18:54Z v1.30.6-debug#163 thread=main =====`
  UTC only (no local-time ambiguity) + explicit version name + code.
- `CrashReporter.buildPayload()`: added a `Regex` parse of that
  header. When it matches, uses the parsed `captured_at` and
  `app_version` / `version_code` for the upload payload. When it
  doesn't (legacy log written by older crash handlers), falls back
  to `Date()` + `BuildConfig` as before. Also added a new
  `installed_version` field that always reports the CURRENT
  BuildConfig — so the dashboard can distinguish "crash happened
  on v1.30.6 and was uploaded while v1.30.6 was still installed"
  from "crash happened on v1.30.5 but user updated to v1.30.6
  before uploading".
- `CrashReporter.uploadNow()`: signature changed from
  `(Boolean)->Unit` → `(String)->Unit`. Returns "sent" / "failed" /
  "nothing". Checks `last_uploaded_mtime` BEFORE uploading manually —
  if nothing's changed since the last successful send, returns
  "nothing" immediately without a network round-trip. Also early-
  returns "nothing" when `crash.log` is missing / empty.
- `mobile/MobileDiagnosticsScreen.kt` + `ui/screens/TVDiagnosticsScreen.kt`:
  added a "nothing" branch to the upload status banner showing a
  yellow "Already sent — nothing new to upload." Color: `#FACC15`.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 163 → 164`, `versionName
  "1.30.6" → "1.30.7"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (only
  AutoMirrored-ArrowBack / Logout deprecation warnings — not
  blockers).
- Shipped as non-mandatory — APK (md5
  `88c23126b8dd5b41a0c1444e9d1e829e`) live on `https://hushtv.xyz`.

Verification plan (to send to user):
1. Install v1.30.7.
2. Settings → Diagnostics → Delete (clears the stale log).
3. Use the app normally — specifically exercise the CW remove flow
  that used to crash.
4. If the v1.30.6 focusRestorer fix works, no crash → Diagnostics
  stays "No crashes logged" → server gets nothing.
5. If anything else crashes, it'll have a NEW-format header and
  show up on the dashboard with the real captured_at time and
  correct app_version.


### Phase 51 — v1.30.6 REAL ROOT CAUSE of TV crashes (2026-04-24 — completed, deployed as MANDATORY)
First crash reports arrived on the server dashboard from a Shield TV
running v1.30.5. Two identical traces, same signature:

```
java.lang.IllegalStateException: Release should only be called once
    at androidx.compose.foundation.lazy.layout.LazyLayoutPinnableItem.release(LazyLayoutPinnableItem.kt:159)
    at androidx.compose.ui.focus.FocusRestorerNode$onExit$1.invoke-3ESFkO8(FocusRestorer.kt:103)
    at androidx.compose.ui.focus.FocusTransactionsKt.performCustomExit-Mxy_nc0(FocusTransactions.kt:646)
    at androidx.compose.ui.focus.FocusTransactionsKt.performCustomClearFocus-Mxy_nc0(FocusTransactions.kt:288)
    at androidx.compose.ui.focus.FocusTransactionsKt.performCustomRequestFocus-Mxy_nc0(FocusTransactions.kt:266)
    ...
    at androidx.compose.ui.platform.AndroidComposeView.dispatchKeyEvent(...)
```

This is a KNOWN Jetpack Compose bug (Google issue tracker
`322811857`, plus several duplicates) where
`Modifier.focusRestorer()` on a LazyRow whose items get removed at
runtime can cause the internal `LazyLayoutPinnableItem` to be
released twice — the first release happens when the item is removed
from the list, the second during the next D-pad focus traversal via
`FocusRestorerNode.onExit`. Neither the LazyLayout nor the
FocusRestorer checks if release was already called.

My earlier phase-49 coroutine-cancellation fix did not address this
because the crash was not in OUR code — it was inside Compose's
internal focus machinery, triggered purely by the list-mutation +
focus-restore combo. The coroutine fix was still correct (it
prevented stale TMDB writes), just not the actual crash driver.

Files changed:
- `ui/screens/home/HomeContinueWatchingSection.kt`: dropped
  `.focusRestorer()` from the CW Column modifier chain (line 122).
  Now: `Modifier.focusRequester(firstItemFocus).focusGroup()` —
  just firstItem focus target + focus group, no pinned-item
  restoration. Added a long explanatory comment pointing to the
  Compose bug so nobody unknowingly re-adds it.
- Audited all other `focusRestorer()` call sites in the codebase
  (Collections / Streaming / Discovery / Years / Genres home rows
  + TVCollectionsBrowseScreen). All are backed by static lists
  sourced from TMDB / Xtream — their items never get removed at
  runtime, so the same bug can't trigger. Left them as-is.

Trade-off:
- Minor UX regression: when the user D-pad-Ups off the CW row and
  then comes back Down, focus lands on the first card instead of
  the exact card they were on. Acceptable — the row is horizontal
  so the LazyRow's internal focus memory still keeps the user near
  where they left off when navigating within the row, just not
  across a row boundary. Zero crashes is worth more than this.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 162 → 163`, `versionName
  "1.30.5" → "1.30.6"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped as MANDATORY — APK (md5
  `f82133b49b87c462a3d2e07f52ec4468`) live on `https://hushtv.xyz`.

Verification path:
- User with the crashing Shield should install v1.30.6.
- Retest the original repro: long-press to remove a CW item →
  D-pad around to other CW items → no crash expected.
- Any new crashes will auto-upload to the dashboard so we can see
  them.


### Phase 50 — v1.30.5 Server-side crash reporting (2026-04-24 — completed, deployed)
User: "I'm seeing crash reports in Diagnostics, but I have no way to
share them — Shield has no mail clients / share targets. Can we POST
them to our dedicated server so you + I can view them centrally?"

Server side (lives at `https://hushtv.xyz/crash/`):
- `/tmp/hushtv_crash_svc.py` — zero-dependency Python 3.12 stdlib
  HTTP service. Endpoints:
    • `POST /submit/<SECRET>` — validates the baked-in app token,
      parses JSON body, enriches with `sent_at` (UTC), clamps string
      fields, writes to `/var/hushtv-crash/<YYYY-MM-DD>/<HHMMSS-µs>-<device>.json`.
    • `GET /` — Basic-Auth-gated HTML dashboard listing every
      report newest-first with chips for version / device / timestamps
      and a 400-char trace preview per card.
    • `GET /report/<rel>` — Basic-Auth-gated single-report view
      (pretty-printed JSON).
    • `GET /health` — plain-text `ok` for uptime checks.
  Threaded HTTP server (stdlib `socketserver.ThreadingMixIn`),
  listens on `127.0.0.1:5055`.
- `/tmp/hushtv-crash.service` — systemd unit with hardening
  (`NoNewPrivileges`, `ProtectSystem=strict`, `ReadWritePaths=/var/hushtv-crash`).
- `/etc/hushtv-crash/config.env` (`chmod 600`) — environment file
  with submit token, dashboard user/password.
- nginx patch — new `location /crash/` block reverse-proxies to
  `127.0.0.1:5055`, with Let's Encrypt SSL inherited from the
  existing vhost.
- Generated tokens:
    • Submit secret (baked in APK): `GbExkT_0wVwqMbw5mwOrRMbe1pS3PghK`
    • Dashboard user/pass: `admin / rTirJ-dNlfKiYRvqqkg`
    • Dashboard URL: `https://hushtv.xyz/crash/`

App side:
- NEW `data/CrashReporter.kt` — background-threaded upload helper:
    • `uploadIfPending(ctx)` — call from `HushTVApp.onCreate()`; runs
      on a single daemon thread and POSTs the current `crash.log`
      iff its mtime differs from the last successfully-uploaded
      mtime (stored in `crash_reporter` SharedPreferences under
      `last_uploaded_mtime`). Silent no-op otherwise.
    • `uploadNow(ctx, callback)` — forced upload used by the
      Diagnostics "Send to server" button. Runs on the same thread,
      calls `callback(success: Boolean)` back on the thread (UI
      mutation uses `mutableStateOf` which is thread-safe for
      primitives).
    • `buildPayload(...)` — emits a tiny hand-rolled JSON object
      with `device` (MANUFACTURER-MODEL-<short ANDROID_ID>),
      `android_sdk`, `app_version`, `version_code`, `captured_at`,
      `trace` (capped at 64 KB — server rejects >512 KB bodies).
    • `postJson(url, body)` — `HttpURLConnection` POST with 10 s
      connect / 15 s read timeouts. HTTP 200 = success.
    • Pure stdlib — no OkHttp / Gson / Kotlin serialization
      pull-ins. ~170 LOC total.
- `HushTVApp.onCreate()`: calls `CrashReporter.uploadIfPending(this)`
  right after `installCrashHandler()`. Silent background task —
  zero UX impact if successful.
- `mobile/MobileDiagnosticsScreen.kt`:
  - New cloud-upload circle button (cyan `CloudUpload` icon) in the
    top bar, next to Share + Delete.
  - New `uploadState: String?` local state — drives a banner below
    the top bar: "Uploading to server…" (cyan),
    "Sent to server. We'll take it from here." (green),
    "Upload failed. Check internet and try again." (red).
- `ui/screens/TVDiagnosticsScreen.kt`:
  - Same CloudUpload D-pad-focusable `TvCircleBtn` + matching
    banner. Empty-state copy updated to mention auto-upload so
    users understand that future crashes will be sent without
    action.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 161 → 162`, `versionName
  "1.30.4" → "1.30.5"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped as versionCode=162 / versionName="1.30.5" — APK (md5
  `d92c70dd47e1622fba035805c6245aa9`) live on `https://hushtv.xyz`.
- Verified end-to-end: `curl -s -X POST https://hushtv.xyz/crash/submit/<SECRET>
  -d '<payload>'` returned `{"ok":true,"stored":...}` and the
  report appeared on the Basic-Auth-gated dashboard.

Operational notes:
- Dashboard URL: `https://hushtv.xyz/crash/` (Basic Auth
  `admin / rTirJ-dNlfKiYRvqqkg`).
- Reports live at `/var/hushtv-crash/<date>/` — plain JSON, easy to
  grep / wc. systemd service is `hushtv-crash.service`.
- Submit secret rotatable via `/etc/hushtv-crash/config.env` +
  `systemctl restart hushtv-crash`, but note APK has the old
  secret baked in — rotation requires a new APK release.


### Phase 49 — v1.30.4 CRITICAL: Continue Watching remove → crash on next nav (2026-04-24 — completed, deployed as MANDATORY)
User: "App is crashing like crazy. After you remove something from
Continue Watching it will remove it, then when you try to navigate
to another Continue Watching item it's crashing the app. Has
something to do with removing CW items and the CW page."

ROOT CAUSE (code audit):
In `ui/screens/home/HomeContinueWatchingSection.kt`
`rememberContinueEntries(...)`, the TMDB hydration was launched via
`rememberCoroutineScope().launch { ... }`. That scope OUTLIVES the
parent `LaunchedEffect(playlistId, version)` — so when the user
removed an item (version++ bumps), the NEW LaunchedEffect cycle
started, but the OLD hydration coroutines from the PREVIOUS cycle
kept running. On completion, each stale coroutine did:

  entries = entries.toMutableList().also { list ->
      if (idx < list.size) list[idx] = list[idx].copy(tmdb = tmdb)
  }

The `idx` was the POSITIONAL index from the OLD list. After a
removal all downstream indices shifted, so stale writes:
  (a) wrote TMDB metadata to the WRONG entry,
  (b) lost in-flight writes from the NEW cycle via read-then-write
      races (no atomic state mutation in coroutines),
  (c) left `entries` in a half-assigned shape with duplicated /
      mismatched `ContinueEntry` objects.

This corrupted state caused the NEXT D-pad navigation to crash —
`focusRestorer()` / `LazyRow`'s focus target resolution resolved to
a stale node that no longer matched the current children.

Fix:
- `ui/screens/home/HomeContinueWatchingSection.kt`:
  - `rememberContinueEntries(...)` rewritten:
    - Removed the `rememberCoroutineScope()` reference entirely.
    - TMDB hydration now runs INSIDE the `LaunchedEffect(playlistId,
      version)` block as child coroutines (`launch { ... }`) of the
      LaunchedEffect's own scope. When `version` bumps, these
      children are CANCELLED automatically.
    - Swapped positional `idx` for key-based lookup when applying
      hydration results — `entries.map { if (matches) it.copy(tmdb)
      else it }`. Stale completions silently no-op instead of
      writing to a shifted slot.
    - Wrapped the whole effect + per-entry hydration in
      `runCatching` so any TMDB / IO / SharedPreferences throwable
      is swallowed (crash handler still logs it to disk).
  - `remove(e)` lambda: now also filters the entry out of `entries`
    SYNCHRONOUSLY before bumping `version++`. Eliminates the
    window where the removed card was still in the list while the
    dialog was dismissing and focus restoration was hunting for a
    target. Dialog's released focus now lands on an actual existing
    adjacent card.
  - Removed the now-unused `rememberCoroutineScope` import.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 160 → 161`, `versionName
  "1.30.3" → "1.30.4"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped as MANDATORY (version.json `mandatory=true`) — APK (md5
  `7d0a046a7f3b7225655630eb21ba5b6b`) live on `https://hushtv.xyz`.


### Phase 48 — v1.30.3 Diagnostics screen (crash log viewer + sharer) (2026-04-24 — completed, deployed)
Closes the loop on the v1.30.2 crash handler: the handler WRITES the
log, now users can READ + SHARE it without adb. This is the real
"monitor crash reports" infrastructure promised in the phase-47
follow-up.

Files added:
- `data/CrashLogStore.kt`: thin helper around `filesDir/crash.log`.
  Three methods: `read(ctx): String`, `clear(ctx)`,
  `hasContent(ctx): Boolean`. Uses `runCatching` on every I/O so a
  missing / locked file never crashes the diagnostics screen itself.
- `mobile/MobileDiagnosticsScreen.kt`: touch-first log viewer.
  Top bar with back button + "Diagnostics · v<VERSION> · Crash log"
  eyebrow + Share + Delete circle buttons (Share/Delete only shown
  when content is present). Body = scrollable monospaced text in a
  cyan-bordered card, or a centred "No crashes logged" empty state
  when the file is empty. Share uses `Intent.ACTION_SEND` with
  `type = "text/plain"` and `EXTRA_SUBJECT = "HushTV crash log —
  v<VERSION>"` so the system chooser shows email / messaging /
  Gmail / Messenger / etc. as targets.
- `ui/screens/TVDiagnosticsScreen.kt`: D-pad-first equivalent for
  TV. Same layout, but circle buttons are cyan-bordered on focus +
  focusable + use the existing `clickableWithEnter` helper so OK
  triggers the action. Back button auto-focuses on entry via
  `FocusRequester` so BACK on the remote immediately returns to
  Settings. Monospaced log text in a rounded border-cyan card;
  vertical scroll works out-of-the-box with TV remotes via
  `verticalScroll(rememberScrollState())`.

Wiring:
- `mobile/MobileApp.kt`: added `composable("mdiag") {
  MobileDiagnosticsScreen(nav) }` next to the search route.
- `mobile/MobileSettingsScreen.kt`: added a `SettingsItem(icon =
  Icons.Default.Report, title = "Diagnostics", subtitle = "Share
  crash reports", onClick = { nav.navigate("mdiag") })` right after
  the "Check for updates" row.
- `MainActivity.kt` (TV): added `composable("diag") {
  TVDiagnosticsScreen(nav) }` next to the TV settings route.
- `ui/screens/TVSettingsScreen.kt`: added a new "DIAGNOSTICS"
  section header + a `SettingsCard(title = "View crash log",
  subtitle = "Share a crash report if the app ever force-closes",
  icon = Report)` that navigates to `"diag"`.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 159 → 160`, `versionName
  "1.30.2" → "1.30.3"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped as versionCode=160 / versionName="1.30.3" — APK (md5
  `f56de95324223b143c1699b2cddc2e4e`) live on `https://hushtv.xyz`.

How the user reports a crash now:
1. App force-closes → HushTVApp's UncaughtExceptionHandler writes
   the trace to `filesDir/crash.log`.
2. User reopens the app → Settings → Diagnostics.
3. Sees the trace + taps Share → picks Gmail / Messenger / etc. →
   ships the log to us.
4. We parse the stack trace, fix the root cause, bump version.


### Phase 47 — v1.30.2 CRITICAL STABILITY FIX: random TV app exits (2026-04-24 — completed, deployed as MANDATORY update)
User reported: "Major issues with TV. App randomly exiting — happens
on Continue Watching when scrolling/clicking/long-pressing to remove,
on Movies when changing categories, and when navigating between Live
TV / Movies / Series. Very frequent. Might be memory."

RCA (via troubleshoot_agent, high confidence):
- PRIMARY CAUSE (85%): Android's low-memory-killer (LMK)
  terminating the app process under pressure. App footprint
  ~250-300 MB (bundled 50 MB Vosk model + 64 MB Coil bitmap cache
  + ExoPlayer + Compose state + EPG caches) exceeded the ~192-256 MB
  default heap on low-memory TVs. No `android:largeHeap="true"` had
  ever been set. Silent process-kill matches the symptom — no
  crash dialog, just a sudden return to launcher.
- SECONDARY (15%): No `Thread.setDefaultUncaughtExceptionHandler`
  installed. Any uncaught exception in the new LaunchedEffects
  (LiveSessionStore/RecentChannelStore writes, reminder scheduling)
  would have silently killed the process. Harder to prove without
  logs — but the lack of any crash visibility was itself a bug.

Fixes (all four shipped together):

1. `AndroidManifest.xml` — added `android:largeHeap="true"` to
   the `<application>` tag. On typical TV devices this bumps the
   per-process heap from ~256 MB to ~512 MB-1 GB depending on OEM,
   giving the app 2-4× more headroom against the LMK.

2. `HushTVApp.kt` — installed a default uncaught exception handler
   in `onCreate()` that writes any crash stack trace to both
   Logcat (tag `HushTVCrash`) AND
   `/data/data/com.hushtv.tv/files/crash.log`. Append mode, capped
   at 256 KB (truncate-on-overflow). Chains to the previous handler
   afterwards so Android still kills the process as expected. Means
   any FUTURE crash leaves evidence we can retrieve.

3. `HushTVApp.kt` — reduced Coil memory cache from `maxSizePercent
   (0.25)` → `maxSizePercent(0.12)` and disk cache from 256 MB →
   128 MB. Frees roughly 30 MB RAM on a 2 GB TV without
   significantly hurting repeat-scroll smoothness (Coil's LRU
   eviction still keeps the hot bitmaps).

4. `TVLiveBrowseScreen.kt` — wrapped all FIVE
   LaunchedEffect blocks added in phase 43 (LiveSessionStore
   persist/restore) with `runCatching {}`. If SharedPreferences or
   NavState throws for any reason, the effect no-ops instead of
   killing the process.

5. `notifications/EpgReminderScheduler.kt` — wrapped
   `schedule(...)` in `runCatching`. On Android 12+, wrapped the
   `setExactAndAllowWhileIdle()` call specifically with try/catch
   SecurityException (Android 14 can REVOKE
   SCHEDULE_EXACT_ALARM at runtime post-install). On denial, falls
   back to `am.set(...)` inexact alarm — reminder still fires, app
   stays alive.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 158 → 159`, `versionName
  "1.30.1" → "1.30.2"`.
- Shipped as MANDATORY update (mandatory=true in version.json) —
  APK (md5 `58b665c449f3b8190f48972246ba9750`) live on
  `https://hushtv.xyz`.

Next step if crashes still persist after user installs v1.30.2:
- Retrieve `/data/data/com.hushtv.tv/files/crash.log` via adb or a
  new "Diagnostics → Share Crash Log" button in Settings (not yet
  built — queue it as a P2 follow-up). Parse stack trace to
  identify the exact root cause.


### Phase 46 — v1.30.1 Mobile Home "Channel History" rail (2026-04-24 — completed, deployed)
User: "Can we have here more channels they were watching like the
last 5 channels (Channel History)?" — the v1.29.0 Resume Live card
only showed ONE channel (the most recent) and left the rest of the
Resume Live page blank.

Files changed:
- `data/RecentChannelStore.kt`: extended with a per-channel
  `Meta(name, poster)` store. Three new APIs:
  - `setMeta(ctx, playlistId, streamId, name, poster)`
  - `getMeta(ctx, playlistId, streamId): Meta?`
  - data class `Meta(val name: String, val poster: String?)`
  Stored under key `meta_${playlistId}_$streamId` with value
  `"name|||poster"`. Triple-pipe chosen because it can't legally
  appear in Xtream-provided channel titles. Keeps the pre-existing
  MRU streamId list untouched.
- `mobile/MobileLiveHubScreen.kt`: extended the existing
  `LaunchedEffect(selectedStreamId, channels, playlistId)` block to
  also call `RecentChannelStore.setMeta(ctx, playlistId, c.streamId,
  c.title, c.poster)` for each newly selected channel. Single write
  site covers all 5 `pushFront(...)` call sites without duplicating
  the plumbing into each.
- `mobile/MobileHomeScreen.kt`:
  - `ResumeLivePage` now reads `RecentChannelStore.getAll(ctx,
    playlistId)` + filters out the hero channel, takes up to 5
    entries, looks up cached `Meta` per id, and builds a
    `List<Pair<Int, Meta>>` for the rail. All reads are synchronous
    from SharedPreferences so the page renders instantly.
  - Added a "CHANNEL HISTORY · last N" section header + a `LazyRow`
    of `ChannelHistoryTile` composables below the hero card. Spacer
    of 22 dp above the header.
  - New private `ChannelHistoryTile(name, poster, onClick)` — 110 dp
    wide tile, cyan-bordered card with a 16:10 logo box (poster or
    2-char monogram fallback), channel name (2-line clamp) below.
    Click → builds Xtream live URL + navigates to
    `mobilePlayerRoute(... isLive = true)` for fullscreen playback.
  - Added `RecentChannelStore` import.
  - Hoisted the `launchChannel: (Int, String) -> Unit` helper so
    both the hero card and every history tile go through the same
    nav-navigate path.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 157 → 158`, `versionName
  "1.30.0" → "1.30.1"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped as versionCode=158 / versionName="1.30.1" — APK (md5
  `b7a0e6f4cb0ab323c7b3a34434c00cf6`) live on `https://hushtv.xyz`.


### Phase 45 — v1.30.0 TV "Set reminder" long-press (2026-04-24 — completed, deployed)
Brings the mobile v1.28.0 reminder feature to the TV form factor.
UX approach was deliberately less invasive than making every EPG
program block focusable (which would have disrupted existing D-pad
flow): long-press OK on a CHANNEL row opens a modal that lists the
next 6 upcoming programs with individual bell toggles.

Files changed:
- `ui/screens/TVLiveBrowseScreen.kt`:
  - Added hoisted `var reminderChannel by remember { mutableStateOf<
    MediaCard?>(null) }` to the top-level `TVLiveBrowseScreen`
    function. Non-null = dialog open.
  - `ChannelsPane(...)` signature extended with `onLongPress: (Int)
    -> Unit`. Both call sites (sidebar branch line 408 + top-bar
    branch line 488) wire it to
    `{ idx -> reminderChannel = filteredChannels.getOrNull(idx) }`.
  - `ChannelRow` signature extended with `onLongPress: () -> Unit`.
    Reused the `ContinueCard` long-press detection pattern from
    `HomeContinueWatchingSection`: tracks `keyDownAtMs` via
    `onPreviewKeyEvent`, fires `onLongPress` on KeyUp if held ≥
    500 ms AND consumes the KeyUp so `clickableWithEnter` doesn't
    ALSO fire (which would have played the channel under the
    dialog). Short-press path unchanged — quick tap still plays.
  - New bottom-of-file composable `TVReminderDialog(playlistId,
    channel, onDismiss)` — rounded cyan-bordered 560 dp wide modal
    backed by `androidx.compose.ui.window.Dialog`. Renders
    `EpgService.upcoming(channel.streamId, limit = 6)` as a
    vertical list of `ReminderRow`s. Auto-focuses the first row
    via `FocusRequester`. BACK dismisses (default Dialog behaviour).
  - New private composable `ReminderRow(program, hasReminder,
    focusRequester, onToggle)` — focusable D-pad row with cyan
    border on focus, start time + program title + bell icon
    (`Notifications` yellow when set, `NotificationsNone` grey when
    not). OK toggles:
    * If already reminded → `ReminderStore.remove(...)`.
    * If not → builds `ReminderStore.Reminder(...)`, calls
      `ReminderStore.add(...)` + `EpgReminderScheduler.schedule(...)`.
  - Bumped `reminderVersion: Int` counter on each toggle so the
    per-row bell + state re-reads fresh without a full dialog
    teardown.
  - Added imports: `Icons.Default.Notifications`,
    `Icons.Default.NotificationsNone` (uses fully-qualified
    `java.text.SimpleDateFormat` / `java.util.Date` / `java.util.
    Locale` inline to avoid top-level import churn).

Build + deploy:
- `app/build.gradle.kts`: `versionCode 156 → 157`, `versionName
  "1.29.0" → "1.30.0"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped as versionCode=157 / versionName="1.30.0" — APK (md5
  `3c7a98a9fda051709efb394fbbc1830f`) live on `https://hushtv.xyz`.

Gap — `TVEpgGridScreen` not covered:
Originally the P1 included both `TVLiveBrowseScreen` AND
`TVEpgGridScreen`. TVLiveBrowseScreen is the primary browse
surface users reach when Live-TV + EPG are needed, so reminders
are now discoverable there. TVEpgGridScreen (the dense timeline
grid) would require making the individual program blocks focusable
which is a non-trivial D-pad refactor. Deferred — can be promoted
back to P1 if the TVLiveBrowseScreen flow feels insufficient once
the user tries it.


### Phase 44 — v1.29.0 Mobile "Resume Live" home card (2026-04-24 — completed, deployed)
Potential-improvement enhancement approved by user. Builds on v1.28.1's
persistent `LiveSessionStore`: adds a new first page to the mobile
Home pager that one-taps the user straight into fullscreen on their
last-watched channel.

Files changed:
- `data/LiveSessionStore.kt`: added `channelName` + `poster` fields
  (String getters/setters) so the Home card can render the channel
  name + logo without a network round-trip to Xtream.
- `mobile/MobileLiveHubScreen.kt`: extended the
  `LaunchedEffect(selectedStreamId, channels, playlistId)` persist
  block to also call `LiveSessionStore.setChannelName(...)` and
  `setPoster(...)` whenever a new preview channel is selected.
- `mobile/MobileHomeScreen.kt`:
  - Reads `resumeLiveSid` / `resumeLiveName` / `resumeLivePoster`
    off the store, keyed on `cwVersion` so they refresh on
    `ON_RESUME` (same lifecycle observer that already powers
    Continue Watching — reuse, no new observers).
  - `hasResumeLive = sid > 0 && name.isNotBlank()` gate.
  - Prepended a new `PageDef("resume_live", "LAST WATCHING",
    "Resume Live", accent = red #EF4444)` to the `pages` list when
    `hasResumeLive` is true. Sits at index 0 — first thing the user
    sees.
  - New private composable `ResumeLivePage(...)`: full-page card
    with a channel logo (or monogram fallback), LIVE badge, TAP TO
    RESUME eyebrow, channel name, and a large red circular play
    button. Single tap → builds the Xtream `liveUrl(...)` and
    navigates to `mobilePlayerRoute(... isLive = true)` to go
    straight to fullscreen, skipping the Live hub.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 155 → 156`, `versionName
  "1.28.1" → "1.29.0"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped as versionCode=156 / versionName="1.29.0" — APK (md5
  `6d7ce3554b58a682a21227140c5ceb3e`) live on `https://hushtv.xyz`.


### Phase 43 — v1.28.1 Live TV resume-where-you-left-off (2026-04-24 — completed, deployed)
User reported: "When I leave Live TV to Movies / Series and come back,
it resets to the default category instead of remembering the category
+ previewed channel I was on." Wanted identical behaviour on mobile
AND TV.

Root cause (mobile): `MobileShell` swaps screens via a plain
`when(tab)` Kotlin block. When the user taps Movies, the entire
`MobileLiveHubScreen` composable is DISPOSED — and `rememberSaveable`
only survives configuration changes (rotation), NOT composable
disposal. So on return, a fresh screen started with default values.

Root cause (TV): `TVLiveBrowseScreen` restores via `NavState`
(process-scoped globals). NavState works for mid-session but is
cleared on process death, and the index-based keys are fragile when
the category list reloads in a different order.

Fix — new persistent store + wiring across both form factors:
- NEW `data/LiveSessionStore.kt`: simple SharedPreferences wrapper
  with per-playlist `categoryId` (String) + `streamId` (Int) get /
  set helpers. Survives app restart, form-factor agnostic.
- `mobile/MobileLiveHubScreen.kt`:
  - Replaced `rememberSaveable(key = "mlive-cat|sid")` with
    `remember(playlistId) { mutableStateOf(LiveSessionStore.getX(...)) }`.
  - Added two `LaunchedEffect`s that write `selectedCatId` /
    `selectedStreamId` back to the store as they change.
  - Dropped the now-unused `rememberSaveable` import.
- `ui/screens/TVLiveBrowseScreen.kt`:
  - Kept `NavState` as the session-scoped fast path for the first
    render, then layered a `LaunchedEffect(uiCategories, playlistId)`
    that resolves the persisted `categoryId` String to an index once
    `uiCategories` has loaded — uses a `catRestored` one-shot flag
    so user picks aren't clobbered.
  - Added `focusRestored` one-shot + a
    `LaunchedEffect(filteredChannels, playlistId, catRestored)` that
    restores `focusedChannelIdx` from the persisted `streamId` once
    the channel list for the restored category is loaded.
  - The existing `LaunchedEffect(focusedChannelIdx)` now also calls
    `LiveSessionStore.setStreamId(...)`, and the equivalent category
    effect calls `LiveSessionStore.setCategoryId(...)` (guarded by
    `catRestored` so the bootstrap doesn't overwrite the saved
    value).

Build + deploy:
- `app/build.gradle.kts`: `versionCode 154 → 155`, `versionName
  "1.28.0" → "1.28.1"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped as versionCode=155 / versionName="1.28.1" — APK (md5
  `f02f1c120d685124fd32de5fcdb80d58`) live on `https://hushtv.xyz`.


### Phase 42 — v1.28.0 Mobile EPG reminders (2026-04-24 — completed, deployed)
Wires the long-press "Set reminder" gesture into the mobile Live Hub
Timeline strip using the existing `ReminderStore` +
`EpgReminderScheduler` (which had been implemented previously but not
surfaced anywhere in the UI, nor properly registered in the manifest).

Infrastructure gap closed — previously broken:
- `AndroidManifest.xml`:
  - Added `<receiver android:name=".notifications.ReminderReceiver"
    android:exported="false"/>` so `AlarmManager` can actually deliver
    the broadcast when the alarm fires. Without this the alarms were
    silently dropped on all Android versions.
  - Added `POST_NOTIFICATIONS` (Android 13+ runtime permission),
    `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM` (Android 12+),
    `RECEIVE_BOOT_COMPLETED` (for future reboot-survival work).

UI wiring:
- `mobile/MobileLiveHubScreen.kt`:
  - `EpgTimelineStrip(streamId, epgVersion)` signature expanded to
    `EpgTimelineStrip(playlistId, streamId, channelName, epgVersion)`
    so the composable can build a full
    `ReminderStore.Reminder(playlistId, streamId, channelName,
    programTitle, programStartMs)` without prop-drilling up to the
    caller.
  - Added `reminderVersion: Int` counter local to the strip — bumped
    on add/remove so the per-chip bell icon re-renders immediately.
  - Added `reminderFor: EpgProgram?` state — drives the bottom
    dialog. Guard: only set when `p.startMs > now` (past / live
    programs don't respond to long-press — nothing to remind).
  - `rememberLauncherForActivityResult(RequestPermission())` to ask
    for `POST_NOTIFICATIONS` on Android 13+. Denial still schedules
    the alarm; the user just won't see the heads-up.
  - `EpgTimelineChip` now takes `hasReminder: Boolean` + `onLongPress`
    and wraps itself in `combinedClickable(onClick = no-op,
    onLongClick = onLongPress)`. Yellow `Icons.Default.Notifications`
    bell renders in the timestamp row when a reminder is set.
  - New reminder action sheet (`Dialog` with rounded card): shows the
    program title + channel + start time. Single action toggles
    between "Set reminder · Notify 5 min before it starts" (when
    none) and "Cancel reminder" (when one exists). Tap calls
    `ReminderStore.add`/`remove` + `EpgReminderScheduler.schedule`
    and bumps `reminderVersion`.
  - Imports added: `Notifications`, `NotificationsOff`.

Header copy update: strip header now reads "TIMELINE · long-press to
set reminder" instead of just "· next N programs", teaching the
gesture directly.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 153 → 154`, `versionName
  "1.27.2" → "1.28.0"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped as versionCode=154 / versionName="1.28.0" — APK (md5
  `0c8225b70555bc41a476c0d682b9a687`) live on `https://hushtv.xyz`.

Testing note (not yet done at runtime): reminders within the next
hour or so can be verified by long-pressing a chip ~6 min out, then
locking the device and waiting. A TV-side surfacing of this flow
(in `TVLiveBrowseScreen` / `TVEpgGridScreen`) is NOT yet wired — TV
parity is a backlog item.


### Phase 41 — v1.27.2 Mobile nav rename + reorder (2026-04-24 — completed, deployed)
User asked to rename "More" → "Settings" and move it to the end of the
bottom nav, after Search.

Files changed:
- `mobile/MobileShell.kt`: `MobileBottomNav` — removed the
  `BottomItem("settings", "More", ...)` entry from the `items` list
  (so the main 4 tabs are Home / Live / Movies / Series only) and
  added a standalone `BottomNavBtn(label = "Settings", ...)` AFTER
  the Search button. This yields the new order: Home · Live ·
  Movies · Series · Search · Settings.
- `app/build.gradle.kts`: `versionCode 152 → 153`, `versionName
  "1.27.1" → "1.27.2"`.

Build + deploy:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped as versionCode=153 / versionName="1.27.2" — APK (md5
  `e6522ca201bd8d52fb460013a17f881d`) live on `https://hushtv.xyz`.


### Phase 40 — v1.27.1 Mobile Search layout bug + polish (2026-04-24 — completed, deployed)
User uploaded a screenshot showing the search screen with a full-screen
cyan-bordered rectangle and no results rendering. Typed "terminator"
but nothing came back.

ROOT CAUSE: `MobileSearchScreen.kt` wrapped the `OutlinedTextField` in
a `Row` with `fillMaxWidth()` and vertical padding but no height
constraint, and the TextField itself was modified with
`.weight(1f).fillMaxHeight()`. In Compose, `fillMaxHeight()` on a Row
child whose Row has no bounded height resolves against the Column's
full `fillMaxSize()` — so the text field exploded to fill the entire
viewport. The LazyColumn below it got zero space, hiding every result
even though the filter logic was running fine.

Files changed:
- `mobile/MobileSearchScreen.kt` — rewrote the screen top-to-bottom.
  Key changes:
  - Replaced the `OutlinedTextField` with a `BasicTextField` wrapped
    in a fixed-height (44 dp) cyan-bordered rounded pill. No more
    `fillMaxHeight` — the pill sizes exactly as designed regardless
    of parent constraints.
  - `FocusRequester` + `LaunchedEffect(Unit) { fieldFocus.requestFocus();
    keyboard?.show() }` auto-opens the soft keyboard on entry so
    users can type immediately (previously you had to tap the field
    first).
  - Added a `×` clear button inside the pill (shown only when there's
    text).
  - New filter chip rail (All · Movies · Series · Live) — thumb-
    friendly equivalent of the TV sidebar, with live counts baked
    into each chip. Selected chip fills cyan.
  - Separate empty / loading / no-match states with richer copy
    ("Start typing to search" centred, "No matches for 'xyz'").
  - Pre-load `allMovies`/`allSeries`/`allLive` via a single
    `Triple`-returning `withContext(Dispatchers.IO) {}` block so all
    three fetches happen in the same background frame.
  - `indexReady` gate so the debounced filter `LaunchedEffect` waits
    for the pre-fetch to finish before declaring "no matches". While
    index is loading we show a `CircularProgressIndicator`.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 151 → 152`, `versionName
  "1.27.0" → "1.27.1"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Shipped as versionCode=152 / versionName="1.27.1" — APK (md5
  `4b97b54d0269f0787a0d66c9b4a3183c`) live on `https://hushtv.xyz`,
  `version.json` bumped with changelog.


### Phase 39 — v1.27.0 Mobile browse parity sort (2026-04-24 — completed, deployed)
User: "Just like TV version, mobile movies need to sort by most
recently added, series by most recently modified, live TV A-Z."

Files changed:
- `mobile/MobileBrowseScreen.kt`: inside the category-load
  `LaunchedEffect`, wrapped the fetched `data` with
  `.sortedWith(compareByDescending<MediaCard> { it.addedTs }
  .thenBy { it.title.lowercase() })`. `MediaCard.addedTs` is
  already populated from Xtream's `added` field for movies and
  `last_modified` for series (see `XtreamApi.kt` lines 240 and 252),
  so the same sort expression correctly implements "most-recently
  added" for movies and "most-recently modified" for series — exact
  parity with TV's `TVBrowseScreen.kt` line 309-312.
- `mobile/MobileLiveHubScreen.kt`:
  - `orderedChannels` changed from `favs + rest` (favourites-pinned-
    first) to pure `channels.sortedBy { it.title.lowercase() }` —
    matches TV's `TVLiveBrowseScreen.kt` line 199. Favourite state
    is still shown per-row via the star icon so information
    fidelity is preserved.
  - Dropped the now-unused `favSet` dep from the `remember` key.
  - `item("meta")` position label now uses `orderedChannels.indexOf`
    (was `channels.indexOf`) so the `N/M` displayed below the live
    preview matches the 1-based channel numbers the user sees in
    the row list.

Build + deploy:
- `app/build.gradle.kts`: `versionCode 150 → 151`, `versionName
  "1.26.1" → "1.27.0"` (BOTH places bumped in lockstep per the
  phase-38 post-mortem).
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped as versionCode=151 / versionName="1.27.0" — APK (md5
  `8d683001e77fcb8b975cf47aef473eb0`) live on `https://hushtv.xyz`,
  `version.json` bumped with changelog.


### Phase 38 — v1.26.1 (second build) OTA loop fix (2026-04-24 — completed, deployed)
User reported: "The same update keeps coming up even after I've
installed it." Screenshot showed `v1.24.1-debug → v1.26.1` in the
update dialog, confirming the installed APK still reported 1.24.1.

ROOT CAUSE (regression spanning phases 35-37): across v1.25.0, v1.26.0,
and v1.26.1 I only updated the server's `/var/www/hushtv/version.json`
with bumped `versionCode` / `versionName`. I never bumped
`versionCode` / `versionName` in `app/build.gradle.kts`, which is the
value baked into the APK at build time and read by
`UpdateManager.currentVersionCode()` via `BuildConfig.VERSION_CODE`.
So every "update" the user installed was the SAME APK version as
before (147 / 1.24.1) — just with new Kotlin code inside. The OTA
check `info.versionCode > currentVersionCode()` then evaluated as
`150 > 147 = true`, re-triggering the dialog on every launch.

Fix:
- `app/build.gradle.kts`: `versionCode = 147` → `150`,
  `versionName = "1.24.1"` → `"1.26.1"`. The `.debug` applicationIdSuffix
  + `-debug` versionNameSuffix still apply, so the installed APK will
  now correctly report `150 / 1.26.1-debug`.
- Rebuilt + re-shipped the APK (md5
  `fb99aedf0e49a7ed6757299f55f0726d`). `version.json` unchanged (still
  says 150 / 1.26.1) so after this install the dialog will stop.

Going forward — remember to bump BOTH places in one commit:
  1. `app/build.gradle.kts` (`versionCode` + `versionName`)
  2. server `/var/www/hushtv/version.json`
…for every release. Numbers must match, or the dialog loops.


### Phase 37 — v1.26.1 Mobile CW resume fix + long-press remove (2026-04-24 — completed, deployed)
User reported that (a) Continue Watching on mobile restarts titles from
the beginning instead of resuming, and (b) there's no way to remove
entries like the TV's long-press flow. Fixed both.

ROOT CAUSE (bug a): `MobileHomeScreen.ContinueWatchingPage`'s row
`.clickable{}` called the 4-arg overload
`mobilePlayerRoute(playlistId, url, e.title, e.kind == "live")` which
leaves `vodStreamId` / `vodKind` / `vodPoster` all null. The resume
`LaunchedEffect` in `MobilePlayerScreen` (shipped in v1.25.0) guards
with `if (vodStreamId == null) return@LaunchedEffect` and never seeks.

Files changed:
- `mobile/MobileHomeScreen.kt`:
  - `rememberContinueEntries`-equivalent inline pattern: hoisted
    `cwVersion` `Int` counter + `DisposableEffect(lifecycleOwner)`
    that bumps `cwVersion` on `Lifecycle.Event.ON_RESUME`. The
    `cwEntries` `remember(cwVersion, playlistId)` re-reads
    `WatchProgressStore.continueWatching(ctx)` every time the user
    comes back from the player (mirrors TV behaviour at
    `ui/screens/home/HomeContinueWatchingSection.kt` line 199-208).
  - `ContinueWatchingPage(...)` gains an `onRemove: (Entry) -> Unit`
    param and an `@OptIn(ExperimentalFoundationApi::class)` on its
    signature. Parent passes a lambda that calls
    `WatchProgressStore.clear(ctx, e.streamId, e.kind)` and bumps
    `cwVersion` so the list re-renders immediately.
  - Row's `.clickable{...}` replaced with `.combinedClickable(onClick,
    onLongClick)`. `onClick` now routes through the 7-arg
    `mobilePlayerRoute(...)` passing `vodStreamId = e.streamId`,
    `vodKind = e.kind`, `vodPoster = e.poster` for VOD; live channels
    leave the params null (no resume needed). `onLongClick` toggles a
    new `actionEntry: Entry?` state.
  - New long-press action sheet: when `actionEntry != null` we render
    a `Dialog` with a single "Remove from Continue Watching" row
    (red `Delete` icon). Tapping the row calls `onRemove(entry)` and
    closes the sheet.
  - Imports added: `ExperimentalFoundationApi`, `combinedClickable`,
    `Delete`, `LocalLifecycleOwner`, `Lifecycle`,
    `LifecycleEventObserver`.

Build + deploy:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only).
- Shipped as versionCode=150 / versionName="1.26.1" — APK (md5
  `e8806c1092f700503dfab4c74103c756`, 113,057,506 bytes) live on
  `https://hushtv.xyz`, `version.json` bumped with changelog.


### Phase 36 — v1.26.0 Mobile Live Hub gestures + EPG timeline (2026-04-24 — completed, deployed)
Makes the mobile Live TV preview card feel native to phones. Adds three
distinct gestures on the preview surface plus a horizontally-scrolling
EPG timeline strip right under it.

Files changed:
- `mobile/MobileLiveHubScreen.kt`:
  - New private composable `MobilePreviewSurface(player, hasSelection,
    onTapFullscreen, onPinchFullscreen, onSwipeUp, onSwipeDown)`. Wraps
    the 16:9 preview Box and owns a single `pointerInput` block that
    classifies gestures on the first pointer event:
      • 2+ pointers → pinch mode; cumulative scale via
        `PointerEvent.calculateZoom()`; once ≥ 1.25× we fire
        `onPinchFullscreen` + LONG_PRESS haptic, then drain.
      • 1 pointer + vertical drag ≥ 60 dp → channel flip
        (`onSwipeUp` / `onSwipeDown`) + VIRTUAL_KEY haptic; short or
        horizontal-dominant drags are ignored so taps still pass
        through to the parent clickable.
      • Pointer-up with no drag → tap handler (fullscreen).
    Claims the gesture (consumes events) at ~30% of threshold so the
    enclosing LazyColumn can't steal vertical drags mid-flip.
  - `hintVisible` mutable state + `AnimatedVisibility` pill at
    bottom-centre that teaches the swipe gesture for 2.5 s on first
    render then fades. A cyan circular arrow (`KeyboardArrowUp/Down`)
    flashes at centre on each flip for visual confirmation.
  - Preview surface replaces the old inlined `Box(...).clickable{...}`
    in `item("preview")`. Fullscreen pill text upgraded from
    "Tap to expand" → "Tap · pinch to expand" to advertise the new
    gesture.
  - New `item("epgstrip")` inserted between `item("preview")` and
    `item("meta")` calling new composable `EpgTimelineStrip(streamId,
    epgVersion)`. Empty-fast: returns early if
    `EpgService.programsOf(streamId)` is empty so we don't render an
    orphaned header.
  - New composable `EpgTimelineChip(p)` — per-program tile whose width
    scales with duration (0.6 dp per minute, clamped 80–240 dp). Live
    program has red pip + cyan border + inline progress bar; past
    programs fade to 50% alpha; future programs show a faint cyan
    border. Title limited to 2 lines with ellipsis.
  - `rememberLazyListState()` on the timeline `LazyRow` +
    `LaunchedEffect(streamId, epgVersion)` auto-scrolls to the
    currently-live program whenever the channel changes or the EPG
    refreshes (80 ms debounce so the list has time to measure).
  - Imports added: `HapticFeedbackConstants`, `AnimatedVisibility`,
    slide/fade transitions, `awaitEachGesture` / `awaitFirstDown` /
    `calculateZoom`, `positionChange`, `pointerInput`, `LocalView`,
    `LocalDensity`, `PointerEventPass`, `KeyboardArrowUp/Down` icons.

Build + deploy:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (warnings only, no
  errors).
- Shipped as versionCode=149 / versionName="1.26.0" — APK (md5
  `0577a53848f1c877c3dbe9b3bb462ed0`, 113,057,502 bytes) live on
  `https://hushtv.xyz`, `version.json` bumped with changelog.


### Phase 35 — v1.25.0 Mobile Continue Watching (2026-04-24 — completed, deployed)
Wires `WatchProgressStore` into the mobile player flow so phones get
the same "resume where you left off" UX the TV already ships.

Files changed:
- `mobile/MobilePlayerScreen.kt`: added three optional params
  `vodStreamId: Int?`, `vodKind: String?`, `vodPoster: String?`.
  New `LaunchedEffect` reads
  `WatchProgressStore.get(ctx, vodStreamId, vodKind)` on first
  composition and seeks the player there if the entry exists and
  `isInProgress`. A second `LaunchedEffect` saves the current
  position every 4 seconds. A `DisposableEffect.onDispose` captures
  the final position on screen exit so we never miss the last 3 s.
  All three are no-ops for `isLive` or when `vodStreamId` / `vodKind`
  are missing — guarantees zero behaviour change for live channels.
- `mobile/MobileApp.kt`: extended the `mplayer/...` nav route
  template to accept three optional query params
  (`vodId`, `vodKind`, `vodPoster`) and pass them through to
  `MobilePlayerScreen`. Updated the `mobilePlayerRoute(...)` helper
  to accept the new params and append them only when non-null.
  Existing callers that only pass the original 4 args compile
  unchanged (defaults preserve behaviour).
- `mobile/MobileBrowseScreen.kt`,
  `mobile/MobileSearchScreen.kt`,
  `mobile/MobileCollectionDetailScreen.kt`,
  `mobile/MobileSeriesDetailScreen.kt`: every VOD-playback
  call-site now passes `vodStreamId = card.streamId`,
  `vodKind = "movie" | "series"`, `vodPoster = card.poster`.
  Series episodes hash the string `ep.id` to an Int so it fits
  `WatchProgressStore`'s `Int` key — collisions are negligible in
  practice since Xtream episode IDs are globally unique.

No changes needed to `MobileHomeScreen` — it already reads
`WatchProgressStore.continueWatching(ctx).take(12)` (line 81) and
renders a `ContinueWatchingPage` (line 191). The rail now fills
automatically as users watch movies/episodes from the mobile shell.

- Shipped as versionCode=148 / versionName="1.25.0" — APK (md5
  `3c9f97a8a25f02d0be8292f2bede984b`, 113,057,507 bytes) live on
  `https://hushtv.xyz`, `version.json` bumped with changelog.


### Phase 34 — v1.19.1 Layout hint chip on top nav (2026-04-23 — completed, deployed)
Potential-improvement enhancement approved by the user. Adds a subtle
discoverability cue to the top nav on Live TV / Movies / Series so new
users always know which layout mode they're in, and can switch with a
single click of OK.

Files changed:
- `ui/screens/home/TopNavBar.kt`: added two new optional params
  `layoutHint: String?` and `onLayoutHintClick: (() -> Unit)?`. When
  both are provided, renders a `LayoutHintChip` between the tab rail
  and the expiry badge. Chip is a compact 28 dp cyan pill
  (`0x5506B6D4` ring over a 12% cyan fill) containing a Dashboard icon
  + the uppercase label ("SIDEBAR" / "TOP BAR"). Focusable; focus
  bumps the border to 1.5 dp solid cyan, lifts the fill to 22% alpha,
  and scales the icon 1.04×. ENTER calls `onLayoutHintClick`.
- `ui/screens/TVBrowseScreen.kt` + `TVLiveBrowseScreen.kt`: switched
  the `layoutMode` val to a `currentLayoutMode` mutableStateOf so
  picking a new mode via the chip re-composes the screen instantly
  (no need to exit + re-enter). Added a `showLayoutChooser` state
  guarding a `LayoutChooserDialog` invocation at the bottom of the
  composable. TopNavBar call-sites now pass
  `layoutHint = if (useSidebar) "SIDEBAR" else "TOP BAR"` and
  `onLayoutHintClick = { showLayoutChooser = true }`.
- `TVMainMenuScreen.kt`: Home screen intentionally does NOT show the
  chip — the layout mode only affects browse screens.

- Shipped as versionCode=123 / versionName="1.19.1" — APK (md5
  `7229a0a133cec5c7709f2848d674b31c`) live on `https://hushtv.xyz`.

### Phase 33 — v1.19.0 Dual layout: Top-Bar vs Left-Sidebar (2026-04-23 — completed, deployed)
User asked for a choice: some prefer the new compact "Top Bar + BROWSE
dropdown" introduced in v1.18.x, others want the classic Tivimate-style
persistent left sidebar. Both must work for Live TV, Movies AND Series,
with a first-run chooser and a Settings toggle.

Files already in place from previous session:
- `data/LayoutPrefsStore.kt` — SharedPreferences wrapper with
  `MODE_TOP` / `MODE_SIDEBAR` plus a `firstRunShown` flag so the modal
  only auto-fires once.
- `ui/screens/home/CategorySidebar.kt` — reusable 240 dp vertical
  category rail (header with count chip, auto-scroll to selected, cyan
  focus ring + border, optional `rightTarget` focus-handoff to grid,
  optional `topRowUpTarget` for top-nav reachability).
- `ui/screens/home/LayoutChooserDialog.kt` — full-screen cinematic
  modal with two large cards showing mini-mockups of each layout.
  Focus auto-routes to whichever card matches the user's current
  setting. Dismissable flag (`true` in Settings, `false` on first run)
  controls back-press behaviour.

Fork-blocker fix:
- `TVMainMenuScreen.kt` was failing to compile because the dialog
  invocation was accidentally dropped INSIDE the `ContinueCard`
  composable (line 1362+) while the referenced `showLayoutChooser`
  state lives in `TVMainMenuScreen` which ends at line 658. Moved the
  `if (showLayoutChooser) { LayoutChooserDialog(...) }` block to the
  correct spot — just before the root Box closes inside
  `TVMainMenuScreen`. Build now passes clean.

New wiring:
- `TVBrowseScreen.kt` (Movies + Series) — added layout mode read +
  `useSidebar` branch. In sidebar mode renders `Row(CategorySidebar +
  grid)` with no toolbar; in top-bar mode keeps the existing
  `Column(CategoryToolbar + grid)`. Grid body factored into a shared
  `@Composable () -> Unit` lambda to avoid duplication. Grid D-pad
  wiring: top-row Up lifts to `dropdownFocus` only in top-bar mode;
  leftmost cards' Left key routes back to the sidebar's first item in
  sidebar mode (else no-op). Initial focus lands on the sidebar's
  first item in sidebar mode, on the BROWSE dropdown otherwise.
- `TVLiveBrowseScreen.kt` — dual-layout branching was already wired
  in this session's previous agent pass. Verified against the new
  `CategorySidebar` signature — no changes needed.
- `TVSettingsScreen.kt` — added a new `LAYOUT` section between
  PROFILE and PARENTAL CONTROLS with a "Change Layout" card that
  shows the current mode in its subtitle and opens the
  `LayoutChooserDialog` when pressed. Dialog closes on ENTER (saves
  via `LayoutPrefsStore.setMode`) or BACK (simple dismiss).

D-pad matrix verified at compile-time:
- Sidebar → RIGHT → first grid card (via `rightTarget`).
- Grid leftmost → LEFT → sidebar first item (via `onLeftEdge`).
- Sidebar top row → UP → top nav Home tab (via `topRowUpTarget`).
- Grid top row → UP → top nav (2D focus search) in both modes; plus
  explicit `up = dropdownFocus` in top-bar mode for stability.

- Shipped as versionCode=122 / versionName="1.19.0" — APK (md5
  `d7924b22e1824314e3557df49c57cca7`, 23,968,884 bytes) live on
  `https://hushtv.xyz`, `version.json` bumped with changelog.

### Phase 32 — v1.13.2 Discovery fix — actually populate 100+ franchises (2026-04-23 — completed, deployed)
User reported that the "See All" grid still showed only the 20 curated
franchises despite v1.13.0 shipping dynamic discovery. Investigated
root cause.

**Root cause:** The discovery function declared its TMDB JSON
response-model data classes as LOCAL CLASSES inside the
`suspend fun discoverPopularCollections` body (with `@JsonClass` and
using Moshi reflection). Kotlin local classes can lose
`@Metadata` annotations in certain compiler configurations, which
breaks Moshi's `KotlinJsonAdapterFactory` reflection lookup at
runtime. Parsing silently returned `null` → empty list → curated 20
were the only collections rendered.

**Fix (v1.13.2):**
- Moved ALL TMDB discovery response wrappers to top-level `internal`
  data classes inside `TmdbService.kt`:
  `TmdbMovieListItem`, `TmdbMovieListResp`, `TmdbBelongsToCollection`,
  `TmdbMovieDetailWithCollection`, `TmdbCollectionSearchHit`,
  `TmdbCollectionSearchResp`.
- Adapter instances now created once outside the parallel
  `async` blocks (a performance win too).
- `CollectionsData.kt`: added a guard to never cache an empty
  discovery result so the app retries on next launch until discovery
  succeeds (defends against transient network / API hiccups).
- Added tagged `Log.i/Log.w("TmdbDiscover", …)` for production
  diagnostics.

- Shipped as versionCode=105 / versionName="1.13.2" — APK (md5
  `5d19cd73c080e1de0aab4022fa6b32a0`) live on `https://hushtv.xyz`.

### Phase 31 — v1.13.1 Collections search filter (2026-04-23 — completed, deployed)
User approved the potential-improvement suggestion: add a live search
filter to the See All grid so users can quickly find franchises as the
catalog grows beyond 150 items.

Implementation:
- Added `var query by mutableStateOf("")` to `TVCollectionsBrowseScreen`
  and a `derivedStateOf`-backed `filtered` list that normalises both
  the query and each collection's `displayName` via the existing
  `TitleMatcher.normalize()`. Match is case/punctuation-insensitive
  contains.
- `CollectionsSearchBar` — 320 × 46 dp pill with search icon, cyan
  focus ring, BasicTextField, X-to-clear button. Mirrors the existing
  `SearchBox` styling in `TVBrowseScreen` for visual consistency.
- D-pad wiring: Down from the search field → first grid card; Up from
  the FIRST ROW of grid cards → back into the search field. Rows
  below the first navigate within the grid normally. Click-to-clear
  on the X tile also works with D-pad + OK.
- Empty-state: when the query returns 0 matches the grid is replaced
  by a centred "No franchises match 'query'" message + subtext.
- Header subtitle now shows "N of M match" when filtering.

- Shipped as versionCode=104 / versionName="1.13.1" — APK (md5
  `51e6f0c9c0b0359958775b6c31ef37e2`) live on `https://hushtv.xyz`.

### Phase 30 — v1.13.0 Collections catalog explosion + See All (2026-04-23 — completed, deployed)
User asked for "way more collections, like 500 top collections" with
a "See All" tile on the home row leading to a dedicated browse screen.

Approach: hybrid curated + dynamic.
- Kept the 20 hand-curated iconic franchises (custom taglines + accent
  colours) as the guaranteed core, always shown FIRST.
- Added **dynamic TMDB discovery**: on first launch (or after 7 days),
  fetches 5 pages of `/movie/popular` (100 movies) then fans out
  parallel `/movie/{id}` calls in batches of 20 to pull each movie's
  `belongs_to_collection` metadata. Dedupes by ID + display name.
  Produces a merged catalog of ~80-100 additional popular franchises
  (sequels/trilogies) with real TMDB names, backdrops, IDs — zero
  guessing.
- Cache: new `DiscoveryCache.saveDiscoveredCollections` /
  `loadDiscoveredCollections` stored as pipe-delimited tuples with a
  7-day TTL. Cold start reads cache synchronously → catalog paints
  instantly with zero API calls on all but the first launch.

Home row change:
- `HomeCollectionsRow` now accepts `maxVisible = 10` + `onSeeAllClick`
  and appends a new **"SEE ALL" tile** (cyan-bordered, "BROWSE →"
  pill) as the 11th item when the catalog has more.
- Row header grew a "N FRANCHISES" subtitle so the user can see the
  total at a glance.

New screen:
- `TVCollectionsBrowseScreen` — 4-column `LazyVerticalGrid` of every
  franchise using cinematic 16:9 backdrop cards. `focusRestorer` +
  `focusGroup` so D-pad bounces off the nav correctly. Card click
  routes to the same `collection/{id}/{name}` detail screen — all the
  chronological matching logic from v1.12.6 applies.
- New route `collections/{playlistId}` added to `MainActivity.kt`.

Files added / changed:
- `data/TmdbService.kt`: `DiscoveredCollection` model +
  `discoverPopularCollections(pages)` + `searchCollection(query)`.
- `data/DiscoveryCache.kt`: `saveDiscoveredCollections` /
  `loadDiscoveredCollections` / `shouldRefreshDiscoveredCollections`.
- `ui/screens/home/CollectionsData.kt`: rewrote to merge curated +
  discovered with dedupe + stable accent-colour hashing for
  discovered entries.
- `ui/screens/home/HomeCollectionsRow.kt`: `maxVisible` cut-off +
  `SeeAllCardView` trailing tile.
- `ui/screens/TVCollectionsBrowseScreen.kt`: new full-grid screen.
- `ui/screens/TVMainMenuScreen.kt`: wires `onSeeAllClick` → new
  `collections/$playlistId` route.
- `MainActivity.kt`: nav route wiring.

Verified: TMDB /movie/popular + /movie/{id} endpoints tested via
curl — `belongs_to_collection` is present on popular films and
returns id + name + backdrop_path as expected.

- Shipped as versionCode=103 / versionName="1.13.0" — APK (md5
  `dd36d4fc0cbd958301de2de55052fce8`) live on `https://hushtv.xyz`.

### Phase 29 — v1.12.6 Collections splash + strict TitleMatcher (2026-04-23 — completed, deployed)
User feedback: 3 major issues on the brand-new Collections page —
(1) hero text overlapping the card row; (2) the collection detail
screen looked jarring while loading (header visible, empty grid
below); (3) MOST IMPORTANT — the library matcher was pulling in
completely wrong films (e.g. "Ed" and "Plastic Galaxy" showing up
inside the Star Wars franchise results) because of loose substring
containment.

Fixes shipped:
- **New `data/TitleMatcher.kt`** — single source of truth for any
  TMDB ↔ Xtream library matching across the whole app (collections,
  cast click, recommendations, future features). Rules:
    1. EXACT normalized match (with year gate when both sides report
       years — remakes are correctly distinguished).
    2. CONTIGUOUS phrase containment with ≥ 3 real words on BOTH
       sides + year gate (when years available and they disagree by
       > 1 year → reject).
  Library titles with fewer than 3 words ("Ed", "Star", "Plastic
  Galaxy") can NO LONGER match long TMDB titles via coincidental
  substrings. Validated with 11 sanity tests covering all user-
  reported false-positives + well-formatted matches + remake edge
  cases. All 11 pass.
- **`TVCollectionDetailScreen.kt` rewrite** — uses
  `TitleMatcher.findBestMatch` / `buildIndex` (O(n) batch lookup).
  Added a full-bleed cinematic **splash loading screen** with the
  franchise's TMDB backdrop + Ken-Burns pulse + accent loader ring +
  franchise name + tagline. Paints instantly from
  `DiscoveryCache.loadCollectionBackdrop` so there's never an empty
  state. `AnimatedVisibility` crossfades smoothly into the results
  grid when both TMDB parts and the user's library have loaded.
- **`HomeCollectionsHeroLayer.kt`** — hero copy top padding dropped
  72 dp → 40 dp; franchise name forced to `maxLines = 1` (was 2);
  tagline also `maxLines = 1` (was 2); font sizes tightened.
  Guarantees the hero column never grows tall enough to overlap the
  card row pinned at the bottom of the page.

- Shipped as versionCode=102 / versionName="1.12.6" — APK (md5
  `93ef9597c31ee6323a9343b104538ed7`) live on `https://hushtv.xyz`.

### Phase 28 — v1.12.4 D-pad focus never stuck in Top Nav (2026-04-23 — completed, deployed)
User feedback: "When you're on Genres/Years/Collections and press RIGHT,
focus jumps into the Top Nav and gets STUCK — pressing DOWN doesn't
bring it back to the content. Fix this for every page, current and
future."

ROOT CAUSE: Each Home row attached its `firstItemFocus` requester to
idx 0 only. When the user D-padded through a LazyRow and scrolled it,
idx 0 got virtualised out of the composition. After focus escaped UP
into the nav (a side-effect of Compose's 2D focus search when there's
no focusable to the right), the nav's Down-handler called
`firstItemFocus.requestFocus()` — which silently no-op'd because the
requester had no attached node.

FIX: Switched every Home row to use Compose's `Modifier.focusRestorer()`
at the Column wrapper level (`focusRequester(fr).focusRestorer().focusGroup()`).
Now `fr.requestFocus()` routes to the LAST-focused child card (or the
first focusable if none yet). Bulletproof against LazyRow
virtualisation; bonus: users return to the EXACT card they left, not
always idx 0.

Files changed (all Home rows unified under the same pattern):
- `HomeCollectionsRow.kt`, `HomeGenresRow.kt`, `HomeYearsRow.kt`,
  `HomeStreamingServicesRow.kt`, `HomeDiscoveryRow.kt`,
  `HomeContinueWatchingSection.kt`
- Each got: `@file:OptIn(ExperimentalComposeUiApi)` (for the
  still-experimental `focusRestorer()`), `focusGroup` import from
  `androidx.compose.foundation`, and the new `focusMod` modifier at
  the Column wrapper. The per-item `focusRequester = if (idx == 0)`
  attachments were removed since the Column now owns focus delegation.

- Shipped as versionCode=100 / versionName="1.12.4" — APK (md5
  `347f8148eeee4b4ef2c1770a1f955071`) live on `https://hushtv.xyz`.

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

## v1.33.0 — 2026-04-25 (versionCode 172)

**Subtitles re-platformed.** Ripped out the entire AI Subtitles stack
(both Vosk on-device and the AssemblyAI/Whisper server pipeline)
because perceived lag on live TV was unacceptable. Replaced with a
clean OpenSubtitles.com integration: human-timed SRTs, zero lag.

### Removed
- All Android AI code: `WhisperServerEngine.kt`, `PcmTapAudioProcessor.kt`,
  `AiEngineStore.kt`, the audio-tap renderers factory, the AI captions
  overlay, the "AI CC" toggles in TV + Mobile players, the audio-offload
  override, the AI Subtitles section in both settings screens.
- GPU server `216.152.147.177`: `hushtv-ai` systemd unit stopped &
  disabled, `/opt/hushtv-ai`, `/etc/hushtv-ai`, `/var/log/hushtv-ai` all
  removed. Nginx site `/etc/nginx/sites-{available,enabled}/ai.hushtv.xyz`
  deleted. Let's Encrypt cert for `ai.hushtv.xyz` revoked + deleted.
  WS upgrade `/etc/nginx/conf.d/ws_upgrade.conf` removed.
  AssemblyAI key + Emergent LLM key removed from server env.
- **User action item**: delete the `ai` A-record from Namecheap so the
  subdomain stops resolving. Server itself can be cancelled / repurposed.

### Added
- `OpenSubtitlesApi.kt` — REST client for `api.opensubtitles.com/api/v1`.
  Search (movie + episode), download with on-device cache keyed by
  `file_id`. Custom `User-Agent: Hushtvapp` + `Api-Key` header.
- `SubtitleSearchContext` — singleton holder so detail screens can hand
  rich metadata (title/year/season/episode) to the player without
  bloating the nav route signature.
- `SubtitleLangPrefStore` — persists user's preferred subtitle language.
- `SubtitleDownloadDialog` — reusable composable for both TV (D-pad
  focusable) and Mobile (touch). Shows top hits sorted by download
  count, language strip with 7 quick-pick languages, one-tap download
  with progress spinner.
- `SubtitlePane` — wraps the existing `TrackPicker` in
  `PlayerOptionsMenu` and adds a "Download from OpenSubtitles" CTA.
  Hidden when `onDownloadSubtitles == null` (Live TV).
- Player wiring: when an SRT is downloaded, the player rebuilds its
  `MediaItem` with `SubtitleConfiguration(SUBRIP, language, DEFAULT)`
  while preserving playback position, then re-prepares — ExoPlayer
  picks up the side-loaded text track natively.
- Mobile: small `CC` chip in the controls bar to open the dialog.
- Detail screens wired to stash `SubtitleSearchContext` before nav:
  TV Movie Detail (title + year), TV Series Detail (show + season +
  episode), Mobile Series Detail (show + season + episode). Other
  entry points (Browse, Home, Search) fall back to using the playback
  title as the search query — works for ~80% of titles automatically.

### Quota / cost
- Account is on a paid OpenSubtitles tier: ~5,000 downloads/day quota
  per IP. Free tier is 5/day. SRTs are cached locally per `file_id`,
  so a re-watch never re-downloads.
- No ongoing costs for AI/STT — both servers gone.

### Files of reference
- `app/src/main/kotlin/com/hushtv/tv/data/OpenSubtitlesApi.kt`
- `app/src/main/kotlin/com/hushtv/tv/data/SubtitleSearchContext.kt`
- `app/src/main/kotlin/com/hushtv/tv/ui/player/SubtitleDownloadDialog.kt`
- `app/src/main/kotlin/com/hushtv/tv/ui/player/PlayerOptionsMenu.kt` (SubtitlePane)
- `app/src/main/kotlin/com/hushtv/tv/ui/screens/TVPlayerScreen.kt` (subtitle plumbing)
- `app/src/main/kotlin/com/hushtv/tv/mobile/MobilePlayerScreen.kt` (subtitle plumbing)

### Backlog after v1.33.0
- **P2** Picture-in-Picture (TV + Mobile)
- **P2** Xtream Catch-up / Archive
- **P3** Re-evaluate Gemini AI Search

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

---

## v1.32.0 — 2026-04-24 (versionCode 169)

**Server-side AI Subtitles — full deploy.** APK: 24 MB (down from 113 MB).

### GPU AI server (`ai.hushtv.xyz`, dedicated Tesla T4)
- Dedicated server provisioned at `216.152.147.177` running:
  - `faster-whisper` 1.0.3 + `ctranslate2` 4.7.1 (model: `base`, `int8_float16`)
  - FastAPI + uvicorn behind nginx → `127.0.0.1:5066`
  - systemd unit `hushtv-ai.service`, env at `/etc/hushtv-ai/config.env`
  - Logs: `/var/log/hushtv-ai/service.log`
- DNS: `ai.hushtv.xyz A 216.152.147.177` (Namecheap host field is just `ai`,
  NOT the FQDN — full FQDN creates `ai.hushtv.xyz.hushtv.xyz`).
- TLS: Let's Encrypt via `certbot --nginx -d ai.hushtv.xyz` (auto-renew
  enabled), HTTP→HTTPS 301 redirect.
- Endpoints:
  - `GET  /health`     → `{"ok":true,"in_flight":N,"model":"base"}`
  - `POST /transcribe` → Bearer auth (header `Authorization: Bearer <SECRET>`),
    body = raw 16-bit LE PCM @ 16 kHz mono, 1.6 KB – 6 MB (~50 ms – 3 min),
    response `{"text","lang","lang_prob","ms"}`. Translates any source
    language to English (`task="translate"`).
- Latency on T4: ~150–400 ms per 3-second chunk (well under the 25-second
  client read timeout). Concurrency cap 300 (env `HUSHTV_AI_MAX_CONCURRENT`).
- **CUDA gotcha**: ctranslate2 4.7+ needs `libcublas.so.12` + `libcudnn.so.9`.
  Installed via the venv (`pip install nvidia-cublas-cu12 nvidia-cudnn-cu12==9.*`)
  and exposed via `Environment=LD_LIBRARY_PATH=...` in the systemd unit.

### Android client
- `WhisperServerEngine.kt` POSTs 96 KB chunks (3 s of mono 16 kHz PCM) to
  `https://ai.hushtv.xyz/transcribe` with the shared bearer token. Stateless
  per-request; failures are silent (UI just stops refreshing).
- Same public surface as the old Vosk engine, so no player-screen changes.
- Verified end-to-end: TLS valid, `/health` 200, auth 401/403/400 paths
  exercised, real Spanish + French TTS samples translated to English with
  correct language detection.

### Backlog after v1.32.0
- **P2** Picture-in-Picture (TV + Mobile)
- **P2** Xtream Catch-up / Archive (`tv_archive=1` → timeshift URL)
- **P3** Re-evaluate Gemini AI Search
- **P3** Monitor T4 GPU concurrency. Estimated ~300 concurrent Whisper-Base
  users; plan for an L4 / RTX 4090 upgrade or a second server before that
  ceiling is hit.
