@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * Horizontal rail of genre cards. Each card is a compact landscape
 * tile (210 × 118 dp) with:
 *   • A full-bleed backdrop from TMDB (if available), otherwise the
 *     genre's signature gradient.
 *   • A bottom gradient darken veil so the label is always legible.
 *   • Genre name overlaid at the bottom.
 *   • A crisp genre-accent focus border.
 *
 * The label sits INSIDE the card (at the bottom) since the backdrop
 * dominates the visual and the label is a natural continuation of it.
 * This is different from Streaming Services (logo-above, name-below)
 * because the art-dominant treatment suits genres better.
 */
@Composable
fun HomeGenresRow(
    genres: List<Genre>,
    kindLabel: String,
    onFocusedGenreChange: (Genre) -> Unit,
    onGenreClick: (Genre) -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: FocusRequester? = null,
    onUpFromRow: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
) {
    if (genres.isEmpty()) return

    // v1.43.98 — focusRequester is wired DIRECTLY into the first
    // card's tvFocusable so requestFocus() lands on the cyan ring.
    Column(
        Modifier
            .focusGroup()
            .fillMaxWidth()
            .padding(
                start = contentStartPadding,
                end = 48.dp,
                top = 16.dp,
                bottom = 32.dp,
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> {
                        if (onUpFromRow != null) { onUpFromRow(); true } else false
                    }
                    Key.DirectionDown -> {
                        if (onDownFromRow != null) { onDownFromRow(); true } else false
                    }
                    else -> false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(Cyan, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                kindLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(14.dp))
        val scrollState = androidx.compose.foundation.rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            genres.forEachIndexed { idx, genre ->
                GenreCardView(
                    genre = genre,
                    onFocus = { onFocusedGenreChange(genre) },
                    onClick = { onGenreClick(genre) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                )
            }
        }
    }
}

@Composable
private fun GenreCardView(
    genre: Genre,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)

    Box(
        Modifier
            .width(210.dp)
            .height(118.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvFocusable(
                scaleOnFocus = 1f,
                shape = cardShape,
                focusRequester = focusRequester,
            )
            .clickableWithEnter(onClick)
            .clip(cardShape)
            .background(
                // Fallback gradient (also shows through if the backdrop
                // hasn't loaded yet).
                Brush.verticalGradient(
                    listOf(genre.gradientTop, genre.gradientBottom)
                )
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) genre.accent
                    else genre.accent.copy(alpha = 0.2f),
                shape = cardShape,
            ),
    ) {
        // Backdrop image (if available) — fills the card edge-to-edge.
        val backdrop = genre.backdropUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom-to-top darkening veil — keeps the label crisp over any
        // backdrop; fades to transparent at the top so the art still
        // reads cleanly.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color(0x4D000000),
                        1.0f to Color(0xE6000000),
                    )
                )
        )

        // Focus accent wash — tints the card in the genre's signature
        // colour on focus so the user instantly feels the brand.
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to genre.accent.copy(alpha = 0.22f),
                            1.0f to Color.Transparent,
                            radius = 360f,
                        )
                    )
            )
        }

        // Label — bottom-left, single line, Inter Black.
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                genre.displayName.uppercase(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                fontFamily = Inter,
            )
        }
    }
}
