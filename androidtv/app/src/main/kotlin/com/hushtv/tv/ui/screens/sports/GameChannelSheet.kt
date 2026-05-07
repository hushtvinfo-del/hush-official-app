@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.sports.SportsApi
import com.hushtv.tv.data.sports.SportsGame
import com.hushtv.tv.data.sports.SportsGameChannel
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * v1.44.27 — Sports channel picker.
 *
 * Full-screen overlay that opens when a user clicks a game card.
 * Lists every channel from THEIR Xtream EPG that's currently airing
 * the game, Canadian-first sorted. Focus is TRAPPED inside the sheet
 * so DPad navigation cannot accidentally control the page beneath.
 *
 * Focus + dismiss flow (v1.44.28):
 *   • The outer Box is `focusable()` and grabs focus on first
 *     composition, so the underlying page never sees subsequent
 *     navigation key events.
 *   • All UP/DOWN/LEFT/RIGHT/CENTER/Back key events are CONSUMED
 *     in `onPreviewKeyEvent` (return true) — the underlying
 *     LeaguePillBar / GameCardsRail focus handlers never fire.
 *   • Even the empty / loading / error states have a focusable
 *     "Dismiss" button so there's always a target for focus.
 *   • A real `BackHandler { onDismiss() }` covers the hardware
 *     Back button reliably (some Fire-TV remotes route Back
 *     differently than DPad keys).
 */
@Composable
fun GameChannelSheet(
    playlistId: String,
    game: SportsGame,
    onDismiss: () -> Unit,
    onPlay: (channelName: String, streamUrl: String) -> Unit,
) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var matches by remember { mutableStateOf<List<SportsGameChannel>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }

    // Hardware-Back support — the onPreviewKeyEvent handler covers
    // DPad Back from the remote, BackHandler covers hardware Back
    // from on-screen TVs that route it differently. Both call
    // onDismiss directly so the sheet always closes on Back.
    BackHandler(enabled = true) { onDismiss() }

    LaunchedEffect(game.id) {
        loading = true
        error = null
        val p = PlaylistStore.find(ctx, playlistId)
        if (p == null) {
            error = "No playlist configured."
            loading = false
            return@LaunchedEffect
        }
        // v1.44.29 fix — gameChannels() makes a synchronous OkHttp
        // call. LaunchedEffect runs on Dispatchers.Main by default,
        // and Android throws NetworkOnMainThreadException when you
        // do network I/O on the main thread. Wrap in withContext(IO).
        val result = kotlinx.coroutines.withContext(
            kotlinx.coroutines.Dispatchers.IO,
        ) {
            SportsApi.gameChannels(
                gameId = game.id,
                host = p.host,
                username = p.username,
                password = p.password,
            )
        }
        when (result) {
            is SportsApi.GameChannelsResult.Success -> {
                matches = result.matches
            }
            is SportsApi.GameChannelsResult.NoEpgMatch -> {
                matches = emptyList()
            }
            is SportsApi.GameChannelsResult.Failure -> {
                matches = emptyList()
                error = result.reason
                android.util.Log.e("GameChannelSheet", "fetch failed: ${result.reason}")
            }
        }
        loading = false
    }

    // Force focus to the first focusable child as soon as the
    // sheet's content is settled. Whether that's the first
    // ChannelRow (matches present) or the DISMISS button (loading /
    // error / no-match), it's the same FocusRequester. Without
    // this, the outer Box keeps focus and DPad / OK presses go to
    // the page underneath.
    LaunchedEffect(loading, matches.size, error) {
        // Tiny yield so the new content has been measured + the
        // FocusRequester is attached before we request focus.
        kotlinx.coroutines.delay(50)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Solid background — opaque so the page behind is fully
            // hidden. v1.44.27 had 0xE6 alpha which let the page
            // bleed through and made users think the sheet wasn't
            // actually open.
            .background(Color(0xFF050810))
            .focusable()
            // Eat tap events so they can't reach the page beneath.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* no-op — just consume the click */ }
            // Eat ALL navigation key events so the underlying
            // LeaguePillBar / GameCardsRail can't react. The focused
            // ChannelRow children below get the events first via
            // their own onPreviewKeyEvent / onClick.
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.Back, Key.Escape -> {
                        onDismiss(); true
                    }
                    Key.DirectionUp,
                    Key.DirectionDown,
                    Key.DirectionLeft,
                    Key.DirectionRight,
                    Key.Enter,
                    Key.NumPadEnter,
                    Key.DirectionCenter -> {
                        // Don't consume — let the focused row handle.
                        // But this branch ensures the underlying page
                        // never fires onPreviewKeyEvent because the
                        // sheet's outer Box is the higher-priority
                        // ancestor in the focus tree.
                        false
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.82f)
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            // ─── Header ───
            val accent = leagueAccent(game.league?.slug)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 18.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    (game.league?.name ?: "GAME").uppercase(),
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.width(12.dp))
                if (game.status.equals("live", ignoreCase = true)) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "LIVE",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontFamily = Inter,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val matchupTitle = buildString {
                append(game.away?.name ?: "Away")
                append("  vs  ")
                append(game.home?.name ?: "Home")
            }
            Text(
                matchupTitle,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Watch on:",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(16.dp))

            // ─── Body ───
            when {
                loading -> {
                    EmptyOrLoading(
                        message = "Searching your EPG…",
                        showDismiss = true,
                        focusRequester = firstFocus,
                        onDismiss = onDismiss,
                    )
                }
                error != null -> {
                    EmptyOrLoading(
                        message = error!!,
                        accent = Color(0xFFEF4444),
                        showDismiss = true,
                        focusRequester = firstFocus,
                        onDismiss = onDismiss,
                    )
                }
                matches.isEmpty() -> {
                    EmptyOrLoading(
                        message = "No matching channels in your EPG",
                        subMessage = "Try Live TV or contact support.",
                        showDismiss = true,
                        focusRequester = firstFocus,
                        onDismiss = onDismiss,
                    )
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(matches.size, key = { idx -> matches[idx].channel_id }) { idx ->
                            val m = matches[idx]
                            ChannelRow(
                                channelName = m.channel_name,
                                programmeTitle = m.programme_title,
                                programmeSub = m.programme_sub.orEmpty(),
                                startUtcMs = m.start_utc_ms,
                                stopUtcMs = m.stop_utc_ms,
                                focusRequester = if (idx == 0) firstFocus else null,
                                onClick = {
                                    val p = PlaylistStore.find(ctx, playlistId)
                                        ?: return@ChannelRow
                                    val sid = SportsApi.findStreamIdByName(
                                        ctx, playlistId, m.channel_name,
                                    )
                                    if (sid == null) {
                                        android.widget.Toast.makeText(
                                            ctx,
                                            "Channel not in your playlist: ${m.channel_name}",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        return@ChannelRow
                                    }
                                    val url = XtreamApi.liveUrl(
                                        p.host, p.username, p.password, sid,
                                    )
                                    onPlay(m.channel_name, url)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Press BACK to dismiss · UP/DOWN to choose · OK to tune",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontFamily = Inter,
            )
        }
    }
}

@Composable
private fun EmptyOrLoading(
    message: String,
    subMessage: String? = null,
    accent: Color = Color(0xFFCBD5E1),
    showDismiss: Boolean = false,
    focusRequester: FocusRequester? = null,
    onDismiss: () -> Unit = {},
) {
    Box(
        Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter,
            )
            if (!subMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subMessage,
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontFamily = Inter,
                )
            }
            if (showDismiss) {
                Spacer(Modifier.height(20.dp))
                DismissButton(focusRequester = focusRequester, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun DismissButton(
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val mod = Modifier
        .height(44.dp)
        .width(160.dp)
        .onFocusChanged { focused = it.isFocused }
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .tvFocusable(scaleOnFocus = 1f, shape = shape)
        .clickableWithEnter(onClick)
        .clip(shape)
        .background(if (focused) Cyan else Color(0xFF111827))
        .border(
            width = if (focused) 0.dp else 1.dp,
            color = if (focused) Color.Transparent else Color(0x33FFFFFF),
            shape = shape,
        )
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Text(
            "DISMISS",
            color = if (focused) Color(0xFF050810) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun ChannelRow(
    channelName: String,
    programmeTitle: String,
    programmeSub: String,
    startUtcMs: Long,
    stopUtcMs: Long,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val mod = Modifier
        .fillMaxWidth()
        .height(78.dp)
        .onFocusChanged { focused = it.isFocused }
        .let {
            if (focusRequester != null) it.focusRequester(focusRequester) else it
        }
        .tvFocusable(scaleOnFocus = 1f, shape = shape)
        .clickableWithEnter(onClick)
        .clip(shape)
        .background(if (focused) Color(0xFF1E293B) else Color(0xFF0B1220))
        .border(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Cyan else Color(0x14FFFFFF),
            shape = shape,
        )
        .padding(horizontal = 18.dp, vertical = 10.dp)

    Row(
        modifier = mod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                channelName,
                color = if (focused) Color.White else Color(0xFFE2E8F0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                fontFamily = Inter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val displayTitle = if (programmeSub.isNotBlank())
                "$programmeTitle · $programmeSub" else programmeTitle
            Text(
                displayTitle,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontFamily = Inter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${formatLocal(startUtcMs)} → ${formatLocal(stopUtcMs)}",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontFamily = Inter,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "▶",
            color = if (focused) Cyan else Color(0xFF475569),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = Inter,
        )
    }
}

private fun formatLocal(utcMs: Long): String {
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    fmt.timeZone = TimeZone.getDefault()
    return fmt.format(Date(utcMs))
}

private fun leagueAccent(slug: String?): Color = when (slug?.lowercase()) {
    "nhl" -> Cyan
    "nba" -> Color(0xFFF97316)
    "mlb" -> Color(0xFFEF4444)
    "nfl", "ncaaf", "cfl" -> Color(0xFF22C55E)
    "epl", "ucl", "mls", "laliga" -> Color(0xFF8B5CF6)
    "ufc" -> Color(0xFFEF4444)
    "f1" -> Color(0xFFEF4444)
    else -> Cyan
}
