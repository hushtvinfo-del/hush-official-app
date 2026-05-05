package com.hushtv.tv.data

import android.content.Context

/**
 * Per-playlist "My List" for movies and series. Keyed by stream_id (movies)
 * or series_id (series) — independent namespaces so collisions are impossible.
 *
 * In-memory cache (Fire Stick perf phase 4)
 * ─────────────────────────────────────────
 * Card composables in `TVMainMenuScreen`, `TVBrowseScreen`, etc.
 * call [isInList] for every visible poster — that's potentially
 * 60+ SharedPreferences reads + string-split parses per recomposition.
 * On Fire TV 4K each prefs read goes through Binder → SystemServer
 * → disk; the cumulative cost is visible as scroll lag.
 *
 * Strategy: parse-once, return-many. We hold a [@Volatile] cached
 * `Set<Int>` per (playlistId, kind) key. Reads return the cached
 * set directly. [toggle] rebuilds the set in-process AND writes
 * to disk in one go. Worst-case staleness is bounded by other
 * processes mutating the same prefs file — which doesn't happen
 * in this app — so the cache is always coherent with our
 * persistent store.
 */
object MyListStore {

    private const val PREFS = "hushtv_my_list"
    private fun keyFor(playlistId: String, kind: String) = "ml_${kind}_$playlistId"

    /** Combined cache key — `playlistId|kind`. Map values are the
     *  parsed integer set the disk string represents. */
    @Volatile
    private var cache: Map<String, Set<Int>> = emptyMap()

    private fun cacheKey(playlistId: String, kind: String) = "$playlistId|$kind"

    fun getAll(ctx: Context, playlistId: String, kind: String): Set<Int> {
        val ck = cacheKey(playlistId, kind)
        cache[ck]?.let { return it }
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyFor(playlistId, kind), null)
        val parsed: Set<Int> = raw?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        // Lock-free update: build a new map and atomically swap. Two
        // threads racing here is fine — they'll both write the same
        // parsed value, last writer wins.
        cache = cache + (ck to parsed)
        return parsed
    }

    fun isInList(ctx: Context, playlistId: String, kind: String, id: Int): Boolean =
        getAll(ctx, playlistId, kind).contains(id)

    fun toggle(ctx: Context, playlistId: String, kind: String, id: Int): Boolean {
        val current = getAll(ctx, playlistId, kind).toMutableSet()
        val nowInList = if (current.contains(id)) {
            current.remove(id); false
        } else {
            current.add(id); true
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(keyFor(playlistId, kind), current.joinToString(","))
            .apply()
        // Update cache atomically so the next [isInList] read on the
        // same render pass sees the new value without going to disk.
        cache = cache + (cacheKey(playlistId, kind) to current.toSet())
        return nowInList
    }
}
