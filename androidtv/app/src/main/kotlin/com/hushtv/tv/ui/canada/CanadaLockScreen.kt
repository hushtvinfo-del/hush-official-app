package com.hushtv.tv.ui.canada

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.CanadaLicenseClient
import com.hushtv.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HushTV Canada payment lock screen — read-only design.
 *
 * Two-line philosophy: (1) the user is sitting in front of their TV with
 * the remote, NOT a keyboard; (2) they will perform the actual Interac
 * e-Transfer from their PHONE's banking app while reading the values
 * from the TV. So there are no copy buttons, no QR codes, no scrolling
 * — just three large step cards laid out side-by-side on landscape
 * (TV / tablet) or stacked on portrait (phone), all visible at once,
 * with the values printed big enough for an elderly user to read from
 * across the room.
 */
@Composable
fun CanadaLockScreen(
    xtreamUsername: String,
    onUnlocked: () -> Unit,
    renewMode: Boolean = false,
) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val checkNowFocus = remember { FocusRequester() }

    // Back / Exit
    BackHandler {
        if (renewMode) onUnlocked()
        else (ctx as? android.app.Activity)?.finish()
    }

    // State
    var loading by remember { mutableStateOf(true) }
    var orderId by remember { mutableStateOf<String?>(null) }
    var emailTo by remember { mutableStateOf("Hushtv.info@gmail.com") }
    var amountCad by remember { mutableStateOf(40.0) }
    var error by remember { mutableStateOf<String?>(null) }
    var paidSuccess by remember { mutableStateOf(false) }

    // Create or re-use order. We ALWAYS hit the server even when a
    // pending order is cached locally, otherwise stale amount/email
    // values stay on screen forever (e.g. when the server-side price
    // changes for a testing window). The server is idempotent — it
    // returns the same pending order with reused=true when one exists.
    LaunchedEffect(xtreamUsername) {
        loading = true; error = null
        val result = withContext(Dispatchers.IO) {
            CanadaLicenseClient.createOrderResult(xtreamUsername, forceNew = renewMode)
        }
        when (result) {
            is CanadaLicenseClient.CreateOrderResult.Success -> {
                val resp = result.data
                when {
                    resp.already_licensed == true && !renewMode -> {
                        paidSuccess = true
                        delay(700); onUnlocked(); return@LaunchedEffect
                    }
                    resp.order != null -> {
                        orderId = resp.order.order_id
                        emailTo = resp.email_to ?: emailTo
                        amountCad = resp.amount_cad ?: amountCad
                        CanadaLicenseClient.savePendingOrder(
                            ctx, xtreamUsername, resp.order.order_id, resp.order.expires_at,
                        )
                    }
                    else -> error = "Server response was incomplete. Please try again."
                }
            }
            is CanadaLicenseClient.CreateOrderResult.Failure -> {
                // If the server is unreachable, fall back to the locally cached
                // order id so the user can still see something to pay against.
                val pending = if (renewMode) null
                              else CanadaLicenseClient.readPendingOrder(ctx, xtreamUsername)
                if (pending != null) {
                    orderId = pending.first
                } else {
                    error = result.message
                }
            }
        }
        loading = false
    }

    // Poll
    LaunchedEffect(orderId) {
        val oid = orderId ?: return@LaunchedEffect
        while (!paidSuccess) {
            delay(5000)
            val resp = withContext(Dispatchers.IO) { CanadaLicenseClient.pollOrder(oid) }
            if (resp?.order?.status == "paid" && resp.license?.paid == true) {
                paidSuccess = true
                CanadaLicenseClient.clearPendingOrder(ctx, xtreamUsername)
                delay(1200); onUnlocked(); return@LaunchedEffect
            }
            if (resp?.order?.status == "expired") {
                CanadaLicenseClient.clearPendingOrder(ctx, xtreamUsername)
                val fresh = withContext(Dispatchers.IO) {
                    CanadaLicenseClient.createOrderResult(xtreamUsername, forceNew = renewMode)
                }
                if (fresh is CanadaLicenseClient.CreateOrderResult.Success && fresh.data.order != null) {
                    orderId = fresh.data.order.order_id
                    CanadaLicenseClient.savePendingOrder(
                        ctx, xtreamUsername, fresh.data.order.order_id, fresh.data.order.expires_at,
                    )
                }
            }
        }
    }

    LaunchedEffect(orderId) {
        if (orderId != null) runCatching { checkNowFocus.requestFocus() }
    }

    val isLandscape = config.screenWidthDp >= config.screenHeightDp
    val sz = remember(config.screenWidthDp, config.screenHeightDp, isLandscape) {
        sizesFor(config.screenWidthDp, config.screenHeightDp, isLandscape)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgBlack, SurfaceNavy, BgBlack))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = sz.pagePad, vertical = sz.pagePadV),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ──────────────── Header (top, compact) ────────────────
            Header(sz, amountCad, renewMode)

            Spacer(Modifier.height(sz.headerToCards))

            // ──────────────── Body: 3 step cards ───────────────────
            when {
                paidSuccess -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { PaidPanel(sz, renewMode) }

                loading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Cyan, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Getting your Order Number…", color = TextSecondary, fontSize = sz.body)
                    }
                }

                error != null && orderId == null -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorPanel(sz, error!!) {
                        scope.launch {
                            loading = true; error = null
                            val r = withContext(Dispatchers.IO) {
                                CanadaLicenseClient.createOrderResult(xtreamUsername, forceNew = renewMode)
                            }
                            if (r is CanadaLicenseClient.CreateOrderResult.Success && r.data.order != null) {
                                orderId = r.data.order.order_id
                                emailTo = r.data.email_to ?: emailTo
                                CanadaLicenseClient.savePendingOrder(
                                    ctx, xtreamUsername, r.data.order.order_id, r.data.order.expires_at,
                                )
                            } else if (r is CanadaLicenseClient.CreateOrderResult.Failure) {
                                error = r.message
                            }
                            loading = false
                        }
                    }
                }

                orderId != null -> Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (isLandscape) {
                        StepsRow(sz, amountCad, emailTo, orderId!!)
                    } else {
                        StepsColumn(sz, amountCad, emailTo, orderId!!)
                    }
                }
            }

            Spacer(Modifier.height(sz.cardsToFooter))

            // ──────────────── Footer (status + actions) ────────────
            if (orderId != null && !paidSuccess) {
                Footer(
                    sz = sz,
                    xtreamUsername = xtreamUsername,
                    checkNowFocus = checkNowFocus,
                    onCheckNow = {
                        scope.launch {
                            val resp = withContext(Dispatchers.IO) {
                                CanadaLicenseClient.pollOrder(orderId!!)
                            }
                            if (resp?.order?.status == "paid") {
                                paidSuccess = true
                                CanadaLicenseClient.clearPendingOrder(ctx, xtreamUsername)
                                delay(1200); onUnlocked()
                            }
                        }
                    },
                    onExit = {
                        if (renewMode) onUnlocked()
                        else (ctx as? android.app.Activity)?.finish()
                    },
                )
            }
        }
    }
}

// ──────── Header ────────
@Composable
private fun Header(sz: Sizes, amountCad: Double, renewMode: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Lock, null, tint = Cyan, modifier = Modifier.size(sz.headerIcon))
            Text(
                if (renewMode) "Renew HushTV Canada" else "HushTV Canada",
                color = TextPrimary,
                fontSize = sz.title,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${if (renewMode) "Renewal" else "One-time setup"} • $${formatAmount(amountCad)} CAD / year • Unlimited devices",
            color = Cyan,
            fontSize = sz.subtitle,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ──────── Steps layouts ────────
@Composable
private fun StepsRow(sz: Sizes, amountCad: Double, emailTo: String, orderId: String) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(sz.cardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepCard(
            sz = sz,
            number = 1,
            instruction = "Open your bank app and start an Interac e-Transfer",
            valueLabel = "Send this amount",
            value = "$${formatAmount(amountCad)} CAD",
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StepCard(
            sz = sz,
            number = 2,
            instruction = "Send the e-Transfer to this email address",
            valueLabel = "Recipient email",
            value = emailTo,
            valueIsEmail = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StepCard(
            sz = sz,
            number = 3,
            instruction = "Type this number in the Interac \"Message\" field",
            valueLabel = "Your Order Number",
            value = orderId,
            valueIsMonospace = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun StepsColumn(sz: Sizes, amountCad: Double, emailTo: String, orderId: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(sz.cardGap),
    ) {
        StepCard(
            sz = sz,
            number = 1,
            instruction = "Open your bank app and start an Interac e-Transfer",
            valueLabel = "Send this amount",
            value = "$${formatAmount(amountCad)} CAD",
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        StepCard(
            sz = sz,
            number = 2,
            instruction = "Send the e-Transfer to this email address",
            valueLabel = "Recipient email",
            value = emailTo,
            valueIsEmail = true,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        StepCard(
            sz = sz,
            number = 3,
            instruction = "Type this number in the Interac \"Message\" field",
            valueLabel = "Your Order Number",
            value = orderId,
            valueIsMonospace = true,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun StepCard(
    sz: Sizes,
    number: Int,
    instruction: String,
    valueLabel: String,
    value: String,
    valueIsMonospace: Boolean = false,
    valueIsEmail: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceNavy, RoundedCornerShape(20.dp))
            .border(2.dp, Cyan, RoundedCornerShape(20.dp))
            .padding(sz.cardPad),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Step number badge
        Box(
            modifier = Modifier
                .size(sz.badge)
                .background(Cyan, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = BgBlack,
                fontWeight = FontWeight.Black, fontSize = sz.badgeText)
        }

        // Instruction line
        Text(
            instruction,
            color = TextPrimary,
            fontSize = sz.instruction,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Value block (label + big value)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                valueLabel,
                color = Cyan,
                fontSize = sz.label,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            if (valueIsEmail) {
                // The recipient email card uses an auto-shrinking text so a
                // long address like Hushtv.info@gmail.com never wraps onto
                // two lines on narrower TVs / tablets. Compose has no
                // built-in autoSize yet, so we measure-and-step-down the
                // font size until the string fits the card width.
                AutoFitSingleLineText(
                    text = value,
                    maxFontSize = sz.value,
                    minFontSize = sz.tiny,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    value,
                    color = TextPrimary,
                    fontSize = sz.value,
                    fontWeight = FontWeight.Black,
                    fontFamily = if (valueIsMonospace) FontFamily.Monospace else FontFamily.Default,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Single-line text that shrinks its font size until the value fits on one
 * line. Used for the recipient-email step card so a long gmail address
 * never wraps onto two lines and breaks the layout. Compose 1.8 ships
 * with `autoSize` but our BOM is pinned earlier; this hand-rolled version
 * does the same job in ~20 lines with no new dependencies.
 */
@Composable
private fun AutoFitSingleLineText(
    text: String,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    color: androidx.compose.ui.graphics.Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier,
        onTextLayout = { layout ->
            if (!ready && layout.didOverflowWidth && fontSize > minFontSize) {
                // Shrink in 2-sp steps until it fits or we hit the floor.
                val next = (fontSize.value - 2f).coerceAtLeast(minFontSize.value)
                fontSize = next.sp
            } else {
                ready = true
            }
        },
    )
}

// ──────── Footer ────────
@Composable
private fun Footer(
    sz: Sizes,
    xtreamUsername: String,
    checkNowFocus: FocusRequester,
    onCheckNow: () -> Unit,
    onExit: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = Cyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                "Waiting for your payment — this screen will unlock automatically in 1–2 minutes",
                color = TextPrimary,
                fontSize = sz.body,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(sz.footerInner))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCheckNow,
                modifier = Modifier
                    .focusRequester(checkNowFocus)
                    .heightIn(min = sz.buttonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgBlack),
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(sz.body.value.dp + 4.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check Now", fontWeight = FontWeight.Black, fontSize = sz.body)
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.heightIn(min = sz.buttonHeight),
                border = BorderStroke(2.dp, BorderSlate),
            ) {
                Text("Exit App", color = TextSecondary,
                    fontWeight = FontWeight.SemiBold, fontSize = sz.body)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Account: $xtreamUsername", color = TextSecondary, fontSize = sz.tiny)
    }
}

// ──────── Other panels ────────
@Composable
private fun ErrorPanel(sz: Sizes, message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)) {
        Icon(Icons.Default.WifiOff, null, tint = Red, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("We couldn't reach the payment server",
            color = TextPrimary, fontSize = sz.title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = TextSecondary, fontSize = sz.body,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry,
            modifier = Modifier.heightIn(min = sz.buttonHeight),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgBlack)) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(sz.body.value.dp + 4.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try Again", fontWeight = FontWeight.Black, fontSize = sz.body)
        }
    }
}

@Composable
private fun PaidPanel(sz: Sizes, renewMode: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                if (renewMode) "License renewed!" else "Payment received!",
                color = TextPrimary, fontSize = sz.title, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (renewMode) "Another year added to your license."
                else "Welcome to HushTV Canada. Loading your library…",
                color = TextSecondary, fontSize = sz.body,
            )
        }
    }
}

// ──────── Sizes ────────
private data class Sizes(
    val pagePad: Dp,
    val pagePadV: Dp,
    val headerToCards: Dp,
    val cardsToFooter: Dp,
    val cardGap: Dp,
    val cardPad: Dp,
    val footerInner: Dp,
    val headerIcon: Dp,
    val title: TextUnit,
    val subtitle: TextUnit,
    val instruction: TextUnit,
    val label: TextUnit,
    val value: TextUnit,
    val valueWrap: TextUnit,  // smaller, used for the email card so long emails fit
    val body: TextUnit,
    val tiny: TextUnit,
    val badge: Dp,
    val badgeText: TextUnit,
    val buttonHeight: Dp,
)

private fun sizesFor(widthDp: Int, heightDp: Int, isLandscape: Boolean): Sizes {
    // TV / wide tablet landscape — most common for this app
    if (isLandscape && widthDp >= 960) {
        // Very wide TV (1920dp etc): generous sizing
        val xl = widthDp >= 1500
        return Sizes(
            pagePad = if (xl) 56.dp else 36.dp,
            pagePadV = if (xl) 36.dp else 24.dp,
            headerToCards = 20.dp,
            cardsToFooter = 20.dp,
            cardGap = if (xl) 24.dp else 18.dp,
            cardPad = if (xl) 28.dp else 22.dp,
            footerInner = 12.dp,
            headerIcon = if (xl) 40.dp else 32.dp,
            title = if (xl) 38.sp else 30.sp,
            subtitle = if (xl) 18.sp else 16.sp,
            instruction = if (xl) 22.sp else 18.sp,
            label = if (xl) 14.sp else 13.sp,
            value = if (xl) 52.sp else 42.sp,
            valueWrap = if (xl) 28.sp else 23.sp,
            body = if (xl) 18.sp else 15.sp,
            tiny = if (xl) 12.sp else 11.sp,
            badge = if (xl) 44.dp else 36.dp,
            badgeText = if (xl) 22.sp else 18.sp,
            buttonHeight = if (xl) 56.dp else 48.dp,
        )
    }
    // Small landscape (phone landscape, small TV)
    if (isLandscape) {
        return Sizes(
            pagePad = 20.dp,
            pagePadV = 14.dp,
            headerToCards = 12.dp,
            cardsToFooter = 12.dp,
            cardGap = 12.dp,
            cardPad = 14.dp,
            footerInner = 8.dp,
            headerIcon = 26.dp,
            title = 22.sp,
            subtitle = 13.sp,
            instruction = 14.sp,
            label = 11.sp,
            value = 28.sp,
            valueWrap = 16.sp,
            body = 13.sp,
            tiny = 10.sp,
            badge = 28.dp,
            badgeText = 14.sp,
            buttonHeight = 44.dp,
        )
    }
    // Tablet portrait
    if (widthDp >= 600) {
        return Sizes(
            pagePad = 28.dp,
            pagePadV = 24.dp,
            headerToCards = 16.dp,
            cardsToFooter = 16.dp,
            cardGap = 14.dp,
            cardPad = 18.dp,
            footerInner = 10.dp,
            headerIcon = 32.dp,
            title = 26.sp,
            subtitle = 15.sp,
            instruction = 17.sp,
            label = 12.sp,
            value = 36.sp,
            valueWrap = 20.sp,
            body = 15.sp,
            tiny = 11.sp,
            badge = 34.dp,
            badgeText = 17.sp,
            buttonHeight = 52.dp,
        )
    }
    // Phone portrait — every dp counts. Tight but readable.
    val small = heightDp < 700
    return Sizes(
        pagePad = if (small) 12.dp else 14.dp,
        pagePadV = if (small) 10.dp else 12.dp,
        headerToCards = if (small) 8.dp else 10.dp,
        cardsToFooter = if (small) 8.dp else 10.dp,
        cardGap = if (small) 8.dp else 10.dp,
        cardPad = if (small) 10.dp else 12.dp,
        footerInner = 6.dp,
        headerIcon = if (small) 22.dp else 26.dp,
        title = if (small) 18.sp else 20.sp,
        subtitle = if (small) 11.sp else 12.sp,
        instruction = if (small) 13.sp else 14.sp,
        label = if (small) 10.sp else 11.sp,
        value = if (small) 26.sp else 30.sp,
        valueWrap = if (small) 14.sp else 16.sp,
        body = if (small) 12.sp else 13.sp,
        tiny = 10.sp,
        badge = if (small) 24.dp else 28.dp,
        badgeText = if (small) 12.sp else 14.sp,
        buttonHeight = if (small) 42.dp else 46.dp,
    )
}

private fun formatAmount(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)
