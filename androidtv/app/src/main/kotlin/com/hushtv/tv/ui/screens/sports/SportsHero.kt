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
    // v1.44.24 — Lite-aware auto-advance. Pro: 8 s rotation
    // through up to 5 hero items. Lite: hold on item 0, no
    // timer, no AnimatedContent crossfade work.
    val isLite = com.hushtv.tv.data.LocalIsLiteMode.current
    var idx by remember(visible.size) { mutableStateOf(0) }
    LaunchedEffect(visible.size, pinned, isLite) {
        if (isLite || pinned != null || visible.size <= 1) return@LaunchedEffect
        while (true) {
            delay(8_000)
            idx = (idx + 1) % visible.size
        }
    }
    val current = visible.getOrNull(idx.coerceIn(0, visible.size - 1)) ?: return

    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (isLite) {
            // Lite: render only the current hero, no crossfade.
            HeroBackdrop(current)
        } else {
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
    // v1.44.24 — Lite-aware Ken Burns. Pro: gentle 1.00→1.04
    // 24 s breath. Lite: static 1.00 (no zoom).
    val scale by com.hushtv.tv.ui.lite.rememberKenBurnsScale(
        label = "sports-kb-${h.id}",
        initialValue = 1.00f,
        targetValue = 1.04f,
        durationMs = 24_000,
    )
    Box(Modifier.fillMaxSize()) {
        if (!h.image.isNullOrBlank()) {
            // v1.44.9 — switched from ContentScale.Crop to .Fit so the
            // FULL team logo is visible. Crop was rendering the
            // top-half of the badge only on most TVs because logos
            // are square 512x512 and the hero box is ~16:9.
            AsyncImage(
                model = h.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 80.dp, top = 30.dp, bottom = 30.dp)
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
            // v1.44.5 — eyebrow + countdown now derived from server-
            // supplied [SportsHero.status], not just inferred from the
            // time delta. A 3-hour-old NHL game that's still going was
            // being rendered as "UPCOMING GAME · FINAL" because the
            // eyebrow was hardcoded "UPCOMING GAME" and the countdown
            // returned "FINAL" past the +2h mark — two contradictory
            // labels in the same row.
            val (eyebrow, eyebrowColor) = sportsEyebrow(h)
            val countdown = sportsCountdown(h)
            Box(
                Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .background(
                        eyebrowColor,
                        RoundedCornerShape(2.dp),
                    )
            )
            Spacer(Modifier.width(12.dp))
            Text(
                eyebrow,
                color = eyebrowColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                countdown,
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = Inter,
            )
            // Show live scores in the eyebrow row when available.
            if (!h.score_home.isNullOrBlank() && !h.score_away.isNullOrBlank()) {
                Spacer(Modifier.width(20.dp))
                Box(
                    Modifier
                        .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${h.score_away}  -  ${h.score_home}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontFamily = Inter,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            h.title,
            color = Color.White,
            // v1.44.17 — User feedback: "Timberwolves @ Spurs" was
            // wrapping to two lines and overlapping the right-side
            // team-logo backdrop. Dropped 44sp → 32sp and forced
            // maxLines = 1 so even the longest matchup labels stay
            // on a single, legible line. Lighter letterSpacing keeps
            // the glyphs tight without bleeding into the logo area.
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 36.sp,
            letterSpacing = 0.5.sp,
            fontFamily = Inter,
            maxLines = 1,
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
/**
 * Eyebrow label + accent colour for the hero card. Returns one of:
 *   PPV EVENT       (red)        — kind == "ppv"
 *   LIVE NOW        (red)        — status == "live"  OR  -5h < delta < +5h with no status
 *   FINAL           (slate-300)  — status == "final" OR delta < -5h
 *   UPCOMING GAME   (cyan)       — everything else
 */
fun sportsEyebrow(h: SportsHero): Pair<String, Color> {
    if (h.kind == "ppv") return "PPV EVENT" to Color(0xFFE10600)
    val status = h.status.lowercase()
    if (status == "live") return "LIVE NOW" to Color(0xFFEF4444)
    if (status == "final") return "FINAL" to Color(0xFFCBD5E1)
    val now = System.currentTimeMillis()
    val diffMs = h.start_utc - now
    if (diffMs in -5 * 3600_000L..5 * 3600_000L) {
        return "LIVE NOW" to Color(0xFFEF4444)
    }
    if (diffMs < -5 * 3600_000L) return "FINAL" to Color(0xFFCBD5E1)
    return "UPCOMING GAME" to Cyan
}

/**
 * Time-aware countdown text companion to [sportsEyebrow]. Returns:
 *   "Quarter / inning / period info" for live games (when known)
 *   "FINAL" for finished games
 *   "TONIGHT · 8:00 PM" / "TOMORROW · 7:30 PM" / "MAY 18 · 7:00 PM"
 *      for upcoming.
 */
fun sportsCountdown(h: SportsHero): String {
    val status = h.status.lowercase()
    if (status == "live") return "IN PROGRESS"
    if (status == "final") return "FINAL"
    val now = System.currentTimeMillis()
    val diffMs = h.start_utc - now
    // A reasonable "long" sport runs ~4h; treat anything within ±5h
    // of start as in-progress until the server tells us otherwise.
    if (diffMs in -5 * 3600_000L..5 * 3600_000L) return "IN PROGRESS"
    if (diffMs < -5 * 3600_000L) return "FINAL"
    return friendlyCountdown(h.start_utc)
}

fun friendlyCountdown(startUtcMs: Long): String {
    if (startUtcMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val diffMs = startUtcMs - now
    // Time-only fallback used by [sportsCountdown] for the upcoming case.
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
