@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
 * Horizontal rail of movie-release-year cards. Only 3 cards (2024,
 * 2025, 2026) so we can afford larger tiles (280 × 170 dp) for maximum
 * visual impact.
 */
@Composable
fun HomeYearsRow(
    years: List<MovieYear>,
    onFocusedYearChange: (MovieYear) -> Unit,
    onYearClick: (MovieYear) -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: FocusRequester? = null,
    onUpFromRow: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
) {
    if (years.isEmpty()) return

    // First-card direct-bind pattern (mirrors HomeContinueWatchingRow):
    // we deliberately AVOID Modifier.focusRequester(...).focusRestorer()
    // on the outer Column. The rail's RIGHT-exit callback only lands
    // reliably on a real focusable card when the requester is bound
    // to that card directly. focusGroup() stays so intra-row LEFT/RIGHT
    // doesn't escape into the rail.
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
                "MOVIES BY RELEASE YEAR",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(14.dp))
        // LazyRow with fixed-width cards so they look right
        // regardless of TV output resolution. Previous `Row +
        // weight(1f)` design assumed 1080p+ density — on Shield
        // output at 720p each card shrunk to ~80 dp wide which
        // forced the 48 sp year text + "MOVIES" chip to wrap
        // character-by-character (the rendering the user reported
        // as "decade cards corrupt"). With LazyRow + fixed width
        // they always render at intended size and the user can
        // scroll horizontally if there are more cards than fit.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 32.dp),
        ) {
            itemsIndexed(
                items = years,
                key = { _, y -> y.year },
            ) { idx, year ->
                YearCardView(
                    year = year,
                    modifier = Modifier.width(240.dp),
                    onFocus = { onFocusedYearChange(year) },
                    onClick = { onYearClick(year) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                )
            }
        }
    }
}

@Composable
private fun YearCardView(
    year: MovieYear,
    modifier: Modifier = Modifier,
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
            .then(modifier)
            .height(170.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
            .focusable()
            .clickableWithEnter(onClick)
            .clip(cardShape)
            .background(
                Brush.verticalGradient(listOf(year.gradientTop, year.gradientBottom))
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) year.accent else year.accent.copy(alpha = 0.22f),
                shape = cardShape,
            ),
    ) {
        // Backdrop (if available).
        val backdrop = year.backdropUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom-to-top darkening veil so the massive year label is
        // always readable regardless of what the backdrop looks like.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x4D000000),
                        0.45f to Color(0x80000000),
                        1.0f to Color(0xEB000000),
                    )
                )
        )

        // Focus wash — accent radial glow.
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to year.accent.copy(alpha = 0.26f),
                            1.0f to Color.Transparent,
                            radius = 420f,
                        )
                    )
            )
        }

        // ── Content: YEAR (huge) + MOVIES tag chip at top-right ──
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            // Top-right tag chip.
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
                        .background(year.accent, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "MOVIES",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = Inter,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // Bottom-left massive year label.
            Column(
                Modifier.align(Alignment.BottomStart),
            ) {
                Text(
                    year.year.toString(),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 50.sp,
                    fontFamily = Inter,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
