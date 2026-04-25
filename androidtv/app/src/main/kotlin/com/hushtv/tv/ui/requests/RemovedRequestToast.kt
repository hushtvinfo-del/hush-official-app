package com.hushtv.tv.ui.requests

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.delay

/**
 * Snackbar-style toast shown at the top of the screen for ~3 s after
 * a request is removed via long-press. Lets the user undo if it was
 * a misfire.
 *
 * Self-managing: caller flips [removed] to a non-null
 * [RemovedRequestSnack] when removal is confirmed; this composable
 * runs its own auto-dismiss timer and reports back via [onUndo] /
 * [onTimeout] / [onAutoDismiss].
 *
 * Same affordance on Mobile (status-bar aware via statusBarsPadding)
 * and TV (D-pad-focusable Undo pill) — single composable, both form
 * factors.
 */
data class RemovedRequestSnack(
    val requestId: String,
    val title: String,
)

@Composable
fun RemovedRequestToast(
    removed: RemovedRequestSnack?,
    onUndo: () -> Unit,
    onAutoDismiss: () -> Unit,
    /** Status-bar inset only matters on Mobile. TV has no status bar. */
    applyStatusBarPadding: Boolean = false,
) {
    val visible = removed != null
    val undoFocus = remember { FocusRequester() }

    LaunchedEffect(removed?.requestId) {
        if (removed == null) return@LaunchedEffect
        // Grab focus on TV so the user can immediately D-pad-Enter
        // the Undo button. Touch users tap normally.
        delay(120)
        runCatching { undoFocus.requestFocus() }
        // Auto-dismiss after 3.5 s — long enough to read + react,
        // short enough to not linger.
        delay(3_500)
        onAutoDismiss()
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            val r = removed ?: return@AnimatedVisibility
            Row(
                Modifier
                    .let { if (applyStatusBarPadding) it.statusBarsPadding() else it }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .widthIn(max = 540.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Removed",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "\"${r.title}\"",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
                Spacer(Modifier.width(14.dp))
                UndoChip(focusRequester = undoFocus, onClick = onUndo)
            }
        }
    }
}

@Composable
private fun UndoChip(focusRequester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .background(if (focused) Cyan else Cyan.copy(alpha = 0.16f), shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Cyan.copy(alpha = 0.4f),
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            "UNDO",
            color = if (focused) Color(0xFF05080F) else Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
        )
    }
}
