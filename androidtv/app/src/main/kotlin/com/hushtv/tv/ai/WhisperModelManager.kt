package com.hushtv.tv.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the Whisper Base GGML model on disk.
 *
 *  • Model is hosted on our OTA CDN (`https://hushtv.xyz/ai-models/`).
 *  • Kept at `filesDir/ai-models/whisper-base.bin`.
 *  • Expected size ≈ 142 MB (the upstream base ggml). Partial files
 *    from a failed / cancelled download are discarded so the next
 *    attempt starts clean — Whisper can't load a truncated model.
 */
object WhisperModelManager {

    private const val TAG = "WhisperModel"
    const val MODEL_URL = "https://hushtv.xyz/ai-models/ggml-base.bin"
    // Sanity check. Whisper Base ggml is 141.11 MB ± a few KB; we
    // reject anything under 120 MB as a bad / truncated download.
    private const val MIN_EXPECTED_SIZE = 120L * 1024 * 1024

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val bytesDone: Long, val bytesTotal: Long) : DownloadState {
            val progress: Float
                get() = if (bytesTotal > 0) (bytesDone.toDouble() / bytesTotal).toFloat() else 0f
        }
        data object Done : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun modelFile(ctx: Context): File {
        val dir = File(ctx.filesDir, "ai-models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "whisper-base.bin")
    }

    fun isModelReady(ctx: Context): Boolean {
        val f = modelFile(ctx)
        return f.exists() && f.length() >= MIN_EXPECTED_SIZE
    }

    fun delete(ctx: Context) {
        runCatching { modelFile(ctx).delete() }
        _downloadState.value = DownloadState.Idle
    }

    suspend fun download(ctx: Context) = withContext(Dispatchers.IO) {
        if (isModelReady(ctx)) {
            _downloadState.value = DownloadState.Done
            return@withContext
        }
        val target = modelFile(ctx)
        // Always start from zero — resumable downloads add complexity
        // we don't need at 140 MB.
        if (target.exists()) target.delete()
        val tmp = File(target.parentFile, target.name + ".part")
        if (tmp.exists()) tmp.delete()

        _downloadState.value = DownloadState.Running(0, 0)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
            }
            conn.connect()
            if (conn.responseCode != 200) {
                _downloadState.value = DownloadState.Failed(
                    "HTTP ${conn.responseCode} from server",
                )
                return@withContext
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastEmitted = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        // Emit progress every 256 KB to avoid flooding
                        // the UI recomposer.
                        if (copied - lastEmitted > 256 * 1024) {
                            _downloadState.value = DownloadState.Running(copied, total)
                            lastEmitted = copied
                        }
                    }
                }
            }
            if (tmp.length() < MIN_EXPECTED_SIZE) {
                tmp.delete()
                _downloadState.value = DownloadState.Failed(
                    "Downloaded file looks truncated (${tmp.length()} bytes)",
                )
                return@withContext
            }
            // Atomic rename only after full verification.
            if (!tmp.renameTo(target)) {
                _downloadState.value = DownloadState.Failed("Could not finalise download")
                return@withContext
            }
            _downloadState.value = DownloadState.Done
            Log.i(TAG, "Whisper Base ready at ${target.absolutePath}")
        } catch (t: Throwable) {
            Log.e(TAG, "Download failed", t)
            runCatching { tmp.delete() }
            _downloadState.value = DownloadState.Failed(
                t.message ?: "Network error",
            )
        } finally {
            conn?.disconnect()
        }
    }
}
