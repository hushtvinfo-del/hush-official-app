package com.hushtv.tv.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.EpgProgram
import com.hushtv.tv.data.EpgService
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamCategory
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mobile Live TV hub — the "always-on preview" experience.
 *
 *   [  Category pill    42 channels  ]
 *   [      16:9 preview player        ]  ← tap to fullscreen
 *   [  ● LIVE   A&E Canada · 42/318  ]
 *   [  NOW  Court Cam                 ]
 *   [       11:30 AM – 12:00 PM · 12 min left ]
 *   [  NEXT 12:00 PM  Court Cam       ]
 *   [  ─────────────────────────────  ]
 *   [  Channel rows (tap = preview)   ]
 *
 * Single tap on a channel row → updates the preview without leaving.
 * Single tap on the preview player → open full-screen player.
 *
 * EPG: fetched on-demand when a channel is selected; cached by
 * EpgService so switching back is instant.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MobileLiveHubScreen(
    nav: NavController,
    playlistId: String,
) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    // ── State ──
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCatId by rememberSaveable(key = "mlive-cat") { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCatPicker by remember { mutableStateOf(false) }
    var selectedStreamId by rememberSaveable(key = "mlive-sid") { mutableStateOf(-1) }
    var epgVersion by remember { mutableStateOf(0) }   // force recompose after EPG fetch

    // ── Load categories once ──
    LaunchedEffect(playlistId) {
        if (playlist == null) { loading = false; return@LaunchedEffect }
        val cats = runCatching {
            withContext(Dispatchers.IO) {
                XtreamApi.getCategories(playlist.host, playlist.username, playlist.password, "live")
            }
        }.getOrDefault(emptyList())
        categories = cats
    }

    // ── Load channels when category changes ──
    LaunchedEffect(selectedCatId, playlistId) {
        if (playlist == null) { loading = false; return@LaunchedEffect }
        loading = true
        val data = runCatching {
            withContext(Dispatchers.IO) {
                if (selectedCatId.isBlank()) {
                    XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "live")
                } else {
                    XtreamApi.getStreamsForCategory(playlist.host, playlist.username, playlist.password, "live", selectedCatId)
                }
            }
        }.getOrDefault(emptyList())
        channels = data
        loading = false
        // Seed the preview on first load or after category switch.
        if (selectedStreamId < 0 || channels.none { it.streamId == selectedStreamId }) {
            selectedStreamId = channels.firstOrNull()?.streamId ?: -1
        }
    }

    // ── Fetch EPG for the currently-previewed channel ──
    LaunchedEffect(selectedStreamId) {
        if (playlist == null || selectedStreamId < 0) return@LaunchedEffect
        runCatching {
            EpgService.fetchShortEpg(playlist.host, playlist.username, playlist.password, selectedStreamId)
        }
        epgVersion++    // refresh the NOW / UP NEXT panel
    }

    // ── ExoPlayer for the preview pane ──
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Swap media item whenever the channel selection changes.
    val currentChannel = channels.firstOrNull { it.streamId == selectedStreamId }
    LaunchedEffect(selectedStreamId) {
        val card = channels.firstOrNull { it.streamId == selectedStreamId } ?: return@LaunchedEffect
        val p = playlist ?: return@LaunchedEffect
        val url = XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    val selectedCatName = remember(selectedCatId, categories) {
        if (selectedCatId.isBlank()) "All channels"
        else categories.firstOrNull { it.category_id == selectedCatId }?.category_name ?: "All"
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        // ── Header (title + category pill) ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Live TV",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Cyan.copy(alpha = 0.15f))
                    .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable { showCatPicker = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedCatName.uppercase(),
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ExpandMore, null, tint = Cyan, modifier = Modifier.size(14.dp))
            }
        }

        // ── Scrollable content: preview + metadata + channel list ──
        LazyColumn(
            Modifier.fillMaxSize(),
        ) {
            // Preview card (player)
            item("preview") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .clickable {
                            // Full-screen the selected channel. We pause the
                            // in-line preview first so we don't double-play.
                            val card = currentChannel ?: return@clickable
                            val p = playlist ?: return@clickable
                            player.pause()
                            val url = XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
                            nav.navigate(
                                mobilePlayerRoute(
                                    playlistId = playlistId,
                                    streamUrl = url,
                                    channelName = card.title,
                                    isLive = true,
                                    liveCategoryId = selectedCatId.ifBlank { null },
                                ),
                            )
                        },
                ) {
                    if (selectedStreamId < 0) {
                        // Nothing selected yet — show an empty cyan-tinted slate.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(
                                    listOf(Color(0xFF0E1A2E), Color(0xFF05080F))
                                )),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Pick a channel to preview",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        AndroidView(
                            factory = {
                                PlayerView(it).apply {
                                    useController = false
                                    this.player = player
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { pv -> pv.player = player },
                        )
                        // Fullscreen affordance pill.
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xAA000000))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Fullscreen, null, tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Tap to expand",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Now / Next metadata
            if (currentChannel != null) {
                item("meta") {
                    val idx = channels.indexOf(currentChannel)
                    NowNextPanel(
                        channel = currentChannel,
                        positionLabel = if (idx >= 0) "${idx + 1}/${channels.size}" else null,
                        epgVersion = epgVersion,
                    )
                }
            }

            // Divider + "Channels" kicker
            item("kicker") {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
                    Text(
                        "CHANNELS · ${channels.size}",
                        color = Cyan,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            if (loading) {
                item("loader") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan)
                    }
                }
            } else if (channels.isEmpty()) {
                item("empty") {
                    Text(
                        "No channels in this category.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                items(channels, key = { "ch-${it.id}" }) { card ->
                    MobileLiveHubRow(
                        card = card,
                        selected = card.streamId == selectedStreamId,
                        epgVersion = epgVersion,
                        onClick = { selectedStreamId = card.streamId },
                    )
                }
                item("pad") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showCatPicker) {
        MobileCategoryPickerSheet(
            categories = categories,
            selectedId = selectedCatId,
            onPick = {
                selectedCatId = it
                showCatPicker = false
            },
            onDismiss = { showCatPicker = false },
        )
    }

    // Pause preview when leaving the screen to save battery.
    DisposableEffect(Unit) { onDispose { player.pause() } }
}

@Composable
private fun NowNextPanel(
    channel: MediaCard,
    positionLabel: String?,
    epgVersion: Int,
) {
    // Re-read EPG every time the version counter bumps (i.e. after a
    // fresh fetch completes). The EpgService itself is in-memory so
    // this is just a cache lookup.
    @Suppress("UNUSED_EXPRESSION") epgVersion
    val now = EpgService.nowPlaying(channel.streamId)
    val next = EpgService.nextUp(channel.streamId)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Channel title row with LIVE pip + position indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(Color(0xFFEF4444), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "LIVE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                channel.title,
                color = Cyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (positionLabel != null) {
                Text(
                    positionLabel,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (now != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                now.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatClock(now.startMs)} – ${formatClock(now.stopMs)}  ·  ${now.minutesLeft.coerceAtLeast(0)} min left",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (now.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    now.description,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Progress bar.
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(2.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(now.progressPct)
                        .height(3.dp)
                        .background(Cyan, RoundedCornerShape(2.dp))
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "No program information for this channel.",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
            )
        }

        if (next != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(Color(0xFFFACC15), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "UP NEXT", color = Color(0xFF111111),
                        fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    formatClock(next.startMs),
                    color = Color(0xFFFACC15),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    next.title,
                    color = Color(0xFFB3B8C1),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MobileLiveHubRow(
    card: MediaCard,
    selected: Boolean,
    epgVersion: Int,
    onClick: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") epgVersion
    val now = EpgService.nowPlaying(card.streamId)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Cyan.copy(alpha = 0.16f) else Color(0xFF0A1220))
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Cyan else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            if (!card.poster.isNullOrBlank()) {
                AsyncImage(
                    model = card.poster,
                    contentDescription = card.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Text(
                    card.title.take(2).uppercase(),
                    color = Color(0xFF64748B),
                    fontSize = 14.sp, fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.title,
                color = if (selected) Cyan else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (now != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    now.title,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Cyan),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow, null,
                    tint = Color(0xFF05080F),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MobileCategoryPickerSheet(
    categories: List<XtreamCategory>,
    selectedId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B1220))
                .padding(vertical = 12.dp),
        ) {
            Text(
                "Pick a category",
                color = Cyan,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    CategorySheetRow("", "All channels", selectedId == "", onPick)
                }
                items(categories, key = { it.category_id }) { cat ->
                    CategorySheetRow(cat.category_id, cat.category_name, selectedId == cat.category_id, onPick)
                }
            }
        }
    }
}

@Composable
private fun CategorySheetRow(
    id: String,
    name: String,
    selected: Boolean,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Cyan.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onPick(id) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            color = if (selected) Cyan else Color(0xFFE5E7EB),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun formatClock(ms: Long): String {
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
}
