@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.data.LayoutPrefsStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.delay

/**
 * First-run / Settings layout chooser. Full-screen modal with two
 * large, tap-friendly cards showing a mini-mockup of each layout.
 * Selecting one persists the choice via [LayoutPrefsStore] and
 * invokes [onPicked] so the caller can dismiss + re-compose the
 * screen with the new mode.
 *
 * Heading and copy are tuned for a TV remote audience — big type,
 * short sentences, unambiguous CTAs.
 */
@Composable
fun LayoutChooserDialog(
    currentMode: String,
    dismissable: Boolean,
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val topFocus = remember { FocusRequester() }
    val sideFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(240)
        runCatching {
            when (currentMode) {
                LayoutPrefsStore.MODE_SIDEBAR -> sideFocus.requestFocus()
                else -> topFocus.requestFocus()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF0B1422),
                        1.0f to Color(0xFF030509),
                    )
                )
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Header ──
                Text(
                    "CHOOSE YOUR LAYOUT",
                    color = Cyan,
                    fontSize = 11.sp,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "How should we show categories?",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 28.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This applies to Live TV, Movies and Series. " +
                        "You can change it anytime from Settings.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(20.dp))

                // ── Two cards side-by-side ──
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    LayoutCard(
                        title = "Top Bar",
                        subtitle = "RECOMMENDED · DEFAULT",
                        description = "Compact toolbar at the top with a BROWSE dropdown. More room for your library.",
                        selected = currentMode == LayoutPrefsStore.MODE_TOP,
                        preview = { TopBarPreview() },
                        focusRequester = topFocus,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onPicked(LayoutPrefsStore.MODE_TOP) },
                    )
                    LayoutCard(
                        title = "Left Sidebar",
                        subtitle = "CLASSIC",
                        description = "Persistent vertical category rail on the left — every category visible at once.",
                        selected = currentMode == LayoutPrefsStore.MODE_SIDEBAR,
                        preview = { SidebarPreview() },
                        focusRequester = sideFocus,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onPicked(LayoutPrefsStore.MODE_SIDEBAR) },
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (dismissable) {
                    Text(
                        "Press BACK to keep current layout",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = Inter,
                    )
                } else {
                    Text(
                        "Pick one to continue — you can change this later in Settings.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = Inter,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutCard(
    title: String,
    subtitle: String,
    description: String,
    selected: Boolean,
    preview: @Composable () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(150),
        label = "layout-card-scale",
    )
    Column(
        modifier
            .widthIn(max = 520.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (focused) Cyan.copy(alpha = 0.08f) else Color(0xFF0C1523),
                shape,
            )
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    selected -> Cyan.copy(alpha = 0.6f)
                    else -> Color(0x22FFFFFF)
                },
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(18.dp),
    ) {
        // Preview — fixed-height so it can't eat all the vertical
        // space on shorter TV screens. weight(1f) absorbs any extra
        // vertical space beyond that minimum.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .heightIn(min = 100.dp, max = 170.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF050B17))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp)),
        ) {
            preview()
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                subtitle,
                color = Cyan,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .background(Cyan, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "CURRENT",
                        color = Color(0xFF05080F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                        fontFamily = Inter,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 24.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = Inter,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/* ──────────────────────────────────────────────────────────────── */
/*  MINI MOCKUPS — rendered to look like actual app screenshots     */
/* ──────────────────────────────────────────────────────────────── */

/**
 * Palette of gradient pairs used as faux-posters in the preview grids.
 * Each poster in the mock uses a different pair so the grid reads as
 * "a collection of movie posters" at a glance, not as flat rectangles.
 */
private val PosterPalette = listOf(
    Color(0xFF1E3A8A) to Color(0xFF0F172A),
    Color(0xFF831843) to Color(0xFF4C0519),
    Color(0xFF064E3B) to Color(0xFF022C22),
    Color(0xFF78350F) to Color(0xFF431407),
    Color(0xFF581C87) to Color(0xFF2E1065),
    Color(0xFF155E75) to Color(0xFF0E3543),
    Color(0xFF9F1239) to Color(0xFF4C0519),
    Color(0xFF1E293B) to Color(0xFF020617),
    Color(0xFF7E22CE) to Color(0xFF3B0764),
    Color(0xFFB45309) to Color(0xFF451A03),
    Color(0xFF0891B2) to Color(0xFF083344),
    Color(0xFF334155) to Color(0xFF0F172A),
)

@Composable
private fun MiniPoster(seed: Int, modifier: Modifier = Modifier) {
    val (top, bottom) = PosterPalette[seed.mod(PosterPalette.size)]
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        // Thin cyan "progress" bar at the bottom of some posters so the
        // grid feels alive, not static. Every 3rd poster gets one.
        if (seed % 3 == 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.6f)
                    .height(1.dp)
                    .background(Cyan),
            )
        }
    }
}

/** Mini mockup of the Top-Bar layout — looks like a real app screenshot. */
@Composable
private fun TopBarPreview() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF050B18),
                    1f to Color(0xFF000000),
                )
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top nav bar ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Color(0xEB0B1220))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Logo pill
                Box(
                    Modifier
                        .size(width = 22.dp, height = 6.dp)
                        .background(Cyan, RoundedCornerShape(1.dp)),
                )
                Spacer(Modifier.width(10.dp))
                // Tabs (5)
                repeat(5) { i ->
                    Box(
                        Modifier
                            .size(width = 14.dp, height = 6.dp)
                            .background(
                                if (i == 2) Color.White else Color(0x66FFFFFF),
                                RoundedCornerShape(1.dp),
                            ),
                    )
                    if (i < 4) Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.weight(1f))
                // Settings gear
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x55FFFFFF)),
                )
            }
            // Cyan separator
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x5506B6D4)))

            // ── Category toolbar ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Color(0xFF05080F))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // BROWSE ▾ pill
                Box(
                    Modifier
                        .size(width = 34.dp, height = 8.dp)
                        .background(Cyan.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, Cyan, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.weight(1f))
                // Title on the right
                Box(
                    Modifier
                        .size(width = 28.dp, height = 6.dp)
                        .background(Color(0x99FFFFFF), RoundedCornerShape(1.dp)),
                )
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x14FFFFFF)))

            // ── Poster grid ── (fills remaining space)
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(3) { row ->
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(7) { col ->
                            MiniPoster(
                                seed = row * 7 + col,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Mini mockup of the Left-Sidebar layout — looks like a real app screenshot. */
@Composable
private fun SidebarPreview() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF050B18),
                    1f to Color(0xFF000000),
                )
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top nav bar ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Color(0xEB0B1220))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 22.dp, height = 6.dp)
                        .background(Cyan, RoundedCornerShape(1.dp)),
                )
                Spacer(Modifier.width(10.dp))
                repeat(5) { i ->
                    Box(
                        Modifier
                            .size(width = 14.dp, height = 6.dp)
                            .background(
                                if (i == 2) Color.White else Color(0x66FFFFFF),
                                RoundedCornerShape(1.dp),
                            ),
                    )
                    if (i < 4) Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x55FFFFFF)),
                )
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x5506B6D4)))

            // ── Sidebar + grid ──
            Row(Modifier.fillMaxSize()) {
                // Sidebar column
                Column(
                    Modifier
                        .width(62.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF05080F))
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Header with accent bar + title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(width = 1.5.dp, height = 7.dp)
                                .background(Cyan, RoundedCornerShape(1.dp)),
                        )
                        Spacer(Modifier.width(3.dp))
                        Box(
                            Modifier
                                .size(width = 28.dp, height = 5.dp)
                                .background(Cyan, RoundedCornerShape(1.dp)),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0x14FFFFFF)),
                    )
                    Spacer(Modifier.height(2.dp))
                    // Category rows — row #2 is the "selected" one (cyan)
                    repeat(8) { i ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    if (i == 2) Cyan.copy(alpha = 0.28f)
                                    else Color.Transparent,
                                    RoundedCornerShape(2.dp),
                                )
                                .border(
                                    width = if (i == 2) 0.5.dp else 0.dp,
                                    color = if (i == 2) Cyan else Color.Transparent,
                                    shape = RoundedCornerShape(2.dp),
                                )
                                .padding(horizontal = 3.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                Modifier
                                    .size(
                                        width = (38 - (i * 2).coerceAtMost(14)).dp,
                                        height = 3.dp,
                                    )
                                    .background(
                                        if (i == 2) Color.White else Color(0x99FFFFFF),
                                        RoundedCornerShape(1.dp),
                                    ),
                            )
                        }
                    }
                }
                // Vertical divider
                Box(Modifier.width(0.5.dp).fillMaxHeight().background(Color(0x1FFFFFFF)))
                // Poster grid
                Column(
                    Modifier.fillMaxSize().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) { row ->
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            repeat(6) { col ->
                                MiniPoster(
                                    seed = row * 6 + col + 3,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
