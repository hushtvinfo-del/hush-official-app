package com.hushtv.tv.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/* ─── TMDB response models ──────────────────────────────────────── */

@JsonClass(generateAdapter = true)
data class TmdbGenre(val id: Int = 0, val name: String = "")

/**
 * A collection surfaced from TMDB — either via `/search/collection` or
 * via popular-movie discovery. Used to populate the Collections list
 * beyond the hand-curated top 20.
 */
data class DiscoveredCollection(
    val id: Int,
    val name: String,
    val backdropUrl: String?,
)

// ── Internal TMDB response wrappers used by discovery ───────────────
// Must be top-level so Moshi reflection can pick up their metadata;
// local/nested classes inside suspend fun bodies can lose it.

@JsonClass(generateAdapter = true)
internal data class TmdbMovieListItem(val id: Int = 0)

@JsonClass(generateAdapter = true)
internal data class TmdbMovieListResp(val results: List<TmdbMovieListItem> = emptyList())

@JsonClass(generateAdapter = true)
internal data class TmdbBelongsToCollection(
    val id: Int = 0,
    val name: String = "",
    val backdrop_path: String? = null,
    val poster_path: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class TmdbMovieDetailWithCollection(
    val belongs_to_collection: TmdbBelongsToCollection? = null,
)

@JsonClass(generateAdapter = true)
internal data class TmdbCollectionSearchHit(
    val id: Int = 0,
    val name: String = "",
    val backdrop_path: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class TmdbCollectionSearchResp(
    val results: List<TmdbCollectionSearchHit> = emptyList(),
)

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
    val backdrop_path: String? = null,
    val poster_path: String? = null,
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
data class TmdbCollectionPart(
    val id: Int = 0,
    val title: String = "",
    val release_date: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val overview: String = "",
)

@JsonClass(generateAdapter = true)
data class TmdbCollectionDetail(
    val id: Int = 0,
    val name: String = "",
    val overview: String = "",
    val backdrop_path: String? = null,
    val poster_path: String? = null,
    val parts: List<TmdbCollectionPart> = emptyList(),
)


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

    /**
     * Resolve TMDB backdrop URLs (w1280) for a batch of Xtream titles in
     * parallel. Strips language prefixes, quality tags and years, then
     * searches TMDB and picks the most popular hit with a backdrop. Used
     * to paint the Home > Discovery hero with hi-res landscape artwork
     * instead of low-res portrait posters.
     */
    suspend fun backdropsForTitles(
        titles: List<String>,
        kind: String,        // "movie" or "series"
        limit: Int = 6,
    ): List<String> = withContext(Dispatchers.IO) {
        if (titles.isEmpty()) return@withContext emptyList()
        val clean = titles.take(limit * 3).map { normaliseTitleForSearch(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val endpoint = if (kind == "series") "search/tv" else "search/movie"
        val hits = clean.map { q ->
            runCatching {
                val encoded = java.net.URLEncoder.encode(q, "UTF-8")
                val url = "$BASE/$endpoint?query=$encoded&include_adult=false&api_key=${ApiKeys.TMDB}"
                val body = client.newCall(Request.Builder().url(url).build()).execute()
                    .body?.string() ?: return@runCatching null
                moshi.adapter(TmdbSearchResponse::class.java).fromJson(body)?.results
                    ?.filter { !it.backdrop_path.isNullOrBlank() }
                    ?.maxByOrNull { it.popularity }
                    ?.backdrop_path
            }.getOrNull()
        }
        hits.filterNotNull().distinct().mapNotNull { img(it, "w1280") }.take(limit)
    }

    /**
     * Fetch TMDB watch-provider metadata (logo + name) for a specific
     * `providerId`. Returns a URL for the provider's logo (w154 sized —
     * smallest resolution that still looks crisp on a 1080p TV card).
     *
     * Uses the `/watch/providers/{kind}` endpoint which returns ALL
     * providers in one call; we then filter by ID. Cached by the caller.
     */
    suspend fun watchProviderLogo(providerId: Int, kind: String): String? =
        watchProviderLogos(kind)[providerId]

    // Simple in-memory cache so successive lookups for 7 providers
    // result in at most TWO HTTP calls (one per kind).
    private val providerLogoCache = mutableMapOf<String, Map<Int, String>>()

    suspend fun watchProviderLogos(kind: String): Map<Int, String> =
        withContext(Dispatchers.IO) {
            val endpoint = if (kind == "series") "watch/providers/tv" else "watch/providers/movie"
            providerLogoCache[endpoint]?.let { return@withContext it }
            val url = "$BASE/$endpoint?language=en-US&watch_region=US&api_key=${ApiKeys.TMDB}"
            val body = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute()
                    .body?.string()
            }.getOrNull() ?: return@withContext emptyMap<Int, String>()

            @JsonClass(generateAdapter = true)
            data class Provider(
                val provider_id: Int = 0,
                val provider_name: String? = null,
                val logo_path: String? = null,
            )
            @JsonClass(generateAdapter = true)
            data class Response(val results: List<Provider> = emptyList())
            val parsed = runCatching {
                moshi.adapter(Response::class.java).fromJson(body)
            }.getOrNull() ?: return@withContext emptyMap<Int, String>()
            val map = parsed.results
                .filter { !it.logo_path.isNullOrBlank() }
                .associate { it.provider_id to ("$IMG_BASE/w154${it.logo_path}") }
            providerLogoCache[endpoint] = map
            map
        }

    /**
     * Resolve a high-res backdrop (w1280) for each TMDB genre ID by
     * calling `/discover/{movie|tv}?with_genres={id}&sort_by=popularity.desc`
     * in parallel. Picks the most popular title with a `backdrop_path`.
     * Returns `{genreId → URL}` for ids where we found something.
     */
    suspend fun backdropsForGenres(
        kind: String,
        genreIds: List<Int>,
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        if (genreIds.isEmpty()) return@withContext emptyMap()
        val endpoint = if (kind == "series") "discover/tv" else "discover/movie"
        // Run all lookups concurrently.
        val deferred = genreIds.map { gid ->
            async {
                runCatching {
                    val url = "$BASE/$endpoint?with_genres=$gid&sort_by=popularity.desc" +
                        "&include_adult=false&include_video=false&language=en-US" +
                        "&api_key=${ApiKeys.TMDB}"
                    val body = client.newCall(Request.Builder().url(url).build()).execute()
                        .body?.string() ?: return@runCatching null
                    val parsed = moshi.adapter(TmdbSearchResponse::class.java).fromJson(body)
                    val backdropPath = parsed?.results
                        ?.filter { !it.backdrop_path.isNullOrBlank() }
                        ?.firstOrNull()
                        ?.backdrop_path
                    val imgUrl = img(backdropPath, "w1280")
                    if (imgUrl != null) gid to imgUrl else null
                }.getOrNull()
            }
        }
        deferred.awaitAll().filterNotNull().toMap()
    }

    /**
     * Resolve hi-res backdrops for specific movie release years in
     * parallel. Uses `/discover/movie?primary_release_year=YYYY` to
     * find the most popular movie of that year with a backdrop.
     * Returns `{year → w1280 URL}`.
     */
    suspend fun backdropsForYears(years: List<Int>): Map<Int, String> =
        withContext(Dispatchers.IO) {
            if (years.isEmpty()) return@withContext emptyMap()
            val deferred = years.map { yr ->
                async {
                    runCatching {
                        val url = "$BASE/discover/movie?primary_release_year=$yr" +
                            "&sort_by=popularity.desc&include_adult=false" +
                            "&include_video=false&language=en-US" +
                            "&api_key=${ApiKeys.TMDB}"
                        val body = client.newCall(Request.Builder().url(url).build())
                            .execute().body?.string() ?: return@runCatching null
                        val parsed = moshi.adapter(TmdbSearchResponse::class.java)
                            .fromJson(body)
                        val backdropPath = parsed?.results
                            ?.filter { !it.backdrop_path.isNullOrBlank() }
                            ?.firstOrNull()?.backdrop_path
                        val imgUrl = img(backdropPath, "w1280")
                        if (imgUrl != null) yr to imgUrl else null
                    }.getOrNull()
                }
            }
            deferred.awaitAll().filterNotNull().toMap()
        }

    /**
     * Fetch the backdrop URL for each TMDB collection ID in parallel.
     * Uses `/collection/{id}` which returns the collection's metadata
     * including `backdrop_path`. Returns `{collectionId → w1280 URL}`.
     */
    suspend fun backdropsForCollections(collectionIds: List<Int>): Map<Int, String> =
        withContext(Dispatchers.IO) {
            if (collectionIds.isEmpty()) return@withContext emptyMap()
            val deferred = collectionIds.map { cid ->
                async {
                    runCatching {
                        val url = "$BASE/collection/$cid?language=en-US&api_key=${ApiKeys.TMDB}"
                        val body = client.newCall(Request.Builder().url(url).build())
                            .execute().body?.string() ?: return@runCatching null
                        val parsed = moshi.adapter(TmdbCollectionDetail::class.java)
                            .fromJson(body)
                        val imgUrl = img(parsed?.backdrop_path, "w1280")
                        if (imgUrl != null) cid to imgUrl else null
                    }.getOrNull()
                }
            }
            deferred.awaitAll().filterNotNull().toMap()
        }

    /**
     * Fetch the parts (movies) of a single TMDB collection. Returns the
     * list of `{title, releaseYear, posterUrl}` for each part so the
     * caller can match them against an Xtream library.
     */
    suspend fun getCollectionParts(collectionId: Int): List<TmdbCollectionPart> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE/collection/$collectionId?language=en-US&api_key=${ApiKeys.TMDB}"
                val body = client.newCall(Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@withContext emptyList()
                moshi.adapter(TmdbCollectionDetail::class.java).fromJson(body)
                    ?.parts
                    ?.sortedBy { it.release_date ?: "9999" }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }

    /**
     * Discover popular movie collections by scanning the TMDB popular-
     * movies list and pulling `belongs_to_collection` from each movie.
     * Returns `{collectionId → DiscoveredCollection}` deduped.
     *
     * Fires `pages` concurrent `/movie/popular` calls, then up to
     * `pages * 20` concurrent `/movie/{id}` calls to extract the
     * collection metadata. All parallel on Dispatchers.IO so the
     * whole run completes in ~3-5 s on a decent connection.
     */
    suspend fun discoverPopularCollections(pages: Int = 5): List<DiscoveredCollection> =
        withContext(Dispatchers.IO) {
            val listAdapter = moshi.adapter(TmdbMovieListResp::class.java)
            val detailAdapter = moshi.adapter(TmdbMovieDetailWithCollection::class.java)

            // Step 1: fetch N pages of popular movies concurrently.
            val movieIds = (1..pages).map { page ->
                async {
                    runCatching {
                        val url = "$BASE/movie/popular?language=en-US&page=$page&api_key=${ApiKeys.TMDB}"
                        val body = client.newCall(Request.Builder().url(url).build())
                            .execute().body?.string() ?: return@runCatching emptyList<Int>()
                        listAdapter.fromJson(body)?.results?.map { it.id } ?: emptyList()
                    }.onFailure {
                        android.util.Log.w("TmdbDiscover", "popular page $page failed", it)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinct()

            android.util.Log.i("TmdbDiscover", "fetched ${movieIds.size} popular movies")

            // Step 2: fetch each movie's belongs_to_collection field in parallel.
            // Batch into chunks of 20 concurrent requests to be polite.
            val discovered = movieIds.chunked(20).flatMap { batch ->
                batch.map { mid ->
                    async {
                        runCatching {
                            val url = "$BASE/movie/$mid?language=en-US&api_key=${ApiKeys.TMDB}"
                            val body = client.newCall(Request.Builder().url(url).build())
                                .execute().body?.string() ?: return@runCatching null
                            val parsed = detailAdapter.fromJson(body)
                            val b = parsed?.belongs_to_collection
                            if (b != null && b.id > 0 && b.name.isNotBlank()) {
                                DiscoveredCollection(
                                    id = b.id,
                                    name = b.name,
                                    backdropUrl = img(b.backdrop_path, "w1280"),
                                )
                            } else null
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }

            // Step 3: dedupe by id, preserve insertion order (popularity rank).
            val seen = linkedSetOf<Int>()
            val final = discovered.filter { seen.add(it.id) }
            android.util.Log.i("TmdbDiscover", "discovered ${final.size} unique collections")
            final
        }

    /**
     * Resolve a named franchise to its TMDB collection ID + backdrop
     * via `/search/collection`. Used when we have a franchise display
     * name but don't want to guess the ID.
     */
    suspend fun searchCollection(query: String): DiscoveredCollection? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$BASE/search/collection?query=$encoded&language=en-US&api_key=${ApiKeys.TMDB}"
                val body = client.newCall(Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@withContext null
                val adapter = moshi.adapter(TmdbCollectionSearchResp::class.java)
                val hit = adapter.fromJson(body)?.results
                    ?.firstOrNull { it.id > 0 && it.name.isNotBlank() }
                if (hit != null) {
                    DiscoveredCollection(
                        id = hit.id,
                        name = hit.name,
                        backdropUrl = img(hit.backdrop_path, "w1280"),
                    )
                } else null
            }.getOrNull()
        }

    /**
     * Heavy normaliser for Xtream-style messy titles:
     *   "[EN] VIP | Den of Thieves (2018)"  →  "Den of Thieves"
     * Strips language/country prefixes like "US |", "EN -", quality tags
     * (4K, HD, FHD, UHD), trailing years in parens, bracketed tags, and
     * collapses whitespace.
     */
    private fun normaliseTitleForSearch(raw: String): String {
        var s = raw
        s = s.replace(Regex("""^\s*\[[^]]*]\s*"""), "")      // leading [TAG]
        s = s.replace(Regex("""^\s*[A-Z]{2,3}\s*[|:\-]\s*"""), "") // "EN | " / "US - "
        s = s.replace(Regex("""\s*\|\s*.*$"""), "")          // anything after | 
        s = s.replace(Regex("""\s*\(\d{4}\)\s*$"""), "")     // trailing (2019)
        s = s.replace(Regex("""\b(4K|UHD|FHD|HD|HDR|DV|SDR)\b""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""\s+"""), " ").trim()
        return s
    }
}
