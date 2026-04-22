package com.hushtv.tv

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.hushtv.tv.data.XtreamApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class HushTVApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Enable HTTP disk cache for Xtream JSON calls.
        XtreamApi.enableDiskCache(this)
    }

    /**
     * Single, globally-configured Coil ImageLoader tuned for TV browsing:
     *  • 25 % RAM used for the in-memory bitmap cache (huge help when
     *    the user scrolls back through a grid they've already seen).
     *  • 256 MB on-disk image cache — survives reboots.
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
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
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
