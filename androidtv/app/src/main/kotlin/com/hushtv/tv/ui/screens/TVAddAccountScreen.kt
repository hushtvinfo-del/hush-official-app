package com.hushtv.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.graphicsLayer
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
 * TV-native login — two-column 1920×1080 layout that fits in a single viewport
 * without any scrolling. No mobile-style stack.
 *
 *  Left 42 %  →  hushtv. wordmark + tagline + marketing blurb
 *  Right 58 % →  form card (Username → Password → Nickname → Connect)
 *
 * Both columns are vertically centered inside the TV safe zone.
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

    // Shake animation on error
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
        // Soft cyan radial glow centered on the card
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyanGlow08, Color.Transparent),
                        radius = 1000f,
                    )
                )
        )

        // Two-column layout
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── LEFT: branding ─────────────────────────────
            Column(
                Modifier
                    .weight(0.42f)
                    .padding(end = 40.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                HushTVLogo(fontSize = 64.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your Stream. Your Way.",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    letterSpacing = 1.7.sp,
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    "Welcome back",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sign in with your HushTV credentials to pick up exactly where you left off — " +
                        "live channels, movies, series, and your watchlist, all on the big screen.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(22.dp))
                BulletPoint("Thousands of live channels and premium VOD")
                Spacer(Modifier.height(6.dp))
                BulletPoint("Per-profile watch history & bookmarks")
                Spacer(Modifier.height(6.dp))
                BulletPoint("TMDB metadata & cast-level recommendations")
            }

            // ── RIGHT: form card ───────────────────────────
            Column(
                Modifier
                    .weight(0.58f)
                    .graphicsLayer { translationX = shakeX.value }
                    .background(Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                // Step dots
                StepDots(active = 0, total = 3)
                Spacer(Modifier.height(14.dp))

                Text(
                    "Connect Your Account",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fill in all three fields to start watching.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = Inter,
                )
                Spacer(Modifier.height(18.dp))

                FieldLabel("Username")
                Spacer(Modifier.height(6.dp))
                TVTextField(
                    value = username,
                    onValue = { username = it; error = null },
                    placeholder = "Your HushTV username",
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.focusRequester(userFocus),
                    isError = error != null,
                )
                Spacer(Modifier.height(14.dp))

                FieldLabel("Password")
                Spacer(Modifier.height(6.dp))
                TVTextField(
                    value = password,
                    onValue = { password = it; error = null },
                    placeholder = "Your HushTV password",
                    keyboardType = KeyboardType.Password,
                    password = true,
                    isError = error != null,
                )
                Spacer(Modifier.height(14.dp))

                FieldLabel("Account Nickname")
                Spacer(Modifier.height(6.dp))
                TVTextField(
                    value = nickname,
                    onValue = { nickname = it; error = null },
                    placeholder = "e.g., Living Room TV",
                    keyboardType = KeyboardType.Text,
                )

                error?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = Red,
                        fontSize = 13.sp,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConnectButton(
                        loading = loading,
                        success = success,
                        modifier = Modifier.weight(1f),
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
                    BackButton(onClick = { nav.popBackStack() })
                }
            }
        }
    }
}

/* ─── Small pieces ────────────────────────────────────────────── */

@Composable
private fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(5.dp)
                .background(Cyan, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontFamily = Inter,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 12.sp,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )
}

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
            .height(56.dp)
            .background(SurfaceNavy, RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 16.sp,
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
                fontSize = 16.sp,
                fontFamily = Inter,
            )
        }
    }
}

@Composable
private fun ConnectButton(
    loading: Boolean,
    success: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(120),
        label = "connect-btn-scale",
    )
    val bg = when {
        success -> Green
        else -> Cyan
    }
    Box(
        modifier
            .height(56.dp)
            .scale(scale)
            .background(bg, RoundedCornerShape(10.dp))
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp),
            )
            success -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connected",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                )
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Connect",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .height(56.dp)
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x14FFFFFF),
                RoundedCornerShape(10.dp),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Cyan else Color(0x33FFFFFF),
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Back",
            color = if (focused) Cyan else TextPrimary,
            fontSize = 15.sp,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StepDots(active: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            when {
                i == active -> Box(
                    Modifier
                        .width(20.dp)
                        .height(6.dp)
                        .background(Cyan, CircleShape),
                )
                i < active -> Box(
                    Modifier
                        .size(6.dp)
                        .background(TextPrimary, CircleShape),
                )
                else -> Box(
                    Modifier
                        .size(6.dp)
                        .background(SurfaceElev, CircleShape),
                )
            }
        }
    }
}
