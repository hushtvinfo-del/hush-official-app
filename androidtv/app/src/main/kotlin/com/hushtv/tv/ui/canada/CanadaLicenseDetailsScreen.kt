package com.hushtv.tv.ui.canada

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.CanadaLicenseClient
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RENEWAL_NUDGE_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000  // 30 days

/**
 * "My HushTV Canada License" screen — accessible from Settings.
 *
 * Shows the user's current paid-license status, paid date, expiry date,
 * days remaining, and a "Renew Now" CTA that becomes prominent when <30
 * days are left. Renewal opens the standard lock-screen payment flow,
 * which on success ADDS a year on top of the current expiry (see
 * `canada_payment_module._grant_license` on the backend).
 *
 * Works for both TV and Mobile — single composable, no platform branches.
 */
@Composable
fun CanadaLicenseDetailsScreen(
    onRenew: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<LicenseUiState>(LicenseUiState.Loading) }

    suspend fun refresh() {
        state = LicenseUiState.Loading
        val playlist = withContext(Dispatchers.IO) {
            PlaylistStore.getAll(ctx).firstOrNull()
        }
        if (playlist == null) {
            state = LicenseUiState.NoAccount
            return
        }
        val res = withContext(Dispatchers.IO) {
            CanadaLicenseClient.fetchLicense(ctx, playlist.username)
        }
        state = when (res) {
            is CanadaLicenseClient.LicenseState.Paid ->
                LicenseUiState.Paid(playlist.username, res.expiresAtMs, res.daysRemaining)
            is CanadaLicenseClient.LicenseState.Unpaid ->
                LicenseUiState.Unpaid(playlist.username)
            is CanadaLicenseClient.LicenseState.NoNetwork ->
                LicenseUiState.NoNetwork(playlist.username)
            is CanadaLicenseClient.LicenseState.Error ->
                LicenseUiState.NoNetwork(playlist.username)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgBlack, SurfaceNavy, BgBlack))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "My HushTV Canada License",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                LicenseUiState.Loading -> LoadingBlock()
                LicenseUiState.NoAccount -> InfoBlock("Sign in to an Xtream account first to view your license.")
                is LicenseUiState.NoNetwork -> NoNetworkBlock(s.username) {
                    scope.launch { refresh() }
                }
                is LicenseUiState.Paid -> PaidCard(
                    username = s.username,
                    expiresAtMs = s.expiresAtMs,
                    daysRemaining = s.daysRemaining,
                    onRefresh = { scope.launch { refresh() } },
                    onRenew = onRenew,
                )
                is LicenseUiState.Unpaid -> UnpaidCard(s.username, onPayNow = onRenew)
            }
        }
    }
}

private sealed class LicenseUiState {
    object Loading : LicenseUiState()
    object NoAccount : LicenseUiState()
    data class NoNetwork(val username: String) : LicenseUiState()
    data class Paid(val username: String, val expiresAtMs: Long, val daysRemaining: Long) : LicenseUiState()
    data class Unpaid(val username: String) : LicenseUiState()
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(color = Cyan) }
}

@Composable
private fun InfoBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) { Text(text, color = TextPrimary, fontSize = 15.sp) }
}

@Composable
private fun NoNetworkBlock(username: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = Amber, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Can't reach our server", color = TextPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "We couldn't check your license right now. Make sure your internet is on and try again.",
            color = TextSecondary, fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text("Signed in as: $username", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
    }
}

@Composable
private fun PaidCard(
    username: String,
    expiresAtMs: Long,
    daysRemaining: Long,
    onRefresh: () -> Unit,
    onRenew: () -> Unit,
) {
    val expiringSoon = daysRemaining in 0..30
    val accent = if (expiringSoon) Amber else Green
    val statusLabel = when {
        daysRemaining <= 0 -> "EXPIRED"
        expiringSoon -> "EXPIRING SOON"
        else -> "ACTIVE"
    }
    val expiryDate = remember(expiresAtMs) {
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(expiresAtMs))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(20.dp))
            .border(2.dp, accent, RoundedCornerShape(20.dp))
            .padding(24.dp),
    ) {
        // Status badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (expiringSoon) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(statusLabel, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black,
                letterSpacing = 2.sp)
        }

        Spacer(Modifier.height(16.dp))

        // Hero: "X days left"
        Text(
            if (daysRemaining > 0) "$daysRemaining days remaining" else "License expired",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (daysRemaining > 0) "Renews on $expiryDate" else "Renew to continue using HushTV Canada",
            color = TextSecondary, fontSize = 14.sp,
        )

        Spacer(Modifier.height(20.dp))

        DetailRow("Account", username)
        DetailRow("Expires", expiryDate)
        DetailRow("Plan", "$40 CAD / year — unlimited devices")

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (expiringSoon || daysRemaining <= 0) {
                Button(
                    onClick = onRenew,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent, contentColor = BgBlack,
                    ),
                ) {
                    Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (daysRemaining <= 0) "Renew Now — $40 CAD" else "Renew Now",
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // Help footer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            "How renewal works: tap Renew Now → send another $40 CAD by Interac e-Transfer using the same instructions. Your new year is added on TOP of your current expiry — you never lose remaining days.",
            color = TextSecondary, fontSize = 13.sp,
        )
    }
}

@Composable
private fun UnpaidCard(username: String, onPayNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(20.dp))
            .border(2.dp, Red, RoundedCornerShape(20.dp))
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = Red, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text("NO ACTIVE LICENSE", color = Red, fontSize = 14.sp, fontWeight = FontWeight.Black,
                letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("No payment on file for $username.", color = TextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Pay $40 CAD to use HushTV Canada for 1 year on unlimited devices.",
            color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPayNow,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgBlack),
        ) {
            Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pay Now — $40 CAD", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(
            value, color = TextPrimary, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (label == "Account") FontFamily.Monospace else FontFamily.Default,
        )
    }
}
