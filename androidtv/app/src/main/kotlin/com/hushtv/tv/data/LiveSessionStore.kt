package com.hushtv.tv.data

import android.content.Context

/**
 * Remembers which category + which channel the user was previewing in
 * Live TV, per playlist. Survives:
 *
 *   • Tab / screen navigation (mobile `when(tab)` disposal)
 *   • App backgrounding
 *   • Process death (backed by SharedPreferences)
 *
 * Used by both `MobileLiveHubScreen` and `TVLiveBrowseScreen` so the
 * behaviour is identical across form factors.
 */
object LiveSessionStore {

    private const val PREFS = "hushtv_live_session"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun catKey(playlistId: String) = "cat:$playlistId"
    private fun sidKey(playlistId: String) = "sid:$playlistId"

    /** Last category id the user picked for this playlist. Empty string = "All". */
    fun getCategoryId(ctx: Context, playlistId: String): String =
        prefs(ctx).getString(catKey(playlistId), "") ?: ""

    fun setCategoryId(ctx: Context, playlistId: String, categoryId: String) {
        prefs(ctx).edit().putString(catKey(playlistId), categoryId).apply()
    }

    /** Last channel streamId the user was previewing. -1 if never set. */
    fun getStreamId(ctx: Context, playlistId: String): Int =
        prefs(ctx).getInt(sidKey(playlistId), -1)

    fun setStreamId(ctx: Context, playlistId: String, streamId: Int) {
        prefs(ctx).edit().putInt(sidKey(playlistId), streamId).apply()
    }
}
