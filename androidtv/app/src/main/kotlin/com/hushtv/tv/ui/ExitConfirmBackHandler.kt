package com.hushtv.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan

/**
 * Drop-in composable for a Home screen's BACK behaviour: instead of
 * silently exiting the app, show a confirmation dialog so the user
 * has a chance to cancel an accidental back press.
 *
 * Behaviour
 * ─────────
 * • First BACK on the host screen → opens the dialog (and consumes
 *   the back press).
 * • BACK while the dialog is open → dismisses the dialog (no exit).
 * • Confirming the dialog calls `Activity.finish()` on the hosting
 *   activity, which closes HushTV and returns to the Android
 *   launcher.
 *
 * UX notes
 * ────────
 * • Custom dark-navy dialog body (matches HushTV's design tokens),
 *   rendered through Material3 AlertDialog so it inherits proper
 *   modal semantics + dimming + outside-tap dismissal.
 * • The "Exit" button is auto-focused on TV so a single ENTER press
 *   confirms exit; "Cancel" is the secondary option behind it on
 *   the focus order so accidental Enter spamming doesn't bypass
 *   the prompt.
 * • Cancel is the LEFT button so D-pad LEFT-from-Exit lands on
 *   Cancel, mirroring the standard TV-launcher convention.
 */
@Composable
fun ExitConfirmBackHandler() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val exitFocus = remember { FocusRequester() }

    BackHandler(enabled = true) { open = true }

    if (open) {
        // Auto-focus the destructive option after the dialog is
        // composed — small delay lets the requester attach.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(120)
            runCatching { exitFocus.requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Color(0xFF0B1424),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Exit HushTV?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp,
                )
            },
            text = {
                Text(
                    "You're about to leave the app. Continue watching " +
                        "from where you left off any time you re-open it.",
                    color = Color(0xFFB8BDC7),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogChip(
                        label = "Cancel",
                        primary = false,
                        onClick = { open = false },
                    )
                    Spacer(Modifier.width(10.dp))
                    DialogChip(
                        label = "Exit",
                        primary = true,
                        focusRequester = exitFocus,
                        onClick = {
                            open = false
                            // v1.44.99 — finishAndRemoveTask + PID kill
                            // (see ExitToLauncher.kt for why a plain
                            // Activity.finish() got the app stuck in a
                            // Fire-TV-relaunch loop after we switched
                            // to release builds in v1.44.96).
                            exitToLauncher(ctx)
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun DialogChip(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val (bg, fg, border) = when {
        primary && focused -> Triple(Cyan, Color(0xFF05080F), Cyan)
        primary -> Triple(Color(0xFF1E3A8A), Color.White, Cyan)
        focused -> Triple(Color(0xFF1E293B), Color.White, Color(0xFF3B82F6))
        else -> Triple(Color(0xFF111827), Color(0xFFE2E8F0), Color(0x33FFFFFF))
    }
    Column(
        Modifier
            .height(44.dp)
            .background(bg, shape)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
        )
    }
}
