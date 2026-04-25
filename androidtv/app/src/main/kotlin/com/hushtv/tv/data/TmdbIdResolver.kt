package com.hushtv.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Confirms an ambiguous library match by exact-comparing TMDB ids.
 *
 * Many Xtream providers expose `tmdb_id` inside their per-title
 * `get_vod_info` / `get_series_info` responses. When [LibraryIndex]
 * returns more than one library candidate for a title (e.g. two
 * "Aladdin" entries — 1992 + 2019, or a provider that mis-labelled
 * a remake) we hit the per-title info endpoint and pick the entry
 * whose tmdb_id matches the request's TMDB id.
 *
 * Cached in-memory (process-scoped) so the same stream is only
 * resolved once. `tmdb_id` doesn't change for a given stream id.
 */
object TmdbIdResolver {

    @Volatile private var movieCache: MutableMap<Int, Int?> = mutableMapOf()
    @Volatile private var seriesCache: MutableMap<Int, Int?> = mutableMapOf()

    fun reset() {
        movieCache = mutableMapOf()
        seriesCache = mutableMapOf()
    }

    /**
     * Resolve the TMDB id for a single library entry by hitting the
     * provider's per-title metadata endpoint. Returns null when the
     * provider doesn't expose a tmdb_id (many don't), so callers
     * should fall back to year-aware title matching.
     */
    suspend fun resolveTmdbId(
        playlist: Playlist,
        entry: LibraryIndex.Entry,
    ): Int? = withContext(Dispatchers.IO) {
        when (entry.kind) {
            "movie" -> {
                val sid = entry.streamId
                if (sid <= 0) return@withContext null
                movieCache[sid]?.let { return@withContext it }
                val info = XtreamApi.getVodInfo(
                    playlist.host, playlist.username, playlist.password, sid,
                )
                val parsed = info?.info?.tmdb_id?.trim()?.takeIf { it.isNotBlank() }
                    ?.toIntOrNull()
                movieCache[sid] = parsed
                parsed
            }
            "series" -> {
                val sid = entry.seriesId
                if (sid <= 0) return@withContext null
                seriesCache[sid]?.let { return@withContext it }
                val info = XtreamApi.getSeriesInfo(
                    playlist.host, playlist.username, playlist.password,
                    sid.toString(),
                )
                // Series info ships tmdb_id inside the loose `info`
                // map. Pull it out tolerantly — some providers store
                // an Int, others a String, others a JSONified number.
                val raw = info.info?.get("tmdb")
                    ?: info.info?.get("tmdb_id")
                val parsed = when (raw) {
                    is Number -> raw.toInt()
                    is String -> raw.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
                    else -> null
                }
                seriesCache[sid] = parsed
                parsed
            }
            else -> null
        }
    }

    /**
     * Pick the library entry whose tmdb_id matches [targetTmdbId]
     * out of [candidates]. Resolves all candidate ids in parallel
     * (bounded — typical ambiguous lookup has 2-3 candidates).
     *
     * Falls back to the first candidate when no provider response
     * contains a tmdb_id at all (common for free Xtream lines).
     */
    suspend fun pickByTmdbId(
        playlist: Playlist,
        candidates: List<LibraryIndex.Entry>,
        targetTmdbId: Int,
    ): LibraryIndex.Entry? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null
        if (candidates.size == 1) return@coroutineScope candidates.first()

        val resolved = candidates.map { entry ->
            async { entry to resolveTmdbId(playlist, entry) }
        }.map { it.await() }

        // Direct match wins — bullet-proof: provider's tmdb_id ==
        // request's tmdb_id. Title shape doesn't matter.
        resolved.firstOrNull { it.second == targetTmdbId }?.first?.let {
            return@coroutineScope it
        }
        // Provider didn't expose tmdb_id for any candidate — caller
        // should treat this as inconclusive and fall back to year.
        if (resolved.all { it.second == null }) return@coroutineScope null
        // Provider exposed tmdb_id for some candidates but none
        // matched — pick the first candidate so the user still gets
        // a Watch-now experience even if ambiguous (better than a
        // dead button). The year-aware matcher upstream has already
        // narrowed this list.
        candidates.first()
    }
}
