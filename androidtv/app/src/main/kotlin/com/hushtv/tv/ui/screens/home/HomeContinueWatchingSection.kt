package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    onLongPressRemove: (ContinueEntry) -> Unit = {},
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: androidx.compose.ui.focus.FocusRequester? = null,
    onUpFromFirstItem: (() -> Unit)? = null,
) {
    if (entries.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(start = contentStartPadding, end = 48.dp, top = 20.dp, bottom = 20.dp)) {
        Text(
            "Continue Watching",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            itemsIndexed(entries, key = { _, it -> "cw-${it.progress.kind}-${it.progress.streamId}" }) { idx, e ->
                ContinueCard(
                    entry = e,
                    onFocus = { onFocusedEntryChange(e) },
                    onClick = { onCardClick(e) },
                    onLongPress = { onLongPressRemove(e) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                    onUpKey = if (idx == 0) onUpFromFirstItem else null,
                )
            }
        }
    }
}

/**
 * Small handle for Home so it can read the list AND trigger an in-place
 * removal from SharedPreferences + immediate UI refresh without re-mounting
 * the whole screen.
 */
class ContinueEntriesHandle internal constructor(
    val entries: List<ContinueEntry>,
    val remove: (ContinueEntry) -> Unit,
)

/**
 * Composable-friendly data loader. Call this at the top of the home screen;
 * pass `handle.entries` to [HomeContinueWatchingRow] and also use the first
 * entry as the initial hero backdrop. Call `handle.remove(e)` to drop an
 * entry — the list recomputes immediately and the section will hide itself
 * if it becomes empty (parent guards with `.isNotEmpty()`).
 */
@Composable
fun rememberContinueEntries(playlistId: String): ContinueEntriesHandle {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember(playlistId) { mutableStateOf<List<ContinueEntry>>(emptyList()) }
    // Version counter — bumping this forces the LaunchedEffect below to
    // re-read from SharedPreferences. Bumped when:
    //   • a user long-presses to remove an entry
    //   • the Home screen returns from the background / another screen
    //     (ON_RESUME) — so progress saved while watching a movie shows up
    //     without the user needing to re-launch the app.
    var version by remember(playlistId) { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                version++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playlistId, version) {
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

    return ContinueEntriesHandle(
        entries = entries,
        remove = { e ->
            WatchProgressStore.clear(ctx, e.progress.streamId, e.progress.kind)
            version++
        },
    )
}

@Composable
private fun ContinueCard(
    entry: ContinueEntry,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onUpKey: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    val shadowColor = if (focused) Cyan else Color.Black
    val shadowElevation = if (focused) 14.dp else 2.dp

    // Long-press detection on OK / D-pad center. Tivimate-style: hold the
    // select button for ~500 ms to open the "Remove from Continue Watching"
    // prompt.
    //
    // IMPORTANT bug fix: we fire `onLongPress` on KeyUp (not during KeyDown)
    // so the KeyUp event is fully consumed by THIS card's handler before the
    // dialog appears. Firing during KeyDown caused a cascade: dialog opened,
    // Remove button auto-focused, then the user's KeyUp landed on it and
    // fired onClick — removing the item without the user clicking "Remove".
    var keyDownAtMs by remember { mutableStateOf(0L) }

    // focusRequester MUST come BEFORE .focusable() — the requester
    // attaches to the next focusable in the chain, not the previous one.
    val baseTop: Modifier = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier

    val base = baseTop
        .width(240.dp)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus()
        }
        .onPreviewKeyEvent { ev ->
            // D-pad UP from this first card should reveal + focus the
            // top nav bar when [onUpKey] is wired in by the parent.
            if (onUpKey != null && ev.type == KeyEventType.KeyDown &&
                ev.key == Key.DirectionUp
            ) {
                onUpKey.invoke()
                return@onPreviewKeyEvent true
            }
            val isEnterKey = ev.key == Key.Enter ||
                ev.key == Key.DirectionCenter ||
                ev.key == Key.NumPadEnter
            if (!isEnterKey) return@onPreviewKeyEvent false
            when (ev.type) {
                KeyEventType.KeyDown -> {
                    if (keyDownAtMs == 0L) keyDownAtMs = System.currentTimeMillis()
                    // Do NOT return true — letting KeyDown pass lets
                    // clickableWithEnter see it for short-press behavior.
                    false
                }
                KeyEventType.KeyUp -> {
                    val held = System.currentTimeMillis() - keyDownAtMs
                    keyDownAtMs = 0L
                    if (held >= 500L) {
                        // Long press — fire remove prompt + consume the
                        // KeyUp so clickableWithEnter doesn't also fire.
                        onLongPress()
                        true
                    } else false
                }
                else -> false
            }
        }
        .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
        .focusable()
        .clickableWithEnter(onClick)

    Column(modifier = base) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .shadow(
                    elevation = shadowElevation,
                    shape = cardShape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                )
                .clip(cardShape)
                .background(Color(0xFF0C101A)),
        ) {
            // Backdrop image
            entry.backdropUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Bottom-up gradient so the title/meta sits cleanly on any art.
            // Runs top→transparent → bottom→~92% black.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.45f to Color(0x66000000),
                            1.0f to Color(0xEB000000),
                        )
                    )
            )

            // Un-focused cards get a subtle dim so the focused one pops.
            if (!focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x33000000))
                )
            }

            // Time-left chip — glass-morphism style pill top-right.
            Surface(
                color = Color(0xCC0B0F1A),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
            ) {
                Text(
                    formatLeft(entry),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            // Play button pops in on focus — centered, semi-transparent cyan
            // circle. Purely decorative; clicking the whole card still plays.
            if (focused) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC06B6D4)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            // Title + meta overlay on the bottom of the image itself.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                // Meta (Movie / Series · Genre) — small caps style
                val metaParts = buildList {
                    add(if (entry.progress.kind == "series") "SERIES" else "MOVIE")
                    entry.genre?.uppercase()?.let { add(it) }
                }
                Text(
                    metaParts.joinToString(" · "),
                    color = Cyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.progress.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Progress bar at the very bottom. 4 dp tall; glows cyan when
            // focused thanks to a subtle outer shadow.
            val ratio = entry.progress.ratio.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomStart)
                    .background(Color(0x33FFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color(0xFF06B6D4),
                                1.0f to Color(0xFF22D3EE),
                            )
                        ),
                )
            }
        }
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
