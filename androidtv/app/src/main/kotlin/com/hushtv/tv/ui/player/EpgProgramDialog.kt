package com.hushtv.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.data.DvrApi
import com.hushtv.tv.data.EpgProgram
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.screens.clickableWithEnter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared EPG program info popup. Powers the Phase 2 / Phase 3 user
 * flow — tap any program cell on the EPG grid (or long-press a
 * channel) and you get this dialog with context-appropriate
 * Watch / Record / Schedule / Season-Pass / Cancel chips.
 *
 * The dialog auto-determines its action set from [program] timing:
 *   • Past   → just info (no actions)
 *   • Live   → "Watch", "Record now", "Cancel recording" (if active)
 *   • Future → "Schedule recording", "Cancel scheduled" (if already)
 *
 * Series detection: if [program.title] contains an episode marker
 * (S##E## / "Season N Episode M") we surface a "Record entire
 * series" chip that creates a Season Pass on the server using
 * [upcomingProgramsOnChannel] as the EPG snapshot.
 */
@Composable
fun EpgProgramDialog(
    playlistId: String,
    channel: MediaCard,
    program: EpgProgram,
    upcomingProgramsOnChannel: List<EpgProgram>,
    existingScheduled: List<DvrApi.Scheduled>,
    onDismiss: () -> Unit,
    onWatchLive: () -> Unit,
    onRecordNow: () -> Unit,
    onStopRecording: () -> Unit,
    isCurrentlyRecording: Boolean,
    onToast: (String) -> Unit,
    onSchedulingChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }
    val userId = remember(playlist) { playlist?.let { DvrApi.userIdFor(it) } }

    val now = remember { System.currentTimeMillis() }
    val isLive = program.startMs <= now && now < program.stopMs
    val isPast = program.stopMs <= now
    val isFuture = program.startMs > now

    val epgId = remember(program, channel) {
        // Stable id used by the server to dedupe season-pass schedules.
        "ch${channel.streamId}-s${program.startMs / 1000L}"
    }
    val alreadyScheduled = remember(existingScheduled, epgId) {
        existingScheduled.firstOrNull {
            it.epg_id == epgId && it.status == "pending"
        }
    }
    val hasEpisodeMarker = remember(program.title) {
        EPISODE_MARKER_RE.containsMatchIn(program.title)
    }

    val firstChip = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstChip.requestFocus() }

    val dateFmt = remember { SimpleDateFormat("EEE, MMM d  h:mm a", Locale.US) }
    val durFmt: (Long) -> String = remember {
        { ms -> "${(ms / 60_000L).toInt()} min" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = Color(0xFF0A101D),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(16.dp)
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp && ev.key == Key.Back) {
                        onDismiss(); true
                    } else false
                },
        ) {
            Column(Modifier.padding(28.dp)) {
                // Header — channel + status badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.title.uppercase(Locale.US),
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    when {
                        isCurrentlyRecording -> StatusPill("● REC", Color(0xFFEF4444))
                        alreadyScheduled != null -> StatusPill("⏰ SCHEDULED", Cyan)
                        isLive -> StatusPill("LIVE", Cyan)
                        isPast -> StatusPill("AIRED", Color(0xFF94A3B8))
                        else -> StatusPill("UPCOMING", Color(0xFF94A3B8))
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    program.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${dateFmt.format(Date(program.startMs))}  ·  ${durFmt(program.durationMs)}",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )

                if (program.description.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        program.description,
                        color = Color(0xFFE5E7EB),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 6,
                    )
                }

                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Live action — Watch
                    if (isLive) {
                        ActionChip(
                            label = "Watch",
                            icon = Icons.Filled.PlayArrow,
                            tint = Cyan,
                            modifier = Modifier.focusRequester(firstChip),
                            onClick = { onWatchLive(); onDismiss() },
                        )
                    }

                    // Live action — Record / Stop
                    if (isLive) {
                        if (isCurrentlyRecording) {
                            ActionChip(
                                label = "Stop recording",
                                icon = Icons.Filled.Close,
                                tint = Color(0xFFEF4444),
                                onClick = { onStopRecording(); onDismiss() },
                            )
                        } else {
                            ActionChip(
                                label = "Record now",
                                icon = Icons.Filled.FiberManualRecord,
                                tint = Color(0xFFEF4444),
                                modifier = if (!isLive) Modifier else Modifier,
                                onClick = { onRecordNow(); onDismiss() },
                            )
                        }
                    }

                    // Future action — Schedule / Cancel scheduled
                    if (isFuture) {
                        if (alreadyScheduled != null) {
                            ActionChip(
                                label = "Cancel scheduled",
                                icon = Icons.Filled.Close,
                                tint = Color(0xFFFACC15),
                                modifier = Modifier.focusRequester(firstChip),
                                onClick = {
                                    val uid = userId ?: return@ActionChip
                                    scope.launch {
                                        val ok = DvrApi.cancelScheduled(uid, alreadyScheduled.sched_id)
                                        onToast(if (ok) "Scheduled recording cancelled."
                                                else "Couldn't cancel — try again.")
                                        if (ok) onSchedulingChanged()
                                        onDismiss()
                                    }
                                },
                            )
                        } else {
                            ActionChip(
                                label = "Schedule recording",
                                icon = Icons.Filled.CalendarMonth,
                                tint = Cyan,
                                modifier = Modifier.focusRequester(firstChip),
                                onClick = {
                                    val uid = userId ?: return@ActionChip
                                    val pl = playlist ?: return@ActionChip
                                    val url = XtreamApi.liveUrl(pl.host, pl.username, pl.password, channel.streamId)
                                    val creds = DvrApi.XtreamCreds(
                                        host = pl.host,
                                        username = pl.username,
                                        password = pl.password,
                                        stream_id = channel.streamId,
                                    )
                                    scope.launch {
                                        val res = DvrApi.schedule(
                                            userId = uid,
                                            channelUrl = url,
                                            channelName = channel.title,
                                            showTitle = program.title,
                                            startAtEpoch = program.startMs / 1000L,
                                            endAtEpoch = program.stopMs / 1000L,
                                            epgId = epgId,
                                            xtream = creds,
                                        )
                                        when (res) {
                                            is DvrApi.ScheduleResult.Success ->
                                                onToast("Recording scheduled for ${dateFmt.format(Date(program.startMs))}.")
                                            is DvrApi.ScheduleResult.Error ->
                                                onToast(res.message)
                                        }
                                        onSchedulingChanged()
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    }

                    // Series action — Season Pass (any future occurrence
                    // with an episode marker triggers it).
                    if ((isFuture || isLive) && hasEpisodeMarker) {
                        ActionChip(
                            label = "Record entire series",
                            icon = Icons.Filled.Repeat,
                            tint = Color(0xFF8B5CF6),
                            onClick = {
                                val uid = userId ?: return@ActionChip
                                val pl = playlist ?: return@ActionChip
                                val url = XtreamApi.liveUrl(pl.host, pl.username, pl.password, channel.streamId)
                                val seriesTitle = stripEpisodeMarker(program.title)
                                val nowS = System.currentTimeMillis() / 1000L
                                val upcoming = upcomingProgramsOnChannel
                                    .asSequence()
                                    .filter { it.startMs / 1000L > nowS }
                                    .filter { sameSeries(seriesTitle, it.title) }
                                    .map { p ->
                                        DvrApi.SeasonPassProgram(
                                            title = p.title,
                                            start_at_epoch = p.startMs / 1000L,
                                            end_at_epoch = p.stopMs / 1000L,
                                            epg_id = "ch${channel.streamId}-s${p.startMs / 1000L}",
                                        )
                                    }
                                    .toList()
                                scope.launch {
                                    val res = DvrApi.createSeasonPass(
                                        userId = uid,
                                        seriesTitle = seriesTitle,
                                        channelId = channel.streamId,
                                        channelName = channel.title,
                                        channelUrl = url,
                                        upcomingPrograms = upcoming,
                                        xtream = DvrApi.XtreamCreds(
                                            host = pl.host,
                                            username = pl.username,
                                            password = pl.password,
                                            stream_id = channel.streamId,
                                        ),
                                    )
                                    when (res) {
                                        is DvrApi.SeasonPassResult.Success -> {
                                            val n = res.pass.scheduled_count
                                            onToast("Season pass set — $n upcoming episode${if (n == 1) "" else "s"} scheduled.")
                                        }
                                        is DvrApi.SeasonPassResult.Error ->
                                            onToast(res.message)
                                    }
                                    onSchedulingChanged()
                                    onDismiss()
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    if (isPast) "This program has finished airing."
                    else if (isLive && isCurrentlyRecording)
                        "Recording will stop automatically when the show ends."
                    else if (isLive) "Press Record now to capture the rest of this show."
                    else if (alreadyScheduled != null)
                        "Already scheduled — cancel anytime before it starts."
                    else "Only one recording at a time. The show will be saved to My Recordings.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                if (focused) tint.copy(alpha = 0.22f) else Color(0x14FFFFFF),
                RoundedCornerShape(99.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) tint else Color(0x33FFFFFF),
                shape = RoundedCornerShape(99.dp),
            )
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickableWithEnter(onClick),
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (focused) tint else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (focused) tint else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val EPISODE_MARKER_RE = Regex(
    """\s*[-–—:]?\s*(?:S\d{1,2}\s*E\d{1,3}|\d{1,2}x\d{1,3}|Season\s+\d{1,2}\s*(?:Episode\s*\d{1,3})?).*$""",
    RegexOption.IGNORE_CASE,
)

private fun stripEpisodeMarker(title: String): String =
    EPISODE_MARKER_RE.replace(title.trim(), "").trim().trim('-', '–', '—', ':').trim()

private fun sameSeries(seriesTitle: String, programTitle: String): Boolean {
    val a = seriesTitle.trim().lowercase(Locale.US)
    val b = stripEpisodeMarker(programTitle).lowercase(Locale.US)
    if (a.isBlank() || b.isBlank()) return false
    return a == b || a.startsWith(b) || b.startsWith(a)
}

// Helper imported from ui.screens.ClickWithEnter (handles D-pad
// center + tap on the same modifier so this dialog works on remotes
// and touch surfaces alike).
