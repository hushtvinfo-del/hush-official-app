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
     *  [callback] receives `true` on HTTP 200, `false` otherwise. */
    fun uploadNow(ctx: Context, callback: (Boolean) -> Unit) {
        executor.execute {
            val ok = runCatching { maybeUpload(ctx, force = true) }
                .getOrDefault(false)
            callback(ok)
        }
    }

    private fun maybeUpload(ctx: Context, force: Boolean): Boolean {
        val f = File(ctx.filesDir, "crash.log")
        if (!f.exists() || f.length() == 0L) return false

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastMtime = prefs.getLong(KEY_LAST_UPLOADED_MTIME, 0L)
        if (!force && f.lastModified() == lastMtime) return false

        val body = buildPayload(ctx, f.readText())
        val success = postJson(ENDPOINT, body)
        if (success) {
            prefs.edit()
                .putLong(KEY_LAST_UPLOADED_MTIME, f.lastModified())
                .apply()
        }
        return success
    }

    private fun buildPayload(ctx: Context, trace: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val deviceId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                ?.take(8)
        }.getOrNull() ?: "unknown"
        val device = "${Build.MANUFACTURER}-${Build.MODEL}-$deviceId"
            .replace(' ', '-').take(60)
        // Trim trace — we cap at 64 KB to stay well under the
        // server's 512 KB body limit.
        val safeTrace = if (trace.length > 64 * 1024) trace.takeLast(64 * 1024) else trace

        return buildJson {
            put("device", device)
            put("android_sdk", Build.VERSION.SDK_INT.toString())
            put("app_version", BuildConfig.VERSION_NAME)
            put("version_code", BuildConfig.VERSION_CODE.toString())
            put("captured_at", fmt.format(Date()))
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
