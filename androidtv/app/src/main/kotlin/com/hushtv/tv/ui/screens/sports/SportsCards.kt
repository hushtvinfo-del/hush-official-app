@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.hushtv.tv.data.sports.SportsGame
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * Big-button game card. Two team badges, score-or-VS in the middle,
 * channel chip below. Sized for "grandfather can read it from the
 * couch" — 360 dp wide, big text.
 *
 * @param onFocus parent updates the hero pin to follow the focused
 *                card so the hero stays in sync with what the user is
 *                eyeing.
 */
@Composable
fun GameCard(
    game: SportsGame,
    matchedChannel: MediaCard,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(18.dp)
    val accent = parseAccent(game.league?.accent) ?: Cyan
    val isLive = game.status.equals("live", ignoreCase = true)

    Box(
        Modifier
            .width(360.dp)
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF111827),
                        Color(0xFF050810),
                    )
                )
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Cyan else accent.copy(alpha = 0.22f),
                shape = cardShape,
            ),
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            // ── Top row: league badge + LIVE pulse + start time ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    (game.league?.name ?: "GAME").uppercase(),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.weight(1f))
                if (isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "LIVE",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontFamily = Inter,
                        )
                    }
                } else {
                    Text(
                        friendlyCountdown(game.start_utc),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = Inter,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Teams: away · vs · home — left/right balanced ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TeamBlock(
                    name = game.away?.short_name ?: game.away?.name ?: "TBA",
                    badgeUrl = game.away?.badge_url ?: game.away?.logo_url,
                    score = if (isLive || game.status == "final") game.score_away else null,
                    align = Alignment.Start,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .size(width = 30.dp, height = 30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isLive || game.status == "final") "–" else "vs",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Inter,
                    )
                }
                TeamBlock(
                    name = game.home?.short_name ?: game.home?.name ?: "TBA",
                    badgeUrl = game.home?.badge_url ?: game.home?.logo_url,
                    score = if (isLive || game.status == "final") game.score_home else null,
                    align = Alignment.End,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Channel chip — the "WHERE TO WATCH" call-to-action ──
            ChannelChip(channelTitle = matchedChannel.title, focused = focused)
        }
    }
}

@Composable
private fun TeamBlock(
    name: String,
    badgeUrl: String?,
    score: String?,
    align: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = align,
    ) {
        if (!badgeUrl.isNullOrBlank()) {
            AsyncImage(
                model = badgeUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp),
            )
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1F2937))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name.uppercase(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (score != null) {
            Text(
                score,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
            )
        }
    }
}

@Composable
private fun ChannelChip(channelTitle: String, focused: Boolean) {
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                channelTitle.uppercase(),
                color = if (focused) Color(0xFF05080F) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
                fontFamily = Inter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Best-effort hex parser for the league.accent string from the
 *  server. Returns null on bad input so the call-site can fall back. */
private fun parseAccent(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.removePrefix("#")
    return runCatching {
        val n = cleaned.toLong(16)
        Color(((0xFF000000 or n) and 0xFFFFFFFF).toInt())
    }.getOrNull()
}

/** League pill bar — the user's spec was "PPV / NHL / MLB / NBA / OTHERS"
 *  but in v1.43.99+ we default to season-aware ordering supplied by the
 *  server. This is just a horizontally-scrollable focusable row.
 */
@Composable
fun LeaguePillBar(
    pills: List<LeaguePill>,
    selectedSlug: String,
    onSelect: (String) -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(start = contentStartPadding, end = 96.dp, top = 12.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pills.forEach { p ->
            LeaguePillView(
                pill = p,
                selected = p.slug == selectedSlug,
                onSelect = { onSelect(p.slug) },
            )
        }
    }
}

data class LeaguePill(
    val slug: String,
    val label: String,
    val accent: Color,
)

@Composable
private fun LeaguePillView(
    pill: LeaguePill,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val pillShape = RoundedCornerShape(999.dp)
    val bg = when {
        selected -> pill.accent
        focused -> Color(0x33FFFFFF)
        else -> Color(0x1AFFFFFF)
    }
    val fg = when {
        selected -> Color(0xFF050810)
        else -> Color.White
    }
    // NOTE: do NOT add an outer `.focusable()` after `.tvFocusable()`.
    // tvFocusable already adds its own internal `.focusable()` and
    // wrapping it again creates two focusables in the chain — see
    // the v1.43.98 cautionary block in TvComponents.kt.
    Box(
        Modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1f, shape = pillShape)
            .clickableWithEnter(onSelect)
            .clip(pillShape)
            .background(bg)
            .border(
                width = if (selected) 0.dp else 1.5.dp,
                color = if (selected) Color.Transparent else pill.accent.copy(alpha = 0.55f),
                shape = pillShape,
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            pill.label.uppercase(),
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.5.sp,
            fontFamily = Inter,
        )
    }
}
