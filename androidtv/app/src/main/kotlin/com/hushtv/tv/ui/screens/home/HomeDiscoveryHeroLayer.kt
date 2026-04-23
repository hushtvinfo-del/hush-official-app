package com.hushtv.tv.ui.screens.home

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.delay

/**
 * Discovery hero backdrop. The old version rendered a tilted, cascading
 * wall of posters that overflowed the screen and felt cluttered. This
 * replacement uses a single full-bleed backdrop (slowly auto-rotated from
 * the category's posters) with a heavy left-to-right veil so the title
 * column stays legible — everything stays inside the viewport, no scroll.
 */
@Composable
fun HomeDiscoveryHeroLayer(card: DiscoveryCard?) {
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

        // Rotate through the card's posters every 5s so the backdrop
        // feels alive without being busy. Fixed backdrop inside the
        // viewport — no tilted mosaic, no overflow.
        var posterIdx by remember(card.id) { mutableStateOf(0) }
        LaunchedEffect(card.id, card.posters) {
            if (card.posters.size <= 1) return@LaunchedEffect
            while (true) {
                delay(5000)
                posterIdx = (posterIdx + 1) % card.posters.size
            }
        }
        val posterUrl = card.posters.getOrNull(posterIdx)

        // Full-bleed backdrop — fills the Home content area cleanly.
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Left-to-right darkening veil so the text column is crisp. Heavy
        // on the left where the copy sits, fading to ~35% opacity on the
        // right so the backdrop still shows through.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF205080F),
                        0.35f to Color(0xCC05080F),
                        0.65f to Color(0x8005080F),
                        1.0f to Color(0x4005080F),
                    )
                )
        )

        // Bottom fade — softens the area where the card row sits so cards
        // visually separate from the backdrop.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.60f to Color.Transparent,
                        1.0f to Color(0xFF05080F),
                    )
                )
        )

        // Accent tint from the bottom-right (cyan or violet) — gives the
        // hero its signature colour temperature.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to accent.copy(alpha = 0.12f),
                        1.0f to Color.Transparent,
                        radius = 900f,
                    )
                )
        )

        // Text column — left 45% of the hero.
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 0.dp, top = 44.dp, end = 48.dp),
        ) {
            DiscoveryTitleBlock(card)
        }
    }
}

@Composable
private fun DiscoveryTitleBlock(card: DiscoveryCard) {
    Column(Modifier.fillMaxWidth(0.45f)) {
        val accent = if (card.type == "series") Color(0xFFA78BFA) else Cyan

        // Eyebrow row — icon + category label.
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

        // Title — Inter Black, sized so two lines fit above the card row.
        Text(
            card.title,
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 54.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        // Subtitle — short descriptive copy (2 lines max).
        Text(
            card.subtitle,
            color = Color(0xFFE2E8F0),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))

        // Count chip with fire icon.
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
