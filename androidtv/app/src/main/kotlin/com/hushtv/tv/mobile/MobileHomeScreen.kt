package com.hushtv.tv.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mobile Home — a compact Netflix-style layout:
 *   • Hero card (top-most trending movie backdrop).
 *   • "Trending Movies" horizontal rail.
 *   • "Trending Series" horizontal rail.
 *   • "Live now" horizontal rail (first channel from each non-adult category).
 *
 * Data comes from the existing TMDB + Xtream services. No TV-specific
 * focus code; everything is tap-driven.
 */
@Composable
fun MobileHomeScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    var movies by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var series by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var liveChannels by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var heroBackdrop by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        if (playlist == null) { loading = false; return@LaunchedEffect }
        val mList = runCatching {
            withContext(Dispatchers.IO) {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "movie")
                    .take(60)
            }
        }.getOrDefault(emptyList())
        val sList = runCatching {
            withContext(Dispatchers.IO) {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "series")
                    .take(60)
            }
        }.getOrDefault(emptyList())
        val lList = runCatching {
            withContext(Dispatchers.IO) {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "live")
                    .take(60)
            }
        }.getOrDefault(emptyList())
        val hero = runCatching {
            withContext(Dispatchers.IO) {
                TmdbService.trendingBackdrops(kind = "movie", limit = 1).firstOrNull()
            }
        }.getOrNull()
        movies = mList
        series = sList
        liveChannels = lList
        heroBackdrop = hero
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Hero
        item {
            HeroCard(
                backdropUrl = heroBackdrop,
                title = playlist?.name ?: "HushTV",
                subtitle = "Your movies, series and live TV — in your pocket",
            )
        }
        // Trending rails
        if (movies.isNotEmpty()) item {
            Rail(
                title = "Movies",
                cards = movies,
                onCardClick = { card ->
                    if (playlist == null) return@Rail
                    val url = XtreamApi.movieUrl(
                        playlist.host, playlist.username, playlist.password,
                        card.streamId, card.containerExtension,
                    )
                    nav.navigate(mobilePlayerRoute(playlistId, url, card.title, isLive = false))
                },
            )
        }
        if (series.isNotEmpty()) item {
            Rail(
                title = "Series",
                cards = series,
                onCardClick = { card ->
                    // For MVP we jump to series browse — individual episode
                    // picker is a future enhancement.
                    nav.navigate("mbrowse/$playlistId/series")
                },
            )
        }
        if (liveChannels.isNotEmpty()) item {
            Rail(
                title = "Live Now",
                cards = liveChannels,
                onCardClick = { card ->
                    if (playlist == null) return@Rail
                    val url = XtreamApi.liveUrl(
                        playlist.host, playlist.username, playlist.password, card.streamId,
                    )
                    nav.navigate(mobilePlayerRoute(playlistId, url, card.title, isLive = true))
                },
            )
        }
    }
}

@Composable
private fun HeroCard(backdropUrl: String?, title: String, subtitle: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    ) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(
                        listOf(Color(0xFF1E3A8A), Color(0xFF030509))
                    ))
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Color(0xFF05080F),
                ))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                title.uppercase(),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun Rail(
    title: String,
    cards: List<MediaCard>,
    onCardClick: (MediaCard) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards, key = { "${it.kind}-${it.id}" }) { card ->
                MobilePoster(card, onClick = { onCardClick(card) })
            }
        }
    }
}

@Composable
fun MobilePoster(card: MediaCard, onClick: () -> Unit) {
    val isLive = card.kind == "live"
    Box(
        Modifier
            .size(width = if (isLive) 130.dp else 110.dp, height = if (isLive) 86.dp else 160.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        if (!card.poster.isNullOrBlank()) {
            AsyncImage(
                model = card.poster,
                contentDescription = card.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1F2937)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    card.title.take(2).uppercase(),
                    color = Color(0xFF64748B),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        // Title fallback for live channels (no poster → use the name overlay)
        if (isLive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color(0xD0000000),
                    ))
            )
            Text(
                card.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
