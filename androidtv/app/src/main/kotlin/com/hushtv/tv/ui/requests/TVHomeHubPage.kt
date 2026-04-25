package com.hushtv.tv.ui.requests

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RecentChannelStore
import com.hushtv.tv.data.RequestMetaStore
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.WatchProgressStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.screens.clickableWithEnterAndLongPress
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary

/**
 * TV "FOR YOU" home hub — single full-screen page in the home pager
 * with three sections stacked vertically, mirroring the Mobile hub:
 *
 *   1. CHANNEL HISTORY (last 5)
 *   2. CONTINUE WATCHING (movies + series resume)
 *   3. MY REQUESTS
 *
 * Each section has a distinct card style for visual variety. The
 * focused card drives the full-screen backdrop behind everything.
 *
 * Sections are skipped entirely when empty — page is hidden if all
 * three are empty (parent pageOrder check).
 *
 * Focus management:
 *   • Down from CH-row → focus first CW card
 *   • Down from CW-row → focus first request card
 *   • Down from REQ-row → next page in pager (host)
 *   • Up from CH-row → top nav (host)
 */
@Composable
fun TVHomeHubPage(
    playlistId: String,
    nav: NavController,
    channelHistory: List<Pair<Int, RecentChannelStore.Meta>>,
    cwEntries: List<com.hushtv.tv.ui.screens.home.ContinueEntry>,
    requests: List<ContentRequestApi.Request>,
    firstItemFocus: FocusRequester,
    onUpFromTop: () -> Unit,
    onDownFromBottom: () -> Unit,
    onRemoveCw: (com.hushtv.tv.ui.screens.home.ContinueEntry) -> Unit,
    onRequestHidden: () -> Unit = {},
) {
    val ctx = LocalContext.current

    // Per-row focus targets so down/up between sections lands on a
    // sensible element instead of jumping to whatever the system
    // picks.
    val cwFirstFocus = remember { FocusRequester() }
    val reqFirstFocus = remember { FocusRequester() }

    // Backdrop driver — the focused card across all three sections
    // updates this so the user sees the relevant backdrop instantly.
    var backdropUrl by remember { mutableStateOf<String?>(null) }
    var hideTarget by remember { mutableStateOf<ContentRequestApi.Request?>(null) }
    var snack by remember { mutableStateOf<RemovedRequestSnack?>(null) }

    Box(Modifier.fillMaxSize()) {
        // Hero backdrop layer.
        TVHubBackdrop(url = backdropUrl)

        // Vertical scroll for the full content stack — important on
        // smaller TVs where 3 stacked rows might overflow.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 48.dp, end = 32.dp, top = 96.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            if (channelHistory.isNotEmpty()) {
                HubSectionLabel("CHANNEL HISTORY", "· last ${channelHistory.size}")
                ChannelHistoryRow(
                    items = channelHistory,
                    playlistId = playlistId,
                    nav = nav,
                    firstItemFocus = firstItemFocus,
                    onFocusedPosterChange = { backdropUrl = it },
                    onUpFromRow = onUpFromTop,
                    onDownFromRow = {
                        if (cwEntries.isNotEmpty()) cwFirstFocus.requestFocus()
                        else if (requests.isNotEmpty()) reqFirstFocus.requestFocus()
                        else onDownFromBottom()
                    },
                )
            }
            if (cwEntries.isNotEmpty()) {
                HubSectionLabel("CONTINUE WATCHING", "· ${cwEntries.size}")
                CwHubRow(
                    entries = cwEntries,
                    playlistId = playlistId,
                    nav = nav,
                    firstItemFocus = if (channelHistory.isEmpty()) firstItemFocus else cwFirstFocus,
                    onFocusedBackdropChange = { backdropUrl = it },
                    onLongPress = onRemoveCw,
                    onUpFromRow = if (channelHistory.isEmpty()) onUpFromTop else { ->
                        // No way to grab specific FocusRequester for
                        // last channel history card — focus parent and
                        // let it walk back. Easiest UX: go to top nav.
                        // User can D-pad down again to find CW.
                        onUpFromTop()
                    },
                    onDownFromRow = {
                        if (requests.isNotEmpty()) reqFirstFocus.requestFocus()
                        else onDownFromBottom()
                    },
                )
            }
            if (requests.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HubSectionLabel("MY REQUESTS", "· ${requests.size}")
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "HOLD OK TO REMOVE",
                        color = Color(0x99FFFFFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                }
                RequestsHubRow(
                    requests = requests,
                    playlistId = playlistId,
                    nav = nav,
                    firstItemFocus = if (channelHistory.isEmpty() && cwEntries.isEmpty())
                        firstItemFocus else reqFirstFocus,
                    onFocusedBackdropChange = { backdropUrl = it },
                    onLongPress = { hideTarget = it },
                    onUpFromRow = onUpFromTop,
                    onDownFromRow = onDownFromBottom,
                )
            }
        }
    }

    // Long-press → confirm dialog → snack with UNDO.
    val ht = hideTarget
    if (ht != null) {
        RemoveRequestDialog(
            title = ht.title,
            onConfirm = {
                com.hushtv.tv.data.RequestHiddenStore.hide(ctx, ht.id)
                snack = RemovedRequestSnack(requestId = ht.id, title = ht.title)
                hideTarget = null
                onRequestHidden()
            },
            onDismiss = { hideTarget = null },
        )
    }
    Box(Modifier.fillMaxSize()) {
        RemovedRequestToast(
            removed = snack,
            onUndo = {
                snack?.let { com.hushtv.tv.data.RequestHiddenStore.unhide(ctx, it.requestId) }
                snack = null
                onRequestHidden()
            },
            onAutoDismiss = { snack = null },
            applyStatusBarPadding = false,
        )
    }
}


/* ───────────────────────────── BACKDROP ──────────────────────────── */

@Composable
private fun TVHubBackdrop(url: String?) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC05080F),
                        0.45f to Color(0xCC05080F),
                        1f to Color(0xF005080F),
                    )
                ),
        )
    }
}


/* ───────────────────────────── LABEL ─────────────────────────────── */

@Composable
private fun HubSectionLabel(label: String, suffix: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = Cyan, fontSize = 13.sp,
            fontWeight = FontWeight.Black, letterSpacing = 2.sp,
        )
        if (suffix != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                suffix, color = Color(0xFF64748B), fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


/* ──────────────────── CHANNEL HISTORY ROW ─────────────────────── */

@Composable
private fun ChannelHistoryRow(
    items: List<Pair<Int, RecentChannelStore.Meta>>,
    playlistId: String,
    nav: NavController,
    firstItemFocus: FocusRequester,
    onFocusedPosterChange: (String?) -> Unit,
    onUpFromRow: () -> Unit,
    onDownFromRow: () -> Unit,
) {
    val ctx = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> { onUpFromRow(); true }
                    Key.DirectionDown -> { onDownFromRow(); true }
                    else -> false
                }
            },
    ) {
        items(items, key = { "ch-${it.first}" }) { entry ->
            val sid = entry.first
            val meta = entry.second
            ChannelHistoryCard(
                name = meta.name,
                poster = meta.poster,
                first = entry === items.firstOrNull(),
                firstItemFocus = firstItemFocus,
                onFocused = { onFocusedPosterChange(meta.poster) },
                onClick = {
                    val playlist = PlaylistStore.find(ctx, playlistId)
                        ?: return@ChannelHistoryCard
                    val url = XtreamApi.liveUrl(
                        playlist.host, playlist.username, playlist.password, sid,
                    )
                    nav.navigate(
                        "player/$playlistId/${android.net.Uri.encode(url)}/" +
                            "${android.net.Uri.encode(meta.name)}/true"
                    )
                },
            )
        }
    }
}

private inline fun <T> List<T>.itemsWithFirstFlag(): List<Pair<T, Boolean>> =
    mapIndexed { i, v -> v to (i == 0) }

@Composable
private fun ChannelHistoryCard(
    name: String,
    poster: String?,
    first: Boolean,
    firstItemFocus: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .let { if (first) it.focusRequester(firstItemFocus) else it }
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A1220))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Cyan else Color(0x2206B6D4),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(2).uppercase(),
                color = Color(0xFF94A3B8),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            if (!poster.isNullOrBlank()) {
                AsyncImage(
                    model = poster, contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name, color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}


/* ────────────────── CONTINUE WATCHING ROW ─────────────────────── */

@Composable
private fun CwHubRow(
    entries: List<com.hushtv.tv.ui.screens.home.ContinueEntry>,
    playlistId: String,
    nav: NavController,
    firstItemFocus: FocusRequester,
    onFocusedBackdropChange: (String?) -> Unit,
    onLongPress: (com.hushtv.tv.ui.screens.home.ContinueEntry) -> Unit,
    onUpFromRow: () -> Unit,
    onDownFromRow: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> { onUpFromRow(); true }
                    Key.DirectionDown -> { onDownFromRow(); true }
                    else -> false
                }
            },
    ) {
        items(entries, key = { "cw-${it.progress.kind}-${it.progress.streamId}" }) { entry ->
            CwHubCard(
                entry = entry,
                first = entry === entries.firstOrNull(),
                firstItemFocus = firstItemFocus,
                onFocused = { onFocusedBackdropChange(entry.progress.poster) },
                onClick = {
                    nav.navigate(
                        "moviedetail/$playlistId/${entry.progress.streamId}" +
                            "/${android.net.Uri.encode(entry.progress.title)}"
                    )
                },
                onLongPress = { onLongPress(entry) },
            )
        }
    }
}

@Composable
private fun CwHubCard(
    entry: com.hushtv.tv.ui.screens.home.ContinueEntry,
    first: Boolean,
    firstItemFocus: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .let { if (first) it.focusRequester(firstItemFocus) else it }
            .size(width = 280.dp, height = 158.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickableWithEnterAndLongPress(onClick = onClick, onLongPress = onLongPress),
    ) {
        if (!entry.progress.poster.isNullOrBlank()) {
            AsyncImage(
                model = entry.progress.poster, contentDescription = entry.progress.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x00000000),
                    0.5f to Color(0x55000000),
                    1f to Color(0xEE000000),
                )
            )
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xCC06B6D4)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow, null,
                tint = Color.White, modifier = Modifier.size(32.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                entry.progress.title,
                color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (entry.progress.durationMs > 0) {
                Spacer(Modifier.height(5.dp))
                val pct = (entry.progress.positionMs.toFloat() /
                    entry.progress.durationMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(3.dp)
                        .background(Color(0x33FFFFFF), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct).height(3.dp)
                            .background(Cyan, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}


/* ─────────────────────── REQUESTS ROW ─────────────────────────── */

@Composable
private fun RequestsHubRow(
    requests: List<ContentRequestApi.Request>,
    playlistId: String,
    nav: NavController,
    firstItemFocus: FocusRequester,
    onFocusedBackdropChange: (String?) -> Unit,
    onLongPress: (ContentRequestApi.Request) -> Unit,
    onUpFromRow: () -> Unit,
    onDownFromRow: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> { onUpFromRow(); true }
                    Key.DirectionDown -> { onDownFromRow(); true }
                    else -> false
                }
            },
    ) {
        items(requests, key = { it.id }) { req ->
            RequestHubCard(
                req = req,
                first = req === requests.firstOrNull(),
                firstItemFocus = firstItemFocus,
                onFocused = { backdropPath ->
                    onFocusedBackdropChange(
                        backdropPath?.let { TmdbService.img(it, "original") }
                    )
                },
                onClick = {
                    nav.navigate("requestdetail/$playlistId/${req.id}")
                },
                onLongPress = { onLongPress(req) },
            )
        }
    }
}

@Composable
private fun RequestHubCard(
    req: ContentRequestApi.Request,
    first: Boolean,
    firstItemFocus: FocusRequester,
    onFocused: (String?) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val unseen = RequestSeenStore.isUnseen(ctx, req)

    val (badgeBg, badgeFg, glow) = when (req.status) {
        ContentRequestApi.Status.PENDING ->
            Triple(Color(0xCCF59E0B), Color(0xFF05080F), Color(0xFFF59E0B))
        ContentRequestApi.Status.IN_PROGRESS ->
            Triple(Color(0xCC3B82F6), Color.White, Color(0xFF60A5FA))
        ContentRequestApi.Status.ALREADY_AVAILABLE ->
            Triple(Color(0xCCA855F7), Color.White, Color(0xFFC084FC))
        ContentRequestApi.Status.ADDED ->
            Triple(Color(0xCC22C55E), Color.White, Color(0xFF34D399))
        ContentRequestApi.Status.NOT_FOUND ->
            Triple(Color(0xCCEF4444), Color.White, Color(0xFFF87171))
    }
    var meta by remember(req.id) {
        mutableStateOf(
            RequestMetaStore.get(ctx, req.id) ?: RequestMetaStore.parseTag(req.additionalInfo)
        )
    }
    LaunchedEffect(req.id) {
        if (meta == null) meta = RequestPosterResolver.resolveOrFetch(ctx, req)
    }
    val card = meta
    LaunchedEffect(focused, card?.backdropPath) {
        if (focused) onFocused(card?.backdropPath)
    }

    Box(
        Modifier
            .let { if (first) it.focusRequester(firstItemFocus) else it }
            .size(width = 280.dp, height = 158.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    unseen -> glow.copy(alpha = 0.6f)
                    else -> Color(0x22FFFFFF)
                },
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnterAndLongPress(onClick = onClick, onLongPress = onLongPress),
    ) {
        val backdropUrl = card?.backdropPath?.let { TmdbService.img(it, "w780") }
        val posterUrl = card?.posterPath?.let { TmdbService.img(it, "w500") }
        when {
            !backdropUrl.isNullOrBlank() -> AsyncImage(
                model = backdropUrl, contentDescription = req.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            !posterUrl.isNullOrBlank() -> AsyncImage(
                model = posterUrl, contentDescription = req.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            else -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(glow.copy(alpha = 0.35f), Color(0xFF05080F))
                    )
                ),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xCC000000),
                    0.4f to Color(0x44000000),
                    1f to Color(0xF005080F),
                )
            )
        )
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .background(badgeBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    "${req.status.emoji} ${req.status.label}",
                    color = badgeFg, fontSize = 10.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (unseen) {
                Box(
                    Modifier.size(8.dp).background(Cyan, CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                req.title, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Black, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
