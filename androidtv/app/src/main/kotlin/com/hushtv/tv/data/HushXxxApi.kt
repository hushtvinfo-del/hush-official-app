package com.hushtv.tv.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * API client for the HushXXX content service running on
 * http://216.152.148.117/ (reverse-proxied by nginx to FastAPI on
 * :8090). Sibling of [DvrApi] — same tight Moshi + OkHttp stack, no
 * coroutines Retrofit.
 *
 * Gating is pure client-side: access to this object is limited to
 * the HushXXX tab, which itself is gated by the age confirmation
 * dialog + Hush+ membership check. The server does not authenticate.
 */
object HushXxxApi {

    private const val BASE_URL = "http://216.152.148.117"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @JsonClass(generateAdapter = true)
    data class Performer(
        val id: Int,
        val slug: String = "",
        val name: String = "",
        val photo_url: String = "",
        val bio: String = "",
    )

    @JsonClass(generateAdapter = true)
    data class Studio(
        val id: Int,
        val slug: String = "",
        val name: String = "",
        val logo_url: String = "",
        val description: String = "",
    )

    @JsonClass(generateAdapter = true)
    data class Category(
        val id: Int,
        val slug: String = "",
        val name: String = "",
        val cover_url: String = "",
    )

    @JsonClass(generateAdapter = true)
    data class Scene(
        val id: Int,
        val slug: String = "",
        val title: String = "",
        val description: String = "",
        val duration_s: Int = 0,
        val release_date: String = "",
        val poster_url: String = "",
        val landscape_url: String = "",
        val preview_url: String = "",
        val rating: Double = 0.0,
        val views: Int = 0,
        val status: String = "active",
        val stream_url: String = "",
        val studio: Studio? = null,
        val performers: List<Performer> = emptyList(),
        val categories: List<Category> = emptyList(),
    ) {
        /** Absolute URLs the Coil image loader can hit. */
        fun absPoster(): String = if (poster_url.startsWith("http")) poster_url
            else "$BASE_URL$poster_url"
        fun absLandscape(): String = if (landscape_url.startsWith("http")) landscape_url
            else "$BASE_URL$landscape_url"
        fun absStream(): String = if (stream_url.startsWith("http")) stream_url
            else "$BASE_URL$stream_url"
    }

    @JsonClass(generateAdapter = true)
    data class Home(
        val rails: Rails = Rails(),
        val categories: List<Category> = emptyList(),
        val studios: List<Studio> = emptyList(),
        val performers: List<Performer> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class Rails(
        val new_and_popular: List<Scene> = emptyList(),
        val trending: List<Scene> = emptyList(),
        val top_rated: List<Scene> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    private data class ScenesEnvelope(val scenes: List<Scene> = emptyList())

    @JsonClass(generateAdapter = true)
    private data class CategoriesEnvelope(val categories: List<Category> = emptyList())

    @JsonClass(generateAdapter = true)
    private data class PerformersEnvelope(val performers: List<Performer> = emptyList())

    @JsonClass(generateAdapter = true)
    private data class StudiosEnvelope(val studios: List<Studio> = emptyList())

    @JsonClass(generateAdapter = true)
    data class SearchResult(
        val scenes: List<Scene> = emptyList(),
        val performers: List<Performer> = emptyList(),
        val studios: List<Studio> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class DmcaBody(
        val claimant_name: String,
        val claimant_email: String,
        val claimant_org: String = "",
        val claimant_phone: String = "",
        val scene_ids: List<Int> = emptyList(),
        val reported_urls: String,
        val description: String,
        val swear_under_penalty: Boolean,
        val signature: String,
    )

    @JsonClass(generateAdapter = true)
    data class DmcaResult(
        val ok: Boolean = false,
        val case_id: Int = 0,
        val message: String = "",
    )

    private val homeAdapter = moshi.adapter(Home::class.java)
    private val sceneAdapter = moshi.adapter(Scene::class.java)
    private val scenesEnvelopeAdapter = moshi.adapter(ScenesEnvelope::class.java)
    private val categoriesEnvelopeAdapter = moshi.adapter(CategoriesEnvelope::class.java)
    private val performersEnvelopeAdapter = moshi.adapter(PerformersEnvelope::class.java)
    private val studiosEnvelopeAdapter = moshi.adapter(StudiosEnvelope::class.java)
    private val studioAdapter = moshi.adapter(Studio::class.java)
    private val performerAdapter = moshi.adapter(Performer::class.java)
    private val categoryAdapter = moshi.adapter(Category::class.java)
    private val searchAdapter = moshi.adapter(SearchResult::class.java)
    private val dmcaBodyAdapter = moshi.adapter(DmcaBody::class.java)
    private val dmcaResultAdapter = moshi.adapter(DmcaResult::class.java)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun home(): Home? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$BASE_URL/api/xxx/home").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                homeAdapter.fromJson(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    suspend fun scenes(sort: String = "new", offset: Int = 0, limit: Int = 30): List<Scene> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$BASE_URL/api/xxx/scenes?sort=$sort&offset=$offset&limit=$limit")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<Scene>()
                    scenesEnvelopeAdapter.fromJson(resp.body?.string().orEmpty())?.scenes
                        ?: emptyList()
                }
            }.getOrNull() ?: emptyList()
        }

    suspend fun scene(id: Int): Scene? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$BASE_URL/api/xxx/scenes/$id").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                sceneAdapter.fromJson(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$BASE_URL/api/xxx/categories").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<Category>()
                categoriesEnvelopeAdapter.fromJson(resp.body?.string().orEmpty())
                    ?.categories ?: emptyList()
            }
        }.getOrNull() ?: emptyList()
    }

    suspend fun performers(): List<Performer> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$BASE_URL/api/xxx/performers").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<Performer>()
                performersEnvelopeAdapter.fromJson(resp.body?.string().orEmpty())
                    ?.performers ?: emptyList()
            }
        }.getOrNull() ?: emptyList()
    }

    suspend fun studios(): List<Studio> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$BASE_URL/api/xxx/studios").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<Studio>()
                studiosEnvelopeAdapter.fromJson(resp.body?.string().orEmpty())
                    ?.studios ?: emptyList()
            }
        }.getOrNull() ?: emptyList()
    }

    suspend fun search(q: String): SearchResult = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(q, "UTF-8")
            val req = Request.Builder().url("$BASE_URL/api/xxx/search?q=$encoded").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use SearchResult()
                searchAdapter.fromJson(resp.body?.string().orEmpty()) ?: SearchResult()
            }
        }.getOrNull() ?: SearchResult()
    }

    suspend fun submitDmca(body: DmcaBody): DmcaResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = dmcaBodyAdapter.toJson(body).toRequestBody(jsonMedia)
            val req = Request.Builder().url("$BASE_URL/api/xxx/dmca").post(payload).build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = runCatching { org.json.JSONObject(raw).optString("detail") }
                        .getOrNull().orEmpty()
                    return@use DmcaResult(false, 0, detail.ifBlank { "Server error." })
                }
                dmcaResultAdapter.fromJson(raw) ?: DmcaResult(false, 0, "Bad JSON.")
            }
        }.getOrElse { e -> DmcaResult(false, 0, e.message ?: "Network error.") }
    }
}
