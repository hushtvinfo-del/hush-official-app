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
        installCrashHandler()
        // v1.43.83 → v1.43.88: REMOVED installMainLooperResilience.
        // The re-entrant `Looper.loop()` posted as a runnable was
        // suspected of preventing crash-log uploads on Google TV /
        // Fire OS 12+ (the platforms throw on nested looper entry).
        // We're better off letting Android's default crash dialog
        // fire — the JVM uncaught-exception handler installed
        // above persists the trace to disk for next-boot upload,
        // and unknown framework races (FocusRequester, LazyLayout
        // pinnable, Navigation destination) happen rarely enough
        // that swallowing them isn't worth the diagnostic blackout.
        com.hushtv.tv.data.EventLog.log(
            "app",
            "onCreate v${BuildConfig.VERSION_NAME}#${BuildConfig.VERSION_CODE}",
        )
        // Enable HTTP disk cache for Xtream JSON calls.
        XtreamApi.enableDiskCache(this)
        // v1.43.86 — load any cached bundled-asset overrides from
        // disk so the Coil mapper can short-circuit immediately on
        // first frame.
        // **DISABLED in v1.43.87** — see HushTVApp.newImageLoader().
        // com.hushtv.tv.data.BundleOverrides.load(this)
        // If a crash was captured during the last session, POST it to
        // the server in the background. Silent-no-op if nothing's new.
        com.hushtv.tv.data.CrashReporter.uploadIfPending(this)
        // v1.44.54 — load the themed-pack bundle from APK assets so
        // any "extra" Moods & Themes rows are merged into
        // HushThemedLists.all on the first composition. Cheap
        // (~20 ms, runs once). Remote refresh is kicked off later
        // by BootRefreshScreen alongside the main library prime.
        com.hushtv.tv.data.ThemePackLoader.loadBundledSync(this)
        // v1.44.3 — start the ANR watchdog. Detects main-thread freezes
        // >4s and writes a stack trace to anr.log so the next launch
        // can ship it. Closes the diagnostic gap that ate v1.44.0–.2:
        // ANRs are SIGKILL'd by the OS and bypass every JVM handler.
        com.hushtv.tv.data.AnrWatchdog.start(this)

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
            }
        // v1.44.119 — on low-RAM devices (AFTSS / AFTKM / 1 GB Fire
        // Sticks) we use RGB_565 instead of ARGB_8888 for poster
        // bitmaps. That halves bitmap memory (2 bytes/px instead of
        // 4) with imperceptible visual loss at TV viewing distance —
        // ~30 MB saved at peak when the home screen has its row of
        // posters in flight. Full-RAM devices keep ARGB_8888.
        val ctx: Context = this
        if (com.hushtv.tv.data.DeviceClass.isLowRam(ctx)) {
            builder.bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            builder.allowRgb565(true)
        }
        return builder
            .memoryCache {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val totalRamMb = am.memoryClass
                // v1.44.119 — even tighter on AFTSS / AFTKM (≤96 MB heap).
                // 7% on those devices ≈ 7 MB which is enough for the
                // currently-visible row of posters but leaves serious
                // headroom for ExoPlayer + EPG + Compose.
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
