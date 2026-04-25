package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
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
import com.hushtv.tv.ui.requests.RequestContentSheet
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Mobile unified search across movies, series and live channels — the
 * thumb-first counterpart to the TV master-search feature.
 *
 *  Layout (top → bottom):
 *    [ ← | 🔍 Search movies, series, channels…          ✕ ]
 *    [ All · Movies · Series · Live ]              ← filter chips
 *    [                                           ]
 *    [  Section: MOVIES · 12                     ]
 *    [  [poster] Title                           ]
 *    [  [poster] Title                           ]
 *    [  Section: SERIES · 3                      ]
 *    [  …                                         ]
 *
 *  • Auto-focuses the input + opens soft keyboard on entry.
 *  • Debounced 180 ms so typing doesn't block the main thread.
 *  • Library index is pre-loaded once in the background so typing
 *    feels instant.
 *  • Filter chips narrow results to one kind (also works as a
 *    visual preview of counts).
 */
@Composable
fun MobileSearchScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }
    val keyboard = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }          // all | movie | series | live
    var movies by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var series by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var live by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var indexReady by remember { mutableStateOf(false) }

    // Pre-fetch the whole library index ONCE. Heavy call; runs off the
    // main thread. While this is in flight we show a spinner so the
    // user knows why results haven't appeared yet.
    var allMovies by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var allSeries by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var allLive by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    LaunchedEffect(playlistId) {
        if (playlist == null) { indexReady = true; return@LaunchedEffect }
        val (m, s, l) = withContext(Dispatchers.IO) {
            val m = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "movie")
            }.getOrDefault(emptyList())
            val s = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "series")
            }.getOrDefault(emptyList())
            val l = runCatching {
                XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, "live")
            }.getOrDefault(emptyList())
            Triple(m, s, l)
        }
        allMovies = m
        allSeries = s
        allLive = l
        indexReady = true
    }

    // Debounced filter — re-runs on query change or when the index
    // finishes loading (so the first keystrokes before indexReady don't
    // silently drop).
    LaunchedEffect(query, indexReady, allMovies, allSeries, allLive) {
        delay(180)
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            movies = emptyList(); series = emptyList(); live = emptyList()
            return@LaunchedEffect
        }
        movies = allMovies.filter { it.title.lowercase().contains(q) }.take(60)
        series = allSeries.filter { it.title.lowercase().contains(q) }.take(60)
        live = allLive.filter { it.title.lowercase().contains(q) }.take(60)
    }

    // Auto-focus the input so the keyboard opens immediately.
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { fieldFocus.requestFocus() }
        keyboard?.show()
    }

    var showRequestModal by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
    Column(Modifier.fillMaxSize()) {
        // ── Search bar row ──────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ArrowBack, null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Search pill — BasicTextField inside a rounded surface so
            // the height is stable and predictable on all screens.
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0F1A2C))
                    .border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search, null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search movies, series, channels…",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0x1FFFFFFF))
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // ── Filter chip rail ─────────────────────────────────────────
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val totalAll = movies.size + series.size + live.size
            item { FilterChip("All", totalAll, filter == "all") { filter = "all" } }
            item { FilterChip("Movies", movies.size, filter == "movie") { filter = "movie" } }
            item { FilterChip("Series", series.size, filter == "series") { filter = "series" } }
            item { FilterChip("Live", live.size, filter == "live") { filter = "live" } }
        }

        Spacer(Modifier.height(4.dp))

        // ── Results ──────────────────────────────────────────────────
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            when {
                query.isBlank() -> {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Start typing to search",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Finds movies, series & live channels",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                !indexReady -> {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Cyan,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                movies.isEmpty() && series.isEmpty() && live.isEmpty() -> {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "No matches for \"${query.trim()}\"",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(14.dp))
                            // Primary CTA — submit a request for the
                            // exact title the user just typed. We send
                            // the query through to RequestContentSheet
                            // as a preset so the user only has to pick
                            // movie/series and tap submit.
                            RequestThisCta(presetTitle = query.trim()) {
                                showRequestModal = true
                            }
                        }
                    }
                }
                else -> {
                    if ((filter == "all" || filter == "movie") && movies.isNotEmpty()) {
                        item { SectionHeader("MOVIES", movies.size) }
                        items(movies, key = { "m-${it.streamId}" }) { c ->
                            SearchResultRow(c) { onCard(c, playlistId, nav) }
                        }
                    }
                    if ((filter == "all" || filter == "series") && series.isNotEmpty()) {
                        item { SectionHeader("SERIES", series.size) }
                        items(series, key = { "s-${it.seriesId}" }) { c ->
                            SearchResultRow(c) { onCard(c, playlistId, nav) }
                        }
                    }
                    if ((filter == "all" || filter == "live") && live.isNotEmpty()) {
                        item { SectionHeader("LIVE", live.size) }
                        items(live, key = { "l-${it.streamId}" }) { c ->
                            SearchResultRow(c) { onCard(c, playlistId, nav) }
                        }
                    }
                    // Even when SOME results matched, give the user a
                    // way to request the exact thing they were looking
                    // for if the matches don't include it.
                    item {
                        Spacer(Modifier.height(20.dp))
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            RequestThisCta(presetTitle = query.trim()) {
                                showRequestModal = true
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal overlay — sits above everything else. Blocks the search
    // surface beneath while the user fills in the form.
    if (showRequestModal) {
        // Heuristic: if the active filter is "series" OR there are
        // already series matches but no movie matches, default the
        // type radio to "series". Otherwise default to "movie".
        val presetType = when {
            filter == "series" -> "series"
            filter == "movie" -> "movie"
            series.isNotEmpty() && movies.isEmpty() -> "series"
            else -> "movie"
        }
        RequestContentSheet(
            presetType = presetType,
            presetTitle = query.trim(),
            onDismiss = { showRequestModal = false },
            onViewMyRequests = {
                showRequestModal = false
                nav.navigate("mrequests/$playlistId")
            },
        )
    }
}
}

private fun onCard(card: MediaCard, playlistId: String, nav: NavController) {
    val ctx = nav.context
    val p = PlaylistStore.find(ctx, playlistId) ?: return
    when (card.kind) {
        "live" -> {
            val url = XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
            nav.navigate(mobilePlayerRoute(playlistId, url, card.title, isLive = true))
        }
        "movie" -> {
            val url = XtreamApi.movieUrl(p.host, p.username, p.password, card.streamId, card.containerExtension)
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

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Cyan else Color(0x14FFFFFF)
    val fg = if (selected) Color(0xFF05080F) else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                color = fg.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Cyan,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "· $count",
            color = Color(0xFF64748B),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SearchResultRow(card: MediaCard, onClick: () -> Unit) {
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
                AsyncImage(
                    model = card.poster, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * "Request this title" CTA — primary button that opens the request
 * sheet pre-filled with whatever the user just typed. Shown both
 * after a no-results search AND alongside partial matches (in case
 * the matches don't include the exact thing the user wanted).
 */
@Composable
private fun RequestThisCta(presetTitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Cyan)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (presetTitle.isBlank()) "Request missing content"
            else "Request \"$presetTitle\"",
            color = Color(0xFF05080F),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
