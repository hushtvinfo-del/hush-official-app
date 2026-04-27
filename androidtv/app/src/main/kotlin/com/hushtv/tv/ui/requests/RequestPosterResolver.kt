package com.hushtv.tv.ui.requests

import android.content.Context
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.RequestMetaStore
import com.hushtv.tv.data.TmdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Retroactive TMDB metadata fetcher for content requests.
 *
 * Requests submitted before the v1.39.0 TMDB-aware request flow
 * shipped have no `RequestMetaStore` entry and no `[TMDB ...]` tag
 * in `additional_info`, so the home rail / list / detail screens
 * have no poster or backdrop to display.
 *
 * To fix that visually, we lazy-search TMDB by title (most-popular
 * hit, same scoring `searchMovie` / `searchTv` use) and persist the
 * result to [RequestMetaStore]. Done at most once per request id —
 * subsequent renders read from cache and never hit TMDB.
 *
 * Also de-duplicates concurrent fetches via [requestMutex] so a
 * cold home screen with 5 backdrop cards doesn't fan out 5
 * identical TMDB calls when a single suspending render call would
 * have shared the result.
 */
object RequestPosterResolver {

    private val inFlight = HashSet<String>()
    private val requestMutex = Mutex()

    /**
     * Returns existing metadata if present, otherwise performs a
     * single TMDB title-search round-trip, persists the top-popularity
     * hit, and returns that. Returns null when:
     *   • [request] has no usable title, OR
     *   • TMDB has no result (will not retry until process restart),
     *   • TMDB call throws (transient error — caller can retry on a
     *     subsequent recomposition).
     */
    suspend fun resolveOrFetch(
        ctx: Context,
        request: ContentRequestApi.Request,
    ): RequestMetaStore.Meta? = withContext(Dispatchers.IO) {
        // 1. Local cache. If the cached meta already has an overview,
        //    we're done — skip any network call. Older caches (before
        //    v1.42.29) didn't persist the overview field even though
        //    the rest of the metadata was fine. In that case we fall
        //    through to the TMDB search below to backfill it, same
        //    way as we do for a completely empty cache.
        val cached = RequestMetaStore.get(ctx, request.id)
        if (cached != null && !cached.overview.isNullOrBlank()) {
            return@withContext cached
        }

        // 2. Inline tag in the gateway-stored `additional_info`.
        //    Only honoured when there's nothing cached at all — if
        //    we already have a richer cached entry (poster, year,
        //    title), keep it and fall through to TMDB to enrich the
        //    overview.
        if (cached == null) {
            RequestMetaStore.parseTag(request.additionalInfo)?.let { meta ->
                RequestMetaStore.put(ctx, request.id, meta)
                if (!meta.overview.isNullOrBlank()) return@withContext meta
            }
        }

        // 3. Best-effort TMDB title-search retrofit. Only fire once
        //    per process per request id — if it fails (no results,
        //    rate limit), we don't keep retrying every recomposition.
        val key = "fetch-${request.id}"
        val canFire = requestMutex.withLock {
            if (inFlight.contains(key)) false else { inFlight.add(key); true }
        }
        if (!canFire) return@withContext cached

        val title = request.title.trim()
        if (title.isBlank()) return@withContext cached

        runCatching {
            val hits = if (request.type == "series") {
                TmdbService.searchTvList(title)
            } else {
                TmdbService.searchMoviesList(title)
            }
            val top = hits.firstOrNull() ?: return@runCatching cached
            val displayTitle = top.title ?: top.name ?: title
            val year = parseYear(top.release_date) ?: parseYear(top.first_air_date)
            val meta = RequestMetaStore.Meta(
                tmdbId = top.id,
                tmdbType = if (request.type == "series") "tv" else "movie",
                // Keep any previously-cached image paths if the new
                // search returned nulls for them — we don't want to
                // regress an existing poster just to backfill the
                // overview.
                posterPath = top.poster_path ?: cached?.posterPath,
                backdropPath = top.backdrop_path ?: cached?.backdropPath,
                releaseYear = year ?: cached?.releaseYear,
                title = displayTitle.ifBlank { cached?.title ?: title },
                overview = top.overview.ifBlank { null },
            )
            RequestMetaStore.put(ctx, request.id, meta)
            meta
        }.getOrNull() ?: cached
    }

    private fun parseYear(date: String?): Int? {
        if (date.isNullOrBlank() || date.length < 4) return null
        return date.substring(0, 4).toIntOrNull()
    }
}
