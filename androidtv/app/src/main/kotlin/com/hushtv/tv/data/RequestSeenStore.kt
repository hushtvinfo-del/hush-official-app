package com.hushtv.tv.data

import android.content.Context

/**
 * Per-request "seen" tracker. Powers two UX features:
 *
 *  1. **Unread badges** on the My Requests list — a row shows a "NEW"
 *     pill any time its current `(status, adminResponse)` signature
 *     differs from the last value the user has acknowledged.
 *
 *  2. **Status-change notifications** (`RequestNotificationHost`) —
 *     the in-app banner only fires when `(status, adminResponse)` has
 *     changed since last seen, so the user sees each new admin
 *     update exactly once.
 *
 * Acknowledgement happens automatically when:
 *   • the user taps a row to open the detail screen, or
 *   • the notification banner renders the request (whichever comes
 *     first).
 *
 * Storage key shape: `sig_<requestId>` → `"<status>|<sha-ish hash of
 * adminResponse>"`. We never store the raw admin response so a stray
 * `getAll()` doesn't dump customer-facing text.
 */
object RequestSeenStore {

    private const val PREF = "hushtv_request_seen"
    private const val PREFIX = "sig_"

    /**
     * Compact "version stamp" for a request. Anything that would
     * reasonably warrant re-notifying the user goes in here:
     * status changes (pending → added) and admin response edits.
     * Two requests with identical status + identical admin notes
     * produce identical signatures.
     */
    fun signatureFor(status: ContentRequestApi.Status, adminResponse: String?): String {
        val notes = adminResponse?.trim().orEmpty()
        // Use Java's hashCode (32-bit) as a cheap fingerprint. Collisions
        // would only mean we miss showing one update, which is preferable
        // to storing arbitrary admin text in plaintext SharedPreferences.
        return "${status.storage}|${notes.hashCode()}"
    }

    fun signatureFor(req: ContentRequestApi.Request): String =
        signatureFor(req.status, req.adminResponse)

    fun lastSeen(ctx: Context, requestId: String): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(PREFIX + requestId, null)

    fun isUnseen(ctx: Context, req: ContentRequestApi.Request): Boolean =
        lastSeen(ctx, req.id) != signatureFor(req)

    /** Marks a single request signature as seen. */
    fun markSeen(ctx: Context, req: ContentRequestApi.Request) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX + req.id, signatureFor(req))
            .apply()
    }

    /** Bulk mark — used after the notification banner renders. */
    fun markSeen(ctx: Context, requests: Collection<ContentRequestApi.Request>) {
        if (requests.isEmpty()) return
        val editor = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        for (r in requests) editor.putString(PREFIX + r.id, signatureFor(r))
        editor.apply()
    }

    /** Returns the subset of [requests] the user has not yet acknowledged. */
    fun filterUnseen(ctx: Context, requests: List<ContentRequestApi.Request>):
        List<ContentRequestApi.Request> {
        if (requests.isEmpty()) return emptyList()
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return requests.filter {
            sp.getString(PREFIX + it.id, null) != signatureFor(it)
        }
    }
}
