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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 * Focus modifier — v1.43.98 edition.
 *
 * What changed in v1.43.98:
 *   • Optional `focusRequester` parameter — when supplied, the
 *     requester is wired DIRECTLY to the inner `.focusable()` so
 *     `requestFocus()` lands on the EXACT same focusable that
 *     draws the cyan ring. Earlier attempts wrapped the
 *     focusRequester on an OUTER Modifier (cardBase) which bound
 *     it to a *different* focusable than the one tvFocusable
 *     itself adds — focus landed somewhere invisible. This is the
 *     root cause of "Discovery works but other rows don't" because
 *     Discovery used the focusGroup-wrapper pattern that happened
 *     to dodge the bug.
 *
 * Removed in earlier builds:
 *   • Scale on focus + shadow glow → tanked Fire Stick framerate.
 *
 * Kept:
 *   • 2 dp cyan border on focus → transparent unfocused border
 *     (no layout shift).
 *   • Cyan-tint background fill on focus.
 *   • Same external API (`scaleOnFocus`, `shape`, `fillOnFocus`).
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.tvFocusable(
    scaleOnFocus: Float = 1.0f,
    shape: Shape = RoundedCornerShape(12.dp),
    fillOnFocus: Boolean = true,
    focusRequester: FocusRequester? = null,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val base = this
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
    // requester goes DIRECTLY before the .focusable() it controls so
    // `requestFocus()` lands on the focusable that updates `focused`
    // and draws the cyan ring. No wrapper, no outer focusable, no
    // ambiguity.
    val withRequester = if (focusRequester != null)
        base.focusRequester(focusRequester) else base
    withRequester.focusable()
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
