package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Person filmography page — opened when a user taps a cast member
 * card on a movie or series detail screen.
 *
 * Behaviour mirrors the request search results pattern:
 *   • Stage 1 — fetch TMDB credits, render every card immediately.
 *   • Stage 2 — cross-reference each title against the user's
 *     Xtream library row-by-row; cards flip from "CHECKING YOUR
 *     LIBRARY…" to one of:
 *         IN LIBRARY · TAP TO WATCH       (deep-link to library)
 *         TAP TO REQUEST                  (open request modal)
 *   • Tap on an undecorated card is a no-op (same safety guard
 *     used in TmdbPickerPhase) so users never accidentally request
 *     a title they already have.
 *
 * Tabs split the credits into Movies and TV. The first non-empty
 * tab is the default; an empty selected tab shows a friendly
 * "no credits in this category" message.
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

    val perPage = 10  // 5 columns × 2 rows on TV
    var loading by remember { mutableStateOf(true) }
    var libraryReady by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<PersonHit>>(emptyList()) }
    var tab by remember { mutableStateOf("movie") }
    var page by remember(tab) { mutableStateOf(0) }
    var requestPreset by remember {
        mutableStateOf<Pair<String, String>?>(null)  // type, title
    }

    // Library prime — same pattern as TmdbPickerPhase.
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

    // Stage 1 — fetch all credits up front. We sort newest-first by
    // year so users see the latest work in the actor's career on
    // page 1; popularity-based sort buried recent releases.
    LaunchedEffect(personId) {
        loading = true
        val raw = withContext(Dispatchers.IO) {
            runCatching { TmdbService.personFilmography(personId) }
                .getOrDefault(emptyList())
        }
        val sorted = raw.sortedByDescending { parseCreditYear(it) ?: 0 }
        hits = sorted.map { PersonHit(it, libraryEntry = null, decorated = false) }
        // Default to the type with the most credits (avoids opening
        // an empty Movies tab for a TV-only actor like the cast of a
        // soap opera).
        val movieCount = sorted.count { it.media_type == "movie" }
        val tvCount = sorted.count { it.media_type == "tv" }
        tab = if (tvCount > movieCount) "tv" else "movie"
        loading = false
    }

    // Stage 2 — decorate ONLY the 10 cards on the visible page. With
    // pagination we don't need to scan 73+ titles up front; we
    // amortise the cost across pages and the user gets a fully
    // verified, ready-to-tap page in well under a second.
    LaunchedEffect(page, tab, libraryReady, hits.size) {
        if (!libraryReady || hits.isEmpty()) return@LaunchedEffect
        val visible = hits
            .withIndex()
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

    Column(
        Modifier.fillMaxSize().background(BgBlack).padding(
            horizontal = 56.dp, vertical = 32.dp,
        ),
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChip(onClick = { nav.popBackStack() })
            Spacer(Modifier.width(20.dp))
            Column {
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
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Tabs ──
        val movieCount = hits.count { it.credit.media_type == "movie" }
        val tvCount = hits.count { it.credit.media_type == "tv" }
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

        Spacer(Modifier.height(18.dp))

        // ── Body ──
        when {
            loading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(top = 36.dp),
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
                    )
                } else {
                    val totalPages = (current.size + perPage - 1) / perPage
                    if (page >= totalPages) page = 0
                    val pageItems = current.drop(page * perPage).take(perPage)
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            PersonCreditsGrid(
                                hits = pageItems,
                                onClick = { ph ->
                                    if (!ph.decorated) return@PersonCreditsGrid
                                    val title = ph.credit.title
                                        ?: ph.credit.name ?: return@PersonCreditsGrid
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
                        if (totalPages > 1) {
                            Spacer(Modifier.height(14.dp))
                            PaginationBar(
                                page = page,
                                totalPages = totalPages,
                                onPrev = { if (page > 0) page -= 1 },
                                onNext = { if (page < totalPages - 1) page += 1 },
                            )
                        }
                    }
                }
            }
        }
    }

    // Request modal — opens pre-filled when the user taps a missing
    // credit. Matches the existing flow used everywhere else.
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
private fun PersonCreditsGrid(
    hits: List<PersonHit>,
    onClick: (PersonHit) -> Unit,
) {
    // Fixed dimensions tuned to fit a 5-column × 2-row grid in the
    // available filmography viewport on a 1080p screen:
    //   • card width 196 dp → poster 2:3 = 196 × 294 dp
    //   • text rows below poster total ~73 dp (title/meta/pill)
    //   • full card height ≈ 367 dp; two rows + 18 dp gap = 752 dp
    //
    // Earlier attempts used Modifier.weight(1f) which stretched
    // each card to ~350 dp wide, which then pushed the 2:3 poster
    // to 524 dp tall, overflowing the second row INTO the first.
    // Anchoring width explicitly makes the grid bulletproof.
    val cardWidth = 196.dp
    val rows = hits.chunked(5)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { ph ->
                    PersonCreditCard(
                        hit = ph,
                        modifier = Modifier.width(cardWidth),
                        onClick = { onClick(ph) },
                    )
                }
                // Pad the last row with empty same-width slots so a
                // page with 7 hits (e.g. last page) keeps the same
                // SpaceEvenly rhythm as a full row.
                repeat(5 - row.size) {
                    Spacer(Modifier.width(cardWidth))
                }
            }
        }
    }
}

@Composable
private fun PaginationBar(
    page: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
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
private fun PersonCreditCard(
    hit: PersonHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        label = "person-card-scale",
    )
    val shape = RoundedCornerShape(12.dp)
    val title = hit.credit.title ?: hit.credit.name ?: "—"
    val year = parseCreditYear(hit.credit)
    val accent = when {
        !hit.decorated -> Color(0xFFF59E0B)
        hit.libraryEntry != null -> Color(0xFF34D399)
        else -> Cyan
    }

    Column(
        modifier
            .scale(scale)
            .focusRequester(remember { FocusRequester() })
            .onFocusChanged { focused = it.isFocused }
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
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) accent else Color(0x22FFFFFF),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val poster = TmdbService.img(hit.credit.poster_path, "w342")
            if (!poster.isNullOrBlank()) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    title.take(1).uppercase(),
                    color = TextSecondary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Fixed-height title row — single line clipped with ellipsis
        // so every card under the row aligns identically. Multi-line
        // titles like "The School for Good and Evil" no longer push
        // the meta + status pill out of alignment with neighbouring
        // cards.
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
        // Fixed-height meta row — also single-line + ellipsis so
        // long character names like "as Lorraine Broughton" don't
        // break the grid.
        Box(
            Modifier
                .fillMaxWidth()
                .height(15.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
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
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Fixed-height status pill — keeps every card the same
        // overall height regardless of title/meta content.
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            StatusPill(hit = hit)
        }
    }
}

@Composable
private fun StatusPill(hit: PersonHit) {
    val (label, fg, bg) = when {
        !hit.decorated -> Triple(
            "CHECKING YOUR LIBRARY…",
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
