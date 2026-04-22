package com.hushtv.tv.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.hushtv.tv.data.MyListStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RpdbService
import com.hushtv.tv.data.TmdbCastMember
import com.hushtv.tv.data.TmdbEpisode
import com.hushtv.tv.data.TmdbSeasonDetail
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.TmdbTv
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamEpisode
import com.hushtv.tv.ui.theme.Amber
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextDim
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ──────────────────────────────────────────────────────────────── */
/*  SERIES DETAIL SCREEN — Tivimate-style with TMDB enrichment      */
/* ──────────────────────────────────────────────────────────────── */

@Composable
fun TVSeriesDetailScreen(
    nav: NavController,
    playlistId: String,
    seriesId: String,
    seriesName: String,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    var xtreamEpisodesBySeason by remember { mutableStateOf<Map<String, List<XtreamEpisode>>>(emptyMap()) }
    var tmdbTv by remember { mutableStateOf<TmdbTv?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Selected season → fetched TMDB season detail (with episode stills)
    var selectedSeasonNum by remember { mutableStateOf<Int?>(null) }
    var tmdbSeason by remember { mutableStateOf<TmdbSeasonDetail?>(null) }

    // Fetch Xtream series info + TMDB
    LaunchedEffect(seriesId) {
        val p = playlist ?: return@LaunchedEffect
        loading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val info = XtreamApi.getSeriesInfo(p.host, p.username, p.password, seriesId)
                    xtreamEpisodesBySeason = info.episodes ?: emptyMap()
                }
                val tvId = TmdbService.searchTv(seriesName)
                if (tvId != null) {
                    tmdbTv = TmdbService.getTv(tvId)
                }
                // Pre-select first available season
                val firstSeason = xtreamEpisodesBySeason.keys
                    .mapNotNull { it.toIntOrNull() }.minOrNull()
                    ?: tmdbTv?.seasons?.firstOrNull { it.season_number > 0 }?.season_number
                selectedSeasonNum = firstSeason
            }
            loading = false
        }
    }

    // Fetch TMDB season details when the user picks a season
    LaunchedEffect(tmdbTv?.id, selectedSeasonNum) {
        val tv = tmdbTv ?: return@LaunchedEffect
        val n = selectedSeasonNum ?: return@LaunchedEffect
        tmdbSeason = null
        scope.launch {
            tmdbSeason = TmdbService.getSeason(tv.id, n)
        }
    }

    // My-list state (series keyed by series_id)
    val seriesIdInt = seriesId.toIntOrNull() ?: 0
    var myListVersion by remember { mutableStateOf(0) }
    val isInMyList = remember(myListVersion) {
        MyListStore.isInList(ctx, playlistId, "series", seriesIdInt)
    }

    val scrollState = rememberScrollState()
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(tmdbTv != null || !loading) {
        kotlinx.coroutines.delay(60)
        runCatching { backFocus.requestFocus() }
    }

    // ── Resolve display strings ──────────────────────────────
    val backdropUrl = TmdbService.img(tmdbTv?.backdrop_path, "w1280")
    val posterUrl = TmdbService.img(tmdbTv?.poster_path, "w500")
    val rpdbPoster = RpdbService.posterUrl(tmdbTv?.external_ids?.imdb_id)
    val displayTitle = tmdbTv?.name?.takeIf { it.isNotBlank() } ?: seriesName
    val displayOverview = tmdbTv?.overview.orEmpty()
    val releaseYear = extractYearStr(tmdbTv?.first_air_date)
    val seasonCount = tmdbTv?.number_of_seasons?.takeIf { it > 0 }
    val runtimeMin = tmdbTv?.episode_run_time?.firstOrNull()
    val director = tmdbTv?.credits?.crew?.firstOrNull { it.job == "Director" || it.job == "Creator" }?.name
    val genres = tmdbTv?.genres?.map { it.name }.orEmpty()
    val castList = tmdbTv?.credits?.cast?.sortedBy { it.order }?.take(20).orEmpty()
    val trailerKey = TmdbService.pickTrailer(tmdbTv?.videos)

    val seasonList = remember(xtreamEpisodesBySeason, tmdbTv) {
        val keys = xtreamEpisodesBySeason.keys
            .mapNotNull { it.toIntOrNull() }
            .sorted()
        if (keys.isNotEmpty()) keys
        else tmdbTv?.seasons?.map { it.season_number }?.filter { it > 0 }.orEmpty()
    }

    // ── Layout ───────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(BgBlack)) {
        // Backdrop — no runtime blur for smoother TV perf
        backdropUrl?.let {
            SubcomposeAsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.28f },
                error = { },
                loading = { },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xAA000000),
                                Color(0xDD000000),
                                Color(0xFF000000),
                            )
                        )
                    )
            )
        }

        // Back button — top-left, outside scroll. Focusing it animates scroll back to 0.
        Box(
            Modifier
                .padding(start = 16.dp, top = 14.dp)
                .align(Alignment.TopStart)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x88000000))
                .border(2.dp, Color(0x55FFFFFF), CircleShape)
                .focusRequester(backFocus)
                .onFocusChanged {
                    if (it.isFocused) scope.launch { scrollState.animateScrollTo(0) }
                }
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown &&
                        (ev.key == Key.Enter || ev.key == Key.DirectionCenter || ev.key == Key.NumPadEnter)
                    ) {
                        nav.popBackStack(); true
                    } else false
                }
                .focusable()
                .clickable { nav.popBackStack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
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
                .padding(start = 72.dp, end = 48.dp, top = 80.dp, bottom = 24.dp)
                .verticalScroll(scrollState),
        ) {
            // ── Hero row — natural height, verticalScroll handles overflow ─
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                val heroPoster = posterUrl ?: rpdbPoster
                Box(
                    Modifier
                        .width(190.dp)
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
                            error = { },
                            loading = { },
                        )
                    }
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayTitle,
                        color = TextPrimary,
                        fontSize = 40.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.0).sp,
                        lineHeight = 42.sp,
                        maxLines = 3,
                    )
                    tmdbTv?.tagline?.takeIf { it.isNotBlank() }?.let { tag ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tag,
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            fontFamily = Inter,
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (releaseYear.isNotBlank()) { SMetaText(releaseYear); SMetaDot() }
                        seasonCount?.let {
                            SMetaText(if (it == 1) "1 Season" else "$it Seasons")
                            SMetaDot()
                        }
                        runtimeMin?.takeIf { it > 0 }?.let {
                            SMetaText("${it}m per ep")
                            if (!director.isNullOrBlank()) SMetaDot()
                        }
                        director?.takeIf { it.isNotBlank() }?.let { SMetaText("Created by $it") }
                    }
                    Spacer(Modifier.height(10.dp))

                    // CTAs — placed HIGH in the info column so they sit in the initial viewport
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SCta(
                            label = if (isInMyList) "In Favorites" else "Add to Favorites",
                            icon = if (isInMyList) Icons.Default.Star else Icons.Default.StarBorder,
                            primary = false,
                            onClick = {
                                MyListStore.toggle(ctx, playlistId, "series", seriesIdInt)
                                myListVersion++
                            },
                        )
                        trailerKey?.let { k ->
                            SCta(
                                label = "Trailer",
                                icon = Icons.Default.PlayCircle,
                                primary = false,
                                onClick = { openYoutubeExt(ctx, k) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Rating badges (TMDB + IMDb)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if ((tmdbTv?.external_ids?.imdb_id).orEmpty().isNotBlank()) {
                            SRatingBadge("IMDb", "✓", Color(0xFFF5C518))
                        }
                        tmdbTv?.vote_average?.takeIf { it > 0 }?.let { t ->
                            SRatingBadge("TMDB", String.format("%.1f", t), Cyan)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (genres.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            genres.take(5).forEach { g -> SGenreChip(g) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (displayOverview.isNotBlank()) {
                        Text(
                            displayOverview,
                            color = Color(0xFFD1D5DB),
                            fontSize = 14.sp,
                            fontFamily = Inter,
                            lineHeight = 20.sp,
                            maxLines = 5,
                        )
                    }
                    // CTAs rendered in the fixed top bar (outside scroll)
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Season selector ──────────────────────────
            if (seasonList.size > 1) {
                SSectionHeader("Seasons")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasonList, key = { "season-$it" }) { num ->
                        SeasonTab(
                            number = num,
                            selected = selectedSeasonNum == num,
                            onClick = { selectedSeasonNum = num },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // ── Episode list ─────────────────────────────
            val seasonKey = selectedSeasonNum?.toString()
            val xtEpisodes = xtreamEpisodesBySeason[seasonKey].orEmpty()
            val tmdbEpisodes = tmdbSeason?.episodes.orEmpty()
            if (xtEpisodes.isNotEmpty()) {
                SSectionHeader("Episodes")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    xtEpisodes.forEach { ep ->
                        val tmdbEp = tmdbEpisodes.firstOrNull { it.episode_number == ep.episode_num }
                        EpisodeRow(
                            xtream = ep,
                            tmdb = tmdbEp,
                            onPlay = { ctx2 ->
                                val p = playlist ?: return@EpisodeRow
                                val url = XtreamApi.episodeUrl(
                                    p.host, p.username, p.password,
                                    ep.id, ep.container_extension ?: "mp4",
                                )
                                val name = ep.title.takeIf { it.isNotBlank() }
                                    ?: "$displayTitle S${selectedSeasonNum}E${ep.episode_num}"
                                nav.navigate(
                                    "player/$playlistId/${Uri.encode(url)}/${Uri.encode(name)}/false"
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Cast ─────────────────────────────────────
            if (castList.isNotEmpty()) {
                SSectionHeader("Cast")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(castList, key = { "cast-${it.id}" }) { c ->
                        SeriesCastCard(c)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  SEASON TAB                                                      */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun SeasonTab(number: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(90),
        label = "season-scale",
    )
    val bg = when {
        focused -> Cyan
        selected -> Color(0x3306B6D4)
        else -> Color(0x14FFFFFF)
    }
    val fg = when {
        focused -> Color.Black
        selected -> Cyan
        else -> Color(0xFFCBD5E1)
    }
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(42.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                2.dp,
                if (focused) Cyan else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Season $number",
            color = fg,
            fontSize = 13.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  EPISODE ROW                                                     */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun EpisodeRow(
    xtream: XtreamEpisode,
    tmdb: TmdbEpisode?,
    onPlay: (Context) -> Unit,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(90),
        label = "ep-scale",
    )
    val still = TmdbService.img(tmdb?.still_path, "w300")
    val title = tmdb?.name?.takeIf { it.isNotBlank() } ?: xtream.title
    val plot = tmdb?.overview?.takeIf { !it.isNullOrBlank() }
        ?: xtream.info?.plot.orEmpty()
    val runtime = tmdb?.runtime?.takeIf { it > 0 }?.let { "${it}m" }
        ?: xtream.info?.duration.orEmpty()
    val airDate = tmdb?.air_date.orEmpty().takeIf { it.isNotBlank() }

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                if (focused) Color(0x1F06B6D4) else Color(0x08FFFFFF),
                RoundedCornerShape(10.dp),
            )
            .border(
                2.dp,
                if (focused) Cyan else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onPlay(ctx) }
            .padding(10.dp),
    ) {
        // Thumbnail
        Box(
            Modifier
                .size(width = 160.dp, height = 90.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceNavy),
        ) {
            if (!still.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = still,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { EpisodeFallback(xtream.episode_num) },
                    loading = { EpisodeFallback(xtream.episode_num) },
                )
            } else {
                EpisodeFallback(xtream.episode_num)
            }
            // Play icon center
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Cyan, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "E${xtream.episode_num}",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            if (airDate != null || runtime.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    airDate?.let {
                        Text(it, color = TextMuted, fontSize = 11.sp, fontFamily = Inter)
                        if (runtime.isNotBlank()) { SMetaDot() }
                    }
                    if (runtime.isNotBlank()) {
                        Text(runtime, color = TextMuted, fontSize = 11.sp, fontFamily = Inter)
                    }
                }
            }
            if (plot.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    plot,
                    color = Color(0xFFB8BDC7),
                    fontSize = 12.sp,
                    fontFamily = Inter,
                    lineHeight = 16.sp,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun EpisodeFallback(num: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceNavy),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "E$num",
            color = Color(0xFF94A3B8),
            fontSize = 18.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  CAST & RECS (series)                                            */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun SeriesCastCard(member: TmdbCastMember) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(90),
        label = "s-cast-scale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
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
                    error = { SeriesCastInitial(member.name) },
                    loading = { SeriesCastInitial(member.name) },
                )
            } else {
                SeriesCastInitial(member.name)
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
        )
    }
}

@Composable
private fun SeriesCastInitial(name: String) {
    Text(
        name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        color = Cyan,
        fontSize = 22.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.Black,
    )
}

/* ──────────────────────────────────────────────────────────────── */
/*  Shared small pieces (prefixed with S to avoid name clash with   */
/*  TVMovieDetailScreen)                                            */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun SSectionHeader(text: String) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 18.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    )
}

@Composable
private fun SMetaText(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, fontFamily = Inter)
}

@Composable
private fun SMetaDot() {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.size(3.dp).background(TextDim, CircleShape))
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun SGenreChip(text: String) {
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

@Composable
private fun SRatingBadge(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0x80000000), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            color = color,
            fontSize = 12.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SCta(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(90),
        label = "scta-scale",
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
        .height(44.dp)
        .background(bg, RoundedCornerShape(8.dp))
        .border(
            2.dp,
            if (focused) Cyan else Color.Transparent,
            RoundedCornerShape(8.dp),
        )
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .clickable(onClick = onClick)
        .padding(horizontal = 18.dp)
    Row(mod, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontSize = 13.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
    }
}

private fun extractYearStr(date: String?): String {
    if (date.isNullOrBlank()) return ""
    return date.take(4).takeIf { it.toIntOrNull() != null } ?: ""
}

private fun openYoutubeExt(ctx: Context, videoId: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId"),
    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { ctx.startActivity(intent) }
}
