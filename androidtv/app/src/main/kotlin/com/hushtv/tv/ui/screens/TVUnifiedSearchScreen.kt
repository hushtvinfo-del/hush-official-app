@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.TitleMatcher
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.screens.home.MovieCollection
import com.hushtv.tv.ui.screens.home.TopNavBar
import com.hushtv.tv.ui.screens.home.TopNavTab
import com.hushtv.tv.ui.screens.home.rememberMovieCollections
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Unified "Search Everything" screen.
 *
 * One query → parallel scan across four sources on the Xtream provider
 *     • Live channels
 *     • Movies (VOD)
 *     • Series
 * ...plus the local Collections catalog in-memory. Results are grouped
 * and displayed as horizontal rails — Netflix-style — so users see
 * what kind of content matched at a glance.
 *
 * Filtering: matches when any normalised word of the query is a prefix
 * of any normalised word in the title, OR a substring of the full
 * normalised title. This is faster than the strict franchise matcher
 * and more permissive — fine for an interactive search experience.
 */
@Composable
fun TVUnifiedSearchScreen(
    nav: NavController,
    playlistId: String,
) {
    val ctx = LocalContext.current
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    // ── Preloaded indices (one shot per session) ──
    var liveAll by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var moviesAll by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var seriesAll by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    val collectionsAll = rememberMovieCollections()
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val live = async { runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "live") }.getOrDefault(emptyList()) }
            val mov  = async { runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "movie") }.getOrDefault(emptyList()) }
            val ser  = async { runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "series") }.getOrDefault(emptyList()) }
            liveAll = live.await()
            moviesAll = mov.await()
            seriesAll = ser.await()
        }
        loading = false
    }

    // ── Query + filtered buckets ──
    var query by remember { mutableStateOf("") }
    val liveHits = rememberFiltered(query, liveAll) { it.title }
    val moviesHits = rememberFiltered(query, moviesAll) { it.title }
    val seriesHits = rememberFiltered(query, seriesAll) { it.title }
    val collectionsHits = rememberFiltered(query, collectionsAll) { it.displayName }

    val totalHits = liveHits.size + moviesHits.size + seriesHits.size + collectionsHits.size

    // ── Focus + TopNav ──
    val tabs = remember {
        listOf(
            TopNavTab("home",   "Home",    Icons.Default.Home,       "menu/$playlistId"),
            TopNavTab("live",   "Live TV", Icons.Default.Tv,         "browse/$playlistId/live"),
            TopNavTab("movies", "Movies",  Icons.Default.Movie,      "browse/$playlistId/movie"),
            TopNavTab("series", "Series",  Icons.Outlined.Slideshow, "browse/$playlistId/series"),
            TopNavTab("search", "Search",  Icons.Default.Search,     "search/$playlistId"),
        )
    }
    val homeFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstLiveFocus = remember { FocusRequester() }
    val firstMovieFocus = remember { FocusRequester() }
    val firstSeriesFocus = remember { FocusRequester() }
    val firstCollFocus = remember { FocusRequester() }

    // Auto-focus search on entry.
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { searchFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 72.dp),
        ) {
            // ── Toolbar ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 26.dp)
                        .background(Cyan, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "SEARCH EVERYTHING",
                        color = Cyan,
                        fontSize = 10.sp,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (query.isBlank()) "Channels · Movies · Series · Franchises"
                        else "$totalHits results for \"$query\"",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                UnifiedSearchBar(
                    value = query,
                    onChange = { query = it },
                    focusRequester = searchFocus,
                    // Primary DOWN target: first bucket that has results.
                    downTarget = when {
                        liveHits.isNotEmpty() -> firstLiveFocus
                        moviesHits.isNotEmpty() -> firstMovieFocus
                        seriesHits.isNotEmpty() -> firstSeriesFocus
                        collectionsHits.isNotEmpty() -> firstCollFocus
                        else -> searchFocus
                    },
                    modifier = Modifier.width(520.dp),
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

            // ── Results or empty state ──
            when {
                loading -> CenterNote("Indexing your library…")
                query.isBlank() -> CenterNote("Type anything — channels, movies, series or franchises.")
                totalHits == 0 -> CenterNote("Nothing matches \"$query\".")
                else -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        contentPadding = PaddingValues(vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        if (liveHits.isNotEmpty()) item("live") {
                            SearchResultRow(
                                label = "LIVE CHANNELS · ${liveHits.size}",
                                accent = Color(0xFFEF4444),
                                items = liveHits,
                                firstItemFocus = firstLiveFocus,
                            ) { mc ->
                                PosterCard(
                                    title = mc.title,
                                    image = mc.poster,
                                    badge = "LIVE",
                                    badgeTint = Color(0xFFEF4444),
                                    onClick = {
                                        val host = playlist?.host ?: return@PosterCard
                                        val url = "$host/${playlist.username}/${playlist.password}/${mc.streamId}.ts"
                                        nav.navigate(
                                            "player/$playlistId/${Uri.encode(url)}/${Uri.encode(mc.title)}/true"
                                        )
                                    },
                                )
                            }
                        }
                        if (moviesHits.isNotEmpty()) item("movies") {
                            SearchResultRow(
                                label = "MOVIES · ${moviesHits.size}",
                                accent = Cyan,
                                items = moviesHits,
                                firstItemFocus = firstMovieFocus,
                            ) { mc ->
                                PosterCard(
                                    title = mc.title,
                                    image = mc.poster,
                                    badge = "MOVIE",
                                    badgeTint = Cyan,
                                    onClick = {
                                        nav.navigate(
                                            "moviedetail/$playlistId/${mc.streamId}/${Uri.encode(mc.title)}"
                                        )
                                    },
                                )
                            }
                        }
                        if (seriesHits.isNotEmpty()) item("series") {
                            SearchResultRow(
                                label = "SERIES · ${seriesHits.size}",
                                accent = Color(0xFF8B5CF6),
                                items = seriesHits,
                                firstItemFocus = firstSeriesFocus,
                            ) { mc ->
                                PosterCard(
                                    title = mc.title,
                                    image = mc.poster,
                                    badge = "SERIES",
                                    badgeTint = Color(0xFF8B5CF6),
                                    onClick = {
                                        nav.navigate(
                                            "seriesdetail/$playlistId/${mc.seriesId}/${Uri.encode(mc.title)}"
                                        )
                                    },
                                )
                            }
                        }
                        if (collectionsHits.isNotEmpty()) item("coll") {
                            SearchResultRow(
                                label = "FRANCHISES · ${collectionsHits.size}",
                                accent = Color(0xFFF97316),
                                items = collectionsHits,
                                firstItemFocus = firstCollFocus,
                            ) { coll ->
                                CollectionPosterCard(
                                    coll = coll,
                                    onClick = {
                                        nav.navigate(
                                            "collection/$playlistId/${coll.tmdbCollectionId}/${Uri.encode(coll.displayName)}"
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top nav.
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            TopNavBar(
                tabs = tabs,
                activeKey = "search",
                homeFocus = homeFocus,
                onTab = { t -> t.route?.let { nav.navigate(it) } },
                onSettings = { nav.navigate("settings/$playlistId") },
            )
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

@Composable
private fun <T> rememberFiltered(
    query: String,
    source: List<T>,
    titleOf: (T) -> String,
): List<T> {
    val state = remember(query, source) {
        derivedStateOf {
            if (query.isBlank()) return@derivedStateOf emptyList<T>()
            val q = TitleMatcher.normalize(query)
            if (q.isBlank()) return@derivedStateOf emptyList<T>()
            val queryWords = q.split(" ").filter { it.isNotBlank() }
            source.asSequence().filter { item ->
                val norm = TitleMatcher.normalize(titleOf(item))
                if (norm.isBlank()) return@filter false
                if (norm.contains(q)) return@filter true
                val words = norm.split(" ")
                queryWords.all { qw -> words.any { it.startsWith(qw) } }
            }.take(40).toList()
        }
    }
    return state.value
}

@Composable
private fun CenterNote(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            msg,
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun <T> SearchResultRow(
    label: String,
    accent: Color,
    items: List<T>,
    firstItemFocus: FocusRequester,
    cardContent: @Composable (T) -> Unit,
) {
    Column(Modifier.padding(start = 40.dp, end = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 14.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items) { item ->
                val mod = if (item == items.firstOrNull())
                    Modifier.focusRequester(firstItemFocus) else Modifier
                Box(mod) { cardContent(item) }
            }
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    image: String?,
    badge: String,
    badgeTint: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(124.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1.05f, shape = shape)
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Cyan else Color.Transparent,
                    shape = shape,
                ),
        ) {
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
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
                        title,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xCC0B1220), RoundedCornerShape(6.dp))
                    .border(1.dp, badgeTint.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    badge,
                    color = badgeTint,
                    fontSize = 8.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionPosterCard(
    coll: MovieCollection,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(180.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1.05f, shape = shape)
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) coll.accent else Color.Transparent,
                    shape = shape,
                ),
        ) {
            coll.backdropUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = coll.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x33000000),
                            1f to Color(0xEB000000),
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(coll.accent, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "FRANCHISE",
                        color = Color.White,
                        fontSize = 8.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    coll.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UnifiedSearchBar(
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    downTarget: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .background(SurfaceNavy, RoundedCornerShape(12.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) Cyan else TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Inter),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusProperties { down = downTarget }
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                            runCatching { downTarget.requestFocus() }
                            true
                        } else false
                    },
            )
            if (value.isEmpty()) {
                Text(
                    "Search across all of HushTV…",
                    color = TextMuted,
                    fontSize = 16.sp,
                    fontFamily = Inter,
                )
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .focusable()
                    .focusProperties { down = downTarget }
                    .clickable { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
