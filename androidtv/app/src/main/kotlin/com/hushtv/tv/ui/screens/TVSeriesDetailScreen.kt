package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamEpisode
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.launch

@Composable
fun TVSeriesDetailScreen(
    nav: NavController,
    playlistId: String,
    seriesId: String,
    seriesName: String
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }
    var seasons by remember { mutableStateOf<Map<String, List<XtreamEpisode>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(seriesId) {
        val p = playlist ?: return@LaunchedEffect
        scope.launch {
            runCatching {
                XtreamApi.getSeriesInfo(p.host, p.username, p.password, seriesId)
            }.onSuccess { info ->
                seasons = info.episodes ?: emptyMap()
                loading = false
            }.onFailure { loading = false }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x1AFFFFFF),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusable(shape = CircleShape)
                        .clickableWithEnter { nav.popBackStack() }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Text(seriesName, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            }

            if (loading) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Loading episodes…", color = TextSecondary, fontSize = 20.sp)
                }
            } else if (seasons.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No episodes available", color = Color(0xFF6B7280), fontSize = 20.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 64.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    seasons.toSortedMap().forEach { (seasonKey, eps) ->
                        item {
                            Text(
                                "Season $seasonKey",
                                color = Cyan,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(eps) { ep ->
                            EpisodeRow(ep) {
                                val p = playlist ?: return@EpisodeRow
                                val url = XtreamApi.episodeUrl(
                                    p.host, p.username, p.password, ep.id, ep.container_extension
                                )
                                val t = "$seriesName — S${ep.season ?: seasonKey} E${ep.episode_num}"
                                nav.navigate("player/$playlistId/${Uri.encode(url)}/${Uri.encode(t)}/false")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: XtreamEpisode, onClick: () -> Unit) {
    Surface(
        color = Color(0x14FFFFFF),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .tvFocusable(shape = RoundedCornerShape(12.dp))
            .clickableWithEnter(onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).background(Cyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("E${ep.episode_num}  ${ep.title}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                val dur = ep.info?.duration
                if (!dur.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(dur, color = TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}
