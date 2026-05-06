@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.lite

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.AppMode
import com.hushtv.tv.data.AppModeStore
import com.hushtv.tv.data.DeviceCapability
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable

/**
 * v1.44.19 — First-launch capability dialog.
 *
 * Shows once (controlled by [AppModeStore.hasPrompted]). After
 * the user picks (or dismisses), it never re-pops on cold
 * launches. They can still switch via Settings → App Mode.
 *
 * Soft recommendation: detector picks Lite or Pro, dialog
 * highlights the recommended button but the user can pick
 * either.
 */
@Composable
fun LiteFirstLaunchDialog(onPicked: (AppMode) -> Unit) {
    val ctx = LocalContext.current
    val capability = remember { DeviceCapability.detect(ctx) }
    val recommended = capability.recommended

    val proFocus = remember { FocusRequester() }
    val liteFocus = remember { FocusRequester() }

    // v1.44.23 — ALWAYS focus Pro by default, regardless of the
    // detector's recommendation. Pro is the headline experience;
    // we don't want to nudge a NVIDIA Shield user into Lite even
    // if the heuristic gets confused. The detector still earns the
    // "RECOMMENDED" cyan pill on whichever button is best for them.
    LaunchedEffect(Unit) {
        runCatching { proFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(680.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0B1220))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(36.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "WELCOME TO HUSHTV",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Pick the version that fits your TV",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Most people want Pro. If your TV feels slow with Pro, switch to Lite anytime in Settings.",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                fontFamily = Inter,
            )
            Spacer(Modifier.height(24.dp))

            // ── Pro is shown FIRST and is the default focus. ──
            ModeOptionRow(
                label = "Pro",
                tagline = "The full HushTV experience — smooth animations, big posters, " +
                    "cinematic backgrounds. Best for Fire Stick 4K, NVIDIA Shield, " +
                    "Chromecast with Google TV, Onn 4K Pro, and similar streaming boxes.",
                isRecommended = recommended == AppMode.PRO,
                focusRequester = proFocus,
                onPick = {
                    AppModeStore.save(ctx, AppMode.PRO)
                    AppModeStore.markPrompted(ctx)
                    onPicked(AppMode.PRO)
                },
            )
            Spacer(Modifier.height(12.dp))
            ModeOptionRow(
                label = "Lite",
                tagline = "Stripped-down version that runs fast on older or slower TVs. " +
                    "Pick this only if your TV is a built-in smart TV (Hisense, " +
                    "TCL, Sceptre, etc.) and Pro feels laggy.",
                isRecommended = recommended == AppMode.LITE,
                focusRequester = liteFocus,
                onPick = {
                    AppModeStore.save(ctx, AppMode.LITE)
                    AppModeStore.markPrompted(ctx)
                    onPicked(AppMode.LITE)
                },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "You can switch between Pro and Lite any time in Settings → App Mode.",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontFamily = Inter,
            )
        }
    }
}

@Composable
private fun ModeOptionRow(
    label: String,
    tagline: String,
    isRecommended: Boolean,
    focusRequester: FocusRequester,
    onPick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(scaleOnFocus = 1f, shape = shape)
            .clickableWithEnter(onPick)
            .clip(shape)
            .background(
                if (focused) Color(0xFF1E293B)
                else if (isRecommended) Color(0xFF0F1B2E)
                else Color(0x14FFFFFF)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    isRecommended -> Cyan.copy(alpha = 0.5f)
                    else -> Color(0x22FFFFFF)
                },
                shape = shape,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = Inter,
                )
                if (isRecommended) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Cyan)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "RECOMMENDED",
                            color = Color(0xFF050810),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            fontFamily = Inter,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tagline,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontFamily = Inter,
            )
        }
        Text(
            "›",
            color = if (focused) Cyan else Color(0xFF64748B),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            fontFamily = Inter,
        )
    }
}
