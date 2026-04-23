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
 * A single movie-release-year card.
 *
 * [searchKeyword] matches the Xtream category exactly (`MOVIES - 2026`
 * etc). [backdropUrl] is populated async from TMDB using
 * `/discover/movie?primary_release_year=YYYY&sort_by=popularity.desc`.
 */
data class MovieYear(
    val year: Int,
    val searchKeyword: String,
    val tagline: String,
    val accent: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val backdropUrl: String? = null,
    // Exact Xtream category ID on the user's primary provider.
    val xtreamCategoryId: String? = null,
)

// Hand-curated palette + copy per year. Three entries matches exactly
// what the user's Xtream library has: MOVIES - 2026 / 2025 / 2024.
private val MOVIE_YEARS_BASE = listOf(
    MovieYear(
        year = 2026,
        searchKeyword = "MOVIES - 2026",
        tagline = "Fresh releases from the current year.",
        accent = Color(0xFF22D3EE),
        gradientTop = Color(0xFF051B2E),
        gradientBottom = Color(0xFF083A5E),
        xtreamCategoryId = "475",
    ),
    MovieYear(
        year = 2025,
        searchKeyword = "MOVIES - 2025",
        tagline = "The biggest blockbusters of 2025.",
        accent = Color(0xFFF97316),
        gradientTop = Color(0xFF2A1204),
        gradientBottom = Color(0xFF5A2908),
        xtreamCategoryId = "414",
    ),
    MovieYear(
        year = 2024,
        searchKeyword = "MOVIES - 2024",
        tagline = "A year of hits and instant classics.",
        accent = Color(0xFFC084FC),
        gradientTop = Color(0xFF1B0B3A),
        gradientBottom = Color(0xFF3D1769),
        xtreamCategoryId = "325",
    ),
)

/**
 * Loads year cards with TMDB backdrops fetched async. Same
 * cache-first strategy as Discovery + Genres: read last-known-good
 * backdrops from SharedPreferences synchronously for instant cold
 * start, refresh in background.
 */
@Composable
fun rememberMovieYears(): List<MovieYear> {
    val ctx = LocalContext.current

    var years by remember {
        mutableStateOf(
            MOVIE_YEARS_BASE.map { y ->
                y.copy(backdropUrl = DiscoveryCache.loadYearBackdrop(ctx, y.year))
            }
        )
    }

    LaunchedEffect(Unit) {
        val freshMap = runCatching {
            TmdbService.backdropsForYears(MOVIE_YEARS_BASE.map { it.year })
        }.getOrDefault(emptyMap())

        if (freshMap.isEmpty()) return@LaunchedEffect

        freshMap.forEach { (yr, url) -> DiscoveryCache.saveYearBackdrop(ctx, yr, url) }

        years = MOVIE_YEARS_BASE.map { y ->
            y.copy(backdropUrl = freshMap[y.year] ?: y.backdropUrl)
        }

        val loader = ctx.imageLoader
        years.mapNotNull { it.backdropUrl }.forEach { url ->
            loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    return years
}
