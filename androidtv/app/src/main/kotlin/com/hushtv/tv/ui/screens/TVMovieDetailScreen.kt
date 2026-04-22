package com.hushtv.tv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.MyListStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RpdbService
import com.hushtv.tv.data.TmdbCastMember
import com.hushtv.tv.data.TmdbMovie
import com.hushtv.tv.data.TmdbRecommendation
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Amber
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.BorderSlate
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextDim
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/* ──────────────────────────────────────────────────────────────── */
/*  MOVIE DETAIL SCREEN                                             */
/* ──────────────────────────────────────────────────────────────── */

@Composable
fun TVMovieDetailScreen(
    nav: NavController,
    playlistId: String,
    streamId: Int,
    title: String,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    var vodInfo by remember { mutableStateOf<com.hushtv.tv.data.XtreamVodInfo?>(null) }
    var tmdbMovie by remember { mutableStateOf<TmdbMovie?>(null) }
    var loading by remember { mutableStateOf(true) }
    // Full library (movies + series) — used for cross-referencing an actor's
    // filmography against titles the user can actually play.
    var library by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    // Fetch Xtream VOD info → tmdb_id (if present) → TMDB details.
    LaunchedEffect(streamId) {
        val p = playlist ?: return@LaunchedEffect
        loading = true
        val vi = XtreamApi.getVodInfo(p.host, p.username, p.password, streamId)
        vodInfo = vi
        val tmdbId = vi?.info?.tmdb_id?.toIntOrNull()
            ?: TmdbService.searchMovie(title, extractYearInt(vi?.info?.releasedate))
        if (tmdbId != null) {
            tmdbMovie = TmdbService.getMovie(tmdbId)
        }
        loading = false
    }

    // Lazy-fetch the full library in the background (for cast filmography lookup).
    LaunchedEffect(streamId) {
        val p = playlist ?: return@LaunchedEffect
        val movies = runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "movie") }.getOrDefault(emptyList())
        val series = runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "series") }.getOrDefault(emptyList())
        library = movies + series
    }

    // Cast-filmography dialog state
    var castDialog by remember { mutableStateOf<CastDialogState?>(null) }

    // My-list state
    var myListVersion by remember { mutableStateOf(0) }
    val isInMyList = remember(myListVersion) {
        MyListStore.isInList(ctx, playlistId, "movie", streamId)
    }

    // Play button focus on entry
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(tmdbMovie != null || !loading) {
        runCatching { playFocus.requestFocus() }
    }

    val inner = vodInfo?.info
    val backdropUrl = TmdbService.img(tmdbMovie?.backdrop_path, "w1280")
        ?: inner?.backdrop_path?.firstOrNull()
        ?: inner?.movie_image
    val posterUrl = TmdbService.img(tmdbMovie?.poster_path, "w500")
        ?: inner?.cover_big ?: inner?.movie_image
    val rpdbPosterUrl = RpdbService.posterUrl(tmdbMovie?.external_ids?.imdb_id)
    val trailerKey = TmdbService.pickTrailer(tmdbMovie?.videos)

    val displayTitle = tmdbMovie?.title?.takeIf { it.isNotBlank() } ?: title
    val displayOverview = tmdbMovie?.overview?.takeIf { it.isNotBlank() }
        ?: inner?.plot ?: inner?.description ?: ""
    val displayTagline = tmdbMovie?.tagline?.takeIf { it.isNotBlank() }
    val releaseYear = extractYear(tmdbMovie?.release_date ?: inner?.releasedate ?: inner?.release_date)
    val runtime = tmdbMovie?.runtime?.takeIf { it > 0 }?.let { runtimeFromMin(it) }
        ?: inner?.duration ?: durationFromSeconds(inner?.duration_secs)
    val director = tmdbMovie?.credits?.crew?.firstOrNull { it.job == "Director" }?.name
        ?: inner?.director
    val genres = tmdbMovie?.genres?.map { it.name }
        ?: inner?.genre?.split(',', '/')?.map { it.trim() }.orEmpty()
    val castList = tmdbMovie?.credits?.cast?.sortedBy { it.order }?.take(20).orEmpty()
    val recommendations = tmdbMovie?.recommendations?.results?.take(20).orEmpty()

    val onPlay: () -> Unit = click@{
        val p = playlist ?: return@click
        val ext = vodInfo?.movie_data?.container_extension ?: "mp4"
        val url = XtreamApi.movieUrl(p.host, p.username, p.password, streamId, ext)
        nav.navigate(
            "player/$playlistId/${Uri.encode(url)}/${Uri.encode(displayTitle)}/false"
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        // ── Full-screen backdrop ────────────────────────────────
        if (!backdropUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.45f }
                    .blur(8.dp),
                error = { },
                loading = { },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x88000000),
                                Color(0xCC000000),
                                Color(0xFF000000),
                            ),
                        )
                    )
            )
        }

        // ── Back button top-left ────────────────────────────────
        Box(
            Modifier
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x55000000))
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .onFocusChanged { /* only focused when nothing else is */ }
                .focusable()
                .clickable { nav.popBackStack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }

        if (loading) {
            CircularProgressIndicator(
                color = Cyan,
                modifier = Modifier.size(40.dp).align(Alignment.Center),
            )
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 72.dp, end = 56.dp, top = 72.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Hero row: poster + info ────────────────────────
            Row(verticalAlignment = Alignment.Top) {
                // Poster — always use the TMDB poster (known 2:3) for the hero
                // so nothing ever gets clipped. RPDB appears on grid thumbnails.
                val heroPoster = posterUrl ?: rpdbPosterUrl
                Box(
                    Modifier
                        .width(200.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceNavy),
                ) {
                    if (!heroPoster.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = heroPoster,
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            error = { PosterMissing() },
                            loading = { },
                        )
                    } else {
                        PosterMissing()
                    }
                }

                Spacer(Modifier.width(32.dp))

                // Right info column
                Column(Modifier.weight(1f)) {
                    // Title
                    Text(
                        displayTitle,
                        color = TextPrimary,
                        fontSize = 44.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.2).sp,
                        lineHeight = 48.sp,
                        maxLines = 3,
                    )
                    displayTagline?.let { tag ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tag,
                            color = Color(0xFFCBD5E1),
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Normal,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Meta line — year · runtime · director
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (releaseYear.isNotBlank()) {
                            MetaText(releaseYear)
                            MetaDot()
                        }
                        if (!runtime.isNullOrBlank()) {
                            MetaText(runtime)
                            MetaDot()
                        }
                        director?.takeIf { it.isNotBlank() }?.let {
                            MetaText("Directed by $it")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Rating badges
                    RatingRow(
                        tmdb = tmdbMovie?.vote_average?.takeIf { it > 0 },
                        imdbId = tmdbMovie?.external_ids?.imdb_id,
                        xtreamRating = inner?.rating?.toFloatOrNull(),
                    )

                    Spacer(Modifier.height(14.dp))

                    // Genres
                    if (genres.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            genres.take(5).forEach { g -> DetailGenreChip(g) }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    // Overview
                    if (displayOverview.isNotBlank()) {
                        Text(
                            displayOverview,
                            color = Color(0xFFD1D5DB),
                            fontSize = 15.sp,
                            fontFamily = Inter,
                            lineHeight = 22.sp,
                            maxLines = 6,
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    // CTAs
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeroCta(
                            label = "Play",
                            icon = Icons.Default.PlayArrow,
                            primary = true,
                            focusRequester = playFocus,
                            onClick = onPlay,
                        )
                        HeroCta(
                            label = if (isInMyList) "In List" else "My List",
                            icon = if (isInMyList) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            onClick = {
                                MyListStore.toggle(ctx, playlistId, "movie", streamId)
                                myListVersion++
                            },
                        )
                        trailerKey?.let { key ->
                            HeroCta(
                                label = "Trailer",
                                icon = Icons.Default.PlayCircle,
                                onClick = { openYoutube(ctx, key) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Cast row ──────────────────────────────────────
            if (castList.isNotEmpty()) {
                SectionHeader("Cast")
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(castList, key = { "cast-${it.id}" }) { c ->
                        CastCard(member = c) {
                            // Open filmography dialog — fetch TMDB person credits
                            // and cross-reference against the user's library.
                            if (library.isEmpty()) {
                                castDialog = CastDialogState(c, emptyList(), loading = true, notice = "Loading library…")
                            } else {
                                castDialog = CastDialogState(c, emptyList(), loading = true)
                            }
                            scope.launch {
                                val titles = TmdbService.personCombinedCredits(c.id)
                                // Wait for library if still loading
                                var lib = library
                                var waits = 0
                                while (lib.isEmpty() && waits < 20) {
                                    kotlinx.coroutines.delay(200)
                                    lib = library
                                    waits++
                                }
                                val matches = matchLibraryByTitles(titles, lib)
                                castDialog = CastDialogState(c, matches, loading = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // ── Recommendations row ───────────────────────────
            if (recommendations.isNotEmpty()) {
                SectionHeader("More Like This")
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recommendations, key = { "rec-${it.id}" }) { r ->
                        RecommendationCard(rec = r)
                    }
                }
            }
        }

        // ── Cast filmography dialog (overlay) ──────────────────────
        castDialog?.let { state ->
            CastFilmographyDialog(
                state = state,
                onDismiss = { castDialog = null },
                onTitleClick = { item ->
                    castDialog = null
                    when (item.kind) {
                        "movie" -> nav.navigate("moviedetail/$playlistId/${item.streamId}/${Uri.encode(item.title)}")
                        "series" -> nav.navigate("series/$playlistId/${item.seriesId}/${Uri.encode(item.title)}")
                    }
                },
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  CAST FILMOGRAPHY DIALOG                                         */
/* ──────────────────────────────────────────────────────────────── */

data class CastDialogState(
    val member: TmdbCastMember,
    val matches: List<MediaCard>,
    val loading: Boolean,
    val notice: String? = null,
)

@Composable
private fun CastFilmographyDialog(
    state: CastDialogState,
    onDismiss: () -> Unit,
    onTitleClick: (MediaCard) -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 1000.dp)
                    .fillMaxWidth(0.8f)
                    .heightIn(max = 620.dp)
                    .background(Color(0xFF0B111D), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(16.dp))
                    .padding(28.dp),
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurfaceNavy),
                        contentAlignment = Alignment.Center,
                    ) {
                        val img = TmdbService.img(state.member.profile_path, "w185")
                        if (!img.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = img,
                                contentDescription = state.member.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                error = { Text(
                                    state.member.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black
                                ) },
                                loading = { },
                            )
                        } else {
                            Text(
                                state.member.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.member.name,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Also in your library",
                            color = Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.3.sp,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                when {
                    state.loading -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    state.notice ?: "Searching your library…",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    state.matches.isEmpty() -> {
                        Box(
                            Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No other titles with ${state.member.name} in your library.",
                                color = TextMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    else -> {
                        androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
                            rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().height(440.dp),
                        ) {
                            items(
                                count = state.matches.size,
                                key = { idx -> "fmg-${state.matches[idx].kind}-${state.matches[idx].id}" },
                            ) { idx ->
                                val item = state.matches[idx]
                                FilmographyCard(item = item, onClick = { onTitleClick(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmographyCard(item: MediaCard, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(140),
        label = "fmg-scale",
    )
    Column(
        Modifier
            .width(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Cyan else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            if (!item.poster.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { PosterMissing() },
                    loading = { },
                )
            } else {
                PosterMissing()
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    if (item.kind == "series") "SERIES" else "MOVIE",
                    color = Cyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.title,
            color = if (focused) Cyan else TextPrimary,
            fontSize = 10.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            lineHeight = 12.sp,
        )
    }
}

@Composable
private fun PosterMissing() {
    Box(
        Modifier.fillMaxSize().background(SurfaceNavy),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            color = BorderSlate,
            fontSize = 36.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Cross-reference a list of title strings from TMDB against the library. */
private fun matchLibraryByTitles(
    titles: List<String>,
    library: List<MediaCard>,
): List<MediaCard> {
    if (titles.isEmpty() || library.isEmpty()) return emptyList()
    val libLower = library.map { it to it.title.lowercase() }
    val seen = mutableSetOf<String>()
    val out = mutableListOf<MediaCard>()
    titles.forEach { raw ->
        val t = raw.trim().lowercase()
        if (t.isBlank() || t.length < 2) return@forEach
        val hit = libLower.firstOrNull { (_, lt) ->
            lt == t || lt.startsWith("$t ") || lt.startsWith("$t:") ||
                (t.length >= 5 && (lt.contains(" $t") || lt.contains(" $t:")))
        }?.first
        if (hit != null && seen.add("${hit.kind}-${hit.id}")) out += hit
    }
    return out.take(30)
}

/* ──────────────────────────────────────────────────────────────── */
/*  RATING BADGES                                                   */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun RatingRow(
    tmdb: Double?,
    imdbId: String?,
    xtreamRating: Float?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Primary — use xtream IMDb-style rating if TMDB missing
        val imdbScore = xtreamRating?.takeIf { it > 0 }
        if (imdbScore != null) {
            RatingBadge(
                label = "IMDb",
                value = String.format("%.1f", imdbScore),
                labelColor = Color(0xFFF5C518),
                valueColor = Color(0xFFF5C518),
            )
        } else if (!imdbId.isNullOrBlank()) {
            // No numeric rating but we have an IMDb link — just show the brand
            RatingBadge(label = "IMDb", value = "✓", labelColor = Color(0xFFF5C518), valueColor = Color(0xFFF5C518))
        }
        tmdb?.takeIf { it > 0 }?.let { t ->
            RatingBadge(label = "TMDB", value = String.format("%.1f", t), labelColor = Cyan, valueColor = Cyan)
        }
    }
}

@Composable
private fun RatingBadge(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0x80000000), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = labelColor,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 13.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  CAST & RECOMMENDATIONS                                          */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun CastCard(member: TmdbCastMember, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(140),
        label = "cast-scale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Cyan else Color(0x33FFFFFF),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val img = TmdbService.img(member.profile_path, "w185")
            if (!img.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = img,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    error = { CastInitial(member.name) },
                    loading = { CastInitial(member.name) },
                )
            } else {
                CastInitial(member.name)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            member.name,
            color = if (focused) Cyan else TextPrimary,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            lineHeight = 13.sp,
        )
        Text(
            member.character,
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = Inter,
            maxLines = 1,
            lineHeight = 12.sp,
        )
    }
}

@Composable
private fun CastInitial(name: String) {
    Text(
        name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        color = Cyan,
        fontSize = 24.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun RecommendationCard(rec: TmdbRecommendation) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(140),
        label = "rec-scale",
    )
    Column(
        modifier = Modifier
            .width(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { /* could navigate to that TMDB detail if in library */ },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Cyan else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            val img = TmdbService.img(rec.poster_path, "w342")
            if (!img.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = img,
                    contentDescription = rec.title ?: rec.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // TMDB rating badge
            if (rec.vote_average > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Default.Star, null, tint = Amber, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        String.format("%.1f", rec.vote_average),
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            rec.title ?: rec.name ?: "",
            color = if (focused) Cyan else TextPrimary,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            lineHeight = 13.sp,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  SHARED SMALL PIECES                                             */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HeroCta(
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(140),
        label = "cta-scale",
    )
    val bg = when {
        primary && focused -> Cyan
        primary -> Color.White
        focused -> Color(0x3306B6D4)
        else -> Color(0x1FFFFFFF)
    }
    val fg = if (primary) Color.Black else if (focused) Cyan else Color.White
    val mod = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .height(48.dp)
        .background(bg, RoundedCornerShape(10.dp))
        .border(
            2.dp,
            if (focused) Cyan else Color.Transparent,
            RoundedCornerShape(10.dp),
        )
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .clickable(onClick = onClick)
        .padding(horizontal = 22.dp)
    Row(mod, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontSize = 14.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 20.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    )
}

@Composable
private fun MetaText(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontFamily = Inter,
    )
}

@Composable
private fun MetaDot() {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.size(3.dp).background(TextDim, CircleShape))
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun DetailGenreChip(text: String) {
    Box(
        Modifier
            .background(Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = Color(0xFFD1D5DB),
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  HELPERS                                                         */
/* ──────────────────────────────────────────────────────────────── */

private fun extractYear(date: String?): String {
    if (date.isNullOrBlank()) return ""
    return date.take(4).takeIf { it.toIntOrNull() != null } ?: ""
}

private fun extractYearInt(date: String?): Int? {
    if (date.isNullOrBlank()) return null
    return date.take(4).toIntOrNull()
}

private fun runtimeFromMin(mins: Int): String {
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun durationFromSeconds(secs: Int?): String? {
    if (secs == null || secs <= 0) return null
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun openYoutube(ctx: android.content.Context, videoId: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }
}
