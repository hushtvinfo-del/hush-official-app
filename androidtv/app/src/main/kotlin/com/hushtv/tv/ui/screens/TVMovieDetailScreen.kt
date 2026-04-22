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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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

    // Shared scroll state — we force it back to 0 whenever Play gains focus
    // (on entry AND when the user navigates back UP) so the poster is always
    // in view when focused on a top-level action.
    val scrollState = rememberScrollState()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(tmdbMovie != null || !loading) {
        kotlinx.coroutines.delay(60)
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
        // ── Backdrop (no runtime blur — use a low-alpha image + vignette
        // gradient instead. blur() is a GPU killer on many TV boxes.) ─
        if (!backdropUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = backdropUrl,
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
                            colors = listOf(
                                Color(0xAA000000),
                                Color(0xDD000000),
                                Color(0xFF000000),
                            ),
                        )
                    )
            )
        }

        // ── Back button — just the back button, no other actions here ────
        Box(
            Modifier
                .padding(start = 16.dp, top = 14.dp)
                .align(Alignment.TopStart)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x88000000))
                .border(2.dp, Color(0x55FFFFFF), CircleShape)
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
                .padding(start = 72.dp, end = 56.dp, top = 80.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Hero row — natural height so content never gets clipped.
            //    The verticalScroll can still scroll UP if the user moves
            //    focus down to the cast/recs sections.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
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

                    // CTAs — placed HIGH in the info column so they sit in
                    // the initial viewport (no scroll on focus). Play is
                    // pinned to scrollState so UP from anywhere brings user
                    // back to top.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroCta(
                            label = "Play",
                            icon = Icons.Default.PlayArrow,
                            primary = true,
                            focusRequester = playFocus,
                            onFocusGained = {
                                // Snap the scroll back to the top whenever
                                // Play regains focus — so users can always
                                // get the poster back into view.
                                scope.launch { scrollState.animateScrollTo(0) }
                            },
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
                        val trailerKeyNow = TmdbService.pickTrailer(tmdbMovie?.videos)
                        trailerKeyNow?.let { k ->
                            HeroCta(
                                label = "Trailer",
                                icon = Icons.Default.PlayCircle,
                                onClick = { openYoutube(ctx, k) },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

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
                    }
                    // CTAs are rendered in the FIXED TOP BAR (outside verticalScroll)
                    // so that auto-focusing Play doesn't cause the page to scroll
                    // and clip the poster. See ActionBar below.
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
                        RecommendationCard(rec = r) {
                            // Try to find this title in the user's library.
                            // If found, open its detail. If not, fallback to a
                            // cast dialog-style "not in library" notice.
                            val title = (r.title ?: r.name).orEmpty()
                            val libMatch = matchLibraryByTitles(listOf(title), library).firstOrNull()
                            if (libMatch != null) {
                                when (libMatch.kind) {
                                    "movie" -> nav.navigate("moviedetail/$playlistId/${libMatch.streamId}/${Uri.encode(libMatch.title)}")
                                    "series" -> nav.navigate("series/$playlistId/${libMatch.seriesId}/${Uri.encode(libMatch.title)}")
                                }
                            } else {
                                // Show a toast-like temporary notice via dialog
                                castDialog = CastDialogState(
                                    TmdbCastMember(
                                        id = 0,
                                        name = title,
                                        character = "Not in your library",
                                    ),
                                    emptyList(),
                                    loading = false,
                                    notice = "$title isn't in your library yet.",
                                )
                            }
                        }
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
    // Inline overlay — NOT androidx.compose.ui.window.Dialog, which has
    // known focus-transfer issues on Android TV. This overlay lives in
    // the same composition as the detail screen so focus requesters work.
    val firstFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(state.matches.size, state.loading) {
        kotlinx.coroutines.delay(80)
        runCatching {
            if (state.matches.isNotEmpty()) firstFocus.requestFocus()
            else closeFocus.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            // BACK key dismisses
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Back || ev.key == Key.Escape)
                ) {
                    onDismiss(); true
                } else false
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 1100.dp)
                .fillMaxWidth(0.82f)
                .heightIn(max = 640.dp)
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
                            error = {
                                Text(
                                    state.member.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black,
                                )
                            },
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
                        if (state.matches.isEmpty() && !state.loading) "Not in your library"
                        else "Also in your library",
                        color = Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.3.sp,
                    )
                }
                // Close button (always reachable)
                Box(
                    Modifier
                        .size(40.dp)
                        .focusRequester(closeFocus)
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown &&
                                (ev.key == Key.Enter || ev.key == Key.DirectionCenter || ev.key == Key.NumPadEnter)
                            ) {
                                onDismiss(); true
                            } else false
                        }
                        .focusable()
                        .clickable(onClick = onDismiss)
                        .background(Color(0x1AFFFFFF), CircleShape)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(18.dp))

            when {
                state.loading -> {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
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
                            state.notice ?: "No other titles with ${state.member.name} in your library.",
                            color = TextMuted,
                            fontSize = 14.sp,
                        )
                    }
                }
                else -> {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    ) {
                        items(
                            count = state.matches.size,
                            key = { idx -> "fmg-${state.matches[idx].kind}-${state.matches[idx].id}" },
                        ) { idx ->
                            val item = state.matches[idx]
                            val mod = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                            FilmographyCard(
                                modifier = mod,
                                item = item,
                                onClick = { onTitleClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmographyCard(
    modifier: Modifier = Modifier,
    item: MediaCard,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(90),
        label = "fmg-scale",
    )
    Column(
        modifier
            .width(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Enter || ev.key == Key.DirectionCenter || ev.key == Key.NumPadEnter)
                ) {
                    onClick(); true
                } else false
            }
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

/** Cross-reference a list of title strings from TMDB against the library.
 *
 *  Xtream catalog titles typically have noisy prefixes like `"US | "`,
 *  `"EN: "`, `"[VIP]"`, country flags, quality tags (`4K`, `HD`, `FHD`),
 *  year suffixes (`(2018)`, ` 2024`), and various punctuation. TMDB returns
 *  clean titles like `"Den of Thieves"`. We normalise both sides heavily and
 *  match by both exact and containment (in either direction).
 */
private fun matchLibraryByTitles(
    titles: List<String>,
    library: List<MediaCard>,
): List<MediaCard> {
    if (titles.isEmpty() || library.isEmpty()) return emptyList()

    // Pre-normalise the library once.
    val libNorm: List<Pair<MediaCard, String>> = library.map { it to normaliseTitle(it.title) }
    val libByExact: Map<String, MediaCard> = libNorm.associate { (card, n) -> n to card }

    val seen = mutableSetOf<String>()
    val out = mutableListOf<MediaCard>()

    fun addIfNew(card: MediaCard) {
        if (seen.add("${card.kind}-${card.id}")) out += card
    }

    for (raw in titles) {
        val t = normaliseTitle(raw)
        if (t.length < 2) continue

        // 1. Exact normalised match (fast path).
        val exactHit = libByExact[t]
        if (exactHit != null) {
            addIfNew(exactHit)
            continue
        }

        // 2. Substring match in either direction — whole-word boundary.
        //    Require a minimum length so short titles like "Up" don't
        //    collide with everything.
        if (t.length < 3) continue
        val pattern = " $t "
        val hit = libNorm.firstOrNull { (_, lt) ->
            val padded = " $lt "
            padded.contains(pattern) ||
                // Library title IS the TMDB title plus a suffix/prefix.
                padded.startsWith("$t ") ||
                padded.endsWith(" $t") ||
                // TMDB title IS the library title plus extra (e.g. library has
                // "Den of Thieves 2" and TMDB filmography has "Den of Thieves").
                (lt.length >= 4 && (" $t ").contains(" $lt "))
        }?.first
        if (hit != null) addIfNew(hit)
    }

    return out.take(30)
}

/**
 * Normalise a noisy Xtream or TMDB title for fuzzy matching.
 *
 *  "US | Den of Thieves (2018)"   → "den of thieves"
 *  "[EN] Den of Thieves 4K"       → "den of thieves"
 *  "VIP: Plane (2023) FHD"        → "plane"
 *  "Den of Thieves"               → "den of thieves"
 */
private fun normaliseTitle(raw: String): String {
    var t = raw.trim().lowercase()

    // Strip bracketed / parenthesised prefixes FIRST, before generic
    // leading-punctuation cleanup removes the opening bracket.
    //   "[EN] Den of Thieves"    → "Den of Thieves"
    //   "(VIP) Plane"            → "Plane"
    t = t.replace(Regex("""^\[[^\]]*\]\s*"""), "")
    t = t.replace(Regex("""^\([^)]*\)\s*"""), "")
    t = t.replace(Regex("""^\{[^}]*\}\s*"""), "")

    // Strip leading flag emojis / other decorative unicode.
    t = t.replace(Regex("^[^a-z0-9]+"), "")

    // Strip 1–4 letter/language codes followed by separator: "US | ", "EN - ", "PT: "
    // Repeat up to 3 times for stacked prefixes like "US | VIP | 4K - "
    repeat(3) {
        t = t.replace(Regex("^[a-z]{1,4}\\s*[|:\\-·•]+\\s*"), "")
        t = t.replace(
            Regex(
                "^(vip|hd|fhd|uhd|sd|4k|hq|dolby|imax|atmos|dual|multi|english|dub(bed)?|sub(bed|titled)?)\\s*[|:\\-·•]?\\s*"
            ),
            ""
        )
    }

    // Strip year in parentheses anywhere: "(2018)".
    t = t.replace(Regex("\\(\\s*(19|20)\\d{2}\\s*\\)"), " ")

    // Strip bare year at the END: " 2018", "-2018".
    t = t.replace(Regex("[\\s\\-]+(19|20)\\d{2}\\s*$"), "")

    // Strip trailing quality / format tags: " 4K", " HD", " FHD"
    repeat(3) {
        t = t.replace(
            Regex("[\\s\\-|·•]+(vip|hd|fhd|uhd|sd|4k|hq|dolby|imax|atmos|dual|multi)\\s*$"),
            ""
        )
    }

    // Replace any remaining punctuation with space.
    t = t.replace(Regex("[^a-z0-9\\s]"), " ")

    // Collapse whitespace.
    t = t.replace(Regex("\\s+"), " ").trim()

    return t
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
        animationSpec = tween(90),
        label = "cast-scale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Enter || ev.key == Key.DirectionCenter || ev.key == Key.NumPadEnter)
                ) {
                    onClick(); true
                } else false
            }
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
private fun RecommendationCard(rec: TmdbRecommendation, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(90),
        label = "rec-scale",
    )
    Column(
        modifier = Modifier
            .width(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Enter || ev.key == Key.DirectionCenter || ev.key == Key.NumPadEnter)
                ) {
                    onClick(); true
                } else false
            }
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
    onFocusGained: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(90),
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
        .onFocusChanged {
            val wasFocused = focused
            focused = it.isFocused
            if (!wasFocused && it.isFocused) onFocusGained?.invoke()
        }
        .onKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown &&
                (ev.key == Key.Enter || ev.key == Key.DirectionCenter || ev.key == Key.NumPadEnter)
            ) {
                onClick(); true
            } else false
        }
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
