package com.hushtv.tv.data

import android.content.Context

/**
 * Persists the user's display name + email used for content requests.
 *
 * The HushTV admin API requires a [customer_email] and [customer_name]
 * with every `createContentRequest` call. Rather than introducing a
 * full sign-up flow, we ask once the first time the user submits a
 * request, save the answer, and never ask again.
 *
 * The user can re-edit these values at any time from the request
 * sheet ("Change") or from the My Requests screen.
 */
object UserContactStore {

    private const val PREF = "hushtv_user_contact"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"

    data class Contact(val name: String, val email: String)

    /** Returns null if the user hasn't provided contact info yet. */
    fun get(ctx: Context): Contact? {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val name = p.getString(KEY_NAME, null)?.trim().orEmpty()
        val email = p.getString(KEY_EMAIL, null)?.trim().orEmpty()
        if (name.isEmpty() || email.isEmpty()) return null
        return Contact(name, email)
    }

    fun set(ctx: Context, name: String, email: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name.trim())
            .putString(KEY_EMAIL, email.trim().lowercase())
            .apply()
    }

    /** Loose RFC-5321-ish check. Good enough to catch obvious typos. */
    fun isValidEmail(s: String): Boolean {
        val trimmed = s.trim()
        if (trimmed.length < 5 || trimmed.length > 320) return false
        val at = trimmed.indexOf('@')
        if (at <= 0 || at == trimmed.lastIndex) return false
        val dot = trimmed.indexOf('.', at)
        return dot > at + 1 && dot < trimmed.lastIndex
    }
}
