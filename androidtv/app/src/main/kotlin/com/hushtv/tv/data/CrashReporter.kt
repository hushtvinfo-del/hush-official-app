package com.hushtv.tv.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.hushtv.tv.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Ships the contents of `filesDir/crash.log` to the HushTV crash-report
 * server so we can analyse TV crashes without asking the user to copy
 * stack traces manually off a Shield / Fire TV.
 *
 * Flow:
 *   1. `HushTVApp.onCreate()` schedules [uploadIfPending] on a single
 *      background thread — runs once per launch.
 *   2. If `crash.log` exists AND its mtime differs from the last
 *      successfully-uploaded mtime (stored in prefs), we POST it.
 *   3. On HTTP 200, record the mtime. Next launch will skip unless a
 *      fresh crash landed.
 *
 * The Diagnostics screen also offers a manual "Send now" button that
 * calls [uploadNow] — useful if the user wants to force a retry.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val ENDPOINT =
        "https://hushtv.xyz/crash/submit/GbExkT_0wVwqMbw5mwOrRMbe1pS3PghK"

    private const val PREFS = "crash_reporter"
    private const val KEY_LAST_UPLOADED_MTIME = "last_uploaded_mtime"
    private const val KEY_LAST_UPLOADED_ANR_MTIME = "last_uploaded_anr_mtime"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hushtv-crash-upload").apply { isDaemon = true }
    }

    /** Kicks off a one-shot upload if the crash log has new content
     *  since the last successful upload. Safe to call on every app
     *  launch — no-ops if nothing's pending. */
    fun uploadIfPending(ctx: Context) {
        executor.execute {
            runCatching { maybeUpload(ctx, force = false) }
                .onFailure { Log.w(TAG, "upload skipped: ${it.message}") }
        }
    }

    /** Forced upload triggered by the Diagnostics screen.
     *  [callback] receives one of:
     *    • "sent"       — upload succeeded
     *    • "nothing"    — crash log is empty / already sent
     *    • "failed"     — network / server error
     */
    fun uploadNow(ctx: Context, callback: (String) -> Unit) {
        executor.execute {
            val result = runCatching {
                val f = File(ctx.filesDir, "crash.log")
                if (!f.exists() || f.length() == 0L) return@runCatching "nothing"
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                // Also respect dedup on manual uploads — stops users
                // re-sending the same stale trace over and over and
                // tagging each one with a wrong "upload-time"
                // app_version. If they hit Send but nothing's new we
                // report "nothing" instead of "sent".
                if (f.lastModified() == prefs.getLong(KEY_LAST_UPLOADED_MTIME, 0L)) {
                    return@runCatching "nothing"
                }
                if (maybeUpload(ctx, force = true)) "sent" else "failed"
            }.getOrDefault("failed")
            callback(result)
        }
    }

    private fun maybeUpload(ctx: Context, force: Boolean): Boolean {
        // Two log files to ship: the JVM uncaught-exception log
        // (crash.log) and the ANR watchdog log (anr.log). Each is
        // tracked separately via its own mtime so we can ship one
        // without re-shipping the other.
        var anyUploaded = false
        for ((name, mtimeKey) in arrayOf(
            "crash.log" to KEY_LAST_UPLOADED_MTIME,
            "anr.log"   to KEY_LAST_UPLOADED_ANR_MTIME,
        )) {
            val f = File(ctx.filesDir, name)
            if (!f.exists() || f.length() == 0L) continue
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastMtime = prefs.getLong(mtimeKey, 0L)
            if (!force && f.lastModified() == lastMtime) continue
            val body = buildPayload(ctx, f.readText())
            if (postJson(ENDPOINT, body)) {
                prefs.edit().putLong(mtimeKey, f.lastModified()).apply()
                anyUploaded = true
            }
        }
        return anyUploaded
    }

    private fun buildPayload(ctx: Context, trace: String): String {
        val fmtUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val deviceId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                ?.take(8)
        }.getOrNull() ?: "unknown"
        val device = "${Build.MANUFACTURER}-${Build.MODEL}-$deviceId"
            .replace(' ', '-').take(60)
        val safeTrace = if (trace.length > 64 * 1024) trace.takeLast(64 * 1024) else trace

        // Parse the machine-readable crash header that
        // HushTVApp.installCrashHandler writes:
        //   ===== HushTV <iso> v<name>#<code> thread=<name> =====
        // When present, use it to report the ACTUAL time + APK version
        // at crash, not upload-time. Stops stale logs from one release
        // getting reported under a later release's version tag.
        val headerRe = Regex(
            """=====\s+HushTV\s+(\S+)\s+v(\S+?)#(\d+)\s+thread=(\S+?)\s+====="""
        )
        val match = headerRe.find(safeTrace)
        val capturedAt: String
        val appVersion: String
        val versionCode: String
        if (match != null) {
            capturedAt = match.groupValues[1]
            appVersion = match.groupValues[2]
            versionCode = match.groupValues[3]
        } else {
            // Fall back to now + current BuildConfig for logs that
            // predate v1.30.7 (no machine header).
            capturedAt = fmtUtc.format(Date())
            appVersion = BuildConfig.VERSION_NAME
            versionCode = BuildConfig.VERSION_CODE.toString()
        }

        return buildJson {
            put("device", device)
            put("android_sdk", Build.VERSION.SDK_INT.toString())
            put("app_version", appVersion)
            put("version_code", versionCode)
            put("captured_at", capturedAt)
            // Keep a marker so the dashboard can tell new-format logs
            // apart from old-format ones at a glance.
            put("installed_version", BuildConfig.VERSION_NAME)
            put("trace", safeTrace)
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
            Log.i(TAG, "crash upload → HTTP $code")
            code == 200
        } catch (e: IOException) {
            Log.w(TAG, "crash upload failed: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    // Tiny hand-rolled JSON builder — avoids pulling Gson just for
    // this one call. Escapes the trace string per RFC 8259.
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
