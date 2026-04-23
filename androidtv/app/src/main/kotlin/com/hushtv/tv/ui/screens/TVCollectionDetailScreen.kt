package com.hushtv.tv.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
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
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
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
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.async

/**
 * A resolved franchise entry — a TMDB collection part matched against
 * the user's Xtream movie library so clicking it opens the correct
 * stream, NOT a dead-end detail page.
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
 * collection, CHRONOLOGICALLY (oldest first). Each entry matched
 * against the Xtream library plays through the normal movie detail
 * flow; entries not in the library still render as locked posters so
 * the user can see what's missing from their provider.
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
        // Pre-build a normalised-title → MediaCard lookup. We keep a
        // list of (normKey, card) since multiple library entries may
        // normalise to the same key (different quality / language
        // prints of the same film) — we take the first hit.
        val libNorms = library.map { normaliseTitle(it.title) to it }
        parts.map { part ->
            val partNorm = normaliseTitle(part.title)
            val match = libNorms.firstOrNull { (ln, _) ->
                ln.isNotBlank() && (ln == partNorm ||
                    ln.contains(partNorm) ||
                    partNorm.contains(ln))
            }?.second
            val yr = part.release_date?.take(4)?.toIntOrNull()
            CollectionEntry(
                part = part,
                releaseYear = yr,
                matched = match,
                // Prefer library poster (already on user's provider) — if
                // nothing matched, fall back to TMDB poster so user sees
                // the film's artwork either way.
                posterUrl = match?.poster ?: TmdbService.img(part.poster_path, "w500"),
                title = part.title,
            )
        }
        // TmdbService.getCollectionParts already sorts by release_date
        // ascending; we preserve that order (with null release dates
        // sinking to the end via getOrDefault("9999")).
    }

    val playlistIdForNav = playlistId
    val tabs = remember {
        listOf(
            TopNavTab("home",   "Home",    Icons.Default.Home,       "menu/$playlistIdForNav"),
            TopNavTab("live",   "Live TV", Icons.Default.Tv,         "browse/$playlistIdForNav/live"),
            TopNavTab("movies", "Movies",  Icons.Default.Movie,      "browse/$playlistIdForNav/movie"),
            TopNavTab("series", "Series",  Icons.Outlined.Slideshow, "browse/$playlistIdForNav/series"),
            TopNavTab("search", "Search",  Icons.Default.Search,     "browse/$playlistIdForNav/search"),
        )
    }
    val homeFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            runCatching { firstCardFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "FRANCHISE · CHRONOLOGICAL",
                            color = Cyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            fontFamily = Inter,
                        )
                    }
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
                if (!loading) {
                    val hits = entries.count { it.matched != null }
                    Text(
                        "$hits in your library · ${entries.size} total",
                        color = TextDim,
                        fontSize = 13.sp,
                        fontFamily = Inter,
                    )
                }
            }

            if (loading && entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Loading franchise…",
                        color = TextDim,
                        fontSize = 14.sp,
                        fontFamily = Inter,
                    )
                }
            } else if (entries.isEmpty()) {
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

/**
 * Aggressive title normaliser for franchise matching:
 * - lowercases
 * - strips leading "[EN]", "US |", quality tags (4K, HD, FHD, UHD…)
 * - strips trailing years "(2019)"
 * - removes the word "the" anywhere
 * - collapses all non-alphanumerics to single spaces
 */
private fun normaliseTitle(raw: String): String {
    var s = raw.lowercase()
    s = s.replace(Regex("""^\s*\[[^]]*]\s*"""), "")
    s = s.replace(Regex("""^\s*[a-z]{2,3}\s*[|:\-]\s*"""), "")
    s = s.replace(Regex("""\s*\|\s*.*$"""), "")
    s = s.replace(Regex("""\s*\(\d{4}\)\s*$"""), "")
    s = s.replace(Regex("""\b(4k|uhd|fhd|hd|hdr|dv|sdr|bluray|remux|x264|x265|hevc)\b"""), "")
    s = s.replace(Regex("""\bthe\b"""), " ")
    s = s.replace(Regex("""[^a-z0-9]+"""), " ")
    return s.trim()
}
