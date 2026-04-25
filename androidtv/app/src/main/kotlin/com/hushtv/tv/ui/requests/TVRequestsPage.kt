package com.hushtv.tv.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.RequestMetaStore
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.ui.screens.clickableWithEnterAndLongPress
import com.hushtv.tv.ui.theme.Cyan

/**
 * TV Home "REQUESTS" page — full-screen page in the home pager,
 * sibling to the Continue Watching / Discover / Movies / etc. pages.
 *
 * Layout mirrors `DiscoveryPage`: a full-bleed hero backdrop driven
 * by the currently-focused request's TMDB backdrop, with a 16:9
 * cinematic card row pinned at the bottom. D-pad-friendly, with
 * up/down delegating to the host so users can page through.
 *
 * Long-press on any card opens the same `RemoveRequestDialog` users
 * see on Mobile.
 */
@Composable
fun TVRequestsPage(
    playlistId: String,
    nav: NavController,
    requests: List<ContentRequestApi.Request>,
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: () -> Unit,
    onRequestHidden: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var focusedReq by remember(requests) {
        mutableStateOf(requests.firstOrNull())
    }
    var hideTarget by remember { mutableStateOf<ContentRequestApi.Request?>(null) }
    var snack by remember { mutableStateOf<RemovedRequestSnack?>(null) }

    Box(Modifier.fillMaxSize()) {
        // Hero backdrop driven by the focused card's TMDB backdrop —
        // same visual rhythm as Continue Watching / Discovery pages.
        TVRequestsHeroBackdrop(req = focusedReq)

        // Bottom-anchored row with a section label above it.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 32.dp),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "MY REQUESTS",
                        color = Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .background(Cyan.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${requests.size}",
                            color = Cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "HOLD OK TO REMOVE",
                        color = Color(0x99FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                }
                TVRequestsRow(
                    requests = requests,
                    firstItemFocus = firstItemFocus,
                    onFocusedReqChange = { focusedReq = it },
                    onCardClick = { req ->
                        nav.navigate("requestdetail/$playlistId/${req.id}")
                    },
                    onLongPress = { hideTarget = it },
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

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

    // Snackbar overlay — top of screen, auto-dismisses in ~3.5 s.
    // Undo restores the request and bumps the parent so the row
    // reappears immediately.
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

@Composable
private fun TVRequestsHeroBackdrop(req: ContentRequestApi.Request?) {
    val ctx = LocalContext.current
    var meta by remember(req?.id) {
        mutableStateOf(
            req?.let {
                RequestMetaStore.get(ctx, it.id) ?: RequestMetaStore.parseTag(it.additionalInfo)
            }
        )
    }
    LaunchedEffect(req?.id) {
        if (req != null && meta == null) {
            meta = RequestPosterResolver.resolveOrFetch(ctx, req)
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        val backdrop = meta?.backdropPath?.let { TmdbService.img(it, "original") }
        val poster = meta?.posterPath?.let { TmdbService.img(it, "w780") }
        when {
            !backdrop.isNullOrBlank() -> AsyncImage(
                model = backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            !poster.isNullOrBlank() -> AsyncImage(
                model = poster,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // Vertical gradient so the bottom row is always readable
        // against any backdrop. Same treatment Continue Watching uses.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x6605080F),
                        0.5f to Color(0xAA05080F),
                        1f to Color(0xF005080F),
                    )
                ),
        )
    }
}

@Composable
private fun TVRequestsRow(
    requests: List<ContentRequestApi.Request>,
    firstItemFocus: FocusRequester,
    onFocusedReqChange: (ContentRequestApi.Request) -> Unit,
    onCardClick: (ContentRequestApi.Request) -> Unit,
    onLongPress: (ContentRequestApi.Request) -> Unit,
    onUpFromRow: () -> Unit,
    onDownFromRow: () -> Unit,
) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        contentPadding = androidx.compose.foundation.layout
            .PaddingValues(horizontal = 16.dp),
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
        items(
            items = requests,
            key = { it.id },
        ) { req ->
            TVRequestHeroCard(
                req = req,
                first = req === requests.firstOrNull(),
                firstItemFocus = firstItemFocus,
                unseen = RequestSeenStore.isUnseen(LocalContext.current, req),
                onFocused = { onFocusedReqChange(req) },
                onClick = { onCardClick(req) },
                onLongPress = { onLongPress(req) },
            )
        }
    }
}

@Composable
private fun TVRequestHeroCard(
    req: ContentRequestApi.Request,
    first: Boolean,
    firstItemFocus: FocusRequester,
    unseen: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val cardWidth = 320.dp
    val cardHeight = 180.dp

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
        if (meta == null) {
            meta = RequestPosterResolver.resolveOrFetch(ctx, req)
        }
    }

    Box(
        Modifier
            .let {
                if (first) it.focusRequester(firstItemFocus) else it
            }
            .size(width = cardWidth, height = cardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    unseen -> glow.copy(alpha = 0.6f)
                    else -> Color(0x22FFFFFF)
                },
                shape = RoundedCornerShape(16.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickableWithEnterAndLongPress(
                onClick = onClick,
                onLongPress = onLongPress,
            ),
    ) {
        val backdropUrl = meta?.backdropPath?.let { TmdbService.img(it, "w780") }
        val posterUrl = meta?.posterPath?.let { TmdbService.img(it, "w500") }
        when {
            !backdropUrl.isNullOrBlank() -> AsyncImage(
                model = backdropUrl,
                contentDescription = req.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            !posterUrl.isNullOrBlank() -> AsyncImage(
                model = posterUrl,
                contentDescription = req.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(glow.copy(alpha = 0.35f), Color(0xFF05080F)),
                        )
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC000000),
                        0.4f to Color(0x44000000),
                        0.7f to Color(0xAA000000),
                        1f to Color(0xF005080F),
                    )
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .background(badgeBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "${req.status.emoji} ${req.status.label}",
                    color = badgeFg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (unseen) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Cyan, CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                req.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val year = meta?.releaseYear
            if (year != null || req.type == "series") {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(if (req.type == "series") "Series" else "Movie")
                        if (year != null) {
                            append(" · ")
                            append(year)
                        }
                    },
                    color = Color(0xCCFFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
