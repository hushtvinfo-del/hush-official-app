package com.hushtv.tv.data

/**
 * Tiny in-memory hand-off for the most-recently-fetched content
 * requests, so the detail screen can render instantly when the user
 * taps a row. Falls back to a fresh `listRequests` call if the cache
 * is cold (e.g. user deep-linked into the detail screen, or the app
 * was process-killed).
 *
 * Lifetime: process-scoped. Cleared on cold start.
 */
object RequestCache {
    @Volatile
    private var snapshot: List<ContentRequestApi.Request> = emptyList()

    @Volatile
    private var fetchedAtMs: Long = 0L

    fun put(requests: List<ContentRequestApi.Request>) {
        snapshot = requests
        fetchedAtMs = System.currentTimeMillis()
    }

    fun all(): List<ContentRequestApi.Request> = snapshot

    fun byId(id: String): ContentRequestApi.Request? =
        snapshot.firstOrNull { it.id == id }

    fun ageMs(): Long =
        if (fetchedAtMs == 0L) Long.MAX_VALUE
        else System.currentTimeMillis() - fetchedAtMs
}
