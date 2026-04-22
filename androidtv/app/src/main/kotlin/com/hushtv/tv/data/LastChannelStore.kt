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

    data class LastChannel(
        val playlistId: String,
        val streamUrl: String,
        val channelName: String,
        val timestamp: Long
    )

    fun save(ctx: Context, playlistId: String, streamUrl: String, channelName: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_PLAYLIST_ID, playlistId)
            .putString(K_STREAM_URL, streamUrl)
            .putString(K_CHANNEL_NAME, channelName)
            .putLong(K_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun load(ctx: Context): LastChannel? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pid = sp.getString(K_PLAYLIST_ID, null) ?: return null
        val url = sp.getString(K_STREAM_URL, null) ?: return null
        val name = sp.getString(K_CHANNEL_NAME, null) ?: return null
        val ts = sp.getLong(K_TIMESTAMP, 0L)
        // Only resume if the matching playlist still exists on the device.
        PlaylistStore.find(ctx, pid) ?: return null
        return LastChannel(pid, url, name, ts)
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
