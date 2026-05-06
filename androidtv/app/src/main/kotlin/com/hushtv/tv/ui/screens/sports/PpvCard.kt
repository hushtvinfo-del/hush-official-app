@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.data.MediaCard
import com.hushtv.tv.data.sports.SportsPpvEvent
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * PPV card — a tall poster-style tile, distinct from regular game cards.
 * Same focus contract (focusRequester direct-bind via tvFocusable).
 */
@Composable
fun PpvCard(
    event: SportsPpvEvent,
    matchedChannel: MediaCard,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(18.dp)
    val accent = Color(0xFFE10600)

    Box(
        Modifier
            .width(300.dp)
            .height(220.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .tvFocusable(
                scaleOnFocus = 1f,
                shape = cardShape,
                focusRequester = focusRequester,
            )
            .clickableWithEnter(onClick)
            .clip(cardShape)
            .background(Color(0xFF050810))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Cyan else accent.copy(alpha = 0.4f),
                shape = cardShape,
            ),
    ) {
        // Poster image, full-bleed, with cinematic vignette.
        if (!event.poster_url.isNullOrBlank()) {
            AsyncImage(
                model = event.poster_url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(cardShape),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0.0f to accent.copy(alpha = 0.45f),
                        1.0f to Color(0xFF050810),
                    )
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x33000000),
                    0.55f to Color(0x99000000),
                    1.0f to Color(0xF0050810),
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "PPV EVENT",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                    fontFamily = Inter,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                event.title.uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 22.sp,
                letterSpacing = 0.5.sp,
                fontFamily = Inter,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                friendlyCountdown(event.start_utc),
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(10.dp))
            // Channel chip — same look as GameCard's chip.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) Color(0xFFFFFFFF) else Color(0x1AFFFFFF))
                    .border(
                        1.dp,
                        if (focused) Color.Transparent else Color(0xFF1E90FF).copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "▶  ",
                        color = if (focused) Color(0xFF05080F) else Cyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Inter,
                    )
                    Text(
                        matchedChannel.title.uppercase(),
                        color = if (focused) Color(0xFF05080F) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.8.sp,
                        fontFamily = Inter,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
