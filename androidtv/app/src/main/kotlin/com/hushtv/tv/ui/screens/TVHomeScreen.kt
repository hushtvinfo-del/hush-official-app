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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
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
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable

@Composable
fun TVHomeScreen(nav: NavController) {
    val ctx = LocalContext.current
    var playlists by remember { mutableStateOf(PlaylistStore.getAll(ctx)) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 64.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HushTVLogo(fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Official Android TV App",
                color = TextSecondary,
                fontSize = 24.sp
            )
            Spacer(Modifier.height(56.dp))

            Column(
                Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                playlists.forEachIndexed { i, p ->
                    val mod = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                    AccountCard(p, modifier = mod) {
                        nav.navigate("menu/${p.id}")
                    }
                }
                AddAccountCard(
                    modifier = if (playlists.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier
                ) {
                    nav.navigate("add")
                }
            }
        }
    }
}

@Composable
private fun AccountCard(p: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Color(0x14FFFFFF),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
            .tvFocusable(shape = RoundedCornerShape(20.dp))
            .clickableWithEnter(onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 32.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF3B82F6), Cyan)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("@${p.username}", color = TextSecondary, fontSize = 18.sp)
            }
            Text("Watch Now →", color = Cyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddAccountCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Color(0x1406B6D4),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, Color(0x6606B6D4), RoundedCornerShape(20.dp))
            .tvFocusable(shape = RoundedCornerShape(20.dp))
            .clickableWithEnter(onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 32.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(Color(0x3306B6D4), CircleShape)
                    .border(2.dp, Color(0x6606B6D4), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, null, tint = Cyan, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(20.dp))
            Text("Add Account", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
