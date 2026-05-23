package com.hushtv.tv.data

import android.content.Context
import android.util.Log
import com.hushtv.tv.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * v1.44.128-dev — Bundled-asset cache for the **DEV flavor only**.
 *
 * Why
 * ───
 * The dev channel is the testing ground for the "bake everything
 * into the APK" experiment. Several support reports said our app
 * felt slower than TiVimate / IPTV Smarters at startup. Their
 * advantage isn't the player — it's that they don't make ~20
 * outbound HTTP calls to fetch static metadata that doesn't change
 * day-to-day (sports schedules, league logos, popular-movie posters).
 *
 * This object provides a single zero-network lookup table backed by
 * JSON files baked into `app/src/dev/assets/dev_only/` at build
 * time by `/app/_buildenv/dev-bundle-fetcher.py`. Canada / official
 * APKs ship without those files; [isActive] returns false and every
 * `get*()` call short-circuits to `null`, letting the existing
 * network code path run unchanged.
 *
 * What's bundled (dev flavor only)
 * ────────────────────────────────
 *   • sports_home.json
 *   • sports_ppv.json
 *   • sports_league_<slug>.json   (nhl, nba, mlb, nfl, ufc, epl,
 *                                  mls, ncaab, ncaaf)
 *   • tmdb_top_movies.json        (100 most-popular movies)
 *   • tmdb_top_tv.json            (100 most-popular tv shows)
 *
 * Total bundle size: ~210 KB. No images bundled in this phase —
 * images are still fetched from TMDB / hushtv.xyz CDN by Coil.
 *
 * Safe for canada / official
 * ──────────────────────────
 * [isActive] returns false unless BuildConfig.UPDATE_CHANNEL == "dev"
 * AND `assets/dev_only/manifest.json` is present. Every public
 * getter does the active check before reading anything, so a
 * canada APK that somehow ships with stale dev assets still
 * behaves identically to the previous build (everything falls
 * through to network).
 */
object DevBundleCache {
    private const val TAG = "DevBundleCache"
    private const val DIR = "dev_only"

    @Volatile
    private var appContext: Context? = null

    /** True only when running the dev flavor AND the bundle is
     *  present on disk. Cached at first lookup. */
    val isActive: Boolean by lazy {
        if (BuildConfig.UPDATE_CHANNEL != "dev") return@lazy false
        val ctx = appContext ?: return@lazy false
        runCatching {
            ctx.assets.open("$DIR/manifest.json").use { /* presence check */ }
            true
        }.getOrDefault(false).also { active ->
            Log.i(TAG, "dev-bundle active=$active (channel=${BuildConfig.UPDATE_CHANNEL})")
        }
    }

    /** Hook called once from HushTVApp.onCreate. Cheap — just stores
     *  the application Context. The actual bundle reads are lazy. */
    fun init(ctx: Context) {
        appContext = ctx.applicationContext
    }

    // ── Asset loading helpers ───────────────────────────────────

    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private fun readAsset(relativePath: String): String? {
        val ctx = appContext ?: return null
        return runCatching {
            ctx.assets.open("$DIR/$relativePath").bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    // ── Sports ──────────────────────────────────────────────────
    //
    // Sports endpoints return raw JSON strings — SportsApi's Moshi
    // adapter parses them. We don't pre-parse here because Moshi
    // KotlinJsonAdapterFactory is already wired in SportsApi and
    // adding a second adapter chain would double the binary.

    /** Returns the bundled sports home JSON (raw string) or null
     *  if dev-bundle isn't active. */
    fun sportsHomeRaw(): String? = if (isActive) {
        readAsset("sports_home.json").also {
            if (it != null) Log.i(TAG, "served sportsHome from bundle (${it.length}B)")
        }
    } else null

    /** Returns the bundled sports ppv JSON (raw string) or null. */
    fun sportsPpvRaw(): String? = if (isActive) {
        readAsset("sports_ppv.json").also {
            if (it != null) Log.i(TAG, "served sportsPpv from bundle (${it.length}B)")
        }
    } else null

    /** Returns the bundled per-league JSON (raw string) or null
     *  when the slug isn't bundled or dev-bundle isn't active. */
    fun sportsLeagueRaw(slug: String): String? = if (isActive) {
        readAsset("sports_league_${slug.lowercase()}.json").also {
            if (it != null) Log.i(TAG, "served sportsLeague($slug) from bundle (${it.length}B)")
        }
    } else null

    // ── TMDB top-popular ────────────────────────────────────────

    private data class TmdbMovieBundle(val items: List<TmdbMovie> = emptyList())
    private data class TmdbTvBundle(val items: List<TmdbTv> = emptyList())

    private val tmdbMovieIndex: Map<Int, TmdbMovie>? by lazy {
        if (!isActive) return@lazy null
        val raw = readAsset("tmdb_top_movies.json") ?: return@lazy null
        runCatching {
            moshi.adapter(TmdbMovieBundle::class.java).fromJson(raw)?.items
                ?.associateBy { it.id }
        }.onFailure {
            Log.w(TAG, "tmdb_top_movies parse failed: ${it.message}")
        }.getOrNull()
    }

    private val tmdbTvIndex: Map<Int, TmdbTv>? by lazy {
        if (!isActive) return@lazy null
        val raw = readAsset("tmdb_top_tv.json") ?: return@lazy null
        runCatching {
            moshi.adapter(TmdbTvBundle::class.java).fromJson(raw)?.items
                ?.associateBy { it.id }
        }.onFailure {
            Log.w(TAG, "tmdb_top_tv parse failed: ${it.message}")
        }.getOrNull()
    }

    /**
     * Returns a bundled TmdbMovie if [tmdbId] is in the top-100
     * popular list AND dev-bundle is active. Notes:
     *   • credits / recommendations / external_ids / videos are
     *     NOT bundled (heavy + cheap to fetch lazily when the
     *     user actually opens the detail screen).
     *   • Caller can detect a bundle hit by `result?.credits == null`
     *     if they need to top-up.
     */
    fun tmdbMovie(tmdbId: Int): TmdbMovie? {
        if (!isActive) return null
        val hit = tmdbMovieIndex?.get(tmdbId) ?: return null
        Log.i(TAG, "tmdbMovie($tmdbId) → bundle hit (${hit.title})")
        return hit
    }

    fun tmdbTv(tmdbId: Int): TmdbTv? {
        if (!isActive) return null
        val hit = tmdbTvIndex?.get(tmdbId) ?: return null
        Log.i(TAG, "tmdbTv($tmdbId) → bundle hit (${hit.name})")
        return hit
    }
}
