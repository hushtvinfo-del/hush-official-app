package com.hushtv.tv.ui.hushplus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.screens.home.TopNavBar
import com.hushtv.tv.ui.screens.home.rememberRequestsBadge
import com.hushtv.tv.ui.screens.home.topNavTabs
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Top-level Hush+ screen for TV.
 *
 * Layout (1920 × 1080):
 *
 *   ┌───────────────────────────────────────────────────────────┐
 *   │  [Home]  [Live TV]  [Movies]  [Series]  [Hush+] …  ⚙       │  ← global top nav
 *   ├───────────────────────────────────────────────────────────┤
 *   │  ┌──────────┐                                              │
 *   │  │ Overview │   ← active                                   │
 *   │  │ HushVOD+ │                                              │
 *   │  │ HushBooks│              Right pane content              │
 *   │  │ HushArcad│              (Overview hero / Addon detail)  │
 *   │  │ HushTube │                                              │
 *   │  └──────────┘                                              │
 *   └───────────────────────────────────────────────────────────┘
 *
 * Sub-page nav is local state (not real nav routes) so users get
 * instant tab switching without back-stack churn. Overview is the
 * default landing.
 */
@Composable
fun TVHushPlusScreen(nav: NavController, playlistId: String) {
    var selectedKey by rememberSaveable {
        mutableStateOf(HushPlusContent.OVERVIEW_KEY)
    }

    val firstSidebarFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { firstSidebarFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
    ) {
        // ── Page content (sits BEHIND the top-nav overlay, with
        //    top padding to clear the nav bar) ─────────────────────
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 110.dp, bottom = 36.dp),
        ) {
            HushPlusSidebar(
                selectedKey = selectedKey,
                onSelect = { selectedKey = it },
                firstFocus = firstSidebarFocus,
            )
            Spacer(Modifier.width(36.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (selectedKey == HushPlusContent.OVERVIEW_KEY) {
                    OverviewPane()
                } else {
                    val addon = HushPlusContent.findAddon(selectedKey)
                    if (addon != null) {
                        AddonDetailPane(addon = addon)
                    }
                }
            }
        }

        // ── Top-nav overlay (renders after content so it stays
        //    above the sidebar/content scrim) ────────────────────────
        val navTabs = topNavTabs(
            playlistId = playlistId,
            requestsBadge = rememberRequestsBadge(),
        )
        val homeTabFocus = remember { FocusRequester() }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
        ) {
            TopNavBar(
                tabs = navTabs,
                activeKey = "hushplus",
                homeFocus = homeTabFocus,
                onTab = { t ->
                    if (t.key == "hushplus") return@TopNavBar
                    t.route?.let { route ->
                        nav.navigate(route) {
                            popUpTo("menu/$playlistId") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onSettings = { nav.navigate("settings/$playlistId") },
            )
        }
    }
}

/* ───────────────────────── Sidebar ───────────────────────── */

@Composable
private fun HushPlusSidebar(
    selectedKey: String,
    onSelect: (String) -> Unit,
    firstFocus: FocusRequester,
) {
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x14FFFFFF))
            .padding(14.dp),
    ) {
        Text(
            "HUSH+",
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Premium add-on suite",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(18.dp))

        SidebarItem(
            label = "Overview",
            icon = Icons.Outlined.Star,
            selected = selectedKey == HushPlusContent.OVERVIEW_KEY,
            focusRequester = if (selectedKey == HushPlusContent.OVERVIEW_KEY) firstFocus else null,
            onClick = { onSelect(HushPlusContent.OVERVIEW_KEY) },
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "ADD-ONS",
            color = TextSecondary.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        HushPlusContent.addons.forEachIndexed { idx, addon ->
            SidebarItem(
                label = addon.name,
                icon = iconFor(addon.key),
                accent = addon.accent,
                selected = selectedKey == addon.key,
                focusRequester = if (
                    selectedKey == HushPlusContent.OVERVIEW_KEY && idx == 0
                ) null else if (selectedKey == addon.key) firstFocus else null,
                onClick = { onSelect(addon.key) },
            )
            if (idx != HushPlusContent.addons.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

private fun iconFor(key: String): ImageVector = when (key) {
    "vod" -> Icons.Outlined.LocalMovies
    "books" -> Icons.Outlined.AutoStories
    "arcade" -> Icons.Outlined.SportsEsports
    "tube" -> Icons.Outlined.Subscriptions
    else -> Icons.Outlined.Star
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester?,
    accent: Color = Cyan,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                when {
                    focused -> accent.copy(alpha = 0.22f)
                    selected -> accent.copy(alpha = 0.12f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                color = when {
                    focused -> accent
                    selected -> accent.copy(alpha = 0.55f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (focused || selected) accent else TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            color = if (focused || selected) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ───────────────────────── Overview pane ───────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewPane() {
    Column(Modifier.fillMaxSize()) {
        Text(
            "PREMIUM ADD-ON",
            color = Cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Introducing Hush+",
            color = TextPrimary,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 56.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The ultimate HushTV add-on suite — premium streaming, " +
                "millions of books & audiobooks, and thousands of retro " +
                "games. All in one.",
            color = TextSecondary,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth(0.9f),
        )

        Spacer(Modifier.height(28.dp))

        // 3-column pillar grid (2 rows × 3 cards = 6 pillars).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            maxItemsInEachRow = 3,
        ) {
            HushPlusContent.pillars.forEach { (title, body) ->
                PillarCard(
                    title = title,
                    body = body,
                    modifier = Modifier
                        .weight(1f)
                        .height(170.dp),
                )
            }
        }
    }
}

@Composable
private fun PillarCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x10FFFFFF))
            .border(
                width = 1.dp,
                color = Color(0x22FFFFFF),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(18.dp),
    ) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ───────────────────────── Addon detail pane ───────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddonDetailPane(addon: HushAddon) {
    Column(Modifier.fillMaxSize()) {
        Text(
            addon.eyebrow,
            color = addon.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            addon.name,
            color = TextPrimary,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 54.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            addon.tagline,
            color = TextSecondary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(24.dp))

        // Hero (left) + description / features (right).
        Row(
            Modifier
                .fillMaxWidth()
                .height(380.dp),
        ) {
            // Hero image / icon panel.
            Box(
                Modifier
                    .width(540.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x18FFFFFF))
                    .border(
                        width = 1.dp,
                        color = addon.accent.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!addon.heroImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = addon.heroImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = iconFor(addon.key),
                        contentDescription = null,
                        tint = addon.accent,
                        modifier = Modifier.size(140.dp),
                    )
                }
            }

            Spacer(Modifier.width(32.dp))

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    addon.description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "WHAT'S INCLUDED",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    addon.features.forEach { f ->
                        FeatureChip(label = f, accent = addon.accent)
                    }
                }
                Spacer(Modifier.weight(1f))
                ComingSoonCta(accent = addon.accent)
            }
        }
    }
}

@Composable
private fun FeatureChip(label: String, accent: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.45f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}

/**
 * Disabled "Coming soon" CTA. The Base44 entitlement check + APK
 * launch flow lands later; until then we render a static informative
 * affordance so users know access is gated and on its way.
 */
@Composable
private fun ComingSoonCta(accent: Color) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .height(54.dp)
            .clip(shape)
            .background(
                if (focused) accent.copy(alpha = 0.22f) else Color(0x14FFFFFF),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accent else accent.copy(alpha = 0.45f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter { /* no-op until Base44 wired */ }
            .padding(horizontal = 22.dp),
    ) {
        Icon(
            Icons.Outlined.Star,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "Coming soon — Hush+ launches with the next platform update",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
