package com.hushtv.tv.ui.requests

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RequestCache
import com.hushtv.tv.data.RequestNotificationStore
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.TitleMatcher
import com.hushtv.tv.data.UserContactStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resolved deep-link target produced by the title resolver. Either a
 * direct hit on a movie or series in the user's Xtream library, or a
 * fallback if no match was found.
 */
sealed class WatchTarget {
    data class Movie(val streamId: Int, val title: String) : WatchTarget()
    data class Series(val seriesId: Int, val title: String, val poster: String?) : WatchTarget()
    /** No match in the user's library — fall back to the My Requests page. */
    data object NotFound : WatchTarget()
}

/**
 * Composition-local exposing the [FocusRequester] of the currently-
 * visible request banner's "Watch now" button. `null` means the
 * banner is not on screen.
 *
 * Why this exists: the banner is hosted as a sibling of the main
 * screen graph in `MainActivity` (so it can overlay any route),
 * which means the home screen and the banner live in DIFFERENT
 * focus subtrees. Compose's 2D spatial-focus search cannot cross
 * those subtrees, so a UP key from the home page's top nav will
 * never naturally land on the banner — the user has to tab all
 * the way RIGHT through every card to reach it.
 *
 * Solution: expose the requester via this local so any screen can
 * opt in to "UP at the topmost row → focus the banner" navigation.
 * Read it with [LocalRequestNotificationFocus.current]; if non-null
 * AND the user pressed UP from your topmost focusable, call
 * `requester.requestFocus()` and consume the key event.
 *
 * The banner publishes itself to this local from inside its
 * `AnimatedVisibility` block, so it is automatically `null` while
 * the slide-out animation is finishing. Defaults to `null` when
 * no banner host is mounted.
 */
val LocalRequestNotificationFocus =
    androidx.compose.runtime.compositionLocalOf<FocusRequester?> { null }

/**
 * Process-global state holder for the banner's focus requester.
 *
 * Why a top-level object instead of a CompositionLocalProvider tree
 * scope: the banner host is mounted as a sibling of the main app
 * graph in `MainActivity`, so wrapping the graph in a Provider would
 * require restructuring `MainActivity`. The mutableStateOf instead
 * lets the banner publish itself once and any screen read it
 * without coupling. Reads are Compose-observable so screens
 * recompose when the banner appears/disappears.
 *
 * The banner sets this to its `watchFocus` requester when visible
 * AND `canWatchNow` (so we don't direct focus to a banner that has
 * no actionable button, e.g. PENDING/NOT_FOUND statuses), and
 * clears it on dismiss / animate-out.
 */
internal object RequestBannerFocusBus {
    var current: FocusRequester? by androidx.compose.runtime.mutableStateOf(null)
}

/** Public read-only accessor — non-null exactly when a focusable
 *  request banner is on-screen. */
@Composable
fun rememberRequestBannerFocus(): FocusRequester? =
    RequestBannerFocusBus.current

/**
 * Background poller + slide-down banner that surfaces "Your request
 * is now available!" notifications for content the admin has marked
 * `added` or `already_available` since the last cold start.
 *
 * Behaviour:
 *  • Polls the gateway at most once per [RequestNotificationStore.POLL_INTERVAL_MS].
 *  • Skips the poll entirely when the user has never submitted a
 *    request (no contact info → no email to query with).
 *  • Banner only shows requests the user has NOT already acknowledged
 *    (tracked via [RequestNotificationStore.markSeen]).
 *  • Marks every newly-fulfilled request as seen the moment we render
 *    the banner — so even if the user dismisses without tapping, we
 *    won't show the same one again on the next launch.
 *
 * The banner has two actions:
 *  • "Watch now" — resolves the title against the Xtream library and
 *    invokes [onWatchNow] with the resolved [WatchTarget].
 *  • Close (X) — silent dismiss.
 *
 * Caller is responsible for the actual nav (it has the NavController).
 */
@Composable
fun RequestNotificationHost(
    playlistId: String?,
    onWatchNow: (WatchTarget) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingNotices by remember { mutableStateOf<List<ContentRequestApi.Request>>(emptyList()) }
    var visibleIdx by remember { mutableStateOf(0) }
    var isVisible by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }

    // ── Poll once on mount, throttled by lastPollMs ──────────────────
    LaunchedEffect(playlistId) {
        if (playlistId == null) return@LaunchedEffect
        if (UserContactStore.get(ctx) == null) return@LaunchedEffect
        if (!RequestNotificationStore.shouldPoll(ctx)) return@LaunchedEffect

        // Wait a beat so the splash + initial home render don't fight
        // for the network at the same instant.
        delay(2_500)

        runCatching {
            val res = withContext(Dispatchers.IO) {
                ContentRequestApi.listRequests(ctx, limit = 30)
            }
            RequestNotificationStore.setLastPollMs(ctx, System.currentTimeMillis())

            val list = (res as? ContentRequestApi.ListResult.Success)?.requests
                ?: return@runCatching

            // Drop any requests the user explicitly hid via long-press.
            val visible = com.hushtv.tv.data.RequestHiddenStore
                .filterVisible(ctx, list)

            // Populate the cache so the home rail / list / detail
            // screens can render without a fresh network call.
            RequestCache.put(visible)

            // Surface the banner for any request whose
            // (status, adminResponse) signature is *new* compared to
            // the last one the user acknowledged. This catches:
            //   • status flips (added / not_found / already_available)
            //   • admin notes added or edited mid-pipeline
            //   • in-progress updates with a fresh admin message
            // PENDING-only flips (e.g. priority change with no note)
            // are intentionally ignored — too noisy.
            val noticeable = RequestSeenStore.filterUnseen(ctx, visible).filter {
                it.status != ContentRequestApi.Status.PENDING ||
                    !it.adminResponse.isNullOrBlank()
            }
            if (noticeable.isNotEmpty()) {
                pendingNotices = noticeable
                visibleIdx = 0
                isVisible = true
                // Mark them seen immediately — the user will see the
                // banner *now*, no need to re-show on the next launch
                // even if they dismiss without tapping.
                RequestSeenStore.markSeen(ctx, noticeable)
            }
        }
    }

    val current = pendingNotices.getOrNull(visibleIdx)

    // ── Banner ───────────────────────────────────────────────────────
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = isVisible && current != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            val req = current ?: return@AnimatedVisibility
            val watchFocus = remember { FocusRequester() }
            LaunchedEffect(req.id) {
                delay(180)
                runCatching { watchFocus.requestFocus() }
            }
            // Tone the banner per status — added is green, in-progress
            // is cyan, not-found is amber. Keeps the same shape so
            // one user can never mistake one for the other.
            val (headerLabel, headerColor, headerIconTint) = when (req.status) {
                ContentRequestApi.Status.ADDED ->
                    Triple("Your request is in!", Color(0xFF34D399), Color(0xFF22C55E))
                ContentRequestApi.Status.ALREADY_AVAILABLE ->
                    Triple("Already in your library", Color(0xFFC084FC), Color(0xFFA855F7))
                ContentRequestApi.Status.IN_PROGRESS ->
                    Triple("We're on it", Color(0xFF60A5FA), Color(0xFF3B82F6))
                ContentRequestApi.Status.NOT_FOUND ->
                    Triple("Update on your request", Color(0xFFF87171), Color(0xFFEF4444))
                ContentRequestApi.Status.PENDING ->
                    Triple("Update on your request", Color(0xFFFCD34D), Color(0xFFF59E0B))
            }
            val canWatchNow = req.status == ContentRequestApi.Status.ADDED ||
                req.status == ContentRequestApi.Status.ALREADY_AVAILABLE

            // Publish the watch-now focus requester to the global bus
            // ONLY while a Watch-Now button is actually rendered. Other
            // screens read this via [rememberRequestBannerFocus] and use
            // it to route UP key events from their topmost focusable to
            // the banner. Cleared when the banner exits the composition
            // (status changes / dismiss / lifecycle).
            androidx.compose.runtime.DisposableEffect(req.id, canWatchNow) {
                if (canWatchNow) {
                    RequestBannerFocusBus.current = watchFocus
                }
                onDispose { RequestBannerFocusBus.current = null }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0B1220))
                        .border(1.5.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = headerIconTint,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            headerLabel,
                            color = headerColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            "${if (req.type == "series") "📺" else "🎬"} ${req.title}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!req.adminResponse.isNullOrBlank()) {
                            Spacer(Modifier.size(2.dp))
                            Text(
                                req.adminResponse,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (canWatchNow) {
                        WatchNowButton(
                            focusRequester = watchFocus,
                            loading = resolving,
                            onClick = onClick@{
                                if (resolving) return@onClick
                                resolving = true
                                scope.launch {
                                    val target = withContext(Dispatchers.IO) {
                                        resolveWatchTarget(ctx, playlistId, req)
                                    }
                                    resolving = false
                                    isVisible = false
                                    onWatchNow(target)
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    DismissButton(
                        onClick = {
                            // Move to the next pending notice if any,
                            // otherwise hide entirely.
                            if (visibleIdx + 1 < pendingNotices.size) {
                                visibleIdx += 1
                            } else {
                                isVisible = false
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Resolves a request title into a deep-link target by walking the
 * user's Xtream movie or series catalog. Uses [TitleMatcher.normalize]
 * to do a tolerant title compare (handles punctuation / case / "the"
 * prefix variations).
 *
 * Returns [WatchTarget.NotFound] when:
 *   • the user has no playlist (shouldn't happen in practice — the
 *     banner only renders inside an authenticated shell),
 *   • the Xtream call throws,
 *   • or no title in the right kind matches the request.
 */
private suspend fun resolveWatchTarget(
    ctx: Context,
    playlistId: String?,
    req: ContentRequestApi.Request,
): WatchTarget {
    val pid = playlistId ?: return WatchTarget.NotFound
    val playlist = PlaylistStore.find(ctx, pid) ?: return WatchTarget.NotFound

    val tmdbMeta = com.hushtv.tv.data.RequestMetaStore.get(ctx, req.id)
        ?: com.hushtv.tv.data.RequestMetaStore.parseTag(req.additionalInfo)
    val targetKind = if (req.type == "series") "series" else "movie"

    if (com.hushtv.tv.data.LibraryIndex.prime(ctx, playlist)) {
        val entry = pickBestLibraryMatch(playlist, req.title, targetKind, tmdbMeta)
        if (entry != null) {
            return when (entry.kind) {
                "series" -> WatchTarget.Series(entry.seriesId, entry.title, entry.poster)
                else -> WatchTarget.Movie(entry.streamId, entry.title)
            }
        }
    }

    // Fallback path — same logic as before but without year. Only
    // hit when the LibraryIndex prime failed (e.g. transient Xtream
    // outage). Keeps Watch-now working in degraded conditions.
    val pool: List<MediaCard> = runCatching {
        XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, targetKind)
    }.getOrNull() ?: return WatchTarget.NotFound

    val needle = TitleMatcher.normalize(req.title)
    if (needle.isBlank()) return WatchTarget.NotFound

    val exact = pool.firstOrNull { TitleMatcher.normalize(it.title) == needle }
    val contains = exact ?: pool.firstOrNull {
        val n = TitleMatcher.normalize(it.title)
        n.isNotBlank() && (n.contains(needle) || needle.contains(n))
    }
    val match = contains ?: pool.firstOrNull {
        val words = TitleMatcher.normalize(it.title).split(" ").filter { w -> w.isNotBlank() }
        val needleWords = needle.split(" ").filter { w -> w.isNotBlank() }
        needleWords.isNotEmpty() && needleWords.all { qw ->
            words.any { it.startsWith(qw) }
        }
    } ?: return WatchTarget.NotFound

    return when (req.type) {
        "series" -> WatchTarget.Series(
            seriesId = match.seriesId,
            title = match.title,
            poster = match.poster,
        )
        else -> WatchTarget.Movie(
            streamId = match.streamId,
            title = match.title,
        )
    }
}

/**
 * Best-of-three matcher used by every Watch-now resolver. Walks
 * down the precision ladder:
 *   1. **TMDB id exact** — fetch `get_vod_info` / `get_series_info`
 *      for each candidate and pick the entry whose `tmdb_id`
 *      matches the request's saved TMDB id. Bullet-proof when the
 *      provider exposes tmdb_id (most premium Xtream lines do).
 *   2. **Year-aware title match** — when no candidate exposes a
 *      tmdb_id, prefer the entry whose parsed year matches the
 *      request's TMDB year (±1 year tolerance).
 *   3. **Title-only fallback** — `LibraryIndex.lookup`, the original
 *      behaviour, as a last resort.
 *
 * Marked `internal` so the Detail screen can also call it.
 */
internal suspend fun pickBestLibraryMatch(
    playlist: com.hushtv.tv.data.Playlist,
    requestTitle: String,
    kind: String,
    tmdbMeta: com.hushtv.tv.data.RequestMetaStore.Meta?,
): com.hushtv.tv.data.LibraryIndex.Entry? {
    // findAllCandidates uses TitleMatcher.isStrongMatch — same strict
    // bar Collections uses — so single-letter library titles can never
    // match into long TMDB titles via substring (the "analyze that"
    // → "Z (2019)" bug).
    val candidates = com.hushtv.tv.data.LibraryIndex.findAllCandidates(
        rawTitle = requestTitle,
        kind = kind,
        preferredYear = tmdbMeta?.releaseYear,
    )
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()

    if (tmdbMeta != null && tmdbMeta.tmdbId > 0) {
        com.hushtv.tv.data.TmdbIdResolver
            .pickByTmdbId(playlist, candidates, tmdbMeta.tmdbId)
            ?.let { return it }
    }
    return com.hushtv.tv.data.LibraryIndex
        .findBest(requestTitle, kind, tmdbMeta?.releaseYear)
}

@Composable
private fun WatchNowButton(
    focusRequester: FocusRequester,
    loading: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (focused) Color.White else Cyan)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Icon(
            Icons.Default.PlayArrow, null,
            tint = Color(0xFF05080F),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (loading) "Opening…" else "Watch now",
            color = Color(0xFF05080F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun DismissButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Close, null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}
