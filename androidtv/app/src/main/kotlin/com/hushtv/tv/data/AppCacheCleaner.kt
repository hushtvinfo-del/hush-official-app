package com.hushtv.tv.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * v1.44.127 — One-touch app-cache clear, mimicking what Android's
 * Settings → Apps → HushTV → Clear Cache button does, but invoked
 * automatically on app launch.
 *
 * Background
 * ----------
 * Several support reports said "clearing cache fixed it" — usually
 * for cases where a corrupted Coil bitmap was rendering wrong, a
 * stale Xtream JSON response was breaking the channel list, or a
 * partial download had left junk behind. Asking every user to
 * navigate to Fire OS Settings → Apps → HushTV → Clear Cache is a
 * non-starter on TV remotes. This automates it.
 *
 * What gets cleared (mirrors what the OS Clear-Cache button does)
 *   • `cacheDir/image_cache/`        — Coil bitmap disk cache
 *   • `cacheDir/xtream_http_cache/`  — OkHttp Xtream JSON cache (20 MB)
 *   • `cacheDir/opensubtitles/`      — downloaded subtitle files
 *   • `cacheDir/home_rails_v1.json`  — cached home-screen rail layout
 *   • externalCacheDir contents      — anything in external cache
 *
 * What we DO NOT touch (these are "data", not "cache")
 *   • filesDir contents              — license, playlists, prefs, sync state
 *   • SharedPreferences               — app_mode_prefs, exit_info_collector, etc.
 *   • Databases                       — Room / SQLite
 *   • Any in-progress OTA APK         — left untouched in cacheDir root
 *
 * Throttle
 * --------
 * Runs at-most-once per [THROTTLE_MS] (6 hours). Fire Stick users
 * troubleshooting often kill+relaunch the app 3-4 times in a row;
 * we don't want those rapid restarts to all blow the disk cache and
 * trigger expensive re-downloads on slow connections.
 *
 * Threading
 * ---------
 * Pure file-system operations (no network, no DB). Safe to call
 * from a background thread. Currently invoked from HushTVApp's
 * deferred-init HandlerThread BEFORE [XtreamApi.enableDiskCache]
 * so OkHttp doesn't have the cache journal open when we delete.
 */
object AppCacheCleaner {
    private const val TAG = "AppCacheCleaner"
    private const val PREFS = "app_cache_cleaner"
    private const val KEY_LAST_CLEAR_MS = "last_clear_ms"
    private const val THROTTLE_MS = 6L * 60 * 60 * 1000  // 6 hours

    /**
     * Directories under `cacheDir/` we know we own. Anything else
     * in cacheDir (OTA APK in progress, OS-managed temp files,
     * future libraries) is left alone.
     */
    private val OWNED_DIRS = listOf(
        "image_cache",
        "xtream_http_cache",
        "opensubtitles",
    )

    /**
     * Single files (not directories) under `cacheDir/` we know we
     * own and want to delete on cache-clear.
     */
    private val OWNED_FILES = listOf(
        "home_rails_v1.json",
    )

    /** Clear cache if it's been more than 6 hours since the last
     *  clear (or if we've never cleared). Returns `true` if a clear
     *  actually ran, `false` if throttled or failed. */
    fun clearIfDue(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_CLEAR_MS, 0L)
        if (last > 0 && (now - last) < THROTTLE_MS) {
            Log.i(TAG, "throttled — last clear was ${(now - last) / 1000}s ago")
            return false
        }
        // Mark the gate FIRST so even if the clear crashes mid-way,
        // we don't retry indefinitely on rapid restarts.
        prefs.edit().putLong(KEY_LAST_CLEAR_MS, now).apply()
        return runCatching { clearNow(ctx) }
            .onFailure { Log.w(TAG, "clear failed: ${it.message}", it) }
            .getOrDefault(false)
    }

    /** Force a clear right now, bypassing the throttle. Used by the
     *  Settings → "Clear Cache" menu item (if/when we surface it
     *  manually). Returns `true` on success. */
    fun forceClearNow(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CLEAR_MS, now).apply()
        return runCatching { clearNow(ctx) }
            .onFailure { Log.w(TAG, "force-clear failed: ${it.message}", it) }
            .getOrDefault(false)
    }

    private fun clearNow(ctx: Context): Boolean {
        val cacheDir = ctx.cacheDir ?: return false
        var freedBytes = 0L
        var deletedItems = 0

        for (dirName in OWNED_DIRS) {
            val dir = File(cacheDir, dirName)
            if (dir.isDirectory) {
                val (bytes, count) = deleteRecursiveContents(dir)
                freedBytes += bytes
                deletedItems += count
            }
        }
        for (fileName in OWNED_FILES) {
            val f = File(cacheDir, fileName)
            if (f.isFile) {
                val sz = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) {
                    freedBytes += sz
                    deletedItems += 1
                }
            }
        }
        // External cache is also fair game — same OS-eviction semantics.
        runCatching {
            val ext = ctx.externalCacheDir
            if (ext != null && ext.isDirectory) {
                val (bytes, count) = deleteRecursiveContents(ext)
                freedBytes += bytes
                deletedItems += count
            }
        }
        val mb = freedBytes / (1024.0 * 1024.0)
        Log.i(
            TAG,
            "cleared ${"%.2f".format(mb)} MB across $deletedItems items",
        )
        return true
    }

    /** Recursively delete every file UNDER `root` but keep `root`
     *  itself. Mirrors what OS clear-cache does (deletes contents,
     *  not the directory itself). Returns (bytes_freed, items_deleted). */
    private fun deleteRecursiveContents(root: File): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        // Walk bottom-up so directories are emptied before we delete
        // them.
        val children = root.listFiles() ?: return 0L to 0
        for (child in children) {
            if (child.isDirectory) {
                val (b, c) = deleteRecursiveContents(child)
                bytes += b
                count += c
                runCatching { child.delete() }  // empty dir
            } else if (child.isFile) {
                val sz = child.length()
                if (runCatching { child.delete() }.getOrDefault(false)) {
                    bytes += sz
                    count += 1
                }
            }
        }
        return bytes to count
    }
}
