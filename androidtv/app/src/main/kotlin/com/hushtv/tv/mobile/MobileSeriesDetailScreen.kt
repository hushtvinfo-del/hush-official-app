package com.hushtv.tv.mobile

import androidx.compose.foundation.background
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
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamEpisode
import com.hushtv.tv.ui.requests.RequestContentSheet
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mobile series detail — Netflix-style:
 *   • Backdrop hero with the series title/plot.
 *   • Horizontal season chip row (S1, S2, … – tap to switch).
 *   • Vertical list of episodes for the active season with tap-to-play.
 *
 * Picks the first season automatically on load. Uses the same
 * `get_series_info` API that TV uses (XtreamApi.getSeriesInfo) so no
 * new backend work.
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

    var loading by remember { mutableStateOf(true) }
    var seasonKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var episodesBySeason by remember { mutableStateOf<Map<String, List<XtreamEpisode>>>(emptyMap()) }
    var activeSeason by remember { mutableStateOf<String?>(null) }
    var showRequestModal by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId, seriesId) {
        if (playlist == null) { loading = false; return@LaunchedEffect }
        val info = runCatching {
            withContext(Dispatchers.IO) {
                XtreamApi.getSeriesInfo(playlist.host, playlist.username, playlist.password, seriesId)
            }
        }.getOrNull()
        val eps = info?.episodes.orEmpty()
        val keys = eps.keys.sortedWith(
            compareBy { it.toIntOrNull() ?: Int.MAX_VALUE },
        )
        seasonKeys = keys
        episodesBySeason = eps
        activeSeason = keys.firstOrNull()
        loading = false
    }

    // Single LazyColumn for the whole page so landscape can scroll past
    // the hero to reach the episode list. Hero is the first item with
    // a fixed height so it doesn't eat the entire viewport in landscape.
    val currentSeason = activeSeason
    val episodes = if (currentSeason != null) episodesBySeason[currentSeason].orEmpty() else emptyList()

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
                    if (!posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
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
                // Season chip row — sticky-feeling strip under the hero.
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

                items(episodes, key = { "ep-${it.id}-${it.episode_num}" }) { ep ->
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        EpisodeRow(ep) {
                            val p = playlist ?: return@EpisodeRow
                            val url = XtreamApi.episodeUrl(
                                p.host, p.username, p.password, ep.id, ep.container_extension,
                            )
                            val title = "$seriesName · S${ep.season ?: currentSeason ?: "?"}E${ep.episode_num}"
                            // Episode id is a string in Xtream's API. We
                            // hash it to an int so it fits WatchProgress's
                            // Int key, which is unique enough in practice.
                            val epIntId = ep.id.toIntOrNull() ?: ep.id.hashCode()
                            // Stash search context so the player can offer
                            // OpenSubtitles downloads with the right
                            // series/season/episode metadata.
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

                // Per-season footer CTA — for users whose Xtream
                // provider is missing one or more episodes from this
                // season. Pre-fills the request form with the show
                // name + season number so the user only has to type
                // which episode is missing.
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
        RequestContentSheet(
            presetType = "series",
            presetTitle = seriesName,
            presetSeason = currentSeason?.let { "Season $it" }.orEmpty(),
            onDismiss = { showRequestModal = false },
            onViewMyRequests = {
                showRequestModal = false
                nav.navigate("mrequests")
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
