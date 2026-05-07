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
import androidx.compose.ui.graphics.Brush
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

    // v1.44.3 — breadcrumbs so the next ANR/crash report can show
    // exactly what state the user was in when it fired.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.hushtv.tv.data.EventLog.log("sports", "page mounted playlistId=$playlistId")
    }
    androidx.compose.runtime.LaunchedEffect(home, liveChannels.size, channelIndex) {
        com.hushtv.tv.data.EventLog.log(
            "sports",
            "data home=${home != null} live=${liveChannels.size} idx=${channelIndex?.size ?: -1}"
        )
    }
    // v1.44.27 — Warm the backend EPG cache for this playlist as
    // soon as the user hits Sports, so the first GameChannelSheet
    // open is <100 ms instead of waiting for an 8 s xmltv pull.
    val ctxForWarm = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(playlistId) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val p = com.hushtv.tv.data.PlaylistStore.find(ctxForWarm, playlistId)
                    ?: return@runCatching
                com.hushtv.tv.data.sports.SportsApi.warmEpg(
                    p.host, p.username, p.password,
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(selectedLeague) {
        com.hushtv.tv.data.EventLog.log("sports", "league=$selectedLeague")
    }

    val pills = remember(home, liveChannels) {
        buildPills(
            home?.leagues?.map {
                Triple(it.league.slug, it.league.name, it.league.logo_url)
            } ?: emptyList()
        )
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

    // v1.44.31 — `rememberSaveable` so the picker reappears when
    // user navigates BACK from the player. Previous behaviour: tap
    // game → picker → tap channel → player → BACK → user landed on
    // raw Sports page (picker was gone). Now: BACK from player →
    // picker reopens with the same game; BACK again → Sports.
    //
    // We persist just the game id (Int is trivially saveable);
    // SportsGame as a whole is not Parcelable. The id is looked up
    // against the fresh `playableGames` list on re-mount below.
    var pickerGameId by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf<Int?>(null)
    }
    val pickerGame: SportsGame? = remember(pickerGameId, playableGames) {
        val id = pickerGameId ?: return@remember null
        playableGames.firstOrNull { it.first.id == id }?.first
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F))
            .focusGroup(),
    ) {
        // ── Hero (top half) ──
        // v1.44.12 — Switched the page from absolute-positioned (top
        // half / overlap-bottom-half) to a Column with weighted
        // children. The previous layout used fixed offsets (350dp,
        // 420dp) which on TVs whose available page area is < ~720dp
        // tall pushed the cards row OFF the bottom of the screen.
        // weight() makes the layout adapt to whatever height we
        // actually have, so cards can NEVER overflow.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.55f),
        ) {
            SportsHeroLayer(
                heroItems = home?.hero ?: emptyList(),
                contentStartPadding = 96.dp,
                pinned = pinnedHero,
            )
        }

        // ── League pills + cards rail (bottom half) ──
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x0005080F),
                        0.18f to Color(0xCC05080F),
                        0.32f to Color(0xFF05080F),
                        1.0f to Color(0xFF05080F),
                    )
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                LeaguePillBar(
                pills = pills,
                selectedSlug = selectedLeague,
                onSelect = { newSlug ->
                    if (newSlug != selectedLeague) selectedLeague = newSlug
                },
                contentStartPadding = 96.dp,
                firstItemFocus = firstItemFocus,
                onUpFromRow = onUpFromRow,
                onDownFromRow = {
                    val hasCards = if (selectedLeague == "ppv") playablePpv.isNotEmpty()
                                   else playableGames.isNotEmpty()
                    if (hasCards) {
                        runCatching { railFocus.requestFocus() }
                            .onFailure {
                                com.hushtv.tv.data.EventLog.log(
                                    "sports",
                                    "railFocus.requestFocus failed: ${it.message}"
                                )
                                onDownFromRow?.invoke()
                            }
                    } else {
                        com.hushtv.tv.data.EventLog.log(
                            "sports",
                            "DOWN from pills with no cards → next page"
                        )
                        onDownFromRow?.invoke()
                    }
                },
            )

            // v1.44.12 — Cards rail occupies the remaining vertical
            // space after the pill row. Wrapping in a weight(1f) Box
            // means card-row height is bounded by the parent so cards
            // can never extend off-screen.
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                    // v1.44.11 — UP from a card now focuses the
                    // PILL ROW (not the page above). Skipping past
                    // the pills to Discovery was the user's complaint.
                    onUpFromCard = {
                        runCatching { firstItemFocus.requestFocus() }
                            .onFailure { onUpFromRow() }
                    },
                    onDownFromRow = onDownFromRow,
                )
            } else {
                GameCardsRail(
                    nav = nav,
                    playlistId = playlistId,
                    items = playableGames,
                    railFocus = railFocus,
                    onCardClick = { g -> pickerGameId = g.id },
                    onGameFocused = { g, ch ->
                        // v1.44.10 — Use short_name (e.g. "White Sox",
                        // "Angels") in the hero title. The previous
                        // version used full names ("Chicago White Sox
                        // @ Los Angeles Angels") which clipped at 56sp
                        // and even at 44sp can wrap. Short names fit
                        // comfortably on one line.
                        val awayName = g.away?.short_name?.takeIf { it.isNotBlank() }
                            ?: g.away?.name ?: "TBA"
                        val homeName = g.home?.short_name?.takeIf { it.isNotBlank() }
                            ?: g.home?.name ?: "TBA"
                        pinnedHero = SportsHero(
                            kind = "game",
                            id = g.id,
                            title = "$awayName  @  $homeName",
                            subtitle = g.league?.name,
                            image = g.home?.badge_url ?: g.home?.logo_url,
                            start_utc = g.start_utc,
                            channel = ch.title,
                            status = g.status,
                            score_home = g.score_home,
                            score_away = g.score_away,
                        )
                    },
                    // v1.44.11 — UP from a card now focuses the
                    // PILL ROW (not the page above).
                    onUpFromCard = {
                        runCatching { firstItemFocus.requestFocus() }
                            .onFailure { onUpFromRow() }
                    },
                    onDownFromRow = onDownFromRow,
                )
            }
            }  // close cards-rail weight(1f) Box
            }  // close inner Column
        }      // close bottom wrapper Box (weight 0.45)
    }          // close outer page Column

        // v1.44.27 — EPG channel picker sheet, overlays everything.
        if (pickerGame != null) {
            val gameForSheet = pickerGame
            GameChannelSheet(
                playlistId = playlistId,
                game = gameForSheet,
                onDismiss = { pickerGameId = null },
                onPlay = { _, url ->
                    val title =
                        "${gameForSheet.away?.short_name ?: gameForSheet.away?.name ?: "?"} @ " +
                            (gameForSheet.home?.short_name ?: gameForSheet.home?.name ?: "?")
                    // v1.44.31 — DO NOT clear pickerGameId here. We
                    // want it preserved so when the user presses BACK
                    // from the player, the picker reappears with the
                    // same game. The picker is dismissed only by an
                    // explicit DISMISS / Back press inside the sheet.
                    nav.navigate(
                        "player/$playlistId/" +
                            "${android.net.Uri.encode(url)}/" +
                            "${android.net.Uri.encode(title)}/true"
                    )
                },
            )
        }
    }  // close outer Box overlay wrapper
}

/** Build the static league-pill list. We always lead with All / Live
 *  / PPV, then the leagues the server reports as having games today,
 *  in their own ordering. */
private fun buildPills(
    leagues: List<Triple<String, String, String?>>,
): List<LeaguePill> {
    val out = mutableListOf<LeaguePill>()
    out += LeaguePill("all", "All", Cyan)
    out += LeaguePill("live", "Live", Color(0xFFEF4444))
    out += LeaguePill("ppv", "PPV", Color(0xFFE10600))
    leagues.forEach { (slug, name, logo) ->
        if (slug == "ppv" || slug == "all" || slug == "live") return@forEach
        val accent = LEAGUE_ACCENTS[slug] ?: Color(0xFF38BDF8)
        out += LeaguePill(slug, name, accent, logoUrl = logo)
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
    onCardClick: (SportsGame) -> Unit,
    onGameFocused: (SportsGame, MediaCard) -> Unit,
    onUpFromCard: () -> Unit,
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
                        com.hushtv.tv.data.EventLog.log("sports", "card click ${ch.title}")
                        // v1.44.27 — Show EPG channel picker instead
                        // of tuning straight to a guessed channel.
                        onCardClick(game)
                    },
                    focusRequester = if (idx == 0) railFocus else null,
                    onUpFromCard = onUpFromCard,
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
    onUpFromCard: () -> Unit,
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
                    onClick = {
                        com.hushtv.tv.data.EventLog.log("sports", "ppv click ${ch.title}")
                        playLiveChannel(ctx, nav, playlistId, ch)
                    },
                    focusRequester = if (idx == 0) railFocus else null,
                    onUpFromCard = onUpFromCard,
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
    // Wrap the entire navigation in runCatching so a malformed
    // streamUrl or a transient navigation race never crashes the
    // process. v1.44.3 — was a suspected crash path on the
    // sports-card → player navigation; defensive belt added.
    runCatching {
        com.hushtv.tv.data.EventLog.log(
            "sports",
            "play streamId=${ch.streamId} title=${ch.title}"
        )
        val p = PlaylistStore.find(ctx, playlistId) ?: run {
            com.hushtv.tv.data.EventLog.log("sports", "play aborted: playlist not found")
            return
        }
        val streamUrl = XtreamApi.liveUrl(p.host, p.username, p.password, ch.streamId)
        if (streamUrl.isBlank() || ch.streamId <= 0) {
            com.hushtv.tv.data.EventLog.log(
                "sports",
                "play aborted: blank/invalid streamUrl streamId=${ch.streamId}"
            )
            return
        }
        val encUrl = android.net.Uri.encode(streamUrl)
        val encTitle = android.net.Uri.encode(ch.title.ifBlank { "Sports" })
        nav.navigate("player/$playlistId/$encUrl/$encTitle/true")
    }.onFailure {
        com.hushtv.tv.data.EventLog.log(
            "sports",
            "play crashed: ${it.javaClass.simpleName}: ${it.message}"
        )
        android.util.Log.w("HushTVSports", "playLiveChannel failed", it)
    }
}
