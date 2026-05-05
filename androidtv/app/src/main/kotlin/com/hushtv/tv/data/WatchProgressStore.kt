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
    /** Minimum saved position (ms) before an entry is treated as "started".
     *  Tiny floor to avoid spurious 0-position writes from player init —
     *  but low enough that a user who paused after 6 seconds still sees
     *  the entry in Continue Watching when they come back. */
    private const val MIN_PROGRESS_MS = 5_000L
    /** Treat a saved duration smaller than this as "untrustworthy" — the
     *  player probably hadn't fully parsed the manifest yet. ExoPlayer
     *  can briefly report a tiny partial duration during prepare which
     *  used to corrupt the saved ratio and hide entries forever. We now
     *  refuse to overwrite a good entry with a bad one. */
    const val MIN_VALID_DURATION_MS = 60_000L

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
        /** An entry is "in progress" — and therefore visible in Continue
         *  Watching — for as long as the user has watched any meaningful
         *  amount of it AND has not finished it. Per product spec
         *  (v1.43.60): the entry stays in CW until completion (≥95%) or
         *  the user explicitly removes it via long-press. */
        val isInProgress: Boolean get() =
            positionMs >= MIN_PROGRESS_MS && ratio < FINISH_THRESHOLD
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: String, streamId: Int): String = "$kind:$streamId"

    // ── In-memory cache (Fire Stick perf phase 4) ──────────────
    // [getRatio] is called per-poster on every home + browse +
    // detail composition. Each call used to hit prefs + decode
    // the pipe-separated string. With 60 visible posters that's
    // 60 disk reads per recomposition.
    //
    // Cache holds a snapshot of every entry. We invalidate on the
    // single mutation paths ([save], [clear]) by bumping
    // [cacheVersion]; readers check the version on each call and
    // refresh by re-walking [SharedPreferences.getAll] only when
    // the version moved. The result: typical render does zero
    // prefs reads; mutation-then-render does one.
    @Volatile
    private var cacheVersion: Long = 0
    @Volatile
    private var lastBuiltVersion: Long = -1
    @Volatile
    private var cachedEntries: Map<String, Entry> = emptyMap()

    private fun ensureCache(ctx: Context) {
        // If the version matches what we last built FOR, the cache
        // is current — even if it contains zero entries (empty
        // prefs is a perfectly valid steady state).
        if (cacheVersion == lastBuiltVersion) return
        val all = prefs(ctx).all
        val rebuilt = HashMap<String, Entry>(all.size)
        for ((k, v) in all) {
            if (v is String) {
                decode(v)?.let { rebuilt[k] = it }
            }
        }
        cachedEntries = rebuilt
        lastBuiltVersion = cacheVersion
    }

    private fun bumpCache() {
        cacheVersion = cacheVersion + 1
    }

    /**
     * Save progress for a movie or series episode.
     *
     * Refuses to overwrite an existing valid entry with a corrupted one.
     * ExoPlayer can briefly report a tiny partial [durationMs] during
     * prepare/seek; before this guard a single bogus tick could push
     * the saved ratio above 1.0 and hide the entry from Continue
     * Watching forever. Now we keep the previous good entry untouched
     * unless the new save passes the validity gates:
     *
     *   • [durationMs] >= [MIN_VALID_DURATION_MS] (60 s) — anything
     *     under a minute is almost certainly the player misreporting
     *     a partial buffer.
     *   • [positionMs] < [durationMs] — out-of-range positions are
     *     impossible for sane content.
     *
     * If the current save is bogus but a previous good entry exists,
     * we silently keep the old one. If neither old nor new is sane,
     * we skip the write entirely.
     *
     * Durability (v1.43.81): uses `commit()` instead of `apply()` so
     * the bytes are guaranteed on disk before this returns. `apply()`
     * queues the write to a background thread, which means a Fire
     * Stick power-pull / hard reboot mid-tick loses the most recent
     * progress. With `commit()`, the periodic 4-second save in the
     * player loop survives any kind of crash. Each entry is ~150
     * bytes so the synchronous write is sub-millisecond on flash —
     * imperceptible from the LaunchedEffect coroutine that calls it.
     */
    fun save(
        ctx: Context,
        streamId: Int,
        kind: String,
        title: String,
        poster: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val sane = durationMs >= MIN_VALID_DURATION_MS &&
            positionMs in 0..durationMs
        if (!sane) return
        val entry = Entry(
            streamId = streamId,
            kind = kind,
            title = title,
            poster = poster,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedAt = System.currentTimeMillis(),
        )
        // commit() — synchronous, durable. See kdoc above.
        runCatching {
            prefs(ctx).edit()
                .putString(key(kind, streamId), encode(entry))
                .commit()
        }
        bumpCache()
    }

    /** Fetch progress (0..1) for a given title. 0 = not started or finished. */
    fun getRatio(ctx: Context, streamId: Int, kind: String): Float {
        val e = get(ctx, streamId, kind) ?: return 0f
        return if (e.isInProgress) e.ratio else 0f
    }

    fun get(ctx: Context, streamId: Int, kind: String): Entry? {
        ensureCache(ctx)
        return cachedEntries[key(kind, streamId)]
    }

    /** Returns all in-progress titles sorted by most-recently-watched first. */
    fun continueWatching(ctx: Context, kind: String? = null): List<Entry> {
        ensureCache(ctx)
        return cachedEntries.values
            .filter { it.isInProgress }
            .filter { kind == null || it.kind == kind }
            .sortedByDescending { it.lastWatchedAt }
    }

    /** Remove a title from Continue Watching (user finished or hid it). */
    fun clear(ctx: Context, streamId: Int, kind: String) {
        prefs(ctx).edit().remove(key(kind, streamId)).apply()
        bumpCache()
    }

    /**
     * One-shot migration for a legacy CW entry that was saved
     * with kind="movie" when it should have been "series". Deletes
     * the old "movie:streamId" row and inserts a "series:streamId"
     * row with the corrected parent-series title, preserving
     * position, duration and last-watched timestamp.
     *
     * No-op if the entry is already correctly classified.
     *
     * Triggered by [com.hushtv.tv.ui.screens.home.reclassify] on
     * the home Continue Watching row — so the data heals itself
     * the first time the user sees the corrected entry.
     */
    fun relabelMovieToSeries(
        ctx: Context,
        streamId: Int,
        correctedTitle: String,
    ) {
        ensureCache(ctx)
        val oldKey = key("movie", streamId)
        val old = cachedEntries[oldKey] ?: return
        val migrated = old.copy(kind = "series", title = correctedTitle)
        prefs(ctx).edit()
            .remove(oldKey)
            .putString(key("series", streamId), encode(migrated))
            .apply()
        bumpCache()
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
