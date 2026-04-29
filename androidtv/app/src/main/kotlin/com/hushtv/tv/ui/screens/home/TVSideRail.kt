package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * Disney+ style left-side navigation rail.
 *
 * **Collapsed (default)** — 80 dp wide vertical strip showing only
 * icons. Sits flush against the left edge of the screen. The user's
 * content (catalogue rows etc.) starts at x = 80 dp so the rail is
 * always visible but never overlaps content.
 *
 * **Expanded (any rail item gets focus)** — animates to 280 dp wide,
 * revealing the wordmark at the top + an icon-and-label row for each
 * destination. The expanded body OVERLAYS the catalogue content
 * with a semi-transparent backdrop dim so users still see what's
 * behind without it being legible enough to distract.
 *
 * # Focus model
 *
 *   ┌────┐                           ┌────┐
 *   │ ★  │   ← user is on a row card │  ☰ │   ← user pressed LEFT,
 *   │    │                           │    │     rail focuses & expands
 *   │ ▢  │                           │ ▢  │
 *   │ ▢  │ ─ press LEFT ─►           │ ▢  │
 *   │ ▢  │                           │ ▢  │
 *   └────┘                           └────┘
 *
 *   Pressing RIGHT collapses the rail and returns focus to whatever
 *   the user came from. We rely on Compose's native focus system —
 *   the caller's screen owns its own FocusRequester for "first card",
 *   and our items' DOWN/RIGHT navigation flows back to it.
 */
data class SideRailItem(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val showBadge: Boolean = false,
)

private val COLLAPSED_WIDTH = 80.dp
private val EXPANDED_WIDTH = 220.dp

/** Public constant — hub screens use this to offset their content. */
val SideRailCollapsedWidth = COLLAPSED_WIDTH

/**
 * Adapter — converts the existing TopNavTab list to SideRailItems
 * so we don't have to rebuild every callsite's tab definitions.
 * Settings is rendered as a dedicated bottom item by the rail itself,
 * so it's filtered out of the items here.
 */
fun List<TopNavTab>.toRailItems(): List<SideRailItem> = map { tab ->
    SideRailItem(
        key = tab.key,
        label = tab.label,
        icon = tab.icon,
        showBadge = tab.showBadge,
    )
}

/**
 * Convenience wrapper: place this inside a fillMaxSize Box on a hub
 * screen to render the rail with the standard items + the right
 * navigate behaviour. The hub is responsible for offsetting its own
 * content by [SideRailCollapsedWidth] on the start edge.
 */
@Composable
fun TVHubRail(
    activeKey: String,
    playlistId: String,
    nav: androidx.navigation.NavController,
    homeFocus: FocusRequester,
) {
    val tabs = topNavTabs(
        playlistId = playlistId,
        requestsBadge = rememberRequestsBadge(),
    )
    TVSideRail(
        activeKey = activeKey,
        items = tabs.toRailItems(),
        firstItemFocus = homeFocus,
        onSelect = { item ->
            if (item.key == activeKey) return@TVSideRail
            tabs.firstOrNull { it.key == item.key }?.route?.let { route ->
                nav.navigate(route) {
                    popUpTo("menu/$playlistId") { inclusive = false }
                    launchSingleTop = true
                }
            }
        },
        onSettings = { nav.navigate("settings/$playlistId") },
    )
}

/**
 * Top-level rail composable. Place INSIDE a fillMaxSize Box, aligned
 * to TopStart. Renders nothing structural over the rest of the
 * content — the catalogue draws normally at the right of [SideRailCollapsedWidth].
 *
 * @param activeKey  route key currently selected — that item is
 *                   styled differently from the rest.
 * @param items      the menu rows (Home / Live / Movies / Series /
 *                   Hush+ / Requests).
 * @param firstItemFocus FocusRequester used when the user presses
 *                   LEFT from the catalogue and we want focus to
 *                   land on the first rail item.
 * @param onSelect   called when a rail item is activated.
 * @param onSettings called when the gear at the bottom is activated.
 * @param onCollapsed called once the rail loses focus — hub screens
 *                   use this to optionally restore focus to "where
 *                   the user came from".
 */
@Composable
fun TVSideRail(
    activeKey: String,
    items: List<SideRailItem>,
    firstItemFocus: FocusRequester,
    onSelect: (SideRailItem) -> Unit,
    onSettings: () -> Unit,
) {
    // Track WHICH item currently has focus and WHEN it gained focus
    // — we only act on focus that has been stable for [HOLD_MS] ms.
    // This prevents the home page's lazy-load focus-flicker from
    // briefly highlighting the rail's first item AND from flashing
    // the expand animation. Below the hold threshold the rail
    // visually pretends nothing happened — the item gets no focus
    // ring, the rail stays its collapsed width, the divider
    // doesn't move.
    var focusedItemKey by remember { mutableStateOf<String?>(null) }
    var stableFocus by remember { mutableStateOf<String?>(null) }
    val expanded = stableFocus != null
    LaunchedEffect(focusedItemKey) {
        val key = focusedItemKey
        if (key == null) {
            stableFocus = null
        } else {
            // Hold at least 250 ms before treating focus as stable.
            // 4-frame transient flickers from lazy-list re-layout
            // are gone in <60 ms, so 250 ms is plenty of cushion.
            kotlinx.coroutines.delay(250)
            if (focusedItemKey == key) stableFocus = key
        }
    }
    val width by animateDpAsState(
        targetValue = if (expanded) EXPANDED_WIDTH else COLLAPSED_WIDTH,
        animationSpec = tween(durationMillis = 220),
        label = "rail-width",
    )

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
            )
        }

        Row(
            Modifier
                .width(width)
                .fillMaxHeight(),
        ) {
            // Solid rail background.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF05080F))
                    .padding(vertical = 24.dp),
            ) {
                BrandMark(expanded = expanded)
                Spacer(Modifier.height(28.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEachIndexed { idx, item ->
                        RailItem(
                            item = item,
                            active = item.key == activeKey,
                            expanded = expanded,
                            showVisualFocus = expanded,
                            focusRequester = if (idx == 0) firstItemFocus else null,
                            onFocusChanged = { hasFocus ->
                                focusedItemKey = if (hasFocus) item.key
                                else if (focusedItemKey == item.key) null
                                else focusedItemKey
                            },
                            onSelect = { onSelect(item) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                RailItem(
                    item = SideRailItem(
                        key = "settings",
                        label = "Settings",
                        icon = Icons.Filled.Settings,
                    ),
                    active = activeKey == "settings",
                    expanded = expanded,
                    showVisualFocus = expanded,
                    focusRequester = null,
                    onFocusChanged = { hasFocus ->
                        focusedItemKey = if (hasFocus) "settings"
                        else if (focusedItemKey == "settings") null
                        else focusedItemKey
                    },
                    onSelect = { onSettings() },
                )
            }
            // Cyan-tinted right-edge divider — sharp visual break
            // between rail and catalogue, no scroll bleed.
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0x4422D3EE)),
            )
        }
    }
}

/* ──────────────────────────────── Pieces ─────────────────────────────── */

@Composable
private fun BrandMark(expanded: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (expanded) {
            // Full wordmark — only shown in the expanded state.
            com.hushtv.tv.ui.HushTVLogo(fontSize = 22.sp)
        } else {
            // Collapsed monogram — square cyan-tinted "H" tile that
            // reads as a recognizable launcher-style icon.
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Cyan, Color(0xFF0891B2)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "H",
                    color = Color(0xFF05080F),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )
            }
        }
    }
}

@Composable
private fun RailItem(
    item: SideRailItem,
    active: Boolean,
    expanded: Boolean,
    showVisualFocus: Boolean,
    focusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Focus-driven visuals only render once the rail has actually
    // committed to being expanded (i.e. the parent's stable-focus
    // debounce confirmed). Without this gate, transient focus
    // events from the home page's lazy-load re-layout would briefly
    // light the item's cyan ring before the debounce nukes it.
    val visuallyFocused = focused && showVisualFocus
    val shape = RoundedCornerShape(12.dp)
    val baseModifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .padding(horizontal = 12.dp)
        .clip(shape)
        .background(
            when {
                visuallyFocused -> Cyan.copy(alpha = 0.22f)
                active -> Cyan.copy(alpha = 0.12f)
                else -> Color.Transparent
            },
        )
        .border(
            width = if (visuallyFocused) 2.dp else 0.dp,
            color = if (visuallyFocused) Cyan else Color.Transparent,
            shape = shape,
        )

    Row(
        modifier = baseModifier
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusable()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (visuallyFocused || active) Cyan else TextSecondary,
                modifier = Modifier.size(24.dp),
            )
            if (item.showBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Cyan),
                )
            }
        }
        // Label only fades in when expanded — collapsed = icon-only.
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180, delayMillis = 80)),
            exit = fadeOut(tween(120)),
        ) {
            Text(
                item.label,
                color = if (visuallyFocused || active) TextPrimary else TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
