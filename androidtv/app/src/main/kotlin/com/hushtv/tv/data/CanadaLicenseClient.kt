package com.hushtv.tv.data

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HushTV Canada $40/yr CDN proxy fee license client.
 *
 * Talks to the production sync server at `https://hushtv.xyz/api/canada/...`.
 * All public methods are blocking — call from a coroutine on Dispatchers.IO.
 *
 * Local cache: a single shared-prefs blob keyed by xtream_username with the
 * last-known `paid_until_ms` and `last_checked_ms`. We fall back to the cache
 * when the network is unreachable so a user who is genuinely paid can still
 * launch the app for ~7 days through ISP outages / Wi-Fi drops.
 */
object CanadaLicenseClient {
    private const val BASE = "https://hushtv.xyz/api/canada"
    private const val PREFS = "hushtv_canada_license"
    private const val KEY_PAID_UNTIL = "paid_until_ms_"
    private const val KEY_CHECKED = "checked_ms_"
    private const val KEY_PENDING_ORDER = "pending_order_id_"
    private const val KEY_PENDING_EXPIRES = "pending_order_expires_ms_"
    private const val OFFLINE_GRACE_MS = 7L * 24 * 60 * 60 * 1000

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val moshi: Moshi by lazy {
        // KotlinJsonAdapterFactory is REQUIRED — without it, Moshi looks
        // for code-generated adapters (kotlin-codegen processor) which
        // this module does not enable. See SportsApi.kt for the same pattern.
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }
    private val JSON = "application/json".toMediaType()

    @JsonClass(generateAdapter = true)
    data class OrderDto(
        val order_id: String,
        val xtream_username: String,
        val created_at: Long,
        val expires_at: Long,
        val status: String,
        val paid_at: Long? = null,
    )

    @JsonClass(generateAdapter = true)
    data class LicenseDto(
        val paid: Boolean = false,
        val expires_at: Long? = null,
        val paid_at: Long? = null,
        val days_remaining: Long? = null,
        val last_order_id: String? = null,
        val expired: Boolean? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CreateOrderReq(val xtream_username: String, val force_new: Boolean = false)

    @JsonClass(generateAdapter = true)
    data class CreateOrderResp(
        val order: OrderDto? = null,
        val amount_cad: Double? = null,
        val email_to: String? = null,
        val reused: Boolean? = null,
        val already_licensed: Boolean? = null,
        val license: LicenseDto? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OrderStatusResp(
        val order: OrderDto,
        val license: LicenseDto? = null,
    )

    @JsonClass(generateAdapter = true)
    data class LicenseResp(
        val xtream_username: String,
        val license: LicenseDto,
    )

    data class CachedLicense(
        val paidUntilMs: Long,
        val lastCheckedMs: Long,
    )

    // ── Public state ────────────────────────────────────────────────
    sealed class LicenseState {
        object NoNetwork : LicenseState()
        data class Paid(val expiresAtMs: Long, val daysRemaining: Long) : LicenseState()
        object Unpaid : LicenseState()
        data class Error(val message: String) : LicenseState()
    }

    /** Wrapped result for create-order calls so the UI can surface real errors. */
    sealed class CreateOrderResult {
        data class Success(val data: CreateOrderResp) : CreateOrderResult()
        data class Failure(val message: String) : CreateOrderResult()
    }

    // ── Cache ───────────────────────────────────────────────────────
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readCache(ctx: Context, username: String): CachedLicense? {
        val u = username.lowercase()
        val p = prefs(ctx)
        val paid = p.getLong(KEY_PAID_UNTIL + u, 0L)
        val checked = p.getLong(KEY_CHECKED + u, 0L)
        return if (paid > 0L && checked > 0L) CachedLicense(paid, checked) else null
    }

    private fun writeCache(ctx: Context, username: String, paidUntil: Long) {
        prefs(ctx).edit()
            .putLong(KEY_PAID_UNTIL + username.lowercase(), paidUntil)
            .putLong(KEY_CHECKED + username.lowercase(), System.currentTimeMillis())
            .apply()
    }

    fun savePendingOrder(ctx: Context, username: String, orderId: String, expiresAt: Long) {
        prefs(ctx).edit()
            .putString(KEY_PENDING_ORDER + username.lowercase(), orderId)
            .putLong(KEY_PENDING_EXPIRES + username.lowercase(), expiresAt)
            .apply()
    }

    fun readPendingOrder(ctx: Context, username: String): Pair<String, Long>? {
        val u = username.lowercase()
        val p = prefs(ctx)
        val oid = p.getString(KEY_PENDING_ORDER + u, null) ?: return null
        val exp = p.getLong(KEY_PENDING_EXPIRES + u, 0L)
        if (exp <= System.currentTimeMillis()) return null
        return oid to exp
    }

    fun clearPendingOrder(ctx: Context, username: String) {
        prefs(ctx).edit()
            .remove(KEY_PENDING_ORDER + username.lowercase())
            .remove(KEY_PENDING_EXPIRES + username.lowercase())
            .apply()
    }

    // ── Network calls ───────────────────────────────────────────────
    fun fetchLicense(ctx: Context, username: String): LicenseState {
        val u = username.lowercase().trim()
        if (u.isEmpty()) return LicenseState.Unpaid
        return try {
            val req = Request.Builder().url("$BASE/license/$u").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return offlineFallback(ctx, u, "HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val parsed = moshi.adapter(LicenseResp::class.java).fromJson(body)
                    ?: return offlineFallback(ctx, u, "bad payload")
                val lic = parsed.license
                if (lic.paid && lic.expires_at != null && lic.expires_at > System.currentTimeMillis()) {
                    writeCache(ctx, u, lic.expires_at)
                    LicenseState.Paid(lic.expires_at, lic.days_remaining ?: 0L)
                } else {
                    // Clear stale cache when server says unpaid.
                    prefs(ctx).edit()
                        .remove(KEY_PAID_UNTIL + u)
                        .remove(KEY_CHECKED + u)
                        .apply()
                    LicenseState.Unpaid
                }
            }
        } catch (e: Exception) {
            offlineFallback(ctx, u, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun offlineFallback(ctx: Context, username: String, why: String): LicenseState {
        val cached = readCache(ctx, username) ?: return LicenseState.NoNetwork
        val now = System.currentTimeMillis()
        // Honour cached "paid" status if it's still valid AND we've checked within grace.
        if (cached.paidUntilMs > now && (now - cached.lastCheckedMs) <= OFFLINE_GRACE_MS) {
            val daysLeft = (cached.paidUntilMs - now) / (24 * 60 * 60 * 1000)
            return LicenseState.Paid(cached.paidUntilMs, daysLeft)
        }
        return LicenseState.NoNetwork
    }

    /** POST /order/create — returns the create response or null on failure. */
    fun createOrder(username: String, forceNew: Boolean = false): CreateOrderResp? {
        val r = createOrderResult(username, forceNew)
        return (r as? CreateOrderResult.Success)?.data
    }

    /** POST /order/create that surfaces the underlying error message. */
    fun createOrderResult(username: String, forceNew: Boolean = false): CreateOrderResult {
        val u = username.lowercase().trim()
        if (u.isEmpty()) return CreateOrderResult.Failure("Empty username — please sign in to your Xtream account first.")
        return try {
            val payload = moshi.adapter(CreateOrderReq::class.java)
                .toJson(CreateOrderReq(u, forceNew))
            val req = Request.Builder().url("$BASE/order/create")
                .post(payload.toRequestBody(JSON)).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
                    return CreateOrderResult.Failure("Server returned HTTP ${resp.code}. ${errBody.take(180)}")
                }
                val body = resp.body?.string().orEmpty()
                val parsed = moshi.adapter(CreateOrderResp::class.java).fromJson(body)
                if (parsed == null) {
                    CreateOrderResult.Failure("Server sent an unexpected response. Please try again in a moment.")
                } else {
                    CreateOrderResult.Success(parsed)
                }
            }
        } catch (e: java.net.UnknownHostException) {
            CreateOrderResult.Failure("Can't reach hushtv.xyz. Your device DNS may be blocking it — try toggling Wi-Fi off/on or restarting your router.")
        } catch (e: javax.net.ssl.SSLException) {
            CreateOrderResult.Failure("Secure connection failed (SSL). The TV's clock may be wrong — check Date & Time in your TV settings. Detail: ${e.message?.take(120) ?: ""}")
        } catch (e: java.net.SocketTimeoutException) {
            CreateOrderResult.Failure("Server is slow to respond (timeout). Please try again in a few seconds.")
        } catch (e: java.io.IOException) {
            CreateOrderResult.Failure("Network error: ${e.javaClass.simpleName} ${e.message?.take(120) ?: ""}")
        } catch (e: Exception) {
            CreateOrderResult.Failure("Unexpected error: ${e.javaClass.simpleName} ${e.message?.take(120) ?: ""}")
        }
    }

    /** GET /order/status/{order_id} — returns the response or null on failure. */
    fun pollOrder(orderId: String): OrderStatusResp? {
        return try {
            val req = Request.Builder().url("$BASE/order/status/$orderId").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                moshi.adapter(OrderStatusResp::class.java).fromJson(body)
            }
        } catch (_: Exception) {
            null
        }
    }
}
