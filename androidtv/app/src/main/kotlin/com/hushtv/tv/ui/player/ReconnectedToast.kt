package com.hushtv.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Soft "we just reconnected" toast that surfaces for 2.5 s after
 * [PlayerBuilder.attachAutoReconnect] reports a successful recovery.
 *
 * Why: when the player silently heals from a CDN hiccup or a brief
 * Wi-Fi drop, the user has no idea why playback paused for a moment.
 * Without a visible signal, that moment of "is the app broken?"
 * panic generates support tickets even when everything worked
 * exactly as designed. A 2-second confirmation chip turns the
 * silent self-heal into a small brand moment instead.
 *
 * Design choices
 *   • Pill shape, glass dark background, soft green accent dot.
 *     Green is the universal "OK / online" signal — different
 *     enough from the cyan (Bell-Blue) brand colour that users
 *     don't confuse it with a focus indicator.
 *   • Anchored top-center of the player surface so it never
 *     overlaps the standard playback controls (which live at the
 *     bottom) or the channel-info HUD (top-left).
 *   • Pure copy: "Back online" — no apology, no jargon, no
 *     "buffering" or "reconnect" technical language. Matches the
 *     warmer phrasing direction the user set with the
 *     "Starting hang tight…" buffer text.
 *   • Auto-dismiss after [VISIBLE_MS]; consumes its own state via
 *     a remember-d MutableState so callers only need to "show"
 *     it — no timeout management at the call site.
 */
@Composable
fun ReconnectedToast(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onHide: () -> Unit,
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(VISIBLE_MS)
            onHide()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) +
            slideInVertically(tween(260)) { -it / 2 },
        exit = fadeOut(tween(220)) +
            slideOutVertically(tween(260)) { -it / 2 },
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(999.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(Color(0xCC0B1220))
                .border(1.dp, Color(0x554ADE80), shape)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4ADE80)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Back online",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * Convenience hoist — gives the caller a single mutable state plus
 * a "fire" lambda. Wire `fire` into `attachAutoReconnect(...,
 * onRecovered = recovered::fire, ...)` and pass `recovered.visible`
 * into [ReconnectedToast].
 */
class RecoveryToastState {
    val visible: MutableState<Boolean> = mutableStateOf(false)
    fun fire() { visible.value = true }
    fun hide() { visible.value = false }
}

@Composable
fun rememberRecoveryToastState(): RecoveryToastState =
    remember { RecoveryToastState() }

/** 2.5 s on screen — long enough to read, short enough to feel
 *  like the self-heal it represents and not a persistent banner. */
private const val VISIBLE_MS = 2_500L
