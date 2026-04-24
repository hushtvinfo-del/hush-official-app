package com.hushtv.tv.ai

import android.content.Context
import android.util.Log
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
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * AI English live-captions engine backed by an on-device Vosk model.
 *
 * Usage lifecycle:
 *   1. [prepare] — unpacks the bundled small-en model to private
 *      storage on first run (a ~5 s operation, idempotent afterwards).
 *   2. [start] — allocates a Vosk Recognizer at the source audio rate.
 *   3. [onPcmFrame] — called by [PcmTapAudioProcessor] for every
 *      decoded PCM buffer. Frames are dropped to a bounded Channel so
 *      the audio thread is never blocked by the STT thread.
 *   4. [stop] — frees the recognizer (model stays cached).
 *
 * Results are published to [text] as a single live StateFlow. Partial
 * results (still-being-spoken words) overwrite the latest final result
 * so the overlay shows a continuous stream without duplicates.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object VoskCaptionEngine {

    private const val TAG = "VoskCaptionEngine"
    private const val MODEL_ASSET_NAME = "vosk-model-small-en-us-0.15"

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    enum class EngineState { IDLE, PREPARING, READY, RUNNING, ERROR }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var scope: CoroutineScope? = null
    private var feedJob: Job? = null
    private var channel: Channel<ShortArray>? = null

    @Volatile private var sourceRate: Int = 0
    @Volatile private var sourceChannels: Int = 0

    init {
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
    }

    /** Must be called once per process — no-op if already prepared. */
    suspend fun prepare(ctx: Context) {
        if (model != null) { _state.value = EngineState.READY; return }
        _state.value = EngineState.PREPARING
        try {
            val m = unpackModel(ctx)
            model = m
            _state.value = EngineState.READY
            Log.i(TAG, "Vosk model ready at ${m}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to prepare Vosk model", t)
            _state.value = EngineState.ERROR
        }
    }

    /**
     * StorageService uses the UUID + files.list inside the bundled
     * model folder to skip re-unpacking when the app is already set up.
     * We suspend the caller on the unpack callback.
     */
    private suspend fun unpackModel(ctx: Context): Model = suspendCoroutine { cont ->
        StorageService.unpack(
            ctx,
            MODEL_ASSET_NAME,
            "model",
            { model -> cont.resume(model) },
            { e -> cont.resumeWithException(e) },
        )
    }

    /** Begin feeding PCM. Idempotent — safe to call twice. */
    fun start(parentScope: CoroutineScope, sampleRate: Int, channels: Int) {
        if (model == null) {
            Log.w(TAG, "start() called before prepare() succeeded")
            return
        }
        stop()    // reset from any prior session
        val localScope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Default)
        scope = localScope
        sourceRate = sampleRate
        sourceChannels = channels
        _text.value = ""

        // We always run Vosk at 16 kHz. The processor downsamples
        // before enqueue so the recognizer buffer stays small (~320
        // samples per 20 ms frame).
        val rec = runCatching { Recognizer(model, 16000f) }.getOrElse {
            Log.e(TAG, "Recognizer init failed", it)
            _state.value = EngineState.ERROR
            return
        }
        runCatching { rec.setPartialWords(true); rec.setWords(false) }
        recognizer = rec

        val ch = Channel<ShortArray>(capacity = 32)
        channel = ch
        _state.value = EngineState.RUNNING

        feedJob = localScope.launch {
            try {
                for (frame in ch) {
                    val bytes = ByteArray(frame.size * 2)
                    // Convert ShortArray → little-endian byte array.
                    var o = 0
                    for (s in frame) {
                        bytes[o++] = (s.toInt() and 0xFF).toByte()
                        bytes[o++] = ((s.toInt() shr 8) and 0xFF).toByte()
                    }
                    val finalised = withContext(Dispatchers.Default) {
                        rec.acceptWaveForm(bytes, bytes.size)
                    }
                    if (finalised) {
                        val json = rec.result ?: ""
                        val finalText = runCatching { JSONObject(json).optString("text", "") }.getOrDefault("")
                        if (finalText.isNotBlank()) _text.value = finalText
                    } else {
                        val json = rec.partialResult ?: ""
                        val partial = runCatching { JSONObject(json).optString("partial", "") }.getOrDefault("")
                        if (partial.isNotBlank()) _text.value = partial
                    }
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Caption feed crashed", t)
                }
            }
        }
    }

    /**
     * Called by the audio processor on the audio thread — must return
     * instantly. We downmix + resample inline (cheap) and drop frames
     * into the bounded channel; if the recognizer is behind, frames
     * are coalesced (older frames stale anyway — captions lag is
     * the lesser evil vs. blocking the audio path).
     */
    fun onPcmFrame(pcm16le: ByteArray, length: Int) {
        val rate = sourceRate
        val ch = sourceChannels
        if (rate <= 0 || ch <= 0) return
        val resampled = PcmTapAudioProcessor.toMono16k(pcm16le, length, ch, rate)
        if (resampled.isEmpty()) return
        val target = channel ?: return
        // trySend drops the frame if the channel is full — we'd rather
        // lose a couple of seconds of captions than stall the audio sink.
        target.trySend(resampled)
    }

    fun stop() {
        _state.value = EngineState.READY
        channel?.close()
        channel = null
        feedJob?.cancel()
        feedJob = null
        recognizer?.close()
        recognizer = null
        scope?.cancel()
        scope = null
        sourceRate = 0
        sourceChannels = 0
        _text.value = ""
    }
}
