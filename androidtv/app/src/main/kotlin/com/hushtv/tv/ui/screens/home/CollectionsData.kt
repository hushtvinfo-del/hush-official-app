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
import com.hushtv.tv.data.DiscoveredCollection
import com.hushtv.tv.data.DiscoveryCache
import com.hushtv.tv.data.TmdbService
import kotlin.math.abs

/**
 * A single movie collection / box-set (e.g. "The Godfather",
 * "Back to the Future"). Top 20 are hand-curated with custom taglines
 * + accent colours; the rest are discovered dynamically from TMDB.
 */
data class MovieCollection(
    val id: String,
    val displayName: String,
    val tagline: String,
    val tmdbCollectionId: Int,
    val accent: Color,
    val backdropUrl: String? = null,
)

// ── Hand-curated top 20 franchises — shown FIRST on the home row. ───
private val CURATED_COLLECTIONS = listOf(
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

// ── Accent palette for DISCOVERED collections. Deterministic hash
//    based on the collection name → stable colour across app runs.
private val DISCOVERED_ACCENTS = listOf(
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308),
    Color(0xFF84CC16), Color(0xFF22C55E), Color(0xFF14B8A6),
    Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF8B5CF6),
    Color(0xFFD946EF), Color(0xFFEC4899), Color(0xFFF43F5E),
    Color(0xFFB45309), Color(0xFF64748B), Color(0xFF0EA5E9),
    Color(0xFFA855F7),
)

private fun accentFor(name: String): Color =
    DISCOVERED_ACCENTS[abs(name.hashCode()) % DISCOVERED_ACCENTS.size]

/** Strip common punctuation + casing so we can dedupe by "same franchise". */
private fun dedupeKey(name: String): String =
    name.lowercase()
        .replace(Regex("""\bcollection\b"""), "")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

/**
 * Load the full collection catalog — curated first, then discovered
 * from TMDB (popularity-seeded, cached 7 days). Everything paints
 * instantly from SharedPreferences on cold start; discovery happens
 * in the background on stale caches.
 */
@Composable
fun rememberMovieCollections(): List<MovieCollection> {
    val ctx = LocalContext.current

    // 1. Seed: curated (with cached backdrops) + previously-discovered.
    val seed = remember {
        val curated = CURATED_COLLECTIONS.map { c ->
            c.copy(backdropUrl = DiscoveryCache.loadCollectionBackdrop(ctx, c.tmdbCollectionId))
        }
        val discoveredCached = DiscoveryCache.loadDiscoveredCollections(ctx)
        mergeCuratedWithDiscovered(curated, discoveredCached)
    }
    var collections by remember { mutableStateOf(seed) }

    LaunchedEffect(Unit) {
        // (A) Refresh curated backdrops in the background.
        val freshMap = runCatching {
            TmdbService.backdropsForCollections(CURATED_COLLECTIONS.map { it.tmdbCollectionId })
        }.getOrDefault(emptyMap())
        if (freshMap.isNotEmpty()) {
            freshMap.forEach { (cid, url) ->
                DiscoveryCache.saveCollectionBackdrop(ctx, cid, url)
            }
        }
        val refreshedCurated = CURATED_COLLECTIONS.map { c ->
            c.copy(
                backdropUrl = freshMap[c.tmdbCollectionId]
                    ?: DiscoveryCache.loadCollectionBackdrop(ctx, c.tmdbCollectionId),
            )
        }

        val cachedDiscovered = DiscoveryCache.loadDiscoveredCollections(ctx)
        // If the cached list is smaller than our expected yield (a hint
        // that it was produced by an older, smaller discovery scan),
        // force a refresh too so users get the full 100+ catalog.
        val expectedMinYield = 80
        val shouldRefresh = cachedDiscovered.isEmpty() ||
            cachedDiscovered.size < expectedMinYield ||
            DiscoveryCache.shouldRefreshDiscoveredCollections(ctx)
        val discovered = if (shouldRefresh) {
            val fresh = runCatching {
                // Bigger pool: 20 pages of /movie/popular + 10 pages of
                // /movie/top_rated → ~600 movies scanned → yields 100+
                // unique franchise collections after dedupe.
                TmdbService.discoverPopularCollections(
                    popularPages = 20,
                    topRatedPages = 10,
                )
            }.getOrDefault(emptyList())
            if (fresh.isNotEmpty()) {
                DiscoveryCache.saveDiscoveredCollections(ctx, fresh)
                fresh
            } else {
                // Discovery failed — fall back to existing cache (even if
                // stale) so user still sees anything we previously had.
                cachedDiscovered
            }
        } else cachedDiscovered

        // (C) Merge, publish, and prefetch artwork so scrolling is instant.
        val merged = mergeCuratedWithDiscovered(refreshedCurated, discovered)
        collections = merged

        val loader = ctx.imageLoader
        merged.mapNotNull { it.backdropUrl }.forEach { url ->
            loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    return collections
}

/**
 * Combine curated + discovered into a single list, deduped by name.
 * Curated always wins and appears first — hand-authored taglines and
 * accent colours should never be overwritten by the generic TMDB data.
 */
private fun mergeCuratedWithDiscovered(
    curated: List<MovieCollection>,
    discovered: List<DiscoveredCollection>,
): List<MovieCollection> {
    val takenKeys = curated.map { dedupeKey(it.displayName) }.toMutableSet()
    val takenIds = curated.map { it.tmdbCollectionId }.toMutableSet()

    val extras = discovered.mapNotNull { d ->
        if (d.id in takenIds) return@mapNotNull null
        val key = dedupeKey(d.name)
        if (key in takenKeys) return@mapNotNull null
        takenKeys += key
        takenIds += d.id

        MovieCollection(
            id = "tmdb_${d.id}",
            displayName = d.name.replace(Regex("""\s*Collection$"""), "").trim(),
            tagline = "Every film in the franchise.",
            tmdbCollectionId = d.id,
            accent = accentFor(d.name),
            backdropUrl = d.backdropUrl,
        )
    }
    return curated + extras
}
