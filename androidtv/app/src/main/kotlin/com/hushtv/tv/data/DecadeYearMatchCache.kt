package com.hushtv.tv.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-scoped cache mapping a (year) → list of library matches
 * resolved against [HushDecadeYears.year].
 *
 * Matches Themes' [ThemedMatchCache] pattern exactly:
 *   • SnapshotStateMap so Compose recomposes whenever a year lands.
 *   • One-time prime that walks every year in popularity order
 *     against the local [LibraryIndex] off the main thread.
 *   • Cheap O(1) reads from the UI thread thereafter.
 *
 * Why bake all 90 years on first prime?
 *   • Each year is ~250 candidate titles. Resolving all 90 years
 *     synchronously after the user clicks a year card would block
 *     the UI thread for ~600 ms on a Fire Stick. Pre-priming on a
 *     background coroutine spreads the work over ~3–4 seconds at
 *     idle, so the year-detail screen always paints in <50 ms.
 *
 * Persistence: in-memory only — re-priming on every cold start is
 * cheap (<5 s on a Fire Stick) and keeps the matches in sync with
 * the user's latest library refresh automatically.
 */
object DecadeYearMatchCache {

    /**
     * Resolved library hits for one year. Same shape as
     * [ThemedLibraryMatch] so detail screens can reuse the existing
     * poster-tile rendering pipeline.
     */
    data class YearMatch(
        val streamId: Int,
        val title: String,
        val poster: String?,
        val year: Int?,
    )

    /** Year → list of library matches (popularity order). Compose-observable. */
    val snapshot = mutableStateMapOf<Int, List<YearMatch>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val primeMutex = Mutex()
    private var primeJob: Job? = null
    private var primedFor: String? = null

    /**
     * Kick the off-thread matcher. Idempotent — the second call for
     * the same playlist is a no-op while the first one is still
     * running; passing a different playlistId restarts.
     */
    fun primeAsync(ctx: Context, playlist: Playlist) {
        scope.launch {
            primeMutex.withLock {
                val key = "${playlist.id}:${playlist.host}"
                if (primedFor == key && primeJob?.isActive != true) return@withLock
                if (primedFor == key && primeJob?.isActive == true) return@withLock
                primedFor = key
                primeJob?.cancel()
                primeJob = scope.launch { primeNow(ctx, playlist) }
            }
        }
    }

    private suspend fun primeNow(ctx: Context, playlist: Playlist) {
        // Make sure LibraryIndex is ready first.
        runCatching { LibraryIndex.prime(ctx, playlist) }

        for (decade in HushDecadeYears.all) {
            for (yr in decade.years) {
                val matches = matchYear(yr)
                snapshot[yr.year] = matches
                // Yield between years so the UI thread stays
                // responsive during the prime.
                kotlinx.coroutines.yield()
            }
        }
    }

    /**
     * Synchronous resolve of one year against [LibraryIndex].
     * Reads ~250 titles in popularity order, picks the first
     * library hit per title (no duplicate streamIds), preserves
     * popularity ordering.
     */
    private fun matchYear(yr: HushYear): List<YearMatch> {
        val out = ArrayList<YearMatch>(yr.movies.size)
        val seen = HashSet<Int>()
        for (title in yr.movies) {
            val hit = LibraryIndex.findBest(title, "movie", yr.year) ?: continue
            if (!seen.add(hit.streamId)) continue
            out += YearMatch(
                streamId = hit.streamId,
                title = hit.title,
                poster = hit.poster,
                year = hit.releaseYear,
            )
        }
        return out
    }

    /**
     * Synchronous fallback. Used by the year-detail screen on first
     * paint when the prime hasn't reached this year yet — caller
     * invokes via withContext(IO) so we don't block the UI.
     */
    fun matchYearBlocking(year: Int): List<YearMatch> {
        snapshot[year]?.let { return it }
        val yr = HushDecadeYears.year(year) ?: return emptyList()
        val resolved = matchYear(yr)
        snapshot[year] = resolved
        return resolved
    }
}
