package com.hushtv.tv.ui.screens.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.hushtv.tv.ui.theme.Inter

/**
 * Fixed hero layer for the Home screen. Lives BEHIND the scrollable content
 * so rows glide over it without the title / backdrop ever being cut off.
 *
 * Renders:
 *  • Full-viewport backdrop image (TMDB `original` size)
 *  • Left→right dark gradient so text column stays readable over artwork
 *  • Bottom vertical fade so card rows scrolling over the hero have depth
 *  • Title / meta / IMDb rating / description column in the upper-left
 *
 * When [entry] is null, paints a subtle dark surface so the home doesn't
 * look broken while TMDB hydrates (or when there are no entries at all).
 */
@Composable
fun HomeHeroLayer(entry: ContinueEntry?, contentStartPadding: androidx.compose.ui.unit.Dp = 64.dp) {
    Box(Modifier.fillMaxSize()) {
        if (entry?.backdropUrl != null) {
            AsyncImage(
                model = entry.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF050507)))
        }

        // Left-to-right darkening veil
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF2000000),
                        0.35f to Color(0xCC000000),
                        0.6f to Color(0x80000000),
                        1.0f to Color(0x00000000),
                    )
                )
        )
        // Bottom vertical fade — softens where the card row sits
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.0f to Color(0xFF050507),
                    )
                )
        )

        // Hero text column
        if (entry != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = contentStartPadding, top = 40.dp, end = 48.dp),
            ) {
                HeroTextBlock(entry)
            }
        }
    }
}

@Composable
private fun HeroTextBlock(entry: ContinueEntry) {
    Column(Modifier.fillMaxWidth(0.55f)) {
        Text(
            entry.progress.title,
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 48.sp,
            fontFamily = Inter,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        val metaParts = buildList {
            if (entry.progress.kind == "series") add("Series")
            else add("Movie")
            entry.genre?.let { add(it) }
            entry.year?.let { add(it) }
        }
        Text(
            metaParts.joinToString("  ·  "),
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Inter,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${entry.minutesLeft}M LEFT",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter,
                letterSpacing = 1.sp,
            )
            entry.ratingText?.let { rating ->
                Spacer(Modifier.width(14.dp))
                Surface(
                    color = Color(0xFFF5C518),
                    shape = RoundedCornerShape(3.dp),
                ) {
                    Text(
                        "IMDb",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    rating,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                )
            }
        }

        val overview = entry.tmdb?.overview
        if (!overview.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "\"$overview\"",
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = Inter,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
