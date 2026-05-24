package com.hushtv.tv.ui.profile.dev

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.AvatarCatalog
import com.hushtv.tv.data.HushProfile
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.screens.clickableWithEnter

private val BG = Color(0xFF06080F)
private val CARD = Color(0xFF0E1217)
private val CYAN = Color(0xFF22D3EE)
private val WHITE = Color(0xFFF8FAFC)
private val MUTED = Color(0xFFA1A1AA)
private val KIDS_ACCENT = Color(0xFFFACC15)

/**
 * v1.44.138-dev — "Who's watching?" screen. Netflix-style grid of
 * profile tiles with a trailing "Add Profile" tile.
 *
 * Navigation:
 *   • Click a profile tile → activates it and calls [onPicked].
 *   • Click "Add Profile" → routes to [onAdd] (caller pushes the
 *     setup screen).
 *   • Click pencil ✏ "Manage Profiles" → routes to [onManage]
 *     (edit-mode: tap profile to edit, includes Delete option).
 *
 * The parent (DevProfileGate) guarantees [profiles] is non-empty
 * before showing this screen — first-time users go through the
 * setup screen directly.
 */
@Composable
fun ProfilePickerScreen(
    profiles: List<HushProfile>,
    onPicked: (HushProfile) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(profiles) { runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 80.dp, vertical = 60.dp),
        ) {
            HushTVLogo(fontSize = 36.sp)
            Spacer(Modifier.height(28.dp))
            Text(
                "Who's watching?",
                color = WHITE,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(36.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                items(profiles, key = { it.id }) { p ->
                    val isFirst = p.id == profiles.first().id
                    ProfileTile(
                        profile = p,
                        modifier = if (isFirst) {
                            Modifier.focusRequester(firstFocus)
                        } else Modifier,
                        onClick = { onPicked(p) },
                    )
                }
                if (profiles.size < HushProfile.MAX_PROFILES) {
                    item("add") { AddProfileTile(onClick = onAdd) }
                }
            }
            Spacer(Modifier.height(40.dp))
            ManageProfilesLink(onClick = onManage)
        }
    }
}

@Composable
private fun ProfileTile(
    profile: HushProfile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(150.dp),
    ) {
        Box(
            Modifier
                .size(if (focused) 144.dp else 130.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CARD)
                .border(
                    width = if (focused) 4.dp else 2.dp,
                    color = if (focused) CYAN else Color(0xFF1F2937),
                    shape = RoundedCornerShape(20.dp),
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickableWithEnter(onClick),
        ) {
            val av = AvatarCatalog.find(profile.avatarId) ?: AvatarCatalog.defaultAvatar()
            Image(
                painter = painterResource(id = av.drawableRes),
                contentDescription = "Profile ${profile.name}",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
            )
            if (profile.isKids) {
                KidsBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name,
            color = if (focused) WHITE else MUTED,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.Black else FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(150.dp),
    ) {
        Box(
            Modifier
                .size(if (focused) 144.dp else 130.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101524))
                .border(
                    width = if (focused) 4.dp else 2.dp,
                    color = if (focused) CYAN else Color(0xFF1F2937),
                    shape = RoundedCornerShape(20.dp),
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickableWithEnter(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add profile",
                tint = if (focused) CYAN else MUTED,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Add Profile",
            color = if (focused) WHITE else MUTED,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun KidsBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(KIDS_ACCENT, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(
            Icons.Default.ChildCare,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "KIDS",
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun ManageProfilesLink(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (focused) CARD else Color.Transparent)
            .border(
                1.5.dp,
                if (focused) CYAN else Color(0xFF374151),
                RoundedCornerShape(999.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = if (focused) CYAN else MUTED,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Manage Profiles",
            color = if (focused) WHITE else MUTED,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}
