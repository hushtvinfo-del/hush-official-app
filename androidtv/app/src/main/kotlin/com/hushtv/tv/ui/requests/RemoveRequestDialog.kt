package com.hushtv.tv.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * Confirmation dialog shown when the user long-presses a request to
 * remove it from their app. The request is hidden client-side via
 * [com.hushtv.tv.data.RequestHiddenStore] — the admin Base44 panel
 * still has the request, so the team can fulfill or close it on
 * their side without the user needing to see it on this device.
 *
 * D-pad-focusable + touch-clickable so it works the same on Mobile
 * and TV.
 */
@Composable
fun RemoveRequestDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(0.9f)
                    .background(SurfaceNavy, RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Text(
                    "Remove this request?",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "\"$title\" will be hidden from your app. Our team " +
                        "will still see it in the admin panel and may " +
                        "still fulfill it.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(20.dp))
                DangerButton("Remove", onConfirm)
                Spacer(Modifier.height(8.dp))
                NeutralButton("Cancel", onDismiss)
            }
        }
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val red = Color(0xFFEF4444)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (focused) Color.White else red, shape)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) red else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) red else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun NeutralButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                if (focused) Cyan.copy(alpha = 0.18f) else SurfaceElev,
                shape,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) Cyan else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
