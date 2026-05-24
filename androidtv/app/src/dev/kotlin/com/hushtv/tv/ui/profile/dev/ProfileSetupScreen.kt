package com.hushtv.tv.ui.profile.dev

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.AvatarCatalog
import com.hushtv.tv.data.HushAvatar
import com.hushtv.tv.data.HushProfile
import com.hushtv.tv.data.ProfileStore
import com.hushtv.tv.ui.screens.clickableWithEnter

private val BG = Color(0xFF06080F)
private val CARD = Color(0xFF0E1217)
private val CYAN = Color(0xFF22D3EE)
private val WHITE = Color(0xFFF8FAFC)
private val MUTED = Color(0xFFA1A1AA)
private val DANGER = Color(0xFFEF4444)
private val KIDS_ACCENT = Color(0xFFFACC15)

/**
 * v1.44.138-dev — Add / edit a profile.
 *
 * Used in three modes:
 *   1. First-time setup — [existing] = null, [allowDelete] = false,
 *      headline says "Set up your profile".
 *   2. Add new profile — [existing] = null, [allowDelete] = false,
 *      headline "Add profile".
 *   3. Edit existing — [existing] != null, [allowDelete] = true if
 *      there are 2+ profiles (we never let the user delete their
 *      last remaining profile), headline "Edit profile".
 *
 * Form fields:
 *   • Name (TextField, max [HushProfile.MAX_NAME_LEN] chars)
 *   • Avatar (grid of 36, focused = ringed border)
 *   • "Kids' profile" toggle row
 *
 * Footer actions:
 *   • Save (always)
 *   • Delete (mode 3 only, with confirm)
 *   • Back (always)
 */
@Composable
fun ProfileSetupScreen(
    xtreamUsername: String,
    existing: HushProfile?,
    allowDelete: Boolean,
    showHeadline: Boolean = true,
    onSaved: (HushProfile) -> Unit,
    onDeleted: () -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var avatarId by remember {
        mutableStateOf(existing?.avatarId ?: AvatarCatalog.defaultAvatar().id)
    }
    var isKids by remember { mutableStateOf(existing?.isKids ?: false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 80.dp, vertical = 48.dp),
    ) {
        if (showHeadline) {
            Text(
                text = when {
                    existing != null -> "Edit profile"
                    else -> "Set up your profile"
                },
                color = WHITE,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Give your profile a name and pick an avatar. " +
                    "Continue watching, favorites, and last channel all sync " +
                    "across your TVs per profile.",
                color = MUTED,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(28.dp))
        }

        Row(modifier = Modifier.weight(1f)) {
            // Left: large preview card.
            ProfilePreviewCard(
                avatarId = avatarId,
                name = name,
                isKids = isKids,
            )
            Spacer(Modifier.width(40.dp))

            // Right: form fields and avatar picker.
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Name")
                Spacer(Modifier.height(8.dp))
                NameField(
                    value = name,
                    onChange = { name = it.take(HushProfile.MAX_NAME_LEN) },
                    modifier = Modifier.focusRequester(nameFocus),
                )
                Spacer(Modifier.height(20.dp))

                FieldLabel("Kids' profile")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Hides 18+ categories. Requires the Parental Controls PIN " +
                        "to switch out of this profile.",
                    color = MUTED,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                KidsToggle(checked = isKids, onChange = { isKids = it })
                Spacer(Modifier.height(20.dp))

                FieldLabel("Avatar")
                Spacer(Modifier.height(8.dp))
                AvatarGrid(
                    selected = avatarId,
                    onPick = { avatarId = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        error?.let {
            Text(
                it,
                color = DANGER,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FooterButton(
                text = "Save",
                primary = true,
                icon = Icons.Default.Check,
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.length < HushProfile.MIN_NAME_LEN) {
                        error = "Please enter a name."
                        return@FooterButton
                    }
                    val saved = if (existing != null) {
                        if (ProfileStore.update(ctx, xtreamUsername, existing.id, trimmed, avatarId, isKids)) {
                            ProfileStore.list(ctx, xtreamUsername).first { it.id == existing.id }
                        } else null
                    } else {
                        ProfileStore.add(ctx, xtreamUsername, trimmed, avatarId, isKids)
                    }
                    if (saved == null) {
                        error = "Couldn't save the profile. The account may already be at the 5-profile limit."
                    } else {
                        onSaved(saved)
                    }
                },
            )
            FooterButton(
                text = "Back",
                primary = false,
                icon = Icons.Default.ArrowBack,
                onClick = onCancel,
            )
            if (allowDelete && existing != null) {
                Spacer(Modifier.weight(1f))
                FooterButton(
                    text = if (confirmingDelete) "Confirm delete" else "Delete",
                    primary = false,
                    icon = Icons.Default.Delete,
                    danger = true,
                    onClick = {
                        if (!confirmingDelete) {
                            confirmingDelete = true
                        } else {
                            ProfileStore.delete(ctx, xtreamUsername, existing.id)
                            // TODO: purge profile-scoped stores in ProfileScopedStores
                            onDeleted()
                        }
                    },
                )
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────

@Composable
private fun ProfilePreviewCard(
    avatarId: String,
    name: String,
    isKids: Boolean,
) {
    val av = AvatarCatalog.find(avatarId) ?: AvatarCatalog.defaultAvatar()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CARD)
            .border(2.dp, Color(0xFF1F2937), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Box(
            Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            Image(
                painter = painterResource(id = av.drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            if (isKids) {
                KidsBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            name.ifBlank { "Your name" },
            color = if (name.isBlank()) MUTED else WHITE,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        color = MUTED,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun NameField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(
            color = WHITE,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(CYAN),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            capitalization = KeyboardCapitalization.Words,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111827))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) CYAN else Color(0xFF1F2937),
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun KidsToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) Color(0x33FACC15) else Color(0xFF111827))
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> CYAN
                    checked -> KIDS_ACCENT
                    else -> Color(0xFF1F2937)
                },
                RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) KIDS_ACCENT else Color(0xFF1F2937)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "This is a kids' profile",
            color = WHITE,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AvatarGrid(
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(9),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        items(AvatarCatalog.all(), key = { it.id }) { av ->
            AvatarSelectorTile(
                avatar = av,
                selected = av.id == selected,
                onPick = { onPick(av.id) },
            )
        }
    }
}

@Composable
private fun AvatarSelectorTile(
    avatar: HushAvatar,
    selected: Boolean,
    onPick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ring = when {
        focused -> CYAN
        selected -> KIDS_ACCENT
        else -> Color(0xFF1F2937)
    }
    Box(
        Modifier
            .size(if (focused) 70.dp else 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CARD)
            .border(if (focused || selected) 3.dp else 1.dp, ring, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onPick),
    ) {
        Image(
            painter = painterResource(id = avatar.drawableRes),
            contentDescription = avatar.label,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun FooterButton(
    text: String,
    primary: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val (bg, fg, ring) = when {
        danger && focused -> Triple(DANGER, Color.Black, DANGER)
        danger -> Triple(Color.Transparent, DANGER, DANGER)
        primary -> Triple(CYAN, Color.Black, CYAN)
        focused -> Triple(CARD, WHITE, CYAN)
        else -> Triple(Color.Transparent, MUTED, Color(0xFF374151))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(2.dp, ring, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
    }
}
