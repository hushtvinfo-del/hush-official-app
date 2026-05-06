package com.hushtv.tv.ui.screens.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.sports.SportsApi
import com.hushtv.tv.data.sports.SportsChannelMatcher
import com.hushtv.tv.data.sports.SportsHomeResponse
import com.hushtv.tv.data.sports.SportsLeagueResponse
import com.hushtv.tv.data.sports.SportsPpvListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sports state holders — load + refresh + match canonical channel
 * names to the user's actual Xtream live channels. Public surface is
 * intentionally tiny so the UI files stay focused on layout.
 */
@Composable
fun rememberSportsHome(): SportsHomeResponse? {
    var data by remember { mutableStateOf<SportsHomeResponse?>(null) }
    LaunchedEffect(Unit) {
        // Fast initial fetch.
        data = withContext(Dispatchers.IO) { SportsApi.home() }
        // Refresh every 5 min to keep scores / countdowns fresh.
        while (true) {
            delay(5 * 60 * 1000L)
            val fresh = withContext(Dispatchers.IO) { SportsApi.home() }
            if (fresh != null) data = fresh
        }
    }
    return data
}

@Composable
fun rememberSportsLeague(slug: String, days: Int = 7): SportsLeagueResponse? {
    var data by remember(slug) { mutableStateOf<SportsLeagueResponse?>(null) }
    LaunchedEffect(slug) {
        data = withContext(Dispatchers.IO) { SportsApi.league(slug, days) }
    }
    return data
}

@Composable
fun rememberSportsPpv(): SportsPpvListResponse? {
    var data by remember { mutableStateOf<SportsPpvListResponse?>(null) }
    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) { SportsApi.ppv() }
    }
    return data
}

/**
 * Loads the user's full live-channel list once for the active
 * playlist and keeps it in memory for SportsChannelMatcher. Returns
 * an empty list while loading (the UI hides un-matchable cards
 * regardless, so this just delays the row a beat).
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
 * For a list of games, returns only the ones whose canonical channel
 * name resolves to a real channel in the user's playlist. Per the
 * user spec from v1.43.99: "hide entirely" — we don't show greyed-out
 * games. The caller passes the raw channels list once and we match
 * each game against it.
 */
fun filterPlayableGames(
    games: List<com.hushtv.tv.data.sports.SportsGame>,
    liveChannels: List<MediaCard>,
): List<Pair<com.hushtv.tv.data.sports.SportsGame, MediaCard>> {
    if (liveChannels.isEmpty()) return emptyList()
    return games.mapNotNull { g ->
        val ch = g.channel ?: return@mapNotNull null
        val match = SportsChannelMatcher.match(ch, liveChannels) ?: return@mapNotNull null
        g to match
    }
}

fun filterPlayablePpv(
    events: List<com.hushtv.tv.data.sports.SportsPpvEvent>,
    liveChannels: List<MediaCard>,
): List<Pair<com.hushtv.tv.data.sports.SportsPpvEvent, MediaCard>> {
    if (liveChannels.isEmpty()) return emptyList()
    return events.mapNotNull { e ->
        val ch = e.channel ?: return@mapNotNull null
        val match = SportsChannelMatcher.match(ch, liveChannels) ?: return@mapNotNull null
        e to match
    }
}
