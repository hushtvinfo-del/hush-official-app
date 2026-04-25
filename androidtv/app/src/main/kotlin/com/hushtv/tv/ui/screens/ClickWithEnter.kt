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
 * for touch gestures. Long-press on TV (DPAD Center hold) maps to
 * Key.DirectionCenter held, which we approximate via the Menu key
 * — TV remotes that have a contextual menu button send Key.Menu on
 * press, which is the closest analogue to a long-press on touch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.clickableWithEnterAndLongPress(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyUp &&
                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
            ) {
                onClick(); true
            } else if (e.type == KeyEventType.KeyUp && e.key == Key.Menu) {
                onLongPress(); true
            } else false
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongPress,
        )
}
