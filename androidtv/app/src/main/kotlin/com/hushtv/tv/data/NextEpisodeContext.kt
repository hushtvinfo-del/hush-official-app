package com.hushtv.tv.data

/**
 * Bridges the **series detail screen** (which has the full ordered
 * episode list + TMDB poster artwork) and the **player screen**
 * (which only knows about a single stream URL) so the player can
 * surface a "Play next episode" card 60 s before the current
 * episode ends.
 *
 * v1.44.111 — initial release. Mirrors the pattern of
 * [SubtitleSearchContext]: detail screen writes once on episode play,
 * player screen reads on composition, and as soon as the user takes
 * the prompt (Play next / Stay) we re-write it shifted by one so the
 * NEXT-next prompt is already ready for the chained episode.
 *
 * Singleton process-scoped state — no persistence — because the only
 * client is the very next player composition cycle. If the user
 * leaves the player and comes back through any other entry point
 * (Home, Continue Watching, direct URL), context stays null and the
 * prompt never appears, which is exactly what we want.
 */
object NextEpisodeContext {

    /** A single episode in the playable series sequence. */
    data class Episode(
        /** Xtream episode stream id (used to build the playable URL). */
        val xtreamId: String,
        val container: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        /** Display title, e.g. "In Perpetuity" or fallback to "Episode 3". */
        val title: String,
        /** TMDB still or backdrop URL for the bottom-right card. Null if
         *  we couldn't resolve it — the card falls back to a neutral
         *  series-poster image. */
        val stillUrl: String? = null,
        /** Plot synopsis, ≤ 140 chars after the card trims it. */
        val overview: String? = null,
        /** Runtime in minutes if TMDB knew it. Shown as "42 min". */
        val runtimeMinutes: Int? = null,
    )

    data class Context(
        val displayTitle: String,
        val playlistId: String,
        /** Connection info — required to build the next episode's URL.
         *  We pass these explicitly so the player doesn't have to do a
         *  fresh playlist DB lookup. */
        val host: String,
        val username: String,
        val password: String,
        /** Full ordered list across all seasons in (season asc, episode
         *  asc) order. Indexing is by `currentIndex`. */
        val episodes: List<Episode>,
        val currentIndex: Int,
    ) {
        val current: Episode? = episodes.getOrNull(currentIndex)
        val next: Episode? = episodes.getOrNull(currentIndex + 1)
        val hasNext: Boolean = next != null
    }

    @Volatile
    private var pending: Context? = null

    /** Series detail screen writes BEFORE navigating to the player. */
    fun set(ctx: Context) { pending = ctx }

    /** Player reads + clears once on launch. Returns null for any
     *  non-series playback (movies, live TV, DVR recordings). */
    fun consume(): Context? {
        val c = pending
        pending = null
        return c
    }

    /** Used by the player to advance the context by one episode after
     *  the user accepts the "Play next episode" prompt. The new context
     *  is set as pending so the NEXT player composition (chained
     *  episode) sees it immediately. */
    fun advance(c: Context): Context? {
        val advanced = c.copy(currentIndex = c.currentIndex + 1)
        if (advanced.current == null) return null
        pending = advanced
        return advanced
    }
}
