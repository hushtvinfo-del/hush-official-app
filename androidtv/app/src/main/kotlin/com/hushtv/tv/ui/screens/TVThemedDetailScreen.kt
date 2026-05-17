package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.HushThemedLists
import com.hushtv.tv.data.LibraryIndex
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.ThemedHeroMetaCache
import com.hushtv.tv.data.ThemedLibraryMatch
import com.hushtv.tv.data.ThemedList
import com.hushtv.tv.data.ThemedScrollMemory
import com.hushtv.tv.ui.screens.home.BackToHomeChip
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Themed movie list detail screen.
 *
 * v1.43.13 architecture — purely synchronous against the local
 * library. The screen paints poster grid + hero on the first
 * frame because [HushThemedLists.matchAgainstLibrary] is in-memory
 * and runs in well under 25 ms.
 *
 * Lazy TMDB hero metadata
 * ───────────────────────
 * The hero text panel (overview / year / rating / runtime) and
 * the cinematic backdrop are fetched on-demand for the **currently
 * focused** poster only. Triggered by a debounced 250 ms wait on
 * focus change so quick D-pad scrolls don't fire 30 TMDB calls;
 * only the poster the user actually pauses on does.
 *
 * Layout
 * ──────
 *   • Top 55 % — focus-driven hero (TMDB backdrop + theme column +
 *     focused-movie title/year/rating/runtime/overview).
 *   • Bottom 45 % — 5-column 2:3 poster grid, scrolls vertically.
 *
 * Library linkage
 * ───────────────
 * Each poster carries the matched library streamId. Click → the
 * standard `moviedetail/{playlistId}/{streamId}/{title}` route.
 */
@Composable
fun TVThemedDetailScreen(
    nav: NavController,
    playlistId: String,
    themeId: String,
) {
    val ctx = LocalContext.current
    val theme = remember(themeId) { HushThemedLists.all.firstOrNull { it.id == themeId } }
    if (theme == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    // Defensive cache prime — boot refresh normally handles this.
    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val playlist = PlaylistStore.find(ctx, playlistId)
            if (playlist != null) {
                runCatching { LibraryIndex.prime(ctx, playlist) }
                com.hushtv.tv.data.ThemedMatchCache.primeAsync(ctx, playlist)
            }
        }
    }

    // Read from the shared process-scoped snapshot. The cache
    // populates progressively (one theme per emission), so this
    // read can return null briefly while the matcher is still
    // working through the catalog before reaching this theme.
    val cachedMatches = com.hushtv.tv.data.ThemedMatchCache.snapshot[themeId]
    val matches: List<ThemedLibraryMatch> = cachedMatches.orEmpty()
    val matchesReady = cachedMatches != null

    val firstFocus = remember { FocusRequester() }
    val restoreFocusReq = remember { FocusRequester() }

    // v1.44.54 — restore the user's last position when they come
    // back from a movie detail page. The triple is
    //   (firstVisibleItemIndex, firstVisibleItemScrollOffset, focusedIndex)
    // and was written before nav.navigate(moviedetail/...).
    val saved: Triple<Int, Int, Int>? = remember(themeId) {
        ThemedScrollMemory.load(themeId)
    }
    var focusedIndex by rememberSaveable(themeId) {
        mutableStateOf(saved?.third ?: 0)
    }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = saved?.first ?: 0,
        initialFirstVisibleItemScrollOffset = saved?.second ?: 0,
    )
    val focusedMatch = matches.getOrNull(focusedIndex)

    // Lazy TMDB metadata for the focused poster — debounced 250 ms
    // so rapid D-pad scrolling doesn't fire dozens of TMDB calls.
    var heroMeta by remember { mutableStateOf<ThemedHeroMetaCache.Meta?>(null) }
    LaunchedEffect(focusedMatch?.streamId) {
        val target = focusedMatch ?: return@LaunchedEffect
        // Cache hit → resolve instantly without debounce.
        ThemedHeroMetaCache.get(target.streamId)?.let {
            heroMeta = it
            return@LaunchedEffect
        }
        heroMeta = null
        delay(250)
        val meta = ThemedHeroMetaCache.resolve(target.streamId, target.title, target.year)
        // Guard against focus moving while the request was in flight.
        val nowFocused = matches.getOrNull(focusedIndex)
        if (nowFocused?.streamId == target.streamId) {
            heroMeta = meta
        }
    }

    // Auto-focus on entry. If we have a saved focused index from a
    // previous visit to this theme, restore it via [restoreFocusReq]
    // (the tile at the saved index attaches that FocusRequester).
    // Otherwise fall back to the first tile.
    LaunchedEffect(matches.isNotEmpty(), themeId) {
        if (matches.isNotEmpty()) {
            delay(180)
            runCatching {
                if (saved != null && saved.third in matches.indices) {
                    restoreFocusReq.requestFocus()
                } else {
                    firstFocus.requestFocus()
                }
            }
        }
    }

    // Persist scroll + focus on every meaningful change so a
    // subsequent nav-back (which re-mounts this composable) can
    // restore the exact spot the user was at. Cheap — three ints
    // into a HashMap, off the rendering hot path.
    androidx.compose.runtime.DisposableEffect(themeId, focusedIndex) {
        onDispose {
            ThemedScrollMemory.save(
                themeId = themeId,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                focusedIndex = focusedIndex,
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        Column(Modifier.fillMaxSize()) {
            ThemedDetailHero(
                theme = theme,
                focused = focusedMatch,
                heroMeta = heroMeta,
                totalCount = matches.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f),
            )

            if (!matchesReady) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = theme.accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            "Curating your matches…",
                            color = Color(0xFFCBD5E1),
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            } else if (matches.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No matches in your library yet",
                            color = Color(0xFFCBD5E1),
                            fontFamily = Inter,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Check back after your next library refresh.",
                            color = Color(0xFF94A3B8),
                            fontFamily = Inter,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentPadding = PaddingValues(
                        start = 56.dp,
                        end = 56.dp,
                        top = 12.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = matches,
                        key = { it.streamId },
                    ) { match ->
                        val idx = matches.indexOf(match)
                        val savedFocusIdx = saved?.third ?: 0
                        val focusMod = when {
                            idx == 0 -> Modifier.focusRequester(firstFocus)
                            idx == savedFocusIdx && saved != null
                                -> Modifier.focusRequester(restoreFocusReq)
                            else -> Modifier
                        }
                        ThemedPosterTile(
                            match = match,
                            accent = theme.accent,
                            focusModifier = focusMod,
                            onFocus = { focusedIndex = idx },
                            onClick = {
                                // Snapshot the user's exact position
                                // BEFORE the nav transition so a
                                // subsequent back-press lands them
                                // on the same poster.
                                ThemedScrollMemory.save(
                                    themeId = themeId,
                                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                                    focusedIndex = idx,
                                )
                                nav.navigate(
                                    "moviedetail/$playlistId/" +
                                        "${match.streamId}/" +
                                        Uri.encode(match.title),
                                )
                            },
                        )
                    }
                }
            }
        }

        BackToHomeChip(
            nav = nav,
            playlistId = playlistId,
        )
    }
}

@Composable
private fun ThemedDetailHero(
    theme: ThemedList,
    focused: ThemedLibraryMatch?,
    heroMeta: ThemedHeroMetaCache.Meta?,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Backdrop priority: TMDB backdrop for the focused movie >
        // TMDB poster for the focused movie > theme's static hero
        // backdrop. Always paints — the static backdrop is
        // hardcoded and always present, so the screen never starts
        // empty.
        val backdropUrl = heroMeta?.backdropUrl
            ?: heroMeta?.posterUrl
            ?: theme.heroBackdropUrl
        AnimatedContent(
            targetState = backdropUrl,
            transitionSpec = { fadeIn(tween(360)) togetherWith fadeOut(tween(360)) },
            label = "themed-detail-backdrop",
        ) { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Bias the crop toward the top of the
                        // backdrop. TMDB cinematic stills almost
                        // always frame faces/action in the upper
                        // third — center alignment chopped off
                        // too much top detail on a tall hero.
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // No backdrop yet — fall back to a theme-accent
                    // gradient so the screen never feels empty.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to theme.accent.copy(alpha = 0.18f),
                                    1f to Color(0xFF0A0F1C),
                                ),
                            ),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x330A0F1C),
                                0.55f to Color(0x990A0F1C),
                                1f to Color(0xFF0A0F1C),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color(0xEE0A0F1C),
                                0.5f to Color(0x880A0F1C),
                                1f to Color(0x330A0F1C),
                            ),
                        ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(theme.accent.copy(alpha = 0.18f))
                        .border(1.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        theme.section.displayName.uppercase(),
                        color = theme.accent,
                        fontFamily = Inter,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                }
                Text(
                    theme.title,
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 46.sp,
                    letterSpacing = (-0.8).sp,
                )
                Box(
                    Modifier
                        .height(3.dp)
                        .width(64.dp)
                        .background(theme.accent),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$totalCount ${if (totalCount == 1) "film" else "films"} in your library",
                    color = theme.accent,
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }

            AnimatedContent(
                targetState = focused?.streamId,
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(280)) },
                label = "themed-focused-copy",
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
            ) { streamId ->
                val current = focused?.takeIf { it.streamId == streamId }
                if (current == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            current.title,
                            color = Color.White,
                            fontFamily = Inter,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 36.sp,
                            letterSpacing = (-0.5).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            (heroMeta?.year ?: current.year)?.let { yr -> MetaChip("$yr") }
                            heroMeta?.voteAverage?.takeIf { it >= 0.1 }?.let { va ->
                                MetaChip("\u2605 " + String.format("%.1f", va))
                            }
                            heroMeta?.runtimeMinutes?.takeIf { it > 0 }?.let { rt ->
                                MetaChip("${rt} min")
                            }
                        }
                        Text(
                            heroMeta?.overview?.ifBlank { null } ?: "—",
                            color = Color(0xFFE5E7EB),
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 21.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x331F2937))
            .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontFamily = Inter,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun ThemedPosterTile(
    match: ThemedLibraryMatch,
    accent: Color,
    focusModifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = focusModifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) accent else Color(0x22FFFFFF),
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnter(onClick = onClick),
    ) {
        if (match.poster != null) {
            AsyncImage(
                model = match.poster,
                contentDescription = match.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    match.title,
                    color = Color(0xFFE5E7EB),
                    fontFamily = Inter,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
