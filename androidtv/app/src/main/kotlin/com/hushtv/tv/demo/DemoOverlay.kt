package com.hushtv.tv.demo

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top-right "REC" pill rendered above the entire app while a manual
 * screen recording is in flight. Confirms to the user that the
 * recorder is live — they then navigate the app at their own pace
 * and hit Stop in Settings (or the notification) when done.
 */
@Composable
fun DemoRecorderOverlay() {
    val phase by DemoController.phase.collectAsState()
    val visible = phase != DemoController.Phase.Idle

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC0B0F14))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                PulsingDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (phase) {
                        DemoController.Phase.Preparing -> "STARTING"
                        DemoController.Phase.Recording -> "REC"
                        DemoController.Phase.Stopping  -> "SAVING"
                        DemoController.Phase.Idle      -> ""
                    },
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val t = rememberInfiniteTransition(label = "rec-dot")
    val a by t.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-dot-alpha",
    )
    Box(
        Modifier
            .size(10.dp)
            .alpha(a)
            .clip(CircleShape)
            .background(Color(0xFFEF4444))
    )
}
