package com.hushtv.tv.data

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.hushtv.tv.BuildConfig
import java.util.concurrent.atomic.AtomicReference

/**
 * v1.44.138-dev — Singleton holding the currently-selected
 * [HushProfile] for the running app process.
 *
 * Why a singleton vs reading SharedPreferences on every lookup
 * ────────────────────────────────────────────────────────────
 * Profile-scoped stores (WatchProgressStore, MyListStore,
 * LastChannelStore, etc.) need to read the active profile id
 * THOUSANDS of times per home-screen render to build their
 * per-profile keys. SharedPreferences reads are fast but not
 * free — keeping the active profile in an `AtomicReference`
 * makes the lookup an instant pointer read.
 *
 * The reference is hydrated by ProfileGate.onActiveChanged and
 * read everywhere else via [requireProfileId] / [profileIdOrAccount].
 *
 * Flavor behaviour (v1.44.138):
 *   • dev flavor — profile system ACTIVE. Every keyed store
 *     uses [requireProfileId] so we never accidentally read
 *     data from the wrong profile.
 *   • canada / official — profile system DORMANT. [isActive]
 *     returns false; profile-scoped stores fall back to their
 *     legacy account-scoped keys. The data model on these
 *     flavors is unchanged from v1.44.137.
 */
object ActiveProfile {
    /** True only when the profile system is wired in (currently
     *  dev flavor only). Canada / official will flip true when the
     *  user gives the green light to promote. */
    val isActive: Boolean = BuildConfig.UPDATE_CHANNEL == "dev"

    private val current = AtomicReference<HushProfile?>(null)

    /** Set by ProfileGate after the user picks a profile (or after
     *  the first-launch migration creates one). Caller's
     *  responsibility to also call [ProfileStore.setActive] to
     *  persist it. */
    fun set(profile: HushProfile?) {
        current.set(profile)
    }

    /** Active profile in this process. `null` BEFORE the picker
     *  finishes (handled by ProfileGate) or on non-active flavors. */
    fun get(): HushProfile? = current.get()

    /** Convenience — returns the profile id when active, or
     *  `account` (a username-scoped sentinel) on non-active
     *  flavors and during the bootstrap window before ProfileGate
     *  has chosen one. */
    fun profileIdOrAccount(): String = current.get()?.id ?: "account"

    /** Stricter variant — throws if called before a profile has
     *  been set on an active-flavor build. Use this for write
     *  paths that should never run before profile-gate completes. */
    fun requireProfileId(): String {
        if (!isActive) return "account"
        return current.get()?.id ?: error(
            "ActiveProfile.requireProfileId called before ProfileGate selected a profile",
        )
    }

    /** True when the active profile has the Kids flag set. Used
     *  by category filters in Live TV / Movies / Series lists. */
    fun isKids(): Boolean = current.get()?.isKids == true
}

/** Compose locals — view code reads these instead of touching
 *  ActiveProfile directly so previews and unit tests can swap in
 *  fakes. */
val LocalActiveProfile = compositionLocalOf<HushProfile?> { null }


/**
 * v1.44.138-dev — Helper that turns a legacy username-scoped store
 * key into a profile-scoped key when the profile system is active.
 *
 * Example call site:
 *   prefs.getString(scopedKey(ctx, username, "last_channel"), null)
 *
 * On the dev flavor with profile system ACTIVE:
 *   → "p:abc-123-def:last_channel"
 *
 * On canada / official:
 *   → "last_channel"    (back-compat, behaves identically to v1.44.137)
 *
 * The "p:" prefix on profile keys is what guarantees we never
 * accidentally cross-read between flavors — a canada APK can't see
 * dev-flavor "p:..." entries and vice versa, even if the user's
 * data directory survives a side-by-side install.
 */
fun scopedKey(suffix: String): String {
    if (!ActiveProfile.isActive) return suffix
    val pid = ActiveProfile.get()?.id ?: return suffix  // pre-gate fallback
    return "p:$pid:$suffix"
}
