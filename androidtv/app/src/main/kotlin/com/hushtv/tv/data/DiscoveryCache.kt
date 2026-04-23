package com.hushtv.tv.data

import android.content.Context

/**
 * Tiny SharedPreferences cache for Discovery card artwork (TMDB
 * backdrops + Xtream posters). Lets the Home screen render hi-res hero
 * art INSTANTLY on cold start instead of waiting for 30+ TMDB searches
 * to finish — we just read what we saw last time, render, then refresh
 * in the background.
 *
 * Keyed by `playlistId:kind` (e.g. "abc123:movie"). Stored as newline-
 * separated URL strings for simplicity.
 */
object DiscoveryCache {

    private const val PREFS = "hushtv_discovery_cache"

    private fun key(playlistId: String, kind: String, bucket: String) =
        "$playlistId:$kind:$bucket"

    fun saveBackdrops(ctx: Context, playlistId: String, kind: String, urls: List<String>) =
        save(ctx, key(playlistId, kind, "backdrops"), urls)

    fun saveposters(ctx: Context, playlistId: String, kind: String, urls: List<String>) =
        save(ctx, key(playlistId, kind, "posters"), urls)

    fun loadBackdrops(ctx: Context, playlistId: String, kind: String): List<String> =
        load(ctx, key(playlistId, kind, "backdrops"))

    fun loadPosters(ctx: Context, playlistId: String, kind: String): List<String> =
        load(ctx, key(playlistId, kind, "posters"))

    fun saveItemCount(ctx: Context, playlistId: String, kind: String, count: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(key(playlistId, kind, "count"), count).apply()
    }

    fun loadItemCount(ctx: Context, playlistId: String, kind: String): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(key(playlistId, kind, "count"), 0)

    private fun save(ctx: Context, key: String, urls: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, urls.joinToString("\n")).apply()
    }

    private fun load(ctx: Context, key: String): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    // ── Genre backdrop cache (keyed by kind + tmdbGenreId) ──────────
    private fun genreKey(kind: String, tmdbGenreId: Int) = "genre:$kind:$tmdbGenreId"

    fun saveGenreBackdrop(ctx: Context, kind: String, tmdbGenreId: Int, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(genreKey(kind, tmdbGenreId), url).apply()
    }

    fun loadGenreBackdrop(ctx: Context, kind: String, tmdbGenreId: Int): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(genreKey(kind, tmdbGenreId), null)
}
