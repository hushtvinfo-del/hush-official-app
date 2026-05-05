package com.hushtv.tv.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * Pre-built home-screen rail snapshot.
 *
 * Why this exists
 * ───────────────
 * Before v1.43.08 the home screen kicked off three Xtream fetches
 * in a `LaunchedEffect` *after* the menu composed. On Fire TV 4K
 * (gen 1 especially) that was 1-3 seconds of empty rails while the
 * device chewed through TLS + JSON + bitmap loading all at once.
 *
 * The fetches themselves were already warmed by [BootRefreshScreen]
 * (which fetches every category + every stream per kind to populate
 * OkHttp's disk cache), but the home screen's *specific* endpoints
 * — `get_*_streams&category_id=<id>` — are a different cache key
 * than the boot's `get_*_streams` (no id). So the disk warming
 * missed the exact URLs the home screen hit.
 *
 * This cache closes that gap. The boot refresh uses the same
 * `/player_api.php` data it already has in memory to build the
 * home rails directly, then drops them into this volatile object.
 * The home screen reads the snapshot synchronously on first
 * composition — rails are *already populated* the moment the user
 * lands on the menu.
 *
 * Memory model
 * ────────────
 * • @Volatile so writes from the boot (main thread, inside a
 *   Dispatchers.IO block that switches back) are visible to the
 *   home screen reader on first composition.
 * • [snapshot] returns the full struct by reference — the fields
 *   are all `val`-only immutable lists, so sharing is safe.
 * • Disk cache is best-effort: surviving a process death we can
 *   paint the home screen in < 50 ms on cold launch (before the
 *   boot refresh completes) with last-known rails. Staleness is
 *   bounded by [STALE_CUTOFF_MS] and the user's explicit refresh
 *   cycles; if the cache is older we just skip the fast paint.
 */
object HomeRailsCache {

    /**
     * Complete home-screen rail set. Each list is sorted and
     * truncated to the sizes the home screen actually renders, so
     * the screen doesn't do any further list transformation.
     */
    data class Rails(
        val liveNow: List<MediaCard>,
        /** First N movie categories paired with their streams. */
        val movies: List<MovieRow>,
        val seriesRow: List<MediaCard>,
        /** Flat top-picks derived from the movie rows. */
        val trendingRow: List<MediaCard>,
        /** Unix millis. Used by callers to decide "is this fresh
         *  enough to trust while the boot refresh is still in
         *  flight?" */
        val fetchedAtMs: Long,
        /** Identifies which profile this snapshot was built for.
         *  Scoped so switching profiles mid-run doesn't paint
         *  rails from the wrong account. */
        val playlistId: String,
    )

    data class MovieRow(
        val categoryName: String,
        val cards: List<MediaCard>,
    )

    /** Accept disk snapshots up to 12 hours old on cold-launch
     *  fast-paint. Boot refresh runs in parallel and will overwrite
     *  with fresh data within a few seconds regardless, so this
     *  ceiling only matters for the first frame. */
    private const val STALE_CUTOFF_MS = 12L * 60 * 60 * 1000

    @Volatile
    private var inMemory: Rails? = null

    /** Fast-path read — no I/O. Called by TVMainMenuScreen on
     *  first composition. */
    fun snapshot(playlistId: String): Rails? =
        inMemory?.takeIf { it.playlistId == playlistId }

    /** Write from the boot refresh once rails are assembled. */
    fun put(rails: Rails) {
        inMemory = rails
    }

    /** Called when the user signs out / switches profile so stale
     *  data from another account never leaks into the next session. */
    fun clear() {
        inMemory = null
    }

    // ── Disk cache ─────────────────────────────────────────────
    // Disk is a single tiny JSON file under cacheDir. We use
    // cacheDir (not filesDir) because OS can evict it under storage
    // pressure without breaking anything — worst case we fall back
    // to the network path.

    private const val DISK_FILE = "home_rails_v1.json"

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val railsAdapter by lazy {
        moshi.adapter(Rails::class.java)
    }

    /** Best-effort persist. Runs on the caller's dispatcher —
     *  callers already pass Dispatchers.IO. Swallows all errors:
     *  disk cache is a perf optimization, not required for correctness. */
    fun persist(ctx: Context, rails: Rails) {
        runCatching {
            val f = File(ctx.cacheDir, DISK_FILE)
            f.writeText(railsAdapter.toJson(rails))
        }
    }

    /** Best-effort load from disk. Returns null on any failure
     *  (file missing, stale, wrong profile, JSON drift after a
     *  schema change). Callers treat null as "no fast paint
     *  possible, wait for boot refresh". */
    fun loadDisk(ctx: Context, playlistId: String): Rails? {
        return runCatching {
            val f = File(ctx.cacheDir, DISK_FILE)
            if (!f.exists()) return@runCatching null
            val json = f.readText()
            val rails = railsAdapter.fromJson(json) ?: return@runCatching null
            if (rails.playlistId != playlistId) return@runCatching null
            if (System.currentTimeMillis() - rails.fetchedAtMs > STALE_CUTOFF_MS) {
                return@runCatching null
            }
            rails
        }.getOrNull()
    }

    /**
     * Build a [Rails] snapshot from raw Xtream data. Called by the
     * boot refresh once it already has categories + streams in
     * memory — no extra network requests, ~zero cost.
     *
     * The slicing mirrors exactly what [TVMainMenuScreen] renders:
     *   • Live row:   first live category, first 12 streams
     *   • Movie rows: first 3 movie categories, first 16 each
     *   • Series row: first series category, first 14
     *   • Trending:   the first 10 of the flattened movie rows
     *
     * If a category slot is missing we just produce an empty row —
     * the home screen already renders "No items" gracefully and a
     * half-empty cache is still miles ahead of a blank screen.
     */
    fun build(
        playlistId: String,
        liveCategories: List<XtreamCategory>,
        movieCategories: List<XtreamCategory>,
        seriesCategories: List<XtreamCategory>,
        liveByCat: Map<String, List<MediaCard>>,
        moviesByCat: Map<String, List<MediaCard>>,
        seriesByCat: Map<String, List<MediaCard>>,
    ): Rails {
        val liveNow = liveCategories.firstOrNull()?.let { c ->
            liveByCat[c.category_id].orEmpty().take(12)
        }.orEmpty()

        val movies = movieCategories.take(3).map { c ->
            MovieRow(
                categoryName = c.category_name,
                cards = moviesByCat[c.category_id].orEmpty().take(16),
            )
        }.filter { it.cards.isNotEmpty() }

        val seriesRow = seriesCategories.firstOrNull()?.let { c ->
            seriesByCat[c.category_id].orEmpty().take(14)
        }.orEmpty()

        val trendingRow = movies.flatMap { it.cards }.take(10)

        return Rails(
            liveNow = liveNow,
            movies = movies,
            seriesRow = seriesRow,
            trendingRow = trendingRow,
            fetchedAtMs = System.currentTimeMillis(),
            playlistId = playlistId,
        )
    }
}
