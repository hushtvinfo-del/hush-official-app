package com.hushtv.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.ui.HushTVLogo
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import com.hushtv.tv.ui.tvFocusable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MenuItem(
    val name: String,
    val icon: ImageVector,
    val type: String,
    val color: Color
)

private val MENU_ITEMS = listOf(
    MenuItem("Live TV", Icons.Default.Tv, "live", Color(0xFF3B82F6)),
    MenuItem("Movies", Icons.Default.Movie, "movie", Color(0xFF8B5CF6)),
    MenuItem("Series", Icons.Outlined.Slideshow, "series", Color(0xFFEC4899)),
    MenuItem("Favorites", Icons.Default.Star, "favorites", Color(0xFFF59E0B)),
    MenuItem("Search", Icons.Default.Search, "search", Color(0xFF10B981)),
)

@Composable
fun TVMainMenuScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    var expiryStr by remember { mutableStateOf<String?>(null) }
    var daysLeft by remember { mutableStateOf<Long?>(null) }
    var displayName by remember { mutableStateOf(playlist?.name ?: "User") }

    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        scope.launch {
            runCatching {
                XtreamApi.authenticate(p.host, p.username, p.password)
            }.onSuccess { resp ->
                val expTs = resp.user_info?.exp_date?.toLongOrNull()
                if (expTs != null && expTs > 0) {
                    val d = Date(expTs * 1000)
                    expiryStr = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(d)
                    daysLeft = ((expTs * 1000 - System.currentTimeMillis()) / (1000L * 60 * 60 * 24))
                }
                resp.user_info?.username?.let { displayName = it }
            }
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(Color(0xFF050D1A), Color.Black))
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HushTVLogo(fontSize = 56.sp)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(displayName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    expiryStr?.let { exp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Expires $exp", color = TextSecondary, fontSize = 14.sp)
                            daysLeft?.let { d ->
                                if (d in 0..7) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier
                                            .background(Color(0xFFEAB308), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "${d}d left",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (d < 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier
                                            .background(Color(0xFFDC2626), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Expired", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .padding(horizontal = 64.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0x8006B6D4), Color.Transparent)))
            )

            Spacer(Modifier.height(48.dp))

            Column(Modifier.padding(horizontal = 64.dp)) {
                Text(
                    "WHAT WOULD YOU LIKE TO WATCH?",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                    MENU_ITEMS.forEachIndexed { i, item ->
                        val mod = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                        MenuTile(
                            modifier = Modifier.weight(1f).then(mod),
                            item = item,
                            onClick = {
                                if (item.type == "favorites" || item.type == "search") {
                                    nav.navigate("browse/$playlistId/${item.type}")
                                } else {
                                    nav.navigate("browse/$playlistId/${item.type}")
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .tvFocusable(shape = RoundedCornerShape(10.dp))
                        .clickableWithEnter { nav.popBackStack() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Switch Account", color = TextSecondary, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuTile(modifier: Modifier = Modifier, item: MenuItem, onClick: () -> Unit) {
    Surface(
        color = Color(0x0DFFFFFF),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .height(220.dp)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
            .tvFocusable(shape = RoundedCornerShape(20.dp))
            .clickableWithEnter(onClick)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(item.icon, null, tint = item.color, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(20.dp))
            Text(item.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
