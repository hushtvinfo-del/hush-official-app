package com.hushtv.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.CyanGlow08
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.TextMuted
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cinematic splash per design-spec section 5.
 *  0–300 ms    pure black
 *  300–1000    "hush" fades in + scales 0.85 → 1.0 (FastOutSlowIn)
 *  450–1000    "tv." appears (150 ms after "hush")
 *  1000–2200   cyan 2 dp progress bar sweeps L→R
 *  1800–2500   tagline fades in
 *  2500–2900   everything fades to black, then onDone()
 */
@Composable
fun HushSplashScreen(onDone: () -> Unit) {
    val hushAlpha   = remember { Animatable(0f) }
    val tvAlpha     = remember { Animatable(0f) }
    val logoScale   = remember { Animatable(0.85f) }
    val barProgress = remember { Animatable(0f) }
    val tagAlpha    = remember { Animatable(0f) }
    val rootAlpha   = remember { Animatable(1f) }

    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Phase 1: black hold
        delay(300)

        // Phase 2: "hush" fade + scale (600 ms)
        coroutineScope {
            launch {
                hushAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            }
            launch {
                logoScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            }
            launch {
                delay(150)
                tvAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            }
        }

        // Phase 3: progress bar sweep (1200 ms)
        barProgress.animateTo(1f, tween(1200, easing = LinearEasing))

        // Phase 4: tagline fade
        tagAlpha.animateTo(1f, tween(400))
        delay(300)

        // Phase 5: fade everything to black
        rootAlpha.animateTo(0f, tween(400))

        done = true
        onDone()
    }

    if (done) return

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .alpha(rootAlpha.value),
        contentAlignment = Alignment.Center,
    ) {
        // Optional soft cyan radial glow behind the logo
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyanGlow08, Color.Transparent),
                        radius = 800f,
                    )
                )
        )

        // Wordmark column — centered
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(logoScale.value),
            ) {
                Text(
                    "hush",
                    color = Color.White,
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                    letterSpacing = (-2.7).sp,
                    modifier = Modifier.alpha(hushAlpha.value),
                )
                Text(
                    "tv.",
                    color = Cyan,
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                    letterSpacing = (-2.7).sp,
                    modifier = Modifier.alpha(tvAlpha.value),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Your Stream. Your Way.",
                color = TextMuted,
                fontSize = 14.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.8.sp,
                modifier = Modifier.alpha(tagAlpha.value),
            )
        }

        // Cyan 2 dp progress bar pinned to bottom
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(barProgress.value)
                .height(2.dp)
                .background(Cyan),
        )
    }
}
