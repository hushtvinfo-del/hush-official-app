package com.hushtv.tv.ui.canada

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
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wraps the rest of the app for the `canada` flavor only. On every launch:
 *   1. If no playlist is configured yet → render [content] (the normal
 *      add-account / picker flow). The lock kicks in AFTER the user has
 *      logged into their playlist.
 *   2. Otherwise → fetch /api/canada/license/{xtream_username}. If paid,
 *      render [content]. If unpaid, render [CanadaLockScreen].
 *   3. Re-checks every 30 minutes while in foreground, so a license that
 *      expires mid-session locks the app on the next check.
 *
 * For Dev / Official flavors this composable is a no-op pass-through, so
 * `MainActivity` can wrap unconditionally without compile-time forking.
 */
@Composable
fun CanadaLicenseGate(content: @Composable () -> Unit) {
    if (BuildConfig.UPDATE_CHANNEL != "canada") {
        content()
        return
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<GateState>(GateState.Checking) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        val playlist = withContext(Dispatchers.IO) {
            // Use the most recently used playlist (or first available) to derive
            // the xtream_username. Lock is per-playlist-username so a single $40
            // covers any number of devices logged into that same Xtream account.
            val lastId = LastProfileStoreSafe(ctx)
            val all = PlaylistStore.getAll(ctx)
            val active = all.firstOrNull { it.id == lastId } ?: all.firstOrNull()
            active
        }
        if (playlist == null) {
            state = GateState.NoAccount
            return@LaunchedEffect
        }
        val res = withContext(Dispatchers.IO) {
            CanadaLicenseClient.fetchLicense(ctx, playlist.username)
        }
        state = when (res) {
            is CanadaLicenseClient.LicenseState.Paid -> GateState.Unlocked
            is CanadaLicenseClient.LicenseState.Unpaid -> GateState.Locked(playlist.username)
            is CanadaLicenseClient.LicenseState.NoNetwork -> {
                // Genuine offline + no cached "paid": still show lock screen
                // (which itself handles offline by displaying the saved order
                // if any). User can re-try once network returns.
                GateState.Locked(playlist.username)
            }
            is CanadaLicenseClient.LicenseState.Error -> GateState.Locked(playlist.username)
        }
    }

    // Periodic re-check while in foreground.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30L * 60 * 1000)
            refreshTick += 1
        }
    }

    when (val s = state) {
        GateState.Checking -> {
            Box(
                modifier = Modifier.fillMaxSize().background(BgBlack),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Cyan) }
        }
        GateState.NoAccount -> content()
        GateState.Unlocked -> content()
        is GateState.Locked -> CanadaLockScreen(
            xtreamUsername = s.username,
            onUnlocked = {
                scope.launch {
                    state = GateState.Unlocked
                    // re-prime cache on success
                    refreshTick += 1
                }
            },
        )
    }
}

private sealed class GateState {
    object Checking : GateState()
    object NoAccount : GateState()
    object Unlocked : GateState()
    data class Locked(val username: String) : GateState()
}

private fun LastProfileStoreSafe(ctx: android.content.Context): String? =
    runCatching { com.hushtv.tv.data.LastProfileStore.load(ctx) }.getOrNull()
