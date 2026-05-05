package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.EpgProgram
import com.hushtv.tv.data.EpgService
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.NavState
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tivimate-style EPG grid:
 *  • channels down the left
 *  • 6-hour timeline across the top, 1 pixel ≈ 30 seconds
 *  • program blocks painted within each channel row
 *  • D-pad: Up/Down changes channel, Left/Right moves through programs,
 *    Enter plays the channel (jumps to "now playing")
 */
@Composable
fun TVEpgGridScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }
    val userId = remember(playlist) { playlist?.let { com.hushtv.tv.data.DvrApi.userIdFor(it) } }

    // Take up to 50 channels from the last-browsed category so the grid
    // loads in a reasonable time. Scrolling down loads more lazily.
    val channels = remember { NavState.liveChannels.take(100) }
    val channelPrograms = remember { mutableStateMapOf<Int, List<EpgProgram>>() }

    // Timeline anchor: now minus 30 min, ending 8 hours later. Wider
    // window means more upcoming programs visible per row without
    // needing to D-pad-scroll for ages.
    val nowMs = remember { System.currentTimeMillis() }
    val timelineStart = remember { nowMs - 30L * 60 * 1000 }
    val timelineEnd = remember { timelineStart + 8L * 60 * 60 * 1000 }

    // Phase 2 — list of currently-pending scheduled recordings for
    // this user. Painted as a ⏰ badge on matching EPG cells. Refreshes
    // when the user schedules / cancels via the dialog.
    var scheduled by remember { mutableStateOf<List<com.hushtv.tv.data.DvrApi.Scheduled>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(refreshTick) {
        val uid = userId ?: return@LaunchedEffect
        scheduled = com.hushtv.tv.data.DvrApi.listScheduled(uid)
            .filter { it.status == "pending" }
    }

    // Lazy-fetch EPG for all visible channels.
    LaunchedEffect(channels) {
        val p = playlist ?: return@LaunchedEffect
        channels.forEach { ch ->
            scope.launch {
                val list = EpgService.fetchShortEpg(p.host, p.username, p.password, ch.streamId)
                channelPrograms[ch.streamId] = list
            }
        }
    }

    // Dialog state
    var dialogChannel by remember { mutableStateOf<MediaCard?>(null) }
    var dialogProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(3_000)
            toast = null
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0x1AFFFFFF), shape = CircleShape,
                modifier = Modifier.size(44.dp)
                    .onFocusChangedBorder()
                    .focusable()
                    .clickableWithEnter { nav.popBackStack() }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Text("TV Guide", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Text(
                SimpleDateFormat("EEE, MMM d  h:mm a", Locale.US).format(Date()),
                color = TextSecondary, fontSize = 14.sp
            )
        }

        Row(Modifier.fillMaxSize()) {
            // Channels column (left)
            Column(
                Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(Color(0x33000000))
            ) {
                // Spacer matches the time-ruler row height
                Box(Modifier.height(32.dp).fillMaxWidth().background(Color(0x33000000)))
                // Live DVR state — ticks when any channel starts/stops recording.
                val recVersion = com.hushtv.tv.data.rememberActiveRecordingVersion(playlistId)
                LazyColumn(state = rememberLazyListState()) {
                    items(channels.size, key = { channels[it].id }) { idx ->
                        val ch = channels[idx]
                        val recording = remember(recVersion, ch.title) {
                            com.hushtv.tv.data.DvrActiveState.isRecording(ch.title)
                        }
                        ChannelRowCell(
                            number = idx + 1,
                            channel = ch,
                            isRecording = recording,
                            onClick = {
                                val p = playlist ?: return@ChannelRowCell
                                NavState.liveChannels = channels
                                NavState.rememberPlayback(idx)
                                NavState.browsePlaylistId = playlistId
                                val url = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
                                nav.navigate(
                                    "player/$playlistId/${Uri.encode(url)}/${Uri.encode(ch.title)}/true"
                                )
                            }
                        )
                    }
                }
            }

            // Timeline + program grid (right, scrolls horizontally)
            val hScroll = rememberScrollState(initial = 0)
            Column(Modifier.weight(1f).horizontalScroll(hScroll)) {
                TimeRuler(timelineStart, timelineEnd)
                LazyColumn(state = rememberLazyListState()) {
                    items(channels.size, key = { "prog-${channels[it].id}" }) { idx ->
                        val ch = channels[idx]
                        val progs = channelPrograms[ch.streamId] ?: emptyList()
                        val schedForChannel = remember(scheduled, ch.streamId) {
                            scheduled.filter { sched ->
                                sched.epg_id.startsWith("ch${ch.streamId}-")
                            }.map { it.epg_id }.toSet()
                        }
                        ProgramsRow(
                            programs = progs,
                            timelineStart = timelineStart,
                            timelineEnd = timelineEnd,
                            channelStreamId = ch.streamId,
                            scheduledEpgIds = schedForChannel,
                            onProgramClick = { p ->
                                dialogChannel = ch
                                dialogProgram = p
                            },
                        )
                    }
                }
            }
        }
    }

    // Phase 2 / 3 — EPG program info dialog with Schedule chips.
    val dlgCh = dialogChannel
    val dlgPr = dialogProgram
    if (dlgCh != null && dlgPr != null) {
        com.hushtv.tv.ui.player.EpgProgramDialog(
            playlistId = playlistId,
            channel = dlgCh,
            program = dlgPr,
            upcomingProgramsOnChannel = channelPrograms[dlgCh.streamId] ?: emptyList(),
            existingScheduled = scheduled,
            isCurrentlyRecording = com.hushtv.tv.data.DvrActiveState.isRecording(dlgCh.title),
            onDismiss = { dialogChannel = null; dialogProgram = null },
            onWatchLive = {
                val p = playlist ?: return@EpgProgramDialog
                val url = XtreamApi.liveUrl(p.host, p.username, p.password, dlgCh.streamId)
                NavState.liveChannels = channels
                NavState.rememberPlayback(channels.indexOf(dlgCh).coerceAtLeast(0))
                NavState.browsePlaylistId = playlistId
                nav.navigate(
                    "player/$playlistId/${Uri.encode(url)}/${Uri.encode(dlgCh.title)}/true"
                )
            },
            onRecordNow = {
                val uid = userId ?: return@EpgProgramDialog
                val p = playlist ?: return@EpgProgramDialog
                val url = XtreamApi.liveUrl(p.host, p.username, p.password, dlgCh.streamId)
                scope.launch {
                    val res = com.hushtv.tv.data.DvrApi.recordNow(
                        userId = uid,
                        channelUrl = url,
                        channelName = dlgCh.title,
                        showTitle = dlgPr.title,
                        showEndsAtEpoch = dlgPr.stopMs / 1000L,
                    )
                    when (res) {
                        is com.hushtv.tv.data.DvrApi.RecordNowResult.Success -> {
                            toast = "Recording started — ${dlgPr.title}"
                        }
                        is com.hushtv.tv.data.DvrApi.RecordNowResult.Error ->
                            toast = res.message
                    }
                }
            },
            onStopRecording = {
                val uid = userId ?: return@EpgProgramDialog
                scope.launch {
                    val active = com.hushtv.tv.data.DvrApi.findActive(uid, dlgCh.title)
                    if (active != null) {
                        com.hushtv.tv.data.DvrApi.delete(uid, active.rec_id)
                    }
                    toast = "Recording stopped."
                }
            },
            onToast = { toast = it },
            onSchedulingChanged = { refreshTick++ },
        )
    }

    // Floating toast overlay (3 s auto-dismiss) for record-now and
    // schedule errors that come back from the server (concurrency
    // limit, quota, network failure, etc).
    val toastMsg = toast
    if (toastMsg != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 60.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = Color(0xEE0A101D),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.55f)),
            ) {
                Text(
                    toastMsg,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeRuler(start: Long, end: Long) {
    val totalMin = ((end - start) / 60_000L).toInt()
    val pxPerMin = 8.dp // 8dp per minute → 8hrs = 3840dp wide
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val nowMs = System.currentTimeMillis()
    val nowOffsetMin = ((nowMs - start) / 60_000L).toInt().coerceIn(0, totalMin)

    Box(
        Modifier
            .width(pxPerMin * totalMin)
            .height(32.dp)
            .background(Color(0x1A000000))
    ) {
        Row(Modifier.fillMaxSize()) {
            // Mark every 30 min
            var t = (start / (30L * 60_000L) + 1) * (30L * 60_000L)
            val offsetMin = ((t - start) / 60_000L).toInt().coerceAtLeast(0)
            Spacer(Modifier.width(pxPerMin * offsetMin))
            while (t < end) {
                Column(
                    Modifier.width(pxPerMin * 30),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        fmt.format(Date(t)),
                        color = Color(0xFFE5E7EB), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                t += 30L * 60_000L
            }
        }
        // Cyan "NOW" marker pip on the ruler.
        Box(
            Modifier
                .offset(x = pxPerMin * nowOffsetMin - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Cyan)
        )
    }
}

@Composable
private fun ProgramsRow(
    programs: List<EpgProgram>,
    timelineStart: Long,
    timelineEnd: Long,
    channelStreamId: Int,
    scheduledEpgIds: Set<String>,
    onProgramClick: (EpgProgram) -> Unit,
) {
    val totalMin = ((timelineEnd - timelineStart) / 60_000L).toInt()
    val pxPerMin = 8.dp
    val clockFmt = remember { SimpleDateFormat("h:mm a", Locale.US) }

    Box(
        Modifier
            .width(pxPerMin * totalMin)
            .height(62.dp)
            .padding(vertical = 2.dp)
            .background(Color(0x08FFFFFF))
    ) {
        programs.forEach { prog ->
            val startMin = (((prog.startMs - timelineStart) / 60_000L).toInt())
                .coerceAtLeast(0)
            val durMin = ((prog.stopMs.coerceAtMost(timelineEnd) - prog.startMs.coerceAtLeast(timelineStart)) / 60_000L)
                .toInt().coerceAtLeast(1)
            if (prog.stopMs <= timelineStart || prog.startMs >= timelineEnd) return@forEach
            val epgId = "ch${channelStreamId}-s${prog.startMs / 1000L}"
            val isScheduled = epgId in scheduledEpgIds
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .offset(x = pxPerMin * startMin)
                    .width(pxPerMin * durMin - 2.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
                    .background(
                        when {
                            focused -> Color(0x55FACC15)
                            prog.isLive -> Color(0x4406B6D4)
                            isScheduled -> Color(0x2206B6D4)
                            else -> Color(0x14FFFFFF)
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = if (focused) 2.dp else if (prog.isLive || isScheduled) 1.dp else 0.dp,
                        color = when {
                            focused -> Color(0xFFFACC15)
                            prog.isLive -> Cyan
                            isScheduled -> Cyan
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .clickableWithEnter { onProgramClick(prog) }
            ) {
                Column {
                    if (durMin >= 25) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                clockFmt.format(Date(prog.startMs)),
                                color = if (prog.isLive) Cyan else Color(0xFFFACC15),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                            )
                            if (prog.isLive) {
                                Spacer(Modifier.width(4.dp))
                                Box(Modifier.size(5.dp).background(Cyan, CircleShape))
                            }
                            if (isScheduled) {
                                Spacer(Modifier.width(6.dp))
                                ScheduledChip()
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                    Text(
                        prog.title,
                        color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (durMin >= 25) 1 else 2,
                    )
                    if (durMin < 25 && isScheduled) {
                        Spacer(Modifier.height(1.dp))
                        ScheduledChip()
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0x2206B6D4), RoundedCornerShape(3.dp))
            .border(1.dp, Cyan.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            "⏰ SCHED",
            color = Cyan,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun ChannelRowCell(
    number: Int,
    channel: MediaCard,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(vertical = 2.dp, horizontal = 6.dp)
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x08FFFFFF),
                RoundedCornerShape(8.dp)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Cyan else Color(0x14FFFFFF),
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 10.dp)
    ) {
        Text(
            number.toString().padStart(3, '0'),
            color = if (focused) Cyan else TextSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
        Text(
            channel.title,
            color = Color.White, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2,
            modifier = Modifier.weight(1f, false)
        )
        if (isRecording) {
            Spacer(Modifier.width(6.dp))
            EpgGridRecBadge()
        }
    }
}

/**
 * Compact "● REC" pill rendered beside the channel title in the EPG
 * grid's left rail. Matches the Live Browse screen's badge style so
 * the two screens feel like one product, just smaller to fit the
 * 62.dp row height.
 */
@Composable
private fun EpgGridRecBadge() {
    val red = Color(0xFFEF4444)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(16.dp)
            .background(Color(0x1FEF4444), RoundedCornerShape(3.dp))
            .border(1.dp, red.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp),
    ) {
        Box(Modifier.size(5.dp).background(red, CircleShape))
        Spacer(Modifier.width(3.dp))
        Text(
            "REC",
            color = red,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun Modifier.onFocusChangedBorder(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) Cyan else Color.Transparent,
            shape = CircleShape
        )
        .onFocusChanged { focused = it.isFocused }
}
