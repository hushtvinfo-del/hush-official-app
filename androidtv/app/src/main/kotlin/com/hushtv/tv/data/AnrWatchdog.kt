package com.hushtv.tv.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Detects Application Not Responding (ANR) events on the main thread
 * and writes a stack-trace + breadcrumb log to
 * `filesDir/anr.log` so [CrashReporter] can ship it on next launch.
 *
 * Why this exists (v1.44.3):
 * Standard `Thread.setDefaultUncaughtExceptionHandler` only catches
 * Java exceptions. ANRs are detected by the OS watchdog and result in
 * `SIGKILL` of the process — bypassing every JVM handler. So when a
 * Compose composable does heavy work on the main thread (or a
 * cross-thread deadlock occurs), the user sees "freeze → app shut
 * down" and ZERO crash data lands on the server. v1.44.0–1.44.2
 * shipped multiple ANR-class bugs that we couldn't diagnose from
 * server logs because of exactly this gap.
 *
 * How it works:
 * - A daemon thread posts a no-op runnable to the main looper every
 *   `tickMs` (default 1 s) and waits up to `timeoutMs` (default 4 s)
 *   for the main looper to drain it.
 * - If the runnable doesn't fire in time, the watchdog captures the
 *   main thread's stack trace, formatted breadcrumbs, and writes
 *   them to `anr.log` in the same machine-readable format
 *   `installCrashHandler` uses.
 * - Subsequent ANR detections within `cooldownMs` are skipped so a
 *   chronic hang doesn't fill the disk with duplicate dumps.
 *
 * The watchdog itself NEVER throws — every body is wrapped in
 * `runCatching` so it can't be the cause of a fresh issue.
 */
object AnrWatchdog {
    private const val TAG = "HushTVAnr"

    @Volatile private var started = false
    @Volatile private var lastReportMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(
        ctx: Context,
        tickMs: Long = 1_000,
        timeoutMs: Long = 4_000,
        cooldownMs: Long = 30_000,
    ) {
        if (started) return
        started = true
        val appCtx = ctx.applicationContext
        Thread({
            runCatching {
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(tickMs)
                    val responded = pingMainThread(timeoutMs)
                    if (!responded) {
                        val now = System.currentTimeMillis()
                        if (now - lastReportMs > cooldownMs) {
                            lastReportMs = now
                            captureAnr(appCtx, timeoutMs)
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "watchdog terminated: ${it.message}") }
        }, "hushtv-anr-watchdog").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "watchdog started (tick=${tickMs}ms timeout=${timeoutMs}ms)")
    }

    /** Returns true if the main thread drained a runnable within
     *  [timeoutMs]; false otherwise. */
    private fun pingMainThread(timeoutMs: Long): Boolean {
        val sync = Object()
        var fired = false
        mainHandler.post {
            synchronized(sync) {
                fired = true
                sync.notifyAll()
            }
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(sync) {
            while (!fired) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                runCatching { sync.wait(remaining) }
            }
        }
        return true
    }

    private fun captureAnr(ctx: Context, timeoutMs: Long) {
        runCatching {
            val mainStack = Looper.getMainLooper().thread.stackTrace
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
            val versionName = runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
            }.getOrDefault("?")
            val versionCode = runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toString()
            }.getOrDefault("?")
            val sb = StringBuilder()
            sb.append("===== HushTV ").append(iso)
                .append(" v").append(versionName)
                .append("#").append(versionCode)
                .append(" thread=anr-watchdog =====\n")
            sb.append("ANR-REPORT main_thread_unresponsive_for_at_least=").append(timeoutMs).append("ms\n")
            sb.append("\n─── Main thread stack at detection ───\n")
            for (e in mainStack) {
                sb.append("  at ").append(e.toString()).append('\n')
            }
            sb.append("\n─── Recent breadcrumbs ───\n")
            runCatching {
                val snap = EventLog.snapshot()
                if (snap.isNotBlank()) {
                    snap.split('\n').takeLast(30).forEach {
                        sb.append("  ").append(it).append('\n')
                    }
                }
            }
            sb.append('\n')

            val f = File(ctx.filesDir, "anr.log")
            // Cap at 256 KB to avoid filling user disk on chronic ANRs.
            if (f.length() > 256L * 1024) f.delete()
            f.appendText(sb.toString() + "\n")
            Log.w(TAG, "ANR captured (${mainStack.size} frames) → ${f.absolutePath}")
        }.onFailure { Log.w(TAG, "failed to capture ANR: ${it.message}") }
    }
}
