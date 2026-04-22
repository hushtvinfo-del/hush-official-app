package com.hushtv.tv.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
 * Design-spec focus state for every D-pad-reachable element.
 *
 * Spec (from design-spec page):
 *  • scale(1.06 → 1.08) on focus
 *  • 2dp cyan border
 *  • rgba(6,182,212,0.15) fill
 *  • outer glow shadow (approximated via elevation shadow)
 *  • 150 ms transform-only transition (hardware-accelerated)
 *  • unfocused: 2dp rgba(255,255,255,0.08) border, transparent fill
 */
fun Modifier.tvFocusable(
    scaleOnFocus: Float = 1.06f,
    shape: Shape = RoundedCornerShape(12.dp),
    /** If true, paints the cyan fill inside the focus border. Set to false for
     *  full-bleed artwork (posters, live cards) that already have an image. */
    fillOnFocus: Boolean = true,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleOnFocus else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tv-focus-scale",
    )
    val shadowElev by animateFloatAsState(
        targetValue = if (focused) 20f else 0f,
        animationSpec = tween(150),
        label = "tv-focus-shadow",
    )
    this
        .scale(scale)
        .shadow(
            elevation = shadowElev.dp,
            shape = shape,
            ambientColor = Cyan,
            spotColor = Cyan,
        )
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
 * Inter Black 900, letter-spacing -0.03em.
 * @param fontSize any scalable size; tracking scales automatically.
 */
@Composable
fun HushTVLogo(
    fontSize: TextUnit = 48.sp,
    modifier: Modifier = Modifier,
) {
    // -0.03em ≈ -3% of em size. sp unit for letter-spacing works visually close.
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
