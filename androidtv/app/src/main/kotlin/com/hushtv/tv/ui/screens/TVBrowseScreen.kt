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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
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
import com.hushtv.tv.data.XtreamVodInfoInner
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
fun TVBrowseScreen(
    nav: NavController,
    playlistId: String,
    type: String,
    initialCategoryName: String? = null,
    initialCategoryId: String? = null,
) {
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

    // Deep-link default: when caller provides a specific category ID
    // (from home Streaming-Services / Genres / Years cards), open the
    // screen ALREADY selected on that category — no name fuzzy-match
    // race condition, instant correct content.
    var selectedCatId by remember {
        mutableStateOf(
            when {
                type == "search" -> CAT_SEARCH
                !initialCategoryId.isNullOrBlank() -> initialCategoryId
                else -> CAT_ALL
            }
        )
    }
    var searchQuery by remember { mutableStateOf("") }

    // ── Fetch categories + All pool once ──────────────────────────
    LaunchedEffect(playlistId, effectiveKind) {
        val p = playlist ?: return@LaunchedEffect
        loadingCats = true
        runCatching {
            allCategories = XtreamApi.getCategories(p.host, p.username, p.password, effectiveKind)
            android.util.Log.i(
                "BrowseDeepLink",
                "loaded ${allCategories.size} $effectiveKind categories " +
                    "(deep-link id=$initialCategoryId name=$initialCategoryName)",
            )
        }
        loadingCats = false
        // Kick off an "all" fetch in the background so virtual categories (New, Top, A-Z, Search) are ready.
        scope.launch {
            runCatching {
                allItems = XtreamApi.getAllStreams(p.host, p.username, p.password, effectiveKind)
            }
        }
    }

    // Auto-select an initial category once the list loads. Priority:
    //   1. initialCategoryId  — caller passed an EXACT Xtream category
    //      ID (home Streaming-Services / Genres / Years cards). Most
    //      reliable; no string matching required.
    //   2. initialCategoryName — legacy Home Discovery cards use this.
    //      Matches EXACT normalized name first, then falls back to
    //      substring contains. Rejects dangerous "needle-in-haystack"
    //      partial matches that used to send short keywords to the
    //      wrong category.
    LaunchedEffect(initialCategoryId, initialCategoryName, allCategories) {
        if (allCategories.isEmpty()) return@LaunchedEffect

        // 1. ID-based deep-link wins and is always safe.
        if (!initialCategoryId.isNullOrBlank()) {
            val hit = allCategories.firstOrNull { it.category_id == initialCategoryId }
            if (hit != null) {
                selectedCatId = hit.category_id
                android.util.Log.i(
                    "BrowseDeepLink",
                    "direct-ID match: $initialCategoryId → ${hit.category_name}",
                )
                return@LaunchedEffect
            } else {
                android.util.Log.w(
                    "BrowseDeepLink",
                    "deep-link ID $initialCategoryId not found in $effectiveKind categories — falling back to name",
                )
            }
        }

        // 2. Name-based fallback.
        if (initialCategoryName.isNullOrBlank()) return@LaunchedEffect
        val needle = normaliseCatName(initialCategoryName)
        if (needle.isBlank()) return@LaunchedEffect

        // Prefer EXACT normalized match (e.g. "netflix" == "netflix").
        val exact = allCategories.firstOrNull {
            normaliseCatName(it.category_name) == needle
        }
        if (exact != null) {
            selectedCatId = exact.category_id
            android.util.Log.i(
                "BrowseDeepLink",
                "exact-name match: '$initialCategoryName' → ${exact.category_name} (${exact.category_id})",
            )
            return@LaunchedEffect
        }

        // Then SUBSTRING contains — but ONLY in the longer direction
        // (needle inside category name). Rejects the old buggy
        // "category name inside needle" path that let 2-char category
        // names like "A&E" hijack 3-char needles like "AMC".
        val sub = allCategories.firstOrNull {
            val hay = normaliseCatName(it.category_name)
            hay.isNotBlank() && hay.contains(needle)
        }
        if (sub != null) {
            selectedCatId = sub.category_id
            android.util.Log.i(
                "BrowseDeepLink",
                "substring-name match: '$initialCategoryName' → ${sub.category_name} (${sub.category_id})",
            )
        } else {
            android.util.Log.w(
                "BrowseDeepLink",
                "NO MATCH for '$initialCategoryName' among ${allCategories.size} categories",
            )
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
                // Legacy — kept for the dedicated Search tab route. New
                // in-screen search in Movies / Series layers on top of
                // the active category (see baseItems filter below).
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
        // ── Default sort ──
        // Movies: most recently added first (Xtream `added` unix ts).
        // Series: most recently modified first (same underlying field —
        // XtreamApi maps `last_modified` into `addedTs` for parity).
        // Items with a 0 timestamp (no value from the provider) sink to
        // the bottom, alphabetically among themselves.
        gridItems = gridItems.sortedWith(
            compareByDescending<com.hushtv.tv.data.MediaCard> { it.addedTs }
                .thenBy { it.title.lowercase() }
        )
    }

    // Final displayed items = gridItems with an optional in-screen
    // search filter layered on top. Makes the inline search work as a
    // "narrow within current category" (e.g. Drama + "breaking") or
    // globally (All + "breaking").
    val displayedItems by remember(searchQuery, gridItems, selectedCatId) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isBlank() || selectedCatId == CAT_SEARCH) gridItems
            else gridItems.filter { it.title.lowercase().contains(q) }
        }
    }

    // ── Focus & detail state ─────────────────────────────────────
    var focusedIdx by remember { mutableStateOf(-1) }
    var gridHasFocus by remember { mutableStateOf(false) }
    // Number of columns in the current grid layout — updated by BoxWithConstraints.
    var gridColumnCount by remember { mutableStateOf(8) }
    var vodInfo by remember { mutableStateOf<XtreamVodInfo?>(null) }

    // Fetch detailed VOD info for the focused item (debounced, cancellable).
    // Movies → get_vod_info. Series → get_series_info, mapped to the same
    // XtreamVodInfo shape so the preview panel can share one renderer.
    LaunchedEffect(focusedIdx, displayedItems) {
        vodInfo = null
        val p = playlist ?: return@LaunchedEffect
        val item = displayedItems.getOrNull(focusedIdx) ?: return@LaunchedEffect
        delay(350)
        vodInfo = when (item.kind) {
            "movie" -> XtreamApi.getVodInfo(p.host, p.username, p.password, item.streamId)
            "series" -> runCatching {
                val raw = XtreamApi.getSeriesInfo(
                    p.host, p.username, p.password, item.seriesId.toString(),
                ).info.orEmpty()
                fun s(k: String): String? = when (val v = raw[k]) {
                    is String -> v.takeIf { it.isNotBlank() }
                    is Number -> v.toString()
                    else -> null
                }
                XtreamVodInfo(
                    info = XtreamVodInfoInner(
                        plot = s("plot") ?: s("description"),
                        description = s("description"),
                        genre = s("genre"),
                        rating = s("rating"),
                        releasedate = s("releaseDate") ?: s("releasedate") ?: s("release_date"),
                        release_date = s("release_date") ?: s("releaseDate"),
                        cast = s("cast"),
                        director = s("director"),
                        cover_big = s("cover") ?: s("cover_big"),
                        movie_image = s("cover") ?: s("cover_big"),
                        backdrop_path = (raw["backdrop_path"] as? List<*>)?.mapNotNull { it as? String },
                        youtube_trailer = s("youtube_trailer"),
                    ),
                    movie_data = null,
                )
            }.getOrNull()
            else -> null
        }
    }

    val firstGridFocus = remember { FocusRequester() }
    val dropdownFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // ── Current layout mode (Top-Bar vs Left Sidebar) ──
    // State-backed so picking a new mode via the chooser re-composes
    // this screen instantly without needing to leave + re-enter.
    var currentLayoutMode by remember {
        mutableStateOf(com.hushtv.tv.data.LayoutPrefsStore.mode(ctx))
    }
    val useSidebar = currentLayoutMode == com.hushtv.tv.data.LayoutPrefsStore.MODE_SIDEBAR
    val sidebarFirstItemFocus = remember { FocusRequester() }
    val browseSidebarItems = remember(allCategories, type) {
        buildList {
            add(com.hushtv.tv.ui.screens.home.SidebarItem(CAT_FAV, "Favorites"))
            add(com.hushtv.tv.ui.screens.home.SidebarItem(CAT_ALL, "All"))
            allCategories.forEach {
                add(com.hushtv.tv.ui.screens.home.SidebarItem(it.category_id, it.category_name))
            }
        }
    }
    // Layout chooser invoked via the TopNavBar hint chip.
    var showLayoutChooser by remember { mutableStateOf(false) }

    var pendingJumpToGrid by remember { mutableStateOf(false) }
    LaunchedEffect(displayedItems, pendingJumpToGrid) {
        if (pendingJumpToGrid && displayedItems.isNotEmpty()) {
            delay(90)
            runCatching { firstGridFocus.requestFocus() }
            pendingJumpToGrid = false
        }
    }

    // Initial focus: put cursor on the dropdown so user can immediately
    // open the category picker or arrow down into the grid. In sidebar
    // mode, focus the first sidebar item instead.
    LaunchedEffect(Unit) {
        delay(260)
        runCatching {
            if (useSidebar) sidebarFirstItemFocus.requestFocus()
            else dropdownFocus.requestFocus()
        }
    }

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
            posterUrl = displayedItems.getOrNull(focusedIdx)?.poster,
            backdropUrl = vodInfo?.info?.backdrop_path?.firstOrNull(),
            visible = gridHasFocus,
        )

        // ── Full-width content: toolbar + detail + grid ────────────
        // In TOP-BAR mode: Column(CategoryToolbar + grid).
        // In SIDEBAR mode: Row(CategorySidebar + grid).
        // Grid body is factored into a lambda to avoid duplication.
        val gridBody: @Composable () -> Unit = {
            // ── Poster grid (claims all remaining space) ──
            val gridState = rememberLazyGridState()
            Box(Modifier.fillMaxSize()) {
                when {
                    loadingItems -> CenterLoader()
                    displayedItems.isEmpty() && searchQuery.trim().isNotEmpty() ->
                        InfoBox(
                            "No results for \"${searchQuery.trim()}\"",
                            Icons.Default.Search,
                        )
                    displayedItems.isEmpty() ->
                        InfoBox(
                            msg = when (selectedCatId) {
                                CAT_FAV -> "No favorites yet. Press and hold OK on any poster to add it here."
                                else -> "Nothing here yet."
                            },
                            icon = when (selectedCatId) {
                                CAT_FAV -> Icons.Default.Star
                                else -> Icons.Default.Movie
                            },
                        )
                    else -> {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val cols = ((maxWidth + 12.dp) / (124.dp + 12.dp)).toInt().coerceAtLeast(1)
                            LaunchedEffect(cols) { gridColumnCount = cols }

                            PositionFocusedItemInLazyLayout(parentFraction = 0.3f) {
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(cols),
                                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    itemsIndexed(
                                        displayedItems,
                                        key = { i, it -> "${it.kind}-${it.id}-$i" },
                                    ) { idx, item ->
                                        val progress = remember(watchVersion, item.id) {
                                            WatchProgressStore.getRatio(ctx, item.streamId, item.kind)
                                        }
                                        val isInList = remember(myListVersion, item.id) {
                                            val id = if (item.kind == "series") item.seriesId else item.streamId
                                            MyListStore.isInList(ctx, playlistId, item.kind, id)
                                        }
                                        // Only the TOP ROW lifts focus up to
                                        // the toolbar (top-bar mode) — rows
                                        // below navigate within the grid
                                        // normally. In sidebar mode there's
                                        // no toolbar so top row Up is a no-op.
                                        val isTopRow = idx < cols
                                        val upTarget = if (useSidebar) null else dropdownFocus
                                        CompactPoster(
                                            item = item,
                                            progress = progress,
                                            isInList = isInList,
                                            focusMod = (if (idx == 0)
                                                Modifier.focusRequester(firstGridFocus)
                                                else Modifier)
                                                .then(
                                                    if (isTopRow && upTarget != null)
                                                        Modifier.focusProperties { up = upTarget }
                                                    else Modifier
                                                ),
                                            onFocus = { focusedIdx = idx },
                                            onLeftEdge = {
                                                if (useSidebar) runCatching {
                                                    sidebarFirstItemFocus.requestFocus()
                                                }
                                            },
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

        if (useSidebar) {
            // ── SIDEBAR MODE ──
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp)
                    .onFocusChanged { gridHasFocus = it.hasFocus && focusedIdx >= 0 },
            ) {
                com.hushtv.tv.ui.screens.home.CategorySidebar(
                    items = browseSidebarItems,
                    selectedId = selectedCatId,
                    title = title,
                    firstItemFocus = sidebarFirstItemFocus,
                    onFocus = { /* no preview-on-focus; ENTER commits */ },
                    onEnter = { item ->
                        selectedCatId = item.id
                        pendingJumpToGrid = true
                    },
                    rightTarget = firstGridFocus,
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x1FFFFFFF)))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    gridBody()
                }
            }
        } else {
            // ── TOP-BAR MODE ── (default)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp) // room for the global top nav
                    .onFocusChanged { gridHasFocus = it.hasFocus && focusedIdx >= 0 },
            ) {
                // ── TOOLBAR — BROWSE dropdown on the LEFT, category
                //     title cluster on the RIGHT. Matches Live TV.
                CategoryToolbar(
                    title = title,
                    totalCategoryCount = allCategories.size,
                    selectedLabel = sidebarEntries.firstOrNull { it.id == selectedCatId }?.label
                        ?: "All",
                    itemCount = displayedItems.size,
                    dropdownExpanded = dropdownExpanded,
                    onDropdownToggle = { dropdownExpanded = !dropdownExpanded },
                    dropdownFocus = dropdownFocus,
                    downTarget = firstGridFocus,
                )

                // Divider line under toolbar.
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    gridBody()
                }
            }
        }

        // ── TOP NAV overlay ──────────────────────────────────────────
        // Same nav as Home so Movies/Series/Live feel like siblings of
        // Home instead of drill-down sub-screens. Active tab highlights
        // based on the current screen kind.
        val navTabs = remember {
            listOf(
                com.hushtv.tv.ui.screens.home.TopNavTab(
                    "home", "Home",
                    Icons.Default.Home,
                    "menu/$playlistId",
                ),
                com.hushtv.tv.ui.screens.home.TopNavTab(
                    "live", "Live TV",
                    Icons.Default.Tv,
                    "browse/$playlistId/live",
                ),
                com.hushtv.tv.ui.screens.home.TopNavTab(
                    "movies", "Movies",
                    Icons.Default.Movie,
                    "browse/$playlistId/movie",
                ),
                com.hushtv.tv.ui.screens.home.TopNavTab(
                    "series", "Series",
                    Icons.Outlined.Slideshow,
                    "browse/$playlistId/series",
                ),
                com.hushtv.tv.ui.screens.home.TopNavTab(
                    "search", "Search",
                    Icons.Default.Search,
                    "search/$playlistId",
                ),
            )
        }
        val activeTabKey = when (type) {
            "live" -> "live"
            "series" -> "series"
            "search" -> "search"
            else -> "movies"
        }
        val homeTabFocus = remember { FocusRequester() }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
        ) {
            com.hushtv.tv.ui.screens.home.TopNavBar(
                tabs = navTabs,
                activeKey = activeTabKey,
                homeFocus = homeTabFocus,
                onTab = { t ->
                    // Don't re-navigate to our current screen.
                    if (t.key == activeTabKey) return@TopNavBar
                    t.route?.let { route ->
                        nav.navigate(route) {
                            // Replace so back still works sensibly.
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

        // ── Category-picker overlay. Rendered at the ROOT Box so it
        // sits above EVERY other pane (grid, toolbar). Previously the
        // panel was nested inside the toolbar Column which let the
        // poster grid bleed through. Top nav stays on top because it's
        // rendered AFTER this overlay.
        if (dropdownExpanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp),
            ) {
                CategoryDropdownPanel(
                    entries = sidebarEntries.filter { !it.isDivider && it.id != CAT_SEARCH },
                    selectedId = selectedCatId,
                    onPick = { id ->
                        selectedCatId = id
                        dropdownExpanded = false
                        pendingJumpToGrid = true
                    },
                    onDismiss = { dropdownExpanded = false },
                )
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
/*  CATEGORY TOOLBAR + DROPDOWN                                     */
/* ──────────────────────────────────────────────────────────────── */

/**
 * Pinned toolbar below the top nav. Matches the Live TV toolbar
 * layout exactly: BROWSE pill on the LEFT, category title cluster
 * right-aligned on the RIGHT. No inline search (the unified Search
 * tab in the top nav covers that).
 *
 * Focus order: dropdown ⇄ grid (first row). Pressing DOWN from the
 * dropdown jumps straight to the grid; pressing UP from the top row
 * of the grid returns to the dropdown.
 */
@Composable
private fun CategoryToolbar(
    title: String,
    totalCategoryCount: Int,
    selectedLabel: String,
    itemCount: Int,
    dropdownExpanded: Boolean,
    onDropdownToggle: () -> Unit,
    dropdownFocus: FocusRequester,
    downTarget: FocusRequester,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── LEFT: Accent bar + Browse dropdown ──
        Box(
            Modifier
                .size(width = 3.dp, height = 22.dp)
                .background(Cyan, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))

        CategoryDropdownButton(
            label = selectedLabel,
            totalCount = totalCategoryCount,
            expanded = dropdownExpanded,
            onToggle = onDropdownToggle,
            focusRequester = dropdownFocus,
            downTarget = downTarget,
        )

        // Flex spacer pushes the title cluster to the right.
        Spacer(Modifier.weight(1f))

        // ── RIGHT: Title + selected category name ──
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                if (itemCount > 0) "${title.uppercase()}  ·  $itemCount"
                else title.uppercase(),
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

    // NOTE: the dropdown panel is rendered at the ROOT Box level (see
    // callsite) so it can overlay the whole screen. Was inside a Column
    // previously which let the grid below bleed through the panel.
}

/** Pill-style dropdown trigger — current category + chevron. */
@Composable
private fun CategoryDropdownButton(
    label: String,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester,
    downTarget: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .background(SurfaceNavy, RoundedCornerShape(10.dp))
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
            if (totalCount > 0) "$label" else "Loading…",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.width(8.dp))
        // Chevron rotates when expanded — gives a clear open/closed cue.
        val rot by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(160),
            label = "chevron-rot",
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

/**
 * Full-screen overlay that slides in when the dropdown is expanded.
 * Single-column scrollable list so every category is walked top-to-
 * bottom in the provider's native order (matches the Live TV picker).
 */
@Composable
private fun CategoryDropdownPanel(
    entries: List<SidebarEntry>,
    selectedId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstItemFocus.requestFocus() }
    }

    // Full-height, fully opaque overlay so nothing bleeds through from
    // the grid below. Gives users a proper room-filling picker with up
    // to 30+ visible categories instead of a cramped drop-down.
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
                    "PICK A CATEGORY",
                    color = Cyan,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${entries.size} categories · press BACK to close",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontFamily = Inter,
                )
            }
            Spacer(Modifier.height(18.dp))

            // Single-column list — matches the Live TV picker so users
            // can scan EVERY category in the provider's native order
            // without skipping across columns. LazyColumn for smooth
            // scrolling on long lists.
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(
                    count = entries.size,
                    key = { idx -> entries[idx].id },
                ) { idx ->
                    val entry = entries[idx]
                    CategoryPill(
                        entry = entry,
                        selected = entry.id == selectedId,
                        focusMod = if (idx == 0)
                            Modifier.focusRequester(firstItemFocus) else Modifier,
                        onClick = { onPick(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    entry: SidebarEntry,
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
            imageVector = entry.icon,
            contentDescription = null,
            tint = if (selected || focused) Cyan else Color(0xFFCBD5E1),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            entry.label,
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

/** Slim inline search pill — same pattern as the franchises browser. */
@Composable
private fun InlineSearchBar(
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
            .background(SurfaceNavy, RoundedCornerShape(10.dp))
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
            tint = if (focused) Cyan else TextMuted,
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
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionDown -> {
                                runCatching { downTarget.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    },
            )
            if (value.isEmpty()) {
                Text(
                    "Search in this section…",
                    color = TextMuted,
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

            // IMDb rating badge (top-right). Same component as Mobile
            // for visual consistency. Internal parser handles rescaling
            // 0..5 ratings to 0..10 and rejects junk like "N/A" or
            // MPAA codes.
            com.hushtv.tv.ui.components.ImdbBadge(
                rating = item.rating,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
                fontSize = 9.sp,
            )

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

/**
 * Normalise a raw Xtream category / card-keyword label so they can
 * be compared reliably:
 *   - lowercases
 *   - strips possessive apostrophes ("Hallmark Movie's" → "hallmark movies")
 *   - strips any non-alphanumeric char (emoji, punctuation, hyphens)
 *   - collapses whitespace runs into single spaces
 *   - trims
 *
 * Used by the deep-link matcher in TVBrowseScreen — NOT the franchise
 * TitleMatcher (which has different, title-specific rules).
 */
private fun normaliseCatName(raw: String): String {
    var s = raw.lowercase()
    // Drop possessive apostrophes BEFORE stripping non-alnum so the
    // "s" stays attached ("movie's" → "movies" rather than "movie s").
    s = s.replace(Regex("""[\u2019']s\b"""), "s")
    // Strip every char that isn't a-z, 0-9 or space.
    s = s.replace(Regex("""[^a-z0-9 ]"""), " ")
    // Collapse runs of whitespace.
    s = s.replace(Regex("""\s+"""), " ").trim()
    return s
}

