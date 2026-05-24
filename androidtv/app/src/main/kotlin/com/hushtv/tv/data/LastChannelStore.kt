package com.hushtv.tv.data

import android.content.Context

/** Persists the most recently watched **live** channel so we can auto-resume
 *  into fullscreen playback on the next app launch (Tivimate-style "last TV on"). */
object LastChannelStore {

    private const val PREFS = "hushtv_last_channel"
    private const val K_PLAYLIST_ID = "playlistId"
    private const val K_STREAM_URL = "streamUrl"
    private const val K_CHANNEL_NAME = "channelName"
    private const val K_TIMESTAMP = "timestamp"

    // v1.44.138-dev — Profile-scoped key builder. When the multi-
    // profile system is active, each profile gets its own
    // "last channel" memory ("p:<id>:playlistId" etc). Canada /
    // official: collapses to the v1.44.137 flat keys.
    private fun k(suffix: String): String =
        if (ActiveProfile.isActive) "p:${ActiveProfile.profileIdOrAccount()}:$suffix" else suffix

    data class LastChannel(
        val playlistId: String,
        val streamUrl: String,
        val channelName: String,
        val timestamp: Long
    )

    fun save(ctx: Context, playlistId: String, streamUrl: String, channelName: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(k(K_PLAYLIST_ID), playlistId)
            .putString(k(K_STREAM_URL), streamUrl)
            .putString(k(K_CHANNEL_NAME), channelName)
            .putLong(k(K_TIMESTAMP), System.currentTimeMillis())
            .apply()
    }

    fun load(ctx: Context): LastChannel? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pid = sp.getString(k(K_PLAYLIST_ID), null) ?: return null
        val url = sp.getString(k(K_STREAM_URL), null) ?: return null
        val name = sp.getString(k(K_CHANNEL_NAME), null) ?: return null
        val ts = sp.getLong(k(K_TIMESTAMP), 0L)
        // Only resume if the matching playlist still exists on the device.
        PlaylistStore.find(ctx, pid) ?: return null
        return LastChannel(pid, url, name, ts)
    }

    fun clear(ctx: Context) {
        // v1.44.138-dev — Profile-aware clear. With multi-profile
        // active we only clear THIS profile's last channel — other
        // profiles' "last channel" memories are preserved.
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (ActiveProfile.isActive) {
            sp.edit()
                .remove(k(K_PLAYLIST_ID))
                .remove(k(K_STREAM_URL))
                .remove(k(K_CHANNEL_NAME))
                .remove(k(K_TIMESTAMP))
                .apply()
        } else {
            sp.edit().clear().apply()
        }
    }
}
