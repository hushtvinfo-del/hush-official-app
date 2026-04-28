package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.navigation.NavController
import com.hushtv.tv.ui.theme.Cyan

/**
 * Mobile scaffold — inner tab switcher (Home / Movies / Series / Live /
 * Settings) driven by a bottom nav bar. Lives INSIDE a profile shell
 * (the "shell/{playlistId}" NavHost entry) so each tab is a composable
 * swap, not a full navigation. Cheaper, preserves scroll state.
 */
@Composable
fun MobileShell(nav: NavController, playlistId: String) {
    var tab by rememberSaveable { mutableStateOf("home") }

    Scaffold(
        containerColor = Color(0xFF05080F),
        bottomBar = {
            MobileBottomNav(
                active = tab,
                onTab = { tab = it },
                onSearch = { nav.navigate("msearch/$playlistId") },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF05080F)),
        ) {
            when (tab) {
                "home" -> MobileHomeScreen(nav, playlistId)
                "movies" -> MobileBrowseScreen(nav, playlistId, "movie", inline = true)
                "series" -> MobileBrowseScreen(nav, playlistId, "series", inline = true)
                "live" -> MobileLiveHubScreen(nav, playlistId)
                "hushplus" -> com.hushtv.tv.ui.hushplus.MobileHushPlusScreen()
                "settings" -> MobileSettingsScreen(nav, playlistId)
                else -> MobileHomeScreen(nav, playlistId)
            }
        }
    }
}

@Composable
private fun MobileBottomNav(
    active: String,
    onTab: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val items = listOf(
        BottomItem("home", "Home", Icons.Default.Home),
        BottomItem("live", "Live", Icons.Default.LiveTv),
        BottomItem("movies", "Movies", Icons.Default.Movie),
        BottomItem("series", "Series", Icons.Default.Tv),
        BottomItem("hushplus", "Hush+", Icons.Default.Star),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A1220))
            .border(
                width = 0.5.dp,
                color = Color(0x1FFFFFFF),
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            BottomNavBtn(
                label = item.label,
                icon = item.icon,
                selected = active == item.key,
                onClick = { onTab(item.key) },
            )
        }
        BottomNavBtn(
            label = "Search",
            icon = Icons.Default.Search,
            selected = false,
            onClick = onSearch,
        )
        // Settings lives at the very end — logical "account & more"
        // bucket that the user reaches for last, just like the
        // settings gear in the TV top-nav.
        BottomNavBtn(
            label = "Settings",
            icon = Icons.Default.Settings,
            selected = active == "settings",
            onClick = { onTab("settings") },
        )
    }
}

@Composable
private fun BottomNavBtn(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) Cyan else Color(0xFF94A3B8),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) Cyan else Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private data class BottomItem(val key: String, val label: String, val icon: ImageVector)
