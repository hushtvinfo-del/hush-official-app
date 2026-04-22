package com.hushtv.tv.ui.player

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier

/** Aspect-ratio handling for the video surface. */
enum class AspectMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    ZOOM("Zoom");

    fun next(): AspectMode {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}

fun Modifier.applyAspectMode(mode: AspectMode, intrinsicRatio: Float?): Modifier = when (mode) {
    AspectMode.FIT -> intrinsicRatio?.let { this.aspectRatio(it) } ?: this
    AspectMode.FILL -> this
    AspectMode.RATIO_16_9 -> this.aspectRatio(16f / 9f)
    AspectMode.RATIO_4_3 -> this.aspectRatio(4f / 3f)
    AspectMode.ZOOM -> this.aspectRatio((intrinsicRatio ?: (16f/9f)) * 1.15f)
}
