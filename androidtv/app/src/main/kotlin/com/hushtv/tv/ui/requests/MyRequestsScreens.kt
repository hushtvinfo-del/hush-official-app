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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.RequestCache
import com.hushtv.tv.data.RequestSeenStore
import com.hushtv.tv.data.UserContactStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only list of the user's submitted content requests with live
 * status badges + per-row "NEW" pills for any update the user hasn't
 * acknowledged yet. Used by both TV and Mobile via thin wrappers
 * that provide a back chip / app bar.
 *
 * Tapping a row navigates to the per-request detail screen via
 * [onOpen]. Caller owns the route and the playlistId.
 *
 * Empty state, network error, and missing-contact (user never
 * submitted a request before) are all handled inline.
 */
@Composable
fun MyRequestsList(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onOpen: (ContentRequestApi.Request) -> Unit,
    onRefresh: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var requests by remember { mutableStateOf<List<ContentRequestApi.Request>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        if (UserContactStore.get(ctx) == null) {
            loading = false
            error = "You haven't submitted any requests yet."
            return@LaunchedEffect
        }
        loading = true
        val res = withContext(Dispatchers.IO) {
            ContentRequestApi.listRequests(ctx, limit = 30)
        }
        loading = false
        when (res) {
            is ContentRequestApi.ListResult.Success -> {
                requests = res.requests
                RequestCache.put(res.requests)
                error = if (res.requests.isEmpty()) "No requests yet." else null
            }
            is ContentRequestApi.ListResult.Error -> error = res.message
        }
    }

    Column(modifier.fillMaxSize().background(BgBlack)) {
        if (showHeader) {
            Text(
                "My Requests",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan)
            }
            error != null -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    error ?: "",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 24.dp, vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("stats") { RequestsStatsHeader(requests) }
                items(requests, key = { it.id }) { r ->
                    val unseen = RequestSeenStore.isUnseen(ctx, r)
                    RequestRow(
                        r = r,
                        unseen = unseen,
                        onClick = {
                            RequestSeenStore.markSeen(ctx, r)
                            onOpen(r)
                        },
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

/** TV variant: full-screen with back chip + Refresh button. */
@Composable
fun TVMyRequestsScreen(nav: NavController, playlistId: String) {
    var refreshKey by remember { mutableStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(BgBlack)
            .padding(horizontal = 64.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChip(onClick = { nav.popBackStack() })
            Spacer(Modifier.width(20.dp))
            Text("My Requests", color = TextPrimary, fontSize = 28.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            RefreshChip(onClick = { refreshKey += 1 })
        }
        Spacer(Modifier.height(20.dp))
        // Re-mount on refresh by keying on refreshKey
        key(refreshKey) {
            MyRequestsList(
                showHeader = false,
                onOpen = { req ->
                    nav.navigate("requestdetail/$playlistId/${req.id}")
                },
            )
        }
    }
}

/** Mobile variant: app bar + back chip; uses status-bar inset. */
@Composable
fun MobileMyRequestsScreen(nav: NavController, playlistId: String) {
    var refreshKey by remember { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .androidx_status_bar_inset(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(18.dp))
                    .clickableWithEnter { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("My Requests", color = TextPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(1.dp).weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(18.dp))
                    .clickableWithEnter { refreshKey += 1 },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Refresh, null, tint = Color.White,
                    modifier = Modifier.size(18.dp))
            }
        }
        key(refreshKey) {
            MyRequestsList(
                showHeader = false,
                onOpen = { req ->
                    nav.navigate("mrequestdetail/$playlistId/${req.id}")
                },
            )
        }
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
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

@Composable
private fun RefreshChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Refresh, null, tint = Color.White,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Refresh", color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RequestsStatsHeader(requests: List<ContentRequestApi.Request>) {
    if (requests.isEmpty()) return
    val total = requests.size
    val open = requests.count {
        it.status == ContentRequestApi.Status.PENDING ||
            it.status == ContentRequestApi.Status.IN_PROGRESS
    }
    val available = requests.count {
        it.status == ContentRequestApi.Status.ADDED ||
            it.status == ContentRequestApi.Status.ALREADY_AVAILABLE
    }
    val notFound = requests.count { it.status == ContentRequestApi.Status.NOT_FOUND }
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(label = "TOTAL", value = total.toString(), color = TextPrimary)
        StatCell(label = "OPEN", value = open.toString(), color = Color(0xFFF59E0B))
        StatCell(label = "AVAILABLE", value = available.toString(), color = Color(0xFF34D399))
        if (notFound > 0) {
            StatCell(label = "CLOSED", value = notFound.toString(), color = Color(0xFFF87171))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color) {
    Column {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(label, color = TextSecondary, fontSize = 9.sp,
            fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun RequestRow(
    r: ContentRequestApi.Request,
    unseen: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(14.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    unseen -> Cyan.copy(alpha = 0.4f)
                    else -> Color(0x22FFFFFF)
                },
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (r.type == "series") "📺" else "🎬",
                fontSize = 18.sp,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = if (unseen) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unseen) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .background(Cyan, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "NEW",
                                color = Color(0xFF05080F),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            StatusBadge(r.status)
        }
        if (r.type == "series" && (!r.seasons.isNullOrBlank() || !r.episodes.isNullOrBlank())) {
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    if (!r.seasons.isNullOrBlank()) {
                        append(r.seasons)
                    }
                    if (!r.episodes.isNullOrBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(r.episodes)
                    }
                },
                color = TextSecondary, fontSize = 12.sp,
            )
        }
        if (!r.adminResponse.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Cyan.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Column {
                    Text("Admin response", color = Cyan, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(r.adminResponse, color = TextPrimary, fontSize = 13.sp,
                        lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ContentRequestApi.Status) {
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            "${status.emoji} ${status.label}",
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

// Tiny helper so MobileMyRequestsScreen can apply status-bar inset
// without repeating the Compose insets boilerplate at every call site.
@Composable
private fun Modifier.androidx_status_bar_inset(): Modifier =
    this.then(windowInsetsPadding(WindowInsets.statusBars))
