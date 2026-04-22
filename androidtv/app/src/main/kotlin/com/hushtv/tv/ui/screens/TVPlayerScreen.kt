package com.hushtv.tv.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.hushtv.tv.data.EpgService
import com.hushtv.tv.data.LastChannelStore
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.NavState
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.WatchProgressStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.player.AspectMode
import com.hushtv.tv.ui.player.PlayerOptionsMenu
import com.hushtv.tv.ui.player.ThumbnailExtractor
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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

    // Current stream state — can change as user zaps with CH+/-.
    var currentUrl by remember { mutableStateOf(streamUrl) }
    var currentName by remember { mutableStateOf(channelName) }
    var currentNumber by remember { mutableStateOf(if (NavState.currentChannelIndex >= 0) NavState.currentChannelIndex + 1 else 0) }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(currentUrl))
            prepare()
            // Live channels auto-play. VOD will wait for the Resume-prompt
            // decision below (if there's saved progress) or auto-play after
            // a brief moment otherwise.
            playWhenReady = isLive
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Thumbnail preview extractor for scrubber (VOD only). Lazy-init so live
    // streams never pay the cost.
    val thumbExtractor = remember(currentUrl, isLive) {
        if (isLive) null else ThumbnailExtractor(currentUrl)
    }
    DisposableEffect(thumbExtractor) {
        onDispose { thumbExtractor?.release() }
    }
    var scrubThumb by remember { mutableStateOf<Bitmap?>(null) }

    // Persist last watched channel whenever stream changes.
    LaunchedEffect(currentUrl, currentName) {
        if (isLive && playlistId.isNotBlank()) {
            LastChannelStore.save(ctx, playlistId, currentUrl, currentName)
        }
    }

    // ─── Zap helper ────────────────────────────────────────────────────────
    var zapLabel by remember { mutableStateOf<String?>(null) }
    var zapTick by remember { mutableStateOf(0) }

    fun playChannel(ch: MediaCard?, number: Int, zapText: String? = null) {
        val p = PlaylistStore.find(ctx, playlistId) ?: return
        if (ch == null) return
        val url = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
        currentUrl = url
        currentName = ch.title
        currentNumber = number
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        zapLabel = zapText ?: "CH ${number.toString().padStart(3, '0')}  ${ch.title}"
        zapTick++
    }

    // Auto-hide the zap banner after 2 s
    LaunchedEffect(zapTick) {
        if (zapLabel != null) {
            delay(2000)
            zapLabel = null
        }
    }

    // ─── OSD controls auto-hide ────────────────────────────────────────────
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
        delay(7000)
        showControls = false
    }

    // Guards against the double-fire bug in ExoPlayer + Compose where
    // clickableWithEnter's KeyUp handler AND Modifier.clickable's built-in
    // Enter-to-click mapping both invoke the onClick — causing play/pause
    // to toggle twice per OK press (paused for ~300ms then resumed).
    var lastToggleMs by remember { mutableStateOf(0L) }
    val togglePlayPause = {
        val now = System.currentTimeMillis()
        if (now - lastToggleMs > 250L) {
            lastToggleMs = now
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    // VOD focus target — when the OSD becomes visible, we move focus to the
    // primary Play/Pause button so the user can immediately navigate the
    // control bar with the D-pad. When the OSD hides, we MUST return focus
    // to the root Box — otherwise the removed-from-composition button takes
    // focus with it, key events have nowhere to land, and pressing OK does
    // nothing (the bug shipped in 1.5.1).
    val playPauseFocus = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(showControls, isLive) {
        if (isLive) return@LaunchedEffect
        if (showControls) {
            delay(50)
            // Default focus → the scrubber. D-pad DOWN moves to the button row.
            // This matches Tivimate/Netflix: the scrubber is the primary
            // interaction and most users want to seek, not pause.
            runCatching { scrubberFocus.requestFocus() }
        } else {
            // Send focus back home so the root's onKeyEvent handler can
            // catch the next key press and re-show the OSD.
            runCatching { rootFocus.requestFocus() }
        }
    }

    // Persist watch progress for movies (every 15s + on dispose).
    // We parse the stream_id from the URL tail — Xtream URLs always end
    // in /{streamId}.{ext}
    val vodStreamId: Int? = remember(currentUrl) {
        if (isLive) return@remember null
        currentUrl.substringAfterLast('/').substringBeforeLast('.').toIntOrNull()
    }

    // ─── Resume prompt ────────────────────────────────────────────────
    // On cold launch, if the user has meaningful saved progress for this
    // VOD title (more than 30s in, less than 30s from the end), show a
    // Resume / Start Over prompt. The player stays paused until they pick.
    var resumePromptMs by remember { mutableStateOf<Long?>(null) }
    var resumePromptHandled by remember { mutableStateOf(false) }
    LaunchedEffect(vodStreamId) {
        if (isLive || vodStreamId == null || resumePromptHandled) return@LaunchedEffect
        val saved = WatchProgressStore.get(ctx, vodStreamId, "movie") ?: run {
            // No saved progress — auto-play.
            player.playWhenReady = true
            resumePromptHandled = true
            return@LaunchedEffect
        }
        val eligible = saved.durationMs > 0 &&
            saved.positionMs > 30_000L &&
            saved.positionMs < saved.durationMs - 30_000L
        if (eligible) {
            resumePromptMs = saved.positionMs
        } else {
            player.playWhenReady = true
            resumePromptHandled = true
        }
    }

    if (!isLive && vodStreamId != null) {
        LaunchedEffect(vodStreamId) {
            while (true) {
                delay(15_000)
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0)
                if (dur > 0 && pos > 5_000) {
                    WatchProgressStore.save(
                        ctx,
                        streamId = vodStreamId,
                        kind = "movie",
                        title = currentName,
                        poster = null,
                        positionMs = pos,
                        durationMs = dur,
                    )
                }
            }
        }
        DisposableEffect(vodStreamId) {
            onDispose {
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0)
                if (dur > 0 && pos > 5_000) {
                    WatchProgressStore.save(
                        ctx,
                        streamId = vodStreamId,
                        kind = "movie",
                        title = currentName,
                        poster = null,
                        positionMs = pos,
                        durationMs = dur,
                    )
                }
            }
        }
    }

    // ─── Info overlay (OK/INFO) ─────────────────────────────────────────
    var infoVisible by remember { mutableStateOf(false) }
    var infoTick by remember { mutableStateOf(0) }
    LaunchedEffect(infoTick) {
        if (infoVisible) {
            delay(6000)
            infoVisible = false
        }
    }

    // Prefetch EPG when channel changes so the Info overlay has data
    val playlistObj = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }
    val currentChannel = remember(NavState.currentChannelIndex, currentUrl) {
        NavState.liveChannels.getOrNull(NavState.currentChannelIndex)
    }
    val epgScope = rememberCoroutineScope()
    LaunchedEffect(currentUrl) {
        val p = playlistObj ?: return@LaunchedEffect
        val ch = currentChannel ?: return@LaunchedEffect
        epgScope.launch {
            EpgService.fetchShortEpg(p.host, p.username, p.password, ch.streamId)
        }
    }

    // ─── Channel-number dialer ─────────────────────────────────────────
    var dialBuf by remember { mutableStateOf("") }
    var dialTick by remember { mutableStateOf(0) }
    LaunchedEffect(dialTick) {
        if (dialBuf.isNotEmpty()) {
            delay(1800)
            val n = dialBuf.toIntOrNull()
            if (n != null) {
                val idx = (n - 1).coerceIn(0, (NavState.liveChannels.size - 1).coerceAtLeast(0))
                val ch = NavState.liveChannels.getOrNull(idx)
                if (ch != null) {
                    NavState.rememberPlayback(idx)
                    playChannel(ch, idx + 1)
                }
            }
            dialBuf = ""
        }
    }

    // ─── Options menu (audio, subtitles, aspect, speed, sleep) ────────
    var optionsOpen by remember { mutableStateOf(false) }
    // When the user taps a quick-action chip in the OSD (CC / Audio / Speed),
    // we jump straight into the matching pane of the options menu.
    var optionsInitialPane by remember { mutableStateOf<String?>(null) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    var sleepExpiresAt by remember { mutableStateOf<Long?>(null) }
    val sleepMinutesLeft by remember {
        derivedStateOf {
            val ts = sleepExpiresAt ?: return@derivedStateOf null
            ((ts - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
        }
    }
    LaunchedEffect(sleepExpiresAt) {
        while (sleepExpiresAt != null) {
            delay(5_000)
            if (sleepExpiresAt != null && System.currentTimeMillis() >= sleepExpiresAt!!) {
                player.pause()
                nav.popBackStack()
                break
            }
        }
    }

    LaunchedEffect(playbackSpeed) {
        runCatching { player.setPlaybackSpeed(playbackSpeed) }
    }

    // ─── Back-double-press → previous channel ─────────────────────────────
    var lastBackMs by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                controlsTick++
                when (e.key) {
                    // Channel up / down via arrow keys, CH+/CH-, and media next/prev
                    Key.DirectionUp, Key.ChannelUp, Key.MediaNext -> {
                        if (isLive) {
                            val next = NavState.nextChannel()
                            playChannel(next, NavState.currentChannelIndex + 1)
                            true
                        } else {
                            player.volume = (player.volume + 0.1f).coerceAtMost(1f); true
                        }
                    }
                    Key.DirectionDown, Key.ChannelDown, Key.MediaPrevious -> {
                        if (isLive) {
                            val prev = NavState.prevChannel()
                            playChannel(prev, NavState.currentChannelIndex + 1)
                            true
                        } else {
                            player.volume = (player.volume - 0.1f).coerceAtLeast(0f); true
                        }
                    }
                    // Play / pause OR info toggle
                    Key.Enter, Key.DirectionCenter, Key.MediaPlayPause,
                    Key.Spacebar, Key.NumPadEnter -> {
                        if (isLive) {
                            // On live streams, center = toggle info overlay
                            infoVisible = !infoVisible
                            if (infoVisible) infoTick++
                            true
                        } else {
                            if (!showControls) {
                                // OSD is hidden — FIRST press of OK just brings
                                // the control bar up and moves focus to the
                                // Play/Pause button (done by the
                                // LaunchedEffect(showControls) above). User's
                                // next OK press actually toggles playback via
                                // the focused button's own clickableWithEnter.
                                // This matches user expectation: "OK brings up
                                // the controls, don't just silently pause".
                                controlsTick++
                                true
                            } else {
                                // OSD is visible. If a button is focused, its
                                // clickableWithEnter handles Enter first and
                                // consumes the event — we never reach here.
                                // This branch only fires if somehow focus is
                                // on the root Box → fall back to toggle.
                                togglePlayPause()
                                controlsTick++
                                true
                            }
                        }
                    }
                    Key.Info -> { infoVisible = !infoVisible; if (infoVisible) infoTick++; true }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); true }
                    // Channel number dialer (0-9)
                    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                    Key.Six, Key.Seven, Key.Eight, Key.Nine,
                    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
                    Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9 -> {
                        if (isLive) {
                            val digit = when (e.key) {
                                Key.Zero, Key.NumPad0 -> "0"
                                Key.One, Key.NumPad1 -> "1"
                                Key.Two, Key.NumPad2 -> "2"
                                Key.Three, Key.NumPad3 -> "3"
                                Key.Four, Key.NumPad4 -> "4"
                                Key.Five, Key.NumPad5 -> "5"
                                Key.Six, Key.NumPad6 -> "6"
                                Key.Seven, Key.NumPad7 -> "7"
                                Key.Eight, Key.NumPad8 -> "8"
                                Key.Nine, Key.NumPad9 -> "9"
                                else -> ""
                            }
                            if (dialBuf.length < 4) dialBuf += digit
                            dialTick++
                            true
                        } else false
                    }
                    // Seek (for VOD only — live ignores)
                    Key.DirectionRight -> {
                        if (!isLive && !showControls) {
                            // Quick 10s skip + reveal OSD. When OSD is
                            // already visible, let Compose focus traversal
                            // move between control buttons instead.
                            player.seekTo(
                                (player.currentPosition + 10_000).coerceAtMost(
                                    player.duration.coerceAtLeast(0)
                                )
                            ); true
                        } else false
                    }
                    Key.DirectionLeft -> {
                        if (!isLive && !showControls) {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); true
                        } else false
                    }
                    Key.MediaFastForward -> {
                        if (!isLive) {
                            player.seekTo(
                                (player.currentPosition + 30_000).coerceAtMost(
                                    player.duration.coerceAtLeast(0)
                                )
                            ); true
                        } else false
                    }
                    Key.MediaRewind -> {
                        if (!isLive) {
                            player.seekTo((player.currentPosition - 30_000).coerceAtLeast(0)); true
                        } else false
                    }
                    // Back: single press → exit. Double press within 1 s → toggle last channel.
                    Key.Back, Key.Escape -> {
                        val now = System.currentTimeMillis()
                        if (isLive && now - lastBackMs < 1000L && NavState.previousChannelIndex >= 0) {
                            val last = NavState.toggleLastChannel()
                            playChannel(last, NavState.currentChannelIndex + 1, "LAST CHANNEL")
                            lastBackMs = 0L
                            true
                        } else {
                            lastBackMs = now
                            nav.popBackStack(); true
                        }
                    }
                    Key.Menu, Key.F1 -> {
                        optionsInitialPane = null
                        optionsOpen = true
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view ->
                view.resizeMode = when (aspectMode) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectMode.RATIO_16_9, AspectMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Channel-zap toast
        AnimatedVisibility(
            visible = zapLabel != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(48.dp)
        ) {
            Surface(
                color = Color(0xE6000000),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(2.dp, Cyan, RoundedCornerShape(14.dp))
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        zapLabel ?: "",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Channel-number dialer overlay (bottom-center)
        if (dialBuf.isNotEmpty()) {
            Surface(
                color = Color(0xE6000000),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(2.dp, Cyan, RoundedCornerShape(18.dp))
            ) {
                Column(
                    Modifier.padding(horizontal = 40.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CHANNEL", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        dialBuf.padEnd(3, '_'),
                        color = Cyan, fontSize = 64.sp, fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Press digits or wait to confirm",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
            }
        }

        // Info overlay (bottom banner)
        if (infoVisible && isLive) {
            val chIdx = NavState.currentChannelIndex
            val ch = NavState.liveChannels.getOrNull(chIdx)
            val now = ch?.let { EpgService.nowPlaying(it.streamId) }
            val next = ch?.let { EpgService.nextUp(it.streamId) }
            Surface(
                color = Color(0xE6000000),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 60.dp)
                    .widthIn(max = 900.dp)
                    .fillMaxWidth(0.9f)
                    .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (chIdx + 1).toString().padStart(3, '0'),
                            color = Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            ch?.title ?: currentName,
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (now != null) {
                        Text(now.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).background(Color(0x14FFFFFF), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(now.progressPct)
                                    .height(4.dp)
                                    .background(Cyan, RoundedCornerShape(2.dp))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${now.minutesLeft.coerceAtLeast(0)} min left",
                            color = TextSecondary, fontSize = 12.sp
                        )
                        if (now.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                now.description,
                                color = Color(0xFFD1D5DB), fontSize = 13.sp,
                                maxLines = 3
                            )
                        }
                    } else {
                        Text("No program info available", color = TextSecondary, fontSize = 14.sp)
                    }
                    if (next != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "NEXT",
                            color = TextSecondary, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            next.title,
                            color = Color(0xFFD1D5DB), fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Center pause indicator — always visible when VOD is paused, regardless
        // of OSD state, so the user immediately sees playback is halted.
        if (!isLive && !isPlaying) {
            Surface(
                color = Color(0xB3000000),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .border(2.dp, Cyan, CircleShape)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Pause,
                        null, tint = Color.White, modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Full OSD
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
                // Top bar
                Row(
                    Modifier.align(Alignment.TopStart).padding(horizontal = 48.dp, vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0x80000000), shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                            .tvFocusable(shape = CircleShape)
                            .clickableWithEnter { nav.popBackStack() }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentNumber > 0) {
                                Text(
                                    currentNumber.toString().padStart(3, '0'),
                                    color = Cyan, fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                currentName, color = Color.White, fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isLive) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    "● LIVE", color = Color(0xFFFCA5A5), fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                        // Tivimate-style scrubber. Focusable; LEFT/RIGHT seek
                        // with acceleration (tap 10s, rapid taps scale up to
                        // 60s per press). DOWN moves to the button row below.
                        // OK toggles play/pause — most common user intent.
                        var scrubFocused by remember { mutableStateOf(false) }
                        var lastSeekMs by remember { mutableStateOf(0L) }
                        var seekChainCount by remember { mutableStateOf(0) }

                        // Async thumbnail extraction — debounced so we don't
                        // spawn a decoder on every D-pad tap during fast scrub.
                        LaunchedEffect(scrubFocused, positionMs) {
                            if (!scrubFocused || thumbExtractor == null) return@LaunchedEffect
                            delay(180)
                            val bmp = withContext(Dispatchers.IO) {
                                thumbExtractor.extract(positionMs)
                            }
                            if (bmp != null) scrubThumb = bmp
                        }

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(scrubberFocus)
                                .onFocusChanged { scrubFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        Key.DirectionLeft, Key.DirectionRight -> {
                                            controlsTick++
                                            val forward = ev.key == Key.DirectionRight
                                            val now = System.currentTimeMillis()
                                            // Escalate step size on rapid
                                            // consecutive presses (adaptive).
                                            seekChainCount = if (now - lastSeekMs < 400L) {
                                                (seekChainCount + 1).coerceAtMost(10)
                                            } else 1
                                            lastSeekMs = now
                                            val stepMs = when {
                                                seekChainCount >= 6 -> 60_000L
                                                seekChainCount >= 3 -> 30_000L
                                                else -> 10_000L
                                            }
                                            val target = if (forward)
                                                (player.currentPosition + stepMs).coerceAtMost(
                                                    player.duration.coerceAtLeast(0)
                                                )
                                            else
                                                (player.currentPosition - stepMs).coerceAtLeast(0)
                                            player.seekTo(target)
                                            true
                                        }
                                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                            controlsTick++
                                            togglePlayPause()
                                            true
                                        }
                                        else -> false
                                    }
                                },
                            verticalArrangement = Arrangement.Center,
                        ) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val trackWidth = maxWidth

                                // Floating thumbnail preview above the playhead.
                                // Only visible while the scrubber is focused AND
                                // we have a decoded frame in hand.
                                val bmp = scrubThumb
                                if (scrubFocused && bmp != null) {
                                    val thumbWidthDp = 160.dp
                                    val offsetX = (trackWidth * pct) - (thumbWidthDp / 2)
                                    val clampedX = offsetX.coerceIn(0.dp, trackWidth - thumbWidthDp)
                                    Column(
                                        Modifier
                                            .offset(x = clampedX, y = -(110.dp))
                                            .width(thumbWidthDp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .width(thumbWidthDp)
                                                .height(90.dp)
                                                .background(Color.Black)
                                                .border(2.dp, Cyan, RoundedCornerShape(6.dp)),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            formatTime(positionMs),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                // Progress track. When focused the bar grows
                                // to 10 dp; unfocused 6 dp.
                                val barHeight = if (scrubFocused) 10.dp else 6.dp
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(barHeight)
                                        .background(Color(0x40FFFFFF), RoundedCornerShape(5.dp))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(pct)
                                            .height(barHeight)
                                            .background(Cyan, RoundedCornerShape(5.dp))
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text(
                                    formatTime(positionMs),
                                    color = if (scrubFocused) Cyan else Color(0xFF9CA3AF),
                                    fontSize = 13.sp,
                                    fontWeight = if (scrubFocused) FontWeight.Bold else FontWeight.Normal,
                                )
                                Spacer(Modifier.weight(1f))
                                val remaining = (durationMs - positionMs).coerceAtLeast(0)
                                Text(
                                    "-${formatTime(remaining)}",
                                    color = Color(0xFF9CA3AF), fontSize = 13.sp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    formatTime(durationMs),
                                    color = Color(0xFF9CA3AF), fontSize = 13.sp,
                                )
                            }
                            if (scrubFocused) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "← → to seek   ↓ for controls   OK to play/pause",
                                    color = Color(0xFF9CA3AF), fontSize = 11.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (isLive) {
                        // Legacy live OSD — unchanged
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OsdCircleButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                size = 64.dp,
                                onClick = togglePlayPause,
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(20.dp))
                            OsdCircleButton(
                                icon = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                size = 64.dp,
                                onClick = {
                                    muted = !muted
                                    player.volume = if (muted) 0f else 1f
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "↑↓ Channel   0-9 Dial   OK Info   MENU Options   Back×2 Last",
                                color = Color(0xFFD1D5DB), fontSize = 14.sp,
                            )
                        }
                    } else {
                        // VOD player — full control bar: seek, play/pause,
                        // subtitles, audio, speed, more.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OsdCircleButton(
                                icon = Icons.Default.Replay30,
                                size = 56.dp,
                                onClick = {
                                    player.seekTo((player.currentPosition - 30_000).coerceAtLeast(0))
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(14.dp))
                            OsdCircleButton(
                                icon = Icons.Default.Replay10,
                                size = 56.dp,
                                onClick = {
                                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(14.dp))
                            OsdCircleButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                size = 72.dp,
                                primary = true,
                                focusRequester = playPauseFocus,
                                onClick = togglePlayPause,
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(14.dp))
                            OsdCircleButton(
                                icon = Icons.Default.Forward10,
                                size = 56.dp,
                                onClick = {
                                    player.seekTo(
                                        (player.currentPosition + 10_000).coerceAtMost(
                                            player.duration.coerceAtLeast(0)
                                        )
                                    )
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(14.dp))
                            OsdCircleButton(
                                icon = Icons.Default.Forward30,
                                size = 56.dp,
                                onClick = {
                                    player.seekTo(
                                        (player.currentPosition + 30_000).coerceAtMost(
                                            player.duration.coerceAtLeast(0)
                                        )
                                    )
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.weight(1f))
                            // Secondary control cluster — opens options menu
                            // on the matching pane so focus lands right where
                            // the user clicked.
                            OsdChipButton(
                                icon = Icons.Default.ClosedCaption,
                                label = "CC",
                                onClick = {
                                    optionsInitialPane = "subtitle"
                                    optionsOpen = true
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(10.dp))
                            OsdChipButton(
                                icon = Icons.Default.Subtitles,
                                label = "Audio",
                                onClick = {
                                    optionsInitialPane = "audio"
                                    optionsOpen = true
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(10.dp))
                            OsdChipButton(
                                icon = Icons.Default.Speed,
                                label = "${trimTrailingZero(playbackSpeed)}×",
                                onClick = {
                                    optionsInitialPane = "speed"
                                    optionsOpen = true
                                },
                                onInteract = { controlsTick++ },
                            )
                            Spacer(Modifier.width(10.dp))
                            OsdChipButton(
                                icon = Icons.Default.Settings,
                                label = "More",
                                onClick = {
                                    optionsInitialPane = null
                                    optionsOpen = true
                                },
                                onInteract = { controlsTick++ },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "← → Seek 10s   ⏪⏩ Seek 30s   OK Play/Pause   MENU Options",
                            color = Color(0xFF9CA3AF), fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // Resume-from-last-position prompt
        val resumePositionMs = resumePromptMs
        if (!isLive && resumePositionMs != null && !resumePromptHandled) {
            ResumePromptOverlay(
                positionMs = resumePositionMs,
                onResume = {
                    player.seekTo(resumePositionMs)
                    player.playWhenReady = true
                    resumePromptMs = null
                    resumePromptHandled = true
                },
                onStartOver = {
                    player.seekTo(0)
                    player.playWhenReady = true
                    resumePromptMs = null
                    resumePromptHandled = true
                },
            )
        }

        // Options menu overlay
        if (optionsOpen) {
            PlayerOptionsMenu(
                player = player,
                aspectMode = aspectMode,
                onAspectChange = { aspectMode = it },
                sleepMinutesLeft = sleepMinutesLeft,
                onSleepChange = { mins ->
                    sleepMinutes = mins
                    sleepExpiresAt = mins?.let {
                        System.currentTimeMillis() + it * 60_000L
                    }
                },
                playbackSpeed = playbackSpeed,
                onPlaybackSpeedChange = { playbackSpeed = it },
                onShowInfo = { infoVisible = true; infoTick++ },
                onDismiss = { optionsOpen = false; optionsInitialPane = null },
                initialPane = optionsInitialPane,
            )
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

@Composable
private fun ResumePromptOverlay(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { resumeFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xFF0B111D),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(min = 520.dp, max = 640.dp)
                .border(1.dp, Color(0x4D06B6D4), RoundedCornerShape(20.dp)),
        ) {
            Column(
                Modifier.padding(horizontal = 36.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    "RESUME WATCHING",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "You were at ${formatTime(positionMs)}",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pick up where you left off, or start from the beginning.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResumeButton(
                        label = "Resume",
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        focusRequester = resumeFocus,
                        onClick = onResume,
                    )
                    ResumeButton(
                        label = "Start Over",
                        icon = Icons.Default.Replay10,
                        primary = false,
                        onClick = onStartOver,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val mod = Modifier
        .height(52.dp)
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .tvFocusable(shape = RoundedCornerShape(10.dp))
        .clickableWithEnter(onClick)
    Surface(
        color = if (primary) Cyan else Color(0x33FFFFFF),
        shape = RoundedCornerShape(10.dp),
        modifier = mod,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon, null,
                tint = if (primary) Color.Black else Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = if (primary) Color.Black else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun trimTrailingZero(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString().trimEnd('0').trimEnd('.')

@Composable
private fun OsdCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onInteract: () -> Unit = {},
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val mod = Modifier
        .size(size)
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .tvFocusable(shape = CircleShape)
        .clickableWithEnter {
            onInteract()
            onClick()
        }
    Surface(
        color = if (primary) Cyan.copy(alpha = 0.18f) else Color(0x26FFFFFF),
        shape = CircleShape,
        modifier = mod,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

@Composable
private fun OsdChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onInteract: () -> Unit = {},
) {
    Surface(
        color = Color(0x26FFFFFF),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .height(44.dp)
            .tvFocusable(shape = RoundedCornerShape(28.dp))
            .clickableWithEnter {
                onInteract()
                onClick()
            },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
