package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.DvrApi
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mobile "My Recordings" screen. Mirrors [TVMyRecordingsScreen]
 * semantics: per-row status badges, live progress for in-progress
 * captures, explicit failure reasons, playable rows for completed
 * and in-progress captures. Polls every 5 s.
 */
@Composable
fun MobileMyRecordingsScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }
    val userId = remember(playlist) { playlist?.let { DvrApi.userIdFor(it) } }

    var loading by remember { mutableStateOf(true) }
    var quota by remember { mutableStateOf<DvrApi.Quota?>(null) }
    var recordings by remember { mutableStateOf<List<DvrApi.Recording>>(emptyList()) }
    var scheduled by remember { mutableStateOf<List<DvrApi.Scheduled>>(emptyList()) }
    var seasonPasses by remember { mutableStateOf<List<DvrApi.SeasonPass>>(emptyList()) }
    var tab by remember { mutableStateOf(0) } // 0=rec, 1=sched, 2=passes

    LaunchedEffect(playlistId) {
        val uid = userId ?: return@LaunchedEffect
        while (true) {
            quota = DvrApi.quota(uid)
            recordings = DvrApi.list(uid)
            scheduled = DvrApi.listScheduled(uid)
            seasonPasses = DvrApi.listSeasonPasses(uid)
            loading = false
            delay(5_000)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "My Recordings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Live captures · auto-delete after 14 days",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                )
            }
        }

        if (userId == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Pick a profile to view recordings",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        QuotaBar(quota)
        Spacer(Modifier.height(4.dp))

        // Tab strip — matches TV variant
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MobileTabChip("Recordings", tab == 0) { tab = 0 }
            MobileTabChip(
                "Scheduled (${scheduled.count { it.status == "pending" }})",
                tab == 1,
            ) { tab = 1 }
            MobileTabChip("Season Passes (${seasonPasses.size})", tab == 2) { tab = 2 }
        }

        when (tab) {
            0 -> MobileRecordingsTab(
                loading = loading,
                recordings = recordings,
                userId = userId,
                playlistId = playlistId,
                onRefresh = {
                    scope.launch {
                        recordings = DvrApi.list(userId)
                        quota = DvrApi.quota(userId)
                    }
                },
                nav = nav,
            )
            1 -> MobileScheduledTab(
                scheduled = scheduled.filter { it.status == "pending" },
                userId = userId,
                onRefresh = {
                    scope.launch { scheduled = DvrApi.listScheduled(userId) }
                },
            )
            2 -> MobilePassesTab(
                passes = seasonPasses,
                scheduledCountFor = { id ->
                    scheduled.count { it.season_pass_id == id && it.status == "pending" }
                },
                userId = userId,
                onRefresh = {
                    scope.launch {
                        seasonPasses = DvrApi.listSeasonPasses(userId)
                        scheduled = DvrApi.listScheduled(userId)
                    }
                },
            )
        }
    }
}

@Composable
private fun MobileTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Cyan.copy(alpha = 0.18f) else Color(0x14FFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (selected) Cyan else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun MobileRecordingsTab(
    loading: Boolean,
    recordings: List<DvrApi.Recording>,
    userId: String?,
    playlistId: String,
    onRefresh: () -> Unit,
    nav: NavController,
) {
    val scope = rememberCoroutineScope()
    when {
        loading && recordings.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
        }
        recordings.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FiberManualRecord, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No recordings yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Hit Record while watching a live channel", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recordings, key = { it.rec_id }) { rec ->
                    RecordingRow(
                        rec = rec,
                        onPlay = {
                            if (!rec.isPlayable) return@RecordingRow
                            val uid = userId ?: return@RecordingRow
                            val url = DvrApi.streamUrl(uid, rec.rec_id)
                            val label = rec.show_title.ifBlank { rec.channel_name }
                                .ifBlank { "Recording" }
                            nav.navigate(
                                mobilePlayerRoute(
                                    playlistId = playlistId,
                                    streamUrl = url,
                                    channelName = label,
                                    isLive = false,
                                ),
                            )
                        },
                        onDelete = {
                            val uid = userId ?: return@RecordingRow
                            scope.launch {
                                DvrApi.delete(uid, rec.rec_id)
                                onRefresh()
                            }
                        },
                        onToggleWatched = {
                            val uid = userId ?: return@RecordingRow
                            scope.launch {
                                DvrApi.markWatched(uid, rec.rec_id, !rec.watched)
                                onRefresh()
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun MobileScheduledTab(
    scheduled: List<DvrApi.Scheduled>,
    userId: String?,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (scheduled.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing scheduled", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Open the TV Guide and tap any future show.", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
        }
        return
    }
    val fmt = remember { SimpleDateFormat("EEE, MMM d  h:mm a", Locale.US) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(scheduled, key = { it.sched_id }) { s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(12.dp),
            ) {
                Icon(Icons.Default.FiberManualRecord, null, tint = Cyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.show_title.ifBlank { s.channel_name },
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                    Text(
                        "${s.channel_name} · ${fmt.format(Date(s.start_at_epoch * 1000L))}",
                        color = Color(0xFF94A3B8), fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0x22EF4444))
                        .clickable {
                            val uid = userId ?: return@clickable
                            scope.launch {
                                DvrApi.cancelScheduled(uid, s.sched_id)
                                onRefresh()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Cancel", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MobilePassesTab(
    passes: List<DvrApi.SeasonPass>,
    scheduledCountFor: (String) -> Int,
    userId: String?,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (passes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No season passes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Tap any series episode in the TV Guide → Record entire series.", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(passes, key = { it.pass_id }) { p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.series_title,
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                    val n = scheduledCountFor(p.pass_id)
                    Text(
                        "${p.channel_name}  ·  $n upcoming episode${if (n == 1) "" else "s"}",
                        color = Color(0xFF94A3B8), fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0x22EF4444))
                        .clickable {
                            val uid = userId ?: return@clickable
                            scope.launch {
                                DvrApi.deleteSeasonPass(uid, p.pass_id)
                                onRefresh()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Cancel pass", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private val DvrApi.Recording.isPlayable: Boolean
    get() = status != "failed" && size_bytes > 0

@Composable
private fun rememberNowEpoch(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1_000)
        }
    }
    return now
}

@Composable
private fun QuotaBar(q: DvrApi.Quota?) {
    val usedH = (q?.used_s ?: 0) / 3600.0
    val quotaH = (q?.quota_s ?: 72000) / 3600.0
    val remainingH = (q?.available_s ?: 72000) / 3600.0
    val pct = if (quotaH > 0) (usedH / quotaH).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Storage",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "%.1f h used · %.1f h left".format(usedH, remainingH),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x22FFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .background(Cyan, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun RecordingRow(
    rec: DvrApi.Recording,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onToggleWatched: () -> Unit = {},
) {
    val nowEpoch = rememberNowEpoch()
    val isRecording = rec.status == "recording"
    val isFailed = rec.status == "failed"
    val isCompleted = rec.status == "completed"
    val primary = rec.show_title.ifBlank { rec.channel_name }.ifBlank { "Recording" }
    val startDate = if (rec.started_at_epoch > 0)
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            .format(Date(rec.started_at_epoch * 1000L))
    else ""
    val secondary = listOfNotNull(
        rec.channel_name.takeIf { it.isNotBlank() && rec.show_title.isNotBlank() },
        startDate.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    val elapsed = (nowEpoch - rec.started_at_epoch).coerceAtLeast(0L).toInt()
    val scheduled = rec.duration_s.coerceAtLeast(0)
    val remaining = (scheduled - elapsed).coerceAtLeast(0)

    val leadingIcon: ImageVector = when {
        isFailed -> Icons.Default.ErrorOutline
        isRecording -> Icons.Default.FiberManualRecord
        else -> Icons.Default.PlayArrow
    }
    val leadingColor = if (isRecording || isFailed) Color(0xFFEF4444) else Cyan

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1220))
            .alpha(if (rec.watched) 0.55f else 1f)
            .clickable(onClick = if (isFailed) onDelete else onPlay)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(leadingIcon, null, tint = leadingColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    primary, color = Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (rec.watched) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(Color(0x2206B6D4), RoundedCornerShape(99.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("✓ WATCHED", color = Cyan, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    }
                }
            }
            if (secondary.isNotEmpty()) {
                Text(secondary, color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            when {
                isRecording -> {
                    val displayElapsed = if (scheduled > 0) elapsed.coerceAtMost(scheduled) else elapsed
                    val pastDue = scheduled > 0 && elapsed >= scheduled
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge("● REC", Color(0xFFEF4444))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (pastDue)
                                "${formatClock(displayElapsed)} · Finishing…"
                            else
                                "${formatClock(elapsed)} · ${formatClock(remaining)} left",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color(0x22FFFFFF), RoundedCornerShape(1.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(if (scheduled > 0) displayElapsed / scheduled.toFloat() else 0f)
                                .background(Color(0xFFEF4444), RoundedCornerShape(1.dp)),
                        )
                    }
                }
                isFailed -> {
                    Badge("FAILED", Color(0xFFEF4444))
                    if (rec.fail_reason.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            rec.fail_reason, color = Color(0xFFFCA5A5),
                            fontSize = 10.sp,
                        )
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge("COMPLETED", Cyan)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildString {
                                append(formatDuration(rec.duration_s))
                                if (rec.size_bytes > 0) {
                                    append(" · ")
                                    append(formatSize(rec.size_bytes))
                                }
                            },
                            color = Color(0xFF94A3B8), fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        // Mark-as-watched toggle (completed recordings only). Tap
        // checkmark to flag, tap ↺ to unmark.
        if (isCompleted) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x14FFFFFF))
                    .clickable(onClick = onToggleWatched),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (rec.watched) "↺" else "✓",
                    color = if (rec.watched) Color(0xFFFACC15) else Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        val (delIcon, delLabel) = if (isRecording)
            Icons.Default.Stop to "Stop this recording"
        else
            Icons.Default.DeleteOutline to "Delete this recording"
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(delIcon, delLabel, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

private fun formatClock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1_048_576.0
    if (mb < 1024) return "%.0f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
