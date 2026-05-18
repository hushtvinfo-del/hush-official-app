package com.hushtv.tv.ui.canada

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
 * Responsive payment lock screen — scales from a 320-dp phone to a 1080-p
 * TV. Designed for non-technical users: large readable text, plain
 * language, big buttons, copy-to-clipboard helpers everywhere, manual
 * "Check now" button so impatient users don't think the app is broken
 * while the IMAP poller catches up.
 */
@Composable
fun CanadaLockScreen(
    xtreamUsername: String,
    onUnlocked: () -> Unit,
    renewMode: Boolean = false,
) {
    val ctx = LocalContext.current
    // Back from the lock screen exits the whole app — same behaviour
    // as pressing Back from the normal Home screen. Without this the
    // user is trapped on the paywall and cannot escape to launcher,
    // which (combined with system screensavers) was forcing hard
    // power-cycles. For renewMode (opened from Settings), we just
    // pop the back stack so they return to where they came from.
    androidx.activity.compose.BackHandler {
        if (renewMode) {
            onUnlocked()  // exits the renewal flow → returns to Settings
        } else {
            (ctx as? android.app.Activity)?.finish()
        }
    }
    val clip: ClipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current

    // Responsive sizing: phone <600dp, tablet 600-840dp, TV/Desktop >840dp.
    val w = config.screenWidthDp
    val sz = remember(w) { sizesFor(w) }

    var loading by remember { mutableStateOf(true) }
    var orderId by remember { mutableStateOf<String?>(null) }
    var emailTo by remember { mutableStateOf("Hushtv.info@gmail.com") }
    var amountCad by remember { mutableStateOf(40.0) }
    var error by remember { mutableStateOf<String?>(null) }
    var copiedField by remember { mutableStateOf<String?>(null) }
    var paidSuccess by remember { mutableStateOf(false) }
    val payButtonFocus = remember { FocusRequester() }

    // 1) Create or re-use a pending order.
    LaunchedEffect(xtreamUsername) {
        loading = true
        error = null
        val pending = if (renewMode) null else CanadaLicenseClient.readPendingOrder(ctx, xtreamUsername)
        if (pending != null) {
            orderId = pending.first
            loading = false
        } else {
            val result = withContext(Dispatchers.IO) {
                CanadaLicenseClient.createOrderResult(xtreamUsername, forceNew = renewMode)
            }
            when (result) {
                is CanadaLicenseClient.CreateOrderResult.Success -> {
                    val resp = result.data
                    when {
                        resp.already_licensed == true && !renewMode -> {
                            paidSuccess = true
                            delay(900); onUnlocked(); return@LaunchedEffect
                        }
                        resp.order != null -> {
                            orderId = resp.order.order_id
                            emailTo = resp.email_to ?: emailTo
                            amountCad = resp.amount_cad ?: amountCad
                            CanadaLicenseClient.savePendingOrder(
                                ctx, xtreamUsername, resp.order.order_id, resp.order.expires_at,
                            )
                        }
                        else -> {
                            error = "Server response was incomplete. Please try again."
                        }
                    }
                }
                is CanadaLicenseClient.CreateOrderResult.Failure -> {
                    error = result.message
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
                delay(1500); onUnlocked(); return@LaunchedEffect
            }
            if (resp?.order?.status == "expired") {
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

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.985f, targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(orderId, loading) {
        if (orderId != null && !loading) runCatching { payButtonFocus.requestFocus() }
    }

    fun copy(label: String, value: String) {
        clip.setText(AnnotatedString(value))
        copiedField = label
        scope.launch { delay(2000); if (copiedField == label) copiedField = null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(BgBlack, SurfaceNavy, BgBlack)),
            ),
    ) {
        if (paidSuccess) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center,
            ) {
                PaidSuccessPanel(sz, renewMode = renewMode)
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sz.pageHorizontal, vertical = sz.pageVertical),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = sz.maxContentWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            HeaderBlock(sz, amountCad, renewMode = renewMode)

            Spacer(Modifier.height(sz.gap))

            if (loading) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Getting your Order Number ready…", color = TextSecondary, fontSize = sz.body)
            } else if (orderId == null && error != null) {
                ErrorPanel(sz, error!!,
                    onRetry = {
                        scope.launch {
                            loading = true; error = null
                            val result = withContext(Dispatchers.IO) {
                                CanadaLicenseClient.createOrderResult(xtreamUsername, forceNew = renewMode)
                            }
                            when (result) {
                                is CanadaLicenseClient.CreateOrderResult.Success -> {
                                    val resp = result.data
                                    if (resp.order != null) {
                                        orderId = resp.order.order_id
                                        emailTo = resp.email_to ?: emailTo
                                        CanadaLicenseClient.savePendingOrder(
                                            ctx, xtreamUsername, resp.order.order_id, resp.order.expires_at,
                                        )
                                    }
                                }
                                is CanadaLicenseClient.CreateOrderResult.Failure -> {
                                    error = result.message
                                }
                            }
                            loading = false
                        }
                    },
                    onCheckForUpdate = {
                        scope.launch {
                            checkForAppUpdate(ctx)
                        }
                    },
                )
            } else if (orderId != null) {
                // ─── Order ID card (the hero) ─────────────────────────
                OrderIdCard(sz, orderId!!, pulse,
                    copied = copiedField == "order",
                    onCopy = { copy("order", orderId!!) })

                Spacer(Modifier.height(sz.gap))

                // ─── QR shortcut card (Phone-only) ────────────────────
                // Renders for phones / tablets ONLY. TVs (>= 900 dp) hide
                // it — you can't scan a QR off your own TV with the same
                // device, and we don't want the user wasting time on the
                // remote trying.
                if (config.screenWidthDp < 900) {
                    QrShortcutCard(sz, orderId!!, emailTo, amountCad)
                    Spacer(Modifier.height(sz.gap))
                }

                // ─── Email "Send To" card ─────────────────────────────
                EmailRecipientCard(sz, emailTo,
                    copied = copiedField == "email",
                    onCopy = { copy("email", emailTo) })

                Spacer(Modifier.height(sz.gap))

                // ─── Amount card ──────────────────────────────────────
                AmountCard(sz, amountCad)

                Spacer(Modifier.height(sz.gap))

                // ─── Plain-language instructions ──────────────────────
                InstructionsPanel(sz, orderId!!, emailTo, amountCad)

                Spacer(Modifier.height(sz.gap))

                // ─── Status indicator + manual check ──────────────────
                WaitingForPaymentRow(sz, payButtonFocus) {
                    scope.launch {
                        val resp = withContext(Dispatchers.IO) {
                            CanadaLicenseClient.pollOrder(orderId!!)
                        }
                        if (resp?.order?.status == "paid") {
                            paidSuccess = true
                            CanadaLicenseClient.clearPendingOrder(ctx, xtreamUsername)
                            delay(1500); onUnlocked()
                        }
                    }
                }

                Spacer(Modifier.height(sz.gap))

                FooterText(sz, emailTo, xtreamUsername, onExit = {
                    if (renewMode) onUnlocked()
                    else (ctx as? android.app.Activity)?.finish()
                })
            }
            }
        }
    }
}

// ─── Building blocks ──────────────────────────────────────────────────

@Composable
private fun HeaderBlock(sz: Sizes, amountCad: Double, renewMode: Boolean = false) {
    Box(
        modifier = Modifier
            .size(sz.headerIcon)
            .background(CyanFocusBg, RoundedCornerShape(50))
            .border(2.dp, Cyan, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Lock, null, tint = Cyan, modifier = Modifier.size(sz.headerIcon / 2))
    }
    Spacer(Modifier.height(16.dp))
    Text(
        if (renewMode) "Renew HushTV Canada" else "HushTV Canada",
        color = TextPrimary,
        fontSize = sz.title,
        fontWeight = FontWeight.Black,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        if (renewMode) "Annual renewal" else "One-time setup",
        color = Cyan,
        fontSize = sz.subtitle,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        if (renewMode)
            "Send another $${formatAmount(amountCad)} CAD by Interac e-Transfer to add 1 more year on top of your current expiry."
        else
            "Send $${formatAmount(amountCad)} CAD by Interac e-Transfer to start using the app.",
        color = TextPrimary,
        fontSize = sz.body,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Valid for 1 year. Works on as many devices as you want.",
        color = TextSecondary,
        fontSize = sz.bodySmall,
    )
}

@Composable
private fun OrderIdCard(sz: Sizes, orderId: String, pulse: Float, copied: Boolean, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulse)
            .background(SurfaceNavy, RoundedCornerShape(20.dp))
            .border(3.dp, Cyan, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = sz.gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepBadge(1)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your Order Number",
            color = Cyan,
            fontSize = sz.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            orderId,
            color = TextPrimary,
            fontSize = sz.orderId,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(12.dp))
        BigCopyButton(sz, "Copy Order Number", copied, onCopy)
    }
}

@Composable
private fun EmailRecipientCard(sz: Sizes, email: String, copied: Boolean, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepBadge(2)
        Spacer(Modifier.height(8.dp))
        Text(
            "Send the e-Transfer to this email",
            color = Cyan,
            fontSize = sz.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Email, null, tint = Cyan, modifier = Modifier.size(sz.body.value.dp + 4.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                email,
                color = TextPrimary,
                fontSize = sz.email,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        BigCopyButton(sz, "Copy Email", copied, onCopy)
    }
}

@Composable
private fun AmountCard(sz: Sizes, amountCad: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepBadge(3)
        Spacer(Modifier.height(8.dp))
        Text(
            "Send this amount",
            color = Cyan,
            fontSize = sz.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "$${formatAmount(amountCad)}",
            color = TextPrimary,
            fontSize = sz.orderId,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Canadian Dollars (CAD)",
            color = TextSecondary,
            fontSize = sz.bodySmall,
        )
    }
}

@Composable
private fun InstructionsPanel(sz: Sizes, orderId: String, emailTo: String, amountCad: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("How to pay — step by step", color = TextPrimary,
            fontSize = sz.h3, fontWeight = FontWeight.Bold)
        InstructionLine(sz, "1.", "Open your bank's app or website on your phone or computer.")
        InstructionLine(sz, "2.", "Find the option called \"Send Money\" or \"Interac e-Transfer\".")
        InstructionLine(sz, "3.", "Send $${formatAmount(amountCad)} CAD to:", emphasis = emailTo)
        InstructionLine(sz, "4.", "When the bank asks for a \"Message\", type this number:", emphasis = orderId)
        InstructionLine(sz, "5.", "Confirm and send. That's it — this screen will unlock by itself when the bank delivers the money (usually 1 to 3 minutes).")
        Spacer(Modifier.height(4.dp))
        WarningStripe(sz)
    }
}

@Composable
private fun InstructionLine(sz: Sizes, num: String, text: String, emphasis: String? = null) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(num, color = Cyan, fontSize = sz.body, fontWeight = FontWeight.Black,
            modifier = Modifier.widthIn(min = 24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text, color = TextPrimary, fontSize = sz.body)
            if (emphasis != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(BgBlack, RoundedCornerShape(10.dp))
                        .border(2.dp, Cyan, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        emphasis,
                        color = Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = sz.body,
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningStripe(sz: Sizes) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyanFocusBg, RoundedCornerShape(10.dp))
            .border(1.dp, Cyan, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(
            "Important: type the Order Number in the Message box EXACTLY. Do not add any other words. That's how we find your payment.",
            color = TextPrimary,
            fontSize = sz.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WaitingForPaymentRow(sz: Sizes, focus: FocusRequester, onCheckNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceNavy, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Cyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Waiting for your e-Transfer…", color = TextPrimary, fontSize = sz.body,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text("We check every 5 seconds. The app will unlock by itself.",
            color = TextSecondary, fontSize = sz.bodySmall)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onCheckNow,
            modifier = Modifier.focusRequester(focus).heightIn(min = sz.buttonHeight),
            border = BorderStroke(2.dp, Cyan),
        ) {
            Icon(Icons.Default.Refresh, null, tint = Cyan, modifier = Modifier.size(sz.body.value.dp + 4.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check now", color = Cyan, fontSize = sz.body, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FooterText(sz: Sizes, emailTo: String, username: String, onExit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Logged in as: $username", color = TextSecondary, fontSize = sz.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Need help? Contact $emailTo",
            color = TextSecondary,
            fontSize = sz.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.heightIn(min = sz.buttonHeight),
            border = BorderStroke(1.dp, BorderSlate),
        ) {
            Text("Exit App", color = TextSecondary,
                fontSize = sz.body, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BigCopyButton(sz: Sizes, label: String, copied: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = sz.buttonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (copied) Green else Cyan,
            contentColor = BgBlack,
        ),
    ) {
        Icon(
            if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
            null,
            modifier = Modifier.size(sz.body.value.dp + 4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (copied) "Copied!" else label,
            fontWeight = FontWeight.Black,
            fontSize = sz.body,
        )
    }
}

@Composable
private fun StepBadge(num: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Cyan, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$num", color = BgBlack, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun QrShortcutCard(sz: Sizes, orderId: String, emailTo: String, amountCad: Double) {
    // Payload: a mailto: link with the Order ID in the subject. Most QR
    // scanners (and Google Lens / the stock Android camera app) recognise
    // mailto: URIs and offer to open an email app — many Canadian banking
    // apps also accept a scanned email address when "Add Recipient" is
    // open. Either way the user gets the email + Order ID pre-filled.
    val payload = remember(orderId, emailTo, amountCad) {
        val subject = java.net.URLEncoder.encode(
            "HushTV CAD$${formatAmount(amountCad)} - Order $orderId",
            "UTF-8",
        ).replace("+", "%20")
        val body = java.net.URLEncoder.encode(
            "Interac e-Transfer\nSend to: $emailTo\nAmount: \$${formatAmount(amountCad)} CAD\nMessage: $orderId",
            "UTF-8",
        ).replace("+", "%20")
        "mailto:$emailTo?subject=$subject&body=$body"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Or scan with another phone",
            color = Cyan,
            fontSize = sz.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(10.dp))
        // White padded box around the QR — critical for scan reliability;
        // a dark/coloured frame next to the QR confuses cheap phone cameras.
        Box(
            modifier = Modifier
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            QrCode(content = payload, size = sz.qr)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Point another phone's camera at this code. It opens an email with the address, amount, and Order Number ready to copy.",
            color = TextPrimary,
            fontSize = sz.bodySmall,
        )
    }
}

@Composable
private fun ErrorPanel(
    sz: Sizes,
    message: String,
    onRetry: () -> Unit,
    onCheckForUpdate: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.WifiOff, null, tint = Red, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "We couldn't reach the payment server",
            color = TextPrimary,
            fontSize = sz.h3,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        // Show the real error so the user can tell us what's wrong.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElev, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Text(message, color = TextSecondary, fontSize = sz.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = sz.buttonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgBlack),
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(sz.body.value.dp + 4.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try again", fontWeight = FontWeight.Black, fontSize = sz.body)
            }
            OutlinedButton(
                onClick = onCheckForUpdate,
                modifier = Modifier.heightIn(min = sz.buttonHeight),
                border = BorderStroke(2.dp, Cyan),
            ) {
                Text("Check for app update", color = Cyan,
                    fontWeight = FontWeight.Bold, fontSize = sz.body)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "If retrying doesn't work, tap \"Check for app update\" to download the latest version.",
            color = TextSecondary, fontSize = sz.bodySmall,
        )
    }
}

private suspend fun checkForAppUpdate(ctx: android.content.Context) {
    val latest = com.hushtv.tv.update.UpdateManager.fetchLatest() ?: run {
        android.widget.Toast.makeText(
            ctx,
            "Couldn't reach update server either. Internet appears blocked.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        return
    }
    if (!com.hushtv.tv.update.UpdateManager.isUpdateAvailable(latest)) {
        android.widget.Toast.makeText(
            ctx,
            "You already have the latest version (${latest.versionName}).",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        return
    }
    // Launch the system browser to the APK URL — bypasses our in-app
    // OTA dialog which itself is rendered INSIDE the gate.
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(latest.apkUrl),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }.onFailure {
        android.widget.Toast.makeText(
            ctx,
            "Open https://hushtv.xyz/hushtv-canada.apk in a browser to update.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
}

@Composable
private fun PaidSuccessPanel(sz: Sizes, renewMode: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                if (renewMode) "License renewed!" else "Payment received!",
                color = TextPrimary,
                fontSize = sz.title,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (renewMode)
                    "Another year added to your license."
                else
                    "Welcome to HushTV Canada. Loading your library…",
                color = TextSecondary,
                fontSize = sz.body,
            )
        }
    }
}

// ─── Responsive sizing primitives ────────────────────────────────────

private data class Sizes(
    val maxContentWidth: Dp,
    val pageHorizontal: Dp,
    val pageVertical: Dp,
    val gap: Dp,
    val headerIcon: Dp,
    val title: TextUnit,
    val subtitle: TextUnit,
    val h3: TextUnit,
    val body: TextUnit,
    val bodySmall: TextUnit,
    val labelSmall: TextUnit,
    val orderId: TextUnit,
    val email: TextUnit,
    val buttonHeight: Dp,
    val qr: Dp,
)

private fun sizesFor(widthDp: Int): Sizes = when {
    widthDp < 380 -> Sizes(           // small phones
        maxContentWidth = 600.dp,
        pageHorizontal = 14.dp,
        pageVertical = 12.dp,
        gap = 10.dp,
        headerIcon = 44.dp,
        title = 20.sp,
        subtitle = 13.sp,
        h3 = 15.sp,
        body = 13.sp,
        bodySmall = 11.sp,
        labelSmall = 10.sp,
        orderId = 30.sp,
        email = 14.sp,
        buttonHeight = 48.dp,
        qr = 160.dp,
    )
    widthDp < 600 -> Sizes(           // normal phones
        maxContentWidth = 600.dp,
        pageHorizontal = 16.dp,
        pageVertical = 14.dp,
        gap = 12.dp,
        headerIcon = 48.dp,
        title = 22.sp,
        subtitle = 13.sp,
        h3 = 16.sp,
        body = 14.sp,
        bodySmall = 12.sp,
        labelSmall = 11.sp,
        orderId = 36.sp,
        email = 15.sp,
        buttonHeight = 52.dp,
        qr = 190.dp,
    )
    widthDp < 900 -> Sizes(           // tablets / foldables
        maxContentWidth = 720.dp,
        pageHorizontal = 32.dp,
        pageVertical = 32.dp,
        gap = 18.dp,
        headerIcon = 64.dp,
        title = 30.sp,
        subtitle = 15.sp,
        h3 = 19.sp,
        body = 16.sp,
        bodySmall = 13.sp,
        labelSmall = 12.sp,
        orderId = 48.sp,
        email = 19.sp,
        buttonHeight = 56.dp,
        qr = 240.dp,
    )
    else -> Sizes(                    // TV / large desktop
        maxContentWidth = 880.dp,
        pageHorizontal = 48.dp,
        pageVertical = 48.dp,
        gap = 24.dp,
        headerIcon = 80.dp,
        title = 36.sp,
        subtitle = 18.sp,
        h3 = 22.sp,
        body = 18.sp,
        bodySmall = 15.sp,
        labelSmall = 14.sp,
        orderId = 60.sp,
        email = 22.sp,
        buttonHeight = 64.dp,
        qr = 240.dp,
    )
}

private fun formatAmount(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)
