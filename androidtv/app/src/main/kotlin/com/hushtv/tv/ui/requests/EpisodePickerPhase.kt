package com.hushtv.tv.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.data.TmdbEpisode
import com.hushtv.tv.data.TmdbSeasonDetail
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.TmdbTv
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Episode picker — drilling-down request flow.
 *
 * Used inside the [RequestContentSheet] when the user clicks the
 * "PICK EPISODE →" affordance on a series TMDB result. Drives a
 * 2-pane layout that fits inside the existing modal viewport with
 * NO outer scrolling (per user requirement: "very clean in the
 * same page not causing any scrolling or overlapping etc").
 *
 * ┌───────────────────────────────────────────────────────────────┐
 * │ ←  Pick a missing episode                                     │
 * │    Gold Rush · 2010                                           │
 * │                                                               │
 * │ ┌────────┐  ┌─────────────────────────────────────────────┐  │
 * │ │ S1     │  │ ┌──────┐ E01 · The Greenhorn                │  │
 * │ │ S2     │  │ │still │ Sep 22, 2024 · 42m                  │  │
 * │ │  …     │  │ └──────┘                                    │  │
 * │ │ S15  ▶ │  │ ┌──────┐ E02 · My Father's Frenemy           │  │
 * │ │ S16    │  │ │still │ Sep 29, 2024                        │  │
 * │ └────────┘  └─────────────────────────────────────────────┘  │
 * └───────────────────────────────────────────────────────────────┘
 *
 * Navigation:
 *   • UP/DOWN within either column = navigate items.
 *   • LEFT/RIGHT = jump between season chips and episode list.
 *   • ENTER on a season = load its episodes.
 *   • ENTER on an episode = submit the request (one tap, no
 *     manual season/episode entry).
 *
 * The picker fetches season metadata via [TmdbService.getTv] (one
 * call) then per-season episodes via [TmdbService.getSeason] when
 * the user picks a season chip. Each network call shows a small
 * spinner inside the right pane only — the season list never
 * disappears.
 */
@Composable
fun EpisodePickerPhase(
    pick: TmdbPick,
    onBack: () -> Unit,
    onSubmitEpisode: (season: Int, episodeLabel: String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tv by remember { mutableStateOf<TmdbTv?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var seasonDetail by remember { mutableStateOf<TmdbSeasonDetail?>(null) }
    var seasonLoading by remember { mutableStateOf(false) }

    val backFocus = remember { FocusRequester() }
    val firstSeasonFocus = remember { FocusRequester() }
    val firstEpisodeFocus = remember { FocusRequester() }

    // Initial fetch — get the show's season list.
    LaunchedEffect(pick.tmdbId) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { TmdbService.getTv(pick.tmdbId) }
        }.onSuccess { fetched ->
            tv = fetched
            // Auto-select the latest non-zero season — that's where
            // a missing episode is most likely to be.
            val firstNonZero = fetched?.seasons
                ?.map { it.season_number }
                ?.filter { it > 0 }
                ?.maxOrNull()
            selectedSeason = firstNonZero
            loading = false
        }.onFailure {
            error = "Couldn't load seasons. Please try again."
            loading = false
        }
    }

    // When the user picks a season, fetch its episodes.
    LaunchedEffect(pick.tmdbId, selectedSeason) {
        val s = selectedSeason ?: return@LaunchedEffect
        seasonLoading = true
        seasonDetail = null
        runCatching {
            withContext(Dispatchers.IO) { TmdbService.getSeason(pick.tmdbId, s) }
        }.onSuccess { seasonDetail = it }
        seasonLoading = false
    }

    // Land focus on the back chip first, then move into the picker
    // once the data arrives.
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { backFocus.requestFocus() }
    }
    LaunchedEffect(loading, tv) {
        if (!loading && tv != null) {
            delay(120)
            runCatching { firstSeasonFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Top bar ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChip(focusRequester = backFocus, onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "PICK A MISSING EPISODE",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(pick.title)
                        if (pick.year != null) append(" · ${pick.year}")
                    },
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Cyan) }
            error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    error ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            tv != null -> {
                val seasons = tv!!.seasons.orEmpty()
                    .filter { it.season_number > 0 || it.episode_count > 0 }
                    .sortedBy { it.season_number }

                Row(Modifier.fillMaxSize()) {
                    // ── Season list (left) ──
                    SeasonList(
                        seasons = seasons.map { it.season_number to it.episode_count },
                        selected = selectedSeason,
                        firstFocus = firstSeasonFocus,
                        onPick = { selectedSeason = it },
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight(),
                    )
                    Spacer(Modifier.width(16.dp))
                    // ── Episode list (right) ──
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            seasonLoading -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = Cyan) }
                            seasonDetail == null -> Text(
                                "Pick a season on the left to see its episodes.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(20.dp),
                            )
                            else -> EpisodeList(
                                episodes = seasonDetail!!.episodes
                                    .filter { it.episode_number > 0 },
                                firstFocus = firstEpisodeFocus,
                                onPick = { ep ->
                                    val cleanName = ep.name
                                        .takeIf { it.isNotBlank() }
                                        ?.let { " — $it" }
                                        .orEmpty()
                                    val label = "E${ep.episode_number}$cleanName"
                                    onSubmitEpisode(
                                        selectedSeason ?: ep.season_number,
                                        label,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}


/* ─────────────────────────── Sub-pieces ─────────────────────────── */

@Composable
private fun BackChip(focusRequester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(36.dp)
            .background(
                if (focused) Cyan.copy(alpha = 0.18f) else Color(0x14FFFFFF),
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x22FFFFFF),
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            "←",
            color = if (focused) Cyan else TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Back",
            color = if (focused) Cyan else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun SeasonList(
    seasons: List<Pair<Int, Int>>,
    selected: Int?,
    firstFocus: FocusRequester,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selected, seasons) {
        if (selected != null) {
            val idx = seasons.indexOfFirst { it.first == selected }
            if (idx >= 0) state.animateScrollToItem(idx.coerceAtLeast(0))
        }
    }
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .padding(8.dp),
    ) {
        Text(
            "SEASONS",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
        )
        LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(seasons, key = { it.first }) { (num, count) ->
                val isFirst = seasons.firstOrNull()?.first == num
                SeasonChip(
                    seasonNum = num,
                    episodeCount = count,
                    selected = num == selected,
                    focusRequester = if (isFirst) firstFocus else null,
                    onClick = { onPick(num) },
                )
            }
        }
    }
}

@Composable
private fun SeasonChip(
    seasonNum: Int,
    episodeCount: Int,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                when {
                    focused -> Cyan.copy(alpha = 0.22f)
                    selected -> Cyan.copy(alpha = 0.10f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                color = when {
                    focused -> Cyan
                    selected -> Cyan.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (seasonNum == 0) "Specials" else "Season $seasonNum",
            color = if (focused || selected) Cyan else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$episodeCount",
            color = if (focused || selected) Cyan else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EpisodeList(
    episodes: List<TmdbEpisode>,
    firstFocus: FocusRequester,
    onPick: (TmdbEpisode) -> Unit,
) {
    if (episodes.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No episodes listed for this season yet.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(episodes, key = { it.id }) { ep ->
            val isFirst = ep == episodes.firstOrNull()
            EpisodeRow(
                episode = ep,
                focusRequester = if (isFirst) firstFocus else null,
                onPick = { onPick(ep) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: TmdbEpisode,
    focusRequester: FocusRequester?,
    onPick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x22FFFFFF),
                shape = shape,
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onPick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElev),
            contentAlignment = Alignment.Center,
        ) {
            val still = TmdbService.img(episode.still_path, "w300")
            if (!still.isNullOrBlank()) {
                AsyncImage(
                    model = still,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "E${episode.episode_number}",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "E${episode.episode_number}",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    episode.name.takeIf { it.isNotBlank() }
                        ?: "Episode ${episode.episode_number}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val airDate = episode.air_date.orEmpty()
            val runtime = episode.runtime?.takeIf { it > 0 }?.let { "${it}m" }.orEmpty()
            if (airDate.isNotBlank() || runtime.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        airDate.takeIf { it.isNotBlank() },
                        runtime.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            val plot = episode.overview.orEmpty()
            if (plot.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    plot,
                    color = Color(0xFFB8BDC7),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "→",
            color = if (focused) Cyan else TextSecondary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
