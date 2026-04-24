package com.hushtv.tv.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.hushtv.tv.data.FavoritesStore
import com.hushtv.tv.data.LiveSessionStore
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RecentChannelStore
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
    // Category + previewed channel persist across screens (and app
    // restarts) via LiveSessionStore — user's expectation is that
    // leaving Live TV and coming back resumes EXACTLY where they
    // left off, not a fresh "All channels + first channel" state.
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCatId by remember(playlistId) {
        mutableStateOf(LiveSessionStore.getCategoryId(ctx, playlistId))
    }
    var channels by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCatPicker by remember { mutableStateOf(false) }
    var selectedStreamId by remember(playlistId) {
        mutableStateOf(LiveSessionStore.getStreamId(ctx, playlistId))
    }
    var epgVersion by remember { mutableStateOf(0) }   // force recompose after EPG fetch
    var favVersion by remember { mutableStateOf(0) }   // force recompose after favorites toggle
    var recentVersion by remember { mutableStateOf(0) } // force recompose after recent update
    // Long-press quick-action sheet. Holds the target card or null.
    var actionCard by remember { mutableStateOf<MediaCard?>(null) }
    // Secondary sheet for "Set reminder…" chosen from the action menu.
    var reminderCard by remember { mutableStateOf<MediaCard?>(null) }

    // Persist selection changes back to the store as they happen.
    LaunchedEffect(selectedCatId, playlistId) {
        LiveSessionStore.setCategoryId(ctx, playlistId, selectedCatId)
    }
    LaunchedEffect(selectedStreamId, channels, playlistId) {
        if (selectedStreamId >= 0) {
            LiveSessionStore.setStreamId(ctx, playlistId, selectedStreamId)
            // Also persist name + poster so the mobile Home "Resume
            // Live" card can render with zero network fetch.
            val c = channels.firstOrNull { it.streamId == selectedStreamId }
            if (c != null) {
                LiveSessionStore.setChannelName(ctx, playlistId, c.title)
                LiveSessionStore.setPoster(ctx, playlistId, c.poster)
                // Feeds the mobile Home "Channel History" rail — one
                // entry per past selection, each with cached name +
                // poster so the rail renders with zero fetch.
                RecentChannelStore.setMeta(ctx, playlistId, c.streamId, c.title, c.poster)
            }
        }
    }

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

    // ── Derived: favorites set + favorites-first ordered channel list
    //    + recent channels rail for the current playlist. Keyed off
    //    fav/recent version counters so toggling re-composes. ──
    val favSet = remember(favVersion, playlistId) {
        FavoritesStore.getAll(ctx, playlistId)
    }
    val recentIds = remember(recentVersion, playlistId) {
        RecentChannelStore.getAll(ctx, playlistId)
    }
    val orderedChannels = remember(channels) {
        // Default sort (parity with TV `TVLiveBrowseScreen`):
        //   • Alphabetical A-Z by channel title, case-insensitive.
        // Favorite state is still shown with a star icon per row and
        // surfaced via the favorites-only filter, so we don't also
        // pin favourites to the top — keeps numbering consistent
        // with the TV browse screen.
        channels.sortedBy { it.title.lowercase() }
    }
    // For the "Recent" rail we resolve the MediaCard objects. When
    // switching categories, some recent channels may not belong —
    // those still show up because the rail is across ALL channels in
    // the provider, fetched lazily below.
    var recentCards by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    LaunchedEffect(recentIds, playlistId) {
        if (recentIds.isEmpty() || playlist == null) { recentCards = emptyList(); return@LaunchedEffect }
        val all = runCatching {
            withContext(Dispatchers.IO) {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "live")
            }
        }.getOrDefault(emptyList())
        // Preserve MRU order.
        val byId = all.associateBy { it.streamId }
        recentCards = recentIds.mapNotNull { byId[it] }
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
            // Preview card (player) with gesture controls:
            //   • Single tap      → fullscreen
            //   • Vertical swipe  → next/prev channel
            //   • Pinch-out       → fullscreen
            item("preview") {
                val idxInList = orderedChannels.indexOfFirst { it.streamId == selectedStreamId }
                val launchFullscreen: () -> Unit = lf@ {
                    val card = currentChannel ?: return@lf
                    val p = playlist ?: return@lf
                    player.pause()
                    val url = XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
                    RecentChannelStore.pushFront(ctx, playlistId, card.streamId)
                    recentVersion++
                    nav.navigate(
                        mobilePlayerRoute(
                            playlistId = playlistId,
                            streamUrl = url,
                            channelName = card.title,
                            isLive = true,
                            liveCategoryId = selectedCatId.ifBlank { null },
                        ),
                    )
                }
                val flipTo: (MediaCard?) -> Unit = { target ->
                    if (target != null) {
                        selectedStreamId = target.streamId
                        RecentChannelStore.pushFront(ctx, playlistId, target.streamId)
                        recentVersion++
                    }
                }
                MobilePreviewSurface(
                    player = player,
                    hasSelection = selectedStreamId >= 0,
                    onTapFullscreen = launchFullscreen,
                    onPinchFullscreen = launchFullscreen,
                    onSwipeUp = {
                        // Next channel in the ordered list (wraps to top).
                        if (orderedChannels.isNotEmpty()) {
                            val nextIdx = if (idxInList < 0) 0
                                else (idxInList + 1) % orderedChannels.size
                            flipTo(orderedChannels[nextIdx])
                        }
                    },
                    onSwipeDown = {
                        // Previous channel (wraps to bottom).
                        if (orderedChannels.isNotEmpty()) {
                            val prevIdx = if (idxInList < 0) 0
                                else (idxInList - 1 + orderedChannels.size) % orderedChannels.size
                            flipTo(orderedChannels[prevIdx])
                        }
                    },
                )
            }

            // EPG timeline scrubber — horizontally scrollable strip of
            // cached programs. Shows the next few hours at a glance.
            if (currentChannel != null) {
                item("epgstrip") {
                    EpgTimelineStrip(
                        playlistId = playlistId,
                        streamId = currentChannel.streamId,
                        channelName = currentChannel.title,
                        epgVersion = epgVersion,
                    )
                }
            }

            // Now / Next metadata
            if (currentChannel != null) {
                item("meta") {
                    // Position label uses `orderedChannels` so it
                    // matches the A-Z-sorted row numbers below.
                    val idx = orderedChannels.indexOf(currentChannel)
                    NowNextPanel(
                        channel = currentChannel,
                        positionLabel = if (idx >= 0) "${idx + 1}/${orderedChannels.size}" else null,
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
                // ── Recent channels horizontal rail ──
                if (recentCards.isNotEmpty()) {
                    item("recent") {
                        Column {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.History, null,
                                    tint = Color(0xFFFACC15),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "RECENT",
                                    color = Color(0xFFFACC15),
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(recentCards, key = { "rc-${it.streamId}" }) { card ->
                                    RecentChannelChip(card) {
                                        selectedStreamId = card.streamId
                                        val p = playlist ?: return@RecentChannelChip
                                        RecentChannelStore.pushFront(ctx, playlistId, card.streamId)
                                        recentVersion++
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
                items(orderedChannels.withIndex().toList(), key = { "ch-${it.value.id}" }) { (idx, card) ->
                    val isFav = card.streamId in favSet
                    MobileLiveHubRow(
                        number = idx + 1,
                        card = card,
                        selected = card.streamId == selectedStreamId,
                        isFavorite = isFav,
                        epgVersion = epgVersion,
                        onClick = {
                            selectedStreamId = card.streamId
                            RecentChannelStore.pushFront(ctx, playlistId, card.streamId)
                            recentVersion++
                        },
                        onLongPress = { actionCard = card },
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

    // Long-press quick-action sheet — favorites toggle + play full-screen.
    val card = actionCard
    if (card != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { actionCard = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0B1220))
                    .padding(16.dp),
            ) {
                Text(
                    card.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Channel actions",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(14.dp))

                val isFav = card.streamId in favSet
                QuickActionRow(
                    icon = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (isFav) "Remove from Favorites" else "Add to Favorites",
                    tint = Color(0xFFFACC15),
                    onClick = {
                        FavoritesStore.toggle(ctx, playlistId, card.streamId)
                        favVersion++
                        actionCard = null
                    },
                )
                QuickActionRow(
                    icon = Icons.Default.Fullscreen,
                    label = "Play full-screen",
                    tint = Cyan,
                    onClick = {
                        val p = playlist ?: return@QuickActionRow
                        val url = XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
                        RecentChannelStore.pushFront(ctx, playlistId, card.streamId)
                        recentVersion++
                        actionCard = null
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
                )
                QuickActionRow(
                    icon = Icons.Default.Notifications,
                    label = "Set reminder…",
                    tint = Cyan,
                    onClick = {
                        // Swap this dialog out for the reminder sub-dialog.
                        reminderCard = card
                        actionCard = null
                    },
                )
            }
        }
    }

    // ── Reminder sub-dialog opened from the action sheet ──
    val remCard = reminderCard
    if (remCard != null) {
        MobileReminderDialog(
            playlistId = playlistId,
            channel = remCard,
            onDismiss = { reminderCard = null },
        )
    }

    // Pause preview when leaving the screen to save battery.
    DisposableEffect(Unit) { onDispose { player.pause() } }
}

@Composable
private fun QuickActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* Reminder sub-dialog — shown from the channel action menu.
   Lists the next 6 upcoming programs on the channel; user can toggle
   a 5-min-before reminder on each. */
@Composable
private fun MobileReminderDialog(
    playlistId: String,
    channel: MediaCard,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var reminderVersion by remember { mutableStateOf(0) }
    val upcoming = remember(reminderVersion, channel.streamId) {
        EpgService.upcoming(channel.streamId, limit = 6)
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B1220))
                .padding(16.dp),
        ) {
            Text(
                channel.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "SET REMINDER",
                color = Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(14.dp))
            if (upcoming.isEmpty()) {
                Text(
                    "No upcoming programs found for this channel.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                )
            } else {
                upcoming.forEach { prog ->
                    val hasReminder = remember(prog.startMs, channel.streamId, reminderVersion) {
                        com.hushtv.tv.data.ReminderStore.exists(ctx, channel.streamId, prog.startMs)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (hasReminder) {
                                    com.hushtv.tv.data.ReminderStore.remove(
                                        ctx, channel.streamId, prog.startMs,
                                    )
                                } else {
                                    val r = com.hushtv.tv.data.ReminderStore.Reminder(
                                        playlistId = playlistId,
                                        streamId = channel.streamId,
                                        channelName = channel.title,
                                        programTitle = prog.title,
                                        programStartMs = prog.startMs,
                                    )
                                    com.hushtv.tv.data.ReminderStore.add(ctx, r)
                                    com.hushtv.tv.notifications.EpgReminderScheduler.schedule(ctx, r)
                                }
                                reminderVersion++
                            }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatClock(prog.startMs),
                            color = if (hasReminder) Cyan else Color(0xFFFACC15),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.width(72.dp),
                        )
                        Text(
                            prog.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (hasReminder) Icons.Default.Notifications
                            else Icons.Default.NotificationsNone,
                            null,
                            tint = if (hasReminder) Color(0xFFFACC15) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap a show to toggle · notifies 5 min before it starts.",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
            )
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileLiveHubRow(
    number: Int,
    card: MediaCard,
    selected: Boolean,
    isFavorite: Boolean,
    epgVersion: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Channel number in a compact column.
        Box(
            Modifier.width(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = if (selected) Cyan else Color(0xFF64748B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(46.dp)
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
                    fontSize = 13.sp, fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.title,
                    color = if (selected) Cyan else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Star, null,
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
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
private fun RecentChannelChip(card: MediaCard, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF112035))
            .border(1.dp, Color(0x5506B6D4), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            if (!card.poster.isNullOrBlank()) {
                AsyncImage(
                    model = card.poster, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Text(
                    card.title.take(1).uppercase(),
                    color = Color(0xFF64748B),
                    fontSize = 10.sp, fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            card.title,
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
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

/* ────────────────────────────────────────────────────────────────
 * Gesture-enabled preview surface.
 *
 * Handles three gestures in priority order, classified on the first
 * pointer event after touch-down:
 *   • 2 pointers (pinch) → zoom; when cumulative scale crosses
 *     [PINCH_FULLSCREEN_THRESHOLD] we fire [onPinchFullscreen] and
 *     consume the gesture.
 *   • 1 pointer + vertical drag ≥ [SWIPE_FLIP_THRESHOLD_DP] →
 *     channel flip ([onSwipeUp] / [onSwipeDown]).
 *   • 1 pointer release with no drag → tap handled by the parent
 *     clickable below (falls through to [onTapFullscreen]).
 *
 * Horizontal jitter is ignored so short left/right wiggles on a
 * slightly angled finger don't cancel the vertical flip.
 * ──────────────────────────────────────────────────────────────── */
private const val PINCH_FULLSCREEN_THRESHOLD = 1.25f
private val SWIPE_FLIP_THRESHOLD_DP = 60.dp

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MobilePreviewSurface(
    player: ExoPlayer,
    hasSelection: Boolean,
    onTapFullscreen: () -> Unit,
    onPinchFullscreen: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val flipThresholdPx = with(density) { SWIPE_FLIP_THRESHOLD_DP.toPx() }
    // Transient UI hint ("Swipe for channel") — fades out after 2.5 s
    // on first composition to teach the gesture without being noisy.
    var hintVisible by remember { mutableStateOf(true) }
    var flipDirectionHint by remember { mutableStateOf<String?>(null) }  // "UP" / "DOWN"
    LaunchedEffect(Unit) {
        delay(2_500)
        hintVisible = false
    }
    // Gesture-triggered arrow flash (plays once per flip).
    LaunchedEffect(flipDirectionHint) {
        if (flipDirectionHint != null) {
            delay(650)
            flipDirectionHint = null
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .pointerInput(Unit) {
                // Custom gesture loop. Runs BEFORE the parent clickable
                // (child-first event ordering in Compose). We only
                // consume events when we decide the gesture belongs to
                // us — otherwise we let the tap fall through.
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var totalDy = 0f
                    var totalDx = 0f
                    var zoomFactor = 1f
                    var isPinching = false
                    var claimed = false    // once true, the tap can't fire

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2 && !isPinching) {
                            isPinching = true
                        }

                        if (isPinching) {
                            val z = event.calculateZoom()
                            if (z > 0f) zoomFactor *= z
                            // Claim the gesture so LazyColumn doesn't scroll.
                            event.changes.forEach { it.consume() }
                            claimed = true
                            if (zoomFactor >= PINCH_FULLSCREEN_THRESHOLD) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onPinchFullscreen()
                                // Drain remaining events so we don't fire twice.
                                while (event.changes.any { it.pressed }) {
                                    val drain = awaitPointerEvent(PointerEventPass.Main)
                                    drain.changes.forEach { it.consume() }
                                    if (drain.changes.none { it.pressed }) break
                                }
                                return@awaitEachGesture
                            }
                        } else {
                            // Single-pointer tracking for swipe detection.
                            val change = event.changes.firstOrNull { it.id == first.id } ?: event.changes.firstOrNull()
                            if (change != null) {
                                val d = change.positionChange()
                                totalDy += d.y
                                totalDx += d.x
                                // Claim the gesture as soon as drag exceeds
                                // half the threshold so LazyColumn doesn't
                                // steal it — but wait for full threshold
                                // before firing the flip action.
                                val magnitude = kotlin.math.abs(totalDy)
                                if (magnitude > flipThresholdPx * 0.3f && magnitude > kotlin.math.abs(totalDx)) {
                                    change.consume()
                                    claimed = true
                                }
                                if (magnitude >= flipThresholdPx) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    if (totalDy < 0) {
                                        flipDirectionHint = "UP"
                                        onSwipeUp()
                                    } else {
                                        flipDirectionHint = "DOWN"
                                        onSwipeDown()
                                    }
                                    // Drain the rest of this gesture.
                                    while (event.changes.any { it.pressed }) {
                                        val drain = awaitPointerEvent(PointerEventPass.Main)
                                        drain.changes.forEach { it.consume() }
                                        if (drain.changes.none { it.pressed }) break
                                    }
                                    return@awaitEachGesture
                                }
                            }
                        }

                        // End of gesture (all pointers up).
                        if (event.changes.none { it.pressed }) {
                            // If we never claimed, let the clickable below
                            // fire its tap.
                            if (!claimed && !isPinching && kotlin.math.abs(totalDy) < flipThresholdPx) {
                                // fall through — tap handled by parent clickable
                            }
                            return@awaitEachGesture
                        }
                    }
                }
            }
            .clickable(onClick = onTapFullscreen),
    ) {
        if (!hasSelection) {
            // Nothing selected yet — cyan-tinted slate.
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
            // Fullscreen affordance pill (top-right).
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
                    "Tap · pinch to expand",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // Gesture hint pill (bottom-center) — teaches swipe-to-flip
            // for the first 2.5 s then fades.
            AnimatedVisibility(
                visible = hintVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xBB000000))
                        .border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp, null, tint = Cyan,
                        modifier = Modifier.size(14.dp),
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown, null, tint = Cyan,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Swipe for channel",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // Big arrow flash when user flips channel — visual feedback.
            AnimatedVisibility(
                visible = flipDirectionHint != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { if (flipDirectionHint == "UP") it else -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { if (flipDirectionHint == "UP") -it else it }),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xDD06B6D4)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (flipDirectionHint == "UP") Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        null,
                        tint = Color(0xFF05080F),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

/* ────────────────────────────────────────────────────────────────
 * EPG Timeline Strip — horizontally scrolling program chips under
 * the preview. Each chip's width scales with the program's duration
 * (0.6 dp per minute, clamped 80–240 dp). The currently-playing
 * program is highlighted in cyan and auto-scrolled into view on
 * first render / channel change.
 *
 * Long-press a FUTURE chip → "Set reminder" action sheet. Past /
 * live programs don't respond to long-press (nothing to remind).
 * ──────────────────────────────────────────────────────────────── */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpgTimelineStrip(
    playlistId: String,
    streamId: Int,
    channelName: String,
    epgVersion: Int,
) {
    @Suppress("UNUSED_EXPRESSION") epgVersion
    val ctx = LocalContext.current
    val programs = remember(streamId, epgVersion) { EpgService.programsOf(streamId) }
    if (programs.isEmpty()) return

    // Counter bumped each time a reminder is added / removed so the
    // bell icons on chips re-render immediately.
    var reminderVersion by remember { mutableStateOf(0) }
    // Long-press target — drives the bottom sheet.
    var reminderFor by remember { mutableStateOf<EpgProgram?>(null) }

    // Android 13+ runtime notification permission launcher. On older
    // Androids POST_NOTIFICATIONS is granted at install time and the
    // launcher is a harmless no-op.
    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — if denied, alarm still fires; user just won't see the toast */ }

    val listState = rememberLazyListState()
    val liveIdx = remember(programs, epgVersion) {
        programs.indexOfFirst { it.isLive }.let { if (it < 0) 0 else it }
    }
    LaunchedEffect(streamId, epgVersion) {
        delay(80)
        runCatching { listState.animateScrollToItem(liveIdx) }
    }

    Column(Modifier.padding(top = 2.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TIMELINE",
                color = Cyan,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "· long-press to set reminder",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(programs, key = { "prg-${it.startMs}" }) { p ->
                val hasReminder = remember(p.startMs, streamId, reminderVersion) {
                    com.hushtv.tv.data.ReminderStore.exists(ctx, streamId, p.startMs)
                }
                EpgTimelineChip(
                    program = p,
                    hasReminder = hasReminder,
                    onLongPress = {
                        // Only future programs can be reminded.
                        if (p.startMs > System.currentTimeMillis()) {
                            reminderFor = p
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    // ── Reminder action sheet ───────────────────────────────────────
    val target = reminderFor
    if (target != null) {
        val already = com.hushtv.tv.data.ReminderStore.exists(ctx, streamId, target.startMs)
        androidx.compose.ui.window.Dialog(onDismissRequest = { reminderFor = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0B1220))
                    .padding(16.dp),
            ) {
                Text(
                    target.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${channelName.uppercase()} · ${formatClock(target.startMs)}",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (already) {
                                com.hushtv.tv.data.ReminderStore.remove(ctx, streamId, target.startMs)
                            } else {
                                // Ask for notification permission first
                                // on Android 13+. If the user denies,
                                // the alarm still fires — they just
                                // won't see the heads-up toast.
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                val r = com.hushtv.tv.data.ReminderStore.Reminder(
                                    playlistId = playlistId,
                                    streamId = streamId,
                                    channelName = channelName,
                                    programTitle = target.title,
                                    programStartMs = target.startMs,
                                )
                                com.hushtv.tv.data.ReminderStore.add(ctx, r)
                                com.hushtv.tv.notifications.EpgReminderScheduler.schedule(ctx, r)
                            }
                            reminderVersion++
                            reminderFor = null
                        }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (already) Icons.Default.NotificationsOff
                        else Icons.Default.Notifications,
                        null,
                        tint = if (already) Color(0xFFEF4444) else Cyan,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            if (already) "Cancel reminder" else "Set reminder",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!already) {
                            Spacer(Modifier.height(1.dp))
                            Text(
                                "Notify 5 min before it starts",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpgTimelineChip(
    program: EpgProgram,
    hasReminder: Boolean,
    onLongPress: () -> Unit,
) {
    val p = program
    val widthDp: Dp = run {
        val minutes = (p.durationMs / 60_000L).toInt().coerceAtLeast(15)
        (minutes * 0.6f).dp.coerceIn(80.dp, 240.dp)
    }
    val live = p.isLive
    val past = p.stopMs < System.currentTimeMillis()
    val bg = when {
        live -> Cyan.copy(alpha = 0.18f)
        past -> Color(0xFF0A1220).copy(alpha = 0.5f)
        else -> Color(0xFF0A1220)
    }
    val border = when {
        live -> Cyan
        past -> Color.Transparent
        else -> Color(0x3306B6D4)
    }
    val titleColor = when {
        live -> Cyan
        past -> Color(0xFF475569)
        else -> Color.White
    }
    Column(
        Modifier
            .width(widthDp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (live) 1.5.dp else if (past) 0.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(8.dp),
            )
            .combinedClickable(
                onClick = { /* tap does nothing — preview already shows current */ },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                formatClock(p.startMs),
                color = if (live) Cyan else Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasReminder) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Notifications, null,
                    tint = Color(0xFFFACC15),
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            p.title,
            color = titleColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (live) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(1.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p.progressPct)
                        .height(2.dp)
                        .background(Cyan, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}
