package com.hushtv.tv.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build

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
 * One-shot device-capability detector. Combines three signals:
 *
 *   1. ActivityManager.getMemoryClass() — Android's official RAM
 *      hint for our process. < 192 MB strongly suggests low-end.
 *
 *   2. Runtime.availableProcessors() — fewer than 4 cores on a TV
 *      box almost always means a 2018-era SoC.
 *
 *   3. Manufacturer / model heuristics — known-good devices
 *      (NVIDIA Shield, Fire TV 4K, Chromecast 4K) get Pro by
 *      default; known-cheap brands (some Hisense / TCL Roku TV
 *      / generic "MIBOX" SOCs) lean Lite.
 *
 * Returns the RECOMMENDATION; the user can override.
 */
object DeviceCapability {
    data class Result(
        val recommended: AppMode,
        val memoryClassMb: Int,
        val cores: Int,
        val manufacturer: String,
        val model: String,
        val reason: String,
    )

    private val PRO_OK_MANUFACTURERS = setOf(
        "NVIDIA", "AMAZON", "GOOGLE", "ONN", "XIAOMI",
    )

    fun detect(ctx: Context): Result {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memMb = runCatching { am.memoryClass }.getOrDefault(0)
        val cores = runCatching { Runtime.getRuntime().availableProcessors() }.getOrDefault(2)
        val manuf = (Build.MANUFACTURER ?: "").uppercase()
        val model = (Build.MODEL ?: "").uppercase()

        // Hard heuristic — definitely Pro
        val proOkBrand = PRO_OK_MANUFACTURERS.any { manuf.contains(it) }
        val plentyRam = memMb >= 256
        val plentyCores = cores >= 4

        // Hard heuristic — definitely Lite
        val verySmallRam = memMb in 1..191
        val fewCores = cores < 4

        return when {
            verySmallRam || fewCores -> Result(
                AppMode.LITE, memMb, cores, manuf, model,
                reason = "Low memory (${memMb}MB) or few cores ($cores)"
            )
            proOkBrand && plentyRam && plentyCores -> Result(
                AppMode.PRO, memMb, cores, manuf, model,
                reason = "Premium device ($manuf $model)"
            )
            plentyRam && plentyCores -> Result(
                AppMode.PRO, memMb, cores, manuf, model,
                reason = "Capable device (${memMb}MB RAM, $cores cores)"
            )
            else -> Result(
                AppMode.LITE, memMb, cores, manuf, model,
                reason = "Marginal device (${memMb}MB RAM, $cores cores)"
            )
        }
    }
}
