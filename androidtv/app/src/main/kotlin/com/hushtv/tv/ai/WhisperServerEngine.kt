package com.hushtv.tv.ai

import android.content.Context
import android.util.Log
import com.hushtv.tv.BuildConfig
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AI captions engine backed by our **GPU-hosted Whisper Base** service
 * at `https://ai.hushtv.xyz/transcribe` (Tesla T4 + faster-whisper +
 * int8_float16). Replaces the old Vosk on-device engine — same public
 * surface so player screens stay unchanged:
 *
 *   • IDLE → READY (toggle on) → RUNNING (audio flowing) → READY → IDLE
 *   • [text] is the live English caption string. Empty string means
 *     "no speech detected yet" — the player overlay decides what to
 *     render in that case (placeholder, etc.).
 *
 * Architecture per playback session:
 *   PcmTapAudioProcessor.onPcm  →  toMono16k(...)  →  Channel<ShortArray>
 *                                                   ↓ (3-sec accumulator)
 *                                            POST raw PCM to /transcribe
 *                                                   ↓
 *                                                _text.value = response
 *
 * Source-language detection + English translation both happen on the
 * server. Client only ships PCM bytes.
 */
object WhisperServerEngine {

    private const val TAG = "WhisperSrv"
    const val ENDPOINT = "https://ai.hushtv.xyz/transcribe"
    private const val SHARED_SECRET =
        "-LIAWe9fAxf_mnKiWXOqZtbQ2c3Tjn-FZP0IWSSvFDw"

    /** 3 seconds of mono 16 kHz int16 = 96000 bytes per upload. Sweet
     *  spot between latency (lower = snappier) and translation
     *  quality (higher = more context).  Matches faster-whisper's
     *  behaviour where short chunks lose grammatical context. */
    private const val CHUNK_SECONDS = 3
    private const val SAMPLES_PER_CHUNK = 16_000 * CHUNK_SECONDS

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    enum class EngineState { IDLE, PREPARING, READY, RUNNING, ERROR }

    private var scope: CoroutineScope? = null
    private var feedJob: Job? = null
    private var channel: Channel<ShortArray>? = null

    @Volatile private var sourceRate: Int = 0
    @Volatile private var sourceChannels: Int = 0

    /** Kept for source compatibility with the old Vosk engine. The
     *  server-backed engine has nothing to "prepare" — it's stateless
     *  per-request — so this is a no-op that just flips state to
     *  READY. */
    suspend fun prepare(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        if (_state.value == EngineState.IDLE || _state.value == EngineState.ERROR) {
            _state.value = EngineState.READY
        }
    }

    fun start(parentScope: CoroutineScope, sampleRate: Int, channels: Int) {
        stop()
        val localScope = CoroutineScope(
            SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.IO,
        )
        scope = localScope
        sourceRate = sampleRate
        sourceChannels = channels
        _text.value = ""

        val ch = Channel<ShortArray>(capacity = 256)
        channel = ch
        _state.value = EngineState.RUNNING

        feedJob = localScope.launch {
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
                            // Snapshot + clear the accumulator BEFORE
                            // the network call so the next chunk can
                            // start filling concurrently.
                            val snapshot = buf.copyOf(SAMPLES_PER_CHUNK)
                            fill = 0
                            launch { dispatchChunk(snapshot) }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "feed crashed", t)
                }
            }
        }
    }

    /**
     * Transcribe a single 3-second chunk via the HTTPS API.
     * Updates [_text] on success. Silent on transport failures —
     * captions just stop refreshing rather than crash the UI.
     */
    private suspend fun dispatchChunk(samples: ShortArray) = withContext(Dispatchers.IO) {
        val body = ByteBuffer
            .allocate(samples.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .also { bb -> for (s in samples) bb.putShort(s) }
            .array()

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 25_000
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Authorization", "Bearer $SHARED_SECRET")
                setRequestProperty("User-Agent", "HushTV/${BuildConfig.VERSION_NAME}")
                setFixedLengthStreamingMode(body.size)
            }
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "server returned HTTP $code")
                return@withContext
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            // Tiny hand-rolled JSON parse — payload is always
            // {"text":"…","lang":"…","lang_prob":0.93,"ms":…}.
            val text = extractJsonString(resp, "text") ?: return@withContext
            if (text.isNotBlank()) {
                _text.value = text
            }
        } catch (e: IOException) {
            Log.w(TAG, "transcribe failed: ${e.message}")
        } catch (t: Throwable) {
            Log.w(TAG, "transcribe error", t)
        } finally {
            conn?.disconnect()
        }
    }

    /** Called from the audio processor thread — must NOT block. */
    fun onPcmFrame(pcm16le: ByteArray, length: Int) {
        val rate = sourceRate
        val ch = sourceChannels
        if (rate <= 0 || ch <= 0) return
        val resampled = PcmTapAudioProcessor.toMono16k(pcm16le, length, ch, rate)
        if (resampled.isEmpty()) return
        channel?.trySend(resampled)
    }

    fun stop() {
        _state.value = if (_state.value == EngineState.ERROR) EngineState.ERROR
            else EngineState.READY
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

    /**
     * Pull the value of [key] out of a flat JSON object like
     * `{"text":"hello","lang":"en"}`. Handles the basic `\n`, `\"`,
     * `\\` escapes Whisper emits — full RFC 8259 unescaping isn't
     * needed since the server clamps everything to UTF-8 strings
     * already.
     */
    private fun extractJsonString(json: String, key: String): String? {
        val needle = "\"$key\":\""
        val start = json.indexOf(needle)
        if (start < 0) return null
        val after = start + needle.length
        val out = StringBuilder()
        var i = after
        while (i < json.length) {
            val c = json[i]
            when {
                c == '\\' && i + 1 < json.length -> {
                    when (json[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        else -> out.append(json[i + 1])
                    }
                    i += 2
                }
                c == '"' -> return out.toString()
                else -> { out.append(c); i++ }
            }
        }
        return null
    }
}
