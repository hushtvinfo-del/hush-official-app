package com.hushtv.tv.mobile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.BuildConfig
import com.hushtv.tv.data.CrashLogStore
import com.hushtv.tv.data.CrashReporter
import com.hushtv.tv.ui.theme.Cyan

/**
 * Reads the app's private `crash.log` (written by the global
 * UncaughtExceptionHandler in HushTVApp) and shows it in a scrollable
 * read-only view. Lets the user Share the log via any intent handler
 * (email, messaging, etc.) or clear it after reading.
 */
@Composable
fun MobileDiagnosticsScreen(nav: NavController) {
    val ctx = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val contents = remember(version) { CrashLogStore.read(ctx) }
    val hasContent = contents.isNotBlank()
    // One of: null (idle) | "sending" | "sent" | "failed"
    var uploadState by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
    ) {
        // ── Top bar ──────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { nav.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ArrowBack, null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Diagnostics",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Crash log · v${BuildConfig.VERSION_NAME}",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (hasContent) {
                ActionIcon(icon = Icons.Default.CloudUpload, tint = Cyan) {
                    uploadState = "sending"
                    CrashReporter.uploadNow(ctx) { result ->
                        uploadState = result
                    }
                }
                Spacer(Modifier.width(8.dp))
                ActionIcon(icon = Icons.Default.Share) {
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
                }
                Spacer(Modifier.width(8.dp))
                ActionIcon(icon = Icons.Default.Delete, tint = Color(0xFFEF4444)) {
                    CrashLogStore.clear(ctx)
                    version++
                    uploadState = null
                }
            }
        }

        // ── Upload status banner ─────────────────────────────────────
        if (uploadState != null) {
            val (bg, fg, msg) = when (uploadState) {
                "sending" -> Triple(Color(0x1406B6D4), Cyan, "Uploading to server…")
                "sent" -> Triple(Color(0x1422C55E), Color(0xFF22C55E),
                    "Sent to server. We'll take it from here.")
                "nothing" -> Triple(Color(0x14FACC15), Color(0xFFFACC15),
                    "Already sent — nothing new to upload.")
                "failed" -> Triple(Color(0x14EF4444), Color(0xFFEF4444),
                    "Upload failed. Check internet and try again.")
                else -> Triple(Color.Transparent, Color.White, "")
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

        // ── Content ──────────────────────────────────────────────────
        if (!hasContent) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No crashes logged",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Keep using the app. If it ever force-closes,\ncome back here to share the report.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A1220))
                    .border(1.dp, Color(0x2206B6D4), RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    contents,
                    color = Color(0xFFE5E7EB),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
