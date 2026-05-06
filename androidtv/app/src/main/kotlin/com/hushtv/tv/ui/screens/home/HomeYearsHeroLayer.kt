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
import com.hushtv.tv.ui.theme.Inter

/**
 * Movies-by-Year hero. Full-bleed crossfading backdrop per focused
 * year with a big year label + tagline on the left column.
 */
@Composable
fun HomeYearsHeroLayer(
    year: MovieYear?,
    contentStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (year == null) {
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

        // Backdrop layer — crossfades between years.
        AnimatedContent(
            targetState = year,
            transitionSpec = {
                (fadeIn(tween(700, easing = LinearEasing)) togetherWith
                    fadeOut(tween(700, easing = LinearEasing)))
            },
            label = "years-hero-crossfade",
        ) { y ->
            YearBackdrop(y)
        }

        // Darkening veils (same pattern as other heroes).
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

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding, top = 72.dp, end = 48.dp),
        ) {
            YearHeroCopy(year)
        }
    }
}

@Composable
private fun YearBackdrop(year: MovieYear) {
    val backdrop = year.backdropUrl
    // v1.44.24 — Lite-mode aware Ken Burns. In Pro the helper
    // returns a normal infinite-repeating tween (1.06 → 1.12,
    // 22 s) — same as before. In Lite it returns a static 1.06,
    // so the backdrop is just a still slightly-zoomed image.
    val scale by com.hushtv.tv.ui.lite.rememberKenBurnsScale(
        label = "year-kb-${year.year}",
        initialValue = 1.06f,
        targetValue = 1.12f,
        durationMs = 22_000,
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
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0.0f to year.gradientTop,
                            1.0f to year.gradientBottom,
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to year.accent.copy(alpha = 0.25f),
                            1.0f to Color.Transparent,
                            radius = 900f,
                        )
                    )
            )
        }
    }
}

@Composable
private fun YearHeroCopy(year: MovieYear) {
    Column(Modifier.fillMaxWidth(0.58f)) {
        // Eyebrow — "RELEASE YEAR" in accent.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 14.dp)
                    .background(year.accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "RELEASE YEAR",
                color = year.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Massive year — 72 sp Inter Black (bigger than other heroes
        // since a 4-digit year is short and deserves the weight).
        Text(
            "Movies · ${year.year}",
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 54.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            year.tagline,
            color = Color(0xFFE2E8F0),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
