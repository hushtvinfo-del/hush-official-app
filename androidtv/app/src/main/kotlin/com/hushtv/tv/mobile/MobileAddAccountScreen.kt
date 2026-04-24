package com.hushtv.tv.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.Playlist
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.theme.Cyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mobile-native add-account screen. Portrait-friendly: back bar on top,
 * single-column form, large 56 dp tap targets, soft-keyboard-aware
 * (IME actions walk Nickname → Username → Password → Submit). Shows
 * success + error states inline without bouncing to a toast.
 */
@Composable
fun MobileAddAccountScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardCtl = LocalSoftwareKeyboardController.current

    var nickname by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSubmit = nickname.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        && !loading && !success

    fun submit() {
        if (!canSubmit) return
        keyboardCtl?.hide()
        error = null
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
                val newId = PlaylistStore.newId()
                PlaylistStore.add(
                    ctx,
                    Playlist(
                        id = newId,
                        name = nickname.trim(),
                        username = username.trim(),
                        password = password.trim(),
                        host = XtreamApi.HUSH_HOST,
                    ),
                )
                LastProfileStore.save(ctx, newId)
                loading = false
                success = true
                delay(700)
                nav.navigate("menu/$newId") {
                    popUpTo("home") { inclusive = true }
                }
            } catch (e: Exception) {
                loading = false
                error = e.message ?: "Failed to sign in. Please try again."
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF04070D)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to Color(0x2206B6D4),
                        1f to Color.Transparent,
                        radius = 600f,
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            // Top row: back button.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable { nav.popBackStack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ArrowBack, "Back",
                        tint = Color.White, modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Hero icon + copy.
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Cyan.copy(alpha = 0.18f))
                    .border(2.dp, Cyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Tv, null,
                    tint = Cyan, modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Add your account",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 32.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter the credentials from your HushTV provider. " +
                    "Your details are stored only on this device.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(24.dp))

            // ── Nickname ──
            LabelledField(
                label = "Profile Nickname",
                value = nickname,
                onChange = { nickname = it },
                leading = Icons.Default.Person,
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Text,
            )
            Spacer(Modifier.height(14.dp))

            // ── Username ──
            LabelledField(
                label = "Username",
                value = username,
                onChange = { username = it },
                leading = Icons.Default.Person,
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Text,
            )
            Spacer(Modifier.height(14.dp))

            // ── Password with show/hide ──
            LabelledField(
                label = "Password",
                value = password,
                onChange = { password = it },
                leading = Icons.Default.Lock,
                imeAction = ImeAction.Done,
                keyboardType = if (showPassword) KeyboardType.Text else KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Toggle password visibility",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showPassword = !showPassword },
                    )
                },
                onImeDone = { submit() },
            )

            Spacer(Modifier.height(20.dp))

            // ── Feedback banners ──
            AnimatedVisibility(visible = error != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3B0A0A))
                        .border(1.dp, Color(0xFFF87171), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ErrorOutline, null,
                        tint = Color(0xFFF87171), modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        error ?: "", color = Color(0xFFFEE2E2),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            AnimatedVisibility(visible = success) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF052E16))
                        .border(1.dp, Color(0xFF22C55E), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Signed in! Opening your library…",
                        color = Color(0xFFBBF7D0),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // ── Submit button ──
            SubmitButton(
                enabled = canSubmit,
                loading = loading,
                onClick = { submit() },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LabelledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    onImeDone: () -> Unit = {},
) {
    Column {
        Text(
            label.uppercase(),
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(leading, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            },
            trailingIcon = trailing,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
            keyboardActions = KeyboardActions(
                onDone = { onImeDone() },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x1806B6D4),
                unfocusedContainerColor = Color(0x10FFFFFF),
                focusedIndicatorColor = Cyan,
                unfocusedIndicatorColor = Color(0x33FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Cyan,
            ),
            shape = RoundedCornerShape(10.dp),
        )
    }
}

@Composable
private fun SubmitButton(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val bgColor = if (enabled) Cyan else Color(0x33FFFFFF)
    val textColor = if (enabled) Color(0xFF05080F) else Color(0xFF64748B)
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color(0xFF05080F),
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Signing in…",
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
        } else {
            Text(
                "CONNECT",
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
        }
    }
}
