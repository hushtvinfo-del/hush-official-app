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

    /** Current layout mode. Defaults to [MODE_TOP] for first-time users. */
    fun mode(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_TOP) ?: MODE_TOP

    fun setMode(ctx: Context, mode: String) {
        if (mode != MODE_TOP && mode != MODE_SIDEBAR) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode).apply()
    }

    /**
     * Whether the first-run layout chooser has already been shown to
     * this user. Flipped to true the first time they make a choice
     * (or dismiss) so the modal never pops up again on its own.
     */
    fun firstRunShown(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_RUN_SHOWN, false)

    fun markFirstRunShown(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FIRST_RUN_SHOWN, true).apply()
    }
}
