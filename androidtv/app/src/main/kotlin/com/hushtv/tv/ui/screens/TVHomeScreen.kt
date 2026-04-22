package com.hushtv.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.Playlist
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.CyanGlow08
import com.hushtv.tv.ui.theme.Inter
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextMuted
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable

/**
 * Account picker — first screen after splash.
 * Shows stored accounts + an "Add Account" tile.
 * Design-spec: pure black canvas, centered logo, 640 dp content column.
 */
@Composable
fun TVHomeScreen(nav: NavController) {
    val ctx = LocalContext.current
    val playlists by remember { mutableStateOf(PlaylistStore.getAll(ctx)) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        // Soft cyan radial glow for depth
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyanGlow08, Color.Transparent),
                        radius = 1100f,
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 96.dp, vertical = 54.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            HushTVLogo(fontSize = 64.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Your Stream. Your Way.",
                color = TextMuted,
                fontSize = 14.sp,
                fontFamily = Inter,
                letterSpacing = 1.7.sp,
            )
            Spacer(Modifier.height(48.dp))

            Text(
                text = if (playlists.isEmpty()) "GET STARTED" else "CHOOSE YOUR PROFILE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(20.dp))

            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                playlists.forEachIndexed { i, p ->
                    val mod = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                    AccountCard(p, modifier = mod) {
                        nav.navigate("menu/${p.id}")
                    }
                }
                AddAccountCard(
                    modifier = if (playlists.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                ) {
                    nav.navigate("add")
                }
            }
        }
    }
}

@Composable
private fun AccountCard(p: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(SurfaceNavy, RoundedCornerShape(14.dp))
            .tvFocusable(shape = RoundedCornerShape(14.dp), fillOnFocus = false)
            .clickableWithEnter(onClick),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar — cyan circle with play icon
            Box(
                Modifier
                    .size(56.dp)
                    .background(Cyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "@${p.username}",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Cyan,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun AddAccountCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(Color.Transparent, RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Cyan.copy(alpha = 0.4f), Cyan.copy(alpha = 0.1f))),
                shape = RoundedCornerShape(14.dp),
            )
            .tvFocusable(shape = RoundedCornerShape(14.dp))
            .clickableWithEnter(onClick),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(Color.Transparent, CircleShape)
                    .border(2.dp, Cyan.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, null, tint = Cyan, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(20.dp))
            Text(
                "Add Account",
                color = Cyan,
                fontSize = 18.sp,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
