package com.hushtv.tv.util

import android.util.Log

/**
 * Single-tag logger for navigation/focus debugging on TV.
 *
 * Run on device to follow the focus dance live:
 *
 *     adb logcat -s HushTVNav
 *
 * Logs are tagged "HushTVNav" so a `grep` cuts through every other
 * Compose / Coil / Media3 line. Disabled in release flavour by the
 * BuildConfig.DEBUG check so production users never see them.
 */
object HushTVNav {
    private const val TAG = "HushTVNav"

    @JvmStatic
    fun d(msg: String) {
        if (com.hushtv.tv.BuildConfig.DEBUG) {
            Log.d(TAG, msg)
        }
    }
}
