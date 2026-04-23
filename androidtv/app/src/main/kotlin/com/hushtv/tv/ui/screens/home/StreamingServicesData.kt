package com.hushtv.tv.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest

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
 * Hand-picked official logo URLs, provided by the user. Overrides
 * whatever TMDB returns for these providers — gives us full control
 * over image quality, cropping, and background transparency.
 */
private val CUSTOM_LOGO_URLS = mapOf(
    "amc" to "https://raw.githubusercontent.com/tv-logo/tv-logos/refs/heads/main/countries/united-states/amc-us.png",
    "appletv" to "https://clipart-library.com/new_gallery/456504_apple-tv-logo-png.png",
    "crave" to "https://raw.githubusercontent.com/tv-logo/tv-logos/refs/heads/main/countries/canada/crave-1-ca.png",
    "disney" to "https://github.com/tv-logo/tv-logos/blob/main/countries/united-states/disney-plus-us.png?raw=true",
    "netflix" to "https://static.vecteezy.com/system/resources/previews/017/396/804/non_2x/netflix-mobile-application-logo-free-png.png",
    "paramount" to "https://github.com/tv-logo/tv-logos/blob/main/countries/france/paramount-channel-fr.png?raw=true",
    "prime" to "https://logodownload.org/wp-content/uploads/2018/07/prime-video-logo-0.png",
)

/**
 * Returns the 7 services with their custom logo URLs applied immediately
 * (no async wait for TMDB since we have hand-picked high-res images).
 * Also warms the Coil disk cache so logos paint flicker-free.
 */
@Composable
fun rememberStreamingServices(kind: String): List<StreamingService> {
    val ctx = LocalContext.current
    val services = remember(kind) {
        MOVIE_SERVICES_BASE.map { s ->
            s.copy(logoUrl = CUSTOM_LOGO_URLS[s.id])
        }
    }
    LaunchedEffect(services) {
        val loader = ctx.imageLoader
        services.mapNotNull { it.logoUrl }.forEach { url ->
            loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }
    return services
}
