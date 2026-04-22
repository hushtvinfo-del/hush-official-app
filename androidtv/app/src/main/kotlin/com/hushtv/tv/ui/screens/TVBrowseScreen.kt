package com.hushtv.tv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.MyListStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.WatchProgressStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamCategory
import com.hushtv.tv.data.XtreamVodInfo
import com.hushtv.tv.ui.theme.Amber
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.BorderSlate
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextDim
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tv.PositionFocusedItemInLazyLayout
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/* ──────────────────────────────────────────────────────────────── */
/*  VIRTUAL CATEGORIES                                              */
/* ──────────────────────────────────────────────────────────────── */

private const val CAT_ALL = "__all__"
private const val CAT_SEARCH = "__search__"
private const val CAT_FAV = "__fav__"

/** Days considered "new" — retained for internal use but not exposed in UI. */
private const val NEW_WINDOW_DAYS = 14L

/* ──────────────────────────────────────────────────────────────── */
/*  SCREEN                                                          */
/* ──────────────────────────────────────────────────────────────── */

@Composable
fun TVBrowseScreen(nav: NavController, playlistId: String, type: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    // "favorites" is an alias for my-list (movie kind). Keep "search" as a full-screen mode.
    val effectiveKind = when (type) {
        "series" -> "series"
        else -> "movie"
    }

    // ── Data state ───────────────────────────────────────────────
    var allCategories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var allItems by remember { mutableStateOf<List<MediaCard>>(emptyList()) } // for All/Search/New
    val itemCache = remember { mutableStateMapOf<String, List<MediaCard>>() }
    var loadingCats by remember { mutableStateOf(true) }
    var loadingItems by remember { mutableStateOf(false) }

    var selectedCatId by remember { mutableStateOf(if (type == "search") CAT_SEARCH else CAT_ALL) }
    var searchQuery by remember { mutableStateOf("") }

    // ── Fetch categories + All pool once ──────────────────────────
    LaunchedEffect(playlistId, effectiveKind) {
        val p = playlist ?: return@LaunchedEffect
        loadingCats = true
        runCatching {
            allCategories = XtreamApi.getCategories(p.host, p.username, p.password, effectiveKind)
        }
        loadingCats = false
        // Kick off an "all" fetch in the background so virtual categories (New, Top, A-Z, Search) are ready.
        scope.launch {
            runCatching {
                allItems = XtreamApi.getAllStreams(p.host, p.username, p.password, effectiveKind)
            }
        }
    }

    // Virtual sidebar: [Search] + [Favorites, All] + real categories
    val sidebarEntries: List<SidebarEntry> = remember(allCategories, type) {
        buildList {
            add(SidebarEntry(CAT_SEARCH, "Search", Icons.Default.Search))
            add(SidebarEntry("__divider__", "", Icons.Default.Info, isDivider = true))
            add(SidebarEntry(CAT_FAV, "Favorites", Icons.Default.Star))
            add(SidebarEntry(CAT_ALL, "All", Icons.Outlined.Slideshow))
            allCategories.forEach { c ->
                add(SidebarEntry(c.category_id, c.category_name, Icons.Default.Movie))
            }
        }
    }

    // ── Grid items for the currently-selected category ────────────
    var gridItems by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var myListVersion by remember { mutableStateOf(0) }
    var watchVersion by remember { mutableStateOf(0) }

    LaunchedEffect(selectedCatId, allItems, myListVersion, watchVersion) {
        val p = playlist ?: return@LaunchedEffect
        when (selectedCatId) {
            CAT_ALL -> gridItems = allItems
            CAT_FAV -> {
                // Filter allItems by "in My List" for the current kind.
                // Movies key on streamId, series on seriesId.
                val favIds = MyListStore.getAll(ctx, playlistId, effectiveKind)
                gridItems = allItems.filter {
                    val id = if (it.kind == "series") it.seriesId else it.streamId
                    favIds.contains(id)
                }
            }
            CAT_SEARCH -> {
                val q = searchQuery.trim().lowercase()
                gridItems = if (q.length < 2) emptyList()
                else allItems.filter { it.title.lowercase().contains(q) }.take(200)
            }
            "__divider__", "__divider2__" -> gridItems = emptyList()
            else -> {
                // Real category
                val cached = itemCache[selectedCatId]
                if (cached != null) gridItems = cached
                else {
                    loadingItems = true
                    runCatching {
                        val list = XtreamApi.getStreamsForCategory(
                            p.host, p.username, p.password, effectiveKind, selectedCatId,
                        )
                        itemCache[selectedCatId] = list
                        gridItems = list
                    }
                    loadingItems = false
                }
            }
        }
    }

    // ── Focus & detail state ─────────────────────────────────────
    var focusedIdx by remember { mutableStateOf(-1) }
    var gridHasFocus by remember { mutableStateOf(false) }
    // Number of columns in the current grid layout — updated by BoxWithConstraints.
    var gridColumnCount by remember { mutableStateOf(8) }
    var vodInfo by remember { mutableStateOf<XtreamVodInfo?>(null) }

    // Fetch detailed VOD info for the focused movie (debounced, cancellable).
    LaunchedEffect(focusedIdx, gridItems) {
        vodInfo = null
        val p = playlist ?: return@LaunchedEffect
        val item = gridItems.getOrNull(focusedIdx) ?: return@LaunchedEffect
        if (item.kind != "movie") return@LaunchedEffect
        delay(350)
        vodInfo = XtreamApi.getVodInfo(p.host, p.username, p.password, item.streamId)
    }

    val firstGridFocus = remember { FocusRequester() }
    var pendingJumpToGrid by remember { mutableStateOf(false) }
    LaunchedEffect(gridItems, pendingJumpToGrid) {
        if (pendingJumpToGrid && gridItems.isNotEmpty()) {
            delay(90)
            runCatching { firstGridFocus.requestFocus() }
            pendingJumpToGrid = false
        }
    }

    // Trigger token — increment to return focus to the sidebar at the
    // currently-selected entry. Used by LEFT-key interception on the grid.
    var returnToSidebarToken by remember { mutableStateOf(0) }

    // Trailer dialog state
    var trailerVideoId by remember { mutableStateOf<String?>(null) }

    // ── Click handler ────────────────────────────────────────────
    val onCardClick: (MediaCard) -> Unit = sel@{ item ->
        when (item.kind) {
            "movie" -> nav.navigate(
                "moviedetail/$playlistId/${item.streamId}/${Uri.encode(item.title)}"
            )
            "series" -> nav.navigate(
                "series/$playlistId/${item.seriesId}/${Uri.encode(item.title)}"
            )
        }
    }

    val title = when (type) {
        "series" -> "Series"
        "favorites" -> "Favorites"
        "search" -> "Search"
        else -> "Movies"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        // ── Blurred backdrop (crossfades on focus change) ──────────
        BlurredBackdrop(
            posterUrl = gridItems.getOrNull(focusedIdx)?.poster,
            backdropUrl = vodInfo?.info?.backdrop_path?.firstOrNull(),
            visible = gridHasFocus,
        )

        // Animated sidebar width — collapses to 0 when focus is in the grid.
        val sidebarWidth by animateDpAsState(
            targetValue = if (gridHasFocus) 0.dp else 200.dp,
            animationSpec = tween(120),
            label = "vod-sidebar-width",
        )

        Row(Modifier.fillMaxSize()) {
            // ── LEFT SIDEBAR — animated-width wrapper ──────────
            Box(
                Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                VodSidebar(
                    entries = sidebarEntries,
                    selectedId = selectedCatId,
                    title = title,
                    loading = loadingCats,
                    returnToSidebarToken = returnToSidebarToken,
                    modifier = Modifier.requiredWidth(200.dp),
                    onBack = { nav.popBackStack() },
                    onFocus = { id -> if (id != "__divider__" && id != "__divider2__") selectedCatId = id },
                    onEnter = { id ->
                        if (id == "__divider__" || id == "__divider2__") return@VodSidebar
                        selectedCatId = id
                        pendingJumpToGrid = true
                    },
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    isSearchActive = selectedCatId == CAT_SEARCH,
                )
            }

            // Thin divider — only visible when sidebar is expanded
            if (sidebarWidth > 20.dp) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x14FFFFFF)))
            }

            // ── RIGHT PANE ──────────────────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { gridHasFocus = it.hasFocus }
                    // Parent-level LEFT interception. Fires for ANY focused
                    // descendant before the child gets to do its own focus
                    // search. Guarantees LEFT always returns to the selected
                    // sidebar row, no matter where the user is in the grid.
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                            // Only intercept when on the leftmost column of the grid.
                            // (If user is in column 2+, let Compose move to column 1 naturally.)
                            if (focusedIdx >= 0 && focusedIdx % gridColumnCount == 0) {
                                returnToSidebarToken++
                                true
                            } else false
                        } else false
                    },
            ) {
                // Top section: detail panel (only when grid has focus + item exists).
                // IMPORTANT: give the Crossfade a FIXED height so that the grid
                // below never shifts when the user moves focus between the
                // sidebar and the grid. Layout shifts are one of the main
                // reasons D-pad focus-scroll drifts in the "wrong direction".
                val focusedItem = gridItems.getOrNull(focusedIdx)
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    Crossfade(
                        targetState = gridHasFocus && focusedItem != null,
                        animationSpec = tween(150),
                        label = "detail-panel",
                    ) { showDetail ->
                        if (showDetail) {
                            DetailPanel(
                                item = focusedItem!!,
                                info = vodInfo,
                                isInMyList = remember(myListVersion) {
                                    val id = if (focusedItem.kind == "series") focusedItem.seriesId else focusedItem.streamId
                                    MyListStore.isInList(ctx, playlistId, focusedItem.kind, id)
                                },
                                onPlay = { onCardClick(focusedItem) },
                                onToggleMyList = {
                                    val id = if (focusedItem.kind == "series") focusedItem.seriesId else focusedItem.streamId
                                    MyListStore.toggle(ctx, playlistId, focusedItem.kind, id)
                                    myListVersion++
                                },
                                onTrailer = { vid ->
                                    trailerVideoId = vid
                                },
                            )
                        } else {
                            CategoryHeader(title = sidebarEntries.firstOrNull { it.id == selectedCatId }?.label ?: title)
                        }
                    }
                }

                // ── Poster grid ────────────────────────────────
                // weight(1f) so the grid claims the exact remaining vertical
                // space — otherwise LazyVerticalGrid falls back to intrinsic
                // sizing which breaks focus-driven scroll math.
                val gridState = rememberLazyGridState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loadingItems -> CenterLoader()
                        selectedCatId == CAT_SEARCH && searchQuery.trim().length < 2 ->
                            InfoBox("Type at least 2 characters to search.", Icons.Default.Search)
                        gridItems.isEmpty() ->
                            InfoBox(
                                msg = when (selectedCatId) {
                                    CAT_SEARCH -> "No results for \"$searchQuery\"."
                                    CAT_FAV -> "No favorites yet. Press and hold the OK / ENTER button on any poster to add it here."
                                    else -> "Nothing here yet."
                                },
                                icon = when (selectedCatId) {
                                    CAT_FAV -> Icons.Default.Star
                                    else -> Icons.Default.Movie
                                },
                            )
                        else -> {
                            // Track column count — used both for laying out the
                            // grid AND for the parent-level LEFT interception.
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val cols = ((maxWidth + 12.dp) / (124.dp + 12.dp)).toInt().coerceAtLeast(1)
                                LaunchedEffect(cols) { gridColumnCount = cols }

                                // TV-grade focus pivot: keeps the focused row
                                // anchored ~30% from the top of the viewport
                                // regardless of D-pad direction. This replaces
                                // the deprecated TvLazyVerticalGrid.pivotOffsets
                                // per Google's 2026 guidance for Compose 1.7+.
                                PositionFocusedItemInLazyLayout(parentFraction = 0.3f) {
                                    LazyVerticalGrid(
                                        state = gridState,
                                        columns = GridCells.Fixed(cols),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        itemsIndexed(gridItems, key = { i, it -> "${it.kind}-${it.id}-$i" }) { idx, item ->
                                            val progress = remember(watchVersion, item.id) {
                                                WatchProgressStore.getRatio(ctx, item.streamId, item.kind)
                                            }
                                            val isInList = remember(myListVersion, item.id) {
                                                val id = if (item.kind == "series") item.seriesId else item.streamId
                                                MyListStore.isInList(ctx, playlistId, item.kind, id)
                                            }
                                            CompactPoster(
                                                item = item,
                                                progress = progress,
                                                isInList = isInList,
                                                focusMod = if (idx == 0) Modifier.focusRequester(firstGridFocus) else Modifier,
                                                onFocus = { focusedIdx = idx },
                                                onLeftEdge = { returnToSidebarToken++ },
                                                isLeftmost = idx % cols == 0,
                                                onClick = { onCardClick(item) },
                                                onLongPressFavorite = {
                                                    val id = if (item.kind == "series") item.seriesId else item.streamId
                                                    val nowInList = MyListStore.toggle(ctx, playlistId, item.kind, id)
                                                    myListVersion++
                                                    android.widget.Toast.makeText(
                                                        ctx,
                                                        if (nowInList) "Added to Favorites" else "Removed from Favorites",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
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
        }
    }

    // ── Trailer dialog ───────────────────────────────────────────
    trailerVideoId?.let { vid ->
        TrailerDialog(
            videoId = vid,
            onClose = { trailerVideoId = null },
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  MODELS                                                          */
/* ──────────────────────────────────────────────────────────────── */

private data class SidebarEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val isDivider: Boolean = false,
)

/* ──────────────────────────────────────────────────────────────── */
/*  LEFT SIDEBAR                                                    */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun VodSidebar(
    entries: List<SidebarEntry>,
    selectedId: String,
    title: String,
    loading: Boolean,
    returnToSidebarToken: Int,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onFocus: (String) -> Unit,
    onEnter: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isSearchActive: Boolean,
) {
    val listState = rememberLazyListState()
    val focusByIndex = remember { mutableMapOf<Int, FocusRequester>() }
    fun reqFor(i: Int) = focusByIndex.getOrPut(i) { FocusRequester() }

    // Auto-focus the currently selected item on first render.
    LaunchedEffect(entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val idx = entries.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        runCatching {
            listState.scrollToItem(idx)
            reqFor(idx).requestFocus()
        }
    }

    // Return-to-sidebar token. When LEFT is pressed in the grid, the parent
    // increments this token; we scroll + focus the SELECTED row with retries.
    LaunchedEffect(returnToSidebarToken) {
        if (returnToSidebarToken == 0 || entries.isEmpty()) return@LaunchedEffect
        val idx = entries.indexOfFirst { it.id == selectedId }
        if (idx < 0) return@LaunchedEffect
        runCatching { listState.scrollToItem(idx) }
        repeat(5) {
            kotlinx.coroutines.delay(40)
            val ok = runCatching { reqFor(idx).requestFocus() }.isSuccess
            if (ok) return@LaunchedEffect
        }
    }

    Column(
        modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Color(0x80000000)),
    ) {
        // Header — back button + screen title
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .tvFocusable(shape = CircleShape, fillOnFocus = false)
                    .clickableWithEnter(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
        }

        // Inline search box (shown when CAT_SEARCH is active)
        if (isSearchActive) {
            SearchBox(
                value = searchQuery,
                onChange = onSearchChange,
                placeholder = "Search titles…",
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(
                    count = entries.size,
                    key = { i -> "${entries[i].id}-$i" },
                ) { i ->
                    val entry = entries[i]
                    if (entry.isDivider) {
                        Box(
                            Modifier
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0x14FFFFFF)),
                        )
                    } else {
                        SidebarRow(
                            entry = entry,
                            selected = entry.id == selectedId,
                            modifier = Modifier.focusRequester(reqFor(i)),
                            onFocus = { onFocus(entry.id) },
                            onClick = { onEnter(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarRow(
    entry: SidebarEntry,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Cyan
        selected -> Color(0x2606B6D4)
        else -> Color.Transparent
    }
    val tint = when {
        focused -> Color.Black
        selected -> Cyan
        else -> Color(0xFFB8BDC7)
    }
    val leftBar = if (selected || focused) Cyan else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(vertical = 10.dp, horizontal = 10.dp),
    ) {
        Box(
            Modifier.width(3.dp).height(22.dp)
                .background(leftBar, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Icon(entry.icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            entry.label,
            color = tint,
            fontSize = 13.sp,
            fontFamily = Inter,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchBox(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "Search…",
    trailingLoader: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(8.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Cyan else Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = Inter),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
            )
            if (value.isEmpty()) {
                Text(placeholder, color = TextMuted, fontSize = 14.sp, fontFamily = Inter)
            }
        }
        if (trailingLoader) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  DETAIL PANEL (top of grid)                                      */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun DetailPanel(
    item: MediaCard,
    info: XtreamVodInfo?,
    isInMyList: Boolean,
    onPlay: () -> Unit,
    onToggleMyList: () -> Unit,
    onTrailer: (String) -> Unit,
) {
    val inner = info?.info
    val year = inner?.releasedate?.take(4) ?: inner?.release_date?.take(4) ?: ""
    val duration = inner?.duration ?: durationFromSecs(inner?.duration_secs)
    val genres = (inner?.genre ?: "").split(',', '/').map { it.trim() }.filter { it.isNotBlank() }
    val rating = item.rating?.toFloatOrNull() ?: inner?.rating?.toFloatOrNull()
    val plot = (inner?.plot ?: inner?.description ?: "").trim()
    val trailerId = inner?.youtube_trailer?.let { extractYoutubeId(it) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(24.dp),
    ) {
        // Left column — title block
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            // Badge
            Text(
                if (item.kind == "series") "SERIES" else "MOVIE",
                color = Cyan,
                fontSize = 10.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(6.dp))
            // Title
            Text(
                item.title,
                color = TextPrimary,
                fontSize = 30.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
                lineHeight = 32.sp,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            // Meta line
            Row(verticalAlignment = Alignment.CenterVertically) {
                rating?.let {
                    Icon(Icons.Default.Star, null, tint = Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        String.format("%.1f", it),
                        color = Amber,
                        fontSize = 13.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                    )
                    MetaDot()
                }
                if (year.isNotBlank()) {
                    Text(year, color = TextSecondary, fontSize = 13.sp, fontFamily = Inter)
                    MetaDot()
                }
                if (duration.isNotBlank()) {
                    Text(duration, color = TextSecondary, fontSize = 13.sp, fontFamily = Inter)
                }
            }

            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genres.take(4).forEach { g ->
                        GenreChip(g)
                    }
                }
            }

            if (plot.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    plot,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontFamily = Inter,
                    lineHeight = 16.sp,
                    maxLines = 3,
                )
            }

            Spacer(Modifier.height(12.dp))

            // CTA row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailCta(
                    label = "Play",
                    icon = Icons.Default.PlayArrow,
                    primary = true,
                    onClick = onPlay,
                )
                DetailCta(
                    label = if (isInMyList) "In Favorites" else "Add to Favorites",
                    icon = if (isInMyList) Icons.Default.Star else Icons.Default.StarBorder,
                    primary = false,
                    onClick = onToggleMyList,
                )
                trailerId?.let { vid ->
                    DetailCta(
                        label = "Trailer",
                        icon = Icons.Default.PlayCircle,
                        primary = false,
                        onClick = { onTrailer(vid) },
                    )
                }
            }
        }

        Spacer(Modifier.width(18.dp))

        // Right column — poster thumbnail
        val posterUrl = inner?.cover_big ?: inner?.movie_image ?: item.poster
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceNavy),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { PosterStub() },
                    loading = { PosterStub() },
                )
            } else {
                PosterStub()
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 22.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )
    }
}

@Composable
private fun MetaDot() {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.size(3.dp).background(TextDim, CircleShape))
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun GenreChip(text: String) {
    Box(
        Modifier
            .background(Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            color = Color(0xFFD1D5DB),
            fontSize = 10.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun DetailCta(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(90),
        label = "cta-scale",
    )
    val bg = when {
        primary && focused -> Cyan
        primary -> Color.White
        focused -> Color(0x3306B6D4)
        else -> Color(0x1FFFFFFF)
    }
    val fg = when {
        primary -> Color.Black
        focused -> Cyan
        else -> Color.White
    }
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(40.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                2.dp,
                if (focused) Cyan else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  COMPACT POSTER (grid tile)                                      */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun CompactPoster(
    item: MediaCard,
    progress: Float,
    isInList: Boolean,
    focusMod: Modifier,
    onFocus: () -> Unit,
    onLeftEdge: () -> Unit,
    isLeftmost: Boolean,
    onClick: () -> Unit,
    onLongPressFavorite: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(90),
        label = "poster-scale",
    )
    // Track whether the current press already fired a long-press toggle so
    // we can consume the subsequent KeyUp and prevent the short-press click
    // (clickableWithEnter) from navigating into the detail screen.
    var longPressFiredByMe by remember { mutableStateOf(false) }
    Column(
        modifier = focusMod
            .onPreviewKeyEvent { ev ->
                // 1) Leftmost LEFT → bubble to sidebar
                if (isLeftmost && ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                    onLeftEdge()
                    return@onPreviewKeyEvent true
                }
                // 2) Long-press OK / ENTER / DPAD_CENTER → toggle favorite
                val isEnterKey = ev.key == Key.Enter ||
                    ev.key == Key.DirectionCenter ||
                    ev.key == Key.NumPadEnter
                if (isEnterKey) {
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            if (ev.nativeKeyEvent.isLongPress) {
                                longPressFiredByMe = true
                                onLongPressFavorite()
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (longPressFiredByMe) {
                                // Eat the KeyUp so clickableWithEnter does not
                                // also fire a short-press navigate.
                                longPressFiredByMe = false
                                return@onPreviewKeyEvent true
                            }
                        }
                        else -> {}
                    }
                }
                false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnter(onClick)
            // Scale is applied AFTER focusable so the focusable's layout
            // bounds are always at their natural (unscaled) size. This keeps
            // Compose's D-pad focus search + bring-into-view scroll math
            // deterministic — previously, scaling the whole Column was
            // nudging the focus bounds just enough to cause "wrong
            // direction" drift on dense grids.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceNavy)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Cyan else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            if (!item.poster.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { PosterStub() },
                    loading = { PosterStub() },
                )
            } else {
                PosterStub()
            }

            // Rating badge (top-right)
            item.rating?.toFloatOrNull()?.takeIf { it > 0 }?.let { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Default.Star, null, tint = Amber, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        String.format("%.1f", r),
                        color = TextPrimary,
                        fontSize = 9.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Favorited corner badge (star)
            if (isInList) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .size(16.dp)
                        .background(Cyan, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }

            // Watch progress — bottom 3dp bar
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x55FFFFFF)),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(Cyan),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            color = if (focused) Cyan else TextPrimary,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun PosterStub() {
    Box(Modifier.fillMaxSize().background(SurfaceNavy), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Movie, null, tint = BorderSlate, modifier = Modifier.size(28.dp))
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  BLURRED BACKDROP                                                */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun BlurredBackdrop(posterUrl: String?, backdropUrl: String?, visible: Boolean) {
    // Blur is too expensive on TV boxes — render a soft radial/vertical
    // vignette instead. Much smoother scrolling, still cinematic.
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.18f else 0f,
        animationSpec = tween(280),
        label = "bg-alpha",
    )
    if (alpha <= 0.01f) return
    val url = backdropUrl?.takeIf { it.isNotBlank() } ?: posterUrl
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { },
                loading = { },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x55000000), Color(0xAA000000), Color(0xFF000000)),
                    )
                )
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  TRAILER DIALOG                                                  */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun TrailerDialog(videoId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    // Try to launch the system YouTube app — it handles D-pad + back perfectly.
    LaunchedEffect(videoId) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
        onClose()
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  STATELESS HELPERS                                               */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun CenterLoader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Cyan, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun InfoBox(msg: String, icon: ImageVector) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = BorderSlate, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text(msg, color = TextMuted, fontSize = 13.sp, fontFamily = Inter)
    }
}

private fun durationFromSecs(secs: Int?): String {
    if (secs == null || secs <= 0) return ""
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Accept either "xyz123" or "https://www.youtube.com/watch?v=xyz123" etc. */
private fun extractYoutubeId(raw: String): String? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    // Already a bare ID?
    if (s.length in 8..16 && !s.contains('/') && !s.contains('?')) return s
    // Extract from URL
    val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return null
    uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it }
    // youtu.be/VIDEOID
    val segments = uri.pathSegments
    segments.lastOrNull()?.takeIf { it.length in 8..16 }?.let { return it }
    return null
}
