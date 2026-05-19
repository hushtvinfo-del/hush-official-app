package com.hushtv.tv.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Cleanly close HushTV and return the user to the Fire TV / Android TV
 * launcher.
 *
 * Why this exists
 * ───────────────
 * Calling [Activity.finish] on the root activity of a `singleTask`
 * launchMode app on Fire TV (in release builds especially) leaves
 * the task in the recents list, and Fire TV's keep-alive logic
 * immediately resurrects it — the user sees the splash screen
 * come back instead of the launcher. This was first observed when
 * we shipped v1.44.96, the first release-mode build. Debug builds
 * didn't have the issue because the OS treats them differently
 * with respect to task restoration.
 *
 * What this does instead
 * ──────────────────────
 * 1. [Activity.finishAndRemoveTask] — finishes the activity AND
 *    explicitly removes the task entry. Fire TV won't try to
 *    bring it back.
 * 2. [Process.killProcess] — belt-and-suspenders for the most
 *    stubborn Fire Sticks. Forces the app process to terminate so
 *    no lingering state (foreground service, baseline-profile
 *    installer thread) can keep the task alive.
 *
 * Must be called from a Compose composable's [LocalContext]:
 *
 *     val ctx = LocalContext.current
 *     // …
 *     exitToLauncher(ctx)
 */
fun exitToLauncher(ctx: Context) {
    val activity = ctx as? Activity ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        activity.finishAndRemoveTask()
    } else {
        @Suppress("DEPRECATION")
        activity.finishAffinity()
    }
    // Fire TV resurrection guard. After ~150 ms the activity is
    // finished — anything still in our process at that point would
    // give Fire TV an excuse to relaunch. Killing the PID makes
    // the task entry uncontested.
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        Process.killProcess(Process.myPid())
    }, 150)
}
