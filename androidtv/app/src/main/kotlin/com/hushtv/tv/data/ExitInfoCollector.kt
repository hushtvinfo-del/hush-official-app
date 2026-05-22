package com.hushtv.tv.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.hushtv.tv.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Post-mortem capture using Android's first-party `ApplicationExitInfo`
 * API (Android 11 / API 30+).
 *
 * Why this exists:
 *   v1.44.118 admin telemetry showed Fire Stick AFTSS / AFTKM (1 GB
 *   RAM) users `19530123`, `irandall16`, `jamvan55@gmail.com` were
 *   experiencing silent app shutdowns IMMEDIATELY after launch — and
 *   crucially with ZERO Java crashes captured. That fingerprint is
 *   either:
 *     (a) a kernel low-memory kill (`REASON_LOW_MEMORY`), or
 *     (b) a native crash inside ExoPlayer libavcodec.so / Coil's
 *         bitmap decoder (`REASON_CRASH_NATIVE` with SIGSEGV/SIGABRT),
 *     (c) an OS signal kill (`REASON_SIGNALED`).
 *
 *   Java-only crash handlers can NEVER see (a)/(b)/(c). They bypass
 *   `Thread.setDefaultUncaughtExceptionHandler` because the process is
 *   torn down by the kernel or the bionic linker — there's no JVM
 *   left to invoke a handler.
 *
 *   `ApplicationExitInfo` is the official post-mortem replacement.
 *   Each entry carries:
 *     • `reason`       — REASON_CRASH / REASON_CRASH_NATIVE /
 *                        REASON_LOW_MEMORY / REASON_SIGNALED / …
 *     • `status`       — the signal number (SIGSEGV=11, SIGABRT=6, …)
 *     • `description`  — human-readable summary
 *     • `pss` / `rss`  — memory footprint at the moment of death
 *     • `traceInputStream()` — native tombstone (for native crashes)
 *                              or main-thread dump (for ANRs)
 *
 * Flow:
 *   1. On each app launch, [collect] walks the OS's process-exit
 *      history.
 *   2. It uses a SharedPreferences high-watermark (last seen
 *      `timestamp`) to skip exits we've already reported.
 *   3. For each fresh ABNORMAL exit (anything except user-requested
 *      / signaled-by-user / dependency etc) it builds a structured
 *      payload and POSTs to the same `/crash/submit/...` endpoint as
 *      [CrashReporter], tagging `kind="exit_info"` so the admin
 *      dashboard can filter on it.
 *   4. Best-effort. Failures are silent — we'll re-try on the next
 *      launch because the watermark only advances on HTTP 200.
 *
 * No-op on API < 30 (Android 10 / Fire OS 7). For those devices the
 * existing Java + ANR pipeline is all we have, but those are also a
 * shrinking minority of the Fire Stick fleet.
 *
 * v1.44.121 — persist-first hardening. Each exit's payload is now
 * written to `filesDir/exit_info_pending/<ts>.json` BEFORE we attempt
 * to upload, and the watermark advances IMMEDIATELY after that write
 * (not after upload). This survives process death mid-upload — if the
 * app gets OOM-killed again before the HTTP POST completes, the
 * payload is still on disk and gets retried on the next launch.
 * Without this, devices that crash every 10-15 seconds (the actual
 * AFTSSS / AFTKRT pattern revealed by v1.44.120 telemetry) never
 * managed to ship a single exit-info report.
 *
 * v1.44.120 — initial release.
 */
object ExitInfoCollector {
    private const val TAG = "ExitInfoCollector"
    private const val ENDPOINT =
        "https://hushtv.xyz/crash/submit/GbExkT_0wVwqMbw5mwOrRMbe1pS3PghK"
    private const val PREFS = "exit_info_collector"
    private const val KEY_LAST_TIMESTAMP_MS = "last_exit_ts_ms"
    private const val MAX_HISTORICAL = 20
    private const val PENDING_DIR = "exit_info_pending"
    private const val MAX_PENDING_FILES = 32

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hushtv-exit-info").apply { isDaemon = true }
    }

    /** Kicks off a one-shot collection on a background thread. Safe to
     *  call on every app launch — no-ops if nothing's new. */
    fun collect(ctx: Context) {
        executor.execute {
            // v1.44.121 — always flush any pending payloads from the
            // previous launch (these were persisted to disk before the
            // app died). Runs even on API < 30 because the pending dir
            // might contain payloads from a previous-installed version
            // that DID run on API 30+.
            runCatching { uploadPending(ctx) }
                .onFailure { Log.w(TAG, "pending upload failed: ${it.message}") }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // ApplicationExitInfo is API 30+. Older devices fall
                // back to the legacy crash.log / anr.log pipeline.
                return@execute
            }
            runCatching { collectInner(ctx) }
                .onFailure { Log.w(TAG, "exit-info collect failed: ${it.message}") }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun collectInner(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // Pass pid=0 to get reasons for all instances of THIS process
        // (including ones that died before the current process was
        // created — which is the whole point).
        val history: List<ApplicationExitInfo> = am.getHistoricalProcessExitReasons(
            null, 0, MAX_HISTORICAL,
        )
        if (history.isEmpty()) return

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeenMs = prefs.getLong(KEY_LAST_TIMESTAMP_MS, 0L)

        // History is returned newest-first; we want to process
        // oldest-first so the watermark advances correctly.
        val fresh = history.filter { it.timestamp > lastSeenMs }
            .sortedBy { it.timestamp }
        if (fresh.isEmpty()) return

        // v1.44.121 — phase 1: persist EVERY fresh non-noise exit to
        // disk and advance the watermark, BEFORE any network attempt.
        // This means even if the app dies mid-loop on the next exit
        // (very likely on a kill-every-10-sec device), the payloads
        // survive to be uploaded on a future launch.
        val pendingDir = File(ctx.filesDir, PENDING_DIR).apply { mkdirs() }
        for (info in fresh) {
            if (isNoiseReason(info.reason)) {
                // Still advance the watermark so we don't re-scan.
                prefs.edit().putLong(KEY_LAST_TIMESTAMP_MS, info.timestamp).apply()
                continue
            }
            val payload = buildPayload(ctx, info)
            val pendingFile = File(pendingDir, "${info.timestamp}.json")
            runCatching { pendingFile.writeText(payload) }
                .onFailure { Log.w(TAG, "persist failed: ${it.message}") }
            // Advance watermark IMMEDIATELY — regardless of whether
            // the write succeeded. If the write threw the next launch
            // will re-fetch this exit from the OS history (since we
            // didn't actually consume it) — wait, no, watermark
            // advanced so we'd skip it. Fine: write failures are
            // exceedingly rare and a missed exit is acceptable.
            prefs.edit().putLong(KEY_LAST_TIMESTAMP_MS, info.timestamp).apply()
        }
        // v1.44.121 — phase 2: try to upload everything on disk now.
        // If this hangs / fails / the process dies, the next launch
        // picks up from where we left off.
        uploadPending(ctx)
    }

    /**
     * Drain pending exit-info payloads from the on-disk queue
     * (filesDir/exit_info_pending) to the crash endpoint.
     * Files that upload successfully are deleted. Files that fail to
     * upload stay on disk and get retried on the next call.
     *
     * Hard cap of MAX_PENDING_FILES to bound disk usage if the server
     * is sustainedly unreachable — oldest files are dropped first.
     */
    private fun uploadPending(ctx: Context) {
        val pendingDir = File(ctx.filesDir, PENDING_DIR)
        if (!pendingDir.isDirectory) return
        val files = pendingDir.listFiles()?.sortedBy { it.name } ?: return
        if (files.isEmpty()) return

        // Trim if we accumulated too many — keep the most recent ones.
        if (files.size > MAX_PENDING_FILES) {
            files.dropLast(MAX_PENDING_FILES).forEach {
                runCatching { it.delete() }
            }
        }
        // Upload oldest-first.
        for (f in files.takeLast(MAX_PENDING_FILES)) {
            val body = runCatching { f.readText() }.getOrNull() ?: continue
            if (body.isBlank()) {
                runCatching { f.delete() }
                continue
            }
            if (postJson(ENDPOINT, body)) {
                runCatching { f.delete() }
            } else {
                // Stop on first failure — preserve order for next try.
                break
            }
        }
    }

    /** Filter out exits that aren't useful for diagnosing crashes. */
    private fun isNoiseReason(reason: Int): Boolean = when (reason) {
        // User said "force stop" / "kill app" or swiped from recents.
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        // Normal lifecycle shutdowns.
        ApplicationExitInfo.REASON_EXIT_SELF,
        // Package was updated / signed / replaced — expected.
        ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        // Dependency died — not actionable for us.
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> true
        else -> false
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun buildPayload(ctx: Context, info: ApplicationExitInfo): String {
        val fmtUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val deviceId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                ?.take(8)
        }.getOrNull() ?: "unknown"
        val device = "${Build.MANUFACTURER}-${Build.MODEL}-$deviceId"
            .replace(' ', '-').take(60)

        val capturedAt = fmtUtc.format(Date(info.timestamp))
        val reasonName = reasonName(info.reason)
        val importance = importanceName(info.importance)

        // Build the human-readable trace block. Mirrors the crash.log
        // header format so the existing server-side `_signature()`
        // parser picks up `thread=...`.
        val sb = StringBuilder()
        sb.append("===== HushTV ").append(capturedAt)
            .append(" v").append(BuildConfig.VERSION_NAME)
            .append("#").append(BuildConfig.VERSION_CODE)
            .append(" thread=exit-info =====\n")
        sb.append("EXIT-INFO reason=").append(reasonName).append('\n')
        sb.append("status=").append(info.status)
            .append(" (").append(signalName(info.status)).append(")\n")
        sb.append("importance=").append(importance).append('\n')
        sb.append("pss=").append(info.pss).append(" KB\n")
        sb.append("rss=").append(info.rss).append(" KB\n")
        sb.append("description=").append(info.description ?: "(none)").append('\n')
        sb.append("processName=").append(info.processName).append('\n')
        sb.append("pid=").append(info.pid).append('\n')

        // Device classification — helps the admin filter for the
        // 1 GB Fire Stick fleet.
        runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            sb.append("memory_class_mb=").append(am.memoryClass).append('\n')
            sb.append("is_low_ram_device=").append(am.isLowRamDevice).append('\n')
            sb.append("avail_mem_mb=").append(mi.availMem / (1024 * 1024)).append('\n')
            sb.append("low_mem_threshold_mb=").append(mi.threshold / (1024 * 1024)).append('\n')
            sb.append("total_mem_mb=").append(mi.totalMem / (1024 * 1024)).append('\n')
            sb.append("low_memory_flag=").append(mi.lowMemory).append('\n')
        }

        // For native crashes / ANRs the OS attaches a tombstone or
        // main-thread dump in `traceInputStream`. Read it (cap at
        // 64 KB to keep the JSON sane).
        val tombstone: String = runCatching {
            info.traceInputStream?.use { stream ->
                val bytes = stream.readBytes()
                val text = String(bytes, Charsets.UTF_8)
                if (text.length > 64 * 1024) text.takeLast(64 * 1024) else text
            } ?: ""
        }.getOrDefault("")
        if (tombstone.isNotBlank()) {
            sb.append("\n─── Tombstone / trace ───\n")
            sb.append(tombstone).append('\n')
        }

        return buildJson {
            put("device", device)
            put("android_sdk", Build.VERSION.SDK_INT.toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("version_code", BuildConfig.VERSION_CODE.toString())
            put("captured_at", capturedAt)
            put("installed_version", BuildConfig.VERSION_NAME)
            put("kind", "exit_info")
            put("exit_reason", reasonName)
            put("exit_status", info.status.toString())
            put("exit_pss_kb", info.pss.toString())
            put("exit_rss_kb", info.rss.toString())
            put("exit_importance", importance)
            // v1.44.118 identifiers so the admin Crashes tab can
            // filter exit-info reports by Xtream username.
            runCatching {
                val playlists = PlaylistStore.getAll(ctx)
                val pl = playlists.firstOrNull()
                if (pl != null) {
                    put("xtream_username", pl.username)
                    put("xtream_host", pl.host)
                    put("playlist_id", pl.id)
                }
                put("playlist_count", playlists.size.toString())
            }
            put("trace", sb.toString())
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }

    private fun importanceName(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
        else -> "IMPORTANCE_$importance"
    }

    /** Translate the most common POSIX signals that show up in
     *  `ApplicationExitInfo.status` when reason == SIGNALED or
     *  CRASH_NATIVE so the admin doesn't have to grep `signal.h`. */
    private fun signalName(sig: Int): String = when (sig) {
        1 -> "SIGHUP"
        2 -> "SIGINT"
        3 -> "SIGQUIT"
        4 -> "SIGILL"
        6 -> "SIGABRT"
        7 -> "SIGBUS"
        8 -> "SIGFPE"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        13 -> "SIGPIPE"
        15 -> "SIGTERM"
        24 -> "SIGXCPU"
        25 -> "SIGXFSZ"
        else -> "signal-$sig"
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
                setRequestProperty("User-Agent", "HushTV/${BuildConfig.VERSION_NAME}")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.i(TAG, "exit-info upload → HTTP $code")
            code == 200
        } catch (e: Exception) {
            Log.w(TAG, "exit-info upload failed: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    // Tiny hand-rolled JSON builder — same pattern as CrashReporter.
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

    private inline fun buildJson(body: JsonBuilder.() -> Unit): String =
        JsonBuilder().apply(body).toString()
}
