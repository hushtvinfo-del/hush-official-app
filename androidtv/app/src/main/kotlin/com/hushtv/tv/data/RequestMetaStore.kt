package com.hushtv.tv.data

import android.content.Context

/**
 * Per-request TMDB metadata cache, keyed by `request_id`.
 *
 * The Base44 gateway doesn't currently echo back the TMDB id /
 * poster path / release year on `getContentRequests`, so we save
 * that data client-side at submit time. This is what powers the
 * TMDB poster on My Requests rows + the detail screen, and the
 * "Open in library" deep-link if the request later flips to
 * `already_available`.
 *
 * Schema is kept narrow on purpose — each field is small and
 * easy to migrate later if we add gateway-side echo.
 */
object RequestMetaStore {

    private const val PREF = "hushtv_request_meta"

    data class Meta(
        val tmdbId: Int,
        val tmdbType: String,        // "movie" | "tv"
        val posterPath: String?,     // /abc.jpg — no host, no size
        val backdropPath: String?,   // /xyz.jpg
        val releaseYear: Int?,       // 2024
        val title: String,           // canonical TMDB title
        val overview: String?,       // first paragraph (max ~600 chars)
        val imdbId: String?,         // "tt1234567" — powers RPDB rating posters
    )

    fun put(ctx: Context, requestId: String, meta: Meta) {
        if (requestId.isBlank()) return
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putInt("${requestId}_tmdb_id", meta.tmdbId)
            putString("${requestId}_tmdb_type", meta.tmdbType)
            putString("${requestId}_poster", meta.posterPath ?: "")
            putString("${requestId}_backdrop", meta.backdropPath ?: "")
            if (meta.releaseYear != null) putInt("${requestId}_year", meta.releaseYear)
            putString("${requestId}_title", meta.title)
            putString("${requestId}_overview", meta.overview ?: "")
            putString("${requestId}_imdb_id", meta.imdbId ?: "")
            apply()
        }
    }

    fun get(ctx: Context, requestId: String): Meta? {
        if (requestId.isBlank()) return null
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val tmdbId = sp.getInt("${requestId}_tmdb_id", 0)
        if (tmdbId <= 0) return null
        return Meta(
            tmdbId = tmdbId,
            tmdbType = sp.getString("${requestId}_tmdb_type", "movie") ?: "movie",
            posterPath = sp.getString("${requestId}_poster", null)?.takeIf { it.isNotBlank() },
            backdropPath = sp.getString("${requestId}_backdrop", null)?.takeIf { it.isNotBlank() },
            releaseYear = sp.getInt("${requestId}_year", 0).takeIf { it > 0 },
            title = sp.getString("${requestId}_title", "") ?: "",
            overview = sp.getString("${requestId}_overview", null)?.takeIf { it.isNotBlank() },
            imdbId = sp.getString("${requestId}_imdb_id", null)?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Try to recover TMDB metadata from the `additional_info` text
     * the request was originally submitted with. We embed a compact
     * tag so even a fresh install (where the SharedPreferences is
     * empty) can still display the poster.
     *
     * Tag shape — see [encodeTag]:
     *   `[TMDB id=12345 type=movie year=2024 poster=/abc.jpg]`
     */
    fun parseTag(additionalInfo: String?): Meta? {
        if (additionalInfo.isNullOrBlank()) return null
        val match = Regex("\\[TMDB ([^\\]]+)]").find(additionalInfo) ?: return null
        val pairs = match.groupValues[1].split(' ').mapNotNull { token ->
            val eq = token.indexOf('=')
            if (eq <= 0) null else token.substring(0, eq) to token.substring(eq + 1)
        }.toMap()

        val tmdbId = pairs["id"]?.toIntOrNull() ?: return null
        val tmdbType = pairs["type"] ?: "movie"
        return Meta(
            tmdbId = tmdbId,
            tmdbType = tmdbType,
            posterPath = pairs["poster"]?.takeIf { it.isNotBlank() && it != "null" },
            backdropPath = pairs["backdrop"]?.takeIf { it.isNotBlank() && it != "null" },
            releaseYear = pairs["year"]?.toIntOrNull(),
            title = "",
            overview = null,
            imdbId = null,
        )
    }

    /**
     * Compact one-line tag we append to `additional_info` so the
     * gateway round-trips TMDB metadata even when the device cache
     * is cold (re-install / cleared data / new device).
     *
     * Output is ASCII-safe — paths and types contain no spaces or
     * brackets, so the regex parser in [parseTag] is unambiguous.
     */
    fun encodeTag(meta: Meta): String {
        val parts = mutableListOf("id=${meta.tmdbId}", "type=${meta.tmdbType}")
        meta.posterPath?.let { parts += "poster=$it" }
        meta.backdropPath?.let { parts += "backdrop=$it" }
        meta.releaseYear?.let { parts += "year=$it" }
        return "[TMDB ${parts.joinToString(" ")}]"
    }

    /**
     * Strip the `[TMDB ...]` tag back out of additional_info so the
     * UI doesn't show that tag verbatim to the user. Returns the
     * original string with the tag removed and any leftover
     * whitespace trimmed.
     */
    fun stripTag(additionalInfo: String?): String? {
        if (additionalInfo.isNullOrBlank()) return additionalInfo
        return additionalInfo
            .replace(Regex("\\s*\\[TMDB [^\\]]+]\\s*"), " ")
            .trim()
            .ifBlank { null }
    }
}
