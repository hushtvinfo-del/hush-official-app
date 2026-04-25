package com.hushtv.tv.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the HushTV Whisper subtitle fallback service.
 *
 * Used by [SubtitleDownloadDialog] when an OpenSubtitles search for an
 * English SRT returns zero results. We POST the playable Xtream stream
 * URL to our backend, which:
 *
 *   1. Hashes the URL → checks cache → returns existing SRT if found.
 *   2. Otherwise pulls audio from the same URL via ffmpeg, hands it to
 *      OpenAI Whisper for transcription, and (if non-English) routes
 *      every line through GPT-4o-mini for English translation.
 *
 * Server-side limits:
 *   • Per-IP rolling 24-hour cap of 240 minutes of new transcription
 *     (cache hits are free), so a runaway client maxes out at ~$1.70/day.
 *   • Cache lives forever — popular titles only ever get transcribed
 *     once across the entire user base.
 *
 * Network shape:
 *   POST https://hushtv.xyz/whisper/translate
 *   Headers: Authorization: Bearer <SHARED_SECRET>
 *   Body:    {"stream_url": "...", "title": "..."}
 *   Returns: {"srt": "...", "lang": "nl", "cached": false,
 *             "ms": 60123, "minutes_remaining_today": 180}
 *
 * Failure modes the caller should be ready for:
 *   • [Result.RateLimited] — daily cap hit.
 *   • [Result.NoSpeech] — file had no recognizable speech (e.g. music).
 *   • [Result.NetworkError] — anything else (timeouts, 5xx, etc.).
 */
object WhisperFallbackApi {

    private const val ENDPOINT = "https://hushtv.xyz/whisper/translate"
    private const val SHARED_SECRET = "o7_CZvfCMtH7P17EcovRy2lr02vE-vR7FDDogV2vJr8"

    sealed interface Result {
        data class Success(
            val srtFile: File,
            val sourceLang: String,
            val cached: Boolean,
            val minutesRemainingToday: Int,
        ) : Result

        data class RateLimited(val message: String) : Result
        data class NoSpeech(val message: String) : Result
        data class NetworkError(val message: String) : Result
    }

    /**
     * Synchronous network call — must run off the main thread.
     *
     * Even with caching, the server can take 60–180 s on a fresh
     * 90-minute movie (audio extraction + Whisper inference). Use a
     * generous read timeout and a clear progress UI.
     */
    fun translate(
        ctx: Context,
        streamUrl: String,
        title: String?,
    ): Result {
        return runCatching {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 600_000   // 10 minutes — server processes long files
                doInput = true
                doOutput = true
                setRequestProperty("Authorization", "Bearer $SHARED_SECRET")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONObject().apply {
                put("stream_url", streamUrl)
                put("title", title ?: "")
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""

            when (code) {
                in 200..299 -> {
                    val obj = JSONObject(text)
                    val srt = obj.optString("srt", "")
                    if (srt.isBlank()) {
                        Result.NoSpeech("Server returned an empty subtitle file.")
                    } else {
                        // Persist into the same OpenSubtitles cache dir
                        // so the player code can side-load it identically.
                        val cacheDir = File(ctx.cacheDir, "opensubtitles").apply { mkdirs() }
                        val fname = "ai_${streamUrl.hashCode()}.srt"
                        val file = File(cacheDir, fname).apply { writeText(srt, Charsets.UTF_8) }
                        Result.Success(
                            srtFile = file,
                            sourceLang = obj.optString("lang", "en"),
                            cached = obj.optBoolean("cached", false),
                            minutesRemainingToday = obj.optInt("minutes_remaining_today", 0),
                        )
                    }
                }
                429 -> Result.RateLimited(parseDetail(text)
                    ?: "You've hit today's free AI subtitle limit. Try again tomorrow.")
                502 -> {
                    val detail = parseDetail(text) ?: ""
                    if ("no segments" in detail) {
                        Result.NoSpeech(
                            "Couldn't find clear speech in this title — AI subtitles unavailable.",
                        )
                    } else {
                        Result.NetworkError(
                            "Couldn't process the audio. The provider stream may be unstable.",
                        )
                    }
                }
                413 -> Result.NetworkError(
                    parseDetail(text) ?: "Title is longer than the AI subtitle service supports.")
                else -> Result.NetworkError("Server error $code: ${parseDetail(text) ?: text.take(120)}")
            }
        }.getOrElse { e ->
            Result.NetworkError(e.message ?: "Network error")
        }
    }

    private fun parseDetail(body: String): String? = runCatching {
        JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
    }.getOrNull()
}
