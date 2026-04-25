package com.hushtv.tv.ai

import android.content.Context
import android.util.Log
import com.hushtv.tv.BuildConfig
import com.hushtv.tv.data.AiEngineStore
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
 * AI captions engine backed by our **GPU server** at `ai.hushtv.xyz`.
 *
 * The user picks between two backends in Settings ([AiEngineStore]):
 *   • STANDARD → wss://ai.hushtv.xyz/ws — free Whisper-Base, ~1 s lag
 *   • REALTIME → wss://ai.hushtv.xyz/ws-realtime — AssemblyAI Universal
 *     Streaming Multilingual, ~300-500 ms lag, auto-translates non-EN
 *     to English. Costs ~$0.15/hour billed against the operator's AAI
 *     account, not the end user.
 *
 * Public surface (unchanged from prior versions):
 *   • IDLE → READY (toggle on) → RUNNING (audio flowing) → READY → IDLE
 *   • [text] is the live English caption string.
 *   • [state] for player overlays.
 *
 * Architecture per playback session:
 *   PcmTapAudioProcessor.onPcm  →  toMono16k(...)  →  WebSocket.send(bytes)
 *                                                   ↓
 *                                       Server returns JSON
 *                                       {"text","lang","ms","engine","final"}
 *                                                   ↓
 *                                            _text.value = response.text
 */
object WhisperServerEngine {

    private const val TAG = "WhisperSrv"
    private const val WS_STANDARD = "wss://ai.hushtv.xyz/ws"
    private const val WS_REALTIME = "wss://ai.hushtv.xyz/ws-realtime"
    private const val SHARED_SECRET =
        "-LIAWe9fAxf_mnKiWXOqZtbQ2c3Tjn-FZP0IWSSvFDw"

    /** Mini-frame size we ship to the server. Anything from ~50-500 ms
     *  works; AAI batches internally and our Whisper proxy keeps a
     *  3-second sliding window. */
    private const val SEND_FRAME_MS = 200
    private const val SEND_FRAME_BYTES = 16_000 * 2 * SEND_FRAME_MS / 1000

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _activeEngine = MutableStateFlow(AiEngineStore.Engine.STANDARD)
    /** Which backend the current session connected to. UI uses this for
     *  the small "⚡ Realtime" badge next to captions. */
    val activeEngine: StateFlow<AiEngineStore.Engine> = _activeEngine.asStateFlow()

    enum class EngineState { IDLE, PREPARING, READY, RUNNING, ERROR }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var ws: WebSocket? = null
    @Volatile private var sourceRate: Int = 0
    @Volatile private var sourceChannels: Int = 0

    /** Last partial (non-final) seen, for fallback display when a final
     *  hasn't arrived yet — prevents the caption from going blank
     *  during AAI's long mid-utterance gaps. */
    @Volatile private var lastPartial: String = ""

    private val sendBuf = ByteBuffer
        .allocate(SEND_FRAME_BYTES * 4)
        .order(ByteOrder.LITTLE_ENDIAN)

    suspend fun prepare(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        if (_state.value == EngineState.IDLE || _state.value == EngineState.ERROR) {
            _state.value = EngineState.READY
        }
    }

    fun start(parentScope: CoroutineScope, sampleRate: Int, channels: Int, ctx: Context) {
        stop()
        sourceRate = sampleRate
        sourceChannels = channels
        _text.value = ""
        lastPartial = ""
        synchronized(sendBuf) { sendBuf.clear() }

        val engine = AiEngineStore.get(ctx)
        _activeEngine.value = engine
        val url = when (engine) {
            AiEngineStore.Engine.REALTIME -> "$WS_REALTIME?token=$SHARED_SECRET"
            AiEngineStore.Engine.STANDARD -> "$WS_STANDARD?token=$SHARED_SECRET"
        }
        _state.value = EngineState.RUNNING
        Log.i(TAG, "starting engine=$engine url=$url")

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "HushTV/${BuildConfig.VERSION_NAME}")
            .build()
        ws = httpClient.newWebSocket(req, Listener)
    }

    /** Source-compat overload: older callers don't have a Context handy. */
    fun start(parentScope: CoroutineScope, sampleRate: Int, channels: Int) {
        // Without a Context we can't read the engine pref, so fall back
        // to STANDARD. The new player code always uses the 4-arg form.
        stop()
        sourceRate = sampleRate
        sourceChannels = channels
        _text.value = ""
        lastPartial = ""
        synchronized(sendBuf) { sendBuf.clear() }
        _activeEngine.value = AiEngineStore.Engine.STANDARD
        _state.value = EngineState.RUNNING
        val req = Request.Builder()
            .url("$WS_STANDARD?token=$SHARED_SECRET")
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
        lastPartial = ""
        synchronized(sendBuf) { sendBuf.clear() }
    }

    fun onPcmFrame(pcm16le: ByteArray, length: Int) {
        val rate = sourceRate
        val ch = sourceChannels
        val socket = ws ?: return
        if (rate <= 0 || ch <= 0) return
        val resampled = PcmTapAudioProcessor.toMono16k(pcm16le, length, ch, rate)
        if (resampled.isEmpty()) return

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
        runCatching { socket.send(out.toByteString()) }
    }

    private object Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            // Server payload:
            //   STANDARD: {"text":"…","lang":"…","ms":N,"engine":"whisper"}
            //   REALTIME: {"text":"…","source_text":"…","lang":"…",
            //              "final":bool,"engine":"assemblyai","ms":N}
            try {
                val json = JSONObject(text)
                val t = json.optString("text", "")
                val isFinal = json.optBoolean("final", true)

                if (t.isEmpty()) {
                    // Silence sentinel — clear the caption.
                    _text.value = ""
                    lastPartial = ""
                    return
                }

                if (isFinal) {
                    _text.value = t
                    lastPartial = ""
                } else {
                    // Partial — show it as a live preview. When a final
                    // arrives later it'll replace this string.
                    lastPartial = t
                    _text.value = t
                }
            } catch (e: Exception) {
                Log.w(TAG, "bad json: $text", e)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.w(TAG, "unexpected binary frame, ${bytes.size} bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "ws closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "ws failed: ${t.message} (http ${response?.code})")
        }
    }
}
