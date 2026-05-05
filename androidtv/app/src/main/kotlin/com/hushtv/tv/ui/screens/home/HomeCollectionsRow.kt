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
 * Horizontal rail of movie-collection (box-set) cards. Shows the top
 * [maxVisible] franchises inline + a "See All" tile at the end that
 * opens the full grid browser. Keeps the home row snappy regardless
 * of how many collections the catalog grows to.
 */
@Composable
fun HomeCollectionsRow(
    collections: List<MovieCollection>,
    onFocusedCollectionChange: (MovieCollection) -> Unit,
    onCollectionClick: (MovieCollection) -> Unit,
    onSeeAllClick: () -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: FocusRequester? = null,
    onUpFromRow: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
    maxVisible: Int = 10,
) {
    if (collections.isEmpty()) return
    val visible = collections.take(maxVisible)
    val hasMore = collections.size > maxVisible

    // CW pattern (mirrors HomeContinueWatchingRow line 222-244):
    // Plain Row + horizontalScroll, NOT LazyRow. LazyRow's virtualisation
    // breaks the focus tree such that the side-rail's RIGHT-exit cannot
    // reliably land on the first card.
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
            if (hasMore) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "${collections.size} FRANCHISES",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = Inter,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val scrollState = androidx.compose.foundation.rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            visible.forEachIndexed { idx, coll ->
                CollectionCardView(
                    coll = coll,
                    onFocus = { onFocusedCollectionChange(coll) },
                    onClick = { onCollectionClick(coll) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                )
            }
            if (hasMore) {
                SeeAllCardView(
                    totalCount = collections.size,
                    onFocus = { /* keep currently-focused hero */ },
                    onClick = onSeeAllClick,
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

    val cardBase: Modifier = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier

    Box(
        cardBase
            .width(260.dp)
            .height(156.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    if (focusRequester != null) {
                        com.hushtv.tv.util.HushTVNav.d(
                            "✓ Collection first card '${coll.id}' GAINED FOCUS (cyan ring on)"
                        )
                    }
                    onFocus()
                }
            }
            .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
            .focusable()
            .clickableWithEnter(onClick)
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


/**
 * Trailing "See All" tile — same dimensions as a franchise card but
 * visually different (accent border, arrow chip, uppercase CTA).
 * Leads to the full Collections grid.
 */
@Composable
private fun SeeAllCardView(
    totalCount: Int,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)

    Box(
        Modifier
            .width(260.dp)
            .height(156.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
            .focusable()
            .clickableWithEnter(onClick)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0B1220), Color(0xFF05080F))
                )
            )
            .border(
                width = if (focused) 2.5.dp else 1.5.dp,
                color = if (focused) Cyan else Cyan.copy(alpha = 0.45f),
                shape = cardShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SEE ALL",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$totalCount franchises",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .background(Cyan, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    "BROWSE  →",
                    color = Color(0xFF05080F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    fontFamily = Inter,
                )
            }
        }
    }
}
