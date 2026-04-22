package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.hushtv.tv.data.EpgProgram
import com.hushtv.tv.data.EpgService
import com.hushtv.tv.data.FavoritesStore
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.NavState
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamCategory
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tivimate-style Live TV browser with:
 *  • persistent sidebar of ALL categories
 *  • scrollable channel list on the right
 *  • mini video preview in the top-right showing the focused channel
 *  • navigation memory: back from player restores exact position
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TVLiveBrowseScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var allChannelsForFavorites by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var selectedCatIndex by remember {
        mutableStateOf(
            if (NavState.browsePlaylistId == playlistId) NavState.selectedCategoryIndex else 0
        )
    }
    var channels by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loadingCats by remember { mutableStateOf(true) }
    var loadingChans by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val channelCache = remember { mutableStateMapOf<String, List<MediaCard>>() }

    // Categories list — prepend two virtual rows: ★ Favorites and "All channels".
    val FAVORITES_ID = "__fav__"
    val ALL_ID = "__all__"
    val uiCategories = remember(categories) {
        listOf(
            XtreamCategory(category_id = FAVORITES_ID, category_name = "★ Favorites"),
            XtreamCategory(category_id = ALL_ID, category_name = "All channels")
        ) + categories
    }

    // Load categories once (or reuse cached).
    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        scope.launch {
            runCatching {
                categories = XtreamApi.getCategories(p.host, p.username, p.password, "live")
            }
            loadingCats = false
        }
    }

    // Load channels when selected category changes.
    LaunchedEffect(selectedCatIndex, uiCategories, allChannelsForFavorites) {
        val p = playlist ?: return@LaunchedEffect
        val cat = uiCategories.getOrNull(selectedCatIndex) ?: return@LaunchedEffect
        when (cat.category_id) {
            FAVORITES_ID -> {
                // Collect all favorites across all categories from the cache,
                // or fetch "all channels" once if we don't have them yet.
                val favIds = FavoritesStore.getAll(ctx, playlistId)
                if (allChannelsForFavorites.isEmpty()) {
                    loadingChans = true
                    scope.launch {
                        runCatching {
                            allChannelsForFavorites = XtreamApi.getAllStreams(
                                p.host, p.username, p.password, "live"
                            )
                        }
                        loadingChans = false
                    }
                }
                channels = allChannelsForFavorites.filter { favIds.contains(it.streamId) }
            }
            ALL_ID -> {
                if (allChannelsForFavorites.isNotEmpty()) {
                    channels = allChannelsForFavorites
                } else {
                    loadingChans = true
                    scope.launch {
                        runCatching {
                            allChannelsForFavorites = XtreamApi.getAllStreams(
                                p.host, p.username, p.password, "live"
                            )
                            channels = allChannelsForFavorites
                        }
                        loadingChans = false
                    }
                }
            }
            else -> {
                val cached = channelCache[cat.category_id]
                if (cached != null) {
                    channels = cached
                } else {
                    loadingChans = true
                    scope.launch {
                        runCatching {
                            val list = XtreamApi.getStreamsForCategory(
                                p.host, p.username, p.password, "live", cat.category_id
                            )
                            channelCache[cat.category_id] = list
                            channels = list
                        }
                        loadingChans = false
                    }
                }
            }
        }
    }

    val currentCategory = uiCategories.getOrNull(selectedCatIndex)
    val filteredChannels = remember(channels, searchQuery) {
        if (searchQuery.isBlank()) channels
        else channels.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Persist "what the player needs to know for CH+/-".
    LaunchedEffect(filteredChannels, playlistId) {
        NavState.browsePlaylistId = playlistId
        NavState.liveChannels = filteredChannels
    }

    // Track focused channel; use it both for state-restore and mini-preview.
    var focusedChannelIdx by remember {
        mutableStateOf(
            if (NavState.browsePlaylistId == playlistId) NavState.focusedChannelIndex else 0
        )
    }
    LaunchedEffect(focusedChannelIdx) {
        NavState.selectedCategoryIndex = selectedCatIndex
        NavState.focusedChannelIndex = focusedChannelIdx
    }
    LaunchedEffect(selectedCatIndex) {
        NavState.selectedCategoryIndex = selectedCatIndex
    }

    // ─── Mini preview player ─────────────────────────────────────────────
    val previewPlayer = remember {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f  // muted — audio only starts in fullscreen
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { previewPlayer.release() } }

    // Debounced preview: 700ms after focus settles, start playing that channel.
    LaunchedEffect(focusedChannelIdx, filteredChannels, playlist) {
        val p = playlist ?: return@LaunchedEffect
        val ch = filteredChannels.getOrNull(focusedChannelIdx) ?: run {
            previewPlayer.stop(); return@LaunchedEffect
        }
        delay(700)
        val url = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
        previewPlayer.setMediaItem(MediaItem.fromUri(url))
        previewPlayer.prepare()
        previewPlayer.play()
    }

    val onPlay: (Int) -> Unit = sel@{ idx ->
        val p = playlist ?: return@sel
        val ch = filteredChannels.getOrNull(idx) ?: return@sel
        NavState.liveChannels = filteredChannels
        NavState.rememberPlayback(idx)
        NavState.browsePlaylistId = playlistId
        previewPlayer.stop()
        val url = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
        nav.navigate("player/${p.id}/${Uri.encode(url)}/${Uri.encode(ch.title)}/true")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(0f to Color(0xFF050B18), 1f to Color(0xFF000000))
            )
    ) {
        TopBar(
            title = currentCategory?.category_name ?: "Live TV",
            count = filteredChannels.size,
            searchOpen = searchOpen,
            searchQuery = searchQuery,
            onSearchToggle = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" },
            onSearchChange = { searchQuery = it },
            onBack = { nav.popBackStack() },
            onOpenGuide = {
                NavState.liveChannels = filteredChannels
                nav.navigate("epg/$playlistId")
            }
        )

        Row(Modifier.weight(1f).fillMaxWidth()) {
            CategorySidebar(
                categories = uiCategories,
                selectedIndex = selectedCatIndex,
                loading = loadingCats,
                onSelect = {
                    if (selectedCatIndex != it) focusedChannelIdx = 0
                    selectedCatIndex = it
                },
                restoreFocusIndex = if (NavState.browsePlaylistId == playlistId) NavState.selectedCategoryIndex else 0
            )

            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x1FFFFFFF)))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                ChannelsPane(
                    playlistId = playlistId,
                    host = playlist?.host ?: "",
                    username = playlist?.username ?: "",
                    password = playlist?.password ?: "",
                    channels = filteredChannels,
                    loading = loadingChans,
                    onFocusChange = { focusedChannelIdx = it },
                    initialFocusIndex = focusedChannelIdx,
                    onPlay = onPlay,
                    emptyReason = when {
                        searchQuery.isNotBlank() && filteredChannels.isEmpty() ->
                            "No channels matching \"$searchQuery\""
                        channels.isEmpty() && !loadingChans -> "No channels in this category"
                        else -> null
                    }
                )

                // Mini preview overlay (top-right)
                val previewChannel = filteredChannels.getOrNull(focusedChannelIdx)
                if (previewChannel != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                    ) {
                        MiniPreview(channel = previewChannel, player = previewPlayer)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini preview window
// ─────────────────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MiniPreview(channel: MediaCard?, player: ExoPlayer) {
    if (channel == null) return
    Surface(
        color = Color(0xFF000000),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(340.dp)
            .border(1.dp, Color(0x5506B6D4), RoundedCornerShape(12.dp))
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Tiny red "LIVE" pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color(0xCCDC2626), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        Modifier.size(6.dp).background(Color.White, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    channel.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Preview — press ENTER to watch fullscreen",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    title: String,
    count: Int,
    searchOpen: Boolean,
    searchQuery: String,
    onSearchToggle: () -> Unit,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x1A000000))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0x1AFFFFFF),
            shape = CircleShape,
            modifier = Modifier.size(44.dp).focusTvCircle().clickableWithEnter(onBack)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        HushTVLogo(fontSize = 22.sp)
        Spacer(Modifier.width(20.dp))
        Box(Modifier.height(24.dp).width(1.dp).background(Color(0x33FFFFFF)))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (count > 0) {
                Text(
                    "$count channel${if (count == 1) "" else "s"}",
                    color = TextSecondary, fontSize = 13.sp
                )
            }
        }
        if (searchOpen) {
            SearchField(value = searchQuery, onChange = onSearchChange, onClose = onSearchToggle)
        } else {
            Surface(
                color = Color(0x14FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .focusTvCircle()
                    .border(1.dp, Color(0x26FFFFFF), CircleShape)
                    .clickableWithEnter(onOpenGuide)
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GridView, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("TV Guide", color = TextSecondary, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                color = Color(0x14FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .focusTvCircle()
                    .border(1.dp, Color(0x26FFFFFF), CircleShape)
                    .clickableWithEnter(onSearchToggle)
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search", color = TextSecondary, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(360.dp)
                .background(Color(0x1FFFFFFF), CircleShape)
                .border(2.dp, if (focused) Cyan else Color(0x33FFFFFF), CircleShape)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
            )
            if (value.isEmpty()) Text("Search channels…", color = TextSecondary, fontSize = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = Color(0x1AFFFFFF), shape = CircleShape,
            modifier = Modifier.size(40.dp).focusTvCircle().clickableWithEnter(onClose)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category sidebar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategorySidebar(
    categories: List<XtreamCategory>,
    selectedIndex: Int,
    loading: Boolean,
    onSelect: (Int) -> Unit,
    restoreFocusIndex: Int
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoreFocusIndex.coerceAtLeast(0)
    )
    val focusByIndex = remember { mutableMapOf<Int, FocusRequester>() }
    fun reqFor(idx: Int) = focusByIndex.getOrPut(idx) { FocusRequester() }

    // Restore focus to the previously-selected category (on first render with data).
    LaunchedEffect(categories.size) {
        if (categories.isNotEmpty()) {
            val target = restoreFocusIndex.coerceIn(0, categories.size - 1)
            runCatching { reqFor(target).requestFocus() }
        }
    }
    // Keep the selected category visible as user D-pads down.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in categories.indices) {
            runCatching { listState.animateScrollToItem(selectedIndex) }
        }
    }

    Column(
        Modifier.width(300.dp).fillMaxHeight().background(Color(0x33000000))
    ) {
        Text(
            "CATEGORIES",
            color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp)
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(28.dp))
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                items(
                    count = categories.size,
                    key = { idx -> categories[idx].category_id }
                ) { idx ->
                    val cat = categories[idx]
                    CategoryRow(
                        name = cat.category_name,
                        selected = idx == selectedIndex,
                        modifier = Modifier.focusRequester(reqFor(idx)),
                        onFocus = { onSelect(idx) },
                        onClick = { onSelect(idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Cyan
        selected -> Color(0x2606B6D4)
        else -> Color.Transparent
    }
    val textColor = when {
        focused -> Color.Black
        selected -> Cyan
        else -> Color(0xFFD1D5DB)
    }
    val leftBar = if (selected || focused) Cyan else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(vertical = 12.dp)
    ) {
        Box(
            Modifier.width(4.dp).height(28.dp)
                .background(leftBar, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(14.dp))
        Text(
            name, color = textColor, fontSize = 15.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, modifier = Modifier.weight(1f, fill = true)
        )
        Spacer(Modifier.width(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Channels pane
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChannelsPane(
    playlistId: String,
    host: String, username: String, password: String,
    channels: List<MediaCard>,
    loading: Boolean,
    emptyReason: String?,
    initialFocusIndex: Int,
    onFocusChange: (Int) -> Unit,
    onPlay: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusByIndex = remember { mutableMapOf<Int, FocusRequester>() }
    fun reqFor(idx: Int) = focusByIndex.getOrPut(idx) { FocusRequester() }
    val listState = rememberLazyListState()

    // Re-render when EPG arrives
    var epgVersion by remember { mutableStateOf(0) }

    // When the list (re)populates, restore focus to previously-watched channel.
    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            val target = initialFocusIndex.coerceIn(0, channels.size - 1)
            runCatching {
                listState.scrollToItem(target.coerceAtLeast(0))
                reqFor(target).requestFocus()
            }
        }
    }

    // Kick off EPG fetch for currently-visible channels (throttled).
    LaunchedEffect(channels, listState.firstVisibleItemIndex) {
        if (host.isBlank() || channels.isEmpty()) return@LaunchedEffect
        val from = listState.firstVisibleItemIndex
        val to = (from + 20).coerceAtMost(channels.size)
        for (i in from until to) {
            val ch = channels[i]
            scope.launch {
                EpgService.fetchShortEpg(host, username, password, ch.streamId)
                epgVersion++
            }
        }
    }

    // Favorites set — live-updated
    var favVersion by remember { mutableStateOf(0) }
    val favs = remember(favVersion, playlistId) { FavoritesStore.getAll(ctx, playlistId) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0E18))) {
        when {
            loading -> CenterProgress()
            emptyReason != null -> EmptyMessage(emptyReason)
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(count = channels.size, key = { idx -> channels[idx].id + "-$idx" }) { idx ->
                        ChannelRow(
                            number = idx + 1,
                            channel = channels[idx],
                            nowPlaying = EpgService.nowPlaying(channels[idx].streamId).also { epgVersion },
                            isFavorite = favs.contains(channels[idx].streamId),
                            modifier = Modifier.focusRequester(reqFor(idx)),
                            onFocus = { onFocusChange(idx) },
                            onPlay = { onPlay(idx) },
                            onToggleFav = {
                                FavoritesStore.toggle(ctx, playlistId, channels[idx].streamId)
                                favVersion++
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    number: Int,
    channel: MediaCard,
    nowPlaying: EpgProgram?,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x08FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Cyan else Color(0x14FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnter(onPlay)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            Modifier.width(52.dp).padding(end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString().padStart(3, '0'),
                color = if (focused) Cyan else TextSecondary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
        }
        ChannelLogo(channel.poster, channel.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.title, color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.weight(1f, false)
                )
                if (isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Star, null,
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (nowPlaying != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    nowPlaying.title,
                    color = Color(0xFFD1D5DB), fontSize = 13.sp,
                    maxLines = 1
                )
                // Mini progress bar
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x14FFFFFF), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(nowPlaying.progressPct)
                            .height(3.dp)
                            .background(Cyan, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        // Favorite star toggle (right side)
        Surface(
            color = if (isFavorite) Color(0x33FACC15) else Color(0x14FFFFFF),
            shape = CircleShape,
            modifier = Modifier
                .size(36.dp)
                .border(
                    1.dp,
                    if (isFavorite) Color(0xFFFACC15) else Color(0x26FFFFFF),
                    CircleShape
                )
                .focusable()
                .onFocusChanged { }
                .clickableWithEnter(onToggleFav)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    null,
                    tint = if (isFavorite) Color(0xFFFACC15) else Color(0xFFB0B3BC),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (focused) {
            Surface(color = Cyan, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(url: String?, name: String) {
    Box(
        Modifier.size(52.dp).background(Color(0xFF1F2937), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = url, contentDescription = name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                error = { LogoFallback(name) }, loading = { LogoFallback(name) }
            )
        } else LogoFallback(name)
    }
}

@Composable
private fun LogoFallback(name: String) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(initial, color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CenterProgress() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Cyan, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text("Loading channels…", color = TextSecondary, fontSize = 16.sp)
    }
}

@Composable
private fun EmptyMessage(msg: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Tv, null, tint = Color(0xFF374151), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(msg, color = Color(0xFF6B7280), fontSize = 17.sp)
    }
}

@Composable
private fun Modifier.focusTvCircle(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) Cyan else Color.Transparent,
            shape = CircleShape
        )
        .onFocusChanged { focused = it.isFocused }
        .focusable()
}
