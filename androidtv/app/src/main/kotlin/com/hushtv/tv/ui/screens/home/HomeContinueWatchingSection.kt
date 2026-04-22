package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.R
import com.hushtv.tv.data.TmdbMovie
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.WatchProgressStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hydrated Continue Watching entry — raw progress + resolved TMDB metadata.
 * Built on the Home screen so the hero panel can render rich artwork and
 * copy even though the local store only knows (streamId, title, position).
 */
data class ContinueEntry(
    val progress: WatchProgressStore.Entry,
    val tmdb: TmdbMovie?,
) {
    val backdropUrl: String? get() = tmdb?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
    val posterUrl: String? get() = tmdb?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
    val year: String? get() = tmdb?.release_date?.take(4)?.takeIf { it.length == 4 }
    val genre: String? get() = tmdb?.genres?.firstOrNull()?.name
    val ratingText: String? get() = tmdb?.vote_average?.takeIf { it > 0 }?.let { String.format("%.1f", it) }
    val minutesLeft: Int get() {
        val remainingMs = (progress.durationMs - progress.positionMs).coerceAtLeast(0)
        return (remainingMs / 60_000L).toInt()
    }
}

@Composable
fun HomeContinueWatchingSection(
    playlistId: String,
    onCardClick: (ContinueEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load raw entries + hydrate with TMDB in parallel. Recompute on each
    // composition key change (playlistId), and also every time the screen
    // becomes active (users finish a movie → re-open home).
    var entries by remember { mutableStateOf<List<ContinueEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        val raw = WatchProgressStore.continueWatching(ctx).take(12)
        if (raw.isEmpty()) {
            entries = emptyList()
            loaded = true
            return@LaunchedEffect
        }

        // Kick off TMDB hydration in parallel — UI renders progressively.
        val shells = raw.map { ContinueEntry(it, null) }
        entries = shells
        loaded = true

        raw.forEachIndexed { idx, entry ->
            scope.launch {
                val tmdb = withContext(Dispatchers.IO) {
                    val id = TmdbService.searchMovie(entry.title, null)
                    id?.let { TmdbService.getMovie(it) }
                }
                // Replace the shell with the hydrated entry, preserving order.
                entries = entries.toMutableList().also { list ->
                    if (idx < list.size) list[idx] = list[idx].copy(tmdb = tmdb)
                }
            }
        }
    }

    if (!loaded || entries.isEmpty()) return

    var focusedIdx by remember { mutableStateOf(0) }
    val focused = entries.getOrNull(focusedIdx) ?: entries.first()

    Box(
        Modifier
            .fillMaxWidth()
            .height(560.dp),
    ) {
        HeroBackdrop(entry = focused)

        // Left-to-right darkening veil over the backdrop so the text column
        // below stays readable. 60 % opacity at the left edge fading to 0 %
        // at ~55 % across the hero matches the reference image well.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF0000000),
                        0.35f to Color(0xC0000000),
                        0.6f to Color(0x70000000),
                        1.0f to Color(0x00000000),
                    )
                )
        )
        // Bottom fade → helps the card row pop against the artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1.0f to Color(0xFF050507),
                    )
                )
        )

        // Text + row column
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 36.dp, end = 48.dp, bottom = 20.dp),
        ) {
            HeroText(entry = focused)
            Spacer(Modifier.weight(1f))
            Text(
                "Continue Watching",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entries, key = { "cw-${it.progress.kind}-${it.progress.streamId}" }) { e ->
                    val idxOfThis = entries.indexOf(e)
                    ContinueCard(
                        entry = e,
                        onFocus = { focusedIdx = idxOfThis },
                        onClick = { onCardClick(e) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(entry: ContinueEntry) {
    val url = entry.backdropUrl
    if (url == null) {
        // Fallback — solid dark surface while TMDB hydrates, or when there's
        // just no backdrop available for this title.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F1A))
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HeroText(entry: ContinueEntry) {
    Column(Modifier.fillMaxWidth(0.55f)) {
        Text(
            entry.progress.title,
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 48.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        // Meta row: S1E1 · Genre · Year
        val metaParts = buildList {
            if (entry.progress.kind == "series") add("Series")
            else add("Movie")
            entry.genre?.let { add(it) }
            entry.year?.let { add(it) }
        }
        Text(
            metaParts.joinToString("  ·  "),
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Inter,
        )
        Spacer(Modifier.height(12.dp))

        // Time left + IMDb rating
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${entry.minutesLeft}M LEFT",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter,
                letterSpacing = 1.sp,
            )
            entry.ratingText?.let { rating ->
                Spacer(Modifier.width(14.dp))
                Surface(
                    color = Color(0xFFF5C518),
                    shape = RoundedCornerShape(3.dp),
                ) {
                    Text(
                        "IMDb",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    rating,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                )
            }
        }

        // Description
        val overview = entry.tmdb?.overview
        if (!overview.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "\"$overview\"",
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = Inter,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContinueCard(
    entry: ContinueEntry,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(10.dp)

    Column(
        Modifier
            .width(240.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvFocusable(shape = cardShape)
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(135.dp)
                .clip(cardShape)
                .background(Color(0xFF141922)),
        ) {
            entry.backdropUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Dim un-focused cards slightly so the focused one pops.
            if (!focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                )
            }

            // Time-left chip (top-right)
            Surface(
                color = Color(0xE0000000),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Text(
                    formatLeft(entry),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            // Progress bar at the bottom of the card
            val ratio = entry.progress.ratio.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomStart)
                    .background(Color(0x40FFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .background(Cyan),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.progress.title,
            color = if (focused) Color.White else Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatLeft(entry: ContinueEntry): String {
    val totalMin = entry.minutesLeft
    return if (totalMin >= 60) {
        val h = totalMin / 60
        val m = totalMin % 60
        "${h}h ${m}m left"
    } else {
        "${totalMin}m left"
    }
}
