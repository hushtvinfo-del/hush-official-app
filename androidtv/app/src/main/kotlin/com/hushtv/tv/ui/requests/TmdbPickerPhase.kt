package com.hushtv.tv.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RequestMetaStore
import com.hushtv.tv.data.TmdbSearchHit
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One picked TMDB candidate, fully resolved against the user's
 * library so the form layer knows whether to allow the request or
 * just deep-link the user into the title they clearly already have.
 */
data class TmdbPick(
    val tmdbId: Int,
    val tmdbType: String,             // "movie" | "tv"
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    /** Already-in-library hit, when found. */
    val library: LibraryIndex.Entry?,
)

/**
 * Phase rendered by the request modal between Contact and the final
 * notes / submit step. Lets the user pick a TMDB title from a poster
 * grid instead of typing a free-text "Title" field.
 *
 * Behaviours:
 *  • Live-search debounced @ 350 ms while the user types
 *  • Pre-filled from caller's preset query
 *  • Each result shows poster + year + "Already in your library"
 *    badge when [LibraryIndex] flags it as a hit
 *  • Already-in-library results route straight to the title via
 *    [onAlreadyAvailable] (no submit, no duplicate request)
 *  • Free-text fallback: if TMDB returns zero results, the user can
 *    still submit "{query}" as a typed request (handles obscure
 *    titles TMDB doesn't index)
 */
@Composable
fun TmdbPickerPhase(
    type: String,                       // "movie" | "series"
    presetQuery: String,
    playlistId: String,
    onCancel: () -> Unit,
    onChangeType: (String) -> Unit,
    onPicked: (TmdbPick) -> Unit,
    onAlreadyAvailable: (LibraryIndex.Entry) -> Unit,
    onFreeTextSubmit: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf(presetQuery) }
    var loading by remember { mutableStateOf(false) }
    var libraryReady by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<TmdbHitWithLibrary>>(emptyList()) }
    var lastSearchedQuery by remember { mutableStateOf("") }
    var decoratedQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var decorateJob by remember { mutableStateOf<Job?>(null) }

    // Prime the LibraryIndex so the "already available" badge can
    // light up for matching results. Cache hit on subsequent uses.
    LaunchedEffect(playlistId) {
        val playlist = PlaylistStore.find(ctx, playlistId)
        if (playlist != null) {
            libraryReady = withContext(Dispatchers.IO) {
                LibraryIndex.prime(ctx, playlist)
            }
        }
    }

    // Stage 1 — TMDB call only. NOT keyed on libraryReady so a slow
    // library prime can't re-trigger the entire search loop after the
    // user has already started looking at results (this was the
    // 15-second-then-crash path: prime finishes → effect re-runs →
    // 20 parallel library scans fan out on a 50k-title library →
    // OOM on TV boxes).
    LaunchedEffect(query, type) {
        searchJob?.cancel()
        decorateJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            hits = emptyList()
            loading = false
            searchError = null
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(350)
            loading = true
            searchError = null
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    if (type == "series") TmdbService.searchTvList(q)
                    else TmdbService.searchMoviesList(q)
                }.getOrElse {
                    searchError = "Couldn't reach TMDB. Check your connection."
                    emptyList()
                }
            }
            hits = raw.map { TmdbHitWithLibrary(it, libraryEntry = null) }
            lastSearchedQuery = q
            loading = false
        }
    }

    // Stage 2 — Library decoration. Independent of stage 1 so it can
    // wait quietly for the library to finish priming without nuking
    // the user's already-rendered TMDB results. Concurrency capped at
    // 4 with a 6-second hard timeout so the worst-case scan can't
    // hang the app.
    LaunchedEffect(lastSearchedQuery, libraryReady, type) {
        decorateJob?.cancel()
        if (!libraryReady) return@LaunchedEffect
        val q = lastSearchedQuery
        if (q.isBlank() || q == decoratedQuery) return@LaunchedEffect
        if (hits.isEmpty()) return@LaunchedEffect
        val snapshot = hits
        decorateJob = scope.launch {
            runCatching {
                kotlinx.coroutines.withTimeoutOrNull(6_000) {
                    val libKind = if (type == "series") "series" else "movie"
                    val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                    val decorated = withContext(Dispatchers.IO) {
                        snapshot.map { wrapped ->
                            async {
                                semaphore.acquire()
                                try {
                                    val title = wrapped.hit.title
                                        ?: wrapped.hit.name ?: ""
                                    val year =
                                        parseYear(wrapped.hit.release_date)
                                            ?: parseYear(wrapped.hit.first_air_date)
                                    val libHit = runCatching {
                                        LibraryIndex.findBest(title, libKind, year)
                                    }.getOrNull()
                                    TmdbHitWithLibrary(wrapped.hit, libHit)
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }.awaitAll()
                    }
                    if (q == lastSearchedQuery) {
                        hits = decorated
                        decoratedQuery = q
                    }
                }
            }
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { firstFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize()) {
        // ─── LEFT PANE: header + search + type pills + cancel ───
        Column(
            Modifier
                .width(440.dp)
                .fillMaxSize()
                .padding(end = 32.dp),
        ) {
            Text(
                "REQUEST MISSING CONTENT",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Search for any movie or series",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 34.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Pick a real TMDB title and we'll add it for you. " +
                    "Already-available titles route you straight into your library.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(22.dp))
            TypeRadioRow(
                type = type,
                onChangeType = onChangeType,
                firstFocus = firstFocus,
            )

            Spacer(Modifier.height(18.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                focusRequester = null,
                placeholder = if (type == "series") "Type a series name…"
                else "Type a movie name…",
            )

            Spacer(Modifier.height(14.dp))
            // Status hint just below the search field — keeps the
            // user oriented while they're typing.
            val hint = when {
                query.trim().length < 2 -> "Type at least 2 characters."
                loading -> "Searching TMDB…"
                hits.isEmpty() && lastSearchedQuery.isNotBlank() ->
                    "No matches yet. Keep typing or hit \"Submit anyway\" on the right."
                else -> "${hits.size} result${if (hits.size == 1) "" else "s"}."
            }
            Text(
                hint,
                color = TextSecondary,
                fontSize = 12.sp,
            )

            Spacer(Modifier.weight(1f))
            SecondaryButtonInline(label = "Cancel", onClick = onCancel)
        }

        // ─── RIGHT PANE: result grid / loading / empty / type-prompt ───
        Box(Modifier.fillMaxSize().padding(start = 4.dp)) {
            when {
                query.trim().length < 2 -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Start typing on the left",
                        color = TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Results from TMDB will appear here.",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                    )
                }
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Searching TMDB…",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        color = Cyan,
                        trackColor = Color(0x22FFFFFF),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hold tight — gathering matches.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                    )
                }
                searchError != null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        searchError ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "We'll retry as soon as you type something new.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                hits.isEmpty() -> EmptyTmdbState(
                    query = query.trim(),
                    onFreeTextSubmit = onFreeTextSubmit,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(hits, key = { it.hit.id }) { wrapped ->
                        TmdbHitRow(
                            wrapped = wrapped,
                            type = type,
                            onPick = onClick@{
                                val title = wrapped.hit.title ?: wrapped.hit.name ?: ""
                                val year = parseYear(wrapped.hit.release_date)
                                    ?: parseYear(wrapped.hit.first_air_date)
                                val pick = TmdbPick(
                                    tmdbId = wrapped.hit.id,
                                    tmdbType = if (type == "series") "tv" else "movie",
                                    title = title,
                                    year = year,
                                    posterPath = wrapped.hit.poster_path,
                                    backdropPath = wrapped.hit.backdrop_path,
                                    overview = wrapped.hit.overview.ifBlank { null },
                                    library = wrapped.libraryEntry,
                                )
                                // For SERIES we always route the parent
                                // to the series-detail phase (regardless
                                // of in-library status) so the user can
                                // pick "Tap to Watch" OR "Request Whole
                                // Series" OR "Request Missing Episodes".
                                // The library entry is carried inside
                                // the pick; the parent decides what to
                                // do.
                                if (type == "series") {
                                    onPicked(pick)
                                    return@onClick
                                }
                                // Movies — keep the existing fast-path:
                                // already-in-library = deep-link, else
                                // submit immediately.
                                if (wrapped.libraryEntry != null) {
                                    scope.launch {
                                        val playlist = withContext(Dispatchers.IO) {
                                            com.hushtv.tv.data.PlaylistStore.find(ctx, playlistId)
                                        }
                                        val confirmed = if (playlist != null) {
                                            val resolved = withContext(Dispatchers.IO) {
                                                com.hushtv.tv.data.TmdbIdResolver
                                                    .resolveTmdbId(playlist, wrapped.libraryEntry)
                                            }
                                            resolved == null || resolved == wrapped.hit.id
                                        } else true
                                        if (confirmed) {
                                            onAlreadyAvailable(wrapped.libraryEntry)
                                        } else {
                                            onPicked(pick)
                                        }
                                    }
                                } else {
                                    onPicked(pick)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class TmdbHitWithLibrary(
    val hit: TmdbSearchHit,
    val libraryEntry: LibraryIndex.Entry?,
)

/* ───────── Search field ───────── */

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester?,
    placeholder: String,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(SurfaceElev, RoundedCornerShape(12.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(Cyan),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .onFocusChanged { focused = it.isFocused },
        )
        if (value.isEmpty()) {
            Text(placeholder, color = Color(0xFF64748B), fontSize = 15.sp)
        }
    }
}

/* ───────── Type radio (Movie / Series) ───────── */

@Composable
private fun TypeRadioRow(
    type: String,
    onChangeType: (String) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TypePill(
            "🎬 Movie",
            selected = type == "movie",
            modifier = Modifier.weight(1f),
            focusRequester = firstFocus,
        ) { onChangeType("movie") }
        TypePill(
            "📺 Series",
            selected = type == "series",
            modifier = Modifier.weight(1f),
            focusRequester = null,
        ) { onChangeType("series") }
    }
}

@Composable
private fun TypePill(
    label: String, selected: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(46.dp)
            .background(
                if (selected) Cyan.copy(alpha = 0.18f) else SurfaceElev,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    selected -> Cyan.copy(alpha = 0.7f)
                    else -> Color(0x33FFFFFF)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected || focused) Cyan else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* ───────── TMDB hit row ───────── */

@Composable
private fun TmdbHitRow(
    wrapped: TmdbHitWithLibrary,
    onPick: () -> Unit,
    type: String,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val hit = wrapped.hit
    val title = hit.title ?: hit.name ?: "—"
    val year = parseYear(hit.release_date) ?: parseYear(hit.first_air_date)
    val inLibrary = wrapped.libraryEntry != null
    val isSeries = type == "series"

    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(12.dp))
            .border(
                width = if (rowFocused) 2.dp else 1.dp,
                color = when {
                    rowFocused -> Cyan
                    inLibrary -> Color(0xFF34D399)
                    else -> Color(0x22FFFFFF)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { rowFocused = it.isFocused }
            .focusable()
            .clickableWithEnter(onPick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Poster — TMDB w185 size is plenty for a row thumbnail.
        Box(
            Modifier
                .width(64.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center,
        ) {
            val url = TmdbService.img(hit.poster_path, "w185")
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    if (hit.title != null) "🎬" else "📺",
                    fontSize = 22.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (year != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    year.toString(),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Badge text differs by content type. For SERIES we route
            // every click to the series-detail phase (regardless of
            // library state) so the badge always says "TAP FOR
            // OPTIONS"; for MOVIES the existing fast-path applies
            // (in-library → deep-link, else → submit).
            when {
                isSeries && inLibrary -> AvailabilityBadge(
                    label = "ALREADY IN LIBRARY · TAP FOR OPTIONS",
                    bg = Color(0x3322C55E),
                    fg = Color(0xFF34D399),
                )
                isSeries -> AvailabilityBadge(
                    label = "TAP TO REQUEST OR PICK EPISODES",
                    bg = Cyan.copy(alpha = 0.15f),
                    fg = Cyan,
                )
                inLibrary -> AvailabilityBadge(
                    label = "ALREADY IN YOUR LIBRARY · TAP TO WATCH",
                    bg = Color(0x3322C55E),
                    fg = Color(0xFF34D399),
                )
                else -> AvailabilityBadge(
                    label = "TAP TO REQUEST",
                    bg = Cyan.copy(alpha = 0.15f),
                    fg = Cyan,
                )
            }
        }
    }
}

@Composable
private fun AvailabilityBadge(label: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
    }
}

/* ───────── Empty TMDB state w/ free-text fallback ───────── */

@Composable
private fun EmptyTmdbState(query: String, onFreeTextSubmit: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .background(SurfaceElev, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "No TMDB results for \"$query\".",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Submit it as a typed request anyway — we'll figure it out.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        FreeTextSubmitButton(query, onClick = { onFreeTextSubmit(query) })
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FreeTextSubmitButton(query: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .background(if (focused) Cyan else Cyan.copy(alpha = 0.85f),
                RoundedCornerShape(22.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(22.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            "Request \"$query\" anyway",
            color = Color(0xFF05080F),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

/* ───────── Generic small button ───────── */

@Composable
private fun SecondaryButtonInline(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(
                if (focused) Cyan.copy(alpha = 0.18f) else SurfaceElev,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) Cyan else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun parseYear(date: String?): Int? {
    if (date.isNullOrBlank() || date.length < 4) return null
    return date.substring(0, 4).toIntOrNull()
}
