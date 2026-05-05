package com.hushtv.tv.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Client for the HushTV Cloud DVR remote service at
 * https://hushtv.xyz/... — served by a dedicated recording server
 * that handles live-TV ffmpeg captures, per-user 20-hour quotas, and
 * 14-day retention. See `/tmp/dvr_service.py` on the deploy host for
 * the full backend surface.
 *
 * The service never sees the user's Xtream credentials — the client
 * derives a deterministic, pseudonymous 16-char hex `user_id` from a
 * local SHA-256 of `host|username|password`. Same playlist = same
 * user_id across reinstalls.
 */
object DvrApi {

    // Public IP of the dedicated DVR server. Nginx on :80 proxies
    // /api/dvr/* to the FastAPI uvicorn workers on 127.0.0.1:8080.
    private const val BASE_URL = "http://216.152.148.150"

    // Long timeouts: recording requests can take a few seconds; list
    // calls are fast. Never use this client for streaming — go through
    // ExoPlayer for the mp4 stream endpoint.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @JsonClass(generateAdapter = true)
    data class Quota(
        val user_id: String,
        val quota_s: Int,
        val used_s: Int,
        val available_s: Int,
    )

    @JsonClass(generateAdapter = true)
    data class Recording(
        val rec_id: String,
        val user_id: String,
        val channel_name: String = "",
        val show_title: String = "",
        val started_at_epoch: Long = 0L,
        val ended_at_epoch: Long = 0L,
        // Scheduled duration for the recording. While still recording,
        // this is the cap ffmpeg will stop at (e.g. show-end + pad).
        // On completion it's rewritten to the actual elapsed seconds.
        val duration_s: Int = 0,
        // "recording" | "completed" | "failed"
        val status: String = "",
        val exit_code: Int = 0,
        val size_bytes: Long = 0L,
        val kind: String = "",
        // Populated by the backend when a recording fails for a
        // reason the user can do something about — e.g. provider
        // IP-blocking the DVR host. UI surfaces this verbatim.
        val fail_reason: String = "",
        // "Mark as watched" flag. Server-side cosmetic only —
        // recording stays on disk and counts against quota.
        // True ⇒ row dimmed + ✓ pill in the UI.
        val watched: Boolean = false,
        val watched_at_epoch: Long = 0L,
    )

    @JsonClass(generateAdapter = true)
    data class Scheduled(
        val sched_id: String,
        val user_id: String,
        val channel_name: String = "",
        val channel_url: String = "",
        val show_title: String = "",
        val poster: String = "",
        val start_at_epoch: Long = 0L,
        val end_at_epoch: Long = 0L,
        val epg_id: String = "",
        val season_pass_id: String = "",
        // "pending" | "fired" | "skipped" | "completed" | "failed"
        val status: String = "pending",
        val skip_reason: String = "",
        val rec_id: String = "",
    )

    @JsonClass(generateAdapter = true)
    data class SeasonPass(
        val pass_id: String,
        val user_id: String,
        val series_title: String,
        val poster: String = "",
        val channel_id: Int = 0,
        val channel_name: String = "",
        val channel_url: String = "",
        val created_at_epoch: Long = 0L,
        val last_refresh_epoch: Long = 0L,
        val scheduled_count: Int = 0,
        val skipped_count: Int = 0,
    )

    /**
     * Push event from the server. Polled by [DvrEventPoller] and
     * surfaced as Android system notifications.
     */
    @JsonClass(generateAdapter = true)
    data class Event(
        // "started" | "completed" | "failed" |
        // "scheduled_started" | "scheduled_skipped"
        val kind: String = "",
        val rec_id: String = "",
        val sched_id: String = "",
        val show_title: String = "",
        val channel_name: String = "",
        val reason: String = "",
        val fail_reason: String = "",
        val ts: Long = 0L,
    )

    @JsonClass(generateAdapter = true)
    private data class EventsEnvelope(
        val events: List<Event> = emptyList(),
        val now: Long = 0L,
    )

    @JsonClass(generateAdapter = true)
    private data class ScheduledEnvelope(val scheduled: List<Scheduled> = emptyList())

    @JsonClass(generateAdapter = true)
    private data class PassesEnvelope(val season_passes: List<SeasonPass> = emptyList())

    @JsonClass(generateAdapter = true)
    data class XtreamCreds(
        val host: String,
        val username: String,
        val password: String,
        val stream_id: Int,
    )

    @JsonClass(generateAdapter = true)
    private data class ScheduleBody(
        val user_id: String,
        val channel_url: String,
        val channel_name: String,
        val show_title: String,
        val poster: String,
        val start_at_epoch: Long,
        val end_at_epoch: Long,
        val epg_id: String,
        val xtream: XtreamCreds? = null,
    )

    @JsonClass(generateAdapter = true)
    data class SeasonPassProgram(
        val title: String,
        val start_at_epoch: Long,
        val end_at_epoch: Long,
        val epg_id: String = "",
    )

    @JsonClass(generateAdapter = true)
    private data class SeasonPassBody(
        val user_id: String,
        val series_title: String,
        val poster: String,
        val channel_id: Int,
        val channel_name: String,
        val channel_url: String,
        val upcoming_programs: List<SeasonPassProgram>,
        val xtream: XtreamCreds? = null,
    )

    @JsonClass(generateAdapter = true)
    private data class RecordingsEnvelope(val recordings: List<Recording> = emptyList())

    @JsonClass(generateAdapter = true)
    private data class RecordNowBody(
        val user_id: String,
        val channel_url: String,
        val channel_name: String,
        val show_title: String,
        val show_ends_at_epoch: Long,
        val fallback_duration_s: Int,
    )

    private val recordingsAdapter = moshi.adapter(RecordingsEnvelope::class.java)
    private val quotaAdapter = moshi.adapter(Quota::class.java)
    private val recordingAdapter = moshi.adapter(Recording::class.java)
    private val recordBodyAdapter = moshi.adapter(RecordNowBody::class.java)
    private val scheduledEnvelopeAdapter = moshi.adapter(ScheduledEnvelope::class.java)
    private val scheduleBodyAdapter = moshi.adapter(ScheduleBody::class.java)
    private val scheduledAdapter = moshi.adapter(Scheduled::class.java)
    private val passesEnvelopeAdapter = moshi.adapter(PassesEnvelope::class.java)
    private val seasonPassBodyAdapter = moshi.adapter(SeasonPassBody::class.java)
    private val seasonPassAdapter = moshi.adapter(SeasonPass::class.java)
    private val eventsEnvelopeAdapter = moshi.adapter(EventsEnvelope::class.java)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * Deterministic 16-char hex user id derived from the active
     * playlist. Same playlist → same id across reinstalls, so a user
     * who wipes the app still sees their own recordings when they
     * re-add the playlist. No credentials ever leave the device.
     */
    fun userIdFor(playlist: Playlist): String {
        val material = "${playlist.host}|${playlist.username}|${playlist.password}"
        val md = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        for (i in 0 until 8) {
            val b = md[i].toInt() and 0xff
            if (b < 0x10) sb.append('0')
            sb.append(b.toString(16))
        }
        return sb.toString()
    }

    /** Public HTTPS-or-HTTP URL for streaming a recording into ExoPlayer.
     *  The user_id is required — the server refuses anonymous access. */
    fun streamUrl(userId: String, recId: String): String =
        "$BASE_URL/api/dvr/recordings/$recId/stream?user_id=$userId"

    /** Thumbnail URL for a recording (may 404 until ffmpeg writes it). */
    fun thumbUrl(userId: String, recId: String): String =
        "$BASE_URL/api/dvr/recordings/$recId/thumb?user_id=$userId"

    suspend fun quota(userId: String): Quota? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/quota?user_id=$userId")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                quotaAdapter.fromJson(body)
            }
        }.getOrNull()
    }

    suspend fun list(userId: String): List<Recording> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/recordings?user_id=$userId")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<Recording>()
                val body = resp.body?.string() ?: return@use emptyList<Recording>()
                recordingsAdapter.fromJson(body)?.recordings ?: emptyList()
            }
        }.getOrNull() ?: emptyList()
    }

    /**
     * Returns the currently-running recording for [channelName] if one
     * exists for [userId], else null. Used by the OSD record pill and
     * the channel long-press dialog to flip the label from "Record" to
     * "Stop recording".
     */
    suspend fun findActive(userId: String, channelName: String): Recording? {
        val all = list(userId)
        return all.firstOrNull {
            it.status == "recording" && it.channel_name.equals(channelName, ignoreCase = true)
        }
    }

    sealed interface RecordNowResult {
        data class Success(val rec: Recording) : RecordNowResult
        data class Error(val message: String) : RecordNowResult
    }

    suspend fun recordNow(
        userId: String,
        channelUrl: String,
        channelName: String,
        showTitle: String = "",
        showEndsAtEpoch: Long = 0L,
        fallbackDurationS: Int = 3600,
    ): RecordNowResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = recordBodyAdapter.toJson(
                RecordNowBody(
                    user_id = userId,
                    channel_url = channelUrl,
                    channel_name = channelName,
                    show_title = showTitle,
                    show_ends_at_epoch = showEndsAtEpoch,
                    fallback_duration_s = fallbackDurationS,
                ),
            )
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/record-now")
                .post(body.toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    // The service sends {"detail":"..."} on 4xx (quota,
                    // bad params, max-concurrent). Surface that message
                    // verbatim if present.
                    val detail = runCatching {
                        org.json.JSONObject(raw).optString("detail")
                    }.getOrNull().orEmpty()
                    val msg = detail.ifBlank { "Couldn't start recording (${resp.code})" }
                    return@use RecordNowResult.Error(msg) as RecordNowResult
                }
                val rec = recordingAdapter.fromJson(raw)
                    ?: return@use RecordNowResult.Error("Server returned bad JSON.") as RecordNowResult
                RecordNowResult.Success(rec)
            }
        }.getOrElse { e -> RecordNowResult.Error(e.message ?: "Network error.") }
    }

    suspend fun delete(userId: String, recId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/recordings/$recId?user_id=$userId")
                .delete()
                .build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrElse { false }
    }

    suspend fun deleteAll(userId: String): Int = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/recordings?user_id=$userId")
                .delete()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use 0
                val body = resp.body?.string() ?: return@use 0
                runCatching { org.json.JSONObject(body).optInt("deleted", 0) }.getOrDefault(0)
            }
        }.getOrElse { 0 }
    }

    // ── Phase 2 — schedule a single future recording ──────────────

    sealed interface ScheduleResult {
        data class Success(val sched: Scheduled) : ScheduleResult
        data class Error(val message: String) : ScheduleResult
    }

    suspend fun schedule(
        userId: String,
        channelUrl: String,
        channelName: String,
        showTitle: String,
        startAtEpoch: Long,
        endAtEpoch: Long,
        epgId: String = "",
        poster: String = "",
        xtream: XtreamCreds? = null,
    ): ScheduleResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = scheduleBodyAdapter.toJson(
                ScheduleBody(
                    user_id = userId,
                    channel_url = channelUrl,
                    channel_name = channelName,
                    show_title = showTitle,
                    poster = poster,
                    start_at_epoch = startAtEpoch,
                    end_at_epoch = endAtEpoch,
                    epg_id = epgId,
                    xtream = xtream,
                ),
            )
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/schedule")
                .post(body.toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = runCatching {
                        org.json.JSONObject(raw).optString("detail")
                    }.getOrNull().orEmpty()
                    val msg = detail.ifBlank { "Couldn't schedule (${resp.code})" }
                    return@use ScheduleResult.Error(msg) as ScheduleResult
                }
                val sched = scheduledAdapter.fromJson(raw)
                    ?: return@use ScheduleResult.Error("Server returned bad JSON.") as ScheduleResult
                ScheduleResult.Success(sched)
            }
        }.getOrElse { e -> ScheduleResult.Error(e.message ?: "Network error.") }
    }

    suspend fun listScheduled(userId: String): List<Scheduled> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/scheduled?user_id=$userId")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<Scheduled>()
                val body = resp.body?.string() ?: return@use emptyList<Scheduled>()
                scheduledEnvelopeAdapter.fromJson(body)?.scheduled ?: emptyList()
            }
        }.getOrNull() ?: emptyList()
    }

    suspend fun cancelScheduled(userId: String, schedId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/scheduled/$schedId?user_id=$userId")
                .delete()
                .build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrElse { false }
    }

    // ── Phase 3 — series season passes ────────────────────────────

    sealed interface SeasonPassResult {
        data class Success(val pass: SeasonPass) : SeasonPassResult
        data class Error(val message: String) : SeasonPassResult
    }

    suspend fun createSeasonPass(
        userId: String,
        seriesTitle: String,
        channelId: Int,
        channelName: String,
        channelUrl: String,
        upcomingPrograms: List<SeasonPassProgram>,
        poster: String = "",
        xtream: XtreamCreds? = null,
    ): SeasonPassResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = seasonPassBodyAdapter.toJson(
                SeasonPassBody(
                    user_id = userId,
                    series_title = seriesTitle,
                    poster = poster,
                    channel_id = channelId,
                    channel_name = channelName,
                    channel_url = channelUrl,
                    upcoming_programs = upcomingPrograms,
                    xtream = xtream,
                ),
            )
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/season-pass")
                .post(body.toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = runCatching {
                        org.json.JSONObject(raw).optString("detail")
                    }.getOrNull().orEmpty()
                    val msg = detail.ifBlank { "Couldn't create season pass (${resp.code})" }
                    return@use SeasonPassResult.Error(msg) as SeasonPassResult
                }
                val pass = seasonPassAdapter.fromJson(raw)
                    ?: return@use SeasonPassResult.Error("Server returned bad JSON.") as SeasonPassResult
                SeasonPassResult.Success(pass)
            }
        }.getOrElse { e -> SeasonPassResult.Error(e.message ?: "Network error.") }
    }

    suspend fun listSeasonPasses(userId: String): List<SeasonPass> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/season-passes?user_id=$userId")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<SeasonPass>()
                val body = resp.body?.string() ?: return@use emptyList<SeasonPass>()
                passesEnvelopeAdapter.fromJson(body)?.season_passes ?: emptyList()
            }
        }.getOrNull() ?: emptyList()
    }

    suspend fun deleteSeasonPass(userId: String, passId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/season-passes/$passId?user_id=$userId")
                .delete()
                .build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrElse { false }
    }

    /** Pull events newer than [sinceEpoch]. Returns ([events], serverNow). */
    suspend fun events(userId: String, sinceEpoch: Long): Pair<List<Event>, Long> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/events?user_id=$userId&since=$sinceEpoch")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<Event>() to sinceEpoch
                val body = resp.body?.string() ?: return@use emptyList<Event>() to sinceEpoch
                val env = eventsEnvelopeAdapter.fromJson(body)
                (env?.events ?: emptyList()) to (env?.now ?: sinceEpoch)
            }
        }.getOrNull() ?: (emptyList<Event>() to sinceEpoch)
    }

    /**
     * Toggle the "watched" flag on a completed recording.
     * Server-side cosmetic — recording stays on disk and counts
     * against quota. UI dims the row + adds a ✓ pill when true.
     */
    suspend fun markWatched(
        userId: String,
        recId: String,
        watched: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/api/dvr/recordings/$recId/watched?user_id=$userId&watched=$watched")
                .post("".toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrElse { false }
    }

    /**
     * If [streamUrl] is one of our DVR `/api/dvr/recordings/{rec_id}/stream`
     * URLs, return the (user_id, rec_id) pair. Otherwise null. Used by
     * the player to auto-mark recordings as watched at ≥95% playback.
     */
    fun parseRecordingUrl(streamUrl: String): Pair<String, String>? {
        if (!streamUrl.contains("/api/dvr/recordings/")) return null
        return runCatching {
            // .../api/dvr/recordings/<rec_id>/stream?user_id=<uid>
            val recId = streamUrl
                .substringAfter("/api/dvr/recordings/")
                .substringBefore("/")
            val uid = streamUrl
                .substringAfter("user_id=", missingDelimiterValue = "")
                .substringBefore("&")
            if (recId.isBlank() || uid.isBlank()) null
            else uid to recId
        }.getOrNull()
    }
}
