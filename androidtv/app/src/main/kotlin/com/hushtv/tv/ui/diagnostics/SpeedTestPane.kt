package com.hushtv.tv.ui.diagnostics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.SpeedTester
import com.hushtv.tv.data.SpeedTier
import com.hushtv.tv.ui.theme.Cyan
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Reusable speed-test pane used by both TV and Mobile shells.
 *
 * Layout:
 *   ┌─────────────────────────────────┐
 *   │   [   speedometer arc + dial   ] │
 *   │                                  │
 *   │      125.4 Mbps     ⚡           │  ← live readout
 *   │      EXCELLENT                   │  ← tier badge
 *   │      Smooth 4K …                 │  ← verdict
 *   │                                  │
 *   │  [tier legend strip — 4 chips]   │
 *   │                                  │
 *   │     [   Run again   ]            │
 *   └─────────────────────────────────┘
 *
 * Animation: live `current` reading drives the dial via spring; final
 * `peak` reading drives the headline number once the test completes.
 */
@Composable
fun SpeedTestPane(modifier: Modifier = Modifier) {
    var live by remember { mutableStateOf(0f) }
    var peak by remember { mutableStateOf(0f) }
    var running by remember { mutableStateOf(false) }
    var hasRun by remember { mutableStateOf(false) }
    var runTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(runTrigger) {
        if (runTrigger == 0) return@LaunchedEffect
        running = true
        live = 0f
        peak = 0f
        peak = SpeedTester.run { mbps ->
            live = mbps
            if (mbps > peak) peak = mbps
        }
        live = peak
        running = false
        hasRun = true
    }

    val displayed = if (running) live else peak
    val tier = SpeedTier.of(displayed)
    val animatedSpeed by animateFloatAsState(
        targetValue = displayed,
        animationSpec = tween(durationMillis = 400),
        label = "speed",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .aspectRatio(1.6f),
            contentAlignment = Alignment.Center,
        ) {
            SpeedDial(
                speedMbps = animatedSpeed,
                tierColor = tier.color,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Headline reading — big, hero typography.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format(
                    if (animatedSpeed < 10f) "%.1f" else "%.0f",
                    animatedSpeed,
                ),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Mbps",
                color = Color(0xFF94A3B8),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 9.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Tier badge + verdict.
        if (running) {
            Text(
                "Testing your connection…",
                color = Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
        } else if (hasRun) {
            Box(
                Modifier
                    .background(tier.color.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                    .border(1.dp, tier.color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    tier.label.uppercase(),
                    color = tier.color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tier.verdict,
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "Tap RUN to measure your download speed.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(20.dp))

        // 4-chip legend strip.
        Row(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpeedTier.values().forEach { t ->
                val active = hasRun && tier == t
                Box(
                    Modifier
                        .weight(1f)
                        .background(
                            if (active) t.color.copy(alpha = 0.22f)
                            else Color(0xFF111827),
                            RoundedCornerShape(8.dp),
                        )
                        .border(
                            if (active) 1.dp else 0.dp,
                            if (active) t.color else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(
                            t.range(),
                            color = if (active) t.color else Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.label,
                            color = if (active) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // CTA button — Run / Run again. D-pad-friendly: focusable with
        // visible focus ring; touch-friendly: large hit area.
        var focused by remember { mutableStateOf(false) }
        Row(
            Modifier
                .clip(CircleShape)
                .background(
                    if (focused || running) Cyan.copy(alpha = 0.18f) else Cyan.copy(alpha = 0.10f),
                    CircleShape,
                )
                .border(
                    if (focused) 2.dp else 1.dp,
                    Cyan, CircleShape,
                )
                .clickable(enabled = !running) {
                    runTrigger++
                }
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (hasRun || running) Icons.Default.Refresh else Icons.Default.Speed,
                null,
                tint = Cyan,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (running) "Testing…"
                else if (hasRun) "Run again"
                else "Run speed test",
                color = Cyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

private fun SpeedTier.range(): String = when (this) {
    SpeedTier.POOR -> "1–10"
    SpeedTier.FAIR -> "10–25"
    SpeedTier.GOOD -> "25–50"
    SpeedTier.EXCELLENT -> "50+"
}

/**
 * Half-circle gauge with a thin trail, a thicker progress arc, and a
 * needle. Calibrated 0..150 Mbps so the dial doesn't peg too easily on
 * gigabit fibre but still gives meaningful resolution at the typical
 * 25-100 Mbps streaming-relevant range.
 */
@Composable
private fun SpeedDial(
    speedMbps: Float,
    tierColor: Color,
    modifier: Modifier = Modifier,
) {
    val DIAL_MAX = 150f
    val frac = (speedMbps / DIAL_MAX).coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.92f
        val radius = min(w * 0.45f, h * 0.85f)
        val strokeBg = 14.dp.toPx()
        val strokeFg = 18.dp.toPx()

        // Background trail — full half-circle, dim.
        drawArc(
            color = Color(0xFF1E293B),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = strokeBg, cap = StrokeCap.Round),
        )

        // Progress arc — gradient from cyan → tier color so it has a
        // sense of "lighting up" even on slow connections.
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Cyan, tierColor),
                center = Offset(cx, cy),
            ),
            startAngle = 180f,
            sweepAngle = 180f * frac,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = strokeFg, cap = StrokeCap.Round),
        )

        // Tick marks at tier boundaries (10/25/50/100 Mbps).
        listOf(10f, 25f, 50f, 100f).forEach { mark ->
            val tFrac = (mark / DIAL_MAX).coerceIn(0f, 1f)
            val angle = Math.toRadians((180f + 180f * tFrac).toDouble())
            val inner = radius - strokeBg * 0.6f
            val outer = radius + strokeBg * 0.2f
            val x1 = cx + inner * cos(angle).toFloat()
            val y1 = cy + inner * sin(angle).toFloat()
            val x2 = cx + outer * cos(angle).toFloat()
            val y2 = cy + outer * sin(angle).toFloat()
            drawLine(
                color = Color(0xFF475569),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Needle.
        val needleAngle = Math.toRadians((180f + 180f * frac).toDouble())
        val needleLen = radius - strokeBg * 0.4f
        val nx = cx + needleLen * cos(needleAngle).toFloat()
        val ny = cy + needleLen * sin(needleAngle).toFloat()
        drawLine(
            color = tierColor,
            start = Offset(cx, cy),
            end = Offset(nx, ny),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Hub.
        drawCircle(
            color = Color(0xFF0F172A),
            radius = 10.dp.toPx(),
            center = Offset(cx, cy),
        )
        drawCircle(
            color = tierColor,
            radius = 6.dp.toPx(),
            center = Offset(cx, cy),
        )
    }
}
