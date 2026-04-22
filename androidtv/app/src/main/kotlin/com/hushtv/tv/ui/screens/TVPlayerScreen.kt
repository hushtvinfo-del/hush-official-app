package com.hushtv.tv.ui.screens

import android.view.KeyEvent
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.hushtv.tv.data.LastChannelStore
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TVPlayerScreen(
    nav: NavController,
    playlistId: String,
    streamUrl: String,
    channelName: String,
    isLive: Boolean
) {
    val ctx = LocalContext.current

    // Persist "last watched live channel" so next cold launch resumes here.
    LaunchedEffect(streamUrl, channelName, isLive, playlistId) {
        if (isLive && playlistId.isNotBlank()) {
            LastChannelStore.save(
                ctx = ctx,
                playlistId = playlistId,
                streamUrl = streamUrl,
                channelName = channelName
            )
        }
    }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var controlsTick by remember { mutableStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    LaunchedEffect(controlsTick) {
        showControls = true
        delay(4000)
        showControls = false
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                controlsTick++
                when (e.key) {
                    Key.Enter, Key.DirectionCenter, Key.MediaPlayPause, Key.Spacebar, Key.NumPadEnter -> {
                        if (player.isPlaying) player.pause() else player.play(); true
                    }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); true }
                    Key.DirectionRight -> {
                        player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0))); true
                    }
                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); true
                    }
                    Key.DirectionUp -> {
                        player.volume = (player.volume + 0.1f).coerceAtMost(1f); true
                    }
                    Key.DirectionDown -> {
                        player.volume = (player.volume - 0.1f).coerceAtLeast(0f); true
                    }
                    Key.Back, Key.Escape -> { nav.popBackStack(); true }
                    else -> false
                }
            }
        ,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay controls
        if (showControls) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xB3000000),
                            0.3f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1.0f to Color(0xD9000000)
                        )
                    )
            ) {
                // Top bar — back + title
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0x80000000),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .tvFocusable(shape = CircleShape)
                            .clickableWithEnter { nav.popBackStack() }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(channelName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        if (isLive) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text("● LIVE", color = Color(0xFFFCA5A5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bottom controls
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 40.dp)
                ) {
                    if (!isLive && durationMs > 0) {
                        val pct = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color(0x40FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(pct)
                                    .height(6.dp)
                                    .background(Cyan, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(formatTime(positionMs), color = Color(0xFF9CA3AF), fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatTime(durationMs), color = Color(0xFF9CA3AF), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0x26FFFFFF),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(64.dp)
                                .tvFocusable(shape = CircleShape)
                                .clickableWithEnter {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null, tint = Color.White, modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Surface(
                            color = Color(0x26FFFFFF),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(64.dp)
                                .tvFocusable(shape = CircleShape)
                                .clickableWithEnter {
                                    muted = !muted
                                    player.volume = if (muted) 0f else 1f
                                }
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    null, tint = Color.White, modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "← → Skip 10s   ↑ ↓ Volume   Back = Exit",
                            color = Color(0xFFD1D5DB),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
