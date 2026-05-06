package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter

/**
 * A single nav tab item rendered in the top bar. The model is kept
 * public so the parent screen can own the tab list + route definitions.
 */
data class TopNavTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val route: String?,
    /**
     * When true, render a pulsing cyan "✨ NEW" indicator dot beside
     * the tab label. Used by the Requests tab to surface unread
     * status changes without forcing the user to open the page.
     */
    val showBadge: Boolean = false,
)

/**
 * Single source of truth for the top-nav tab list.
 *
 * EVERY top-level screen (Home, Live TV, Movies, Series, Requests,
 * Search, Collection Detail, …) must call this rather than building
 * its own list. Otherwise tabs added to one screen don't appear on
 * the others — which is exactly how the Requests tab was missing
 * from Live TV / Movies / Series in the past.
 *
 * Order, labels and icons are intentionally fixed. The only per-call
 * variation is:
 *   - [requestsBadge] : drives the pulsing cyan dot on the Requests
 *     tab to surface unread status changes.
 *   - [homeRoute] : Home is `null` on the Home screen itself (no-op
 *     when tapped) but `"menu/{playlistId}"` everywhere else so the
 *     user can navigate back home.
 */
@Composable
fun topNavTabs(
    playlistId: String,
    requestsBadge: Boolean = false,
    homeRoute: String? = "menu/$playlistId",
): List<TopNavTab> = listOf(
    TopNavTab("home",     "Home",     Icons.Default.Home,       homeRoute),
    TopNavTab("live",     "Live TV",  Icons.Default.Tv,         "browse/$playlistId/live"),
    TopNavTab("movies",   "Movies",   Icons.Default.Movie,      "browse/$playlistId/movie"),
    TopNavTab("series",   "Series",   Icons.Outlined.Slideshow, "browse/$playlistId/series"),
    TopNavTab("hushplus", "Hush+",    Icons.Default.Star,       "hushplus/$playlistId"),
    TopNavTab("requests", "Requests", Icons.Default.Inbox,      "requests/$playlistId", showBadge = requestsBadge),
    TopNavTab("search",   "Search",   Icons.Default.Search,     "search/$playlistId"),
)

/**
 * Shared "are there unseen request status changes?" reactive state.
 * Drives the cyan pulse dot on the Requests tab from EVERY top-level
 * screen, not just Home — otherwise the dot disappears when you
 * leave Home and the user has no idea anything's pending.
 *
 * Recomputes on every Lifecycle.Event.ON_RESUME so navigating into
 * the Requests page (which calls markSeen) silences the dot
 * immediately when the user comes back.
 */
@Composable
fun rememberRequestsBadge(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick += 1
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return remember(tick) {
        val cached = com.hushtv.tv.data.RequestCache.all()
        com.hushtv.tv.data.RequestSeenStore
            .filterUnseen(ctx, cached).isNotEmpty()
    }
}

/**
 * Netflix-style top navigation bar.
 *
 * Rendered as an OVERLAY — sits on top of the hero backdrop without
 * clipping or inset. The hero art renders edge-to-edge behind it, so
 * removing this nav causes zero layout reflow.
 *
 *   [ Logo ]  Home · Live TV · Movies · Series · Search           ⚙
 *
 *   • 72 dp tall solid container (deep navy 92% alpha) — gives the
 *     buttons a proper framed zone so they're never floating over a
 *     bright patch of the backdrop
 *   • Thin 1 dp cyan-tinted bottom border to visually separate nav
 *     from content
 *   • Active tab: cyan-filled pill with black text
 *   • Focused tab: cyan ring + white text
 *   • Settings gear pinned to the far right; profile actions live
 *     inside the Settings screen itself so the nav stays minimal
 */
@Composable
fun TopNavBar(
    tabs: List<TopNavTab>,
    activeKey: String,
    homeFocus: FocusRequester,
    onTab: (TopNavTab) -> Unit,
    onSettings: () -> Unit,
    daysLeft: Long? = null,
    layoutHint: String? = null,
    onLayoutHintClick: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            // Solid container — gives the tabs their own framed zone
            // against any backdrop art. Keep it just dark enough that
            // dropped contrast is never an issue; still faintly shows
            // the backdrop beneath so the app doesn't feel boxed-in.
            .background(Color(0xEB0B1220))
            // Thin cyan-tinted bottom border — subtle separator.
            .border(
                width = 0.dp,
                color = Color.Transparent,
                shape = RoundedCornerShape(0.dp),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Logo ────────────────────────────────────────────────
            HushTVLogo(fontSize = 22.sp)

            Spacer(Modifier.width(40.dp))

            // ── Center tab rail ────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { idx, tab ->
                    val mod = if (idx == 0) Modifier.focusRequester(homeFocus) else Modifier
                    TopNavTabView(
                        tab = tab,
                        active = tab.key == activeKey,
                        modifier = mod,
                        onClick = { onTab(tab) },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Layout-hint chip and subscription expiry badge were
            // intentionally removed from the top bar — together they
            // made the right edge feel cluttered. Settings is still
            // reachable via the gear icon. Both pieces of info live
            // on the dedicated Settings screen.

            // ── Settings gear ──────────────────────────────────────
            SettingsIconButton(onClick = onSettings)
        }

        // 1 dp cyan-tinted bottom border drawn as a child Box so it
        // sits exactly on the bottom edge regardless of row alignment.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x3306B6D4)),
        )
    }
}

@Composable
private fun TopNavTabView(
    tab: TopNavTab,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    // Modern underline style — no pill, no rounded background fill.
    // Only the underneath cyan bar shifts with state:
    //   • active         → solid cyan 3 dp underline (fills 100% of label)
    //   • focused        → solid cyan 2 dp underline
    //   • active+focused → same as active (3 dp) with slightly brighter text
    //   • neither        → no underline, muted grey text
    val contentColor = when {
        active -> Color.White
        focused -> Color.White
        else -> Color(0xFFCBD5E1)
    }
    val underlineColor by animateColorAsState(
        targetValue = when {
            active || focused -> Cyan
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "top-nav-underline-color",
    )
    val underlineThickness by animateDpAsState(
        targetValue = when {
            active -> 3.dp
            focused -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(160),
        label = "top-nav-underline-thickness",
    )

    Column(
        modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                tab.label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (active || focused) FontWeight.Black else FontWeight.SemiBold,
                fontFamily = Inter,
                letterSpacing = 0.3.sp,
            )
            // ── Unread/"new" pulse dot ──
            // Tabs that surface server-side state (e.g. Requests) opt
            // in via `tab.showBadge`. The dot pulses in opacity so
            // it's eye-catching without being noisy. Hidden once the
            // user opens the page (caller flips `showBadge = false`).
            if (tab.showBadge) {
                Spacer(Modifier.width(6.dp))
                // v1.44.24 — Lite-aware. Pro: pulse 0.45→1.0 alpha
                // every 900 ms (Reverse). Lite: full alpha, no pulse.
                val alpha by com.hushtv.tv.ui.lite.rememberLiteAwareFloat(
                    label = "nav-badge",
                    liteValue = 1f,
                    initialValue = 0.45f,
                    targetValue = 1f,
                    durationMs = 900,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                )
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Cyan.copy(alpha = alpha)),
                )
            }
        }
        // Underline — 5 dp gap below the text, width scales with label.
        Spacer(Modifier.height(5.dp))
        val underlineWidth = (24 + tab.label.length * 7).dp
        Box(
            Modifier
                .width(underlineWidth)
                .height(underlineThickness)
                .background(underlineColor, RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Subscription expiry indicator. Tiny coloured dot + days-remaining
 * text, rendered as a compact non-focusable pill.
 *
 *   • Green   → > 30 days left (healthy)
 *   • Cyan    → 8–30 days left (fine but getting close)
 *   • Amber   → 1–7 days left (renew soon)
 *   • Red     → expired (0 or negative)
 */
@Composable
private fun ExpiryBadge(daysLeft: Long) {
    val (dotColor, label) = when {
        daysLeft <= 0 -> Color(0xFFEF4444) to "EXPIRED"
        daysLeft <= 7 -> Color(0xFFF59E0B) to "${daysLeft}d left"
        daysLeft <= 30 -> Cyan to "${daysLeft}d left"
        else -> Color(0xFF22C55E) to "${daysLeft}d left"
    }
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun SettingsIconButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) Color(0x26FFFFFF) else Color(0x14FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Settings",
            tint = if (focused) Cyan else Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Tiny layout-mode hint chip. Tucked between the tab rail and the
 * expiry badge. Cyan-tinted pill shows a Dashboard icon + the current
 * layout label ("SIDEBAR" or "TOP BAR"). Focusable; ENTER opens the
 * layout chooser so power-users can toggle with a single click.
 */
@Composable
private fun LayoutHintChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(140),
        label = "layout-hint-scale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Cyan.copy(alpha = 0.22f) else Color(0x1F06B6D4))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) Cyan else Color(0x5506B6D4),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 10.dp),
    ) {
        Icon(
            Icons.Default.Dashboard,
            contentDescription = null,
            tint = Cyan,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label.uppercase(),
            color = if (focused) Color.White else Color(0xFFB7EAF3),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp,
            fontFamily = Inter,
        )
    }
}
