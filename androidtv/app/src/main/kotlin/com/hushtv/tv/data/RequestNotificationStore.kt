package com.hushtv.tv.data

import android.content.Context

/**
 * Tracks which content-request status changes have already been
 * surfaced to the user via the in-app banner, plus the timestamp of
 * the last server poll.
 *
 * Goal: when a user's request flips to ADDED or ALREADY_AVAILABLE on
 * the admin side, we want to tell them *once* without spamming the
 * banner every cold start. We also want to throttle the gateway
 * round-trip — once every [POLL_INTERVAL_MS] is plenty for a
 * "did anything new arrive?" check.
 */
object RequestNotificationStore {

    private const val PREF = "hushtv_request_notifications"
    private const val KEY_LAST_POLL = "last_poll_ms"
    private const val KEY_SEEN_IDS = "seen_added_ids"

    /** 30 min — chosen so a cold start a few hours later always polls,
     *  but rapid back-to-back launches (e.g. switching apps) don't. */
    const val POLL_INTERVAL_MS = 30L * 60L * 1000L

    fun lastPollMs(ctx: Context): Long =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_POLL, 0L)

    fun setLastPollMs(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_POLL, ms).apply()
    }

    fun shouldPoll(ctx: Context): Boolean =
        System.currentTimeMillis() - lastPollMs(ctx) >= POLL_INTERVAL_MS

    fun seenIds(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_SEEN_IDS, emptySet())
            ?: emptySet()

    fun isSeen(ctx: Context, id: String): Boolean = seenIds(ctx).contains(id)

    fun markSeen(ctx: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val current = seenIds(ctx).toMutableSet()
        current.addAll(ids)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_SEEN_IDS, current).apply()
    }
}
