@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.lite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * Shared lightweight building blocks for the Lite UI tree.
 *
 * Design rules for everything in `ui.lite.*`:
 *   • NO graphicsLayer{}, NO Brush.gradient backgrounds.
 *   • NO infiniteRepeatable / InfiniteTransition / animateFloat
 *     animations. Static colors only.
 *   • Focus state = single 2-dp cyan border. NO scale, NO glow.
 *   • Solid color backgrounds, never blurred or grain-overlaid.
 *   • AsyncImage uses ContentScale.Crop with no fade-in.
 */

@Composable
fun LitePosterCard(
    card: MediaCard,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    widthDp: Int = 130,
    heightDp: Int = 195,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(widthDp.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvFocusable(
                scaleOnFocus = 1f,
                shape = shape,
                focusRequester = focusRequester,
            )
            .clickableWithEnter(onClick),
    ) {
        Box(
            Modifier
                .width(widthDp.dp)
                .height(heightDp.dp)
                .clip(shape)
                .background(Color(0xFF1F2937))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Cyan else Color(0x14FFFFFF),
                    shape = shape,
                ),
        ) {
            if (!card.poster.isNullOrBlank()) {
                AsyncImage(
                    model = card.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Plain initials fallback — no gradient, no animation.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        card.title.take(2).uppercase(),
                        color = Color(0xFF94A3B8),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            color = if (focused) Color.White else Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Plain channel pill used by Live TV. Shows logo + name. No
 * animations, no glow.
 */
@Composable
fun LiteChannelTile(
    card: MediaCard,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .width(280.dp)
            .height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(
                scaleOnFocus = 1f,
                shape = shape,
                focusRequester = focusRequester,
            )
            .clickableWithEnter(onClick)
            .clip(shape)
            .background(if (focused) Color(0xFF1E293B) else Color(0xFF111A2C))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Cyan else Color(0x14FFFFFF),
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            if (!card.poster.isNullOrBlank()) {
                AsyncImage(
                    model = card.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    card.title.take(2).uppercase(),
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            card.title,
            color = if (focused) Color.White else Color(0xFFE2E8F0),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Reusable horizontal row with a section title above. Lazy so
 * off-screen items aren't built until scrolled to (the v1.44.19
 * "lazy load only focused row" behaviour for low-end TVs).
 */
@Composable
fun LiteRow(
    title: String,
    items: List<MediaCard>,
    onClick: (MediaCard) -> Unit,
    firstItemFocus: FocusRequester? = null,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = Inter,
            modifier = Modifier.padding(start = 48.dp, top = 12.dp, bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 48.dp, end = 48.dp,
            ),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexedStable(items) { idx, card ->
                LitePosterCard(
                    card = card,
                    onClick = { onClick(card) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                )
            }
        }
    }
}

/** itemsIndexed equivalent that uses item.id as key for stable
 *  scroll position when the list reshuffles. */
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedStable(
    items: List<T>,
    crossinline keySelector: (T) -> Any = { it as Any },
    crossinline content: @Composable (index: Int, item: T) -> Unit,
) {
    items(
        count = items.size,
        key = { idx -> keySelector(items[idx]) }
    ) { idx -> content(idx, items[idx]) }
}
