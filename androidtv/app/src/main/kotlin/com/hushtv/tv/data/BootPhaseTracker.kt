package com.hushtv.tv.data

import android.content.Context
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
 * Per-launch boot phase tracker. Closes the diagnostic blind spot that
 * `ApplicationExitInfo` leaves on Fire OS 7 / Android 9 (API 28) —
 * which is exactly the AFTSS 1 GB Fire Stick fleet that crashes
 * silently with no Java exception, no native tombstone, and no
 * ApplicationExitInfo entry (because the API doesn't exist).
 *
 * Strategy
 * --------
 * Each boot phase ([mark]) writes a tiny "STAGE=X at T" line to
 * `filesDir/boot_phase.log`. On the NEXT launch [collect] reads the
 * file BACK and if the previous run never reached the [REACHED_ALIVE]
 * phase, we know the app died in startup AND we know exactly which
 * stage was the last one reached. The file is then truncated for the
 * new launch's phases.
 *
 * The boot log is shipped to the crash dashboard with `kind=boot_died`
 * so the admin Crashes tab gets a new row whenever a previous launch
 * didn't make it to "alive". Each report carries the same memory
 * snapshot + Xtream username as a regular crash so the admin can
 * filter by user.
 *
 * Cost
 * ----
 *  - 1 sync `appendText` per phase mark. Each phase is ~80 bytes.
 *    Total for a healthy launch: ~10 lines × 80 b = 800 b. Survives
 *    process death because it's flushed-on-append on Linux ext4.
 *  - One background HTTP POST on next launch (only if the previous
 *    run died) — same executor that handles `CrashReporter`.
 *
 * Phases (caller-defined; canonical list)
 * ---------------------------------------
 *  - "app_oncreate_start"     — first line of HushTVApp.onCreate
 *  - "app_oncreate_anr"       — after AnrWatchdog.start
 *  - "app_oncreate_end"       — final line of HushTVApp.onCreate
 *  - "main_oncreate_start"    — first line of MainActivity.onCreate
 *  - "main_setContent"        — about to call setContent
 *  - "main_first_compose"     — Compose first composition observed
 *  - "ui_first_frame"         — first frame drawn
 *  - "ui_alive_5s"            — app survived 5 seconds (= [REACHED_ALIVE])
 *
 * If we see a report whose `last_stage` is "main_setContent" we know
 * the issue is between setContent() and Compose first composition —
 * very narrow root cause. That's a step-change in actionable
 * diagnostic for the 1 GB AFTSS fleet.
 *
 * v1.44.121 — initial release.
 */
object BootPhaseTracker {
    private const val TAG = "BootPhaseTracker"
    private const val FILE_PREV = "boot_phase_prev.log"
    private const val FILE_CURR = "boot_phase.log"
    private const val ENDPOINT =
        "https://hushtv.xyz/crash/submit/GbExkT_0wVwqMbw5mwOrRMbe1pS3PghK"

    /** Phase that means "the app is fully alive and stable" — if we
     *  reach this, we don't bother reporting the boot trace because
     *  it was a healthy boot. */
    const val REACHED_ALIVE = "ui_alive_5s"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hushtv-boot-phase").apply { isDaemon = true }
    }

    @Volatile private var reachedAlive: Boolean = false

    /** Write a phase marker. Synchronous append — fast (<1 ms) and
     *  flushed by the kernel so it survives process death.
     *
     *  Safe to call from any thread, any time. Best-effort: never
     *  throws, never blocks UI. */
    fun mark(ctx: Context, phase: String) {
        if (reachedAlive) {
            // No need to log further phases once we know the launch
            // succeeded — saves disk I/O for the rest of the session.
            return
        }
        runCatching {
            val nowMs = System.currentTimeMillis()
            val f = File(ctx.filesDir, FILE_CURR)
            // Append (creates file if missing). 1-line format:
            //   <epoch_ms>|<phase>
            f.appendText("$nowMs|$phase\n")
            if (phase == REACHED_ALIVE) {
                reachedAlive = true
            }
        }.onFailure { Log.w(TAG, "mark($phase) failed: ${it.message}") }
    }

    /**
     * Snapshot the previous launch's boot trace, rotate the file, and
     * (if the previous launch didn't reach [REACHED_ALIVE]) upload it
     * as a `kind=boot_died` report.
     *
     * Call once early in [HushTVApp.onCreate] — BEFORE [mark] for the
     * current launch. Order matters because [mark] appends to the same
     * file that this method rotates.
     */
    fun collectAndRotate(ctx: Context) {
        executor.execute {
            runCatching { collectInner(ctx) }
                .onFailure { Log.w(TAG, "collect failed: ${it.message}") }
        }
    }

    private fun collectInner(ctx: Context) {
        val curr = File(ctx.filesDir, FILE_CURR)
        val prev = File(ctx.filesDir, FILE_PREV)

        // The PREV file is what the LAST launch produced. Read it,
        // upload-if-bad, then overwrite with this launch's fresh log.
        // CURR was rotated INTO prev at the END of the previous
        // launch in `rotateOnAlive`. If CURR exists and PREV doesn't,
        // it means the previous launch died BEFORE rotateOnAlive ran.
        // Either way: PREV is the file to inspect.
        //
        // Defensive: also inspect CURR — if PREV is missing but CURR
        // exists, treat CURR as "previous" (the previous launch
        // crashed before it could even rotate).
        val sourceForUpload: File? = when {
            prev.exists() && prev.length() > 0 -> prev
            curr.exists() && curr.length() > 0 -> curr
            else -> null
        }
        if (sourceForUpload != null) {
            val text = runCatching { sourceForUpload.readText() }
                .getOrDefault("")
            if (text.isNotBlank()) {
                val lines = text.lines().filter { it.isNotBlank() }
                val lastStage = lines.lastOrNull()
                    ?.split('|')?.getOrNull(1)?.trim().orEmpty()
                if (lastStage.isNotBlank() && lastStage != REACHED_ALIVE) {
                    // Previous launch DIED before reaching alive.
                    // Build + ship the report.
                    val payload = buildPayload(ctx, text, lastStage)
                    postJson(ENDPOINT, payload)
                }
            }
        }
        // Reset for this launch. We move CURR→PREV inside
        // `rotateOnAlive()` once the launch is healthy; here we just
        // truncate so the new launch's marks start fresh.
        runCatching {
            if (prev.exists()) prev.delete()
            if (curr.exists()) curr.delete()
        }
    }

    /**
     * Call when the launch reaches a stable state (= after
     * [REACHED_ALIVE] has been marked). Moves the current log to
     * `boot_phase_prev.log` so the NEXT launch's [collectAndRotate]
     * has the right source file to inspect.
     */
    fun rotateOnAlive(ctx: Context) {
        executor.execute {
            runCatching {
                val curr = File(ctx.filesDir, FILE_CURR)
                val prev = File(ctx.filesDir, FILE_PREV)
                if (curr.exists()) {
                    if (prev.exists()) prev.delete()
                    curr.renameTo(prev)
                }
            }.onFailure { Log.w(TAG, "rotateOnAlive failed: ${it.message}") }
        }
    }

    private fun buildPayload(ctx: Context, traceText: String, lastStage: String): String {
        val fmtUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val deviceId = runCatching {
            android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )?.take(8)
        }.getOrNull() ?: "unknown"
        val device = "${android.os.Build.MANUFACTURER}-${android.os.Build.MODEL}-$deviceId"
            .replace(' ', '-').take(60)
        val capturedAt = fmtUtc.format(Date())

        val sb = StringBuilder()
        sb.append("===== HushTV ").append(capturedAt)
            .append(" v").append(BuildConfig.VERSION_NAME)
            .append("#").append(BuildConfig.VERSION_CODE)
            .append(" thread=boot-died =====\n")
        sb.append("BOOT-DIED last_stage=").append(lastStage).append('\n')
        sb.append("device_class_low_ram=").append(DeviceClass.isLowRam(ctx)).append('\n')
        // Memory snapshot for cross-correlation with exit-info /
        // crash reports.
        runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            sb.append("memory_class_mb=").append(am.memoryClass).append('\n')
            sb.append("is_low_ram_device=").append(am.isLowRamDevice).append('\n')
            sb.append("avail_mem_mb=").append(mi.availMem / (1024 * 1024)).append('\n')
            sb.append("total_mem_mb=").append(mi.totalMem / (1024 * 1024)).append('\n')
            sb.append("low_mem_threshold_mb=").append(mi.threshold / (1024 * 1024)).append('\n')
            sb.append("low_memory_flag=").append(mi.lowMemory).append('\n')
        }
        sb.append("\n─── Boot phase trace ───\n")
        sb.append(traceText)

        return buildJson {
            put("device", device)
            put("android_sdk", android.os.Build.VERSION.SDK_INT.toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("version_code", BuildConfig.VERSION_CODE.toString())
            put("captured_at", capturedAt)
            put("installed_version", BuildConfig.VERSION_NAME)
            put("kind", "boot_died")
            put("last_stage", lastStage)
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
            Log.i(TAG, "boot-died upload → HTTP $code")
            code == 200
        } catch (e: Exception) {
            Log.w(TAG, "boot-died upload failed: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

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
