@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * Horizontal rail of streaming-service shortcut cards. Each card:
 *  • Portrait tile (180×220 dp) — taller than Discovery cards so the
 *    logo has visual weight without feeling cramped.
 *  • Vertical brand-gradient fill (service's signature colour palette).
 *  • TMDB-sourced transparent PNG logo centered in the upper 60%.
 *  • Service display name underneath the logo.
 *  • On focus: thick brand-accent border, accent-coloured shadow glow,
 *    subtle 1.04 scale.
 *
 * Row-level D-pad handlers for Up (back to previous page) and Down
 * (next page) so D-pad nav feels consistent across all home pages.
 */
@Composable
fun HomeStreamingServicesRow(
    services: List<StreamingService>,
    kindLabel: String,
    onFocusedServiceChange: (StreamingService) -> Unit,
    onServiceClick: (StreamingService) -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: FocusRequester? = null,
    onUpFromRow: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
) {
    if (services.isEmpty()) return

    // First-card direct-bind pattern (mirrors HomeContinueWatchingRow):
    // the rail's RIGHT-exit callback only lands reliably on a real
    // focusable card when `firstItemFocus` is bound to that card
    // directly. focusGroup() stays so intra-row LEFT/RIGHT doesn't
    // escape into the rail.
    Column(
        Modifier
            .focusGroup()
            .fillMaxWidth()
            .padding(
                start = contentStartPadding,
                end = 48.dp,
                top = 16.dp,
                bottom = 32.dp,
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> {
                        if (onUpFromRow != null) { onUpFromRow(); true } else false
                    }
                    Key.DirectionDown -> {
                        if (onDownFromRow != null) { onDownFromRow(); true } else false
                    }
                    else -> false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(Cyan, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                kindLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            itemsIndexed(services, key = { _, s -> s.id }) { idx, service ->
                ServiceCardView(
                    service = service,
                    onFocus = { onFocusedServiceChange(service) },
                    onClick = { onServiceClick(service) },
                    focusRequester = if (idx == 0) firstItemFocus else null,
                )
            }
        }
    }
}

@Composable
private fun ServiceCardView(
    service: StreamingService,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        Modifier.width(196.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── CARD ──  pure logo-on-gradient. Focus ring + glow live here.
        val baseTop: Modifier = if (focusRequester != null)
            Modifier.focusRequester(focusRequester) else Modifier
        Box(
            baseTop
                .width(196.dp)
                .height(118.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .tvFocusable(scaleOnFocus = 1f, shape = cardShape)
                .focusable()
                .clickableWithEnter(onClick)
                .clip(cardShape)
                .background(
                    Brush.verticalGradient(
                        listOf(service.brandTop, service.brandBottom)
                    )
                )
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) service.accent
                        else service.accent.copy(alpha = 0.15f),
                    shape = cardShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Ambient brand accent glow from top-right.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.0f to service.accent.copy(alpha = if (focused) 0.22f else 0.10f),
                            1.0f to Color.Transparent,
                            radius = 320f,
                        )
                    )
            )

            val logoUrl = service.logoUrl
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = service.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(140.dp)
                        .height(74.dp)
                        .graphicsLayer {
                            scaleX = service.logoScale
                            scaleY = service.logoScale
                        },
                )
            } else {
                Text(
                    service.displayName,
                    color = service.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
            }
        }

        // ── LABEL beneath the card ── plain Text in a Box with NO
        // focusable / clickable modifiers, so Compose never draws a
        // focus ring or highlight around it. Fully decoupled from the
        // card above.
        Spacer(Modifier.height(10.dp))
        Text(
            service.displayName,
            color = if (focused) Color.White else Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            fontFamily = Inter,
        )
    }
}
