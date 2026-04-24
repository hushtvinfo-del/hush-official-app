package com.hushtv.tv.data

import android.content.Context
import java.io.File

/**
 * Thin helper around `filesDir/crash.log` — the file that the global
 * UncaughtExceptionHandler in [com.hushtv.tv.HushTVApp] writes to on
 * every uncaught exception. Exposed so both the mobile Diagnostics
 * screen and the TV Diagnostics screen can read / share / clear the
 * same file.
 */
object CrashLogStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "crash.log")

    /** Full text of the log. Empty string if the file doesn't exist. */
    fun read(ctx: Context): String {
        val f = file(ctx)
        if (!f.exists()) return ""
        return runCatching { f.readText() }.getOrDefault("")
    }

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    fun hasContent(ctx: Context): Boolean {
        val f = file(ctx)
        return f.exists() && f.length() > 0
    }
}
