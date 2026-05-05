package com.hushtv.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.DvrApi
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TV "My Recordings" screen.
 *
 * Key UX:
 *   • Quota bar at top (X of 20 h used).
 *   • Each row gets an explicit status badge — RECORDING / COMPLETED /
 *     FAILED — plus a subtitle that describes what the user can do
 *     with it (play, stop, or see the failure reason).
 *   • While a recording is in progress, the row IS playable (the
 *     backend writes fragmented MP4, so partial playback works). The
 *     delete button flips to "stop" semantics — it terminates the
 *     ongoing capture.
 *   • List auto-refreshes every 5 s so time-left counters stay
 *     accurate without requiring the user to back-and-forward.
 */
@Composable
fun TVMyRecordingsScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }
    val userId = remember(playlist) { playlist?.let { DvrApi.userIdFor(it) } }

    var loading by remember { mutableStateOf(true) }
    var quota by remember { mutableStateOf<DvrApi.Quota?>(null) }
    var recordings by remember { mutableStateOf<List<DvrApi.Recording>>(emptyList()) }
    // Phase 2 / 3 state
    var scheduled by remember { mutableStateOf<List<DvrApi.Scheduled>>(emptyList()) }
    var seasonPasses by remember { mutableStateOf<List<DvrApi.SeasonPass>>(emptyList()) }
    var tab by remember { mutableStateOf(MyRecordingsTab.RECORDINGS) }

    // Auto-refresh every 5 s so RECORDING rows tick forward and the
    // status flips to COMPLETED/FAILED the moment ffmpeg exits.
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

    Column(Modifier.fillMaxSize().background(BgBlack)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton { nav.popBackStack() }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "My Recordings",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Live TV captures stay for 14 days, then auto-delete.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        if (userId == null) {
            EmptyState(
                title = "Sign in to see recordings",
                subtitle = "Pick a profile from the home screen first.",
            )
            return@Column
        }

        QuotaBar(quota)
        Spacer(Modifier.height(8.dp))

        // Tab strip
        Row(
            Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabChip("Recordings (${recordings.size})", tab == MyRecordingsTab.RECORDINGS) {
                tab = MyRecordingsTab.RECORDINGS
            }
            TabChip(
                "Scheduled (${scheduled.count { it.status == "pending" }})",
                tab == MyRecordingsTab.SCHEDULED,
            ) { tab = MyRecordingsTab.SCHEDULED }
            TabChip("Season Passes (${seasonPasses.size})", tab == MyRecordingsTab.PASSES) {
                tab = MyRecordingsTab.PASSES
            }
        }

        when (tab) {
            MyRecordingsTab.RECORDINGS -> RecordingsTabContent(
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
            MyRecordingsTab.SCHEDULED -> ScheduledTabContent(
                scheduled = scheduled.filter { it.status == "pending" },
                userId = userId,
                onRefresh = {
                    scope.launch { scheduled = DvrApi.listScheduled(userId) }
                },
            )
            MyRecordingsTab.PASSES -> SeasonPassesTabContent(
                passes = seasonPasses,
                scheduledCountFor = { passId ->
                    scheduled.count { it.season_pass_id == passId && it.status == "pending" }
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

private enum class MyRecordingsTab { RECORDINGS, SCHEDULED, PASSES }

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                if (active) Cyan.copy(alpha = 0.18f) else Color(0x14FFFFFF),
                RoundedCornerShape(99.dp),
            )
            .border(
                if (active) 2.dp else 1.dp,
                if (active) Cyan else Color(0x33FFFFFF),
                RoundedCornerShape(99.dp),
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickableWithEnter(onClick),
    ) {
        Text(
            label,
            color = if (active) Cyan else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun RecordingsTabContent(
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
                Text("Loading…", color = TextSecondary, fontSize = 15.sp)
            }
        }
        recordings.isEmpty() -> {
            EmptyState(
                title = "No recordings yet",
                subtitle = "Press Record while watching any live channel — it shows up here immediately.",
            )
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recordings, key = { it.rec_id }) { rec ->
                    RecordingRow(
                        rec = rec,
                        onPlay = {
                            if (!rec.isPlayable) return@RecordingRow
                            val uid = userId ?: return@RecordingRow
                            val url = DvrApi.streamUrl(uid, rec.rec_id)
                            val encoded = android.net.Uri.encode(url)
                            val label = rec.show_title.ifBlank { rec.channel_name }
                                .ifBlank { "Recording" }
                            val name = android.net.Uri.encode(label)
                            nav.navigate("player/$playlistId/$encoded/$name/false")
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
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ScheduledTabContent(
    scheduled: List<DvrApi.Scheduled>,
    userId: String?,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (scheduled.isEmpty()) {
        EmptyState(
            title = "Nothing scheduled",
            subtitle = "Open the TV Guide, click any future show and tap Schedule recording.",
        )
        return
    }
    val fmt = remember { SimpleDateFormat("EEE, MMM d  h:mm a", Locale.US) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(scheduled, key = { it.sched_id }) { s ->
            var focused by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (focused) Color(0x2206B6D4) else Color(0x14FFFFFF),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) Cyan else Color(0x22FFFFFF),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused },
            ) {
                Icon(
                    Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.show_title.ifBlank { s.channel_name },
                        color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${s.channel_name} · ${fmt.format(Date(s.start_at_epoch * 1000L))}",
                        color = TextSecondary, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = Color(0x22EF4444),
                    shape = RoundedCornerShape(99.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .focusable()
                        .clickableWithEnter {
                            val uid = userId ?: return@clickableWithEnter
                            scope.launch {
                                DvrApi.cancelScheduled(uid, s.sched_id)
                                onRefresh()
                            }
                        },
                ) {
                    Text(
                        "Cancel",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SeasonPassesTabContent(
    passes: List<DvrApi.SeasonPass>,
    scheduledCountFor: (String) -> Int,
    userId: String?,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (passes.isEmpty()) {
        EmptyState(
            title = "No season passes",
            subtitle = "From the TV Guide, click any series episode → Record entire series.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(passes, key = { it.pass_id }) { p ->
            var focused by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (focused) Color(0x228B5CF6) else Color(0x14FFFFFF),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) Color(0xFF8B5CF6) else Color(0x22FFFFFF),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused },
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.series_title,
                        color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    val n = scheduledCountFor(p.pass_id)
                    Text(
                        "${p.channel_name}  ·  $n upcoming episode${if (n == 1) "" else "s"} scheduled",
                        color = TextSecondary, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = Color(0x22EF4444),
                    shape = RoundedCornerShape(99.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .focusable()
                        .clickableWithEnter {
                            val uid = userId ?: return@clickableWithEnter
                            scope.launch {
                                DvrApi.deleteSeasonPass(uid, p.pass_id)
                                onRefresh()
                            }
                        },
                ) {
                    Text(
                        "Cancel pass",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private val DvrApi.Recording.isPlayable: Boolean
    get() = status != "failed" && size_bytes > 0

@Composable
private fun QuotaBar(q: DvrApi.Quota?) {
    val usedH = (q?.used_s ?: 0) / 3600.0
    val quotaH = (q?.quota_s ?: 72000) / 3600.0
    val remainingH = (q?.available_s ?: 72000) / 3600.0
    val pct = if (quotaH > 0) (usedH / quotaH).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(Modifier.padding(horizontal = 48.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Storage",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "%.1f h used · %.1f h left".format(usedH, remainingH),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0x22FFFFFF), RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .background(Cyan, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "%.0f h total".format(quotaH),
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FiberManualRecord, null,
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}

// Live-ticking "now" epoch so RECORDING rows animate smoothly without
// hammering the server.
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
private fun RecordingRow(
    rec: DvrApi.Recording,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onToggleWatched: () -> Unit = {},
) {
    var cardFocused by remember { mutableStateOf(false) }
    var delFocused by remember { mutableStateOf(false) }
    var watchFocused by remember { mutableStateOf(false) }
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

    // Elapsed / remaining for RECORDING rows (driven by nowEpoch so
    // time-left decrements every second).
    val elapsed = (nowEpoch - rec.started_at_epoch).coerceAtLeast(0L).toInt()
    val scheduled = rec.duration_s.coerceAtLeast(0)
    val remaining = (scheduled - elapsed).coerceAtLeast(0)

    // Click action depends on state:
    //   failed   → delete (there's nothing to play)
    //   other    → play
    val onCardClick: () -> Unit = if (isFailed) onDelete else onPlay

    val leadingColor = when {
        isFailed -> Color(0xFFEF4444)
        isRecording -> Color(0xFFEF4444)
        else -> Cyan
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (cardFocused) Color(0x261E90FF) else Color(0x08FFFFFF),
                RoundedCornerShape(12.dp),
            )
            .border(
                if (cardFocused) 2.dp else 1.dp,
                if (cardFocused) Cyan else Color(0x14FFFFFF),
                RoundedCornerShape(12.dp),
            )
            .alpha(if (rec.watched) 0.55f else 1f)
            .onFocusChanged { cardFocused = it.isFocused }
            .focusable()
            .clickableWithEnter(onCardClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val leadingIcon: ImageVector = when {
            isFailed -> Icons.Default.ErrorOutline
            isRecording -> Icons.Default.FiberManualRecord
            else -> Icons.Default.PlayArrow
        }
        Icon(leadingIcon, null, tint = leadingColor, modifier = Modifier.size(22.dp))

        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    primary, color = Color.White,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                )
                if (rec.watched) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        color = Color(0x2206B6D4),
                        shape = RoundedCornerShape(99.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.55f)),
                    ) {
                        Text(
                            "✓ WATCHED",
                            color = Cyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            if (secondary.isNotEmpty()) {
                Text(secondary, color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            StatusStrip(
                rec = rec,
                elapsed = elapsed,
                remaining = remaining,
                scheduled = scheduled,
            )
        }

        // Mark-as-watched button: only for completed (and non-failed)
        // recordings. Toggles between "mark watched" and "mark
        // unwatched" so the user can flip a row back if they
        // tagged it by accident.
        if (isCompleted) {
            Spacer(Modifier.width(8.dp))
            val watchTint = if (rec.watched) Color(0xFFFACC15) else Cyan
            Surface(
                color = if (watchFocused) watchTint.copy(alpha = 0.22f) else Color(0x14FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .border(
                        if (watchFocused) 2.dp else 0.dp,
                        if (watchFocused) watchTint else Color.Transparent,
                        CircleShape,
                    )
                    .onFocusChanged { watchFocused = it.isFocused }
                    .focusable()
                    .clickableWithEnter(onToggleWatched),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (rec.watched) "↺" else "✓",
                        color = if (watchFocused) watchTint else Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))
        val (delIcon, delLabel) = if (isRecording)
            Icons.Default.Stop to "Stop this recording"
        else
            Icons.Default.DeleteOutline to "Delete this recording"

        Surface(
            color = if (delFocused) Color(0x33EF4444) else Color(0x14FFFFFF),
            shape = CircleShape,
            modifier = Modifier
                .size(42.dp)
                .border(
                    if (delFocused) 2.dp else 0.dp,
                    if (delFocused) Color(0xFFEF4444) else Color.Transparent,
                    CircleShape,
                )
                .onFocusChanged { delFocused = it.isFocused }
                .focusable()
                .clickableWithEnter(onDelete),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    delIcon, delLabel,
                    tint = if (delFocused) Color(0xFFFCA5A5) else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(
    rec: DvrApi.Recording,
    elapsed: Int,
    remaining: Int,
    scheduled: Int,
) {
    val isRecording = rec.status == "recording"
    val isFailed = rec.status == "failed"
    val isCompleted = rec.status == "completed"

    when {
        isRecording -> {
            // Clamp counters to the scheduled duration so the UI
            // never shows "negative remaining" and flips from the
            // live countdown into a "Finishing…" affordance the moment
            // we hit the cap. The backend enforces a hard wall-clock
            // cut-off + 10 s grace, so status will turn to COMPLETED
            // within a single poll.
            val displayElapsed = if (scheduled > 0) elapsed.coerceAtMost(scheduled) else elapsed
            val pastDue = scheduled > 0 && elapsed >= scheduled
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(text = "● RECORDING", color = Color(0xFFEF4444))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (pastDue)
                            "${formatClock(displayElapsed)} recorded · Finishing…"
                        else
                            "${formatClock(elapsed)} recorded · ${formatClock(remaining)} left",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ProgressLine(
                    progress = if (scheduled > 0) displayElapsed / scheduled.toFloat() else 0f,
                    color = Color(0xFFEF4444),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pastDue)
                        "Wrapping up — recording will save in a few seconds."
                    else
                        "Press OK to watch from the start, even while recording.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        isFailed -> {
            Column {
                Badge(text = "FAILED", color = Color(0xFFEF4444))
                if (rec.fail_reason.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        rec.fail_reason,
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        modifier = Modifier.widthIn(max = 640.dp),
                    )
                }
            }
        }
        isCompleted -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(text = "COMPLETED", color = Cyan)
                Spacer(Modifier.width(10.dp))
                Text(
                    buildString {
                        append(formatDuration(rec.duration_s))
                        if (rec.size_bytes > 0) {
                            append(" · ")
                            append(formatSize(rec.size_bytes))
                        }
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        else -> {
            Text(rec.status.uppercase(), color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun ProgressLine(progress: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0x22FFFFFF), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = Color(0x1AFFFFFF), shape = CircleShape,
        modifier = Modifier
            .size(44.dp)
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Cyan else Color.Transparent,
                CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// "5m 32s" / "1h 12m"
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

// "H:MM:SS" / "MM:SS"
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
