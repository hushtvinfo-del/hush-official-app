package com.hushtv.tv.ui.hushxxx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hushtv.tv.data.HushXxxAgeGate
import com.hushtv.tv.data.HushXxxApi
import com.hushtv.tv.ui.screens.clickableWithEnter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Brand palette per /app/design_guidelines.json ────────────────
private val InkPrimary = Color(0xFF050505)
private val InkSecondary = Color(0xFF0A0A0A)
private val SurfaceColor = Color(0xFF141414)
private val SurfaceGlass = Color(0x99141414)
private val HotPink = Color(0xFFFF2A6D)
private val HotPinkHover = Color(0xFFFF5285)
private val PinkGlow = Color(0x80FF2A6D)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA3A3A3)
private val TextMuted = Color(0xFF737373)

@Composable
fun HushXxxScreen(
    onPlayScene: (streamUrl: String, title: String) -> Unit,
    onDmcaOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var ageOk by remember { mutableStateOf(HushXxxAgeGate.isConfirmed(ctx)) }

    if (!ageOk) {
        HushXxxAgeGateDialog(
            onConfirm = { HushXxxAgeGate.confirm(ctx); ageOk = true },
            onDecline = onDismiss,
        )
        return
    }

    var home by remember { mutableStateOf<HushXxxApi.Home?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<HushXxxApi.Scene?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        home = HushXxxApi.home()
        loading = false
    }

    Box(Modifier.fillMaxSize().background(InkPrimary)) {
        when {
            loading -> LoadingShimmer()
            home == null -> ErrorState()
            else -> HomeContent(
                home = home!!,
                onSceneClick = { selected = it },
                onPlayDirect = { s -> onPlayScene(s.absStream(), s.title) },
                onDmcaOpen = onDmcaOpen,
            )
        }

        selected?.let { s ->
            HushXxxSceneDetailDialog(
                scene = s,
                onPlay = {
                    onPlayScene(s.absStream(), s.title)
                    scope.launch { HushXxxApi.scene(s.id) }
                    selected = null
                },
                onDismiss = { selected = null },
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                          AGE GATE                                */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HushXxxAgeGateDialog(
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { confirmFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(InkPrimary.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center,
        ) {
            // Background pink-aurora glow
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            listOf(PinkGlow.copy(alpha = 0.3f), Color.Transparent),
                            radius = 800f,
                        ),
                    ),
            )
            Surface(
                color = Color(0xE6141414),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                shadowElevation = 60.dp,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(24.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(48.dp),
                ) {
                    Text(
                        "HUSH",
                        color = TextPrimary,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp,
                    )
                    Text(
                        "XXX",
                        color = HotPink,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp,
                        modifier = Modifier
                            .offset(y = (-12).dp)
                            .shadow(20.dp, spotColor = HotPink),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .background(HotPink.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
                            .border(1.dp, HotPink.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "18+ ONLY · ADULT CONTENT",
                            color = HotPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                    }

                    Spacer(Modifier.height(36.dp))
                    Text(
                        "Are you over 18?",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "By continuing, you confirm that you are at least 18 years old and that adult content is legal where you are. If you're under 18 or find such content inappropriate, please leave.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )

                    Spacer(Modifier.height(40.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BigPrimaryButton(
                            label = "I AM 18+ — ENTER",
                            modifier = Modifier.focusRequester(confirmFocus),
                            onClick = onConfirm,
                        )
                        BigSecondaryButton("Leave", onClick = onDecline)
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                          HOME                                    */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HomeContent(
    home: HushXxxApi.Home,
    onSceneClick: (HushXxxApi.Scene) -> Unit,
    onPlayDirect: (HushXxxApi.Scene) -> Unit,
    onDmcaOpen: () -> Unit,
) {
    val featured = remember(home) {
        (home.rails.new_and_popular + home.rails.trending + home.rails.top_rated)
            .distinctBy { it.id }
            .take(5)
    }
    val railsListState = rememberLazyListState()

    // ── Pinned hero on top, scrollable rails below.
    //    Hero is OUTSIDE the LazyColumn so D-pad-down through rails
    //    only scrolls the rails — the Hero stays anchored so the
    //    Play button is always reachable by D-pad-up.
    Column(Modifier.fillMaxSize()) {
        if (featured.isNotEmpty()) {
            HeroCarousel(
                scenes = featured,
                onPlay = onPlayDirect,
                onInfo = onSceneClick,
                onDmcaOpen = onDmcaOpen,
            )
        } else {
            Spacer(Modifier.height(60.dp))
        }

        LazyColumn(
            state = railsListState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 12.dp, bottom = 60.dp),
        ) {
            if (home.rails.new_and_popular.isNotEmpty()) {
                item {
                    Rail(
                        title = "New & Popular",
                        scenes = home.rails.new_and_popular,
                        onClick = onSceneClick,
                        cardStyle = RailCardStyle.WIDE,
                    )
                }
            }
            if (home.rails.trending.isNotEmpty()) {
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Rail(
                        title = "Trending Now",
                        scenes = home.rails.trending,
                        onClick = onSceneClick,
                        cardStyle = RailCardStyle.WIDE,
                    )
                }
            }
            if (home.rails.top_rated.isNotEmpty()) {
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Rail(
                        title = "Top Rated",
                        scenes = home.rails.top_rated,
                        onClick = onSceneClick,
                        cardStyle = RailCardStyle.WIDE,
                    )
                }
            }
            if (home.categories.isNotEmpty()) {
                item { Spacer(Modifier.height(24.dp)) }
                item { CategoriesRail(home.categories) }
            }
            if (home.performers.isNotEmpty()) {
                item { Spacer(Modifier.height(24.dp)) }
                item { PerformersRail(home.performers) }
            }
            if (home.studios.isNotEmpty()) {
                item { Spacer(Modifier.height(24.dp)) }
                item { StudiosRail(home.studios) }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                       HERO CAROUSEL                              */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HeroCarousel(
    scenes: List<HushXxxApi.Scene>,
    onPlay: (HushXxxApi.Scene) -> Unit,
    onInfo: (HushXxxApi.Scene) -> Unit,
    onDmcaOpen: () -> Unit,
) {
    var index by remember { mutableStateOf(0) }
    val current = scenes[index.coerceIn(0, scenes.lastIndex)]
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Auto-advance every 7 s
        while (true) {
            delay(7_000)
            index = (index + 1) % scenes.size
        }
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { playFocus.requestFocus() }
    }

    // Hero takes the top ~58 % of the screen, leaving ~42 % for the
    // rails. Both fit on one page with no scrolling needed for the
    // first two-three rows.
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.58f),
    ) {
        // Backdrop image — full bleed, fills the entire hero
        AsyncImage(
            model = current.absLandscape(),
            contentDescription = current.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .background(InkSecondary),
        )
        // Soft left-side fade for legibility
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            InkPrimary.copy(alpha = 0.82f),
                            InkPrimary.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        endX = 1200f,
                    ),
                ),
        )
        // Bottom fade — blend into the rails below
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, InkPrimary),
                    ),
                ),
        )

        // ── TOP ROW — hushxxx wordmark + 18+ pill (left) and the
        //    REPORT DMCA link (right). Lives at the top of the
        //    hero, never overlaps the studio name below.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "hush",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Text(
                "xxx",
                color = HotPink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .background(HotPink.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                    .border(1.dp, HotPink.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "18+",
                    color = HotPink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            HeroDmcaLink(onClick = onDmcaOpen)
        }

        // ── BOTTOM-LEFT — scene metadata + CTAs + indicator dots.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, end = 32.dp, bottom = 28.dp)
                .widthIn(max = 760.dp),
        ) {
            // Studio name
            current.studio?.let { st ->
                Text(
                    st.name.uppercase(),
                    color = HotPink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
            // Title
            Text(
                current.title,
                color = TextPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 44.sp,
                letterSpacing = (-0.5).sp,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))

            // Meta strip
            val metaParts = buildList {
                if (current.release_date.isNotBlank()) add(current.release_date.take(10))
                if (current.duration_s > 0) add(formatDuration(current.duration_s))
                val perfs = current.performers.take(3).joinToString(", ") { it.name }
                if (perfs.isNotEmpty()) add(perfs)
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    metaParts.joinToString("  ·  "),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigPrimaryButton(
                    label = "▶  Play",
                    modifier = Modifier.focusRequester(playFocus),
                    onClick = { onPlay(current) },
                )
                BigSecondaryButton(
                    label = "More info",
                    onClick = { onInfo(current) },
                )
            }
            Spacer(Modifier.height(12.dp))

            // Hero indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                scenes.forEachIndexed { i, _ ->
                    val active = i == index
                    Box(
                        Modifier
                            .height(3.dp)
                            .width(if (active) 28.dp else 14.dp)
                            .background(
                                if (active) HotPink else Color(0x33FFFFFF),
                                RoundedCornerShape(99.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroDmcaLink(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (focused) HotPink.copy(alpha = 0.18f) else Color(0x33000000),
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) HotPink else Color.Transparent,
                shape = RoundedCornerShape(99.dp),
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickableWithEnter { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "REPORT DMCA",
            color = if (focused) HotPink else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                         RAILS                                    */
/* ──────────────────────────────────────────────────────────────── */

private enum class RailCardStyle { WIDE, POSTER }

@Composable
private fun Rail(
    title: String,
    scenes: List<HushXxxApi.Scene>,
    onClick: (HushXxxApi.Scene) -> Unit,
    cardStyle: RailCardStyle,
) {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp)) {
        Text(
            title.uppercase(),
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 32.dp),
        ) {
            items(scenes, key = { it.id }) { s ->
                when (cardStyle) {
                    RailCardStyle.WIDE -> SceneCardWide(s, onClick = { onClick(s) })
                    RailCardStyle.POSTER -> SceneCardPoster(s, onClick = { onClick(s) })
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                       SCENE CARDS                                */
/* ──────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneCardWide(scene: HushXxxApi.Scene, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (focused) 1.0f else 0.85f,
        animationSpec = tween(220),
        label = "alpha",
    )
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .width(320.dp)
            .height(180.dp)
            .alpha(alpha)
            .shadow(
                elevation = if (focused) 28.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = HotPink,
                spotColor = HotPink,
            )
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceColor)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) HotPink else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    scope.launch { bringIntoView.bringIntoView() }
                }
            }
            .focusable()
            .clickableWithEnter { onClick() },
    ) {
        AsyncImage(
            model = scene.absLandscape(),
            contentDescription = scene.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(InkSecondary),
        )
        // Bottom dark fade for legibility
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f)),
                        startY = 60f,
                    ),
                ),
        )
        // Duration pill (top-right)
        if (scene.duration_s > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    formatDuration(scene.duration_s),
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // Studio + title
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .fillMaxWidth(),
        ) {
            scene.studio?.let { st ->
                Text(
                    st.name.uppercase(),
                    color = HotPink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                scene.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SceneCardPoster(scene: HushXxxApi.Scene, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(180.dp)
            .height(270.dp)
            .shadow(
                elevation = if (focused) 28.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = HotPink,
                ambientColor = HotPink,
            )
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceColor)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) HotPink else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter { onClick() },
    ) {
        AsyncImage(
            model = scene.absPoster(),
            contentDescription = scene.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(InkSecondary),
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                    CATEGORIES RAIL                               */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun CategoriesRail(categories: List<HushXxxApi.Category>) {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp)) {
        Text(
            "BROWSE BY CATEGORY",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 32.dp),
        ) {
            items(categories, key = { it.id }) { c -> CategoryChip(c) }
        }
    }
}

@Composable
private fun CategoryChip(c: HushXxxApi.Category) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .shadow(
                elevation = if (focused) 18.dp else 0.dp,
                shape = RoundedCornerShape(99.dp),
                spotColor = HotPink, ambientColor = HotPink,
            )
            .clip(RoundedCornerShape(99.dp))
            .background(if (focused) HotPink.copy(alpha = 0.18f) else SurfaceColor)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) HotPink else Color(0x14FFFFFF),
                shape = RoundedCornerShape(99.dp),
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Text(
            c.name,
            color = if (focused) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                   PERFORMERS RAIL                                */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun PerformersRail(performers: List<HushXxxApi.Performer>) {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp)) {
        Text(
            "TOP PERFORMERS",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(end = 32.dp),
        ) {
            items(performers, key = { it.id }) { p -> PerformerCard(p) }
        }
    }
}

@Composable
private fun PerformerCard(p: HushXxxApi.Performer) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .focusable()
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(
            Modifier
                .size(96.dp)
                .shadow(
                    elevation = if (focused) 24.dp else 0.dp,
                    shape = CircleShape,
                    spotColor = HotPink, ambientColor = HotPink,
                )
                .clip(CircleShape)
                .background(SurfaceColor)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) HotPink else Color(0x22FFFFFF),
                    shape = CircleShape,
                ),
        ) {
            if (p.photo_url.isNotBlank()) {
                AsyncImage(
                    model = p.photo_url,
                    contentDescription = p.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        p.name.take(1).uppercase(),
                        color = HotPink,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            p.name,
            color = if (focused) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                      STUDIOS RAIL                                */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun StudiosRail(studios: List<HushXxxApi.Studio>) {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp)) {
        Text(
            "STUDIOS",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 32.dp),
        ) {
            items(studios, key = { it.id }) { s -> StudioCard(s) }
        }
    }
}

@Composable
private fun StudioCard(s: HushXxxApi.Studio) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(220.dp)
            .height(72.dp)
            .shadow(
                elevation = if (focused) 18.dp else 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = HotPink, ambientColor = HotPink,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceColor)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) HotPink else Color(0x14FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            s.name,
            color = if (focused) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                      DMCA FOOTER                                 */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun DmcaFooter(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(if (focused) HotPink.copy(alpha = 0.15f) else Color.Transparent)
                .border(
                    width = if (focused) 1.dp else 0.dp,
                    color = if (focused) HotPink else Color.Transparent,
                    shape = RoundedCornerShape(99.dp),
                )
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .clickableWithEnter { onClick() }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                "Report a DMCA violation",
                color = if (focused) HotPink else TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                  SCENE DETAIL DIALOG                             */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun HushXxxSceneDetailDialog(
    scene: HushXxxApi.Scene,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { playFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(InkPrimary)) {
            // Backdrop
            AsyncImage(
                model = scene.absLandscape(),
                contentDescription = scene.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(InkSecondary),
            )
            // Left fade
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(InkPrimary, InkPrimary.copy(alpha = 0.7f), Color.Transparent),
                            endX = 1500f,
                        ),
                    ),
            )
            // Bottom fade
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, InkPrimary),
                            startY = 200f,
                        ),
                    ),
            )

            // Close button (top-right)
            var closeFocused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(28.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (closeFocused) HotPink else Color(0x66000000))
                    .border(
                        width = if (closeFocused) 2.dp else 1.dp,
                        color = if (closeFocused) Color.White else Color(0x33FFFFFF),
                        shape = CircleShape,
                    )
                    .focusable()
                    .onFocusChanged { closeFocused = it.isFocused }
                    .clickableWithEnter { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // Content (bottom-left)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, end = 32.dp, bottom = 48.dp)
                    .widthIn(max = 800.dp),
            ) {
                scene.studio?.let { st ->
                    Text(
                        st.name.uppercase(),
                        color = HotPink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.5.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    scene.title,
                    color = TextPrimary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 54.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 2,
                )
                Spacer(Modifier.height(14.dp))

                // Meta row
                val meta = buildList {
                    if (scene.release_date.isNotBlank()) add(scene.release_date.take(10))
                    if (scene.duration_s > 0) add(formatDuration(scene.duration_s))
                    if (scene.views > 0) add("${scene.views} views")
                }.joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(meta, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                if (scene.performers.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Cast: " + scene.performers.joinToString(", ") { it.name },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                    )
                }

                if (scene.description.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        scene.description,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        maxLines = 4,
                    )
                }

                if (scene.categories.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        scene.categories.take(8).forEach { c ->
                            Box(
                                Modifier
                                    .background(SurfaceGlass, RoundedCornerShape(99.dp))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    c.name,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BigPrimaryButton(
                        label = "▶  Play",
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = onPlay,
                    )
                    BigSecondaryButton("Close", onClick = onDismiss)
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                      BUTTONS                                     */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun BigPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .shadow(
                elevation = if (focused) 30.dp else 8.dp,
                shape = RoundedCornerShape(99.dp),
                spotColor = HotPink, ambientColor = HotPink,
            )
            .clip(RoundedCornerShape(99.dp))
            .background(if (focused) HotPinkHover else HotPink)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(99.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter { onClick() }
            .padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun BigSecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .shadow(
                elevation = if (focused) 16.dp else 0.dp,
                shape = RoundedCornerShape(99.dp),
                spotColor = HotPink, ambientColor = HotPink,
            )
            .clip(RoundedCornerShape(99.dp))
            .background(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) HotPink else Color(0x33FFFFFF),
                shape = RoundedCornerShape(99.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter { onClick() }
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Text(
            label,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*                  LOADING + ERROR STATES                          */
/* ──────────────────────────────────────────────────────────────── */

@Composable
private fun LoadingShimmer() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text("hush", color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                Text("xxx", color = HotPink, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Loading library…", color = TextSecondary, fontSize = 13.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun ErrorState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Can't reach HushXXX right now.", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Check your connection and try again.", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
