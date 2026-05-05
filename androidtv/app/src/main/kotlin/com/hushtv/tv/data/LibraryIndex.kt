package com.hushtv.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lazy-loaded, in-memory snapshot of every movie + series title in
 * the active Xtream library. Uses [TitleMatcher.isStrongMatch] /
 * [TitleMatcher.findBestMatch] under the hood — the SAME proven
 * matcher the Collections system relies on. That matcher requires:
 *
 *   • exact normalised-title equality (with year gate ±1y), OR
 *   • contiguous word-phrase containment **with** ≥ 3 real words on
 *     both sides AND year agreement within ±1 year.
 *
 * That's strict enough to prevent a 1-letter library title like
 * "Z" matching "Analyze That" via naive substring, which the
 * earlier custom matcher allowed and produced spectacularly wrong
 * Watch-now deep-links from.
 *
 * Cached per playlist for the lifetime of the process; refreshed
 * automatically if the playlist tuple changes (host/user/pass).
 */
object LibraryIndex {

    /**
     * One stored entry. Captured lean — we keep just enough to flip
     * the "Already in library" badge on AND deep-link to the title.
     */
    data class Entry(
        val kind: String,           // "movie" | "series"
        val streamId: Int,          // movie streamId  (0 for series)
        val seriesId: Int,          // series id       (0 for movies)
        val title: String,
        val poster: String?,
        /** Best-effort year parsed from the title (e.g. "Title (2024)"). */
        val releaseYear: Int?,
    )

    @Volatile private var primedKey: String? = null
    @Volatile private var allEntries: List<Entry> = emptyList()

    // Pre-built TitleMatcher indices, one per kind, so every lookup
    // is a list scan over normalised-key tuples instead of rebuilding
    // the index on each call.
    @Volatile private var movieIdx: List<TitleMatcher.LibraryEntry<Entry>> = emptyList()
    @Volatile private var seriesIdx: List<TitleMatcher.LibraryEntry<Entry>> = emptyList()

    /** True if the library has been primed at least once for any
     *  playlist. Used by UIs that need to gate work behind a primed
     *  index without re-priming themselves. */
    fun isPrimed(): Boolean = primedKey != null && allEntries.isNotEmpty()

    /** Read-only snapshot of every library entry, both kinds. Used
     *  by Continue Watching to cross-reference streamId → kind for
     *  legacy entries saved before v1.43.31. */
    fun allEntries(): List<Entry> = allEntries

    /** Drop the cache, e.g. when user switches profile. */
    fun reset() {
        primedKey = null
        allEntries = emptyList()
        movieIdx = emptyList()
        seriesIdx = emptyList()
    }

    /**
     * Loads movies + series for [playlist] if not already primed.
     * Idempotent — repeated calls are no-ops once primed.
     *
     * @return true if the index is populated after this call,
     *         false on a network failure (caller can try again).
     */
    suspend fun prime(ctx: Context, playlist: Playlist): Boolean =
        withContext(Dispatchers.IO) {
            val key = "${playlist.host}|${playlist.username}|${playlist.password}"
            if (primedKey == key && allEntries.isNotEmpty()) return@withContext true

            val movieResult = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username,
                    playlist.password, "movie")
            }.getOrNull() ?: return@withContext false
            val seriesResult = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username,
                    playlist.password, "series")
            }.getOrNull() ?: return@withContext false

            val combined = mutableListOf<Entry>()
            for (c in movieResult) {
                combined += Entry(
                    kind = "movie",
                    streamId = c.streamId,
                    seriesId = 0,
                    title = c.title,
                    poster = c.poster,
                    releaseYear = TitleMatcher.extractYear(c.title),
                )
            }
            for (c in seriesResult) {
                combined += Entry(
                    kind = "series",
                    streamId = 0,
                    seriesId = c.seriesId,
                    title = c.title,
                    poster = c.poster,
                    releaseYear = TitleMatcher.extractYear(c.title),
                )
            }

            allEntries = combined
            movieIdx = TitleMatcher.buildIndex(combined.filter { it.kind == "movie" }) { it.title }
            seriesIdx = TitleMatcher.buildIndex(combined.filter { it.kind == "series" }) { it.title }
            primedKey = key
            true
        }

    private fun indexFor(kind: String) =
        if (kind == "series") seriesIdx else movieIdx

    /**
     * Strict lookup using the same [TitleMatcher.findBestMatch] that
     * the Collections feature uses. Returns null when no library
     * entry passes the strong-match bar. Year-aware when caller
     * provides one.
     */
    fun lookup(rawTitle: String, kind: String): Entry? =
        TitleMatcher.findBestMatch(
            tmdbTitle = rawTitle,
            tmdbYear = null,
            libraryIndex = indexFor(kind),
        )

    /**
     * Year-aware best match — preferred when caller has TMDB
     * metadata. Pure delegation to [TitleMatcher.findBestMatch].
     * Same selector Collections relies on, so request "Watch now"
     * resolution and franchise tile clicks always agree.
     */
    fun findBest(
        rawTitle: String,
        kind: String,
        preferredYear: Int?,
    ): Entry? = TitleMatcher.findBestMatch(
        tmdbTitle = rawTitle,
        tmdbYear = preferredYear,
        libraryIndex = indexFor(kind),
    )

    /**
     * Returns every library entry that passes [TitleMatcher.isStrongMatch]
     * for the given title + year. Used by [TmdbIdResolver] to fan
     * out per-title `vod_info` calls when more than one candidate
     * passes the strong-match bar (rare, but happens with sequels
     * sharing a phrase).
     */
    fun findAllCandidates(
        rawTitle: String,
        kind: String,
        preferredYear: Int? = null,
    ): List<Entry> = indexFor(kind)
        .filter { entry ->
            TitleMatcher.isStrongMatch(
                tmdbTitle = rawTitle,
                tmdbYear = preferredYear,
                libTitle = entry.raw,
                libYear = entry.year,
            )
        }
        .map { it.payload }
}
