package com.hushtv.tv.ui.canada

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.BuildConfig
import kotlinx.coroutines.delay

/**
 * v1.44.94 — Canada free-trial countdown pill.
 *
 * Renders a small "Free trial · 2d 4h" badge when the
 * [CanadaTrialState.expiresAtMs] flow is non-null. Self-ticking each
 * minute (or every 30 s when < 1 h remains) to keep the readout fresh
 * without re-polling the network.
 *
 * No-op on non-canada flavors so this can be dropped into any
 * shared composable.
 */
@Composable
fun CanadaTrialBadge(
    modifier: Modifier = Modifier,
) {
    if (BuildConfig.UPDATE_CHANNEL != "canada") return
    val expiresAt by CanadaTrialState.expiresAtMs.collectAsState()
    val deadline = expiresAt ?: return

    // Local clock — re-evaluated by a self-driving LaunchedEffect so
    // the displayed remaining time updates without depending on the
    // gate poller.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val remaining = deadline - nowMs
            // Tighten the tick rate as we approach zero so the visible
            // countdown never lags by more than ~30 s near the deadline.
            val sleepMs = when {
                remaining <= 0L            -> break  // expired
                remaining < 60 * 60 * 1000 -> 30_000L
                else                       -> 60_000L
            }
            delay(sleepMs)
        }
    }

    val remaining = deadline - nowMs
    if (remaining <= 0L) return

    val label = formatTrialRemaining(remaining)
    val isUrgent = remaining < 6L * 60 * 60 * 1000  // < 6 h → red, pulse

    val bg = if (isUrgent) Color(0xCC7F1D1D) else Color(0xCC0E3B47)
    val border = if (isUrgent) Color(0xFFEF4444) else Color(0xFF22D3EE)
    val text = if (isUrgent) Color(0xFFFCA5A5) else Color(0xFF67E8F9)

    val pulseAlpha = if (isUrgent) {
        val t = rememberInfiniteTransition(label = "trial-pulse")
        val a by t.animateFloat(
            initialValue = 0.65f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "trial-pulse-alpha",
        )
        a
    } else 1f

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .alpha(pulseAlpha)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                "FREE TRIAL",
                color = text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Full-screen overlay variant — anchors the badge to the top-right
 * regardless of which home layout (top-bar or sidebar) is active.
 * Composed once at the activity level above the entire app tree.
 */
@Composable
fun CanadaTrialBadgeOverlay() {
    if (BuildConfig.UPDATE_CHANNEL != "canada") return
    Box(Modifier.fillMaxSize()) {
        CanadaTrialBadge(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 24.dp),
        )
    }
}

/** "1d 4h", "23h 5m", "47m 12s" — chooses the largest two non-zero units. */
private fun formatTrialRemaining(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val d = totalSec / 86_400
    val h = (totalSec % 86_400) / 3_600
    val m = (totalSec % 3_600) / 60
    val s = totalSec % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else  -> "${s}s"
    }
}
