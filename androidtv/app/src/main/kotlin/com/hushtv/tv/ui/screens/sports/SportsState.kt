package com.hushtv.tv.ui.screens.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.sports.SportsApi
import com.hushtv.tv.data.sports.SportsChannelMatcher
import com.hushtv.tv.data.sports.SportsGame
import com.hushtv.tv.data.sports.SportsHomeResponse
import com.hushtv.tv.data.sports.SportsLeagueResponse
import com.hushtv.tv.data.sports.SportsPpvEvent
import com.hushtv.tv.data.sports.SportsPpvListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sports state holders — load + refresh + match canonical channel
 * names to the user's actual Xtream live channels. Public surface is
 * intentionally tiny so the UI files stay focused on layout.
 *
 * v1.44.2 PERF + ANR FIX:
 * Channel matching used to run in a Compose `remember{}` block on the
 * MAIN thread. With ~5000 live channels in a typical playlist, the
 * normalize-then-match nested loop took 5-15 s, triggering an OS-level
 * ANR kill the moment the user landed on the Sports page.
 *
 * Two-part fix:
 *   1. The matcher now caches normalized channels in [ChannelIndex]
 *      (built once per playlist).
 *   2. [filterPlayableGamesAsync] / [filterPlayablePpvAsync] do all
 *      the matching on `Dispatchers.Default`, so the main thread
 *      stays responsive while the CPU work happens in the background.
 *
 * Net effect: the Sports page composes in ~16 ms and the rail no
 * longer half-opens behind the page while the match work catches up.
 */
@Composable
fun rememberSportsHome(): SportsHomeResponse? {
    var data by remember { mutableStateOf<SportsHomeResponse?>(null) }
    LaunchedEffect(Unit) {
        // Fast initial fetch.
        data = withContext(Dispatchers.IO) {
            runCatching { SportsApi.home() }.getOrNull()
        }
        // Refresh every 5 min to keep scores / countdowns fresh.
        while (true) {
            delay(5 * 60 * 1000L)
            val fresh = withContext(Dispatchers.IO) {
                runCatching { SportsApi.home() }.getOrNull()
            }
            if (fresh != null) data = fresh
        }
    }
    return data
}

@Composable
fun rememberSportsLeague(slug: String, days: Int = 7): SportsLeagueResponse? {
    var data by remember(slug) { mutableStateOf<SportsLeagueResponse?>(null) }
    LaunchedEffect(slug) {
        data = withContext(Dispatchers.IO) {
            runCatching { SportsApi.league(slug, days) }.getOrNull()
        }
    }
    return data
}

@Composable
fun rememberSportsPpv(): SportsPpvListResponse? {
    var data by remember { mutableStateOf<SportsPpvListResponse?>(null) }
    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) {
            runCatching { SportsApi.ppv() }.getOrNull()
        }
    }
    return data
}

/**
 * Loads the user's full live-channel list once for the active
 * playlist and keeps it in memory. Runs on Dispatchers.IO so the
 * (potentially-thousands-of-channels) HTTP fetch never blocks the
 * UI thread.
 */
@Composable
fun rememberLiveChannels(playlistId: String): List<MediaCard> {
    val ctx = LocalContext.current
    var channels by remember(playlistId) { mutableStateOf<List<MediaCard>>(emptyList()) }
    LaunchedEffect(playlistId) {
        val p = PlaylistStore.find(ctx, playlistId) ?: return@LaunchedEffect
        channels = withContext(Dispatchers.IO) {
            runCatching { XtreamApi.getAllStreams(p.host, p.username, p.password, "live") }
                .getOrDefault(emptyList())
        }
    }
    return channels
}

/**
 * Builds a [SportsChannelMatcher.ChannelIndex] off the main thread
 * the moment the live-channel list is ready. The index encapsulates
 * every channel's normalized form so per-game matching becomes
 * O(games × channels) instead of O(games × channels × normalize_cost).
 */
@Composable
fun rememberChannelIndex(
    channels: List<MediaCard>,
): SportsChannelMatcher.ChannelIndex? {
    return produceState<SportsChannelMatcher.ChannelIndex?>(
        initialValue = null,
        key1 = channels,
    ) {
        if (channels.isEmpty()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            SportsChannelMatcher.ChannelIndex(channels)
        }
    }.value
}

/**
 * Async equivalent of the old `filterPlayableGames`. Returns
 * `emptyList()` while the index or game list are still loading; emits
 * the resolved list once the background filter completes.
 *
 * Keys on the identity of [games] and [index] so a swipe between
 * leagues (which changes [games]) re-runs the filter, but a steady-
 * state recomposition (e.g. focus tick) reuses the cached value.
 */
@Composable
fun rememberPlayableGames(
    games: List<SportsGame>,
    index: SportsChannelMatcher.ChannelIndex?,
): List<Pair<SportsGame, MediaCard>> {
    return produceState<List<Pair<SportsGame, MediaCard>>>(
        initialValue = emptyList(),
        key1 = games,
        key2 = index,
    ) {
        if (index == null || index.isEmpty || games.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            games.mapNotNull { g ->
                val ch = g.channel ?: return@mapNotNull null
                val match = SportsChannelMatcher.match(ch, index) ?: return@mapNotNull null
                g to match
            }
        }
    }.value
}

@Composable
fun rememberPlayablePpv(
    events: List<SportsPpvEvent>,
    index: SportsChannelMatcher.ChannelIndex?,
): List<Pair<SportsPpvEvent, MediaCard>> {
    return produceState<List<Pair<SportsPpvEvent, MediaCard>>>(
        initialValue = emptyList(),
        key1 = events,
        key2 = index,
    ) {
        if (index == null || index.isEmpty || events.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            events.mapNotNull { e ->
                val ch = e.channel ?: return@mapNotNull null
                val match = SportsChannelMatcher.match(ch, index) ?: return@mapNotNull null
                e to match
            }
        }
    }.value
}
