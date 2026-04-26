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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mobile player — touch-first.
 *
 * Gestures:
 *   • Tap anywhere → toggle controls visibility.
 *   • Double-tap left third → seek -10 s.
 *   • Double-tap right third → seek +10 s.
 *   • Drag on progress bar → scrub (simple linear map).
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

    // Player.
    val player = remember {
        com.hushtv.tv.data.PlayerBuilder.build(ctx).apply {
            setMediaItem(MediaItem.fromUri(currentStreamUrl))
            prepare()
            // Subtitles default OFF on every new playback session.
            // Some HLS / MKV streams come with embedded text tracks
            // that ExoPlayer would otherwise auto-select. Users want
            // explicit opt-in via the CC chip per-session.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Auto-reconnect on transient IO errors — see TVPlayerScreen.kt.
    DisposableEffect(player, currentTitle) {
        com.hushtv.tv.data.PlayerBuilder.attachAutoReconnect(player, currentTitle)
        onDispose { }
    }
    // ─── Freeze monitor ─────────────────────────────────────────────
    DisposableEffect(player, currentStreamUrl) {
        val mon = com.hushtv.tv.data.PlaybackFreezeMonitor.attach(
            ctx, player,
            streamUrl = currentStreamUrl,
            isLive = isLive,
            channelName = currentTitle,
        )
        onDispose { mon.detach() }
    }

    // ── OpenSubtitles plumbing ──────────────────────────────────────
    val subtitleQuery = remember {
        // Detail screens that know rich metadata (movies → year,
        // series → season+episode) stash it here. For other entry
        // points (Browse, Home, Search) we fall back to using the
        // stream title as a query — works fine for ~80% of titles.
        com.hushtv.tv.data.SubtitleSearchContext.consume()
            ?: if (!isLive) com.hushtv.tv.data.SubtitleSearchContext.Query(
                title = channelName,
                kind = "movie",
                streamUrl = currentStreamUrl,
            ) else null
    }
    var showSubtitleDownload by remember { mutableStateOf(false) }
    var downloadedSrt by remember { mutableStateOf<java.io.File?>(null) }
    var downloadedSrtLang by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(downloadedSrt, downloadedSrtLang, currentStreamUrl) {
        val srt = downloadedSrt ?: return@LaunchedEffect
        val lang = downloadedSrtLang ?: "en"
        val savedPos = player.currentPosition
        val item = MediaItem.Builder()
            .setUri(currentStreamUrl)
            .setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(
                        android.net.Uri.fromFile(srt),
                    )
                        .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage(lang)
                        .build(),
                ),
            )
            .build()
        player.setMediaItem(item, savedPos)
        player.prepare()
        player.playWhenReady = true
        // Re-enable text tracks AND prefer the SRT's language so
        // ExoPlayer picks our side-loaded SRT over any embedded
        // foreign text track baked into the IPTV stream.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(lang)
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
            .build()
    }

    // Subtitle on/off state for the CC chip in the controls bar.
    // Embedded foreign-language tracks (Dutch/Danish baked into HLS)
    // are ignored — only user-acquired SRTs or embedded English count
    // as "available" so the CC chip routes to the download dialog
    // when only foreign embedded tracks exist.
    var subsAvailable by remember { mutableStateOf(false) }
    var subsEnabled by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val textGroups = tracks.groups.filter {
                    it.type == androidx.media3.common.C.TRACK_TYPE_TEXT
                }
                val hasUserSrt = downloadedSrt != null
                val hasEnglishEmbedded = textGroups.any { grp ->
                    (0 until grp.length).any { i ->
                        grp.getTrackFormat(i).language?.lowercase()?.startsWith("en") == true
                    }
                }
                subsAvailable = hasUserSrt || hasEnglishEmbedded
                subsEnabled = textGroups.any { grp ->
                    (0 until grp.length).any { i -> grp.isTrackSelected(i) }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun toggleSubtitles() {
        val params = player.trackSelectionParameters
        if (subsEnabled) {
            player.trackSelectionParameters = params.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                .build()
        } else {
            val preferredLang = downloadedSrtLang ?: "en"
            player.trackSelectionParameters = params.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(preferredLang)
                .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                .build()
        }
    }

    // ── Auto-load best English SRT on first CC tap ──
    var autoLoading by remember { mutableStateOf(false) }
    var autoLoadFailed by remember { mutableStateOf(false) }

    fun autoLoadEnglishSrt() {
        val q = subtitleQuery ?: return
        if (downloadedSrt != null || autoLoading) return
        autoLoading = true
        autoLoadFailed = false
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val hits = if (q.kind == "episode" &&
                        q.seasonNumber != null && q.episodeNumber != null
                    ) {
                        com.hushtv.tv.data.OpenSubtitlesApi.searchEpisode(
                            seriesTitle = q.title,
                            seasonNumber = q.seasonNumber,
                            episodeNumber = q.episodeNumber,
                            languages = listOf("en"),
                        )
                    } else {
                        com.hushtv.tv.data.OpenSubtitlesApi.searchMovie(
                            title = q.title,
                            year = q.year,
                            languages = listOf("en"),
                        )
                    }
                    val best = hits.sortedWith(
                        compareByDescending<com.hushtv.tv.data.OpenSubtitlesApi.Hit> { it.fromTrusted }
                            .thenByDescending { it.downloadCount }
                    ).firstOrNull() ?: return@runCatching null
                    com.hushtv.tv.data.OpenSubtitlesApi.fetchSrt(ctx, best.fileId)
                }.getOrNull()
            }
            autoLoading = false
            if (file != null) {
                downloadedSrt = file
                downloadedSrtLang = "en"
            } else {
                autoLoadFailed = true
                // Educate the user about the long-press shortcut to
                // the manual language picker — most users won't
                // discover it otherwise.
                android.widget.Toast.makeText(
                    ctx,
                    "No English subtitles found for \"${q.title}\". " +
                        "Long-press CC to try another language.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

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
                        if (subtitleQuery != null) {
                            // CC chip:
                            //   • No SRT yet → tap auto-downloads top English.
                            //   • SRT loaded → tap toggles ON/OFF.
                            //   • Long-press → opens manual picker dialog
                            //     (so users can choose a different language
                            //     or a different release).
                            val srtIsAi = downloadedSrt?.name?.startsWith("ai_") == true
                            val chipLabel = when {
                                autoLoading -> "CC…"
                                subsAvailable && subsEnabled && srtIsAi -> "CC · AI"
                                subsAvailable && subsEnabled -> "CC ON"
                                subsAvailable -> "CC OFF"
                                autoLoadFailed -> "CC ?"
                                else -> "CC"
                            }
                            val chipActive = subsAvailable && subsEnabled
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (chipActive) Cyan.copy(alpha = 0.22f)
                                        else Color(0x22FFFFFF),
                                    )
                                    .pointerInput(subsAvailable, subsEnabled, autoLoading) {
                                        detectTapGestures(
                                            onTap = {
                                                when {
                                                    autoLoading -> Unit
                                                    subsAvailable -> toggleSubtitles()
                                                    else -> autoLoadEnglishSrt()
                                                }
                                                controlsTick++
                                            },
                                            onLongPress = {
                                                showSubtitleDownload = true
                                                controlsTick++
                                            },
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.ClosedCaption, null,
                                    tint = if (chipActive) Cyan else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    chipLabel,
                                    color = if (chipActive) Cyan else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
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

        // OpenSubtitles dialog overlays everything when active.
        if (showSubtitleDownload && subtitleQuery != null) {
            com.hushtv.tv.ui.player.SubtitleDownloadDialog(
                query = subtitleQuery,
                onDismiss = { showSubtitleDownload = false },
                onPicked = { file, lang ->
                    showSubtitleDownload = false
                    downloadedSrt = file
                    downloadedSrtLang = lang
                },
            )
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
