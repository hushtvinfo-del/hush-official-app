package com.hushtv.tv.data

/**
 * Process-scoped one-shot bag of metadata for the next playback
 * session. Set by detail screens just before navigating to
 * [com.hushtv.tv.ui.screens.TVPlayerScreen]; the player consumes
 * it to:
 *   • Save Continue-Watching with the right `kind` (movie/series).
 *   • Save Continue-Watching with the SERIES title for episodes —
 *     not the per-episode title — so TMDB hydration on the home
 *     row can find a matching show.
 *
 * Why a singleton instead of an extra navigation argument: the
 * player route signature is shared between live, movies and
 * series, and reused from the legacy mobile app. Adding 3 more
 * URL-encoded path segments would touch ~30 call sites and create
 * a permanent test-id-style fragility. A volatile in-memory bag
 * is the simplest path that doesn't compromise architecture.
 *
 * Lifecycle: the player calls [consume] on save so a stale value
 * never leaks into the NEXT playback session that didn't set
 * meta. If unset, the player falls back to URL-path detection
 * (`/series/...` vs `/movie/...`) for `kind` and the on-screen
 * `currentName` for the title.
 */
object PlaybackMeta {

    data class Meta(
        /** "movie" or "series". Live channels don't use this. */
        val kind: String,
        /**
         * Parent series title (e.g. "Gold Rush") for episodes —
         * NOT the per-episode title (e.g. "Gold Rush S15E03").
         * Used as the WatchProgressStore.title so TMDB Tv search
         * can find the show.
         *
         * For movies, set to the movie title.
         */
        val displayTitle: String?,
        /** Optional poster URL — falls back to TMDB hydration. */
        val poster: String? = null,
        /** Optional backdrop URL — falls back to TMDB hydration. */
        val backdrop: String? = null,
    )

    @Volatile private var current: Meta? = null

    fun set(meta: Meta?) {
        current = meta
    }

    fun get(): Meta? = current

    /**
     * Returns the current meta and clears it. The player calls
     * this on the FIRST save so the meta doesn't leak into a
     * subsequent unrelated playback session.
     */
    fun consume(): Meta? {
        val out = current
        current = null
        return out
    }
}
