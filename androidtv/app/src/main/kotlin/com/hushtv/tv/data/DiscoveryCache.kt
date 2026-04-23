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

    // ── Year backdrop cache (movie release year) ────────────────────
    private fun yearKey(year: Int) = "year:$year"

    fun saveYearBackdrop(ctx: Context, year: Int, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(yearKey(year), url).apply()
    }

    fun loadYearBackdrop(ctx: Context, year: Int): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(yearKey(year), null)

    // ── Collection backdrop cache (TMDB collection id) ──────────────
    private fun collectionKey(collectionId: Int) = "collection:$collectionId"

    fun saveCollectionBackdrop(ctx: Context, collectionId: Int, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(collectionKey(collectionId), url).apply()
    }

    fun loadCollectionBackdrop(ctx: Context, collectionId: Int): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(collectionKey(collectionId), null)

    // ── Discovered collections list (TMDB popularity-seeded) ────────
    // Stored as a pipe-separated tuple-per-line:
    //   "id|name|backdropUrl"
    // Plus a timestamp so we can expire after 7 days.
    private const val DISCOVERED_LIST_KEY = "collections:discovered:list"
    private const val DISCOVERED_TIME_KEY = "collections:discovered:timestamp"

    fun saveDiscoveredCollections(ctx: Context, list: List<DiscoveredCollection>) {
        val serialised = list.joinToString("\n") { c ->
            val name = c.name.replace("|", "‖")  // sanitise field separator
            val url = c.backdropUrl?.replace("|", "‖").orEmpty()
            "${c.id}|$name|$url"
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(DISCOVERED_LIST_KEY, serialised)
            .putLong(DISCOVERED_TIME_KEY, System.currentTimeMillis())
            .apply()
    }

    fun loadDiscoveredCollections(ctx: Context): List<DiscoveredCollection> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(DISCOVERED_LIST_KEY, null) ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 2) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val name = parts.getOrNull(1).orEmpty().replace("‖", "|")
            val url = parts.getOrNull(2).orEmpty().replace("‖", "|").ifBlank { null }
            DiscoveredCollection(id = id, name = name, backdropUrl = url)
        }
    }

    /** True when the discovered-collections cache is older than 7 days. */
    fun shouldRefreshDiscoveredCollections(ctx: Context): Boolean {
        val ts = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(DISCOVERED_TIME_KEY, 0L)
        if (ts == 0L) return true
        val ageMs = System.currentTimeMillis() - ts
        return ageMs > 7L * 24L * 60L * 60L * 1000L
    }
}
