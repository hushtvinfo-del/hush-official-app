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
        val next = ((currentChannelIndex + 1) % liveChannels.size).coerceAtLeast(0)
        rememberPlayback(next)
        return liveChannels[next]
    }

    /** Returns the previous channel (wraps). */
    fun prevChannel(): MediaCard? {
        if (liveChannels.isEmpty()) return null
        val prev = if (currentChannelIndex <= 0) liveChannels.size - 1
                   else currentChannelIndex - 1
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
