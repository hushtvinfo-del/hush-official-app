@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.data.HushThemedLists
import com.hushtv.tv.data.ThemedList
import com.hushtv.tv.data.ThemedMatchCache
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * Horizontal rail of curated "Moods & Themes" tiles for the TV home
 * screen. Sits ABOVE the Decades row and routes each tap to
 * `themedetail/{playlistId}/{themeId}`. A trailing "See All" tile
 * opens the full themed catalog (`themes/{playlistId}`) when the
 * curated set exceeds [maxVisible].
 *
 * Mirrors the focus / scroll model of [HomeCollectionsRow]:
 *   • `focusGroup() + focusRestorer()` so D-pad Down from the side
 *     rail returns the user to the same card they last focused.
 *   • `onPreviewKeyEvent` consumes vertical D-pad presses that
 *     would otherwise break out of the row, routing them to the
 *     parent's `onUp/DownFromRow` callbacks.
 */
@Composable
fun HomeThemedRow(
    themes: List<ThemedList>,
    onFocusedThemeChange: (ThemedList) -> Unit,
    onThemeClick: (ThemedList) -> Unit,
    onSeeAllClick: () -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: FocusRequester? = null,
    onUpFromRow: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
    maxVisible: Int = 12,
) {
    if (themes.isEmpty()) return
    val visible = themes.take(maxVisible)
    val hasMore = themes.size > maxVisible

    // First-card direct-bind pattern (mirrors HomeContinueWatchingRow):
    // the rail's RIGHT-exit callback only lands reliably on a real
    // focusable card when `firstItemFocus` is bound to that card
    // directly. focusGroup() stays so intra-row LEFT/RIGHT doesn't
    // escape into the rail.

    // Subscribe to the cache snapshot so tiles upgrade their
    // backdrop from the hardcoded TMDB still to the user's matched
    // library poster as soon as the matcher resolves a theme.
    val matchSnapshot = ThemedMatchCache.snapshot

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
        // ── Row header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(Color(0xFFEC4899), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "MOODS & THEMES",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
            if (hasMore) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "${themes.size} CURATED LISTS",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = Inter,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            itemsIndexed(visible, key = { _, t -> t.id }) { idx, theme ->
                val matches = matchSnapshot[theme.id]
                ThemedCardView(
                    theme = theme,
                    libraryPosterUrl = matches?.firstOrNull()?.poster,
                    libraryMatchCount = matches?.size ?: 0,
                    onFocus = { onFocusedThemeChange(theme) },
                    onClick = { onThemeClick(theme) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                )
            }
            if (hasMore) {
                item(key = "themed_see_all") {
                    ThemedSeeAllCardView(
                        totalCount = themes.size,
                        onClick = onSeeAllClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemedCardView(
    theme: ThemedList,
    libraryPosterUrl: String?,
    libraryMatchCount: Int,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)

    val baseTop: Modifier = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier

    Box(
        baseTop
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
                Brush.verticalGradient(
                    listOf(
                        theme.accent.copy(alpha = 0.32f),
                        Color(0xFF05080F),
                    )
                )
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) theme.accent else theme.accent.copy(alpha = 0.22f),
                shape = cardShape,
            ),
    ) {
        // Backdrop — the hardcoded TMDB still ALWAYS paints first
        // (zero-network, zero-race-condition); when the library
        // matcher resolves we show the matched poster on top so the
        // tile reads as "this exists in your library" art instead
        // of generic editorial art.
        AsyncImage(
            model = libraryPosterUrl ?: theme.heroBackdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom-to-top darkening veil — keeps the title legible.
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

        // Focus accent radial wash.
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to theme.accent.copy(alpha = 0.24f),
                            1.0f to Color.Transparent,
                            radius = 420f,
                        )
                    )
            )
        }

        // ── Content: section/glyph chip top-right + title bottom-left ──
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            // Top-right section + glyph chip.
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
                        .background(theme.accent, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    theme.section.displayName.uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Top-left big glyph — keeps the tile recognisable even
            // before the matched poster lands.
            Text(
                theme.glyph,
                color = theme.accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
                modifier = Modifier.align(Alignment.TopStart),
            )

            // Bottom-left theme title + subtitle / count.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Text(
                    theme.title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 21.sp,
                    fontFamily = Inter,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (libraryMatchCount > 0) {
                        "$libraryMatchCount in your library"
                    } else {
                        theme.subtitle
                    },
                    color = if (libraryMatchCount > 0) theme.accent else Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ThemedSeeAllCardView(
    totalCount: Int,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)

    Box(
        Modifier
            .width(260.dp)
            .height(156.dp)
            .onFocusChanged { focused = it.isFocused }
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
                "ALL THEMES",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$totalCount curated lists",
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
