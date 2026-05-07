@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.TmdbMovie
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.TmdbTv
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
 *
 * Carries TMDB metadata for BOTH content kinds. The accessors below
 * resolve to the right field set based on which one is non-null —
 * for an entry of `kind = "series"`, only [tv] is populated; for
 * `kind = "movie"`, only [tmdb] is. This keeps a single uniform
 * type for the home row to render without splitting it into a
 * sealed hierarchy.
 */
data class ContinueEntry(
    val progress: WatchProgressStore.Entry,
    val tmdb: TmdbMovie? = null,
    val tv: TmdbTv? = null,
    /**
     * Effective content kind — may differ from [progress.kind]
     * for legacy entries that were incorrectly saved as "movie"
     * before v1.43.31. Recomputed at hydration time by
     * cross-referencing the streamId against the movie library
     * and/or detecting an episode marker (SxxExx) in the title.
     */
    val effectiveKind: String = progress.kind,
    /**
     * Effective display title — strips trailing episode markers
     * (" S01E05", " - S01 E05", " 1x05", etc.) from legacy
     * series entries so the card shows the parent show's name.
     */
    val effectiveTitle: String = progress.title,
) {
    val backdropUrl: String? get() {
        // Stored poster from the player save site wins — it's the
        // canonical artwork the user already saw on the detail
        // screen and matches what they'd expect for "the show I
        // was watching". TMDB hydration is a fallback for older
        // CW entries saved before posters were tracked.
        progress.poster?.takeIf { it.isNotBlank() }?.let { return it }
        tmdb?.backdrop_path?.let { return "https://image.tmdb.org/t/p/original$it" }
        tv?.backdrop_path?.let { return "https://image.tmdb.org/t/p/original$it" }
        return null
    }
    val posterUrl: String? get() {
        progress.poster?.takeIf { it.isNotBlank() }?.let { return it }
        tmdb?.poster_path?.let { return "https://image.tmdb.org/t/p/w500$it" }
        tv?.poster_path?.let { return "https://image.tmdb.org/t/p/w500$it" }
        return null
    }
    val year: String? get() =
        tmdb?.release_date?.take(4)?.takeIf { it.length == 4 }
            ?: tv?.first_air_date?.take(4)?.takeIf { it.length == 4 }
    val genre: String? get() =
        tmdb?.genres?.firstOrNull()?.name
            ?: tv?.genres?.firstOrNull()?.name
    val ratingText: String? get() =
        (tmdb?.vote_average ?: tv?.vote_average)
            ?.takeIf { it > 0 }
            ?.let { String.format("%.1f", it) }
    val overview: String? get() =
        tmdb?.overview?.takeIf { it.isNotBlank() }
            ?: tv?.overview?.takeIf { it.isNotBlank() }
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
    onClearAll: (() -> Unit)? = null,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: androidx.compose.ui.focus.FocusRequester? = null,
    onUpFromFirstItem: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
) {
    if (entries.isEmpty()) return

    // NOTE: we deliberately do NOT use Modifier.focusRestorer() here.
    // Continue Watching is the only row on the home page whose items
    // get removed at runtime (long-press → Remove). The focusRequester
    // is bound directly to the first ContinueCard (not to this outer
    // Column) so the sidebar's RIGHT-exit callback's requestFocus()
    // lands on a real focusable card with a visible cyan ring.

    Column(
        Modifier
            .focusGroup()
            .fillMaxWidth()
            .padding(start = contentStartPadding, end = 48.dp, top = 20.dp, bottom = 20.dp)
            // Row-level D-pad Down → fires onDownFromRow (used by Home to
            // slide to the Discovery page). Attached on the Column so it
            // catches Down from ANY card in the row, not just the last.
            .onPreviewKeyEvent { ev ->
                if (onDownFromRow != null &&
                    ev.type == KeyEventType.KeyDown &&
                    ev.key == Key.DirectionDown
                ) {
                    onDownFromRow.invoke()
                    true
                } else false
            },
    ) {
        // Section header — matches the "DISCOVER" style used elsewhere on
        // the home page: small cyan accent bar + uppercase, heavy-tracking
        // label so Continue Watching reads as a proper section, not a card.
        //
        // start = 12.dp mirrors the LazyRow's horizontal contentPadding
        // below so the accent bar lines up with the first card's left
        // edge (otherwise the label sits 12dp further left than the row
        // of cards — the exact bug the user flagged in v1.43.33).
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(Cyan, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "CONTINUE WATCHING",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(14.dp))
        // Plain Row + horizontalScroll — see HomeYearsRow comment
        // for the full reasoning. Inline composition is required
        // for the outer-Column focusRequester to land on the first
        // CW card from the sidebar's RIGHT-exit callback.
        val scrollState = androidx.compose.foundation.rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            entries.forEachIndexed { idx, e ->
                ContinueCard(
                    entry = e,
                    onFocus = { onFocusedEntryChange(e) },
                    onClick = { onCardClick(e) },
                    onLongPress = { onLongPressRemove(e) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                    onUpKey = if (idx == 0) onUpFromFirstItem else null,
                )
            }
            if (onClearAll != null) {
                ClearAllCard(onClick = onClearAll)
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

/**
 * Matches trailing episode markers in a saved title so we can
 * recover the parent series title from a legacy entry.
 *
 * Covers the common Xtream / TMDB title formats in the wild:
 *   "Gold Rush S15E03"
 *   "Gold Rush - S15 E03 - Title"
 *   "Gold Rush 15x03"
 *   "Gold Rush Season 15 Episode 3"
 *
 * Matches at the LAST occurrence so a movie literally named
 * "7x7" doesn't get chopped.
 */
private val EPISODE_MARKER_REGEX = Regex(
    """\s*[-–—:]?\s*(?:S\d{1,2}\s*E\d{1,3}|\d{1,2}x\d{1,3}|Season\s+\d{1,2}\s*(?:Episode\s*\d{1,3})?).*$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Strips episode/season markers from a saved CW title. Leaves the
 * title unchanged when no marker is present (movies or already-
 * cleaned series entries from v1.43.31+).
 */
private fun stripEpisodeMarker(title: String): String =
    EPISODE_MARKER_REGEX.replace(title.trim(), "").trim().trim('-', '–', '—', ':').trim()

/**
 * Reclassifies a raw [WatchProgressStore.Entry] by consulting the
 * LibraryIndex as the source of truth. Fixes legacy entries that
 * were saved with `kind="movie"` for everything (the pre-v1.43.31
 * bug) by:
 *
 *   1. If the LibraryIndex isn't primed yet, we trust the stored
 *      kind and DO NOT relabel. (Previously this branch would flip
 *      every movie to "series" — a destructive rewrite that hid
 *      legitimate movie entries from Continue Watching forever.)
 *   2. If the streamId is in the movie library → keep as movie.
 *   3. If the streamId is in the series library (as a flat episode
 *      stream lookup) → relabel to series.
 *   4. Otherwise — provider may have removed the title from the
 *      playlist. Trust the stored kind; do NOT relabel.
 *
 * Genuinely new entries saved after v1.43.31 already carry the
 * correct kind + parent title from [PlaybackMeta], so the
 * reclassification is a no-op for them.
 */
private fun reclassify(p: WatchProgressStore.Entry): ContinueEntry {
    // If the stored kind is already "series", trust it — only new
    // entries from v1.43.31+ take this branch, and they already
    // have the right parent-series title baked in.
    if (p.kind == "series") {
        return ContinueEntry(
            progress = p,
            effectiveKind = "series",
            effectiveTitle = stripEpisodeMarker(p.title),
        )
    }

    // kind == "movie" — might be a legitimate movie OR a legacy
    // misclassified series episode. Only the LibraryIndex can tell
    // us, and only if it's primed. If the index is empty (boot
    // refresh in flight, profile freshly switched, network hiccup
    // during prime) we MUST trust the stored kind — relabeling
    // every movie as "series" used to permanently hide them from
    // Continue Watching, which is the v1.43.59 user-reported bug.
    if (!LibraryIndex.isPrimed()) {
        return ContinueEntry(progress = p, effectiveKind = "movie", effectiveTitle = p.title)
    }

    val isInMovieLib = LibraryIndex.allEntries()
        .any { it.kind == "movie" && it.streamId == p.streamId }
    if (isInMovieLib) {
        return ContinueEntry(progress = p, effectiveKind = "movie", effectiveTitle = p.title)
    }

    // Not in the movie library. Could be a legacy misclassified
    // series episode OR a movie the provider has since removed.
    // Only relabel as series if the title shows clear episode
    // markers (S##E##, NxN, "Season N Episode M"). Otherwise trust
    // the stored kind so deleted-from-playlist movies still resume.
    val hasEpisodeMarker = EPISODE_MARKER_REGEX.containsMatchIn(p.title)
    return if (hasEpisodeMarker) {
        ContinueEntry(
            progress = p,
            effectiveKind = "series",
            effectiveTitle = stripEpisodeMarker(p.title),
        )
    } else {
        ContinueEntry(progress = p, effectiveKind = "movie", effectiveTitle = p.title)
    }
}


@Composable
fun rememberContinueEntries(playlistId: String): ContinueEntriesHandle {
    val ctx = LocalContext.current
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

    // TMDB hydration runs INSIDE this LaunchedEffect (not rememberCoroutineScope)
    // so when [version] bumps (e.g. user removed an entry) every stale
    // hydration coroutine from the previous cycle is CANCELLED automatically.
    // Previously they'd keep running and clobber `entries` by positional
    // index — which, after a removal, meant writing tmdb metadata to the
    // wrong slot AND racing with the fresh cycle's writes, leaving the
    // UI in a half-assigned state that crashed on next focus / navigation.
    LaunchedEffect(playlistId, version) {
        runCatching {
            val raw = WatchProgressStore.continueWatching(ctx).take(12)
            if (raw.isEmpty()) {
                entries = emptyList()
                return@runCatching
            }

            // Reclassify each raw entry. Legacy entries saved before
            // v1.43.31 have kind="movie" for EVERYTHING, so we can't
            // trust the stored kind alone. Use the library as the
            // authority: if the streamId is in the movie catalog
            // it's a movie, otherwise it's a series episode whose
            // title needs stripping.
            val reclassified = raw.map { p -> reclassify(p) }

            // Write-through migration: for any entry whose stored
            // kind was "movie" but reclassify() concluded it's a
            // "series", permanently heal the data in
            // SharedPreferences. Next boot the entry is already
            // correctly classified — no re-reclassification work.
            reclassified.forEach { e ->
                if (e.progress.kind == "movie" && e.effectiveKind == "series") {
                    runCatching {
                        WatchProgressStore.relabelMovieToSeries(
                            ctx = ctx,
                            streamId = e.progress.streamId,
                            correctedTitle = e.effectiveTitle,
                        )
                    }
                }
            }
            // Render shells instantly — with the corrected kind and
            // title already applied so the card never flashes the
            // wrong label.
            entries = reclassified

            // Hydrate each entry.
            reclassified.forEach { shell ->
                launch {
                    if (shell.effectiveKind == "series") {
                        val tv = runCatching {
                            withContext(Dispatchers.IO) {
                                val id = TmdbService.searchTv(shell.effectiveTitle)
                                id?.let { TmdbService.getTv(it) }
                            }
                        }.getOrNull()
                        entries = entries.map { e ->
                            if (e.progress.streamId == shell.progress.streamId &&
                                e.progress.kind == shell.progress.kind
                            ) e.copy(tv = tv) else e
                        }
                    } else {
                        val tmdb = runCatching {
                            withContext(Dispatchers.IO) {
                                val id = TmdbService.searchMovie(shell.effectiveTitle, null)
                                id?.let { TmdbService.getMovie(it) }
                            }
                        }.getOrNull()
                        entries = entries.map { e ->
                            if (e.progress.streamId == shell.progress.streamId &&
                                e.progress.kind == shell.progress.kind
                            ) e.copy(tmdb = tmdb) else e
                        }
                    }
                }
            }
        }
    }

    return ContinueEntriesHandle(
        entries = entries,
        remove = { e ->
            runCatching {
                WatchProgressStore.clear(ctx, e.progress.streamId, e.progress.kind)
                // Immediately drop the entry from our local state so the
                // LazyRow re-measures NOW — before SharedPreferences /
                // LaunchedEffect / TMDB hydration can clash with focus
                // restoration. The version++ that follows will catch up
                // but this synchronous drop kills the crash window.
                entries = entries.filterNot {
                    it.progress.streamId == e.progress.streamId &&
                        it.progress.kind == e.progress.kind
                }
                version++
            }
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

    // Themes-pattern modifier chain — see HomeYearsRow for full
    // explanation. focusRequester is now applied DIRECTLY without
    // tvFocusable in the way, and bound right before .focusable()
    // so requestFocus lands on a real focusable card with cyan ring.
    val base = Modifier
        .width(240.dp)
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) {
                if (focusRequester != null) {
                    com.hushtv.tv.util.HushTVNav.d(
                        "✓ CW first card '${entry.effectiveTitle}' GAINED FOCUS (cyan ring on)"
                    )
                }
                onFocus()
            }
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
                    add(if (entry.effectiveKind == "series") "SERIES" else "MOVIE")
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
                    entry.effectiveTitle,
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
                                // Continue-watching progress fill —
                                // brand-blue gradient. Goes from the
                                // darker DodgerBlue → lighter sky blue
                                // so the bar still has the "edge
                                // highlight" depth feel. Both ends are
                                // within the new brand blue family
                                // (#1E90FF → #59BFF2) so the gradient
                                // stays hue-coherent.
                                0.0f to Cyan,
                                1.0f to Color(0xFF59BFF2),
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

/**
 * Trailing "Clear All" focusable tile rendered after the last
 * Continue Watching card. A confirmation dialog is wired up by the
 * parent — this composable just fires [onClick] when activated.
 *
 * Same width / height as a [ContinueCard] so the row keeps a clean
 * uniform rhythm. Uses a darker glass-morphism surface with a subtle
 * cyan border so it visually reads as a utility action, not content.
 */
@Composable
private fun ClearAllCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    val borderColor = if (focused) Cyan else Color(0x33FFFFFF)
    Column(
        modifier = Modifier
            .width(240.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .shadow(
                    elevation = if (focused) 14.dp else 2.dp,
                    shape = cardShape,
                    ambientColor = if (focused) Cyan else Color.Black,
                    spotColor = if (focused) Cyan else Color.Black,
                )
                .clip(cardShape)
                .background(Color(0xFF0C101A)),
            contentAlignment = Alignment.Center,
        ) {
            // Subtle border so the tile reads as a "secondary action".
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x14FFFFFF))
            )
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = borderColor,
                        shape = cardShape,
                    ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (focused) Color(0xCC06B6D4) else Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = if (focused) Color.Black else Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "CLEAR ALL",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Remove from list",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = Inter,
                )
            }
        }
    }
}
