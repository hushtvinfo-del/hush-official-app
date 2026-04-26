package com.hushtv.tv.data

import android.content.Context

/**
 * Tiny pref backing the "auto-open last channel on app launch" toggle.
 *
 * Default: **disabled** (changed in v1.42.16 — was unconditionally on
 * before that). The setting lives in TV Settings → MY CONTENT and the
 * mobile equivalent so users can opt back in.
 */
object AutoResumeStore {
    private const val PREFS = "hushtv_auto_resume"
    private const val K_ENABLED = "enabled"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(K_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(K_ENABLED, value)
            .apply()
    }
}
