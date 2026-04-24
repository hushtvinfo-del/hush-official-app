package com.hushtv.tv.ai

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioProcessor] that passes audio through unchanged while
 * mirroring every PCM frame to an external consumer. Used by the AI
 * caption engine to feed Vosk while ExoPlayer plays audio normally.
 *
 * **Pass-through contract** — we MUST copy input → output byte-for-byte
 * and keep the sink format identical to the source format, otherwise
 * the audio sink will re-configure every time captions toggle on/off
 * and the user will hear a click.
 *
 * **Consumer contract** — the consumer callback receives 16-bit LE PCM
 * exactly as ExoPlayer decoded it (still stereo, still at source rate).
 * The [WhisperCaptionEngine] is responsible for downmixing + resampling
 * to 16 kHz mono on a background thread.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PcmTapAudioProcessor : BaseAudioProcessor() {

    /** Snapshot of the negotiated input format so consumers can resample. */
    @Volatile var tapSampleRate: Int = 0
        private set
    @Volatile var tapChannelCount: Int = 0
        private set
    /** Latest encoding we're reading from ExoPlayer. Kept for diagnostics. */
    @Volatile var tapEncoding: Int = 0
        private set

    /** Callback invoked off the audio thread with a fresh PCM chunk.
     *  Buffer is NOT owned by the callee — copy what you need.
     *  ALWAYS 16-bit LE regardless of the underlying sink encoding
     *  (we convert float → Int16 inline). */
    @Volatile var onPcm: ((ByteArray, Int) -> Unit)? = null

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        // Accept both classic 16-bit PCM and Android 11+ float PCM.
        // Anything else (encoded passthrough like AC3 / EAC3 / DTS)
        // we refuse — the sink will route around us and audio will
        // still play, just without AI captions.
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> { /* ok */ }
            else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        tapSampleRate = inputAudioFormat.sampleRate
        tapChannelCount = inputAudioFormat.channelCount
        tapEncoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Mirror to the consumer BEFORE forwarding so we never delay
        // playback. We copy out of the buffer because ExoPlayer reuses
        // it the moment queueInput returns.
        val callback = onPcm
        if (callback != null) {
            val tmp: ByteArray = when (tapEncoding) {
                C.ENCODING_PCM_FLOAT -> floatToInt16LE(inputBuffer, remaining)
                else -> {
                    val arr = ByteArray(remaining)
                    val mark = inputBuffer.position()
                    inputBuffer.get(arr)
                    inputBuffer.position(mark)
                    arr
                }
            }
            runCatching { callback.invoke(tmp, tmp.size) }
        }

        // Forward unchanged to the output buffer.
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer).flip()
    }

    /**
     * Pulls float32 samples from [src] starting at its current position
     * (no modification to src position — we rewind at the end) and
     * returns them as 16-bit little-endian PCM bytes. Samples outside
     * [-1.0f, 1.0f] are clipped.
     */
    private fun floatToInt16LE(src: ByteBuffer, remaining: Int): ByteArray {
        val mark = src.position()
        val order = src.order()
        src.order(ByteOrder.nativeOrder())
        val floatCount = remaining / 4
        val out = ByteArray(floatCount * 2)
        var oi = 0
        val fb = src.asFloatBuffer()
        for (i in 0 until floatCount) {
            val f = fb.get()
            val clipped = when {
                f >= 1.0f -> Short.MAX_VALUE
                f <= -1.0f -> Short.MIN_VALUE
                else -> (f * 32767.0f).toInt().toShort()
            }
            out[oi++] = (clipped.toInt() and 0xFF).toByte()
            out[oi++] = ((clipped.toInt() shr 8) and 0xFF).toByte()
        }
        // Restore position + order so the sink read is unaffected.
        src.order(order)
        src.position(mark)
        return out
    }

    /** Helpful for tests: best-effort PCM → mono 16kHz conversion. */
    companion object {
        /**
         * Downmix stereo → mono and naive linear-resample to [targetRate].
         * Input buffer is 16-bit LE PCM, [channels] channels, [sourceRate] Hz.
         * Returns a fresh short array at the target rate.
         *
         * This is a lightweight decimator — good enough for speech where
         * we're going from 44.1/48 kHz down to 16 kHz. No low-pass
         * filter because Vosk's acoustic model is robust to aliasing
         * in that band.
         */
        fun toMono16k(
            input: ByteArray,
            length: Int,
            channels: Int,
            sourceRate: Int,
        ): ShortArray {
            if (length == 0 || channels <= 0 || sourceRate <= 0) return ShortArray(0)
            val bb = ByteBuffer.wrap(input, 0, length).order(ByteOrder.LITTLE_ENDIAN)
            val totalShorts = length / 2
            // Step 1 — downmix to mono shorts.
            val monoCount = totalShorts / channels
            val mono = ShortArray(monoCount)
            var mi = 0
            while (bb.remaining() >= channels * 2) {
                var acc = 0
                for (ch in 0 until channels) acc += bb.short.toInt()
                mono[mi++] = (acc / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            if (sourceRate == 16000) return mono

            // Step 2 — linear-resample to 16 kHz.
            val targetRate = 16000
            val outLen = (mono.size.toLong() * targetRate / sourceRate).toInt().coerceAtLeast(0)
            val out = ShortArray(outLen)
            if (outLen == 0) return out
            val step = mono.size.toDouble() / outLen.toDouble()
            var pos = 0.0
            for (i in 0 until outLen) {
                val src = pos.toInt().coerceIn(0, mono.size - 1)
                out[i] = mono[src]
                pos += step
            }
            return out
        }
    }
}
