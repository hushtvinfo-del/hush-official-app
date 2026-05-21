package com.hushtv.tv.data

import android.app.ActivityManager
import android.content.Context

/**
 * Detect "low-RAM" devices so heavy-memory features can be selectively
 * disabled at runtime, keeping the full Pro UI on capable hardware
 * while preventing OOM kills on 1 GB-RAM Fire Sticks.
 *
 * Target devices (kernel-killed without producing a crash log):
 *   • AFTSS  — Fire TV Stick 4K (2018), 1 GB RAM
 *   • AFTSSS — Fire TV Stick 4K Max (early 2021), 2 GB RAM but tight
 *   • AFTKM  — Fire TV Stick Lite (2020), 1 GB RAM
 *
 * Heuristic: `ActivityManager.memoryClass` reports the per-app Java
 * heap budget. On Fire Stick Lite / 4K (2018) this is 96 MB; on 2-4 GB
 * devices it's 192+ MB. We treat ≤ 96 MB as low-RAM.
 *
 * v1.44.119 — introduced as part of Step-1 "Memory-Trimmed Pro" so
 * AFTSS / AFTKM / AFTSSS users get the same Pro UI as everyone else
 * but with the heaviest memory peaks (Discovery hero backdrops,
 * up-front Sports + PPV preload, full-res poster bitmaps, ahead-of-
 * time EPG fetches) gated off.
 */
object DeviceClass {

    private const val LOW_RAM_HEAP_MB = 96

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
