package com.hushtv.tv.ai.whisper

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

private const val LOG_TAG = "WhisperLib"

/**
 * Thin Kotlin wrapper around the whisper.cpp JNI bindings vendored at
 * `/app/androidtv/app/src/main/cpp/`. Adapted from the upstream
 * `examples/whisper.android/lib/.../LibWhisper.kt` — renamed package
 * to `com.hushtv.tv.ai.whisper` so the JNI symbols we build match.
 *
 * Thread-safety: whisper.cpp is NOT thread safe. All calls are
 * serialised onto a single background executor.
 */
class WhisperContext private constructor(private var ptr: Long) {

    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )

    /**
     * Run Whisper over a PCM float buffer and return the concatenated
     * transcript (no timestamps — our overlay just needs a live
     * string). [data] must be 16 kHz mono float32 samples in
     * [-1.0, 1.0].
     */
    suspend fun transcribe(data: FloatArray): String = withContext(scope.coroutineContext) {
        require(ptr != 0L) { "WhisperContext already released" }
        val numThreads = WhisperCpuConfig.preferredThreadCount
        WhisperLib.fullTranscribe(ptr, numThreads, data)
        val n = WhisperLib.getTextSegmentCount(ptr)
        buildString {
            for (i in 0 until n) {
                append(WhisperLib.getTextSegment(ptr, i))
            }
        }.trim()
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
    }

    protected fun finalize() {
        if (ptr != 0L) {
            runCatching { WhisperLib.freeContext(ptr) }
            ptr = 0L
        }
    }

    companion object {
        fun fromFile(filePath: String): WhisperContext {
            val p = WhisperLib.initContext(filePath)
            if (p == 0L) throw RuntimeException("whisper init failed for $filePath")
            return WhisperContext(p)
        }
    }
}

internal class WhisperLib {
    companion object {
        init {
            Log.d(LOG_TAG, "Primary ABI: ${Build.SUPPORTED_ABIS[0]}")
            // Variant selection (fp16 / vfpv4) mirrors the upstream
            // example. On ARM64 with fphp we load the fast-math .so;
            // everything else falls back to plain libwhisper.so.
            val abi = Build.SUPPORTED_ABIS[0]
            var loaded = false
            runCatching {
                val cpuInfo = File("/proc/cpuinfo").takeIf { it.exists() }
                    ?.readText().orEmpty()
                when {
                    abi == "arm64-v8a" && cpuInfo.contains("fphp") -> {
                        System.loadLibrary("whisper_v8fp16_va")
                        loaded = true
                    }
                    abi == "armeabi-v7a" && cpuInfo.contains("vfpv4") -> {
                        System.loadLibrary("whisper_vfpv4")
                        loaded = true
                    }
                }
            }.onFailure { Log.w(LOG_TAG, "variant load failed, falling back", it) }
            if (!loaded) System.loadLibrary("whisper")
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        external fun getSystemInfo(): String
        external fun benchMemcpy(nthread: Int): String
        external fun benchGgmlMulMat(nthread: Int): String
    }
}
