package com.hushtv.tv.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

/** Persists the list of Xtream accounts ("playlists" in the React app) to SharedPreferences. */
object PlaylistStore {

    private const val PREFS = "hushtv_prefs"
    private const val KEY_PLAYLISTS = "playlists"

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, Playlist::class.java)
    private val adapter = moshi.adapter<List<Playlist>>(listType)

    fun getAll(ctx: Context): List<Playlist> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun add(ctx: Context, p: Playlist) {
        val list = getAll(ctx) + p
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAYLISTS, adapter.toJson(list)).apply()
    }

    fun remove(ctx: Context, id: String) {
        val list = getAll(ctx).filterNot { it.id == id }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAYLISTS, adapter.toJson(list)).apply()
    }

    fun find(ctx: Context, id: String): Playlist? = getAll(ctx).firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString()
}
