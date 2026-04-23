package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
    daysLeft: Long? = null,
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

            // ── Subscription expiry badge ──────────────────────────
            if (daysLeft != null) {
                ExpiryBadge(daysLeft = daysLeft)
                Spacer(Modifier.width(14.dp))
            }

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
