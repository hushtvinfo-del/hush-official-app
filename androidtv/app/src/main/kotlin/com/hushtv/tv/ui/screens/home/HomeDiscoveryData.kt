package com.hushtv.tv.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import com.hushtv.tv.data.DiscoveryCache
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * One discovery shortcut card — a full-bleed tile on Home that jumps
 * straight into a specific Xtream category. Also provides the artwork
 * pool for the hero backdrop that renders when the card is focused.
 *
 * [backdrops] are hi-res TMDB landscape images (w1280) — the preferred
 * artwork for the full-bleed hero. [posters] is the Xtream fallback so
 * the hero never ends up empty if TMDB doesn't find matches.
 */
data class DiscoveryCard(
    val id: String,
    val title: String,
    val eyebrow: String,
    val subtitle: String,
    val tag: String,
    val type: String,              // "movie" or "series" — for the nav route
    val categoryName: String,      // matched against Xtream categories
    val backdrops: List<String>,   // TMDB w1280 landscape URLs (preferred)
    val posters: List<String>,     // Xtream poster URLs (fallback)
    val itemCount: Int,            // used in the subtitle copy
) {
    /** Best-quality artwork pool — TMDB backdrops first, posters only if empty. */
    val heroArt: List<String> get() = backdrops.ifEmpty { posters }
}

/**
 * Loads Discovery card artwork with a two-phase strategy:
 *
 *  1) **Cache-first** — on first composition we synchronously read the
 *     last-known-good backdrops/posters/counts out of [DiscoveryCache]
 *     (SharedPreferences). The hero renders hi-res art INSTANTLY — no
 *     empty-state flash on cold start, no waiting for 30+ TMDB calls.
 *
 *  2) **Background refresh** — a `LaunchedEffect` then re-fetches from
 *     Xtream + TMDB. If the fresh data differs, the cards swap and the
 *     cache is updated. Coil prefetches each backdrop URL to the disk
 *     cache so the 12 s rotation + Ken-Burns crossfade is flicker-free.
 */
@Composable
fun rememberDiscoveryCards(playlistId: String): List<DiscoveryCard> {
    val ctx = LocalContext.current

    // Seed state from cache on first composition so the hero is never empty.
    var cards by remember(playlistId) {
        mutableStateOf(buildCardsFromCache(ctx, playlistId))
    }

    LaunchedEffect(playlistId) {
        val p = PlaylistStore.find(ctx, playlistId) ?: return@LaunchedEffect
        val (movies, series) = withContext(Dispatchers.IO) {
            coroutineScope {
                val m = async {
                    runCatching {
                        pickCategory(p.host, p.username, p.password, "movie", "NEW RELEASES")
                    }.getOrDefault(Triple(emptyList<MediaCard>(), emptyList(), emptyList()))
                }
                val s = async {
                    runCatching {
                        pickCategory(p.host, p.username, p.password, "series", "POPULAR SERIES")
                    }.getOrDefault(Triple(emptyList<MediaCard>(), emptyList(), emptyList()))
                }
                m.await() to s.await()
            }
        }

        // Persist fresh artwork so the NEXT cold start is instant.
        DiscoveryCache.saveBackdrops(ctx, playlistId, "movie", movies.third)
        DiscoveryCache.saveposters(ctx, playlistId, "movie", movies.second)
        DiscoveryCache.saveItemCount(ctx, playlistId, "movie", movies.first.size)
        DiscoveryCache.saveBackdrops(ctx, playlistId, "series", series.third)
        DiscoveryCache.saveposters(ctx, playlistId, "series", series.second)
        DiscoveryCache.saveItemCount(ctx, playlistId, "series", series.first.size)

        cards = listOf(
            DiscoveryCard(
                id = "latest-movies",
                title = "Latest Movies",
                eyebrow = "JUST ADDED",
                subtitle = "Fresh blockbusters straight from the big screen — new every week.",
                tag = "${movies.first.size} NEW",
                type = "movie",
                categoryName = "NEW RELEASES",
                backdrops = movies.third,
                posters = movies.second,
                itemCount = movies.first.size,
            ),
            DiscoveryCard(
                id = "latest-series",
                title = "Latest Series",
                eyebrow = "TRENDING NOW",
                subtitle = "The hottest shows everyone's binging right now.",
                tag = "${series.first.size} HOT",
                type = "series",
                categoryName = "POPULAR SERIES",
                backdrops = series.third,
                posters = series.second,
                itemCount = series.first.size,
            ),
        )

        // Warm the Coil image cache for every backdrop we'll rotate
        // through so the first swap is visible INSTANTLY.
        prefetchAll(ctx, cards.flatMap { it.heroArt })
    }

    return cards
}

/**
 * Reconstruct Discovery cards straight out of the SharedPreferences
 * cache. Safe to call on the main thread — just a prefs read.
 * Returns an empty list if we've never seen this playlist before.
 */
private fun buildCardsFromCache(
    ctx: android.content.Context,
    playlistId: String,
): List<DiscoveryCard> {
    val movieBackdrops = DiscoveryCache.loadBackdrops(ctx, playlistId, "movie")
    val moviePosters = DiscoveryCache.loadPosters(ctx, playlistId, "movie")
    val movieCount = DiscoveryCache.loadItemCount(ctx, playlistId, "movie")
    val seriesBackdrops = DiscoveryCache.loadBackdrops(ctx, playlistId, "series")
    val seriesPosters = DiscoveryCache.loadPosters(ctx, playlistId, "series")
    val seriesCount = DiscoveryCache.loadItemCount(ctx, playlistId, "series")

    if (movieBackdrops.isEmpty() && moviePosters.isEmpty() &&
        seriesBackdrops.isEmpty() && seriesPosters.isEmpty()
    ) return emptyList()

    return listOf(
        DiscoveryCard(
            id = "latest-movies",
            title = "Latest Movies",
            eyebrow = "JUST ADDED",
            subtitle = "Fresh blockbusters straight from the big screen — new every week.",
            tag = "${movieCount} NEW",
            type = "movie",
            categoryName = "NEW RELEASES",
            backdrops = movieBackdrops,
            posters = moviePosters,
            itemCount = movieCount,
        ),
        DiscoveryCard(
            id = "latest-series",
            title = "Latest Series",
            eyebrow = "TRENDING NOW",
            subtitle = "The hottest shows everyone's binging right now.",
            tag = "${seriesCount} HOT",
            type = "series",
            categoryName = "POPULAR SERIES",
            backdrops = seriesBackdrops,
            posters = seriesPosters,
            itemCount = seriesCount,
        ),
    )
}

/** Fire-and-forget Coil prefetch — warms the disk cache for each URL. */
private fun prefetchAll(ctx: android.content.Context, urls: List<String>) {
    val loader = ctx.imageLoader
    urls.forEach { url ->
        val req = ImageRequest.Builder(ctx).data(url).build()
        loader.enqueue(req)
    }
}

/**
 * Finds the category whose name matches [categoryHint] (normalised
 * contains) and returns (items, xtream posters, tmdb backdrops). Falls
 * back to the "all" pool for the kind if no matching category exists —
 * so the card still shows SOMETHING instead of being empty.
 */
private suspend fun pickCategory(
    host: String,
    username: String,
    password: String,
    kind: String,
    categoryHint: String,
): Triple<List<MediaCard>, List<String>, List<String>> {
    val categories = XtreamApi.getCategories(host, username, password, kind)
    val needle = categoryHint.lowercase()
        .replace(Regex("[^a-z0-9 ]"), "").trim()
    val match = categories.firstOrNull {
        val hay = it.category_name.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "").trim()
        hay.contains(needle) || needle.contains(hay)
    }
    val items = if (match != null) {
        XtreamApi.getStreamsForCategory(host, username, password, kind, match.category_id)
    } else {
        XtreamApi.getAllStreams(host, username, password, kind).take(60)
    }
    val posters = items
        .mapNotNull { it.poster?.takeIf { u -> u.isNotBlank() } }
        .distinct()
        .take(8)
    val topTitles = items.take(18).map { it.title }
    val backdrops = runCatching {
        TmdbService.backdropsForTitles(topTitles, kind, limit = 8)
    }.getOrDefault(emptyList())
    return Triple(items, posters, backdrops)
}
