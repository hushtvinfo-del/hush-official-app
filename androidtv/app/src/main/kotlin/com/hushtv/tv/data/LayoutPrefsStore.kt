package com.hushtv.tv.data

import android.content.Context

/**
 * User preference for how the category chooser is presented across
 * Live TV / Movies / Series. Two modes:
 *
 *   • [MODE_TOP] — pinned toolbar at the top with a BROWSE ▾ dropdown
 *     panel (default, modern).
 *   • [MODE_SIDEBAR] — persistent left-hand vertical rail of every
 *     category (classic Tivimate-like layout).
 *
 * Backed by SharedPreferences so the choice survives cold starts and
 * is available synchronously on the UI thread — no loading spinners.
 */
object LayoutPrefsStore {
    const val MODE_TOP = "top"
    const val MODE_SIDEBAR = "sidebar"

    private const val PREFS = "hushtv_layout_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_FIRST_RUN_SHOWN = "first_run_shown"

    /** Current layout mode. Defaults to [MODE_SIDEBAR] for first-time users. */
    fun mode(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_SIDEBAR) ?: MODE_SIDEBAR

    fun setMode(ctx: Context, mode: String) {
        if (mode != MODE_TOP && mode != MODE_SIDEBAR) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode).apply()
    }

    /**
     * Whether the first-run layout chooser has already been shown to
     * this user. Flipped to true the first time they make a choice
     * (or dismiss) so the modal never pops up again on its own.
     *
     * v1.43.84 self-heal: if the user has [KEY_MODE] set (i.e.
     * they picked a layout at some point) but [KEY_FIRST_RUN_SHOWN]
     * is false, that's the SyncEngine v1.43.82/83 regression where
     * `applyDownload(...).clear()` wiped the boolean flag every
     * time sync ran. We auto-promote `firstRunShown` to true on
     * the next call so the modal stops popping up. Subsequent sync
     * uploads use the new type-preserving wire format and the
     * boolean propagates across devices.
     */
    fun firstRunShown(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val flag = sp.getBoolean(KEY_FIRST_RUN_SHOWN, false)
        if (flag) return true
        // Self-heal: if mode was set, the user already made a choice
        // and got bitten by the bug. Restore the flag.
        if (sp.contains(KEY_MODE)) {
            sp.edit().putBoolean(KEY_FIRST_RUN_SHOWN, true).apply()
            return true
        }
        return false
    }

    fun markFirstRunShown(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FIRST_RUN_SHOWN, true).apply()
    }
}
