package com.hushtv.tv.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Pins the focused item in a Lazy layout to a consistent spatial position
 * (parentFraction of the viewport). Without this, stock `LazyVerticalGrid`
 * can visibly "scroll in the wrong direction" when the D-pad moves focus to
 * an item near the edges: the grid snaps just-enough to reveal the new item,
 * which on a 10-ft TV reads as jittery / unpredictable.
 *
 * This is the Compose Foundation 1.7.0+ replacement for the deprecated
 * `TvLazyVerticalGrid.pivotOffsets` (see Android dev docs: Create scrollable
 * layouts for TV).
 *
 *  • parentFraction 0f  = focused item at TOP of grid
 *  • parentFraction 0.3f = focused row sits ~30% from top (recommended TV default)
 *  • parentFraction 1f  = focused item at BOTTOM
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PositionFocusedItemInLazyLayout(
    parentFraction: Float = 0.3f,
    childFraction: Float = 0f,
    content: @Composable () -> Unit,
) {
    val bringIntoViewSpec = remember(parentFraction, childFraction) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val initialTargetForLeadingEdge =
                    parentFraction * containerSize - (childFraction * size)
                val targetForLeadingEdge =
                    if (size <= containerSize &&
                        (containerSize - initialTargetForLeadingEdge) < size
                    ) {
                        // Near end of list — align trailing edge so we don't
                        // over-scroll past the final item.
                        containerSize - size
                    } else {
                        initialTargetForLeadingEdge
                    }
                return offset - targetForLeadingEdge
            }
        }
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        content = content,
    )
}
