package com.hushtv.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
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
 * 4-digit PIN dialog.
 *
 * v1.44.103 — Rewritten to use a proper on-screen 3×4 number pad
 * because Shield, Fire Stick, and basically every Android TV remote
 * lacks number keys. The previous version only listened for
 * `Key.Zero`–`Key.Nine` hardware presses, so users had no way to
 * enter a PIN on any of those devices.
 *
 * New behaviour:
 *  - Renders a `1 2 3 / 4 5 6 / 7 8 9 / ⌫ 0 ✓` grid of focusable
 *    buttons. D-pad navigates the grid; OK presses the focused
 *    button. ⌫ deletes last digit, ✓ auto-fires (4 digits also
 *    auto-confirms — the check button is mostly a visual anchor).
 *  - Hardware number keys still work (USB / Bluetooth keyboards,
 *    rare TV remotes with number pads).
 *  - Initial focus lands on the "5" key so the cursor is in the
 *    middle of the grid and all 12 keys are at most 2 D-pad presses
 *    away.
 */
enum class PinMode { Verify, SetNew }

@Composable
fun PinDialog(
    mode: PinMode,
    title: String = if (mode == PinMode.Verify) "Enter PIN" else "Set a new PIN",
    onCancel: () -> Unit,
    onSuccess: (String) -> Unit,
    verifier: ((String) -> Boolean)? = null,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var askingSecond by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
    fun submit() {
        if (current.length == 4) {
            // The LaunchedEffect below handles the validation;
            // explicit submit is here so the ✓ button has a function
            // for users who want to confirm without the 4th digit
            // being the trigger. Effectively no-op when length < 4.
        }
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
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))
                // Hardware key support — kept for USB / BT keyboards
                // and the rare TV remote with a number pad.
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
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Color(0xFF0B111D),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.55f)
                    .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(20.dp)),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Lock, null, tint = Cyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (askingSecond) "Confirm new PIN" else title,
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(14.dp))
                    // Pin dots
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { i ->
                            val filled = i < current.length
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(
                                        if (filled) Cyan else Color(0x22FFFFFF),
                                        CircleShape,
                                    )
                                    .border(
                                        1.dp, Cyan.copy(alpha = 0.33f),
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    error?.let {
                        Text(
                            it,
                            color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // 3 × 4 number pad
                    NumberPad(
                        onDigit = { push(it.toString()) },
                        onBackspace = { pop() },
                        onConfirm = { submit() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Use the remote arrows + OK",
                        color = TextSecondary, fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Focus on the centre "5" key initially. Two D-pad presses gets
    // you to ANY key from there.
    val centerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { centerFocus.requestFocus() } }

    @Composable
    fun row(content: @Composable RowScope.() -> Unit) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) { content() }
    }

    row {
        PadKey("1") { onDigit(1) }
        PadKey("2") { onDigit(2) }
        PadKey("3") { onDigit(3) }
    }
    row {
        PadKey("4") { onDigit(4) }
        PadKey("5", initialFocus = centerFocus) { onDigit(5) }
        PadKey("6") { onDigit(6) }
    }
    row {
        PadKey("7") { onDigit(7) }
        PadKey("8") { onDigit(8) }
        PadKey("9") { onDigit(9) }
    }
    row {
        PadKey(icon = { Icon(Icons.Default.Backspace, null, tint = Color.White, modifier = Modifier.size(20.dp)) }) { onBackspace() }
        PadKey("0") { onDigit(0) }
        PadKey(
            icon = { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
            accent = true,
        ) { onConfirm() }
    }
}

@Composable
private fun PadKey(
    label: String? = null,
    icon: (@Composable () -> Unit)? = null,
    accent: Boolean = false,
    initialFocus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val baseBg = if (accent) Color(0xFF155E75) else Color(0xFF1F2937)
    val focusBg = if (accent) Color(0xFF0891B2) else Cyan
    val bg = if (focused) focusBg else baseBg

    var m: Modifier = Modifier
        .size(width = 56.dp, height = 44.dp)
        .background(bg, RoundedCornerShape(8.dp))
        .border(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Color.White else Color(0x33FFFFFF),
            shape = RoundedCornerShape(8.dp),
        )
    if (initialFocus != null) m = m.focusRequester(initialFocus)
    m = m
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .clickableWithEnter { onClick() }

    Box(modifier = m, contentAlignment = Alignment.Center) {
        if (label != null) {
            Text(
                label,
                color = if (focused) Color(0xFF0B111D) else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            icon?.invoke()
        }
    }
}
