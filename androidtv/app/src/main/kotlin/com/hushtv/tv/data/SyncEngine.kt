package com.hushtv.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Cross-device sync engine.
 *
 * Identity (per ask_human v1.43.82, choice 1a)
 * ────────────────────────────────────────────
 * Auto-pair via the user's primary Xtream playlist:
 * `userId = sha256(host|username)[:16]`. Same Xtream creds on two
 * devices = same sync ID — no signup, no pairing code.
 *
 * Strategy
 * ────────
 * - **Per-store generic LWW** over a `Map<String, String>` wire
 *   format. Every store named in [SYNC_STORES] is treated as an
 *   opaque dict of its SharedPreferences entries.
 * - **Continue Watching gets per-record merge** on the server side
 *   (the server matches `lastWatchedAt` per entry so two devices
 *   watching different titles concurrently never clobber each
 *   other).
 *
 * Type-preserving wire format (v1.43.84, fix for the LayoutPrefs
 * regression where the first-run modal kept popping up)
 * ─────────────────────────────────────────────────────────────────
 * SharedPreferences supports `String`, `Int`, `Long`, `Float`,
 * `Boolean`, and `Set<String>`. The previous engine only
 * synced String entries and `clear()`-ed the prefs file on every
 * download — which **wiped** every non-String entry, e.g.
 * `LayoutPrefsStore.KEY_FIRST_RUN_SHOWN` (boolean), causing the
 * "How should we show categories?" first-run modal to re-appear
 * every launch. This release fixes both halves of that bug:
 *
 * 1. **Upload encodes all types** with a single-character prefix
 *    so the server's `Dict[str, str]` schema is unchanged:
 *    ```
 *    "s:hello"        ← String "hello"
 *    "i:42"           ← Int 42
 *    "l:1234567890"   ← Long
 *    "f:3.14"         ← Float
 *    "b:1" / "b:0"    ← Boolean true / false
 *    "S:a\u001fb"     ← Set<String> {a, b}, fields ASCII-US
 *    ```
 *    Untagged values (legacy data already on the server) decode as
 *    plain Strings — backwards compatible.
 *
 * 2. **Download applies selectively**: instead of `clear()` then
 *    re-write, we only `put*()` the keys present in the incoming
 *    blob. Keys NOT in the blob (because the uploading device is
 *    on an older client that didn't include non-strings) are left
 *    untouched. This protects the local boolean flags, ints, etc.
 *    even if an old client uploads a string-only blob.
 *
 * Lifecycle
 * ─────────
 * Started from [com.hushtv.tv.MainActivity] inside `lifecycleScope`.
 * Initial sync 3 s after onCreate, then every 30 s.
 *
 * Privacy / Security
 * ──────────────────
 * - HTTPS only.
 * - [PinStore] and [PlaylistStore] are explicitly excluded — the
 *   former is a security secret, the latter contains the Xtream
 *   password and IS the identity.
 * - Hash + ts metadata stays in `hushtv_sync_meta` so we don't
 *   re-upload unchanged data or re-download what we just pushed.
 */
object SyncEngine {

    /** Stores replicated. New stores added later just need to drop
     *  their filename here — the engine handles them generically. */
    private val SYNC_STORES = listOf(
        "hushtv_watch_progress",          // Continue Watching (per-record merge)
        "hushtv_favorites",                // Per-playlist live channel favs
        "hushtv_my_list",                  // Per-playlist movies/series
        "recent_channels",                 // Per-playlist recent channels
        "hushtv_live_session",             // Last channel/category per playlist
        "hushtv_last_channel",             // Last channel
        "hushtv_layout_prefs",             // Card sizes / poster modes / first-run flag
        "hushtv_reminders",                // EPG reminders
        "hushtv_request_hidden",           // Hidden requests
        "hushtv_request_seen",             // Seen request signatures
        "hushtv_request_meta",             // Request metadata
        "hushtv_request_notifications",    // Request notifications
        "hushtv_user_contact",             // User contact form values
        "hushtv_auto_resume",              // Auto-resume opt-in
    )

    private const val META_PREFS = "hushtv_sync_meta"
    private const val SYNC_URL = "https://hushtv.xyz/api/sync/state"

    private const val CW_STORE = "hushtv_watch_progress"
    private const val US = '\u001f'   // ASCII unit separator

    private var job: Job? = null

    fun userId(ctx: Context): String? {
        val pl = PlaylistStore.getAll(ctx).firstOrNull() ?: return null
        if (pl.host.isBlank() || pl.username.isBlank()) return null
        val raw = "${pl.host.lowercase().trim()}|${pl.username.lowercase().trim()}"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    suspend fun runOnce(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = userId(ctx) ?: return@withContext false
        val meta = ctx.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        val uploads = JSONArray()
        val knownTs = JSONObject()

        for (store in SYNC_STORES) {
            val sp = ctx.getSharedPreferences(store, Context.MODE_PRIVATE)
            val encoded = encodeAll(sp)
            val hash = entriesHash(encoded)
            val prevHash = meta.getInt("hash:$store", 0)
            knownTs.put(store, meta.getLong("ts:$store", 0L))
            if (hash != prevHash) {
                val ts = System.currentTimeMillis()
                val blob = JSONObject()
                for ((k, v) in encoded) blob.put(k, v)
                uploads.put(JSONObject().apply {
                    put("store", store)
                    put("ts", ts)
                    put("blob", blob)
                })
            }
        }

        val body = JSONObject().apply {
            put("user_id", uid)
            put("uploads", uploads)
            put("known_ts", knownTs)
        }.toString()

        val resp = post(SYNC_URL, body) ?: return@withContext false
        val downloads = resp.optJSONArray("downloads") ?: JSONArray()

        for (i in 0 until downloads.length()) {
            val dl = downloads.getJSONObject(i)
            val store = dl.optString("store")
            if (store.isBlank() || store !in SYNC_STORES) continue
            val ts = dl.optLong("ts", 0L)
            val blob = dl.optJSONObject("blob") ?: continue
            applyDownload(ctx, store, ts, blob)
        }

        for (i in 0 until uploads.length()) {
            val up = uploads.getJSONObject(i)
            val store = up.getString("store")
            val sp = ctx.getSharedPreferences(store, Context.MODE_PRIVATE)
            val newHash = entriesHash(encodeAll(sp))
            meta.edit()
                .putInt("hash:$store", newHash)
                .putLong("ts:$store", up.getLong("ts"))
                .commit()
        }
        true
    }

    fun start(ctx: Context, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            delay(3_000)
            while (isActive) {
                runCatching { runOnce(ctx) }
                delay(30_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ── Type-preserving encode / decode ─────────────────────────

    /** Encode every entry of [sp] into a wire-safe `String` value
     *  with a one-char type prefix (see class doc for the table).
     *  Stable iteration order so equal contents always hash equal.
     */
    @Suppress("UNCHECKED_CAST")
    private fun encodeAll(sp: SharedPreferences): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in sp.all.toSortedMap()) {
            val enc = when (v) {
                is String -> "s:$v"
                is Boolean -> if (v) "b:1" else "b:0"
                is Int -> "i:$v"
                is Long -> "l:$v"
                is Float -> "f:$v"
                is Set<*> -> {
                    // Sort for stable hashing.
                    val items = (v as Set<String?>)
                        .filterNotNull()
                        .sorted()
                    "S:" + items.joinToString(US.toString())
                }
                null -> continue
                else -> continue   // unknown type — skip silently
            }
            out[k] = enc
        }
        return out
    }

    /** Decode a single wire value and write it to [editor] under
     *  [key] using the right `put*()` overload. Untagged values
     *  (no `prefix:` colon, or unknown prefix) are stored as plain
     *  Strings — keeps us backwards-compatible with the v1.43.82
     *  wire format that only had Strings.
     */
    private fun applyEncoded(
        editor: SharedPreferences.Editor,
        key: String,
        encoded: String,
    ) {
        if (encoded.length < 2 || encoded[1] != ':') {
            // Legacy plain-string entry from v1.43.82.
            editor.putString(key, encoded)
            return
        }
        val tag = encoded[0]
        val rest = encoded.substring(2)
        when (tag) {
            's' -> editor.putString(key, rest)
            'b' -> editor.putBoolean(key, rest == "1" || rest.equals("true", ignoreCase = true))
            'i' -> rest.toIntOrNull()?.let { editor.putInt(key, it) }
                ?: editor.putString(key, rest)
            'l' -> rest.toLongOrNull()?.let { editor.putLong(key, it) }
                ?: editor.putString(key, rest)
            'f' -> rest.toFloatOrNull()?.let { editor.putFloat(key, it) }
                ?: editor.putString(key, rest)
            'S' -> {
                val items = if (rest.isEmpty()) emptySet()
                else rest.split(US).toSet()
                editor.putStringSet(key, items)
            }
            else -> editor.putString(key, encoded)   // unknown tag — keep as string
        }
    }

    private fun entriesHash(entries: Map<String, String>): Int {
        val sb = StringBuilder()
        entries.forEach { (k, v) ->
            sb.append(k).append('=').append(v).append('|')
        }
        return sb.toString().hashCode()
    }

    /**
     * Apply a downloaded blob to the local prefs file. **Selective
     * update** — we only `put*` the keys present in the blob; we
     * never `clear()`, so non-synced or non-downloaded keys (e.g.
     * keys an older device didn't include) are preserved. This is
     * what fixes the LayoutPrefs first-run modal regression.
     *
     * Continue Watching gets its own per-record merge by the
     * embedded `lastWatchedAt` timestamp so two devices' positions
     * don't clobber each other.
     */
    private fun applyDownload(
        ctx: Context,
        store: String,
        ts: Long,
        blob: JSONObject,
    ) {
        val sp = ctx.getSharedPreferences(store, Context.MODE_PRIVATE)
        val ed = sp.edit()
        if (store == CW_STORE) {
            val keys = blob.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val incomingEnc = blob.optString(k, "")
                val incoming = stripStringTag(incomingEnc)
                val incomingTs = parseCwTs(incoming)
                val existing = sp.getString(k, null) ?: ""
                val existingTs = parseCwTs(existing)
                if (incomingTs > existingTs) {
                    ed.putString(k, incoming)
                }
            }
        } else {
            val keys = blob.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                applyEncoded(ed, k, blob.optString(k, ""))
            }
        }
        ed.commit()
        ctx.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).edit()
            .putLong("ts:$store", ts)
            .putInt("hash:$store", entriesHash(encodeAll(sp)))
            .commit()
    }

    /** CW entries are stored as raw `streamId\u001fkind\u001f…`
     *  strings. Strip our `s:` tag if a newer client sent it
     *  prefixed; older blobs are untagged and pass through. */
    private fun stripStringTag(encoded: String): String =
        if (encoded.length >= 2 && encoded[1] == ':' && encoded[0] == 's')
            encoded.substring(2)
        else
            encoded

    private fun parseCwTs(s: String): Long {
        if (s.isEmpty()) return 0
        val parts = s.split(US)
        if (parts.size < 7) return 0
        return parts[6].toLongOrNull() ?: 0
    }

    private fun post(url: String, body: String): JSONObject? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 12_000
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.errorStream?.close()
            null
        } else {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        }
    } catch (_: Exception) {
        null
    }
}
