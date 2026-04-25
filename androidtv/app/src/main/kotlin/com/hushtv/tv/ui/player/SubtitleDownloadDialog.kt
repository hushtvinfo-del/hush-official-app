package com.hushtv.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.OpenSubtitlesApi
import com.hushtv.tv.data.SubtitleLangPrefStore
import com.hushtv.tv.data.SubtitleSearchContext
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manual subtitle picker dialog — used when the user wants to swap to
 * a different language than the one auto-loaded by the player, or
 * when they triggered the picker explicitly (long-press CC chip on
 * mobile, or Player Options → Subtitles → Download on TV).
 *
 * The default user flow does NOT open this dialog: tapping CC quietly
 * downloads the most-popular English SRT in the background. This
 * picker exists for power users who want to choose manually.
 */
@Composable
fun SubtitleDownloadDialog(
    query: SubtitleSearchContext.Query,
    onDismiss: () -> Unit,
    onPicked: (File, String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var lang by remember { mutableStateOf(SubtitleLangPrefStore.get(ctx)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<OpenSubtitlesApi.Hit>>(emptyList()) }
    var downloadingFileId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(lang, query) {
        loading = true
        error = null
        val results = withContext(Dispatchers.IO) {
            runCatching {
                if (query.kind == "episode" &&
                    query.seasonNumber != null && query.episodeNumber != null
                ) {
                    OpenSubtitlesApi.searchEpisode(
                        seriesTitle = query.title,
                        seasonNumber = query.seasonNumber,
                        episodeNumber = query.episodeNumber,
                        languages = listOf(lang),
                    )
                } else {
                    OpenSubtitlesApi.searchMovie(
                        title = query.title,
                        year = query.year,
                        languages = listOf(lang),
                    )
                }
            }.getOrNull()
        }
        loading = false
        if (results == null) {
            error = "Couldn't reach OpenSubtitles. Check your connection."
            hits = emptyList()
        } else {
            hits = results
            if (results.isEmpty()) error = "No subtitles found in ${lang.uppercase()}."
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .clickable(enabled = false) {},
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .background(SurfaceNavy, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(28.dp),
        ) {
            Text(
                "Download subtitles",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(query.title)
                    if (query.kind == "episode" &&
                        query.seasonNumber != null && query.episodeNumber != null
                    ) {
                        append("  ·  S${query.seasonNumber.toString().padStart(2, '0')}")
                        append("E${query.episodeNumber.toString().padStart(2, '0')}")
                    } else if (query.year != null) {
                        append("  ·  ${query.year}")
                    }
                },
                color = TextSecondary,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en", "es", "fr", "de", "it", "pt", "ar").forEach { code ->
                    val active = code == lang
                    Box(
                        Modifier
                            .clickable {
                                if (code != lang) {
                                    lang = code
                                    SubtitleLangPrefStore.set(ctx, code)
                                }
                            }
                            .background(
                                if (active) Cyan.copy(alpha = 0.22f) else SurfaceElev,
                                RoundedCornerShape(10.dp),
                            )
                            .border(
                                if (active) 1.dp else 0.dp,
                                if (active) Cyan else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            code.uppercase(),
                            color = if (active) Cyan else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Cyan)
                }
                error != null -> Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        error ?: "",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(hits, key = { it.fileId }) { hit ->
                        val isDownloading = downloadingFileId == hit.fileId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = downloadingFileId == null) {
                                    downloadingFileId = hit.fileId
                                    scope.launch {
                                        val file = withContext(Dispatchers.IO) {
                                            OpenSubtitlesApi.fetchSrt(ctx, hit.fileId)
                                        }
                                        downloadingFileId = null
                                        if (file != null) {
                                            onPicked(file, hit.language)
                                        } else {
                                            error = "Download failed. Daily limit reached?"
                                        }
                                    }
                                }
                                .background(SurfaceElev, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hit.release.ifEmpty { hit.featureTitle },
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        hit.language.uppercase(),
                                        color = Cyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (hit.hd) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("HD", color = Color(0xFF22D3EE), fontSize = 11.sp)
                                    }
                                    if (hit.fromTrusted) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("✓ Trusted", color = Color(0xFF34D399), fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "${hit.downloadCount} downloads",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    color = Cyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text(
                                    "DOWNLOAD",
                                    color = Cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .background(SurfaceElev, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Close",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
