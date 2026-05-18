package com.hushtv.tv.ui.canada

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hushtv.tv.BuildConfig
import com.hushtv.tv.data.CanadaLicenseClient
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** v1.44.82 — Heartbeat helpers. The admin "Last active" + "Devices"
 * columns are powered by these. ANDROID_ID is a stable per-device,
 * per-app installation identifier and is the right primitive here. */
private const val HEARTBEAT_INTERVAL_MS = 5L * 60_000

@Suppress("HardwareIds")
private fun stableDeviceId(ctx: Context): String =
    Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

private fun platformLabel(ctx: Context): String {
    val isTv = ctx.packageManager.hasSystemFeature("android.software.leanback")
    return if (isTv) "android-tv" else "android-mobile"
}

private fun deviceModel(): String =
    "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: ""}".trim().take(120)

/**
 * Wraps the rest of the app for the `canada` flavor only.
 *
 * Two distinct gating modes operate in parallel:
 *
 *   PRE-LOGIN: No playlist configured yet → just renders `content` so the
 *   user can add their Xtream account. The lock screen has no username
 *   to anchor against at this point.
 *
 *   POST-LOGIN: As soon as ANY playlist exists in `PlaylistStore`, we
 *   resolve its xtream_username and call /api/canada/license/{user}.
 *   If unlicensed → render `CanadaLockScreen` instead of `content`, and
 *   keep polling the server every 5 seconds for payment confirmation.
 *
 * Implementation: a single forever-coroutine polls every 2 s. It cheaply
 * compares the current "active username" against the previous one and
 * re-checks the license whenever the username changes (i.e. the user
 * just added or switched a playlist). It also re-checks every 30 minutes
 * to catch licenses that EXPIRE mid-session.
 *
 * For Dev / Official this composable is a no-op pass-through, so
 * MainActivity can wrap unconditionally without compile-time forking.
 */
@Composable
fun CanadaLicenseGate(content: @Composable () -> Unit) {
    if (BuildConfig.UPDATE_CHANNEL != "canada") {
        content()
        return
    }

    val ctx = LocalContext.current
    // null = checking / pre-login.  empty string = no playlist yet.
    var currentUser by remember { mutableStateOf<String?>(null) }
    // null = unknown (checking).  true = paid.  false = unpaid.
    var licensed by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        var lastUser = ""
        var lastFullCheckMs = 0L
        var lastHeartbeatMs = 0L
        while (true) {
            val playlists = withContext(Dispatchers.IO) { PlaylistStore.getAll(ctx) }
            val lastId = withContext(Dispatchers.IO) {
                runCatching { LastProfileStore.load(ctx) }.getOrNull()
            }
            val active = playlists.firstOrNull { it.id == lastId } ?: playlists.firstOrNull()
            val u = active?.username?.trim()?.lowercase().orEmpty()

            val usernameChanged = u != lastUser
            val periodicRefresh =
                u.isNotEmpty() && System.currentTimeMillis() - lastFullCheckMs > 30L * 60_000

            if (usernameChanged) {
                lastUser = u
                currentUser = if (u.isEmpty()) "" else u
                if (u.isEmpty()) {
                    licensed = null  // pre-login pass-through
                } else {
                    licensed = null  // force re-check
                }
            }

            if (u.isNotEmpty() && (usernameChanged || licensed == null || periodicRefresh)) {
                val res = withContext(Dispatchers.IO) {
                    CanadaLicenseClient.fetchLicense(ctx, u)
                }
                licensed = when (res) {
                    is CanadaLicenseClient.LicenseState.Paid -> true
                    is CanadaLicenseClient.LicenseState.Unpaid -> false
                    is CanadaLicenseClient.LicenseState.NoNetwork -> false
                    is CanadaLicenseClient.LicenseState.Error -> false
                }
                lastFullCheckMs = System.currentTimeMillis()
            }

            // Heartbeat — fire once we're sure the user is licensed and
            // then every HEARTBEAT_INTERVAL_MS while the app stays open.
            // Best-effort: failures are swallowed in the client.
            val now = System.currentTimeMillis()
            if (u.isNotEmpty() && licensed == true &&
                now - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS
            ) {
                lastHeartbeatMs = now
                val deviceId = stableDeviceId(ctx)
                if (deviceId.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        CanadaLicenseClient.sendHeartbeat(
                            username = u,
                            deviceId = deviceId,
                            appVersion = BuildConfig.VERSION_NAME,
                            platform = platformLabel(ctx),
                            model = deviceModel(),
                        )
                    }
                }
            }

            delay(2000)
        }
    }

    val u = currentUser
    when {
        // Pre-login: no playlist yet — let the user reach the add-account flow.
        u == null || u.isEmpty() -> content()
        // Playlist exists, license unknown — show a lightweight loader.
        licensed == null -> Box(
            modifier = Modifier.fillMaxSize().background(BgBlack),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = Cyan) }
        // Licensed → app proceeds normally.
        licensed == true -> content()
        // Unpaid OR offline → hard-lock with the payment screen.
        else -> CanadaLockScreen(
            xtreamUsername = u,
            onUnlocked = { licensed = true },
        )
    }
}
