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
 * Horizontal rail of movie-collection (box-set) cards — 20 items,
 * e.g. Star Wars, Harry Potter, Mission: Impossible. Uses larger
 * 260 × 156 dp landscape tiles so franchise backdrops read clearly
 * at TV distance. Each tile: full-bleed TMDB backdrop, franchise
 * name bottom-left in Inter Black, accent-tinted focus glow.
 */
@Composable
fun HomeCollectionsRow(
    collections: List<MovieCollection>,
    onFocusedCollectionChange: (MovieCollection) -> Unit,
    onCollectionClick: (MovieCollection) -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: FocusRequester? = null,
    onUpFromRow: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
) {
    if (collections.isEmpty()) return

    Column(
        Modifier
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
        // Row header.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(Cyan, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "MOVIE COLLECTIONS",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            itemsIndexed(collections, key = { _, c -> c.id }) { idx, coll ->
                CollectionCardView(
                    coll = coll,
                    onFocus = { onFocusedCollectionChange(coll) },
                    onClick = { onCollectionClick(coll) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                )
            }
        }
    }
}

@Composable
private fun CollectionCardView(
    coll: MovieCollection,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)

    val base: Modifier = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier

    Box(
        base
            .width(260.dp)
            .height(156.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
            .focusable()
            .clickableWithEnter(onClick)
            .shadow(
                elevation = if (focused) 22.dp else 5.dp,
                shape = cardShape,
                ambientColor = coll.accent,
                spotColor = coll.accent,
            )
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        coll.accent.copy(alpha = 0.32f),
                        Color(0xFF05080F),
                    )
                )
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) coll.accent else coll.accent.copy(alpha = 0.22f),
                shape = cardShape,
            ),
    ) {
        // Backdrop image (if available).
        val backdrop = coll.backdropUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom-to-top darken veil — keeps the title crisp over any
        // backdrop while letting the hero art breathe at the top.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x33000000),
                        0.50f to Color(0x80000000),
                        1.0f to Color(0xEB000000),
                    )
                )
        )

        // Focus accent wash — radial glow in the franchise's colour.
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to coll.accent.copy(alpha = 0.24f),
                            1.0f to Color.Transparent,
                            radius = 420f,
                        )
                    )
            )
        }

        // ── Content: FRANCHISE chip top-right + title bottom-left ──
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            // Top-right franchise tag.
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(coll.accent, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "FRANCHISE",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = Inter,
                )
            }

            // Bottom-left franchise name.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Text(
                    coll.displayName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 22.sp,
                    fontFamily = Inter,
                    maxLines = 2,
                )
            }
        }
    }
}
