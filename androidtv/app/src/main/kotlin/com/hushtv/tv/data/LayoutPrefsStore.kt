package com.hushtv.tv.data

import android.content.Context
import com.hushtv.tv.BuildConfig

/**
 * User preference for how the category chooser is presented across
 * Live TV / Movies / Series. Two modes:
 *
 *   • [MODE_TOP] — pinned toolbar at the top with a BROWSE ▾ dropdown
 *     panel.
 *   • [MODE_SIDEBAR] — persistent left-hand vertical rail of every
 *     category (classic Tivimate-like layout). DEFAULT.
 *
 * Backed by SharedPreferences so the choice survives cold starts and
 * is available synchronously on the UI thread — no loading spinners.
 *
 * v1.44.41 channel split:
 *   • Official channel — locked to [MODE_SIDEBAR]. The first-run
 *     chooser is suppressed and the Settings → Change Layout card
 *     is hidden. End users only ever see the canonical sidebar UI.
 *   • Dev channel — full freedom. The Settings card stays so we can
 *     A/B between layouts. The first-run chooser is also suppressed
 *     here (we already pre-pick sidebar) but can be re-opened from
 *     Settings at any time.
 */
object LayoutPrefsStore {
    const val MODE_TOP = "top"
    const val MODE_SIDEBAR = "sidebar"

    private const val PREFS = "hushtv_layout_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_FIRST_RUN_SHOWN = "first_run_shown"

    /** True if this build allows the user to switch layout modes.
     *  Wired to the gradle product-flavor channel — only `dev` users
     *  see the Settings card. */
    val isLayoutSwitchAllowed: Boolean
        get() = BuildConfig.UPDATE_CHANNEL == "dev"

    /** Current layout mode.
     *
     *  Official channel ALWAYS reports [MODE_SIDEBAR] regardless of
     *  what's persisted in SharedPreferences. This is the
     *  belt-and-braces guarantee — even if a user comes from an older
     *  install where [MODE_TOP] was persisted, they get sidebar on
     *  the very first composition. The persisted value isn't deleted;
     *  if they later flip to a dev build the old preference is still
     *  there.
     *
     *  Dev channel honours the persisted value, defaulting to sidebar
     *  for first-time installs. */
    fun mode(ctx: Context): String {
        if (!isLayoutSwitchAllowed) return MODE_SIDEBAR
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_SIDEBAR) ?: MODE_SIDEBAR
    }

    fun setMode(ctx: Context, mode: String) {
        if (mode != MODE_TOP && mode != MODE_SIDEBAR) return
        // Official channel ignores writes — no UI path can reach this
        // (the Settings card is hidden) but defensive guard here too.
        if (!isLayoutSwitchAllowed) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode).apply()
    }

    /**
     * Whether the first-run layout chooser should be presented to
     * this user.
     *
     * v1.44.41: returns `true` (already shown) for ALL users. The
     * chooser modal is retired. Default is sidebar; the dev-only
     * Settings card is the one entry point for switching from now on.
     */
    fun firstRunShown(ctx: Context): Boolean = true

    fun markFirstRunShown(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FIRST_RUN_SHOWN, true).apply()
    }
}
