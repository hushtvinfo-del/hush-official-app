package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.hushtv.tv.data.HushThemedLists
import com.hushtv.tv.data.ThemedMatchCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

/* ════════════════════════════════════════════════════════════════
 *  Mobile-optimized detail screens that mirror the TV experience.
 *
 *  Same data layer (HushThemedLists, HushDecadeYears, the match
 *  caches, ThemedHeroMetaCache) — different layout: vertical-first,
 *  no left/right hero/grid split, no D-pad focus rings. All three
 *  share the same `MobileMoviesGridScreen` skeleton:
 *
 *    1. Top hero strip with a focused-movie backdrop that swaps
 *       as the user scrolls.
 *    2. Section title + count.
 *    3. 3-column poster grid.
 *
 *  Routes (registered in MobileApp.kt):
 *    • themedetail/{playlistId}/{themeId}
 *    • decadeyears/{playlistId}/{startYear}
 *    • yearmovies/{playlistId}/{year}
 *  Same route paths as the TV navigation graph so a single
 *  `nav.navigate("themedetail/...")` call works on both form
 *  factors.
 * ════════════════════════════════════════════════════════════════ */

// ── Themes detail ─────────────────────────────────────────────────

@Composable
fun MobileThemedDetailScreen(
    nav: NavController,
    playlistId: String,
    themeId: String,
) {
    val ctx = LocalContext.current
    val theme = remember(themeId) {
        HushThemedLists.all.firstOrNull { it.id == themeId }
    }
    if (theme == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val playlist = PlaylistStore.find(ctx, playlistId) ?: return@withContext
            runCatching { LibraryIndex.prime(ctx, playlist) }
            ThemedMatchCache.primeAsync(ctx, playlist)
        }
    }

    val cached = ThemedMatchCache.matchesFor(themeId)
    var matches by remember(themeId) {
        mutableStateOf(cached.orEmpty().map {
            MovieGridEntry(it.streamId, it.title, it.poster, it.year)
        })
    }
    var ready by remember(themeId) { mutableStateOf(cached != null) }
    LaunchedEffect(themeId) {
        if (cached != null) {
            matches = cached.map { MovieGridEntry(it.streamId, it.title, it.poster, it.year) }
            ready = true; return@LaunchedEffect
        }
        // Poll the snapshot every 200 ms until the prime catches up.
        // Bound to ~10 s so we never spin forever.
        repeat(50) {
            kotlinx.coroutines.delay(200)
            ThemedMatchCache.matchesFor(themeId)?.let { hits ->
                matches = hits.map { MovieGridEntry(it.streamId, it.title, it.poster, it.year) }
                ready = true
                return@LaunchedEffect
            }
        }
        // Even if prime never lands, mark ready so the empty-state copy renders.
        ready = true
    }

    MobileMoviesGridScreen(
        nav = nav,
        playlistId = playlistId,
        eyebrow = theme.section.displayName.uppercase(),
        title = theme.title,
        accent = theme.accent,
        fallbackBackdrop = theme.heroBackdropUrl,
        ready = ready,
        matches = matches,
    )
}

// ── Decade years grid ─────────────────────────────────────────────

@Composable
fun MobileDecadeYearsScreen(
    nav: NavController,
    playlistId: String,
    decadeStartYear: Int,
) {
    val ctx = LocalContext.current
    val decade = remember(decadeStartYear) {
        HushDecadeYears.decadeOf(decadeStartYear)
    }
    if (decade == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val playlist = PlaylistStore.find(ctx, playlistId) ?: return@withContext
            runCatching { LibraryIndex.prime(ctx, playlist) }
            DecadeYearMatchCache.primeAsync(ctx, playlist)
        }
    }

    val matchSnapshot = DecadeYearMatchCache.snapshot

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                MobileBackHeader(nav = nav, title = decade.label, accent = decade.accent)
            }
            item {
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    if (decade.heroBackdropUrl != null) {
                        AsyncImage(
                            model = decade.heroBackdropUrl,
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
                                    0f to Color(0x550A1220),
                                    1f to Color(0xFF0A1220),
                                ),
                            ),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                    ) {
                        Text(
                            decade.label,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 38.sp,
                        )
                        Text(
                            decade.title,
                            color = decade.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            items(decade.years, key = { it.year }) { yr ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A1220))
                        .clickable { nav.navigate("yearmovies/$playlistId/${yr.year}") },
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
                                Brush.horizontalGradient(
                                    0f to Color(0xCC000000),
                                    0.6f to Color(0x44000000),
                                    1f to Color.Transparent,
                                ),
                            ),
                    )
                    Column(
                        Modifier.align(Alignment.CenterStart).padding(16.dp),
                    ) {
                        Text(
                            yr.year.toString(),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 30.sp,
                        )
                        val cnt = matchSnapshot[yr.year]?.size
                        if (cnt != null && cnt > 0) {
                            Text(
                                "$cnt in your library",
                                color = decade.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Year movies grid ──────────────────────────────────────────────

@Composable
fun MobileYearMoviesScreen(
    nav: NavController,
    playlistId: String,
    year: Int,
) {
    val ctx = LocalContext.current
    val yearData = remember(year) { HushDecadeYears.year(year) }
    val decade = remember(year) {
        HushDecadeYears.all.firstOrNull { year in it.startYear..(it.startYear + 9) }
    }
    if (yearData == null || decade == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val playlist = PlaylistStore.find(ctx, playlistId) ?: return@withContext
            runCatching { LibraryIndex.prime(ctx, playlist) }
            DecadeYearMatchCache.primeAsync(ctx, playlist)
        }
    }

    val cached = DecadeYearMatchCache.snapshot[year]
    var matches by remember(year) {
        mutableStateOf(cached.orEmpty().map {
            MovieGridEntry(it.streamId, it.title, it.poster, it.year)
        })
    }
    var ready by remember(year) { mutableStateOf(cached != null) }
    LaunchedEffect(year, cached?.size) {
        if (cached != null) {
            matches = cached.map { MovieGridEntry(it.streamId, it.title, it.poster, it.year) }
            ready = true; return@LaunchedEffect
        }
        val resolved = withContext(Dispatchers.IO) {
            DecadeYearMatchCache.matchYearBlocking(year)
        }
        matches = resolved.map { MovieGridEntry(it.streamId, it.title, it.poster, it.year) }
        ready = true
    }

    MobileMoviesGridScreen(
        nav = nav,
        playlistId = playlistId,
        eyebrow = "${decade.label.uppercase()} · ${decade.title.uppercase()}",
        title = "Movies · $year",
        accent = decade.accent,
        fallbackBackdrop = yearData.backdropUrl ?: decade.heroBackdropUrl,
        ready = ready,
        matches = matches,
    )
}

// ── Shared mobile movies grid ─────────────────────────────────────

private data class MovieGridEntry(
    val streamId: Int,
    val title: String,
    val poster: String?,
    val year: Int?,
)

@Composable
private fun MobileMoviesGridScreen(
    nav: NavController,
    playlistId: String,
    eyebrow: String,
    title: String,
    accent: Color,
    fallbackBackdrop: String?,
    ready: Boolean,
    matches: List<MovieGridEntry>,
) {
    val ctx = LocalContext.current

    // Look up the playlist once so click handlers can build the
    // streaming URL without hitting SharedPreferences on every tap.
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    // Hero backdrop swaps to the focused poster's TMDB backdrop.
    var focusedIdx by remember(matches.size) { mutableStateOf(0) }
    val focused = matches.getOrNull(focusedIdx)
    var heroMeta by remember(matches.size) {
        mutableStateOf<ThemedHeroMetaCache.Meta?>(null)
    }
    LaunchedEffect(focused?.streamId) {
        val target = focused ?: return@LaunchedEffect
        ThemedHeroMetaCache.get(target.streamId)?.let {
            heroMeta = it; return@LaunchedEffect
        }
        heroMeta = null
        kotlinx.coroutines.delay(250)
        val meta = ThemedHeroMetaCache.resolve(target.streamId, target.title, target.year)
        if (matches.getOrNull(focusedIdx)?.streamId == target.streamId) {
            heroMeta = meta
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                MobileBackHeader(nav = nav, title = title, accent = accent)
            }
            item {
                MobileDetailHero(
                    eyebrow = eyebrow,
                    title = title,
                    accent = accent,
                    backdropUrl = heroMeta?.backdropUrl
                        ?: heroMeta?.posterUrl
                        ?: fallbackBackdrop,
                    focusedTitle = focused?.title,
                    focusedYear = (heroMeta?.year ?: focused?.year),
                    rating = heroMeta?.voteAverage?.takeIf { it >= 0.1 },
                    runtime = heroMeta?.runtimeMinutes?.takeIf { it > 0 },
                    overview = heroMeta?.overview,
                    matchCount = matches.size,
                    ready = ready,
                )
            }
            if (!ready) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    }
                }
            } else if (matches.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Nothing in your library matches this list yet.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(((matches.size + 2) / 3 * 200 + 16).dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false,
                    ) {
                        gridItems(matches, key = { it.streamId }) { m ->
                            MobilePosterTile(
                                m = m,
                                accent = accent,
                                onClick = {
                                    // Build the Xtream movie URL and
                                    // jump straight into the mobile
                                    // player. Mobile has no separate
                                    // "movie detail" page — every
                                    // poster is a play-on-tap card,
                                    // matching the existing browse
                                    // behaviour. If the playlist
                                    // failed to load (cold start race),
                                    // bail silently — the grid will
                                    // still scroll, just won't play
                                    // until the next tap once the
                                    // playlist is ready.
                                    val p = playlist ?: return@MobilePosterTile
                                    val url = com.hushtv.tv.data.XtreamApi.movieUrl(
                                        p.host, p.username, p.password,
                                        m.streamId, /* ext = */ null,
                                    )
                                    nav.navigate(
                                        mobilePlayerRoute(
                                            playlistId = playlistId,
                                            streamUrl = url,
                                            channelName = m.title,
                                            isLive = false,
                                            vodStreamId = m.streamId,
                                            vodKind = "movie",
                                            vodPoster = m.poster,
                                        ),
                                    )
                                },
                                onFocus = { focusedIdx = matches.indexOf(m).coerceAtLeast(0) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileBackHeader(
    nav: NavController,
    title: String,
    accent: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MobileDetailHero(
    eyebrow: String,
    title: String,
    accent: Color,
    backdropUrl: String?,
    focusedTitle: String?,
    focusedYear: Int?,
    rating: Double?,
    runtime: Int?,
    overview: String?,
    matchCount: Int,
    ready: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
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
                        0.6f to Color(0xCC0A1220),
                        1f to Color(0xFF0A1220),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text(
                eyebrow,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 30.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ready) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "$matchCount ${if (matchCount == 1) "film" else "films"} in your library",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
            }
            if (focusedTitle != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        focusedTitle,
                        color = Color(0xFFE5E7EB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    focusedYear?.let {
                        Spacer(Modifier.width(6.dp))
                        Text("· $it", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    rating?.let {
                        Spacer(Modifier.width(6.dp))
                        Text("· \u2605 ${"%.1f".format(it)}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MobilePosterTile(
    m: MovieGridEntry,
    accent: Color,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .clickable {
                onFocus()
                onClick()
            },
    ) {
        if (m.poster != null) {
            AsyncImage(
                model = m.poster,
                contentDescription = m.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.title,
                    color = Color(0xFFE5E7EB),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
