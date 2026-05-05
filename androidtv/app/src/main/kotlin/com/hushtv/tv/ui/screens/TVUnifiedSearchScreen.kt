@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.TitleMatcher
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.requests.RequestContentSheet
import com.hushtv.tv.ui.screens.home.MovieCollection
import com.hushtv.tv.ui.screens.home.rememberMovieCollections
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.util.safeFocusTraversal
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified "Search Everything" screen.
 *
 * One query → parallel scan across four sources on the Xtream provider
 *     • Live channels
 *     • Movies (VOD)
 *     • Series
 * ...plus the local Collections catalog in-memory. Results are grouped
 * and displayed as horizontal rails — Netflix-style — so users see
 * what kind of content matched at a glance.
 *
 * Filtering: matches when any normalised word of the query is a prefix
 * of any normalised word in the title, OR a substring of the full
 * normalised title. This is faster than the strict franchise matcher
 * and more permissive — fine for an interactive search experience.
 */
@Composable
fun TVUnifiedSearchScreen(
    nav: NavController,
    playlistId: String,
) {
    val ctx = LocalContext.current
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Per-click "resolving" state — when the user clicks a series
    // result we kick off the disambiguating resolver to find the
    // canonical series_id (the one with episodes loaded), THEN
    // navigate. While the resolver is running we show a small
    // overlay so the user knows something is happening. Without
    // this the search-flow series_id can be a stale duplicate that
    // returns empty episodes (the bug user reported repeatedly).
    var resolvingSeriesNav by remember { mutableStateOf(false) }

    // ── Preloaded indices (one shot per session) ──
    var liveAll by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var moviesAll by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var seriesAll by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    val collectionsAll = rememberMovieCollections()
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val live = async { runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "live") }.getOrDefault(emptyList()) }
            val mov  = async { runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "movie") }.getOrDefault(emptyList()) }
            val ser  = async { runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "series") }.getOrDefault(emptyList()) }
            liveAll = live.await()
            moviesAll = mov.await()
            seriesAll = ser.await()
        }
        loading = false
    }

    // ── Query + filtered buckets ──
    // Live-typing would filter three Xtream lists (can each be 10k+
    // titles) on the main thread per keystroke → ANR / OOM / crash on
    // big providers. Debounce the query + move the filtering work to
    // Dispatchers.Default so typing is always buttery smooth and the
    // provider lists are walked at most once every 350 ms.
    var query by remember { mutableStateOf("") }
    val debouncedQuery = rememberDebouncedQuery(query, delayMs = 350L)

    val liveHits by rememberAsyncFiltered(debouncedQuery, liveAll) { it.title }
    val moviesHits by rememberAsyncFiltered(debouncedQuery, moviesAll) { it.title }
    val seriesHits by rememberAsyncFiltered(debouncedQuery, seriesAll) { it.title }
    val collectionsHits by rememberAsyncFiltered(debouncedQuery, collectionsAll) { it.displayName }

    val totalHits = liveHits.size + moviesHits.size + seriesHits.size + collectionsHits.size

    // Show a spinner while the debounced query catches up to the raw
    // query — prevents "no results" flashing between keystrokes.
    val filtering = query.trim() != debouncedQuery.trim() && query.isNotBlank()

    // ── Focus ──
    val firstLiveFocus = remember { FocusRequester() }
    val firstMovieFocus = remember { FocusRequester() }
    val firstSeriesFocus = remember { FocusRequester() }
    val firstCollFocus = remember { FocusRequester() }
    val requestCtaFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }

    var showRequestModal by remember { mutableStateOf(false) }

    // Auto-focus search on entry.
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { searchFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 72.dp),
        ) {
            // ── Toolbar ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 26.dp)
                        .background(Cyan, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "SEARCH EVERYTHING",
                        color = Cyan,
                        fontSize = 10.sp,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (query.isBlank()) "Channels · Movies · Series · Franchises"
                        else "$totalHits results for \"$query\"",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                UnifiedSearchBar(
                    value = query,
                    onChange = { query = it },
                    focusRequester = searchFocus,
                    // Primary DOWN target: first bucket that has results.
                    downTarget = when {
                        liveHits.isNotEmpty() -> firstLiveFocus
                        moviesHits.isNotEmpty() -> firstMovieFocus
                        seriesHits.isNotEmpty() -> firstSeriesFocus
                        collectionsHits.isNotEmpty() -> firstCollFocus
                        else -> searchFocus
                    },
                    modifier = Modifier.width(520.dp),
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

            // ── Results or empty state ──
            when {
                loading -> CenterNote("Indexing your library…")
                query.isBlank() -> CenterNote("Type anything — channels, movies, series or franchises.")
                filtering -> CenterNote("Searching…")
                totalHits == 0 -> NoMatchesState(
                    query = query.trim(),
                    requestFocus = requestCtaFocus,
                    onRequest = { showRequestModal = true },
                )
                else -> {
                    // Regular Column + verticalScroll — NOT a LazyColumn.
                    // The result rows live inside horizontal LazyRows
                    // already, so the vertical content is at most 4
                    // items (live, movies, series, collections). With a
                    // LazyColumn, rows below the viewport weren't being
                    // composed, so their `firstXFocus` requesters had
                    // nothing to attach to and DOWN-from-card-to-card
                    // navigation hit a dead end (Compose's 2D focus
                    // search can't find a focusable that hasn't been
                    // laid out yet). The user reproduced this by
                    // searching titles with both Series and Franchise
                    // hits — the franchise row was off-screen until the
                    // LazyColumn happened to scroll it in. Regular
                    // verticalScroll composes all children up-front so
                    // every row's first card is always a valid focus
                    // target.
                    val resultsScrollState = rememberScrollState()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(resultsScrollState)
                            .padding(vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        if (liveHits.isNotEmpty()) {
                            SearchResultRow(
                                label = "LIVE CHANNELS · ${liveHits.size}",
                                accent = Color(0xFFEF4444),
                                items = liveHits,
                                firstItemFocus = firstLiveFocus,
                            ) { mc, fr ->
                                PosterCard(
                                    title = mc.title,
                                    image = mc.poster,
                                    badge = "LIVE",
                                    badgeTint = Color(0xFFEF4444),
                                    focusRequester = fr,
                                    onClick = {
                                        val p = playlist ?: return@PosterCard
                                        val url = XtreamApi.liveUrl(
                                            p.host, p.username, p.password, mc.streamId,
                                        )
                                        nav.navigate(
                                            "player/$playlistId/${Uri.encode(url)}/${Uri.encode(mc.title)}/true"
                                        )
                                    },
                                )
                            }
                        }
                        if (moviesHits.isNotEmpty()) {
                            SearchResultRow(
                                label = "MOVIES · ${moviesHits.size}",
                                accent = Cyan,
                                items = moviesHits,
                                firstItemFocus = firstMovieFocus,
                            ) { mc, fr ->
                                PosterCard(
                                    title = mc.title,
                                    image = mc.poster,
                                    badge = "MOVIE",
                                    badgeTint = Cyan,
                                    focusRequester = fr,
                                    onClick = {
                                        nav.navigate(
                                            "moviedetail/$playlistId/${mc.streamId}/${Uri.encode(mc.title)}"
                                        )
                                    },
                                )
                            }
                        }
                        if (seriesHits.isNotEmpty()) {
                            SearchResultRow(
                                label = "SERIES · ${seriesHits.size}",
                                accent = Color(0xFF8B5CF6),
                                items = seriesHits,
                                firstItemFocus = firstSeriesFocus,
                            ) { mc, fr ->
                                PosterCard(
                                    title = mc.title,
                                    image = mc.poster,
                                    badge = "SERIES",
                                    badgeTint = Color(0xFF8B5CF6),
                                    focusRequester = fr,
                                    onClick = {
                                        // Resolve to the canonical
                                        // series_id BEFORE navigating.
                                        // The resolver short-circuits
                                        // (one network call) when
                                        // mc.seriesId already has
                                        // episodes; only kicks the
                                        // expensive multi-category
                                        // walk when the supplied id
                                        // returns empty.
                                        if (resolvingSeriesNav) return@PosterCard
                                        val p = playlist ?: return@PosterCard
                                        resolvingSeriesNav = true
                                        scope.launch {
                                            val resolvedId = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    com.hushtv.tv.data.XtreamApi
                                                        .resolveSeriesInfo(
                                                            p.host, p.username, p.password,
                                                            mc.seriesId.toString(), mc.title,
                                                        ).seriesId
                                                }.getOrNull() ?: mc.seriesId.toString()
                                            }
                                            resolvingSeriesNav = false
                                            nav.navigate(
                                                "series/$playlistId/$resolvedId/${Uri.encode(mc.title)}"
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        if (collectionsHits.isNotEmpty()) {
                            SearchResultRow(
                                label = "FRANCHISES · ${collectionsHits.size}",
                                accent = Color(0xFFF97316),
                                items = collectionsHits,
                                firstItemFocus = firstCollFocus,
                            ) { coll, fr ->
                                CollectionPosterCard(
                                    coll = coll,
                                    focusRequester = fr,
                                    onClick = {
                                        nav.navigate(
                                            "collection/$playlistId/${coll.tmdbCollectionId}/${Uri.encode(coll.displayName)}"
                                        )
                                    },
                                )
                            }
                        }
                        // Footer: request CTA — even when matches
                        // exist, give users a way to request the exact
                        // title they were looking for (e.g. typo, or
                        // their library doesn't carry that franchise).
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp, vertical = 8.dp),
                        ) {
                            RequestCta(
                                label = "Don't see it? Request \"${query.trim()}\"",
                                focusRequester = requestCtaFocus,
                                onClick = { showRequestModal = true },
                            )
                        }
                    }
                }
            }
        }

        // Top-left Back-to-Home chip — replaces the old top nav.
        // Matches the full-screen pattern used by Movies / Series /
        // Live TV / Hush+ etc.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 32.dp, top = 24.dp),
        ) {
            com.hushtv.tv.ui.screens.home.BackToHomeChip(
                nav = nav,
                playlistId = playlistId,
            )
        }
    }

    // Request modal — full-screen scrim. Renders ON TOP of the search
    // surface and dismissed via the Cancel / Close buttons inside the
    // sheet.
    if (showRequestModal) {
        val presetType = if (seriesHits.isNotEmpty() && moviesHits.isEmpty()) "series" else "movie"
        com.hushtv.tv.ui.requests.RequestContentSheet(
            presetType = presetType,
            presetTitle = query.trim(),
            playlistId = playlistId,
            onDismiss = { showRequestModal = false },
            onViewMyRequests = {
                showRequestModal = false
                nav.navigate("myrequests/$playlistId")
            },
            onAlreadyAvailable = { entry ->
                showRequestModal = false
                if (entry.kind == "series") {
                    nav.navigate(
                        "series/$playlistId/${entry.seriesId}/${Uri.encode(entry.title)}"
                    )
                } else {
                    nav.navigate(
                        "moviedetail/$playlistId/${entry.streamId}/${Uri.encode(entry.title)}"
                    )
                }
            },
        )
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

/**
 * Returns the input query after no keystrokes for [delayMs]. Prevents
 * every character press from triggering a big filter pass on the
 * main thread. 350 ms is the sweet spot: feels instant while cutting
 * ~10× keystroke amplification on typical typing speed.
 */
@Composable
private fun rememberDebouncedQuery(query: String, delayMs: Long = 350L): String {
    var debounced by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        delay(delayMs)
        debounced = query
    }
    return debounced
}

/**
 * Async, off-main-thread filter. Runs on Dispatchers.Default (CPU
 * pool) so big Xtream libraries never stall the UI. Bails out of the
 * current computation when the query changes (LaunchedEffect key)
 * — no wasted work when the user keeps typing during a slow pass.
 *
 * Matching rules:
 *   1. Full normalised query appears as substring of title, OR
 *   2. Every normalised query word is a prefix of a title word.
 * Caps each bucket at 40 hits to keep render cheap.
 */
@Composable
private fun <T> rememberAsyncFiltered(
    query: String,
    source: List<T>,
    titleOf: (T) -> String,
): androidx.compose.runtime.State<List<T>> {
    val state = remember { mutableStateOf<List<T>>(emptyList()) }

    // Reset to empty immediately whenever the query goes blank so the
    // UI never shows stale hits under an empty search bar.
    LaunchedEffect(query, source) {
        val q = TitleMatcher.normalize(query)
        if (q.isBlank() || source.isEmpty()) {
            state.value = emptyList()
            return@LaunchedEffect
        }
        val queryWords = q.split(" ").filter { it.isNotBlank() }
        // Push the actual scan off the main thread so a 30k-item walk
        // can never ANR, even on cheap TV hardware.
        val result = withContext(Dispatchers.Default) {
            val bag = ArrayList<T>(40)
            for (item in source) {
                val norm = TitleMatcher.normalize(titleOf(item))
                if (norm.isBlank()) continue
                val match = norm.contains(q) || run {
                    val words = norm.split(" ")
                    queryWords.all { qw -> words.any { it.startsWith(qw) } }
                }
                if (match) {
                    bag += item
                    if (bag.size >= 40) break
                }
            }
            bag
        }
        state.value = result
    }
    return state
}

@Composable
private fun CenterNote(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            msg,
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            fontFamily = Inter,
        )
    }
}

/**
 * Empty-results state with a primary "Request this title" CTA. Used
 * when the live search index has zero matches across all four
 * buckets — users can submit a content request directly from here.
 */
@Composable
private fun NoMatchesState(
    query: String,
    requestFocus: FocusRequester,
    onRequest: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Text(
                "Nothing matches \"$query\".",
                color = Color(0xFFE2E8F0),
                fontSize = 18.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Want it added? Submit a request and we'll get on it.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontFamily = Inter,
            )
            RequestCta(
                label = "Request \"$query\"",
                focusRequester = requestFocus,
                onClick = onRequest,
            )
        }
    }
    LaunchedEffect(query) {
        delay(180)
        runCatching { requestFocus.requestFocus() }
    }
}

/**
 * D-pad-focusable cyan pill that opens the request modal. Mirrors the
 * primary CTA styling used elsewhere in the TV UI.
 */
@Composable
private fun RequestCta(
    label: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)

    // Modern dark-surface CTA with cyan accent. Was previously a
    // solid-cyan pill with dark-navy text — the low-contrast dark
    // text on bright cyan was almost unreadable on real TVs (user
    // screenshot showed the label nearly invisible). Now: dark
    // glassy surface with a cyan border that intensifies on focus,
    // bright white label, plus an icon prefix for at-a-glance
    // intent.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .height(56.dp)
            .background(
                if (focused) Cyan.copy(alpha = 0.20f)
                else Color(0x14FFFFFF),
                shape,
            )
            .border(
                width = if (focused) 3.dp else 1.5.dp,
                color = if (focused) Cyan else Cyan.copy(alpha = 0.45f),
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 28.dp),
    ) {
        // Plus-circle glyph — clearly signals "create / submit"
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (focused) Cyan else Cyan.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                null,
                tint = Color(0xFF05080F),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            label,
            color = Color.White,
            fontSize = 16.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun <T> SearchResultRow(
    label: String,
    accent: Color,
    items: List<T>,
    firstItemFocus: FocusRequester,
    cardContent: @Composable (T, FocusRequester?) -> Unit,
) {
    Column(Modifier.padding(start = 40.dp, end = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 14.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items) { item ->
                // Pass the focus requester DOWN INTO the card itself
                // (rather than its non-focusable wrapper Box) so the
                // search bar's `firstItemFocus.requestFocus()` from
                // the DOWN keypress lands on something focusable.
                // Previously the requester sat on a wrapper Box that
                // wasn't `.focusable()`, so requestFocus() silently
                // no-op'd and the user got stuck on the search bar.
                cardContent(item, if (item == items.firstOrNull()) firstItemFocus else null)
            }
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    image: String?,
    badge: String,
    badgeTint: Color,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(124.dp)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1.05f, shape = shape)
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Cyan else Color.Transparent,
                    shape = shape,
                ),
        ) {
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0B1220))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xCC0B1220), RoundedCornerShape(6.dp))
                    .border(1.dp, badgeTint.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    badge,
                    color = badgeTint,
                    fontSize = 8.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionPosterCard(
    coll: MovieCollection,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(180.dp)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1.05f, shape = shape)
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) coll.accent else Color.Transparent,
                    shape = shape,
                ),
        ) {
            coll.backdropUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = coll.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x33000000),
                            1f to Color(0xEB000000),
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(coll.accent, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "FRANCHISE",
                        color = Color.White,
                        fontSize = 8.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    coll.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UnifiedSearchBar(
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    downTarget: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .background(SurfaceNavy, RoundedCornerShape(12.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) Cyan else TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Inter),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { ev ->
                        // Google TV's IME dispatches KeyDown + KeyUp pairs;
                        // consuming only KeyDown leaves the matching KeyUp
                        // unconsumed which has crashed FocusOwnerImpl
                        // .focusSearch on some hardware. Consume both
                        // halves of the DPAD-Down event so the matching
                        // KeyUp never reaches the framework's focus
                        // search at all.
                        if (ev.key == Key.DirectionDown) {
                            if (ev.type == KeyEventType.KeyDown) {
                                runCatching { downTarget.requestFocus() }
                            }
                            true
                        } else false
                    },
            )
            if (value.isEmpty()) {
                Text(
                    "Search across all of HushTV…",
                    color = TextMuted,
                    fontSize = 16.sp,
                    fontFamily = Inter,
                )
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .focusable()
                    .safeFocusTraversal(onDown = downTarget)
                    .clickableWithEnter { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
