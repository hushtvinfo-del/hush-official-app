package com.hushtv.tv

/**
 * Process-scoped one-shot flag for the boot-refresh splash.
 *
 * Why static and not a SavedStateHandle? Because we want exactly
 * "did we run boot refresh once during the lifetime of this JVM
 * process?" — Activity recreation (rotation / configuration change)
 * keeps the flag set, full process death wipes it. That's the
 * platform-correct way to model "cold start" without false positives.
 */
object BootGate {
    @Volatile var didBootRefresh: Boolean = false
}
