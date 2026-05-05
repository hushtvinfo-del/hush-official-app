package com.hushtv.tv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection

/**
 * Hybrid override mechanism layered on top of [BundledAssets].
 *
 * `BundledAssets` is generated at build time (see /tmp/build_bundle.py)
 * and ships ~3 MB of WebP-encoded posters/backdrops/logos inside
 * the APK. The bundle is *static* — when a streaming service
 * rebrands, we'd normally need an app update.
 *
 * `BundleOverrides` adds a server-side hot-patch path:
 *   GET https://hushtv.xyz/bundle_overrides.json
 *
 * The endpoint returns a flat `Map<String, String>` of
 * `<source-url>` → `<replacement-url>`. Examples:
 *
 * ```json
 * {
 *   "https://image.tmdb.org/t/p/original/oldlogo.jpg":
 *     "https://hushtv.xyz/bundle/netflix-2026.webp",
 *
 *   "https://imageT.tmdb.org/t/p/original/wronglogo.jpg":
 *     "https://image.tmdb.org/t/p/original/correctlogo.jpg"
 * }
 * ```
 *
 * The server can target either:
 *   • a *bundled* URL (replacing the in-APK asset for everyone)
 *   • a never-bundled URL (e.g. fixing a typo in someone's data
 *     file)
 *
 * The override map is cached in SharedPreferences and refreshed
 * on every app start (foreground only — not on each sync tick to
 * avoid unnecessary requests).
 *
 * Resolution order in [resolve]:
 *   1. Server override → use the override URL (network or local).
 *   2. Bundled asset →  `file:///android_asset/...` (instant).
 *   3. Fall through →   the original URL (existing behaviour).
 *
 * Resolution is exposed via [resolve]; the Coil
 * [com.hushtv.tv.HushTVApp.newImageLoader] mapper consults it on
 * every image load.
 */
object BundleOverrides {

    private const val PREFS = "hushtv_bundle_overrides"
    private const val KEY_BLOB = "blob_v1"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val OVERRIDES_URL = "https://hushtv.xyz/bundle_overrides.json"
    /** Refresh at most once every 6 h — overrides are not
     *  time-critical and we don't want to hammer the endpoint. */
    private const val REFRESH_MS = 6L * 60L * 60L * 1000L

    @Volatile
    private var cache: Map<String, String> = emptyMap()
    private var refreshJob: Job? = null

    /** Public resolution. Returns a URL if any layer wants to
     *  redirect [sourceUrl]; null means "use as-is".
     *
     *  Pre-conditions: [load] must have been called at least once.
     *  Returns null safely when the override map is empty. */
    fun resolve(sourceUrl: String): String? {
        cache[sourceUrl]?.let { return it }
        return BundledAssets.resolve(sourceUrl)
    }

    /** Load the cached override map from disk. Cheap — runs on
     *  the main thread during app boot.  */
    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_BLOB, null) ?: return
        cache = parseBlob(raw)
    }

    /** Kick off a background refresh of the override blob. Safe
     *  to call repeatedly — debounces against [REFRESH_MS]. */
    fun startRefresh(ctx: Context, scope: CoroutineScope) {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            // Initial settle + refresh-throttle check.
            delay(2_000)
            while (isActive) {
                runCatching { refreshOnce(ctx) }
                delay(REFRESH_MS)
            }
        }
    }

    private fun refreshOnce(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastFetched = sp.getLong(KEY_FETCHED_AT, 0L)
        if (now - lastFetched < REFRESH_MS / 2) return
        val text = httpGet(OVERRIDES_URL) ?: return
        val map = parseBlob(text)
        cache = map
        sp.edit()
            .putString(KEY_BLOB, text)
            .putLong(KEY_FETCHED_AT, now)
            .apply()
    }

    private fun parseBlob(text: String): Map<String, String> = runCatching {
        val obj = JSONObject(text)
        val out = HashMap<String, String>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optString(k, "")
            if (v.isNotBlank()) out[k] = v
        }
        out as Map<String, String>
    }.getOrDefault(emptyMap())

    private fun httpGet(url: String): String? = runCatching {
        val conn = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
