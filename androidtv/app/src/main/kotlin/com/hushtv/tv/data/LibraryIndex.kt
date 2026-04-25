package com.hushtv.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lazy-loaded, in-memory snapshot of every movie + series title in
 * the active Xtream library, grouped by [TitleMatcher.normalize] key.
 *
 * Powers the "Already in your library" deduplication on the request
 * picker — when the user types "Terminator" and TMDB returns 8
 * candidates, we use this index to flag which ones the user can
 * already stream right now (and route them straight to the title
 * instead of letting them submit a duplicate request).
 *
 * Cached per playlist for the lifetime of the process; refreshed
 * automatically if the playlist tuple changes (host/user/pass).
 *
 * Construction is cheap (does NOT hit the network on cache hit) but
 * the initial `prime` call talks to Xtream and lists all VOD/series
 * — so call it from a background coroutine.
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
    )

    @Volatile private var primedKey: String? = null
    @Volatile private var byNormalisedTitle: Map<String, List<Entry>> = emptyMap()
    @Volatile private var byNormalisedTitleNoYear: Map<String, List<Entry>> = emptyMap()

    /** Drop the cache, e.g. when user switches profile. */
    fun reset() {
        primedKey = null
        byNormalisedTitle = emptyMap()
        byNormalisedTitleNoYear = emptyMap()
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
            if (primedKey == key && byNormalisedTitle.isNotEmpty()) return@withContext true

            val movieResult = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username,
                    playlist.password, "movie")
            }.getOrNull() ?: return@withContext false
            val seriesResult = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username,
                    playlist.password, "series")
            }.getOrNull() ?: return@withContext false

            val mainBucket = HashMap<String, MutableList<Entry>>()
            val noYearBucket = HashMap<String, MutableList<Entry>>()

            fun ingest(entries: List<MediaCard>, kind: String) {
                for (c in entries) {
                    val raw = c.title
                    val full = TitleMatcher.normalize(raw)
                    if (full.isBlank()) continue
                    val e = Entry(
                        kind = kind,
                        streamId = if (kind == "movie") c.streamId else 0,
                        seriesId = if (kind == "series") c.seriesId else 0,
                        title = raw,
                        poster = c.poster,
                    )
                    mainBucket.getOrPut(full) { mutableListOf() } += e
                    val withoutYear = stripTrailingYear(full)
                    if (withoutYear != full) {
                        noYearBucket.getOrPut(withoutYear) { mutableListOf() } += e
                    }
                }
            }

            ingest(movieResult, "movie")
            ingest(seriesResult, "series")

            byNormalisedTitle = mainBucket
            byNormalisedTitleNoYear = noYearBucket
            primedKey = key
            true
        }

    /**
     * Tolerant exact / near-exact lookup. Returns the first matching
     * library entry restricted to [kind] ("movie" or "series") OR
     * null if nothing matches.
     *
     * Match passes (in order):
     *   1. Normalised title equality
     *   2. Normalised title equality with a trailing "(YYYY)" / "YYYY"
     *      stripped from the candidate side
     *   3. Substring containment (either direction) — only when the
     *      query is at least 5 chars to avoid false positives like
     *      "It" matching "Its Always Sunny".
     */
    fun lookup(rawTitle: String, kind: String): Entry? {
        val norm = TitleMatcher.normalize(rawTitle)
        if (norm.isBlank()) return null
        byNormalisedTitle[norm]?.firstOrNull { it.kind == kind }?.let { return it }
        val withoutYear = stripTrailingYear(norm)
        if (withoutYear != norm) {
            byNormalisedTitle[withoutYear]?.firstOrNull { it.kind == kind }?.let { return it }
            byNormalisedTitleNoYear[withoutYear]?.firstOrNull { it.kind == kind }?.let { return it }
        }
        if (norm.length >= 5) {
            byNormalisedTitle.entries.firstOrNull { (k, list) ->
                list.any { it.kind == kind } &&
                    (k.contains(norm) || norm.contains(k))
            }?.value?.firstOrNull { it.kind == kind }?.let { return it }
        }
        return null
    }

    private fun stripTrailingYear(norm: String): String {
        // matches "title 1984" or "title (1984)" — both already lowered
        // by TitleMatcher.normalize(). Removes the year + any leading
        // whitespace.
        val regex = Regex("\\s*\\(?(19|20)\\d{2}\\)?\\s*$")
        return norm.replace(regex, "").trim()
    }
}
