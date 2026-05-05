package com.hushtv.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lazy on-demand TMDB metadata cache for the **currently focused**
 * poster on the themed-detail screen.
 *
 * Why a dedicated cache (and not just hit TMDB inside the composable):
 *   • The detail screen has 30-90 posters; pre-fetching every one
 *     would burn 60+ TMDB calls on entry. Most users only browse a
 *     handful before clicking — pre-fetch is wasted bandwidth.
 *   • The focused-item hero text (overview/year/rating/runtime/
 *     backdrop) only needs to be ready by the time the user lingers
 *     on a poster, not before. ~250 ms of latency on first hover is
 *     invisible because the user typically takes 500 ms+ to read.
 *   • Once fetched, the same item is hit again on every focus
 *     return — so we keep an in-process cache keyed by the
 *     (libraryStreamId, libraryTitle) tuple for the lifetime of the
 *     process.
 *
 * Threading: [resolve] is suspend and runs on Dispatchers.IO.
 * Snapshot reads are lock-free.
 */
object ThemedHeroMetaCache {

    /** TMDB metadata pulled lazily for a single library hit. */
    data class Meta(
        val tmdbId: Int,
        val backdropUrl: String?,
        val posterUrl: String?,
        val overview: String,
        val voteAverage: Double,
        val runtimeMinutes: Int?,
        val year: Int?,
    )

    @Volatile private var snapshot: Map<Int, Meta> = emptyMap()

    /** Read-only snapshot getter. Returns null if not cached yet. */
    fun get(streamId: Int): Meta? = snapshot[streamId]

    /**
     * Fetch TMDB metadata for the given library hit. No-op if
     * already cached. Errors swallowed — caller falls back to
     * library poster + theme blurb in that case.
     */
    suspend fun resolve(streamId: Int, title: String, year: Int?): Meta? {
        snapshot[streamId]?.let { return it }
        val meta = withContext(Dispatchers.IO) {
            val tmdbId = runCatching { TmdbService.searchMovie(title, year) }
                .getOrNull() ?: return@withContext null
            val movie = runCatching { TmdbService.getMovie(tmdbId) }
                .getOrNull() ?: return@withContext null
            Meta(
                tmdbId = movie.id,
                backdropUrl = TmdbService.img(movie.backdrop_path, "original"),
                posterUrl = TmdbService.img(movie.poster_path, "original"),
                overview = movie.overview,
                voteAverage = movie.vote_average,
                runtimeMinutes = movie.runtime,
                year = movie.release_date?.take(4)?.toIntOrNull(),
            )
        } ?: return null
        snapshot = snapshot + (streamId to meta)
        return meta
    }
}
