package com.hushtv.tv.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

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
