package com.hushtv.tv.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material.icons.outlined.PlayArrow
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
import com.hushtv.tv.data.TmdbService
import com.hushtv.tv.data.TmdbTv
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Series Detail phase — the "in-modal landing page" for a series the
 * user picked from search. Replaces the old inline "PICK EPISODE →"
 * chip on the result row (which had broken D-pad focus + ugly UX).
 *
 * Layout (left/right split, fits in the modal viewport with no
 * outer scrolling):
 *
 *   ┌───────────────────────────────────────────────────────────────┐
 *   │ ←  Back                                                       │
 *   │                                                               │
 *   │ ┌──────────┐   GOLD RUSH                                      │
 *   │ │          │   2010 · Drama, Reality                          │
 *   │ │  poster  │                                                  │
 *   │ │  2:3     │   Synopsis paragraphs, scrollable if long…      │
 *   │ │          │                                                  │
 *   │ │          │   [▶ Tap to Watch]   (only if in library)        │
 *   │ │          │   [+ Request Whole Series]                       │
 *   │ └──────────┘   [+ Request Missing Episodes]                   │
 *   └───────────────────────────────────────────────────────────────┘
 *
 * Behaviour summary:
 *  • "Tap to Watch" only renders when [pick.library] is non-null
 *    (i.e. the user already has the show). Calls [onTapToWatch]
 *    which the parent uses to dismiss the modal and deep-link the
 *    user into their library entry.
 *  • "Request Whole Series" submits a full-series request via
 *    [onRequestWholeSeries].
 *  • "Request Missing Episodes" routes to the multi-episode picker
 *    via [onRequestMissingEpisodes].
 *  • TMDB metadata (synopsis, year, genres) is fetched in a
 *    LaunchedEffect — the screen renders the static title + poster
 *    immediately, then enriches.
 */
@Composable
fun SeriesDetailPhase(
    pick: TmdbPick,
    onBack: () -> Unit,
    onTapToWatch: () -> Unit,
    onRequestWholeSeries: () -> Unit,
    onRequestMissingEpisodes: () -> Unit,
) {
    var tv by remember(pick.tmdbId) { mutableStateOf<TmdbTv?>(null) }

    LaunchedEffect(pick.tmdbId) {
        if (pick.tmdbId <= 0) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) { TmdbService.getTv(pick.tmdbId) }
        }.onSuccess { tv = it }
    }

    val backFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(220)
        runCatching { backFocus.requestFocus() }
    }
    // Once content is ready, lift focus down to the primary CTA so
    // the user can ENTER without an extra DOWN press.
    LaunchedEffect(tv) {
        if (tv != null) {
            delay(140)
            runCatching { primaryFocus.requestFocus() }
        }
    }

    val inLibrary = pick.library != null

    Column(Modifier.fillMaxSize()) {
        // ── Top bar ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChipPill(focusRequester = backFocus, onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Text(
                "REQUEST CONTENT",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxSize()) {
            // ── Left: poster ──
            Box(
                Modifier
                    .width(280.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElev),
                contentAlignment = Alignment.Center,
            ) {
                val url = TmdbService.img(pick.posterPath, "w500")
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        pick.title.take(1).uppercase(),
                        color = TextSecondary,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Spacer(Modifier.width(28.dp))

            // ── Right: metadata + CTAs ──
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                Text(
                    pick.title,
                    color = TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 38.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val metaLine = buildString {
                    pick.year?.let { append(it) }
                    val genres = tv?.genres
                        ?.map { it.name }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (genres.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(genres.take(3).joinToString(", "))
                    }
                    val seasonCount = tv?.seasons?.count { it.season_number > 0 }
                    if (seasonCount != null && seasonCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("$seasonCount season${if (seasonCount == 1) "" else "s"}")
                    }
                }
                if (metaLine.isNotBlank()) {
                    Text(
                        metaLine,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (inLibrary) {
                    Spacer(Modifier.height(10.dp))
                    InLibraryBadge()
                }

                Spacer(Modifier.height(16.dp))

                // ── Synopsis (scrollable to keep CTAs always visible) ──
                val overview = (tv?.overview ?: pick.overview).orEmpty()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        overview.isNotBlank() -> Text(
                            overview,
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                        tv == null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                color = Cyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "Loading details…",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        else -> Text(
                            "No synopsis available.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── CTAs ──
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (inLibrary) {
                        ActionButton(
                            label = "Tap to Watch",
                            icon = Icons.Outlined.PlayArrow,
                            primary = true,
                            focusRequester = primaryFocus,
                            onClick = onTapToWatch,
                        )
                        ActionButton(
                            label = "Request Missing Episodes",
                            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                            primary = false,
                            focusRequester = null,
                            onClick = onRequestMissingEpisodes,
                        )
                        ActionButton(
                            label = "Request Whole Series",
                            icon = Icons.Outlined.LibraryAddCheck,
                            primary = false,
                            focusRequester = null,
                            onClick = onRequestWholeSeries,
                        )
                    } else {
                        ActionButton(
                            label = "Request Whole Series",
                            icon = Icons.Outlined.Add,
                            primary = true,
                            focusRequester = primaryFocus,
                            onClick = onRequestWholeSeries,
                        )
                        ActionButton(
                            label = "Request Missing Episodes",
                            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                            primary = false,
                            focusRequester = null,
                            onClick = onRequestMissingEpisodes,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InLibraryBadge() {
    Box(
        Modifier
            .background(Color(0x2222C55E), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF34D399).copy(alpha = 0.5f),
                RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            "ALREADY IN YOUR LIBRARY",
            color = Color(0xFF34D399),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        primary && focused -> Color.White
        primary -> Cyan
        focused -> Cyan.copy(alpha = 0.22f)
        else -> Color(0x14FFFFFF)
    }
    val borderColor = when {
        primary && focused -> Cyan
        focused -> Cyan
        primary -> Color.Transparent
        else -> Cyan.copy(alpha = 0.45f)
    }
    val textColor = if (primary) Color(0xFF05080F) else if (focused) Cyan else TextPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(bg, shape)
            .border(
                width = if (focused) 2.dp else if (primary) 0.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
}

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
