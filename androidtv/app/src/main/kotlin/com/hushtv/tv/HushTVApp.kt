package com.hushtv.tv

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
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
        // v1.44.121 — boot-phase tracker. BEFORE anything else (other
        // than super.onCreate), so we capture the very first marker
        // even if the crash happens 1 line later.
        com.hushtv.tv.data.BootPhaseTracker.collectAndRotate(this)
        com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oncreate_start")

        // v1.44.123 — install the crash handler FIRST (before anything
        // that could throw). If anything below this line crashes the
        // JVM, the trace lands in crash.log instead of vanishing.
        installCrashHandler()
        com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_crashhandler")

        // v1.44.128-dev — initialise the DEV-flavor bundled-asset cache
        // EARLY (cheap, just stores the Context). On canada / official
        // this is a no-op because the bundle files aren't shipped, so
        // isActive returns false and every lookup short-circuits to
        // network. See DevBundleCache.kt for details.
        com.hushtv.tv.data.DevBundleCache.init(this)

        com.hushtv.tv.data.EventLog.log(
            "app",
            "onCreate v${BuildConfig.VERSION_NAME}#${BuildConfig.VERSION_CODE}",
        )
        com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_eventlog")

        // v1.44.124 — DEFERRED INIT (expanded from v1.44.123). Now
        // includes runAutoMigrationV122IfNeeded — v1.44.123 telemetry
        // showed 12 users dying at `app_oc_post_crashhandler` in 24h
        // because the migration call's first-access SharedPreferences
        // lazy-load (reading `app_mode_prefs.xml` from disk) blocked
        // the main thread long enough on busy Fire Stick eMMC that
        // the ANR watchdog fired → SIGKILL. Moving it OFF the main
        // thread eliminates the kill while still applying Lite mode:
        // MainActivity's first composition reads UNSET → falls back
        // to DeviceCapability.detect() → uses Lite UI. When the
        // deferred migration completes ~100ms later, save() pushes
        // LITE to the StateFlow which re-emits and the UI switches
        // permanently to Lite. For PRO users being demoted, they
        // briefly see Pro UI until the StateFlow re-emits — better
        // than the v123 outcome of the app dying entirely.
        val deferredInit = android.os.HandlerThread("hushtv-deferred-init").apply {
            start()
        }
        android.os.Handler(deferredInit.looper).post {
            runCatching {
                // Migration FIRST so the StateFlow re-emits as soon
                // as possible — Compose will recompose to the right
                // mode within one frame of the save() call landing.
                com.hushtv.tv.data.AppModeStore.runAutoMigrationV122IfNeeded(this)
                com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_migration")
                // v1.44.127 — clear app cache (Coil bitmaps, OkHttp
                // Xtream JSON, subtitles, home-rails cache) once per
                // 6 hours. Mimics the Settings → Clear Cache button
                // that several support cases said "fixed it" — we
                // automate it so TV-remote users don't have to dive
                // into Fire OS settings. Throttled so rapid restarts
                // don't all re-download images. MUST run BEFORE
                // XtreamApi.enableDiskCache so OkHttp doesn't have
                // the cache journal open when we delete.
                com.hushtv.tv.data.AppCacheCleaner.clearIfDue(this)
                com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_cache_clear")
                XtreamApi.enableDiskCache(this)
                com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_xtream_cache")
                com.hushtv.tv.data.CrashReporter.uploadIfPending(this)
                com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_crash_upload")
                com.hushtv.tv.data.ExitInfoCollector.collect(this)
                com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_exit_collector")
                com.hushtv.tv.data.ThemePackLoader.loadBundledSync(this)
                com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_themepack")
                com.hushtv.tv.data.AnrWatchdog.start(this)
                com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oc_post_anr_watchdog")
            }.onFailure { e ->
                android.util.Log.e("HushTVApp", "deferred init failed: ${e.message}", e)
            }
            deferredInit.quitSafely()
        }

        // v1.44.112 — (dev only) main-thread StrictMode. Logs any
        // disk read/write or network call accidentally landing on the
        // main thread, plus slow-call detection, so we can spot
        // future regressions before they hit production. Penalty is
        // LOG (never crash) so this stays diagnostic — no behaviour
        // change for the user. Off in canada/official builds.
        if (BuildConfig.UPDATE_CHANNEL == "dev") {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
        }
        com.hushtv.tv.data.BootPhaseTracker.mark(this, "app_oncreate_end")
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
     * Survive known-survivable Compose / Navigation framework race
     * conditions on the main thread. This is the canonical
     * Bugsnag / Crashlytics pattern for in-process exception
     * recovery — we post a runnable that wraps `Looper.loop()` in a
     * `while (true) { try { } catch }` so a survivable exception
     * inside a message dispatch unwinds to our catch block, gets
     * logged, and the looper resumes processing the next message.
     *
     * The exceptions we suppress are all well-documented Compose /
     * Navigation framework bugs:
     *
     * 1. **`FocusRequester is not initialized`** — Compose UI
     *    `focusProperties { up = X }` resolves the target via
     *    `FocusRequester.findFocusTargetNode` on every D-pad key
     *    event. If the target's `Modifier.focusRequester(X)` hasn't
     *    been attached yet (LazyColumn item not laid out, conditional
     *    panel hidden, focus restorer mid-detach), the framework
     *    throws and kills the process. The next D-pad event would
     *    succeed normally — so suppressing this is safe.
     *    Tracked at https://issuetracker.google.com/289010143
     *
     * 2. **`Release should only be called once`** — Compose
     *    Foundation `LazyLayoutPinnableItem.release()` gets called
     *    twice when `Modifier.focusRestorer()` is attached to a
     *    LazyLayout child whose item slot is being recycled at the
     *    same time as the recomposer is detaching it. The recomposer
     *    retries on the next frame, so the user sees a frame skip
     *    instead of an app crash. Tracked at
     *    https://issuetracker.google.com/315214786
     *    Surfaces hardest on Onn Google TV / Chromecast Google TV
     *    because Google TV recomposes more aggressively on focus
     *    transitions than Fire OS does.
     *
     * 3. **`Navigation destination ... cannot be found`** — Compose
     *    Navigation's `NavController.navigate(route)` throws an
     *    `IllegalArgumentException` if the destination hasn't been
     *    added to the graph yet. Race when the user taps a card the
     *    instant after a screen transition. Suppressing means the
     *    nav is silently dropped — the user just taps again.
     *
     * Anything we don't recognise as one of these three is re-thrown
     * so unknown bugs still get reported via [installCrashHandler].
     */
    private fun installMainLooperResilience() {
        Handler(Looper.getMainLooper()).post {
            // Once we're inside a posted runnable, we're already on
            // the main thread. Re-entering Looper.loop() here lets us
            // wrap each message dispatch in our own try/catch.
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    if (!isSuppressibleFrameworkRace(t)) {
                        // Unknown crash — let the default handler
                        // log + report, then re-throw to terminate.
                        Thread.getDefaultUncaughtExceptionHandler()
                            ?.uncaughtException(Thread.currentThread(), t)
                        throw t
                    }
                    Log.w(
                        "HushTVCrash",
                        "Suppressed survivable framework race: " +
                            "${t.javaClass.simpleName}: ${t.message?.take(180)}",
                    )
                }
            }
        }
    }

    private fun isSuppressibleFrameworkRace(t: Throwable): Boolean {
        // Walk the cause chain — Compose often wraps the real cause
        // in DiagnosticCoroutineContextException.
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 6) {
            val msg = cur.message.orEmpty()
            when {
                msg.contains("FocusRequester is not initialized") -> return true
                msg.contains("Release should only be called once") -> return true
                msg.contains("Navigation destination") &&
                    msg.contains("cannot be found") -> return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }

    /**
     * Single, globally-configured Coil ImageLoader tuned for TV browsing
     * with Fire TV 4K (gen 1 + 2) as the primary target hardware.
     *
     * Tuning rationale (v1.43.09 update — Fire Stick perf phase 2):
     *  • **20 % memory cache** (was 12 %): poster grids are heavy
     *    repeat readers — once a card is in memory, subsequent
     *    scrolls + back-navigations should hit it. The previous 12 %
     *    was sized for paranoia about 2 GB devices, but the bigger
     *    Fire Stick 4K gen 1 has 1.5 GB RAM and Coil's LRU eviction
     *    keeps us safely under the system low-memory threshold.
     *  • **allowRgb565(true)** — opaque posters don't need 32-bit
     *    ARGB_8888. RGB_565 stores them in 50 % the bytes with
     *    visually-indistinguishable quality (banding only shows on
     *    smooth gradients, which posters don't have). Cuts heap
     *    pressure roughly in half on poster-dense screens.
     *  • **No crossfade** (already): the 100 ms crossfade animation
     *    feels sluggish on TV scrolls and adds GPU work per frame.
     *  • **Aggressive READ cache policy** (already): repeat scrolls
     *    are instant when the bitmap is in memory.
     *  • **128 MB on-disk image cache** (already): survives reboots,
     *    so a cold start on familiar content paints fast.
     *  • **Hardware bitmaps from API 26+** (already): on hardware
     *    that can decode straight to GPU memory, this skips a CPU
     *    bitmap copy on every render.
     */
    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val builder = ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .components {
                add(SvgDecoder.Factory())
                // v1.44.128-dev — Register the Coil URL mapper that
                // short-circuits image loads to bundled assets BEFORE
                // any network hit. Resolution chain (see HushBundleMapper):
                //   1. DEV-flavor TMDB image bundle (Phase 2, ~16 MB)
                //   2. Server override map (BundleOverrides)
                //   3. APK-bundled streaming/decade/theme assets (44 entries)
                //   4. Pass-through to the original URL
                // NOTE: this was a pre-existing wiring bug — the mapper
                // existed but was never added to .components{}, which
                // meant the original 44-entry BundledAssets table was
                // also silently bypassed. Fixed alongside Phase 2.
                add(HushBundleMapper)
            }
        // v1.44.119/121 — on low-RAM devices (AFTSS / AFTKM / AFTSSS /
        // AFTKRT — anything with per-app heap ≤ 128 MB) we use RGB_565
        // instead of ARGB_8888 for poster bitmaps. That halves bitmap
        // memory (2 bytes/px instead of 4) with imperceptible visual
        // loss at TV viewing distance — ~30 MB saved at peak when the
        // home screen has its row of posters in flight. Full-RAM
        // devices keep ARGB_8888.
        val ctx: Context = this
        if (com.hushtv.tv.data.DeviceClass.isLowRam(ctx)) {
            builder.bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            builder.allowRgb565(true)
        }
        return builder
            .memoryCache {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val totalRamMb = am.memoryClass
                val pct = when {
                    totalRamMb <= 96  -> 0.07
                    totalRamMb <= 128 -> 0.10
                    else              -> 0.20
                }
                MemoryCache.Builder(this)
                    .maxSizePercent(pct)
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
            .allowRgb565(true)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            .build()
    }
}
