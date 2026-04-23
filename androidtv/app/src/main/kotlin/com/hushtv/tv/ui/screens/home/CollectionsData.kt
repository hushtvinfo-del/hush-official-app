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
 * "Back to the Future"). Top 20 most-iconic franchises.
 *
 * [tmdbCollectionId] is the TMDB collection ID (stable, lookup-safe).
 * [backdropUrl] is populated async from the collection's official
 * TMDB metadata (cached in SharedPreferences so cold starts paint
 * instantly).
 */
data class MovieCollection(
    val id: String,
    val displayName: String,
    val tagline: String,
    val tmdbCollectionId: Int,
    val accent: Color,
    val backdropUrl: String? = null,
)

// Top 20 most iconic movie box-sets / franchises. TMDB collection IDs
// are permanent — these won't drift.
private val COLLECTIONS_BASE = listOf(
    MovieCollection("star_wars", "Star Wars",
        "A galaxy far, far away — every saga film in order.",
        10, Color(0xFFFACC15)),
    MovieCollection("marvel_avengers", "The Avengers",
        "Earth's mightiest heroes, assembled.",
        86311, Color(0xFFDC2626)),
    MovieCollection("harry_potter", "Harry Potter",
        "The boy who lived — eight magical chapters.",
        1241, Color(0xFFB45309)),
    MovieCollection("fast_furious", "Fast & Furious",
        "Family. Horsepower. Heist after heist.",
        9485, Color(0xFF3B82F6)),
    MovieCollection("lotr", "The Lord of the Rings",
        "One ring to rule them all.",
        119, Color(0xFFD4A574)),
    MovieCollection("john_wick", "John Wick",
        "They shouldn't have killed his dog.",
        404609, Color(0xFF7F1D1D)),
    MovieCollection("mission_impossible", "Mission: Impossible",
        "Your mission, should you choose to accept it.",
        87359, Color(0xFFEF4444)),
    MovieCollection("james_bond", "James Bond",
        "The name's Bond — every 007 adventure.",
        645, Color(0xFFD4A574)),
    MovieCollection("jurassic", "Jurassic Park",
        "Life finds a way — the complete dinosaur saga.",
        328, Color(0xFFEA580C)),
    MovieCollection("terminator", "Terminator",
        "I'll be back. Every time.",
        528, Color(0xFFEF4444)),
    MovieCollection("matrix", "The Matrix",
        "There is no spoon. Follow the white rabbit.",
        2344, Color(0xFF22C55E)),
    MovieCollection("pirates", "Pirates of the Caribbean",
        "Yo ho, a pirate's life for thee.",
        295, Color(0xFFEAB308)),
    MovieCollection("back_to_future", "Back to the Future",
        "Great Scott! The time-travel trilogy.",
        264, Color(0xFFF97316)),
    MovieCollection("godfather", "The Godfather",
        "An offer you can't refuse.",
        230, Color(0xFFD97706)),
    MovieCollection("indiana_jones", "Indiana Jones",
        "Snakes. Why did it have to be snakes?",
        84, Color(0xFFB45309)),
    MovieCollection("rocky", "Rocky",
        "Going the distance — the full heavyweight saga.",
        1575, Color(0xFFEAB308)),
    MovieCollection("hobbit", "The Hobbit",
        "An unexpected journey — in and back again.",
        121938, Color(0xFF84CC16)),
    MovieCollection("toy_story", "Toy Story",
        "To infinity — and beyond.",
        10194, Color(0xFF3B82F6)),
    MovieCollection("shrek", "Shrek",
        "Ogres have layers. Like onions.",
        2150, Color(0xFF84CC16)),
    MovieCollection("bourne", "The Bourne Series",
        "He doesn't know who he is — but he knows what he can do.",
        31562, Color(0xFF64748B)),
)

/**
 * Loads collection cards with TMDB backdrops fetched async. Cache-first
 * for instant cold starts; refreshes in background.
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
