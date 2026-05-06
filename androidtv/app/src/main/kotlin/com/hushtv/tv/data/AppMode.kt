package com.hushtv.tv.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.compositionLocalOf

/**
 * v1.44.24 — Lite/Pro plumbing.
 *
 * The app SHIPS A SINGLE SHARED UI TREE (the existing Pro
 * screens). When the user turns on Lite mode, [LocalIsLiteMode]
 * flips to `true` and individual heavy-effect call sites
 * (Ken Burns scale animations, hero auto-cycling, etc.) skip
 * their work. Pro behaviour is unchanged when the flag is
 * `false` (the default).
 *
 * v1.44.19–23 mistakenly built a parallel flat UI in
 * `ui.lite.*`; that has been deleted and replaced with this
 * flag-based approach.
 */

/**
 * v1.44.19 — Lite/Pro mode plumbing.
 *
 * The same APK ships with TWO completely separate UI trees:
 *   • Pro  (`ui.screens.*`)   → cinematic, animated, beautiful
 *   • Lite (`ui.lite.*`)      → flat, fast, animation-free
 *
 * On first launch we detect the device's capability (RAM + CPU
 * cores + manufacturer signal) and recommend one. The user can
 * switch any time via Settings → App Mode.
 *
 * Lite changes physically cannot affect Pro because they live
 * in a separate package and a separate set of files. The only
 * "shared" bits are the data layer (Xtream, TMDB, sports, sync)
 * — which is exactly what we want: no risk of divergent auth
 * keys, stale APIs, or duplicated business logic.
 */
enum class AppMode {
    PRO,
    LITE,
    UNSET; // first-launch sentinel — triggers the recommendation dialog

    companion object {
        fun fromString(s: String?): AppMode = when (s?.uppercase()) {
            "PRO" -> PRO
            "LITE" -> LITE
            else -> UNSET
        }
    }
}

/**
 * Compose-tree flag indicating whether the user has chosen Lite
 * mode. Wrap the menu-route children in
 *   `CompositionLocalProvider(LocalIsLiteMode provides true) { ... }`
 * to enable. Defaults to `false` so any composable that doesn't
 * wrap (or doesn't read this) keeps Pro behaviour exactly.
 */
val LocalIsLiteMode = compositionLocalOf { false }

object AppModeStore {
    private const val PREFS = "app_mode_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_PROMPTED = "prompted"

    fun load(ctx: Context): AppMode {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return AppMode.fromString(raw)
    }

    fun save(ctx: Context, mode: AppMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    /** True once the first-launch capability dialog has been shown
     *  (whether the user picked Lite, Pro, or dismissed it). Stops
     *  the dialog from re-popping on every cold launch. */
    fun hasPrompted(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMPTED, false)

    fun markPrompted(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PROMPTED, true)
            .apply()
    }
}

/**
 * One-shot device-capability detector.
 *
 * v1.44.23 — Rewritten because v1.44.19 was reading
 * ActivityManager.getMemoryClass(), which returns the per-app
 * heap LIMIT (often 96–192MB even on devices with 8GB of RAM).
 * That meant a NVIDIA Shield was being mis-classified as Lite,
 * which is the opposite of correct.
 *
 * The right signal for "is this device actually fast?" is:
 *   1. Total system RAM, via ActivityManager.MemoryInfo.totalMem.
 *   2. Total CPU core count.
 *   3. The brand/manufacturer.
 *
 * Pro is the DEFAULT. Lite is only recommended for devices that
 * meet ALL of: low RAM AND not a known premium brand AND
 * not enough cores. The bar is intentionally low so we don't
 * drag good devices into Lite.
 */
object DeviceCapability {
    data class Result(
        val recommended: AppMode,
        val totalRamMb: Long,
        val cores: Int,
        val manufacturer: String,
        val model: String,
        val reason: String,
    )

    /** Brands that ship dedicated streaming hardware. ALWAYS get Pro
     *  unless the device truly can't handle it (very old generation). */
    private val PREMIUM_DEVICE_BRANDS = setOf(
        "NVIDIA", "AMAZON", "GOOGLE", "XIAOMI", "ONN", "ROKU",
        "TIVO", "DYNALINK", "WALMART",
    )

    fun detect(ctx: Context): Result {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am.getMemoryInfo(memInfo) }
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        val cores = runCatching { Runtime.getRuntime().availableProcessors() }.getOrDefault(2)
        val manuf = (Build.MANUFACTURER ?: "").uppercase()
        val model = (Build.MODEL ?: "").uppercase()

        // Premium dedicated streaming devices: always Pro.
        // (NVIDIA Shield, Fire TV / Fire Stick 4K, Chromecast w/ Google TV,
        //  Onn 4K Pro, Xiaomi Mi Box 4K, Roku, etc.)
        val isPremiumBrand = PREMIUM_DEVICE_BRANDS.any { manuf.contains(it) }
        if (isPremiumBrand) {
            return Result(
                AppMode.PRO, totalMb, cores, manuf, model,
                reason = "Powerful streaming device — Pro will run smoothly.",
            )
        }

        // For everything else (built-in smart TVs, generic boxes,
        // unknown brands), use objective performance heuristics:
        //   • >= 2GB total RAM
        //   • >= 4 CPU cores
        // Anything below either bar gets Lite.
        val plentyRam = totalMb >= 2048
        val plentyCores = cores >= 4

        return when {
            plentyRam && plentyCores -> Result(
                AppMode.PRO, totalMb, cores, manuf, model,
                reason = "Your device looks capable — Pro is recommended.",
            )
            else -> Result(
                AppMode.LITE, totalMb, cores, manuf, model,
                reason = "Built-in smart TVs run smoother on Lite.",
            )
        }
    }
}
