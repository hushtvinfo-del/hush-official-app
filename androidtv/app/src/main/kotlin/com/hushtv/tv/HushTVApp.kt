package com.hushtv.tv

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.hushtv.tv.BuildConfig
import com.hushtv.tv.data.XtreamApi
import okhttp3.OkHttpClient
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HushTVApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        // Enable HTTP disk cache for Xtream JSON calls.
        XtreamApi.enableDiskCache(this)
        // If a crash was captured during the last session, POST it to
        // the server in the background. Silent-no-op if nothing's new.
        com.hushtv.tv.data.CrashReporter.uploadIfPending(this)
    }

    /**
     * Writes any uncaught exception to both Logcat (tag HushTVCrash) and
     * /data/data/<pkg>/files/crash.log so we can diagnose silent exits
     * on user devices without adb access. Chains to the previous
     * handler afterwards so Android still kills the process.
     */
    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                // UTC ISO-8601 so we can parse it back unambiguously.
                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(Date())
                // Header format (machine-parseable):
                //   ===== HushTV <iso> v<name>#<code> thread=<name> =====
                val header =
                    "===== HushTV " + iso +
                        " v" + BuildConfig.VERSION_NAME +
                        "#" + BuildConfig.VERSION_CODE +
                        " thread=" + thread.name +
                        " =====\n"
                Log.e("HushTVCrash", header + sw.toString())
                val f = File(filesDir, "crash.log")
                if (f.length() > 256L * 1024) f.delete()
                f.appendText(header + sw.toString() + "\n\n")
            }
            prev?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Single, globally-configured Coil ImageLoader tuned for TV browsing:
     *  • 12 % RAM used for the in-memory bitmap cache — balances repeat-
     *    scroll speed against the low-memory-killer on older TVs
     *    (phase 47: 25 % was too aggressive on 2 GB devices).
     *  • 128 MB on-disk image cache — survives reboots.
     *  • No crossfade — the 100 ms crossfade feels sluggish on TV scrolls.
     *  • Aggressive READ cache policy so repeat scrolls are instant.
     */
    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .components {
                // Support SVG URLs (Wikipedia logos etc.) directly.
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .crossfade(false)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            .build()
    }
}
