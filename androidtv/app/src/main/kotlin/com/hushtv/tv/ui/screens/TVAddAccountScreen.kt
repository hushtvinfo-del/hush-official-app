package com.hushtv.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.Playlist
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.BorderSlate
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.CyanGlow08
import com.hushtv.tv.ui.theme.Green
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Cinema-grade login screen per design-spec §6.
 * Pure black canvas, centered wordmark, card max-width 640dp.
 * Fields: Username → Password → Nickname → Connect.
 */
@Composable
fun TVAddAccountScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

    val userFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { userFocus.requestFocus() } }

    // Shake animation for errors
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            repeat(3) {
                shakeX.animateTo(8f, tween(60))
                shakeX.animateTo(-8f, tween(60))
            }
            shakeX.animateTo(0f, tween(60))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        // Subtle cyan radial glow behind the card
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyanGlow08, Color.Transparent),
                        radius = 900f,
                    )
                )
        )

        Column(
            Modifier
                .widthIn(max = 720.dp)
                .align(Alignment.Center)
                .padding(horizontal = 96.dp, vertical = 54.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo block
            HushTVLogo(fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your Stream. Your Way.",
                color = TextMuted,
                fontSize = 13.sp,
                fontFamily = Inter,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(28.dp))

            // Step indicator (3 dots) — all "pending" for this simple single-step login
            StepDots(active = 0, total = 3)
            Spacer(Modifier.height(20.dp))

            // Card container
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(x = shakeX.value.dp)
                    .background(Color(0x08FFFFFF), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                    .padding(40.dp),
            ) {
                Text(
                    "Connect Your Account",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter your HushTV credentials below to start watching.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(28.dp))

                // Fields
                FieldLabel("Username")
                Spacer(Modifier.height(8.dp))
                TVTextField(
                    value = username,
                    onValue = { username = it; error = null },
                    placeholder = "Your HushTV username",
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.focusRequester(userFocus),
                    isError = error != null,
                )
                Spacer(Modifier.height(20.dp))

                FieldLabel("Password")
                Spacer(Modifier.height(8.dp))
                TVTextField(
                    value = password,
                    onValue = { password = it; error = null },
                    placeholder = "Your HushTV password",
                    keyboardType = KeyboardType.Password,
                    password = true,
                    isError = error != null,
                )
                Spacer(Modifier.height(20.dp))

                FieldLabel("Account Nickname")
                Spacer(Modifier.height(8.dp))
                TVTextField(
                    value = nickname,
                    onValue = { nickname = it; error = null },
                    placeholder = "e.g., Living Room TV",
                    keyboardType = KeyboardType.Text,
                )

                error?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        msg,
                        color = Red,
                        fontSize = 14.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(28.dp))

                // CTA — full-width cyan button
                ConnectButton(
                    loading = loading,
                    success = success,
                    onClick = {
                        if (loading || success) return@ConnectButton
                        error = null
                        if (username.isBlank() || password.isBlank() || nickname.isBlank()) {
                            error = "Please fill in all three fields."
                            return@ConnectButton
                        }
                        loading = true
                        scope.launch {
                            try {
                                val resp = XtreamApi.authenticate(
                                    XtreamApi.HUSH_HOST,
                                    username.trim(),
                                    password.trim(),
                                )
                                if (resp.user_info?.auth == 0 || resp.server_info == null) {
                                    throw RuntimeException("Invalid username or password.")
                                }
                                PlaylistStore.add(
                                    ctx,
                                    Playlist(
                                        id = PlaylistStore.newId(),
                                        name = nickname.trim(),
                                        username = username.trim(),
                                        password = password.trim(),
                                        host = XtreamApi.HUSH_HOST,
                                    ),
                                )
                                loading = false
                                success = true
                                kotlinx.coroutines.delay(800)
                                nav.popBackStack()
                            } catch (e: Exception) {
                                loading = false
                                error = e.message ?: "Failed to sign in. Please try again."
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // Secondary help link
            HelpLink { nav.popBackStack() }
        }
    }
}

/** Always-visible label above each field (per spec — not a floating placeholder). */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 64 dp tall input with cyan focus glow. */
@Composable
private fun TVTextField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    password: Boolean = false,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> Red
        focused -> Cyan
        else -> SurfaceElev
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(SurfaceNavy, RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 18.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
            ),
            cursorBrush = SolidColor(Cyan),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(remember { FocusRequester() })
                .onFocusChanged { focused = it.isFocused }
                .focusable(),
        )
        if (value.isEmpty()) {
            Text(
                placeholder,
                color = BorderSlate,
                fontSize = 18.sp,
                fontFamily = Inter,
            )
        }
    }
}

@Composable
private fun ConnectButton(loading: Boolean, success: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else if (focused) 1.02f else 1f,
        animationSpec = tween(120),
        label = "connect-btn-scale",
    )
    val bg = when {
        success -> Green
        focused -> Cyan
        else -> Cyan
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale)
            .background(bg, RoundedCornerShape(10.dp))
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter {
                pressed = true
                onClick()
                pressed = false
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp),
            )
            success -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connected",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                )
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Connect",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StepDots(active: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            when {
                i == active -> Box(
                    Modifier
                        .width(24.dp)
                        .height(8.dp)
                        .background(Cyan, CircleShape),
                )
                i < active -> Box(
                    Modifier
                        .size(8.dp)
                        .background(TextPrimary, CircleShape),
                )
                else -> Box(
                    Modifier
                        .size(8.dp)
                        .background(SurfaceElev, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun HelpLink(onBack: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = "← Back",
        color = if (focused) Cyan else TextMuted,
        fontSize = 14.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .alpha(if (focused) 1f else 0.8f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onBack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
