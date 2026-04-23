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
 * A single genre for the Home Genres pages. [searchKeyword] must match
 * the target Xtream category name exactly so the browse-screen
 * `initialCategoryName` contains-match resolves cleanly.
 *
 * [backdropUrl] is populated asynchronously from TMDB's `/discover`
 * endpoint filtered by `tmdbGenreId`. Cards render the gradient + label
 * immediately; the backdrop fades in once available.
 */
data class Genre(
    val id: String,
    val displayName: String,
    val searchKeyword: String,
    val tmdbGenreId: Int,         // 0 means "no TMDB match" (uses gradient only)
    val tagline: String,
    val accent: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val backdropUrl: String? = null,
    // Exact Xtream category ID on the user's primary provider.
    // Bulletproof deep-link without name fuzzy-matching; null here
    // falls back to the name-based matcher in TVBrowseScreen.
    val xtreamCategoryId: String? = null,
)

// ── MOVIE GENRES ────────────────────────────────────────────────────
// TMDB movie genre IDs (stable — published by TMDB). Accent colours
// hand-picked to evoke the mood of each genre. Xtream IDs match the
// user's primary provider's movie category list.
private val MOVIE_GENRES_BASE = listOf(
    Genre("action", "Action", "Action", 28,
        "Heart-pounding chases, explosive showdowns, non-stop adrenaline.",
        Color(0xFFF97316), Color(0xFF3A0A04), Color(0xFF7F1D1D),
        xtreamCategoryId = "107"),
    Genre("adventure", "Adventure", "Adventure", 12,
        "Epic quests and untamed journeys to places far from home.",
        Color(0xFFFCD34D), Color(0xFF1E3A2B), Color(0xFF3F4C1F),
        xtreamCategoryId = "109"),
    Genre("animation", "Animation", "Animation", 16,
        "Worlds only imagination can draw — for all ages.",
        Color(0xFFF472B6), Color(0xFF2D1040), Color(0xFF5B2180),
        xtreamCategoryId = "110"),
    Genre("comedy", "Comedy", "Comedy", 35,
        "Laughs, punchlines, and feel-good chaos.",
        Color(0xFFFACC15), Color(0xFF2A1E08), Color(0xFF7A5B0E),
        xtreamCategoryId = "111"),
    Genre("crime", "Crime", "Crime", 80,
        "Heists, stings, and streets that don't forgive.",
        Color(0xFF60A5FA), Color(0xFF0B1220), Color(0xFF1E293B),
        xtreamCategoryId = "112"),
    Genre("documentary", "Documentary", "Documentary", 99,
        "Real stories. Real people. Real impact.",
        Color(0xFF94A3B8), Color(0xFF0F172A), Color(0xFF334155),
        xtreamCategoryId = "113"),
    Genre("drama", "Drama", "Drama", 18,
        "Character, conflict, and the weight of every choice.",
        Color(0xFFC084FC), Color(0xFF1E1B3A), Color(0xFF4C1D95),
        xtreamCategoryId = "114"),
    Genre("family", "Family", "Family", 10751,
        "Films made for everyone around the couch.",
        Color(0xFF34D399), Color(0xFF042F2E), Color(0xFF0F4D45),
        xtreamCategoryId = "115"),
    Genre("fantasy", "Fantasy", "Fantasy", 14,
        "Magic, myth, and worlds that play by their own rules.",
        Color(0xFF8B5CF6), Color(0xFF1A0B3E), Color(0xFF4C1D95),
        xtreamCategoryId = "116"),
    Genre("history", "History", "History", 36,
        "Moments that shaped civilisations.",
        Color(0xFFD4A574), Color(0xFF2B1B0E), Color(0xFF57351A),
        xtreamCategoryId = "117"),
    Genre("horror", "Horror", "Horror", 27,
        "Dim the lights. Something's watching.",
        Color(0xFFDC2626), Color(0xFF07020A), Color(0xFF2B0404),
        xtreamCategoryId = "118"),
    Genre("mystery", "Mystery", "Mystery", 9648,
        "Clues, whispers, and the truth just out of reach.",
        Color(0xFF818CF8), Color(0xFF0B0A1F), Color(0xFF1E1A4D),
        xtreamCategoryId = "121"),
    Genre("music", "Music", "Music", 10402,
        "Sound, stage, and the rhythm of their lives.",
        Color(0xFFF43F5E), Color(0xFF2B041A), Color(0xFF5B0B33),
        xtreamCategoryId = "120"),
    Genre("romance", "Romance", "Romance", 10749,
        "Hearts colliding, butterflies rising.",
        Color(0xFFFB7185), Color(0xFF2A0B1A), Color(0xFF5B1433),
        xtreamCategoryId = "124"),
    Genre("scifi", "Science Fiction", "Science Fiction", 878,
        "Tomorrow, next century, a galaxy over.",
        Color(0xFF22D3EE), Color(0xFF041824), Color(0xFF0C3A5C),
        xtreamCategoryId = "126"),
    Genre("thriller", "Thriller", "Thriller", 53,
        "The clock is ticking. The stakes are real.",
        Color(0xFFEF4444), Color(0xFF0B0F1A), Color(0xFF2E0610),
        xtreamCategoryId = "129"),
    Genre("tv_movie", "TV Movie", "TV Movie", 10770,
        "Feature storytelling made for the small screen.",
        Color(0xFF38BDF8), Color(0xFF071B2E), Color(0xFF0F3A5E),
        xtreamCategoryId = "106"),
    Genre("war", "War", "War", 10752,
        "Courage, sacrifice, and history's hardest chapters.",
        Color(0xFFA16207), Color(0xFF1F140A), Color(0xFF4A2C10),
        xtreamCategoryId = "131"),
    Genre("western", "Western", "Western", 37,
        "Dust, grit, and showdowns under a setting sun.",
        Color(0xFFEA580C), Color(0xFF2A0D04), Color(0xFF5A1D08),
        xtreamCategoryId = "133"),
    Genre("standup", "Standup", "Standup", 0, // no TMDB equivalent
        "One mic, one room, all laughs.",
        Color(0xFFFACC15), Color(0xFF1A1407), Color(0xFF3A2E10),
        xtreamCategoryId = "204"),
)

// ── SERIES GENRES ───────────────────────────────────────────────────
// TMDB TV genre IDs. Some differ from movie genres (e.g. Action &
// Adventure is a combined TV-only genre). Xtream IDs map to the
// user's primary provider's series category list.
private val SERIES_GENRES_BASE = listOf(
    Genre("tv_action_adventure", "Action & Adventure", "Action & Adventure", 10759,
        "Serialised thrill-rides and sprawling quests.",
        Color(0xFFF97316), Color(0xFF3A0A04), Color(0xFF7F1D1D),
        xtreamCategoryId = "134"),
    Genre("tv_animation", "Animation", "Animation", 16,
        "Drawn universes that don't break for commercial.",
        Color(0xFFF472B6), Color(0xFF2D1040), Color(0xFF5B2180),
        xtreamCategoryId = "136"),
    Genre("tv_crime", "Crime", "Crime", 80,
        "Investigations, procedurals, and the ones who got away.",
        Color(0xFF60A5FA), Color(0xFF0B1220), Color(0xFF1E293B),
        xtreamCategoryId = "138"),
    Genre("tv_comedy", "Comedy", "Comedy", 35,
        "Laughs across 20-minute episodes — or 60.",
        Color(0xFFFACC15), Color(0xFF2A1E08), Color(0xFF7A5B0E),
        xtreamCategoryId = "137"),
    Genre("tv_documentary", "Documentary", "Documentary", 99,
        "Long-form real-life stories told across episodes.",
        Color(0xFF94A3B8), Color(0xFF0F172A), Color(0xFF334155),
        xtreamCategoryId = "139"),
    Genre("tv_drama", "Drama", "Drama", 18,
        "Season-long arcs, unforgettable characters.",
        Color(0xFFC084FC), Color(0xFF1E1B3A), Color(0xFF4C1D95),
        xtreamCategoryId = "140"),
    Genre("tv_mystery", "Mystery", "Mystery", 9648,
        "Every episode, one step closer to the answer.",
        Color(0xFF818CF8), Color(0xFF0B0A1F), Color(0xFF1E1A4D),
        xtreamCategoryId = "146"),
    Genre("tv_reality", "Reality", "Reality", 10764,
        "Unscripted chaos. Real drama, real people.",
        Color(0xFFFB7185), Color(0xFF2A0B1A), Color(0xFF5B1433),
        xtreamCategoryId = "147"),
    Genre("tv_scifi_fantasy", "Sci-Fi & Fantasy", "Sci-Fi & Fantasy", 10765,
        "Elsewhere, elsewhen, what if.",
        Color(0xFF22D3EE), Color(0xFF041824), Color(0xFF0C3A5C),
        xtreamCategoryId = "172"),
    Genre("tv_sitcoms", "Sitcoms", "Sitcoms", 0, // uses Comedy backdrop fallback
        "Laugh-track living rooms and misunderstandings.",
        Color(0xFFFBBF24), Color(0xFF2A1E08), Color(0xFF7A5B0E),
        xtreamCategoryId = "373"),
    Genre("tv_soap", "Soap", "Soap", 10766,
        "Daily drama, long-running families, endless twists.",
        Color(0xFFFB7185), Color(0xFF2A0B1A), Color(0xFF5B1433),
        xtreamCategoryId = "202"),
    Genre("tv_war_politics", "War & Politics", "War & Politics", 10768,
        "Power, strategy, and lines that get crossed.",
        Color(0xFFA16207), Color(0xFF1F140A), Color(0xFF4A2C10),
        xtreamCategoryId = "209"),
    Genre("tv_western", "Western", "Western", 37,
        "Prairies, pistols, and prestige TV.",
        Color(0xFFEA580C), Color(0xFF2A0D04), Color(0xFF5A1D08),
        xtreamCategoryId = "151"),
)

/**
 * Loads genre cards for the given kind (`movie` or `series`) with
 * TMDB backdrops fetched in the background. Same caching strategy as
 * `rememberDiscoveryCards` — reads the last-known-good backdrops from
 * SharedPreferences synchronously so cold start paints instantly.
 */
@Composable
fun rememberGenres(kind: String): List<Genre> {
    val ctx = LocalContext.current
    val base = if (kind == "series") SERIES_GENRES_BASE else MOVIE_GENRES_BASE

    // Seed state from cache on first composition so cards paint instantly.
    var genres by remember(kind) {
        mutableStateOf(
            base.map { g ->
                g.copy(
                    backdropUrl = DiscoveryCache.loadGenreBackdrop(ctx, kind, g.tmdbGenreId)
                )
            }
        )
    }

    LaunchedEffect(kind) {
        val freshMap = runCatching {
            TmdbService.backdropsForGenres(
                kind = kind,
                genreIds = base.map { it.tmdbGenreId }.filter { it > 0 },
            )
        }.getOrDefault(emptyMap())

        if (freshMap.isEmpty()) return@LaunchedEffect

        // Persist + enrich.
        freshMap.forEach { (genreId, url) ->
            DiscoveryCache.saveGenreBackdrop(ctx, kind, genreId, url)
        }

        genres = base.map { g ->
            g.copy(backdropUrl = freshMap[g.tmdbGenreId] ?: g.backdropUrl)
        }

        // Prefetch so scrolling is flicker-free.
        val loader = ctx.imageLoader
        genres.mapNotNull { it.backdropUrl }.forEach { url ->
            loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    return genres
}
