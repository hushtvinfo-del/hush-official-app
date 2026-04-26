package com.hushtv.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hushtv.tv.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Catches "the channel froze" cases that aren't crashes — the JVM is
 * fine, the player is just stuck in STATE_BUFFERING with no incoming
 * data. Standard crash reporters (which hook
 * Thread.UncaughtExceptionHandler) miss these entirely.
 *
 * Usage from a Composable:
 *
 *   val ctx = LocalContext.current
 *   DisposableEffect(player) {
 *       val monitor = PlaybackFreezeMonitor.attach(
 *           ctx, player, streamUrl = currentUrl, isLive = isLive,
 *           channelName = currentName,
 *       )
 *       onDispose { monitor.detach() }
 *   }
 *
 * Triggers a report when:
 *   • Player has been in STATE_BUFFERING for more than 6 seconds while
 *     `playWhenReady = true` (i.e. user wants playback, but data isn't
 *     flowing).
 *   • Player emits `onPlayerError`.
 *
 * To avoid spamming the server during e.g. a flaky network, each
 * `attach()` lifetime fires AT MOST one freeze report — when the user
 * channel-zaps to recover, the new attach() resets that flag.
 */
class PlaybackFreezeMonitor private constructor(
    private val ctx: Context,
    private val player: ExoPlayer,
    private val streamUrl: String,
    private val isLive: Boolean,
    private val channelName: String,
) {
    private val attachedAt = System.currentTimeMillis()
    private val bufferingSinceMs = AtomicLong(0L)
    private val freezeReported = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastError: PlaybackException? = null
    @Volatile private var detached = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val checkInterval = 1000L
    // Drop from 6 s → 3 s. Real Fire Stick / Shield freezes always
    // outlast 3 s; user-perceived "instant freeze" is typically already
    // 2-3 s of stall by the time they notice.
    private val freezeThresholdMs = 3_000L

    // Frozen-position detection — sometimes the player stays in
    // STATE_READY but stops advancing (decoder OK, network dead).
    // Track last-observed position and emit a report if it hasn't moved
    // for `freezeThresholdMs` while the user wants playback.
    private var lastPosMs = 0L
    private var lastPosCheckMs = System.currentTimeMillis()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (detached) return
            when (state) {
                Player.STATE_BUFFERING -> {
                    if (bufferingSinceMs.get() == 0L) {
                        bufferingSinceMs.set(System.currentTimeMillis())
                        EventLog.log(
                            "freeze-monitor",
                            "ENTER STATE_BUFFERING url=${streamUrl.takeLast(60)}",
                        )
                    }
                }
                Player.STATE_READY -> {
                    if (bufferingSinceMs.get() != 0L) {
                        EventLog.log(
                            "freeze-monitor",
                            "EXIT STATE_BUFFERING (recovered after " +
                                "${System.currentTimeMillis() - bufferingSinceMs.get()}ms)",
                        )
                        bufferingSinceMs.set(0L)
                    }
                }
                Player.STATE_IDLE,
                Player.STATE_ENDED -> {
                    bufferingSinceMs.set(0L)
                }
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            if (detached) return
            lastError = error
            EventLog.log(
                "freeze-monitor",
                "onPlayerError code=${error.errorCode} name=${error.errorCodeName}",
            )
            scheduleReport(reason = "PlayerError")
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (detached) return
            val now = System.currentTimeMillis()
            // 1. Buffering-stall detection.
            val since = bufferingSinceMs.get()
            if (since != 0L && player.playWhenReady) {
                val stuckMs = now - since
                if (stuckMs > freezeThresholdMs) {
                    scheduleReport(reason = "BufferingStall:${stuckMs}ms")
                    bufferingSinceMs.set(0L)
                }
            }
            // 2. Frozen-position detection — player says READY + pwr=true,
            //    but currentPosition isn't advancing. Live streams can
            //    legitimately not advance during ad breaks or DVR pauses,
            //    but >3 s with no movement on a live IPTV feed is a
            //    network-side freeze.
            if (player.playbackState == Player.STATE_READY &&
                player.playWhenReady &&
                bufferingSinceMs.get() == 0L
            ) {
                val pos = player.currentPosition
                if (pos != lastPosMs) {
                    lastPosMs = pos
                    lastPosCheckMs = now
                } else if (now - lastPosCheckMs > freezeThresholdMs) {
                    scheduleReport(
                        reason = "FrozenPosition:pos=${pos}ms idleMs=${now - lastPosCheckMs}",
                    )
                    // Reset so we don't keep re-firing every tick.
                    lastPosCheckMs = now
                }
            } else {
                // Not playing → reset position tracker.
                lastPosMs = player.currentPosition
                lastPosCheckMs = now
            }
            handler.postDelayed(this, checkInterval)
        }
    }

    private fun scheduleReport(reason: String) {
        if (!freezeReported.compareAndSet(false, true)) return
        executor.execute {
            runCatching { uploadFreeze(reason) }
                .onFailure { Log.w(TAG, "freeze upload failed: ${it.message}") }
        }
    }

    private fun uploadFreeze(reason: String) {
        val body = buildPayload(reason)
        val ok = postJson(ENDPOINT, body)
        EventLog.log("freeze-monitor", "freeze upload → ${if (ok) "200" else "FAIL"}")
    }

    private fun buildPayload(reason: String): String {
        val fmtUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val deviceId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                ?.take(8)
        }.getOrNull() ?: "unknown"
        val device = "${Build.MANUFACTURER}-${Build.MODEL}-$deviceId"
            .replace(' ', '-').take(60)

        // Player state snapshot (read on main thread for safety).
        val ms = runOnMain {
            mapOf(
                "state" to when (player.playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "?"
                },
                "pwr" to player.playWhenReady,
                "pos" to player.currentPosition,
                "buffered" to player.totalBufferedDuration,
            )
        } ?: emptyMap()
        val state = ms["state"] ?: "?"
        val pwr = ms["pwr"] ?: false
        val pos = ms["pos"] ?: 0L
        val buffered = ms["buffered"] ?: 0L
        val now = System.currentTimeMillis()
        val sessionMs = now - attachedAt
        val errCode = lastError?.errorCodeName ?: "—"
        val errMsg = lastError?.message ?: "—"

        // Network snapshot.
        val (netType, netDetails) = describeNetwork(ctx)

        // Recent events from the in-app ring buffer.
        val events = EventLog.snapshot().takeLast(8 * 1024)

        // Thread dump (just main thread + the player's internal threads
        // are interesting here — capping at 6 KB).
        val threads = Thread.getAllStackTraces().entries
            .joinToString("\n\n") { (t, frames) ->
                "Thread ${t.name} (state=${t.state}, daemon=${t.isDaemon}):\n" +
                    frames.take(20).joinToString("\n") { "  at $it" }
            }
            .let { if (it.length > 6 * 1024) it.takeLast(6 * 1024) else it }

        // Reuse the crash server's machine-readable header so the
        // dashboard tags this with the right captured-at + version.
        val header =
            "===== HushTV ${fmtUtc.format(Date())} v${BuildConfig.VERSION_NAME}#${BuildConfig.VERSION_CODE} thread=freeze-monitor =====\n" +
                "FREEZE-REPORT reason=$reason\n" +
                "channel=$channelName isLive=$isLive\n" +
                "url=$streamUrl\n" +
                "playerState=$state pwr=$pwr pos=${pos}ms buffered=${buffered}ms\n" +
                "sessionAge=${sessionMs}ms\n" +
                "lastError=$errCode  msg=$errMsg\n" +
                "network=$netType $netDetails\n" +
                "androidSdk=${Build.VERSION.SDK_INT}\n" +
                "─── recent events ──────────────────────────\n" +
                events +
                "\n─── thread dump ────────────────────────────\n" +
                threads + "\n"

        return JsonBuilder().apply {
            put("device", device)
            put("android_sdk", Build.VERSION.SDK_INT.toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("version_code", BuildConfig.VERSION_CODE.toString())
            put("captured_at", fmtUtc.format(Date()))
            put("installed_version", BuildConfig.VERSION_NAME)
            put("kind", "freeze")
            put("trace", header)
        }.toString()
    }

    fun detach() {
        if (detached) return
        // If we're detaching mid-stall (user channel-zapped to recover
        // from a freeze BEFORE the threshold hit), still flush a
        // "best-effort" report so the data isn't lost. This catches the
        // "I waited 2 s, freezed, channel-flipped" case which the
        // pre-tightening monitor missed entirely.
        val now = System.currentTimeMillis()
        val since = bufferingSinceMs.get()
        if (since != 0L && (now - since) > 1_500L) {
            scheduleReport(
                reason = "DetachWhileBuffering:${now - since}ms",
            )
        }
        detached = true
        runCatching { player.removeListener(listener) }
        handler.removeCallbacks(ticker)
        EventLog.log("freeze-monitor", "detached")
    }

    /** Read a value off the player on the Main thread (it doesn't like
     *  being touched from background threads). Blocks the caller for at
     *  most ~50 ms. Returns null on timeout. */
    private fun <T : Any> runOnMain(block: () -> T): T? {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return block()
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        var box: T? = null
        handler.post {
            box = runCatching(block).getOrNull()
            latch.countDown()
        }
        latch.await(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        return box
    }

    companion object {
        private const val TAG = "FreezeMonitor"
        // Same gateway as crash uploads — server tags by `kind` field.
        private const val ENDPOINT =
            "https://hushtv.xyz/crash/submit/GbExkT_0wVwqMbw5mwOrRMbe1pS3PghK"

        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "hushtv-freeze-upload").apply { isDaemon = true }
        }

        fun attach(
            ctx: Context,
            player: ExoPlayer,
            streamUrl: String,
            isLive: Boolean,
            channelName: String,
        ): PlaybackFreezeMonitor {
            val m = PlaybackFreezeMonitor(
                ctx.applicationContext, player, streamUrl, isLive, channelName,
            )
            player.addListener(m.listener)
            m.handler.postDelayed(m.ticker, m.checkInterval)
            EventLog.log(
                "freeze-monitor",
                "attached for $channelName (isLive=$isLive)",
            )
            return m
        }

        private fun describeNetwork(ctx: Context): Pair<String, String> {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return "?" to ""
            val net = cm.activeNetwork ?: return "OFFLINE" to ""
            val caps = cm.getNetworkCapabilities(net) ?: return "?" to ""
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "OTHER"
            }
            val down = caps.linkDownstreamBandwidthKbps
            val up = caps.linkUpstreamBandwidthKbps
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            return type to "down=${down}kbps up=${up}kbps validated=$validated"
        }

        private fun postJson(url: String, body: String): Boolean {
            var conn: HttpURLConnection? = null
            return try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("User-Agent", "HushTV/${BuildConfig.VERSION_NAME}-freeze")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                conn.responseCode == 200
            } catch (e: IOException) {
                Log.w(TAG, "freeze upload failed: ${e.message}")
                false
            } finally {
                conn?.disconnect()
            }
        }

        // Tiny hand-rolled JSON builder — same shape as CrashReporter so
        // the server side can parse both reports identically.
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
