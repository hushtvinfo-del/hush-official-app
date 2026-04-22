package com.hushtv.tv.data

import android.content.Context

/**
 * Persists per-title watch progress in SharedPreferences.
 *
 *  • For movies → stores position (ms) + duration (ms).
 *  • For series episodes → stores position keyed by episode id.
 *
 * The ratio is used to render a cyan arc ring around partially-watched
 * posters, and to populate the "Continue Watching" virtual category.
 */
object WatchProgressStore {

    private const val PREFS = "hushtv_watch_progress"
    /** When progress is this close to the end, treat the title as "finished"
     *  and stop showing it in Continue Watching. */
    private const val FINISH_THRESHOLD = 0.95f
    /** Below this ratio we don't count it as "started" yet. */
    private const val START_THRESHOLD = 0.02f

    data class Entry(
        val streamId: Int,
        val kind: String,          // "movie" | "series"
        val title: String,
        val poster: String?,
        val positionMs: Long,
        val durationMs: Long,
        val lastWatchedAt: Long,
    ) {
        val ratio: Float get() =
            if (durationMs <= 0) 0f
            else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val isInProgress: Boolean get() = ratio in START_THRESHOLD..FINISH_THRESHOLD
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: String, streamId: Int): String = "$kind:$streamId"

    /** Save progress for a movie or series episode. */
    fun save(
        ctx: Context,
        streamId: Int,
        kind: String,
        title: String,
        poster: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val entry = Entry(
            streamId = streamId,
            kind = kind,
            title = title,
            poster = poster,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedAt = System.currentTimeMillis(),
        )
        prefs(ctx).edit()
            .putString(key(kind, streamId), encode(entry))
            .apply()
    }

    /** Fetch progress (0..1) for a given title. 0 = not started or finished. */
    fun getRatio(ctx: Context, streamId: Int, kind: String): Float {
        val e = get(ctx, streamId, kind) ?: return 0f
        return if (e.isInProgress) e.ratio else 0f
    }

    fun get(ctx: Context, streamId: Int, kind: String): Entry? {
        val raw = prefs(ctx).getString(key(kind, streamId), null) ?: return null
        return decode(raw)
    }

    /** Returns all in-progress titles sorted by most-recently-watched first. */
    fun continueWatching(ctx: Context, kind: String? = null): List<Entry> {
        val p = prefs(ctx)
        val entries = p.all.values
            .mapNotNull { if (it is String) decode(it) else null }
            .filter { it.isInProgress }
            .filter { kind == null || it.kind == kind }
            .sortedByDescending { it.lastWatchedAt }
        return entries
    }

    /** Remove a title from Continue Watching (user finished or hid it). */
    fun clear(ctx: Context, streamId: Int, kind: String) {
        prefs(ctx).edit().remove(key(kind, streamId)).apply()
    }

    // Encoding: pipe-separated (unit separator inside text fields).
    private fun encode(e: Entry): String {
        val safe = { s: String -> s.replace('\u001f', ' ').replace('|', '/') }
        return "${e.streamId}\u001f${e.kind}\u001f${safe(e.title)}\u001f" +
            "${e.poster ?: ""}\u001f${e.positionMs}\u001f${e.durationMs}\u001f${e.lastWatchedAt}"
    }

    private fun decode(s: String): Entry? {
        val parts = s.split('\u001f')
        if (parts.size < 7) return null
        return Entry(
            streamId = parts[0].toIntOrNull() ?: return null,
            kind = parts[1],
            title = parts[2],
            poster = parts[3].ifBlank { null },
            positionMs = parts[4].toLongOrNull() ?: 0L,
            durationMs = parts[5].toLongOrNull() ?: 0L,
            lastWatchedAt = parts[6].toLongOrNull() ?: 0L,
        )
    }
}
