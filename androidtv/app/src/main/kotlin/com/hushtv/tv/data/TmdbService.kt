package com.hushtv.tv.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/* ─── TMDB response models ──────────────────────────────────────── */

@JsonClass(generateAdapter = true)
data class TmdbGenre(val id: Int = 0, val name: String = "")

@JsonClass(generateAdapter = true)
data class TmdbCastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String = "",
    val profile_path: String? = null,
    val order: Int = 0,
)

@JsonClass(generateAdapter = true)
data class TmdbCrewMember(
    val id: Int = 0,
    val name: String = "",
    val job: String = "",
    val department: String = "",
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbExternalIds(
    val imdb_id: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbVideo(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbSearchHit(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val popularity: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse(val results: List<TmdbSearchHit> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbRecommendation(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val poster_path: String? = null,
    val vote_average: Double = 0.0,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val media_type: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbRecommendations(val results: List<TmdbRecommendation> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbMovie(
    val id: Int = 0,
    val title: String = "",
    val original_title: String? = null,
    val overview: String = "",
    val tagline: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val runtime: Int? = null,
    val vote_average: Double = 0.0,
    val vote_count: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    val credits: TmdbCredits? = null,
    val recommendations: TmdbRecommendations? = null,
    val external_ids: TmdbExternalIds? = null,
    val videos: TmdbVideos? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbTvSeason(
    val id: Int = 0,
    val name: String = "",
    val season_number: Int = 0,
    val episode_count: Int = 0,
    val poster_path: String? = null,
    val air_date: String? = null,
    val overview: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbTv(
    val id: Int = 0,
    val name: String = "",
    val original_name: String? = null,
    val overview: String = "",
    val tagline: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String? = null,
    val last_air_date: String? = null,
    val episode_run_time: List<Int> = emptyList(),
    val number_of_seasons: Int = 0,
    val number_of_episodes: Int = 0,
    val vote_average: Double = 0.0,
    val vote_count: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    val seasons: List<TmdbTvSeason> = emptyList(),
    val credits: TmdbCredits? = null,
    val recommendations: TmdbRecommendations? = null,
    val external_ids: TmdbExternalIds? = null,
    val videos: TmdbVideos? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbEpisode(
    val id: Int = 0,
    val episode_number: Int = 0,
    val season_number: Int = 0,
    val name: String = "",
    val overview: String? = null,
    val still_path: String? = null,
    val air_date: String? = null,
    val runtime: Int? = null,
    val vote_average: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetail(
    val id: Int = 0,
    val name: String = "",
    val season_number: Int = 0,
    val overview: String? = null,
    val poster_path: String? = null,
    val episodes: List<TmdbEpisode> = emptyList(),
)

/* ─── Service ───────────────────────────────────────────────────── */

/**
 * Thin client for TMDB v3. Uses the app's global OkHttp cache (7-day).
 *
 * Images:  TMDB serves at https://image.tmdb.org/t/p/{size}{path}
 *          where {size} ∈ w185, w500, w780, w1280, original.
 */
object TmdbService {

    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG_BASE = "https://image.tmdb.org/t/p"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    /** Compose a full image URL for a TMDB relative path. */
    fun img(path: String?, size: String = "w500"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$IMG_BASE/$size$it" }

    // ── MOVIES ──────────────────────────────────────────────

    suspend fun getMovie(tmdbId: Int): TmdbMovie? = withContext(Dispatchers.IO) {
        val url = "$BASE/movie/$tmdbId?api_key=${ApiKeys.TMDB}" +
            "&append_to_response=credits,recommendations,external_ids,videos"
        runCatching {
            val body = client.newCall(Request.Builder().url(url).build()).execute()
                .body?.string() ?: return@withContext null
            moshi.adapter(TmdbMovie::class.java).fromJson(body)
        }.getOrNull()
    }

    suspend fun searchMovie(title: String, year: Int? = null): Int? = withContext(Dispatchers.IO) {
        val q = title.substringBeforeLast(" (")
            .substringBeforeLast(" -")
            .substringBeforeLast(" [")
            .trim()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val yearParam = year?.let { "&year=$it" } ?: ""
        val url = "$BASE/search/movie?query=$encoded&include_adult=false$yearParam&api_key=${ApiKeys.TMDB}"
        runCatching {
            val body = client.newCall(Request.Builder().url(url).build()).execute()
                .body?.string() ?: return@withContext null
            moshi.adapter(TmdbSearchResponse::class.java).fromJson(body)?.results
                ?.maxByOrNull { it.popularity }?.id
        }.getOrNull()
    }

    // ── SERIES ──────────────────────────────────────────────

    suspend fun getTv(tmdbId: Int): TmdbTv? = withContext(Dispatchers.IO) {
        val url = "$BASE/tv/$tmdbId?api_key=${ApiKeys.TMDB}" +
            "&append_to_response=credits,recommendations,external_ids,videos"
        runCatching {
            val body = client.newCall(Request.Builder().url(url).build()).execute()
                .body?.string() ?: return@withContext null
            moshi.adapter(TmdbTv::class.java).fromJson(body)
        }.getOrNull()
    }

    suspend fun searchTv(title: String): Int? = withContext(Dispatchers.IO) {
        val q = title.substringBeforeLast(" (")
            .substringBeforeLast(" -")
            .substringBeforeLast(" [")
            .trim()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val url = "$BASE/search/tv?query=$encoded&include_adult=false&api_key=${ApiKeys.TMDB}"
        runCatching {
            val body = client.newCall(Request.Builder().url(url).build()).execute()
                .body?.string() ?: return@withContext null
            moshi.adapter(TmdbSearchResponse::class.java).fromJson(body)?.results
                ?.maxByOrNull { it.popularity }?.id
        }.getOrNull()
    }

    suspend fun getSeason(tvId: Int, seasonNumber: Int): TmdbSeasonDetail? = withContext(Dispatchers.IO) {
        val url = "$BASE/tv/$tvId/season/$seasonNumber?api_key=${ApiKeys.TMDB}"
        runCatching {
            val body = client.newCall(Request.Builder().url(url).build()).execute()
                .body?.string() ?: return@withContext null
            moshi.adapter(TmdbSeasonDetail::class.java).fromJson(body)
        }.getOrNull()
    }

    /** Discover an actor's filmography (movies + TV). Used for cast-click suggestions. */
    suspend fun personCombinedCredits(personId: Int): List<String> = withContext(Dispatchers.IO) {
        val url = "$BASE/person/$personId/combined_credits?api_key=${ApiKeys.TMDB}"
        runCatching {
            val body = client.newCall(Request.Builder().url(url).build()).execute()
                .body?.string() ?: return@withContext emptyList()
            @JsonClass(generateAdapter = true)
            data class CreditEntry(val title: String? = null, val name: String? = null)
            @JsonClass(generateAdapter = true)
            data class CombinedCredits(val cast: List<CreditEntry> = emptyList())
            val parsed = moshi.adapter(CombinedCredits::class.java).fromJson(body)
            parsed?.cast?.mapNotNull { it.title ?: it.name }?.distinct().orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Pull the YouTube key for the first official trailer, if any. */
    fun pickTrailer(videos: TmdbVideos?): String? {
        if (videos == null) return null
        val trailers = videos.results.filter { it.site == "YouTube" && it.type == "Trailer" }
        return trailers.firstOrNull { it.official }?.key ?: trailers.firstOrNull()?.key
    }
}
