package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
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
 * Apply this Modifier to the OUTER container of a hub screen's
 * content so:
 *   • Pressing LEFT when there is no further card to the left
 *     (i.e. focus has reached the leftmost column of a row) jumps
 *     focus to the rail's first item (Home), regardless of which
 *     row the user is on. Compose's default 2D focus search would
 *     otherwise pick whichever rail item is vertically aligned
 *     with the user's current card; we want the entry point to
 *     ALWAYS be Home so the model is predictable.
 *   • When the user enters the rail and then presses RIGHT to come
 *     back, focus is restored to the SAME card they came from
 *     (focusRestorer remembers the last-focused child of the group).
 *   • Intra-grid LEFT/RIGHT/UP/DOWN navigation between cards is
 *     completely unaffected — only the LEFT-at-the-edge case is
 *     redirected to the rail.
 *
 * # Implementation
 *
 * We can't naively consume LEFT in `onPreviewKeyEvent` (that breaks
 * intra-row navigation) and we can't trust `onKeyEvent` to fire only
 * after children handle it (Compose's default focusable navigation
 * doesn't propagate "consumed" upwards — `onKeyEvent` always fires
 * on every ancestor regardless of whether focus moved).
 *
 * The robust approach is: in `onPreviewKeyEvent`, MANUALLY call
 * `focusManager.moveFocus(Left)`. This returns `true` when focus
 * successfully moved within the focus group (intra-row case), and
 * `false` when no focusable target was found in that direction
 * (we're at the leftmost edge). Either way we consume the event so
 * the default focusable handler doesn't fire afterwards and
 * double-move focus.
 *
 * `focusGroup()` is what bounds `moveFocus(Left)` to STAY inside
 * the content area — without it, the spatial search would happily
 * step out of the content area and into the rail, defeating the
 * whole point of the redirect.
 *
 * We deliberately AVOID the declarative `focusProperties { exit = … }`
 * form because it resolves its target synchronously inside
 * `dispatchKeyEvent` and crashes if the target isn't attached.
 * The codebase's `auditFocusProperties` Gradle task enforces this.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvHubContentFocus(
    firstRailItemFocus: FocusRequester,
): Modifier {
    val focusManager = LocalFocusManager.current
    return this
        .focusGroup()
        .focusRestorer()
        .onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (ev.key != Key.DirectionLeft) return@onPreviewKeyEvent false
            // Try moving focus left WITHIN the focus group first.
            // focusGroup() bounds the search so it can't escape into
            // the rail — when there's no card to the left, this
            // returns false and we redirect.
            val moved = focusManager.moveFocus(FocusDirection.Left)
            if (!moved) {
                runCatching { firstRailItemFocus.requestFocus() }
            }
            // Always consume so Compose's default focusable LEFT
            // handler doesn't run afterwards and double-move focus.
            true
        }
}

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
                    .padding(vertical = 18.dp),
            ) {
                BrandMark(expanded = expanded)
                Spacer(Modifier.height(18.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Rail can hold up to 7 items (Home / Live TV /
                        // Movies / Series / Hush+ / Requests / Search)
                        // plus a separate Settings row at the bottom.
                        // On a 540 dp-effective TV that's tight, so we
                        // (a) keep item heights compact (48 dp) and
                        // (b) wrap in verticalScroll as a safety valve.
                        // D-pad focus traversal triggers bringIntoView
                        // so the user never sees an item disappear
                        // off-screen even on the smallest TVs.
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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

                Spacer(Modifier.height(4.dp))
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
            // Solid opaque divider — fully opaque dark grey so the
            // line cannot ever pick up the background colour of the
            // catalogue content as it scrolls past. Translucent cyan
            // looked great on a static background but visibly shifted
            // hue against bright content, which read as a "glitch".
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1F2937)),
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
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (expanded) {
            // Full wordmark — only shown in the expanded state.
            com.hushtv.tv.ui.HushTVLogo(fontSize = 22.sp)
        } else {
            // Collapsed brand mark — modelled on the user's
            // reference design:
            //   • Outer dark rounded-square "tile" (chip).
            //   • Inner inset frame outlined with a cyan→violet
            //     diagonal gradient — the iconic touch.
            //   • Centred lowercase "h" with a chrome / silver
            //     vertical gradient (white at top → slightly
            //     dimmed at bottom) for premium depth.
            //   • Optional subtle outer halo behind the chip so
            //     the icon glows against pure-black backgrounds
            //     without looking glued on.
            HushBrandTile(size = 48.dp)
        }
    }
}

/**
 * Reusable HushTV brand tile. Drop this anywhere a square brand
 * monogram is wanted (collapsed sidebar, splash, etc.). All sizing
 * is derived from a single [size] parameter so the icon scales
 * gracefully.
 */
@Composable
private fun HushBrandTile(size: androidx.compose.ui.unit.Dp) {
    val outerShape = RoundedCornerShape(percent = 22)
    val frameShape = RoundedCornerShape(percent = 22)
    // Cyan → violet gradient palette borrowed from the reference
    // image. The cyan end matches our existing brand accent so it
    // stays on-palette.
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF22D3EE),  // cyan
            Color(0xFF6366F1),  // indigo
            Color(0xFF8B5CF6),  // violet
        ),
    )
    val haloRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (size * 1.2f).toPx()
    }
    Box(
        Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Outer halo glow so the icon never looks flat against
        // pure black.
        Box(
            Modifier
                .size(size)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x4422D3EE),
                            Color.Transparent,
                        ),
                        radius = haloRadiusPx,
                    ),
                ),
        )
        // Outer dark chip.
        Box(
            Modifier
                .size(size)
                .clip(outerShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0C1426),
                            Color(0xFF050912),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = Color(0x33FFFFFF),
                    shape = outerShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Inner gradient-bordered inset frame.
            Box(
                Modifier
                    .size(size * 0.72f)
                    .clip(frameShape)
                    .background(Color(0xFF050912))
                    .border(
                        width = 2.dp,
                        brush = gradient,
                        shape = frameShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Chrome "h" — vertical gradient from bright white
                // at the top to a slightly muted off-white at the
                // base, mimicking polished chrome / silver.
                Text(
                    "h",
                    color = Color.White,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = com.hushtv.tv.ui.theme.Inter,
                    letterSpacing = (-1).sp,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFD4D9E0),
                            ),
                        ),
                    ),
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
        .height(48.dp)
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
