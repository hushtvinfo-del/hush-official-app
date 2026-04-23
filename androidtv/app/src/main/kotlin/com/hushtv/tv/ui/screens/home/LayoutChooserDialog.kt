@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.data.LayoutPrefsStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import kotlinx.coroutines.delay

/**
 * First-run / Settings layout chooser. Full-screen modal with two
 * large, tap-friendly cards showing a mini-mockup of each layout.
 * Selecting one persists the choice via [LayoutPrefsStore] and
 * invokes [onPicked] so the caller can dismiss + re-compose the
 * screen with the new mode.
 *
 * Heading and copy are tuned for a TV remote audience — big type,
 * short sentences, unambiguous CTAs.
 */
@Composable
fun LayoutChooserDialog(
    currentMode: String,
    dismissable: Boolean,
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val topFocus = remember { FocusRequester() }
    val sideFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(240)
        runCatching {
            when (currentMode) {
                LayoutPrefsStore.MODE_SIDEBAR -> sideFocus.requestFocus()
                else -> topFocus.requestFocus()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF0B1422),
                        1.0f to Color(0xFF030509),
                    )
                )
                .padding(horizontal = 64.dp, vertical = 48.dp),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Header ──
                Text(
                    "CHOOSE YOUR LAYOUT",
                    color = Cyan,
                    fontSize = 11.sp,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "How should we show categories?",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 34.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This applies to Live TV, Movies and Series. " +
                        "You can change it anytime from Settings.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(28.dp))

                // ── Two cards side-by-side ──
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    LayoutCard(
                        title = "Top Bar",
                        subtitle = "RECOMMENDED · DEFAULT",
                        description = "Compact toolbar at the top. Click BROWSE to pop a full-screen category picker. More room for your library.",
                        selected = currentMode == LayoutPrefsStore.MODE_TOP,
                        preview = { TopBarPreview() },
                        focusRequester = topFocus,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onPicked(LayoutPrefsStore.MODE_TOP) },
                    )
                    LayoutCard(
                        title = "Left Sidebar",
                        subtitle = "CLASSIC",
                        description = "Persistent vertical category rail on the left. Every category visible at once — fastest to scan.",
                        selected = currentMode == LayoutPrefsStore.MODE_SIDEBAR,
                        preview = { SidebarPreview() },
                        focusRequester = sideFocus,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onPicked(LayoutPrefsStore.MODE_SIDEBAR) },
                    )
                }

                Spacer(Modifier.height(16.dp))
                if (dismissable) {
                    Text(
                        "Press BACK to keep current layout",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = Inter,
                    )
                } else {
                    Text(
                        "Pick one to continue — you can change this later in Settings.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = Inter,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutCard(
    title: String,
    subtitle: String,
    description: String,
    selected: Boolean,
    preview: @Composable () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(150),
        label = "layout-card-scale",
    )
    Column(
        modifier
            .widthIn(max = 560.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (focused) Cyan.copy(alpha = 0.08f) else Color(0xFF0C1523),
                shape,
            )
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                color = when {
                    focused -> Cyan
                    selected -> Cyan.copy(alpha = 0.6f)
                    else -> Color(0x22FFFFFF)
                },
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(24.dp),
    ) {
        // Mini preview.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF050B17))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)),
        ) {
            preview()
        }

        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                subtitle,
                color = Cyan,
                fontSize = 10.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Inter,
            )
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .background(Cyan, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "CURRENT",
                        color = Color(0xFF05080F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                        fontFamily = Inter,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 28.sp,
            fontFamily = Inter,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            color = Color(0xFFCBD5E1),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontFamily = Inter,
        )
    }
}

/** Mini mockup of the Top-Bar layout. */
@Composable
private fun TopBarPreview() {
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        // Top bar.
        Row(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Color(0xFF14223A), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 34.dp, height = 8.dp).background(Cyan, RoundedCornerShape(2.dp)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 22.dp, height = 8.dp).background(Color(0x55FFFFFF), RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.height(8.dp))
        // Grid.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(6) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(24.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** Mini mockup of the Sidebar layout. */
@Composable
private fun SidebarPreview() {
    Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Sidebar.
        Column(
            Modifier
                .width(54.dp)
                .fillMaxHeight()
                .background(Color(0xFF0F1A2D), RoundedCornerShape(4.dp))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(7) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            if (i == 1) Cyan.copy(alpha = 0.6f) else Color(0xFF1E293B),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
        // Grid.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(20.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}
