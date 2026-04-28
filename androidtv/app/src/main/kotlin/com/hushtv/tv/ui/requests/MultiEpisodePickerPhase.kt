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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
 * Multi-Episode Picker — page 3 of the new "Request Missing Episodes"
 * flow. User picks a season on the left; episode rows render on the
 * right with checkboxes. They can select individual episodes or tick
 * "Select Whole Season" to grab the whole list. A footer pinned to
 * the bottom shows "{N} episodes selected" + a SUBMIT button.
 *
 * Per user requirement: selection is **per-season** — switching
 * seasons clears the previous season's selections. One submit equals
 * one season's worth of missing episodes, packed into a single
 * request via `seasons = "N"` and `episodes = "E04 — Foo, E05 — Bar"`.
 *
 * Layout:
 *
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │ ← Back   Pick missing episodes                                 │
 *   │           Gold Rush · 2010                                     │
 *   │                                                                │
 *   │ ┌──────────┐  ┌──────────────────────────────────────────────┐ │
 *   │ │ S1     ▶ │  │ [✓] Select Whole Season                      │ │
 *   │ │ S2       │  │ ──────────────────────────────────────────── │ │
 *   │ │ …        │  │ ☐ E01 · The Greenhorn                       │ │
 *   │ │ S15      │  │ ☐ E02 · My Father's Frenemy                 │ │
 *   │ │ S16      │  │ ☑ E03 · Pay Dirt                             │ │
 *   │ └──────────┘  └──────────────────────────────────────────────┘ │
 *   │                                                                │
 *   │ 1 episode selected                          [Submit Request →] │
 *   └────────────────────────────────────────────────────────────────┘
 *
 * The Xtream library cross-reference (from v1.42.50) is preserved —
 * episodes already present in the user's library show "IN LIBRARY"
 * pills and a quieter resting border, drawing the user's eye toward
 * the cyan-bordered MISSING rows.
 */
@Composable
fun MultiEpisodePickerPhase(
    pick: TmdbPick,
    playlistId: String,
    submitting: Boolean,
    onBack: () -> Unit,
    onSubmit: (season: Int, episodesLabel: String) -> Unit,
    onTapInLibraryEpisode: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tv by remember { mutableStateOf<TmdbTv?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var seasonDetail by remember { mutableStateOf<TmdbSeasonDetail?>(null) }
    var seasonLoading by remember { mutableStateOf(false) }
    var xtreamPresent by remember { mutableStateOf<Map<String, Set<Int>>?>(null) }
    // Selection is per-season — the Set holds episode_number values
    // and is reset every time selectedSeason changes.
    var selectedEpisodes by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val episodeListState = rememberLazyListState()
    val backFocus = remember { FocusRequester() }
    val firstSeasonFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }

    // Initial fetch — get the show's season list.
    LaunchedEffect(pick.tmdbId) {
        if (pick.tmdbId <= 0) {
            error = "TMDB metadata not available for this title."
            loading = false
            return@LaunchedEffect
        }
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

    // When the user picks a season, fetch its episodes AND clear any
    // selection from the previous season (per-season selection rule).
    LaunchedEffect(pick.tmdbId, selectedSeason) {
        val s = selectedSeason ?: return@LaunchedEffect
        seasonLoading = true
        seasonDetail = null
        selectedEpisodes = emptySet()
        runCatching {
            withContext(Dispatchers.IO) { TmdbService.getSeason(pick.tmdbId, s) }
        }.onSuccess { seasonDetail = it }
        seasonLoading = false
    }

    // Cross-reference the user's Xtream library so we can flag which
    // episodes they already have. Same approach as the v1.42.50
    // EpisodePickerPhase — falls back to empty map on any error so
    // the picker still works as a plain TMDB browser.
    LaunchedEffect(pick.tmdbId, pick.title) {
        val playlist = com.hushtv.tv.data.PlaylistStore.find(ctx, playlistId)
            ?: return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val resolved = com.hushtv.tv.data.XtreamApi.resolveSeriesInfo(
                    playlist.host, playlist.username, playlist.password,
                    seriesId = "0",          // forces title-search fallback
                    seriesName = pick.title,
                )
                resolved.info.episodes
                    ?.mapValues { (_, list) ->
                        list.mapNotNull { ep -> ep.episode_num.takeIf { it > 0 } }
                            .toSet()
                    }
                    ?: emptyMap()
            }
        }.onSuccess { xtreamPresent = it }
            .onFailure { xtreamPresent = emptyMap() }
    }

    // Auto-scroll to the first missing episode once both data sides
    // are loaded — same UX as v1.42.50.
    LaunchedEffect(seasonDetail, xtreamPresent, selectedSeason) {
        val episodes = seasonDetail?.episodes
            ?.filter { it.episode_number > 0 }
            ?: return@LaunchedEffect
        val present = xtreamPresent?.get(selectedSeason?.toString()).orEmpty()
        if (present.isEmpty()) return@LaunchedEffect
        val firstMissingIdx = episodes.indexOfFirst { it.episode_number !in present }
        if (firstMissingIdx > 0) {
            episodeListState.animateScrollToItem(firstMissingIdx)
        }
    }

    // Land focus on the back chip first; once seasons load, jump to
    // the first season chip so the user can dive in straight away.
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
            BackChipPill(focusRequester = backFocus, onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "PICK MISSING EPISODES",
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
                Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Cyan) }
            error != null -> Box(
                Modifier.fillMaxSize().weight(1f),
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

                Row(
                    Modifier.fillMaxWidth().weight(1f),
                ) {
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
                            else -> {
                                val episodes = seasonDetail!!.episodes
                                    .filter { it.episode_number > 0 }
                                val present = xtreamPresent
                                    ?.get(selectedSeason?.toString())
                                    .orEmpty()
                                val hasLibraryData = present.isNotEmpty()
                                EpisodeCheckboxList(
                                    episodes = episodes,
                                    selectedNums = selectedEpisodes,
                                    presentEpisodeNums = present,
                                    listState = episodeListState,
                                    onToggleEpisode = { num ->
                                        // Don't allow toggling for
                                        // episodes already in library —
                                        // those route to the player.
                                        if (hasLibraryData && num in present) {
                                            onTapInLibraryEpisode()
                                            return@EpisodeCheckboxList
                                        }
                                        selectedEpisodes =
                                            if (num in selectedEpisodes)
                                                selectedEpisodes - num
                                            else selectedEpisodes + num
                                    },
                                    onToggleSelectAll = {
                                        // "Whole season" only picks
                                        // episodes the user is MISSING.
                                        // In-library episodes are never
                                        // requestable.
                                        val requestable = episodes
                                            .map { it.episode_number }
                                            .filter {
                                                !hasLibraryData ||
                                                    it !in present
                                            }
                                            .toSet()
                                        selectedEpisodes =
                                            if (selectedEpisodes
                                                    .containsAll(requestable) &&
                                                requestable.isNotEmpty()
                                            ) emptySet()
                                            else requestable
                                    },
                                    isAllSelected = run {
                                        val requestable = episodes
                                            .map { it.episode_number }
                                            .filter {
                                                !hasLibraryData ||
                                                    it !in present
                                            }
                                            .toSet()
                                        requestable.isNotEmpty() &&
                                            selectedEpisodes
                                                .containsAll(requestable)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Footer ──
        Spacer(Modifier.height(14.dp))
        SubmitFooter(
            count = selectedEpisodes.size,
            submitting = submitting,
            focusRequester = submitFocus,
            onSubmit = {
                val s = selectedSeason ?: return@SubmitFooter
                val episodes = seasonDetail?.episodes
                    ?.filter { it.episode_number in selectedEpisodes }
                    ?.sortedBy { it.episode_number }
                    .orEmpty()
                if (episodes.isEmpty()) return@SubmitFooter
                val label = episodes.joinToString(", ") { ep ->
                    val cleanName = ep.name
                        .takeIf { it.isNotBlank() }
                        ?.let { " — $it" }
                        .orEmpty()
                    "E${ep.episode_number}$cleanName"
                }
                onSubmit(s, label)
            },
        )
    }
}

/* ─────────────────────────── Sub-pieces ─────────────────────────── */

@Composable
private fun BackChipPill(focusRequester: FocusRequester, onClick: () -> Unit) {
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
private fun EpisodeCheckboxList(
    episodes: List<TmdbEpisode>,
    selectedNums: Set<Int>,
    presentEpisodeNums: Set<Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isAllSelected: Boolean,
    onToggleEpisode: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
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
    val hasLibraryData = presentEpisodeNums.isNotEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp, bottom = 4.dp),
    ) {
        item("select-all") {
            SelectWholeSeasonChip(
                selected = isAllSelected,
                onClick = onToggleSelectAll,
            )
        }
        items(episodes, key = { it.id }) { ep ->
            val checked = ep.episode_number in selectedNums
            val isMissing = hasLibraryData && ep.episode_number !in presentEpisodeNums
            EpisodeCheckboxRow(
                episode = ep,
                checked = checked,
                isMissing = isMissing,
                showLibraryBadge = hasLibraryData,
                onToggle = { onToggleEpisode(ep.episode_number) },
            )
        }
    }
}

@Composable
private fun SelectWholeSeasonChip(
    selected: Boolean,
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
                    selected -> Cyan.copy(alpha = 0.12f)
                    else -> Color(0x10FFFFFF)
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    selected -> Cyan.copy(alpha = 0.6f)
                    else -> Color(0x22FFFFFF)
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckboxBox(checked = selected)
        Text(
            "Select Whole Season",
            color = if (focused || selected) Cyan else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun EpisodeCheckboxRow(
    episode: TmdbEpisode,
    checked: Boolean,
    isMissing: Boolean,
    showLibraryBadge: Boolean,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    // In-library episodes are NOT requestable. They render with a
    // play affordance instead of a checkbox; tapping the row jumps
    // the user into the series detail screen so they can hit play.
    val isInLibrary = showLibraryBadge && !isMissing
    val restingBorder = when {
        isInLibrary -> Color(0x3322C55E)
        checked -> Cyan.copy(alpha = 0.6f)
        !showLibraryBadge -> Color(0x22FFFFFF)
        isMissing -> Cyan.copy(alpha = 0.45f)
        else -> Color(0x14FFFFFF)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    isInLibrary -> Color(0x1422C55E)
                    checked -> Cyan.copy(alpha = 0.10f)
                    else -> SurfaceNavy
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    if (isInLibrary) Color(0xFF34D399) else Cyan
                } else restingBorder,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isInLibrary) {
            PlayAffordance(focused = focused)
        } else {
            CheckboxBox(checked = checked)
        }
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
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "E${episode.episode_number}",
                    color = if (isInLibrary) Color(0xFF34D399) else Cyan,
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showLibraryBadge) {
                    Spacer(Modifier.width(8.dp))
                    LibraryStatusPill(missing = isMissing)
                }
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
    }
}

/** Used in place of the checkbox on episodes the user already has in
 *  their Xtream library — visually distinct play glyph signals "tap
 *  me to watch instead of selecting for request". */
@Composable
private fun PlayAffordance(focused: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .size(22.dp)
            .background(
                if (focused) Color(0xFF34D399) else Color(0x3322C55E),
                shape,
            )
            .border(
                width = 2.dp,
                color = Color(0xFF34D399),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Triangle play glyph rendered as a centered "▶".
        Text(
            "▶",
            color = if (focused) Color(0xFF05080F) else Color(0xFF34D399),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun CheckboxBox(checked: Boolean) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .size(22.dp)
            .background(if (checked) Cyan else Color.Transparent, shape)
            .border(
                width = 2.dp,
                color = if (checked) Cyan else Color(0x99FFFFFF),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color(0xFF05080F),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Library-presence pill — "MISSING" (cyan) vs "IN LIBRARY" (green). */
@Composable
private fun LibraryStatusPill(missing: Boolean) {
    val (label, fg, bg) = if (missing) {
        Triple("MISSING", Cyan, Cyan.copy(alpha = 0.16f))
    } else {
        Triple("IN LIBRARY", Color(0xFF34D399), Color(0x2222C55E))
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun SubmitFooter(
    count: Int,
    submitting: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append(count)
                append(" episode")
                if (count != 1) append("s")
                append(" selected")
            },
            color = if (count > 0) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        SubmitButton(
            enabled = count > 0 && !submitting,
            loading = submitting,
            focusRequester = focusRequester,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun SubmitButton(
    enabled: Boolean,
    loading: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        !enabled -> Cyan.copy(alpha = 0.30f)
        focused -> Color.White
        else -> Cyan
    }
    Row(
        Modifier
            .height(46.dp)
            .background(bg, shape)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .clickableWithEnter { if (enabled) onClick() }
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color(0xFF05080F),
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            if (loading) "Submitting…" else "Submit Request",
            color = Color(0xFF05080F),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}
