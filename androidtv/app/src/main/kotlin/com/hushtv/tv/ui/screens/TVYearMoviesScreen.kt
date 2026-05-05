package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.ThemedHeroMetaCache
import com.hushtv.tv.ui.screens.home.BackToHomeChip
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Year detail screen — poster grid of every movie in your library
 * that matches the year's top 250 popular titles, sorted by TMDB
 * popularity descending.
 *
 * v1.43.51: refactored to mirror [TVThemedDetailScreen] exactly so
 * the layout, focus-driven backdrop, and per-movie hero text
 * (title + year + rating + runtime + overview) match the
 * "Based on True Stories" Themes UI the user explicitly asked for.
 *
 * Layout
 * ──────
 *   • Top 52 % — focus-driven hero. Backdrop = focused movie's
 *     TMDB backdrop (falls back to year's #1 backdrop). Left
 *     column = decade chip + giant year label + count. Right
 *     column = focused movie title + meta chips + overview.
 *   • Bottom 48 % — 6-column 2:3 poster grid.
 *
 * Re-uses [ThemedHeroMetaCache] for per-movie TMDB metadata
 * lookups (cached per streamId, debounced 250 ms on focus
 * change so D-pad scrolling doesn't fire dozens of TMDB calls).
 */
@Composable
fun TVYearMoviesScreen(
    nav: NavController,
    playlistId: String,
    year: Int,
) {
    val ctx = LocalContext.current
    val yearData = remember(year) { HushDecadeYears.year(year) }
    val decade: HushDecade? = remember(year) {
        HushDecadeYears.all.firstOrNull { year in it.startYear..(it.startYear + 9) }
    }
    if (yearData == null || decade == null) {
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

    // Try the cache; fall back to a synchronous match if the prime
    // hasn't reached this year yet.
    val cachedMatches = DecadeYearMatchCache.snapshot[year]
    var matches by remember(year) {
        mutableStateOf(cachedMatches ?: emptyList())
    }
    var matchesReady by remember(year) { mutableStateOf(cachedMatches != null) }
    LaunchedEffect(year, cachedMatches?.size) {
        if (cachedMatches != null) {
            matches = cachedMatches
            matchesReady = true
            return@LaunchedEffect
        }
        val resolved = withContext(Dispatchers.IO) {
            DecadeYearMatchCache.matchYearBlocking(year)
        }
        matches = resolved
        matchesReady = true
    }

    val firstFocus = remember { FocusRequester() }
    var focusedIndex by remember(year) { mutableStateOf(0) }
    val focusedMatch = matches.getOrNull(focusedIndex)

    // Lazy TMDB metadata for the focused poster — debounced 250 ms
    // so rapid D-pad scrolling doesn't fire dozens of TMDB calls.
    var heroMeta by remember(year) { mutableStateOf<ThemedHeroMetaCache.Meta?>(null) }
    LaunchedEffect(focusedMatch?.streamId) {
        val target = focusedMatch ?: return@LaunchedEffect
        ThemedHeroMetaCache.get(target.streamId)?.let {
            heroMeta = it
            return@LaunchedEffect
        }
        heroMeta = null
        delay(250)
        val meta = ThemedHeroMetaCache.resolve(target.streamId, target.title, target.year)
        val nowFocused = matches.getOrNull(focusedIndex)
        if (nowFocused?.streamId == target.streamId) {
            heroMeta = meta
        }
    }

    // Auto-focus the first poster as soon as the matches list is
    // available. Wrapped in a small delay so the FocusRequester is
    // attached before we request focus.
    LaunchedEffect(matches.isNotEmpty()) {
        if (matches.isNotEmpty()) {
            delay(180)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        Column(Modifier.fillMaxSize()) {
            YearDetailHero(
                year = year,
                decade = decade,
                yearBackdropFallback = yearData.backdropUrl ?: decade.heroBackdropUrl,
                focused = focusedMatch,
                heroMeta = heroMeta,
                totalCount = matches.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f),
            )

            if (!matchesReady) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(
                            color = decade.accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            "Curating your $year library…",
                            color = Color(0xFFCBD5E1),
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            } else if (matches.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No matches in your library for $year",
                            color = Color(0xFFCBD5E1),
                            fontFamily = Inter,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Try a different year, or check back after your next library refresh.",
                            color = Color(0xFF94A3B8),
                            fontFamily = Inter,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentPadding = PaddingValues(
                        start = 56.dp,
                        end = 56.dp,
                        top = 12.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = matches,
                        key = { it.streamId },
                    ) { match ->
                        val idx = matches.indexOf(match)
                        YearPosterTile(
                            match = match,
                            accent = decade.accent,
                            focusModifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onFocus = { focusedIndex = idx },
                            onClick = {
                                nav.navigate(
                                    "moviedetail/$playlistId/" +
                                        "${match.streamId}/" +
                                        Uri.encode(match.title),
                                )
                            },
                        )
                    }
                }
            }
        }

        BackToHomeChip(nav = nav, playlistId = playlistId)
    }
}

@Composable
private fun YearDetailHero(
    year: Int,
    decade: HushDecade,
    yearBackdropFallback: String?,
    focused: DecadeYearMatchCache.YearMatch?,
    heroMeta: ThemedHeroMetaCache.Meta?,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Backdrop priority: focused movie's TMDB backdrop > focused
        // movie's TMDB poster > year's #1 backdrop > decade hero.
        val backdropUrl = heroMeta?.backdropUrl
            ?: heroMeta?.posterUrl
            ?: yearBackdropFallback
        AnimatedContent(
            targetState = backdropUrl,
            transitionSpec = { fadeIn(tween(360)) togetherWith fadeOut(tween(360)) },
            label = "year-detail-backdrop",
        ) { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to decade.accent.copy(alpha = 0.18f),
                                    1f to Color(0xFF0A0F1C),
                                ),
                            ),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x330A0F1C),
                                0.55f to Color(0x990A0F1C),
                                1f to Color(0xFF0A0F1C),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color(0xEE0A0F1C),
                                0.5f to Color(0x880A0F1C),
                                1f to Color(0x330A0F1C),
                            ),
                        ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(decade.accent.copy(alpha = 0.18f))
                        .border(1.dp, decade.accent.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        decade.label.uppercase(),
                        color = decade.accent,
                        fontFamily = Inter,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                }
                Text(
                    "Movies · $year",
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 46.sp,
                    letterSpacing = (-0.8).sp,
                )
                Box(
                    Modifier
                        .height(3.dp)
                        .width(64.dp)
                        .background(decade.accent),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$totalCount ${if (totalCount == 1) "film" else "films"} in your library",
                    color = decade.accent,
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }

            AnimatedContent(
                targetState = focused?.streamId,
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(280)) },
                label = "year-focused-copy",
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
            ) { streamId ->
                val current = focused?.takeIf { it.streamId == streamId }
                if (current == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            current.title,
                            color = Color.White,
                            fontFamily = Inter,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 36.sp,
                            letterSpacing = (-0.5).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            (heroMeta?.year ?: current.year)?.let { yr -> YearMetaChip("$yr") }
                            heroMeta?.voteAverage?.takeIf { it >= 0.1 }?.let { va ->
                                YearMetaChip("\u2605 " + String.format("%.1f", va))
                            }
                            heroMeta?.runtimeMinutes?.takeIf { it > 0 }?.let { rt ->
                                YearMetaChip("${rt} min")
                            }
                        }
                        Text(
                            heroMeta?.overview?.ifBlank { null } ?: "—",
                            color = Color(0xFFE5E7EB),
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 21.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearMetaChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x331F2937))
            .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontFamily = Inter,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun YearPosterTile(
    match: DecadeYearMatchCache.YearMatch,
    accent: Color,
    focusModifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = focusModifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
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
        if (match.poster != null) {
            AsyncImage(
                model = match.poster,
                contentDescription = match.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    match.title,
                    color = Color(0xFFE5E7EB),
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
