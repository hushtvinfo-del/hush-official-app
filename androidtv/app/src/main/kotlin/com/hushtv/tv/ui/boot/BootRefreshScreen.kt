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
import androidx.compose.foundation.layout.widthIn
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

            // Slots that the "Building home" step will fill in as
            // earlier steps complete. Each step populates just its
            // own slot so if one times out we still build a partial
            // rail set rather than blocking the home screen on one
            // slow endpoint.
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
                }
            }
            steps += BootStep("Refreshing Live TV…") {
                withContext(Dispatchers.IO) {
                    // Warm OkHttp disk cache for the "all live" URL
                    // (used by the Live TV browse screen).
                    runCatching {
                        withTimeoutOrNull(3_000) {
                            XtreamApi.getAllStreams(
                                playlist.host, playlist.username,
                                playlist.password, "live",
                            )
                        }
                    }
                    // Also fetch streams for the first (home-rendered)
                    // live category specifically. This warms the exact
                    // URL key the home screen uses AND lets us build
                    // the rails cache without a second network round.
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
                    // Fetch the first 3 movie categories (home renders 3 rails).
                    // Run in parallel via coroutineScope + async; each has
                    // its own 2 s cap inside a shared 4 s outer timeout so
                    // one slow one can't stall the boot.
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
                        // Persist for instant first-frame on the next
                        // cold launch (before boot refresh completes).
                        com.hushtv.tv.data.HomeRailsCache.persist(ctx, rails)
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

            // Hard 11-second wall-clock cap on the NETWORK-bound
            // sequence above. Library indexing + themed-list
            // matching run OUTSIDE this cap because they MUST
            // complete before the home page renders — the cache
            // they fill is what makes home preview pills paint
            // with real cover art on first frame.
            withTimeoutOrNull(11_000) {
                steps.forEachIndexed { idx, step ->
                    stepLabel = step.label
                    step.action.invoke()
                    // Reserve last 2 progress slots for the post-cap
                    // indexing + theme-matching steps below.
                    progress = (idx + 1).toFloat() / (steps.size + 2)
                }
            }

            // Post-cap step 1: prime the LibraryIndex. Has its own
            // 8 s budget — most providers finish in 1-2 s, but a
            // huge catalog over a slow link can need more. The
            // OkHttp HTTP cache populated by the network steps
            // above usually means this completes in well under 1 s
            // because getAllStreams hits cache.
            stepLabel = "Indexing your library…"
            withContext(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(8_000) {
                        LibraryIndex.reset()
                        com.hushtv.tv.data.ThemedMatchCache.reset()
                        LibraryIndex.prime(ctx, playlist)
                    }
                }
            }

            // Themed-list matching is now LAZY — it kicks off async
            // (NOT blocking boot) just like Decades. The themes
            // catalog screen also defensively re-primes when
            // entered, so the user never sees a blank state. This
            // matches the user-requested "curate when I open the
            // themes section, not before" behavior.
            com.hushtv.tv.data.ThemedMatchCache.primeAsync(ctx, playlist)

            // Decade-year prime — kicked off async (NOT blocking
            // boot). 90 years × ~250 candidates is heavier than
            // themes, so we let it warm up in the background while
            // the user lands on the home screen. Year-detail
            // screens fall back to a synchronous match-on-open if
            // the cache hasn't reached that year yet.
            com.hushtv.tv.data.DecadeYearMatchCache.primeAsync(ctx, playlist)
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
