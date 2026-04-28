package com.hushtv.tv

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches every uncaught exception in the JVM, dumps a full stack
 * trace to `/sdcard/Android/data/com.hushtv.tv/files/crash.log`, then
 * lets the platform default handler kill the process so users still
 * see the "stopped working" dialog and the OS records the crash.
 *
 * The dump file persists across launches and is intentionally
 * publicly readable — a HushTV reseller or the user can grab it via
 * adb pull or just connect the box's storage to a PC and copy it.
 *
 * No PII is captured: only the device model, OS version, app
 * versionCode/Name, the active thread name, and the exception
 * itself.
 *
 * Wire-up: call [install] exactly once from MainActivity.onCreate.
 */
object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 256 * 1024L  // ~256 KB cap — rolls over on overflow

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { dump(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun dump(ctx: Context, thread: Thread, throwable: Throwable) {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val file = File(dir, FILE_NAME)
        // Roll over once we go past MAX_BYTES so the log doesn't
        // accumulate forever.
        if (file.exists() && file.length() > MAX_BYTES) {
            runCatching { file.delete() }
        }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val pkgInfo = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }.getOrNull()
        val versionName = pkgInfo?.versionName ?: "unknown"
        val versionCode = pkgInfo?.let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 0L
        val sb = StringBuilder()
        sb.append("\n===== HushTV crash @ $ts =====\n")
        sb.append("App      : $versionName ($versionCode)\n")
        sb.append("Device   : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android  : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Thread   : ${thread.name}\n")
        sb.append("Cause    : ${throwable.javaClass.name}: ${throwable.message}\n")
        sb.append("Stack    :\n")
        for (frame in throwable.stackTrace) sb.append("  at $frame\n")
        var cause: Throwable? = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            sb.append("Caused by: ${cause.javaClass.name}: ${cause.message}\n")
            for (frame in cause.stackTrace) sb.append("  at $frame\n")
            cause = cause.cause
            depth++
        }
        runCatching {
            file.appendText(sb.toString())
            Log.e(TAG, "Crash dumped to ${file.absolutePath}")
        }
    }
}
