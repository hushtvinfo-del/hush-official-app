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
import com.hushtv.tv.data.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One discovery shortcut card — a full-bleed tile on Home that jumps
 * straight into a specific Xtream category. Also provides the artwork
 * pool for the hero mosaic backdrop that renders when the card is
 * focused.
 */
data class DiscoveryCard(
    val id: String,
    val title: String,
    val eyebrow: String,
    val subtitle: String,
    val tag: String,
    val type: String,              // "movie" or "series" — for the nav route
    val categoryName: String,      // matched against Xtream categories
    val posters: List<String>,     // up to 8 poster URLs for the mosaic
    val itemCount: Int,            // used in the subtitle copy
)

/**
 * Loads both discovery categories in parallel — "NEW RELEASES" movies and
 * "POPULAR SERIES". Posters power both the 2 home cards AND the hero
 * backdrop mosaic when a card is focused.
 *
 * Returns an empty list while loading so the parent can gracefully fall
 * back to a static placeholder.
 */
@Composable
fun rememberDiscoveryCards(playlistId: String): List<DiscoveryCard> {
    val ctx = LocalContext.current
    var cards by remember(playlistId) { mutableStateOf<List<DiscoveryCard>>(emptyList()) }

    LaunchedEffect(playlistId) {
        val p = PlaylistStore.find(ctx, playlistId) ?: return@LaunchedEffect
        val movies = withContext(Dispatchers.IO) {
            runCatching {
                pickPostersForCategory(
                    host = p.host, username = p.username, password = p.password,
                    kind = "movie", categoryHint = "NEW RELEASES",
                )
            }.getOrDefault(emptyList<MediaCard>() to emptyList())
        }
        val series = withContext(Dispatchers.IO) {
            runCatching {
                pickPostersForCategory(
                    host = p.host, username = p.username, password = p.password,
                    kind = "series", categoryHint = "POPULAR SERIES",
                )
            }.getOrDefault(emptyList<MediaCard>() to emptyList())
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
                posters = series.second,
                itemCount = series.first.size,
            ),
        )
    }

    return cards
}

/**
 * Finds the category whose name matches [categoryHint] (normalised
 * contains) and returns its items plus up to 8 poster URLs. Falls back to
 * the "all" pool for the kind if no matching category exists — so the card
 * still shows SOMETHING instead of being empty.
 */
private suspend fun pickPostersForCategory(
    host: String,
    username: String,
    password: String,
    kind: String,
    categoryHint: String,
): Pair<List<MediaCard>, List<String>> {
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
    return items to posters
}
