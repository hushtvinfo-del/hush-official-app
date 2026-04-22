package com.hushtv.tv.data

/**
 * RPDB (Rating Poster Database) — pre-rendered poster & backdrop images
 * with IMDb, TMDB, Rotten Tomatoes, Metacritic, Trakt etc ratings baked in.
 *
 * Pattern:  https://api.ratingposterdb.com/{APIKEY}/imdb/{TYPE}-default/{IMDB_ID}.jpg
 *           TYPE ∈ "poster" | "background"
 *
 * Returns direct JPG URLs → Coil handles caching & loading.
 */
object RpdbService {

    private const val BASE = "https://api.ratingposterdb.com"

    /** Poster variant with default ratings row at the bottom. */
    fun posterUrl(imdbId: String?): String? {
        val id = normaliseImdbId(imdbId) ?: return null
        return "$BASE/${ApiKeys.RPDB}/imdb/poster-default/$id.jpg"
    }

    /** Background / 16:9 hero art with ratings + certification. */
    fun backgroundUrl(imdbId: String?): String? {
        val id = normaliseImdbId(imdbId) ?: return null
        return "$BASE/${ApiKeys.RPDB}/imdb/background-default/$id.jpg"
    }

    /** IMDb IDs must be prefixed with `tt` and at least 7 digits. */
    private fun normaliseImdbId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        return if (s.startsWith("tt")) s else "tt$s"
    }
}
