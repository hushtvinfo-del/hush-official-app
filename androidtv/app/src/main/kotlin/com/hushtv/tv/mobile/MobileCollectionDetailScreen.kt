package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Mobile collection detail — lists every TMDB part of a franchise
 * (e.g. "Star Wars") and marks the ones that exist in the user's
 * Xtream library as playable. Missing parts show greyed out with
 * their TMDB artwork so the user sees the full saga even when a few
 * films aren't on their provider.
 *
 * Navigation taps from MobileHomeScreen's Collections page land here.
 */
@Composable
fun MobileCollectionDetailScreen(
    nav: NavController,
    playlistId: String,
    tmdbCollectionId: Int,
    collectionName: String,
) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    data class Entry(
        val title: String,
        val year: Int?,
        val posterUrl: String?,
        val matched: MediaCard?,
    )

    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(tmdbCollectionId, playlistId) {
        val p = playlist ?: return@LaunchedEffect
        loading = true
        val (parts, library) = coroutineScope {
            val partsD = async(Dispatchers.IO) { TmdbService.getCollectionParts(tmdbCollectionId) }
            val libD = async(Dispatchers.IO) {
                runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "movie") }
                    .getOrDefault(emptyList())
            }
            partsD.await() to libD.await()
        }
        val idx = TitleMatcher.buildIndex(library) { it.title }
        entries = parts.map { part ->
            val year = part.release_date?.take(4)?.toIntOrNull()
            val match = TitleMatcher.findBestMatch(
                tmdbTitle = part.title,
                tmdbYear = year,
                libraryIndex = idx,
            )
            Entry(
                title = part.title,
                year = year,
                posterUrl = match?.poster ?: TmdbService.img(part.poster_path, "w500"),
                matched = match,
            )
        }
        loading = false
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF05080F)),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            // Hero with the collection name.
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Brush.verticalGradient(
                            listOf(Color(0xFF172038), Color(0xFF05080F))
                        )),
                ) {
                    Text(
                        collectionName,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                    val count = entries.count { it.matched != null }
                    if (!loading) {
                        Text(
                            if (count == 0) "No films from this franchise in your library."
                            else "$count / ${entries.size} on your provider",
                            color = Cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(horizontal = 18.dp, vertical = 50.dp),
                        )
                    }
                }
            }

            if (loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Cyan)
                    }
                }
            } else if (entries.isEmpty()) {
                item {
                    Text(
                        "No films found for this collection.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            // Fixed height to play nicely inside LazyColumn;
                            // 220 dp per grid row * ceil(parts / 3) rows.
                            .heightIn(min = 200.dp)
                            .height((220.dp * (((entries.size + 2) / 3).coerceAtLeast(1)))),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false,
                    ) {
                        items(entries, key = { it.title + (it.year ?: 0) }) { entry ->
                            CollectionTile(entry.title, entry.year, entry.posterUrl, entry.matched != null) {
                                val match = entry.matched
                                val p = playlist
                                if (match != null && p != null) {
                                    val url = XtreamApi.movieUrl(
                                        p.host, p.username, p.password,
                                        match.streamId, match.containerExtension,
                                    )
                                    nav.navigate(
                                        mobilePlayerRoute(
                                            playlistId = playlistId,
                                            streamUrl = url,
                                            channelName = match.title,
                                            isLive = false,
                                            vodStreamId = match.streamId,
                                            vodKind = "movie",
                                            vodPoster = match.poster ?: entry.posterUrl,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // Floating back button.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xCC000000))
                .clickable { nav.popBackStack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CollectionTile(
    title: String,
    year: Int?,
    posterUrl: String?,
    playable: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(enabled = playable, onClick = onClick),
    ) {
        Box(
            Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1F2937)),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (!playable) {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)))
                Text(
                    "Not in your library",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Cyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint = Color(0xFF05080F),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            color = if (playable) Color.White else Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )
        if (year != null) {
            Text(
                year.toString(),
                color = Color(0xFF64748B),
                fontSize = 10.sp,
            )
        }
    }
}
