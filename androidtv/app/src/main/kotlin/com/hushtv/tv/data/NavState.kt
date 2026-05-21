package com.hushtv.tv.data

/**
 * Process-scoped state that survives navigation (but not app death). Used to
 * preserve "where I was" when navigating between the Live Browse screen and
 * the fullscreen Player — e.g. which category was selected, which channel had
 * focus, what the adjacent channels are (so CH+/- zap works) and what channel
 * we were last watching (so double-back toggles to it).
 */
object NavState {

    // ─── Live Browse memory ──────────────────────────────────────────────
    var browsePlaylistId: String = ""
    var selectedCategoryIndex: Int = 0
    var focusedChannelIndex: Int = 0

    // ─── Live playback memory ────────────────────────────────────────────
    /** Channels of the category currently being watched. Fullscreen
     *  player reads this to implement CH+ / CH- zapping. */
    var liveChannels: List<MediaCard> = emptyList()
    /** Index of the channel currently playing (within liveChannels). */
    var currentChannelIndex: Int = -1
    /** Index of the previously-played channel, for double-back "last" toggle. */
    var previousChannelIndex: Int = -1

    fun rememberPlayback(index: Int) {
        if (currentChannelIndex >= 0 && currentChannelIndex != index) {
            previousChannelIndex = currentChannelIndex
        }
        currentChannelIndex = index
    }

    /** Returns the next channel after current (wraps). */
    fun nextChannel(): MediaCard? {
        if (liveChannels.isEmpty()) return null
        // v1.44.116 — guard against stale currentChannelIndex when the
        // user has just switched Xtream playlists / categories while
        // the player is still alive. Without this, we crashed with
        // ArrayIndexOutOfBoundsException: length=3; index=11 because
        // currentChannelIndex retained the old (longer) category's
        // index and overflowed the new (shorter) channel list.
        val safeCur = currentChannelIndex.coerceIn(-1, liveChannels.lastIndex)
        val next = ((safeCur + 1) % liveChannels.size).coerceAtLeast(0)
        rememberPlayback(next)
        return liveChannels[next]
    }

    /** Returns the previous channel (wraps). */
    fun prevChannel(): MediaCard? {
        if (liveChannels.isEmpty()) return null
        val safeCur = currentChannelIndex.coerceIn(-1, liveChannels.lastIndex)
        val prev = if (safeCur <= 0) liveChannels.size - 1
                   else safeCur - 1
        rememberPlayback(prev)
        return liveChannels[prev]
    }

    /** Flip between the current channel and the previously-played one. */
    fun toggleLastChannel(): MediaCard? {
        if (previousChannelIndex < 0 || previousChannelIndex >= liveChannels.size) return null
        val t = currentChannelIndex
        currentChannelIndex = previousChannelIndex
        previousChannelIndex = t
        return liveChannels[currentChannelIndex]
    }

    fun channelAt(index: Int): MediaCard? =
        liveChannels.getOrNull(index)
}
