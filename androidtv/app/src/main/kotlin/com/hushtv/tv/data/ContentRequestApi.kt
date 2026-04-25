package com.hushtv.tv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the HushTV admin API gateway.
 *
 * Single endpoint pattern: every action goes to
 * `POST https://hushtv.base44.app/api/hushtvapiGateway` with an
 * `X-API-Key` header and a JSON body that includes an `action` field
 * naming the operation.
 *
 * Implemented here:
 *   • [submitRequest]  → action="createContentRequest"
 *   • [listRequests]   → action="getContentRequests"
 *
 * All calls are blocking — callers must run them on a background
 * dispatcher (Dispatchers.IO).
 */
object ContentRequestApi {

    private const val ENDPOINT = "https://hushtv.com/api/functions/hushtvapiGateway"
    private const val API_KEY =
        "htv_FIe0oUPLXQ8PorAoxgWgewYjxcsLal78ls4DR1jx7omxBGSi"

    enum class Status(val storage: String, val label: String, val emoji: String) {
        PENDING("pending", "Pending", "⏳"),
        IN_PROGRESS("in_progress", "In Progress", "🔄"),
        ALREADY_AVAILABLE("already_available", "Already Available", "✅"),
        ADDED("added", "Added!", "✅"),
        NOT_FOUND("not_found", "Not Found", "❌");

        companion object {
            fun of(raw: String?): Status =
                values().firstOrNull { it.storage == raw } ?: PENDING
        }
    }

    /** A single content request returned by [listRequests]. */
    data class Request(
        val id: String,
        val title: String,
        val type: String,                // "movie" | "series"
        val status: Status,
        val adminResponse: String?,
        val createdDate: String,         // ISO-8601 UTC
        val updatedDate: String,         // ISO-8601 UTC — last admin/system mutation
        val seriesRequestType: String?,  // "entire_series" | "specific_episodes"
        val seasons: String?,
        val episodes: String?,
        val additionalInfo: String?,
        val priority: String?,           // "low" | "medium" | "high"
    )

    sealed interface SubmitResult {
        data class Success(val requestId: String, val status: Status, val message: String) : SubmitResult
        data class Error(val message: String) : SubmitResult
    }

    sealed interface ListResult {
        data class Success(val requests: List<Request>) : ListResult
        data class Error(val message: String) : ListResult
    }

    /**
     * POST createContentRequest.
     *
     * For series-specific submissions, pass [seriesRequestType] +
     * optionally [seasons] / [episodes]. For movies, leave those null.
     */
    fun submitRequest(
        ctx: Context,
        type: String,                       // "movie" | "series"
        title: String,
        additionalInfo: String? = null,
        seriesRequestType: String? = null,  // "entire_series" | "specific_episodes"
        seasons: String? = null,
        episodes: String? = null,
        priority: String = "medium",
    ): SubmitResult {
        val contact = UserContactStore.get(ctx)
            ?: return SubmitResult.Error("Add your name and email first.")

        val body = JSONObject().apply {
            put("action", "createContentRequest")
            put("customer_email", contact.email)
            put("customer_name", contact.name)
            put("type", type)
            put("title", title)
            put("priority", priority)
            if (!additionalInfo.isNullOrBlank()) put("additional_info", additionalInfo)
            if (!seriesRequestType.isNullOrBlank()) put("series_request_type", seriesRequestType)
            if (!seasons.isNullOrBlank()) put("seasons", seasons)
            if (!episodes.isNullOrBlank()) put("episodes", episodes)
        }
        return runCatching {
            val (code, text) = post(body.toString())
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json?.optBoolean("success") == true) {
                SubmitResult.Success(
                    requestId = json.optString("request_id", ""),
                    status = Status.of(json.optString("status")),
                    message = json.optString("message", "Request submitted."),
                )
            } else {
                val err = json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "Submit failed (HTTP $code)."
                SubmitResult.Error(err)
            }
        }.getOrElse { e ->
            SubmitResult.Error(e.message ?: "Network error")
        }
    }

    /** POST getContentRequests for the currently-saved user email. */
    fun listRequests(ctx: Context, limit: Int = 30): ListResult {
        val contact = UserContactStore.get(ctx)
            ?: return ListResult.Error("Add your name and email first.")

        val body = JSONObject().apply {
            put("action", "getContentRequests")
            put("customer_email", contact.email)
            put("limit", limit)
        }
        return runCatching {
            val (code, text) = post(body.toString())
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json?.optBoolean("success") == true || json?.has("requests") == true) {
                val arr = json.optJSONArray("requests") ?: JSONArray()
                val items = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Request(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        type = o.optString("type"),
                        status = Status.of(o.optString("status")),
                        adminResponse = o.optString("admin_response").takeIf { it.isNotBlank() && it != "null" },
                        createdDate = o.optString("created_date"),
                        updatedDate = o.optString("updated_date").takeIf { it.isNotBlank() && it != "null" }
                            ?: o.optString("created_date"),
                        seriesRequestType = o.optString("series_request_type").takeIf { it.isNotBlank() && it != "null" },
                        seasons = o.optString("seasons").takeIf { it.isNotBlank() && it != "null" },
                        episodes = o.optString("episodes").takeIf { it.isNotBlank() && it != "null" },
                        additionalInfo = o.optString("additional_info").takeIf { it.isNotBlank() && it != "null" },
                        priority = o.optString("priority").takeIf { it.isNotBlank() && it != "null" },
                    )
                }
                ListResult.Success(items)
            } else {
                ListResult.Error(
                    json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Couldn't load requests (HTTP $code)."
                )
            }
        }.getOrElse { e ->
            ListResult.Error(e.message ?: "Network error")
        }
    }

    private fun post(body: String): Pair<Int, String> {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-API-Key", API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }
            ?: ""
        return code to text
    }
}
