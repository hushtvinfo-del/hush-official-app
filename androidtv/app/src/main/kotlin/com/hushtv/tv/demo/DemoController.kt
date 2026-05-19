package com.hushtv.tv.demo

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.hushtv.tv.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual screen recorder controller (v1.44.92).
 *
 * Singleton state for the in-app demo screen recorder. Plain manual
 * start / stop — no scripted tour, no UI hijacking. The actual
 * recording is done by [ScreenRecordingService].
 *
 * Lifecycle:
 *   Idle → (user taps Start in Settings)
 *         → permission dialog → activity result fires
 *         → [startRecording] → service starts → phase=Preparing
 *         → encoders running → phase=Recording
 *   Recording → (user taps Stop in Settings OR notification action)
 *         → [stopRecording] → phase=Stopping
 *         → MP4 finalised → optional upload → phase=Idle
 *         → [outputPath] + [uploadStatus] updated for the next
 *           Settings card render.
 */
object DemoController {

    enum class Phase { Idle, Preparing, Recording, Stopping }

    // ── Reactive state ────────────────────────────────────────────
    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _outputPath = MutableStateFlow<String?>(null)
    /** Last successful local MP4 path. */
    val outputPath: StateFlow<String?> = _outputPath.asStateFlow()

    private val _uploadStatus = MutableStateFlow<String?>(null)
    /** Optional human-readable upload status: "Uploading…", "Uploaded id=ab12", "Upload failed: …". */
    val uploadStatus: StateFlow<String?> = _uploadStatus.asStateFlow()

    /**
     * Kick off recording. The caller must already have a valid
     * MediaProjection token (from the Activity Result API).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startRecording(ctx: Context, resultCode: Int, data: Intent) {
        if (_phase.value != Phase.Idle) return
        _phase.value = Phase.Preparing
        _outputPath.value = null
        _uploadStatus.value = null
        val intent = Intent(ctx, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START
            putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    /** Stop the in-flight recording. Safe to call when idle. */
    fun stopRecording(ctx: Context?) {
        if (ctx == null) return
        if (_phase.value == Phase.Idle) return
        _phase.value = Phase.Stopping
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
        _outputPath.value = localFile
        if (error != null && _uploadStatus.value == null) {
            _uploadStatus.value = "Recording error: $error"
        }
    }

    internal fun setUploadStatus(s: String?) { _uploadStatus.value = s }

    /** True only when the dev flavor has wired in a demo upload URL/token. */
    val isUploadConfigured: Boolean
        get() = BuildConfig.DEMO_UPLOAD_URL.isNotBlank() &&
            BuildConfig.DEMO_UPLOAD_TOKEN.isNotBlank()
}
