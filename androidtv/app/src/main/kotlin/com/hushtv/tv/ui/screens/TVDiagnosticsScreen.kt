package com.hushtv.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.BuildConfig
import com.hushtv.tv.data.CrashLogStore
import com.hushtv.tv.data.CrashReporter
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * TV-side crash-log viewer. Reads `filesDir/crash.log` and lets the
 * user Share or Clear it via D-pad-focusable action buttons.
 *
 * Kept deliberately simple — one scroll view + two actions — so it
 * works cleanly with TV remotes that only have D-pad + OK + BACK.
 */
@Composable
fun TVDiagnosticsScreen(nav: NavController) {
    val ctx = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val contents = remember(version) { CrashLogStore.read(ctx) }
    val hasContent = contents.isNotBlank()
    // null (idle) | "sending" | "sent" | "failed"
    var uploadState by remember { mutableStateOf<String?>(null) }

    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvCircleBtn(
                icon = Icons.Default.ArrowBack,
                focusRequester = backFocus,
                onClick = { nav.popBackStack() },
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Diagnostics",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "HushTV v${BuildConfig.VERSION_NAME} · crash log",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )
            }
            if (hasContent) {
                TvCircleBtn(Icons.Default.CloudUpload, tint = Cyan, onClick = {
                    uploadState = "sending"
                    CrashReporter.uploadNow(ctx) { result ->
                        uploadState = result
                    }
                })
                Spacer(Modifier.width(10.dp))
                TvCircleBtn(Icons.Default.Share, onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "HushTV crash log — v${BuildConfig.VERSION_NAME}",
                        )
                        putExtra(Intent.EXTRA_TEXT, contents)
                    }
                    runCatching {
                        ctx.startActivity(
                            Intent.createChooser(intent, "Share crash log").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                })
                Spacer(Modifier.width(10.dp))
                TvCircleBtn(
                    icon = Icons.Default.Delete,
                    tint = Color(0xFFEF4444),
                    onClick = {
                        CrashLogStore.clear(ctx)
                        version++
                        uploadState = null
                    },
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

        // ── Decoder status (live, updated as you play streams) ──────
        val decoderLines = remember(version) {
            com.hushtv.tv.data.PlayerBuilder.lastDecoderLines()
        }
        if (decoderLines.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1422C55E))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        "DECODER (last seen)",
                        color = Color(0xFF22C55E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        decoderLines,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // ── Upload status banner ─────────────────────────────────────
        if (uploadState != null) {
            val (bg, fg, msg) = when (uploadState) {
                "sending" -> Triple(Color(0x1406B6D4), Cyan,
                    "Uploading crash log to server…")
                "sent" -> Triple(Color(0x1422C55E), Color(0xFF22C55E),
                    "Sent to server. We'll take it from here.")
                "nothing" -> Triple(Color(0x1422C55E), Color(0xFF22C55E),
                    "Already on the server — uploaded automatically when the app started. Nothing new to send.")
                "failed" -> Triple(Color(0x14EF4444), Color(0xFFEF4444),
                    "Upload failed. Check internet and try again.")
                else -> Triple(Color.Transparent, Color.White, "")
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 48.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!hasContent) {
            Column(
                Modifier.fillMaxSize().padding(48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No crashes logged",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Crashes AND channel-freezes (player stalled for >6 s) are reported to the server automatically. Come back here to share manually if you want.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "─── recent in-app events ───",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    com.hushtv.tv.data.EventLog.snapshot()
                        .ifBlank { "(no events yet)" },
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A1220))
                    .border(1.dp, Color(0x2206B6D4), RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    contents,
                    color = Color(0xFFE5E7EB),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun TvCircleBtn(
    icon: ImageVector,
    focusRequester: FocusRequester? = null,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val base: Modifier = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier
    Box(
        base
            .size(48.dp)
            .clip(CircleShape)
            .background(if (focused) Cyan.copy(alpha = 0.25f) else Color(0x22FFFFFF))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
