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
 *
 * Tombstones (v1.44.42)
 * ─────────────────────
 * `clear()` no longer removes the row — it writes a tombstone with
 * `deleted = true` and `lastWatchedAt = now` so the [SyncEngine]
 * propagates the deletion to other devices via standard last-write-
 * wins merge. Tombstones are filtered out of [continueWatching] and
 * [get] so the UI never sees them. They are hard-pruned by
 * [pruneOld] after 14 days from `lastWatchedAt`, which is plenty of
 * time for any other device to come online and observe the deletion.
 */
object WatchProgressStore {

    private const val PREFS = "hushtv_watch_progress"
    /** When progress is this close to the end, treat the title as "finished"
     *  and stop showing it in Continue Watching. */
    private const val FINISH_THRESHOLD = 0.95f
    /** Minimum saved position (ms) before an entry is treated as "started". */
    private const val MIN_PROGRESS_MS = 5_000L
    /** Treat a saved duration smaller than this as "untrustworthy". */
    const val MIN_VALID_DURATION_MS = 60_000L
    /** Hard-prune any row (good entry or tombstone) whose
     *  `lastWatchedAt` is older than this. 14 days is enough time
     *  for sibling devices to come online and observe a tombstone. */
    private const val PRUNE_AGE_MS = 14L * 24 * 60 * 60 * 1000

    /**
     * Sentinel titles that should NEVER appear in Continue Watching.
     * These are the literal words "Movie" / "Series" / "movie" /
     * "series" — kind labels, not real titles. Any entry carrying one
     * of these is a corrupt save from a buggy upstream code path
     * (most commonly: a deep-link launch that handed the player
     * `channelName = "Series"` while `PlaybackMeta` was unset, then
     * a periodic / dispose save fired with that string).
     *
     * v1.44.46 only rejected fully-blank titles. v1.44.51 widens the
     * block to these sentinels too. We additionally TOMBSTONE any
     * such entry already on disk (rather than just hard-delete)
     * so the deletion propagates via SyncEngine to every device on
     * the same playlist — fixing the user-reported bug where the
     * bad card kept coming back from the cloud after Clear All.
     */
    private val KIND_SENTINEL_TITLES = setOf(
        "Movie", "movie", "MOVIE",
        "Series", "series", "SERIES",
        "Episode", "episode", "EPISODE",
        "Show", "show", "SHOW",
    )

    data class Entry(
        val streamId: Int,
        val kind: String,          // "movie" | "series"
        val title: String,
        val poster: String?,
        val positionMs: Long,
        val durationMs: Long,
        val lastWatchedAt: Long,
        /** Tombstone flag — true when the user explicitly cleared this
         *  entry. Tombstones are kept in storage so [SyncEngine] can
         *  propagate the deletion to other devices via LWW; they are
         *  filtered out of all read paths and hard-pruned after 14
         *  days by [pruneOld]. */
        val deleted: Boolean = false,
    ) {
        val ratio: Float get() =
            if (durationMs <= 0) 0f
            else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        /** An entry is "in progress" — and therefore visible in Continue
         *  Watching — iff it is NOT a tombstone, the user has watched a
         *  meaningful amount, and they have not finished it. */
        val isInProgress: Boolean get() =
            !deleted && positionMs >= MIN_PROGRESS_MS && ratio < FINISH_THRESHOLD
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: String, streamId: Int): String = "$kind:$streamId"

    // ── In-memory cache ────────────────────────────────────────
    @Volatile
    private var cacheVersion: Long = 0
    @Volatile
    private var lastBuiltVersion: Long = -1
    @Volatile
    private var cachedEntries: Map<String, Entry> = emptyMap()

    private fun ensureCache(ctx: Context) {
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
     * Refuses bogus saves:
     *   • durationMs < 60s
     *   • positionMs out of [0, durationMs]
     *   • title is blank — without a title the home row renders an
     *     orphan "SERIES" placeholder card with no poster, no name,
     *     no information; clearing the list won't help because the
     *     player's dispose-save can resurrect it. Refuse the write
     *     here instead. (v1.44.46 fix for the bare-SERIES-card bug.)
     *
     * Saving always clears the tombstone flag so re-watching a title
     * the user previously cleared brings it back to Continue Watching.
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
            positionMs in 0..durationMs &&
            title.isNotBlank() &&
            title.trim() !in KIND_SENTINEL_TITLES
        if (!sane) {
            // Telemetry: if a save was rejected ONLY because the
            // title was blank or a kind-sentinel, fire a one-shot
            // diagnostic ping so we can spot the upstream bug
            // feeding the player an empty channelName + missing
            // PlaybackMeta. Rate-limited inside CrashReporter to
            // once per app launch. We deliberately DO NOT report
            // duration/position failures — those are usually just
            // early-frame saves when ExoPlayer hasn't decoded a
            // duration yet.
            val titleProblem = title.isBlank() || title.trim() in KIND_SENTINEL_TITLES
            if (titleProblem &&
                durationMs >= MIN_VALID_DURATION_MS &&
                positionMs in 0..durationMs
            ) {
                runCatching {
                    CrashReporter.reportEvent(
                        ctx,
                        if (title.isBlank())
                            "watch_progress_save_blank_title"
                        else
                            "watch_progress_save_sentinel_title",
                        "kind=$kind streamId=$streamId title='$title' " +
                            "positionMs=$positionMs durationMs=$durationMs " +
                            "posterPresent=${!poster.isNullOrBlank()}",
                    )
                }
            }
            return
        }
        val entry = Entry(
            streamId = streamId,
            kind = kind,
            title = title,
            poster = poster,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedAt = System.currentTimeMillis(),
            deleted = false,
        )
        runCatching {
            prefs(ctx).edit()
                .putString(key(kind, streamId), encode(entry))
                .commit()
        }
        bumpCache()
    }

    /** Fetch progress (0..1) for a given title. 0 = not started, finished, or tombstoned. */
    fun getRatio(ctx: Context, streamId: Int, kind: String): Float {
        val e = get(ctx, streamId, kind) ?: return 0f
        return if (e.isInProgress) e.ratio else 0f
    }

    /** Returns the entry for a given title, or null if missing or tombstoned. */
    fun get(ctx: Context, streamId: Int, kind: String): Entry? {
        ensureCache(ctx)
        val e = cachedEntries[key(kind, streamId)] ?: return null
        return if (e.deleted) null else e
    }

    /** Returns all in-progress titles sorted by most-recently-watched first.
     *
     *  Filters out entries with a blank title OR a kind-sentinel title
     *  ("Movie", "Series", etc.) — those are corrupt saves from older
     *  builds (or from a player session that fired before its metadata
     *  was hydrated) and would render as bare placeholder cards with
     *  no poster, no name. See [pruneOld] for the parallel
     *  tombstone-then-delete path that garbage-collects them on the
     *  next read AND propagates the deletion to other devices via
     *  SyncEngine. */
    fun continueWatching(ctx: Context, kind: String? = null): List<Entry> {
        // Opportunistic prune — drops tombstones + stale entries older
        // than 14 days, AND tombstones any kind-sentinel-title corrupt
        // entry so the deletion syncs across devices. Cheap (single
        // prefs scan) and only mutates if something actually expired.
        pruneOld(ctx)
        ensureCache(ctx)
        return cachedEntries.values
            .filter { it.isInProgress }
            .filter { it.title.isNotBlank() && it.title.trim() !in KIND_SENTINEL_TITLES }
            .filter { kind == null || it.kind == kind }
            .sortedByDescending { it.lastWatchedAt }
    }

    /**
     * Tombstone a single entry. The row stays in storage with
     * `deleted = true` and a fresh `lastWatchedAt` so the [SyncEngine]
     * propagates the deletion to other devices on the next push.
     * Hard-pruned after 14 days by [pruneOld].
     */
    fun clear(ctx: Context, streamId: Int, kind: String) {
        ensureCache(ctx)
        val k = key(kind, streamId)
        val existing = cachedEntries[k]
        // Build a tombstone preserving identifying data so the wire
        // payload stays valid. If we don't have an existing row, there's
        // nothing to clear.
        val tombstone = (existing ?: return).copy(
            lastWatchedAt = System.currentTimeMillis(),
            deleted = true,
        )
        runCatching {
            prefs(ctx).edit()
                .putString(k, encode(tombstone))
                .commit()
        }
        bumpCache()
    }

    /**
     * Tombstone every in-progress entry. Used by the "Clear All" UI
     * button on the Continue Watching row. Tombstones sync across
     * devices and are pruned after 14 days.
     */
    fun clearAll(ctx: Context) {
        ensureCache(ctx)
        val now = System.currentTimeMillis()
        val ed = prefs(ctx).edit()
        var changed = false
        for ((k, e) in cachedEntries) {
            if (e.deleted) continue
            val tombstone = e.copy(lastWatchedAt = now, deleted = true)
            ed.putString(k, encode(tombstone))
            changed = true
        }
        if (changed) {
            runCatching { ed.commit() }
            bumpCache()
        }
    }

    /**
     * Hard-remove any row whose `lastWatchedAt` is older than
     * [PRUNE_AGE_MS]. For corrupt entries (blank title OR title
     * equals one of [KIND_SENTINEL_TITLES]) we instead **tombstone**
     * them (set `deleted = true` with `lastWatchedAt = now`) so the
     * SyncEngine propagates the deletion to every other device on
     * the same playlist via standard LWW merge. The tombstone itself
     * is then hard-pruned 14 days later by the normal age check.
     *
     * This fixes the v1.44.50-and-earlier user-reported bug where a
     * corrupt CW entry (e.g. literal title="Series") kept reappearing
     * after Clear All — it was being re-downloaded from the cloud
     * because another device on the playlist still had it. By writing
     * a fresh-timestamp tombstone, we guarantee LWW resolves to the
     * deletion on every device's next sync cycle.
     *
     * Idempotent and cheap; safe to call on every read.
     */
    fun pruneOld(ctx: Context) {
        val now = System.currentTimeMillis()
        val cutoff = now - PRUNE_AGE_MS
        val all = prefs(ctx).all
        val ed = prefs(ctx).edit()
        var pruned = 0
        var tombstoned = 0
        for ((k, v) in all) {
            if (v !is String) continue
            val e = decode(v) ?: continue
            val isStale = e.lastWatchedAt > 0 && e.lastWatchedAt < cutoff
            val isCorrupt = !e.deleted &&
                (e.title.isBlank() || e.title.trim() in KIND_SENTINEL_TITLES)
            when {
                isStale -> {
                    ed.remove(k)
                    pruned++
                }
                isCorrupt -> {
                    // Tombstone (don't hard-delete) so the deletion
                    // syncs to other devices via LWW. The tombstone
                    // itself will hard-prune in 14 days.
                    val tombstone = e.copy(
                        lastWatchedAt = now,
                        deleted = true,
                    )
                    ed.putString(k, encode(tombstone))
                    tombstoned++
                }
            }
        }
        if (pruned > 0 || tombstoned > 0) {
            runCatching { ed.commit() }
            bumpCache()
            // Telemetry — fires once per app launch even if multiple
            // entries got tombstoned. Helps us see how widely the
            // upstream bug spreads across the user base.
            if (tombstoned > 0) {
                runCatching {
                    CrashReporter.reportEvent(
                        ctx,
                        "watch_progress_corrupt_entry_tombstoned",
                        "tombstoned=$tombstoned pruned=$pruned",
                    )
                }
            }
        }
    }

    /**
     * One-shot migration for a legacy CW entry that was saved
     * with kind="movie" when it should have been "series".
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
    // Wire format:
    //   streamId \u001f kind \u001f title \u001f poster \u001f
    //   positionMs \u001f durationMs \u001f lastWatchedAt
    //   [\u001f deleted]                ← optional 8th field, v1.44.42+
    //
    // Older clients writing 7 fields decode with deleted=false, so
    // the wire format is fully backward-compatible.
    private fun encode(e: Entry): String {
        val safe = { s: String -> s.replace('\u001f', ' ').replace('|', '/') }
        val base = "${e.streamId}\u001f${e.kind}\u001f${safe(e.title)}\u001f" +
            "${e.poster ?: ""}\u001f${e.positionMs}\u001f${e.durationMs}\u001f${e.lastWatchedAt}"
        return if (e.deleted) "$base\u001f1" else base
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
            deleted = parts.getOrNull(7) == "1",
        )
    }
}
