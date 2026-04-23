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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter

/**
 * Genres hero — full-bleed backdrop that crossfades as the user moves
 * between genre cards. Uses the focused genre's TMDB backdrop (or its
 * signature gradient if no backdrop is available). Text column on the
 * left shows the genre name in massive Inter-Black + a one-line tagline.
 */
@Composable
fun HomeGenresHeroLayer(
    genre: Genre?,
    contentStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (genre == null) {
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

        // ── Backdrop layer (crossfaded) ──
        AnimatedContent(
            targetState = genre,
            transitionSpec = {
                (fadeIn(tween(700, easing = LinearEasing)) togetherWith
                    fadeOut(tween(700, easing = LinearEasing)))
            },
            label = "genres-hero-crossfade",
        ) { g ->
            GenreBackdrop(g)
        }

        // ── Darkening veils ──
        // Heavy left-to-right so the text column is crisp.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF205080F),
                        0.40f to Color(0xCC05080F),
                        0.70f to Color(0x8005080F),
                        1.0f to Color(0x4005080F),
                    )
                )
        )
        // Bottom fade to separate the hero from the card row below.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1.0f to Color(0xFF05080F),
                    )
                )
        )

        // ── Text column ──
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding, top = 72.dp, end = 48.dp),
        ) {
            GenreHeroCopy(genre)
        }
    }
}

@Composable
private fun GenreBackdrop(genre: Genre) {
    val backdrop = genre.backdropUrl

    // Slow scale breath for cinematic feel. No translation to avoid
    // ever exposing an edge.
    val transition = rememberInfiniteTransition(label = "genre-kb-${genre.id}")
    val scale by transition.animateFloat(
        initialValue = 1.06f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
        ),
        label = "genre-kb-scale",
    )

    Box(Modifier.fillMaxSize()) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        } else {
            // Fallback — genre gradient + radial accent glow.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0.0f to genre.gradientTop,
                            1.0f to genre.gradientBottom,
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to genre.accent.copy(alpha = 0.25f),
                            1.0f to Color.Transparent,
                            radius = 900f,
                        )
                    )
            )
        }
    }
}

@Composable
private fun GenreHeroCopy(genre: Genre) {
    Column(Modifier.fillMaxWidth(0.58f)) {
        // Genre name — massive, single line.
        Text(
            genre.displayName,
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 54.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        // Tagline — descriptive single line.
        Text(
            genre.tagline,
            color = Color(0xFFE2E8F0),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        // Accent chip — colored pill with the genre name's accent dot.
        Row(
            Modifier
                .height(26.dp)
                .padding(0.dp)
                .background(Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(genre.accent, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "OPEN CATEGORY",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontFamily = Inter,
            )
        }
    }
}
