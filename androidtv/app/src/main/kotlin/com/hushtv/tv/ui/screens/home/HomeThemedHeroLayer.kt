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
import com.hushtv.tv.data.ThemedList
import com.hushtv.tv.ui.theme.Inter

/**
 * Themes & Moods hero — full-bleed Ken-Burns backdrop of the focused
 * theme + a left-column block with the section eyebrow, big title,
 * subtitle, and a soft accent CTA chip.
 *
 * Mirrors the visual contract of [HomeCollectionsHeroLayer] and
 * [HomeYearsHeroLayer] so the home pager feels like one coherent
 * surface as the user vertical-scrolls between sections.
 */
@Composable
fun HomeThemedHeroLayer(
    theme: ThemedList?,
    contentStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (theme == null) {
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
            targetState = theme,
            transitionSpec = {
                (fadeIn(tween(700, easing = LinearEasing)) togetherWith
                    fadeOut(tween(700, easing = LinearEasing)))
            },
            label = "themed-hero-crossfade",
        ) { t ->
            ThemedBackdrop(t)
        }

        // ── Darkening veils — match the rest of the home pages ──
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
                .padding(start = contentStartPadding, top = 40.dp, end = 48.dp),
        ) {
            ThemedHeroCopy(theme)
        }
    }
}

@Composable
private fun ThemedBackdrop(theme: ThemedList) {
    val transition = rememberInfiniteTransition(label = "themed-kb-${theme.id}")
    val scale by transition.animateFloat(
        initialValue = 1.06f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
        ),
        label = "themed-kb-scale",
    )

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = theme.heroBackdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        // Accent radial wash to amplify the theme's identity colour.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to theme.accent.copy(alpha = 0.18f),
                        1.0f to Color.Transparent,
                        radius = 900f,
                    )
                )
        )
    }
}

@Composable
private fun ThemedHeroCopy(theme: ThemedList) {
    Column(Modifier.fillMaxWidth(0.58f)) {
        // Eyebrow — section name + accent bar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 14.dp)
                    .background(theme.accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                theme.section.displayName.uppercase(),
                color = theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(12.dp))

        Text(
            theme.title,
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 48.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            theme.subtitle,
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        // Glyph + curated-list chip.
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .height(26.dp)
                    .background(Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        theme.glyph,
                        color = theme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CURATED LIST",
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
