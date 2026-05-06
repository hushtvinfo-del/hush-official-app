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
}
