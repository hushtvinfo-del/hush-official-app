package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Settings
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
)

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

    // Visual treatment:
    //   • active + focused  → bright cyan fill, black text
    //   • active only       → dimmer cyan fill, black text
    //   • focused only      → translucent white fill, cyan ring, white text
    //   • neither           → fully transparent, grey text
    val bg: Color
    val contentColor: Color
    val borderColor: Color
    val borderW = if (focused) 2.dp else 0.dp
    when {
        focused && active -> {
            bg = Cyan; contentColor = Color(0xFF0A0F1C); borderColor = Cyan
        }
        active -> {
            bg = Color(0xFF0891B2); contentColor = Color(0xFF0A0F1C); borderColor = Color.Transparent
        }
        focused -> {
            bg = Color(0x26FFFFFF); contentColor = Color.White; borderColor = Cyan
        }
        else -> {
            bg = Color.Transparent; contentColor = Color(0xFFCBD5E1); borderColor = Color.Transparent
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(140),
        label = "top-nav-scale",
    )

    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(width = borderW, color = borderColor, shape = RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            tab.label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (active || focused) FontWeight.Black else FontWeight.SemiBold,
            fontFamily = Inter,
            letterSpacing = 0.3.sp,
        )
    }
    @Suppress("UNUSED_EXPRESSION") scale
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
