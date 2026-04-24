package com.hushtv.tv.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.LiveSessionStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.WatchProgressStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.screens.home.rememberDiscoveryCards
import com.hushtv.tv.ui.screens.home.rememberGenres
import com.hushtv.tv.ui.screens.home.rememberMovieCollections
import com.hushtv.tv.ui.screens.home.rememberMovieYears
import com.hushtv.tv.ui.screens.home.rememberStreamingServices
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mobile Home — mirrors the TV AnimatedContent pager but swipes
 * horizontally (thumb-native). Eight themed pages that the user
 * flips through:
 *   1. (optional) Continue Watching
 *   2. Discovery (curated picks)
 *   3. Streaming Services · Movies
 *   4. Streaming Services · Series
 *   5. Collections (franchises)
 *   6. Genres · Movies
 *   7. Genres · Series
 *   8. Years · Movies
 *
 * Each page is a full-screen themed surface with a hero + tile/rail
 * of tappable entries, all feeding into the same Xtream deep-links
 * the TV app uses.
 */
@Composable
fun MobileHomeScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    // ── Shared data for all pages (reused from TV) ──
    val ssMovies = rememberStreamingServices("movie")
    val ssSeries = rememberStreamingServices("series")
    val collections = rememberMovieCollections()
    val genresMovies = rememberGenres("movie")
    val genresSeries = rememberGenres("series")
    val movieYears = rememberMovieYears()
    val discoveryCards = rememberDiscoveryCards(playlistId)

    // ── Continue Watching state — version-counter pattern so we
    //    can refresh on (a) user removing an entry and (b) the
    //    activity returning from the player screen (ON_RESUME).
    //    Mirrors the TV rememberContinueEntries() contract. ──
    var cwVersion by remember(playlistId) { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cwVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val cwEntries = remember(cwVersion, playlistId) {
        WatchProgressStore.continueWatching(ctx).take(12)
    }
    val hasCw = cwEntries.isNotEmpty()

    // Resume Live — pulled from LiveSessionStore. Re-read on every
    // ON_RESUME (same lifecycle observer above) so returning from
    // Live Hub / Player updates the card without a manual refresh.
    val resumeLiveSid = remember(cwVersion, playlistId) {
        LiveSessionStore.getStreamId(ctx, playlistId)
    }
    val resumeLiveName = remember(cwVersion, playlistId) {
        LiveSessionStore.getChannelName(ctx, playlistId)
    }
    val resumeLivePoster = remember(cwVersion, playlistId) {
        LiveSessionStore.getPoster(ctx, playlistId)
    }
    val hasResumeLive = resumeLiveSid > 0 && resumeLiveName.isNotBlank()

    // ── Pages ──
    data class PageDef(val id: String, val kicker: String, val title: String, val accent: Color)
    val pages = buildList {
        if (hasResumeLive) add(PageDef("resume_live", "LAST WATCHING", "Resume Live", Color(0xFFEF4444)))
        if (hasCw) add(PageDef("cw", "PICK UP WHERE YOU LEFT OFF", "Continue Watching", Color(0xFFFACC15)))
        add(PageDef("discovery", "CURATED FOR YOU", "Discover", Cyan))
        add(PageDef("ss_movies", "STREAMING SERVICES", "Movies", Color(0xFFEF4444)))
        add(PageDef("ss_series", "STREAMING SERVICES", "Series", Color(0xFF22D3EE)))
        add(PageDef("collections", "FRANCHISES & SAGAS", "Collections", Color(0xFFA855F7)))
        add(PageDef("genres_movies", "GENRES", "Movies by genre", Color(0xFFF97316)))
        add(PageDef("genres_series", "GENRES", "Series by genre", Color(0xFF14B8A6)))
        add(PageDef("years_movies", "BY YEAR", "Movies", Color(0xFF3B82F6)))
    }

    val pagerState = rememberPagerState { pages.size }

    Column(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        // ── Header ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "HushTV",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                playlist?.name ?: "",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }

        // ── Page dots ──
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { idx, _ ->
                val selected = idx == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(
                            width = if (selected) 20.dp else 6.dp,
                            height = 6.dp,
                        )
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) Cyan else Color(0x33FFFFFF))
                )
            }
        }

        // ── Pager ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 0.dp,
        ) { pageIdx ->
            val def = pages[pageIdx]
            // Title block re-used by each page.
            val titleBlock: @Composable () -> Unit = {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        def.kicker,
                        color = def.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        def.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 30.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            when (def.id) {
                "resume_live" -> ResumeLivePage(
                    nav = nav,
                    playlistId = playlistId,
                    streamId = resumeLiveSid,
                    channelName = resumeLiveName,
                    poster = resumeLivePoster.ifBlank { null },
                    titleBlock = titleBlock,
                )
                "cw" -> ContinueWatchingPage(
                    nav = nav,
                    playlistId = playlistId,
                    entries = cwEntries,
                    titleBlock = titleBlock,
                    onRemove = { e ->
                        WatchProgressStore.clear(ctx, e.streamId, e.kind)
                        cwVersion++
                    },
                )
                "discovery" -> DiscoveryPageMobile(nav, playlistId, discoveryCards, titleBlock)
                "ss_movies" -> StreamingServicesPage(nav, playlistId, ssMovies, "movie", titleBlock)
                "ss_series" -> StreamingServicesPage(nav, playlistId, ssSeries, "series", titleBlock)
                "collections" -> CollectionsPageMobile(nav, playlistId, collections, titleBlock)
                "genres_movies" -> GenresPageMobile(nav, playlistId, genresMovies, "movie", titleBlock)
                "genres_series" -> GenresPageMobile(nav, playlistId, genresSeries, "series", titleBlock)
                "years_movies" -> YearsPageMobile(nav, playlistId, movieYears, titleBlock)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PAGE: Resume Live
// ══════════════════════════════════════════════════════════════════

@Composable
private fun ResumeLivePage(
    nav: NavController,
    playlistId: String,
    streamId: Int,
    channelName: String,
    poster: String?,
    titleBlock: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    val launchLive: () -> Unit = lf@ {
        val p = playlist ?: return@lf
        val url = XtreamApi.liveUrl(p.host, p.username, p.password, streamId)
        nav.navigate(
            mobilePlayerRoute(
                playlistId = playlistId,
                streamUrl = url,
                channelName = channelName,
                isLive = true,
            ),
        )
    }

    Column(Modifier.fillMaxSize()) {
        titleBlock()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A0F14), Color(0xFF0A1220))
                    )
                )
                .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .clickable(onClick = launchLive)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Channel logo / fallback monogram
                Box(
                    Modifier
                        .size(width = 92.dp, height = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1F2937)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!poster.isNullOrBlank()) {
                        AsyncImage(
                            model = poster,
                            contentDescription = channelName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            channelName.take(2).uppercase(),
                            color = Color(0xFF94A3B8),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    // LIVE badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFEF4444))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "TAP TO RESUME",
                            color = Color(0xFFEF4444),
                            fontSize = 9.sp,
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        channelName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Big round play button
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PAGE: Continue Watching
// ══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingPage(
    nav: NavController,
    playlistId: String,
    entries: List<WatchProgressStore.Entry>,
    titleBlock: @Composable () -> Unit,
    onRemove: (WatchProgressStore.Entry) -> Unit,
) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }
    // Long-press target — when non-null we render a bottom dialog with
    // the "Remove from Continue Watching" action.
    var actionEntry by remember { mutableStateOf<WatchProgressStore.Entry?>(null) }

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item { titleBlock() }
        items(entries, key = { "cw-${it.kind}-${it.streamId}" }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A1220))
                    .combinedClickable(
                        onClick = {
                            val p = playlist ?: return@combinedClickable
                            val url = when (e.kind) {
                                "live" -> XtreamApi.liveUrl(p.host, p.username, p.password, e.streamId)
                                "series" -> XtreamApi.episodeUrl(p.host, p.username, p.password, e.streamId.toString(), null)
                                else -> XtreamApi.movieUrl(p.host, p.username, p.password, e.streamId, null)
                            }
                            // Pass vod* params so MobilePlayerScreen can
                            // seek to the saved position. Live channels
                            // don't need resume — leave the params null.
                            val isLive = e.kind == "live"
                            nav.navigate(
                                mobilePlayerRoute(
                                    playlistId = playlistId,
                                    streamUrl = url,
                                    channelName = e.title,
                                    isLive = isLive,
                                    vodStreamId = if (isLive) null else e.streamId,
                                    vodKind = if (isLive) null else e.kind,
                                    vodPoster = if (isLive) null else e.poster,
                                ),
                            )
                        },
                        onLongClick = { actionEntry = e },
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 96.dp, height = 54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1F2937)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!e.poster.isNullOrBlank()) {
                        AsyncImage(
                            model = e.poster, contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (e.durationMs > 0) {
                        Spacer(Modifier.height(4.dp))
                        val pct = (e.positionMs.toFloat() / e.durationMs.toFloat()).coerceIn(0f, 1f)
                        Box(
                            Modifier.fillMaxWidth().height(2.dp)
                                .background(Color(0x22FFFFFF), RoundedCornerShape(2.dp)),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(pct).height(2.dp)
                                    .background(Cyan, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Long-press action sheet ──
    val entry = actionEntry
    if (entry != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { actionEntry = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0B1220))
                    .padding(16.dp),
            ) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Continue Watching",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onRemove(entry)
                            actionEntry = null
                        }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Delete, null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Remove from Continue Watching",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PAGE: Discovery
// ══════════════════════════════════════════════════════════════════

@Composable
private fun DiscoveryPageMobile(
    nav: NavController,
    playlistId: String,
    cards: List<com.hushtv.tv.ui.screens.home.DiscoveryCard>,
    titleBlock: @Composable () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item { titleBlock() }
        items(cards, key = { it.id }) { card ->
            MobileDiscoveryCard(card) {
                // Deep-link into the matching browse filter.
                val type = if (card.type == "series") "series" else "movie"
                nav.navigate("mbrowse/$playlistId/$type")
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MobileDiscoveryCard(
    card: com.hushtv.tv.ui.screens.home.DiscoveryCard,
    onClick: () -> Unit,
) {
    val bg = card.heroArt.firstOrNull()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A1220))
            .clickable(onClick = onClick),
    ) {
        if (!bg.isNullOrBlank()) {
            AsyncImage(
                model = bg, contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to Color(0xE0000000),
                0.6f to Color(0x33000000),
                1f to Color.Transparent,
            ))
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(16.dp)
                .fillMaxWidth(0.75f),
        ) {
            Text(
                card.eyebrow.uppercase(),
                color = Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                card.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                card.subtitle,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BROWSE",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, tint = Cyan, modifier = Modifier.size(12.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PAGE: Streaming Services
// ══════════════════════════════════════════════════════════════════

@Composable
private fun StreamingServicesPage(
    nav: NavController,
    playlistId: String,
    services: List<com.hushtv.tv.ui.screens.home.StreamingService>,
    kind: String,
    titleBlock: @Composable () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item { titleBlock() }
        items(services.chunked(2).withIndex().toList(), key = { it.index }) { (_, pair) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { svc ->
                    SsTile(
                        service = svc,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val catId = if (kind == "series") svc.xtreamSeriesCategoryId
                            else svc.xtreamMovieCategoryId
                            if (!catId.isNullOrBlank())
                                nav.navigate("mbrowse/$playlistId/$kind?catId=$catId")
                            else
                                nav.navigate("mbrowse/$playlistId/$kind")
                        },
                    )
                }
                // Pad the row if odd count.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SsTile(
    service: com.hushtv.tv.ui.screens.home.StreamingService,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(service.brandTop, service.brandBottom)))
            .border(1.dp, service.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!service.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = service.logoUrl, contentDescription = service.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                service.displayName.uppercase(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PAGE: Collections
// ══════════════════════════════════════════════════════════════════

@Composable
private fun CollectionsPageMobile(
    nav: NavController,
    playlistId: String,
    collections: List<com.hushtv.tv.ui.screens.home.MovieCollection>,
    titleBlock: @Composable () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item { titleBlock() }
        items(collections, key = { it.id }) { col ->
            BackdropCard(
                title = col.displayName,
                tagline = col.tagline,
                backdropUrl = col.backdropUrl,
                accent = col.accent,
                onClick = {
                    // Open the TMDB-backed franchise detail with
                    // library matching — same flow as the TV app.
                    nav.navigate(
                        "mcollection/$playlistId/${col.tmdbCollectionId}/" +
                            android.net.Uri.encode(col.displayName)
                    )
                },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PAGE: Genres
// ══════════════════════════════════════════════════════════════════

@Composable
private fun GenresPageMobile(
    nav: NavController,
    playlistId: String,
    genres: List<com.hushtv.tv.ui.screens.home.Genre>,
    kind: String,
    titleBlock: @Composable () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item { titleBlock() }
        items(genres, key = { it.id }) { g ->
            BackdropCard(
                title = g.displayName,
                tagline = g.tagline,
                backdropUrl = g.backdropUrl,
                accent = g.accent,
                onClick = {
                    val catId = g.xtreamCategoryId
                    if (!catId.isNullOrBlank())
                        nav.navigate("mbrowse/$playlistId/$kind?catId=$catId")
                    else
                        nav.navigate("mbrowse/$playlistId/$kind")
                },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PAGE: Years
// ══════════════════════════════════════════════════════════════════

@Composable
private fun YearsPageMobile(
    nav: NavController,
    playlistId: String,
    years: List<com.hushtv.tv.ui.screens.home.MovieYear>,
    titleBlock: @Composable () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item { titleBlock() }
        items(years, key = { it.year }) { y ->
            BackdropCard(
                title = y.year.toString(),
                tagline = y.tagline,
                backdropUrl = y.backdropUrl,
                accent = y.accent,
                onClick = {
                    val catId = y.xtreamCategoryId
                    if (!catId.isNullOrBlank())
                        nav.navigate("mbrowse/$playlistId/movie?catId=$catId")
                    else
                        nav.navigate("mbrowse/$playlistId/movie")
                },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Shared card — cinematic backdrop + title + tagline + CTA
// ══════════════════════════════════════════════════════════════════

@Composable
private fun BackdropCard(
    title: String,
    tagline: String,
    backdropUrl: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A1220))
            .clickable(onClick = onClick),
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.3f), Color(0xFF0A1220))
                ))
            )
        }
        Box(
            Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to Color(0xE0000000),
                0.55f to Color(0x33000000),
                1f to Color.Transparent,
            ))
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(16.dp)
                .fillMaxWidth(0.7f),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tagline,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "OPEN",
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.ArrowForward, null, tint = accent, modifier = Modifier.size(12.dp))
            }
        }
    }
}
