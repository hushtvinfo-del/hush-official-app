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
    val nowMs = remember { System.currentTimeMillis() }
    val deltaMs = game.start_utc - nowMs
    val effectivelyLive = isLive ||
        (!isFinal && deltaMs in -5 * 3600_000L..5 * 3600_000L)
    val effectivelyFinal = isFinal || deltaMs < -5 * 3600_000L
    val showScores = (isLive || isFinal) &&
        !game.score_home.isNullOrBlank() && !game.score_away.isNullOrBlank()

    androidx.compose.runtime.LaunchedEffect(game.id, game.status, game.score_home, game.score_away) {
        com.hushtv.tv.data.EventLog.log(
            "sports",
            "card[${game.id}] status=${game.status} score=${game.score_away}-${game.score_home} showScores=$showScores"
        )
    }

    Box(
        Modifier
            // v1.44.16 — Card height bumped 200 → 220dp. Combined with
            // the new zoned Column layout below this gives the
            // scores/badges row 150dp of dedicated breathing room —
            // impossible to clip at any font scale a user might set.
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
                    0f to Color(0xFF111A2C),
                    1f to Color(0xFF050810),
                )
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Cyan else Color(0x22FFFFFF),
                shape = cardShape,
            ),
    ) {
        // v1.44.16 — Rewrote card internals as a three-zone Column:
        //   Zone 1 (fixed 22dp) : status row (league tag + LIVE/ETA)
        //   Zone 2 (weight 1f)  : scores/badges (the hero of the card)
        //   Zone 3 (fixed 2dp)  : cyan "live pulse" accent line
        //
        // Column is strict vertical stacking — zones physically
        // cannot overlap each other by construction. The weight(1f)
        // middle zone absorbs all remaining space, so if a translator
        // gives us "Manchester United" instead of "MAN UTD" the score
        // row just gets less vertical slack; it never spills into
        // the status row and never clips at the bottom.
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            // ── Zone 1: Top status row ──
            Row(
                Modifier.fillMaxWidth().height(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    letterSpacing = 2.sp,
                    fontFamily = Inter,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                            if (showScores) "LIVE" else "LIVE · ${elapsedShort(deltaMs)}",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            fontFamily = Inter,
                            maxLines = 1,
                        )
                    }
                } else if (effectivelyFinal) {
                    Text(
                        "FINAL",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        fontFamily = Inter,
                    )
                } else {
                    Text(
                        friendlyCountdown(game.start_utc),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = Inter,
                        maxLines = 1,
                    )
                }
            }

            // ── Zone 2: Logos + scores/VS row, with FULL team names
            //          stacked directly under each logo. ──
            //
            // v1.44.17 — User feedback: previous "[badge] NAME VS NAME
            // [badge]" single-row layout truncated team names with
            // "..." on long names like TIMBERWOLVES. New layout uses
            // three side-by-side Columns:
            //   ┌ badge ┐  ┌ score / VS ┐  ┌ badge ┐
            //   └ name  ┘                  └ name  ┘
            // Team names go UNDER the badges, in a smaller font, with
            // softWrap disabled so they NEVER show "...". Column with
            // `weight(1f)` on each side gives the name as much
            // horizontal room as possible. Badges + score box are
            // top-aligned and the score is height-matched to the
            // badge so it visually centers on the badge row even
            // though the side columns are taller (badge + name).
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    // ─ Away team column (badge + full name underneath) ─
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TeamBadgeOnly(
                            badgeUrl = game.away?.badge_url ?: game.away?.logo_url,
                            modifier = Modifier.size(48.dp),
                            focused = focused,
                            accent = accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (game.away?.short_name ?: game.away?.name ?: "TBA").uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            fontFamily = Inter,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }

                    // ─ Center: scores OR "VS" — height-locked to 48dp
                    //   (badge height) so it visually aligns with the
                    //   badge centers regardless of the name text below. ─
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showScores) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    game.score_away ?: "0",
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = Inter,
                                    maxLines = 1,
                                )
                                Text(
                                    "—",
                                    color = Color(0xFF475569),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Inter,
                                )
                                Text(
                                    game.score_home ?: "0",
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = Inter,
                                    maxLines = 1,
                                )
                            }
                        } else {
                            Text(
                                "VS",
                                color = accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = Inter,
                            )
                        }
                    }

                    // ─ Home team column (badge + full name underneath) ─
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TeamBadgeOnly(
                            badgeUrl = game.home?.badge_url ?: game.home?.logo_url,
                            modifier = Modifier.size(48.dp),
                            focused = focused,
                            accent = accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (game.home?.short_name ?: game.home?.name ?: "TBA").uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            fontFamily = Inter,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            // ── Zone 3: Bottom accent pulse line ──
            // A hair-thin horizontal line. When the card is focused
            // it brightens to full cyan; otherwise a subtle 20% tint.
            // Replaces the old channel chip (removed v1.44.13) and
            // gives the card a "finished" feel without consuming any
            // meaningful vertical space.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (focused) accent
                        else accent.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

/**
 * Just the badge image (no name, no score). Used in the "scoreboard"
 * layout where the score numbers are the central element and the
 * badges are decorative bookends.
 *
 * v1.44.14 — When [focused] is true and an [accent] is supplied, the
 * badge gets a soft accent-coloured halo behind it. Reads as
 * "this card is hot, watch this game" without flashy motion.
 */
@Composable
private fun TeamBadgeOnly(
    badgeUrl: String?,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    accent: Color = Cyan,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (focused) {
            // v1.44.15 — glow rendered as a slightly OVER-SIZED Box
            // behind the badge. Original v1.44.14 used a negative
            // padding to bleed past the badge edge — Compose throws
            // `IllegalArgumentException: Padding must be non-negative`
            // on negative values, instantly crashing the card on
            // focus. Using a positive `requiredSize` set 12dp larger
            // than the badge produces the same visual halo without
            // touching padding.
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            0.0f to accent.copy(alpha = 0.35f),
                            0.55f to accent.copy(alpha = 0.10f),
                            1.0f to Color.Transparent,
                        )
                    )
            )
        }
        if (!badgeUrl.isNullOrBlank()) {
            AsyncImage(
                model = badgeUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1F2937))
            )
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
    focused: Boolean = false,
    accent: Color = Cyan,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = align,
    ) {
        // v1.44.14 — Reuse TeamBadgeOnly so the focus glow logic is
        // shared between the scoreboard layout (TeamBadgeOnly direct)
        // and the fallback-with-names layout (TeamBlock wraps it).
        TeamBadgeOnly(
            badgeUrl = badgeUrl,
            modifier = Modifier.size(56.dp),
            focused = focused,
            accent = accent,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            name.uppercase(),
            color = Color.White,
            // v1.44.14 — Bumped 14sp → 18sp now that the channel chip
            // is gone. Card has the headroom and the names are the
            // hero of this layout.
            fontSize = 18.sp,
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
