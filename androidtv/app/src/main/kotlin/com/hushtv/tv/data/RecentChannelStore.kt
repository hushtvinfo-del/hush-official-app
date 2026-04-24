package com.hushtv.tv.data

import android.content.Context

/**
 * Persists the most-recently-watched live channels per profile.
 * Used by the mobile Live TV hub to show a "Recent" rail above the
 * category list. Keeps at most [MAX] entries in MRU order.
 */
object RecentChannelStore {
    private const val PREFS = "recent_channels"
    private const val MAX = 12

    private fun key(playlistId: String) = "recent_$playlistId"

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
}
