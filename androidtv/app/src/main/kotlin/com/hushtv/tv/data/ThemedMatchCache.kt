package com.hushtv.tv.data

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-scoped singleton cache for `Map<themeId, List<ThemedLibraryMatch>>`.
 *
 * Why a singleton (not per-screen `produceState`)
 * ────────────────────────────────────────────────
 *  • The home preview pills, themed catalog screen, and themed
 *    detail screen all need the SAME data. Computing it three
 *    times (once per screen instance) wastes 200+ ms of regex on a
 *    Fire Stick and risks racing the main thread on the home page
 *    where the 7 theme pills compose together.
 *  • The home pills compose and dispose every time the user swipes
 *    between home pages. A `produceState` per pill would re-run the
 *    matcher on every swipe — exactly the regression that caused
 *    the v1.43.13 home-scroll ANR.
 *  • Boot refresh primes the library asynchronously; we want to
 *    chain the theme match RIGHT AFTER prime so the cache is hot
 *    by the time the user lands on the home menu.
 *
 * Lifecycle
 *   • [primeAsync] is called from [com.hushtv.tv.ui.boot.BootRefreshScreen]
 *     after [LibraryIndex.prime] succeeds. Runs on Dispatchers.Default.
 *   • [reset] is called when the user switches profile / playlist
 *     (paired with [LibraryIndex.reset]).
 *   • Cached values live forever in the JVM — themes and libraries
 *     don't change without an app restart.
 *
 * Reading
 *   • [snapshot] is a Compose [SnapshotStateMap] keyed by themeId.
 *     Reading from it inside a composable will trigger recomposition
 *     when the cache fills in, so the home pills automatically
 *     switch from gradient → poster the moment the matcher
 *     completes — no manual subscription required.
 *   • [matchesFor] returns the cached list, or null if the matcher
 *     hasn't run yet for that theme.
 */
object ThemedMatchCache {

    /**
     * Compose-observable map. Reads inside a composable subscribe
     * to changes automatically.
     */
    val snapshot: SnapshotStateMap<String, List<ThemedLibraryMatch>> =
        mutableStateMapOf()

    @Volatile private var primingJob: Job? = null
    @Volatile private var primedKey: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Synchronous (suspending) primer — used by boot refresh so it
     * can BLOCK the loading screen on theme matching. Resolves all
     * 25 themes on Dispatchers.Default in one shot, then publishes
     * to the [snapshot] in a single Main-thread post.
     *
     * Cost on a Fire Stick after the v1.43.14 matcher optimisation:
     * ~1.5-2 s for the entire 25-theme catalog. Cheap enough to be
     * a boot step, eliminates the gradient-placeholder flash on
     * the home page.
     *
     * Idempotent — caller can re-invoke on the same library and
     * the second call is a no-op.
     */
    suspend fun primeBlocking(ctx: Context, playlist: Playlist) {
        val key = "${playlist.host}|${playlist.username}|${playlist.password}"
        if (primedKey == key && snapshot.isNotEmpty()) return
        primingJob?.cancel()
        primedKey = key
        val resolved: Map<String, List<ThemedLibraryMatch>> =
            withContext(Dispatchers.Default) {
                HushThemedLists.all.associate { theme ->
                    theme.id to HushThemedLists.matchAgainstLibrary(theme)
                }
            }
        withContext(Dispatchers.Main) {
            snapshot.clear()
            snapshot.putAll(resolved)
        }
    }

    /**
     * Synchronous getter — returns null if matcher hasn't run yet
     * for this theme. Safe to call from the main thread.
     */
    fun matchesFor(themeId: String): List<ThemedLibraryMatch>? =
        snapshot[themeId]

    /**
     * True once the matcher has completed for at least one theme.
     * Used by UI to decide whether to show a loader vs. gradients.
     */
    val isReady: Boolean get() = snapshot.isNotEmpty()

    /**
     * Kick off off-thread theme matching. Idempotent — repeated
     * calls for the same library key are no-ops while the previous
     * job is still running or has completed.
     *
     * @param libraryKey a stable identifier for the current library
     *  (typically host|user|pass). When this changes, the cache is
     *  cleared and a fresh match is computed.
     */
    fun primeAsync(ctx: Context, libraryKey: String) {
        if (primedKey == libraryKey && snapshot.isNotEmpty()) return
        if (primingJob?.isActive == true && primedKey == libraryKey) return
        primingJob?.cancel()
        primedKey = libraryKey
        snapshot.clear()
        primingJob = scope.launch {
            val themes = HushThemedLists.all
            for (theme in themes) {
                val matches = withContext(Dispatchers.Default) {
                    HushThemedLists.matchAgainstLibrary(theme)
                }
                // Drop into the snapshot one theme at a time so the
                // home pills can paint their backdrops progressively
                // — no need to wait for the whole 25-theme scan
                // before any tile turns from gradient → poster.
                withContext(Dispatchers.Main) {
                    snapshot[theme.id] = matches
                }
            }
        }
    }

    /**
     * Convenience overload — derives [libraryKey] from the playlist.
     * Use from boot refresh.
     */
    fun primeAsync(ctx: Context, playlist: Playlist) {
        primeAsync(
            ctx = ctx,
            libraryKey = "${playlist.host}|${playlist.username}|${playlist.password}",
        )
    }

    /** Wipe the cache, e.g. when user switches profile. */
    fun reset() {
        primingJob?.cancel()
        primingJob = null
        primedKey = null
        snapshot.clear()
    }
}
