@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.lite

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.AppMode
import com.hushtv.tv.data.AppModeStore
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v1.44.19 — Lite shell.
 *
 * Top-of-screen tab strip + a content pane that swaps based on
 * the selected tab. Zero animations on the swap (no AnimatedContent,
 * no fade) — content just replaces.
 *
 * Tabs:
 *   Home · Live TV · Movies · Series · Sports · Search · Settings
 *
 * The corner "LITE" badge advertises which mode is active so a
 * returning user always knows.
 */
private enum class LiteTab(val label: String) {
    HOME("Home"),
    LIVE("Live TV"),
    MOVIES("Movies"),
    SERIES("Series"),
    SPORTS("Sports"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

@Composable
fun LiteShellScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(LiteTab.HOME) }
    val tabFocusers = remember { LiteTab.values().associateWith { FocusRequester() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F))
    ) {
        Column(Modifier.fillMaxSize()) {
            LiteTopBar(
                selected = tab,
                onSelect = { tab = it },
                tabFocusers = tabFocusers,
            )
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    LiteTab.HOME ->
                        LiteHomeContent(nav, playlistId)
                    LiteTab.LIVE ->
                        LiteLiveContent(nav, playlistId)
                    LiteTab.MOVIES ->
                        LiteCatalogContent(nav, playlistId, kind = "movie")
                    LiteTab.SERIES ->
                        LiteCatalogContent(nav, playlistId, kind = "series")
                    LiteTab.SPORTS ->
                        LiteSportsContent(nav, playlistId)
                    LiteTab.SEARCH ->
                        LiteSearchContent(nav, playlistId)
                    LiteTab.SETTINGS ->
                        LiteSettingsContent(nav, playlistId)
                }
            }
        }
        // Persistent corner "LITE" badge so users always know which
        // mode they're in.
        LiteBadge(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 28.dp)
        )
    }
}

@Composable
private fun LiteTopBar(
    selected: LiteTab,
    onSelect: (LiteTab) -> Unit,
    tabFocusers: Map<LiteTab, FocusRequester>,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1220))
            .padding(horizontal = 36.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "HUSHTV",
            color = Cyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            fontFamily = Inter,
        )
        Spacer(Modifier.width(28.dp))
        LiteTab.values().forEach { t ->
            LiteTabButton(
                label = t.label,
                selected = t == selected,
                onClick = { onSelect(t) },
                focusRequester = tabFocusers[t],
            )
        }
    }
}

@Composable
private fun LiteTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        selected -> Cyan
        focused -> Color(0x33FFFFFF)
        else -> Color.Transparent
    }
    val fg = if (selected) Color(0xFF050810) else Color.White
    Box(
        Modifier
            .height(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1f, shape = shape, focusRequester = focusRequester)
            .clickableWithEnter(onClick)
            .clip(shape)
            .background(bg)
            .border(
                width = if (focused && !selected) 1.dp else 0.dp,
                color = Cyan.copy(alpha = 0.6f),
                shape = shape,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun LiteBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF14B8A6))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "LITE",
            color = Color(0xFF052E2A),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = Inter,
        )
    }
}

// ────────────────────────────────────────────────────────────
// HOME — three lazy rows: continue-watching, recent movies,
//        favorite live channels. No cinematic hero, no Ken Burns.
// ────────────────────────────────────────────────────────────
@Composable
private fun LiteHomeContent(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    var movies by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var series by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var live by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    LaunchedEffect(playlistId) {
        val p = PlaylistStore.find(ctx, playlistId) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                movies = XtreamApi.getAllStreams(p.host, p.username, p.password, "movie")
                    .sortedByDescending { it.addedTs }
                    .take(40)
                series = XtreamApi.getAllStreams(p.host, p.username, p.password, "series")
                    .sortedByDescending { it.addedTs }
                    .take(40)
                live = XtreamApi.getAllStreams(p.host, p.username, p.password, "live").take(40)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                "Welcome back",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
                modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        item {
            LiteRow(
                title = "Recently Added Movies",
                items = movies,
                onClick = { card ->
                    nav.navigate(
                        "moviedetail/$playlistId/${card.streamId}/${Uri.encode(card.title)}"
                    )
                },
            )
        }
        item {
            LiteRow(
                title = "Recently Added Series",
                items = series,
                onClick = { card ->
                    nav.navigate(
                        "series/$playlistId/${card.seriesId}/${Uri.encode(card.title)}"
                    )
                },
            )
        }
        item {
            LiteRow(
                title = "Live TV",
                items = live,
                onClick = { card ->
                    val p = PlaylistStore.find(ctx, playlistId) ?: return@LiteRow
                    val url = XtreamApi.liveUrl(p.host, p.username, p.password, card.streamId)
                    nav.navigate(
                        "player/$playlistId/${Uri.encode(url)}/${Uri.encode(card.title)}/true"
                    )
                },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
// LIVE TV — flat alphabetical channel list, no backdrop.
// ────────────────────────────────────────────────────────────
@Composable
private fun LiteLiveContent(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    var channels by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    LaunchedEffect(playlistId) {
        val p = PlaylistStore.find(ctx, playlistId) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                channels = XtreamApi
                    .getAllStreams(p.host, p.username, p.password, "live")
                    .sortedBy { it.title }
            }
        }
    }
    if (channels.isEmpty()) {
        LiteEmpty("No live channels found.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(channels, key = { it.id }) { ch ->
            LiteChannelTile(
                card = ch,
                onClick = {
                    val p = PlaylistStore.find(ctx, playlistId) ?: return@LiteChannelTile
                    val url = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
                    nav.navigate(
                        "player/$playlistId/${Uri.encode(url)}/${Uri.encode(ch.title)}/true"
                    )
                },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
// MOVIES / SERIES catalog — flat alpha grid via LazyColumn of
// poster rows (5 per row). No category nav (keeps it simple).
// ────────────────────────────────────────────────────────────
@Composable
private fun LiteCatalogContent(nav: NavController, playlistId: String, kind: String) {
    val ctx = LocalContext.current
    var items by remember(kind) { mutableStateOf<List<MediaCard>>(emptyList()) }
    LaunchedEffect(playlistId, kind) {
        val p = PlaylistStore.find(ctx, playlistId) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                items = XtreamApi
                    .getAllStreams(p.host, p.username, p.password, kind)
                    .sortedBy { it.title }
            }
        }
    }
    if (items.isEmpty()) {
        LiteEmpty(if (kind == "movie") "No movies found." else "No series found.")
        return
    }
    val perRow = 6
    val rows = items.chunked(perRow)
    LazyColumn(
        Modifier.fillMaxSize().padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(rows.size) { rowIdx ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows[rowIdx].forEach { card ->
                    LitePosterCard(
                        card = card,
                        onClick = {
                            if (kind == "movie") {
                                nav.navigate(
                                    "moviedetail/$playlistId/${card.streamId}/${Uri.encode(card.title)}"
                                )
                            } else {
                                nav.navigate(
                                    "series/$playlistId/${card.seriesId}/${Uri.encode(card.title)}"
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// SPORTS — static title hero (no Ken Burns, no auto-cycle).
// Reuses the existing /api/sports/home backend so games & PPV
// data is identical to Pro.
// ────────────────────────────────────────────────────────────
@Composable
private fun LiteSportsContent(nav: NavController, playlistId: String) {
    val home = com.hushtv.tv.ui.screens.sports.rememberSportsHome()
    val live =
        com.hushtv.tv.ui.screens.sports.rememberLiveChannels(playlistId)
    val channelIndex =
        com.hushtv.tv.ui.screens.sports.rememberChannelIndex(live)
    val flat = remember(home) {
        (home?.leagues ?: emptyList()).flatMap { it.games }
    }
    val playable =
        com.hushtv.tv.ui.screens.sports.rememberPlayableGames(flat, channelIndex)

    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Sports",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
                modifier = Modifier.padding(start = 48.dp, top = 6.dp, bottom = 8.dp),
            )
        }
        if (playable.isEmpty()) {
            item { LiteEmpty("No live or upcoming games right now.") }
        } else {
            items(playable.size) { idx ->
                val (game, ch) = playable[idx]
                LiteSportsRow(game = game, channelTitle = ch.title) {
                    val p = PlaylistStore.find(ctx, playlistId) ?: return@LiteSportsRow
                    val url = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
                    nav.navigate(
                        "player/$playlistId/${Uri.encode(url)}/${Uri.encode(ch.title)}/true"
                    )
                }
            }
        }
    }
}

@Composable
private fun LiteSportsRow(
    game: com.hushtv.tv.data.sports.SportsGame,
    channelTitle: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val isLive = game.status.equals("live", ignoreCase = true)
    val isFinal = game.status.equals("final", ignoreCase = true)
    val showScores = (isLive || isFinal) &&
        !game.score_home.isNullOrBlank() && !game.score_away.isNullOrBlank()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .height(60.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1f, shape = shape)
            .clickableWithEnter(onClick)
            .clip(shape)
            .background(if (focused) Color(0xFF1E293B) else Color(0xFF111A2C))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x14FFFFFF),
                shape = shape,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // League tag
        Text(
            (game.league?.name ?: "GAME").uppercase(),
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = Inter,
            modifier = Modifier.width(60.dp),
        )
        // Matchup label
        val away = game.away?.short_name ?: game.away?.name ?: "TBA"
        val homeName = game.home?.short_name ?: game.home?.name ?: "TBA"
        Text(
            "$away  @  $homeName",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        // Score / LIVE / countdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showScores) {
                Text(
                    "${game.score_away}–${game.score_home}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
                Spacer(Modifier.width(10.dp))
            }
            if (isLive) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "LIVE",
                    color = Color(0xFFEF4444),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = Inter,
                )
            } else if (isFinal) {
                Text(
                    "FINAL",
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = Inter,
                )
            } else {
                Text(
                    com.hushtv.tv.ui.screens.sports.friendlyCountdown(game.start_utc),
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    fontFamily = Inter,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            channelTitle.uppercase(),
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp),
        )
    }
}

// ────────────────────────────────────────────────────────────
// SEARCH — opens the existing Pro search screen via nav. The
// Pro search is already lazy-rendering by design and isn't a
// performance bottleneck.
// ────────────────────────────────────────────────────────────
@Composable
private fun LiteSearchContent(nav: NavController, playlistId: String) {
    LaunchedEffect(Unit) {
        nav.navigate("search/$playlistId")
    }
    LiteEmpty("Opening search…")
}

// ────────────────────────────────────────────────────────────
// SETTINGS — minimal: switch back to Pro, log out (via Pro
// settings), version info.
// ────────────────────────────────────────────────────────────
@Composable
private fun LiteSettingsContent(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Settings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            fontFamily = Inter,
        )
        LiteSettingsRow(
            label = "Switch to Pro Mode",
            sub = "Cinematic UI · animations · richer detail screens.",
            onClick = {
                AppModeStore.save(ctx, AppMode.PRO)
                // Restart into Pro by going to the menu route.
                nav.navigate("menu/$playlistId") {
                    popUpTo(nav.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
        LiteSettingsRow(
            label = "Account, devices & advanced settings",
            sub = "Opens the full settings screen.",
            onClick = { nav.navigate("settings/$playlistId") }
        )
        LiteSettingsRow(
            label = "Diagnostics",
            sub = "Network speed, stream test, crash report.",
            onClick = { nav.navigate("diag") }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "HushTV Lite · v${com.hushtv.tv.BuildConfig.VERSION_NAME}",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun LiteSettingsRow(label: String, sub: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1f, shape = shape)
            .clickableWithEnter(onClick)
            .clip(shape)
            .background(if (focused) Color(0xFF1E293B) else Color(0xFF111A2C))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x14FFFFFF),
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Inter,
        )
        Text(
            sub,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun LiteEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = Color(0xFF94A3B8),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
        )
    }
}
