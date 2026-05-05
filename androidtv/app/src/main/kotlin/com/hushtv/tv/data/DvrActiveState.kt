package com.hushtv.tv.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Process-wide cache of "which live channels are currently being
 * recorded for this profile?". One coroutine polls the DVR list
 * endpoint on a fixed interval so the channel list, EPG grid, and
 * player OSD can all render a red "● REC" badge WITHOUT each of them
 * hammering the backend on its own.
 *
 * Keyed by `playlistId` so switching profiles doesn't surface a stale
 * set. Cleared on profile switch by simply replacing the key.
 */
object DvrActiveState {

    // Poll cadence. 8 s is a good balance: the badge flips within ~10
    // s of starting a recording from another screen (good enough that
    // it feels live) without turning the backend into a firehose.
    private const val POLL_INTERVAL_MS = 8_000L

    // Normalised channel name → rec_id. Normalising avoids "A&E Canada"
    // vs "A&E CANADA" bugs when channels are renamed upstream.
    @Volatile private var cache: Map<String, String> = emptyMap()
    @Volatile private var cacheKey: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pollJob: Job? = null
    // Listeners that want recomposition when the cache changes.
    private val listeners = mutableListOf<() -> Unit>()

    private fun normalise(channelName: String): String =
        channelName.trim().lowercase()

    fun isRecording(channelName: String): Boolean {
        return cache.containsKey(normalise(channelName))
    }

    fun activeRecIdFor(channelName: String): String? =
        cache[normalise(channelName)]

    /**
     * Subscribe the caller to poll this playlist's recordings every
     * 8 s. Returns a cancel function; call it from DisposableEffect
     * so the poll stops when the last observer leaves the screen.
     */
    fun subscribe(ctx: Context, playlistId: String, onChange: () -> Unit): () -> Unit {
        synchronized(this) {
            listeners += onChange
            if (pollJob == null || cacheKey != playlistId) {
                cacheKey = playlistId
                cache = emptyMap()
                pollJob?.cancel()
                pollJob = scope.launch {
                    pollLoop(ctx, playlistId)
                }
            } else {
                // Existing poller is already covering this key —
                // immediately notify the new subscriber with whatever
                // is already cached.
                onChange()
            }
        }
        return {
            synchronized(this) {
                listeners.remove(onChange)
                if (listeners.isEmpty()) {
                    pollJob?.cancel()
                    pollJob = null
                }
            }
        }
    }

    private suspend fun pollLoop(ctx: Context, playlistId: String) {
        while (true) {
            val playlist = PlaylistStore.find(ctx, playlistId)
            if (playlist != null) {
                val uid = DvrApi.userIdFor(playlist)
                val recs = DvrApi.list(uid)
                val next = recs.asSequence()
                    .filter { it.status == "recording" && it.channel_name.isNotBlank() }
                    .associate { normalise(it.channel_name) to it.rec_id }
                if (next != cache) {
                    cache = next
                    synchronized(this@DvrActiveState) {
                        listeners.toList()
                    }.forEach { it() }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }
}

/**
 * Composable helper: gives the caller a `version` that ticks every
 * time the set of active recordings changes for [playlistId]. Use it
 * as a recomposition key:
 *
 * ```
 * val recVersion = rememberActiveRecordingVersion(playlistId)
 * // …later in the list row:
 * val recording = remember(recVersion) {
 *     DvrActiveState.isRecording(channelName)
 * }
 * ```
 */
@Composable
fun rememberActiveRecordingVersion(playlistId: String): Int {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var version by remember(playlistId) { mutableStateOf(0) }
    DisposableEffect(playlistId) {
        val cancel = DvrActiveState.subscribe(ctx, playlistId) {
            version++
        }
        onDispose { cancel() }
    }
    return version
}
