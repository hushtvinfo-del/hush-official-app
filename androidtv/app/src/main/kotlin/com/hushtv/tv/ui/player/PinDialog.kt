package com.hushtv.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * 4-digit PIN dialog. Remote D-pad: digits from number keys or from focus
 * ring on the on-screen number pad. OK confirms, * clears, BACK cancels.
 *
 * Use [mode] to pick the behaviour:
 *  - Verify: asks for the existing PIN (used to unlock a category)
 *  - SetNew: asks twice to confirm a new PIN
 *  - Change: asks for old, then new twice
 */
enum class PinMode { Verify, SetNew }

@Composable
fun PinDialog(
    mode: PinMode,
    title: String = if (mode == PinMode.Verify) "Enter PIN" else "Set a new PIN",
    onCancel: () -> Unit,
    onSuccess: (String) -> Unit,
    verifier: ((String) -> Boolean)? = null
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var askingSecond by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    val current = if (askingSecond) second else first
    fun push(d: String) {
        error = null
        if (askingSecond) { if (second.length < 4) second += d }
        else { if (first.length < 4) first += d }
    }
    fun pop() {
        error = null
        if (askingSecond) { if (second.isNotEmpty()) second = second.dropLast(1) }
        else { if (first.isNotEmpty()) first = first.dropLast(1) }
    }

    // Auto-confirm when 4 digits typed
    LaunchedEffect(current) {
        if (current.length == 4) {
            when (mode) {
                PinMode.Verify -> {
                    if (verifier?.invoke(current) == true) onSuccess(current)
                    else { error = "Incorrect PIN"; first = "" }
                }
                PinMode.SetNew -> {
                    if (!askingSecond) {
                        askingSecond = true
                    } else {
                        if (first == second) onSuccess(first)
                        else {
                            error = "PINs don't match — try again"
                            first = ""; second = ""; askingSecond = false
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0xE6000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color(0xFF0B111D),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(0.55f)
                    .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(20.dp))
                    .focusRequester(rootFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.Zero, Key.NumPad0 -> { push("0"); true }
                            Key.One, Key.NumPad1 -> { push("1"); true }
                            Key.Two, Key.NumPad2 -> { push("2"); true }
                            Key.Three, Key.NumPad3 -> { push("3"); true }
                            Key.Four, Key.NumPad4 -> { push("4"); true }
                            Key.Five, Key.NumPad5 -> { push("5"); true }
                            Key.Six, Key.NumPad6 -> { push("6"); true }
                            Key.Seven, Key.NumPad7 -> { push("7"); true }
                            Key.Eight, Key.NumPad8 -> { push("8"); true }
                            Key.Nine, Key.NumPad9 -> { push("9"); true }
                            Key.Delete, Key.Backspace -> { pop(); true }
                            Key.Back, Key.Escape -> { onCancel(); true }
                            else -> false
                        }
                    }
            ) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Lock, null, tint = Cyan, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (askingSecond) "Confirm new PIN" else title,
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(18.dp))
                    // Pin dots
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(4) { i ->
                            val filled = i < current.length
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(
                                        if (filled) Cyan else Color(0x22FFFFFF),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                    .border(
                                        1.dp, Color(0x5506B6D4),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    error?.let {
                        Text(it, color = Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        "Type 4 digits using your remote",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
            }
        }
    }
}
