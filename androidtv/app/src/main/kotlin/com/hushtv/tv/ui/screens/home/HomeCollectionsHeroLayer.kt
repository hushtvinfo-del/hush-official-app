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
 * Collections hero — full-bleed backdrop of the focused franchise with
 * a big franchise name + tagline on the left column. Ken-Burns scale
 * pulse + 700 ms crossfade between franchises.
 */
@Composable
fun HomeCollectionsHeroLayer(
    coll: MovieCollection?,
    contentStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (coll == null) {
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
            targetState = coll,
            transitionSpec = {
                (fadeIn(tween(700, easing = LinearEasing)) togetherWith
                    fadeOut(tween(700, easing = LinearEasing)))
            },
            label = "collections-hero-crossfade",
        ) { c ->
            CollectionBackdrop(c)
        }

        // ── Darkening veils ──
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
            CollectionHeroCopy(coll)
        }
    }
}

@Composable
private fun CollectionBackdrop(coll: MovieCollection) {
    val backdrop = coll.backdropUrl
    // v1.44.24 — Lite-aware Ken Burns. Pro: same 22 s tween.
    // Lite: static 1.06 zoom.
    val scale by com.hushtv.tv.ui.lite.rememberKenBurnsScale(
        label = "coll-kb-${coll.tmdbCollectionId}",
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
            // Fallback — accent-tinted gradient.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0.0f to coll.accent.copy(alpha = 0.45f),
                            1.0f to Color(0xFF05080F),
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to coll.accent.copy(alpha = 0.3f),
                            1.0f to Color.Transparent,
                            radius = 900f,
                        )
                    )
            )
        }
    }
}

@Composable
private fun CollectionHeroCopy(coll: MovieCollection) {
    Column(Modifier.fillMaxWidth(0.58f)) {
        // Eyebrow — "FRANCHISE" in accent.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 14.dp)
                    .background(coll.accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "FRANCHISE",
                color = coll.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Massive franchise name — hard-capped to 1 line so the hero
        // copy never expands tall enough to overlap the card row
        // pinned at the bottom of the page.
        Text(
            coll.displayName,
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 48.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            coll.tagline,
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        // "WATCH IN ORDER" chip — signals the chronological sort.
        Row(
            Modifier
                .height(26.dp)
                .background(Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(coll.accent, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "WATCH IN ORDER",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontFamily = Inter,
            )
        }
    }
}
