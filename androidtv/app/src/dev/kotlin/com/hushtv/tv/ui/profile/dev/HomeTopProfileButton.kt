package com.hushtv.tv.ui.profile.dev

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.AvatarCatalog
import com.hushtv.tv.data.HushProfile
import com.hushtv.tv.ui.profile.ProfileGateRegistry
import com.hushtv.tv.ui.screens.clickableWithEnter

private val CYAN = Color(0xFF22D3EE)
private val WHITE = Color(0xFFF8FAFC)
private val MUTED = Color(0xFFCBD5E1)
private val CHIP_BG = Color(0xCC0E1217)

/**
 * v1.44.139-dev — Top-right "Who's watching as ___" avatar button
 * shown on the home screen (Netflix-style).
 *
 * Behaviour
 * ─────────
 *   • Click → opens the "Who's watching?" picker via
 *     [ProfileGateRegistry.current.requestSwitch].
 *   • When focused, expands to show the profile name as a chip
 *     so D-pad users can confirm they've landed on the right
 *     control before pressing Enter.
 *
 * Focus & D-pad
 * ─────────────
 * Two parameters wire the navigation:
 *   • [onDownFromAvatar] — invoked when the user presses D-pad
 *     DOWN. Caller passes a FocusRequester for the first content
 *     row, so DOWN reliably lands on a content card instead of
 *     letting Compose 2D-search guess.
 *   • [focusRequester] — exposed so the home page can request
 *     focus on the avatar (e.g. on first composition, or via
 *     swallow-up-from-top-row).
 *
 * Visibility
 * ──────────
 * Only renders when [activeProfile] is non-null. Hidden on
 * canada / official (no profile system) — the host composable
 * passes null in that case.
 */
@Composable
fun HomeTopProfileButton(
    activeProfile: HushProfile?,
    xtreamUsername: String,
    focusRequester: FocusRequester,
    onDownFromAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeProfile == null) return
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val av = AvatarCatalog.find(activeProfile.avatarId) ?: AvatarCatalog.defaultAvatar()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier
            .padding(end = 24.dp, top = 16.dp),
    ) {
        // Profile-name chip — only visible when the avatar is
        // focused. Saves screen real-estate when it's idle.
        if (focused) {
            Text(
                "Switch profile",
                color = MUTED,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CHIP_BG)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    activeProfile.name,
                    color = WHITE,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
        }

        // The avatar circle itself.
        Box(
            Modifier
                .size(if (focused) 50.dp else 44.dp)
                .clip(CircleShape)
                .background(Color(0xFF0E1217))
                .border(
                    width = if (focused) 3.dp else 1.5.dp,
                    color = if (focused) CYAN else Color(0x33FFFFFF),
                    shape = CircleShape,
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                // v1.44.139-dev — Explicit D-pad DOWN handler.
                // Compose's 2D focus search from a top-corner
                // element is unreliable on TV — sometimes it
                // sends focus to the sidebar instead of the first
                // content row. Routing DOWN explicitly to the
                // page's first focusable lock-fixes that.
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                        onDownFromAvatar()
                        true
                    } else false
                }
                .clickableWithEnter {
                    ProfileGateRegistry.current.requestSwitch(ctx, xtreamUsername)
                },
        ) {
            Image(
                painter = painterResource(id = av.drawableRes),
                contentDescription = "Switch profile — currently ${activeProfile.name}",
                modifier = Modifier.size(if (focused) 50.dp else 44.dp).clip(CircleShape),
            )
        }
    }
}
