package com.hushtv.tv.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    suspend fun getVodInfo(
        host: String, username: String, password: String, streamId: Int
    ): XtreamVodInfo? {
        return runCatching {
            val body = rawCall(host, username, password, mapOf(
                "action" to "get_vod_info",
                "vod_id" to streamId.toString(),
            ))
            moshi.adapter(XtreamVodInfo::class.java).fromJson(repairAndParse(body))
        }.getOrNull()
    }

    suspend fun getSeriesInfo(
        host: String, username: String, password: String, seriesId: String
    ): XtreamSeriesInfo {
        val body = rawCall(host, username, password, mapOf(
            "action" to "get_series_info", "series_id" to seriesId))
        val adapter = moshi.adapter(XtreamSeriesInfo::class.java)
        return adapter.fromJson(repairAndParse(body)) ?: XtreamSeriesInfo()
    }

    /**
     * Disambiguating wrapper around [getSeriesInfo].
     *
     * Why this exists: many Xtream providers carry the SAME show
     * under multiple categories (e.g. "TV Shows / Reality" AND
     * "Top Shows") with a different `series_id` per category. Some
     * of those duplicate IDs are stale — `get_series_info` returns
     * an empty `episodes` map for them — while at least one is the
     * canonical entry that has all the episodes loaded.
     *
     * The Series-tab browse path (one category at a time) tends to
     * land users on the canonical id. The Search path
     * (`get_all_streams`) walks every category, sometimes picking a
     * stale id, and the user sees an empty episode list — even
     * though the show IS in their library.
     *
     * This helper:
     *  1. Tries the user-supplied [seriesId] first (fastest path).
     *  2. If episodes come back empty, fetches the full series list,
     *     finds every candidate whose normalised title matches
     *     [seriesName], skips the one we already tried, and tries
     *     `get_series_info` for each until one returns non-empty
     *     episodes (max [maxAttempts] extra fetches to keep the UI
     *     responsive).
     *  3. Falls back to whatever the original call returned (still
     *     possibly empty) so callers see consistent shape.
     *
     * Returns the [XtreamSeriesInfo] AND the resolved id so the
     * caller can use the resolved id for episode URL construction
     * (episode urls are keyed by episode_id, not series_id, so this
     * is informational — the URL builder doesn't care).
     */
    data class ResolvedSeries(
        val seriesId: String,
        val info: XtreamSeriesInfo,
    )

    suspend fun resolveSeriesInfo(
        host: String,
        username: String,
        password: String,
        seriesId: String,
        seriesName: String,
        maxAttempts: Int = 8,
    ): ResolvedSeries = coroutineScope {
        val first = getSeriesInfo(host, username, password, seriesId)
        if (!first.episodes.isNullOrEmpty()) {
            return@coroutineScope ResolvedSeries(seriesId, first)
        }
        if (seriesName.isBlank()) {
            return@coroutineScope ResolvedSeries(seriesId, first)
        }

        // Build the candidate pool from BOTH sources in parallel:
        //   • `getAllStreams("series")` — the no-category fast list,
        //     same data the search screen uses.
        //   • `getCategories("series")` + per-category fetches —
        //     some providers expose series under category-specific
        //     calls with a DIFFERENT series_id than the no-category
        //     call returns. The Series-tab UI uses these per-category
        //     calls; if those land users on the canonical id while
        //     `getAllStreams` lands them on a stale dupe, we have to
        //     consult the per-category list to recover.
        // Both sources run concurrently; we union their results
        // before walking title matches.
        val allDeferred = async {
            runCatching { getAllStreams(host, username, password, "series") }
                .getOrDefault(emptyList())
        }
        val byCategoryDeferred = async {
            val cats = runCatching { getCategories(host, username, password, "series") }
                .getOrDefault(emptyList())
            if (cats.isEmpty()) emptyList()
            else cats.map { cat ->
                async {
                    // Per-category fetches get a 6-second cap so a
                    // single slow / hanging endpoint can't block the
                    // whole resolver. Most providers respond in < 1s.
                    runCatching {
                        kotlinx.coroutines.withTimeoutOrNull(6_000) {
                            getStreamsForCategory(
                                host, username, password, "series", cat.category_id,
                            )
                        }.orEmpty()
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        val pool = (allDeferred.await() + byCategoryDeferred.await())
            .filter { it.kind == "series" && it.seriesId > 0 }
            .distinctBy { it.seriesId }

        // Title-match filter — three tiers, in order:
        //   1. Exact normalised match (covers bare-titled entries
        //      and short titles where containment-with-year-gate
        //      would over-reject).
        //   2. `isStrongMatch` (containment + year gate, 3+ word
        //      titles).
        //   3. Token-subsequence containment — the needle's
        //      normalised word sequence appears as a contiguous
        //      run of words inside the library entry's normalised
        //      title. Catches the very common Xtream shape where
        //      series are split per-season ("Gold Rush S01",
        //      "Gold Rush S02 2011", "Gold Rush US S03 HD") — none
        //      of which match the bare "Gold Rush" needle via the
        //      first two tiers, but all of which start with the
        //      needle's tokens.
        val seriesIndex = com.hushtv.tv.data.TitleMatcher.buildIndex(pool) { it.title }
        val needleNorm = com.hushtv.tv.data.TitleMatcher.normalize(seriesName)
        val needleTokens = needleNorm.split(' ').filter { it.isNotBlank() }

        fun tokenSubsequence(libNorm: String): Boolean {
            if (needleTokens.isEmpty()) return false
            val libTokens = libNorm.split(' ').filter { it.isNotBlank() }
            if (libTokens.size < needleTokens.size) return false
            for (i in 0..libTokens.size - needleTokens.size) {
                if ((0 until needleTokens.size).all { libTokens[i + it] == needleTokens[it] }) {
                    return true
                }
            }
            return false
        }

        // Score each candidate — HIGHER is better:
        //   • 100 = exact normalised match (bare-titled entry)
        //   • 70  = title starts with the needle's tokens followed by
        //          additional tokens (e.g. "Gold Rush S01" — same
        //          show, just a per-season entry)
        //   • 50  = needle appears as a token subsequence somewhere
        //          inside the library title (e.g. "[USA] Gold Rush HD")
        //   • 30  = passes isStrongMatch's containment-with-year-gate
        //          but didn't qualify for the higher tiers (e.g.
        //          "Gold Rush: White Water" — a SUB-FRANCHISE, NOT
        //          the same show)
        // We FAN OUT all candidates of the BEST TIER ONLY. Only if
        // the best tier returns no winner do we try the next tier.
        // This prevents sub-franchises ("Gold Rush: White Water")
        // from winning the race against the actual main "Gold Rush"
        // when both happen to have episodes loaded.
        fun scoreCandidate(entry: com.hushtv.tv.data.TitleMatcher.LibraryEntry<MediaCard>): Int {
            if (entry.normalized == needleNorm) return 100
            val libTokens = entry.normalized.split(' ').filter { it.isNotBlank() }
            if (libTokens.size > needleTokens.size &&
                (0 until needleTokens.size).all { libTokens[it] == needleTokens[it] }) {
                return 70  // prefix match — same show, different entry
            }
            if (tokenSubsequence(entry.normalized)) return 50
            if (com.hushtv.tv.data.TitleMatcher.isStrongMatch(
                    tmdbTitle = seriesName,
                    tmdbYear = null,
                    libTitle = entry.raw,
                    libYear = entry.year,
                )
            ) return 30
            return 0
        }

        val scored = seriesIndex
            .mapNotNull { entry ->
                val s = scoreCandidate(entry)
                if (s > 0 && entry.payload.seriesId.toString() != seriesId) {
                    Triple(s, entry.payload.seriesId.toString(), entry.raw)
                } else null
            }
            .distinctBy { it.second }
            .sortedByDescending { it.first }

        if (scored.isEmpty()) {
            return@coroutineScope ResolvedSeries(seriesId, first)
        }

        // Walk score tiers from highest to lowest. For each tier,
        // fan out all its candidates in parallel and take the first
        // one that returns non-empty episodes. If the entire tier
        // returns empty, move down to the next.
        val tieredCandidates = scored
            .groupBy { it.first }
            .toSortedMap(compareByDescending { it })

        for ((_, tierEntries) in tieredCandidates) {
            val tierIds = tierEntries
                .map { it.second }
                .take(maxAttempts)
            if (tierIds.isEmpty()) continue
            val deferreds = tierIds.map { candidate ->
                async {
                    val info = runCatching {
                        kotlinx.coroutines.withTimeoutOrNull(8_000) {
                            getSeriesInfo(host, username, password, candidate)
                        }
                    }.getOrNull()
                    candidate to info
                }
            }
            val results = deferreds.awaitAll()
            val winner = results.firstOrNull { (_, info) ->
                info != null && !info.episodes.isNullOrEmpty()
            }
            if (winner != null) {
                return@coroutineScope ResolvedSeries(winner.first, winner.second!!)
            }
        }
        return@coroutineScope ResolvedSeries(seriesId, first)
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

/** Raw extended info we need for `added` timestamp & genre on the VOD card. */
private fun XtreamVod.toCard() = MediaCard(
    id = stream_id.toString(),
    title = name,
    poster = stream_icon,
    rating = rating,
    streamId = stream_id,
    seriesId = 0,
    containerExtension = container_extension ?: "mp4",
    kind = "movie",
    addedTs = added?.toLongOrNull() ?: 0L,
)

private fun XtreamSeries.toCard() = MediaCard(
    id = series_id.toString(),
    title = name,
    poster = cover,
    rating = rating,
    streamId = 0,
    seriesId = series_id,
    containerExtension = null,
    kind = "series",
    addedTs = last_modified?.toLongOrNull() ?: 0L,
)
