package com.hushtv.tv.data

import android.app.ActivityManager
import android.content.Context

/**
 * Detect "low-RAM" devices so heavy-memory features can be selectively
 * disabled at runtime, keeping the full Pro UI on capable hardware
 * while preventing OOM kills on 1 GB-RAM Fire Sticks.
 *
 * Target devices (kernel-killed without producing a Java crash log):
 *   • AFTSS  — Fire TV Stick 4K (2018), 1 GB RAM   (memoryClass=96)
 *   • AFTKM  — Fire TV Stick Lite (2020), 1 GB RAM (memoryClass=96)
 *
 * Heuristic: `ActivityManager.memoryClass` reports the per-app Java
 * heap budget. On Fire Stick Lite / 4K (2018) this is 96 MB; on
 * Fire Stick 4K Max / 4K (2023) it's 128 MB; on 4 GB+ devices it's
 * 192+ MB. We treat ≤ 96 MB as low-RAM.
 *
 * v1.44.122 — threshold REVERTED to ≤ 96 MB. The 128 MB devices
 * (AFTSSS / AFTKRT) that v1.44.121 added to this trim are now
 * auto-demoted to Lite mode by `DeviceCapability.detect()`, which
 * is a much better fix because it skips the entire heavy Compose
 * tree (Ken Burns animator, AnimatedContent crossfade, hero
 * AsyncImage cascade) — not just bitmap allocation. RGB_565 alone
 * was treating the symptom; Lite mode treats the cause.
 *
 * v1.44.121 — threshold bumped from 96 → 128 (superseded).
 * v1.44.119 — introduced as Step-1 "Memory-Trimmed Pro" for
 * AFTSS / AFTKM users still on Pro.
 */
object DeviceClass {

    // v1.44.122 — reverted to 96 (Lite-mode auto-migration handles
    // the 128 MB devices now).
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
