package com.hushtv.tv.ai

import android.content.Context
import android.util.Log
import com.hushtv.tv.ai.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * AI captions engine backed by on-device **Whisper Base** (≈290 MB
 * ggml model, downloaded lazily on first enable).
 *
 * Differences vs. the old Vosk engine it replaces:
 *   • Works with ANY source-language audio → always outputs English
 *     (we run Whisper's `translate` task with `language = "auto"`).
 *   • Model is NOT bundled in the APK. User opts in via the AI-caption
 *     toggle, which kicks off [WhisperModelManager.download].
 *   • Whisper is batch-oriented — captions arrive every ~3 s once the
 *     current chunk finishes decoding (vs. Vosk's streaming
 *     low-latency output). The UX expectation shifts accordingly.
 *
 * Public surface preserved from Vosk: [state], [text], [prepare],
 * [start], [onPcmFrame], [stop]. So all existing player-screen code
 * keeps working as a drop-in.
 */
object WhisperCaptionEngine {

    private const val TAG = "WhisperCaption"

    /** Chunk size (s) we feed to Whisper. 3 s is the sweet spot in the
     *  upstream example: long enough to contain a full phrase, short
     *  enough that perceived caption latency is bearable. */
    private const val CHUNK_SECONDS = 3
    private const val SAMPLES_PER_CHUNK = 16_000 * CHUNK_SECONDS

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    enum class EngineState {
        IDLE,        // never touched
        PREPARING,   // loading model into memory
        READY,       // model resident, not currently transcribing
        RUNNING,     // transcription loop alive
        ERROR,       // last operation failed
    }

    private var whisper: WhisperContext? = null
    private var scope: CoroutineScope? = null
    private var feedJob: Job? = null
    private var channel: Channel<ShortArray>? = null

    @Volatile private var sourceRate: Int = 0
    @Volatile private var sourceChannels: Int = 0

    /** Idempotent model load. Returns fast after first call.
     *  Fails softly — caller should check [state] afterward. */
    suspend fun prepare(ctx: Context) {
        if (whisper != null) { _state.value = EngineState.READY; return }
        val file = WhisperModelManager.modelFile(ctx)
        if (!file.exists() || file.length() < 1_000_000L) {
            // Model not downloaded yet. Stay IDLE so the player UI can
            // continue pointing the user at the download flow.
            _state.value = EngineState.IDLE
            return
        }
        _state.value = EngineState.PREPARING
        try {
            whisper = withContext(Dispatchers.IO) {
                WhisperContext.fromFile(file.absolutePath)
            }
            _state.value = EngineState.READY
            Log.i(TAG, "Whisper ready from ${file.absolutePath}")
        } catch (t: Throwable) {
            Log.e(TAG, "Whisper init failed", t)
            whisper = null
            _state.value = EngineState.ERROR
        }
    }

    fun start(parentScope: CoroutineScope, sampleRate: Int, channels: Int) {
        val w = whisper
        if (w == null) {
            Log.w(TAG, "start() before prepare()")
            return
        }
        stop()
        val localScope = CoroutineScope(
            SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Default,
        )
        scope = localScope
        sourceRate = sampleRate
        sourceChannels = channels
        _text.value = ""

        val ch = Channel<ShortArray>(capacity = 128)
        channel = ch
        _state.value = EngineState.RUNNING

        feedJob = localScope.launch {
            // Running buffer collecting audio until we hit CHUNK_SECONDS.
            val buf = ShortArray(SAMPLES_PER_CHUNK)
            var fill = 0
            try {
                for (frame in ch) {
                    var srcOff = 0
                    while (srcOff < frame.size) {
                        val space = SAMPLES_PER_CHUNK - fill
                        val take = minOf(space, frame.size - srcOff)
                        System.arraycopy(frame, srcOff, buf, fill, take)
                        fill += take
                        srcOff += take
                        if (fill >= SAMPLES_PER_CHUNK) {
                            // Convert to float32 in [-1, 1] — Whisper's
                            // native input format.
                            val floats = FloatArray(SAMPLES_PER_CHUNK)
                            for (i in 0 until SAMPLES_PER_CHUNK) {
                                floats[i] = buf[i] / 32768.0f
                            }
                            fill = 0
                            // Guard each chunk with a timeout so a
                            // stuck inference can never freeze the
                            // feed loop for the whole session.
                            val text = withTimeoutOrNull(30_000) {
                                runCatching { w.transcribe(floats) }
                                    .getOrElse {
                                        Log.w(TAG, "transcribe threw", it)
                                        ""
                                    }
                            } ?: ""
                            if (text.isNotBlank()) _text.value = text
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Caption feed crashed", t)
                }
            }
        }
    }

    /** Called from the audio processor thread — must NOT block. */
    fun onPcmFrame(pcm16le: ByteArray, length: Int) {
        val rate = sourceRate
        val ch = sourceChannels
        if (rate <= 0 || ch <= 0) return
        val resampled = PcmTapAudioProcessor.toMono16k(pcm16le, length, ch, rate)
        if (resampled.isEmpty()) return
        val target = channel ?: return
        target.trySend(resampled)   // drop on back-pressure
    }

    fun stop() {
        _state.value = if (whisper != null) EngineState.READY else EngineState.IDLE
        channel?.close()
        channel = null
        feedJob?.cancel()
        feedJob = null
        scope?.cancel()
        scope = null
        sourceRate = 0
        sourceChannels = 0
        _text.value = ""
    }

    /** Release the native Whisper context. Called e.g. when the user
     *  disables AI captions permanently or logs out. Normally we leave
     *  the model resident between sessions for fast re-enable. */
    suspend fun release() {
        stop()
        whisper?.release()
        whisper = null
        _state.value = EngineState.IDLE
    }
}
