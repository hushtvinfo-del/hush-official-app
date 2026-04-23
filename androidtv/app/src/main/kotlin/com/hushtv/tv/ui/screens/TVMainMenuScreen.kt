package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ──────────────────────────────────────────────────────────────── */
/*  HERO / TAB MODELS                                               */
/* ──────────────────────────────────────────────────────────────── */

private data class HeroSlide(
    val title: String,
    val badge: String,
    val genres: List<String>,
    val synopsis: String,
    val accent: Color,
    val gradient: List<Color>,
)

private val HERO_SLIDES = listOf(
    HeroSlide(
        title = "Live Sports & Events",
        badge = "LIVE",
        genres = listOf("Sports", "HD"),
        synopsis = "Every match, every moment — Premier League, NBA, UFC and more, streaming right now.",
        accent = Red,
        gradient = listOf(Color(0xFF7F1D1D), Color(0xFF3F0A0A), Color(0xFF000000)),
    ),
    HeroSlide(
        title = "Thousands of Movies",
        badge = "MOVIES",
        genres = listOf("Blockbusters", "4K"),
        synopsis = "From Oscar winners to summer blockbusters — a library that updates every week.",
        accent = Cyan,
        gradient = listOf(Color(0xFF164E63), Color(0xFF083344), Color(0xFF000000)),
    ),
    HeroSlide(
        title = "Binge-Worthy Series",
        badge = "SERIES",
        genres = listOf("Drama", "Comedy"),
        synopsis = "Follow your favorite shows season by season — full catalogs, latest episodes.",
        accent = Amber,
        gradient = listOf(Color(0xFF713F12), Color(0xFF422006), Color(0xFF000000)),
    ),
)

private data class NavTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val route: String?, // null for "Home" (already here)
)

/* ──────────────────────────────────────────────────────────────── */
/*  SCREEN                                                          */
/* ──────────────────────────────────────────────────────────────── */

@Composable
fun TVMainMenuScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    // Account info
    var expiryStr by remember { mutableStateOf<String?>(null) }
    var daysLeft by remember { mutableStateOf<Long?>(null) }

    // Row data
    var liveNow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Pair<String, List<MediaCard>>>>(emptyList()) }
    var seriesRow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var trendingRow by remember { mutableStateOf<List<MediaCard>>(emptyList()) }

    val lastChannel = remember { LastChannelStore.load(ctx) }

    // Fetch
    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        runCatching { XtreamApi.authenticate(p.host, p.username, p.password) }
            .onSuccess { resp ->
                val expTs = resp.user_info?.exp_date?.toLongOrNull()
                if (expTs != null && expTs > 0) {
                    expiryStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(expTs * 1000))
                    daysLeft = ((expTs * 1000 - System.currentTimeMillis()) / (1000L * 60 * 60 * 24))
                }
            }
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
            NavTab("home",     "Home",     Icons.Default.Home,       null),
            NavTab("live",     "Live TV",  Icons.Default.Tv,         "browse/$playlistId/live"),
            NavTab("movies",   "Movies",   Icons.Default.Movie,      "browse/$playlistId/movie"),
            NavTab("series",   "Series",   Icons.Outlined.Slideshow, "browse/$playlistId/series"),
            NavTab("search",   "Search",   Icons.Default.Search,     "browse/$playlistId/search"),
            NavTab("settings", "Settings", Icons.Default.Settings,   "settings/$playlistId"),
        )
    }

    // Focus handling — initial focus on Home tab (top of sidebar)
    val sidebarHomeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { sidebarHomeFocus.requestFocus() } }

    var sidebarFocused by remember { mutableStateOf(true) }

    val onCardSelect: (MediaCard) -> Unit = sel@{ item ->
        val p = playlist ?: return@sel
        when (item.kind) {
            "live" -> {
                val url = XtreamApi.liveUrl(p.host, p.username, p.password, item.streamId)
                nav.navigate("player/$playlistId/${Uri.encode(url)}/${Uri.encode(item.title)}/true")
            }
            "movie" -> {
                nav.navigate("moviedetail/$playlistId/${item.streamId}/${Uri.encode(item.title)}")
            }
            "series" -> {
                nav.navigate("series/$playlistId/${item.seriesId}/${Uri.encode(item.title)}")
            }
        }
    }

    // Netflix-style layered layout: content is a fixed-width canvas that
    // NEVER shifts when the sidebar expands/collapses. The sidebar overlays
    // the content's left edge — content always starts at the same screen-x
    // regardless of sidebar state. This is what fixes the "content jumps /
    // text overflows when I move into the content" bug.
    //
    // Geometry:
    //   • Root Box fills the screen with BgBlack, minus 32 dp right overscan
    //   • Content fills the Box but has fixed left padding = 132 dp (sidebar
    //     expanded width 116 dp + 16 dp breathing room). This leaves a tiny
    //     sliver visible when the sidebar collapses to 52 dp — that gap is
    //     filled by the blend gradient so it looks seamless.
    //   • Sidebar is aligned CenterStart on top; its own width animates
    //     116 <-> 52 dp but the CONTENT underneath NEVER REFLOWS.
    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(end = 32.dp),
    ) {
        // ── CONTENT (full-width, fixed position) ──────────────────────

        // ── CONTENT ───────────────────────────────────────────
        // Layered:
        //   1. Fixed hero backdrop (behind, never scrolls — stays on screen
        //      as user moves down through rows).
        //   2. Scrollable rows (in front, transparent top so hero peeks
        //      through; first row sits at ~55% of the viewport so the hero
        //      text is fully visible on first render).
        // ── CONTENT layer (fixed-position canvas) ─────────────────────
        // 156 dp left padding = sidebar width (140) + 16 dp gap, so content
        // never overlaps the sidebar even when it's fully expanded.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 156.dp),
        ) {
            val continueHandle = com.hushtv.tv.ui.screens.home.rememberContinueEntries(playlistId)
            val continueEntries = continueHandle.entries
            var heroEntry by remember { mutableStateOf<com.hushtv.tv.ui.screens.home.ContinueEntry?>(null) }
            LaunchedEffect(continueEntries.firstOrNull()) {
                if (heroEntry == null || continueEntries.none { it === heroEntry }) {
                    heroEntry = continueEntries.firstOrNull()
                }
            }

            // Long-press → remove prompt. Null means no prompt showing.
            var removePromptFor by remember {
                mutableStateOf<com.hushtv.tv.ui.screens.home.ContinueEntry?>(null)
            }

            // Discovery — shown when Continue Watching is empty.
            val discoveryCards = com.hushtv.tv.ui.screens.home.rememberDiscoveryCards(playlistId)
            var focusedDiscoveryCard by remember {
                mutableStateOf<com.hushtv.tv.ui.screens.home.DiscoveryCard?>(null)
            }
            LaunchedEffect(discoveryCards.firstOrNull()) {
                if (focusedDiscoveryCard == null ||
                    discoveryCards.none { it === focusedDiscoveryCard }
                ) {
                    focusedDiscoveryCard = discoveryCards.firstOrNull()
                }
            }

            val showDiscovery = continueEntries.isEmpty()

            // Hero layer — Continue Watching artwork OR Discovery mosaic,
            // depending on what the user is seeing below.
            if (showDiscovery) {
                com.hushtv.tv.ui.screens.home.HomeDiscoveryHeroLayer(card = focusedDiscoveryCard)
            } else {
                com.hushtv.tv.ui.screens.home.HomeHeroLayer(
                    entry = heroEntry,
                    contentStartPadding = 0.dp,
                )
            }

            // Continue Watching row is PINNED TO THE BOTTOM of the content
            // Box via Alignment.BottomStart. No LazyColumn, no focus-driven
            // auto-scroll, no chance of the row riding up into the hero
            // description. This is what the user kept asking for: the hero
            // block stays put and the cards stay put, regardless of focus.
            if (continueEntries.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                ) {
                    com.hushtv.tv.ui.screens.home.HomeContinueWatchingRow(
                        playlistId = playlistId,
                        entries = continueEntries,
                        contentStartPadding = 0.dp,
                        onFocusedEntryChange = { heroEntry = it },
                        onCardClick = { entry ->
                            nav.navigate(
                                "moviedetail/$playlistId/${entry.progress.streamId}" +
                                    "/${Uri.encode(entry.progress.title)}"
                            )
                        },
                        onLongPressRemove = { removePromptFor = it },
                    )
                }
            } else if (discoveryCards.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                ) {
                    com.hushtv.tv.ui.screens.home.HomeDiscoveryRow(
                        cards = discoveryCards,
                        contentStartPadding = 0.dp,
                        onFocusedCardChange = { focusedDiscoveryCard = it },
                        onCardClick = { card ->
                            val encoded = Uri.encode(card.categoryName)
                            nav.navigate("browse/$playlistId/${card.type}?category=$encoded")
                        },
                    )
                }
            }

            // Remove-from-Continue-Watching confirmation dialog.
            removePromptFor?.let { entry ->
                com.hushtv.tv.ui.screens.home.RemoveContinueWatchingDialog(
                    entry = entry,
                    onConfirm = {
                        continueHandle.remove(entry)
                        removePromptFor = null
                    },
                    onDismiss = { removePromptFor = null },
                )
            }
        }

        // ── SIDEBAR blend veil ───────────────────────────────────────
        // A wide horizontal gradient that sits BENEATH the sidebar but
        // extends past its 140 dp edge all the way to ~300 dp, giving
        // the nav a long, gentle fade into the content instead of a
        // sharp seam. Nothing in here is focusable — it's purely visual.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(300.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xFF0B1220),
                        0.40f to Color(0xE60B1220),
                        0.70f to Color(0x800B1220),
                        1.0f to Color(0x000B1220),
                    )
                )
        )

        // ── SIDEBAR overlay (on top, aligned left, never pushes content) ─
        // Always expanded — the collapsed/icon-only state caused focus-
        // traversal weirdness with the content to its right, so we lock it
        // to its full expanded width at all times. Still focusable, still
        // D-pad navigable, just no animation.
        Box(Modifier.align(Alignment.CenterStart)) {
            Sidebar(
                tabs = tabs,
                activeKey = "home",
                expanded = true,
                expiryStr = expiryStr,
                daysLeft = daysLeft,
                homeFocus = sidebarHomeFocus,
                onExpandChange = { /* no-op — sidebar always expanded */ },
                onTab = { t -> t.route?.let { nav.navigate(it) } },
                onProfile = { nav.navigate("home") },
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  LEFT SIDEBAR                                                    */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun Sidebar(
    tabs: List<NavTab>,
    activeKey: String,
    expanded: Boolean,
    expiryStr: String?,
    daysLeft: Long?,
    homeFocus: FocusRequester,
    onExpandChange: (Boolean) -> Unit,
    onTab: (NavTab) -> Unit,
    onProfile: () -> Unit,
) {
    val width by animateDpAsState(
        targetValue = if (expanded) 140.dp else 60.dp,
        animationSpec = tween(150),
        label = "sidebar-width",
    )

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            // Sidebar is now fully transparent — the wide horizontal blend
            // veil rendered behind it in the parent Box handles the fade
            // into the content. This removes the 140 dp hard-edge seam.
            .onFocusChanged { onExpandChange(it.hasFocus) }
            .padding(vertical = 28.dp, horizontal = 0.dp),
    ) {
        // Brand mark — Inter Black "hush.tv" when expanded, cyan dot when
        // collapsed. Animates cleanly.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(90)),
            ) {
                HushTVLogo(fontSize = 22.sp)
            }
            if (!expanded) {
                Box(
                    Modifier
                        .padding(start = 22.dp)
                        .size(10.dp)
                        .background(Cyan, CircleShape),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Nav tabs. No background boxes. Focus = cyan left accent bar +
        // white text + cyan icon. Active (non-focused) = white dim.
        tabs.forEachIndexed { i, tab ->
            val mod = if (i == 0) Modifier.focusRequester(homeFocus) else Modifier
            SidebarItem(
                label = tab.label,
                icon = tab.icon,
                active = tab.key == activeKey,
                expanded = expanded,
                modifier = mod,
                onClick = { onTab(tab) },
            )
        }

        Spacer(Modifier.weight(1f))

        // Expiry pill (only when expanded). Subtle — no background, just
        // quiet text in the corner.
        AnimatedVisibility(
            visible = expanded && expiryStr != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            expiryStr?.let { exp ->
                Column(Modifier.padding(start = 18.dp, end = 12.dp, bottom = 8.dp)) {
                    Text(
                        "EXPIRES",
                        color = TextDim,
                        fontSize = 9.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        exp,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                    )
                    daysLeft?.let { d ->
                        when {
                            d in 0..7 -> {
                                Spacer(Modifier.height(4.dp))
                                Badge(text = "${d}d left", bg = Amber, fg = Color.Black)
                            }
                            d < 0 -> {
                                Spacer(Modifier.height(4.dp))
                                Badge(text = "Expired", bg = Red, fg = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Divider above Profile — very subtle.
        Box(
            Modifier
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x1AFFFFFF))
        )

        SidebarItem(
            label = "Profile",
            icon = Icons.Default.Person,
            active = false,
            expanded = expanded,
            onClick = onProfile,
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Cyan accent bar slides in from the left on focus. Width animates
    // 0 → 3 dp. Matches the Netflix / Disney+ / Prime focus language: no
    // chunky highlight pills, just a clean vertical indicator and a
    // color change on icon + label.
    val accentWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = tween(140),
        label = "accent-width",
    )
    val iconTint = when {
        focused -> Cyan
        active -> TextPrimary
        else -> Color(0xFF94A3B8)
    }
    val textColor = when {
        focused -> TextPrimary
        active -> TextPrimary
        else -> Color(0xFFCBD5E1)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
    ) {
        // Left accent bar (fixed-width slot of 3 dp so icons don't shift
        // horizontally when focus arrives — only the cyan bar's alpha/width
        // animates within the slot).
        Box(
            Modifier
                .padding(start = 0.dp)
                .width(3.dp)
                .height(24.dp)
                .background(
                    if (accentWidth > 0.dp) Cyan else Color.Transparent,
                    RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp),
                )
        )
        Spacer(Modifier.width(if (expanded) 15.dp else 18.dp))

        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))

        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                color = textColor,
                fontSize = 13.sp,
                fontFamily = Inter,
                fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            color = fg,
            fontSize = 10.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  HERO BILLBOARD                                                  */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HeroBillboard(
    onPlay: () -> Unit,
    onMyList: () -> Unit,
) {
    var slideIdx by remember { mutableStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }

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
            .height(380.dp)
            .background(Brush.verticalGradient(colors = slide.gradient))
            .onFocusChanged { heroFocused = it.hasFocus },
    ) {
        // Gradients
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xB3000000), Color.Transparent),
                        startX = 0f,
                        endX = 800f,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xE6000000)),
                        startY = 180f,
                    )
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 40.dp)
                .widthIn(max = 620.dp),
        ) {
            Box(
                Modifier
                    .background(slide.accent, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    slide.badge,
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.8.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                slide.title,
                color = TextPrimary,
                fontSize = 40.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.2).sp,
                lineHeight = 44.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slide.genres.forEach { g ->
                    Box(
                        Modifier
                            .background(Color(0x26FFFFFF), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(g, color = TextPrimary, fontSize = 11.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!heroFocused) {
                Spacer(Modifier.height(10.dp))
                Text(
                    slide.synopsis,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = Inter,
                    lineHeight = 18.sp,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPlayButton(onClick = onPlay)
                HeroSecondaryButton(onClick = onMyList)
            }
        }

        // Progress dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 28.dp),
        ) {
            HERO_SLIDES.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .size(if (i == slideIdx) 18.dp else 6.dp, 6.dp)
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
private fun HeroPlayButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(90),
        label = "hero-play",
    )
    Row(
        Modifier
            .height(44.dp)
            .widthIn(min = 140.dp)
            .scale(scale)
            .background(if (focused) Cyan else Color.White, RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Play", color = Color.Black, fontSize = 15.sp, fontFamily = Inter, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroSecondaryButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(90),
        label = "hero-mylist",
    )
    Row(
        Modifier
            .height(44.dp)
            .widthIn(min = 140.dp)
            .scale(scale)
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x1FFFFFFF),
                RoundedCornerShape(7.dp),
            )
            .border(
                2.dp,
                if (focused) Cyan else Color(0x33FFFFFF),
                RoundedCornerShape(7.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Search", color = TextPrimary, fontSize = 15.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  ROWS & CARDS (compact sizing)                                   */
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
            .padding(horizontal = 48.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badgeColor?.let {
            Box(
                Modifier
                    .size(8.dp)
                    .background(it, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            color = TextPrimary,
            fontSize = 16.sp,
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
        fontSize = 12.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun LiveCard(card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Box(
        Modifier
            .size(width = 240.dp, height = 135.dp)
            .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
            .clickableWithEnter { onSelect(card) },
    ) {
        if (!card.poster.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceNavy),
                error = { LiveFallback() },
                loading = { LiveFallback() },
            )
        } else {
            LiveFallback()
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                )
        )
        Text(
            card.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp, end = 10.dp),
        )
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Red, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(
                "LIVE",
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
            )
        }
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
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Tv, null, tint = BorderSlate, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun PosterCardV2(card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Box(
        Modifier
            .size(width = 130.dp, height = 195.dp)
            .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
            .clickableWithEnter { onSelect(card) },
    ) {
        if (!card.poster.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                error = { PosterFallback() },
                loading = { PosterFallback() },
            )
        } else {
            PosterFallback()
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                )
        )
        Text(
            card.title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PosterFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceNavy, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Movie, null, tint = BorderSlate, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun TrendingCard(rank: Int, card: MediaCard, onSelect: (MediaCard) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            rank.toString(),
            color = Color(0x1AFFFFFF),
            fontSize = 90.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.Black,
            letterSpacing = (-4).sp,
            modifier = Modifier.offset(x = 16.dp, y = 4.dp),
        )
        Box(
            Modifier
                .size(width = 120.dp, height = 180.dp)
                .offset(x = (-18).dp)
                .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
                .clickableWithEnter { onSelect(card) },
        ) {
            if (!card.poster.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = card.poster,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
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
            .size(width = 200.dp, height = 112.dp)
            .tvFocusable(shape = RoundedCornerShape(10.dp), fillOnFocus = false)
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF164E63), Color(0xFF0F172A))),
                    RoundedCornerShape(10.dp),
                ),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .background(Cyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.35f)
                .height(3.dp)
                .background(Cyan),
        )
    }
}
