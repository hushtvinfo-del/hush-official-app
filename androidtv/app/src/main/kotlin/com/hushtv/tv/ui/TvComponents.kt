package com.hushtv.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.CyanFocusBg
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.UnfocusedBorder

/**
 * Performance-optimised focus modifier.
 *
 *  • Linear tween (100 ms) — no spring physics → cheaper to compute.
 *  • graphicsLayer scale only (already hardware-accelerated).
 *  • No shadow elevation animation (that was the biggest GPU sink since
 *    elevation shadows re-rasterise every frame while animating).
 *  • 2 dp cyan border on focus, transparent otherwise — cheap.
 */
fun Modifier.tvFocusable(
    scaleOnFocus: Float = 1.06f,
    shape: Shape = RoundedCornerShape(12.dp),
    fillOnFocus: Boolean = true,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleOnFocus else 1f,
        animationSpec = tween(100),
        label = "tv-focus-scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
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
