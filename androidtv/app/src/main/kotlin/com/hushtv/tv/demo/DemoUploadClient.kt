package com.hushtv.tv.demo

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.hushtv.tv.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Upload demo MP4s to the sync server.
 *
 * The endpoint and token are baked into the build via
 *   • [BuildConfig.DEMO_UPLOAD_URL]   (POST URL)
 *   • [BuildConfig.DEMO_UPLOAD_TOKEN] (X-Demo-Upload-Token header)
 *
 * For dev/canada builds only — official builds ship with the fields
 * empty and we skip the upload silently.
 */
object DemoUploadClient {

    data class Result(
        val success: Boolean,
        val skipped: Boolean = false,
        val recordId: String? = null,
        val error: String? = null,
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun upload(ctx: Context, file: File): Result {
        val url = BuildConfig.DEMO_UPLOAD_URL.takeIf { it.isNotBlank() }
            ?: return Result(false, skipped = true, error = "DEMO_UPLOAD_URL not configured")
        val token = BuildConfig.DEMO_UPLOAD_TOKEN.takeIf { it.isNotBlank() }
            ?: return Result(false, skipped = true, error = "DEMO_UPLOAD_TOKEN not configured")
        if (!file.exists() || file.length() == 0L) {
            return Result(false, error = "MP4 missing or empty")
        }
        val deviceId = runCatching {
            @Suppress("HardwareIds")
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: "unknown"
        val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val version = "${BuildConfig.UPDATE_CHANNEL}/${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
        val body = file.asRequestBody("video/mp4".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("X-Demo-Upload-Token", token)
            .header("X-Device-Id", deviceId.take(96))
            .header("X-Device-Label", deviceLabel.take(96))
            .header("X-App-Version", version)
            .header("X-Note", "auto-pilot home tour")
            .post(body)
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Result(false, error = "HTTP ${resp.code}: ${txt.take(160)}")
                } else {
                    val id = runCatching { JSONObject(txt).optString("id") }.getOrNull()
                    Result(true, recordId = id)
                }
            }
        } catch (t: Throwable) {
            Result(false, error = t.message ?: t.javaClass.simpleName)
        }
    }
}
