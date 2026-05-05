package com.hushtv.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.DecadeYearMatchCache
import com.hushtv.tv.data.HushDecade
import com.hushtv.tv.data.HushDecadeYears
import com.hushtv.tv.data.HushYear
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.screens.home.BackToHomeChip
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Decade detail screen — shows the 10 years inside the selected
 * decade as large cards, oldest → newest. Each year card uses the
 * year's #1-popular movie backdrop (hardcoded TMDB CDN URL) and a
 * live count of library matches pulled from [DecadeYearMatchCache].
 *
 * Layout
 * ──────
 *   • Top 38 % — focus-driven hero. Painted with the focused year's
 *     backdrop, decade title, year label, and library match count.
 *   • Bottom 62 % — 5-column year-card grid.
 *
 * Routes here from the home page Decades rail.
 */
@Composable
fun TVDecadeYearsScreen(
    nav: NavController,
    playlistId: String,
    decadeStartYear: Int,
) {
    val ctx = LocalContext.current
    val decade = remember(decadeStartYear) { HushDecadeYears.decadeOf(decadeStartYear) }
    if (decade == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    // Defensive cache prime — boot refresh handles this normally.
    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val playlist = PlaylistStore.find(ctx, playlistId) ?: return@withContext
            runCatching { LibraryIndex.prime(ctx, playlist) }
            DecadeYearMatchCache.primeAsync(ctx, playlist)
        }
    }

    // Compose-observable cache — tiles light up as years land.
    val matchSnapshot = DecadeYearMatchCache.snapshot

    val firstFocus = remember { FocusRequester() }
    var focusedYear by remember { mutableStateOf(decade.years.firstOrNull()) }

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        Column(Modifier.fillMaxSize()) {
            DecadeHero(
                decade = decade,
                focusedYear = focusedYear,
                focusedMatchCount = focusedYear?.let { matchSnapshot[it.year]?.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.34f),
            )
            // ── Year grid ──
            // Hard-bounded 2 rows × 5 cards layout. Sized via
            // weight on the rows so all 10 cards always fit on
            // screen regardless of the device's actual height
            // (1080p / 720p / tablet) — no internal scrolling.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.66f)
                    .padding(
                        start = 56.dp,
                        end = 56.dp,
                        top = 18.dp,
                        bottom = 56.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val rows = decade.years.chunked(5)
                rows.forEachIndexed { rowIdx, rowYears ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowYears.forEach { yr ->
                            val absIdx = rowIdx * 5 + decade.years.indexOf(yr).coerceAtLeast(0).let { 0 }
                            val isFirst = rowIdx == 0 && rowYears.first() == yr
                            YearTile(
                                yr = yr,
                                accent = decade.accent,
                                matchCount = matchSnapshot[yr.year]?.size,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                focusModifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                                onFocus = { focusedYear = yr },
                                onClick = {
                                    nav.navigate("yearmovies/$playlistId/${yr.year}")
                                },
                            )
                            // Suppress unused — placeholder to allow
                            // future per-tile decoration.
                            @Suppress("UNUSED_EXPRESSION") absIdx
                        }
                        // Pad short rows to keep weight math sane
                        // (won't happen for 10 standard years).
                        repeat(5 - rowYears.size) {
                            Box(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
        BackToHomeChip(nav = nav, playlistId = playlistId)
    }
}

@Composable
private fun DecadeHero(
    decade: HushDecade,
    focusedYear: HushYear?,
    focusedMatchCount: Int?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Backdrop = focused year's #1 backdrop, fall back to decade hero.
        val backdrop = focusedYear?.backdropUrl ?: decade.heroBackdropUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Vignette + bottom darkening so text stays legible.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x66000000),
                        0.5f to Color(0xAA000000),
                        1f to Color(0xFF000000),
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xCC000000),
                        0.5f to Color(0x66000000),
                        1f to Color(0x33000000),
                    )
                )
        )

        // Hero copy — left-aligned column.
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 56.dp, top = 36.dp, end = 56.dp, bottom = 24.dp),
        ) {
            // Eyebrow.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 16.dp)
                        .background(decade.accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "DECADE",
                    color = decade.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    fontFamily = Inter,
                )
            }
            Spacer(Modifier.height(10.dp))

            // Massive decade label.
            Text(
                decade.label,
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 74.sp,
                fontFamily = Inter,
            )

            // Focused-year quick stats chip.
            if (focusedYear != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x22FFFFFF))
                            .border(
                                1.dp,
                                decade.accent.copy(alpha = 0.55f),
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "${focusedYear.year}",
                            color = decade.accent,
                            fontFamily = Inter,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                        )
                    }
                    if (focusedMatchCount != null && focusedMatchCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$focusedMatchCount in your library",
                            color = Color(0xFFCBD5E1),
                            fontFamily = Inter,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearTile(
    yr: HushYear,
    accent: Color,
    matchCount: Int?,
    modifier: Modifier = Modifier,
    focusModifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .then(focusModifier)
            .clip(shape)
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) accent else Color(0x22FFFFFF),
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnter(onClick = onClick),
    ) {
        if (yr.backdropUrl != null) {
            AsyncImage(
                model = yr.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x55000000),
                        0.5f to Color(0x99000000),
                        1f to Color(0xEE000000),
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // Top-right: in-library count chip.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (matchCount != null && matchCount > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x33000000))
                            .border(
                                1.dp,
                                accent.copy(alpha = 0.65f),
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "$matchCount",
                            color = accent,
                            fontFamily = Inter,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // Bottom-left: year label.
            Text(
                yr.year.toString(),
                color = Color.White,
                fontFamily = Inter,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 38.sp,
            )
        }
    }
}
