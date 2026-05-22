package com.hushtv.tv.data

import android.app.ActivityManager
import android.content.Context

/**
 * Detect "low-RAM" devices so heavy-memory features can be selectively
 * disabled at runtime, keeping the full Pro UI on capable hardware
 * while preventing OOM kills on 1-2 GB-RAM Fire Sticks.
 *
 * Target devices (kernel-killed without producing a Java crash log;
 * v1.44.120 ExitInfoCollector confirmed 1,176 OOM/SIGKILL events vs
 * only 3 native crashes — this is a memory problem, not a code bug):
 *   • AFTSS   — Fire TV Stick 4K (2018), 1 GB RAM     (memoryClass=96)
 *   • AFTKM   — Fire TV Stick Lite (2020), 1 GB RAM   (memoryClass=96)
 *   • AFTSSS  — Fire TV Stick 4K Max (early 2021), 2 GB but tight (memoryClass=128)
 *   • AFTKRT  — Fire TV Stick 4K (2nd gen 2023), 2 GB (memoryClass=128) — top crash device in telemetry
 *
 * Heuristic: `ActivityManager.memoryClass` reports the per-app Java
 * heap budget. On Fire Stick Lite / 4K (2018) this is 96 MB; on
 * Fire Stick 4K Max / 4K (2023) it's 128 MB; on 4 GB+ devices it's
 * 192+ MB.
 *
 * v1.44.121 — threshold bumped from ≤ 96 MB → ≤ 128 MB after the
 * v1.44.120 telemetry revealed AFTSSS / AFTKRT were the TOP crash
 * devices (113 + 49 OOM kills in 2 days) despite being above the
 * old 96 MB threshold. Both devices have memoryClass=128 MB → were
 * getting the full memory-hungry UI even though their per-app heap
 * is tight enough that the home screen's poster + backdrop peaks
 * trip the kernel lmkd.
 *
 * v1.44.119 — introduced as Step-1 "Memory-Trimmed Pro" so
 * AFTSS / AFTKM users get the same Pro UI as everyone else
 * but with the heaviest memory peaks (Discovery hero backdrops,
 * up-front Sports + PPV preload, full-res poster bitmaps, ahead-of-
 * time EPG fetches) gated off.
 */
object DeviceClass {

    // v1.44.121 — bumped from 96 → 128. See class doc above.
    private const val LOW_RAM_HEAP_MB = 128

    @Volatile private var cached: Boolean? = null

    fun isLowRam(ctx: Context): Boolean {
        cached?.let { return it }
        val v = compute(ctx)
        cached = v
        return v
    }

    private fun compute(ctx: Context): Boolean {
        return runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.memoryClass <= LOW_RAM_HEAP_MB || am.isLowRamDevice
        }.getOrDefault(false)
    }

    /** Returns the Coil image-request size budget for posters. Full-RAM
     *  devices use the source resolution (-1, -1 = native). Low-RAM
     *  devices clamp posters to 360 px wide so a single bitmap is
     *  ≈ 360 × 540 × 4 = 0.75 MB instead of 1.5 MB at the typical
     *  500 × 750 w500 size. The visible quality difference on a TV
     *  viewed from 3+ metres is imperceptible. */
    fun posterMaxWidthPx(ctx: Context): Int =
        if (isLowRam(ctx)) 360 else -1
}
