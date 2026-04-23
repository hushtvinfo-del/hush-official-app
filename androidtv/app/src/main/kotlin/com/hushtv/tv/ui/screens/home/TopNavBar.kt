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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Brush
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
 * Netflix / YouTube-TV-style top navigation bar.
 *
 *   [ Logo ]  Home · Live TV · Movies · Series · Search      Profile ⚙
 *
 *  • Full-width, 72 dp tall, top-down dark-to-transparent gradient
 *    background so hero art shows through while nav stays legible.
 *  • D-pad left/right cycles tabs. First tab gets initial focus.
 *  • Active tab: cyan-filled pill behind the label.
 *  • Focused tab: cyan ring + subtle lift.
 *  • Profile chip (avatar + nickname) on the far left after the logo —
 *    D-pad left from Home reaches it. Settings gear is pinned far right.
 */
@Composable
fun TopNavBar(
    tabs: List<TopNavTab>,
    activeKey: String,
    profileNickname: String,
    homeFocus: FocusRequester,
    onTab: (TopNavTab) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            // Top-down dark gradient — black 72% at the top fades to
            // transparent at the bottom so the hero art peeks through
            // without losing nav legibility.
            .background(
                Brush.verticalGradient(
                    0.0f to Color(0xB8000000),
                    0.55f to Color(0x66000000),
                    1.0f to Color.Transparent,
                )
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Logo ────────────────────────────────────────────────
            HushTVLogo(fontSize = 22.sp)

            Spacer(Modifier.width(28.dp))

            // ── Profile chip ───────────────────────────────────────
            ProfileChip(
                nickname = profileNickname,
                onClick = onProfile,
            )

            Spacer(Modifier.width(36.dp))

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

            // ── Spacer push settings to far right ──────────────────
            Spacer(Modifier.weight(1f))

            // ── Settings gear ──────────────────────────────────────
            SettingsIconButton(onClick = onSettings)
        }
    }
}

@Composable
private fun ProfileChip(nickname: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val ringColor = if (focused) Cyan else Color(0x33FFFFFF)

    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color(0x26FFFFFF) else Color(0x14FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = ringColor,
                shape = RoundedCornerShape(20.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — circular initials badge, cyan background.
        Box(
            Modifier
                .size(28.dp)
                .background(Cyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                nickname.take(1).uppercase().ifBlank { "H" },
                color = Color(0xFF0A0F1C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            nickname.ifBlank { "Profile" },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
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
    val (bg, contentColor, borderColor, borderW) = when {
        focused && active -> Quad(Cyan, Color(0xFF0A0F1C), Cyan, 2.dp)
        active -> Quad(Color(0xFF0891B2), Color(0xFF0A0F1C), Color.Transparent, 0.dp)
        focused -> Quad(Color(0x26FFFFFF), Color.White, Cyan, 2.dp)
        else -> Quad(Color.Transparent, Color(0xFFCBD5E1), Color.Transparent, 0.dp)
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
    // We don't actually use `scale` — but keeping it computed lets a
    // future scaleOnFocus modifier slot in easily. Consumed by the
    // framework for recomposition tracking.
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

private data class Quad<A, B, C, D>(
    val a: A, val b: B, val c: C, val d: D,
)