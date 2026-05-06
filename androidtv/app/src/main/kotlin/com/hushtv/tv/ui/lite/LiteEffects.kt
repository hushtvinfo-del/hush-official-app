package com.hushtv.tv.ui.lite

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.hushtv.tv.data.LocalIsLiteMode
import kotlinx.coroutines.delay

/**
 * v1.44.24 — Lite-aware infinite-repeating Float animator.
 *
 * Drop-in replacement for the
 *   rememberInfiniteTransition() + transition.animateFloat(...)
 * pattern. Used by every Ken-Burns hero layer, the streaming-service
 * watermark pulse, the splash ring spinner, and similar.
 *
 * In LITE mode this returns a static [liteValue] — no animator
 * allocation, no per-frame recomposition, no `graphicsLayer` work.
 * In PRO mode it spins up the same `infiniteRepeatable(tween(...))`
 * the original code did, with bit-for-bit identical params.
 */
@Composable
fun rememberLiteAwareFloat(
    label: String,
    liteValue: Float,
    initialValue: Float,
    targetValue: Float,
    durationMs: Int,
    easing: Easing = LinearEasing,
    repeatMode: RepeatMode = RepeatMode.Restart,
): State<Float> {
    if (LocalIsLiteMode.current) {
        return remember(liteValue) { mutableFloatStateOf(liteValue) }
    }
    val transition = rememberInfiniteTransition(label = "$label-trans")
    return transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = easing),
            repeatMode = repeatMode,
        ),
        label = label,
    )
}

/** Convenience wrapper for the Ken-Burns scale pattern (the
 *  most common case). Lite stays at the original `initialValue`
 *  so the hero is just a static slightly-zoomed image. */
@Composable
fun rememberKenBurnsScale(
    label: String,
    initialValue: Float = 1.06f,
    targetValue: Float = 1.12f,
    durationMs: Int = 22_000,
): State<Float> = rememberLiteAwareFloat(
    label = label,
    liteValue = initialValue,
    initialValue = initialValue,
    targetValue = targetValue,
    durationMs = durationMs,
)

/**
 * v1.44.24 — Lite-aware auto-cycling index.
 *
 * Replaces the pattern
 *   var idx by remember { mutableIntStateOf(0) }
 *   LaunchedEffect(items) {
 *       while (true) { delay(8_000); idx = (idx + 1) % items.size }
 *   }
 * In Lite mode the `LaunchedEffect`'s body short-circuits so the
 * hero stays on whatever item is at index 0 — no timer, no
 * crossfade churn. Pro behaviour unchanged.
 */
@Composable
fun rememberAutoCycleIndex(
    count: Int,
    intervalMs: Long = 8_000L,
): State<Int> {
    val isLite = LocalIsLiteMode.current
    val state = remember { mutableIntStateOf(0) }
    LaunchedEffect(count, intervalMs, isLite) {
        if (isLite || count <= 1) return@LaunchedEffect
        while (true) {
            delay(intervalMs)
            state.intValue = (state.intValue + 1) % count
        }
    }
    return state
}
