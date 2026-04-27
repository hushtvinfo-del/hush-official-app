package com.hushtv.tv.ui.requests

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RequestCache
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.RpdbService
import com.hushtv.tv.data.TitleMatcher
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(RequestCache.byId(requestId) == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf(RequestCache.byId(requestId)) }
    var refreshTick by remember { mutableStateOf(0) }
    var resolving by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        if (request != null && refreshTick == 0) {
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

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
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
            request != null -> TVRequestDetailBody(
                req = request!!,
                resolving = resolving,
                onPopBack = { nav.popBackStack() },
                onRefresh = { refreshTick += 1 },
                onWatchNow = {
                    if (resolving) return@TVRequestDetailBody
                    resolving = true
                    scope.launch {
                        val target = withContext(Dispatchers.IO) {
                            resolveTarget(ctx, playlistId, request!!)
                        }
                        resolving = false
                        when (target) {
                            is WatchTarget.Movie -> nav.navigate(
                                "moviedetail/$playlistId/${target.streamId}/${android.net.Uri.encode(target.title)}"
                            )
                            is WatchTarget.Series -> nav.navigate(
                                "series/$playlistId/${target.seriesId}/${android.net.Uri.encode(target.title)}"
                            )
                            WatchTarget.NotFound -> { /* stay on detail */ }
                        }
                    }
                },
            )
        }
    }
}

/**
 * Fixed-layout TV body — everything fits in one 1920×1080 frame with
 * no scrolling. Top bar carries Back + Refresh; the main body is a
 * 2-column Row: 320 dp poster/title pane on the left, full-flex status
 * + meta + actions pane on the right.
 *
 * Extra meta rows (seasons, episodes, additional info) overflow into
 * the right pane's reserved space without ever pushing the action row
 * off-screen. If truly too long, the right pane falls back to a
 * vertical scroll container, BUT the top bar remains fixed so the
 * user never loses the Back/Refresh focus targets — no "can't scroll
 * back up" trap.
 */
@Composable
private fun TVRequestDetailBody(
    req: ContentRequestApi.Request,
    resolving: Boolean,
    onPopBack: () -> Unit,
    onRefresh: () -> Unit,
    onWatchNow: () -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { backFocus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 28.dp),
    ) {
        // ── Top bar — fixed height, always visible ──
        TopBar(
            backFocus = backFocus,
            onPopBack = onPopBack,
            onRefresh = onRefresh,
        )

        Spacer(Modifier.height(28.dp))

        // ── 2-column body ──
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // LEFT — poster + identity. Fixed width.
            HeroPane(
                req = req,
                modifier = Modifier.width(320.dp),
            )
            // RIGHT — status timeline, admin note, meta, actions.
            // Weighted so it takes every remaining pixel.
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                StatusPipeline(req.status)
                if (!req.adminResponse.isNullOrBlank()) {
                    AdminNote(req.adminResponse)
                }
                MetaBlock(req)
                if (req.status == ContentRequestApi.Status.ADDED ||
                    req.status == ContentRequestApi.Status.ALREADY_AVAILABLE ||
                    req.status == ContentRequestApi.Status.NOT_FOUND
                ) {
                    PrimaryActionButton(
                        label = when (req.status) {
                            ContentRequestApi.Status.NOT_FOUND ->
                                "Re-request with more info"
                            else -> if (resolving) "Opening…" else "Watch now"
                        },
                        icon = if (req.status == ContentRequestApi.Status.NOT_FOUND)
                            Icons.Outlined.Replay
                        else Icons.Outlined.PlayArrow,
                        onClick = onWatchNow,
                    )
                }
            }
        }
    }
}

@Composable
fun MobileRequestDetailScreen(
    nav: NavController,
    requestId: String,
    playlistId: String,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(RequestCache.byId(requestId) == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf(RequestCache.byId(requestId)) }
    var refreshTick by remember { mutableStateOf(0) }
    var resolving by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        if (request != null && refreshTick == 0) {
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
                    .background(Color(0x14FFFFFF))
                    .clickableWithEnter { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack, null,
                    tint = Color.White, modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Request details",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x14FFFFFF))
                    .clickableWithEnter { refreshTick += 1 },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Refresh, null,
                    tint = Color.White, modifier = Modifier.size(18.dp),
                )
            }
        }
        when {
            loading && request == null -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Cyan) }
            error != null && request == null -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(error ?: "", color = TextSecondary, fontSize = 14.sp)
            }
            request != null -> {
                val req = request!!
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { HeroPane(req, modifier = Modifier.fillMaxWidth(), compact = true) }
                    item { StatusPipeline(req.status) }
                    if (!req.adminResponse.isNullOrBlank()) {
                        item { AdminNote(req.adminResponse) }
                    }
                    item { MetaBlock(req) }
                    item {
                        if (req.status == ContentRequestApi.Status.ADDED ||
                            req.status == ContentRequestApi.Status.ALREADY_AVAILABLE ||
                            req.status == ContentRequestApi.Status.NOT_FOUND
                        ) {
                            PrimaryActionButton(
                                label = when (req.status) {
                                    ContentRequestApi.Status.NOT_FOUND ->
                                        "Re-request with more info"
                                    else -> if (resolving) "Opening…" else "Watch now"
                                },
                                icon = if (req.status == ContentRequestApi.Status.NOT_FOUND)
                                    Icons.Outlined.Replay
                                else Icons.Outlined.PlayArrow,
                                onClick = {
                                    if (resolving) return@PrimaryActionButton
                                    resolving = true
                                    scope.launch {
                                        val target = withContext(Dispatchers.IO) {
                                            resolveTarget(ctx, playlistId, req)
                                        }
                                        resolving = false
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
                                            WatchTarget.NotFound -> { /* stay */ }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}


/* ──────────────────────────────────────────────────────────────────
 *  Top bar — Back chip on the left, inline title, Refresh on the right.
 *  Never leaves the viewport, so D-pad UP from any content always
 *  has somewhere to land.
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun TopBar(
    backFocus: FocusRequester,
    onPopBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconPill(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            label = "Back",
            focusRequester = backFocus,
            onClick = onPopBack,
        )
        Spacer(Modifier.width(22.dp))
        Text(
            "Request details",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp,
        )
        Spacer(Modifier.weight(1f))
        IconPill(
            icon = Icons.Outlined.Refresh,
            label = "Refresh",
            onClick = onRefresh,
        )
    }
}

@Composable
private fun IconPill(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(120))
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(shape)
            .background(
                if (focused) Cyan.copy(alpha = 0.16f) else Color(0x10FFFFFF),
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x1FFFFFFF),
                shape = shape,
            )
            .let { m ->
                if (focusRequester != null) m.focusRequester(focusRequester) else m
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Icon(icon, null, tint = if (focused) Cyan else Color.White,
            modifier = Modifier.size(16.dp))
        Text(
            label,
            color = if (focused) Cyan else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
}


/* ──────────────────────────────────────────────────────────────────
 *  Hero pane — poster + type + title + year + status chip.
 *
 *  [compact = false] (TV) : vertical layout with a tall 2:3 poster,
 *      fits a fixed 320 dp width column.
 *  [compact = true]  (Mobile) : horizontal row with a 96×144 poster
 *      on the left and text stacked to the right, so the Hero doesn't
 *      monopolise the phone viewport.
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun HeroPane(
    req: ContentRequestApi.Request,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val ctx = LocalContext.current
    var meta by remember(req.id) {
        mutableStateOf(
            com.hushtv.tv.data.RequestMetaStore.get(ctx, req.id)
                ?: com.hushtv.tv.data.RequestMetaStore.parseTag(req.additionalInfo)
        )
    }
    LaunchedEffect(req.id) {
        if (meta == null) {
            meta = RequestPosterResolver.resolveOrFetch(ctx, req)
        }
        // After the TMDB poster/overview is in place, kick off a
        // one-shot external_ids fetch so the RPDB rating-baked
        // poster can take over from the plain TMDB poster. Cached
        // for life after the first successful call, so this only
        // costs one extra HTTP round-trip per request.
        val enriched = RequestPosterResolver.ensureImdbId(ctx, req)
        if (enriched != null && enriched.imdbId != meta?.imdbId) {
            meta = enriched
        }
    }

    if (compact) {
        HeroPaneCompact(req, meta, modifier)
    } else {
        HeroPaneTall(req, meta, modifier)
    }
}

@Composable
private fun HeroPaneTall(
    req: ContentRequestApi.Request,
    meta: com.hushtv.tv.data.RequestMetaStore.Meta?,
    modifier: Modifier,
) {
    Column(modifier) {
        // 2:3 poster, full width of pane.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF0A0F1A),
                        1f to Color(0xFF05080F),
                    )
                )
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            RatingAwarePoster(
                meta = meta,
                type = req.type,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                tmdbSize = "w500",
            )
        }

        Spacer(Modifier.height(16.dp))
        HeroTypeRow(req.type)
        Spacer(Modifier.height(6.dp))
        Text(
            req.title,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 28.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val year = meta?.releaseYear
        if (year != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                year.toString(),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        StatusChip(req.status)
        HeroPriorityTag(req.priority)
        HeroSynopsis(meta?.overview, maxLines = 5)
    }
}

@Composable
private fun HeroPaneCompact(
    req: ContentRequestApi.Request,
    meta: com.hushtv.tv.data.RequestMetaStore.Meta?,
    modifier: Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(width = 96.dp, height = 144.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0F1A)),
            contentAlignment = Alignment.Center,
        ) {
            RatingAwarePoster(
                meta = meta,
                type = req.type,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                tmdbSize = "w342",
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            HeroTypeRow(req.type)
            Spacer(Modifier.height(4.dp))
            Text(
                req.title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val year = meta?.releaseYear
            if (year != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    year.toString(),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            StatusChip(req.status)
            HeroPriorityTag(req.priority)
            HeroSynopsis(meta?.overview, maxLines = 3)
        }
    }
}

/**
 * Poster variant that prefers RPDB's rating-baked image (IMDb /
 * Rotten Tomatoes / Metacritic / TMDB scores rendered into the
 * bottom strip of the poster) when we have an imdb_id, else falls
 * back to the plain TMDB poster, else to the type icon.
 *
 * Coil's listener fires onError when the RPDB image 404s (e.g.
 * subscription expired, a title RPDB doesn't have) — we swap to
 * the TMDB URL automatically in that case so users never see a
 * broken image.
 */
@Composable
private fun RatingAwarePoster(
    meta: com.hushtv.tv.data.RequestMetaStore.Meta?,
    type: String,
    modifier: Modifier,
    tmdbSize: String,
) {
    val tmdbUrl = meta?.posterPath?.let {
        com.hushtv.tv.data.TmdbService.img(it, tmdbSize)
    }
    val rpdbUrl = RpdbService.posterUrl(meta?.imdbId)

    // Local swap state — flips to TMDB if RPDB fails to load.
    var useTmdb by remember(meta?.imdbId, meta?.posterPath) {
        mutableStateOf(rpdbUrl.isNullOrBlank())
    }
    val chosen = if (useTmdb || rpdbUrl.isNullOrBlank()) tmdbUrl else rpdbUrl

    if (!chosen.isNullOrBlank()) {
        coil.compose.AsyncImage(
            model = chosen,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier,
            onError = { useTmdb = true },
        )
    } else {
        Icon(
            if (type == "series") Icons.Outlined.LiveTv else Icons.Outlined.Movie,
            null,
            tint = Color(0xFF334155),
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun HeroTypeRow(type: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (type == "series") Icons.Outlined.LiveTv else Icons.Outlined.Movie,
            null,
            tint = Cyan,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (type == "series") "SERIES REQUEST" else "MOVIE REQUEST",
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun HeroPriorityTag(priority: String?) {
    if (priority.isNullOrBlank() || priority == "medium") return
    Spacer(Modifier.height(8.dp))
    val (label, color) = when (priority) {
        "high" -> "HIGH PRIORITY" to Color(0xFFEF4444)
        "low" -> "LOW PRIORITY" to Color(0xFF94A3B8)
        else -> priority.uppercase() to Color(0xFF94A3B8)
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
    }
}

/**
 * TMDB synopsis block — 3-to-5 line clamped paragraph under the
 * status chip. Only renders when we have text; gracefully absent
 * for fresh requests where the resolver hasn't finished yet or
 * when TMDB has no overview for the title.
 */
@Composable
private fun HeroSynopsis(overview: String?, maxLines: Int) {
    val cleaned = overview?.trim().orEmpty()
    if (cleaned.isBlank()) return
    Spacer(Modifier.height(12.dp))
    Text(
        cleaned,
        color = TextSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}


/* ──────────────────────────────────────────────────────────────────
 *  Status chip — compact, inline-friendly, icon + label.
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun StatusChip(status: ContentRequestApi.Status) {
    val (icon, accent, label) = statusVisual(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(12.dp))
        Text(
            label.uppercase(),
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
    }
}

private data class StatusVisual(
    val icon: ImageVector,
    val accent: Color,
    val label: String,
)

private fun statusVisual(status: ContentRequestApi.Status): StatusVisual = when (status) {
    ContentRequestApi.Status.PENDING ->
        StatusVisual(Icons.Outlined.HourglassTop, Color(0xFFF59E0B), "Pending")
    ContentRequestApi.Status.IN_PROGRESS ->
        StatusVisual(Icons.Outlined.Autorenew, Color(0xFF60A5FA), "In Progress")
    ContentRequestApi.Status.ADDED ->
        StatusVisual(Icons.Outlined.CheckCircle, Color(0xFF34D399), "Added")
    ContentRequestApi.Status.ALREADY_AVAILABLE ->
        StatusVisual(Icons.Outlined.LibraryAddCheck, Color(0xFFC084FC), "Already Available")
    ContentRequestApi.Status.NOT_FOUND ->
        StatusVisual(Icons.Outlined.ErrorOutline, Color(0xFFF87171), "Not Found")
}


/* ──────────────────────────────────────────────────────────────────
 *  Status pipeline — horizontal progress line with 3 icon nodes.
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun StatusPipeline(current: ContentRequestApi.Status) {
    val isResolved = current == ContentRequestApi.Status.ADDED ||
        current == ContentRequestApi.Status.ALREADY_AVAILABLE ||
        current == ContentRequestApi.Status.NOT_FOUND

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        SectionLabel("STATUS")

        // Terminal special-case states render a single-row inline info.
        if (current == ContentRequestApi.Status.ALREADY_AVAILABLE) {
            Spacer(Modifier.height(14.dp))
            TerminalRow(
                icon = Icons.Outlined.LibraryAddCheck,
                label = "Already in your library",
                description = "Good news — this title is already available to stream.",
                accent = Color(0xFFC084FC),
            )
            return@Column
        }
        if (current == ContentRequestApi.Status.NOT_FOUND) {
            Spacer(Modifier.height(14.dp))
            TerminalRow(
                icon = Icons.Outlined.ErrorOutline,
                label = "Couldn't be added",
                description = "See the note above for details — re-submit with more info.",
                accent = Color(0xFFF87171),
            )
            return@Column
        }

        Spacer(Modifier.height(14.dp))

        val steps = listOf(
            ContentRequestApi.Status.PENDING,
            ContentRequestApi.Status.IN_PROGRESS,
            ContentRequestApi.Status.ADDED,
        )
        val currentIdx = steps.indexOf(current).coerceAtLeast(0)

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { idx, step ->
                val state = when {
                    isResolved && idx < steps.lastIndex -> PipelineState.DONE
                    idx < currentIdx -> PipelineState.DONE
                    idx == currentIdx -> PipelineState.CURRENT
                    else -> PipelineState.UPCOMING
                }
                PipelineNode(step, state, modifier = Modifier.width(88.dp))
                if (idx < steps.lastIndex) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (state == PipelineState.DONE) Cyan
                                else Color(0x1AFFFFFF),
                            ),
                    )
                }
            }
        }
    }
}

private enum class PipelineState { DONE, CURRENT, UPCOMING }

@Composable
private fun PipelineNode(
    step: ContentRequestApi.Status,
    state: PipelineState,
    modifier: Modifier = Modifier,
) {
    val visual = statusVisual(step)
    val ringColor = when (state) {
        PipelineState.DONE -> Cyan
        PipelineState.CURRENT -> visual.accent
        PipelineState.UPCOMING -> Color(0xFF334155)
    }
    val fillColor = when (state) {
        PipelineState.DONE -> Cyan
        PipelineState.CURRENT -> visual.accent.copy(alpha = 0.16f)
        PipelineState.UPCOMING -> Color(0x08FFFFFF)
    }
    val iconTint = when (state) {
        PipelineState.DONE -> Color(0xFF05080F)
        PipelineState.CURRENT -> visual.accent
        PipelineState.UPCOMING -> Color(0xFF64748B)
    }
    val labelColor = when (state) {
        PipelineState.UPCOMING -> TextSecondary
        else -> TextPrimary
    }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(2.dp, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state == PipelineState.DONE) Icons.Outlined.Check else visual.icon,
                null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            visual.label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (state == PipelineState.CURRENT)
                FontWeight.Black else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun TerminalRow(
    icon: ImageVector,
    label: String,
    description: String,
    accent: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp)
            Spacer(Modifier.height(3.dp))
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}


/* ──────────────────────────────────────────────────────────────────
 *  Admin note — cyan-accented quote card.
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun AdminNote(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Cyan.copy(alpha = 0.08f))
            .border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Cyan,
                modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "NOTE FROM HUSHTV",
                color = Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
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


/* ──────────────────────────────────────────────────────────────────
 *  Meta block — compact, icon-prefixed rows.
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun MetaBlock(req: ContentRequestApi.Request) {
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        SectionLabel("DETAILS")
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            MetaRow(Icons.Outlined.Schedule, "Submitted", formatDate(req.createdDate))
            if (req.updatedDate.isNotBlank() && req.updatedDate != req.createdDate) {
                MetaRow(Icons.Outlined.Autorenew, "Last update", formatDate(req.updatedDate))
            }
            if (req.type == "series" && !req.seriesRequestType.isNullOrBlank()) {
                MetaRow(
                    Icons.Outlined.LiveTv,
                    "Scope",
                    if (req.seriesRequestType == "specific_episodes")
                        "Specific seasons / episodes"
                    else "Entire series",
                )
            }
            if (!req.seasons.isNullOrBlank()) {
                MetaRow(Icons.Outlined.LiveTv, "Seasons", req.seasons)
            }
            if (!req.episodes.isNullOrBlank()) {
                MetaRow(Icons.Outlined.LiveTv, "Episodes", req.episodes)
            }
            if (!req.additionalInfo.isNullOrBlank()) {
                val cleaned = com.hushtv.tv.data.RequestMetaStore.stripTag(req.additionalInfo)
                if (!cleaned.isNullOrBlank()) {
                    Column {
                        Text(
                            "Additional info",
                            color = TextSecondary, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(cleaned, color = TextPrimary, fontSize = 13.sp,
                            lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = TextSecondary,
            modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 110.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
    )
}


/* ──────────────────────────────────────────────────────────────────
 *  Primary action button (Watch now / Re-request).
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(if (focused) Color.White else Cyan, shape)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Icon(icon, null, tint = Color(0xFF05080F), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = Color(0xFF05080F),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
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

    if (LibraryIndex.prime(ctx, playlist)) {
        com.hushtv.tv.ui.requests.pickBestLibraryMatch(
            playlist, req.title, targetKind, tmdbMeta,
        )?.let { entry ->
            return when (entry.kind) {
                "series" -> WatchTarget.Series(entry.seriesId, entry.title, entry.poster)
                else -> WatchTarget.Movie(entry.streamId, entry.title)
            }
        }
        return WatchTarget.NotFound
    }

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
