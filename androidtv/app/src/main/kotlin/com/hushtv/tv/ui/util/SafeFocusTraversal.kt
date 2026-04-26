@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Safe replacement for `Modifier.focusProperties { up = … }`.
 *
 * Why this exists
 * ───────────────
 * Compose's declarative `focusProperties` block resolves its target
 * `FocusRequester` synchronously inside `dispatchKeyEvent` via
 * `FocusRequester.findFocusTargetNode`. When the target hasn't been
 * attached to any composable yet (LazyColumn item not laid out, list
 * empty during a category switch, panel hidden, etc.) it throws
 *   java.lang.IllegalStateException: FocusRequester is not initialized
 * and crashes the process.
 *
 * This helper does the same thing imperatively via `onPreviewKeyEvent`
 * + `runCatching { target.requestFocus() }`. When the target is
 * attached we consume the key (identical behavior to the declarative
 * form). When it isn't, we return `false` and let Compose's default
 * 2D focus search take over instead of crashing.
 *
 * Usage
 * ─────
 *     Modifier.safeFocusTraversal(
 *         onDown = firstGridFocus,
 *         onRight = sidebarFirstFocus,
 *     )
 *
 * Pass only the directions you care about; null arguments are skipped
 * and Compose's default focus search handles them.
 *
 * One thing this does NOT replace: `focusProperties { … = X }` where
 * X is GUARANTEED to be attached (e.g. siblings inside the same
 * conditional `if (showControls) { … }` block). For those, the
 * declarative form is fine and a touch cheaper. Prefer this helper
 * any time the target is in a LazyColumn / LazyVerticalGrid / a
 * conditionally-rendered panel.
 */
fun Modifier.safeFocusTraversal(
    onUp: FocusRequester? = null,
    onDown: FocusRequester? = null,
    onLeft: FocusRequester? = null,
    onRight: FocusRequester? = null,
): Modifier = this.onPreviewKeyEvent { ev ->
    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val target = when (ev.key) {
        Key.DirectionUp -> onUp
        Key.DirectionDown -> onDown
        Key.DirectionLeft -> onLeft
        Key.DirectionRight -> onRight
        else -> null
    } ?: return@onPreviewKeyEvent false
    runCatching { target.requestFocus() }.isSuccess
}
