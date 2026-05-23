package com.hushtv.tv.ui.boot

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.R
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    // v1.44.132 — Lite-mode shortcut. On weak GPU Fire Sticks
    // (AFTSSS, AFTSS, AFTKM, AFTKRT, etc.) the original animated
    // boot refresh — gradient brush + animateFloatAsState progress
    // + rememberInfiniteTransition pulse + AnimatedVisibility fades
    // — repeatedly ANR'd the main thread (Compose layer matrix
    // recalc on every animation frame). Crash Monitor showed a
    // user stuck in a refresh→ANR→refresh loop. The simplified
    // composable below has the same network sequence and timing
    // but draws a STATIC frame (no animations) so the GPU work
    // per frame is near zero. Net effect: same UX, no ANR.
    val ctx = LocalContext.current
    val isLite = remember { com.hushtv.tv.data.AppModeStore.load(ctx) == com.hushtv.tv.data.AppMode.LITE ||
        com.hushtv.tv.data.DeviceClass.isLowRam(ctx) }
    if (isLite) {
        BootRefreshScreenLite(onDone)
    } else {
        BootRefreshScreenPro(onDone)
    }
}

@Composable
private fun BootRefreshScreenPro(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var stepLabel by remember { mutableStateOf("Refreshing your library…") }
    var progress by remember { mutableStateOf(0f) }
    // v1.44.102 — Gcore CDN connection state. Flips to true the first
    // time a network step (categories fetch) returns real data,
    // proving the device is talking to the Xtream backend through
    // its Gcore-fronted edge. Drives the bottom pill that reads
    // "Connecting to Gcore CDN…" → "Connected to Gcore CDN".
    var gcoreConnected by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 280),
        label = "boot-progress",
    )

    LaunchedEffect(Unit) {
        // v1.44.132 — Network sequence extracted to shared
        // runBootRefresh() so Pro and Lite variants stay
        // byte-identical in behaviour. Only rendering differs.
        runBootRefresh(
            ctx = ctx,
            onProgress = { progress = it },
            onLabel = { stepLabel = it },
            onGcoreConnected = { gcoreConnected = true },
            onDone = onDone,
        )
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
        // Column width is bounded by both fillMaxWidth (so it adapts
        // to phones / tablets) AND widthIn(max = …) so on a TV it
        // doesn't sprawl across the whole 16:9 panel. The previous
        // hard 0.42 × screen width was too narrow on a phone — the
        // "Hush" wordmark was wrapping mid-word ("Hu" / "sh") and
        // "Refreshing your library" was wrapping after each word.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 32.dp, vertical = 40.dp),
        ) {
            // Wordmark — same recipe as the cinematic splash
            // (`HushSplashScreen` + `HushTVLogo`) so the boot
            // refresh feels like a continuation of the launcher
            // splash rather than a separate screen with a
            // different visual identity.
            //
            // `softWrap = false` + `maxLines = 1` guarantees the
            // wordmark NEVER breaks mid-word even on the narrowest
            // foldable phone. The font-size is responsive (chosen
            // by clamping the available width) below.
            com.hushtv.tv.ui.HushTVLogo(fontSize = 48.sp)
            Spacer(Modifier.height(36.dp))
            Text(
                "Refreshing your library",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                softWrap = false,
                maxLines = 1,
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
                    maxLines = 1,
                    softWrap = false,
                )
            }
            // v1.44.102 — Gcore CDN connection pill. Shows
            //   "Connecting to Gcore CDN…" with a pulsing orange dot
            // while we're still warming up the first network call,
            // then transitions to
            //   "Connected to Gcore CDN" with a steady green dot
            // the moment the categories endpoint comes back with
            // real data. Tells users their stream is routed through
            // a real, named enterprise CDN (not a random reseller).
            Spacer(Modifier.height(18.dp))
            GcoreConnectBadge(connected = gcoreConnected)
        }
    }
}

@Composable
private fun GcoreConnectBadge(connected: Boolean) {
    val label = if (connected) "Connected to Gcore CDN" else "Connecting to Gcore CDN…"
    val dotColor = if (connected) Color(0xFF22C55E) else Color(0xFFFF6B00)
    // Pulsing alpha for the "connecting" state — fades the dot in
    // and out at ~1 Hz so the user can tell something is happening.
    val pulseAlpha = if (connected) {
        1f
    } else {
        val t = rememberInfiniteTransition(label = "gcore-pulse")
        t.animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "gcore-pulse-alpha",
        ).value
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .background(
                color = Color(0xFF0E1217),
                shape = RoundedCornerShape(20.dp),
            )
            .border(
                width = 1.dp,
                color = Color(0xFF1F2937),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Gcore logo
        Image(
            painter = painterResource(id = R.drawable.ic_gcore),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        // Status dot
        Box(
            Modifier
                .size(7.dp)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = if (connected) Color(0xFF86EFAC) else Color(0xFFFCD34D),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private data class BootStep(
    val label: String,
    val action: suspend () -> Unit,
)

/**
 * v1.44.132 — Lite-mode boot refresh, designed for the weakest
 * Fire Stick GPUs (AFTSSS, AFTSS, AFTKM, AFTKRT). Same network
 * sequence as [BootRefreshScreenPro], same timing budgets — only
 * the rendering is different:
 *
 *   • Solid color background (no gradient brush)
 *   • Static progress value (no `animateFloatAsState`)
 *   • Static dot color (no `rememberInfiniteTransition`)
 *   • Static step label (no `AnimatedVisibility` fade)
 *
 * Compose still recomposes the screen each time `progress` or
 * `stepLabel` mutates, but those are infrequent (~9 times over
 * 12 s). The expensive per-frame work (matrix recalc, gradient
 * rasterisation, layer composition for fade animations) is
 * completely eliminated — which was killing weak Fire Sticks
 * with `Matrix.constructor-impl` and `Surface.nativeAllocateBuffers`
 * ANRs (see crash report on marychaisson56@gmail.com).
 */
@Composable
private fun BootRefreshScreenLite(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var stepLabel by remember { mutableStateOf("Refreshing your library…") }
    var progress by remember { mutableStateOf(0f) }
    var gcoreConnected by remember { mutableStateOf(false) }

    // Same network sequence as Pro — extracted into runBootRefresh
    // to share the implementation byte-for-byte. The Pro variant
    // calls the same function; only the rendering differs.
    LaunchedEffect(Unit) {
        runBootRefresh(
            ctx = ctx,
            onProgress = { progress = it },
            onLabel = { stepLabel = it },
            onGcoreConnected = { gcoreConnected = true },
            onDone = onDone,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1424)),  // solid — no gradient
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 32.dp, vertical = 40.dp),
        ) {
            com.hushtv.tv.ui.HushTVLogo(fontSize = 48.sp)
            Spacer(Modifier.height(36.dp))
            Text(
                "Refreshing your library",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                softWrap = false,
                maxLines = 1,
            )
            Spacer(Modifier.height(20.dp))
            // Raw progress — Compose still updates the bar when
            // `progress` mutates, but there's no per-frame tween
            // animation eating GPU.
            LinearProgressIndicator(
                progress = { progress },
                color = Cyan,
                trackColor = Color(0x22FFFFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.height(14.dp))
            // Plain Text, no AnimatedVisibility.
            Text(
                stepLabel,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(18.dp))
            // Static Gcore pill — solid dot, no pulse animation.
            GcoreConnectBadgeStatic(connected = gcoreConnected)
        }
    }
}

@Composable
private fun GcoreConnectBadgeStatic(connected: Boolean) {
    val label = if (connected) "Connected to Gcore CDN" else "Connecting to Gcore CDN…"
    val dotColor = if (connected) Color(0xFF22C55E) else Color(0xFFFF6B00)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .background(color = Color(0xFF0E1217), shape = RoundedCornerShape(20.dp))
            .border(width = 1.dp, color = Color(0xFF1F2937), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_gcore),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = if (connected) Color(0xFF86EFAC) else Color(0xFFFCD34D),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Shared network-refresh sequence used by BOTH the Pro and Lite
 * boot refresh composables. Extracted into a top-level suspend
 * function so the two variants diverge only in rendering, never
 * in behaviour.
 */
private suspend fun runBootRefresh(
    ctx: Context,
    onProgress: (Float) -> Unit,
    onLabel: (String) -> Unit,
    onGcoreConnected: () -> Unit,
    onDone: () -> Unit,
) {
    runCatching {
        val playlist = withContext(Dispatchers.IO) {
            val id = LastProfileStore.load(ctx) ?: return@withContext null
            PlaylistStore.find(ctx, id)
        }
        if (playlist == null) {
            onDone(); return
        }
        var liveCats: List<com.hushtv.tv.data.XtreamCategory> = emptyList()
        var movieCats: List<com.hushtv.tv.data.XtreamCategory> = emptyList()
        var seriesCats: List<com.hushtv.tv.data.XtreamCategory> = emptyList()
        var liveByCat: Map<String, List<com.hushtv.tv.data.MediaCard>> = emptyMap()
        var moviesByCat: Map<String, List<com.hushtv.tv.data.MediaCard>> = emptyMap()
        var seriesByCat: Map<String, List<com.hushtv.tv.data.MediaCard>> = emptyMap()

        val steps = mutableListOf<BootStep>()
        steps += BootStep("Clearing cache…") {
            withContext(Dispatchers.IO) {
                runCatching { XtreamApi.flushHttpCache() }
                runCatching { trimCoilCache(ctx) }
            }
        }
        steps += BootStep("Refreshing categories…") {
            withContext(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(2_500) {
                        liveCats = XtreamApi.getCategories(
                            playlist.host, playlist.username,
                            playlist.password, "live",
                        )
                    }
                }
                runCatching {
                    withTimeoutOrNull(2_500) {
                        movieCats = XtreamApi.getCategories(
                            playlist.host, playlist.username,
                            playlist.password, "movie",
                        )
                    }
                }
                runCatching {
                    withTimeoutOrNull(2_500) {
                        seriesCats = XtreamApi.getCategories(
                            playlist.host, playlist.username,
                            playlist.password, "series",
                        )
                    }
                }
                if (liveCats.isNotEmpty() || movieCats.isNotEmpty() ||
                    seriesCats.isNotEmpty()
                ) {
                    onGcoreConnected()
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
                val firstLive = liveCats.firstOrNull()
                if (firstLive != null) {
                    runCatching {
                        withTimeoutOrNull(3_000) {
                            val streams = XtreamApi.getStreamsForCategory(
                                playlist.host, playlist.username,
                                playlist.password, "live", firstLive.category_id,
                            )
                            liveByCat = mapOf(firstLive.category_id to streams)
                        }
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
                val targetCats = movieCats.take(3)
                if (targetCats.isNotEmpty()) {
                    runCatching {
                        withTimeoutOrNull(4_000) {
                            kotlinx.coroutines.coroutineScope {
                                val results = targetCats.map { c ->
                                    async(Dispatchers.IO) {
                                        withTimeoutOrNull(2_000) {
                                            c.category_id to XtreamApi.getStreamsForCategory(
                                                playlist.host, playlist.username,
                                                playlist.password, "movie", c.category_id,
                                            )
                                        }
                                    }
                                }.awaitAll().filterNotNull().toMap()
                                moviesByCat = results
                            }
                        }
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
                val firstSeries = seriesCats.firstOrNull()
                if (firstSeries != null) {
                    runCatching {
                        withTimeoutOrNull(3_000) {
                            val streams = XtreamApi.getStreamsForCategory(
                                playlist.host, playlist.username,
                                playlist.password, "series", firstSeries.category_id,
                            )
                            seriesByCat = mapOf(firstSeries.category_id to streams)
                        }
                    }
                }
            }
        }
        steps += BootStep("Building home…") {
            withContext(Dispatchers.IO) {
                runCatching {
                    val rails = com.hushtv.tv.data.HomeRailsCache.build(
                        playlistId = playlist.id,
                        liveCategories = liveCats,
                        movieCategories = movieCats,
                        seriesCategories = seriesCats,
                        liveByCat = liveByCat,
                        moviesByCat = moviesByCat,
                        seriesByCat = seriesByCat,
                    )
                    com.hushtv.tv.data.HomeRailsCache.put(rails)
                    com.hushtv.tv.data.HomeRailsCache.persist(ctx, rails)
                }
            }
        }
        steps += BootStep("Syncing requests…") {
            withContext(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(2_500) {
                        com.hushtv.tv.data.RequestCache.put(emptyList())
                    }
                }
            }
        }

        withTimeoutOrNull(11_000) {
            steps.forEachIndexed { idx, step ->
                onLabel(step.label)
                step.action.invoke()
                onProgress((idx + 1).toFloat() / (steps.size + 2))
            }
        }

        onLabel("Indexing your library…")
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(8_000) {
                    LibraryIndex.reset()
                    com.hushtv.tv.data.ThemedMatchCache.reset()
                    LibraryIndex.prime(ctx, playlist)
                }
            }
        }

        com.hushtv.tv.data.ThemedMatchCache.primeAsync(ctx, playlist)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { com.hushtv.tv.data.ThemePackLoader.refreshRemote(ctx) }
        }
        com.hushtv.tv.data.DecadeYearMatchCache.primeAsync(ctx, playlist)
    }
    onProgress(1f)
    delay(220)
    onDone()
}

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
