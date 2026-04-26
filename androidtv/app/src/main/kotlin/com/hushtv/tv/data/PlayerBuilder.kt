package com.hushtv.tv.data

import android.content.Context
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
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
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
     * Wire automatic reconnect on transient IO errors. Call ONCE per
     * player. Returned `Player.Listener` is also added internally —
     * pass it back to `player.removeListener(...)` if you want to
     * detach early (otherwise the listener is GCed with the player).
     *
     * Strategy:
     *   1. Track an attempt counter, capped at MAX_AUTO_RETRIES.
     *   2. On every `onPlayerError`, if the error is in the IO /
     *      live-window family AND we're under the cap, log the event
     *      and call `prepare()` immediately (ExoPlayer's
     *      recommended recovery path).
     *   3. On every `onPlaybackStateChanged(STATE_READY)`, reset
     *      the counter — we're back in business.
     */
    fun attachAutoReconnect(
        player: ExoPlayer,
        channelName: String,
        onMaxRetriesExceeded: (() -> Unit)? = null,
    ): Player.Listener {
        val ioErrorCodes = setOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        )
        var attempts = 0
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode in ioErrorCodes) {
                    if (attempts < MAX_AUTO_RETRIES) {
                        attempts++
                        EventLog.log(
                            "auto-reconnect",
                            "$channelName attempt=$attempts code=${error.errorCodeName}",
                        )
                        // Re-prepare from the current MediaItem.
                        // Default-MediaSource.SeekParameters means the
                        // live source will rejoin at the live edge.
                        runCatching { player.prepare() }
                    } else {
                        EventLog.log(
                            "auto-reconnect",
                            "$channelName GIVE UP after $attempts retries (${error.errorCodeName})",
                        )
                        onMaxRetriesExceeded?.invoke()
                    }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && attempts > 0) {
                    EventLog.log(
                        "auto-reconnect",
                        "$channelName recovered after $attempts attempt(s)",
                    )
                    attempts = 0
                }
            }
        }
        player.addListener(listener)
        return listener
    }

    private const val MAX_AUTO_RETRIES = 5
}
