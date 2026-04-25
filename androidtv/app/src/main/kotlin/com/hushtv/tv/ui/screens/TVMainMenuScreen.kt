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

    val tabs = remember {
        listOf(
            com.hushtv.tv.ui.screens.home.TopNavTab("home",   "Home",    Icons.Default.Home,       null),
            com.hushtv.tv.ui.screens.home.TopNavTab("live",   "Live TV", Icons.Default.Tv,         "browse/$playlistId/live"),
            com.hushtv.tv.ui.screens.home.TopNavTab("movies", "Movies",  Icons.Default.Movie,      "browse/$playlistId/movie"),
            com.hushtv.tv.ui.screens.home.TopNavTab("series", "Series",  Icons.Outlined.Slideshow, "browse/$playlistId/series"),
            com.hushtv.tv.ui.screens.home.TopNavTab("search", "Search",  Icons.Default.Search,     "search/$playlistId"),
        )
    }

    // Focus: first tab (Home) gets initial focus.
    val topNavHomeFocus = remember { FocusRequester() }
    // Focus requester for the first card in each page. Nav-Down lands on
    // the first card of whichever page is currently showing.
    val firstCwFocus = remember { FocusRequester() }
    val firstRequestsFocus = remember { FocusRequester() }
    val firstHubFocus = remember { FocusRequester() }
    val firstDiscoveryFocus = remember { FocusRequester() }
    val firstSsMoviesFocus = remember { FocusRequester() }
    val firstSsSeriesFocus = remember { FocusRequester() }
    val firstGenresMoviesFocus = remember { FocusRequester() }
    val firstGenresSeriesFocus = remember { FocusRequester() }
    val firstYearsMoviesFocus = remember { FocusRequester() }
    val firstCollectionsFocus = remember { FocusRequester() }
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

    // Outstanding-requests fetch — drives the dedicated REQUESTS
    // page in the home pager. Refetched on each main-menu mount via
    // RequestCache (which is also populated by the notification host
    // and the My Requests list).
    val ctxLocal = androidx.compose.ui.platform.LocalContext.current
    var homeRequests by remember {
        mutableStateOf(com.hushtv.tv.data.RequestCache.all())
    }
    // Bumping triggers a recomposition with the latest hidden-set
    // applied — long-press → "Remove" hides via RequestHiddenStore
    // and signals here so the row vanishes immediately.
    var hideTick by remember { mutableStateOf(0) }
    LaunchedEffect(playlistId, hideTick) {
        if (com.hushtv.tv.data.UserContactStore.get(ctxLocal) == null) return@LaunchedEffect
        // Re-apply the hidden filter to the existing cache snapshot.
        homeRequests = com.hushtv.tv.data.RequestHiddenStore
            .filterVisible(ctxLocal, com.hushtv.tv.data.RequestCache.all())
        if (com.hushtv.tv.data.RequestCache.all().isNotEmpty() &&
            com.hushtv.tv.data.RequestCache.ageMs() < 60_000
        ) return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        runCatching {
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.hushtv.tv.data.ContentRequestApi.listRequests(ctxLocal, limit = 20)
            }
            if (res is com.hushtv.tv.data.ContentRequestApi.ListResult.Success) {
                val visible = com.hushtv.tv.data.RequestHiddenStore
                    .filterVisible(ctxLocal, res.requests)
                com.hushtv.tv.data.RequestCache.put(visible)
                homeRequests = visible
            }
        }
    }
    val requestsForPage = remember(homeRequests) {
        val now = System.currentTimeMillis()
        val twentyFourHoursMs = 24L * 60L * 60L * 1000L
        homeRequests
            .filter { r ->
                when (r.status) {
                    com.hushtv.tv.data.ContentRequestApi.Status.PENDING,
                    com.hushtv.tv.data.ContentRequestApi.Status.IN_PROGRESS -> {
                        // Always show open requests so the user can
                        // see what they've asked for at a glance.
                        true
                    }
                    else -> {
                        // Terminal states (added / already_available /
                        // not_found) auto-hide once the user has had
                        // 24 h to digest the result. Falls back to
                        // createdDate if the gateway didn't echo an
                        // updatedDate. Bad timestamps → keep the
                        // request visible (safer than hiding it).
                        val ts = parseIsoTimestamp(r.updatedDate.ifBlank { r.createdDate })
                        ts == null || (now - ts) < twentyFourHoursMs
                    }
                }
            }
            .sortedByDescending { it.updatedDate.ifBlank { it.createdDate } }
            .take(15)
    }
    val hasRequests = requestsForPage.isNotEmpty()

    // Channel history snapshot (last 5 entries with metadata).
    val channelHistory = remember(continueHandle.entries, playlistId) {
        com.hushtv.tv.data.RecentChannelStore.getAll(ctxLocal, playlistId)
            .mapNotNull { id ->
                com.hushtv.tv.data.RecentChannelStore.getMeta(ctxLocal, playlistId, id)
                    ?.let { id to it }
            }
            .take(5)
    }
    val hasChannelHistory = channelHistory.isNotEmpty()
    // Hub is the new combined "FOR YOU" page — visible whenever any
    // of channel history / continue watching / requests has content.
    val hasHub = hasChannelHistory || hasCw || hasRequests

    var currentPage by remember {
        // Sticky: initialised once on first composition and not reset
        // when flags flip later. Prevents the user from being yanked
        // back to the hub after they navigated to DISCOVERY.
        mutableStateOf(if (hasHub) "hub" else "discovery")
    }
    val pageOrder = remember(hasHub) {
        buildList {
            if (hasHub) add("hub")
            add("discovery")
            add("ss_movies")
            add("ss_series")
            add("collections")
            add("genres_movies")
            add("genres_series")
            add("years_movies")
        }
    }
    // If the page list shrinks (e.g. requests dropped to zero) and
    // the user was on a page that no longer exists, fall back to
    // discovery rather than an undefined branch.
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
        // If CW drops to empty while the user is on the CW page (they
        // long-pressed remove), bounce them to Discovery.
        LaunchedEffect(hasCw) {
            if (!hasCw && currentPage == "cw") currentPage = "discovery"
        }

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

        // Nav-Down target — follows the CURRENTLY VISIBLE page so the
        // requestFocus() call always hits a composable that's actually
        // attached to the tree.
        val navDownTarget = currentPage

        // Static top nav height — hero + content start right below it
        // at a constant 72 dp offset. No animation.
        val navHeightDp = 72.dp
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = navHeightDp),
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
                    "hub" -> com.hushtv.tv.ui.requests.TVHomeHubPage(
                        playlistId = playlistId,
                        nav = nav,
                        channelHistory = channelHistory,
                        cwEntries = continueEntries,
                        requests = requestsForPage,
                        firstItemFocus = firstHubFocus,
                        onUpFromTop = showNavAndFocus,
                        onDownFromBottom = {
                            val idx = pageOrder.indexOf("hub")
                            if (idx >= 0 && idx + 1 < pageOrder.size) {
                                currentPage = pageOrder[idx + 1]
                            }
                        },
                        onRemoveCw = { removePromptFor = it },
                        onRequestHidden = { hideTick += 1 },
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
                        onUpFromRow = { currentPage = "discovery" },
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
                        onDownFromRow = { currentPage = "years_movies" },
                    )
                    "years_movies" -> YearsPage(
                        playlistId = playlistId,
                        nav = nav,
                        years = movieYears,
                        focused = focusedMovieYear,
                        onFocusedChange = { focusedMovieYear = it },
                        firstItemFocus = firstYearsMoviesFocus,
                        onUpFromRow = { currentPage = "genres_series" },
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
                        onDownFromRow = { currentPage = "ss_movies" },
                    )
                }
            }

            // Auto-focus the new page's first card when the user slides
            // between pages.
            LaunchedEffect(currentPage) {
                kotlinx.coroutines.delay(320)
                runCatching {
                    when (currentPage) {
                        "hub" -> if (hasHub) firstHubFocus.requestFocus()
                        "requests" -> if (hasRequests) firstRequestsFocus.requestFocus()
                        "cw" -> if (hasCw) firstCwFocus.requestFocus()
                        "discovery" -> firstDiscoveryFocus.requestFocus()
                        "collections" -> firstCollectionsFocus.requestFocus()
                        "ss_movies" -> firstSsMoviesFocus.requestFocus()
                        "ss_series" -> firstSsSeriesFocus.requestFocus()
                        "genres_movies" -> firstGenresMoviesFocus.requestFocus()
                        "genres_series" -> firstGenresSeriesFocus.requestFocus()
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
                        "hub" -> "FOR YOU"
                        "requests" -> "REQUESTS"
                        "cw" -> "WATCHING"
                        "discovery" -> "DISCOVER"
                        "collections" -> "COLLECT"
                        "ss_movies" -> "MOVIES"
                        "ss_series" -> "SERIES"
                        "genres_movies" -> "G·MOV"
                        "genres_series" -> "G·SER"
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
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .onPreviewKeyEvent { ev ->
                    // D-pad DOWN from any top-nav tab → focus first card
                    // of the CURRENTLY VISIBLE page.
                    if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        ev.key == androidx.compose.ui.input.key.Key.DirectionDown
                    ) {
                        val target = when (navDownTarget) {
                            "hub" -> firstHubFocus
                            "requests" -> firstRequestsFocus
                            "cw" -> firstCwFocus
                            "collections" -> firstCollectionsFocus
                            "ss_movies" -> firstSsMoviesFocus
                            "ss_series" -> firstSsSeriesFocus
                            "genres_movies" -> firstGenresMoviesFocus
                            "genres_series" -> firstGenresSeriesFocus
                            "years_movies" -> firstYearsMoviesFocus
                            else -> firstDiscoveryFocus
                        }
                        runCatching { target.requestFocus() }
                        true
                    } else false
                },
        ) {
            com.hushtv.tv.ui.screens.home.TopNavBar(
                tabs = tabs,
                activeKey = "home",
                homeFocus = topNavHomeFocus,
                onTab = { t -> t.route?.let { nav.navigate(it) } },
                onSettings = { nav.navigate("settings/$playlistId") },
                daysLeft = daysLeft,
            )
        }

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

        // Expiry pill (only when expanded). Subtle — no background, just
        // quiet text in the corner.
        AnimatedVisibility(
            visible = expanded && expiryStr != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            expiryStr?.let { exp ->
                Column(Modifier.padding(start = 18.dp, end = 12.dp, bottom = 8.dp)) {
                    Text(
                        "EXPIRES",
                        color = TextDim,
                        fontSize = 9.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        exp,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                    )
                    daysLeft?.let { d ->
                        when {
                            d in 0..7 -> {
                                Spacer(Modifier.height(4.dp))
                                Badge(text = "${d}d left", bg = Amber, fg = Color.Black)
                            }
                            d < 0 -> {
                                Spacer(Modifier.height(4.dp))
                                Badge(text = "Expired", bg = Red, fg = Color.White)
                            }
                        }
                    }
                }
            }
        }

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
                        val encoded = Uri.encode(y.searchKeyword)
                        val catParam = y.xtreamCategoryId?.let { "&catId=$it" }.orEmpty()
                        nav.navigate("browse/$playlistId/movie?category=$encoded$catParam")
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
