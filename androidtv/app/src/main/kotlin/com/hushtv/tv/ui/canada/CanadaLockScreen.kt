package com.hushtv.tv.ui.canada

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.CanadaLicenseClient
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.BorderSlate
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.CyanFocusBg
import com.hushtv.tv.ui.theme.Green
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lock screen shown on the HushTV Canada flavor BEFORE the user can enter
 * the app. Displays a $40 CAD / year Interac e-Transfer payment flow and
 * polls the server every 5 s for confirmation.
 *
 * After successful payment the [onUnlocked] callback fires and the host
 * activity drops the lock and renders the rest of the app.
 */
@Composable
fun CanadaLockScreen(
    xtreamUsername: String,
    onUnlocked: () -> Unit,
) {
    val ctx = LocalContext.current
    val clip: ClipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var orderId by remember { mutableStateOf<String?>(null) }
    var emailTo by remember { mutableStateOf("Hushtv.info@gmail.com") }
    var amountCad by remember { mutableStateOf(40.0) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var paidSuccess by remember { mutableStateOf(false) }
    val payButtonFocus = remember { FocusRequester() }

    // 1) Create or reuse an order on first composition.
    LaunchedEffect(xtreamUsername) {
        loading = true
        error = null
        val pending = CanadaLicenseClient.readPendingOrder(ctx, xtreamUsername)
        if (pending != null) {
            orderId = pending.first
            loading = false
        } else {
            val resp = withContext(Dispatchers.IO) {
                CanadaLicenseClient.createOrder(xtreamUsername)
            }
            when {
                resp?.already_licensed == true -> {
                    paidSuccess = true
                    delay(900)
                    onUnlocked()
                    return@LaunchedEffect
                }
                resp?.order != null -> {
                    orderId = resp.order.order_id
                    emailTo = resp.email_to ?: emailTo
                    amountCad = resp.amount_cad ?: amountCad
                    CanadaLicenseClient.savePendingOrder(
                        ctx, xtreamUsername, resp.order.order_id, resp.order.expires_at,
                    )
                }
                else -> {
                    error = "Could not reach payment server. Check your internet and try again."
                }
            }
            loading = false
        }
    }

    // 2) Poll for payment every 5 s.
    LaunchedEffect(orderId) {
        val oid = orderId ?: return@LaunchedEffect
        while (!paidSuccess) {
            delay(5000)
            val resp = withContext(Dispatchers.IO) { CanadaLicenseClient.pollOrder(oid) }
            if (resp?.order?.status == "paid" && resp.license?.paid == true) {
                paidSuccess = true
                CanadaLicenseClient.clearPendingOrder(ctx, xtreamUsername)
                delay(1500)
                onUnlocked()
                return@LaunchedEffect
            }
            if (resp?.order?.status == "expired") {
                // Re-create a fresh order.
                CanadaLicenseClient.clearPendingOrder(ctx, xtreamUsername)
                val fresh = withContext(Dispatchers.IO) {
                    CanadaLicenseClient.createOrder(xtreamUsername)
                }
                if (fresh?.order != null) {
                    orderId = fresh.order.order_id
                    CanadaLicenseClient.savePendingOrder(
                        ctx, xtreamUsername, fresh.order.order_id, fresh.order.expires_at,
                    )
                }
            }
        }
    }

    // Animated pulse on the order id card so users notice it even on a TV across the room.
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(orderId, loading) {
        if (orderId != null && !loading) {
            runCatching { payButtonFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgBlack, SurfaceNavy, BgBlack),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (paidSuccess) {
            PaidSuccessPanel()
            return@Box
        }

        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .padding(horizontal = 48.dp, vertical = 48.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    "HushTV Canada — Annual CDN Proxy Fee",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "One-time $${String.format("%.0f", amountCad)} CAD / year via Interac e-Transfer",
                color = TextSecondary,
                fontSize = 16.sp,
            )

            Spacer(Modifier.height(28.dp))

            if (loading) {
                CircularProgressIndicator(color = Cyan)
                Spacer(Modifier.height(12.dp))
                Text("Preparing your order…", color = TextSecondary)
            } else if (error != null && orderId == null) {
                ErrorPanel(error!!) {
                    scope.launch {
                        loading = true
                        error = null
                        val resp = withContext(Dispatchers.IO) {
                            CanadaLicenseClient.createOrder(xtreamUsername)
                        }
                        if (resp?.order != null) {
                            orderId = resp.order.order_id
                            CanadaLicenseClient.savePendingOrder(
                                ctx, xtreamUsername, resp.order.order_id, resp.order.expires_at,
                            )
                        } else {
                            error = "Still can't reach the payment server. Check your internet."
                        }
                        loading = false
                    }
                }
            } else if (orderId != null) {
                OrderCard(orderId!!, pulse) {
                    clip.setText(AnnotatedString(orderId!!))
                    copied = true
                    scope.launch { delay(1800); copied = false }
                }
                Spacer(Modifier.height(20.dp))

                InstructionsPanel(
                    orderId = orderId!!,
                    emailTo = emailTo,
                    amountCad = amountCad,
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = Cyan,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "Waiting for your Interac e-Transfer… checking every 5 seconds.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            clip.setText(AnnotatedString(orderId!!))
                            copied = true
                            scope.launch { delay(1800); copied = false }
                        },
                        modifier = Modifier.focusRequester(payButtonFocus),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgBlack),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (copied) "Copied!" else "Copy Order ID", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val resp = withContext(Dispatchers.IO) {
                                    CanadaLicenseClient.pollOrder(orderId!!)
                                }
                                if (resp?.order?.status == "paid") {
                                    paidSuccess = true
                                    CanadaLicenseClient.clearPendingOrder(ctx, xtreamUsername)
                                    delay(1500)
                                    onUnlocked()
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check now")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "Need help? Contact ${emailTo}",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun OrderCard(orderId: String, pulse: Float, onCopy: () -> Unit) {
    Box(
        modifier = Modifier
            .scale(pulse)
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(20.dp))
            .border(2.dp, Cyan, RoundedCornerShape(20.dp))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "YOUR ORDER ID",
                color = Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                orderId,
                color = TextPrimary,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Include this in the Interac \"Message\" field",
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun InstructionsPanel(orderId: String, emailTo: String, amountCad: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "How to pay",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Step(1, "Open your bank app and select Interac e-Transfer (also called \"Send Money\").")
        Step(2, "Send exactly $${String.format("%.2f", amountCad)} CAD to: ", highlight = emailTo)
        Step(3, "In the e-Transfer Message field, type: ", highlight = orderId)
        Step(4, "Send. The app will unlock automatically within 1–2 minutes of the bank confirming the deposit.")
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyanFocusBg, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Text(
                "Make sure the Message field contains ONLY the 8-digit Order ID. Do not add extra text.",
                color = TextPrimary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Step(num: Int, text: String, highlight: String? = null) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Cyan, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$num", color = BgBlack, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text, color = TextPrimary, fontSize = 15.sp)
            if (highlight != null) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(BgBlack, RoundedCornerShape(8.dp))
                        .border(1.dp, Cyan, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        highlight,
                        color = Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Red, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = TextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgBlack),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PaidSuccessPanel() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "Payment received — welcome to HushTV Canada!",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your license is active for 1 year. Loading your library…",
                color = TextSecondary,
                fontSize = 16.sp,
            )
        }
    }
}
