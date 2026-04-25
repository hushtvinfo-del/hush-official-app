package com.hushtv.tv.data

import android.content.Context

/**
 * Client-side "hide this request" tracker.
 *
 * The HushTV admin gateway exposes `getContentRequests`,
 * `createContentRequest` and `updateContentRequest` — but no
 * `deleteContentRequest`. Status is locked to one of {pending,
 * in_progress, already_available, added, not_found} so we can't
 * round-trip a "cancelled" state either.
 *
 * For the user this means: when they want to "remove" a request
 * from the app (e.g. a typo, no longer interested), we hide it
 * locally on this device — the request still exists in the admin
 * Base44 panel (admin can fulfill it or close it manually) but it
 * disappears from the user's app: home rail, My Requests list,
 * detail screen, notification banner.
 *
 * Stored as a SharedPreferences string-set keyed by request id.
 * Cleared only via [unhideAll] (debug / future "show hidden"
 * affordance).
 */
object RequestHiddenStore {

    private const val PREF = "hushtv_request_hidden"
    private const val KEY = "hidden_ids"

    fun hidden(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()

    fun isHidden(ctx: Context, id: String): Boolean =
        hidden(ctx).contains(id)

    fun hide(ctx: Context, id: String) {
        if (id.isBlank()) return
        val current = hidden(ctx).toMutableSet()
        current += id
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, current).apply()
    }

    fun unhide(ctx: Context, id: String) {
        if (id.isBlank()) return
        val current = hidden(ctx).toMutableSet()
        if (current.remove(id)) {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY, current).apply()
        }
    }

    fun unhideAll(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    /** Convenience filter for caller side. */
    fun filterVisible(
        ctx: Context,
        requests: List<ContentRequestApi.Request>,
    ): List<ContentRequestApi.Request> {
        val hiddenIds = hidden(ctx)
        if (hiddenIds.isEmpty()) return requests
        return requests.filterNot { it.id in hiddenIds }
    }
}
