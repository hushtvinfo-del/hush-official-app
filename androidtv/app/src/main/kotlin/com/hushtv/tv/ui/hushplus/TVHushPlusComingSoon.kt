package com.hushtv.tv.ui.hushplus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * Hush+ "Coming Soon" home-page section (read-only advertisement).
 *
 * Rendered as a full-height home-page panel beneath Discovery — replaces
 * the old standalone Hush+ menu route. Fits entirely in one viewport,
 * no scrolling, no buttons, no interaction except an invisible focus
 * anchor so D-pad up/down to neighbouring home pages still works on TV.
 *
 * Used by both the TV main menu and the Mobile MobileHushPlusScreen.
 * Layout adapts to screen width (TV row-of-4, mobile column-of-4).
 */
@Composable
fun TVHushPlusComingSoonSection(
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: () -> Unit,
) {
    // Invisible focus anchor at the top of this section so the existing
    // home-page Channel-Up / D-pad-Up handlers still receive key events
    // when the user lands here from neighbouring pages. We use an
    // onPreviewKeyEvent rather than a real interactive element because
    // this whole page is intentionally non-interactive (advertisement).
    LaunchedEffect(Unit) {
        runCatching { firstItemFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(firstItemFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> { onUpFromRow(); true }
                    Key.DirectionDown -> { onDownFromRow(); true }
                    else -> false
                }
            },
    ) {
        HushPlusComingSoonContent()
    }
}

/**
 * The visual payload — extracted so MobileHushPlusScreen can reuse it
 * inside a Scaffold without needing TV-only focus plumbing.
 */
@Composable
fun HushPlusComingSoonContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BgBlack,
                        Color(0xFF06101D),
                        Color(0xFF0B1F38),
                        BgBlack,
                    ),
                ),
            )
            .padding(horizontal = 48.dp, vertical = 36.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── COMING SOON eyebrow ────────────────────────────────
            Box(
                modifier = Modifier
                    .background(Cyan.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    "COMING SOON",
                    color = Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── Wordmark ──────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "hush",
                    color = TextPrimary,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )
                Text(
                    "+",
                    color = Cyan,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(6.dp))

            // ── Tagline ───────────────────────────────────────────
            Text(
                "The next chapter of HushTV — every kind of entertainment, one app.",
                color = TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 900.dp),
            )
            Spacer(Modifier.height(28.dp))

            // ── 4 addon pillars (row on TV, wraps on smaller) ─────
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PillarCard(
                    icon = Icons.Default.MovieFilter,
                    eyebrow = "FLAGSHIP",
                    title = "HushVOD+",
                    accent = Color(0xFF1E90FF),
                    points = listOf(
                        "Millions of movies & series",
                        "Every language, every region",
                        "4K · 1080p · 720p — your pick",
                        "No VPN, no geo blocks",
                    ),
                    modifier = Modifier.weight(1f),
                )
                PillarCard(
                    icon = Icons.Default.AutoStories,
                    eyebrow = "READ & LISTEN",
                    title = "HushBooks+",
                    accent = Color(0xFFF59E0B),
                    points = listOf(
                        "Millions of eBooks",
                        "Full audiobook library",
                        "Every language & genre",
                        "Instant access, no limits",
                    ),
                    modifier = Modifier.weight(1f),
                )
                PillarCard(
                    icon = Icons.Default.SportsEsports,
                    eyebrow = "RETRO GAMING",
                    title = "HushArcade+",
                    accent = Color(0xFFC026D3),
                    points = listOf(
                        "Classic arcade catalogue",
                        "Console emulation built-in",
                        "Couch-coop friendly",
                        "Game with your remote",
                    ),
                    modifier = Modifier.weight(1f),
                )
                PillarCard(
                    icon = Icons.Default.Subscriptions,
                    eyebrow = "AD-FREE",
                    title = "HushTube+",
                    accent = Color(0xFFEF4444),
                    points = listOf(
                        "YouTube without the ads",
                        "Background play & PiP",
                        "Sponsor-skip & chapters",
                        "Subscriptions follow you",
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Footer band: included with HushTV ─────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x661E90FF).copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Exclusively for HushTV members",
                    color = Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .size(width = 1.dp, height = 18.dp)
                        .background(Cyan.copy(alpha = 0.4f)),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Launches alongside your existing HushTV subscription — no extra signup.",
                    color = TextPrimary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun PillarCard(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    accent: Color,
    points: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceNavy.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        // Icon disc
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(accent.copy(alpha = 0.18f), RoundedCornerShape(50))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        // Eyebrow
        Text(
            eyebrow,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(4.dp))
        // Title
        Text(
            title,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(12.dp))
        // Feature bullet list
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            points.forEach { p ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .background(accent, RoundedCornerShape(50)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        p,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}
