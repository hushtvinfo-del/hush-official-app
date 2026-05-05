package com.hushtv.tv.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Identifies whether the current device is a touch-first device
 * that happens to have been routed into the TV layout.
 *
 * Why this exists
 * ───────────────
 * `MainActivity` sends any device with `smallestScreenWidthDp >= 600`
 * into the TV `AppContent` path. That correctly covers Leanback TVs
 * AND tablets like the Galaxy Tab S series. But TV layouts are
 * built around D-pad focus navigation — their scroll semantics
 * (page-at-a-time Channel Up/Down on the home screen, for example)
 * don't respond to a finger.
 *
 * When [LocalIsTouchDevice] is true, touch-aware affordances are
 * added to TV layouts without changing anything about how they
 * look or behave on a true TV:
 *
 *   • vertical swipe on the home screen paginates between sections
 *     (equivalent to Channel Up / Down)
 *   • floating chevron buttons anchor to the right edge for
 *     explicit tap navigation between pages
 *
 * The value is set once at app root from the device configuration
 * and stays constant across the composition — it's fine as a
 * static local.
 */
val LocalIsTouchDevice = staticCompositionLocalOf { false }
