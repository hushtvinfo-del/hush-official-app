package com.hushtv.tv.data

/**
 * Process-wide memory of theme-detail scroll positions and the
 * focused poster index PER theme.
 *
 * Why this is needed
 * ──────────────────
 * Compose's [androidx.compose.foundation.lazy.grid.LazyGridState]
 * is owned by the composable; when the user opens a movie from
 * the themed-detail screen, navigates back, the screen RE-MOUNTS
 * and a fresh `LazyGridState` is created at index 0. That hard
 * resets the user to the top of "Mind-Bending Movies" every time
 * they exit a movie — frustrating and slow on Fire TV because the
 * grid then re-decodes posters from the top.
 *
 * This singleton survives nav back-pops because it lives on the
 * Application classloader, NOT in any composable's `remember`. The
 * detail screen reads + writes its scroll state through it so a
 * round-trip into the movie detail page restores you exactly
 * where you were.
 *
 * Footprint is minimal: 2 small ints per visited theme. Reset on
 * profile switch via [reset].
 */
object ThemedScrollMemory {

    private data class State(
        val firstVisibleItemIndex: Int,
        val firstVisibleItemScrollOffset: Int,
        val focusedIndex: Int,
    )

    private val map: MutableMap<String, State> = HashMap()

    fun save(
        themeId: String,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        focusedIndex: Int,
    ) {
        synchronized(map) {
            map[themeId] = State(
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                focusedIndex = focusedIndex,
            )
        }
    }

    fun load(themeId: String): Triple<Int, Int, Int>? = synchronized(map) {
        map[themeId]?.let {
            Triple(
                it.firstVisibleItemIndex,
                it.firstVisibleItemScrollOffset,
                it.focusedIndex,
            )
        }
    }

    /** Wipe state — invoke from profile/playlist switch flows. */
    fun reset() {
        synchronized(map) { map.clear() }
    }

    /** Drop a single theme's state — invoke when the theme's match
     *  set changes meaningfully (e.g. library refresh). */
    fun forget(themeId: String) {
        synchronized(map) { map.remove(themeId) }
    }
}
