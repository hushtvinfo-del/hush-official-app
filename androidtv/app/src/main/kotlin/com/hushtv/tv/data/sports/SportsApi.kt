package com.hushtv.tv.data.sports

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
 */
object SportsApi {
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
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                moshi.adapter(T::class.java).fromJson(body)
            }
        }.getOrNull()
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
}
