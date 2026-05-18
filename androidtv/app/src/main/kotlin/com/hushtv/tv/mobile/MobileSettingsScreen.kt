package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.BuildConfig
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mobile settings — simple tap-list with minimum viable controls:
 * account switcher, add-another, app version, log out (clear last
 * profile so the picker comes back on next launch). TV's richer
 * settings (parental lock, layout chooser) don't apply on mobile.
 */
@Composable
fun MobileSettingsScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember(playlistId) { PlaylistStore.find(ctx, playlistId) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
    ) {
        Text(
            "Settings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        // Account card.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A1220))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Cyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, null, tint = Cyan, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        playlist?.name ?: "No profile",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        playlist?.username ?: "—",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        SettingsItem(
            icon = Icons.Default.PersonAdd,
            title = "Switch / Add Profile",
            onClick = { nav.navigate("home") },
        )
        if (com.hushtv.tv.BuildConfig.UPDATE_CHANNEL == "canada") {
            SettingsItem(
                icon = Icons.Default.WorkspacePremium,
                title = "My HushTV Canada License",
                subtitle = "View your $40 CAD / year status and renew",
                onClick = { nav.navigate("canada/license") },
            )
        }
        SettingsItem(
            icon = Icons.Default.Update,
            title = "Check for updates",
            subtitle = "v${BuildConfig.VERSION_NAME}",
            onClick = { /* auto-check runs on launch */ },
        )
        SettingsItem(
            icon = Icons.Default.Inbox,
            title = "My content requests",
            subtitle = "Track requested movies / series",
            onClick = { nav.navigate("mrequests/$playlistId") },
        )
        SettingsItem(
            icon = Icons.Default.FiberManualRecord,
            title = "My recordings",
            subtitle = "Cloud DVR — 20 h of Live TV captures",
            onClick = { nav.navigate("mrecordings/$playlistId") },
        )
        SettingsItem(
            icon = Icons.Default.Speed,
            title = "Speed test",
            subtitle = "Check if your connection can stream smoothly",
            onClick = { nav.navigate("mspeed") },
        )
        SettingsItem(
            icon = Icons.Default.Report,
            title = "Diagnostics",
            subtitle = "Share crash reports",
            onClick = { nav.navigate("mdiag") },
        )
        SettingsItem(
            icon = Icons.Default.Logout,
            title = "Log out",
            subtitle = "Return to the profile picker",
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { LastProfileStore.clear(ctx) }
                    nav.navigate("home") {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                    }
                }
            },
        )
        SettingsItem(
            icon = Icons.Default.Info,
            title = "About HushTV",
            subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            onClick = { /* info only */ },
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1220))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
        }
    }
}
