package com.hushtv.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Red
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.launch

@Composable
fun TVAddAccountScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val userFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { userFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0F2657), Color.Black),
                    radius = 1600f
                )
            )
    ) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 64.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            HushTVLogo(fontSize = 52.sp)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, null, tint = Cyan, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Sign In to HushTV",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Enter your credentials to add an account",
                color = TextSecondary,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(20.dp))

            FieldLabel("Username")
            TVTextField(
                value = username,
                onValue = { username = it },
                placeholder = "Your HushTV username",
                keyboardType = KeyboardType.Text,
                modifier = Modifier.focusRequester(userFocus)
            )
            Spacer(Modifier.height(12.dp))
            FieldLabel("Password")
            TVTextField(
                value = password,
                onValue = { password = it },
                placeholder = "Your HushTV password",
                keyboardType = KeyboardType.Password,
                password = true
            )
            Spacer(Modifier.height(12.dp))
            FieldLabel("Account Nickname")
            TVTextField(
                value = nickname,
                onValue = { nickname = it },
                placeholder = "e.g., Living Room TV",
                keyboardType = KeyboardType.Text
            )
            Spacer(Modifier.height(16.dp))

            error?.let {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x26EF4444), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x66EF4444), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null, tint = Red, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(it, color = Color(0xFFFCA5A5), fontSize = 18.sp)
                }
                Spacer(Modifier.height(20.dp))
            }

            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(shape = RoundedCornerShape(16.dp))
                    .clickableWithEnter {
                        if (loading) return@clickableWithEnter
                        error = null
                        if (username.isBlank() || password.isBlank() || nickname.isBlank()) {
                            error = "Please fill in all fields"
                            return@clickableWithEnter
                        }
                        loading = true
                        scope.launch {
                            try {
                                val resp = XtreamApi.authenticate(
                                    XtreamApi.HUSH_HOST, username.trim(), password.trim()
                                )
                                if (resp.user_info?.auth == 0 || resp.server_info == null) {
                                    throw RuntimeException("Invalid username or password. Please try again.")
                                }
                                PlaylistStore.add(
                                    ctx,
                                    Playlist(
                                        id = PlaylistStore.newId(),
                                        name = nickname.trim(),
                                        username = username.trim(),
                                        password = password.trim(),
                                        host = XtreamApi.HUSH_HOST
                                    )
                                )
                                nav.popBackStack()
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to sign in. Please try again."
                            } finally {
                                loading = false
                            }
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF3B82F6), Cyan)),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(26.dp))
                    } else {
                        Text("Sign In", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                color = Color(0x0DFFFFFF),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                    .tvFocusable(shape = RoundedCornerShape(16.dp))
                    .clickableWithEnter { nav.popBackStack() }
            ) {
                Row(
                    Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Back", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = Color(0xFFD1D5DB),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun TVTextField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(12.dp))
            .border(
                2.dp,
                if (focused) Cyan else Color(0x26FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Cyan),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
        )
        if (value.isEmpty()) {
            Text(placeholder, color = TextSecondary, fontSize = 18.sp)
        }
    }
}
