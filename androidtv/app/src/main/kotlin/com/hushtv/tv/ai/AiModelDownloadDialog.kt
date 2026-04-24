package com.hushtv.tv.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.launch

/**
 * Shown the first time a user tries to enable AI captions, when the
 * ~142 MB Whisper Base model isn't present on disk yet. Buttons:
 *
 *   • Download (fires [WhisperModelManager.download])
 *   • Cancel  (dismisses the dialog)
 *
 * While downloading we show a progress bar inline and the Download
 * button flips to a disabled-looking "Downloading…" state. On success
 * the dialog auto-dismisses and the caller is notified via
 * [onReady] so it can flip the AI toggle on.
 *
 * Works on both TV (D-pad focusable rows) and mobile (touch-clickable)
 * — shared composable to keep behaviour identical across form factors.
 */
@Composable
fun AiModelDownloadDialog(
    onReady: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadState by WhisperModelManager.downloadState.collectAsState()

    val downloadFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { downloadFocus.requestFocus() } }

    // When download completes, notify + dismiss.
    LaunchedEffect(downloadState) {
        if (downloadState is WhisperModelManager.DownloadState.Done) {
            onReady()
            onDismiss()
        }
    }

    Dialog(onDismissRequest = {
        if (downloadState !is WhisperModelManager.DownloadState.Running) onDismiss()
    }) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1220))
                .border(1.5.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Cyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Language, null,
                        tint = Cyan,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "AI CAPTIONS DOWNLOAD",
                        color = Cyan,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "English subtitles over any language",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "The AI captions engine works fully offline — once downloaded it stays on your device. It listens to the audio locally, detects the language (Dutch, Spanish, French and 95+ others), and writes the English translation at the bottom of the screen.",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "One-time download · about 142 MB · takes ~20 s on a good connection.",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )

            // ── Progress / status ──
            val state = downloadState
            if (state is WhisperModelManager.DownloadState.Running) {
                Spacer(Modifier.height(14.dp))
                val pct = (state.progress * 100).toInt().coerceIn(0, 100)
                val mb = state.bytesDone / (1024 * 1024)
                val totalMb = state.bytesTotal / (1024 * 1024)
                Text(
                    "Downloading · $pct %  ($mb / ${totalMb.coerceAtLeast(1L)} MB)",
                    color = Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Cyan,
                    trackColor = Color(0x22FFFFFF),
                )
            } else if (state is WhisperModelManager.DownloadState.Failed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Download failed · ${state.message}",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Buttons (stacked on portrait, side-by-side on wide) ──
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillBtn(
                    label = "Cancel",
                    primary = false,
                    onClick = {
                        if (downloadState !is WhisperModelManager.DownloadState.Running) onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                PillBtn(
                    label = when (downloadState) {
                        is WhisperModelManager.DownloadState.Running -> "Downloading…"
                        is WhisperModelManager.DownloadState.Failed -> "Retry"
                        else -> "Download"
                    },
                    primary = true,
                    focusRequester = downloadFocus,
                    enabled = downloadState !is WhisperModelManager.DownloadState.Running,
                    onClick = {
                        scope.launch { WhisperModelManager.download(ctx) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PillBtn(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val base: Modifier = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier
    val bg = when {
        !enabled -> Color(0x15FFFFFF)
        primary && focused -> Cyan
        primary -> Cyan.copy(alpha = 0.85f)
        focused -> Color(0x3306B6D4)
        else -> Color(0x14FFFFFF)
    }
    val fg = when {
        !enabled -> Color(0xFF94A3B8)
        primary -> Color(0xFF05080F)
        else -> Color.White
    }
    Box(
        base
            .then(modifier)
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
