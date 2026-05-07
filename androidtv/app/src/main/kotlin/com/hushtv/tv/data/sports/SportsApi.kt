package com.hushtv.tv.data.sports

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Thin REST client for the sports backend on hushtv.xyz. All calls
 * are blocking — invoke from a coroutine on Dispatchers.IO.
 *
 * Why no Retrofit: keeps APK size flat (we already have OkHttp) and
 * the surface area is tiny (4 endpoints).
 *
 * BELT-AND-SUSPENDERS NOTE (v1.44.1+):
 * The Moshi adapter call below is wrapped in `runCatching { … }`
 * which DOES catch `Throwable` (Errors included). The previous
 * v1.44.0 crash on opening Sports was caused by
 * `@JsonClass(generateAdapter = true)` on the wire models — Moshi's
 * `KotlinJsonAdapterFactory` actively REFUSES classes annotated with
 * `generateAdapter = true` when no kapt-generated adapter exists,
 * and threw `IllegalArgumentException` from `adapter()` BEFORE the
 * runCatching block in some build configs because the adapter
 * lookup happened during `inline fun <reified T>` resolution.
 *
 * The fix is two-layered:
 *   1. SportsModels.kt has zero `@JsonClass` annotations. Reflection
 *      handles them via `KotlinJsonAdapterFactory`.
 *   2. We log every failure via `Log.w("HushTVSports", ...)` so the
 *      next "what happened" question can be answered from logcat
 *      (or, if the failure crashes the process, from
 *      `installCrashHandler`'s crash.log upload).
 */
object SportsApi {
    private const val TAG = "HushTVSports"
    private const val BASE = "https://hushtv.xyz"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private inline fun <reified T> getJson(path: String): T? {
        val req = Request.Builder().url("$BASE$path").build()
        // Two nested runCatching blocks intentionally:
        //   • inner catches body.string() / parse exceptions
        //   • outer catches HTTP / OkHttp / DNS exceptions
        // Both log so we keep visibility, but neither lets a Moshi
        // or network surprise propagate up into the Compose runtime.
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GET $path → HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string()
                if (body.isNullOrEmpty()) {
                    Log.w(TAG, "GET $path → empty body")
                    return@use null
                }
                runCatching {
                    moshi.adapter(T::class.java).fromJson(body)
                }.getOrElse { e ->
                    Log.w(TAG, "GET $path → parse failed: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
        }.getOrElse { e ->
            Log.w(TAG, "GET $path → network failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Top-of-page payload — hero + ppv + per-league buckets. */
    fun home(): SportsHomeResponse? =
        getJson<SportsHomeResponse>("/api/sports/home")

    /** Full game list for one league, grouped by date on the client. */
    fun league(slug: String, days: Int = 7): SportsLeagueResponse? =
        getJson<SportsLeagueResponse>("/api/sports/league/$slug?days=$days")

    /** Upcoming PPV events (UFC / boxing / wrestling + admin-curated). */
    fun ppv(): SportsPpvListResponse? =
        getJson<SportsPpvListResponse>("/api/sports/ppv")

    /**
     * v1.44.27 — EPG channel picker for a game.
     *
     * Calls /api/sports/game/{id}/channels, passing the user's
     * Xtream creds so the backend can search the user's xmltv.php
     * EPG. Returns a Canadian-first sorted list of channels
     * currently airing the game (each with EPG title + start/stop).
     *
     * Returns null on transport / parse failure (UI surfaces a
     * generic error). Returns empty list when the EPG genuinely
     * has no match (UI shows "no matching channels" empty state).
     */
    fun gameChannels(
        gameId: Int,
        host: String,
        username: String,
        password: String,
    ): List<SportsGameChannel>? {
        val url = "$BASE/api/sports/game/$gameId/channels" +
            "?host=" + java.net.URLEncoder.encode(host, "UTF-8") +
            "&username=" + java.net.URLEncoder.encode(username, "UTF-8") +
            "&password=" + java.net.URLEncoder.encode(password, "UTF-8")
        val req = Request.Builder().url(url).build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "gameChannels($gameId) → HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                if (body.isEmpty()) return@use null
                val parsed = runCatching {
                    moshi.adapter(SportsGameChannelsResponse::class.java).fromJson(body)
                }.getOrElse {
                    Log.w(TAG, "gameChannels parse: ${it.message}")
                    null
                }
                if (parsed?.ok == true) parsed.matches.orEmpty() else emptyList()
            }
        }.getOrElse {
            Log.w(TAG, "gameChannels($gameId) network: ${it.message}")
            null
        }
    }

    /**
     * Pre-warm the EPG cache on the backend. Cheap (idempotent) —
     * call once at app start so the first /game/{id}/channels call
     * is fast.
     */
    fun warmEpg(host: String, username: String, password: String): Boolean {
        val url = "$BASE/api/sports/epg/warm" +
            "?host=" + java.net.URLEncoder.encode(host, "UTF-8") +
            "&username=" + java.net.URLEncoder.encode(username, "UTF-8") +
            "&password=" + java.net.URLEncoder.encode(password, "UTF-8")
        return runCatching {
            val req = Request.Builder().url(url)
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Resolve an Xtream channel by display name. The EPG-given
     * channel_name (e.g. "Sportsnet Ontario") is matched against
     * the user's playlist via the existing token-aware fuzzy
     * matcher used by sports auto-mapping. Returns the streamId
     * needed to build a play URL, or null if the name doesn't
     * resolve to any channel in the user's playlist.
     */
    fun findStreamIdByName(
        ctx: android.content.Context,
        playlistId: String,
        name: String,
    ): Int? {
        if (name.isBlank()) return null
        val p = com.hushtv.tv.data.PlaylistStore.find(ctx, playlistId) ?: return null
        return runCatching {
            // getAllStreams is suspend — bridge with runBlocking.
            // findStreamIdByName is itself called from a coroutine
            // (LaunchedEffect in GameChannelSheet) but on Dispatchers.IO
            // — runBlocking here is a brief synchronous bridge.
            val streams = kotlinx.coroutines.runBlocking {
                com.hushtv.tv.data.XtreamApi.getAllStreams(
                    p.host, p.username, p.password, "live",
                )
            }
            val index =
                com.hushtv.tv.data.sports.SportsChannelMatcher.ChannelIndex(streams)
            com.hushtv.tv.data.sports.SportsChannelMatcher.match(name, index)?.streamId
        }.getOrNull()
    }

    // Wire models for /api/sports/game/{id}/channels
    data class SportsGameChannelsResponse(
        val ok: Boolean,
        val reason: String? = null,
        val matches: List<SportsGameChannel>? = null,
    )

    data class SportsGameChannel(
        val channel_id: String,
        val channel_name: String,
        val programme_title: String,
        val programme_sub: String? = null,
        val start_utc_ms: Long,
        val stop_utc_ms: Long,
    )
}
