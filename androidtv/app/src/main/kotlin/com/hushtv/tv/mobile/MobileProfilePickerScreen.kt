package com.hushtv.tv.mobile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.Playlist
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.Cyan

/**
 * Mobile profile picker — replaces the TV picker when running on a
 * phone. Touch-first: one-tap-to-choose, long-press / swipe-style
 * delete via a trailing delete affordance (mobile users don't D-pad
 * to a delete icon — they need it prominent).
 *
 * Design: dark navy canvas, cyan radial halo, HushTV wordmark, account
 * cards that fill the column width and have big 56 dp tap targets.
 */
@Composable
fun MobileProfilePickerScreen(nav: NavController) {
    val ctx = LocalContext.current
    var profiles by remember { mutableStateOf(PlaylistStore.getAll(ctx)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF04070D)),
    ) {
        // Cyan halo behind the header for depth.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to Color(0x3306B6D4),
                        1f to Color.Transparent,
                        radius = 700f,
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Wordmark ──
            Text(
                "HushTV",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Who's watching?",
                color = Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(28.dp))

            profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    onTap = {
                        LastProfileStore.save(ctx, profile.id)
                        nav.navigate("menu/${profile.id}") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    onDelete = {
                        PlaylistStore.remove(ctx, profile.id)
                        profiles = PlaylistStore.getAll(ctx)
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            AddProfileCard(onTap = { nav.navigate("add") })

            Spacer(Modifier.height(40.dp))
            Text(
                "Streaming powered by your Xtream provider.",
                color = Color(0xFF475569),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Playlist,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(120), label = "press",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0E1A2E), Color(0xFF0A1220)),
                )
            )
            .border(
                width = 1.dp,
                color = Color(0x3306B6D4),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable {
                pressed = true
                onTap()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar disc.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Cyan.copy(alpha = 0.2f))
                .border(2.dp, Cyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.name.take(1).uppercase().ifBlank { "H" },
                color = Cyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                profile.username,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Delete affordance — separated tap target so tapping the card
        // body picks the profile and only the trash icon removes it.
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Delete, "Remove profile",
                tint = Color(0xFFF87171),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.ChevronRight, null,
            tint = Color(0xFF64748B), modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AddProfileCard(onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = Cyan,
                shape = RoundedCornerShape(14.dp),
            )
            .background(Cyan.copy(alpha = 0.08f))
            .clickable(onClick = onTap)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Cyan),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add, null,
                tint = Color(0xFF05080F),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Add Profile",
                color = Cyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Sign in with your Xtream credentials",
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
            )
        }
    }
}
