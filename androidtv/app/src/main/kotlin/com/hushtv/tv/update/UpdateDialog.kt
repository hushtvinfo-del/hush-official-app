package com.hushtv.tv.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.delay

private enum class UpdateUiState { PROMPT, DOWNLOADING, INSTALLING, FAILED, NEEDS_PERMISSION }

@Composable
fun UpdateDialog(
    info: VersionInfo,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity

    var ui by remember { mutableStateOf(UpdateUiState.PROMPT) }
    var progressPct by remember { mutableStateOf(0f) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var receiver by remember { mutableStateOf<BroadcastReceiver?>(null) }

    // Register a broadcast receiver for download completion.
    DisposableEffect(Unit) {
        val r = UpdateManager.registerOnComplete(ctx) { id ->
            if (id == downloadId) {
                // Check final status
                val p = UpdateManager.queryProgress(ctx, id)
                if (p?.status == DownloadManager.STATUS_SUCCESSFUL) {
                    ui = UpdateUiState.INSTALLING
                    val permIntent = UpdateManager.triggerInstall(ctx)
                    if (permIntent != null) {
                        ui = UpdateUiState.NEEDS_PERMISSION
                        activity?.startActivity(permIntent)
                    }
                } else {
                    ui = UpdateUiState.FAILED
                    errorMsg = "Download failed (code ${p?.reason ?: -1})"
                }
            }
        }
        receiver = r
        onDispose { UpdateManager.unregister(ctx, r) }
    }

    // Poll progress while downloading
    LaunchedEffect(ui, downloadId) {
        if (ui == UpdateUiState.DOWNLOADING && downloadId != null) {
            while (ui == UpdateUiState.DOWNLOADING) {
                val p = UpdateManager.queryProgress(ctx, downloadId!!)
                if (p != null && p.bytesTotal > 0) {
                    progressPct = (p.bytesDownloaded.toFloat() / p.bytesTotal.toFloat()).coerceIn(0f, 1f)
                }
                if (p?.status == DownloadManager.STATUS_FAILED) {
                    ui = UpdateUiState.FAILED
                    errorMsg = "Download failed"
                    break
                }
                delay(500)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (ui == UpdateUiState.PROMPT) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = ui == UpdateUiState.PROMPT,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color(0xFF0B111D),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(0.7f)
                    .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(24.dp))
            ) {
                Column(Modifier.padding(32.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF3B82F6), Cyan)),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Download, null,
                                tint = Color.White, modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Update available",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "v${UpdateManager.currentVersionName()}  →  v${info.versionName}",
                                color = Cyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    when (ui) {
                        UpdateUiState.PROMPT -> PromptBody(
                            info = info,
                            onUpdate = {
                                try {
                                    downloadId = UpdateManager.enqueueDownload(ctx, info.apkUrl)
                                    ui = UpdateUiState.DOWNLOADING
                                } catch (e: Exception) {
                                    ui = UpdateUiState.FAILED
                                    errorMsg = e.message ?: "Failed to start download"
                                }
                            },
                            onLater = onDismiss,
                            mandatory = info.mandatory
                        )
                        UpdateUiState.DOWNLOADING -> DownloadingBody(progressPct)
                        UpdateUiState.INSTALLING -> InstallingBody()
                        UpdateUiState.NEEDS_PERMISSION -> NeedsPermissionBody(
                            onRetry = {
                                // Re-trigger install once user returns from settings
                                ui = UpdateUiState.INSTALLING
                                val more = UpdateManager.triggerInstall(ctx)
                                if (more != null) {
                                    activity?.startActivity(more)
                                }
                            },
                            onDismiss = onDismiss
                        )
                        UpdateUiState.FAILED -> FailedBody(
                            msg = errorMsg ?: "Update failed",
                            onRetry = {
                                errorMsg = null
                                progressPct = 0f
                                downloadId = null
                                ui = UpdateUiState.PROMPT
                            },
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptBody(
    info: VersionInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    mandatory: Boolean
) {
    val updateFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { updateFocus.requestFocus() } }

    Text("What's new", color = TextSecondary, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    Spacer(Modifier.height(10.dp))

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (info.changelog.isEmpty()) {
            Text("• Bug fixes and improvements",
                color = Color(0xFFE5E7EB), fontSize = 15.sp)
        } else {
            info.changelog.take(8).forEach { line ->
                Text("•  $line", color = Color(0xFFE5E7EB), fontSize = 15.sp)
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(
            text = "Update Now",
            modifier = Modifier.weight(1f).focusRequester(updateFocus),
            onClick = onUpdate
        )
        if (!mandatory) {
            SecondaryButton(
                text = "Later",
                modifier = Modifier.weight(1f),
                onClick = onLater
            )
        }
    }
}

@Composable
private fun DownloadingBody(pct: Float) {
    Text(
        "Downloading update…",
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(14.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0x20FFFFFF), RoundedCornerShape(4.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0f, 1f))
                .height(8.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Cyan)),
                    RoundedCornerShape(4.dp)
                )
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "${(pct * 100).toInt()}% — please stay on this screen",
        color = TextSecondary,
        fontSize = 13.sp
    )
}

@Composable
private fun InstallingBody() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = Cyan, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Opening installer…",
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Press 'Install' on the next screen to finish.",
                color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NeedsPermissionBody(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Settings, null, tint = Color(0xFFFACC15), modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text("Enable 'Install unknown apps' for HushTV",
            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Android requires your permission before HushTV can install an update. " +
            "Enable it in the settings screen we just opened, then come back here.",
        color = TextSecondary, fontSize = 14.sp
    )
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("I've enabled it — Install", Modifier.weight(1f), onRetry)
        SecondaryButton("Cancel", Modifier.weight(1f), onDismiss)
    }
}

@Composable
private fun FailedBody(msg: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Error, null, tint = Red, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text("Update failed", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(6.dp))
    Text(msg, color = Color(0xFFFCA5A5), fontSize = 14.sp)
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("Retry", Modifier.weight(1f), onRetry)
        SecondaryButton("Dismiss", Modifier.weight(1f), onDismiss)
    }
}

// ─── Buttons ─────────────────────────────────────────────────────────────

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .tvFocusable(shape = RoundedCornerShape(14.dp))
            .clickableWithEnter(onClick)
    ) {
        Box(
            Modifier
                .background(
                    Brush.linearGradient(listOf(Color(0xFF3B82F6), Cyan)),
                    RoundedCornerShape(14.dp)
                )
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Color(0x1AFFFFFF),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .tvFocusable(shape = RoundedCornerShape(14.dp))
            .clickableWithEnter(onClick)
    ) {
        Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
