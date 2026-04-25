package com.hushtv.tv.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.UserContactStore
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modal for submitting a "Request Missing Content" entry to the
 * HushTV admin API.
 *
 * Single composable used by both TV (D-pad / clicker focus) and
 * Mobile (touch). Centered card layout, ~600 dp max width — safe on
 * both 10-foot displays and phones in portrait.
 *
 * Three internal screens, swapped by [Phase]:
 *   • CONTACT  — first-time prompt for name + email; skipped on
 *                subsequent uses thanks to [UserContactStore].
 *   • FORM     — type (movie/series), pre-filled title, optional
 *                series details, optional notes.
 *   • SUCCESS  — confirmation with "View My Requests" link.
 *   • ERROR    — inline; never an alert dialog (per spec).
 *
 * The dialog accepts [presetType] / [presetTitle] / [presetSeason] /
 * [presetEpisode] so callers can pre-fill from the screen the user
 * came from (search query, missing episode, etc.).
 *
 * [onViewMyRequests] navigates to the My Requests screen on success.
 * Caller can pass null on TV/Mobile screens that don't have nav set
 * up yet — the link just won't render.
 */
@Composable
fun RequestContentSheet(
    presetType: String = "movie",
    presetTitle: String = "",
    presetSeason: String = "",
    presetEpisode: String = "",
    onDismiss: () -> Unit,
    onViewMyRequests: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember {
        mutableStateOf(
            if (UserContactStore.get(ctx) == null) Phase.CONTACT else Phase.FORM
        )
    }

    var contactName by remember {
        mutableStateOf(UserContactStore.get(ctx)?.name.orEmpty())
    }
    var contactEmail by remember {
        mutableStateOf(UserContactStore.get(ctx)?.email.orEmpty())
    }
    var contactError by remember { mutableStateOf<String?>(null) }

    var type by remember { mutableStateOf(presetType.takeIf { it == "series" } ?: "movie") }
    var title by remember { mutableStateOf(presetTitle) }
    var seriesScope by remember {
        mutableStateOf(
            if (presetSeason.isNotBlank() || presetEpisode.isNotBlank())
                "specific_episodes" else "entire_series"
        )
    }
    var seasons by remember { mutableStateOf(presetSeason) }
    var episodes by remember { mutableStateOf(presetEpisode) }
    var notes by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<ContentRequestApi.SubmitResult.Success?>(null) }

    // Outer scrim — fills the screen and absorbs taps so background
    // controls don't fire underneath.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .clickable(enabled = false) {},
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp)
                .background(SurfaceNavy, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 26.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (phase) {
                Phase.CONTACT -> ContactPhase(
                    name = contactName,
                    email = contactEmail,
                    error = contactError,
                    onName = { contactName = it },
                    onEmail = { contactEmail = it },
                    onContinue = {
                        val n = contactName.trim()
                        val e = contactEmail.trim()
                        when {
                            n.isEmpty() -> contactError = "Please enter your name."
                            !UserContactStore.isValidEmail(e) ->
                                contactError = "That doesn't look like a valid email."
                            else -> {
                                UserContactStore.set(ctx, n, e)
                                contactError = null
                                phase = Phase.FORM
                            }
                        }
                    },
                    onCancel = onDismiss,
                )
                Phase.FORM -> FormPhase(
                    type = type, onType = { type = it },
                    title = title, onTitle = { title = it },
                    seriesScope = seriesScope, onSeriesScope = { seriesScope = it },
                    seasons = seasons, onSeasons = { seasons = it },
                    episodes = episodes, onEpisodes = { episodes = it },
                    notes = notes, onNotes = { notes = it },
                    submitting = submitting,
                    error = submitError,
                    onChangeContact = {
                        contactError = null
                        phase = Phase.CONTACT
                    },
                    onCancel = onDismiss,
                    onSubmit = submit@{
                        val cleanTitle = title.trim()
                        if (cleanTitle.isEmpty()) {
                            submitError = "Please enter a title."
                            return@submit
                        }
                        submitting = true
                        submitError = null
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                ContentRequestApi.submitRequest(
                                    ctx = ctx,
                                    type = type,
                                    title = cleanTitle,
                                    additionalInfo = notes.trim().ifBlank { null },
                                    seriesRequestType = if (type == "series") seriesScope else null,
                                    seasons = if (type == "series" && seriesScope == "specific_episodes")
                                        seasons.trim().ifBlank { null } else null,
                                    episodes = if (type == "series" && seriesScope == "specific_episodes")
                                        episodes.trim().ifBlank { null } else null,
                                )
                            }
                            submitting = false
                            when (res) {
                                is ContentRequestApi.SubmitResult.Success -> {
                                    lastResult = res
                                    phase = Phase.SUCCESS
                                }
                                is ContentRequestApi.SubmitResult.Error ->
                                    submitError = res.message
                            }
                        }
                    },
                )
                Phase.SUCCESS -> SuccessPhase(
                    title = title.trim().ifEmpty { "your title" },
                    onClose = onDismiss,
                    onViewMyRequests = onViewMyRequests,
                )
            }
        }
    }
}

private enum class Phase { CONTACT, FORM, SUCCESS }


// ─── Contact phase ──────────────────────────────────────────────────

@Composable
private fun ContactPhase(
    name: String, email: String, error: String?,
    onName: (String) -> Unit, onEmail: (String) -> Unit,
    onContinue: () -> Unit, onCancel: () -> Unit,
) {
    Text("First, who are you?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
        "We'll use this to update you when your request is added. " +
            "We only ask once.",
        color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp,
    )
    Spacer(Modifier.height(20.dp))
    LabeledField("Your name", name, onName, KeyboardType.Text)
    Spacer(Modifier.height(12.dp))
    LabeledField("Your email", email, onEmail, KeyboardType.Email)
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(error, color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(22.dp))
    PrimaryButton(label = "Continue", enabled = true, loading = false, onClick = onContinue)
    Spacer(Modifier.height(8.dp))
    SecondaryButton(label = "Cancel", onClick = onCancel)
}


// ─── Form phase ─────────────────────────────────────────────────────

@Composable
private fun FormPhase(
    type: String, onType: (String) -> Unit,
    title: String, onTitle: (String) -> Unit,
    seriesScope: String, onSeriesScope: (String) -> Unit,
    seasons: String, onSeasons: (String) -> Unit,
    episodes: String, onEpisodes: (String) -> Unit,
    notes: String, onNotes: (String) -> Unit,
    submitting: Boolean, error: String?,
    onChangeContact: () -> Unit, onCancel: () -> Unit, onSubmit: () -> Unit,
) {
    Text("Request missing content", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "Tell us what to add and we'll get on it.",
        color = TextSecondary, fontSize = 14.sp,
    )

    Spacer(Modifier.height(20.dp))
    Text("WHAT IS IT?", color = TextSecondary, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TypeRadio("🎬", "Movie", type == "movie", Modifier.weight(1f)) { onType("movie") }
        TypeRadio("📺", "Series", type == "series", Modifier.weight(1f)) { onType("series") }
    }

    Spacer(Modifier.height(20.dp))
    LabeledField("Title", title, onTitle, KeyboardType.Text)

    if (type == "series") {
        Spacer(Modifier.height(20.dp))
        Text("WHAT'S MISSING?", color = TextSecondary, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        ScopeRow("Entire series", seriesScope == "entire_series") {
            onSeriesScope("entire_series")
        }
        Spacer(Modifier.height(8.dp))
        ScopeRow("Specific seasons / episodes", seriesScope == "specific_episodes") {
            onSeriesScope("specific_episodes")
        }
        if (seriesScope == "specific_episodes") {
            Spacer(Modifier.height(14.dp))
            LabeledField("Seasons (e.g. \"Season 1, Season 3\")", seasons, onSeasons,
                KeyboardType.Text)
            Spacer(Modifier.height(10.dp))
            LabeledField("Episodes (e.g. \"S1E5, S2E3-S2E7\")", episodes, onEpisodes,
                KeyboardType.Text)
        }
    }

    Spacer(Modifier.height(20.dp))
    LabeledField(
        label = "Additional info (optional)",
        value = notes,
        onValue = onNotes,
        keyboardType = KeyboardType.Text,
        placeholder = "e.g. actor names, release year, network",
        minHeight = 92.dp,
    )

    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

    Spacer(Modifier.height(22.dp))
    PrimaryButton(
        label = "Submit request",
        enabled = title.isNotBlank() && !submitting,
        loading = submitting,
        onClick = onSubmit,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextOnlyButton("Change contact info", onChangeContact)
        TextOnlyButton("Cancel", onCancel)
    }
}


// ─── Success phase ──────────────────────────────────────────────────

@Composable
private fun SuccessPhase(
    title: String,
    onClose: () -> Unit,
    onViewMyRequests: (() -> Unit)?,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("✅", fontSize = 56.sp)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Request submitted!",
        color = TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "We'll add \"$title\" as soon as we can. " +
            "You'll see status updates in My Requests.",
        color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp,
    )

    Spacer(Modifier.height(22.dp))
    if (onViewMyRequests != null) {
        PrimaryButton(label = "View my requests", enabled = true, loading = false) {
            onViewMyRequests()
        }
        Spacer(Modifier.height(8.dp))
    }
    SecondaryButton(label = "Close", onClick = onClose)
}


// ─── Reusable bits ─────────────────────────────────────────────────

@Composable
private fun TypeRadio(
    emoji: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(
                if (selected) Cyan.copy(alpha = 0.16f) else SurfaceElev,
                RoundedCornerShape(14.dp),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Cyan else Color(0x33FFFFFF),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) Cyan else TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ScopeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) Cyan.copy(alpha = 0.16f) else SurfaceElev,
                RoundedCornerShape(12.dp),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Cyan else Color(0x33FFFFFF),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(Color.Transparent, RoundedCornerShape(50))
                .border(
                    2.dp, if (selected) Cyan else Color(0x66FFFFFF),
                    RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).background(Cyan, RoundedCornerShape(50)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    keyboardType: KeyboardType,
    placeholder: String? = null,
    minHeight: androidx.compose.ui.unit.Dp = 56.dp,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(SurfaceElev, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = minHeight <= 56.dp,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(Cyan),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, color = Color(0xFF64748B), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                if (enabled) Cyan else Cyan.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.Black, strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(label, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black,
                letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(SurfaceElev, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextOnlyButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(label, color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
