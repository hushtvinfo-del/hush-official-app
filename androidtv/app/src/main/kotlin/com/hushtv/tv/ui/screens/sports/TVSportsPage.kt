@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.sports.SportsGame
import com.hushtv.tv.data.sports.SportsHero
import com.hushtv.tv.data.sports.SportsPpvEvent
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter

/**
 * Main "PPV & LIVE SPORTS" home page used inside TVMainMenuScreen's
 * page pager. Layout:
 *   ┌──────────────────────────── Hero ─────────────────────────────┐
 *   │  cinematic backdrop · countdown · channel chip                │
 *   └───────────────────────────────────────────────────────────────┘
 *   ┌── League pills row (← FIRST FOCUS LANDS HERE) ────────────────┐
 *   │ [LIVE] [PPV] [NHL] [MLB] [NBA] [NFL] [MLS] [UCL] ...           │
 *   └───────────────────────────────────────────────────────────────┘
 *   ┌── Game cards rail (horizontal scroll) ───────────────────────┐
 *   │ [card1] [card2] [card3] [card4] ...                          │
 *   └───────────────────────────────────────────────────────────────┘
 *
 * Channel cards filter to only games / PPV the user can actually
 * watch (per spec: "hide entirely" if no playlist match).
 *
 * v1.44.2 ANR FIX:
 * The first focusable on this page is now the FIRST PILL ("All"), not
 * the first card. Reasons:
 *   • Pills always exist (even while data loads), so
 *     `firstItemFocus.requestFocus()` always lands on a real
 *     focusable. Previously the requester was bound to GameCard idx 0
 *     which doesn't get composed when the games list is still loading
 *     → requester unattached → request silently fails → focus drifts
 *     to the side rail (which auto-expands), exactly the symptom the
 *     user reported.
 *   • Pills are the natural entry point for the page anyway — the
 *     first thing the user wants to do is choose a league.
 *
 * Card matching now runs on Dispatchers.Default via
 * [rememberPlayableGames] / [rememberPlayablePpv] so the main thread
 * never blocks waiting for ~5000-channel × ~160-game match work.
 */
@Composable
fun TVSportsPage(
    nav: NavController,
    playlistId: String,
    firstItemFocus: FocusRequester,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    val home = rememberSportsHome()
    val liveChannels = rememberLiveChannels(playlistId)
    val channelIndex = rememberChannelIndex(liveChannels)

    var selectedLeague by remember { mutableStateOf<String>("all") }
    var pinnedHero by remember { mutableStateOf<SportsHero?>(null) }
    val railFocus = remember { FocusRequester() }

    val pills = remember(home, liveChannels) {
        buildPills(home?.leagues?.map { it.league.slug to it.league.name } ?: emptyList())
    }

    // Background-thread filter — never blocks the main thread even
    // when the playlist has thousands of channels.
    val flatGames = remember(home) {
        (home?.leagues ?: emptyList()).flatMap { it.games }
    }
    val playableAll: List<Pair<SportsGame, MediaCard>> =
        rememberPlayableGames(flatGames, channelIndex)

    val playableGames: List<Pair<SportsGame, MediaCard>> =
        remember(playableAll, selectedLeague) {
            when (selectedLeague) {
                "all" -> playableAll.sortedBy { it.first.start_utc }
                "live" -> playableAll
                    .filter { it.first.status.equals("live", ignoreCase = true) }
                    .sortedBy { it.first.start_utc }
                "ppv" -> emptyList() // handled separately below
                else -> playableAll
                    .filter { it.first.league?.slug == selectedLeague }
                    .sortedBy { it.first.start_utc }
            }
        }

    val playablePpv: List<Pair<SportsPpvEvent, MediaCard>> =
        rememberPlayablePpv(home?.ppv ?: emptyList(), channelIndex)
            .let { list -> remember(list) { list.sortedBy { it.first.start_utc } } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F))
            .focusGroup(),
    ) {
        // ── Hero (top half) ──
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp),
        ) {
            SportsHeroLayer(
                heroItems = home?.hero ?: emptyList(),
                contentStartPadding = 96.dp,
                pinned = pinnedHero,
            )
        }

        // ── League pills + cards rail (bottom half) ──
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 360.dp),
        ) {
            LeaguePillBar(
                pills = pills,
                selectedSlug = selectedLeague,
                onSelect = { selectedLeague = it },
                contentStartPadding = 96.dp,
                firstItemFocus = firstItemFocus,
                onUpFromRow = onUpFromRow,
                onDownFromRow = { railFocus.requestFocus() },
            )

            if (selectedLeague == "ppv") {
                PpvCardsRail(
                    nav = nav,
                    playlistId = playlistId,
                    items = playablePpv,
                    railFocus = railFocus,
                    onPpvFocused = { p, ch ->
                        pinnedHero = SportsHero(
                            kind = "ppv",
                            id = p.id,
                            title = p.title,
                            subtitle = p.subtitle,
                            image = p.poster_url,
                            start_utc = p.start_utc,
                            channel = ch.title,
                        )
                    },
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            } else {
                GameCardsRail(
                    nav = nav,
                    playlistId = playlistId,
                    items = playableGames,
                    railFocus = railFocus,
                    onGameFocused = { g, ch ->
                        pinnedHero = SportsHero(
                            kind = "game",
                            id = g.id,
                            title = "${g.away?.name ?: "TBA"}  @  ${g.home?.name ?: "TBA"}",
                            subtitle = g.league?.name,
                            image = g.home?.badge_url ?: g.home?.logo_url,
                            start_utc = g.start_utc,
                            channel = ch.title,
                        )
                    },
                    onUpFromRow = onUpFromRow,
                    onDownFromRow = onDownFromRow,
                )
            }
        }
    }
}

/** Build the static league-pill list. We always lead with All / Live
 *  / PPV, then the leagues the server reports as having games today,
 *  in their own ordering. */
private fun buildPills(leagueSlugsAndNames: List<Pair<String, String>>): List<LeaguePill> {
    val out = mutableListOf<LeaguePill>()
    out += LeaguePill("all", "All", Cyan)
    out += LeaguePill("live", "Live", Color(0xFFEF4444))
    out += LeaguePill("ppv", "PPV", Color(0xFFE10600))
    leagueSlugsAndNames.forEach { (slug, name) ->
        if (slug == "ppv" || slug == "all" || slug == "live") return@forEach
        val accent = LEAGUE_ACCENTS[slug] ?: Color(0xFF38BDF8)
        out += LeaguePill(slug, name, accent)
    }
    return out
}

private val LEAGUE_ACCENTS: Map<String, Color> = mapOf(
    "nhl" to Color(0xFF111111),
    "mlb" to Color(0xFF132448),
    "nba" to Color(0xFFC8102E),
    "nfl" to Color(0xFF013369),
    "ufc" to Color(0xFFD20A0A),
    "mls" to Color(0xFF00205B),
    "epl" to Color(0xFF3D195B),
    "ucl" to Color(0xFF00387B),
    "ncaaf" to Color(0xFFBB0000),
    "ncaab" to Color(0xFFBB0000),
    "cfl" to Color(0xFFB8860B),
    "f1" to Color(0xFFE10600),
)

@Composable
private fun GameCardsRail(
    nav: NavController,
    playlistId: String,
    items: List<Pair<SportsGame, MediaCard>>,
    railFocus: FocusRequester,
    onGameFocused: (SportsGame, MediaCard) -> Unit,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .focusGroup()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionDown -> {
                        if (onDownFromRow != null) { onDownFromRow(); true } else false
                    }
                    else -> false
                }
            },
    ) {
        if (items.isEmpty()) {
            EmptyState("No games scheduled for this league right now.")
            return@Column
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(start = 96.dp, end = 96.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items.forEachIndexed { idx, (game, ch) ->
                GameCard(
                    game = game,
                    matchedChannel = ch,
                    onFocus = { onGameFocused(game, ch) },
                    onClick = {
                        playLiveChannel(ctx, nav, playlistId, ch)
                    },
                    focusRequester = if (idx == 0) railFocus else null,
                    onUpFromCard = onUpFromRow,
                )
            }
        }
    }
}

@Composable
private fun PpvCardsRail(
    nav: NavController,
    playlistId: String,
    items: List<Pair<SportsPpvEvent, MediaCard>>,
    railFocus: FocusRequester,
    onPpvFocused: (SportsPpvEvent, MediaCard) -> Unit,
    onUpFromRow: () -> Unit,
    onDownFromRow: (() -> Unit)?,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .focusGroup()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionDown -> {
                        if (onDownFromRow != null) { onDownFromRow(); true } else false
                    }
                    else -> false
                }
            },
    ) {
        if (items.isEmpty()) {
            EmptyState("No upcoming PPV events you can watch right now.")
            return@Column
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(start = 96.dp, end = 96.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items.forEachIndexed { idx, (event, ch) ->
                PpvCard(
                    event = event,
                    matchedChannel = ch,
                    onFocus = { onPpvFocused(event, ch) },
                    onClick = { playLiveChannel(ctx, nav, playlistId, ch) },
                    focusRequester = if (idx == 0) railFocus else null,
                    onUpFromCard = onUpFromRow,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(start = 96.dp, end = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color(0xFF94A3B8),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
        )
    }
}

private fun playLiveChannel(
    ctx: android.content.Context,
    nav: NavController,
    playlistId: String,
    ch: MediaCard,
) {
    val p = PlaylistStore.find(ctx, playlistId) ?: return
    val streamUrl = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
    val encUrl = android.net.Uri.encode(streamUrl)
    val encTitle = android.net.Uri.encode(ch.title)
    nav.navigate("player/$playlistId/$encUrl/$encTitle/true")
}
