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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.RequestCache
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.UserContactStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Compact "MY REQUESTS" rail rendered at the top of the Home screen
 * on both Mobile and TV. Shows up to 3 most-recent open or recently-
 * updated requests as horizontally-scrollable cards. Hidden entirely
 * when:
 *   • the user has never submitted a request,
 *   • all requests are closed (added / available / not_found) AND
 *     have already been acknowledged on the detail screen.
 *
 * Tapping a card calls [onOpen] with the picked request — caller
 * navigates to the appropriate detail route.
 *
 * Tapping the "View all" pill calls [onViewAll].
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
        // If we already have a fresh-ish cached snapshot, skip the
        // network call entirely. RequestNotificationHost or
        // MyRequestsList may have already populated the cache.
        if (RequestCache.all().isNotEmpty() && RequestCache.ageMs() < 60_000) {
            requests = RequestCache.all()
            return@LaunchedEffect
        }
        delay(800) // give the heavier home loaders a head start
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
    val gap = if (isTv) 14.dp else 10.dp
    val titleSize = if (isTv) 13.sp else 11.sp

    Column(Modifier.fillMaxWidth().padding(top = if (isTv) 12.dp else 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPad, vertical = 6.dp),
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

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = outerPad,
            ),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(
                items = highlight,
                key = { it.id },
            ) { req ->
                RequestSummaryCard(
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

@Composable
private fun RequestSummaryCard(
    req: ContentRequestApi.Request,
    unseen: Boolean,
    onClick: () -> Unit,
    isTv: Boolean,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val cardWidth = if (isTv) 280.dp else 230.dp
    val (badgeBg, badgeFg) = when (req.status) {
        ContentRequestApi.Status.PENDING -> Color(0x33F59E0B) to Color(0xFFF59E0B)
        ContentRequestApi.Status.IN_PROGRESS -> Color(0x333B82F6) to Color(0xFF60A5FA)
        ContentRequestApi.Status.ALREADY_AVAILABLE -> Color(0x33A855F7) to Color(0xFFC084FC)
        ContentRequestApi.Status.ADDED -> Color(0x3322C55E) to Color(0xFF34D399)
        ContentRequestApi.Status.NOT_FOUND -> Color(0x33EF4444) to Color(0xFFF87171)
    }
    val meta = remember(req.id, req.additionalInfo) {
        com.hushtv.tv.data.RequestMetaStore.get(ctx, req.id)
            ?: com.hushtv.tv.data.RequestMetaStore.parseTag(req.additionalInfo)
    }
    androidx.compose.foundation.layout.Row(
        Modifier
            .width(cardWidth)
            .background(SurfaceNavy, RoundedCornerShape(14.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    unseen -> Cyan.copy(alpha = 0.5f)
                    else -> Color(0x22FFFFFF)
                },
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Poster — w185 is plenty for a 50x75 thumbnail
        Box(
            Modifier
                .size(width = 50.dp, height = 75.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center,
        ) {
            val url = meta?.posterPath?.let {
                com.hushtv.tv.data.TmdbService.img(it, "w185")
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
                    fontSize = 22.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(badgeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        "${req.status.emoji} ${req.status.label}",
                        color = badgeFg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (unseen) {
                    Box(Modifier.size(8.dp).background(Cyan, CircleShape))
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                req.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta?.releaseYear != null) {
                Spacer(Modifier.size(2.dp))
                Text(
                    meta.releaseYear.toString(),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
