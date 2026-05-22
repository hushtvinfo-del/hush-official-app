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
    // v1.44.122 — one-shot key for the auto-migration. Once true,
    // we never run the migration again so a user who deliberately
    // sets Pro on a Fire Stick after the migration is respected
    // and never silently demoted back to Lite.
    private const val KEY_AUTO_MIGRATED_V122 = "auto_migrated_v122"
    // v1.44.122 — one-shot key for the "We optimized your device"
    // toast. Set after the migration fires so the toast shows once
    // on the FIRST launch of v1.44.122, then never again.
    private const val KEY_MIGRATION_TOAST_PENDING = "migration_toast_pending"

    // v1.44.115 — reactive cache. Settings calls save(), Compose reads
    // flow(). Previously the live UI never picked up a mode change
    // because callers used `remember { load(ctx) }` which only runs
    // once per composition. With the flow, every observer recomposes
    // immediately when the user toggles Lite/Pro from Settings.
    @Volatile private var _flow: kotlinx.coroutines.flow.MutableStateFlow<AppMode>? = null

    private fun flowFor(ctx: Context): kotlinx.coroutines.flow.MutableStateFlow<AppMode> {
        _flow?.let { return it }
        synchronized(this) {
            _flow?.let { return it }
            val f = kotlinx.coroutines.flow.MutableStateFlow(load(ctx))
            _flow = f
            return f
        }
    }

    /** Compose-friendly observable. Read with `flow(ctx).collectAsState()`
     *  in MainActivity so any save() call from Settings re-renders the
     *  whole tree with the new LocalIsLiteMode value. */
    fun flow(ctx: Context): kotlinx.coroutines.flow.StateFlow<AppMode> =
        flowFor(ctx)

    fun load(ctx: Context): AppMode {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return AppMode.fromString(raw)
    }

    fun save(ctx: Context, mode: AppMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .apply()
        // Push to all observers so the UI updates without needing a
        // fresh activity composition.
        flowFor(ctx).value = mode
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

    /** Returns "LITE", "PRO", or "UNSET" for inclusion in crash /
     *  exit-info / boot-died report payloads so the admin Crashes tab
     *  can filter by mode and we can confirm migrations actually took
     *  effect on the device. Best-effort — returns "?" on any read
     *  failure. */
    fun snapshotMode(ctx: Context): String =
        runCatching { load(ctx).name }.getOrDefault("?")

    /** Returns "migrated_v122", "pristine", or "?". Lets the admin
     *  see at a glance whether a crashing user has actually had the
     *  v1.44.122/123 auto-migration applied. */
    fun snapshotMigrationState(ctx: Context): String = runCatching {
        val migrated = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_MIGRATED_V122, false)
        if (migrated) "migrated_v122" else "pristine"
    }.getOrDefault("?")

    /**
     * v1.44.122 — one-shot migration for users who landed on PRO mode
     * on a Fire Stick (or any device that the v1.44.121 capability
     * detector wrongly flagged as "premium" via brand alone). The
     * v1.44.120 telemetry confirmed 1,700+ silent OOM kills/day on
     * Fire Sticks running Pro, while Skyworth and similar TVs on
     * Lite have zero crashes.
     *
     * v1.44.123 — also applies to UNSET (fresh-install) users whose
     * device the detector recommends as Lite. The v1.44.122 release
     * left a hole: a user who uninstalls + reinstalls (like
     * irandall16@hotmail.com on an AFTKRT) lands with `KEY_MODE=null`
     * → `currentMode=UNSET` → migration was skipped → UI defaulted to
     * Pro (because `MainActivity.isLite = savedMode == LITE` treats
     * UNSET as not-Lite). Now an UNSET user also gets the recommended
     * mode written to prefs immediately, so the app composes the
     * RIGHT UI on the very first frame.
     *
     * Migration rule (v1.44.123): if the device detector recommends
     * LITE AND the user is currently on PRO **or UNSET**, write LITE
     * to prefs. PRO users also get the one-time toast; UNSET users
     * don't (they were never running Pro to begin with, no surprise
     * to explain).
     *
     * Runs at-most-once per device — gated by [KEY_AUTO_MIGRATED_V122].
     * If the user manually toggles to Pro from Settings later, their
     * choice is respected forever because the gate never re-fires.
     *
     * @return `true` if the migration ran AND should show a toast
     * (only true for PRO → LITE, not UNSET → LITE).
     */
    fun runAutoMigrationV122IfNeeded(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AUTO_MIGRATED_V122, false)) return false
        // Mark the gate FIRST so a crash mid-migration can't loop.
        prefs.edit().putBoolean(KEY_AUTO_MIGRATED_V122, true).apply()

        val currentRaw = prefs.getString(KEY_MODE, null)
        val currentMode = AppMode.fromString(currentRaw)
        // v1.44.123 — accept UNSET (fresh install) in addition to PRO.
        if (currentMode != AppMode.PRO && currentMode != AppMode.UNSET) return false

        val recommended = runCatching { DeviceCapability.detect(ctx).recommended }
            .getOrDefault(AppMode.PRO)
        if (recommended != AppMode.LITE) return false

        // Demote: switch to Lite. Toast only fires for PRO→LITE
        // transitions; UNSET→LITE is the first-launch experience so
        // we shouldn't surprise the user with a "we changed your
        // settings" message they never set.
        save(ctx, AppMode.LITE)
        if (currentMode == AppMode.PRO) {
            prefs.edit().putBoolean(KEY_MIGRATION_TOAST_PENDING, true).apply()
            return true
        }
        return false
    }

    /** Read + clear the one-time post-migration toast flag. Returns
     *  `true` exactly once after [runAutoMigrationV122IfNeeded]
     *  flipped the mode; every subsequent call returns `false`. */
    fun consumeMigrationToast(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_MIGRATION_TOAST_PENDING, false)) return false
        prefs.edit().putBoolean(KEY_MIGRATION_TOAST_PENDING, false).apply()
        return true
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

    /** Brands that ship genuinely-powerful streaming hardware where
     *  brand alone is enough to guarantee Pro can run smoothly.
     *
     *  v1.44.122 — AMAZON removed. The brand alone is not enough
     *  because Fire OS spans a huge range from the Fire TV Cube
     *  (3rd gen, 2 GB heap class, fast A73 cores) all the way down
     *  to the AFTKM Fire Stick Lite (96 MB heap, slow A55 cores)
     *  which crashes within 10-15 s of the home screen mounting in
     *  Pro mode (v1.44.120 telemetry confirmed). Amazon devices now
     *  fall through to the objective memoryClass + RAM + cores test
     *  below — only the actual Fire TV Cube (≥192 MB heap) gets PRO,
     *  every Fire Stick gets LITE. Same UI Skyworth users already
     *  run, which has zero crashes on weaker hardware. */
    private val PREMIUM_DEVICE_BRANDS = setOf(
        "NVIDIA",
        // Note: "GOOGLE" stays (Chromecast 4K w/ Google TV is genuinely
        // fast, all current Pixel Tablet variants are fast). "XIAOMI"
        // (Mi Box S 4K is fast). "ONN" (the 4K Pro is genuinely capable).
        // "ROKU" doesn't run our APK anyway. Brand-only bypass is for
        // devices where the WHOLE catalog is fast.
        "GOOGLE", "XIAOMI", "ONN", "ROKU",
        "TIVO", "DYNALINK", "WALMART",
    )

    /** Per-app Java heap budget (memoryClass) is the most reliable
     *  signal that a device can actually run the Pro Compose UI.
     *  Fire Sticks all sit at 96 or 128 MB. Fire TV Cube (3rd gen)
     *  and NVIDIA Shield Pro sit at 192+ MB. Standard Android TV
     *  set-tops sit at 96-128 MB. The 192 MB floor cleanly separates
     *  "real streaming box" from "embedded TV stick". */
    private const val PRO_HEAP_CLASS_FLOOR_MB = 192

    fun detect(ctx: Context): Result {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am.getMemoryInfo(memInfo) }
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        val cores = runCatching { Runtime.getRuntime().availableProcessors() }.getOrDefault(2)
        val manuf = (Build.MANUFACTURER ?: "").uppercase()
        val model = (Build.MODEL ?: "").uppercase()
        // v1.44.122 — actual per-app heap budget. The objective test
        // for whether the Pro Compose tree can run without OOM-killing
        // the device.
        val heapMb = runCatching { am.memoryClass }.getOrDefault(0)

        // Premium dedicated streaming devices: always Pro.
        // (NVIDIA Shield, Chromecast w/ Google TV 4K, Mi Box 4K, etc.)
        // v1.44.122 — Amazon removed; Fire Sticks fall through to the
        // objective heap+RAM+cores test so AFTSS/AFTKM/AFTSSS/AFTKRT
        // get LITE (same UI Skyworth users already run smoothly).
        val isPremiumBrand = PREMIUM_DEVICE_BRANDS.any { manuf.contains(it) }
        if (isPremiumBrand) {
            return Result(
                AppMode.PRO, totalMb, cores, manuf, model,
                reason = "Powerful streaming device — Pro will run smoothly.",
            )
        }

        // v1.44.122 — objective performance heuristic. ALL THREE must
        // pass for Pro:
        //   • per-app heap class >= 192 MB (= "real streaming box",
        //     not a Fire Stick / generic TV stick)
        //   • total RAM >= 2 GB
        //   • >= 4 CPU cores
        // The heap-class gate is the new safety net that catches the
        // 2 GB Fire Sticks (AFTSSS/AFTKRT) — they have enough total
        // RAM and cores but their per-app heap is only 128 MB which
        // OOM-stalls the Pro Compose tree on first composition.
        val plentyHeap = heapMb >= PRO_HEAP_CLASS_FLOOR_MB
        val plentyRam = totalMb >= 2048
        val plentyCores = cores >= 4

        return when {
            plentyHeap && plentyRam && plentyCores -> Result(
                AppMode.PRO, totalMb, cores, manuf, model,
                reason = "Your device looks capable — Pro is recommended.",
            )
            else -> Result(
                AppMode.LITE, totalMb, cores, manuf, model,
                reason = "Lite is faster and more stable on this device.",
            )
        }
    }
}
