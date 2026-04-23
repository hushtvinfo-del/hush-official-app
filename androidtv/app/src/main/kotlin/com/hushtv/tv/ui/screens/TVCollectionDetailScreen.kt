package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
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
import com.hushtv.tv.data.DiscoveryCache
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.TitleMatcher
import com.hushtv.tv.data.TmdbCollectionPart
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.screens.home.TopNavBar
import com.hushtv.tv.ui.screens.home.TopNavTab
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextDim
import kotlinx.coroutines.async

/**
 * A resolved franchise entry — a TMDB collection part matched against
 * the user's Xtream movie library using the strict [TitleMatcher].
 */
private data class CollectionEntry(
    val part: TmdbCollectionPart,
    val releaseYear: Int?,
    val matched: MediaCard?,
    val posterUrl: String?,
    val title: String,
)

/**
 * TVCollectionDetailScreen — shows every movie in the clicked TMDB
 * collection in CHRONOLOGICAL order.
 *
 * Flow:
 *   1. On entry → full-bleed cinematic splash with the franchise
 *      backdrop + Ken-Burns + accent loader ring while TMDB + Xtream
 *      libraries fetch in parallel.
 *   2. Once data arrives → smooth crossfade into the results grid.
 *
 * Matching uses [TitleMatcher.isStrongMatch] — EXACT normalized title
 * equality OR contiguous containment with ≥ 3-word minimums and year
 * gates. Prevents junk library titles like "Ed" / "Star" from matching
 * franchise films via coincidental substrings.
 */
@Composable
fun TVCollectionDetailScreen(
    nav: NavController,
    playlistId: String,
    tmdbCollectionId: Int,
    collectionName: String,
) {
    val ctx = LocalContext.current
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    // Grab the cached backdrop instantly so the splash paints hi-res
    // art in the first frame — no empty state ever visible.
    val cachedBackdrop = remember(tmdbCollectionId) {
        DiscoveryCache.loadCollectionBackdrop(ctx, tmdbCollectionId)
    }

    var parts by remember { mutableStateOf<List<TmdbCollectionPart>>(emptyList()) }
    var library by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Fetch the two data sources in parallel and match them.
    LaunchedEffect(tmdbCollectionId, playlistId) {
        val p = playlist ?: return@LaunchedEffect
        loading = true
        kotlinx.coroutines.coroutineScope {
            val partsDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                TmdbService.getCollectionParts(tmdbCollectionId)
            }
            val libraryDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    XtreamApi.getAllStreams(p.host, p.username, p.password, "movie")
                }.getOrDefault(emptyList())
            }
            parts = partsDeferred.await()
            library = libraryDeferred.await()
        }
        loading = false
    }

    val entries: List<CollectionEntry> = remember(parts, library) {
        if (parts.isEmpty()) return@remember emptyList()
        // Pre-index library ONCE for all collection parts — normalized
        // keys + extracted years cached per item.
        val libraryIndex = TitleMatcher.buildIndex(library) { it.title }
        parts.map { part ->
            val tmdbYear = part.release_date?.take(4)?.toIntOrNull()
            val match = TitleMatcher.findBestMatch(
                tmdbTitle = part.title,
                tmdbYear = tmdbYear,
                libraryIndex = libraryIndex,
            )
            CollectionEntry(
                part = part,
                releaseYear = tmdbYear,
                matched = match,
                // Prefer library poster (already on user's provider); fall
                // back to TMDB poster so unmatched films still show art.
                posterUrl = match?.poster ?: TmdbService.img(part.poster_path, "w500"),
                title = part.title,
            )
        }
    }

    val playlistIdForNav = playlistId
    val tabs = remember {
        listOf(
            TopNavTab("home",   "Home",    Icons.Default.Home,       "menu/$playlistIdForNav"),
            TopNavTab("live",   "Live TV", Icons.Default.Tv,         "browse/$playlistIdForNav/live"),
            TopNavTab("movies", "Movies",  Icons.Default.Movie,      "browse/$playlistIdForNav/movie"),
            TopNavTab("series", "Series",  Icons.Outlined.Slideshow, "browse/$playlistIdForNav/series"),
            TopNavTab("search", "Search",  Icons.Default.Search,     "search/$playlistIdForNav"),
        )
    }
    val homeFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(loading, entries.isNotEmpty()) {
        if (!loading && entries.isNotEmpty()) {
            kotlinx.coroutines.delay(200)
            runCatching { firstCardFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        // ── SPLASH (shown while loading) ──
        androidx.compose.animation.AnimatedVisibility(
            visible = loading,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
        ) {
            FranchiseSplash(
                collectionName = collectionName,
                backdropUrl = cachedBackdrop,
            )
        }

        // ── RESULTS (shown after loading) ──
        AnimatedVisibility(
            visible = !loading,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200)),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top nav overlay (same as other browse screens).
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
                    TopNavBar(
                        tabs = tabs,
                        activeKey = "movies",
                        homeFocus = homeFocus,
                        onTab = { t -> t.route?.let { nav.navigate(it) } },
                        onSettings = { nav.navigate("settings/$playlistIdForNav") },
                    )
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 72.dp, start = 48.dp, end = 48.dp, bottom = 24.dp),
                ) {
                    // Header — franchise name + count + chronological badge.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 18.dp, bottom = 18.dp),
                    ) {
                        Box(
                            Modifier
                                .size(width = 4.dp, height = 28.dp)
                                .background(Cyan, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "FRANCHISE · CHRONOLOGICAL",
                                color = Cyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp,
                                fontFamily = Inter,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                collectionName,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 38.sp,
                                fontFamily = Inter,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(24.dp))
                        val hits = entries.count { it.matched != null }
                        Text(
                            "$hits in your library · ${entries.size} total",
                            color = TextDim,
                            fontSize = 13.sp,
                            fontFamily = Inter,
                        )
                    }

                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No titles found for this franchise.",
                                color = TextDim,
                                fontSize = 14.sp,
                                fontFamily = Inter,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(entries, key = { it.part.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    isFirst = entry == entries.first(),
                                    firstCardFocus = firstCardFocus,
                                    onClick = click@{
                                        val m = entry.matched ?: return@click
                                        nav.navigate(
                                            "moviedetail/$playlistIdForNav/${m.streamId}/${Uri.encode(m.title)}"
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cinematic loading splash — full-bleed franchise backdrop with
 * Ken-Burns pulse + accent loader ring + franchise name + tagline.
 * Shown for the few hundred ms it takes to fetch TMDB parts + the
 * Xtream library in parallel.
 */
@Composable
private fun FranchiseSplash(
    collectionName: String,
    backdropUrl: String?,
) {
    val transition = rememberInfiniteTransition(label = "splash-kb")
    val scale by transition.animateFloat(
        initialValue = 1.04f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
        ),
        label = "splash-scale",
    )
    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "splash-ring",
    )

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        // ── Backdrop layer ──
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to Color(0xFF0E1422),
                            1.0f to Color(0xFF05080F),
                        )
                    )
            )
        }

        // Darkening veils — heavier than the home hero since the splash
        // content is centered and needs uniform legibility.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0x4D000000),
                        1.0f to Color(0xE6000000),
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xB3000000),
                        0.5f to Color(0x66000000),
                        1.0f to Color(0xEE000000),
                    )
                )
        )

        // ── Centered splash copy ──
        Column(
            Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "FRANCHISE · CHRONOLOGICAL",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                collectionName,
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 60.sp,
                fontFamily = Inter,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Scanning your library — we'll line every film up in release order.",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                fontFamily = Inter,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(30.dp))
            // Accent loader ring — 3 dp cyan arc that spins.
            Box(
                Modifier.size(46.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .graphicsLayer { rotationZ = ringRotation }
                        .border(3.dp, Cyan.copy(alpha = 0.85f), CircleShape)
                        .background(Color.Transparent, CircleShape),
                )
                // Cut-out arc — small inner dot for extra polish.
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Cyan, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: CollectionEntry,
    isFirst: Boolean,
    firstCardFocus: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(90),
        label = "coll-entry-scale",
    )
    val available = entry.matched != null
    val focusMod = if (isFirst) Modifier.focusRequester(firstCardFocus) else Modifier

    Column(
        modifier = focusMod
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Cyan else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            if (!entry.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.posterUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0B1220))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        entry.title,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            // Not-in-library veil + badge.
            if (!available) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xDD0B1220), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x6606B6D4), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        "LOCKED",
                        color = Color.White,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                }
            }

            // Year chip bottom-left.
            entry.releaseYear?.let { yr ->
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color(0xCC0B1220), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        yr.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.title,
            color = if (available) Color.White else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
