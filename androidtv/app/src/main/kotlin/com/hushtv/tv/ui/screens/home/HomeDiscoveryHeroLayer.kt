package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter

/**
 * Cinematic hero layer for the Discovery section. When a Discovery card is
 * focused, this layer paints a full-viewport backdrop made of a tilted
 * poster mosaic on the right half + a massive title block on the left.
 *
 * Designed to feel like the landing page of a premium streaming service —
 * no single backdrop image needed; we build it dynamically from the user's
 * own library.
 */
@Composable
fun HomeDiscoveryHeroLayer(card: DiscoveryCard?) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        if (card == null) {
            // Empty state while data is loading — soft cyan radial glow so
            // the screen doesn't look broken.
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

        // Right-half tilted poster mosaic
        PosterMosaic(
            posters = card.posters,
            tintColor = if (card.type == "series") Color(0xFF8B5CF6) else Cyan,
        )

        // Left-to-right darkening veil so the text column is readable
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF205080F),
                        0.35f to Color(0xCC05080F),
                        0.6f to Color(0x9905080F),
                        1.0f to Color(0x3305080F),
                    )
                )
        )
        // Bottom fade — softens the area where the card row sits
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

        // Text column
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
    // 42% of content width — the card row below spans ~0–720 dp (two 340 dp
    // cards + 16 dp gap), which is ~60% of a typical 1200 dp content area,
    // so the title column must stay inside the left 40% to not visually
    // overlap the cards.
    Column(Modifier.fillMaxWidth(0.42f)) {
        // Cyan (or violet for series) eyebrow with icon
        Row(verticalAlignment = Alignment.CenterVertically) {
            val accent = if (card.type == "series") Color(0xFFA78BFA) else Cyan
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

        // Title — Inter Black, sized so 2 lines fit comfortably above the
        // card row even on 720p TVs.
        Text(
            card.title,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 50.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        // Subtitle (2 lines max)
        Text(
            card.subtitle,
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))

        // Count chip
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

/**
 * Builds the tilted poster wall that sits on the right ~55% of the hero.
 * Three staggered columns rotated slightly off-axis, each fading out at the
 * edges. Gives the hero a premium "wall of posters" feel without any
 * asset pipeline work.
 */
@Composable
private fun PosterMosaic(posters: List<String>, tintColor: Color) {
    if (posters.isEmpty()) return

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Slight global rotation so the columns feel organic
                rotationZ = -6f
            },
    ) {
        val cols = 3
        // Offsets per column — creates the staggered cascade
        val offsetsY = listOf(-80.dp, 60.dp, -40.dp)
        Row(
            Modifier
                .fillMaxSize()
                .offset(x = 400.dp, y = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(cols) { colIdx ->
                PosterColumn(
                    posters = posters.drop(colIdx).filterIndexed { i, _ -> i % cols == 0 },
                    yOffset = offsetsY[colIdx % offsetsY.size],
                )
            }
        }

        // Tint glow emanating from the bottom-right corner (cyan/violet)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to tintColor.copy(alpha = 0.14f),
                        1.0f to Color.Transparent,
                        radius = 900f,
                    )
                )
        )
    }
}

@Composable
private fun PosterColumn(posters: List<String>, yOffset: androidx.compose.ui.unit.Dp) {
    Column(
        Modifier
            .width(180.dp)
            .fillMaxHeight()
            .offset(y = yOffset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Render each poster twice so the column is visually full top→bottom
        val loop = (posters + posters).take(6)
        loop.forEach { url ->
            Box(
                Modifier
                    .width(180.dp)
                    .height(260.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0E1422)),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Darken posters slightly so the hero text remains dominant
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x40000000))
                )
            }
        }
    }
}
