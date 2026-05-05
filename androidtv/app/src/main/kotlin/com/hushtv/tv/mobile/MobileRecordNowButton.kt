package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.DvrApi
import com.hushtv.tv.data.EpgService
import com.hushtv.tv.data.NavState
import com.hushtv.tv.data.PlaylistStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compact "● REC" pill anchored to the mobile player's top bar. Tap
 * once to start recording the current live channel via the Cloud DVR
 * backend. On success the chip flips to "RECORDING" for ~3 s so a
 * user can visually confirm before the chip returns to idle.
 */
@Composable
fun MobileRecordNowButton(
    playlistId: String,
    channelName: String,
    channelUrl: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("idle") }

    LaunchedEffect(state) {
        if (state == "ok" || state == "err") {
            delay(3_000)
            state = "idle"
        }
    }

    val (label, dotColor) = when (state) {
        "busy" -> "…" to Color(0xFFFACC15)
        "ok" -> "REC" to Color(0xFFEF4444)
        "err" -> "!" to Color(0xFFEF4444)
        else -> "REC" to Color(0xFFEF4444)
    }
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(Color(0xCC1F0606))
            .border(1.dp, Color(0x55EF4444), shape)
            .clickable {
                if (state != "idle") return@clickable
                val playlist = PlaylistStore.find(ctx, playlistId) ?: run {
                    state = "err"; return@clickable
                }
                val uid = DvrApi.userIdFor(playlist)
                state = "busy"
                scope.launch {
                    val show = resolveNow(playlist)
                    val result = DvrApi.recordNow(
                        userId = uid,
                        channelUrl = channelUrl,
                        channelName = channelName,
                        showTitle = show?.title.orEmpty(),
                        showEndsAtEpoch = (show?.stopMs ?: 0L) / 1000L,
                    )
                    when (result) {
                        is DvrApi.RecordNowResult.Success -> {
                            state = "ok"
                            android.widget.Toast.makeText(
                                ctx,
                                "Recording started — check My Recordings.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                        is DvrApi.RecordNowResult.Error -> {
                            state = "err"
                            android.widget.Toast.makeText(
                                ctx, result.message, android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
        )
    }
}

private suspend fun resolveNow(
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
