package com.hushtv.tv.ui.requests

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RequestCache
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.TitleMatcher
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ──────────────────────────────────────────────────────────────────
 *  Public entry points — TV and Mobile wrappers around the shared
 *  RequestDetailContent composable.
 * ────────────────────────────────────────────────────────────────── */

@Composable
fun TVRequestDetailScreen(
    nav: NavController,
    requestId: String,
    playlistId: String,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(horizontal = 64.dp, vertical = 28.dp),
    ) {
        // Header row with a focusable back chip — auto-focuses the
        // first action on the detail body, so back is one DPAD-LEFT
        // away, not the default focus target.
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailBackChip(onClick = { nav.popBackStack() })
            Spacer(Modifier.width(20.dp))
            Text(
                "Request details",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(20.dp))
        RequestDetailContent(
            requestId = requestId,
            playlistId = playlistId,
            onPopBack = { nav.popBackStack() },
            onWatchTarget = { target ->
                when (target) {
                    is WatchTarget.Movie -> nav.navigate(
                        "moviedetail/$playlistId/${target.streamId}/${android.net.Uri.encode(target.title)}"
                    )
                    is WatchTarget.Series -> nav.navigate(
                        "series/$playlistId/${target.seriesId}/${android.net.Uri.encode(target.title)}"
                    )
                    WatchTarget.NotFound -> { /* stay on detail */ }
                }
            },
        )
    }
}

@Composable
fun MobileRequestDetailScreen(
    nav: NavController,
    requestId: String,
    playlistId: String,
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickableWithEnter { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Request details",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        RequestDetailContent(
            requestId = requestId,
            playlistId = playlistId,
            onPopBack = { nav.popBackStack() },
            onWatchTarget = { target ->
                when (target) {
                    is WatchTarget.Movie -> {
                        val playlist = PlaylistStore.find(ctx, playlistId)
                        if (playlist != null) {
                            val url = XtreamApi.movieUrl(
                                playlist.host, playlist.username, playlist.password,
                                target.streamId, null,
                            )
                            nav.navigate(
                                com.hushtv.tv.mobile.mobilePlayerRoute(
                                    playlistId = playlistId,
                                    streamUrl = url,
                                    channelName = target.title,
                                    isLive = false,
                                    vodStreamId = target.streamId,
                                    vodKind = "movie",
                                    vodPoster = null,
                                ),
                            )
                        }
                    }
                    is WatchTarget.Series -> nav.navigate(
                        com.hushtv.tv.mobile.mobileSeriesRoute(
                            playlistId = playlistId,
                            seriesId = target.seriesId.toString(),
                            name = target.title,
                            poster = target.poster,
                        ),
                    )
                    WatchTarget.NotFound -> { /* stay on detail */ }
                }
            },
        )
    }
}


/* ──────────────────────────────────────────────────────────────────
 *  Shared body — used by both TV and Mobile screens.
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun RequestDetailContent(
    requestId: String,
    playlistId: String,
    onPopBack: () -> Unit,
    onWatchTarget: (WatchTarget) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(RequestCache.byId(requestId) == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf(RequestCache.byId(requestId)) }
    var refreshTick by remember { mutableStateOf(0) }
    var resolving by remember { mutableStateOf(false) }

    // Lazy fetch only when the cache is cold or the user pulls to
    // refresh manually (refreshTick bump).
    LaunchedEffect(refreshTick) {
        if (request != null && refreshTick == 0) {
            // Cache hit — also fire-and-forget mark seen so the row
            // badge clears.
            RequestSeenStore.markSeen(ctx, request!!)
            return@LaunchedEffect
        }
        loading = true
        error = null
        val res = withContext(Dispatchers.IO) {
            ContentRequestApi.listRequests(ctx, limit = 50)
        }
        loading = false
        when (res) {
            is ContentRequestApi.ListResult.Success -> {
                RequestCache.put(res.requests)
                val match = res.requests.firstOrNull { it.id == requestId }
                if (match == null) {
                    error = "We couldn't find this request."
                } else {
                    request = match
                    RequestSeenStore.markSeen(ctx, match)
                }
            }
            is ContentRequestApi.ListResult.Error -> error = res.message
        }
    }

    when {
        loading && request == null -> Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Cyan)
        }
        error != null && request == null -> Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                error ?: "",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        request != null -> {
            val req = request!!
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item("hero") { DetailHero(req) }
                item("timeline") { StatusTimeline(req.status) }
                if (!req.adminResponse.isNullOrBlank()) {
                    item("notes") { AdminResponseCard(req.adminResponse) }
                }
                item("meta") { RequestMetaSection(req) }
                item("actions") {
                    DetailActions(
                        req = req,
                        resolving = resolving,
                        onWatchNow = onWatch@{
                            if (resolving) return@onWatch
                            resolving = true
                            scope.launch {
                                val target = withContext(Dispatchers.IO) {
                                    resolveTarget(ctx, playlistId, req)
                                }
                                resolving = false
                                onWatchTarget(target)
                            }
                        },
                        onReRequest = { onPopBack() },
                        onRefresh = { refreshTick += 1 },
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}


/* ──────────────────────────────────────────────────────────────────
 *  Sections
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun DetailHero(req: ContentRequestApi.Request) {
    val ctx = LocalContext.current
    val meta = remember(req.id, req.additionalInfo) {
        com.hushtv.tv.data.RequestMetaStore.get(ctx, req.id)
            ?: com.hushtv.tv.data.RequestMetaStore.parseTag(req.additionalInfo)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Poster — TMDB metadata gives us a real image; otherwise
        // fall back to a tinted box with the type emoji.
        Box(
            Modifier
                .size(width = 100.dp, height = 150.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center,
        ) {
            val url = meta?.posterPath?.let {
                com.hushtv.tv.data.TmdbService.img(it, "w342")
            }
            if (!url.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    if (req.type == "series") "📺" else "🎬",
                    fontSize = 36.sp,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (req.type == "series") "SERIES REQUEST" else "MOVIE REQUEST",
                color = Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                req.title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 26.sp,
            )
            if (meta?.releaseYear != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta.releaseYear.toString(),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            BigStatusBadge(req.status)
            if (!req.priority.isNullOrBlank() && req.priority != "medium") {
                Spacer(Modifier.height(8.dp))
                val (label, color) = when (req.priority) {
                    "high" -> "HIGH PRIORITY" to Color(0xFFEF4444)
                    "low" -> "LOW PRIORITY" to Color(0xFF94A3B8)
                    else -> req.priority.uppercase() to Color(0xFF94A3B8)
                }
                Box(
                    Modifier
                        .background(color.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(label, color = color, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusTimeline(current: ContentRequestApi.Status) {
    val steps = listOf(
        ContentRequestApi.Status.PENDING,
        ContentRequestApi.Status.IN_PROGRESS,
        ContentRequestApi.Status.ADDED,
    )
    val isResolved = current == ContentRequestApi.Status.ADDED ||
        current == ContentRequestApi.Status.ALREADY_AVAILABLE ||
        current == ContentRequestApi.Status.NOT_FOUND

    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            "STATUS",
            color = TextSecondary, fontSize = 10.sp,
            fontWeight = FontWeight.Black, letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(10.dp))

        // Special-case the two terminal-but-not-pipeline states.
        if (current == ContentRequestApi.Status.ALREADY_AVAILABLE) {
            TimelineRow(
                emoji = "✅",
                label = "Already in your library",
                description = "Good news — this title is already available to stream.",
                accent = Color(0xFFA855F7),
            )
            return@Column
        }
        if (current == ContentRequestApi.Status.NOT_FOUND) {
            TimelineRow(
                emoji = "❌",
                label = "Couldn't be added",
                description = "We weren't able to add this title. See the notes above for details.",
                accent = Color(0xFFEF4444),
            )
            return@Column
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            steps.forEachIndexed { idx, step ->
                val state = when {
                    step == current -> TimelineState.CURRENT
                    steps.indexOf(current) > idx -> TimelineState.DONE
                    isResolved && idx < steps.lastIndex -> TimelineState.DONE
                    else -> TimelineState.UPCOMING
                }
                TimelinePill(step, state, modifier = Modifier.weight(1f))
                if (idx < steps.lastIndex) {
                    Box(
                        Modifier
                            .padding(top = 14.dp)
                            .height(2.dp)
                            .weight(0.5f)
                            .background(
                                if (state == TimelineState.DONE) Cyan
                                else Color(0x22FFFFFF),
                            ),
                    )
                }
            }
        }
    }
}

private enum class TimelineState { DONE, CURRENT, UPCOMING }

@Composable
private fun TimelinePill(
    step: ContentRequestApi.Status,
    state: TimelineState,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        TimelineState.DONE -> Cyan
        TimelineState.CURRENT -> Color(0xFFFACC15)
        TimelineState.UPCOMING -> Color(0xFF475569)
    }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(color.copy(alpha = if (state == TimelineState.UPCOMING) 0.15f else 0.22f), CircleShape)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state == TimelineState.DONE) "✓"
                else (steps_emoji(step)),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            step.label,
            color = if (state == TimelineState.UPCOMING) TextSecondary else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun steps_emoji(step: ContentRequestApi.Status): String = when (step) {
    ContentRequestApi.Status.PENDING -> "⏳"
    ContentRequestApi.Status.IN_PROGRESS -> "🔄"
    ContentRequestApi.Status.ADDED -> "✅"
    else -> "·"
}

@Composable
private fun TimelineRow(
    emoji: String, label: String, description: String, accent: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape)
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp)
            Spacer(Modifier.height(2.dp))
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun AdminResponseCard(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Cyan.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Cyan,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "NOTE FROM HUSHTV",
                color = Cyan, fontSize = 10.sp,
                fontWeight = FontWeight.Black, letterSpacing = 2.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun RequestMetaSection(req: ContentRequestApi.Request) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "REQUEST DETAILS",
            color = TextSecondary, fontSize = 10.sp,
            fontWeight = FontWeight.Black, letterSpacing = 2.sp,
        )
        MetaRow("Submitted", formatDate(req.createdDate))
        if (req.updatedDate.isNotBlank() && req.updatedDate != req.createdDate) {
            MetaRow("Last update", formatDate(req.updatedDate))
        }
        if (req.type == "series" && !req.seriesRequestType.isNullOrBlank()) {
            MetaRow(
                "Scope",
                if (req.seriesRequestType == "specific_episodes")
                    "Specific seasons / episodes"
                else "Entire series",
            )
        }
        if (!req.seasons.isNullOrBlank()) MetaRow("Seasons", req.seasons)
        if (!req.episodes.isNullOrBlank()) MetaRow("Episodes", req.episodes)
        if (!req.additionalInfo.isNullOrBlank()) {
            val cleaned = com.hushtv.tv.data.RequestMetaStore.stripTag(req.additionalInfo)
            if (!cleaned.isNullOrBlank()) {
                Column {
                    Text("Additional info", color = TextSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(cleaned, color = TextPrimary, fontSize = 13.sp,
                        lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 110.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Tries to render an ISO-8601 timestamp as a human-readable date.
 *  Tolerates whatever shape the gateway throws at us — falls back to
 *  the raw string if parsing fails. */
private fun formatDate(iso: String): String {
    if (iso.isBlank()) return "—"
    val candidate = if (iso.endsWith("Z") || iso.contains("+")) iso else "${iso}Z"
    return runCatching {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
            java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(iso.substringBefore('.'))
        if (parsed != null) {
            java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.US)
                .format(parsed)
        } else iso
    }.getOrDefault(candidate)
}

@Composable
private fun DetailActions(
    req: ContentRequestApi.Request,
    resolving: Boolean,
    onWatchNow: () -> Unit,
    onReRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (req.status) {
            ContentRequestApi.Status.ADDED,
            ContentRequestApi.Status.ALREADY_AVAILABLE -> {
                ActionButton(
                    label = if (resolving) "Opening…" else "Watch now",
                    leadingEmoji = "▶",
                    primary = true,
                    onClick = onWatchNow,
                )
            }
            ContentRequestApi.Status.NOT_FOUND -> {
                ActionButton(
                    label = "Re-request with more info",
                    leadingEmoji = "🔁",
                    primary = true,
                    onClick = onReRequest,
                )
            }
            else -> { /* pending / in_progress — no primary action yet */ }
        }
        ActionButton(
            label = "Refresh",
            leadingEmoji = "↻",
            primary = false,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    leadingEmoji: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                when {
                    primary && focused -> Color.White
                    primary -> Cyan
                    focused -> Cyan.copy(alpha = 0.18f)
                    else -> SurfaceElev
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Text(
            leadingEmoji,
            fontSize = 16.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (primary) Color(0xFF05080F) else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
private fun BigStatusBadge(status: ContentRequestApi.Status) {
    val (bg, fg) = when (status) {
        ContentRequestApi.Status.PENDING -> Color(0x33F59E0B) to Color(0xFFF59E0B)
        ContentRequestApi.Status.IN_PROGRESS -> Color(0x333B82F6) to Color(0xFF60A5FA)
        ContentRequestApi.Status.ALREADY_AVAILABLE -> Color(0x33A855F7) to Color(0xFFC084FC)
        ContentRequestApi.Status.ADDED -> Color(0x3322C55E) to Color(0xFF34D399)
        ContentRequestApi.Status.NOT_FOUND -> Color(0x33EF4444) to Color(0xFFF87171)
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            "${status.emoji} ${status.label}",
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun DetailBackChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { focusRequester.requestFocus() }
    }
    Row(
        Modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.ArrowBack, null, tint = Color.White,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Back", color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
    }
}


/* ──────────────────────────────────────────────────────────────────
 *  Library deep-link resolver — re-uses the same matcher logic the
 *  RequestNotificationHost banner uses.
 * ────────────────────────────────────────────────────────────────── */

private suspend fun resolveTarget(
    ctx: Context,
    playlistId: String,
    req: ContentRequestApi.Request,
): WatchTarget {
    val playlist = PlaylistStore.find(ctx, playlistId) ?: return WatchTarget.NotFound
    val targetKind = if (req.type == "series") "series" else "movie"

    val tmdbMeta = com.hushtv.tv.data.RequestMetaStore.get(ctx, req.id)
        ?: com.hushtv.tv.data.RequestMetaStore.parseTag(req.additionalInfo)

    // Year-aware library lookup — when the request was made via the
    // TMDB picker we know the exact release year, so two films with
    // the same title (e.g. "Aladdin" 1992 vs 2019) resolve correctly.
    if (LibraryIndex.prime(ctx, playlist)) {
        LibraryIndex.findBest(req.title, targetKind, tmdbMeta?.releaseYear)?.let { entry ->
            return when (entry.kind) {
                "series" -> WatchTarget.Series(
                    seriesId = entry.seriesId,
                    title = entry.title,
                    poster = entry.poster,
                )
                else -> WatchTarget.Movie(
                    streamId = entry.streamId,
                    title = entry.title,
                )
            }
        }
        return WatchTarget.NotFound
    }

    // Fallback for when LibraryIndex prime fails (Xtream outage).
    val pool: List<MediaCard> = runCatching {
        XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, targetKind)
    }.getOrNull() ?: return WatchTarget.NotFound

    val needle = TitleMatcher.normalize(req.title)
    if (needle.isBlank()) return WatchTarget.NotFound

    val match = pool.firstOrNull { TitleMatcher.normalize(it.title) == needle }
        ?: pool.firstOrNull {
            val n = TitleMatcher.normalize(it.title)
            n.isNotBlank() && (n.contains(needle) || needle.contains(n))
        }
        ?: return WatchTarget.NotFound

    return when (req.type) {
        "series" -> WatchTarget.Series(match.seriesId, match.title, match.poster)
        else -> WatchTarget.Movie(match.streamId, match.title)
    }
}
