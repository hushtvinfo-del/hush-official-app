package com.hushtv.tv.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Tivimate-style Live TV browser.
 *
 *  ┌──────────────┬────────────────────────────────────────────┐
 *  │              │  <Category Name>                           │
 *  │  Categories  │  [logo] 101  CBS                           │
 *  │              │  [logo] 102  NBC                           │
 *  │  > US Ent    │  [logo] 103  ABC                           │
 *  │    UK        │  ...                                       │
 *  │    Sports    │                                            │
 *  └──────────────┴────────────────────────────────────────────┘
 */
@Composable
fun TVLiveBrowseScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCatIndex by remember { mutableStateOf(0) }
    var channels by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loadingCats by remember { mutableStateOf(true) }
    var loadingChans by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Channel cache so switching back to a previously-loaded category is instant.
    val channelCache = remember { mutableStateMapOf<String, List<MediaCard>>() }

    // Load categories once.
    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        scope.launch {
            runCatching {
                categories = XtreamApi.getCategories(p.host, p.username, p.password, "live")
            }
            loadingCats = false
        }
    }

    // Load channels when the selected category changes.
    LaunchedEffect(selectedCatIndex, categories) {
        val p = playlist ?: return@LaunchedEffect
        val cat = categories.getOrNull(selectedCatIndex) ?: return@LaunchedEffect
        val cached = channelCache[cat.category_id]
        if (cached != null) {
            channels = cached; return@LaunchedEffect
        }
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

    val currentCategory = categories.getOrNull(selectedCatIndex)
    val filteredChannels = remember(channels, searchQuery) {
        if (searchQuery.isBlank()) channels
        else channels.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val onPlay: (MediaCard) -> Unit = sel@{ c ->
        val p = playlist ?: return@sel
        val url = XtreamApi.liveUrl(p.host, p.username, p.password, c.streamId)
        nav.navigate("player/${Uri.encode(url)}/${Uri.encode(c.title)}/true")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF050B18), 1f to Color(0xFF000000)
                )
            )
    ) {
        // Top bar
        TopBar(
            title = currentCategory?.category_name ?: "Live TV",
            count = filteredChannels.size,
            searchOpen = searchOpen,
            searchQuery = searchQuery,
            onSearchToggle = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" },
            onSearchChange = { searchQuery = it },
            onBack = { nav.popBackStack() }
        )

        // Main pane: sidebar + channel list
        Row(Modifier.weight(1f).fillMaxWidth()) {
            CategorySidebar(
                categories = categories,
                selectedIndex = selectedCatIndex,
                loading = loadingCats,
                onSelect = { selectedCatIndex = it }
            )

            // Vertical divider
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0x1FFFFFFF))
            )

            ChannelsPane(
                channels = filteredChannels,
                loading = loadingChans,
                emptyReason = when {
                    searchQuery.isNotBlank() && filteredChannels.isEmpty() ->
                        "No channels matching \"$searchQuery\""
                    channels.isEmpty() && !loadingChans -> "No channels in this category"
                    else -> null
                },
                onPlay = onPlay
            )
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
    onBack: () -> Unit
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
            modifier = Modifier
                .size(44.dp)
                .focusTvCircle()
                .clickableWithEnter(onBack)
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
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        if (searchOpen) {
            SearchField(
                value = searchQuery,
                onChange = onSearchChange,
                onClose = onSearchToggle
            )
        } else {
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
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit
) {
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
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
            )
            if (value.isEmpty()) Text("Search channels…", color = TextSecondary, fontSize = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = Color(0x1AFFFFFF),
            shape = CircleShape,
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
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(categories.size) {
        if (categories.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && categories.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(selectedIndex) }
        }
    }

    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color(0x33000000))
    ) {
        Text(
            "CATEGORIES",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.5.sp,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp)
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(28.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    count = categories.size,
                    key = { idx -> categories[idx].category_id }
                ) { idx ->
                    val cat = categories[idx]
                    val mod = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                    CategoryRow(
                        name = cat.category_name,
                        selected = idx == selectedIndex,
                        modifier = mod,
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
    val leftBar = when {
        selected || focused -> Cyan
        else -> Color.Transparent
    }

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
            Modifier
                .width(4.dp)
                .height(28.dp)
                .background(leftBar, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = true)
        )
        Spacer(Modifier.width(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Channel list pane
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChannelsPane(
    channels: List<MediaCard>,
    loading: Boolean,
    emptyReason: String?,
    onPlay: (MediaCard) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E18))
    ) {
        when {
            loading -> CenterProgress()
            emptyReason != null -> EmptyMessage(emptyReason)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        count = channels.size,
                        key = { idx -> channels[idx].id + "-$idx" }
                    ) { idx ->
                        ChannelRow(
                            number = idx + 1,
                            channel = channels[idx],
                            onPlay = { onPlay(channels[idx]) }
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
    onPlay: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onPlay)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Channel number
        Box(
            Modifier
                .width(56.dp)
                .padding(end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString().padStart(3, '0'),
                color = if (focused) Cyan else TextSecondary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Logo
        ChannelLogo(channel.poster, channel.title)

        Spacer(Modifier.width(14.dp))

        // Name
        Column(Modifier.weight(1f)) {
            Text(
                channel.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        if (focused) {
            Surface(
                color = Cyan,
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
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
        Modifier
            .size(52.dp)
            .background(Color(0xFF1F2937), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                error = { LogoFallback(name) },
                loading = { LogoFallback(name) }
            )
        } else {
            LogoFallback(name)
        }
    }
}

@Composable
private fun LogoFallback(name: String) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(initial, color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

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

/** A small helper that makes circle-shaped interactive surfaces show the
 *  focus ring without the scale-bump that tvFocusable applies (used only
 *  for the back/search icons in the top bar). */
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
