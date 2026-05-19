package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.hushtv.tv.data.LastChannelStore
import com.hushtv.tv.data.LayoutPrefsStore
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.screens.home.tvHubContentFocus
import com.hushtv.tv.ui.theme.Amber
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.BorderSlate
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextDim
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ──────────────────────────────────────────────────────────────── */
/*  HERO / TAB MODELS                                               */
/* ──────────────────────────────────────────────────────────────── */

private data class HeroSlide(
    val title: String,
    val badge: String,
    val genres: List<String>,
    val synopsis: String,
    val accent: Color,
    val gradient: List<Color>,
)

private val HERO_SLIDES = listOf(
    HeroSlide(
        title = "Live Sports & Events",
        badge = "LIVE",
        genres = listOf("Sports", "HD"),
        synopsis = "Every match, every moment — Premier League, NBA, UFC and more, streaming right now.",
        accent = Red,
        gradient = listOf(Color(0xFF7F1D1D), Color(0xFF3F0A0A), Color(0xFF000000)),
    ),
    HeroSlide(
        title = "Thousands of Movies",
        badge = "MOVIES",
        genres = listOf("Blockbusters", "4K"),
        synopsis = "From Oscar winners to summer blockbusters — a library that updates every week.",
        accent = Cyan,
        gradient = listOf(Color(0xFF164E63), Color(0xFF083344), Color(0xFF000000)),
    ),
    HeroSlide(
        title = "Binge-Worthy Series",
        badge = "SERIES",
        genres = listOf("Drama", "Comedy"),
        synopsis = "Follow your favorite shows season by season — full catalogs, latest episodes.",
        accent = Amber,
        gradient = listOf(Color(0xFF713F12), Color(0xFF422006), Color(0xFF000000)),
    ),
)

private data class NavTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val route: String?, // null for "Home" (already here)
)

/* ──────────────────────────────────────────────────────────────── */
/*  SCREEN                                                          */
/* ──────────────────────────────────────────────────────────────── */

@Composable
fun TVMainMenuScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    // First-run layout chooser. Shown once per install — flips
    // `firstRunShown` to true the first time the user makes a choice
    // (or dismisses). Runs INDEPENDENT of the user explicitly opening
    // Settings → Change Layout.
    var showLayoutChooser by remember {
        mutableStateOf(!LayoutPrefsStore.firstRunShown(ctx))
    }
    var currentLayoutMode by remember { mutableStateOf(LayoutPrefsStore.mode(ctx)) }

    // Account info
    var expiryStr by remember { mutableStateOf<String?>(null) }
    var daysLeft by remember { mutableStateOf<Long?>(null) }

    // Row data
    var liveNow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Pair<String, List<MediaCard>>>>(emptyList()) }
    var seriesRow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var trendingRow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    val lastChannel = remember { LastChannelStore.load(ctx) }

    // BACK on the TV home screen pops the exit-confirm dialog
    // (instead of silently quitting). Mirrors the mobile home BACK
    // behaviour added in v1.43.0+ and re-introduced for TV in
    // v1.43.99 after a refactor accidentally removed it.
    com.hushtv.tv.ui.ExitConfirmBackHandler()

    // Fetch
    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        runCatching { XtreamApi.authenticate(p.host, p.username, p.password) }
            .onSuccess { resp ->
                val expTs = resp.user_info?.exp_date?.toLongOrNull()
                if (expTs != null && expTs > 0) {
                    expiryStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(expTs * 1000))
                    daysLeft = ((expTs * 1000 - System.currentTimeMillis()) / (1000L * 60 * 60 * 24))
                }
            }
        coroutineScope {
            launch {
                runCatching {
                    val cats = XtreamApi.getCategories(p.host, p.username, p.password, "live")
                    val cat = cats.firstOrNull() ?: return@runCatching
                    liveNow = XtreamApi.getStreamsForCategory(
                        p.host, p.username, p.password, "live", cat.category_id,
                    ).take(12)
                }
            }
            launch {
                runCatching {
                    val cats = XtreamApi.getCategories(p.host, p.username, p.password, "movie")
                    val built = mutableListOf<Pair<String, List<MediaCard>>>()
                    cats.take(3).forEach { c ->
                        val items = XtreamApi.getStreamsForCategory(
                            p.host, p.username, p.password, "movie", c.category_id,
                        ).take(16)
                        if (items.isNotEmpty()) built += c.category_name to items
                    }
                    movies = built
                    trendingRow = built.flatMap { it.second }.take(10)
                }
            }
            launch {
                runCatching {
                    val cats = XtreamApi.getCategories(p.host, p.username, p.password, "series")
                    val cat = cats.firstOrNull() ?: return@runCatching
                    seriesRow = XtreamApi.getStreamsForCategory(
                        p.host, p.username, p.password, "series", cat.category_id,
                    ).take(14)
                }
            }
        }
    }

    // ── Requests-tab "NEW" pulse-dot ──
    // Reactive state powered by the shared rememberRequestsBadge()
    // helper, which is now used by EVERY top-level screen so the dot
    // is consistent regardless of which tab the user is on.
    val requestsBadge = com.hushtv.tv.ui.screens.home.rememberRequestsBadge()

    val tabs = com.hushtv.tv.ui.screens.home.topNavTabs(
        playlistId = playlistId,
        requestsBadge = requestsBadge,
        // Home tab on the Home screen is a no-op (we're already here).
        homeRoute = null,
    )

    // Focus: first tab (Home) gets initial focus.
    val topNavHomeFocus = remember { FocusRequester() }
    // Focus requester for the first card in each page. Nav-Down lands on
    // the first card of whichever page is currently showing.
    val firstCwFocus = remember { FocusRequester() }
    val firstDiscoveryFocus = remember { FocusRequester() }
    val firstSsMoviesFocus = remember { FocusRequester() }
    val firstSsSeriesFocus = remember { FocusRequester() }
    val firstGenresMoviesFocus = remember { FocusRequester() }
    val firstGenresSeriesFocus = remember { FocusRequester() }
    val firstYearsMoviesFocus = remember { FocusRequester() }
    val firstCollectionsFocus = remember { FocusRequester() }
    val firstThemedFocus = remember { FocusRequester() }
    val firstSportsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { topNavHomeFocus.requestFocus() } }

    val onCardSelect: (MediaCard) -> Unit = sel@{ item ->
        val p = playlist ?: return@sel
        when (item.kind) {
            "live" -> {
                val url = XtreamApi.liveUrl(p.host, p.username, p.password, item.streamId)
                nav.navigate("player/$playlistId/${Uri.encode(url)}/${Uri.encode(item.title)}/true")
            }
            "movie" -> {
                nav.navigate("moviedetail/$playlistId/${item.streamId}/${Uri.encode(item.title)}")
            }
            "series" -> {
                nav.navigate("series/$playlistId/${item.seriesId}/${Uri.encode(item.title)}")
            }
        }
    }

    // Netflix / YouTube-TV layered layout:
    //   1. HERO backdrop (full-bleed, edge-to-edge — behind everything)
    //   2. CONTENT (card row pinned near the bottom)
    //   3. TOP NAV overlay (auto-hides when focus leaves it; reappears when
    //      the user D-pad-ups from the first content card)
    // Hoisted so the root Box's onPreviewKeyEvent (Channel Up/Down
    // shortcut) can read + mutate them.
    val continueHandle = com.hushtv.tv.ui.screens.home.rememberContinueEntries(playlistId)
    val continueEntries = continueHandle.entries
    val hasCw = continueEntries.isNotEmpty()

    val ctxLocal = androidx.compose.ui.platform.LocalContext.current

    // v1.44.31 — `rememberSaveable` instead of `remember` so the
    // selected page survives navigation. Previously, leaving the
    // menu (player / detail screen / settings) and pressing BACK
    // re-mounted the menu and reset the page to "cw" or "discovery".
    // SavedStateHandle is scoped to this nav back-stack entry so
    // BACK from player → comes back on the same Sports tab.
    var currentPage by androidx.compose.runtime.saveable.rememberSaveable {
        // Initial value: prefer "discovery" because hasCw is initially
        // false (entries load asynchronously). Once entries hydrate,
        // the LaunchedEffect(hasCw) below auto-snaps to "cw" exactly
        // once per app launch (gated by [autoLandedOnCw]). Subsequent
        // re-mounts respect whatever page the user last navigated to.
        mutableStateOf("discovery")
    }
    // One-shot flag that gates the auto-land-on-CW behaviour to the
    // first time entries become available in this app process. Saved
    // across re-mounts so navigating away from + back to Home doesn't
    // re-snap the user off whichever page they chose.
    var autoLandedOnCw by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(hasCw) {
        if (!autoLandedOnCw && hasCw) {
            currentPage = "cw"
            autoLandedOnCw = true
        }
    }
    val pageOrder = remember(hasCw) {
        buildList {
            if (hasCw) add("cw")
            add("discovery")
            // v1.44.78 — Hush+ is now a Coming-Soon home-page section
            // (the live Sports surface moved to the top-nav tab where
            // Hush+ used to live). Keeping it here directly under
            // discovery puts the marketing/preview right where users
            // already scroll down looking for "what's next".
            add("hushplus")
            add("ss_movies")
            add("ss_series")
            add("collections")
            add("genres_movies")
            add("genres_series")
            // v1.43.91 — Themes & Moods restored to its original
            // home position above Decades, per user request. Was
            // briefly added as a top-nav tab in v1.43.90, but the
            // user clarified it always lived as a home page section.
            add("themed")
            add("years_movies")
        }
    }
    // If CW drops to empty while the user is on the CW page, bounce
    // them to Discovery. Otherwise also fall back to a valid page if
    // the order shrinks for any reason (defensive).
    LaunchedEffect(pageOrder) {
        if (currentPage !in pageOrder) {
            currentPage = pageOrder.firstOrNull() ?: "discovery"
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .onPreviewKeyEvent { ev ->
                // Channel Up / Down (Android TV remotes) + Page Up / Down
                // (some TV remotes) → jump full Home pages from ANY focus.
                if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val k = ev.key
                val isPageUp = k == androidx.compose.ui.input.key.Key.ChannelUp ||
                    k == androidx.compose.ui.input.key.Key.PageUp
                val isPageDown = k == androidx.compose.ui.input.key.Key.ChannelDown ||
                    k == androidx.compose.ui.input.key.Key.PageDown
                if (!isPageUp && !isPageDown) return@onPreviewKeyEvent false
                val idx = pageOrder.indexOf(currentPage).coerceAtLeast(0)
                val next = when {
                    isPageUp && idx > 0 -> pageOrder[idx - 1]
                    isPageDown && idx < pageOrder.lastIndex -> pageOrder[idx + 1]
                    else -> null
                }
                if (next != null) { currentPage = next; true } else false
            },
    ) {
        // State for Home content. Continue entries already hoisted above.
        var heroEntry by remember { mutableStateOf<com.hushtv.tv.ui.screens.home.ContinueEntry?>(null) }
        LaunchedEffect(continueEntries.firstOrNull()) {
            if (heroEntry == null || continueEntries.none { it === heroEntry }) {
                heroEntry = continueEntries.firstOrNull()
            }
        }

        var removePromptFor by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.ContinueEntry?>(null)
        }
        var clearAllPromptOpen by remember { mutableStateOf(false) }

        val discoveryCards = com.hushtv.tv.ui.screens.home.rememberDiscoveryCards(playlistId)
        var focusedDiscoveryCard by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.DiscoveryCard?>(null)
        }
        LaunchedEffect(discoveryCards.firstOrNull()) {
            if (focusedDiscoveryCard == null ||
                discoveryCards.none { it === focusedDiscoveryCard }
            ) {
                focusedDiscoveryCard = discoveryCards.firstOrNull()
            }
        }
        // Which section the user's focus is currently in. Drives which
        // hero backdrop renders. Defaults to "discovery" because the user
        // explicitly wanted Discovery to be the default main home section.
        var heroSection by remember { mutableStateOf("discovery") }

        // Top nav is ALWAYS visible — static, never hides. The auto-hide
        // behaviour was causing visual "breaks" when the user D-padded
        // back up from a content card; a static nav keeps the hero art
        // perfectly framed at all times.
        //
        // Callback the first card fires on D-pad UP — just focuses the
        // Home tab. No visibility toggle any more.
        val showNavAndFocus: () -> Unit = {
            runCatching { topNavHomeFocus.requestFocus() }
        }

        // ── 1. PAGER ── CW page and Discovery page are SEPARATE screens.
        // Each page owns its own hero backdrop + card row, so the two
        // sections never fight for space or overlap each other's art.
        // Default page = Continue Watching when entries exist (natural
        // return-to-resume flow); otherwise Discovery. Up/Down D-pad
        // slides cleanly between pages via AnimatedContent.
        //
        // `hasCw` / `currentPage` / `pageOrder` are all hoisted above
        // the root Box so the Channel-Up/Down shortcut handler there
        // can read + mutate them.
        //
        // Pages in vertical order (top → bottom):
        //   "cw"         → Continue Watching (optional)
        //   "discovery"  → Latest Movies / Latest Series
        //   "ss_movies"  → Streaming Services (Movies)
        //   "ss_series"  → Streaming Services (Series)
        //
        // No "cw" page exists in pageOrder anymore (Hub was removed).
        // CwPage / hasCw plumbing is left in place only because the
        // Discovery page composable still accepts the parameter.

        // Streaming service state (loaded lazily on composition).
        val ssMovies = com.hushtv.tv.ui.screens.home.rememberStreamingServices("movie")
        val ssSeries = com.hushtv.tv.ui.screens.home.rememberStreamingServices("series")
        var focusedSsMovie by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.StreamingService?>(null)
        }
        var focusedSsSeries by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.StreamingService?>(null)
        }
        LaunchedEffect(ssMovies.firstOrNull()) {
            if (focusedSsMovie == null) focusedSsMovie = ssMovies.firstOrNull()
        }
        LaunchedEffect(ssSeries.firstOrNull()) {
            if (focusedSsSeries == null) focusedSsSeries = ssSeries.firstOrNull()
        }

        // Genre state — loaded lazily, cached per kind.
        val genresMovies = com.hushtv.tv.ui.screens.home.rememberGenres("movie")
        val genresSeries = com.hushtv.tv.ui.screens.home.rememberGenres("series")
        var focusedGenreMovie by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.Genre?>(null)
        }
        var focusedGenreSeries by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.Genre?>(null)
        }
        LaunchedEffect(genresMovies.firstOrNull()) {
            if (focusedGenreMovie == null) focusedGenreMovie = genresMovies.firstOrNull()
        }
        LaunchedEffect(genresSeries.firstOrNull()) {
            if (focusedGenreSeries == null) focusedGenreSeries = genresSeries.firstOrNull()
        }

        // Movie-release-year state.
        val movieYears = com.hushtv.tv.ui.screens.home.rememberMovieYears()
        var focusedMovieYear by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.MovieYear?>(null)
        }
        LaunchedEffect(movieYears.firstOrNull()) {
            if (focusedMovieYear == null) focusedMovieYear = movieYears.firstOrNull()
        }

        // Movie collections (franchises / boxsets) state.
        val movieCollections = com.hushtv.tv.ui.screens.home.rememberMovieCollections()
        var focusedCollection by remember {
            mutableStateOf<com.hushtv.tv.ui.screens.home.MovieCollection?>(null)
        }
        LaunchedEffect(movieCollections.firstOrNull()) {
            if (focusedCollection == null) focusedCollection = movieCollections.firstOrNull()
        }

        // Themes & Moods state — sourced directly from the static
        // [HushThemedLists.all] catalog. Every item ships with a
        // hand-picked TMDB hero backdrop so the hero paints on the
        // first frame even before the library matcher has run.
        val themedLists = remember { com.hushtv.tv.data.HushThemedLists.all }
        var focusedTheme by remember {
            mutableStateOf<com.hushtv.tv.data.ThemedList?>(null)
        }
        LaunchedEffect(themedLists.firstOrNull()) {
            if (focusedTheme == null) focusedTheme = themedLists.firstOrNull()
        }

        // Nav-Down target — follows the CURRENTLY VISIBLE page so the
        // requestFocus() call always hits a composable that's actually
        // attached to the tree.
        val navDownTarget = currentPage

        // Static top nav height — hero + content start right below it
        // at a constant 72 dp offset. No animation.
        val navHeightDp = com.hushtv.tv.ui.screens.home.SideRailCollapsedWidth
        // Hoisted to share between rail and content's focus-redirect.
        val homeTabFocus = remember { FocusRequester() }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = navHeightDp)
                .tvHubContentFocus(homeTabFocus),
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    // Direction: compare the index of target vs initial
                    // in the pageOrder list. Higher index = below →
                    // slide new in from below. Lower index = above →
                    // slide new in from above.
                    val fromIdx = pageOrder.indexOf(initialState).coerceAtLeast(0)
                    val toIdx = pageOrder.indexOf(targetState).coerceAtLeast(0)
                    val goingDown = toIdx > fromIdx
                    val slideDir = if (goingDown) 1 else -1
                    (androidx.compose.animation.slideInVertically(
                        androidx.compose.animation.core.tween(280)
                    ) { h -> slideDir * h / 3 } +
                        androidx.compose.animation.fadeIn(
                            androidx.compose.animation.core.tween(280)
                        )) togetherWith
                        (androidx.compose.animation.slideOutVertically(
                            androidx.compose.animation.core.tween(280)
                        ) { h -> -slideDir * h / 3 } +
                            androidx.compose.animation.fadeOut(
                                androidx.compose.animation.core.tween(200)
                            ))
                },
                label = "home-pager",
            ) { page ->
                when (page) {
                    "cw" -> CwPage(
                        playlistId = playlistId,
                        nav = nav,
                        entries = continueEntries,
                        heroEntry = heroEntry,
                        firstCwFocus = firstCwFocus,
                        showNavAndFocus = showNavAndFocus,
                        onFocusedEntryChange = {
                            heroEntry = it
                            heroSection = "cw"
                        },
                        onLongPressRemove = { removePromptFor = it },
                        onClearAll = { clearAllPromptOpen = true },
                        onDownFromRow = { currentPage = "discovery" },
                    )
                    "hushplus" -> com.hushtv.tv.ui.hushplus.TVHushPlusComingSoonSection(
                        firstItemFocus = firstSportsFocus,
                        onUpFromRow = { currentPage = "discovery" },
                        onDownFromRow = { currentPage = "ss_movies" },
                    )
                    "ss_movies" -> SsPage(
                        playlistId = playlistId,
                        nav = nav,
                        services = ssMovies,
                        focused = focusedSsMovie,
                        onFocusedChange = { focusedSsMovie = it },
                        kindLabel = "STREAMING SERVICES · MOVIES",
                        kind = "movie",
                        firstItemFocus = firstSsMoviesFocus,
                        // v1.44.78 — UP from streaming-movies returns
                        // to the Hush+ Coming Soon section (the page
                        // above it in pageOrder), not Discovery.
                        onUpFromRow = { currentPage = "hushplus" },
                        onDownFromRow = { currentPage = "ss_series" },
                    )
                    "ss_series" -> SsPage(
                        playlistId = playlistId,
                        nav = nav,
                        services = ssSeries,
                        focused = focusedSsSeries,
                        onFocusedChange = { focusedSsSeries = it },
                        kindLabel = "STREAMING SERVICES · SERIES",
                        kind = "series",
                        firstItemFocus = firstSsSeriesFocus,
                        onUpFromRow = { currentPage = "ss_movies" },
                        onDownFromRow = { currentPage = "collections" },
                    )
                    "genres_movies" -> GenresPage(
                        playlistId = playlistId,
                        nav = nav,
                        genres = genresMovies,
                        focused = focusedGenreMovie,
                        onFocusedChange = { focusedGenreMovie = it },
                        kindLabel = "GENRES · MOVIES",
                        kind = "movie",
                        firstItemFocus = firstGenresMoviesFocus,
                        onUpFromRow = { currentPage = "collections" },
                        onDownFromRow = { currentPage = "genres_series" },
                    )
                    "genres_series" -> GenresPage(
                        playlistId = playlistId,
                        nav = nav,
                        genres = genresSeries,
                        focused = focusedGenreSeries,
                        onFocusedChange = { focusedGenreSeries = it },
                        kindLabel = "GENRES · SERIES",
                        kind = "series",
                        firstItemFocus = firstGenresSeriesFocus,
                        onUpFromRow = { currentPage = "genres_movies" },
                        onDownFromRow = { currentPage = "themed" },
                    )
                    "themed" -> ThemedPage(
                        playlistId = playlistId,
                        nav = nav,
                        themes = themedLists,
                        focused = focusedTheme,
                        onFocusedChange = { focusedTheme = it },
                        firstItemFocus = firstThemedFocus,
                        onUpFromRow = { currentPage = "genres_series" },
                        onDownFromRow = { currentPage = "years_movies" },
                    )
                    "years_movies" -> YearsPage(
                        playlistId = playlistId,
                        nav = nav,
                        years = movieYears,
                        focused = focusedMovieYear,
                        onFocusedChange = { focusedMovieYear = it },
                        firstItemFocus = firstYearsMoviesFocus,
                        onUpFromRow = { currentPage = "themed" },
                        onDownFromRow = null,
                    )
                    "collections" -> CollectionsPage(
                        playlistId = playlistId,
                        nav = nav,
                        collections = movieCollections,
                        focused = focusedCollection,
                        onFocusedChange = { focusedCollection = it },
                        firstItemFocus = firstCollectionsFocus,
                        onUpFromRow = { currentPage = "ss_series" },
                        onDownFromRow = { currentPage = "genres_movies" },
                    )
                    else -> DiscoveryPage(
                        playlistId = playlistId,
                        nav = nav,
                        cards = discoveryCards,
                        focused = focusedDiscoveryCard,
                        firstDiscoveryFocus = firstDiscoveryFocus,
                        hasCw = hasCw,
                        showNavAndFocus = showNavAndFocus,
                        onFocusedCardChange = {
                            focusedDiscoveryCard = it
                            heroSection = "discovery"
                        },
                        onUpFromRow = {
                            if (hasCw) currentPage = "cw" else showNavAndFocus()
                        },
                        onDownFromRow = { currentPage = "hushplus" },
                    )
                }
            }

            // Auto-focus the new page's first card when the user slides
            // between pages.
            LaunchedEffect(currentPage) {
                kotlinx.coroutines.delay(320)
                runCatching {
                    when (currentPage) {
                        "cw" -> if (hasCw) firstCwFocus.requestFocus()
                        "discovery" -> firstDiscoveryFocus.requestFocus()
                        "hushplus" -> firstSportsFocus.requestFocus()
                        "collections" -> firstCollectionsFocus.requestFocus()
                        "ss_movies" -> firstSsMoviesFocus.requestFocus()
                        "ss_series" -> firstSsSeriesFocus.requestFocus()
                        "genres_movies" -> firstGenresMoviesFocus.requestFocus()
                        "genres_series" -> firstGenresSeriesFocus.requestFocus()
                        "themed" -> firstThemedFocus.requestFocus()
                        "years_movies" -> firstYearsMoviesFocus.requestFocus()
                    }
                }
            }

            // Remove-from-Continue-Watching confirmation dialog.
            removePromptFor?.let { entry ->
                com.hushtv.tv.ui.screens.home.RemoveContinueWatchingDialog(
                    entry = entry,
                    onConfirm = {
                        continueHandle.remove(entry)
                        removePromptFor = null
                    },
                    onDismiss = { removePromptFor = null },
                )
            }

            // Clear-All-from-Continue-Watching confirmation dialog.
            if (clearAllPromptOpen) {
                com.hushtv.tv.ui.screens.home.ClearAllContinueWatchingDialog(
                    count = continueEntries.size,
                    onConfirm = {
                        com.hushtv.tv.data.WatchProgressStore.clearAll(ctxLocal)
                        // Drop every entry from local handle state so
                        // the LazyRow re-measures NOW; bumps the
                        // version inside [rememberContinueEntries] to
                        // re-read prefs, and ensures the row hides
                        // cleanly because hasCw flips false.
                        continueEntries.toList().forEach(continueHandle.remove)
                        clearAllPromptOpen = false
                    },
                    onDismiss = { clearAllPromptOpen = false },
                )
            }
        }

        // ── 2.5. PAGE INDICATOR ────────────────────────────────────
        // Vertical dots + chevrons pinned to the right edge showing the
        // current page position and hinting at pages above/below.
        // Also hosts the Channel Up/Down / Page Up/Down shortcut keys
        // for instant page jumping from any focus position.
        val indicatorPages = remember(pageOrder) {
            pageOrder.map { k ->
                com.hushtv.tv.ui.screens.home.HomePage(
                    key = k,
                    label = when (k) {
                        "cw" -> "RESUME"
                        "discovery" -> "DISCOVER"
                        "sports" -> "SPORTS"
                        "collections" -> "COLLECT"
                        "ss_movies" -> "MOVIES"
                        "ss_series" -> "SERIES"
                        "genres_movies" -> "G·MOV"
                        "genres_series" -> "G·SER"
                        "themed" -> "MOODS"
                        "years_movies" -> "YEARS"
                        else -> k.uppercase()
                    },
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            com.hushtv.tv.ui.screens.home.HomePageIndicator(
                pages = indicatorPages,
                currentPage = currentPage,
            )
        }

        // ── 3. STATIC TOP NAV ─────────────────────────────────────────
        // Always visible — no auto-hide, no animation. Sits pinned to
        // the top of the screen. D-pad Down from any tab still focuses
        // the first content card; D-pad Up from the first card focuses
        // the Home tab (no visual break — the nav was already there).
        // Disney+ left rail — replaces the previous top nav bar.
        // Self-positions to the start edge with fillMaxHeight at
        // its collapsed width; expands on focus and overlays content
        // with a backdrop dim.
        //
        // Wire `onExitRight` on the rail with a callback that EXPLICITLY
        // calls `firstFocus.requestFocus()` for whichever home page is
        // currently visible. Compose's default 2D spatial focus search
        // is unreliable when the page contains a hero layer + a row
        // pinned to the bottom (only Discovery worked because of its
        // particular layout) — explicit requestFocus to a focusRequester
        // bound directly to the first card is the only reliable way.
        // Log every step so we can diagnose if it ever stops working.
        // v1.43.98 — Defer requestFocus via a state ticker rather
        // than calling it synchronously inside the rail's RIGHT-arrow
        // handler. Synchronous requestFocus failed because:
        //   • The rail was still focused & expanded at the moment
        //     onPreviewKeyEvent fires; Compose's focus system was
        //     mid-transition and silently rejected the request.
        //   • The user-discovered workaround "press UP then DOWN
        //     within home" worked precisely because that path goes
        //     through `LaunchedEffect(currentPage) { delay(320);
        //     requestFocus() }` — the 320 ms delay lets the rail
        //     collapse and focus state settle BEFORE requestFocus
        //     fires.
        //
        // We do the same here: increment a tick-state when RIGHT is
        // pressed; a sibling LaunchedEffect waits the same 320 ms,
        // then runs the per-page requestFocus. Same path, same
        // delay, same reliability.
        var railExitTick by remember { mutableStateOf(0) }
        LaunchedEffect(railExitTick) {
            if (railExitTick == 0) return@LaunchedEffect
            kotlinx.coroutines.delay(320)
            com.hushtv.tv.util.HushTVNav.d(
                "RailExit#$railExitTick fires → currentPage=$currentPage hasCw=$hasCw"
            )
            runCatching {
                when (currentPage) {
                    "cw" -> if (hasCw) firstCwFocus.requestFocus()
                    "discovery" -> firstDiscoveryFocus.requestFocus()
                    "sports" -> firstSportsFocus.requestFocus()
                    "hushplus" -> firstSportsFocus.requestFocus()
                    "collections" -> firstCollectionsFocus.requestFocus()
                    "ss_movies" -> firstSsMoviesFocus.requestFocus()
                    "ss_series" -> firstSsSeriesFocus.requestFocus()
                    "genres_movies" -> firstGenresMoviesFocus.requestFocus()
                    "genres_series" -> firstGenresSeriesFocus.requestFocus()
                    "themed" -> firstThemedFocus.requestFocus()
                    "years_movies" -> firstYearsMoviesFocus.requestFocus()
                    else -> firstDiscoveryFocus.requestFocus()
                }
            }.onFailure { e ->
                com.hushtv.tv.util.HushTVNav.d("  ✗ requestFocus FAILED: ${e.message}")
            }
        }

        com.hushtv.tv.ui.screens.home.TVHubRail(
            activeKey = "home",
            playlistId = playlistId,
            nav = nav,
            homeFocus = homeTabFocus,
            onExitRight = {
                com.hushtv.tv.util.HushTVNav.d(
                    "RailRight pressed → enqueuing exit tick (currentPage=$currentPage)"
                )
                railExitTick += 1
            },
        )

        // ── First-run / Settings layout chooser modal ──
        // Declared at the end of the root Box so it composes last and
        // sits on top of the whole screen. Variables are hoisted at the
        // top of TVMainMenuScreen so ContinueCard can't read them.
        if (showLayoutChooser) {
            com.hushtv.tv.ui.screens.home.LayoutChooserDialog(
                currentMode = currentLayoutMode,
                dismissable = LayoutPrefsStore.firstRunShown(ctx),
                onPicked = { mode ->
                    LayoutPrefsStore.setMode(ctx, mode)
                    LayoutPrefsStore.markFirstRunShown(ctx)
                    currentLayoutMode = mode
                    showLayoutChooser = false
                },
                onDismiss = {
                    LayoutPrefsStore.markFirstRunShown(ctx)
                    showLayoutChooser = false
                },
            )
        }

        // ─── Version badge ────────────────────────────────────────
        // Tiny, very low-contrast text in the bottom-right corner so
        // the user can verify at a glance which build is actually
        // running. Doesn't take focus, doesn't intercept any input —
        // purely informational. Solves the "did you actually update?"
        // ambiguity that bit us during the v1.42.8→22 deploy bug.
        Text(
            "v${com.hushtv.tv.BuildConfig.VERSION_NAME} · #${com.hushtv.tv.BuildConfig.VERSION_CODE}",
            color = Color(0x66FFFFFF),
            fontSize = 9.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 12.dp),
        )
    }
}
// End TVMainMenuScreen

/* ──────────────────────────────────────────────────────────────── */
/*  LEFT SIDEBAR                                                    */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun Sidebar(
    tabs: List<NavTab>,
    activeKey: String,
    expanded: Boolean,
    expiryStr: String?,
    daysLeft: Long?,
    homeFocus: FocusRequester,
    onExpandChange: (Boolean) -> Unit,
    onTab: (NavTab) -> Unit,
    onProfile: () -> Unit,
) {
    val width by animateDpAsState(
        targetValue = if (expanded) 140.dp else 60.dp,
        animationSpec = tween(150),
        label = "sidebar-width",
    )

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            // Sidebar is now fully transparent — the wide horizontal blend
            // veil rendered behind it in the parent Box handles the fade
            // into the content. This removes the 140 dp hard-edge seam.
            .onFocusChanged { onExpandChange(it.hasFocus) }
            .padding(vertical = 28.dp, horizontal = 0.dp),
    ) {
        // Brand mark — Inter Black "hush.tv" when expanded, cyan dot when
        // collapsed. Animates cleanly.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(90)),
            ) {
                HushTVLogo(fontSize = 22.sp)
            }
            if (!expanded) {
                Box(
                    Modifier
                        .padding(start = 22.dp)
                        .size(10.dp)
                        .background(Cyan, CircleShape),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Nav tabs. No background boxes. Focus = cyan left accent bar +
        // white text + cyan icon. Active (non-focused) = white dim.
        tabs.forEachIndexed { i, tab ->
            val mod = if (i == 0) Modifier.focusRequester(homeFocus) else Modifier
            SidebarItem(
                label = tab.label,
                icon = tab.icon,
                active = tab.key == activeKey,
                expanded = expanded,
                modifier = mod,
                onClick = { onTab(tab) },
            )
        }

        Spacer(Modifier.weight(1f))

        // Subscription expiry pill removed from the sidebar — felt
        // crowded next to the Profile entry. Expiry info is still
        // visible on the Settings → Subscription screen.

        // Divider above Profile — very subtle.
        Box(
            Modifier
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x1AFFFFFF))
        )

        SidebarItem(
            label = "Profile",
            icon = Icons.Default.Person,
            active = false,
            expanded = expanded,
            onClick = onProfile,
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Cyan accent bar slides in from the left on focus. Width animates
    // 0 → 3 dp. Matches the Netflix / Disney+ / Prime focus language: no
    // chunky highlight pills, just a clean vertical indicator and a
    // color change on icon + label.
    val accentWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = tween(140),
        label = "accent-width",
    )
    val iconTint = when {
        focused -> Cyan
        active -> TextPrimary
        else -> Color(0xFF94A3B8)
    }
    val textColor = when {
        focused -> TextPrimary
        active -> TextPrimary
        else -> Color(0xFFCBD5E1)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        // Left accent bar (fixed-width slot of 3 dp so icons don't shift
        // horizontally when focus arrives — only the cyan bar's alpha/width
        // animates within the slot).
        Box(
            Modifier
                .padding(start = 0.dp)
                .width(3.dp)
                .height(24.dp)
                .background(
                    if (accentWidth > 0.dp) Cyan else Color.Transparent,
                    RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp),
                )
        )
        Spacer(Modifier.width(if (expanded) 15.dp else 18.dp))

        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))

        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                color = textColor,
                fontSize = 13.sp,
                fontFamily = Inter,
                fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            color = fg,
            fontSize = 10.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  HERO BILLBOARD                                                  */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HeroBillboard(
    onPlay: () -> Unit,
    onMyList: () -> Unit,
) {
    var slideIdx by remember { mutableStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }

    LaunchedEffect(heroFocused) {
        while (!heroFocused) {
            kotlinx.coroutines.delay(8000)
            slideIdx = (slideIdx + 1) % HERO_SLIDES.size
        }
    }

    val slide = HERO_SLIDES[slideIdx]

    Box(
        Modifier
            .fillMaxWidth()
            .height(380.dp)
            .background(Brush.verticalGradient(colors = slide.gradient))
            .onFocusChanged { heroFocused = it.hasFocus },
    ) {
        // Gradients
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xB3000000), Color.Transparent),
                        startX = 0f,
                        endX = 800f,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xE6000000)),
                        startY = 180f,
                    )
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 40.dp)
                .widthIn(max = 620.dp),
        ) {
            Box(
                Modifier
                    .background(slide.accent, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    slide.badge,
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.8.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                slide.title,
                color = TextPrimary,
                fontSize = 40.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.2).sp,
                lineHeight = 44.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slide.genres.forEach { g ->
                    Box(
                        Modifier
                            .background(Color(0x26FFFFFF), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(g, color = TextPrimary, fontSize = 11.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!heroFocused) {
                Spacer(Modifier.height(10.dp))
                Text(
                    slide.synopsis,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = Inter,
                    lineHeight = 18.sp,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPlayButton(onClick = onPlay)
                HeroSecondaryButton(onClick = onMyList)
            }
        }

        // Progress dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 28.dp),
        ) {
            HERO_SLIDES.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .size(if (i == slideIdx) 18.dp else 6.dp, 6.dp)
                        .background(
                            if (i == slideIdx) Cyan else Color(0x55FFFFFF),
                            RoundedCornerShape(999.dp),
                        )
                )
            }
        }
    }
}

@Composable
private fun HeroPlayButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(90),
        label = "hero-play",
    )
    Row(
        Modifier
            .height(44.dp)
            .widthIn(min = 140.dp)
            .scale(scale)
            .background(if (focused) Cyan else Color.White, RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Play", color = Color.Black, fontSize = 15.sp, fontFamily = Inter, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroSecondaryButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(90),
        label = "hero-mylist",
    )
    Row(
        Modifier
            .height(44.dp)
            .widthIn(min = 140.dp)
            .scale(scale)
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x1FFFFFFF),
                RoundedCornerShape(7.dp),
            )
            .border(
                2.dp,
                if (focused) Cyan else Color(0x33FFFFFF),
                RoundedCornerShape(7.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Search", color = TextPrimary, fontSize = 15.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  ROWS & CARDS (compact sizing)                                   */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun RowHeader(
    title: String,
    badgeColor: Color? = null,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badgeColor?.let {
            Box(
                Modifier
                    .size(8.dp)
                    .background(it, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
        )
        if (showSeeAll) {
            Spacer(Modifier.weight(1f))
            SeeAllLink(onClick = onSeeAll)
        }
    }
}

@Composable
private fun SeeAllLink(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        "See All →",
        color = if (focused) TextPrimary else Cyan,
        fontSize = 12.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun LiveCard(card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Box(
        Modifier
            .size(width = 240.dp, height = 135.dp)
            .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
            .clickableWithEnter { onSelect(card) },
    ) {
        if (!card.poster.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceNavy),
                error = { LiveFallback() },
                loading = { LiveFallback() },
            )
        } else {
            LiveFallback()
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                )
        )
        Text(
            card.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp, end = 10.dp),
        )
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Red, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(
                "LIVE",
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.6f)
                .height(3.dp)
                .background(Cyan),
        )
    }
}

@Composable
private fun LiveFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(SurfaceNavy, BgBlack)),
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Tv, null, tint = BorderSlate, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun PosterCardV2(card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Box(
        Modifier
            .size(width = 130.dp, height = 195.dp)
            .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
            .clickableWithEnter { onSelect(card) },
    ) {
        if (!card.poster.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                error = { PosterFallback() },
                loading = { PosterFallback() },
            )
        } else {
            PosterFallback()
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                )
        )
        Text(
            card.title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PosterFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceNavy, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Movie, null, tint = BorderSlate, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun TrendingCard(rank: Int, card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            rank.toString(),
            color = Color(0x1AFFFFFF),
            fontSize = 90.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
            letterSpacing = (-4).sp,
            modifier = Modifier.offset(x = 16.dp, y = 4.dp),
        )
        Box(
            Modifier
                .size(width = 120.dp, height = 180.dp)
                .offset(x = (-18).dp)
                .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
                .clickableWithEnter { onSelect(card) },
        ) {
            if (!card.poster.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = card.poster,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                    error = { PosterFallback() },
                    loading = { PosterFallback() },
                )
            } else {
                PosterFallback()
            }
        }
    }
}

@Composable
private fun ContinueCard(title: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 200.dp, height = 112.dp)
            .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF164E63), Color(0xFF0F172A))),
                    RoundedCornerShape(10.dp),
                ),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .background(Cyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.35f)
                .height(3.dp)
                .background(Cyan),
        )
    }
}


/* ──────────────────────────────────────────────────────────────── */
/*  HOME PAGER — PAGE COMPOSABLES                                   */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun CwPage(
    playlistId: String,
    nav: NavController,
    entries: List<com.hushtv.tv.ui.screens.home.ContinueEntry>,
    heroEntry: com.hushtv.tv.ui.screens.home.ContinueEntry?,
    firstCwFocus: FocusRequester,
    showNavAndFocus: () -> Unit,
    onFocusedEntryChange: (com.hushtv.tv.ui.screens.home.ContinueEntry) -> Unit,
    onLongPressRemove: (com.hushtv.tv.ui.screens.home.ContinueEntry) -> Unit,
    onClearAll: () -> Unit,
    onDownFromRow: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        com.hushtv.tv.ui.screens.home.HomeHeroLayer(
            entry = heroEntry,
            contentStartPadding = 80.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                com.hushtv.tv.ui.screens.home.HomeContinueWatchingRow(
                    playlistId = playlistId,
                    entries = entries,
                    contentStartPadding = 0.dp,
                    onFocusedEntryChange = onFocusedEntryChange,
                    onCardClick = { entry ->
                        nav.navigate(
                            "moviedetail/$playlistId/${entry.progress.streamId}" +
                                "/${Uri.encode(entry.progress.title)}"
                        )
                    },
                    onLongPressRemove = onLongPressRemove,
                    onClearAll = onClearAll,
                    firstItemFocus = firstCwFocus,
                    onUpFromFirstItem = showNavAndFocus,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryPage(
    playlistId: String,
    nav: NavController,
    cards: List<com.hushtv.tv.ui.screens.home.DiscoveryCard>,
    focused: com.hushtv.tv.ui.screens.home.DiscoveryCard?,
    firstDiscoveryFocus: FocusRequester,
    hasCw: Boolean,
    showNavAndFocus: () -> Unit,
    onFocusedCardChange: (com.hushtv.tv.ui.screens.home.DiscoveryCard) -> Unit,
    onUpFromRow: () -> Unit,
    onDownFromRow: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        com.hushtv.tv.ui.screens.home.HomeDiscoveryHeroLayer(
            card = focused,
            contentStartPadding = 80.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                com.hushtv.tv.ui.screens.home.HomeDiscoveryRow(
                    cards = cards,
                    contentStartPadding = 0.dp,
                    onFocusedCardChange = onFocusedCardChange,
                    onCardClick = { card ->
                        val encoded = Uri.encode(card.categoryName)
                        nav.navigate("browse/$playlistId/${card.type}?category=$encoded")
                    },
                    firstItemFocus = firstDiscoveryFocus,
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}

@Composable
private fun SsPage(
    playlistId: String,
    nav: NavController,
    services: List<com.hushtv.tv.ui.screens.home.StreamingService>,
    focused: com.hushtv.tv.ui.screens.home.StreamingService?,
    onFocusedChange: (com.hushtv.tv.ui.screens.home.StreamingService) -> Unit,
    kindLabel: String,
    kind: String, // "movie" or "series"
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        com.hushtv.tv.ui.screens.home.HomeStreamingServicesHeroLayer(
            service = focused,
            kindLabel = kindLabel,
            contentStartPadding = 80.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                com.hushtv.tv.ui.screens.home.HomeStreamingServicesRow(
                    services = services,
                    kindLabel = kindLabel,
                    contentStartPadding = 0.dp,
                    onFocusedServiceChange = onFocusedChange,
                    onServiceClick = { svc ->
                        val encoded = Uri.encode(svc.searchKeyword)
                        val catId = if (kind == "series")
                            svc.xtreamSeriesCategoryId else svc.xtreamMovieCategoryId
                        val catParam = if (catId != null) "&catId=$catId" else ""
                        nav.navigate("browse/$playlistId/$kind?category=$encoded$catParam")
                    },
                    firstItemFocus = firstItemFocus,
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}


@Composable
private fun GenresPage(
    playlistId: String,
    nav: NavController,
    genres: List<com.hushtv.tv.ui.screens.home.Genre>,
    focused: com.hushtv.tv.ui.screens.home.Genre?,
    onFocusedChange: (com.hushtv.tv.ui.screens.home.Genre) -> Unit,
    kindLabel: String,
    kind: String, // "movie" or "series"
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        com.hushtv.tv.ui.screens.home.HomeGenresHeroLayer(
            genre = focused,
            contentStartPadding = 80.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                com.hushtv.tv.ui.screens.home.HomeGenresRow(
                    genres = genres,
                    kindLabel = kindLabel,
                    contentStartPadding = 0.dp,
                    onFocusedGenreChange = onFocusedChange,
                    onGenreClick = { g ->
                        val encoded = Uri.encode(g.searchKeyword)
                        val catParam = g.xtreamCategoryId?.let { "&catId=$it" }.orEmpty()
                        nav.navigate("browse/$playlistId/$kind?category=$encoded$catParam")
                    },
                    firstItemFocus = firstItemFocus,
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}

@Composable
private fun ThemedPage(
    playlistId: String,
    nav: NavController,
    themes: List<com.hushtv.tv.data.ThemedList>,
    focused: com.hushtv.tv.data.ThemedList?,
    onFocusedChange: (com.hushtv.tv.data.ThemedList) -> Unit,
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        com.hushtv.tv.ui.screens.home.HomeThemedHeroLayer(
            theme = focused,
            contentStartPadding = 80.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                com.hushtv.tv.ui.screens.home.HomeThemedRow(
                    themes = themes,
                    contentStartPadding = 0.dp,
                    onFocusedThemeChange = onFocusedChange,
                    onThemeClick = { t ->
                        nav.navigate("themedetail/$playlistId/${t.id}")
                    },
                    onSeeAllClick = {
                        nav.navigate("themes/$playlistId")
                    },
                    firstItemFocus = firstItemFocus,
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}

@Composable
private fun YearsPage(
    playlistId: String,
    nav: NavController,
    years: List<com.hushtv.tv.ui.screens.home.MovieYear>,
    focused: com.hushtv.tv.ui.screens.home.MovieYear?,
    onFocusedChange: (com.hushtv.tv.ui.screens.home.MovieYear) -> Unit,
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        com.hushtv.tv.ui.screens.home.HomeYearsHeroLayer(
            year = focused,
            contentStartPadding = 80.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                com.hushtv.tv.ui.screens.home.HomeYearsRow(
                    years = years,
                    contentStartPadding = 0.dp,
                    onFocusedYearChange = onFocusedChange,
                    onYearClick = { y ->
                        // v1.43.89 — clicking a decade tile on the
                        // home screen now opens the curated decade
                        // screen (TVDecadeYearsScreen) instead of
                        // the generic `browse?category=2010s` route
                        // which was returning empty results because
                        // Xtream providers don't have categories
                        // matching decade labels. Mobile already
                        // routes to `decadeyears` — bringing TV in
                        // line.
                        nav.navigate("decadeyears/$playlistId/${y.year}")
                    },
                    firstItemFocus = firstItemFocus,
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}



@Composable
private fun CollectionsPage(
    playlistId: String,
    nav: NavController,
    collections: List<com.hushtv.tv.ui.screens.home.MovieCollection>,
    focused: com.hushtv.tv.ui.screens.home.MovieCollection?,
    onFocusedChange: (com.hushtv.tv.ui.screens.home.MovieCollection) -> Unit,
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        com.hushtv.tv.ui.screens.home.HomeCollectionsHeroLayer(
            coll = focused,
            contentStartPadding = 80.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                com.hushtv.tv.ui.screens.home.HomeCollectionsRow(
                    collections = collections,
                    contentStartPadding = 0.dp,
                    onFocusedCollectionChange = onFocusedChange,
                    onCollectionClick = { c ->
                        nav.navigate(
                            "collection/$playlistId/${c.tmdbCollectionId}/" +
                                Uri.encode(c.displayName)
                        )
                    },
                    onSeeAllClick = {
                        nav.navigate("collections/$playlistId")
                    },
                    firstItemFocus = firstItemFocus,
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}


/**
 * Tolerantly parses the ISO-8601 timestamps returned by the HushTV
 * gateway (shapes seen in the wild include
 * "2026-04-25T16:04:36.835000", "2026-04-25T16:04:36Z", and
 * "2026-04-25T16:04:36+00:00"). Returns null on failure so callers
 * can fall back to a "treat as fresh" default — better to over-show
 * than to incorrectly hide a legitimate request.
 */
private fun parseIsoTimestamp(iso: String): Long? {
    if (iso.isBlank()) return null
    val cleaned = iso.substringBefore('.').substringBefore('+').removeSuffix("Z")
    return runCatching {
        val sdf = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            java.util.Locale.US,
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        sdf.parse(cleaned)?.time
    }.getOrNull()
}
