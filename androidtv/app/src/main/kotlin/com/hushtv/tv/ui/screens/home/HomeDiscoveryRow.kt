package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * Two poster-free discovery tiles that sit pinned to the bottom of the
 * Home screen. The user asked for the cards to be clean and text-forward
 * (no busy poster mosaic inside the card), so the design here is a pure
 * typography-and-gradient tile:
 *
 *   • Accent vertical stripe on the left (cyan for movies / violet for series)
 *   • DISCOVER eyebrow micro-label
 *   • Massive Inter-Black title
 *   • Short subtitle
 *   • Pill CTA at the bottom that fills with the accent color on focus
 */
@Composable
fun HomeDiscoveryRow(
    cards: List<DiscoveryCard>,
    onFocusedCardChange: (DiscoveryCard) -> Unit,
    onCardClick: (DiscoveryCard) -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
) {
    if (cards.isEmpty()) return

    Column(
        Modifier.fillMaxWidth().padding(
            start = contentStartPadding,
            end = 48.dp,
            top = 16.dp,
            bottom = 72.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(Cyan, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "DISCOVER",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(14.dp))
        // Plain Row — exactly 2 cards, zero auto-scroll, zero bringIntoView.
        // The hero backdrop above never shifts regardless of focus.
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            cards.forEach { card ->
                DiscoveryCardView(
                    card = card,
                    onFocus = { onFocusedCardChange(card) },
                    onClick = { onCardClick(card) },
                )
            }
        }
    }
}

@Composable
private fun DiscoveryCardView(
    card: DiscoveryCard,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(16.dp)
    val accent = if (card.type == "series") Color(0xFFA78BFA) else Cyan

    // Gradient depth — focus brightens the fill and lifts the glow.
    val fillTop = if (focused) Color(0xFF182033) else Color(0xFF0D1322)
    val fillBottom = if (focused) Color(0xFF0A0F1C) else Color(0xFF070B14)
    val borderColor = if (focused) accent else accent.copy(alpha = 0.12f)
    val shadowElev = animateFloatAsState(
        targetValue = if (focused) 24f else 6f,
        animationSpec = tween(160),
        label = "discovery-card-shadow",
    )

    Box(
        Modifier
            .width(360.dp)
            .height(168.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            // No scale — keeps cards safely inside TV overscan.
            .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
            .focusable()
            .shadow(
                elevation = shadowElev.value.dp,
                shape = cardShape,
                ambientColor = accent,
                spotColor = accent,
            )
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = cardShape,
            )
            .clickableWithEnter(onClick),
    ) {
        // Accent stripe — thin vertical bar on the left edge.
        Box(
            Modifier
                .width(4.dp)
                .fillMaxSize()
                .background(accent),
        )

        // Ambient accent glow emanating from top-right (gives the card
        // depth without needing any poster art).
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to accent.copy(alpha = if (focused) 0.20f else 0.10f),
                        1.0f to Color.Transparent,
                        radius = 520f,
                    )
                )
        )

        // Content column — sits to the right of the stripe with generous padding.
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Top block: eyebrow + title + subtitle ────────────────
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (card.type == "series") Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        card.eyebrow,
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.5.sp,
                        fontFamily = Inter,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    card.title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 28.sp,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    card.subtitle,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = Inter,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Bottom row: count chip + CTA pill ────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (card.itemCount > 0) {
                    // Count chip — monochrome so it never fights with the accent.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1AFFFFFF))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(accent, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${card.itemCount} TITLES",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontFamily = Inter,
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                // CTA pill — fills with accent on focus; black text for contrast.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (focused) accent else Color(0x14FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Browse",
                        color = if (focused) Color(0xFF0A0F1C) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        fontFamily = Inter,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = if (focused) Color(0xFF0A0F1C) else Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
