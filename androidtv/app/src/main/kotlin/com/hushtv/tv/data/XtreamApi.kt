package com.hushtv.tv.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.util.concurrent.TimeUnit

object XtreamApi {

    /** The Xtream server URL hardcoded in the React app (TVAddAccount.jsx). */
    const val HUSH_HOST = "https://hushvipnew.ink:443"

    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Call this once at app startup (from Application.onCreate) to enable a
     *  shared 20MB on-disk response cache. Dramatically speeds up the second
     *  cold-launch because category/stream lists are served from local disk. */
    fun enableDiskCache(ctx: android.content.Context) {
        val cacheDir = File(ctx.cacheDir, "xtream_http_cache")
        val cache = Cache(cacheDir, maxSize = 20L * 1024 * 1024)
        client = client.newBuilder()
            .cache(cache)
            // Accept cached responses up to 5 min old even if the upstream
            // sends no-cache — this is safe for IPTV category lists which
            // rarely change.
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val resp = chain.proceed(req)
                resp.newBuilder()
                    .header("Cache-Control", "public, max-age=300")
                    .removeHeader("Pragma")
                    .build()
            }
            .build()
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** Raw call to /player_api.php on the given host. Returns response body text. */
    private suspend fun rawCall(
        host: String,
        username: String,
        password: String,
        extra: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val normalizedHost = if (host.startsWith("http")) host else "http://$host"
        val base = "$normalizedHost/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
        extra.forEach { (k, v) -> base.addQueryParameter(k, v) }
        val req = Request.Builder()
            .url(base.build())
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
    }

    /** Robust parse that repairs malformed JSON arrays — mirrors Deno fn logic. */
    private fun repairAndParse(text: String): String {
        return if (text.trim().startsWith("[") && !text.trim().endsWith("]")) "$text]" else text
    }

    suspend fun authenticate(
        host: String,
        username: String,
        password: String
    ): AuthResponse {
        val body = rawCall(host, username, password)
        val adapter = moshi.adapter(AuthResponse::class.java)
        return adapter.fromJson(repairAndParse(body))
            ?: throw RuntimeException("Invalid response from provider")
    }

    suspend fun getCategories(
        host: String,
        username: String,
        password: String,
        kind: String // "live" | "movie" | "series"
    ): List<XtreamCategory> {
        val action = when (kind) {
            "live" -> "get_live_categories"
            "movie" -> "get_vod_categories"
            "series" -> "get_series_categories"
            else -> return emptyList()
        }
        val body = rawCall(host, username, password, mapOf("action" to action))
        val listType = Types.newParameterizedType(List::class.java, XtreamCategory::class.java)
        val adapter = moshi.adapter<List<XtreamCategory>>(listType)
        return adapter.fromJson(repairAndParse(body)) ?: emptyList()
    }

    suspend fun getStreamsForCategory(
        host: String,
        username: String,
        password: String,
        kind: String,
        categoryId: String
    ): List<MediaCard> {
        return when (kind) {
            "live" -> {
                val body = rawCall(host, username, password, mapOf(
                    "action" to "get_live_streams", "category_id" to categoryId))
                val t = Types.newParameterizedType(List::class.java, XtreamLiveStream::class.java)
                val adapter = moshi.adapter<List<XtreamLiveStream>>(t)
                (adapter.fromJson(repairAndParse(body)) ?: emptyList()).map { it.toCard() }
            }
            "movie" -> {
                val body = rawCall(host, username, password, mapOf(
                    "action" to "get_vod_streams", "category_id" to categoryId))
                val t = Types.newParameterizedType(List::class.java, XtreamVod::class.java)
                val adapter = moshi.adapter<List<XtreamVod>>(t)
                (adapter.fromJson(repairAndParse(body)) ?: emptyList()).map { it.toCard() }
            }
            "series" -> {
                val body = rawCall(host, username, password, mapOf(
                    "action" to "get_series", "category_id" to categoryId))
                val t = Types.newParameterizedType(List::class.java, XtreamSeries::class.java)
                val adapter = moshi.adapter<List<XtreamSeries>>(t)
                (adapter.fromJson(repairAndParse(body)) ?: emptyList()).map { it.toCard() }
            }
            else -> emptyList()
        }
    }

    suspend fun getAllStreams(
        host: String, username: String, password: String, kind: String
    ): List<MediaCard> {
        return getStreamsForCategory(host, username, password, kind, "")
            .ifEmpty {
                // Some providers need no category_id → call without it
                when (kind) {
                    "live" -> {
                        val body = rawCall(host, username, password, mapOf("action" to "get_live_streams"))
                        val t = Types.newParameterizedType(List::class.java, XtreamLiveStream::class.java)
                        (moshi.adapter<List<XtreamLiveStream>>(t).fromJson(repairAndParse(body)) ?: emptyList())
                            .map { it.toCard() }
                    }
                    "movie" -> {
                        val body = rawCall(host, username, password, mapOf("action" to "get_vod_streams"))
                        val t = Types.newParameterizedType(List::class.java, XtreamVod::class.java)
                        (moshi.adapter<List<XtreamVod>>(t).fromJson(repairAndParse(body)) ?: emptyList())
                            .map { it.toCard() }
                    }
                    "series" -> {
                        val body = rawCall(host, username, password, mapOf("action" to "get_series"))
                        val t = Types.newParameterizedType(List::class.java, XtreamSeries::class.java)
                        (moshi.adapter<List<XtreamSeries>>(t).fromJson(repairAndParse(body)) ?: emptyList())
                            .map { it.toCard() }
                    }
                    else -> emptyList()
                }
            }
    }

    suspend fun getSeriesInfo(
        host: String, username: String, password: String, seriesId: String
    ): XtreamSeriesInfo {
        val body = rawCall(host, username, password, mapOf(
            "action" to "get_series_info", "series_id" to seriesId))
        val adapter = moshi.adapter(XtreamSeriesInfo::class.java)
        return adapter.fromJson(repairAndParse(body)) ?: XtreamSeriesInfo()
    }

    // URL builders — mirror TVBrowse.jsx + standard Xtream
    fun liveUrl(host: String, user: String, pass: String, streamId: Int): String {
        val h = if (host.startsWith("http")) host else "http://$host"
        return "$h/live/$user/$pass/$streamId.m3u8"
    }
    fun movieUrl(host: String, user: String, pass: String, streamId: Int, ext: String?): String {
        val h = if (host.startsWith("http")) host else "http://$host"
        return "$h/movie/$user/$pass/$streamId.${ext ?: "mp4"}"
    }
    fun episodeUrl(host: String, user: String, pass: String, episodeId: String, ext: String?): String {
        val h = if (host.startsWith("http")) host else "http://$host"
        return "$h/series/$user/$pass/$episodeId.${ext ?: "mp4"}"
    }
}

// ---- Extension helpers -------------------------------------------------------

private fun XtreamLiveStream.toCard() = MediaCard(
    id = stream_id.toString(),
    title = name,
    poster = stream_icon,
    rating = null,
    streamId = stream_id,
    seriesId = 0,
    containerExtension = "m3u8",
    kind = "live"
)

private fun XtreamVod.toCard() = MediaCard(
    id = stream_id.toString(),
    title = name,
    poster = stream_icon,
    rating = rating,
    streamId = stream_id,
    seriesId = 0,
    containerExtension = container_extension ?: "mp4",
    kind = "movie"
)

private fun XtreamSeries.toCard() = MediaCard(
    id = series_id.toString(),
    title = name,
    poster = cover,
    rating = rating,
    streamId = 0,
    seriesId = series_id,
    containerExtension = null,
    kind = "series"
)
