package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamCategory
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mobile browse — category bar on top (bottom-sheet-like picker), 3-col
 * poster grid below. Works for movie / series / live (live shows a
 * taller landscape-ratio card so channel names fit). When [inline] is
 * true this screen is embedded inside MobileShell's tab content (no
 * back arrow); when false it has its own top bar for deep-linked entry.
 */
@Composable
fun MobileBrowseScreen(
    nav: NavController,
    playlistId: String,
    type: String,
    initialCategoryId: String? = null,
    inline: Boolean = false,
) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }
    val title = when (type) { "movie" -> "Movies"; "series" -> "Series"; else -> "Live TV" }
    val isLive = type == "live"

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCatId by remember { mutableStateOf(initialCategoryId ?: "") }
    var cardList by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCatPicker by remember { mutableStateOf(false) }

    // Load categories once.
    LaunchedEffect(playlistId, type) {
        if (playlist == null) { loading = false; return@LaunchedEffect }
        val cats = runCatching {
            withContext(Dispatchers.IO) {
                XtreamApi.getCategories(playlist.host, playlist.username, playlist.password, type)
            }
        }.getOrDefault(emptyList())
        categories = cats
    }

    // Reload items whenever the category changes.
    LaunchedEffect(selectedCatId, categories, playlistId, type) {
        if (playlist == null) { loading = false; return@LaunchedEffect }
        loading = true
        val data = runCatching {
            withContext(Dispatchers.IO) {
                if (selectedCatId.isBlank()) {
                    XtreamApi.getAllStreams(playlist.host, playlist.username, playlist.password, type)
                } else {
                    XtreamApi.getStreamsForCategory(playlist.host, playlist.username, playlist.password, type, selectedCatId)
                }
            }
        }.getOrDefault(emptyList())
        cardList = data
        loading = false
    }

    val selectedCatName = remember(selectedCatId, categories) {
        if (selectedCatId.isBlank()) "All categories"
        else categories.firstOrNull { it.category_id == selectedCatId }?.category_name ?: "All"
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF05080F))) {
        // Header row. Inline variant hides the back arrow (BottomNav handles return).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!inline) {
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
                Spacer(Modifier.width(10.dp))
            }
            Text(
                title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
        }

        // Category pill.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Cyan.copy(alpha = 0.15f))
                    .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable { showCatPicker = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedCatName.uppercase(),
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ExpandMore, null, tint = Cyan, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${cardList.size}",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Content area.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cyan)
                }
                cardList.isEmpty() -> Text(
                    "Nothing here yet.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                isLive -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    listItems(cardList, key = { "live-${it.id}" }) { card ->
                        MobileLiveRow(card) {
                            val p = playlist ?: return@MobileLiveRow
                            val url = XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
                            nav.navigate(mobilePlayerRoute(playlistId, url, card.title, isLive = true))
                        }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(cardList, key = { "${it.kind}-${it.id}" }) { card ->
                        MobileVodCard(card) {
                            val p = playlist ?: return@MobileVodCard
                            if (type == "movie") {
                                val url = XtreamApi.movieUrl(
                                    p.host, p.username, p.password,
                                    card.streamId, card.containerExtension,
                                )
                                nav.navigate(mobilePlayerRoute(playlistId, url, card.title, isLive = false))
                            } else {
                                // Series — for MVP jump into the first episode
                                // isn't feasible without series detail; keep
                                // a placeholder that opens nothing for now.
                                // A future iteration would pop a bottom sheet
                                // of seasons / episodes.
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCatPicker) {
        MobileCategoryPicker(
            categories = categories,
            selectedId = selectedCatId,
            onPick = { id ->
                selectedCatId = id
                showCatPicker = false
            },
            onDismiss = { showCatPicker = false },
        )
    }
}

@androidx.compose.runtime.Composable
private fun MobileVodCard(card: MediaCard, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1F2937)),
        ) {
            if (!card.poster.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = card.poster,
                    contentDescription = card.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        card.title.take(2).uppercase(),
                        color = Color(0xFF64748B),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )
    }
}

@androidx.compose.runtime.Composable
private fun MobileLiveRow(card: MediaCard, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1220))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            if (!card.poster.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = card.poster, contentDescription = card.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Text(
                    card.title.take(2).uppercase(),
                    color = Color(0xFF64748B), fontSize = 18.sp, fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Live channel",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun MobileCategoryPicker(
    categories: List<XtreamCategory>,
    selectedId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B1220))
                .padding(vertical = 12.dp),
        ) {
            Text(
                "Pick a category",
                color = Cyan,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                // "All" option first, then every real category. Using item{}
                // directly to sidestep the items-overload ambiguity caused
                // by importing both list and grid item DSLs in this file.
                item {
                    CategoryPickerRow("", "All categories", selectedId == "", onPick)
                }
                categories.forEach { cat ->
                    item(key = cat.category_id) {
                        CategoryPickerRow(
                            cat.category_id, cat.category_name,
                            selectedId == cat.category_id, onPick,
                        )
                    }
                }
            }
        }
    }
}


@androidx.compose.runtime.Composable
private fun CategoryPickerRow(
    id: String,
    name: String,
    selected: Boolean,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Cyan.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onPick(id) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            color = if (selected) Cyan else Color(0xFFE5E7EB),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
