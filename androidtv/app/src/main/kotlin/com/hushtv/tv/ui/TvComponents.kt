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
 * ╔═══════════════════════════════════════════════════════════════╗
 * ║  ⚠️  RAIL-RIGHT-EXIT FOCUS RULE — DO NOT REMOVE OR REWORK     ║
 * ║      WITHOUT READING THIS FIRST. We spent multiple painful   ║
 * ║      iterations finding this bug.                            ║
 * ║                                                              ║
 * ║  WHEN ADDING A NEW HOME ROW (or any LEFT-rail-exit target),  ║
 * ║  the first card MUST follow this exact pattern:              ║
 * ║                                                              ║
 * ║    @Composable                                               ║
 * ║    private fun MyCardView(                                   ║
 * ║        ...                                                   ║
 * ║        focusRequester: FocusRequester? = null,  // ← REQUIRED║
 * ║    ) {                                                       ║
 * ║        Box(                                                  ║
 * ║            Modifier                                          ║
 * ║                .width(...).height(...)                       ║
 * ║                .onFocusChanged { focused = it.isFocused }    ║
 * ║                .tvFocusable(                                 ║
 * ║                    shape = cardShape,                        ║
 * ║                    focusRequester = focusRequester, // ← KEY ║
 * ║                )                                             ║
 * ║                .clickableWithEnter(onClick)                  ║
 * ║                ...                                           ║
 * ║        )                                                     ║
 * ║    }                                                         ║
 * ║                                                              ║
 * ║  And at the call-site:                                       ║
 * ║                                                              ║
 * ║    items.forEachIndexed { idx, item ->                       ║
 * ║        MyCardView(                                           ║
 * ║            ...                                               ║
 * ║            focusRequester = if (idx == 0) firstItemFocus     ║
 * ║                              else null,                      ║
 * ║        )                                                     ║
 * ║    }                                                         ║
 * ║                                                              ║
 * ║  And in TVMainMenuScreen, register a `firstXxxFocus`         ║
 * ║  FocusRequester, route it through the page, AND add it to    ║
 * ║  BOTH the `LaunchedEffect(currentPage)` auto-focus table AND ║
 * ║  the `LaunchedEffect(railExitTick)` rail-exit table.         ║
 * ║                                                              ║
 * ║  DO NOT:                                                     ║
 * ║   • Place `Modifier.focusRequester(req)` BEFORE `tvFocusable`║
 * ║     in the chain — the requester would attach to the wrong   ║
 * ║     focusable (we proved this with screenshots in v1.43.94+).║
 * ║   • Add a redundant `.focusable()` after `tvFocusable` —     ║
 * ║     `tvFocusable` already adds one. Two focusables create    ║
 * ║     ambiguous focus targets.                                 ║
 * ║   • Call `firstFocus.requestFocus()` SYNCHRONOUSLY from a    ║
 * ║     rail-RIGHT key handler — it races the rail-collapse      ║
 * ║     animation. Use the `railExitTick` deferred path with a   ║
 * ║     320 ms LaunchedEffect delay (see TVMainMenuScreen.kt).   ║
 * ║   • Add `.focusRestorer()` at any OUTER focus-group level    ║
 * ║     that crosses pages (e.g. `tvHubContentFocus`). It saves a║
 * ║     stale pivot when AnimatedContent unmounts a page.        ║
 * ╚═══════════════════════════════════════════════════════════════╝
 *
 * What changed in v1.43.98:
 *   • Optional `focusRequester` parameter — when supplied, the
 *     requester is wired DIRECTLY to the inner `.focusable()` so
 *     `requestFocus()` lands on the EXACT same focusable that
 *     draws the cyan ring.
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
