package com.hushtv.tv.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin REST client for **OpenSubtitles.com API v1**.
 *
 * Auth: every request carries `Api-Key: <key>` and a custom
 * `User-Agent` that we registered with our consumer record. The free
 * tier permits ~5 SRT downloads per IP per 24 hours; for higher limits
 * we would log in and add a `Authorization: Bearer <jwt>` header but
 * for now anonymous calls are fine.
 *
 * API ref: https://opensubtitles.stoplight.io/docs/opensubtitles-api
 *
 * Two main flows:
 *
 *   1. [searchMovie] / [searchEpisode] → returns up to 50 candidate
 *      subtitle entries, each with a `file_id` we can download.
 *
 *   2. [downloadSrt] (POST /download) → exchanges a `file_id` for a
 *      short-lived `link` to the actual `.srt` file, which we then
 *      stream to disk under the app's cache dir. The cached file is
 *      keyed by `file_id` so repeat plays don't burn another download
 *      from the daily quota.
 */
object OpenSubtitlesApi {

    private const val BASE = "https://api.opensubtitles.com/api/v1"
    // Embedded for now. The whole API key system on OS is rate-limited
    // to per-IP/day, so leaking it is annoying but not catastrophic.
    // If usage grows we move this server-side.
    private const val API_KEY = "fkKIMFl9BaLxw6ITOnbaWxB2NqpaTQlA"
    private const val USER_AGENT = "Hushtvapp"

    data class Hit(
        val fileId: Long,
        val language: String,
        val release: String,
        val downloadCount: Int,
        val ratings: Double,
        val featureTitle: String,
        val featureYear: Int?,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val hd: Boolean,
        val foreignPartsOnly: Boolean,
        val fromTrusted: Boolean,
    )

    data class DownloadInfo(
        val link: String,
        val fileName: String?,
        val remaining: Int?,
        val resetTime: String?,
    )

    fun searchMovie(
        title: String,
        year: Int?,
        languages: List<String>,
    ): List<Hit> {
        val params = mutableListOf(
            "query" to title,
            "type" to "movie",
            "languages" to languages.joinToString(","),
            "order_by" to "download_count",
            "order_direction" to "desc",
        )
        if (year != null) params += "year" to year.toString()
        return doSearch(params)
    }

    fun searchEpisode(
        seriesTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        languages: List<String>,
    ): List<Hit> {
        val params = listOf(
            "query" to seriesTitle,
            "type" to "episode",
            "season_number" to seasonNumber.toString(),
            "episode_number" to episodeNumber.toString(),
            "languages" to languages.joinToString(","),
            "order_by" to "download_count",
            "order_direction" to "desc",
        )
        return doSearch(params)
    }

    private fun doSearch(params: List<Pair<String, String>>): List<Hit> {
        val qs = params.joinToString("&") {
            "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}"
        }
        val resp = httpGet("$BASE/subtitles?$qs") ?: return emptyList()
        val data = JSONObject(resp).optJSONArray("data") ?: return emptyList()
        val hits = mutableListOf<Hit>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val attrs = item.optJSONObject("attributes") ?: continue
            val files = attrs.optJSONArray("files")
            val fileId = files?.optJSONObject(0)?.optLong("file_id", -1L) ?: -1L
            if (fileId <= 0) continue
            val featureDetails = attrs.optJSONObject("feature_details")
            hits += Hit(
                fileId = fileId,
                language = attrs.optString("language", "").lowercase(),
                release = attrs.optString("release", ""),
                downloadCount = attrs.optInt("download_count", 0),
                ratings = attrs.optDouble("ratings", 0.0),
                featureTitle = featureDetails?.optString("title", "") ?: "",
                featureYear = featureDetails?.optInt("year", 0)?.takeIf { it > 0 },
                seasonNumber = featureDetails?.optInt("season_number", 0)?.takeIf { it > 0 },
                episodeNumber = featureDetails?.optInt("episode_number", 0)?.takeIf { it > 0 },
                hd = attrs.optBoolean("hd", false),
                foreignPartsOnly = attrs.optBoolean("foreign_parts_only", false),
                fromTrusted = attrs.optBoolean("from_trusted", false),
            )
        }
        return hits
    }

    /**
     * POST /download → returns a temporary URL to the actual SRT file.
     *
     * The endpoint also reports remaining daily downloads in the
     * response, which we surface so the UI can warn users approaching
     * the limit.
     */
    fun downloadInfo(fileId: Long): DownloadInfo? {
        val body = JSONObject().put("file_id", fileId).toString()
        val resp = httpPost("$BASE/download", body) ?: return null
        val obj = JSONObject(resp)
        val link = obj.optString("link", "")
        if (link.isEmpty()) return null
        return DownloadInfo(
            link = link,
            fileName = obj.optString("file_name").takeIf { it.isNotEmpty() },
            remaining = obj.optInt("remaining", -1).takeIf { it >= 0 },
            resetTime = obj.optString("reset_time").takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Resolves [fileId] into a local SRT file. Cached: subsequent calls
     * for the same id return the existing file without burning the
     * daily quota.
     *
     * Returns null on any failure (network, quota exhausted, bad SRT).
     */
    fun fetchSrt(ctx: Context, fileId: Long): File? {
        val cacheDir = File(ctx.cacheDir, "opensubtitles").apply { mkdirs() }
        val target = File(cacheDir, "$fileId.srt")
        if (target.exists() && target.length() > 0L) return target

        val info = downloadInfo(fileId) ?: return null
        // Pull the actual SRT bytes from the temporary link. No auth
        // headers required on this URL.
        return runCatching {
            val conn = (URL(info.link).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            conn.inputStream.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            if (target.length() <= 0L) {
                target.delete()
                return@runCatching null
            }
            target
        }.getOrNull()
    }

    // ── HTTP plumbing ───────────────────────────────────────────────

    private fun httpGet(url: String): String? = call(url, method = "GET", body = null)

    private fun httpPost(url: String, body: String): String? =
        call(url, method = "POST", body = body)

    private fun call(url: String, method: String, body: String?): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8_000
                readTimeout = 15_000
                doInput = true
                setRequestProperty("Api-Key", API_KEY)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val txt = stream?.bufferedReader()?.use { it.readText() }
            if (code in 200..299) txt else {
                // Surface common rate-limit / auth errors as null —
                // callers handle it as "no results / try later".
                null
            }
        }.getOrNull()
    }

    /** Quick health probe — used by the diagnostics screen. */
    @Throws(IOException::class)
    fun ping(): Boolean {
        return httpGet("$BASE/infos/languages") != null
    }
}
