package com.hushtv.tv.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.material3.Text
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.delay

/**
 * Branded buffering overlay — the drop-in replacement for the
 * default ExoPlayer spinner used everywhere the app shows live
 * video (Live TV, Movies, Series; TV and Mobile players).
 *
 * # Design intent
 *
 * The stock ExoPlayer spinner reads as "cheap free-app vibes" —
 * the user asked for a modern, reassuring, Netflix/Disney+-grade
 * buffering experience that keeps them calm and informed instead
 * of wondering if the stream is broken or the app crashed.
 *
 * This overlay delivers that in three layers:
 *
 *   1. **Branded animation** — 5 animated signal bars pulsing in a
 *      staggered wave pattern. Reads as "we are searching for the
 *      stream" at a glance; feels alive, not frozen. Alternatives
 *      considered: circular progress ring, pulsing logo. Signal
 *      bars won because they directly map to the user's mental
 *      model of "IPTV looking for signal".
 *
 *   2. **Status text, re-assuring and escalating** — starts with a
 *      calm "Buffering… connecting to stream", escalates at 3 s to
 *      "Still working on it…", at 10 s to "Checking your
 *      connection", and if still stuck at 18 s surfaces a Retry
 *      button. Text adapts to [ContentType] so Live TV says
 *      "Reconnecting to live TV…" instead.
 *
 *   3. **Non-jarring entry / exit** — 300 ms fade in and 300 ms
 *      fade out; overlay is only shown after a 400 ms grace period
 *      so the user never sees a flash during a micro-rebuffer
 *      (track switch, manifest refresh, etc.).
 *
 * # Why drive off ExoPlayer listener + SystemClock instead of
 *   derivedStateOf on `isPlaying`?
 *
 * Compose's recomposition model is fine for "am I buffering right
 * now?" but we need TIME since the buffer started so the text can
 * escalate. We hook `Player.Listener` to capture the exact instant
 * buffering began, then a `LaunchedEffect` ticks every 500 ms to
 * bump the observed-duration state. Cheap, accurate, decoupled
 * from the player's internal timing.
 */
enum class BufferingContent {
    LIVE_TV,
    MOVIE,
    SERIES,
    GENERIC,
}

private data class BufferingCopy(
    val primary: String,
    val secondary: String,
)

private fun bufferingCopy(
    content: BufferingContent, secondsBuffering: Long, hasError: Boolean,
): BufferingCopy = when {
    hasError -> BufferingCopy(
        primary = when (content) {
            BufferingContent.LIVE_TV -> "Stream paused — attempting to reconnect"
            else -> "Connection hiccup — trying again"
        },
        secondary = "This usually takes just a few seconds.",
    )
    secondsBuffering >= 10 -> BufferingCopy(
        primary = "Still working on it… checking your connection",
        secondary = "We'll keep trying — hang tight.",
    )
    secondsBuffering >= 3 -> BufferingCopy(
        primary = when (content) {
            BufferingContent.LIVE_TV -> "Reconnecting to live TV…"
            else -> "Hang tight — loading your video"
        },
        secondary = "Improving connection quality…",
    )
    // Initial 0-3 s state. The user explicitly asked us to remove
    // the word "Buffering" from every surface — it reads as a
    // technical / failure signal and makes people think the
    // stream is broken. We use warm, reassuring phrasing instead
    // ("Starting hang tight…", "Tuning to live stream…") so the
    // first impression during load is the app saying "we've got
    // this" rather than "something's wrong".
    else -> BufferingCopy(
        primary = when (content) {
            BufferingContent.LIVE_TV -> "Tuning to live stream…"
            BufferingContent.MOVIE   -> "Starting hang tight…"
            BufferingContent.SERIES  -> "Starting hang tight…"
            BufferingContent.GENERIC -> "Starting hang tight…"
        },
        secondary = "This usually takes just a few seconds.",
    )
}

/**
 * The public API — drop this Composable into any player screen
 * AFTER the `AndroidView(PlayerView)` and the branded overlay will
 * take care of itself. Consumes the whole parent, fades in only
 * when [player] enters STATE_BUFFERING for > 400 ms, and provides
 * a Retry button if buffering persists past 18 s.
 */
@Composable
fun BufferingOverlay(
    player: ExoPlayer,
    content: BufferingContent,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    // ── Observe the player's buffering state ───────────────────
    var buffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    // `startedAt` is the SystemClock-millis of the moment we
    // entered STATE_BUFFERING. 0 when we're not buffering.
    var startedAt by remember { mutableLongStateOf(0L) }
    // `nowMs` is a tick that advances every 500 ms while we're
    // buffering, so the status text can escalate without making
    // the whole tree recompose per-frame.
    var nowMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        buffering = true
                        if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
                        hasError = false
                    }
                    Player.STATE_READY, Player.STATE_ENDED,
                    Player.STATE_IDLE -> {
                        buffering = false
                        startedAt = 0L
                        hasError = false
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                buffering = true
                hasError = true
                if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
            }
            override fun onPlayerErrorChanged(error: PlaybackException?) {
                if (error == null) hasError = false
            }
            override fun onTracksChanged(tracks: Tracks) {
                // When tracks arrive, if the player is already
                // playing we can exit the buffering state even if
                // STATE_READY fired before the overlay noticed —
                // this handles the quick recovery case cleanly.
                if (player.isPlaying) {
                    buffering = false
                    startedAt = 0L
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Tick driver — advances `nowMs` every 500 ms while we're
    // buffering so text escalates smoothly.
    LaunchedEffect(buffering) {
        while (buffering) {
            withFrameMillis { }  // yield to next frame first
            nowMs = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    // Grace period — don't flash the overlay for micro-rebuffers
    // (track switches, DASH manifest refresh, etc. under 400 ms).
    var shouldShow by remember { mutableStateOf(false) }
    LaunchedEffect(buffering) {
        if (!buffering) {
            shouldShow = false
        } else {
            delay(400)
            if (buffering) shouldShow = true
        }
    }

    val secondsBuffering = if (startedAt == 0L) 0L
        else (nowMs - startedAt) / 1000L

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        BufferingCard(
            content = content,
            secondsBuffering = secondsBuffering,
            hasError = hasError,
            showRetry = secondsBuffering >= 18L && onRetry != null,
            onRetry = onRetry ?: {},
        )
    }
}

@Composable
private fun BufferingCard(
    content: BufferingContent,
    secondsBuffering: Long,
    hasError: Boolean,
    showRetry: Boolean,
    onRetry: () -> Unit,
) {
    val copy = bufferingCopy(content, secondsBuffering, hasError)
    Box(
        Modifier
            .fillMaxSize()
            // Smoothly darken the frozen last frame underneath. We
            // use a subtle radial so the buffering card feels
            // spotlit without hard-cutting the video.
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xCC000000),
                        Color(0xEE000000),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE60A0F1C))
                .border(
                    1.dp,
                    Color(0x33FFFFFF),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            SignalBarsAnimation()
            Spacer(Modifier.height(4.dp))
            Text(
                copy.primary,
                color = Color(0xFFF4F5F8),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
            Text(
                copy.secondary,
                color = Color(0xFF9AA3B5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
            )
            // Small time-elapsed counter once buffering stretches
            // past the initial 3-second grace. Feels like a status
            // dashboard rather than a frozen screen.
            if (secondsBuffering >= 3) {
                Text(
                    "• " + formatSeconds(secondsBuffering),
                    color = Color(0x80FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            if (showRetry) {
                Spacer(Modifier.height(2.dp))
                RetryChip(onClick = onRetry)
            }
        }
    }
}

private fun formatSeconds(s: Long): String =
    if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"

/**
 * Five vertical bars that pulse in a staggered wave. Each bar is
 * driven by its own `infiniteTransition` with a phase offset so
 * the composite reads as a smooth moving wave instead of five
 * bars jumping in sync.
 */
@Composable
private fun SignalBarsAnimation() {
    val infinite = rememberInfiniteTransition(label = "bars")
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(44.dp),
    ) {
        // Five bars with evenly spaced phase delays.
        repeat(5) { i ->
            val phase = i * 160  // ms offset between bars
            val scale by infinite.animateFloat(
                initialValue = 0.32f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 900,
                        delayMillis = phase,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            // Height for each bar — max 40 dp, scales down to
            // ~12 dp at the trough. Alpha tracks scale so the
            // dimmer bars also fade slightly, adding depth.
            Bar(heightDp = 12.dp + 28.dp * scale, alpha = 0.55f + 0.45f * scale)
        }
    }
}

@Composable
private fun Bar(heightDp: Dp, alpha: Float) {
    Box(
        Modifier
            .width(7.dp)
            .height(heightDp)
            .clip(RoundedCornerShape(3.dp))
            .background(Cyan.copy(alpha = alpha)),
    )
}

@Composable
private fun RetryChip(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Cyan)
            .clickableWithEnter(onClick)
            .focusable()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            "Retry now",
            color = Color(0xFF05080F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}
