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

    // Take up to 50 channels from the last-browsed category so the grid
    // loads in a reasonable time. Scrolling down loads more lazily.
    val channels = remember { NavState.liveChannels.take(100) }
    val channelPrograms = remember { mutableStateMapOf<Int, List<EpgProgram>>() }

    // Timeline anchor: now minus 30 min, ending 6 hours later.
    val nowMs = remember { System.currentTimeMillis() }
    val timelineStart = remember { nowMs - 30L * 60 * 1000 }
    val timelineEnd = remember { timelineStart + 6L * 60 * 60 * 1000 }

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

    Column(Modifier.fillMaxSize().background(Color(0xFF050A15))) {
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
                LazyColumn(state = rememberLazyListState()) {
                    items(channels.size, key = { channels[it].id }) { idx ->
                        val ch = channels[idx]
                        ChannelRowCell(
                            number = idx + 1,
                            channel = ch,
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
                        ProgramsRow(
                            programs = progs,
                            timelineStart = timelineStart,
                            timelineEnd = timelineEnd
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRuler(start: Long, end: Long) {
    val totalMin = ((end - start) / 60_000L).toInt()
    val pxPerMin = 8.dp // 8dp per minute → 6hrs = 2880dp wide
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.US) }

    Row(
        Modifier
            .width(pxPerMin * totalMin)
            .height(32.dp)
            .background(Color(0x1A000000))
    ) {
        // Mark every 30 min
        var t = (start / (30L * 60_000L) + 1) * (30L * 60_000L)
        var offsetMin = ((t - start) / 60_000L).toInt().coerceAtLeast(0)
        Spacer(Modifier.width(pxPerMin * offsetMin))
        while (t < end) {
            Column(
                Modifier.width(pxPerMin * 30),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    fmt.format(Date(t)),
                    color = TextSecondary, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            t += 30L * 60_000L
        }
    }
}

@Composable
private fun ProgramsRow(
    programs: List<EpgProgram>,
    timelineStart: Long,
    timelineEnd: Long
) {
    val totalMin = ((timelineEnd - timelineStart) / 60_000L).toInt()
    val pxPerMin = 8.dp

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
            Box(
                Modifier
                    .offset(x = pxPerMin * startMin)
                    .width(pxPerMin * durMin - 2.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
                    .background(
                        if (prog.isLive) Color(0x4406B6D4) else Color(0x14FFFFFF),
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        if (prog.isLive) 1.dp else 0.dp,
                        if (prog.isLive) Cyan else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    prog.title,
                    color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun ChannelRowCell(
    number: Int,
    channel: MediaCard,
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
            fontWeight = FontWeight.SemiBold, maxLines = 2
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
