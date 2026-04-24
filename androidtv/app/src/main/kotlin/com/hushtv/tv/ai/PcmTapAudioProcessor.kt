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
 * The [VoskCaptionEngine] is responsible for downmixing + resampling
 * to 16 kHz mono on a background thread.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PcmTapAudioProcessor : BaseAudioProcessor() {

    /** Snapshot of the negotiated input format so consumers can resample. */
    @Volatile var tapSampleRate: Int = 0
        private set
    @Volatile var tapChannelCount: Int = 0
        private set

    /** Callback invoked off the audio thread with a fresh PCM chunk.
     *  Buffer is NOT owned by the callee — copy what you need. */
    @Volatile var onPcm: ((ByteArray, Int) -> Unit)? = null

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        // Only accept 16-bit LE PCM. Anything else we refuse to process
        // (the sink will route around us).
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        tapSampleRate = inputAudioFormat.sampleRate
        tapChannelCount = inputAudioFormat.channelCount
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
            val tmp = ByteArray(remaining)
            val mark = inputBuffer.position()
            inputBuffer.get(tmp)
            inputBuffer.position(mark)      // rewind for the sink
            runCatching { callback.invoke(tmp, remaining) }
        }

        // Forward unchanged to the output buffer.
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer).flip()
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
