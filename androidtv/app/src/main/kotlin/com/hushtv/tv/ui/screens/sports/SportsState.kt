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
 * v1.44.58 behaviour change
 * ─────────────────────────
 * The previous version (`mapNotNull` on `SportsChannelMatcher.match`)
 * dropped a game ENTIRELY when its API-supplied primary broadcaster
 * (e.g. "CBC", "NBC", "TSN") didn't strict-match a channel in the
 * user's playlist. That created a confusing UX:
 *
 *   • A Habs-vs-Bills NHL game whose primary broadcaster the API
 *     reported as "CBC" would NEVER show up in the games rail —
 *     not even if the user's EPG had TSN / Sportsnet broadcasting
 *     the SAME game on alternate channels.
 *   • The user attributed this to the CBC blacklist added in
 *     v1.44.56, but the picker blacklist runs on a different list
 *     ([SportsApi.gameChannels]) and never touched the games rail.
 *     The real culprit was the strict primary-channel match.
 *
 * New behaviour: keep EVERY game whose API record carries some
 * broadcaster string. If the primary broadcaster also resolves to a
 * playlist channel, attach it as the [MediaCard] (so the card hero,
 * logo, and channel label still render). If not, attach a synthetic
 * MediaCard built from the API channel name so the card still
 * paints — at click time the [GameChannelSheet] makes its own
 * per-game EPG call (which is independent of the primary-broadcaster
 * match) and offers every actually-playable channel from the user's
 * EPG, minus blacklisted patterns.
 *
 * Trade-off: the user might tap a card and find no playable EPG
 * matches in their guide. That's strictly better than the previous
 * behaviour where the game silently vanished — at least now they
 * see the game exists and can decide whether to look elsewhere.
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
        if (games.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            games.mapNotNull { g ->
                val ch = g.channel ?: return@mapNotNull null
                val match = if (index != null && !index.isEmpty) {
                    SportsChannelMatcher.match(ch, index)
                } else null
                val card = match ?: syntheticChannelCard(ch)
                g to card
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
        if (events.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            events.mapNotNull { e ->
                val ch = e.channel ?: return@mapNotNull null
                // PPV cards tune DIRECTLY via [playLiveChannel] (no
                // intermediate picker sheet), so we MUST have a real
                // playlist match — otherwise there's nothing to tune.
                // Synthetic cards are reserved for the game rail
                // where GameChannelSheet does its own resolution.
                if (index == null || index.isEmpty) return@mapNotNull null
                val match = SportsChannelMatcher.match(ch, index) ?: return@mapNotNull null
                e to match
            }
        }
    }.value
}

/**
 * Build a placeholder [MediaCard] for a game whose API-supplied
 * primary broadcaster didn't resolve to a real playlist channel.
 *
 * The card carries:
 *   • A `streamId` of `0` so any accidental "play this channel"
 *     code path is a no-op (and `GameChannelSheet` will block on
 *     "Channel not in your playlist" if reached — the correct
 *     defensive behaviour for unmatched cards).
 *   • The API channel name as the title so the card visibly says
 *     e.g. "CBC" instead of going blank.
 *   • Empty poster / category so Coil falls back to its default
 *     gradient — no broken-image artifact.
 */
private fun syntheticChannelCard(name: String): MediaCard = MediaCard(
    id = "synthetic-${name.hashCode()}",
    title = name,
    poster = null,
    rating = null,
    streamId = 0,
    seriesId = 0,
    containerExtension = null,
    kind = "live",
)
