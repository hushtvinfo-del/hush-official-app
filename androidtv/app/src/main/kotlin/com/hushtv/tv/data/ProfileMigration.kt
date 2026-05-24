package com.hushtv.tv.data

import android.content.Context
import android.util.Log

/**
 * v1.44.138-dev — One-shot migration that copies legacy (account-
 * scoped) keys to profile-scoped keys when a returning user creates
 * their first HushProfile.
 *
 * Why
 * ───
 * Before v138, every "Continue Watching" / "My List" / "Last
 * Channel" entry was stored under flat keys like
 *   `live:14589`            (WatchProgressStore)
 *   `ml_movie_<playlistId>` (MyListStore)
 *   `playlistId`            (LastChannelStore)
 *
 * v138 prefixes these with `p:<profileId>:` once a profile is
 * active. If we did nothing, existing users would log into v138,
 * be forced through profile setup, and find an EMPTY Continue
 * Watching list — terrifying. This module forwards the legacy
 * values to the new profile's keys exactly once, then sets a
 * migration flag.
 *
 * Pre-conditions for run:
 *   • ActiveProfile.isActive == true       (dev flavor only)
 *   • A profile has been chosen
 *   • The migration flag has not been set on this device
 *
 * Idempotency: gated by the `did_migrate_v1` flag stored ONCE
 * per Xtream username + profileId combo, so:
 *   • Re-running it is a no-op (won't double-migrate).
 *   • Each new profile (Mom, Dad, Kids) does NOT inherit the
 *     legacy data — only the FIRST profile a returning user
 *     creates picks it up. Subsequent profiles start empty,
 *     which matches Netflix behaviour.
 */
object ProfileMigration {
    private const val TAG = "ProfileMigration"
    private const val PREFS = "hushtv_profile_migration"

    /**
     * Run the legacy-to-profile-scoped migration ONCE per device.
     * Safe to call repeatedly. The [profileId] is the destination
     * profile that inherits the legacy data.
     */
    fun runOnce(ctx: Context, xtreamUsername: String, profileId: String) {
        if (!ActiveProfile.isActive) return
        val mark = prefs(ctx)
        if (mark.getBoolean(KEY_DONE, false)) {
            Log.i(TAG, "migration already done — skipping")
            return
        }

        // ── WatchProgressStore — copy every flat-keyed entry into
        //     a profile-scoped clone.
        runCatching {
            val sp = ctx.getSharedPreferences("hushtv_watch_progress", Context.MODE_PRIVATE)
            val all = sp.all.toMap()
            val ed = sp.edit()
            var copied = 0
            for ((k, v) in all) {
                // Skip entries that are already profile-scoped (re-run safety).
                if (k.startsWith("p:")) continue
                // Skip the HushXxxPurge sentinel.
                if (k == "hushxxx_purge_v1_done") continue
                if (v is String) {
                    ed.putString("p:$profileId:$k", v)
                    copied++
                }
            }
            ed.apply()
            Log.i(TAG, "watch progress: copied $copied legacy entries → profile $profileId")
        }.onFailure { Log.w(TAG, "watch progress migration: ${it.message}") }

        // ── MyListStore — keys are `ml_<kind>_<playlistId>` storing
        //     JSON arrays. Copy under p:<id>: prefix.
        runCatching {
            val sp = ctx.getSharedPreferences("hushtv_my_list", Context.MODE_PRIVATE)
            val all = sp.all.toMap()
            val ed = sp.edit()
            var copied = 0
            for ((k, v) in all) {
                if (k.startsWith("p:")) continue
                if (v is String && k.startsWith("ml_")) {
                    ed.putString("p:$profileId:$k", v)
                    copied++
                }
            }
            ed.apply()
            Log.i(TAG, "my-list: copied $copied legacy entries → profile $profileId")
        }.onFailure { Log.w(TAG, "my-list migration: ${it.message}") }

        // ── LastChannelStore — single-keyed (playlistId, streamUrl,
        //     channelName, timestamp). Copy each with prefix.
        runCatching {
            val sp = ctx.getSharedPreferences("hushtv_last_channel", Context.MODE_PRIVATE)
            val pid = sp.getString("playlistId", null)
            val url = sp.getString("streamUrl", null)
            val name = sp.getString("channelName", null)
            val ts = sp.getLong("timestamp", 0L)
            if (pid != null && url != null && name != null) {
                sp.edit()
                    .putString("p:$profileId:playlistId", pid)
                    .putString("p:$profileId:streamUrl", url)
                    .putString("p:$profileId:channelName", name)
                    .putLong("p:$profileId:timestamp", ts)
                    .apply()
                Log.i(TAG, "last-channel: copied → profile $profileId")
            }
        }.onFailure { Log.w(TAG, "last-channel migration: ${it.message}") }

        mark.edit().putBoolean(KEY_DONE, true).apply()
        Log.i(TAG, "migration complete")
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val KEY_DONE = "did_migrate_v1"
}
