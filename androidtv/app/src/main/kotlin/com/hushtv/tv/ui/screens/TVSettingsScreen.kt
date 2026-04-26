package com.hushtv.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.data.LayoutPrefsStore
import com.hushtv.tv.data.PinStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.data.XtreamApi
import com.hushtv.tv.data.XtreamCategory
import com.hushtv.tv.ui.player.PinDialog
import com.hushtv.tv.ui.player.PinMode
import com.hushtv.tv.ui.screens.home.LayoutChooserDialog
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Settings / Parental controls screen.
 *  • Set or change the 4-digit PIN
 *  • Toggle which Live TV categories are locked behind the PIN
 */
@Composable
fun TVSettingsScreen(nav: NavController, playlistId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist = remember { PlaylistStore.find(ctx, playlistId) }

    var hasPin by remember { mutableStateOf(PinStore.hasPin(ctx)) }
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var lockedIds by remember { mutableStateOf(PinStore.lockedCategoryIds(ctx)) }

    // Auto-resume last channel on launch — opt-in toggle.
    var autoResume by remember {
        mutableStateOf(com.hushtv.tv.data.AutoResumeStore.isEnabled(ctx))
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf<() -> Unit>({}) }

    // Layout chooser (reuses the first-run modal).
    var showLayoutChooser by remember { mutableStateOf(false) }
    var currentLayoutMode by remember { mutableStateOf(LayoutPrefsStore.mode(ctx)) }
    val currentLayoutLabel = when (currentLayoutMode) {
        LayoutPrefsStore.MODE_SIDEBAR -> "Left Sidebar"
        else -> "Top Bar"
    }

    LaunchedEffect(playlistId) {
        val p = playlist ?: return@LaunchedEffect
        scope.launch {
            runCatching {
                categories = XtreamApi.getCategories(p.host, p.username, p.password, "live")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackBtn { nav.popBackStack() }
            Spacer(Modifier.width(16.dp))
            Text("Parental Controls", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── PROFILE ─────────────────────────────────────────────
            item {
                Text(
                    "PROFILE",
                    color = TextSecondary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
                )
            }
            item {
                SettingsCard(
                    title = "Switch Profile",
                    subtitle = playlist?.name?.let { "Signed in as $it — switch to another" }
                        ?: "Pick a different profile",
                    icon = { Icon(Icons.Default.Person, null, tint = Cyan, modifier = Modifier.size(24.dp)) },
                    onClick = { nav.navigate("home") },
                )
            }
            item {
                SettingsCard(
                    title = "Add Another Profile",
                    subtitle = "Sign in with a different Xtream account",
                    icon = { Icon(Icons.Default.PersonAdd, null, tint = Cyan, modifier = Modifier.size(24.dp)) },
                    onClick = { nav.navigate("add") },
                )
            }

            // ── LAYOUT ──────────────────────────────────────────────
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    "LAYOUT",
                    color = TextSecondary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
                )
            }
            item {
                SettingsCard(
                    title = "Change Layout",
                    subtitle = "Currently using $currentLayoutLabel — applies to Live TV, Movies, Series",
                    icon = { Icon(Icons.Default.Dashboard, null, tint = Cyan, modifier = Modifier.size(24.dp)) },
                    onClick = { showLayoutChooser = true },
                )
            }

            // ── MY CONTENT ──────────────────────────────────────────
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    "MY CONTENT",
                    color = TextSecondary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
                )
            }
            item {
                SettingsCard(
                    title = "My content requests",
                    subtitle = "See the status of your missing-content requests",
                    icon = { Icon(Icons.Default.Inbox, null, tint = Cyan, modifier = Modifier.size(24.dp)) },
                    onClick = { nav.navigate("myrequests/$playlistId") },
                )
            }
            item {
                SettingsCard(
                    title = "Auto-resume last channel on launch",
                    subtitle = if (autoResume)
                        "ON — app will jump straight into your last live channel"
                    else
                        "OFF — app opens to the home menu",
                    icon = {
                        Icon(
                            Icons.Default.PlayCircle, null,
                            tint = if (autoResume) Cyan else TextSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        autoResume = !autoResume
                        com.hushtv.tv.data.AutoResumeStore.setEnabled(ctx, autoResume)
                    },
                )
            }

            // ── DIAGNOSTICS ─────────────────────────────────────────
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    "DIAGNOSTICS",
                    color = TextSecondary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
                )
            }
            item {
                SettingsCard(
                    title = "Speed test",
                    subtitle = "Check your network against the streaming tiers",
                    icon = { Icon(Icons.Default.Speed, null, tint = Cyan, modifier = Modifier.size(24.dp)) },
                    onClick = { nav.navigate("speedtest") },
                )
            }
            item {
                SettingsCard(
                    title = "View / send crash log",
                    subtitle = "Share a crash report if the app force-closes",
                    icon = { Icon(Icons.Default.Report, null, tint = Cyan, modifier = Modifier.size(24.dp)) },
                    onClick = { nav.navigate("diag") },
                )
            }

            // ── PARENTAL CONTROLS ───────────────────────────────────
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    "PARENTAL CONTROLS",
                    color = TextSecondary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
                )
            }
            item {
                SettingsCard(
                    title = if (hasPin) "Change PIN" else "Set a 4-digit PIN",
                    subtitle = if (hasPin) "You can change your PIN anytime"
                               else "Required before you can lock categories",
                    icon = { Icon(Icons.Default.Lock, null, tint = Cyan, modifier = Modifier.size(24.dp)) },
                    onClick = {
                        if (hasPin) {
                            pinAction = {
                                showPinDialog = false
                                pinAction = { showPinDialog = false; hasPin = true }
                                showPinDialog = true
                            }
                        } else {
                            pinAction = {
                                showPinDialog = false
                                hasPin = true
                            }
                        }
                        showPinDialog = true
                    },
                )
            }
            if (hasPin) {
                item {
                    SettingsCard(
                        title = "Remove PIN",
                        subtitle = "Disable parental controls entirely",
                        icon = { Icon(Icons.Default.LockOpen, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(24.dp)) },
                        onClick = {
                            pinAction = {
                                PinStore.clearPin(ctx)
                                hasPin = false
                                lockedIds = emptySet()
                                showPinDialog = false
                            }
                            showPinDialog = true
                        },
                    )
                }
            }

            // ── LOCKED CATEGORIES ───────────────────────────────────
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    "LOCKED CATEGORIES",
                    color = TextSecondary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
                )
            }
            if (!hasPin) {
                item {
                    Text(
                        "Set a PIN first to start locking categories",
                        color = TextSecondary, fontSize = 14.sp,
                    )
                }
            }
            items(categories, key = { it.category_id }) { cat ->
                LockRow(
                    name = cat.category_name,
                    locked = lockedIds.contains(cat.category_id),
                    enabled = hasPin,
                    onToggle = {
                        if (!hasPin) return@LockRow
                        PinStore.toggleLock(ctx, cat.category_id)
                        lockedIds = PinStore.lockedCategoryIds(ctx)
                    },
                )
            }
            // Trailing space so the last item isn't flush with the bottom edge
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showPinDialog) {
        if (hasPin) {
            PinDialog(
                mode = PinMode.Verify,
                title = "Enter your PIN",
                onCancel = { showPinDialog = false },
                onSuccess = { pinAction() },
                verifier = { PinStore.checkPin(ctx, it) }
            )
        } else {
            PinDialog(
                mode = PinMode.SetNew,
                onCancel = { showPinDialog = false },
                onSuccess = { pin ->
                    PinStore.setPin(ctx, pin)
                    pinAction()
                }
            )
        }
    }

    if (showLayoutChooser) {
        LayoutChooserDialog(
            currentMode = currentLayoutMode,
            dismissable = true,
            onPicked = { mode ->
                LayoutPrefsStore.setMode(ctx, mode)
                currentLayoutMode = mode
                showLayoutChooser = false
            },
            onDismiss = { showLayoutChooser = false },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x08FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Cyan else Color(0x14FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LockRow(name: String, locked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x06FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Cyan else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onToggle)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            name,
            color = if (enabled) Color.White else TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (locked) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFFFACC15), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Locked", color = Color(0xFFFACC15), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text("Open", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BackBtn(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = Color(0x1AFFFFFF), shape = CircleShape,
        modifier = Modifier
            .size(44.dp)
            .border(if (focused) 2.dp else 0.dp, if (focused) Cyan else Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
