package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

/**
 * Loads + hydrates the Continue Watching list and fires [onEntriesLoaded]
 * with the results (shell first, then re-fires as TMDB metadata lands for
 * each title). Also takes a callback for the focused entry so the parent
 * can render a sticky hero backdrop that reacts to D-pad focus.
 *
 * Renders JUST the card row — no hero. The hero is a separate fixed layer
 * behind the scrollable content (see [HomeHeroLayer]).
 */
@Composable
fun HomeContinueWatchingRow(
    playlistId: String,
    entries: List<ContinueEntry>,
    onFocusedEntryChange: (ContinueEntry) -> Unit,
    onCardClick: (ContinueEntry) -> Unit,
) {
    if (entries.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 20.dp)) {
        Text(
            "Continue Watching",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { "cw-${it.progress.kind}-${it.progress.streamId}" }) { e ->
                ContinueCard(
                    entry = e,
                    onFocus = { onFocusedEntryChange(e) },
                    onClick = { onCardClick(e) },
                )
            }
        }
    }
}

/**
 * Composable-friendly data loader. Call this at the top of the home screen;
 * pass the returned list to [HomeContinueWatchingRow] and also use the first
 * entry as the initial hero backdrop.
 */
@Composable
fun rememberContinueEntries(playlistId: String): List<ContinueEntry> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember(playlistId) { mutableStateOf<List<ContinueEntry>>(emptyList()) }

    LaunchedEffect(playlistId) {
        val raw = WatchProgressStore.continueWatching(ctx).take(12)
        if (raw.isEmpty()) {
            entries = emptyList()
            return@LaunchedEffect
        }
        // Render shells instantly, then hydrate TMDB per-entry in parallel.
        entries = raw.map { ContinueEntry(it, null) }
        raw.forEachIndexed { idx, entry ->
            scope.launch {
                val tmdb = withContext(Dispatchers.IO) {
                    val id = TmdbService.searchMovie(entry.title, null)
                    id?.let { TmdbService.getMovie(it) }
                }
                entries = entries.toMutableList().also { list ->
                    if (idx < list.size) list[idx] = list[idx].copy(tmdb = tmdb)
                }
            }
        }
    }
    return entries
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
            if (!focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                )
            }

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
