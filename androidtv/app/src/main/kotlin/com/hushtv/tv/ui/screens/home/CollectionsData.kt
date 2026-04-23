package com.hushtv.tv.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import com.hushtv.tv.data.DiscoveryCache
import com.hushtv.tv.data.TmdbService

/**
 * A single movie collection / box-set (e.g. "The Godfather",
 * "Back to the Future"). 20 hand-picked most-iconic franchises.
 *
 * [tmdbCollectionId] is the TMDB collection ID (stable, lookup-safe).
 * [backdropUrl] is populated async from the collection's official
 * TMDB metadata.
 */
data class MovieCollection(
    val id: String,
    val displayName: String,
    val tmdbCollectionId: Int,
    val accent: Color,
    val backdropUrl: String? = null,
)

// Top 20 most iconic movie box-sets / franchises. TMDB collection IDs
// are permanent — these won't drift.
private val COLLECTIONS_BASE = listOf(
    MovieCollection("star_wars", "Star Wars", 10, Color(0xFFFACC15)),
    MovieCollection("marvel_avengers", "The Avengers", 86311, Color(0xFFDC2626)),
    MovieCollection("harry_potter", "Harry Potter", 1241, Color(0xFFB45309)),
    MovieCollection("fast_furious", "Fast & Furious", 9485, Color(0xFF3B82F6)),
    MovieCollection("lotr", "The Lord of the Rings", 119, Color(0xFFD4A574)),
    MovieCollection("john_wick", "John Wick", 404609, Color(0xFF7F1D1D)),
    MovieCollection("mission_impossible", "Mission: Impossible", 87359, Color(0xFFEF4444)),
    MovieCollection("james_bond", "James Bond", 645, Color(0xFFD4A574)),
    MovieCollection("jurassic", "Jurassic Park", 328, Color(0xFFEA580C)),
    MovieCollection("terminator", "Terminator", 528, Color(0xFFEF4444)),
    MovieCollection("matrix", "The Matrix", 2344, Color(0xFF22C55E)),
    MovieCollection("pirates", "Pirates of the Caribbean", 295, Color(0xFFEAB308)),
    MovieCollection("back_to_future", "Back to the Future", 264, Color(0xFFF97316)),
    MovieCollection("godfather", "The Godfather", 230, Color(0xFFD97706)),
    MovieCollection("indiana_jones", "Indiana Jones", 84, Color(0xFFB45309)),
    MovieCollection("rocky", "Rocky", 1575, Color(0xFFEAB308)),
    MovieCollection("hobbit", "The Hobbit", 121938, Color(0xFF84CC16)),
    MovieCollection("toy_story", "Toy Story", 10194, Color(0xFF3B82F6)),
    MovieCollection("shrek", "Shrek", 2150, Color(0xFF84CC16)),
    MovieCollection("bourne", "The Bourne Series", 31562, Color(0xFF64748B)),
)

/**
 * Loads collection cards with TMDB backdrops fetched async. Cache-first
 * for instant cold starts.
 */
@Composable
fun rememberMovieCollections(): List<MovieCollection> {
    val ctx = LocalContext.current

    var collections by remember {
        mutableStateOf(
            COLLECTIONS_BASE.map { c ->
                c.copy(
                    backdropUrl = DiscoveryCache.loadCollectionBackdrop(ctx, c.tmdbCollectionId),
                )
            }
        )
    }

    LaunchedEffect(Unit) {
        val freshMap = runCatching {
            TmdbService.backdropsForCollections(COLLECTIONS_BASE.map { it.tmdbCollectionId })
        }.getOrDefault(emptyMap())

        if (freshMap.isEmpty()) return@LaunchedEffect

        freshMap.forEach { (cid, url) -> DiscoveryCache.saveCollectionBackdrop(ctx, cid, url) }

        collections = COLLECTIONS_BASE.map { c ->
            c.copy(backdropUrl = freshMap[c.tmdbCollectionId] ?: c.backdropUrl)
        }

        val loader = ctx.imageLoader
        collections.mapNotNull { it.backdropUrl }.forEach { url ->
            loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    return collections
}
