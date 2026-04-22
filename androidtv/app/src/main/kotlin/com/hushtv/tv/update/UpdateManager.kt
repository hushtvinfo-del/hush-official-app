package com.hushtv.tv.update

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hushtv.tv.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Server-side update manifest published at https://hushtv.xyz/version.json */
@JsonClass(generateAdapter = true)
data class VersionInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: List<String> = emptyList(),
    val mandatory: Boolean = false,
    val releasedAt: String? = null
)

object UpdateManager {

    const val VERSION_JSON_URL = "https://hushtv.xyz/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    /** Fetches version.json. Returns null on any failure (we never want to
     *  interrupt the app if the update endpoint is down). */
    suspend fun fetchLatest(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(VERSION_JSON_URL)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                moshi.adapter(VersionInfo::class.java).fromJson(body)
            }
        } catch (_: Exception) { null }
    }

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE
    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    fun isUpdateAvailable(info: VersionInfo): Boolean =
        info.versionCode > currentVersionCode() && info.apkUrl.isNotBlank()

    // ─── Download + install ───────────────────────────────────────────────

    private const val DOWNLOAD_FILENAME = "HushTV-update.apk"

    /** Enqueues a download via the system DownloadManager. Returns the
     *  download ID so the caller can observe progress. */
    fun enqueueDownload(ctx: Context, apkUrl: String): Long {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Remove any previous file so we always pull the freshest APK.
        val dest = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FILENAME)
        if (dest.exists()) dest.delete()

        val req = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("HushTV update")
            .setDescription("Downloading the latest HushTV app…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, DOWNLOAD_FILENAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return dm.enqueue(req)
    }

    /** Queries progress + status for an in-flight download. */
    data class DownloadProgress(
        val status: Int,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val reason: Int
    )

    fun queryProgress(ctx: Context, downloadId: Long): DownloadProgress? {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(downloadId)
        dm.query(q).use { c ->
            if (c == null || !c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val down = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            return DownloadProgress(status, down, total, reason)
        }
    }

    /** Locates the downloaded APK file on disk. */
    fun downloadedApk(ctx: Context): File =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FILENAME)

    /** Launches the system package installer on the APK we just downloaded.
     *  If the app doesn't have REQUEST_INSTALL_PACKAGES permission yet
     *  (Android 8+ only), returns a Settings intent that the caller must
     *  fire to send the user to the permission screen. */
    fun triggerInstall(ctx: Context): Intent? {
        val apk = downloadedApk(ctx)
        if (!apk.exists()) return null

        // On API 26+ the app needs REQUEST_INSTALL_PACKAGES granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = ctx.packageManager
            if (!pm.canRequestPackageInstalls()) {
                return Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        val authority = "${ctx.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(ctx, authority, apk)

        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(install)
        return null
    }

    // ─── Broadcast receiver helper ────────────────────────────────────────

    fun registerOnComplete(
        ctx: Context,
        onComplete: (Long) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                onComplete(id)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        return receiver
    }

    fun unregister(ctx: Context, receiver: BroadcastReceiver) {
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
