package com.hushtv.tv.demo

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.hushtv.tv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Auto-pilot Demo Recorder — Phase 1.
 *
 * Singleton state controller for the in-app demo screen recorder. Owns
 *   1) Reactive state read by the home screen (which page to slide to)
 *      and the overlay (current step caption + REC badge).
 *   2) The script coroutine that drives the home tour after the
 *      [ScreenRecordingService] has confirmed it's recording.
 *
 * The recording itself is performed by [ScreenRecordingService] using
 * Android's MediaProjection + MediaRecorder. We never touch the screen
 * here — the home tour just mutates [scriptedPage] which the home
 * screen observes via a LaunchedEffect.
 */
object DemoController {

    /** What the demo is currently doing. */
    enum class Phase { Idle, Preparing, Recording, Stopping }

    /** Tour step that maps to a TVMainMenuScreen `currentPage` value. */
    data class Step(
        val page: String,
        val caption: String,
        val holdMs: Long,
    )

    /** The script: ~90 s home walkthrough.
     *
     * Page values MUST match the keys in TVMainMenuScreen.pageOrder.
     * If a key isn't in pageOrder for the current install, the home
     * screen's defensive LaunchedEffect resets to the first available
     * page — that's fine, the script still proceeds.
     */
    val SCRIPT: List<Step> = listOf(
        Step("discovery",     "Discovery — what's new tonight",              10_000),
        Step("ss_movies",     "Streaming Services — Movies",                 10_000),
        Step("ss_series",     "Streaming Services — Series",                  9_000),
        Step("collections",   "Movie Collections — including Star Wars",     14_000),
        Step("genres_movies", "Browse by Genre — Movies",                     9_000),
        Step("genres_series", "Browse by Genre — Series",                     8_000),
        Step("themed",        "Themes & Moods",                              10_000),
        Step("years_movies",  "Browse by Decade",                            10_000),
    )

    val TOTAL_DURATION_MS: Long = SCRIPT.sumOf { it.holdMs } + 4_000  // intro + outro pad

    // ── Reactive state ────────────────────────────────────────────
    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _scriptedPage = MutableStateFlow<String?>(null)
    /** Non-null whenever the demo wants the home screen on a specific page. */
    val scriptedPage: StateFlow<String?> = _scriptedPage.asStateFlow()

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()

    private val _outputPath = MutableStateFlow<String?>(null)
    /** Last successful local file path. Used by the dialog's "last clip" display. */
    val outputPath: StateFlow<String?> = _outputPath.asStateFlow()

    private val _uploadStatus = MutableStateFlow<String?>(null)
    /** Optional human-readable status: "Uploading 12.3 MB…", "Uploaded id=ab12", "Upload failed: …". */
    val uploadStatus: StateFlow<String?> = _uploadStatus.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scriptJob: Job? = null

    /**
     * Called by the home screen once it's confirmed mounted. Starts the
     * scripted tour. Only effective when [phase] is [Phase.Recording].
     */
    fun beginScriptedTour() {
        if (_phase.value != Phase.Recording) return
        if (scriptJob?.isActive == true) return
        scriptJob = scope.launch {
            // Brief intro pad — let the user (or the recorder warm-up) see
            // the home screen as it normally loads.
            _caption.value = "HushTV — auto-pilot demo"
            delay(2_500)
            SCRIPT.forEach { step ->
                _scriptedPage.value = step.page
                _caption.value = step.caption
                delay(step.holdMs)
            }
            // Outro pad before we tell the service to stop.
            _caption.value = "Wrapping up…"
            delay(1_500)
            requestStopFromScript()
        }
    }

    private fun requestStopFromScript() {
        // The service is the source of truth for stop — we proxy through it
        // so the MP4 is finalised cleanly before status flips.
        scope.launch { stopRecording(ScreenRecordingService.appContextOrNull()) }
    }

    /**
     * Kick off recording. The caller must already have a valid
     * MediaProjection token (from [android.app.Activity.startActivityForResult]).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startRecording(ctx: Context, resultCode: Int, data: Intent) {
        if (_phase.value != Phase.Idle) return
        _phase.value = Phase.Preparing
        _caption.value = "Preparing recorder…"
        _scriptedPage.value = null
        _outputPath.value = null
        _uploadStatus.value = null
        val intent = Intent(ctx, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START
            putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
        }
        // Must be foreground service — MediaProjection requires it on Android 10+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun stopRecording(ctx: Context?) {
        if (ctx == null) return
        if (_phase.value == Phase.Idle) return
        _phase.value = Phase.Stopping
        scriptJob?.cancel(); scriptJob = null
        val intent = Intent(ctx, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        }
        ctx.startService(intent)
    }

    /** Called by the service when MediaRecorder has actually started. */
    internal fun onServiceRecordingStarted() {
        _phase.value = Phase.Recording
    }

    /** Called by the service after MP4 is finalised + (optional) upload. */
    internal fun onServiceStopped(localFile: String?, error: String?) {
        _phase.value = Phase.Idle
        _scriptedPage.value = null
        _outputPath.value = localFile
        if (error != null) {
            _caption.value = "Recording error: $error"
        } else if (localFile != null) {
            _caption.value = "Saved: ${localFile.substringAfterLast('/')}"
        } else {
            _caption.value = ""
        }
    }

    internal fun setUploadStatus(s: String?) { _uploadStatus.value = s }

    /** True only when the dev flavor has wired in a demo upload URL/token. */
    val isUploadConfigured: Boolean
        get() = BuildConfig.DEMO_UPLOAD_URL.isNotBlank() &&
            BuildConfig.DEMO_UPLOAD_TOKEN.isNotBlank()
}
