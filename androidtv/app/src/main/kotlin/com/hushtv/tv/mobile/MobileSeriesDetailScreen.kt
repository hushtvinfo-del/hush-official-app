package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.RpdbService
import com.hushtv.tv.data.TmdbEpisode
import com.hushtv.tv.data.TmdbSeasonDetail
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.TmdbTv
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamEpisode
import com.hushtv.tv.ui.requests.RequestContentSheet
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mobile series detail — Netflix-style:
 *   • Backdrop hero with the series title/plot. Backdrop prefers the
 *     RPDB rating-baked variant (IMDb / Rotten Tomatoes / Metacritic /
 *     TMDB scores embedded in the artwork) when we resolve the IMDb
 *     id, falls back to the caller-supplied poster, falls back to a
 *     gradient.
 *   • Horizontal season chip row. Seasons come from the union of
 *     Xtream and TMDB so brand-new airing seasons show up even when
 *     the user's provider hasn't indexed them yet.
 *   • Vertical list of episodes for the active season:
 *       1. Xtream has episodes → playable rows with thumbnail + tap
 *          to open the player.
 *       2. Xtream empty, TMDB has episodes → REQUEST rows. Tapping
 *          opens the request modal pre-filled with the exact episode
 *          (e.g. "E04 — The Last Bonanza") so the user only has to
 *          hit Submit.
 *       3. Both empty → friendly empty-season card with the same
 *          request CTA.
 *
 * Uses the same `get_series_info` API that TV uses, plus TMDB's
 * search/tv-detail/season endpoints, plus the existing RPDB service.
 */
@Composable
fun MobileSeriesDetailScreen(
    nav: NavController,
    playlistId: String,
    seriesId: String,
    seriesName: String,
    posterUrl: String?,
) {
    val ctx = LocalContext.current
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    // ── Xtream state ──
    var loading by remember { mutableStateOf(true) }
    var xtreamSeasonKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var episodesBySeason by remember { mutableStateOf<Map<String, List<XtreamEpisode>>>(emptyMap()) }
    var activeSeason by remember { mutableStateOf<String?>(null) }

    // ── TMDB state — backfills missing seasons / episodes / RPDB id ──
    var tmdbTv by remember { mutableStateOf<TmdbTv?>(null) }
    var tmdbSeason by remember { mutableStateOf<TmdbSeasonDetail?>(null) }

    // ── Request modal state ──
    var showRequestModal by remember { mutableStateOf(false) }
    // Captures the TMDB-only episode label the user tapped so the
    // modal pre-fills with the EXACT episode rather than just the
    // season. Reset on every modal dismiss path so the next generic
    // "Missing an episode? Request it" footer button doesn't
    // accidentally inherit it.
    var presetEpisodeText by remember { mutableStateOf("") }

    // 1️⃣ Xtream series_info — primary data source.
    LaunchedEffect(playlistId, seriesId) {
        if (playlist == null) { loading = false; return@LaunchedEffect }
        val info = runCatching {
            withContext(Dispatchers.IO) {
                // Disambiguating resolver: when search-flow lands on
                // a stale duplicate series_id (some Xtream providers
                // index the same show under multiple categories with
                // a different id per category, and only one of those
                // ids has episodes loaded), this auto-retries against
                // other entries with the same normalised title until
                // one returns episodes.
                XtreamApi.resolveSeriesInfo(
                    playlist.host, playlist.username, playlist.password,
                    seriesId, seriesName,
                ).info
            }
        }.getOrNull()
        val eps = info?.episodes.orEmpty()
        val keys = eps.keys.sortedWith(
            compareBy { it.toIntOrNull() ?: Int.MAX_VALUE },
        )
        xtreamSeasonKeys = keys
        episodesBySeason = eps
        activeSeason = keys.firstOrNull()
        loading = false
    }

    // 2️⃣ TMDB show detail — search by name, fetch detail for backdrop,
    // imdb_id and full season list. Fires once per series visit.
    LaunchedEffect(seriesName) {
        runCatching {
            withContext(Dispatchers.IO) {
                val id = TmdbService.searchTv(seriesName) ?: return@withContext null
                TmdbService.getTv(id)
            }
        }.onSuccess { tmdbTv = it }
    }

    // Combined season keys: Xtream first (in their natural order),
    // then any TMDB-only seasons appended after. Falling back entirely
    // to TMDB when Xtream returned nothing keeps the UI useful even
    // for series the provider hasn't indexed at all.
    val seasonKeys = remember(xtreamSeasonKeys, tmdbTv) {
        val tmdbKeys = tmdbTv?.seasons
            ?.filter { it.season_number > 0 }
            ?.map { it.season_number.toString() }
            .orEmpty()
        if (xtreamSeasonKeys.isEmpty()) tmdbKeys
        else xtreamSeasonKeys + tmdbKeys.filter { it !in xtreamSeasonKeys }
    }
    // If we just learned about more seasons (TMDB) and the user
    // hasn't picked one yet, pre-select the first.
    LaunchedEffect(seasonKeys) {
        if (activeSeason == null) activeSeason = seasonKeys.firstOrNull()
    }

    // 3️⃣ TMDB season detail — fetches when active season changes so
    // we have episode names + stills + overviews for the current
    // season's TMDB-only fallback rows.
    LaunchedEffect(tmdbTv?.id, activeSeason) {
        val tvId = tmdbTv?.id
        val seasonNum = activeSeason?.toIntOrNull()
        if (tvId == null || seasonNum == null) {
            tmdbSeason = null
            return@LaunchedEffect
        }
        runCatching {
            withContext(Dispatchers.IO) { TmdbService.getSeason(tvId, seasonNum) }
        }.onSuccess { tmdbSeason = it }
    }

    val currentSeason = activeSeason
    val xtEpisodes = if (currentSeason != null) {
        episodesBySeason[currentSeason].orEmpty()
    } else emptyList()
    val tmdbEpisodes = tmdbSeason?.episodes
        ?.filter { it.episode_number > 0 }
        .orEmpty()

    // Backdrop URL: RPDB rating-baked → caller poster → null.
    val rpdbBackdrop = RpdbService.backgroundUrl(tmdbTv?.external_ids?.imdb_id)
    var useFallbackBackdrop by remember(tmdbTv?.external_ids?.imdb_id) {
        mutableStateOf(rpdbBackdrop.isNullOrBlank())
    }
    val heroUrl = if (useFallbackBackdrop || rpdbBackdrop.isNullOrBlank()) posterUrl
                  else rpdbBackdrop

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
        ) {
            // ── Backdrop hero ──
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                ) {
                    if (!heroUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = heroUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = { useFallbackBackdrop = true },
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF05080F))
                                ))
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(
                            0f to Color(0x66000000),
                            0.5f to Color.Transparent,
                            1f to Color(0xFF05080F),
                        ))
                    )
                    Text(
                        seriesName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }

            // ── Loading / empty states ──
            if (loading) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Cyan)
                    }
                }
            } else if (seasonKeys.isEmpty()) {
                item {
                    Text(
                        "No episodes found for this series.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                // Season chip row.
                if (seasonKeys.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasonKeys, key = { it }) { s ->
                                SeasonChip(
                                    label = "Season $s",
                                    selected = s == activeSeason,
                                    onClick = { activeSeason = s },
                                )
                            }
                        }
                    }
                } else {
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // ── Episode list — 3-way render ──
                if (xtEpisodes.isNotEmpty()) {
                    items(xtEpisodes, key = { "xt-${it.id}-${it.episode_num}" }) { ep ->
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            EpisodeRow(ep) {
                                val p = playlist ?: return@EpisodeRow
                                val url = XtreamApi.episodeUrl(
                                    p.host, p.username, p.password, ep.id, ep.container_extension,
                                )
                                val title = "$seriesName · S${ep.season ?: currentSeason ?: "?"}E${ep.episode_num}"
                                val epIntId = ep.id.toIntOrNull() ?: ep.id.hashCode()
                                com.hushtv.tv.data.SubtitleSearchContext.set(
                                    com.hushtv.tv.data.SubtitleSearchContext.Query(
                                        title = seriesName,
                                        seasonNumber = ep.season ?: currentSeason?.toIntOrNull(),
                                        episodeNumber = ep.episode_num.takeIf { it > 0 },
                                        kind = "episode",
                                        streamUrl = url,
                                    ),
                                )
                                nav.navigate(
                                    mobilePlayerRoute(
                                        playlistId = playlistId,
                                        streamUrl = url,
                                        channelName = title,
                                        isLive = false,
                                        vodStreamId = epIntId,
                                        vodKind = "series",
                                        vodPoster = ep.info?.movie_image ?: posterUrl,
                                    ),
                                )
                            }
                        }
                    }
                } else if (tmdbEpisodes.isNotEmpty()) {
                    // TMDB-only fallback — provider hasn't indexed
                    // this season yet. Tapping any row pre-fills the
                    // request modal with the exact episode.
                    item {
                        Text(
                            "Your provider hasn't indexed Season $currentSeason yet — " +
                                "tap any episode to request it.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    items(
                        tmdbEpisodes,
                        key = { "tmdb-${it.id}-${it.episode_number}" },
                    ) { ep ->
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            TmdbOnlyEpisodeRow(ep) {
                                val name = ep.name.takeIf { it.isNotBlank() }
                                    ?.let { " — $it" }.orEmpty()
                                presetEpisodeText = "E${ep.episode_number}$name"
                                showRequestModal = true
                            }
                        }
                    }
                } else {
                    item {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            EmptySeasonCard(
                                seasonNum = currentSeason,
                                onRequest = { showRequestModal = true },
                            )
                        }
                    }
                }

                // Per-season footer CTA — generic "missing an episode"
                // request that pre-fills only the show + season. The
                // TmdbOnlyEpisodeRow path above handles the
                // exact-episode pre-fill.
                item {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(Cyan.copy(alpha = 0.16f))
                                .clickable { showRequestModal = true }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Missing an episode? Request it",
                                color = Cyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // Floating back button — always reachable even when scrolled
        // deep into episodes.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xCC000000))
                .clickable { nav.popBackStack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }

    // Request modal — full-screen scrim overlay, dismissed via the
    // sheet's own Cancel / Close buttons.
    if (showRequestModal) {
        com.hushtv.tv.ui.requests.RequestContentSheet(
            presetType = "series",
            presetTitle = seriesName,
            presetSeason = currentSeason?.let { "Season $it" }.orEmpty(),
            presetEpisode = presetEpisodeText,
            playlistId = playlistId,
            onDismiss = {
                showRequestModal = false
                presetEpisodeText = ""
            },
            onViewMyRequests = {
                showRequestModal = false
                presetEpisodeText = ""
                nav.navigate("mrequests/$playlistId")
            },
            onAlreadyAvailable = { entry ->
                showRequestModal = false
                presetEpisodeText = ""
                if (entry.kind == "series") {
                    nav.navigate(
                        mobileSeriesRoute(
                            playlistId = playlistId,
                            seriesId = entry.seriesId.toString(),
                            name = entry.title,
                            poster = entry.poster,
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Cyan.copy(alpha = 0.24f) else Color(0x18FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Cyan else Color(0xFFE5E7EB),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun EpisodeRow(ep: XtreamEpisode, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1220))
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 92.dp, height = 54.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            val img = ep.info?.movie_image
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "E${ep.episode_num}",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Icon(
                Icons.Default.PlayArrow, null, tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "E${ep.episode_num} · ${ep.title.ifBlank { "Untitled" }}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val plot = ep.info?.plot
            if (!plot.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    plot,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val dur = ep.info?.duration
            if (!dur.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    dur,
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * TMDB-only episode row — shown when the user's Xtream provider
 * has no episodes for the active season (e.g. a season just-airing
 * the provider hasn't indexed yet). Same shape as the playable
 * [EpisodeRow] but the corner badge says REQUEST instead of PLAY,
 * and tapping calls back to the request modal handler with the
 * exact episode label captured.
 */
@Composable
private fun TmdbOnlyEpisodeRow(
    ep: TmdbEpisode,
    onRequest: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(10.dp))
            .clickable(onClick = onRequest)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 92.dp, height = 54.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            val still = TmdbService.img(ep.still_path, "w300")
            if (!still.isNullOrBlank()) {
                AsyncImage(
                    model = still,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "E${ep.episode_number}",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            // REQUEST badge top-left so the row reads as
            // "tap to request" rather than "tap to play".
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color(0xCC05080F), RoundedCornerShape(4.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    "REQUEST",
                    color = Cyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val name = ep.name.ifBlank { "Episode ${ep.episode_number}" }
            Text(
                "E${ep.episode_number} · $name",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val plot = ep.overview.orEmpty()
            if (plot.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    plot,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val airDate = ep.air_date.orEmpty()
            if (airDate.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    airDate,
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Empty-state card shown when neither Xtream nor TMDB has any
 * episode metadata for the currently-selected season. Tapping
 * opens the generic per-season request modal.
 */
@Composable
private fun EmptySeasonCard(
    seasonNum: String?,
    onRequest: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onRequest)
            .padding(16.dp),
    ) {
        Text(
            "No episodes for Season ${seasonNum ?: "—"} yet",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Your provider hasn't loaded this season into the catalog. Tap to ask our team to add it.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}
