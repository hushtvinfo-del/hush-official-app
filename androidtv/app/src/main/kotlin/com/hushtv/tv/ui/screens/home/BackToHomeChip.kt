package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary

/**
 * Back-to-Home chip used at the top-left of every non-Home hub.
 *
 * ⚠ DISABLED in v1.43.38 by user request — the chip cluttered the
 *   top-left corner on every detail screen and the device BACK
 *   button already does the same job (`nav.popBackStack()`), so
 *   the chip was redundant.
 *
 * The composable is intentionally kept as a no-op stub instead of
 * being deleted: all 11 call-sites still compile, and re-enabling
 * the visual is a single early-return removal away.
 */
@Composable
fun BackToHomeChip(
    nav: NavController,
    playlistId: String,
    focusRequester: FocusRequester? = null,
) {
    // No-op — the back chip is hidden globally.
    @Suppress("UNUSED_PARAMETER") val unused = Triple(nav, playlistId, focusRequester)
    return
    // -- The original render (kept for reference, never executed) --
    @Suppress("UNREACHABLE_CODE")
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val onBack = {
        nav.navigate("menu/$playlistId") {
            popUpTo("menu/$playlistId") { inclusive = false }
            launchSingleTop = true
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(
                if (focused) Cyan.copy(alpha = 0.22f) else Color(0x14FFFFFF),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x33FFFFFF),
                shape = shape,
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { ev ->
                // Hardware/IR remote BACK key on TV boxes — also
                // route to Home rather than letting it pop the
                // back stack to whatever was before.
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Back) {
                    onBack(); true
                } else false
            }
            .clickableWithEnter(onBack)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            Icons.Filled.Home,
            contentDescription = null,
            tint = if (focused) Cyan else TextPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Home",
            color = if (focused) Cyan else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
}
