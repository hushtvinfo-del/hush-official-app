package com.hushtv.tv.data

import android.content.Context

/**
 * Bridges the **detail screens** (which know movie / series metadata)
 * and the **player** (which only gets a stream URL). Detail screens
 * write a [Query] here right before navigating to the player; the
 * player reads it once at composition and clears it.
 *
 * Singleton process-scoped state — no persistence — because the only
 * client of a given query is the very next composition cycle.
 *
 * Why not pass via nav route? The route signature is shared between
 * Live TV (no metadata), Movies (title+year), and Series
 * (title+season+episode). Inflating it with optional path segments
 * makes the routes brittle and breaks nothing-special-here playback.
 */
object SubtitleSearchContext {

    data class Query(
        val title: String,
        val year: Int? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        /** "movie" or "episode"; everything else (e.g. live) hides the UI. */
        val kind: String = "movie",
    )

    @Volatile
    private var pending: Query? = null

    fun set(q: Query) { pending = q }

    /** Returns and clears any pending query. Player calls once on launch. */
    fun consume(): Query? {
        val q = pending
        pending = null
        return q
    }

    fun peek(): Query? = pending
}

/**
 * Persists which subtitle language the user prefers for OpenSubtitles
 * downloads. Defaults to English; the user can change it in Settings
 * or via the "More languages" subscreen of the download dialog.
 */
object SubtitleLangPrefStore {

    private const val PREF = "hushtv_sub_lang"
    private const val KEY = "lang"
    private const val DEFAULT = "en"

    fun get(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    fun set(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, lang.lowercase())
            .apply()
    }
}
