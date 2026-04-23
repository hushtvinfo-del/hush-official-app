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
import com.hushtv.tv.data.TmdbService

/**
 * A single streaming service tile. Logos are fetched from TMDB's
 * `/watch/providers` API so there are NO bundled asset files — the
 * logos always match whatever TMDB is currently serving, and they load
 * on demand via Coil (cached after first fetch).
 *
 * [brandTop] / [brandBottom] define the tile's vertical gradient fill;
 * [accent] is used for the focus ring, hero accents, and CTA glow; and
 * [searchKeyword] is the token passed to the Xtream browse screen's
 * `initialCategoryName` so clicking the card deep-links into any
 * Xtream category whose name contains that token.
 */
data class StreamingService(
    val id: String,
    val displayName: String,
    val searchKeyword: String,
    val tmdbProviderId: Int,
    val brandTop: Color,
    val brandBottom: Color,
    val accent: Color,
    // Populated asynchronously from TMDB. Null while loading.
    val logoUrl: String? = null,
)

/** Hand-curated brand palette. Order matches the user's spec. */
private val MOVIE_SERVICES_BASE = listOf(
    StreamingService(
        id = "amc",
        displayName = "AMC+",
        searchKeyword = "AMC",
        tmdbProviderId = 526,
        brandTop = Color(0xFF2A0A0A),
        brandBottom = Color(0xFF8C1515),
        accent = Color(0xFFE53935),
    ),
    StreamingService(
        id = "appletv",
        displayName = "Apple TV+",
        searchKeyword = "Apple TV",
        tmdbProviderId = 350,
        brandTop = Color(0xFF0B0B0F),
        brandBottom = Color(0xFF1C1C24),
        accent = Color(0xFFEAEAEA),
    ),
    StreamingService(
        id = "crave",
        displayName = "CRAVE / STARZ",
        searchKeyword = "STARZ",
        tmdbProviderId = 43,
        brandTop = Color(0xFF120202),
        brandBottom = Color(0xFF4C0A0A),
        accent = Color(0xFFE50914),
    ),
    StreamingService(
        id = "disney",
        displayName = "Disney+",
        searchKeyword = "Disney",
        tmdbProviderId = 337,
        brandTop = Color(0xFF050E2B),
        brandBottom = Color(0xFF0E2B70),
        accent = Color(0xFF00C2FF),
    ),
    StreamingService(
        id = "netflix",
        displayName = "Netflix",
        searchKeyword = "Netflix",
        tmdbProviderId = 8,
        brandTop = Color(0xFF0A0A0A),
        brandBottom = Color(0xFF2C0404),
        accent = Color(0xFFE50914),
    ),
    StreamingService(
        id = "paramount",
        displayName = "Paramount+",
        searchKeyword = "Paramount",
        tmdbProviderId = 531,
        brandTop = Color(0xFF001C48),
        brandBottom = Color(0xFF0064FF),
        accent = Color(0xFF3BA0FF),
    ),
    StreamingService(
        id = "prime",
        displayName = "Prime Video",
        searchKeyword = "Prime",
        tmdbProviderId = 9,
        brandTop = Color(0xFF00050D),
        brandBottom = Color(0xFF012040),
        accent = Color(0xFF00A8E1),
    ),
)

/**
 * Enriches the base service list with TMDB logo URLs fetched once per
 * kind. Also warms Coil's image cache so the logos render instantly on
 * first paint. Returns the base list immediately (with logoUrl=null) so
 * the UI never blocks waiting for TMDB.
 */
@Composable
fun rememberStreamingServices(kind: String): List<StreamingService> {
    val ctx = LocalContext.current
    var services by remember(kind) { mutableStateOf(MOVIE_SERVICES_BASE) }

    LaunchedEffect(kind) {
        val logos = runCatching {
            TmdbService.watchProviderLogos(kind)
        }.getOrDefault(emptyMap())

        if (logos.isEmpty()) return@LaunchedEffect

        services = MOVIE_SERVICES_BASE.map { s ->
            s.copy(logoUrl = logos[s.tmdbProviderId])
        }

        // Warm the Coil disk cache so the logos paint instantly.
        val loader = ctx.imageLoader
        services.mapNotNull { it.logoUrl }.forEach { url ->
            loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    return services
}
