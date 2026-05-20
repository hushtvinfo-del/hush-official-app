package com.hushtv.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.data.NextEpisodeContext
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Bottom-right "Play next episode" card (Netflix-style).
 *
 * Shows the upcoming episode's still + S{N}E{N} label + title + runtime
 * + plot snippet, plus a 10-second countdown that auto-fires the
 * "Play next episode" action unless the user picks "Stay on this one"
 * or BACKs out.
 *
 * Why this is a SEPARATE composable (not inline in TVPlayerScreen):
 *   • Keeps TVPlayerScreen's 2k-line file from growing further.
 *   • Owns its own focusRequester + initial focus claim. The parent
 *     player short-circuits its onKeyEvent while we're visible (see
 *     `nextEpisodeCardOpen` gate in TVPlayerScreen.kt) so D-pad and
 *     Enter route here naturally — no background channel-zap, no
 *     accidental play/pause, no fighting the underlying surface.
 *   • Encapsulates the 10-second auto-advance countdown — caller just
 *     wires `onPlayNext` and `onStay` callbacks.
 *
 * Auto-advance contract:
 *   The countdown fires `onPlayNext()` itself when it reaches zero.
 *   The parent can also still call `onPlayNext()` directly (e.g. if
 *   playback reaches duration before the user chose). If the user
 *   picks "Stay on this one", the parent should hide the card AND
 *   NOT show it again for the current episode — we use a single
 *   `userDismissed` flag in the parent for that.
 */
@Composable
fun NextEpisodeCard(
    next: NextEpisodeContext.Episode,
    countdownSeconds: Int,
    onPlayNext: () -> Unit,
    onStay: () -> Unit,
) {
    // BACK dismisses to "Stay" — never advances. Matches user instinct.
    androidx.activity.compose.BackHandler(enabled = true) { onStay() }

    // Live countdown — ticks every 100 ms so the ring animates smoothly.
    var remainingMs by remember(next.xtreamId) {
        mutableStateOf(countdownSeconds * 1000L)
    }
    LaunchedEffect(next.xtreamId) {
        val deadline = System.currentTimeMillis() + countdownSeconds * 1000L
        while (true) {
            val now = System.currentTimeMillis()
            val left = deadline - now
            if (left <= 0L) {
                remainingMs = 0L
                onPlayNext()
                return@LaunchedEffect
            }
            remainingMs = left
            delay(100L)
        }
    }

    // Hosted in the player's full-screen Box; we anchor the card to
    // the bottom-right and inset 32 dp for TV-safe area.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(
                initialOffsetX = { it / 4 },
                animationSpec = tween(220, easing = EaseOutCubic),
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { it / 4 },
                animationSpec = tween(160),
            ) + fadeOut(animationSpec = tween(140)),
        ) {
            Card(
                next = next,
                remainingMs = remainingMs,
                totalCountdownMs = countdownSeconds * 1000L,
                onPlayNext = onPlayNext,
                onStay = onStay,
            )
        }
    }
}

@Composable
private fun Card(
    next: NextEpisodeContext.Episode,
    remainingMs: Long,
    totalCountdownMs: Long,
    onPlayNext: () -> Unit,
    onStay: () -> Unit,
) {
    val secondsLeft = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)
    Column(
        modifier = Modifier
            .widthIn(min = 380.dp, max = 480.dp)
            .wrapContentHeight()
            .background(Color(0xF20A0F1A), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        // ── Hero row: still + meta ───────────────────────────────────
        Row(verticalAlignment = Alignment.Top) {
            // Episode still (or neutral placeholder if TMDB didn't have one).
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF111827)),
                contentAlignment = Alignment.Center,
            ) {
                if (next.stillUrl != null) {
                    AsyncImage(
                        model = next.stillUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("S${next.seasonNumber}E${next.episodeNumber}",
                        color = TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold)
                }
                // Soft cyan corner badge with countdown ring + number.
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CountdownRing(
                        progress = remainingMs.toFloat() / totalCountdownMs.coerceAtLeast(1L).toFloat(),
                    )
                    Text("$secondsLeft",
                        color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "UP NEXT",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "S${next.seasonNumber} · Episode ${next.episodeNumber}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    next.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                if (next.runtimeMinutes != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${next.runtimeMinutes} min",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        // Optional plot snippet, 2-line clamp via maxLines.
        if (!next.overview.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                next.overview.take(160),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(14.dp))
        // ── Action row ──────────────────────────────────────────────
        // Auto-focus "Play next episode" so OK confirms immediately,
        // matching the user's expectation when the countdown is
        // already running.
        val playFocus = remember { FocusRequester() }
        LaunchedEffect(next.xtreamId) {
            runCatching { playFocus.requestFocus() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(
                label = "Play next episode",
                primary = true,
                focusRequester = playFocus,
                onClick = onPlayNext,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Pill(
                label = "Stay on this one",
                primary = false,
                onClick = onStay,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Pill(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val baseBg = if (primary) Color(0xFF06B6D4) else Color(0x14FFFFFF)
    val focusBg = if (primary) Color(0xFF22D3EE) else Color(0x3306B6D4)
    val baseBorder = if (primary) Color(0xFF06B6D4) else Color(0x33FFFFFF)
    val focusBorder = Cyan
    val textColor = when {
        primary && focused -> Color.Black
        primary -> Color(0xFF021014)
        focused -> Color.White
        else -> Color.White
    }
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                if (focused) focusBg else baseBg,
                RoundedCornerShape(10.dp),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) focusBorder else baseBorder,
                RoundedCornerShape(10.dp),
            )
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}

/** Thin circular progress ring overlaid on the still's corner badge. */
@Composable
private fun CountdownRing(progress: Float) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val stroke = 3.dp.toPx()
        val sweep = (progress.coerceIn(0f, 1f)) * 360f
        // Background track
        drawArc(
            color = Color(0x44FFFFFF),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke),
        )
        // Progress arc, brand cyan
        drawArc(
            brush = Brush.sweepGradient(
                listOf(Color(0xFF06B6D4), Color(0xFF22D3EE)),
            ),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke),
        )
    }
}
