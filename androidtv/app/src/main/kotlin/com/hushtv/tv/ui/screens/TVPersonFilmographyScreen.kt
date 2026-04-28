package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.ui.requests.RequestContentSheet
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Person filmography page — opened when a user taps a cast member
 * card on a movie or series detail screen.
 *
 * # Layout strategy
 *
 * Earlier iterations of this screen used `Modifier.weight(1f)` and
 * `aspectRatio(2f/3f)` to size cards. On a 1920×1080 TV that pushed
 * card width to ~349 dp and the 2:3 poster shot up to ~524 dp tall —
 * way past the available height. Cards spilled over each other and
 * the underlying detail screen bled through the gaps.
 *
 * The fix is a fully deterministic grid: we use [BoxWithConstraints]
 * to read the actual viewport size, then compute exact card width
 * and height numbers that mathematically fit a 5×2 grid plus the
 * paginator. NOTHING uses aspectRatio or weight for sizing — every
 * dimension is in dp and every container has [Modifier.clipToBounds]
 * so even with a layout bug nothing can ever paint outside its
 * bounds again.
 *
 * # Pagination
 *
 * 10 results per page (5 cols × 2 rows). The library cross-reference
 * runs ONLY for the currently visible page so each page is verified
 * in well under a second instead of waiting on 70+ titles.
 *
 * # Click safety
 *
 * Cards are click-blocked until decoration completes for that row
 * (same pattern as the request modal) so users can never request a
 * title we already have.
 */
@Composable
fun TVPersonFilmographyScreen(
    nav: NavController,
    playlistId: String,
    personId: Int,
    personName: String,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val perPage = 8
    var loading by remember { mutableStateOf(true) }
    var libraryReady by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<PersonHit>>(emptyList()) }
    var tab by remember { mutableStateOf("movie") }
    var page by remember(tab) { mutableStateOf(0) }
    var requestPreset by remember {
        mutableStateOf<Pair<String, String>?>(null)
    }

    // Library prime
    LaunchedEffect(playlistId) {
        val playlist = PlaylistStore.find(ctx, playlistId)
        if (playlist != null) {
            libraryReady = withContext(Dispatchers.IO) {
                LibraryIndex.prime(ctx, playlist)
            }
        } else {
            libraryReady = true
        }
    }

    // Stage 1 — fetch credits, sort newest-first.
    LaunchedEffect(personId) {
        loading = true
        val raw = withContext(Dispatchers.IO) {
            runCatching { TmdbService.personFilmography(personId) }
                .getOrDefault(emptyList())
        }
        val sorted = raw.sortedByDescending { parseCreditYear(it) ?: 0 }
        hits = sorted.map { PersonHit(it, libraryEntry = null, decorated = false) }
        val movieCount = sorted.count { it.media_type == "movie" }
        val tvCount = sorted.count { it.media_type == "tv" }
        tab = if (tvCount > movieCount) "tv" else "movie"
        loading = false
    }

    // Stage 2 — decorate ONLY the visible page.
    LaunchedEffect(page, tab, libraryReady, hits.size) {
        if (!libraryReady || hits.isEmpty()) return@LaunchedEffect
        val visible = hits.withIndex()
            .filter { it.value.credit.media_type == tab }
            .drop(page * perPage)
            .take(perPage)
            .filter { !it.value.decorated }
        if (visible.isEmpty()) return@LaunchedEffect
        val rolling = hits.toMutableList()
        for ((idx, ph) in visible) {
            kotlinx.coroutines.yield()
            val title = ph.credit.title ?: ph.credit.name ?: ""
            val year = parseCreditYear(ph.credit)
            val libKind = if (ph.credit.media_type == "tv") "series" else "movie"
            val libHit = withContext(Dispatchers.Default) {
                runCatching {
                    LibraryIndex.findBest(title, libKind, year)
                }.getOrNull()
            }
            rolling[idx] = ph.copy(libraryEntry = libHit, decorated = true)
            hits = rolling.toList()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .clipToBounds(),  // belt-and-braces: no painting can
                              // escape this Box, ever
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 28.dp),
        ) {
            // ── Header row: Back · Filmography/Name · Tabs ──
            // Tabs live INLINE with the title to save vertical space
            // for bigger cards below. Layout left-to-right with the
            // Movies / TV tabs pushed to the right edge.
            val movieCount = hits.count { it.credit.media_type == "movie" }
            val tvCount = hits.count { it.credit.media_type == "tv" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackChip(onClick = { nav.popBackStack() })
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "FILMOGRAPHY",
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        personName,
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilmTab(
                        label = "Movies", count = movieCount,
                        selected = tab == "movie", onClick = { tab = "movie" },
                    )
                    FilmTab(
                        label = "TV", count = tvCount,
                        selected = tab == "tv", onClick = { tab = "tv" },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Body ───────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
            ) {
                when {
                    loading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.5f),
                    ) {
                        Text(
                            "Loading $personName's credits…",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            color = Cyan,
                            trackColor = Color(0x22FFFFFF),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                        )
                    }
                    else -> {
                        val current = hits.filter { it.credit.media_type == tab }
                        if (current.isEmpty()) {
                            Text(
                                if (tab == "movie") "No movie credits."
                                else "No TV credits.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            val totalPages = (current.size + perPage - 1) / perPage
                            if (page >= totalPages) page = 0
                            val pageItems = current.drop(page * perPage).take(perPage)
                            DeterministicGrid(
                                hits = pageItems,
                                totalPages = totalPages,
                                page = page,
                                onPrev = { if (page > 0) page -= 1 },
                                onNext = { if (page < totalPages - 1) page += 1 },
                                onClick = { ph ->
                                    // Belt-and-braces: the card's
                                    // own clickableWithEnter already
                                    // blocks taps on undecorated
                                    // cards. This is a second
                                    // safety check so even if some
                                    // future refactor breaks the
                                    // inner guard, requests still
                                    // can't fire on a row where the
                                    // library cross-reference hasn't
                                    // finished yet.
                                    if (!ph.decorated) return@DeterministicGrid
                                    val title = ph.credit.title
                                        ?: ph.credit.name ?: return@DeterministicGrid
                                    val entry = ph.libraryEntry
                                    if (entry != null) {
                                        val encoded = Uri.encode(title)
                                        if (entry.kind == "series") {
                                            nav.navigate(
                                                "series/$playlistId/${entry.seriesId}/$encoded",
                                            )
                                        } else {
                                            nav.navigate(
                                                "moviedetail/$playlistId/${entry.streamId}/$encoded",
                                            )
                                        }
                                    } else {
                                        requestPreset = (
                                            if (ph.credit.media_type == "tv") "series"
                                            else "movie"
                                        ) to title
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    requestPreset?.let { (type, title) ->
        RequestContentSheet(
            presetType = type,
            presetTitle = title,
            playlistId = playlistId,
            onDismiss = { requestPreset = null },
            onAlreadyAvailable = { entry ->
                requestPreset = null
                val encoded = Uri.encode(entry.title)
                if (entry.kind == "series") {
                    nav.navigate("series/$playlistId/${entry.seriesId}/$encoded")
                } else {
                    nav.navigate("moviedetail/$playlistId/${entry.streamId}/$encoded")
                }
            },
        )
    }
}

private data class PersonHit(
    val credit: TmdbService.PersonCredit,
    val libraryEntry: LibraryIndex.Entry?,
    val decorated: Boolean,
)

private fun parseCreditYear(c: TmdbService.PersonCredit): Int? {
    val raw = c.release_date.takeIf { !it.isNullOrBlank() }
        ?: c.first_air_date.takeIf { !it.isNullOrBlank() }
        ?: return null
    return raw.take(4).toIntOrNull()
}

/* ───────────── Deterministic 4×2 grid + paginator ───────────── */

@Composable
private fun DeterministicGrid(
    hits: List<PersonHit>,
    totalPages: Int,
    page: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClick: (PersonHit) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        // Reserve 56dp at the bottom for the paginator (40dp + 16dp gap).
        // Two rows + one 18dp row gap fills the rest.
        val totalH = maxHeight
        val paginatorBlock = 56.dp
        val rowGap = 18.dp
        val gridH = totalH - paginatorBlock
        val rowH: Dp = (gridH - rowGap) / 2

        Column(Modifier.fillMaxSize()) {
            val rows = hits.chunked(4)
            for ((rowIdx, row) in rows.withIndex()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowH),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { ph ->
                        ImmersiveCreditCard(
                            hit = ph,
                            cardHeight = rowH,
                            onClick = { onClick(ph) },
                        )
                    }
                }
                if (rowIdx == 0 && rows.size > 1) {
                    Spacer(Modifier.height(rowGap))
                }
            }
            Spacer(Modifier.weight(1f, fill = true))
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageChip(
                        label = "← Prev",
                        enabled = page > 0,
                        onClick = onPrev,
                    )
                    Box(
                        Modifier
                            .background(
                                Color(0x14FFFFFF),
                                RoundedCornerShape(999.dp),
                            )
                            .border(
                                1.dp,
                                Color(0x22FFFFFF),
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Page ${page + 1} of $totalPages",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        )
                    }
                    PageChip(
                        label = "Next →",
                        enabled = page < totalPages - 1,
                        onClick = onNext,
                    )
                }
            }
        }
    }
}

/**
 * Immersive card — entire surface is the poster, with title /
 * meta / status pill overlaid on a dark gradient at the bottom.
 *
 * The gradient is what solves the white-on-white readability
 * problem: regardless of the poster's background colour, the
 * bottom 60% of the card is darkened to a near-black, which gives
 * the white title text guaranteed contrast.
 *
 * Aspect ratio is locked to 2:3 with [matchHeightConstraintsFirst]
 * = true so the card height is set externally (one half of the
 * grid area) and the width is derived from it. That keeps every
 * poster at proper movie-poster proportions and never crops the
 * artwork.
 */
@Composable
private fun ImmersiveCreditCard(
    hit: PersonHit,
    cardHeight: Dp,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(hit)
    val ctx = LocalContext.current
    val title = hit.credit.title ?: hit.credit.name ?: "—"
    val year = parseCreditYear(hit.credit)
    val accent = when {
        !hit.decorated -> Color(0xFFF59E0B)
        hit.libraryEntry != null -> Color(0xFF34D399)
        else -> Cyan
    }
    val shape = RoundedCornerShape(14.dp)
    val cardAlpha = if (hit.decorated) 1f else 0.55f
    val poster = TmdbService.img(hit.credit.poster_path, "w500")

    Box(
        Modifier
            .height(cardHeight)
            .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
            .clip(shape)
            .background(SurfaceNavy)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) accent else Color(0x22FFFFFF),
                shape = shape,
            )
            .focusRequester(remember { FocusRequester() })
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter {
                if (current.decorated) {
                    onClick()
                } else {
                    android.widget.Toast.makeText(
                        ctx,
                        "Still checking your library — hang tight…",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .graphicsLayer { alpha = cardAlpha },
    ) {
        // ── Poster (fills the entire card) ──────────────────
        if (!poster.isNullOrBlank()) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title.take(1).uppercase(),
                    color = TextSecondary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        // ── Dark gradient (bottom half) for text readability ──
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        // Linear ramp:
                        //   top 50%  fully transparent
                        //   55–80 %  fade in
                        //   80–100% near-opaque black
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.50f to Color.Transparent,
                            0.75f to Color(0xAA000000),
                            1.0f to Color(0xF0000000),
                        ),
                    ),
                ),
        )

        // ── Status pill (top-right corner) ──────────────────
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        ) {
            ImmersiveStatusPill(hit = hit)
        }

        // ── Title + meta (bottom) ──────────────────────────
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val metaLine = buildString {
                if (year != null) append(year)
                if (hit.credit.character.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("as ${hit.credit.character}")
                }
            }
            if (metaLine.isNotBlank()) {
                Text(
                    metaLine,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Compact variant of the status pill for the immersive card. */
@Composable
private fun ImmersiveStatusPill(hit: PersonHit) {
    val (label, fg, bg) = when {
        !hit.decorated -> Triple(
            "CHECKING…",
            Color(0xFFF59E0B),
            Color(0xCC1A1F2E),
        )
        hit.libraryEntry != null -> Triple(
            "IN LIBRARY",
            Color(0xFF34D399),
            Color(0xDD0F2A1E),
        )
        else -> Triple(
            "TAP TO REQUEST",
            Cyan,
            Color(0xDD0E2530),
        )
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, fg.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ───────────────────────── Atoms ───────────────────────── */

@Composable
private fun FilmTab(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(40.dp)
            .background(
                when {
                    focused -> Cyan.copy(alpha = 0.22f)
                    selected -> Cyan.copy(alpha = 0.14f)
                    else -> Color(0x14FFFFFF)
                },
                shape,
            )
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = if (focused || selected) Cyan else Color(0x22FFFFFF),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            label,
            color = if (focused || selected) Cyan else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
        Text(
            "$count",
            color = if (focused || selected) Cyan else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusPill(hit: PersonHit) {
    val (label, fg, bg) = when {
        !hit.decorated -> Triple(
            "CHECKING…",
            Color(0xFFF59E0B),
            Color(0x22F59E0B),
        )
        hit.libraryEntry != null -> Triple(
            "IN LIBRARY · TAP TO WATCH",
            Color(0xFF34D399),
            Color(0x3322C55E),
        )
        else -> Triple(
            "TAP TO REQUEST",
            Cyan,
            Cyan.copy(alpha = 0.15f),
        )
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PageChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val alpha = if (enabled) 1f else 0.35f
    Box(
        Modifier
            .height(40.dp)
            .background(
                if (enabled && focused) Cyan.copy(alpha = 0.22f)
                else Color(0x14FFFFFF),
                shape,
            )
            .border(
                width = if (enabled && focused) 2.dp else 1.dp,
                color = if (enabled && focused) Cyan
                else Color(0x22FFFFFF),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .clickableWithEnter { if (enabled) onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = (if (enabled && focused) Cyan else TextPrimary)
                .copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(36.dp)
            .background(
                if (focused) Cyan.copy(alpha = 0.18f) else Color(0x14FFFFFF),
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x22FFFFFF),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            "←",
            color = if (focused) Cyan else TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Back",
            color = if (focused) Cyan else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}
