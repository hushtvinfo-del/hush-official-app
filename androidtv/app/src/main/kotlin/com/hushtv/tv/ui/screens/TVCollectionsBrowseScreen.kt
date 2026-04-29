@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hushtv.tv.data.TitleMatcher
import com.hushtv.tv.ui.screens.home.MovieCollection
import com.hushtv.tv.ui.screens.home.rememberMovieCollections
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.util.safeFocusTraversal
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.tvFocusable

/**
 * Full-grid "See All" Collections browser. Renders every franchise in
 * the catalog (curated + dynamically discovered from TMDB) as a grid
 * of cinematic backdrop tiles. Same click route as the home row →
 * detail screen chronological view.
 */
@Composable
fun TVCollectionsBrowseScreen(
    nav: NavController,
    playlistId: String,
) {
    val collections = rememberMovieCollections()

    // Live search state — normalised via TitleMatcher so e.g. typing
    // "batman" matches "Batman (Christopher Nolan Collection)" and
    // "batman!" alike.
    var query by remember { mutableStateOf("") }
    val filtered by remember(collections) {
        derivedStateOf {
            val q = TitleMatcher.normalize(query)
            if (q.isBlank()) collections
            else collections.filter {
                TitleMatcher.normalize(it.displayName).contains(q)
            }
        }
    }

    val searchFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    LaunchedEffect(collections.isNotEmpty()) {
        if (collections.isNotEmpty()) {
            kotlinx.coroutines.delay(220)
            runCatching { firstCardFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        // Top-left Back-to-Home chip — replaces the old top nav.
        // Matches the full-screen pattern used by Movies / Series /
        // Live TV / Hush+ etc.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 32.dp, top = 24.dp),
        ) {
            com.hushtv.tv.ui.screens.home.BackToHomeChip(
                nav = nav,
                playlistId = playlistId,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 80.dp, start = 48.dp, end = 48.dp, bottom = 24.dp),
        ) {
            // ── Page header + inline search bar ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 18.dp, bottom = 20.dp).fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 32.dp)
                        .background(Cyan, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "FRANCHISES · ALL",
                        color = Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        fontFamily = Inter,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Movie Collections",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 40.sp,
                        fontFamily = Inter,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (query.isBlank())
                            "${collections.size} franchises · click any to watch in order"
                        else
                            "${filtered.size} of ${collections.size} match \"$query\"",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = Inter,
                    )
                }
                Spacer(Modifier.width(24.dp))
                // ── Search bar ──
                CollectionsSearchBar(
                    value = query,
                    onChange = { query = it },
                    focusRequester = searchFocus,
                    downTarget = firstCardFocus,
                )
            }

            if (collections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Loading your collection catalog…",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontFamily = Inter,
                    )
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No franchises match \"$query\"",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Try a different word or clear the search",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontFamily = Inter,
                        )
                    }
                }
            } else {
                // focusRestorer so D-pad Up → nav → D-pad Down returns
                // focus to exactly the card the user was on.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(gridFocus)
                        .focusRestorer()
                        .focusGroup(),
                ) {
                    items(filtered, key = { it.id }) { coll ->
                        BrowseCollectionCard(
                            coll = coll,
                            isFirst = coll == filtered.first(),
                            firstCardFocus = firstCardFocus,
                            onUpFromFirstRow = { runCatching { searchFocus.requestFocus() } },
                            onClick = {
                                nav.navigate(
                                    "collection/$playlistId/${coll.tmdbCollectionId}/" +
                                        Uri.encode(coll.displayName)
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single-line search bar — pure BasicTextField with cyan focus ring
 * and an X-to-clear button.
 *
 * D-pad routing is declarative via `Modifier.focusProperties { down = ... }`
 * on the TextField — this is the canonical Compose way and is
 * bulletproof against IME consumption / event-preview ordering quirks
 * that `onPreviewKeyEvent` can suffer from on Android TV. We ALSO
 * keep an `onPreviewKeyEvent` handler as belt-and-suspenders for any
 * device where focusProperties somehow doesn't fire.
 */
@Composable
private fun CollectionsSearchBar(
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    downTarget: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(320.dp)
            .height(46.dp)
            .background(SurfaceNavy, RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x22FFFFFF),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) Cyan else TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = Inter),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionDown -> {
                                runCatching { downTarget.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    },
            )
            if (value.isEmpty()) {
                Text(
                    "Search franchises…",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                )
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0x22FFFFFF))
                    .focusable()
                    .safeFocusTraversal(onDown = downTarget)
                    .clickableWithEnter { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun BrowseCollectionCard(
    coll: MovieCollection,
    isFirst: Boolean,
    firstCardFocus: FocusRequester,
    onUpFromFirstRow: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(90),
        label = "browse-coll-scale",
    )
    val cardShape = RoundedCornerShape(14.dp)
    val focusMod = if (isFirst) Modifier.focusRequester(firstCardFocus) else Modifier

    Column(
        modifier = focusMod
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Only the first row should lift focus to the search
                // bar — rows below just navigate within the grid.
                if (isFirst && ev.key == Key.DirectionUp) {
                    onUpFromFirstRow()
                    return@onPreviewKeyEvent true
                }
                false
            }
            .clickableWithEnter(onClick)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(
                    elevation = if (focused) 22.dp else 4.dp,
                    shape = cardShape,
                    ambientColor = coll.accent,
                    spotColor = coll.accent,
                )
                .clip(cardShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            coll.accent.copy(alpha = 0.28f),
                            Color(0xFF05080F),
                        )
                    )
                )
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) coll.accent else coll.accent.copy(alpha = 0.22f),
                    shape = cardShape,
                ),
        ) {
            // Backdrop.
            coll.backdropUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Dark veil.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x33000000),
                            0.60f to Color(0x80000000),
                            1.0f to Color(0xEB000000),
                        )
                    )
            )
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                0.0f to coll.accent.copy(alpha = 0.22f),
                                1.0f to Color.Transparent,
                                radius = 380f,
                            )
                        )
                )
            }

            // Content.
            Box(Modifier.fillMaxSize().padding(14.dp)) {
                // Accent pill top-left.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0x40FFFFFF), RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(coll.accent, RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "FRANCHISE",
                        color = Color.White,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                }
                // Name bottom-left.
                Text(
                    coll.displayName,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 19.sp,
                    fontFamily = Inter,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}
