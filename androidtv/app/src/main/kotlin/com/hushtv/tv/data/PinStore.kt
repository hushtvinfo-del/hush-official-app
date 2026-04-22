package com.hushtv.tv.data

import android.content.Context

/** Parental PIN + locked-categories store. */
object PinStore {

    private const val PREFS = "hushtv_pin"
    private const val K_PIN = "pin"
    private const val K_LOCKED = "locked_categories" // comma-separated category_ids

    fun hasPin(ctx: Context): Boolean =
        !ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_PIN, null).isNullOrBlank()

    fun setPin(ctx: Context, pin: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_PIN, pin).apply()
    }

    fun clearPin(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(K_PIN).remove(K_LOCKED).apply()
    }

    fun checkPin(ctx: Context, attempt: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_PIN, null) == attempt

    fun lockedCategoryIds(ctx: Context): Set<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LOCKED, "")
            ?: return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun isLocked(ctx: Context, categoryId: String): Boolean =
        lockedCategoryIds(ctx).contains(categoryId)

    fun toggleLock(ctx: Context, categoryId: String): Boolean {
        val set = lockedCategoryIds(ctx).toMutableSet()
        val nowLocked = if (set.contains(categoryId)) { set.remove(categoryId); false }
                       else { set.add(categoryId); true }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_LOCKED, set.joinToString(","))
            .apply()
        return nowLocked
    }
}

/** In-memory "I already entered the PIN this session" so we don't re-prompt
 *  on every category switch within the same app run. */
object SessionPinGate {
    private val unlocked = mutableSetOf<String>()
    fun isUnlocked(categoryId: String) = unlocked.contains(categoryId)
    fun markUnlocked(categoryId: String) { unlocked.add(categoryId) }
    fun reset() { unlocked.clear() }
}
