package com.hushtv.tv.data

import android.content.Context

/**
 * Tracks whether the user has acknowledged the HushXXX 18+ age gate.
 * Persisted once per device — re-confirmed by clearing prefs /
 * uninstall / clear-data. Admin can reset via Settings → Privacy →
 * Reset adult content confirmation.
 */
object HushXxxAgeGate {
    private const val PREFS = "hushxxx_age_gate"
    private const val KEY_CONFIRMED_AT = "confirmed_at"

    fun isConfirmed(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_CONFIRMED_AT, 0L) > 0L
    }

    fun confirm(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CONFIRMED_AT, System.currentTimeMillis())
            .apply()
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
