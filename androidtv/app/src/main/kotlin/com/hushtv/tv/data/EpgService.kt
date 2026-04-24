package com.hushtv.tv.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Base64
import java.util.concurrent.TimeUnit

/** A single program in the EPG. */
data class EpgProgram(
    val title: String,
    val description: String,
    val startMs: Long,
    val stopMs: Long,
    val nowPlaying: Boolean
) {
    val durationMs: Long get() = (stopMs - startMs).coerceAtLeast(0)
    val progressPct: Float get() {
        val now = System.currentTimeMillis()
        if (now <= startMs) return 0f
        if (now >= stopMs) return 1f
        return ((now - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
    val minutesLeft: Int get() = ((stopMs - System.currentTimeMillis()) / 60_000L).toInt()
    val isLive: Boolean get() {
        val now = System.currentTimeMillis()
        return now in startMs..stopMs
    }
}

@JsonClass(generateAdapter = true)
private data class EpgEntryRaw(
    val id: String? = null,
    val title: String = "",
    val description: String = "",
    val start: String? = null,
    val end: String? = null,
    val start_timestamp: String? = null,
    val stop_timestamp: String? = null,
    val now_playing: Int? = 0
)

@JsonClass(generateAdapter = true)
private data class EpgResponse(
    val epg_listings: List<EpgEntryRaw>? = emptyList()
)

/**
 * Fetches short-EPG (current + next ~3 programs) for live channels and
 * caches results in memory for 5 min so the Live Browse screen doesn't
 * hammer the Xtream server.
 */
object EpgService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private data class Entry(val programs: List<EpgProgram>, val fetchedAt: Long)
    private val cache = HashMap<Int, Entry>()
    private val inFlight = HashMap<Int, Mutex>()
    private val lock = Mutex()

    private const val CACHE_TTL_MS = 5L * 60 * 1000

    /** Returns the currently-playing program for this channel, or null if
     *  we haven't fetched yet (use [fetchShortEpg] first). */
    fun nowPlaying(streamId: Int): EpgProgram? {
        val e = cache[streamId] ?: return null
        return e.programs.firstOrNull { it.isLive }
    }

    /** Returns the next-up program for this channel, or null. */
    fun nextUp(streamId: Int): EpgProgram? {
        val e = cache[streamId] ?: return null
        val now = System.currentTimeMillis()
        return e.programs.firstOrNull { it.startMs > now }
    }

    /**
     * Returns the next [limit] upcoming programs (after the currently
     * playing one). Used by the Live TV preview bar to show what's on
     * in the next few hours, not just the very next show.
     */
    fun upcoming(streamId: Int, limit: Int = 3): List<EpgProgram> {
        val e = cache[streamId] ?: return emptyList()
        val now = System.currentTimeMillis()
        return e.programs.filter { it.startMs > now }.take(limit)
    }

    /** All cached programs for the given stream (Now + Next + more). */
    fun programsOf(streamId: Int): List<EpgProgram> =
        cache[streamId]?.programs ?: emptyList()

    /** Fetches short-EPG for a channel. Deduped + cached. */
    suspend fun fetchShortEpg(
        host: String, username: String, password: String, streamId: Int
    ): List<EpgProgram> = withContext(Dispatchers.IO) {
        // Check cache first
        cache[streamId]?.let {
            if (System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MS) return@withContext it.programs
        }
        // Dedupe concurrent calls for the same stream
        val mutex = lock.withLock { inFlight.getOrPut(streamId) { Mutex() } }
        mutex.withLock {
            cache[streamId]?.let {
                if (System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MS) return@withLock it.programs
            }
            val programs = try {
                val h = if (host.startsWith("http")) host else "http://$host"
                val url = "$h/player_api.php".toHttpUrl().newBuilder()
                    .addQueryParameter("username", username)
                    .addQueryParameter("password", password)
                    .addQueryParameter("action", "get_short_epg")
                    .addQueryParameter("stream_id", streamId.toString())
                    .addQueryParameter("limit", "20")
                    .build()
                val req = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) "" else (r.body?.string() ?: "")
                }
                if (body.isBlank()) emptyList()
                else {
                    val resp = moshi.adapter(EpgResponse::class.java).fromJson(body)
                    (resp?.epg_listings ?: emptyList()).mapNotNull { it.toProgram() }
                        .sortedBy { it.startMs }
                }
            } catch (_: Exception) { emptyList() }

            cache[streamId] = Entry(programs, System.currentTimeMillis())
            programs
        }
    }

    private fun EpgEntryRaw.toProgram(): EpgProgram? {
        val start = start_timestamp?.toLongOrNull()?.times(1000) ?: return null
        val stop = stop_timestamp?.toLongOrNull()?.times(1000) ?: return null
        val t = decodeBase64(title).ifBlank { title }
        val d = decodeBase64(description).ifBlank { description }
        return EpgProgram(
            title = t,
            description = d,
            startMs = start,
            stopMs = stop,
            nowPlaying = (now_playing ?: 0) == 1
        )
    }

    // Use android.util.Base64 (NOT java.util.Base64) — the latter only
    // exists on Android 8.0+ (SDK 26) and crashes Fire TV Stick 4K Max
    // (SDK 25) the moment the EPG returns a base64-encoded title.
    // android.util.Base64 ships back to Android 2.2.
    private fun decodeBase64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) { "" }
}
