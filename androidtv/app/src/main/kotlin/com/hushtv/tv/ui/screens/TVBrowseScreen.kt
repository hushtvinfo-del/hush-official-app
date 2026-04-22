package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamCategory
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.launch

@Composable
fun TVBrowseScreen(nav: NavController, playlistId: String, type: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    var rows by remember { mutableStateOf<List<Pair<String, List<MediaCard>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchMode by remember { mutableStateOf(type == "search") }
    var searchQuery by remember { mutableStateOf("") }
    var allForSearch by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    val effectiveType = if (type == "search") "movie" else type

    // Load categories + first page of items on entry (when not search-only screen)
    LaunchedEffect(playlistId, type) {
        val p = playlist ?: return@LaunchedEffect
        loading = true
        if (type == "search") {
            // Pre-load all content types for search (movies + series + live)
            scope.launch {
                runCatching {
                    val allMovies = XtreamApi.getAllStreams(p.host, p.username, p.password, "movie")
                    val allSeries = XtreamApi.getAllStreams(p.host, p.username, p.password, "series")
                    val allLive = XtreamApi.getAllStreams(p.host, p.username, p.password, "live")
                    allForSearch = allMovies + allSeries + allLive
                    loading = false
                }.onFailure { loading = false }
            }
        } else if (type == "favorites") {
            rows = emptyList()
            loading = false
        } else {
            scope.launch {
                runCatching {
                    val cats: List<XtreamCategory> =
                        XtreamApi.getCategories(p.host, p.username, p.password, effectiveType)
                    val firstCats = cats.take(6)
                    val built = mutableListOf<Pair<String, List<MediaCard>>>()
                    for (c in firstCats) {
                        val items = XtreamApi.getStreamsForCategory(
                            p.host, p.username, p.password, effectiveType, c.category_id
                        ).take(30)
                        if (items.isNotEmpty()) built.add(c.category_name to items)
                    }
                    rows = built
                }.onFailure { /* swallow */ }
                loading = false
            }
        }
    }

    // Live search filter
    LaunchedEffect(searchQuery, allForSearch) {
        if (type == "search" && searchQuery.length >= 2) {
            val q = searchQuery.lowercase()
            searchResults = allForSearch.filter { it.title.lowercase().contains(q) }.take(50)
        } else {
            searchResults = emptyList()
        }
    }

    val typeTitle = when (type) {
        "live" -> "Live TV"
        "movie" -> "Movies"
        "series" -> "Series"
        "favorites" -> "Favorites"
        "search" -> "Search"
        else -> type
    }

    val searchFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(searchMode) {
        if (searchMode) runCatching { searchFocus.requestFocus() }
    }
    LaunchedEffect(Unit) {
        if (type != "search") runCatching { backFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    val onSelect: (MediaCard) -> Unit = sel@{ item ->
        val p = playlist ?: return@sel
        when (item.kind) {
            "live" -> {
                val url = XtreamApi.liveUrl(p.host, p.username, p.password, item.streamId)
                nav.navigate("player/$playlistId/${Uri.encode(url)}/${Uri.encode(item.title)}/true")
            }
            "movie" -> {
                val url = XtreamApi.movieUrl(p.host, p.username, p.password, item.streamId, item.containerExtension)
                nav.navigate("player/$playlistId/${Uri.encode(url)}/${Uri.encode(item.title)}/false")
            }
            "series" -> {
                nav.navigate("series/$playlistId/${item.seriesId}/${Uri.encode(item.title)}")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x1AFFFFFF),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .focusRequester(backFocus)
                        .tvFocusable(shape = CircleShape)
                        .clickableWithEnter { nav.popBackStack() }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Text(typeTitle, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                if (type != "search") {
                    if (!searchMode) {
                        Surface(
                            color = Color(0x14FFFFFF),
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, Color(0x26FFFFFF), CircleShape)
                                .tvFocusable(shape = CircleShape)
                                .clickableWithEnter { searchMode = true }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Search", color = TextSecondary, fontSize = 18.sp)
                            }
                        }
                    } else {
                        SearchBox(
                            query = searchQuery,
                            onQuery = { searchQuery = it },
                            placeholder = "Search $typeTitle...",
                            focusRequester = searchFocus
                        )
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            color = Color(0x1AFFFFFF),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .tvFocusable(shape = CircleShape)
                                .clickableWithEnter {
                                    searchMode = false; searchQuery = ""
                                }
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                } else {
                    // Global search screen — input is the main widget
                    SearchBox(
                        query = searchQuery,
                        onQuery = { searchQuery = it },
                        placeholder = "Search movies, series, channels…",
                        focusRequester = searchFocus
                    )
                }
            }

            // Content
            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> LoadingBox()
                    type == "favorites" -> EmptyBox("Favorites coming soon")
                    type == "search" -> {
                        if (searchQuery.length < 2) {
                            EmptyBox("Type at least 2 characters to search")
                        } else if (searchResults.isEmpty()) {
                            EmptyBox("No results for \"$searchQuery\"")
                        } else {
                            RowsList(
                                rows = listOf("Results for \"$searchQuery\"" to searchResults),
                                onSelect = onSelect
                            )
                        }
                    }
                    searchMode && searchQuery.length >= 2 -> {
                        val flat = rows.flatMap { it.second }
                            .filter { it.title.lowercase().contains(searchQuery.lowercase()) }
                            .take(50)
                        if (flat.isEmpty()) EmptyBox("No results for \"$searchQuery\"")
                        else RowsList(rows = listOf("Results" to flat), onSelect = onSelect)
                    }
                    rows.isEmpty() -> EmptyBox("No content available")
                    else -> RowsList(rows = rows, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Cyan, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Loading content…", color = TextSecondary, fontSize = 20.sp)
    }
}

@Composable
private fun EmptyBox(msg: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Tv, null, tint = Color(0xFF374151), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(msg, color = Color(0xFF6B7280), fontSize = 20.sp)
    }
}

@Composable
private fun RowsList(
    rows: List<Pair<String, List<MediaCard>>>,
    onSelect: (MediaCard) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        items(rows) { (title, items) ->
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 64.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 64.dp, vertical = 16.dp)
                ) {
                    items(items) { card -> PosterCard(card, onSelect) }
                }
            }
        }
    }
}

@Composable
private fun PosterCard(card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Surface(
        color = Color(0xFF111111),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .size(width = 180.dp, height = 270.dp)
            .tvFocusable(shape = RoundedCornerShape(12.dp))
            .clickableWithEnter { onSelect(card) }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!card.poster.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = card.poster,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { FallbackPoster() },
                    loading = { FallbackPoster() }
                )
            } else {
                FallbackPoster()
            }

            // Bottom gradient
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000), Color(0xFF000000))
                        )
                    )
            )

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    card.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                if (!card.rating.isNullOrBlank() && card.rating != "0") {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFACC15), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(card.rating, color = Color(0xFFFACC15), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FallbackPoster() {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF1F2937)),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.Movie, null, tint = Color(0xFF4B5563), modifier = Modifier.size(48.dp)) }
}

@Composable
private fun SearchBox(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(420.dp)
            .background(Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
            .border(2.dp, if (focused) Cyan else Color(0x33FFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Cyan),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
        )
        if (query.isEmpty()) Text(placeholder, color = TextSecondary, fontSize = 18.sp)
    }
}
