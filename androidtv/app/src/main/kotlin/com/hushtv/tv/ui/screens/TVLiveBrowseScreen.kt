package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.hushtv.tv.ui.theme.Inter
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
        val base = if (searchQuery.isBlank()) channels
        else channels.filter { it.title.contains(searchQuery, ignoreCase = true) }
        // Default sort: alphabetical A-Z by channel title, case-insensitive.
        base.sortedBy { it.title.lowercase() }
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

    // Track whether focus is currently inside the channels pane. The preview
    // only plays while the user is actively browsing channels — not while
    // navigating the category sidebar.
    var channelsPaneFocused by remember { mutableStateOf(false) }

    // Focus requester for the first channel in the list — used to jump focus
    // when the user presses ENTER on a category.
    val firstChannelFocus = remember { FocusRequester() }

    // "Please focus the first channel once the list has populated" flag.
    // Set by ENTER-on-category; cleared after the focus request succeeds.
    var pendingJumpToFirstChannel by remember { mutableStateOf(false) }

    // Wait for channels to actually load before moving focus — this avoids
    // trying to focus a node that hasn't been composed yet.
    LaunchedEffect(filteredChannels, pendingJumpToFirstChannel) {
        if (pendingJumpToFirstChannel && filteredChannels.isNotEmpty()) {
            delay(80)
            runCatching { firstChannelFocus.requestFocus() }
            pendingJumpToFirstChannel = false
        }
    }

    // Debounced preview: starts ~600 ms after the user settles on a channel,
    // but ONLY while their focus is inside the channels pane.
    LaunchedEffect(focusedChannelIdx, filteredChannels, channelsPaneFocused, playlist) {
        if (!channelsPaneFocused) {
            previewPlayer.pause()
            return@LaunchedEffect
        }
        val p = playlist ?: return@LaunchedEffect
        val ch = filteredChannels.getOrNull(focusedChannelIdx) ?: run {
            previewPlayer.stop(); return@LaunchedEffect
        }
        delay(600)
        val url = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
        previewPlayer.setMediaItem(MediaItem.fromUri(url))
        previewPlayer.prepare()
        previewPlayer.play()
    }

    // Pre-fetch the EPG for the channel under focus (short EPG → now + next).
    LaunchedEffect(focusedChannelIdx, filteredChannels) {
        val p = playlist ?: return@LaunchedEffect
        val ch = filteredChannels.getOrNull(focusedChannelIdx) ?: return@LaunchedEffect
        runCatching { EpgService.fetchShortEpg(p.host, p.username, p.password, ch.streamId) }
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

    // Dropdown state — hoisted so the panel can be rendered as a
    // fullscreen overlay at the ROOT Box level (above all content).
    // Previously the panel was nested inside the toolbar Column which
    // meant the preview bar still rendered below it — the user saw
    // the CTV channel logo peeking through. This top-level overlay
    // approach guarantees nothing can bleed through.
    val dropdownFocus = remember { FocusRequester() }
    val searchFocusTB = remember { FocusRequester() }
    val guideFocus = remember { FocusRequester() }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Current layout mode — state-backed so picking a new mode via
    // the TopNavBar hint chip re-composes instantly, no need to leave
    // + re-enter the screen.
    var currentLayoutMode by remember {
        mutableStateOf(com.hushtv.tv.data.LayoutPrefsStore.mode(ctx))
    }
    val useSidebar = currentLayoutMode == com.hushtv.tv.data.LayoutPrefsStore.MODE_SIDEBAR
    val sidebarFirstItemFocus = remember { FocusRequester() }
    val sidebarItems = remember(uiCategories) {
        uiCategories.map {
            com.hushtv.tv.ui.screens.home.SidebarItem(
                id = it.category_id,
                label = it.category_name,
            )
        }
    }
    // Layout chooser invoked via the TopNavBar hint chip.
    var showLayoutChooser by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    if (useSidebar) {
        // ── SIDEBAR MODE ──
        // Classic Tivimate-style layout. No top toolbar. Sidebar on
        // the left; PreviewBar + ChannelsPane fill the rest.
        Row(
            Modifier
                .fillMaxSize()
                .padding(top = 72.dp)
                .background(
                    Brush.verticalGradient(0f to Color(0xFF050B18), 1f to Color(0xFF000000))
                )
        ) {
            com.hushtv.tv.ui.screens.home.CategorySidebar(
                items = sidebarItems,
                selectedId = uiCategories.getOrNull(selectedCatIndex)?.category_id,
                title = "Live TV",
                firstItemFocus = sidebarFirstItemFocus,
                onFocus = { /* no preview-on-focus; ENTER commits */ },
                onEnter = { item ->
                    val idx = uiCategories.indexOfFirst { it.category_id == item.id }
                    if (idx >= 0) {
                        if (selectedCatIndex != idx) focusedChannelIdx = 0
                        selectedCatIndex = idx
                        pendingJumpToFirstChannel = true
                    }
                },
                rightTarget = firstChannelFocus,
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x1FFFFFFF)))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { channelsPaneFocused = it.hasFocus && focusedChannelIdx >= 0 }
            ) {
                PreviewBar(
                    channel = filteredChannels.getOrNull(focusedChannelIdx),
                    player = previewPlayer,
                    showVideo = channelsPaneFocused,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
                ChannelsPane(
                    playlistId = playlistId,
                    host = playlist?.host ?: "",
                    username = playlist?.username ?: "",
                    password = playlist?.password ?: "",
                    channels = filteredChannels,
                    loading = loadingChans,
                    onFocusChange = { focusedChannelIdx = it },
                    initialFocusIndex = focusedChannelIdx,
                    firstChannelFocus = firstChannelFocus,
                    onLeftEdge = { runCatching { sidebarFirstItemFocus.requestFocus() } },
                    onPlay = onPlay,
                    emptyReason = if (channels.isEmpty() && !loadingChans)
                        "No channels in this category" else null,
                    topRowUpTarget = null,
                )
            }
        }
    } else {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 72.dp)
            .background(
                Brush.verticalGradient(0f to Color(0xFF050B18), 1f to Color(0xFF000000))
            )
    ) {
        // ── New toolbar — matches the Movies / Series browse style.
        // Replaces the old TopBar + left-sidebar combo. Holds the
        // category dropdown, live-search pill and a "GUIDE" CTA.

        LiveCategoryToolbar(
            selectedLabel = currentCategory?.category_name ?: "Live TV",
            categoryCount = filteredChannels.size,
            totalCategories = uiCategories.size,
            searchQuery = searchQuery,
            onSearchChange = {
                searchQuery = it
                searchOpen = it.isNotEmpty()
            },
            dropdownExpanded = dropdownExpanded,
            onDropdownToggle = { dropdownExpanded = !dropdownExpanded },
            onDropdownClose = { dropdownExpanded = false },
            categories = uiCategories,
            selectedIndex = selectedCatIndex,
            onPickCategory = { idx ->
                if (selectedCatIndex != idx) focusedChannelIdx = 0
                selectedCatIndex = idx
                dropdownExpanded = false
                pendingJumpToFirstChannel = true
            },
            onOpenGuide = {
                NavState.liveChannels = filteredChannels
                nav.navigate("epg/$playlistId")
            },
            dropdownFocus = dropdownFocus,
            searchFocus = searchFocusTB,
            guideFocus = guideFocus,
            downTarget = firstChannelFocus,
        )

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

        // ── Right pane (now FULL WIDTH). Preview bar at top + channels below. ──
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onFocusChanged { channelsPaneFocused = it.hasFocus && focusedChannelIdx >= 0 }
        ) {
            // ── Tivimate-style preview bar (video + EPG info) ──────────
            PreviewBar(
                channel = filteredChannels.getOrNull(focusedChannelIdx),
                player = previewPlayer,
                showVideo = channelsPaneFocused,
            )

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))

            // ── Channel list (scrolls below the preview bar) ──────────
            ChannelsPane(
                playlistId = playlistId,
                host = playlist?.host ?: "",
                username = playlist?.username ?: "",
                password = playlist?.password ?: "",
                channels = filteredChannels,
                loading = loadingChans,
                onFocusChange = { focusedChannelIdx = it },
                initialFocusIndex = focusedChannelIdx,
                firstChannelFocus = firstChannelFocus,
                onLeftEdge = { /* no sidebar to return to */ },
                onPlay = onPlay,
                emptyReason = when {
                    searchQuery.isNotBlank() && filteredChannels.isEmpty() ->
                        "No channels matching \"$searchQuery\""
                    channels.isEmpty() && !loadingChans -> "No channels in this category"
                    else -> null
                },
                topRowUpTarget = dropdownFocus,
            )
        }
    }
    } // end useSidebar branch
    // ── TOP NAV overlay ─────────────────────────────────────
    val navTabs = remember {
        listOf(
            com.hushtv.tv.ui.screens.home.TopNavTab(
                "home", "Home",
                androidx.compose.material.icons.Icons.Default.Home,
                "menu/$playlistId",
            ),
            com.hushtv.tv.ui.screens.home.TopNavTab(
                "live", "Live TV",
                androidx.compose.material.icons.Icons.Default.Tv,
                "browse/$playlistId/live",
            ),
            com.hushtv.tv.ui.screens.home.TopNavTab(
                "movies", "Movies",
                androidx.compose.material.icons.Icons.Default.Movie,
                "browse/$playlistId/movie",
            ),
            com.hushtv.tv.ui.screens.home.TopNavTab(
                "series", "Series",
                androidx.compose.material.icons.Icons.Outlined.Slideshow,
                "browse/$playlistId/series",
            ),
            com.hushtv.tv.ui.screens.home.TopNavTab(
                "search", "Search",
                androidx.compose.material.icons.Icons.Default.Search,
                "search/$playlistId",
            ),
        )
    }
    val navHomeFocus = remember { FocusRequester() }
    Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
        com.hushtv.tv.ui.screens.home.TopNavBar(
            tabs = navTabs,
            activeKey = "live",
            homeFocus = navHomeFocus,
            onTab = { t ->
                if (t.key == "live") return@TopNavBar
                t.route?.let { route ->
                    nav.navigate(route) {
                        popUpTo("menu/$playlistId") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            },
            onSettings = { nav.navigate("settings/$playlistId") },
            layoutHint = if (useSidebar) "SIDEBAR" else "TOP BAR",
            onLayoutHintClick = { showLayoutChooser = true },
        )
    }

    // ── Category-picker overlay. Rendered at the ROOT Box so it sits
    // above every other pane (preview bar, channel list, toolbar) —
    // fixes the "CTV logo bleed-through" the user reported. Top nav
    // stays on top thanks to the preceding Box overlay ordering.
    if (dropdownExpanded && !useSidebar) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 72.dp),
        ) {
            LiveCategoryPanel(
                categories = uiCategories,
                selectedIndex = selectedCatIndex,
                onPick = { idx ->
                    if (selectedCatIndex != idx) focusedChannelIdx = 0
                    selectedCatIndex = idx
                    dropdownExpanded = false
                    pendingJumpToFirstChannel = true
                },
                onDismiss = { dropdownExpanded = false },
            )
        }
    }
    } // close outer Box

    // ── Layout chooser ───────────────────────────────────────────
    // Opens when the user clicks the SIDEBAR / TOP BAR hint chip in
    // the top nav. Settings → Change Layout shows the same modal.
    if (showLayoutChooser) {
        com.hushtv.tv.ui.screens.home.LayoutChooserDialog(
            currentMode = currentLayoutMode,
            dismissable = true,
            onPicked = { mode ->
                com.hushtv.tv.data.LayoutPrefsStore.setMode(ctx, mode)
                currentLayoutMode = mode
                showLayoutChooser = false
            },
            onDismiss = { showLayoutChooser = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tivimate-style Preview Bar — video on the left, EPG info on the right.
// Sits as a fixed header at the top of the right pane. Never steals focus.
// Shows the channel the user currently has focus on in the channels list.
// ─────────────────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PreviewBar(
    channel: MediaCard?,
    player: ExoPlayer,
    showVideo: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Color(0xFF050A15))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left — 16:9 video box (fixed aspect, fills vertical space)
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .background(Color.Black, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel != null && showVideo) {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (channel != null) {
                // Focus is in sidebar — show a static placeholder poster
                ChannelLogoLarge(channel.poster, channel.title)
            } else {
                Icon(
                    Icons.Default.Tv, null,
                    tint = Color(0x55FFFFFF),
                    modifier = Modifier.size(44.dp),
                )
            }
            // LIVE badge
            if (channel != null && showVideo) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xDCDC2626), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Box(Modifier.size(5.dp).background(Color.White, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                    )
                }
            }
        }

        Spacer(Modifier.width(18.dp))

        // Right — EPG info panel
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            if (channel == null) {
                Text(
                    "Select a channel to preview",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
                return@Column
            }
            EpgInfoPanel(channel = channel)
        }
    }
}

@Composable
private fun EpgInfoPanel(channel: MediaCard) {
    val now = EpgService.nowPlaying(channel.streamId)
    val next = EpgService.nextUp(channel.streamId)

    // Channel name
    Text(
        channel.title,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
    Spacer(Modifier.height(8.dp))

    if (now != null) {
        // NOW PLAYING
        Text(
            "NOW",
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            now.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatClock(now.startMs)} – ${formatClock(now.stopMs)}  •  ${now.minutesLeft} min left",
            color = TextSecondary,
            fontSize = 11.sp,
        )
        // Progress bar
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0x22FFFFFF), RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(now.progressPct)
                    .height(3.dp)
                    .background(Cyan, RoundedCornerShape(2.dp))
            )
        }
        // Description (truncated)
        if (now.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                now.description,
                color = Color(0xFFB3B8C1),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
            )
        }
    } else {
        Text(
            "No program information",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }

    if (next != null) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NEXT",
                color = Color(0xFFFACC15),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatClock(next.startMs),
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                next.title,
                color = Color(0xFFD1D5DB),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChannelLogoLarge(url: String?, name: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Text(
                        name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Cyan,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                    )
                },
                loading = {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(28.dp))
                },
            )
        } else {
            Text(
                name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Cyan,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun formatClock(ms: Long): String {
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    return fmt.format(java.util.Date(ms))
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────


// ─────────────────────────────────────────────────────────────────────────────
// LIVE CATEGORY TOOLBAR — replaces the left sidebar. Dropdown + Search + Guide.
// Matches the visual language of the Movies / Series BrowseScreen toolbar.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveCategoryToolbar(
    selectedLabel: String,
    categoryCount: Int,
    totalCategories: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    dropdownExpanded: Boolean,
    onDropdownToggle: () -> Unit,
    onDropdownClose: () -> Unit,
    categories: List<XtreamCategory>,
    selectedIndex: Int,
    onPickCategory: (Int) -> Unit,
    onOpenGuide: () -> Unit,
    dropdownFocus: FocusRequester,
    searchFocus: FocusRequester,
    guideFocus: FocusRequester,
    downTarget: FocusRequester,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── LEFT: Accent bar + Browse dropdown ──
        // Search bar is gone per user request — Browse now sits on
        // the left where the title used to be, and the title cluster
        // is pushed to the right.
        Box(
            Modifier
                .size(width = 3.dp, height = 22.dp)
                .background(Cyan, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))

        LiveDropdownButton(
            label = selectedLabel,
            totalCount = totalCategories,
            expanded = dropdownExpanded,
            onToggle = onDropdownToggle,
            focusRequester = dropdownFocus,
            downTarget = downTarget,
            rightTarget = dropdownFocus, // no search; keep right on self
        )

        // Flex spacer pushes the title cluster to the right.
        Spacer(Modifier.weight(1f))

        // ── RIGHT: Title + live category name ──
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "LIVE TV  ·  ${categoryCount} CH",
                color = Cyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp,
                fontFamily = Inter,
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                selectedLabel,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 18.sp,
                fontFamily = Inter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // NOTE: the dropdown panel is rendered at the ROOT Box level
    // (see callsite), NOT here, so it can overlay the whole screen.
}

@Composable
private fun LiveDropdownButton(
    label: String,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester,
    downTarget: FocusRequester,
    rightTarget: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .background(Color(0xFF0A121F), RoundedCornerShape(10.dp))
            .border(
                width = if (focused || expanded) 2.dp else 1.dp,
                color = if (focused || expanded) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                down = downTarget
                right = rightTarget
            }
            .focusable()
            .clickableWithEnter(onToggle),
    ) {
        Text(
            "BROWSE",
            color = Cyan,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Black,
            fontFamily = Inter,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (totalCount > 0) label else "Loading…",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.width(8.dp))
        val rot by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(160),
            label = "live-chev-rot",
        )
        Text(
            "▼",
            color = if (focused || expanded) Cyan else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { rotationZ = rot },
        )
    }
}

@Composable
private fun LiveInlineSearch(
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
            .height(40.dp)
            .background(Color(0xFF0A121F), RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) Cyan else Color(0xFF64748B),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = Inter),
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
                    "Search channels…",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    fontFamily = Inter,
                )
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0x22FFFFFF))
                    .focusable()
                    .focusProperties { down = downTarget }
                    .clickableWithEnter { onChange("") },
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

@Composable
private fun GuideButton(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    downTarget: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .background(
                if (focused) Cyan else Color(0xFF0A121F),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 18.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { down = downTarget }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = if (focused) Color(0xFF05080F) else Cyan,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "FULL GUIDE",
            color = if (focused) Color(0xFF05080F) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun LiveCategoryPanel(
    categories: List<XtreamCategory>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstItemFocus.requestFocus() }
    }

    // Full-height, fully opaque overlay — was previously a short 440 dp
    // window so the preview/channel list bled through below it (including
    // the big hushtv. branding, which looked terrible). Now it claims the
    // whole screen under the top nav for a proper picker experience.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F))
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Back) {
                    onDismiss(); true
                } else false
            }
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 24.dp)
                        .background(Cyan, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "PICK A CHANNEL CATEGORY",
                    color = Cyan,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${categories.size} categories · press BACK to close",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontFamily = Inter,
                )
            }
            Spacer(Modifier.height(18.dp))

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Single-column list — matches the old left-sidebar
                // ordering so users can scan EVERY category in the
                // provider's original order without skipping across
                // columns. LazyColumn so it scrolls fluidly.
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = categories.size,
                        key = { idx -> categories[idx].category_id },
                    ) { idx ->
                        val cat = categories[idx]
                        LiveCategoryPill(
                            name = cat.category_name,
                            selected = idx == selectedIndex,
                            focusMod = if (idx == 0)
                                Modifier.focusRequester(firstItemFocus) else Modifier,
                            onClick = { onPick(idx) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCategoryPill(
    name: String,
    selected: Boolean,
    focusMod: Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = focusMod
            .fillMaxWidth()
            .height(40.dp)
            .background(
                when {
                    focused -> Cyan.copy(alpha = 0.18f)
                    selected -> Color(0xFF14223A)
                    else -> Color(0x14FFFFFF)
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                color = when {
                    focused -> Cyan
                    selected -> Cyan.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = null,
            tint = if (selected || focused) Cyan else Color(0xFFCBD5E1),
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            color = if (selected || focused) Color.White else Color(0xFFE2E8F0),
            fontSize = 13.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            Text(
                "✓",
                color = Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}


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
    returnToSidebarToken: Int,
    onSelect: (Int) -> Unit,
    onEnter: (Int) -> Unit,
    restoreFocusIndex: Int
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoreFocusIndex.coerceAtLeast(0)
    )
    val focusByIndex = remember { mutableMapOf<Int, FocusRequester>() }
    fun reqFor(idx: Int) = focusByIndex.getOrPut(idx) { FocusRequester() }

    // Restore focus to the previously-selected category on first render with data.
    LaunchedEffect(categories.size) {
        if (categories.isNotEmpty()) {
            val target = restoreFocusIndex.coerceIn(0, categories.size - 1)
            runCatching { reqFor(target).requestFocus() }
        }
    }
    // Keep the selected category visible as user D-pads down in the sidebar.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in categories.indices) {
            runCatching { listState.animateScrollToItem(selectedIndex) }
        }
    }

    // ── Return-to-sidebar token. Incremented externally whenever the user
    //   presses LEFT from the channels pane. We scroll the selected row
    //   into view, yield a frame so LazyColumn composes it, then request
    //   focus. With retries to guarantee success. ──
    LaunchedEffect(returnToSidebarToken) {
        if (returnToSidebarToken == 0) return@LaunchedEffect
        if (categories.isEmpty()) return@LaunchedEffect
        val idx = selectedIndex.coerceIn(0, categories.size - 1)
        runCatching { listState.scrollToItem(idx) }
        // Retry loop — requestFocus() can fail if the row hasn't been
        // composed yet; try a few frames.
        repeat(5) {
            kotlinx.coroutines.delay(40)
            val ok = runCatching { reqFor(idx).requestFocus() }.isSuccess
            if (ok) return@LaunchedEffect
        }
    }

    Column(
        Modifier.width(220.dp).fillMaxHeight().background(Color(0x33000000))
    ) {
        Text(
            "CATEGORIES",
            color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)
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
                        onClick = { onEnter(idx) }
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true)
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
    firstChannelFocus: FocusRequester,
    onLeftEdge: () -> Unit,
    onFocusChange: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    topRowUpTarget: FocusRequester? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusByIndex = remember { mutableMapOf<Int, FocusRequester>() }
    fun reqFor(idx: Int) = focusByIndex.getOrPut(idx) { FocusRequester() }
    val listState = rememberLazyListState()

    // Re-render when EPG arrives
    var epgVersion by remember { mutableStateOf(0) }

    // Track whether we've already hijacked focus once (only for nav-back restore).
    var hasRestoredFocus by remember { mutableStateOf(false) }
    val shouldRestoreOnFirstLoad = remember {
        NavState.browsePlaylistId == playlistId && initialFocusIndex > 0
    }

    // When the list populates, scroll to the target. Only steal focus ONCE on
    // the initial nav-back restore — never when the user is browsing categories
    // with focus still in the sidebar.
    LaunchedEffect(channels) {
        if (channels.isEmpty()) return@LaunchedEffect
        val target = initialFocusIndex.coerceIn(0, channels.size - 1)
        if (shouldRestoreOnFirstLoad && !hasRestoredFocus) {
            hasRestoredFocus = true
            runCatching {
                listState.scrollToItem(target.coerceAtLeast(0))
                reqFor(target).requestFocus()
            }
        } else {
            // New category loaded silently — just reset scroll to top.
            runCatching { listState.scrollToItem(0) }
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                        val rowModifier = if (idx == 0) {
                            Modifier
                                .focusRequester(firstChannelFocus)
                                .focusRequester(reqFor(idx))
                                .let {
                                    if (topRowUpTarget != null)
                                        it.focusProperties { up = topRowUpTarget } else it
                                }
                        } else {
                            Modifier.focusRequester(reqFor(idx))
                        }
                        ChannelRow(
                            number = idx + 1,
                            channel = channels[idx],
                            nowPlaying = EpgService.nowPlaying(channels[idx].streamId).also { epgVersion },
                            isFavorite = favs.contains(channels[idx].streamId),
                            modifier = rowModifier,
                            onFocus = { onFocusChange(idx) },
                            onPlay = { onPlay(idx) },
                            onLeftEdge = onLeftEdge,
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
    onLeftEdge: () -> Unit,
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
            // ── Direct LEFT interception on the focusable itself. This is
            //   the ONLY reliable place to intercept because Compose's
            //   geometric focus search fires on the focused node.
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                    onLeftEdge()
                    true
                } else false
            }
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
