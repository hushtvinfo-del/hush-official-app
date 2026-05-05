package com.hushtv.tv.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.hushtv.tv.data.HushDecadeYears

/**
 * A single decade card on the home rail.
 *
 * Field names are kept as the legacy "MovieYear" shape so the
 * existing [HomeYearsRow] / [HomeYearsHeroLayer] composables can
 * consume it without renaming hundreds of call-sites:
 *
 *   • [year]            → decade start year, e.g. 1990
 *   • [searchKeyword]   → display label, e.g. "1990s"
 *   • [tagline]         → decade subtitle, e.g. "Tarantino, Coens..."
 *   • [accent]          → decade accent colour
 *   • [gradientTop]     → fallback gradient when no backdrop loaded
 *   • [gradientBottom]  → fallback gradient bottom
 *   • [backdropUrl]     → hardcoded TMDB CDN URL of decade hero
 *   • [xtreamCategoryId]→ unused for decades, kept for compat (null)
 */
data class MovieYear(
    val year: Int,
    val searchKeyword: String,
    val tagline: String,
    val accent: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val backdropUrl: String? = null,
    val xtreamCategoryId: String? = null,
    /** Decade marketing title — "The Independent Boom" etc. */
    val title: String = searchKeyword,
)

/**
 * Ordered list of the 9 decade cards shown on the home page rail.
 * Source of truth = [HushDecadeYears.all]. Pure mapping, no network,
 * no caching needed — paints synchronously on the first frame.
 *
 * Order: NEWEST decade first (2020s → 1940s) so the first card
 * focused on entry is the most recent / most relevant era for
 * a typical library.
 */
@Composable
fun rememberMovieYears(): List<MovieYear> = remember {
    HushDecadeYears.all.reversed().map { decade ->
        MovieYear(
            year = decade.startYear,
            searchKeyword = decade.label,                  // "1990s"
            tagline = decade.subtitle,
            title = decade.title,
            accent = decade.accent,
            // Subtle dark gradient fallback derived from the decade
            // accent; only visible while the TMDB backdrop loads.
            gradientTop = Color(0xFF0B1220),
            gradientBottom = Color(0xFF05080F),
            backdropUrl = decade.heroBackdropUrl,
            xtreamCategoryId = null,
        )
    }
}
