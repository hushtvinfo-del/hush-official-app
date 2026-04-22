package com.hushtv.tv.data

import android.content.Context

/** Per-playlist favourite channels, keyed by stream_id. */
object FavoritesStore {

    private const val PREFS = "hushtv_favorites"
    private fun keyFor(playlistId: String) = "fav_$playlistId"

    fun getAll(ctx: Context, playlistId: String): Set<Int> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyFor(playlistId), null) ?: return emptySet()
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    fun isFavorite(ctx: Context, playlistId: String, streamId: Int): Boolean =
        getAll(ctx, playlistId).contains(streamId)

    fun toggle(ctx: Context, playlistId: String, streamId: Int): Boolean {
        val set = getAll(ctx, playlistId).toMutableSet()
        val nowFav = if (set.contains(streamId)) {
            set.remove(streamId); false
        } else {
            set.add(streamId); true
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(keyFor(playlistId), set.joinToString(","))
            .apply()
        return nowFav
    }
}
