package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * Two big poster-collage cards sitting at the bottom of the hero area —
 * Latest Movies / Latest Series. Each card:
 *   • Shows a 4-tile grid of posters as its visual content
 *   • Has an overlaid title + tag chip
 *   • On focus: lifts with cyan glow, updates the parent hero backdrop
 *
 * Clicking a card deep-links into Movies or Series with the target category
 * pre-selected (via the `category` nav query arg).
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
            top = 20.dp,
            bottom = 20.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .background(Cyan, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "DISCOVER",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            items(cards, key = { it.id }) { card ->
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
    val cardShape = RoundedCornerShape(14.dp)
    val accent = if (card.type == "series") Color(0xFFA78BFA) else Cyan
    val shadowColor = if (focused) accent else Color.Black
    val shadowElevation = if (focused) 18.dp else 4.dp

    Column(
        Modifier
            .width(420.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            // No scale — keeps cards safely inside TV overscan.
            .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .shadow(
                    elevation = shadowElevation,
                    shape = cardShape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                )
                .clip(cardShape)
                .background(Color(0xFF0B1020)),
        ) {
            // 4-poster mosaic fills the card's right half
            PosterQuad(posters = card.posters.take(4))

            // Left-to-right veil so the overlay text is crisp
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color(0xF00B1020),
                            0.55f to Color(0x990B1020),
                            1.0f to Color(0x330B1020),
                        )
                    )
            )

            if (!focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x33000000))
                )
            }

            // Content column — left half
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (card.type == "series") Icons.Default.Tv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            card.eyebrow,
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.4.sp,
                            fontFamily = Inter,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        card.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 30.sp,
                        fontFamily = Inter,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (card.itemCount > 0) {
                        Text(
                            "${card.itemCount} titles",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Inter,
                        )
                    }
                }
                // Call-to-action
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (focused) accent else Color(0x26FFFFFF),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Browse",
                                color = if (focused) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Inter,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = if (focused) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2×2 poster grid that fills the right half of a Discovery card. Each cell
 * clipped to match the card's rounded corners; missing posters show a dark
 * placeholder so the card is never visually broken.
 */
@Composable
private fun PosterQuad(posters: List<String>) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 200.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PosterTile(posters.getOrNull(0), Modifier.weight(1f))
            PosterTile(posters.getOrNull(1), Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PosterTile(posters.getOrNull(2), Modifier.weight(1f))
            PosterTile(posters.getOrNull(3), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PosterTile(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1422)),
    ) {
        url?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
