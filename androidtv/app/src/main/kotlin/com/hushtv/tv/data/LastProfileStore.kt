package com.hushtv.tv.data

import android.content.Context

/**
 * Remembers the most recently used profile (Xtream playlist) id so the app
 * can auto-login and skip the account picker on subsequent launches.
 *
 * Written when the user:
 *   • picks a profile from [TVHomeScreen]
 *   • finishes creating a new profile in [TVAddAccountScreen]
 *
 * Cleared when the user deletes that profile.
 */
object LastProfileStore {

    private const val PREFS = "hushtv_prefs"
    private const val KEY = "last_profile_id"

    fun save(ctx: Context, playlistId: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, playlistId).apply()
    }

    fun load(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY).apply()
    }

    /** Returns the saved profile if it still exists in [PlaylistStore], else null. */
    fun loadValid(ctx: Context): Playlist? {
        val id = load(ctx) ?: return null
        return PlaylistStore.find(ctx, id)
    }
}
