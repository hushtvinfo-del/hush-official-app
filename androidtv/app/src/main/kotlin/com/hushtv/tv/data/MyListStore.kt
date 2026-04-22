package com.hushtv.tv.data

import android.content.Context

/**
 * Per-playlist "My List" for movies and series. Keyed by stream_id (movies)
 * or series_id (series) — independent namespaces so collisions are impossible.
 */
object MyListStore {

    private const val PREFS = "hushtv_my_list"
    private fun keyFor(playlistId: String, kind: String) = "ml_${kind}_$playlistId"

    fun getAll(ctx: Context, playlistId: String, kind: String): Set<Int> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyFor(playlistId, kind), null) ?: return emptySet()
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    fun isInList(ctx: Context, playlistId: String, kind: String, id: Int): Boolean =
        getAll(ctx, playlistId, kind).contains(id)

    fun toggle(ctx: Context, playlistId: String, kind: String, id: Int): Boolean {
        val set = getAll(ctx, playlistId, kind).toMutableSet()
        val nowInList = if (set.contains(id)) {
            set.remove(id); false
        } else {
            set.add(id); true
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(keyFor(playlistId, kind), set.joinToString(","))
            .apply()
        return nowInList
    }
}
