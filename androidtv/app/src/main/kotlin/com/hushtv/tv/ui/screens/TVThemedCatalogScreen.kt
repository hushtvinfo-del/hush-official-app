package com.hushtv.tv.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.hushtv.tv.data.ThemedLibraryMatch
import com.hushtv.tv.data.ThemedList
import com.hushtv.tv.ui.screens.home.BackToHomeChip
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Moods & Themes" catalog — browsable list of curated themed
 * movie lists backed by [HushThemedLists.all].
 *
 * v1.43.13 rewrite — purely synchronous against the local library.
 * Every theme tile renders the count of library matches and uses
 * the first matched library poster as its backdrop. No network on
 * the rendering path → instant first frame, no spinners, no
 * "Curating…" splash.
 *
 * Layout
 * ──────
 * Left 58 % of the screen = focus-driven hero. As the user moves
 * focus between tile cards the hero panel cross-fades to show the
 * focused theme's signature backdrop, title, subtitle, section
 * tag, and library match count. Right 42 % = 2-column vertical
 * scrolling grid of 3:4 glass tiles.
 */
@Composable
fun TVThemedCatalogScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current

    val themes = remember { HushThemedLists.all }

    // Defensive cache prime — boot refresh handles this normally,
    // but if the user deep-linked or switched profile mid-session,
    // we kick it from here too. Both calls are idempotent.
    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val playlist = PlaylistStore.find(ctx, playlistId) ?: return@withContext
            runCatching { LibraryIndex.prime(ctx, playlist) }
            com.hushtv.tv.data.ThemedMatchCache.primeAsync(ctx, playlist)
        }
    }

    // Read from the shared process-scoped snapshot. Reads inside
    // the composable subscribe to the SnapshotStateMap so tiles
    // automatically swap from gradient to poster as each theme
    // lands. Cost: zero main-thread work.
    val cacheSnapshot = com.hushtv.tv.data.ThemedMatchCache.snapshot
    val matches: Map<String, List<ThemedLibraryMatch>> = cacheSnapshot
    val matchesReady = cacheSnapshot.isNotEmpty()

    // Currently focused theme id — drives the hero panel.
    var focusedThemeId by remember { mutableStateOf<String?>(themes.firstOrNull()?.id) }
    val focusedTheme = themes.firstOrNull { it.id == focusedThemeId }
    val focusedMatches = focusedTheme?.id?.let { matches[it] }.orEmpty()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(matchesReady) {
        if (matchesReady) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top header — page title + accent line + count ──
            // Replaces the previous big focus-driven hero on the
            // left half of the screen. The hero made the catalog
            // visually indistinguishable from a single-theme detail
            // page (Based on True Stories, etc.) — users kept
            // confusing it for the wrong screen. A flat header
            // makes it unambiguous: this IS the "All Themes" page.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 96.dp, end = 96.dp, top = 56.dp, bottom = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 16.dp)
                            .background(Color(0xFFEC4899), RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "MOODS & THEMES",
                        color = Color(0xFFEC4899),
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "All Themes",
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.6).sp,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .width(64.dp)
                        .background(Color(0xFFEC4899)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${themes.size} curated lists",
                    color = Color(0xFFEC4899),
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }

            // ── Full-width 5-column grid ──
            // 25 themes / 5 cols = 5 rows; with the new ~190dp tile
            // height the entire grid fits 1080p without scrolling.
            // Bottom padding is small so the last row's focus glow
            // isn't clipped by the screen edge.
            if (!matchesReady) {
                ThemeMatcherLoader(
                    accent = focusedTheme?.accent ?: Color(0xFF06B6D4),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 96.dp, vertical = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 96.dp,
                        end = 96.dp,
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = themes,
                        key = { it.id },
                    ) { theme ->
                        val themeMatches = matches[theme.id].orEmpty()
                        val index = themes.indexOf(theme)
                        ThemeTile(
                            theme = theme,
                            heroPosterUrl = themeMatches.firstOrNull()?.poster,
                            resolvedCount = themeMatches.size,
                            focusModifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onFocus = { focusedThemeId = theme.id },
                            onClick = { nav.navigate("themedetail/$playlistId/${theme.id}") },
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

/**
 * Left-side hero panel. Cross-fades between themes as focus moves.
 * Background uses the first matched library poster (cropped) as a
 * rich, themed backdrop with dark vignette so the title text stays
 * legible regardless of poster brightness.
 */
@Composable
private fun ThemedCatalogHero(
    theme: ThemedList?,
    matches: List<ThemedLibraryMatch>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Hardcoded TMDB original-size backdrop baked into the
        // ThemedList itself. No fetcher, no cache, no race
        // condition — paints with the right artwork on the very
        // first frame. See HushThemedLists.HERO_BACKDROPS.
        val backdropUrl = theme?.heroBackdropUrl
        AnimatedContent(
            targetState = theme?.id to backdropUrl,
            transitionSpec = {
                (fadeIn(tween(380)) togetherWith fadeOut(tween(380)))
            },
            label = "themed-catalog-hero",
        ) { (_, posterUrl) ->
            Box(Modifier.fillMaxSize()) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Bias toward top — library posters are
                        // 2:3 portrait but the catalog hero is
                        // nearly square, so center crop loses
                        // the title artwork at the top of the
                        // poster. Top alignment keeps the
                        // recognisable upper portion in frame.
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Multi-stop gradient — dark bottom ensures overlay
                // text is always legible, right fade ties the hero
                // to the grid visually (tiles sit on the same bed).
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x000A0F1C),
                                0.35f to Color(0x550A0F1C),
                                1f to Color(0xF20A0F1C),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                1f to Color(0xFF0A0F1C),
                            ),
                        ),
                )
            }
        }

        if (theme != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.9f)
                    .padding(start = 56.dp, bottom = 60.dp, end = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(theme.accent.copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                theme.accent.copy(alpha = 0.65f),
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp),
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
                        "${matches.size} ${if (matches.size == 1) "film" else "films"} in your library",
                        color = Color(0xFFCBD5E1),
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    )
                }
                Text(
                    theme.title,
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 58.sp,
                    letterSpacing = (-0.8).sp,
                )
                Box(
                    Modifier
                        .height(3.dp)
                        .width(64.dp)
                        .background(theme.accent),
                )
                Text(
                    theme.subtitle,
                    color = Color(0xFFCBD5E1),
                    fontFamily = Inter,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 56.dp, bottom = 60.dp),
            ) {
                Text(
                    "MOODS",
                    color = Color(0xFF64748B),
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Find your next watch by feel",
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 48.sp,
                )
            }
        }
    }
}

/** 3:4 glass tile with poster backdrop + glyph + title. Tap opens
 *  theme detail. Focus ring uses the theme's accent colour. */
@Composable
private fun ThemeTile(
    theme: ThemedList,
    heroPosterUrl: String?,
    resolvedCount: Int,
    focusModifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = focusModifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(shape)
            .background(Color(0xFF0F172A))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) theme.accent else Color(0x22FFFFFF),
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickableWithEnter(onClick = onClick),
    ) {
        if (heroPosterUrl != null) {
            AsyncImage(
                model = heroPosterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                theme.accent.copy(alpha = 0.35f),
                                Color(0xFF0F172A),
                            ),
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
                        0.45f to Color(0x7A0A0F1C),
                        1f to Color(0xEE0A0F1C),
                    ),
                ),
        )
        Text(
            theme.glyph,
            color = theme.accent.copy(alpha = 0.85f),
            fontFamily = Inter,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 14.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                theme.title,
                color = Color.White,
                fontFamily = Inter,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                theme.subtitle,
                color = Color(0xFFCBD5E1),
                fontFamily = Inter,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (resolvedCount > 0) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x221FFFFF))
                        .border(
                            1.dp,
                            theme.accent.copy(alpha = 0.55f),
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "$resolvedCount films",
                        color = theme.accent,
                        fontFamily = Inter,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
}


/**
 * Tiny accent-coloured spinner shown while the off-thread theme
 * matcher is running. After v1.43.14's matcher optimisation this
 * is typically only visible for ~1-2 seconds on a Fire Stick.
 */
@Composable
private fun ThemeMatcherLoader(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                color = accent,
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
}
