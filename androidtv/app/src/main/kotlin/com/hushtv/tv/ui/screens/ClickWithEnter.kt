package com.hushtv.tv.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * Adds a tap/Enter/D-pad-center handler to a Modifier.
 * D-pad Enter on Android TV arrives as KEYCODE_DPAD_CENTER which Compose maps to Key.Enter/Key.DirectionCenter.
 */
@Composable
fun Modifier.clickableWithEnter(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyUp &&
                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
            ) {
                onClick(); true
            } else false
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
}

/**
 * Like [clickableWithEnter] but ALSO accepts a long-press handler
 * for touch gestures + TV remote alternatives:
 *   • Holding DPAD Center / Enter for ≥ 700 ms triggers onLongPress
 *     (we measure between KeyDown and KeyUp events).
 *   • A short single press still fires onClick (≤ 700 ms).
 *   • Key.Menu (contextual menu button) immediately fires onLongPress
 *     when pressed — quick affordance for remotes that have it.
 *   • Touch long-press (Android Compose default) fires onLongPress.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.clickableWithEnterAndLongPress(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val keyDownMs = remember { kotlin.collections.ArrayDeque<Long>() }
    return this
        .onKeyEvent { e ->
            val isCenter = e.key == Key.Enter ||
                e.key == Key.DirectionCenter ||
                e.key == Key.NumPadEnter
            when {
                isCenter && e.type == KeyEventType.KeyDown -> {
                    if (keyDownMs.isEmpty()) {
                        keyDownMs.addLast(System.currentTimeMillis())
                    }
                    true
                }
                isCenter && e.type == KeyEventType.KeyUp -> {
                    val downMs = keyDownMs.removeLastOrNull()
                    val held = if (downMs != null) {
                        System.currentTimeMillis() - downMs
                    } else 0L
                    if (held >= 700L) onLongPress() else onClick()
                    true
                }
                e.type == KeyEventType.KeyUp && e.key == Key.Menu -> {
                    onLongPress(); true
                }
                else -> false
            }
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongPress,
        )
}
