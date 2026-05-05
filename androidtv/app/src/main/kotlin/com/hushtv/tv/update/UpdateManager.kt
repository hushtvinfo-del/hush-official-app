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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

    /**
     * Update manifest URL — comes from BuildConfig so each
     * distribution channel pulls from its own version.json:
     *   • "dev"      → https://hushtv.xyz/version.json
     *   • "official" → https://hushtv.xyz/version-official.json
     * See app/build.gradle.kts productFlavors for definitions.
     */
    val VERSION_JSON_URL: String = com.hushtv.tv.BuildConfig.UPDATE_MANIFEST_URL

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

    /** True when the OS has granted this app permission to launch the installer. */
    fun canInstallPackages(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return ctx.packageManager.canRequestPackageInstalls()
    }

    /** Intent that opens the "Install unknown apps" screen for this package. */
    fun unknownSourcesSettingsIntent(ctx: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    // ─── Download + install ───────────────────────────────────────────────

    private const val DOWNLOAD_FILENAME = "HushTV-update.apk"

    /**
     * Dedicated OkHttp client for APK streaming. We DON'T share the tiny
     * `client` used for version.json — the APK download needs long read
     * windows (the whole file can take a minute over a weak connection)
     * and should NOT time out during the streaming phase.
     */
    private val apkClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)   // unbounded — we stream
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Status snapshot used by the UI's progress poller. */
    data class DownloadProgress(
        val status: Int,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val reason: Int,
    )

    /** Mutable in-memory snapshot of the current direct-HTTP download.
     *  [UpdateDialog] polls this instead of `DownloadManager.Query()`. */
    @Volatile
    private var directProgress: DownloadProgress = DownloadProgress(0, 0, 0, 0)

    @Volatile
    private var currentDownloadJob: kotlinx.coroutines.Job? = null

    /**
     * Downloads the APK directly via OkHttp. Blocks until either:
     *  • the full file has been streamed and written to disk, OR
     *  • an exception is thrown (propagates to caller).
     *
     * Progress is exposed through [queryProgress] on the synthetic
     * download id returned here. This completely bypasses the Android
     * system DownloadManager, which has been observed hanging at 0% on
     * several Android TV / Fire TV OEM builds.
     */
    suspend fun downloadApkDirect(
        ctx: Context,
        apkUrl: String,
    ): Unit = withContext(Dispatchers.IO) {
        // Seed the snapshot so the UI flips from 0 to "downloading" state
        // immediately instead of waiting for the first progress tick.
        directProgress = DownloadProgress(
            status = DownloadManager.STATUS_RUNNING,
            bytesDownloaded = 0L,
            bytesTotal = 0L,
            reason = 0,
        )

        val dest = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FILENAME)
        val tmp = File(dest.parentFile, "$DOWNLOAD_FILENAME.part")
        runCatching { dest.delete() }
        runCatching { tmp.delete() }
        dest.parentFile?.mkdirs()

        try {
            val req = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "HushTV/${BuildConfig.VERSION_NAME}")
                .header("Cache-Control", "no-cache")
                .build()

            apkClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    directProgress = directProgress.copy(
                        status = DownloadManager.STATUS_FAILED,
                        reason = resp.code,
                    )
                    throw IllegalStateException("HTTP ${resp.code} fetching APK")
                }
                val body = resp.body ?: throw IllegalStateException("Empty APK body")
                val total = body.contentLength().coerceAtLeast(0L)
                directProgress = directProgress.copy(bytesTotal = total)

                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastTick = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            downloaded += n
                            val now = System.currentTimeMillis()
                            // Throttle snapshot writes to ~10 Hz so we
                            // don't thrash CPU on ultra-fast downloads.
                            if (now - lastTick > 100 || downloaded == total) {
                                lastTick = now
                                directProgress = DownloadProgress(
                                    status = DownloadManager.STATUS_RUNNING,
                                    bytesDownloaded = downloaded,
                                    bytesTotal = total,
                                    reason = 0,
                                )
                            }
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
            }

            // Rename .part → final. Atomic-ish on same filesystem.
            if (!tmp.renameTo(dest)) {
                // Fallback: copy+delete if rename refuses (rare on some
                // FAT-formatted external dirs).
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }

            directProgress = DownloadProgress(
                status = DownloadManager.STATUS_SUCCESSFUL,
                bytesDownloaded = dest.length(),
                bytesTotal = dest.length(),
                reason = 0,
            )
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            directProgress = directProgress.copy(
                status = DownloadManager.STATUS_FAILED,
                reason = -1,
            )
            throw e
        }
    }

    /** Launches the direct download on a coroutine and returns a synthetic
     *  id so callers can treat it like a DownloadManager job. Pass the id
     *  to [queryProgress] for streaming progress. */
    fun startDownload(
        ctx: Context,
        apkUrl: String,
        scope: kotlinx.coroutines.CoroutineScope,
        onFinished: (Boolean) -> Unit,
    ): Long {
        currentDownloadJob?.cancel()
        currentDownloadJob = scope.launch(Dispatchers.IO) {
            val ok = runCatching { downloadApkDirect(ctx, apkUrl) }.isSuccess
            withContext(Dispatchers.Main) { onFinished(ok) }
        }
        // Synthetic id — any non-zero value works; we only use it to match
        // poller calls to THIS download.
        return 1L
    }

    /** Returns the latest snapshot of the in-flight direct download. */
    @Suppress("UNUSED_PARAMETER")
    fun queryProgress(ctx: Context, downloadId: Long): DownloadProgress = directProgress

    /** Locates the downloaded APK file on disk. */
    fun downloadedApk(ctx: Context): File =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FILENAME)

    /**
     * Launches the system package installer on the APK we just downloaded.
     *
     * Returns:
     *  • `Intent` pointing at the "install unknown apps" settings screen — caller
     *    must `startActivity()` it to send the user to enable the permission.
     *  • `null` when the installer launched successfully.
     *
     * Throws `IllegalStateException` with a human-readable message when
     * something irrecoverable went wrong (missing file, installer rejection).
     */
    fun triggerInstall(ctx: Context): Intent? {
        val apk = downloadedApk(ctx)
        if (!apk.exists() || apk.length() < 1_000_000L) {
            throw IllegalStateException(
                "Downloaded APK missing. Expected at ${apk.absolutePath}"
            )
        }

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
        val uri: Uri = try {
            FileProvider.getUriForFile(ctx, authority, apk)
        } catch (e: Exception) {
            throw IllegalStateException(
                "FileProvider rejected the APK: ${e.message}. " +
                    "You can install manually via:\n${apk.absolutePath}",
                e,
            )
        }

        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        try {
            ctx.startActivity(install)
        } catch (e: Exception) {
            throw IllegalStateException(
                "The TV refused to launch the installer: ${e.message}. " +
                    "Install manually: ${apk.absolutePath}",
                e,
            )
        }
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
