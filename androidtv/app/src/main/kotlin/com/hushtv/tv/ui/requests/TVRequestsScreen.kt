package com.hushtv.tv.ui.requests

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.RequestCache
import com.hushtv.tv.data.RequestHiddenStore
import com.hushtv.tv.data.RequestMetaStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.UserContactStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.screens.clickableWithEnterAndLongPress
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Standalone "Requests" destination on TV — opened from the top-nav
 * "Requests" tab as `requests/{playlistId}`.
 *
 * Visual structure (top → bottom on a 1920×1080 canvas):
 *
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │ HERO BACKDROP (full-bleed, blurred + gradient)               │
 *   │   ▸ "MY REQUESTS" eyebrow                                    │
 *   │   ▸ Focused-card title (huge, plays as billboard)            │
 *   │   ▸ Status badge + admin response if any                     │
 *   │   ▸ "+ NEW REQUEST" button + "REFRESH" button                │
 *   └──────────────────────────────────────────────────────────────┘
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │ FILTER CHIPS: All · Pending · In Progress · Ready · Other    │
 *   └──────────────────────────────────────────────────────────────┘
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │ POSTER GRID — 5 cols × N rows, big 16:9 backdrop cards       │
 *   │ with focus glow, status pill, status-tinted ring on unseen.  │
 *   └──────────────────────────────────────────────────────────────┘
 *
 * Long-press DPAD_CENTER on any card → confirm-remove dialog.
 * Short-press → drill into RequestDetailScreen.
 */
@Composable
fun TVRequestsScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reactive list — driven by RequestCache + a one-shot fetch on mount
    // (also re-fetched on every ON_RESUME so we pick up admin status
    // changes without forcing an app restart).
    var allRequests by remember {
        mutableStateOf(
            RequestHiddenStore.filterVisible(ctx, RequestCache.all())
        )
    }
    var loading by remember { mutableStateOf(allRequests.isEmpty()) }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(refreshTick) {
        if (UserContactStore.get(ctx) == null) {
            loading = false; return@LaunchedEffect
        }
        if (RequestCache.all().isEmpty() || RequestCache.ageMs() > 30_000) {
            loading = allRequests.isEmpty()
            val res = withContext(Dispatchers.IO) {
                ContentRequestApi.listRequests(ctx, limit = 60)
            }
            if (res is ContentRequestApi.ListResult.Success) {
                val visible = RequestHiddenStore.filterVisible(ctx, res.requests)
                RequestCache.put(visible)
                allRequests = visible
            }
            loading = false
        } else {
            allRequests = RequestHiddenStore.filterVisible(ctx, RequestCache.all())
            loading = false
        }
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshTick += 1
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // On every non-empty list update: (1) capture the "unseen" IDs
    // ONCE per visit BEFORE we mark anything read, so we can pin
    // those cards to the top of the rail. (2) Then mark the whole
    // visible list as seen so the top-nav pulse dot + per-row unread
    // badges clear. Order matters — read-before-write.
    var pinnedIds by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(allRequests) {
        if (allRequests.isEmpty()) return@LaunchedEffect
        if (pinnedIds == null) {
            pinnedIds = com.hushtv.tv.data.RequestSeenStore
                .filterUnseen(ctx, allRequests)
                .map { it.id }
                .toSet()
        }
        com.hushtv.tv.data.RequestSeenStore.markSeen(ctx, allRequests)
    }

    // Filtering by status group. Pinned (recently-updated-since-last-
    // visit) IDs always land first, preserving their update order.
    var filter by remember { mutableStateOf(Filter.ALL) }
    val filtered = remember(allRequests, filter, pinnedIds) {
        val byUpdate = allRequests
            .filter { filter.matches(it.status) }
            .sortedByDescending { it.updatedDate.ifBlank { it.createdDate } }
        val pinned = pinnedIds.orEmpty()
        if (pinned.isEmpty()) byUpdate
        else byUpdate.filter { pinned.contains(it.id) } +
             byUpdate.filterNot { pinned.contains(it.id) }
    }

    // Long-press → confirm dialog state.
    var pendingRemoval by remember { mutableStateOf<ContentRequestApi.Request?>(null) }
    var lastRemoved by remember { mutableStateOf<ContentRequestApi.Request?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    // Hero focus state — focused card drives the backdrop billboard.
    var focusedReq by remember(filtered) {
        mutableStateOf(filtered.firstOrNull())
    }

    val newRequestFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(filtered.size) {
        // Land focus on the first card if there are any; otherwise on
        // the New Request button so the user always has somewhere to go.
        if (filtered.isNotEmpty()) {
            runCatching { firstCardFocus.requestFocus() }
        } else {
            runCatching { newRequestFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
    ) {
        // ────────────────────────────────────────────────────────────────
        // 1. CINEMATIC HERO BACKDROP — driven by focused card's TMDB image
        // ────────────────────────────────────────────────────────────────
        HeroBillboard(focusedReq = focusedReq)

        // ────────────────────────────────────────────────────────────────
        // 2. FOREGROUND CONTENT — single column, horizontal card rail.
        //    Everything fits in one 1080p frame with no overlap:
        //      Back/Refresh → eyebrow → title → status → actions →
        //      filters → horizontal card rail (below the buttons).
        // ────────────────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 28.dp),
        ) {
            // Top row: back chip + REFRESH
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconChip(
                    icon = Icons.Default.ArrowBack,
                    label = "Back",
                    focusRequester = backFocus,
                    onClick = { nav.popBackStack() },
                )
                Spacer(Modifier.weight(1f))
                IconChip(
                    icon = Icons.Default.Refresh,
                    label = "Refresh",
                    onClick = { refreshTick += 1 },
                )
            }

            Spacer(Modifier.height(24.dp))

            // Eyebrow + title (single line, truncate if long)
            Text(
                "MY REQUESTS",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                focusedReq?.title?.ifBlank { "Request your missing content" }
                    ?: "Request your missing content",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 40.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.65f),
            )
            Spacer(Modifier.height(10.dp))
            FocusedRequestSummary(req = focusedReq, total = allRequests.size)

            Spacer(Modifier.height(18.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    icon = Icons.Default.Add,
                    label = "New request",
                    primary = true,
                    focusRequester = newRequestFocus,
                    onClick = { showSheet = true },
                )
                if (focusedReq != null) {
                    PillButton(
                        icon = Icons.Default.Inbox,
                        label = "Open details",
                        primary = false,
                        onClick = {
                            val r = focusedReq ?: return@PillButton
                            nav.navigate("requestdetail/$playlistId/${r.id}")
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            FilterChipRow(
                current = filter,
                counts = countsByFilter(allRequests),
                onSelect = { filter = it },
            )

            Spacer(Modifier.height(18.dp))

            // ── Horizontal card rail, BELOW all header content ──
            // Fixed card size (320×180 dp, 16:9) so items stay
            // proportioned and never collapse into skinny slivers
            // when the list only has one entry. LazyRow → D-pad
            // RIGHT scrolls through many cards without ever needing
            // a vertical grid.
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                when {
                    loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp)
                    }
                    filtered.isEmpty() && allRequests.isEmpty() -> EmptyStateInline(
                        onCreate = { showSheet = true },
                    )
                    filtered.isEmpty() -> Text(
                        "Nothing in this filter — try another tab.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                    )
                    else -> LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 40.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, r -> r.id }) { idx, r ->
                            BackdropPosterCard(
                                req = r,
                                focusMod = if (idx == 0)
                                    Modifier.focusRequester(firstCardFocus) else Modifier,
                                onFocus = { focusedReq = r },
                                onClick = {
                                    nav.navigate("requestdetail/$playlistId/${r.id}")
                                },
                                onLongPress = { pendingRemoval = r },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── New-request sheet (TMDB-aware) ──
    if (showSheet) {
        RequestContentSheet(
            playlistId = playlistId,
            onDismiss = {
                showSheet = false
                refreshTick += 1
            },
            onViewMyRequests = {
                showSheet = false
                refreshTick += 1
            },
            onAlreadyAvailable = { entry ->
                showSheet = false
                if (entry.kind == "series") {
                    nav.navigate(
                        "series/$playlistId/${entry.seriesId}/" +
                            android.net.Uri.encode(entry.title),
                    )
                } else {
                    nav.navigate(
                        "moviedetail/$playlistId/${entry.streamId}/" +
                            android.net.Uri.encode(entry.title),
                    )
                }
            },
        )
    }

    // ── Long-press remove confirmation ──
    pendingRemoval?.let { target ->
        RemoveRequestDialog(
            title = target.title,
            onDismiss = { pendingRemoval = null },
            onConfirm = {
                RequestHiddenStore.hide(ctx, target.id)
                allRequests = allRequests.filterNot { it.id == target.id }
                lastRemoved = target
                pendingRemoval = null
                if (focusedReq?.id == target.id) {
                    focusedReq = allRequests.firstOrNull()
                }
            },
        )
    }

    // ── UNDO snackbar after remove ──
    RemovedRequestToast(
        removed = lastRemoved?.let { com.hushtv.tv.ui.requests.RemovedRequestSnack(it.id, it.title) },
        onUndo = {
            val removed = lastRemoved ?: return@RemovedRequestToast
            RequestHiddenStore.unhide(ctx, removed.id)
            allRequests = (allRequests + removed)
                .distinctBy { it.id }
                .sortedByDescending { it.updatedDate.ifBlank { it.createdDate } }
            lastRemoved = null
        },
        onAutoDismiss = { lastRemoved = null },
    )
}

/* ───────────────────────── Hero billboard ───────────────────────── */

@Composable
private fun HeroBillboard(focusedReq: ContentRequestApi.Request?) {
    val ctx = LocalContext.current
    var meta by remember(focusedReq?.id) {
        mutableStateOf(
            focusedReq?.let {
                RequestMetaStore.get(ctx, it.id)
                    ?: RequestMetaStore.parseTag(it.additionalInfo)
            }
        )
    }
    LaunchedEffect(focusedReq?.id) {
        val r = focusedReq ?: return@LaunchedEffect
        if (meta == null || meta?.tmdbId != RequestMetaStore.get(ctx, r.id)?.tmdbId) {
            meta = RequestPosterResolver.resolveOrFetch(ctx, r) ?: meta
        }
    }
    val backdrop = meta?.backdropPath?.let { TmdbService.img(it, "original") }

    Box(Modifier.fillMaxSize()) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Multi-stop dim overlay so foreground text always passes contrast,
        // regardless of how busy the backdrop is.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Color(0xFF05080F),
                    0.55f to Color(0xCC05080F),
                    1.0f to Color(0x6605080F),
                ),
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x6605080F),
                    0.55f to Color(0xAA05080F),
                    1.0f to Color(0xFF05080F),
                ),
            )
        )
    }
}

@Composable
private fun FocusedRequestSummary(req: ContentRequestApi.Request?, total: Int) {
    if (req == null) {
        Text(
            if (total == 0)
                "Search returned nothing? Submit a request and we'll add it for you."
            else
                "Pick any card below to preview it here.",
            color = Color(0xFFCBD5E1),
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth(0.55f),
        )
        return
    }
    val (chipBg, chipFg) = statusChipColors(req.status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(chipBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                req.status.label.uppercase(),
                color = chipFg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            req.adminResponse?.take(120)?.ifBlank { null }
                ?: defaultStatusBlurb(req.status),
            color = Color(0xFFCBD5E1),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.55f),
        )
    }
}

private fun defaultStatusBlurb(s: ContentRequestApi.Status) = when (s) {
    ContentRequestApi.Status.PENDING -> "We've got it — admin will review shortly."
    ContentRequestApi.Status.IN_PROGRESS -> "Working on it. Hold tight."
    ContentRequestApi.Status.ALREADY_AVAILABLE -> "Already in our library — open it from the details page."
    ContentRequestApi.Status.ADDED -> "It's live! Open the details to start watching."
    ContentRequestApi.Status.NOT_FOUND -> "We couldn't find this title. Try resubmitting with more detail."
}

/* ───────────────────────── Filter chips ───────────────────────── */

private enum class Filter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    READY("Ready"),
    OTHER("Other");

    fun matches(status: ContentRequestApi.Status): Boolean = when (this) {
        ALL -> true
        PENDING -> status == ContentRequestApi.Status.PENDING
        IN_PROGRESS -> status == ContentRequestApi.Status.IN_PROGRESS
        READY -> status == ContentRequestApi.Status.ADDED ||
                 status == ContentRequestApi.Status.ALREADY_AVAILABLE
        OTHER -> status == ContentRequestApi.Status.NOT_FOUND
    }
}

private fun countsByFilter(all: List<ContentRequestApi.Request>) =
    Filter.values().associateWith { f -> all.count { f.matches(it.status) } }

@Composable
private fun FilterChipRow(
    current: Filter,
    counts: Map<Filter, Int>,
    onSelect: (Filter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Filter.values().forEach { f ->
            FilterChip(
                label = "${f.label} · ${counts[f] ?: 0}",
                selected = f == current,
                onClick = { onSelect(f) },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> Cyan
        focused -> Color(0xFF1E293B)
        else -> Color(0x66000000)
    }
    val fg = when {
        selected -> Color(0xFF05080F)
        else -> Color(0xFFE2E8F0)
    }
    val border = when {
        selected -> Cyan
        focused -> Cyan.copy(alpha = 0.7f)
        else -> Color(0x33FFFFFF)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(999.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

/* ───────────────────────── Pill / Icon buttons ───────────────────────── */

@Composable
private fun PillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(120))
    val baseBg = if (primary) Cyan else Color(0xFF111827)
    val bg = if (focused && primary) Color(0xFF22D3EE) else baseBg
    val fg = if (primary) Color(0xFF05080F) else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(999.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .let { m ->
                if (focusRequester != null) m.focusRequester(focusRequester) else m
            }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (focused) Color(0xFF1E293B) else Color(0x66000000))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(999.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .let { m ->
                if (focusRequester != null) m.focusRequester(focusRequester) else m
            }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ───────────────────────── Poster card ───────────────────────── */

@Composable
private fun BackdropPosterCard(
    req: ContentRequestApi.Request,
    focusMod: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val (chipBg, chipFg) = statusChipColors(req.status)
    val statusGlow = when (req.status) {
        ContentRequestApi.Status.PENDING -> Color(0xFFF59E0B)
        ContentRequestApi.Status.IN_PROGRESS -> Color(0xFF60A5FA)
        ContentRequestApi.Status.ALREADY_AVAILABLE -> Color(0xFFC084FC)
        ContentRequestApi.Status.ADDED -> Color(0xFF22C55E)
        ContentRequestApi.Status.NOT_FOUND -> Color(0xFFF87171)
    }

    var meta by remember(req.id) {
        mutableStateOf(
            RequestMetaStore.get(ctx, req.id) ?: RequestMetaStore.parseTag(req.additionalInfo)
        )
    }
    LaunchedEffect(req.id) {
        if (meta == null) meta = RequestPosterResolver.resolveOrFetch(ctx, req)
        // Enrich with imdbId so RPDB's rating-baked backdrop can
        // take over from the plain TMDB backdrop. One-shot per
        // request id thanks to the resolver's mutex de-dupe.
        val enriched = RequestPosterResolver.ensureImdbId(ctx, req)
        if (enriched != null && enriched.imdbId != meta?.imdbId) {
            meta = enriched
        }
    }

    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(120))

    Box(
        focusMod
            .width(320.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Cyan else Color(0x22FFFFFF),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnterAndLongPress(onClick = onClick, onLongPress = onLongPress),
    ) {
        // Backdrop image — prefer RPDB's rating-baked variant (IMDb,
        // RT, Metacritic, TMDB scores embedded in the artwork itself)
        // when we have an imdb_id; fall back to plain TMDB backdrop;
        // fall back to status-tinted gradient. Coil's onError flips
        // us to TMDB if RPDB 404s for a particular title.
        val tmdbBackdrop = meta?.backdropPath?.let { TmdbService.img(it, "w780") }
            ?: meta?.posterPath?.let { TmdbService.img(it, "w780") }
        val rpdbBackdrop = com.hushtv.tv.data.RpdbService.backgroundUrl(meta?.imdbId)
        var useTmdbBackdrop by remember(meta?.imdbId, meta?.backdropPath) {
            mutableStateOf(rpdbBackdrop.isNullOrBlank())
        }
        val chosen = if (useTmdbBackdrop || rpdbBackdrop.isNullOrBlank()) tmdbBackdrop
                     else rpdbBackdrop
        if (chosen != null) {
            AsyncImage(
                model = chosen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { useTmdbBackdrop = true },
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to statusGlow.copy(alpha = 0.4f),
                        1.0f to Color(0xFF0F172A),
                    )
                )
            )
        }
        // Bottom gradient for text legibility.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.45f to Color(0x4D000000),
                    1.0f to Color(0xE6000000),
                )
            )
        )
        // Status chip top-right.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(CircleShape)
                .background(chipBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                req.status.label.uppercase(),
                color = chipFg,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
        }
        // Title bottom-left.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .fillMaxWidth(),
        ) {
            Text(
                req.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${req.type.uppercase()}  ·  ${shortDate(req.createdDate)}",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
    }
}

private fun statusChipColors(s: ContentRequestApi.Status): Pair<Color, Color> = when (s) {
    ContentRequestApi.Status.PENDING -> Color(0xFFF59E0B) to Color(0xFF05080F)
    ContentRequestApi.Status.IN_PROGRESS -> Color(0xFF3B82F6) to Color.White
    ContentRequestApi.Status.ALREADY_AVAILABLE -> Color(0xFFA855F7) to Color.White
    ContentRequestApi.Status.ADDED -> Color(0xFF22C55E) to Color.White
    ContentRequestApi.Status.NOT_FOUND -> Color(0xFFEF4444) to Color.White
}

private fun shortDate(iso: String): String {
    if (iso.isBlank()) return "—"
    return runCatching {
        // ISO-8601 like "2026-04-26T00:43:16Z" → "Apr 26"
        val date = iso.substring(0, 10) // yyyy-MM-dd
        val parts = date.split("-")
        val month = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )[parts[1].toInt() - 1]
        "$month ${parts[2].toInt()}"
    }.getOrDefault("—")
}

/* ───────────────────────── Empty state ───────────────────────── */

@Composable
private fun EmptyStateInline(onCreate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Inbox, null,
                tint = Cyan,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "No requests yet",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "When a movie or series isn't in our library, hit the + New Request button above to ask for it.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
            )
        }
    }
}
