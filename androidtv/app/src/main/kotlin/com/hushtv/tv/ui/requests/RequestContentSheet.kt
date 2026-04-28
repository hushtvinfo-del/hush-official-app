package com.hushtv.tv.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.data.ContentRequestApi
import com.hushtv.tv.data.RequestMetaStore
import com.hushtv.tv.data.UserContactStore
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.SurfaceElev
import com.hushtv.tv.ui.theme.SurfaceNavy
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modal for submitting a "Request Missing Content" entry to the
 * HushTV admin API.
 *
 * Wrapped in `Dialog(usePlatformDefaultWidth = false)` so the modal
 * gets its own focus root and D-pad input can't escape to the
 * background screen. Every interactive surface uses
 * [clickableWithEnter] + [focusable] so TV remotes can navigate the
 * form natively. Touch on mobile still works the same way.
 *
 * Three internal screens, swapped by [Phase]:
 *   • CONTACT  — first-time prompt for name + email; skipped on
 *                subsequent uses thanks to [UserContactStore].
 *   • FORM     — type (movie/series), pre-filled title, optional
 *                series details, optional notes.
 *   • SUCCESS  — confirmation with "View My Requests" link.
 *
 * Errors are rendered inline (never in a nested alert dialog).
 *
 * The dialog accepts [presetType] / [presetTitle] / [presetSeason] /
 * [presetEpisode] so callers can pre-fill from the screen the user
 * came from (search query, missing episode, etc.).
 *
 * [onViewMyRequests] navigates to the My Requests screen on success.
 * Caller can pass null on screens that don't have nav set up — the
 * link just won't render.
 */
@Composable
fun RequestContentSheet(
    presetType: String = "movie",
    presetTitle: String = "",
    presetSeason: String = "",
    presetEpisode: String = "",
    playlistId: String = "",
    onDismiss: () -> Unit,
    onViewMyRequests: (() -> Unit)? = null,
    onAlreadyAvailable: ((LibraryEntry) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember {
        mutableStateOf(
            if (UserContactStore.get(ctx) == null) Phase.CONTACT else Phase.PICK
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
    // The TMDB pick the user committed to. Null when they haven't
    // chosen a TMDB candidate (or chose to free-text submit).
    var pickedTmdb by remember { mutableStateOf<TmdbPick?>(null) }
    var freeTextTitle by remember { mutableStateOf<String?>(null) }
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

    /**
     * Build the API submit call. Used by both the immediate-submit
     * path (movies) and the explicit-submit path (series, where the
     * user might pick scope + episodes first).
     */
    fun doSubmit(
        finalType: String,
        finalTitle: String,
        finalTmdb: TmdbPick?,
    ) {
        if (finalTitle.isBlank()) {
            submitError = "Please pick or type a title."
            return
        }
        if (submitting) return  // guard against double-tap that fires
                                 // while the submit POST is still
                                 // in flight — without this a quick
                                 // double-press shoots two requests
                                 // and on slow networks the second
                                 // landed fast enough to cause UI
                                 // confusion.
        submitting = true
        submitError = null
        scope.launch {
            // Outer runCatching so an unexpected throw (Moshi
            // serialisation, OOM in tmdb meta build, anything else)
            // surfaces as an inline error instead of killing the
            // app from the launch dispatcher's unhandled-exception
            // path.
            runCatching {
                val tmdbMeta = finalTmdb?.let {
                    RequestMetaStore.Meta(
                        tmdbId = it.tmdbId,
                        tmdbType = it.tmdbType,
                        posterPath = it.posterPath,
                        backdropPath = it.backdropPath,
                        releaseYear = it.year,
                        title = it.title,
                        overview = it.overview,
                        imdbId = null,
                    )
                }
                val res = withContext(Dispatchers.IO) {
                    ContentRequestApi.submitRequest(
                        ctx = ctx,
                        type = finalType,
                        title = finalTitle,
                        additionalInfo = notes.trim().ifBlank { null },
                        seriesRequestType = if (finalType == "series") seriesScope else null,
                        seasons = if (finalType == "series" && seriesScope == "specific_episodes")
                            seasons.trim().ifBlank { null } else null,
                        episodes = if (finalType == "series" && seriesScope == "specific_episodes")
                            episodes.trim().ifBlank { null } else null,
                        tmdbMeta = tmdbMeta,
                    )
                }
                when (res) {
                    is ContentRequestApi.SubmitResult.Success -> {
                        if (tmdbMeta != null) {
                            // SharedPreferences write is async via
                            // apply(), but route through IO anyway
                            // for parity with all our other disk
                            // writes.
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    RequestMetaStore.put(ctx, res.requestId, tmdbMeta)
                                }
                            }
                        }
                        lastResult = res
                        phase = Phase.SUCCESS
                    }
                    is ContentRequestApi.SubmitResult.Error ->
                        submitError = res.message
                }
            }.onFailure { e ->
                submitError = e.message ?: "Submit failed unexpectedly."
            }
            submitting = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF205080F)),
        ) {
            // Full-screen content frame with generous TV safe-area
            // margins — fills the whole canvas instead of being a tiny
            // 600 dp dialog floating in the middle. All phases get the
            // entire ~1820 dp width, so the TMDB picker can lay out
            // its search field on the left and a scrolling result
            // grid on the right with no visual cramping.
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 56.dp, vertical = 36.dp),
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
                                    phase = Phase.PICK
                                }
                            }
                        },
                        onCancel = onDismiss,
                    )
                    Phase.PICK -> TmdbPickerPhase(
                        type = type,
                        presetQuery = presetTitle,
                        playlistId = playlistId,
                        onCancel = onDismiss,
                        onChangeType = { type = it },
                        onPicked = { pick ->
                            pickedTmdb = pick
                            freeTextTitle = null
                            // Movies — submit immediately. Notes /
                            // priority can be edited later from the
                            // detail page or by re-requesting.
                            // Series — show the new SERIES_DETAIL
                            // landing page so the user can choose
                            // Tap-to-Watch (if in library), Request
                            // Whole Series, or Request Missing
                            // Episodes.
                            if (type == "series") {
                                phase = Phase.SERIES_DETAIL
                            } else {
                                doSubmit(
                                    finalType = "movie",
                                    finalTitle = pick.title,
                                    finalTmdb = pick,
                                )
                            }
                        },
                        onAlreadyAvailable = { entry ->
                            // Map LibraryIndex.Entry → exposed
                            // LibraryEntry value class so callers
                            // don't need to import data layer.
                            onAlreadyAvailable?.invoke(
                                LibraryEntry(
                                    kind = entry.kind,
                                    streamId = entry.streamId,
                                    seriesId = entry.seriesId,
                                    title = entry.title,
                                    poster = entry.poster,
                                ),
                            )
                            onDismiss()
                        },
                        onFreeTextSubmit = { typed ->
                            pickedTmdb = null
                            freeTextTitle = typed
                            if (type == "series") {
                                // No TMDB id → no episode picker
                                // possible. Drop straight into the
                                // legacy DETAILS phase so the user
                                // can hand-type seasons/episodes.
                                phase = Phase.DETAILS
                            } else {
                                doSubmit(
                                    finalType = "movie",
                                    finalTitle = typed,
                                    finalTmdb = null,
                                )
                            }
                        },
                    )
                    Phase.SERIES_DETAIL -> SeriesDetailPhase(
                        pick = pickedTmdb ?: TmdbPick(
                            tmdbId = 0, tmdbType = "tv",
                            title = freeTextTitle ?: "",
                            year = null, posterPath = null,
                            backdropPath = null, overview = null,
                            library = null,
                        ),
                        onBack = { phase = Phase.PICK },
                        onTapToWatch = {
                            val entry = pickedTmdb?.library ?: return@SeriesDetailPhase
                            onAlreadyAvailable?.invoke(
                                LibraryEntry(
                                    kind = entry.kind,
                                    streamId = entry.streamId,
                                    seriesId = entry.seriesId,
                                    title = entry.title,
                                    poster = entry.poster,
                                ),
                            )
                            onDismiss()
                        },
                        onRequestWholeSeries = {
                            seriesScope = "entire_series"
                            seasons = ""
                            episodes = ""
                            doSubmit(
                                finalType = "series",
                                finalTitle = pickedTmdb?.title
                                    ?: freeTextTitle ?: "",
                                finalTmdb = pickedTmdb,
                            )
                        },
                        onRequestMissingEpisodes = {
                            seriesScope = "specific_episodes"
                            phase = Phase.MULTI_EPISODE_PICKER
                        },
                    )
                    Phase.MULTI_EPISODE_PICKER -> MultiEpisodePickerPhase(
                        pick = pickedTmdb ?: TmdbPick(
                            tmdbId = 0, tmdbType = "tv",
                            title = freeTextTitle ?: "",
                            year = null, posterPath = null,
                            backdropPath = null, overview = null,
                            library = null,
                        ),
                        playlistId = playlistId,
                        submitting = submitting,
                        onBack = { phase = Phase.SERIES_DETAIL },
                        onSubmit = { season, episodesLabel ->
                            seriesScope = "specific_episodes"
                            seasons = season.toString()
                            episodes = episodesLabel
                            doSubmit(
                                finalType = "series",
                                finalTitle = pickedTmdb?.title
                                    ?: freeTextTitle ?: "",
                                finalTmdb = pickedTmdb,
                            )
                        },
                        // In-library episodes are not requestable.
                        // Tapping one routes the user into the
                        // library entry the same way the Tap-to-Watch
                        // CTA on Page 2 does.
                        onTapInLibraryEpisode = {
                            val entry = pickedTmdb?.library
                                ?: return@MultiEpisodePickerPhase
                            onAlreadyAvailable?.invoke(
                                LibraryEntry(
                                    kind = entry.kind,
                                    streamId = entry.streamId,
                                    seriesId = entry.seriesId,
                                    title = entry.title,
                                    poster = entry.poster,
                                ),
                            )
                            onDismiss()
                        },
                    )
                    Phase.DETAILS -> DetailsPhase(
                        chosenTitle = pickedTmdb?.title ?: freeTextTitle ?: "",
                        chosenYear = pickedTmdb?.year,
                        chosenPosterPath = pickedTmdb?.posterPath,
                        seriesScope = seriesScope,
                        onSeriesScope = { seriesScope = it },
                        seasons = seasons, onSeasons = { seasons = it },
                        episodes = episodes, onEpisodes = { episodes = it },
                        notes = notes, onNotes = { notes = it },
                        submitting = submitting,
                        error = submitError,
                        onBack = { phase = Phase.PICK },
                        onCancel = onDismiss,
                        onSubmit = {
                            doSubmit(
                                finalType = "series",
                                finalTitle = pickedTmdb?.title ?: freeTextTitle ?: "",
                                finalTmdb = pickedTmdb,
                            )
                        },
                    )
                    Phase.SUCCESS -> SuccessPhase(
                        title = pickedTmdb?.title ?: freeTextTitle ?: "your title",
                        posterPath = pickedTmdb?.posterPath,
                        onClose = onDismiss,
                        onViewMyRequests = onViewMyRequests,
                    )
                }
            }
        }
    }
}

private enum class Phase { CONTACT, PICK, SERIES_DETAIL, MULTI_EPISODE_PICKER, DETAILS, SUCCESS }

/**
 * Public, slim copy of [com.hushtv.tv.data.LibraryIndex.Entry] so
 * call sites of [RequestContentSheet] don't need to import the data
 * layer. Used in the [RequestContentSheet.onAlreadyAvailable]
 * callback so the screen owning the modal can navigate to the right
 * library route (TV vs. Mobile have different routes).
 */
data class LibraryEntry(
    val kind: String,
    val streamId: Int,
    val seriesId: Int,
    val title: String,
    val poster: String?,
)


// ─── Contact phase ──────────────────────────────────────────────────

@Composable
private fun ContactPhase(
    name: String, email: String, error: String?,
    onName: (String) -> Unit, onEmail: (String) -> Unit,
    onContinue: () -> Unit, onCancel: () -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { nameFocus.requestFocus() }
    }

    Text("First, who are you?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
        "We'll use this to update you when your request is added. " +
            "We only ask once.",
        color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp,
    )
    Spacer(Modifier.height(20.dp))
    LabeledField("Your name", name, onName, KeyboardType.Text, focusRequester = nameFocus)
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


// ─── Details phase (series only — pick scope, optional notes) ──────

@Composable
private fun DetailsPhase(
    chosenTitle: String,
    chosenYear: Int?,
    chosenPosterPath: String?,
    seriesScope: String, onSeriesScope: (String) -> Unit,
    seasons: String, onSeasons: (String) -> Unit,
    episodes: String, onEpisodes: (String) -> Unit,
    notes: String, onNotes: (String) -> Unit,
    submitting: Boolean, error: String?,
    onBack: () -> Unit, onCancel: () -> Unit, onSubmit: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { firstFocus.requestFocus() }
    }

    Text(
        "Request a series",
        color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "What's missing from \"$chosenTitle\"${chosenYear?.let { " ($it)" } ?: ""}?",
        color = TextSecondary, fontSize = 13.sp, lineHeight = 17.sp,
    )

    if (chosenPosterPath != null) {
        Spacer(Modifier.height(14.dp))
        ChosenTitleCard(
            posterPath = chosenPosterPath,
            title = chosenTitle,
            year = chosenYear,
        )
    }

    Spacer(Modifier.height(18.dp))
    Text("WHAT'S MISSING?", color = TextSecondary, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.focusRequester(firstFocus)) {
        ScopeRow("Entire series", seriesScope == "entire_series") {
            onSeriesScope("entire_series")
        }
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

    Spacer(Modifier.height(18.dp))
    LabeledField(
        label = "Additional info (optional)",
        value = notes,
        onValue = onNotes,
        keyboardType = KeyboardType.Text,
        placeholder = "e.g. actor names, release year, network",
        minHeight = 84.dp,
    )

    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

    Spacer(Modifier.height(20.dp))
    PrimaryButton(
        label = "Submit request",
        enabled = !submitting,
        loading = submitting,
        onClick = onSubmit,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextOnlyButton("Back", onBack)
        TextOnlyButton("Cancel", onCancel)
    }
}

@Composable
private fun ChosenTitleCard(
    posterPath: String,
    title: String,
    year: Int?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceElev, RoundedCornerShape(12.dp))
            .border(1.dp, com.hushtv.tv.ui.theme.Cyan.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(48.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center,
        ) {
            val url = com.hushtv.tv.data.TmdbService.img(posterPath, "w154")
            if (!url.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, color = TextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (year != null) {
                Spacer(Modifier.height(2.dp))
                Text(year.toString(), color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}


// ─── Success phase ──────────────────────────────────────────────────

@Composable
private fun SuccessPhase(
    title: String,
    posterPath: String?,
    onClose: () -> Unit,
    onViewMyRequests: (() -> Unit)?,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (posterPath != null) {
            // Show the picked poster — way more visceral than just
            // a checkmark emoji and reassures the user we got the
            // right title.
            Box(
                Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center,
            ) {
                val url = com.hushtv.tv.data.TmdbService.img(posterPath, "w342")
                if (!url.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // ✅ overlay so the user instantly reads "submitted"
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x6622C55E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✅", fontSize = 48.sp)
                }
            }
        } else {
            Text("✅", fontSize = 56.sp)
        }
    }
    Spacer(Modifier.height(14.dp))
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
        PrimaryButton(
            label = "View my requests",
            enabled = true, loading = false,
            modifier = Modifier.focusRequester(firstFocus),
        ) { onViewMyRequests() }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(label = "Close", onClick = onClose)
    } else {
        SecondaryButton(
            label = "Close",
            onClick = onClose,
            modifier = Modifier.focusRequester(firstFocus),
        )
    }
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
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .background(
                if (selected) Cyan.copy(alpha = 0.16f) else SurfaceElev,
                RoundedCornerShape(14.dp),
            )
            .border(
                if (focused || selected) 2.dp else 1.dp,
                if (focused) Cyan else if (selected) Cyan.copy(alpha = 0.7f) else Color(0x33FFFFFF),
                RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected || focused) Cyan else TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ScopeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) Cyan.copy(alpha = 0.16f) else SurfaceElev,
                RoundedCornerShape(12.dp),
            )
            .border(
                if (focused || selected) 2.dp else 1.dp,
                if (focused) Cyan else if (selected) Cyan.copy(alpha = 0.7f) else Color(0x33FFFFFF),
                RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
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
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(SurfaceElev, RoundedCornerShape(10.dp))
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) Cyan else Color(0x33FFFFFF),
                    RoundedCornerShape(10.dp),
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                    .onFocusChanged { focused = it.isFocused },
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                if (enabled) (if (focused) Color.White else Cyan)
                else Cyan.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp),
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Cyan else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .clickableWithEnter { if (enabled) onClick() },
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
private fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                if (focused) Cyan.copy(alpha = 0.18f) else SurfaceElev,
                RoundedCornerShape(12.dp),
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Cyan else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) Cyan else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TextOnlyButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .background(
                if (focused) Cyan.copy(alpha = 0.16f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
    ) {
        Text(label, color = Cyan, fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Black else FontWeight.SemiBold)
    }
}
