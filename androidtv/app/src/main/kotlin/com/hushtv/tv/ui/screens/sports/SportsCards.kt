@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
    onUpFromCard: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(18.dp)
    val accent = parseAccent(game.league?.accent) ?: Cyan
    val isLive = game.status.equals("live", ignoreCase = true)
    val isFinal = game.status.equals("final", ignoreCase = true)
    // v1.44.5 — Treat games within ±5h of start as "in progress"
    // when the server hasn't yet pushed a definitive status. Avoids
    // showing "FINAL" for a game that's still on the air.
    val nowMs = remember { System.currentTimeMillis() }
    val deltaMs = game.start_utc - nowMs
    val effectivelyLive = isLive ||
        (!isFinal && deltaMs in -5 * 3600_000L..5 * 3600_000L)
    val effectivelyFinal = isFinal || deltaMs < -5 * 3600_000L
    val showScores = (isLive || isFinal) &&
        !game.score_home.isNullOrBlank() && !game.score_away.isNullOrBlank()

    // v1.44.7 — Breadcrumb every card composition with the exact
    // status + score values we received. If a future user reports
    // "scores not showing" we can pull their diagnostic and see
    // whether the data was missing or the render path is wrong.
    androidx.compose.runtime.LaunchedEffect(game.id, game.status, game.score_home, game.score_away) {
        com.hushtv.tv.data.EventLog.log(
            "sports",
            "card[${game.id}] status=${game.status} score=${game.score_away}-${game.score_home} showScores=$showScores"
        )
    }

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
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (ev.key == Key.DirectionUp && onUpFromCard != null) {
                    onUpFromCard(); true
                } else false
            }
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
                if (effectivelyLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            // v1.44.6 — when we know the game is live but
                            // upstream hasn't pushed scores yet, show
                            // elapsed time instead of just "LIVE". Beats
                            // staring at "VS" with no extra info.
                            if (showScores) "LIVE" else "LIVE  ·  ${elapsedShort(deltaMs)}",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontFamily = Inter,
                        )
                    }
                } else if (effectivelyFinal) {
                    Text(
                        "FINAL",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontFamily = Inter,
                    )
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

            // ── Teams row ──
            // v1.44.7 — When scores are available, render them as BIG
            // central numbers ("2  —  4") that can't be missed. When
            // they aren't, fall back to the logo + name + tiny "vs"
            // layout. The previous layout buried the score under the
            // team name where it was getting lost.
            if (showScores) {
                // v1.44.9 — scores at 28sp (was 38sp). 38sp was being
                // clipped at the bottom of the 220dp card on actual TVs.
                // 28sp + a vertically-centered Box leaves comfortable
                // breathing room above the channel chip.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TeamBadgeOnly(
                        badgeUrl = game.away?.badge_url ?: game.away?.logo_url,
                        modifier = Modifier.size(40.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            game.score_away ?: "0",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = Inter,
                        )
                        Text(
                            "—",
                            color = Color(0xFF64748B),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter,
                        )
                        Text(
                            game.score_home ?: "0",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = Inter,
                        )
                    }
                    TeamBadgeOnly(
                        badgeUrl = game.home?.badge_url ?: game.home?.logo_url,
                        modifier = Modifier.size(40.dp),
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TeamBlock(
                        name = game.away?.short_name ?: game.away?.name ?: "TBA",
                        badgeUrl = game.away?.badge_url ?: game.away?.logo_url,
                        score = null,
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
                            when {
                                effectivelyLive -> "—"
                                effectivelyFinal -> "—"
                                else -> "vs"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter,
                        )
                    }
                    TeamBlock(
                        name = game.home?.short_name ?: game.home?.name ?: "TBA",
                        badgeUrl = game.home?.badge_url ?: game.home?.logo_url,
                        score = null,
                        align = Alignment.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Channel chip — the "WHERE TO WATCH" call-to-action ──
            ChannelChip(channelTitle = matchedChannel.title, focused = focused)
        }
    }
}

/**
 * Just the badge image (no name, no score). Used in the "scoreboard"
 * layout where the score numbers are the central element and the
 * badges are decorative bookends.
 */
@Composable
private fun TeamBadgeOnly(
    badgeUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (!badgeUrl.isNullOrBlank()) {
        AsyncImage(
            model = badgeUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Box(
            modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1F2937))
        )
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

/**
 * Short "elapsed" label for live games whose upstream scores haven't
 * landed yet — e.g. "2H 14M" if start was 2h14m ago. Returns a clean
 * "0M" floor so we never render negative time.
 */
private fun elapsedShort(deltaMs: Long): String {
    val elapsed = -deltaMs   // game started in the past, so deltaMs < 0
    if (elapsed <= 0) return "0M"
    val totalMin = (elapsed / 60_000).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h <= 0 -> "${m}M"
        m == 0 -> "${h}H"
        else -> "${h}H ${m}M"
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
 *
 *  v1.44.2: the first pill ("All") is now the page's first focusable.
 *  See TVSportsPage's class comment for why.
 */
@Composable
fun LeaguePillBar(
    pills: List<LeaguePill>,
    selectedSlug: String,
    onSelect: (String) -> Unit,
    contentStartPadding: androidx.compose.ui.unit.Dp = 96.dp,
    firstItemFocus: FocusRequester? = null,
    onUpFromRow: (() -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .focusGroup()
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
            }
            .padding(start = contentStartPadding, end = 96.dp, top = 12.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pills.forEachIndexed { idx, p ->
            LeaguePillView(
                pill = p,
                selected = p.slug == selectedSlug,
                onSelect = { onSelect(p.slug) },
                focusRequester = if (idx == 0) firstItemFocus else null,
            )
        }
    }
}

data class LeaguePill(
    val slug: String,
    val label: String,
    val accent: Color,
    /** v1.44.11 — Optional league badge URL. When present the chip
     *  shows the badge to the left of the label; absent (the All /
     *  Live / PPV virtual tabs) shows a small icon glyph instead. */
    val logoUrl: String? = null,
)

@Composable
private fun LeaguePillView(
    pill: LeaguePill,
    selected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val pillShape = RoundedCornerShape(999.dp)
    // v1.44.11 — Three visual states:
    //   • selected → solid accent fill, dark text, no border
    //   • focused (but not selected) → translucent white-bg, white text, accent ring
    //   • idle → near-transparent bg, white text, faint accent ring
    val bg = when {
        selected -> pill.accent
        focused -> Color(0x33FFFFFF)
        else -> Color(0x14FFFFFF)
    }
    val fg = when {
        selected -> Color(0xFF050810)
        else -> Color.White
    }
    val ringWidth = when {
        selected -> 0.dp
        focused -> 2.dp
        else -> 1.dp
    }
    val ringColor = when {
        selected -> Color.Transparent
        focused -> pill.accent
        else -> pill.accent.copy(alpha = 0.30f)
    }
    Row(
        Modifier
            .height(54.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(
                scaleOnFocus = 1f,
                shape = pillShape,
                focusRequester = focusRequester,
            )
            .clickableWithEnter(onSelect)
            .clip(pillShape)
            .background(bg)
            .border(width = ringWidth, color = ringColor, shape = pillShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!pill.logoUrl.isNullOrBlank()) {
            // Real league badge
            AsyncImage(
                model = pill.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(28.dp),
            )
        } else {
            // "All" / "Live" / "PPV" — render a tinted circle so the
            // strip stays visually coherent (every chip has a left
            // graphic). The dot color uses the chip's accent.
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (selected) fg else pill.accent)
            )
        }
        Text(
            pill.label.uppercase(),
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.5.sp,
            fontFamily = Inter,
        )
    }
}
