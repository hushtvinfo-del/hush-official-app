package com.hushtv.tv.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * v1.44.138-dev — Multi-profile (Netflix-style "Who's Watching?")
 * support. A *profile* is a viewing identity within a paid HushTV
 * account. Multiple profiles per Xtream username:
 *   • Mom / Dad / Kids / Guest, each with their own Continue Watching,
 *     My List, Last Channel, Watch Progress, Themed match cache, etc.
 *   • Cross-device sync moves from `<xtream_username>/...` keys to
 *     `<xtream_username>/p/<profileId>/...` keys, so Mom's TV in
 *     the basement and Mom's tablet in the kitchen share state, but
 *     Mom's data doesn't bleed into Kids on the upstairs Fire Stick.
 *
 * The profile LIST itself is account-scoped — when Mom adds a Kid
 * profile on TV A, it appears on TV B too. License, trial state,
 * Xtream credentials, and master Parental Controls PIN are also
 * account-scoped.
 *
 * Hard cap: 5 profiles per account (matches Netflix).
 *
 * Storage: a single JSON blob in SharedPreferences under the
 * Xtream username, written atomically on every mutate. We don't
 * use multiple per-profile prefs files because the profile list
 * is a small fixed-size set we read constantly during navigation.
 *
 * Kids profile lockdown (v1.44.138-dev):
 *   • Adult-keyword categories are filtered out of Live TV /
 *     Movies / Series lists.
 *   • Switching OUT of a Kids profile requires entering the
 *     account's master PIN (configured in Parental Controls).
 *     The Kids profile itself has no extra PIN — it's the EXIT
 *     that's locked, not the ENTRY.
 *
 * Sync coverage:
 *   • Add / rename / delete / kid-toggle / avatar-change all
 *     propagate via SyncEngine at the username level, so the
 *     household sees a single coherent profile list across all
 *     their devices.
 */
data class HushProfile(
    val id: String,            // UUID — stable identity, used in sync keys
    val name: String,          // user-visible label, 1-20 chars
    val avatarId: String,      // key into AvatarCatalog, e.g. "h03_kenji"
    val isKids: Boolean,       // adult-content lockdown when true
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("avatar", avatarId)
        put("kids", isKids)
        put("c", createdAt)
        put("u", updatedAt)
    }

    companion object {
        const val MAX_PROFILES = 5
        const val MIN_NAME_LEN = 1
        const val MAX_NAME_LEN = 20

        fun fromJson(o: JSONObject): HushProfile? = runCatching {
            HushProfile(
                id = o.getString("id"),
                name = o.getString("name"),
                avatarId = o.getString("avatar"),
                isKids = o.optBoolean("kids", false),
                createdAt = o.optLong("c", 0L),
                updatedAt = o.optLong("u", 0L),
            )
        }.getOrNull()
    }
}

object ProfileStore {
    private const val TAG = "ProfileStore"
    private const val PREFS = "hush_profiles_v1"
    private const val KEY_LIST = "list"           // JSONArray of HushProfile
    private const val KEY_ACTIVE = "active"        // profileId currently selected

    /** Snapshot accessor — returns the full profile list for the
     *  given Xtream username. Empty list means a fresh install
     *  that hasn't run the migration setup yet. */
    fun list(ctx: Context, xtreamUsername: String): List<HushProfile> {
        val raw = prefs(ctx).getString(scopedKey(xtreamUsername, KEY_LIST), null)
            ?: return emptyList()
        return parse(raw)
    }

    /** Returns the profile the user is currently using (sticky for
     *  this app session). `null` if no profile has been selected
     *  yet on this device — the caller (ProfileGate) MUST show the
     *  picker / setup screen before letting any content render. */
    fun active(ctx: Context, xtreamUsername: String): HushProfile? {
        val activeId = prefs(ctx).getString(scopedKey(xtreamUsername, KEY_ACTIVE), null)
            ?: return null
        return list(ctx, xtreamUsername).firstOrNull { it.id == activeId }
    }

    /** Set the active profile. Persists across cold starts so the
     *  picker only re-prompts when the user explicitly switches. */
    fun setActive(ctx: Context, xtreamUsername: String, profileId: String) {
        prefs(ctx).edit().putString(scopedKey(xtreamUsername, KEY_ACTIVE), profileId).apply()
    }

    /** Clear the active profile so the next launch re-shows the
     *  picker. Used by "Switch Profile" in Settings. */
    fun clearActive(ctx: Context, xtreamUsername: String) {
        prefs(ctx).edit().remove(scopedKey(xtreamUsername, KEY_ACTIVE)).apply()
    }

    /**
     * Add a new profile. Returns the created HushProfile on success,
     * or `null` if the account has already hit MAX_PROFILES (the
     * UI must check `list().size < MAX_PROFILES` before showing
     * the "Add Profile" tile).
     */
    fun add(
        ctx: Context,
        xtreamUsername: String,
        name: String,
        avatarId: String,
        isKids: Boolean,
    ): HushProfile? {
        val current = list(ctx, xtreamUsername).toMutableList()
        if (current.size >= HushProfile.MAX_PROFILES) {
            Log.w(TAG, "add() rejected — already at MAX_PROFILES=${HushProfile.MAX_PROFILES}")
            return null
        }
        val now = System.currentTimeMillis()
        val profile = HushProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(HushProfile.MAX_NAME_LEN),
            avatarId = avatarId,
            isKids = isKids,
            createdAt = now,
            updatedAt = now,
        )
        current += profile
        persist(ctx, xtreamUsername, current)
        return profile
    }

    /**
     * Update an existing profile. Returns true on success, false
     * when [profileId] doesn't exist. Mutates `name`, `avatarId`,
     * and `isKids` — `createdAt` is immutable.
     */
    fun update(
        ctx: Context,
        xtreamUsername: String,
        profileId: String,
        name: String,
        avatarId: String,
        isKids: Boolean,
    ): Boolean {
        val current = list(ctx, xtreamUsername).toMutableList()
        val idx = current.indexOfFirst { it.id == profileId }
        if (idx < 0) return false
        current[idx] = current[idx].copy(
            name = name.trim().take(HushProfile.MAX_NAME_LEN),
            avatarId = avatarId,
            isKids = isKids,
            updatedAt = System.currentTimeMillis(),
        )
        persist(ctx, xtreamUsername, current)
        return true
    }

    /** Delete a profile. Returns true on success. The caller is
     *  responsible for clearing any profile-scoped SharedPreferences
     *  (Continue Watching, My List, etc.) — see
     *  ProfileScopedStores.purgeProfile() for the orchestration. */
    fun delete(ctx: Context, xtreamUsername: String, profileId: String): Boolean {
        val current = list(ctx, xtreamUsername).toMutableList()
        val removed = current.removeAll { it.id == profileId }
        if (!removed) return false
        persist(ctx, xtreamUsername, current)
        // If we just deleted the active profile, clear active so the
        // next launch shows the picker.
        if (prefs(ctx).getString(scopedKey(xtreamUsername, KEY_ACTIVE), null) == profileId) {
            clearActive(ctx, xtreamUsername)
        }
        return true
    }

    // ── Internals ───────────────────────────────────────────────

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Scope the storage keys by Xtream username so households
     *  with multiple Xtream accounts on the same device don't
     *  collide. */
    private fun scopedKey(xtreamUsername: String, suffix: String): String =
        "u:${xtreamUsername.lowercase().trim()}:$suffix"

    private fun persist(ctx: Context, xtreamUsername: String, profiles: List<HushProfile>) {
        val arr = JSONArray()
        for (p in profiles) arr.put(p.toJson())
        prefs(ctx).edit().putString(scopedKey(xtreamUsername, KEY_LIST), arr.toString()).apply()
    }

    private fun parse(raw: String): List<HushProfile> {
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                HushProfile.fromJson(arr.getJSONObject(i))
            }
        }.getOrElse { e ->
            Log.w(TAG, "parse failed: ${e.message}")
            emptyList()
        }
    }
}
