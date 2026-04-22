package com.hushtv.tv.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private enum class UpdateUiState {
    PROMPT,
    NEEDS_PERMISSION,     // shown BEFORE download if permission missing
    DOWNLOADING,
    INSTALLING,
    NEEDS_PERMISSION_POST,// shown AFTER download if permission got revoked mid-flight
    FAILED,
}

@Composable
fun UpdateDialog(
    info: VersionInfo,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity

    // If the "Install unknown apps" permission isn't granted we jump straight
    // to the permission prompt — no point downloading if the OS will refuse
    // to install anyway.
    val initialState = if (UpdateManager.canInstallPackages(ctx)) UpdateUiState.PROMPT
    else UpdateUiState.NEEDS_PERMISSION

    var ui by remember { mutableStateOf(initialState) }
    var progressPct by remember { mutableStateOf(0f) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var manualPath by remember { mutableStateOf<String?>(null) }

    // Re-check permission on resume (user may have granted it in settings and come back).
    LaunchedEffect(ui) {
        while (ui == UpdateUiState.NEEDS_PERMISSION || ui == UpdateUiState.NEEDS_PERMISSION_POST) {
            delay(600)
            if (UpdateManager.canInstallPackages(ctx)) {
                ui = if (ui == UpdateUiState.NEEDS_PERMISSION) UpdateUiState.PROMPT
                else UpdateUiState.INSTALLING
            }
        }
    }

    // Broadcast receiver for download completion.
    DisposableEffect(Unit) {
        val r = UpdateManager.registerOnComplete(ctx) { id ->
            if (id == downloadId && ui == UpdateUiState.DOWNLOADING) {
                val p = UpdateManager.queryProgress(ctx, id)
                if (p?.status == DownloadManager.STATUS_SUCCESSFUL) {
                    ui = UpdateUiState.INSTALLING
                } else {
                    ui = UpdateUiState.FAILED
                    errorMsg = "Download failed (code ${p?.reason ?: -1})"
                }
            }
        }
        onDispose { UpdateManager.unregister(ctx, r) }
    }

    // Polling fallback — some TV boxes suppress the ACTION_DOWNLOAD_COMPLETE broadcast.
    LaunchedEffect(ui, downloadId) {
        if (ui == UpdateUiState.DOWNLOADING && downloadId != null) {
            while (ui == UpdateUiState.DOWNLOADING) {
                val p = UpdateManager.queryProgress(ctx, downloadId!!)
                if (p != null && p.bytesTotal > 0) {
                    progressPct = (p.bytesDownloaded.toFloat() / p.bytesTotal.toFloat()).coerceIn(0f, 1f)
                }
                when (p?.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        ui = UpdateUiState.INSTALLING
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        ui = UpdateUiState.FAILED
                        errorMsg = "Download failed (reason ${p.reason})"
                        break
                    }
                }
                delay(600)
            }
        }
    }

    // Kick the installer whenever we enter the INSTALLING state.
    LaunchedEffect(ui) {
        if (ui == UpdateUiState.INSTALLING) {
            try {
                val permIntent = UpdateManager.triggerInstall(ctx)
                if (permIntent != null) {
                    // Permission was revoked mid-flight
                    ui = UpdateUiState.NEEDS_PERMISSION_POST
                    activity?.startActivity(permIntent)
                }
                // Otherwise the installer is now up; the dialog stays in INSTALLING
                // and the OS takes over. When the new APK installs, the app restarts.
            } catch (e: Exception) {
                manualPath = UpdateManager.downloadedApk(ctx).absolutePath
                errorMsg = e.message ?: "Installer refused to launch"
                ui = UpdateUiState.FAILED
            }
        }
    }

    Dialog(
        onDismissRequest = { if (ui == UpdateUiState.PROMPT) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = ui == UpdateUiState.PROMPT || ui == UpdateUiState.FAILED,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Color(0xFF0B111D),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(0.72f)
                    .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(20.dp)),
            ) {
                Column(Modifier.padding(28.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF3B82F6), Cyan)),
                                    RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Download, null,
                                tint = Color.White, modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Update available",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "v${UpdateManager.currentVersionName()} → v${info.versionName}",
                                color = Cyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    when (ui) {
                        UpdateUiState.PROMPT -> PromptBody(
                            info = info,
                            onUpdate = {
                                try {
                                    downloadId = UpdateManager.enqueueDownload(ctx, info.apkUrl)
                                    ui = UpdateUiState.DOWNLOADING
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Could not start download"
                                    ui = UpdateUiState.FAILED
                                }
                            },
                            onLater = onDismiss,
                            mandatory = info.mandatory,
                        )
                        UpdateUiState.NEEDS_PERMISSION -> NeedsPermissionBody(
                            introText = "Before HushTV can install updates, Android needs your permission.",
                            onOpenSettings = {
                                val intent = UpdateManager.unknownSourcesSettingsIntent(ctx)
                                activity?.startActivity(intent)
                            },
                            onCancel = onDismiss,
                        )
                        UpdateUiState.DOWNLOADING -> DownloadingBody(progressPct)
                        UpdateUiState.INSTALLING -> InstallingBody()
                        UpdateUiState.NEEDS_PERMISSION_POST -> NeedsPermissionBody(
                            introText = "Permission was revoked. Re-enable \"Install unknown apps\" for HushTV to finish the update.",
                            onOpenSettings = {
                                val intent = UpdateManager.unknownSourcesSettingsIntent(ctx)
                                activity?.startActivity(intent)
                            },
                            onCancel = onDismiss,
                        )
                        UpdateUiState.FAILED -> FailedBody(
                            msg = errorMsg ?: "Update failed",
                            manualPath = manualPath,
                            onRetry = {
                                errorMsg = null
                                progressPct = 0f
                                manualPath = null
                                ui = if (UpdateManager.canInstallPackages(ctx)) UpdateUiState.PROMPT
                                else UpdateUiState.NEEDS_PERMISSION
                            },
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

/* ─── Body variants ───────────────────────────────────────────────── */

@Composable
private fun PromptBody(
    info: VersionInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    mandatory: Boolean,
) {
    val updateFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { updateFocus.requestFocus() } }

    Text(
        "WHAT'S NEW",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
    )
    Spacer(Modifier.height(8.dp))

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val bullets = info.changelog.ifEmpty { listOf("Bug fixes and improvements") }
        bullets.take(8).forEach { line ->
            Text("•  $line", color = Color(0xFFE5E7EB), fontSize = 13.sp, lineHeight = 17.sp)
        }
    }

    Spacer(Modifier.height(18.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FlatButton(
            text = "Update Now",
            primary = true,
            modifier = Modifier.weight(1f).focusRequester(updateFocus),
            onClick = onUpdate,
        )
        if (!mandatory) {
            FlatButton(
                text = "Later",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onLater,
            )
        }
    }
}

@Composable
private fun DownloadingBody(pct: Float) {
    Text(
        "Downloading update…",
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0x20FFFFFF), RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0f, 1f))
                .height(8.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Cyan)),
                    RoundedCornerShape(4.dp),
                )
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "${(pct * 100).toInt()}% — please stay on this screen",
        color = TextSecondary,
        fontSize = 12.sp,
    )
}

@Composable
private fun InstallingBody() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = Cyan, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Opening installer…",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Press 'Install' on the system screen that just opened.",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun NeedsPermissionBody(
    introText: String,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { settingsFocus.requestFocus() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Settings, null, tint = Color(0xFFFACC15), modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Enable \"Install unknown apps\"",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(introText, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Navigate to HushTV in the settings screen, toggle it ON, then press BACK to return here.",
        color = Color(0xFF9CA3AF),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FlatButton(
            text = "Open Settings",
            primary = true,
            modifier = Modifier.weight(1f).focusRequester(settingsFocus),
            onClick = onOpenSettings,
        )
        FlatButton(
            text = "Cancel",
            primary = false,
            modifier = Modifier.weight(1f),
            onClick = onCancel,
        )
    }
}

@Composable
private fun FailedBody(
    msg: String,
    manualPath: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Error, null, tint = Red, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Update failed",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(msg, color = Color(0xFFFCA5A5), fontSize = 13.sp, lineHeight = 17.sp)
    manualPath?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            "You can still install manually from:",
            color = TextSecondary,
            fontSize = 11.sp,
        )
        Text(
            it,
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FlatButton(
            text = "Retry",
            primary = true,
            modifier = Modifier.weight(1f).focusRequester(retryFocus),
            onClick = onRetry,
        )
        FlatButton(
            text = "Dismiss",
            primary = false,
            modifier = Modifier.weight(1f),
            onClick = onDismiss,
        )
    }
}

/* ─── Button — plain Box, always visible, no Surface ────────────── */

@Composable
private fun FlatButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val base = modifier
        .height(52.dp)
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .clickable(onClick = onClick)

    val boxMod = if (primary) {
        base.background(
            brush = if (focused)
                Brush.linearGradient(listOf(Cyan, Cyan))
            else
                Brush.linearGradient(listOf(Color(0xFF3B82F6), Cyan)),
            shape = RoundedCornerShape(12.dp),
        ).border(
            width = 2.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
        )
    } else {
        base.background(
            color = if (focused) Color(0x2606B6D4) else Color(0x1AFFFFFF),
            shape = RoundedCornerShape(12.dp),
        ).border(
            width = 2.dp,
            color = if (focused) Cyan else Color(0x33FFFFFF),
            shape = RoundedCornerShape(12.dp),
        )
    }

    Box(boxMod, contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (primary) Color.White else if (focused) Cyan else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
