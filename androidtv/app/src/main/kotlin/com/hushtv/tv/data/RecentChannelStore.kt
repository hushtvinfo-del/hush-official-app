package com.hushtv.tv.data

import android.content.Context

/**
 * Persists the most-recently-watched live channels per profile.
 *
 * Stores two things:
 *  1. An ordered MRU list of streamIds (MAX entries).
 *  2. For each streamId, a lightweight cached {name, poster} tuple so
 *     the mobile Home "Channel History" rail can render without a
 *     round-trip to Xtream. Meta is refreshed every time the user
 *     selects that channel, so it always reflects the latest logo
 *     the provider is serving.
 */
object RecentChannelStore {
    private const val PREFS = "recent_channels"
    private const val MAX = 12

    private fun key(playlistId: String) = "recent_$playlistId"
    private fun metaKey(playlistId: String, streamId: Int) = "meta_${playlistId}_$streamId"

    fun getAll(ctx: Context, playlistId: String): List<Int> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(playlistId), null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }.take(MAX)
    }

    fun pushFront(ctx: Context, playlistId: String, streamId: Int) {
        if (streamId <= 0) return
        val current = getAll(ctx, playlistId).filter { it != streamId }
        val next = (listOf(streamId) + current).take(MAX)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(playlistId), next.joinToString(","))
            .apply()
    }

    data class Meta(val name: String, val poster: String?)

    fun setMeta(ctx: Context, playlistId: String, streamId: Int, name: String, poster: String?) {
        if (streamId <= 0 || name.isBlank()) return
        // "name|||poster" — pipes are not legal in stream titles coming
        // from Xtream so they're safe as a separator.
        val value = name.replace("|||", "|") + "|||" + (poster ?: "")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(metaKey(playlistId, streamId), value)
            .apply()
    }

    fun getMeta(ctx: Context, playlistId: String, streamId: Int): Meta? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(metaKey(playlistId, streamId), null)
            ?: return null
        val parts = raw.split("|||", limit = 2)
        val name = parts.getOrNull(0).orEmpty().trim()
        if (name.isBlank()) return null
        val poster = parts.getOrNull(1)?.ifBlank { null }
        return Meta(name, poster)
    }
}
