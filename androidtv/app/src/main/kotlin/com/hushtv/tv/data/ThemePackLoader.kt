package com.hushtv.tv.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection

/**
 * Loads the bundled (and optional remote) Themes & Moods pack.
 *
 * Why this exists
 * ───────────────
 * The original 25 themes live as hardcoded Kotlin in [HushThemedLists].
 * That's fine for the bootstrap set, but adding new themes used to
 * require a Kotlin edit + APK build. This loader unlocks the next
 * stage: themes can ship as a JSON pack — bundled in the APK as
 * `assets/themes_pack.json` and/or hot-patched from
 * `https://hushtv.xyz/themes_pack.json` (same pattern as
 * [BundleOverrides]).
 *
 * Format (slim shape — see [parseSlim]):
 * ```json
 * {
 *   "version": 2,
 *   "themes": [{ "slug": "...", "name": "...", "target": 250 }, ...],
 *   "items":  [ ["theme_slug", rank, "title", year], ... ]
 * }
 * ```
 *
 * Merge policy
 * ────────────
 * Each pack theme is keyed by `theme_slug`. If a [SLUG_TO_LEGACY_ID]
 * mapping exists, the pack entry is treated as a DUPLICATE of an
 * existing hardcoded theme and is dropped (the hardcoded curation
 * wins — it has richer altTitles and metadata). Pack themes whose
 * slug has no legacy mapping become "extra" themes appended to the
 * Moods & Themes catalog with default metadata derived from a
 * stable hash of the slug.
 *
 * This means adding a brand-new themed row tomorrow is:
 *   1) append rows to `assets/themes_pack.json` (or post a new file
 *      at `https://hushtv.xyz/themes_pack.json`)
 *   2) optionally register a slug → metadata override in
 *      [PackThemeMetadata.HERO_OVERRIDES] for a custom hero.
 *
 * No Kotlin curation file change required.
 */
object ThemePackLoader {

    private const val ASSET_NAME = "themes_pack.json"
    private const val REMOTE_URL = "https://hushtv.xyz/themes_pack.json"
    private const val PREFS = "hushtv_theme_pack"
    private const val KEY_BLOB = "blob_v2"
    private const val KEY_FETCHED_AT = "fetched_at"
    /** Refresh at most once every 12 h. */
    private const val REFRESH_MS = 12L * 60L * 60L * 1000L

    /**
     * Slugs in the v2 pack that map onto already-curated themes in
     * [HushThemedLists]. The hardcoded curation wins so we don't
     * regress on hand-picked altTitles, year disambiguation, or
     * hero backdrops.
     */
    private val SLUG_TO_LEGACY_ID: Map<String, String> = mapOf(
        "based_on_true_stories"          to "true_stories_v1",
        "plot_twist_endings"             to "plot_twists_v1",
        "mind_bending_movies"            to "mind_bending_v1",
        "underrated_hidden_gems"         to "hidden_gems_v1",
        "movies_that_make_you_cry"       to "make_you_cry_v1",
        "feel_good_movies"               to "feel_good_v1",
        "coming_of_age"                  to "coming_of_age_v1",
        "revenge_movies"                 to "revenge_v1",
        "survival_movies"                to "survival_v1",
        "movies_that_mess_with_time"     to "time_bending_v1",
        "action_comedy"                  to "action_comedy_v1",
        "dark_comedy"                    to "dark_comedy_v1",
        "psychological_thrillers"        to "psychological_thriller_v1",
        "fantasy_epics"                  to "epic_fantasy_v1",
        "crime_masterpieces"             to "crime_masterpieces_v1",
        "late_night_movies"              to "late_night_v1",
        "watch_with_friends"             to "watch_with_friends_v1",
        "turn_your_brain_off"            to "brain_off_v1",
        "visually_stunning"              to "visually_stunning_v1",
        "soundtrack_driven"              to "soundtrack_driven_v1",
        "didnt_understand_first_time"    to "rewatch_decoder_v1",
        "better_than_the_book"           to "better_than_book_v1",
        "one_location_movies"            to "one_location_v1",
        "minimal_dialogue_movies"        to "minimal_dialogue_v1",
        "wtf_did_i_just_watch"           to "wtf_v1",
        "top_disney_movies_of_all_time"  to "disney_classics_v1",
    )

    private data class PackTheme(
        val slug: String,
        val name: String,
    )

    private data class Pack(
        val version: Int,
        val themes: List<PackTheme>,
        val items: Map<String, List<ThemedMovieRef>>, // slug -> ordered refs
    )

    @Volatile private var cached: Pack? = null
    @Volatile private var derived: List<ThemedList>? = null

    /**
     * Returns the list of "extra" themes from the pack that have NO
     * legacy hardcoded equivalent. The result is computed lazily on
     * first call and cached for the JVM lifetime — pack contents
     * don't change at runtime without a refresh + restart.
     *
     * Safe to call from any thread. Returns empty list before the
     * pack has been loaded (boot calls [primeAsync] early).
     */
    fun extraThemes(): List<ThemedList> = derived.orEmpty()

    /**
     * Load the bundled pack synchronously (cheap — ~75 KB JSON parse,
     * runs in ~20 ms on a Fire Stick). Called on boot from
     * [com.hushtv.tv.HushTVApp.onCreate].
     */
    fun loadBundledSync(ctx: Context) {
        if (cached != null) return
        val pack = runCatching {
            ctx.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()?.let(::parseSlim)
        if (pack != null) {
            cached = pack
            derived = derive(pack)
        }
        // Also try any previously-cached remote blob from prefs so
        // the user keeps the newest pack across cold starts even if
        // the network is unreachable on this launch.
        runCatching {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = sp.getString(KEY_BLOB, null) ?: return@runCatching
            val remote = parseSlim(raw) ?: return@runCatching
            if (remote.version >= (cached?.version ?: 0)) {
                cached = remote
                derived = derive(remote)
            }
        }
    }

    /**
     * Background refresh of the remote pack. Idempotent + throttled
     * to [REFRESH_MS].
     *
     * On a successful refresh that returns a non-empty [derived],
     * this also primes [ThemedMatchCache] entries for the
     * pack-extra themes so they paint with real movies on the
     * FIRST catalog open (instead of a "Curating…" empty state
     * that would persist until the next cold launch).
     */
    suspend fun refreshRemote(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastFetched = sp.getLong(KEY_FETCHED_AT, 0L)
        if (now - lastFetched < REFRESH_MS / 2) return@withContext false

        val text = httpGet(REMOTE_URL) ?: return@withContext false
        val remote = parseSlim(text) ?: return@withContext false
        if (remote.version < (cached?.version ?: 0)) return@withContext false

        cached = remote
        val newDerived = derive(remote)
        derived = newDerived
        sp.edit()
            .putString(KEY_BLOB, text)
            .putLong(KEY_FETCHED_AT, now)
            .apply()

        // Prime ThemedMatchCache for the new pack-extra themes so
        // the FIRST catalog open paints with real movies instead of
        // a "Curating…" placeholder. Safe if LibraryIndex hasn't
        // been primed yet — `matchAgainstLibrary` will just return
        // empty matches and the catalog screen will defensively
        // re-prime on entry.
        if (newDerived.isNotEmpty()) {
            for (theme in newDerived) {
                val matches = runCatching {
                    HushThemedLists.matchAgainstLibrary(theme)
                }.getOrDefault(emptyList())
                withContext(Dispatchers.Main) {
                    ThemedMatchCache.snapshot[theme.id] = matches
                }
            }
        }
        true
    }

    // ── Internals ───────────────────────────────────────────────────

    private fun derive(pack: Pack): List<ThemedList> {
        // Drop entries whose slug shadows an existing hardcoded id.
        val extras = pack.themes.filter { it.slug !in SLUG_TO_LEGACY_ID }
        if (extras.isEmpty()) return emptyList()
        return extras.map { t ->
            val refs = pack.items[t.slug].orEmpty()
            val meta = PackThemeMetadata.metaFor(t.slug, t.name)
            ThemedList(
                id = "pack_${t.slug}",
                title = t.name,
                subtitle = meta.subtitle,
                section = meta.section,
                accent = meta.accent,
                glyph = meta.glyph,
                movies = refs,
                heroBackdropUrl = meta.heroBackdropUrl,
            )
        }
    }

    private fun parseSlim(text: String): Pack? = runCatching {
        val obj = JSONObject(text)
        val version = obj.optInt("version", 1)
        val themesArr = obj.optJSONArray("themes") ?: return@runCatching null
        val themes = ArrayList<PackTheme>(themesArr.length())
        for (i in 0 until themesArr.length()) {
            val t = themesArr.optJSONObject(i) ?: continue
            val slug = t.optString("slug").trim()
            val name = t.optString("name").trim()
            if (slug.isBlank() || name.isBlank()) continue
            themes += PackTheme(slug = slug, name = name)
        }

        val itemsArr = obj.optJSONArray("items") ?: return@runCatching null
        val itemsBySlug = HashMap<String, ArrayList<Pair<Int, ThemedMovieRef>>>()
        for (i in 0 until itemsArr.length()) {
            val row = itemsArr.optJSONArray(i) ?: continue
            // [slug, rank, title, year]
            val slug = row.optString(0).trim()
            val rank = row.optInt(1, 999_999)
            val title = row.optString(2).trim()
            val year = row.optInt(3, 0).takeIf { it > 0 }
            if (slug.isBlank() || title.isBlank()) continue
            itemsBySlug.getOrPut(slug) { ArrayList() }
                .add(rank to ThemedMovieRef(title = title, year = year))
        }
        // Sort each theme's items by rank (asc) and drop the rank key.
        val items: Map<String, List<ThemedMovieRef>> = itemsBySlug.mapValues { (_, list) ->
            list.sortedBy { it.first }.map { it.second }
        }
        Pack(version = version, themes = themes, items = items)
    }.getOrNull()

    private fun httpGet(url: String): String? = runCatching {
        val conn = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        if (conn.responseCode !in 200..299) return@runCatching null
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}

/**
 * Per-slug presentation metadata for themes that arrive via the
 * pack (i.e., themes WITHOUT a hardcoded entry in [HushThemedLists]).
 *
 * The pack only carries (slug, name, target_count) + (title, year)
 * tuples — no accent colors, glyphs, sections, or hero backdrops.
 * This object provides those without requiring a network call so
 * "extra" theme tiles paint on the first frame.
 */
private object PackThemeMetadata {

    data class Meta(
        val accent: Color,
        val glyph: String,
        val subtitle: String,
        val section: ThemedList.Section,
        val heroBackdropUrl: String,
    )

    /**
     * Optional per-slug overrides for hand-curated hero artwork.
     * Add an entry here when you ship a hot-patched theme and want
     * its catalog tile to use a specific TMDB backdrop instead of
     * the slug-hash fallback.
     */
    val HERO_OVERRIDES: Map<String, String> = emptyMap()

    /** Stable, deterministic palette of accents for fallback. */
    private val PALETTE: List<Color> = listOf(
        Color(0xFFF59E0B), Color(0xFF06B6D4), Color(0xFF8B5CF6),
        Color(0xFF10B981), Color(0xFFF43F5E), Color(0xFF3B82F6),
        Color(0xFFEAB308), Color(0xFFDC2626), Color(0xFF14B8A6),
        Color(0xFF6366F1), Color(0xFFFB7185), Color(0xFFA3E635),
    )

    private val SECTIONS = ThemedList.Section.entries.toTypedArray()

    /**
     * Generic fallback hero backdrop — a wide cinematic still. Used
     * when no [HERO_OVERRIDES] entry exists for a slug. Same TMDB
     * `original`-size pattern as [HushThemedLists.HERO_BACKDROPS].
     */
    private const val FALLBACK_HERO =
        "https://image.tmdb.org/t/p/original/9BBTo63ANSmhC4e6r62OJFuK2GL.jpg" // Avengers

    fun metaFor(slug: String, name: String): Meta {
        val hash = slug.hashCode().toUInt().toInt() and 0x7FFFFFFF
        val accent = PALETTE[hash % PALETTE.size]
        val section = SECTIONS[hash % SECTIONS.size]
        // Two-glyph palette — keeps the catalog visually consistent
        // with the legacy themes which all use a small set of marks.
        val glyph = listOf("\u2605", "\u25C6", "\u25CF", "\u25B2")[hash % 4]
        val hero = HERO_OVERRIDES[slug] ?: FALLBACK_HERO
        // Subtitle is a soft one-liner derived from the name. We
        // deliberately keep it short so the catalog hero panel
        // stays clean.
        return Meta(
            accent = accent,
            glyph = glyph,
            subtitle = shortSubtitle(name),
            section = section,
            heroBackdropUrl = hero,
        )
    }

    private fun shortSubtitle(name: String): String {
        // Lowercase the name for a soft, eyebrow-style subtitle.
        return name.lowercase()
    }
}
