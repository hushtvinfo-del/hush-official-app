package com.hushtv.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.DvrApi
import com.hushtv.tv.data.EpgService
import com.hushtv.tv.data.NavState
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Record button for the Live TV fullscreen OSD.
 *
 * Lives in the bottom controls row alongside Play/Pause and Mute so
 * it's reachable with a D-pad right from them — no special focus
 * acrobatics. Renders as a Surface-pill to match the visual weight of
 * the circle buttons next to it.
 *
 * States
 * ──────
 * • idle       → red dot + "REC". OK fires a Cloud-DVR record-now.
 * • busy       → "Starting…" while the POST is in flight.
 * • recording  → red pulsing dot + "REC" label on a red background.
 *                Detected by polling the DVR list every 6 s for an
 *                entry with status="recording" matching the current
 *                channel name. OK stops the recording.
 * • stopping   → "Stopping…" while the DELETE is in flight.
 * • err        → surfaces the server detail for ~3 s then returns to
 *                idle.
 *
 * Duration is automatically clamped to the end of the currently-airing
 * EPG show + a small pad. When no EPG is available the backend falls
 * back to a 1-hour rolling capture (ffmpeg `-t` cap).
 */
@Composable
fun RecordNowChip(
    playlistId: String,
    channelName: String,
    channelUrl: String,
    onInteract: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }

    // "idle" | "busy" | "recording" | "stopping" | "err"
    var state by remember(channelName) { mutableStateOf("idle") }
    var toast by remember { mutableStateOf<String?>(null) }
    var activeRecId by remember(channelName) { mutableStateOf<String?>(null) }

    // Poll for active-recording state so if a recording is already
    // running (e.g. started from the long-press dialog, or still live
    // from a previous session) the OSD button reflects that.
    LaunchedEffect(playlistId, channelName) {
        while (true) {
            val playlist = PlaylistStore.find(ctx, playlistId)
            if (playlist != null) {
                val uid = DvrApi.userIdFor(playlist)
                val active = DvrApi.findActive(uid, channelName)
                // Only swap to/from "recording" when we're idle or already
                // in the recording state — don't clobber busy/stopping/err.
                if (state == "idle" && active != null) {
                    activeRecId = active.rec_id
                    state = "recording"
                } else if (state == "recording" && active == null) {
                    activeRecId = null
                    state = "idle"
                }
            }
            delay(6_000)
        }
    }

    LaunchedEffect(state) {
        if (state == "err") {
            delay(3_000)
            state = if (activeRecId != null) "recording" else "idle"
            toast = null
        }
    }

    val shape = RoundedCornerShape(32.dp)
    val (label, dotColor, bg) = when (state) {
        "busy" -> Triple("Starting…", Color(0xFFFACC15), Color(0x33FACC15))
        "recording" -> Triple("● REC", Color(0xFFEF4444), Color(0x66EF4444))
        "stopping" -> Triple("Stopping…", Color(0xFFFACC15), Color(0x33FACC15))
        "err" -> Triple(toast ?: "Couldn't record", Color(0xFFEF4444), Color(0x33EF4444))
        else -> Triple("REC", Color(0xFFEF4444), Color(0x26FFFFFF))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(56.dp)
            .clip(shape)
            .background(if (focused) Color(0x99EF4444) else bg)
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color(0xFFEF4444) else Color.Transparent,
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter {
                onInteract()
                when (state) {
                    "idle" -> startRecording(
                        ctx = ctx,
                        scope = scope,
                        playlistId = playlistId,
                        channelName = channelName,
                        channelUrl = channelUrl,
                        setState = { state = it },
                        setToast = { toast = it },
                        setActiveRecId = { activeRecId = it },
                    )
                    "recording" -> stopRecording(
                        ctx = ctx,
                        scope = scope,
                        playlistId = playlistId,
                        channelName = channelName,
                        recId = activeRecId,
                        setState = { state = it },
                        setToast = { toast = it },
                        setActiveRecId = { activeRecId = it },
                    )
                    else -> Unit // busy / stopping / err → ignore
                }
            }
            .padding(horizontal = 20.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
        )
    }
}

private fun startRecording(
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    playlistId: String,
    channelName: String,
    channelUrl: String,
    setState: (String) -> Unit,
    setToast: (String?) -> Unit,
    setActiveRecId: (String?) -> Unit,
) {
    val playlist = PlaylistStore.find(ctx, playlistId) ?: run {
        setState("err"); setToast("No profile"); return
    }
    val uid = DvrApi.userIdFor(playlist)
    setState("busy")
    scope.launch {
        val nowShow = resolveNowPlaying(playlist)
        val result = DvrApi.recordNow(
            userId = uid,
            channelUrl = channelUrl,
            channelName = channelName,
            showTitle = nowShow?.title.orEmpty(),
            showEndsAtEpoch = (nowShow?.stopMs ?: 0L) / 1000L,
        )
        when (result) {
            is DvrApi.RecordNowResult.Success -> {
                setActiveRecId(result.rec.rec_id)
                setState("recording")
                setToast(null)
                android.widget.Toast.makeText(
                    ctx,
                    "Recording \"$channelName\" — find it under My Recordings.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            is DvrApi.RecordNowResult.Error -> {
                setState("err")
                setToast(result.message.take(60))
                android.widget.Toast.makeText(
                    ctx, result.message, android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

private fun stopRecording(
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    playlistId: String,
    channelName: String,
    recId: String?,
    setState: (String) -> Unit,
    setToast: (String?) -> Unit,
    setActiveRecId: (String?) -> Unit,
) {
    val playlist = PlaylistStore.find(ctx, playlistId) ?: run {
        setState("err"); setToast("No profile"); return
    }
    val uid = DvrApi.userIdFor(playlist)
    setState("stopping")
    scope.launch {
        val id = recId ?: DvrApi.findActive(uid, channelName)?.rec_id
        val ok = if (id != null) DvrApi.delete(uid, id) else false
        if (ok) {
            setActiveRecId(null)
            setState("idle")
            android.widget.Toast.makeText(
                ctx,
                "Stopped recording \"$channelName\".",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } else {
            setState("err")
            setToast("Couldn't stop")
        }
    }
}

/**
 * Best-effort resolution of the current EPG show for the channel being
 * played. Uses [NavState.liveChannels] + [NavState.currentChannelIndex]
 * to recover the streamId, then goes through [EpgService] which reads
 * from its in-memory cache first (typically already primed by the Live
 * Browse screen). On cache miss we fire a short-EPG fetch so the
 * next press (or running recording re-query) gets a hit.
 */
private suspend fun resolveNowPlaying(
    playlist: com.hushtv.tv.data.Playlist,
): com.hushtv.tv.data.EpgProgram? {
    val channel = NavState.channelAt(NavState.currentChannelIndex) ?: return null
    if (channel.streamId <= 0) return null
    EpgService.nowPlaying(channel.streamId)?.let { return it }
    runCatching {
        EpgService.fetchShortEpg(
            host = playlist.host,
            username = playlist.username,
            password = playlist.password,
            streamId = channel.streamId,
        )
    }
    return EpgService.nowPlaying(channel.streamId)
}
