package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.hushtv.tv.data.LastChannelStore
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.theme.Amber
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.BorderSlate
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextDim
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.theme.UnfocusedBorder
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ──────────────────────────────────────────────────────────────── */
/*  MODELS                                                          */
/* ──────────────────────────────────────────────────────────────── */

private data class HeroSlide(
    val title: String,
    val badge: String,
    val genres: List<String>,
    val synopsis: String,
    val accent: Color,
    val gradient: List<Color>,
)

/** Curated static hero slides — pure-black friendly gradient backdrops. */
private val HERO_SLIDES = listOf(
    HeroSlide(
        title = "Live Sports & Events",
        badge = "LIVE",
        genres = listOf("Sports", "Premium", "HD"),
        synopsis = "Catch every match, every moment — Premier League, NBA, UFC and more, streaming right now in premium quality.",
        accent = Red,
        gradient = listOf(Color(0xFF7F1D1D), Color(0xFF450A0A), Color(0xFF000000)),
    ),
    HeroSlide(
        title = "Thousands of Movies",
        badge = "MOVIES",
        genres = listOf("Blockbusters", "New Releases", "4K"),
        synopsis = "From Oscar winners to summer blockbusters — dive into a library that updates every week.",
        accent = Cyan,
        gradient = listOf(Color(0xFF164E63), Color(0xFF083344), Color(0xFF000000)),
    ),
    HeroSlide(
        title = "Binge-Worthy Series",
        badge = "SERIES",
        genres = listOf("Drama", "Comedy", "Thriller"),
        synopsis = "Follow your favorite shows season by season — full catalogs with the latest episodes ready to stream.",
        accent = Amber,
        gradient = listOf(Color(0xFF713F12), Color(0xFF422006), Color(0xFF000000)),
    ),
)

private data class NavTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val route: String?, // null for "Home" (we're already here)
)

/* ──────────────────────────────────────────────────────────────── */
/*  SCREEN                                                          */
/* ──────────────────────────────────────────────────────────────── */

@Composable
fun TVMainMenuScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    // Account info — for expiry pill in top nav
    var expiryStr by remember { mutableStateOf<String?>(null) }
    var daysLeft by remember { mutableStateOf<Long?>(null) }

    // Row data
    var liveNow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Pair<String, List<MediaCard>>>>(emptyList()) }
    var seriesRow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var trendingRow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    // Continue watching — from last channel store
    val lastChannel = remember { LastChannelStore.load(ctx) }

    // Data fetch
    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        // Expiry
        runCatching { XtreamApi.authenticate(p.host, p.username, p.password) }
            .onSuccess { resp ->
                val expTs = resp.user_info?.exp_date?.toLongOrNull()
                if (expTs != null && expTs > 0) {
                    expiryStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(expTs * 1000))
                    daysLeft = ((expTs * 1000 - System.currentTimeMillis()) / (1000L * 60 * 60 * 24))
                }
            }
        // Live — first category, limited
        coroutineScope {
            launch {
                runCatching {
                    val cats = XtreamApi.getCategories(p.host, p.username, p.password, "live")
                    val cat = cats.firstOrNull() ?: return@runCatching
                    liveNow = XtreamApi.getStreamsForCategory(
                        p.host, p.username, p.password, "live", cat.category_id,
                    ).take(12)
                }
            }
            launch {
                runCatching {
                    val cats = XtreamApi.getCategories(p.host, p.username, p.password, "movie")
                    val built = mutableListOf<Pair<String, List<MediaCard>>>()
                    cats.take(3).forEach { c ->
                        val items = XtreamApi.getStreamsForCategory(
                            p.host, p.username, p.password, "movie", c.category_id,
                        ).take(16)
                        if (items.isNotEmpty()) built += c.category_name to items
                    }
                    movies = built
                    // Trending — composite of first two categories' top picks
                    trendingRow = built.flatMap { it.second }.take(10)
                }
            }
            launch {
                runCatching {
                    val cats = XtreamApi.getCategories(p.host, p.username, p.password, "series")
                    val cat = cats.firstOrNull() ?: return@runCatching
                    seriesRow = XtreamApi.getStreamsForCategory(
                        p.host, p.username, p.password, "series", cat.category_id,
                    ).take(14)
                }
            }
        }
    }

    val tabs = remember {
        listOf(
            NavTab("home",    "Home",     Icons.Default.Home,        null),
            NavTab("live",    "Live TV",  Icons.Default.Tv,          "browse/$playlistId/live"),
            NavTab("movies",  "Movies",   Icons.Default.Movie,       "browse/$playlistId/movie"),
            NavTab("series",  "Series",   Icons.Outlined.Slideshow,  "browse/$playlistId/series"),
            NavTab("search",  "Search",   Icons.Default.Search,      "browse/$playlistId/search"),
            NavTab("settings","Settings", Icons.Default.Settings,    "settings/$playlistId"),
        )
    }

    val heroPlayFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { heroPlayFocus.requestFocus() } }

    // Card click routing
    val onCardSelect: (MediaCard) -> Unit = sel@{ item ->
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

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 64.dp),
        ) {
            // ── ① Top Nav ──────────────────────────────────────────
            item {
                TopNavBar(
                    tabs = tabs,
                    activeKey = "home",
                    expiryStr = expiryStr,
                    daysLeft = daysLeft,
                    onTab = { t -> t.route?.let { nav.navigate(it) } },
                    onProfile = { nav.popBackStack() }, // exit to account picker
                )
            }

            // ── ② Hero Billboard ───────────────────────────────────
            item {
                HeroBillboard(
                    playFocus = heroPlayFocus,
                    onPlay = {
                        // "Play" → jump to Live TV browse (most common user intent)
                        nav.navigate("browse/$playlistId/live")
                    },
                    onMyList = {
                        nav.navigate("browse/$playlistId/search")
                    },
                )
            }

            // ── ③ Continue Watching ────────────────────────────────
            lastChannel?.let { lc ->
                item {
                    RowHeader(title = "Continue Watching", showSeeAll = false)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 12.dp),
                    ) {
                        item {
                            ContinueCard(
                                title = lc.channelName,
                                onClick = {
                                    nav.navigate(
                                        "player/${lc.playlistId}" +
                                            "/${Uri.encode(lc.streamUrl)}" +
                                            "/${Uri.encode(lc.channelName)}/true"
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── ④ Live Now ─────────────────────────────────────────
            if (liveNow.isNotEmpty()) {
                item {
                    RowHeader(title = "Live Now", badgeColor = Red, showSeeAll = true) {
                        nav.navigate("browse/$playlistId/live")
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 12.dp),
                    ) {
                        items(liveNow, key = { it.id }) { card ->
                            LiveCard(card, onSelect = onCardSelect)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── ⑤ Trending This Week ──────────────────────────────
            if (trendingRow.isNotEmpty()) {
                item {
                    RowHeader(title = "Trending This Week", showSeeAll = true) {
                        nav.navigate("browse/$playlistId/movie")
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 12.dp),
                    ) {
                        itemsIndexed(trendingRow.take(10)) { idx, card ->
                            TrendingCard(rank = idx + 1, card = card, onSelect = onCardSelect)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── ⑥ Movies (first category) ─────────────────────────
            movies.firstOrNull()?.let { (title, items) ->
                item {
                    RowHeader(title = "New Movies · $title", showSeeAll = true) {
                        nav.navigate("browse/$playlistId/movie")
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 12.dp),
                    ) {
                        items(items, key = { it.id }) { c -> PosterCardV2(c, onSelect = onCardSelect) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── ⑦ Series ──────────────────────────────────────────
            if (seriesRow.isNotEmpty()) {
                item {
                    RowHeader(title = "Featured Series", showSeeAll = true) {
                        nav.navigate("browse/$playlistId/series")
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 12.dp),
                    ) {
                        items(seriesRow, key = { it.id }) { c -> PosterCardV2(c, onSelect = onCardSelect) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── ⑧ Dynamic genre rows (2nd & 3rd movie categories) ─
            movies.drop(1).forEach { (title, items) ->
                item {
                    RowHeader(title = title, showSeeAll = true) {
                        nav.navigate("browse/$playlistId/movie")
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 12.dp),
                    ) {
                        items(items, key = { "${title}-${it.id}" }) { c -> PosterCardV2(c, onSelect = onCardSelect) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  TOP NAV BAR                                                     */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun TopNavBar(
    tabs: List<NavTab>,
    activeKey: String,
    expiryStr: String?,
    daysLeft: Long?,
    onTab: (NavTab) -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 96.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HushTVLogo(fontSize = 28.sp)
        Spacer(Modifier.width(48.dp))

        // Tab strip
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { tab ->
                NavTabItem(
                    label = tab.label,
                    active = tab.key == activeKey,
                    onClick = { onTab(tab) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Expiry pill
        expiryStr?.let { exp ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(SurfaceNavy, RoundedCornerShape(999.dp))
                    .border(1.dp, UnfocusedBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.CalendarMonth, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Expires $exp",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = Inter,
                )
                daysLeft?.let { d ->
                    when {
                        d in 0..7 -> {
                            Spacer(Modifier.width(8.dp))
                            Badge(text = "${d}d left", bg = Amber, fg = Color.Black)
                        }
                        d < 0 -> {
                            Spacer(Modifier.width(8.dp))
                            Badge(text = "Expired", bg = Red, fg = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        // Profile avatar
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceNavy)
                .tvFocusable(shape = CircleShape, fillOnFocus = false)
                .clickableWithEnter(onProfile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Cyan, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NavTabItem(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150),
        label = "nav-tab-scale",
    )
    val textColor = when {
        focused -> Cyan
        active -> TextPrimary
        else -> TextDim
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 120.dp)
            .scale(scale)
            .background(
                if (focused) Color(0x1F06B6D4) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 15.sp,
            fontFamily = Inter,
            fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        // Active underline
        Box(
            Modifier
                .height(3.dp)
                .width(if (active) 32.dp else 0.dp)
                .background(Cyan, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontFamily = Inter, fontWeight = FontWeight.Bold)
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  HERO BILLBOARD                                                  */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HeroBillboard(
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onMyList: () -> Unit,
) {
    var slideIdx by remember { mutableStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }

    // Auto-rotate every 8 s when not focused
    LaunchedEffect(heroFocused) {
        while (!heroFocused) {
            kotlinx.coroutines.delay(8000)
            slideIdx = (slideIdx + 1) % HERO_SLIDES.size
        }
    }

    val slide = HERO_SLIDES[slideIdx]

    Box(
        Modifier
            .fillMaxWidth()
            .height(500.dp)
            .background(
                Brush.verticalGradient(colors = slide.gradient),
            )
            .onFocusChanged { heroFocused = it.hasFocus },
    ) {
        // Left vignette
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xB3000000), Color.Transparent),
                        startX = 0f,
                        endX = 900f,
                    )
                )
        )
        // Bottom-to-transparent
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xE6000000)),
                        startY = 240f,
                    )
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 96.dp, end = 96.dp, bottom = 56.dp)
                .widthIn(max = 760.dp),
        ) {
            // Badge
            Box(
                Modifier
                    .background(slide.accent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    slide.badge,
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                slide.title,
                color = TextPrimary,
                fontSize = 56.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.6).sp,
                lineHeight = 58.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slide.genres.forEach { g ->
                    Box(
                        Modifier
                            .background(Color(0x26FFFFFF), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(g, color = TextPrimary, fontSize = 12.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!heroFocused) {
                Spacer(Modifier.height(14.dp))
                Text(
                    slide.synopsis,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontFamily = Inter,
                    lineHeight = 22.sp,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroPlayButton(focusRequester = playFocus, onClick = onPlay)
                HeroSecondaryButton(onClick = onMyList)
            }
        }

        // Progress dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 96.dp, bottom = 40.dp),
        ) {
            HERO_SLIDES.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .size(if (i == slideIdx) 24.dp else 8.dp, 8.dp)
                        .background(
                            if (i == slideIdx) Cyan else Color(0x55FFFFFF),
                            RoundedCornerShape(999.dp),
                        )
                )
            }
        }
    }
}

@Composable
private fun HeroPlayButton(focusRequester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(150),
        label = "hero-play",
    )
    Row(
        Modifier
            .height(56.dp)
            .widthIn(min = 180.dp)
            .scale(scale)
            .background(if (focused) Cyan else Color.White, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text("Play", color = Color.Black, fontSize = 18.sp, fontFamily = Inter, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroSecondaryButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(150),
        label = "hero-mylist",
    )
    Row(
        Modifier
            .height(56.dp)
            .widthIn(min = 180.dp)
            .scale(scale)
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x1FFFFFFF),
                RoundedCornerShape(8.dp),
            )
            .border(
                2.dp,
                if (focused) Cyan else Color(0x33FFFFFF),
                RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Search", color = TextPrimary, fontSize = 18.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  ROWS & CARDS                                                    */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun RowHeader(
    title: String,
    badgeColor: Color? = null,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 96.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badgeColor?.let {
            Box(
                Modifier
                    .size(10.dp)
                    .background(it, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
        )
        if (showSeeAll) {
            Spacer(Modifier.weight(1f))
            SeeAllLink(onClick = onSeeAll)
        }
    }
}

@Composable
private fun SeeAllLink(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        "See All →",
        color = if (focused) TextPrimary else Cyan,
        fontSize = 13.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun LiveCard(card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Box(
        Modifier
            .size(width = 300.dp, height = 168.dp)
            .tvFocusable(shape = RoundedCornerShape(12.dp), fillOnFocus = false)
            .clickableWithEnter { onSelect(card) },
    ) {
        // Thumbnail (channel logo if present, otherwise gradient)
        if (!card.poster.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceNavy),
                error = { LiveFallback() },
                loading = { LiveFallback() },
            )
        } else {
            LiveFallback()
        }
        // Bottom scrim + name
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                )
        )
        Text(
            card.title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 10.dp, end = 12.dp),
        )
        // LIVE badge
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Red, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                "LIVE",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.3.sp,
            )
        }
        // Cyan 3 dp progress bar at very bottom (static — represents "currently airing")
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.6f)
                .height(3.dp)
                .background(Cyan),
        )
    }
}

@Composable
private fun LiveFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(SurfaceNavy, BgBlack)),
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Tv, null, tint = BorderSlate, modifier = Modifier.size(42.dp))
    }
}

@Composable
private fun PosterCardV2(card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Box(
        Modifier
            .size(width = 160.dp, height = 240.dp)
            .tvFocusable(shape = RoundedCornerShape(12.dp), fillOnFocus = false)
            .clickableWithEnter { onSelect(card) },
    ) {
        if (!card.poster.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                error = { PosterFallback() },
                loading = { PosterFallback() },
            )
        } else {
            PosterFallback()
        }
        // Bottom gradient + title
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                )
        )
        Text(
            card.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PosterFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceNavy, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Movie, null, tint = BorderSlate, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun TrendingCard(rank: Int, card: MediaCard, onSelect: (MediaCard) -> Unit) {
    // Ghost rank number + poster side-by-side
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            rank.toString(),
            color = Color(0x1AFFFFFF),
            fontSize = 120.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
            letterSpacing = (-6).sp,
            modifier = Modifier.offset(x = 20.dp, y = 4.dp),
        )
        Box(
            Modifier
                .size(width = 150.dp, height = 225.dp)
                .offset(x = (-24).dp)
                .tvFocusable(shape = RoundedCornerShape(12.dp), fillOnFocus = false)
                .clickableWithEnter { onSelect(card) },
        ) {
            if (!card.poster.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = card.poster,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    error = { PosterFallback() },
                    loading = { PosterFallback() },
                )
            } else {
                PosterFallback()
            }
        }
    }
}

@Composable
private fun ContinueCard(title: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 240.dp, height = 135.dp)
            .tvFocusable(shape = RoundedCornerShape(12.dp), fillOnFocus = false)
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF164E63), Color(0xFF0F172A))),
                    RoundedCornerShape(12.dp),
                ),
        )
        // Play overlay
        Box(
            Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .background(Cyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(30.dp))
        }
        // Title
        Text(
            title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        // Cyan progress bar (represents "resume position")
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.35f)
                .height(3.dp)
                .background(Cyan),
        )
    }
}
