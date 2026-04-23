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
import androidx.compose.ui.draw.clip
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
 * Streaming-service hero layer — renders an immersive brand-coloured
 * backdrop for the CURRENTLY focused service tile. Fades between
 * service palettes as the user moves across the row.
 *
 *  • Full-bleed diagonal gradient using the service's brand colours
 *    with a radial accent glow from the bottom-right.
 *  • Centred-left copy block (kind label → huge service wordmark →
 *    tagline → category hint).
 *  • Giant translucent watermark logo in the right half pulses slowly
 *    via `graphicsLayer` for subtle cinematic breath.
 *  • 900 ms fade crossfade between services so switching feels smooth
 *    rather than snapping.
 */
@Composable
fun HomeStreamingServicesHeroLayer(
    service: StreamingService?,
    kindLabel: String,
    contentStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (service == null) {
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

        AnimatedContent(
            targetState = service,
            transitionSpec = {
                (fadeIn(tween(900, easing = LinearEasing)) togetherWith
                    fadeOut(tween(900, easing = LinearEasing)))
            },
            label = "streaming-hero-crossfade",
        ) { s ->
            BrandedHeroBackdrop(s)
        }

        // Left-column text stays stable across the crossfade (sits ON
        // TOP of the AnimatedContent so it doesn't flicker). Positioned
        // higher up so it never crowds the card row below.
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding, top = 8.dp, end = 48.dp),
        ) {
            BrandedHeroCopy(service)
        }
    }
}

@Composable
private fun BrandedHeroBackdrop(service: StreamingService) {
    // Diagonal brand gradient + radial accent glow.
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0.0f to service.brandTop,
                        1.0f to service.brandBottom,
                    )
                )
        )
        // Bottom-right radial accent glow.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to service.accent.copy(alpha = 0.28f),
                        0.6f to service.accent.copy(alpha = 0.06f),
                        1.0f to Color.Transparent,
                        radius = 1200f,
                    )
                )
        )

        // Giant translucent watermark logo in the right half — subtle
        // cinematic flourish. Pulses slowly via graphicsLayer alpha.
        val transition = rememberInfiniteTransition(label = "wm-pulse-${service.id}")
        val alpha by transition.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
            ),
            label = "wm-alpha",
        )
        val scale by transition.animateFloat(
            initialValue = 1.00f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
            ),
            label = "wm-scale",
        )
        val logoUrl = service.logoUrl
        if (logoUrl != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(end = 100.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(width = 440.dp, height = 260.dp)
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }

        // Left-to-right dark veil so the text column stays legible.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xCC05080F),
                        0.45f to Color(0x6605080F),
                        1.0f to Color.Transparent,
                    )
                )
        )

        // Bottom fade — separates the hero from the card row below.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1.0f to Color(0xFF05080F),
                    )
                )
        )
    }
}

@Composable
private fun BrandedHeroCopy(service: StreamingService) {
    Column(Modifier.fillMaxWidth(0.58f)) {
        // Service name — big, confident. Single-line enforced.
        // No red eyebrow / kind label here (the row header already says
        // "STREAMING SERVICES · MOVIES" / "...SERIES").
        Text(
            service.displayName,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 52.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        // Tagline — short, brand-neutral. Single-line.
        Text(
            "Browse all ${service.displayName} titles in your library.",
            color = Color(0xFFE2E8F0),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))

        // Brand badge — translucent pill with service initial-letter
        // in the accent colour. Gives the hero a grounded left-anchor.
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x26FFFFFF))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(service.accent, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "TAP TO OPEN",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontFamily = Inter,
            )
        }
    }
}
