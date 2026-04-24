package com.hushtv.tv.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.hushtv.tv.ai.PcmTapAudioProcessor
import com.hushtv.tv.ai.VoskCaptionEngine
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mobile player — touch-first.
 *
 * Gestures:
 *   • Tap anywhere → toggle controls visibility.
 *   • Double-tap left third → seek -10 s.
 *   • Double-tap right third → seek +10 s.
 *   • Drag on progress bar → scrub (simple linear map).
 *
 * Reuses the exact same AI-CC engine as the TV player so mobile users
 * benefit from bundled Vosk captions without any extra server cost.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MobilePlayerScreen(
    nav: NavController,
    playlistId: String,
    streamUrl: String,
    channelName: String,
    isLive: Boolean,
    liveCategoryId: String? = null,
    vodStreamId: Int? = null,
    vodKind: String? = null,
    vodPoster: String? = null,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Live-TV only — load the full channel list for this category so we
    // can hop to the next / previous channel without backing out.
    val playlist = remember(playlistId) {
        com.hushtv.tv.data.PlaylistStore.find(ctx, playlistId)
    }
    var liveChannels by remember { mutableStateOf<List<com.hushtv.tv.data.MediaCard>>(emptyList()) }
    LaunchedEffect(liveCategoryId, playlistId, isLive) {
        if (!isLive || playlist == null) return@LaunchedEffect
        val list = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (liveCategoryId.isNullOrBlank()) {
                    com.hushtv.tv.data.XtreamApi.getAllStreams(
                        playlist.host, playlist.username, playlist.password, "live",
                    )
                } else {
                    com.hushtv.tv.data.XtreamApi.getStreamsForCategory(
                        playlist.host, playlist.username, playlist.password, "live", liveCategoryId,
                    )
                }
            }
        }.getOrDefault(emptyList())
        liveChannels = list
    }

    // State-backed so the screen can repaint when user hits ±channel.
    var currentStreamUrl by remember { mutableStateOf(streamUrl) }
    var currentTitle by remember { mutableStateOf(channelName) }

    // Lock to landscape for comfortable viewing, keep screen on.
    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        val prevOri = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val win = activity?.window
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Record this live channel in the "Recent" MRU so the Live TV
        // hub can show it above the main list next time the user opens
        // it. VOD plays are tracked by WatchProgressStore separately.
        if (isLive && playlist != null) {
            val card = liveChannels.firstOrNull()
            val streamIdHint = runCatching {
                android.net.Uri.parse(streamUrl).lastPathSegment?.substringBeforeLast('.')?.toIntOrNull()
            }.getOrNull()
            if (streamIdHint != null) {
                com.hushtv.tv.data.RecentChannelStore.pushFront(ctx, playlistId, streamIdHint)
            } else if (card != null) {
                com.hushtv.tv.data.RecentChannelStore.pushFront(ctx, playlistId, card.streamId)
            }
        }
        onDispose {
            activity?.requestedOrientation = prevOri ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // AI captions wiring — same as TV player.
    val pcmTap = remember { PcmTapAudioProcessor() }
    var aiCaptions by rememberSaveable { mutableStateOf(false) }
    val aiCaptionText by VoskCaptionEngine.text.collectAsState()

    val player = remember {
        val renderersFactory = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(pcmTap))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
        ExoPlayer.Builder(ctx, renderersFactory).build().apply {
            setMediaItem(MediaItem.fromUri(currentStreamUrl))
            prepare()
            // Disable audio offload so the PCM tap (AI captions) sees
            // decoded samples. Offload bypasses processors entirely.
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    androidx.media3.common.TrackSelectionParameters
                        .AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            androidx.media3.common.TrackSelectionParameters
                                .AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                        )
                        .build(),
                )
                .build()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // ── Resume from saved position (VOD only) ──
    // Using an AtomicBoolean-style one-shot flag so re-seeking doesn't
    // happen on orientation changes or on channel-flips within the
    // same player instance.
    var resumeApplied by remember { mutableStateOf(false) }
    LaunchedEffect(vodStreamId, vodKind) {
        if (isLive || vodStreamId == null || vodStreamId <= 0 || vodKind.isNullOrBlank()) return@LaunchedEffect
        if (resumeApplied) return@LaunchedEffect
        val saved = com.hushtv.tv.data.WatchProgressStore.get(ctx, vodStreamId, vodKind)
        if (saved != null && saved.isInProgress) {
            // Small delay so ExoPlayer has actually parsed the stream
            // before we seek — otherwise the seek can be ignored.
            kotlinx.coroutines.delay(250)
            player.seekTo(saved.positionMs)
        }
        resumeApplied = true
    }

    // ── Periodic progress save (every 4 s while playing) ──
    LaunchedEffect(vodStreamId, vodKind) {
        if (isLive || vodStreamId == null || vodStreamId <= 0 || vodKind.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(4_000)
            val pos = player.currentPosition
            val dur = player.duration
            if (dur > 0 && pos > 1_000) {
                com.hushtv.tv.data.WatchProgressStore.save(
                    ctx = ctx,
                    streamId = vodStreamId,
                    kind = vodKind,
                    title = channelName,
                    poster = vodPoster,
                    positionMs = pos,
                    durationMs = dur,
                )
            }
        }
    }

    // Save one final time when leaving the screen so we capture the
    // exit position even if the 4-second tick hasn't fired yet.
    DisposableEffect(vodStreamId, vodKind) {
        onDispose {
            if (!isLive && vodStreamId != null && vodStreamId > 0 && !vodKind.isNullOrBlank()) {
                val pos = player.currentPosition
                val dur = player.duration
                if (dur > 0 && pos > 1_000) {
                    com.hushtv.tv.data.WatchProgressStore.save(
                        ctx = ctx,
                        streamId = vodStreamId,
                        kind = vodKind,
                        title = channelName,
                        poster = vodPoster,
                        positionMs = pos,
                        durationMs = dur,
                    )
                }
            }
        }
    }

    // Switching channels (or to a new movie) — swap the media item without
    // releasing the player so we don't blink the surface.
    LaunchedEffect(currentStreamUrl) {
        if (player.currentMediaItem?.localConfiguration?.uri?.toString() != currentStreamUrl) {
            player.setMediaItem(MediaItem.fromUri(currentStreamUrl))
            player.prepare()
            player.playWhenReady = true
        }
    }

    // Compute prev/next indices in the live channel list so we can
    // grey out the chevrons when we're at the edges.
    val currentIndex = remember(liveChannels, currentStreamUrl) {
        if (!isLive || liveChannels.isEmpty() || playlist == null) -1
        else liveChannels.indexOfFirst {
            com.hushtv.tv.data.XtreamApi.liveUrl(
                playlist.host, playlist.username, playlist.password, it.streamId,
            ) == currentStreamUrl
        }
    }
    val canPrev = isLive && currentIndex > 0
    val canNext = isLive && currentIndex in 0 until liveChannels.size - 1

    fun goChannel(delta: Int) {
        val p = playlist ?: return
        if (!isLive || liveChannels.isEmpty() || currentIndex < 0) return
        val nextIdx = (currentIndex + delta).coerceIn(0, liveChannels.size - 1)
        if (nextIdx == currentIndex) return
        val card = liveChannels[nextIdx]
        val url = com.hushtv.tv.data.XtreamApi.liveUrl(
            p.host, p.username, p.password, card.streamId,
        )
        com.hushtv.tv.data.RecentChannelStore.pushFront(ctx, playlistId, card.streamId)
        currentStreamUrl = url
        currentTitle = card.title
    }

    LaunchedEffect(Unit) { VoskCaptionEngine.prepare(ctx) }

    DisposableEffect(aiCaptions) {
        if (aiCaptions) {
            pcmTap.onPcm = { bytes, len -> VoskCaptionEngine.onPcmFrame(bytes, len) }
            scope.launch {
                repeat(30) {
                    val rate = pcmTap.tapSampleRate
                    val ch = pcmTap.tapChannelCount
                    if (rate > 0 && ch > 0) {
                        VoskCaptionEngine.start(scope, rate, ch)
                        return@launch
                    }
                    delay(200)
                }
            }
        } else {
            pcmTap.onPcm = null
            VoskCaptionEngine.stop()
        }
        onDispose { pcmTap.onPcm = null; VoskCaptionEngine.stop() }
    }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide controls after 4 s of inactivity.
    LaunchedEffect(controlsTick, showControls) {
        if (!showControls) return@LaunchedEffect
        delay(4000)
        if (isPlaying) showControls = false
    }

    // Width tracker for double-tap-side detection.
    var boxWidth by remember { mutableStateOf(1f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { boxWidth = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        if (showControls) controlsTick++
                    },
                    onDoubleTap = { offset ->
                        // Left third → rewind 10s, right third → fwd 10s.
                        val w = boxWidth
                        when {
                            offset.x < w / 3f -> player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                            offset.x > 2 * w / 3f -> player.seekTo(
                                (player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0))
                            )
                            else -> {
                                // Middle third: toggle playback.
                                if (isPlaying) player.pause() else player.play()
                            }
                        }
                        controlsTick++
                    },
                )
            },
    ) {
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

        // ── AI caption overlay (always on when enabled). ──
        if (aiCaptions) {
            val engineState by VoskCaptionEngine.state.collectAsState()
            var showPlaceholder by remember { mutableStateOf(false) }
            LaunchedEffect(aiCaptions) {
                if (aiCaptions) {
                    showPlaceholder = true
                    delay(4_000)
                    showPlaceholder = false
                } else {
                    showPlaceholder = false
                }
            }
            LaunchedEffect(aiCaptionText) {
                if (aiCaptionText.isNotBlank()) showPlaceholder = false
            }

            val overlayText: String? = when {
                aiCaptionText.isNotBlank() -> aiCaptionText
                engineState == VoskCaptionEngine.EngineState.ERROR ->
                    "AI captions unavailable on this stream"
                engineState == VoskCaptionEngine.EngineState.PREPARING ->
                    "Loading English speech model…"
                showPlaceholder -> "Listening · English only"
                else -> null
            }
            if (overlayText != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (showControls) 130.dp else 40.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            overlayText,
                            color = if (aiCaptionText.isNotBlank())
                                Color(0xFFFAFAFA)
                            else Color(0xFF94A3B8),
                            fontSize = if (aiCaptionText.isNotBlank()) 16.sp else 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }

        // ── Controls overlay ──
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // Dim scrim so controls pop.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000)),
            )
        }

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color.Transparent),
                        ))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x33000000))
                            .clickable { nav.popBackStack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        currentTitle,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isLive) {
                        // Channel position indicator (e.g. "42 / 318") —
                        // gives users a sense of place in the category.
                        if (currentIndex >= 0 && liveChannels.isNotEmpty()) {
                            Text(
                                "${currentIndex + 1}/${liveChannels.size}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Box(
                            Modifier
                                .background(Cyan, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("LIVE", color = Color(0xFF111111), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Center cluster — Replay / Play / Forward (VOD) or
                // Prev-Channel / Play / Next-Channel (Live).
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLive) {
                        CircleBtn(
                            Icons.Default.SkipPrevious,
                            52.dp,
                            enabled = canPrev,
                        ) {
                            goChannel(-1); controlsTick++
                        }
                        Spacer(Modifier.width(20.dp))
                    } else {
                        CircleBtn(Icons.Default.Replay10, 52.dp) {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                            controlsTick++
                        }
                        Spacer(Modifier.width(20.dp))
                    }
                    CircleBtn(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        66.dp,
                    ) {
                        if (isPlaying) player.pause() else player.play()
                        controlsTick++
                    }
                    if (isLive) {
                        Spacer(Modifier.width(20.dp))
                        CircleBtn(
                            Icons.Default.SkipNext,
                            52.dp,
                            enabled = canNext,
                        ) {
                            goChannel(+1); controlsTick++
                        }
                    } else {
                        Spacer(Modifier.width(20.dp))
                        CircleBtn(Icons.Default.Forward10, 52.dp) {
                            player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0L)))
                            controlsTick++
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Bottom bar — progress + time + CC
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (!isLive && durationMs > 0) {
                        val pct = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        // Simple touch-draggable progress bar.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .pointerInput(durationMs) {
                                    detectTapGestures { offset ->
                                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        player.seekTo((frac * durationMs).toLong())
                                        controlsTick++
                                    }
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            // Track
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x33FFFFFF)),
                            )
                            // Fill
                            Box(
                                Modifier
                                    .fillMaxWidth(pct)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Cyan),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(formatMs(positionMs), color = Color(0xFFE2E8F0), fontSize = 11.sp)
                            Text(formatMs(durationMs), color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (aiCaptions) Cyan.copy(alpha = 0.22f) else Color(0x22FFFFFF))
                                .clickable {
                                    aiCaptions = !aiCaptions
                                    controlsTick++
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.ClosedCaption, null,
                                tint = if (aiCaptions) Cyan else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (aiCaptions) "AI ON" else "AI CC",
                                color = if (aiCaptions) Cyan else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Tap sides to skip · Double-tap center to pause",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x33000000))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms < 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
