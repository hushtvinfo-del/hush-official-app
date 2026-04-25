package com.hushtv.tv.ai

import android.content.Context
import android.util.Log
import com.hushtv.tv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * AI captions engine backed by our **GPU-hosted Whisper Base** service
 * at `wss://ai.hushtv.xyz/ws` (Tesla T4 + faster-whisper +
 * int8_float16) using a streaming WebSocket. Replaces the old Vosk
 * on-device engine — same public surface so player screens stay
 * unchanged:
 *
 *   • IDLE → READY (toggle on) → RUNNING (audio flowing) → READY → IDLE
 *   • [text] is the live English caption string. Empty string means
 *     "no speech detected yet" — the player overlay decides what to
 *     render in that case (placeholder, etc.).
 *
 * Architecture per playback session:
 *   PcmTapAudioProcessor.onPcm  →  toMono16k(...)  →  WebSocket.send(bytes)
 *                                                   ↓
 *                                       Server holds 3-s sliding window,
 *                                       runs Whisper every ~1 s, pushes
 *                                       JSON {"text","lang","ms"} back.
 *                                                   ↓
 *                                            _text.value = response.text
 *
 * Source-language detection + English translation both happen on the
 * server. Client only ships PCM bytes.
 *
 * v1.32.1: switched from per-chunk HTTP POST to WebSocket streaming,
 * cutting perceived lag from ~3.2 s to ~1.2 s.
 */
object WhisperServerEngine {

    private const val TAG = "WhisperSrv"
    private const val WS_URL = "wss://ai.hushtv.xyz/ws"
    private const val SHARED_SECRET =
        "-LIAWe9fAxf_mnKiWXOqZtbQ2c3Tjn-FZP0IWSSvFDw"

    /** Send mini frames as soon as they're ready. Anything from ~50 ms
     *  to ~500 ms works; the server batches into a 3-s sliding window
     *  internally. */
    private const val SEND_FRAME_MS = 200
    private const val SEND_FRAME_BYTES = 16_000 * 2 * SEND_FRAME_MS / 1000

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    enum class EngineState { IDLE, PREPARING, READY, RUNNING, ERROR }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Long-lived ws — disable read timeout (default 10 s would kill it).
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var ws: WebSocket? = null
    @Volatile private var sourceRate: Int = 0
    @Volatile private var sourceChannels: Int = 0

    /** Accumulator that lets us send neat 200-ms frames over the wire
     *  even when the audio processor hands us oddly-sized buffers. */
    private val sendBuf = ByteBuffer
        .allocate(SEND_FRAME_BYTES * 4)
        .order(ByteOrder.LITTLE_ENDIAN)

    /** Kept for source compatibility with the old Vosk engine. The
     *  server-backed engine has nothing to "prepare" — it's stateless
     *  per-request — so this is a no-op that just flips state to
     *  READY. */
    suspend fun prepare(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        if (_state.value == EngineState.IDLE || _state.value == EngineState.ERROR) {
            _state.value = EngineState.READY
        }
    }

    fun start(@Suppress("UNUSED_PARAMETER") parentScope: CoroutineScope, sampleRate: Int, channels: Int) {
        stop()
        sourceRate = sampleRate
        sourceChannels = channels
        _text.value = ""
        synchronized(sendBuf) { sendBuf.clear() }
        _state.value = EngineState.RUNNING

        val req = Request.Builder()
            .url("$WS_URL?token=$SHARED_SECRET")
            .header("User-Agent", "HushTV/${BuildConfig.VERSION_NAME}")
            .build()
        ws = httpClient.newWebSocket(req, Listener)
    }

    fun stop() {
        _state.value = if (_state.value == EngineState.ERROR) EngineState.ERROR
            else EngineState.READY
        runCatching { ws?.close(1000, null) }
        ws = null
        sourceRate = 0
        sourceChannels = 0
        _text.value = ""
        synchronized(sendBuf) { sendBuf.clear() }
    }

    /** Called from the audio processor thread — must NOT block. */
    fun onPcmFrame(pcm16le: ByteArray, length: Int) {
        val rate = sourceRate
        val ch = sourceChannels
        val socket = ws ?: return
        if (rate <= 0 || ch <= 0) return
        val resampled = PcmTapAudioProcessor.toMono16k(pcm16le, length, ch, rate)
        if (resampled.isEmpty()) return

        // Pack shorts into the accumulator and flush whenever we have
        // ≥ SEND_FRAME_BYTES queued up. Synchronised because audio
        // processor + GC could race on stop().
        synchronized(sendBuf) {
            for (s in resampled) {
                if (sendBuf.remaining() < 2) {
                    flushLocked(socket)
                }
                sendBuf.putShort(s)
            }
            if (sendBuf.position() >= SEND_FRAME_BYTES) {
                flushLocked(socket)
            }
        }
    }

    private fun flushLocked(socket: WebSocket) {
        if (sendBuf.position() == 0) return
        val out = ByteArray(sendBuf.position())
        sendBuf.flip()
        sendBuf.get(out)
        sendBuf.clear()
        // OkHttp's send returns false when the outbound queue is full
        // (e.g. backpressure from a slow network). We just drop the
        // frame in that case rather than back up audio playback.
        runCatching { socket.send(out.toByteString()) }
    }

    private object Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            // Payload: {"text":"…","lang":"…","ms":123}
            try {
                val json = JSONObject(text)
                val t = json.optString("text", "")
                _text.value = t
            } catch (e: Exception) {
                Log.w(TAG, "bad json: $text", e)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Server only sends text frames — log if we ever see binary.
            Log.w(TAG, "unexpected binary frame, ${bytes.size} bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "ws closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "ws failed: ${t.message} (http ${response?.code})")
            // Keep the engine in RUNNING but stop emitting captions —
            // matches the old "fail silent" behaviour. User can toggle
            // off/on to rebuild a fresh socket.
        }
    }
}
