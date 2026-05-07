package com.hushtv.tv.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.hushtv.tv.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-fidelity per-session playback telemetry, built specifically to
 * track down "the recording opens but never plays" black-screen
 * problems on Cloud-DVR streams without guessing.
 *
 * The freeze monitor [PlaybackFreezeMonitor] already reports when a
 * stream is wedged. That's reactive — fires after 3 s of buffer-stall
 * or on PlayerError. This class is *proactive*: it transcribes the
 * full internal life of the player from the moment it's attached
 * until detached:
 *
 *   • Every Player.Listener event (state, isPlaying, video size,
 *     tracks, position discontinuity, timeline change, error).
 *   • Every AnalyticsListener event we care about for diagnostic
 *     purposes: load started / completed / error / canceled, decoder
 *     init, video & audio input format changed, downstream format
 *     change, dropped frames, RENDERED FIRST FRAME (the holy grail —
 *     if this never fires, the player never put a pixel on screen),
 *     video / audio codec error.
 *   • Active HTTP probe of the stream URL: HEAD response (status,
 *     headers), then a single Range GET of the first 64 KB to verify
 *     server returns video/mp4 with a recognisable ftyp/moov atom.
 *
 * On [detach] (or after [SESSION_AUTO_FLUSH_MS] if the screen never
 * disposes), the buffered events are POSTed to the crash server with
 * `kind=playback_telemetry`. The dashboard surfaces these alongside
 * crashes so we can see exactly what ExoPlayer did with the file.
 *
 * Designed for Cloud-DVR debugging but agnostic — also attaches to
 * live + VOD playback so we get a useful corpus to compare against.
 */
class PlaybackTelemetry private constructor(
    private val ctx: Context,
    private val player: ExoPlayer,
    private val streamUrl: String,
    private val isLive: Boolean,
    private val channelName: String,
    private val isDvr: Boolean,
) {
    private val attachedAtMs = System.currentTimeMillis()
    private val attachedAtNs = System.nanoTime()
    private val events = StringBuilder(8 * 1024)
    private val eventCount = AtomicInteger(0)
    private val firstFrameRendered = AtomicBoolean(false)
    private val flushed = AtomicBoolean(false)
    private val audioDecoderInit = AtomicBoolean(false)
    private val videoDecoderInit = AtomicBoolean(false)
    private val sawAudioFormat = AtomicBoolean(false)
    @Volatile private var detached = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            log("Player.STATE", stateName(state))
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            log("Player.isPlaying", "$isPlaying")
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            log("Player.playWhenReady", "$playWhenReady reason=$reason")
        }
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            log(
                "Player.videoSize",
                "${videoSize.width}x${videoSize.height} par=${videoSize.pixelWidthHeightRatio}",
            )
        }
        override fun onTracksChanged(tracks: Tracks) {
            val groups = tracks.groups
            log("Player.tracks", "groups=${groups.size}")
            groups.forEach { tg ->
                for (i in 0 until tg.length) {
                    val fmt = tg.getTrackFormat(i)
                    val supported = tg.isTrackSupported(i)
                    val selected = tg.isTrackSelected(i)
                    log(
                        "Player.track",
                        "type=${trackTypeName(tg.type)} id=${fmt.id} " +
                            "codec=${fmt.codecs} mime=${fmt.sampleMimeType} " +
                            "${fmt.width}x${fmt.height}@${fmt.frameRate}fps " +
                            "br=${fmt.bitrate} supported=$supported selected=$selected",
                    )
                }
            }
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            val durMs = if (timeline.windowCount > 0) {
                timeline.getWindow(0, androidx.media3.common.Timeline.Window()).durationMs
            } else 0
            log("Player.timeline", "windows=${timeline.windowCount} reason=$reason dur=${durMs}ms")
        }
        override fun onPlayerError(error: PlaybackException) {
            log(
                "Player.ERROR",
                "code=${error.errorCode} name=${error.errorCodeName} " +
                    "msg=${error.message?.take(200)} " +
                    "cause=${error.cause?.javaClass?.simpleName}:${error.cause?.message?.take(200)}",
            )
        }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            log(
                "Player.posDisc",
                "from=${oldPosition.positionMs}ms to=${newPosition.positionMs}ms reason=$reason",
            )
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            log(
                "Loader.start",
                "uri=${loadEventInfo.uri.toString().takeLast(140)} " +
                    "type=${dataTypeName(mediaLoadData.dataType)}",
            )
        }
        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            log(
                "Loader.done",
                "type=${dataTypeName(mediaLoadData.dataType)} " +
                    "loadMs=${loadEventInfo.loadDurationMs} " +
                    "bytes=${loadEventInfo.bytesLoaded} " +
                    "track=${mediaLoadData.trackType}",
            )
        }
        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            log(
                "Loader.ERROR",
                "type=${dataTypeName(mediaLoadData.dataType)} " +
                    "uri=${loadEventInfo.uri.toString().takeLast(140)} " +
                    "loadMs=${loadEventInfo.loadDurationMs} " +
                    "bytes=${loadEventInfo.bytesLoaded} " +
                    "exception=${error.javaClass.simpleName}:${error.message?.take(200)} " +
                    "canceled=$wasCanceled",
            )
        }
        override fun onLoadCanceled(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            log(
                "Loader.cancel",
                "type=${dataTypeName(mediaLoadData.dataType)} bytes=${loadEventInfo.bytesLoaded}",
            )
        }
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) {
            log("Renderer.video.format", formatStr(format))
        }
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) {
            sawAudioFormat.set(true)
            log("Renderer.audio.format", formatStr(format))
        }
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoderInit.set(true)
            log(
                "Renderer.video.decoder",
                "name=$decoderName initMs=$initializationDurationMs",
            )
        }
        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            audioDecoderInit.set(true)
            log(
                "Renderer.audio.decoder",
                "name=$decoderName initMs=$initializationDurationMs",
            )
        }
        override fun onDownstreamFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            mediaLoadData: MediaLoadData,
        ) {
            val fmt = mediaLoadData.trackFormat
            log(
                "Renderer.downstream.fmt",
                "type=${trackTypeName(mediaLoadData.trackType)} " +
                    "format=${if (fmt != null) formatStr(fmt) else "null"}",
            )
        }
        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long,
        ) {
            firstFrameRendered.set(true)
            log(
                "Renderer.FIRST_FRAME",
                "output=${output.javaClass.simpleName} elapsedSinceAttachMs=${renderTimeMs}",
            )
        }
        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            log("Renderer.dropped", "frames=$droppedFrames elapsedMs=$elapsedMs")
        }
        override fun onVideoCodecError(
            eventTime: AnalyticsListener.EventTime,
            videoCodecError: Exception,
        ) {
            log(
                "Renderer.video.ERROR",
                "${videoCodecError.javaClass.simpleName}:${videoCodecError.message?.take(200)}",
            )
        }
        override fun onAudioCodecError(
            eventTime: AnalyticsListener.EventTime,
            audioCodecError: Exception,
        ) {
            log(
                "Renderer.audio.ERROR",
                "${audioCodecError.javaClass.simpleName}:${audioCodecError.message?.take(200)}",
            )
        }
    }

    init {
        log("attach", "url=${streamUrl.takeLast(140)} isLive=$isLive isDvr=$isDvr name=$channelName")
        log(
            "device",
            "${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} app=v${BuildConfig.VERSION_NAME}#${BuildConfig.VERSION_CODE}",
        )
        try {
            player.addListener(playerListener)
            player.addAnalyticsListener(analyticsListener)
            log("listeners", "attached OK")
        } catch (t: Throwable) {
            log("listeners", "FAILED ${t.javaClass.simpleName}:${t.message}")
        }
        // HTTP probe runs off-main; result is appended once it
        // returns. We only probe DVR URLs to keep the noise down.
        if (isDvr) {
            executor.execute { runCatching { httpProbe() } }
        }
    }

    fun detach() {
        if (detached) return
        detached = true
        runCatching { player.removeListener(playerListener) }
        runCatching { player.removeAnalyticsListener(analyticsListener) }
        log("detach", "elapsedMs=${System.currentTimeMillis() - attachedAtMs} firstFrame=${firstFrameRendered.get()}")
        flushIfDvr("detach")
    }

    /** Submit telemetry to the crash server. Idempotent — we only
     *  flush once per session. We emit a report for any session that
     *  is suspicious from a debugging POV:
     *   • DVR sessions (always — used to drive forward DVR fixes).
     *   • Any Player.ERROR / Renderer.ERROR / Loader.ERROR.
     *   • A video-decoder-init session that didn't see any audio
     *     format AND ran for more than 8 s — strong signal that
     *     the audio track is silently unavailable on this device
     *     (e.g. AC3/EAC3 missing decoder, malformed audio format).
     *     v1.44.36 — debugging "Blue Ruin has no audio". */
    private fun flushIfDvr(reason: String) {
        if (!flushed.compareAndSet(false, true)) return
        val hasError = events.contains("Player.ERROR") ||
            events.contains("Renderer.video.ERROR") ||
            events.contains("Renderer.audio.ERROR") ||
            events.contains("Loader.ERROR")
        val sessionMs = System.currentTimeMillis() - attachedAtMs
        val audioMissing = videoDecoderInit.get() &&
            !audioDecoderInit.get() &&
            !sawAudioFormat.get() &&
            sessionMs > 8_000L
        if (!isDvr && !hasError && !audioMissing) return
        executor.execute {
            runCatching { upload(reason, hasError, audioMissing) }
                .onFailure { Log.w(TAG, "upload failed ${it.message}") }
        }
    }

    private fun upload(flushReason: String, hasError: Boolean, audioMissing: Boolean) {
        val fmtUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val deviceId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                ?.take(8)
        }.getOrNull() ?: "unknown"
        val device = "${Build.MANUFACTURER}-${Build.MODEL}-$deviceId"
            .replace(' ', '-').take(60)
        val now = fmtUtc.format(Date())

        val sb = StringBuilder()
        sb.append("===== HushTV ").append(now)
            .append(" v").append(BuildConfig.VERSION_NAME)
            .append("#").append(BuildConfig.VERSION_CODE)
            .append(" thread=playback-telemetry =====\n")
        sb.append("PLAYBACK-TELEMETRY flushReason=").append(flushReason)
            .append(" firstFrame=").append(firstFrameRendered.get())
            .append(" videoDecoderInit=").append(videoDecoderInit.get())
            .append(" audioDecoderInit=").append(audioDecoderInit.get())
            .append(" sawAudioFormat=").append(sawAudioFormat.get())
            .append(" hasError=").append(hasError)
            .append(" audioMissing=").append(audioMissing)
            .append(" eventCount=").append(eventCount.get()).append('\n')
        sb.append("device=").append(device).append('\n')
        sb.append("android_sdk=").append(Build.VERSION.SDK_INT).append('\n')
        sb.append("android_release=").append(Build.VERSION.RELEASE).append('\n')
        sb.append("isLive=").append(isLive).append('\n')
        sb.append("isDvr=").append(isDvr).append('\n')
        sb.append("channelName=").append(channelName).append('\n')
        sb.append("streamUrl=").append(streamUrl).append('\n')
        sb.append("attachedAt=").append(fmtUtc.format(Date(attachedAtMs))).append('\n')
        sb.append("sessionDurationMs=").append(System.currentTimeMillis() - attachedAtMs).append('\n')
        sb.append("\n─── Event log ───\n")
        sb.append(events)

        val body = jsonObject {
            put("device", device)
            put("android_sdk", Build.VERSION.SDK_INT.toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("version_code", BuildConfig.VERSION_CODE.toString())
            put("captured_at", now)
            put("installed_version", BuildConfig.VERSION_NAME)
            put("kind", "playback_telemetry")
            put("trace", sb.toString())
        }
        post(ENDPOINT, body)
    }

    /** Issue a HEAD then a Range GET to the stream URL and write the
     *  results into the event log. Synchronous — runs on the
     *  background executor. */
    private fun httpProbe() {
        // 1. HEAD
        runCatching {
            val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("User-Agent", "HushTV-PlaybackTelemetry/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            val ct = conn.getHeaderField("Content-Type") ?: "(none)"
            val cl = conn.getHeaderField("Content-Length") ?: "(none)"
            val ar = conn.getHeaderField("Accept-Ranges") ?: "(none)"
            log(
                "HttpProbe.HEAD",
                "code=$code Content-Type=$ct Content-Length=$cl Accept-Ranges=$ar",
            )
            conn.disconnect()
        }.onFailure {
            log("HttpProbe.HEAD.ERROR", "${it.javaClass.simpleName}:${it.message?.take(200)}")
        }
        // 2. Range GET first 64 KB — verify ftyp/moov/moof presence.
        runCatching {
            val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("Range", "bytes=0-65535")
                setRequestProperty("User-Agent", "HushTV-PlaybackTelemetry/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            val ct = conn.getHeaderField("Content-Type") ?: "(none)"
            val cl = conn.getHeaderField("Content-Length") ?: "(none)"
            val cr = conn.getHeaderField("Content-Range") ?: "(none)"
            val buf = conn.inputStream.use { input ->
                val out = ByteArray(65536)
                var off = 0
                while (off < out.size) {
                    val n = input.read(out, off, out.size - off)
                    if (n <= 0) break
                    off += n
                }
                if (off == out.size) out else out.copyOf(off)
            }
            val ftyp = buf.indexOf2(byteArrayOf(0x66, 0x74, 0x79, 0x70))
            val moov = buf.indexOf2(byteArrayOf(0x6D, 0x6F, 0x6F, 0x76))
            val moof = buf.indexOf2(byteArrayOf(0x6D, 0x6F, 0x6F, 0x66))
            val major = if (ftyp >= 0 && ftyp + 8 <= buf.size) {
                String(buf, ftyp + 4, 4, Charsets.US_ASCII)
            } else "(none)"
            log(
                "HttpProbe.GET64K",
                "code=$code bytes=${buf.size} Content-Type=$ct Content-Length=$cl Content-Range=$cr " +
                    "ftypAt=$ftyp major=$major moovAt=$moov moofAt=$moof",
            )
            conn.disconnect()
        }.onFailure {
            log("HttpProbe.GET.ERROR", "${it.javaClass.simpleName}:${it.message?.take(200)}")
        }
    }

    private fun log(category: String, detail: String) {
        // Cap event count so a runaway loop can't OOM us.
        if (eventCount.incrementAndGet() > MAX_EVENTS) return
        val tMs = System.currentTimeMillis() - attachedAtMs
        synchronized(events) {
            events.append(String.format(Locale.US, "[%6d ms] ", tMs))
                .append(category).append("  ").append(detail).append('\n')
        }
    }

    private fun formatStr(f: Format): String =
        "id=${f.id} codec=${f.codecs} mime=${f.sampleMimeType} ${f.width}x${f.height}@${f.frameRate}fps br=${f.bitrate}"

    private fun stateName(s: Int) = when (s) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "STATE_$s"
    }

    private fun trackTypeName(t: Int) = when (t) {
        androidx.media3.common.C.TRACK_TYPE_VIDEO -> "video"
        androidx.media3.common.C.TRACK_TYPE_AUDIO -> "audio"
        androidx.media3.common.C.TRACK_TYPE_TEXT -> "text"
        androidx.media3.common.C.TRACK_TYPE_METADATA -> "meta"
        androidx.media3.common.C.TRACK_TYPE_NONE -> "none"
        else -> "type_$t"
    }

    private fun dataTypeName(t: Int) = when (t) {
        androidx.media3.common.C.DATA_TYPE_MEDIA -> "media"
        androidx.media3.common.C.DATA_TYPE_MEDIA_INITIALIZATION -> "media-init"
        androidx.media3.common.C.DATA_TYPE_MANIFEST -> "manifest"
        androidx.media3.common.C.DATA_TYPE_DRM -> "drm"
        androidx.media3.common.C.DATA_TYPE_UNKNOWN -> "unknown"
        else -> "data_$t"
    }

    companion object {
        private const val TAG = "PlaybackTelemetry"
        private const val MAX_EVENTS = 800
        private const val ENDPOINT =
            "https://hushtv.xyz/crash/submit/GbExkT_0wVwqMbw5mwOrRMbe1pS3PghK"

        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "hushtv-playback-telemetry").apply { isDaemon = true }
        }

        /**
         * Attach to a player about to begin playback. Caller must call
         * [PlaybackTelemetry.detach] when the screen is disposed.
         */
        fun attach(
            ctx: Context,
            player: ExoPlayer,
            streamUrl: String,
            isLive: Boolean,
            channelName: String,
        ): PlaybackTelemetry {
            val isDvr = DvrApi.parseRecordingUrl(streamUrl) != null
            return PlaybackTelemetry(ctx, player, streamUrl, isLive, channelName, isDvr)
        }

        private fun post(url: String, body: String): Boolean {
            var conn: HttpURLConnection? = null
            return try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("User-Agent", "HushTV/${BuildConfig.VERSION_NAME}")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                Log.i(TAG, "telemetry upload → HTTP $code (body=${body.length}b)")
                code in 200..299
            } catch (e: IOException) {
                Log.w(TAG, "telemetry upload failed: ${e.message}")
                false
            } finally {
                conn?.disconnect()
            }
        }

        private inline fun jsonObject(body: JsonBuilder.() -> Unit): String =
            JsonBuilder().apply(body).toString()

        private class JsonBuilder {
            private val sb = StringBuilder("{")
            private var first = true
            fun put(key: String, value: String) {
                if (!first) sb.append(',')
                first = false
                sb.append('"').append(key).append("\":")
                sb.append('"').append(escape(value)).append('"')
            }
            override fun toString() = sb.append('}').toString()

            private fun escape(s: String): String {
                val out = StringBuilder(s.length + 16)
                for (c in s) {
                    when (c) {
                        '\\' -> out.append("\\\\")
                        '"' -> out.append("\\\"")
                        '\n' -> out.append("\\n")
                        '\r' -> out.append("\\r")
                        '\t' -> out.append("\\t")
                        '\b' -> out.append("\\b")
                        '\u000C' -> out.append("\\f")
                        else -> if (c.code < 0x20) {
                            out.append(String.format("\\u%04x", c.code))
                        } else out.append(c)
                    }
                }
                return out.toString()
            }
        }
    }
}

/** First index of [needle] in this [ByteArray], or -1 if not found.
 *  Suffix `2` to avoid clashing with stdlib `indexOf` extensions. */
private fun ByteArray.indexOf2(needle: ByteArray): Int {
    if (needle.isEmpty()) return 0
    outer@ for (i in 0..(this.size - needle.size)) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}
