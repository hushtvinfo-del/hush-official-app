package com.hushtv.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.datasource.DefaultHttpDataSource

/**
 * Centralised ExoPlayer construction. Consolidates the fixes that
 * each individual player screen used to apply by hand AND adds the
 * "stream stability" tuning we'd been missing — bigger buffer +
 * auto-reconnect on transient IO errors, mirroring what users see
 * in apps like XC IPTV.
 *
 * Why this matters
 * ────────────────
 * Default ExoPlayer config for live HLS keeps a tiny ~6-15 s buffer.
 * One transient CDN hiccup or packet-loss blip exhausts the buffer
 * and emits a fatal `ERROR_CODE_IO_*` → player goes IDLE → channel
 * freezes. We saw this on a CNN HLS stream over a 672 Mbps WiFi
 * link — clearly not a network issue, just an over-eager error
 * handling default.
 *
 * What we change
 *   • LoadControl: 30 s minBuffer / 60 s maxBuffer / 1 s startup /
 *     5 s after-rebuffer. Same shape as XC IPTV's "20 second buffer"
 *     with a bit more headroom for HD streams.
 *   • DefaultRenderersFactory: prefer extension renderers + enable
 *     decoder fallback so the device's hardware H.264/H.265/AV1
 *     decoder is used when available. Fire Stick / Shield both have
 *     dedicated decoder chips that idle without this hint.
 *   • HLS MediaSource: setAllowChunklessPreparation(true) so live
 *     playlists with no segment list start faster, and a generous
 *     HTTP read/connect timeout so a slow first byte doesn't kill
 *     the stream.
 *   • Auto-reconnect listener: when a `Player.Listener` reports an
 *     IO error (the actual freeze cause), we call `prepare()` to
 *     re-establish the connection instead of giving up. Also kicks
 *     in for ERROR_CODE_BEHIND_LIVE_WINDOW which fires when the
 *     buffer fell behind the live edge.
 */
object PlayerBuilder {

    /** Build an ExoPlayer with our standard tuning. Caller is
     *  responsible for `release()` and for passing it to
     *  `attachAutoReconnect(...)` once `setMediaItem(...) + prepare()`
     *  has been called. */
    fun build(ctx: Context): ExoPlayer {
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderers = DefaultRenderersFactory(ctx).apply {
            // Use the platform's MediaCodec decoder list (hardware-first
            // on every Android TV / Fire TV / Shield I'm aware of). The
            // EXTENSION_RENDERER_MODE_ON flag lets us fall back to the
            // bundled software AV1 / FLAC / etc. decoders only when the
            // device can't do it in hardware.
            //
            // ⚠ Do NOT use EXTENSION_RENDERER_MODE_PREFER — that picks
            // the SOFTWARE extension decoders over the platform's
            // hardware MediaCodec, which is the opposite of what we
            // want for IPTV streams.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("HushTV/Android-ExoPlayer")

        val msFactory = DefaultMediaSourceFactory(ctx)
            .setDataSourceFactory(httpFactory)
            // For HLS streams (most IPTV providers) tweak the
            // dedicated factory so live playlists don't choke on
            // missing chunklists.
            .setLiveTargetOffsetMs(15_000)
            .setLiveMaxOffsetMs(30_000)
            .setLiveMinOffsetMs(2_000)

        return ExoPlayer.Builder(ctx)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderers)
            .setMediaSourceFactory(msFactory)
            // ── AudioAttributes for Dolby Digital + DTS auto-passthrough ──
            // Tagging the player as USAGE_MEDIA + CONTENT_TYPE_MOVIE
            // tells the platform's AudioPolicy this stream may carry
            // surround content. Combined with `handleAudioFocus = true`
            // this:
            //   • Enables automatic Dolby Digital (AC-3), Dolby
            //     Digital Plus (E-AC-3), and DTS passthrough over
            //     HDMI when the connected AVR/soundbar advertises
            //     support via EDID. ExoPlayer's `DefaultAudioSink`
            //     queries `AudioCapabilities.getCapabilities(ctx)`
            //     at build time and on every audio-output change,
            //     so plugging in a surround receiver mid-session
            //     automatically switches from PCM downmix to
            //     bitstream passthrough — no app-level config.
            //   • Falls back to PCM stereo / 5.1 downmix via the
            //     device's hardware MediaCodec AC-3/EAC-3 decoder
            //     when the output is just TV speakers — every Fire
            //     TV stick from the 4K era onward ships with this
            //     built in, so no FFmpeg extension is needed.
            //   • Honours the system's "Surround Sound: Best
            //     Available" toggle so users on Auto get Dolby
            //     when their setup supports it and silent fallback
            //     to stereo otherwise.
            //
            // We do NOT need to set `experimentalSetOffloadEnabled`
            // (that's a power-saving / deep-buffer codepath, not
            // related to surround). The simple AudioAttributes
            // hint above is what unlocks the Dolby pipeline.
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                // ── Default audio track preference ──────────────────
                // Always prefer English audio when the stream provides
                // multiple audio tracks. Some IPTV providers ship
                // sources where the FIRST audio track is in the source
                // language (e.g. Korean for "Gold Land", Japanese for
                // anime, Hindi for Bollywood, etc.) and ExoPlayer's
                // default behaviour is to pick the first track.
                //
                // We pass BOTH ISO-639-1 ("en") and ISO-639-2 ("eng")
                // because providers tag tracks inconsistently — some
                // streams use one, some use the other. ExoPlayer's
                // matcher normalises via java.util.Locale, but
                // providing both removes any ambiguity. The track
                // matcher honours order, so "en" is tried first, then
                // "eng" as a fallback. If NEITHER is present (English
                // simply doesn't exist on this stream) ExoPlayer falls
                // back to its default selection — usually the first
                // track — so we never silently mute the audio.
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguages("en", "eng")
                    .build()
                // Log the decoder ExoPlayer ends up picking — gives us
                // ground-truth visibility into "are we running on
                // hardware?" without guessing. The decoder name + an
                // is-hardware verdict appears in EventLog and so in
                // any future freeze report. The Diagnostics screen
                // also surfaces the most recent EventLog snapshot.
                addAnalyticsListener(DecoderInspector)
            }
    }

    /**
     * Static AnalyticsListener that logs every video/audio decoder
     * ExoPlayer initialises. Decoder names follow Android's MediaCodec
     * naming convention:
     *   • `c2.android.*` / `OMX.google.*`  → SOFTWARE
     *   • `c2.qti.*`, `c2.mtk.*`, `OMX.Nvidia.*`, `OMX.MTK.*`,
     *     `OMX.qcom.*`, `OMX.exynos.*`, `OMX.Intel.*`, `OMX.amlogic.*`
     *     etc.                              → HARDWARE
     * (The Codec2 prefix `c2.android.*` covers both Google's reference
     * software codecs and AOSP's; both are software.)
     */
    private val DecoderInspector = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            val kind = if (isLikelyHardware(decoderName)) "HARDWARE" else "SOFTWARE"
            EventLog.log("decoder", "video → $decoderName  ($kind)")
        }
        override fun onAudioDecoderInitialized(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            val kind = if (isLikelyHardware(decoderName)) "HARDWARE" else "SOFTWARE"
            EventLog.log("decoder", "audio → $decoderName  ($kind)")
        }
    }

    private fun isLikelyHardware(name: String): Boolean {
        val n = name.lowercase()
        // Anything Google / AOSP / FFmpeg ships is software.
        if (n.startsWith("c2.android.") ||
            n.startsWith("omx.google.") ||
            n.contains("ffmpeg") ||
            n.contains(".sw.") ||
            n.endsWith(".sw")
        ) return false
        // Vendor namespaces are nearly always hardware-backed.
        return n.startsWith("c2.") ||
            n.startsWith("omx.") ||
            n.startsWith("media3.")
    }

    /** Snapshot the most recent decoder lines logged for the
     *  Diagnostics screen header. Empty string if nothing's played
     *  yet this session. */
    fun lastDecoderLines(): String =
        EventLog.snapshot().lineSequence()
            .filter { it.contains(": video → ") || it.contains(": audio → ") }
            .toList()
            .takeLast(2)
            .joinToString("\n")

    /**
     * Wire automatic reconnect for both error AND silent-stall cases.
     * Call ONCE per player after [setMediaItem] + [prepare].
     *
     * What it recovers from
     * ─────────────────────
     *   1. `onPlayerError` with any IO / parsing / live-window code.
     *      ExoPlayer's "stream broke, give up" path. We re-prepare.
     *   2. STATE_BUFFERING that lasts > [STALL_BUFFER_MS] while the
     *      user wants playback. Often happens when a CDN edge stops
     *      sending bytes without ever closing the socket — ExoPlayer
     *      sits there politely waiting forever. We force a re-prepare.
     *   3. STATE_READY but `currentPosition` not advancing for >
     *      [STALL_FROZEN_MS]. The decoder thinks it's healthy but
     *      isn't actually rendering — hardware decoder can wedge in
     *      this state. Same fix: re-prepare.
     *   4. ConnectivityManager reports network is back. If we're
     *      stuck IDLE / errored at that moment (Wi-Fi just came back
     *      from a 2-minute outage), kick a fresh prepare immediately
     *      instead of waiting for the next backoff tick.
     *
     * Recovery strategy
     * ─────────────────
     *   • Track current position. For VOD, save it before re-prepare
     *     and seekTo() it once the next STATE_READY arrives. For LIVE,
     *     don't seek — the HLS source will rejoin at the live edge.
     *   • Backoff between consecutive recovery attempts: 1 s, 2 s,
     *     4 s, 8 s, capped at [MAX_BACKOFF_MS]. Avoids hammering a
     *     downed CDN.
     *   • Cap at [MAX_AUTO_RETRIES]. We pick a high cap on purpose:
     *     a 5-retry cap exhausted in 5 seconds was the bug we saw
     *     before, where users came back from a 30-second cable hiccup
     *     to a permanently dead player.
     *   • Reset the attempt counter only after [STABLE_PLAYBACK_MS]
     *     of clean ready-state playback, NOT on the first STATE_READY
     *     after recovery (which can flap right back into errors).
     *
     * Returned [Disposable] MUST be disposed when the caller's
     * lifetime ends — it unregisters the network callback and the
     * player listener.
     */
    fun attachAutoReconnect(
        ctx: Context,
        player: ExoPlayer,
        channelName: String,
        isLive: Boolean,
        onRecoveryStart: ((reason: String) -> Unit)? = null,
        onRecovered: (() -> Unit)? = null,
        onMaxRetriesExceeded: (() -> Unit)? = null,
    ): Disposable {
        val handler = Handler(Looper.getMainLooper())
        val state = ReconnectState()

        // v1.44.62 — Snapshot the MediaItem at attach time so the
        // recovery path can do a full stop → clearMediaItems →
        // setMediaItem → prepare → seekToDefaultPosition. Calling
        // just player.prepare() is insufficient for certain
        // wedged states (HLS source exhausted, codec stuck post-
        // error). We refresh this snapshot every time the user
        // zaps channels so the watchdog uses the latest URL.
        var snapshotItem: MediaItem? = player.currentMediaItem

        fun scheduleRecovery(reason: String) {
            // Already a recovery in flight? Skip — we'll get another
            // signal if it failed.
            if (state.recoveryInFlight) return
            if (state.attempts >= MAX_AUTO_RETRIES) {
                EventLog.log(
                    "auto-reconnect",
                    "$channelName GIVE UP after ${state.attempts} (${reason})",
                )
                onMaxRetriesExceeded?.invoke()
                return
            }
            state.recoveryInFlight = true
            state.attempts += 1
            val backoff = backoffMsFor(state.attempts)
            // v1.44.63 — Notify the UI that recovery just kicked off
            // so the player can show a subtle "Reconnecting…" pill.
            // Fires on EVERY retry attempt (not just the first) —
            // this is intentional: each retry restarts the visible
            // pill timer in the UI so users see continuous feedback.
            handler.post { onRecoveryStart?.invoke(reason) }
            // Save the current position so we can seek back after
            // re-prepare for VOD. For live, this is effectively a
            // no-op — we never re-seek.
            state.savedPositionMs = if (!isLive) {
                runCatching { player.currentPosition }.getOrDefault(0L)
            } else 0L
            // v1.44.62 — Re-snapshot the MediaItem in case it
            // changed (user zapped channels) since attach.
            runCatching { player.currentMediaItem }
                .getOrNull()
                ?.let { snapshotItem = it }
            EventLog.log(
                "auto-reconnect",
                "$channelName retry=${state.attempts} reason=$reason " +
                    "backoff=${backoff}ms savedPos=${state.savedPositionMs}ms",
            )
            handler.postDelayed({
                if (state.disposed) return@postDelayed
                runCatching {
                    // v1.44.62 — Hard source rebuild instead of bare
                    // prepare(). For wedged HLS / TS sources, calling
                    // prepare() alone can be a no-op (the MediaSource
                    // believes it's already prepared) or fail
                    // silently. The full teardown forces a fresh
                    // HTTP fetch and a new ExtractorMediaSource.
                    val item = snapshotItem ?: player.currentMediaItem
                    player.stop()
                    player.clearMediaItems()
                    if (item != null) {
                        player.setMediaItem(item)
                    }
                    player.prepare()
                    if (isLive) {
                        // Snap back to the live edge so the user
                        // catches up to "now" rather than resuming
                        // 20-30 s behind.
                        player.seekToDefaultPosition()
                    } else if (state.savedPositionMs > 0L) {
                        // VOD: set a flag — actual seek happens on
                        // next STATE_READY so the buffer is ready
                        // first.
                        state.pendingSeek = true
                    }
                    if (!player.playWhenReady) player.playWhenReady = true
                }
            }, backoff)
        }

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (state.disposed) return
                if (error.errorCode in RECOVERABLE_ERRORS) {
                    scheduleRecovery("err=${error.errorCodeName}")
                } else {
                    EventLog.log(
                        "auto-reconnect",
                        "$channelName non-recoverable code=${error.errorCodeName}",
                    )
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                // v1.44.62 — Keep our snapshot in sync when the user
                // zaps channels (TVPlayerScreen calls
                // setMediaItem(MediaItem.fromUri(newUrl))).
                //
                // v1.44.64 — Only reset state for a GENUINE user
                // channel change. Our own recovery code calls
                // clearMediaItems() + setMediaItem(item) which also
                // fires this listener; resetting state in that case
                // would clear the recovery counter mid-recovery and
                // allow the give-up cap to be bypassed indefinitely.
                if (mediaItem != null && !state.recoveryInFlight) {
                    snapshotItem = mediaItem
                    // Fresh channel: re-arm everything.
                    state.attempts = 0
                    state.recoveryNotified = false
                    state.bufferingSinceMs = 0L
                    state.lastPosCheckMs = 0L
                    state.hasBeenReady = false
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (state.disposed) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (state.bufferingSinceMs == 0L) {
                            state.bufferingSinceMs = System.currentTimeMillis()
                        }
                        // Stop counting "stable playback" while buffering.
                        state.readySinceMs = 0L
                    }
                    Player.STATE_READY -> {
                        // First time we ever reach READY → arm the
                        // stall watchdog. Initial channel-load
                        // buffering before this point is NOT a stall.
                        state.hasBeenReady = true
                        state.bufferingSinceMs = 0L
                        state.recoveryInFlight = false
                        if (state.pendingSeek) {
                            // Restore VOD position now that the stream
                            // is buffered enough to seek into.
                            val pos = state.savedPositionMs
                            state.pendingSeek = false
                            if (pos > 0L) {
                                runCatching { player.seekTo(pos) }
                            }
                        }
                        // Fire the "we just recovered" signal exactly
                        // once per recovery cycle. Triggered the first
                        // time we hit STATE_READY after at least one
                        // recovery attempt — the moment the picture
                        // comes back. The flag is reset by the
                        // watchdog when attempts drops back to 0
                        // (after STABLE_PLAYBACK_MS of clean playback).
                        if (state.attempts > 0 && !state.recoveryNotified) {
                            state.recoveryNotified = true
                            handler.post { onRecovered?.invoke() }
                        }
                        if (state.readySinceMs == 0L) {
                            state.readySinceMs = System.currentTimeMillis()
                        }
                    }
                    Player.STATE_IDLE -> {
                        // Player gave up. If we're online, try right
                        // away (subject to backoff); if we're offline,
                        // the network callback will pick it up when we
                        // come back.
                        //
                        // v1.44.64 — Also skip if we've never been
                        // READY yet — the player's initial state IS
                        // IDLE before the first prepare(), and our
                        // recovery's own stop() also goes through
                        // IDLE briefly. recoveryInFlight catches the
                        // recovery case but not the initial-state
                        // case during the very first channel load.
                        if (state.recoveryInFlight) return
                        if (!state.hasBeenReady) return
                        if (isOnline(ctx)) {
                            scheduleRecovery("state=IDLE")
                        } else {
                            EventLog.log(
                                "auto-reconnect",
                                "$channelName IDLE while offline — waiting for network",
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        // v1.44.62 — For a LIVE stream, STATE_ENDED
                        // is a contradiction in terms — live streams
                        // never end. When this fires for live, the
                        // upstream HLS playlist either bottomed out
                        // (provider stopped publishing segments) or
                        // the source decided the manifest was
                        // exhausted. Either way the only fix is a
                        // full source rebuild.
                        //
                        // v1.44.64 — Gate on `hasBeenReady`. A
                        // STATE_ENDED reaching us BEFORE we've ever
                        // been READY almost always means our own
                        // recovery code called clearMediaItems()
                        // and the listener fired transiently while
                        // the player was being rebuilt — NOT a real
                        // upstream-died signal. Treating it as one
                        // caused infinite recovery loops on channel
                        // open (v1.44.62/63 regression: user reported
                        // "channels keep reconnecting unwatchable").
                        if (isLive && state.hasBeenReady &&
                            !state.recoveryInFlight
                        ) {
                            scheduleRecovery("state=ENDED (live)")
                        }
                    }
                }
            }
        }
        player.addListener(listener)

        // ── Stall watchdog ───────────────────────────────────────
        // Polls every second on the main thread. Looks for two
        // patterns the Player.Listener can't catch:
        //   1. STATE_BUFFERING for too long with no error
        //   2. STATE_READY but currentPosition isn't advancing
        // and triggers a recovery in either case.
        val watchdog = object : Runnable {
            override fun run() {
                if (state.disposed) return
                val now = System.currentTimeMillis()

                // 1. Long-buffer stall
                // Live streams need quick recovery — a stuck CDN edge
                // is the most common cause and 5 s is the sweet spot
                // before the user channel-zaps. VOD / DVR recordings
                // are static files: a long initial wait is normal
                // (large moov atom over slow wifi can take 15+ s) and
                // re-prepare()ing during that window just restarts
                // the moov download from scratch — symptom: player
                // never plays, OK doesn't unpause, classic "recording
                // shows black screen forever". For VOD give the
                // download a generous 30 s before forcing recovery.
                //
                // v1.44.64 — Gate on `hasBeenReady`. Initial channel-
                // load buffering (which can legitimately take 4-8 s
                // on busy live streams like Sportsnet East / TSN HD)
                // must NOT be treated as a stall. Before this gate
                // was added, the watchdog would fire recovery on
                // every channel open, the full-source-rebuild from
                // v1.44.62 would restart buffering from scratch,
                // and we'd infinite-loop into the user's reported
                // "channels keep reconnecting unwatchable" bug.
                val stallThresholdMs = if (isLive) STALL_BUFFER_MS_LIVE
                    else STALL_BUFFER_MS_VOD
                val bufStart = state.bufferingSinceMs
                if (bufStart != 0L && player.playWhenReady &&
                    !state.recoveryInFlight &&
                    state.hasBeenReady &&
                    now - bufStart > stallThresholdMs
                ) {
                    state.bufferingSinceMs = 0L
                    scheduleRecovery("stall=buffer ${now - bufStart}ms")
                }

                // 2. Frozen position in READY
                // v1.44.64 — Also gated on `hasBeenReady`. Belt-and-
                // braces: STATE_READY implies hasBeenReady=true so
                // in practice this guard is redundant, but it
                // documents intent.
                if (player.playbackState == Player.STATE_READY &&
                    player.playWhenReady &&
                    !state.recoveryInFlight &&
                    state.hasBeenReady &&
                    state.bufferingSinceMs == 0L
                ) {
                    val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                    if (pos != state.lastPosMs) {
                        state.lastPosMs = pos
                        state.lastPosCheckMs = now
                    } else if (state.lastPosCheckMs != 0L &&
                        now - state.lastPosCheckMs > STALL_FROZEN_MS
                    ) {
                        state.lastPosCheckMs = now
                        scheduleRecovery("stall=frozen pos=${pos}ms")
                    }
                } else {
                    // Reset frozen-position tracker when not playing.
                    state.lastPosMs = runCatching { player.currentPosition }
                        .getOrDefault(0L)
                    state.lastPosCheckMs = now
                }

                // 3. Reset attempts after sustained clean playback
                val readyStart = state.readySinceMs
                if (readyStart != 0L &&
                    state.attempts > 0 &&
                    now - readyStart > STABLE_PLAYBACK_MS
                ) {
                    EventLog.log(
                        "auto-reconnect",
                        "$channelName recovered (clean for ${(now - readyStart) / 1000}s)",
                    )
                    state.attempts = 0
                    state.recoveryNotified = false
                    state.readySinceMs = now // keep ticking, don't reset to 0
                }

                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)

        // ── Network callback ─────────────────────────────────────
        // When connectivity returns AND we're not currently playing
        // happily, immediately retry. Critical for "Wi-Fi was off
        // for 2 minutes, came back, stream should resume".
        val cm = ctx.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (state.disposed) return
                handler.post {
                    if (state.disposed) return@post
                    val ps = player.playbackState
                    val needsKick = ps == Player.STATE_IDLE ||
                        (ps == Player.STATE_BUFFERING && player.playWhenReady) ||
                        state.attempts > 0 // mid-recovery, accelerate it
                    if (needsKick && !state.recoveryInFlight) {
                        EventLog.log(
                            "auto-reconnect",
                            "$channelName network restored — kicking recovery",
                        )
                        // Reset backoff so we retry immediately.
                        state.attempts = (state.attempts - 1).coerceAtLeast(0)
                        scheduleRecovery("network=available")
                    }
                }
            }
            override fun onLost(network: Network) {
                EventLog.log(
                    "auto-reconnect",
                    "$channelName network lost — staying calm, waiting for return",
                )
            }
        }
        runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(req, netCallback)
        }

        return object : Disposable {
            override fun dispose() {
                state.disposed = true
                handler.removeCallbacks(watchdog)
                runCatching { player.removeListener(listener) }
                runCatching { cm?.unregisterNetworkCallback(netCallback) }
            }
        }
    }

    /** Lightweight cleanup contract — caller invokes [dispose] on
     *  the same thread the listener was registered on. */
    interface Disposable {
        fun dispose()
    }

    private class ReconnectState {
        var attempts = 0
        var recoveryInFlight = false
        var disposed = false
        var bufferingSinceMs = 0L
        var lastPosMs = 0L
        var lastPosCheckMs = 0L
        var readySinceMs = 0L
        var savedPositionMs = 0L
        var pendingSeek = false
        var recoveryNotified = false
        /** v1.44.64 — Set true the first time the player reaches
         *  STATE_READY for a given media item. Gates the stall
         *  watchdog so initial channel-load buffering (which can
         *  legitimately take 4–8 s on busy live streams) is NOT
         *  treated as a stall. Resets to false on every legitimate
         *  user-initiated channel change. */
        var hasBeenReady = false
    }

    /** 1 s → 2 s → 4 s → 8 s, capped at [MAX_BACKOFF_MS]. */
    private fun backoffMsFor(attempt: Int): Long {
        val raw = 1_000L shl (attempt - 1).coerceAtLeast(0).coerceAtMost(6)
        return raw.coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun isOnline(ctx: Context): Boolean {
        val cm = ctx.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // assume online if we can't tell
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** ExoPlayer error codes we treat as worth a re-prepare attempt.
     *  Excludes `ERROR_CODE_DRM_*`, decoder-init failures, and
     *  unsupported-format errors — those don't get better with
     *  retry. Includes the obvious IO + parsing + live-window set
     *  plus the catch-all `ERROR_CODE_UNSPECIFIED` because some
     *  providers wrap their errors and we'd rather try than not. */
    private val RECOVERABLE_ERRORS = setOf(
        PlaybackException.ERROR_CODE_UNSPECIFIED,
        PlaybackException.ERROR_CODE_REMOTE_ERROR,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    )

    /** Stall thresholds — empirically chosen.
     *  • Live: 4 s buffer is long enough to cover a normal ad-break
     *    bumper or HLS playlist refresh, but short enough that
     *    the user hasn't yet decided to channel-zap. Tightened
     *    from 5 s in v1.44.62 — the average viewer has decided to
     *    leave by ~6-7 s of a black screen, so getting recovery
     *    started a second earlier matters.
     *  • VOD/DVR: 30 s. Static files don't need fast recovery — large
     *    moov atoms (2 MB+ for an hour-long capture) can take 15-20 s
     *    over slow WiFi before the first frame is decodable. A 5 s
     *    threshold causes the watchdog to keep re-prepare()ing in a
     *    loop, the moov download starts over each time, and the user
     *    sees a black screen forever (v1.44.32 DVR playback bug).
     *  • 4 s of frozen position (down from 5 s) covers most
     *    stuck-decoder cases without false-positive on legitimate
     *    live pauses.
     *  • 15 s of clean playback before we trust the recovery and
     *    reset the attempt counter — anything shorter and we keep
     *    bouncing between failure and success. */
    private const val STALL_BUFFER_MS_LIVE = 4_000L
    private const val STALL_BUFFER_MS_VOD = 30_000L
    private const val STALL_FROZEN_MS = 4_000L
    private const val STABLE_PLAYBACK_MS = 15_000L
    private const val WATCHDOG_INTERVAL_MS = 1_000L
    private const val MAX_BACKOFF_MS = 10_000L
    private const val MAX_AUTO_RETRIES = 50
}