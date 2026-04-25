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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.RequestCache
import com.hushtv.tv.data.RequestMetaStore
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.UserContactStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Compact "MY REQUESTS" rail rendered below the pager on the Mobile
 * Home screen and below the top nav on TV. Shows up to 6 most-recent
 * open or recently-updated requests as horizontally-scrollable
 * cinematic cards (TMDB backdrop + status badge + title overlay).
 *
 * Hidden entirely when:
 *   • the user has never submitted a request,
 *   • all requests are closed AND already acknowledged.
 *
 * Tapping a card calls [onOpen] with the picked request — caller
 * navigates to the appropriate detail route.
 *
 * Network: lazy on first composition, then cache-only. Doesn't
 * compete with the home screen's heavier loads (CW, posters, etc.).
 */
@Composable
fun RequestsHomeRail(
    onOpen: (ContentRequestApi.Request) -> Unit,
    onViewAll: () -> Unit,
    /** When true, uses TV-friendly larger spacing + focus visuals. */
    isTv: Boolean = false,
) {
    val ctx = LocalContext.current
    var requests by remember { mutableStateOf(RequestCache.all()) }

    LaunchedEffect(Unit) {
        if (UserContactStore.get(ctx) == null) return@LaunchedEffect
        if (RequestCache.all().isNotEmpty() && RequestCache.ageMs() < 60_000) {
            requests = RequestCache.all()
            return@LaunchedEffect
        }
        delay(800)
        runCatching {
            val res = withContext(Dispatchers.IO) {
                ContentRequestApi.listRequests(ctx, limit = 20)
            }
            if (res is ContentRequestApi.ListResult.Success) {
                RequestCache.put(res.requests)
                requests = res.requests
            }
        }
    }

    val highlight = filterForHomeRail(ctx, requests)
    if (highlight.isEmpty()) return

    val outerPad = if (isTv) 64.dp else 16.dp
    val gap = if (isTv) 16.dp else 12.dp
    val titleSize = if (isTv) 13.sp else 11.sp

    // Top divider + breathing room so the rail visually separates
    // from whatever sits above it (pager dots on mobile, top nav on
    // TV). Bottom padding keeps cards off the system nav bar.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (isTv) 12.dp else 14.dp, bottom = if (isTv) 12.dp else 18.dp),
    ) {
        // ── Hairline divider, full-width ──
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x14FFFFFF)),
        )
        Spacer(Modifier.height(if (isTv) 14.dp else 12.dp))

        // ── Section header ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MY REQUESTS",
                color = Cyan,
                fontSize = titleSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(Cyan.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "${highlight.size}",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.weight(1f))
            ViewAllChip(onViewAll, isTv = isTv)
        }

        Spacer(Modifier.height(10.dp))

        // ── Cards ──
        LazyRow(
            contentPadding = PaddingValues(horizontal = outerPad),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(items = highlight, key = { it.id }) { req ->
                BackdropRequestCard(
                    req = req,
                    unseen = RequestSeenStore.isUnseen(ctx, req),
                    onClick = { onOpen(req) },
                    isTv = isTv,
                )
            }
        }
    }
}

/**
 * Returns the subset of [requests] that should appear in the home
 * rail: every open request (pending / in_progress) PLUS any closed
 * request the user has not yet acknowledged on the detail screen.
 *
 * Capped at 6 rows so the horizontal rail stays browseable.
 */
private fun filterForHomeRail(
    ctx: android.content.Context,
    requests: List<ContentRequestApi.Request>,
): List<ContentRequestApi.Request> {
    if (requests.isEmpty()) return emptyList()
    return requests
        .filter { r ->
            val open = r.status == ContentRequestApi.Status.PENDING ||
                r.status == ContentRequestApi.Status.IN_PROGRESS
            open || RequestSeenStore.isUnseen(ctx, r)
        }
        .sortedByDescending { it.updatedDate.ifBlank { it.createdDate } }
        .take(6)
}

@Composable
private fun ViewAllChip(onClick: () -> Unit, isTv: Boolean) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .background(
                if (focused) Cyan else Color(0x14FFFFFF),
                RoundedCornerShape(14.dp),
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = if (isTv) 14.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "View all",
            color = if (focused) Color(0xFF05080F) else TextPrimary,
            fontSize = if (isTv) 12.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

/**
 * Cinematic card — uses TMDB backdrop as the full background, with a
 * dark gradient overlay so the title reads white on top. Status badge
 * sits in the top-left corner; un-seen indicator dot top-right.
 *
 * When backdrop is unavailable (provider didn't echo TMDB metadata
 * AND the picker submitted free-text) we fall back to a tinted
 * status-tone gradient so the card still looks designed instead of
 * empty.
 */
@Composable
private fun BackdropRequestCard(
    req: ContentRequestApi.Request,
    unseen: Boolean,
    onClick: () -> Unit,
    isTv: Boolean,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val cardWidth = if (isTv) 320.dp else 270.dp
    // 16:9 — same ratio TMDB uses for backdrops, looks cinematic.
    val cardHeight = cardWidth * 9f / 16f

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
    val meta = remember(req.id, req.additionalInfo) {
        RequestMetaStore.get(ctx, req.id) ?: RequestMetaStore.parseTag(req.additionalInfo)
    }

    Box(
        Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    unseen -> glow.copy(alpha = 0.6f)
                    else -> Color(0x22FFFFFF)
                },
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        // ── Backdrop (or status-tinted fallback) ─────────────────────
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
                // Some titles have no backdrop on TMDB — use the
                // poster cropped to fill so the card still looks
                // built rather than empty.
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
                            listOf(glow.copy(alpha = 0.35f), Color(0xFF05080F))
                        )
                    ),
            )
        }

        // ── Bottom-up gradient so text always has contrast ──────────
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC000000),
                        0.35f to Color(0x44000000),
                        0.65f to Color(0xAA000000),
                        1f to Color(0xF005080F),
                    )
                ),
        )

        // ── Top row: status badge (left) + unseen dot (right) ──────
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (unseen) {
                // Cyan pulse-style dot signals an unread admin
                // update that the user hasn't seen yet.
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Cyan, CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                )
            }
        }

        // ── Bottom: title + year, white-on-gradient ────────────────
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                req.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta?.releaseYear != null || req.type == "series") {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(if (req.type == "series") "Series" else "Movie")
                        if (meta?.releaseYear != null) {
                            append(" · ")
                            append(meta.releaseYear)
                        }
                    },
                    color = Color(0xCCFFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
