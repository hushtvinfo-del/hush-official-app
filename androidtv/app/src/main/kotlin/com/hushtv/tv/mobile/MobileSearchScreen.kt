package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Mobile unified search across movies, series and live channels. */
@Composable
fun MobileSearchScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    var query by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var series by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var live by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    // Pre-fetch full index ONCE so typing is instant.
    var allMovies by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var allSeries by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var allLive by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    LaunchedEffect(playlistId) {
        if (playlist == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            allMovies = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "movie")
            }.getOrDefault(emptyList())
            allSeries = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "series")
            }.getOrDefault(emptyList())
            allLive = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "live")
            }.getOrDefault(emptyList())
        }
    }

    // Debounced filter.
    LaunchedEffect(query, allMovies, allSeries, allLive) {
        delay(180)
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            movies = emptyList(); series = emptyList(); live = emptyList()
            return@LaunchedEffect
        }
        movies = allMovies.filter { it.title.lowercase().contains(q) }.take(40)
        series = allSeries.filter { it.title.lowercase().contains(q) }.take(40)
        live = allLive.filter { it.title.lowercase().contains(q) }.take(40)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                placeholder = { Text("Search movies, series, channels…", color = Color(0xFF64748B), fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x18FFFFFF),
                    unfocusedContainerColor = Color(0x10FFFFFF),
                    focusedIndicatorColor = Cyan,
                    unfocusedIndicatorColor = Color(0x33FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Cyan,
                ),
                shape = RoundedCornerShape(10.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.isBlank()) {
                item {
                    Text(
                        "Start typing to search your library…",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else if (movies.isEmpty() && series.isEmpty() && live.isEmpty()) {
                item {
                    Text(
                        "No matches.",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                if (movies.isNotEmpty()) {
                    item { SectionHeader("MOVIES · ${movies.size}") }
                    items(movies, key = { "m-${it.streamId}" }) { c ->
                        SearchResultRow(c) { onCard(c, playlistId, nav) }
                    }
                }
                if (series.isNotEmpty()) {
                    item { SectionHeader("SERIES · ${series.size}") }
                    items(series, key = { "s-${it.seriesId}" }) { c ->
                        SearchResultRow(c) { onCard(c, playlistId, nav) }
                    }
                }
                if (live.isNotEmpty()) {
                    item { SectionHeader("LIVE · ${live.size}") }
                    items(live, key = { "l-${it.streamId}" }) { c ->
                        SearchResultRow(c) { onCard(c, playlistId, nav) }
                    }
                }
            }
        }
    }
}

private fun onCard(card: com.hushtv.tv.data.MediaCard, playlistId: String, nav: androidx.navigation.NavController) {
    val ctx = nav.context
    val p = com.hushtv.tv.data.PlaylistStore.find(ctx, playlistId) ?: return
    when (card.kind) {
        "live" -> {
            val url = com.hushtv.tv.data.XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
            nav.navigate(mobilePlayerRoute(playlistId, url, card.title, isLive = true))
        }
        "movie" -> {
            val url = com.hushtv.tv.data.XtreamApi.movieUrl(p.host, p.username, p.password, card.streamId, card.containerExtension)
            nav.navigate(
                mobilePlayerRoute(
                    playlistId = playlistId,
                    streamUrl = url,
                    channelName = card.title,
                    isLive = false,
                    vodStreamId = card.streamId,
                    vodKind = "movie",
                    vodPoster = card.poster,
                ),
            )
        }
        "series" -> {
            nav.navigate(
                mobileSeriesRoute(
                    playlistId = playlistId,
                    seriesId = card.seriesId.toString(),
                    name = card.title,
                    poster = card.poster,
                ),
            )
        }
    }
}


@androidx.compose.runtime.Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Cyan,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@androidx.compose.runtime.Composable
private fun SearchResultRow(
    card: com.hushtv.tv.data.MediaCard,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1220))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 54.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            if (!card.poster.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = card.poster, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Text(
                    card.title.take(2).uppercase(),
                    color = Color(0xFF64748B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            card.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
