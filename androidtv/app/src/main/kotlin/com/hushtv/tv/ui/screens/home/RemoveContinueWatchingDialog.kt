package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.delay

/**
 * Confirmation modal shown when a user long-presses a Continue Watching
 * card. "Remove" clears the entry from [WatchProgressStore]; "Cancel"
 * dismisses. BACK on the remote also dismisses.
 *
 * Focus lands on the Remove button by default — Tivimate convention — so a
 * second OK press confirms the removal instantly.
 */
@Composable
fun RemoveContinueWatchingDialog(
    entry: ContinueEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val removeFocus = remember { FocusRequester() }
    // Debounce window — ignore any Enter/OK events for the first 400 ms after
    // the dialog appears. Belt-and-suspenders protection against the OK key
    // press that triggered the long-press still being physically held down
    // when focus arrives on the Remove button.
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { removeFocus.requestFocus() }
        delay(400)
        ready = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            // Full-screen dialog: we draw our own dim layer. Without
            // this, Compose caps the dialog window at ~280dp wide and
            // our Surface gets clipped.
            usePlatformDefaultWidth = false,
        ),
    ) {
    // Dim-scrim Box. Crucially this is NOT focusable() and has NO key
    // handler — otherwise focus latches onto the scrim instead of the
    // Remove button and every key gets swallowed (the bug fixed in
    // v1.43.36). The Dialog window above already isolates focus from
    // the home pager behind us, so no key-eating is needed here.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xFF0B111D),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(min = 520.dp, max = 620.dp)
                .border(1.dp, Color(0x4D06B6D4), RoundedCornerShape(20.dp)),
        ) {
            Column(
                Modifier.padding(horizontal = 36.dp, vertical = 30.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color(0x26FFFFFF), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "REMOVE FROM LIST",
                            color = Cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            fontFamily = Inter,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.progress.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Inter,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Remove this title from Continue Watching? Your playback progress will be cleared — you can still find it under Movies or Series.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogButton(
                        label = "Remove",
                        primary = true,
                        focusRequester = removeFocus,
                        // Debounce: ignore the leftover OK press from
                        // the long-press that opened this dialog.
                        onClick = { if (ready) onConfirm() },
                    )
                    DialogButton(
                        label = "Cancel",
                        primary = false,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun DialogButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val mod = Modifier
        .height(48.dp)
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .tvFocusable(shape = shape)
        .clickableWithEnter(onClick)
    Surface(
        color = if (primary) Cyan else Color(0x33FFFFFF),
        shape = shape,
        modifier = mod,
    ) {
        Box(
            Modifier.padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (primary) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
