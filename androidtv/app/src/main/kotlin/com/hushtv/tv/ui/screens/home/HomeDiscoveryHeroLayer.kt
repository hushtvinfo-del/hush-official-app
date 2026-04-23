package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.delay

/**
 * Discovery hero backdrop. Uses TMDB hi-res landscape backdrops (w1280)
 * when available, falling back to Xtream posters only if TMDB didn't
 * match. Rotates through the art pool every 12 s with a fade crossfade
 * + Ken-Burns slow-pan so the screen feels alive without being busy.
 * Everything is bounded to the viewport — no scroll, no overflow.
 *
 * The BACKDROP is FULL-BLEED — rendered edge-to-edge so it passes
 * cleanly beneath the left sidebar with no seam. Only the text column
 * is offset by [contentStartPadding] (sidebar-avoiding inset).
 */
@Composable
fun HomeDiscoveryHeroLayer(
    card: DiscoveryCard?,
    contentStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (card == null) {
            // Empty state while data loads — soft cyan radial glow so the
            // screen doesn't look broken.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to Color(0xFF0E1422),
                            1.0f to Color(0xFF05080F),
                        )
                    )
            )
            return
        }

        val accent = if (card.type == "series") Color(0xFF8B5CF6) else Cyan
        val art = card.heroArt

        // Rotate through the art pool every 12 s — slow enough for the
        // Ken-Burns pan to be felt, fast enough to stay alive.
        var artIdx by remember(card.id) { mutableStateOf(0) }
        LaunchedEffect(card.id, art) {
            if (art.size <= 1) return@LaunchedEffect
            while (true) {
                delay(12_000)
                artIdx = (artIdx + 1) % art.size
            }
        }
        val currentArt = art.getOrNull(artIdx)

        // ── Full-bleed backdrop with Ken-Burns + fade crossfade ──
        if (currentArt != null) {
            AnimatedContent(
                targetState = currentArt,
                transitionSpec = {
                    // 1.8 s soft crossfade between posters
                    (fadeIn(tween(1800, easing = LinearEasing)) togetherWith
                        fadeOut(tween(1800, easing = LinearEasing)))
                },
                label = "discovery-backdrop-crossfade",
            ) { url ->
                KenBurnsBackdrop(url = url, panIndex = artIdx)
            }
        }

        // Left-to-right darkening veil — now MUCH lighter so the
        // backdrop colours punch through like the Genres page. Text
        // column still stays readable thanks to a subtle drop shadow.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xB3000814),
                        0.35f to Color(0x66000814),
                        0.70f to Color(0x1A000814),
                        1.0f to Color(0x00000814),
                    )
                )
        )

        // Bottom fade — lighter than before so the backdrop breathes.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.70f to Color.Transparent,
                        1.0f to Color(0xD905080F),
                    )
                )
        )

        // Accent tint from the bottom-right (cyan or violet) — brighter
        // now to give the brighter backdrop a warm glow.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to accent.copy(alpha = 0.18f),
                        1.0f to Color.Transparent,
                        radius = 1100f,
                    )
                )
        )

        // Text column — offset past the sidebar. Positioned high so the
        // title + subtitle + count chip block has plenty of empty space
        // between itself and the card row below.
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding, top = 32.dp, end = 48.dp),
        ) {
            DiscoveryTitleBlock(card)
        }
    }
}

/**
 * Single Ken-Burns backdrop cell — a SCALE-ONLY slow pulse that keeps
 * the image mathematically centered at all times. We deliberately
 * dropped all x/y translation: any translation risks exposing an edge
 * regardless of how much buffer you bake in, and the user wants the
 * hero to stay perfectly full-bleed on every side. The scale range
 * (1.06 → 1.12 over 22 s linear loop) still gives the poster a subtle
 * cinematic breath without ever shifting off-centre.
 */
@Composable
private fun KenBurnsBackdrop(url: String, panIndex: Int) {
    val transition = rememberInfiniteTransition(label = "ken-burns-$panIndex")
    val scale by transition.animateFloat(
        initialValue = 1.06f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
        ),
        label = "ken-burns-scale",
    )
    val ctx = LocalContext.current
    // Request with aggressive caching + crossfade disabled (we're doing
    // our own crossfade at the AnimatedContent level).
    val request = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // No translation — keeping the image perfectly centred
                // guarantees every edge is covered at every frame.
            },
    )
}

@Composable
private fun DiscoveryTitleBlock(card: DiscoveryCard) {
    // Widened to 58% so "Latest Movies" (wider than "Latest Series")
    // always fits on a single line at the 48 sp Inter Black weight.
    Column(Modifier.fillMaxWidth(0.58f)) {
        val accent = if (card.type == "series") Color(0xFFA78BFA) else Cyan

        // Eyebrow row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (card.type == "series") Icons.Default.Tv else Icons.Default.Movie,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.width(14.dp).height(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                card.eyebrow,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Title — Inter Black, single line. Drop shadow helps the
        // white type stay crisp against the now-brighter backdrop.
        Text(
            card.title,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 52.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xCC000000),
                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                    blurRadius = 14f,
                )
            ),
        )
        Spacer(Modifier.height(12.dp))

        Text(
            card.subtitle,
            color = Color(0xFFF1F5F9),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xAA000000),
                    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                    blurRadius = 8f,
                )
            ),
        )
        Spacer(Modifier.height(14.dp))

        if (card.itemCount > 0) {
            Surface(
                color = Color(0x26FFFFFF),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.width(12.dp).height(12.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${card.itemCount} TITLES",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = Inter,
                    )
                }
            }
        }
    }
}
