@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.sports

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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.hushtv.tv.data.sports.SportsHero
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.delay

/**
 * Cinematic hero for the Sports home page. Crossfades through up to 5
 * upcoming hero items (mix of PPV events + top games). Each shows:
 *   • Large team-badge / poster on the right
 *   • Channel chip & countdown on the left (most legible info first)
 *   • Title + subtitle
 *
 * Auto-advances every 8 s. Pauses if a single item is supplied so an
 * embedded "focused-game" pin can drive it from the cards rail.
 */
@Composable
fun SportsHeroLayer(
    heroItems: List<SportsHero>,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    pinned: SportsHero? = null,
) {
    if (heroItems.isEmpty() && pinned == null) {
        SportsHeroPlaceholder()
        return
    }
    val visible = pinned?.let { listOf(it) } ?: heroItems.take(5)
    var idx by remember(visible.size) { mutableStateOf(0) }
    LaunchedEffect(visible.size, pinned) {
        if (pinned != null || visible.size <= 1) return@LaunchedEffect
        while (true) {
            delay(8_000)
            idx = (idx + 1) % visible.size
        }
    }
    val current = visible.getOrNull(idx.coerceIn(0, visible.size - 1)) ?: return

    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        // Crossfade between hero items.
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                fadeIn(tween(900, easing = LinearEasing)) togetherWith
                    fadeOut(tween(900, easing = LinearEasing))
            },
            label = "sports-hero-crossfade",
        ) { h ->
            HeroBackdrop(h)
        }

        // Darken+gradient overlays so copy is always legible.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Color(0xF205080F),
                    0.45f to Color(0xCC05080F),
                    0.8f to Color(0x4005080F),
                    1.0f to Color(0x0005080F),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1.0f to Color(0xFF05080F),
                )
            )
        )

        // Copy block — left aligned for natural reading flow.
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = contentStartPadding, top = 60.dp, end = 96.dp),
        ) {
            HeroCopy(current)
        }

        // Tiny pagination dots bottom-right (only when we're rotating).
        if (pinned == null && visible.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 80.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visible.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .size(if (i == idx) 22.dp else 7.dp, 7.dp)
                            .clip(CircleShape)
                            .background(if (i == idx) Cyan else Color(0x66FFFFFF))
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(h: SportsHero) {
    val transition = rememberInfiniteTransition(label = "sports-kb-${h.id}")
    val scale by transition.animateFloat(
        initialValue = 1.04f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "sports-kb-scale",
    )
    Box(Modifier.fillMaxSize()) {
        if (!h.image.isNullOrBlank()) {
            AsyncImage(
                model = h.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
        } else {
            // Fallback: cyan radial wash. Better than a blank black box.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF1E90FF).copy(alpha = 0.35f),
                        1.0f to Color(0xFF05080F),
                    )
                )
            )
        }
        // Bottom edge cyan glow tints the whole image with the brand.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.0f to Color(0xFF1E90FF).copy(alpha = 0.16f),
                    1.0f to Color.Transparent,
                    radius = 1100f,
                )
            )
        )
    }
}

@Composable
private fun HeroCopy(h: SportsHero) {
    Column(Modifier.fillMaxWidth(0.65f)) {
        // ── Eyebrow: kind + countdown ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .background(
                        if (h.kind == "ppv") Color(0xFFE10600) else Cyan,
                        RoundedCornerShape(2.dp),
                    )
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (h.kind == "ppv") "PPV EVENT" else "UPCOMING GAME",
                color = if (h.kind == "ppv") Color(0xFFE10600) else Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                friendlyCountdown(h.start_utc),
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            h.title,
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 60.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (!h.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                h.subtitle,
                color = Color(0xFFE2E8F0),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontFamily = Inter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Channel chip — big & legible per "easy for old people". ──
        if (!h.channel.isNullOrBlank()) {
            Row(
                Modifier
                    .background(Color(0xFFFFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "▶  WATCH ON  ",
                    color = Color(0xFF05080F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = Inter,
                )
                Text(
                    h.channel.uppercase(),
                    color = Color(0xFF05080F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = Inter,
                )
            }
        }
    }
}

@Composable
private fun SportsHeroPlaceholder() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                0.0f to Color(0xFF1E90FF).copy(alpha = 0.18f),
                1.0f to Color(0xFF05080F),
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "✦  PPV & LIVE SPORTS  ✦",
                color = Cyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Loading the schedule…",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter,
            )
        }
    }
}

/**
 * Returns a human-friendly relative-time label for a UTC ms timestamp.
 *   "LIVE NOW", "TONIGHT 8:30 PM", "TOMORROW 7:00 PM", "FRIDAY 9:00 PM",
 *   "MAY 18 · 7:00 PM"
 * Designed to be glanceable on a TV from the couch — matches the
 * "easy for grandfather" requirement.
 */
fun friendlyCountdown(startUtcMs: Long): String {
    if (startUtcMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val diffMs = startUtcMs - now
    if (diffMs <= -2 * 3600 * 1000L) return "FINAL"
    if (diffMs <= 3 * 3600 * 1000L && diffMs > -2 * 3600 * 1000L) return "LIVE NOW"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = startUtcMs }
    val today = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val dayDelta = (cal.get(java.util.Calendar.DAY_OF_YEAR) -
                    today.get(java.util.Calendar.DAY_OF_YEAR) +
                    365 * (cal.get(java.util.Calendar.YEAR) -
                           today.get(java.util.Calendar.YEAR)))
    val timePart = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        .format(java.util.Date(startUtcMs))
    return when (dayDelta) {
        0 -> "TONIGHT  ·  $timePart"
        1 -> "TOMORROW  ·  $timePart"
        in 2..6 -> {
            val day = java.text.SimpleDateFormat("EEEE", java.util.Locale.US)
                .format(java.util.Date(startUtcMs))
            "${day.uppercase()}  ·  $timePart"
        }
        else -> {
            val day = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                .format(java.util.Date(startUtcMs))
            "${day.uppercase()}  ·  $timePart"
        }
    }
}
