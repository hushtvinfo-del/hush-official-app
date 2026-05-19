package com.hushtv.tv.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.hushtv.tv.BuildConfig
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * Settings dialog that explains the auto-pilot demo and offers a single
 * "Start" button. The recording is actually kicked off by the host
 * activity, which owns the MediaProjection permission launcher.
 */
@Composable
fun DemoModeDialog(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val phase by DemoController.phase.collectAsState()
    val lastClip by DemoController.outputPath.collectAsState()
    val uploadStatus by DemoController.uploadStatus.collectAsState()
    val isBusy = phase != DemoController.Phase.Idle

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = Color(0xFF0B0F14),
        title = {
            Text(
                text = "Auto-pilot Demo Recorder",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column {
                Text(
                    "Records the screen for ~90 seconds while the app " +
                        "automatically tours every Home section (Discovery, " +
                        "Streaming, Collections, Genres, Themes, Decades). " +
                        "1080p · 60 fps · 12 Mbps · system audio.",
                    color = Color(0xFFBFC5D1),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                val uploadLine = when {
                    BuildConfig.DEMO_UPLOAD_URL.isBlank() ->
                        "Upload URL not configured — clip will only be saved locally."
                    else ->
                        "Auto-upload: ${BuildConfig.DEMO_UPLOAD_URL}"
                }
                Text(
                    uploadLine,
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                if (lastClip != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Last clip: ${lastClip!!.substringAfterLast('/')}",
                        color = Cyan,
                        fontSize = 12.sp,
                    )
                }
                if (uploadStatus != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        uploadStatus!!,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogActionBtn(
                        label = when (phase) {
                            DemoController.Phase.Idle -> "Start recording"
                            DemoController.Phase.Preparing -> "Preparing…"
                            DemoController.Phase.Recording -> "Recording — stop"
                            DemoController.Phase.Stopping -> "Finalising…"
                        },
                        cyan = phase == DemoController.Phase.Idle,
                        red = phase == DemoController.Phase.Recording,
                        enabled = phase == DemoController.Phase.Idle ||
                            phase == DemoController.Phase.Recording,
                        testTag = "demo-start-stop",
                        onClick = {
                            if (phase == DemoController.Phase.Idle) onStart()
                            else if (phase == DemoController.Phase.Recording) {
                                DemoController.stopRecording(ctx.applicationContext)
                            }
                        },
                    )
                    DialogActionBtn(
                        label = "Close",
                        cyan = false, red = false, enabled = !isBusy,
                        testTag = "demo-close",
                        onClick = onDismiss,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DialogActionBtn(
    label: String,
    cyan: Boolean,
    red: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        !enabled -> Color(0xFF1F2937)
        red && focused -> Color(0xFFB91C1C)
        red -> Color(0xFF7F1D1D)
        cyan && focused -> Color(0xFF0891B2)
        cyan -> Color(0xFF155E75)
        focused -> Color(0xFF334155)
        else -> Color(0xFF1F2937)
    }
    val border = if (focused) Color.White else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(if (focused) 2.dp else 0.dp, border, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .clickableWithEnter { if (enabled) onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (enabled) Color.White else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
