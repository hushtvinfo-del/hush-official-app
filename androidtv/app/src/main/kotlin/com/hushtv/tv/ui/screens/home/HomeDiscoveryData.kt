package com.hushtv.tv.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
 * Loads both discovery categories in parallel — "NEW RELEASES" movies and
 * "POPULAR SERIES". Posters + TMDB backdrops power the hero rotation when
 * a card is focused. Returns an empty list while loading so the parent
 * can gracefully render a soft placeholder.
 */
@Composable
fun rememberDiscoveryCards(playlistId: String): List<DiscoveryCard> {
    val ctx = LocalContext.current
    var cards by remember(playlistId) { mutableStateOf<List<DiscoveryCard>>(emptyList()) }

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
    }

    return cards
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
