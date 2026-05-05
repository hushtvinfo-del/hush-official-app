package com.hushtv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.CyanFocusBg
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.UnfocusedBorder

/**
 * Focus modifier — flat performance-first edition (v1.43.85).
 *
 * Removed (per user request, "remove the magnify + glow throughout
 * the whole app — Fire Sticks and lower devices feel sluggish, and
 * the magnify keeps clipping cards off the screen edge"):
 *  • `graphicsLayer { scaleX/scaleY = 1.06f }` — even though
 *    graphicsLayer is hardware-accelerated, every animating card
 *    triggers a layer composition pass and a redraw of the
 *    surrounding rail. On Fire OS / lower-end Google TV hardware
 *    this stacks up across visible cards and tanks the scroll
 *    framerate.
 *  • `animateFloatAsState` tween — no longer needed once scale
 *    is gone. Pure border + background flips are constant-time.
 *
 * Kept:
 *  • 2 dp cyan border on focus → transparent unfocused border
 *    (no layout shift — width unchanged).
 *  • Cyan-tint background fill on focus (cheap composition).
 *  • Same external API (`scaleOnFocus`, `shape`, `fillOnFocus`)
 *    so call-sites compile unchanged. The `scaleOnFocus` param is
 *    kept for source-level compatibility but **ignored**.
 *
 * Why we kept the border: it's the single cheapest, most legible
 * focus indicator on a TV. No depth-of-field shifts, no GPU layer,
 * no animation timeline.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.tvFocusable(
    scaleOnFocus: Float = 1.0f,
    shape: Shape = RoundedCornerShape(12.dp),
    fillOnFocus: Boolean = true,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .background(
            color = if (focused && fillOnFocus) CyanFocusBg else Color.Transparent,
            shape = shape,
        )
        .border(
            width = 2.dp,
            color = if (focused) Cyan else UnfocusedBorder,
            shape = shape,
        )
        .onFocusChanged { focused = it.isFocused }
        .focusable()
}

/**
 * "hushtv." wordmark — white "hush" + cyan "tv."
 */
@Composable
fun HushTVLogo(
    fontSize: TextUnit = 48.sp,
    modifier: Modifier = Modifier,
) {
    val tracking = (fontSize.value * -0.03f).sp
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "hush",
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            fontFamily = Inter,
            letterSpacing = tracking,
        )
        Text(
            "tv.",
            color = Cyan,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            fontFamily = Inter,
            letterSpacing = tracking,
        )
    }
}
