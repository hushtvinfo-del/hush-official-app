package com.hushtv.tv.data

import kotlin.math.abs

/**
 * Shared title-matching logic for ANY TMDB → Xtream-library lookup
 * (collections, cast filmography, recommendations, future features).
 *
 * Xtream libraries label films in maddeningly inconsistent ways:
 *
 *   "[EN] VIP | Star Wars: Return of the Jedi (1983)"
 *   "US - THE EMPIRE STRIKES BACK 4K"
 *   "Return of the Jedi"
 *   "Star Wars - Episode VI Return of the Jedi"
 *
 * The old strategy — naive `contains` on normalized strings — is UNSAFE
 * because short junk library titles like "Ed" or "Star" or even
 * completely unrelated films ("Plastic Galaxy") can match into longer
 * TMDB titles via coincidental substring hits ("Jedi" contains "edi",
 * "Star Wars" contains "Star", etc.).
 *
 * This matcher enforces:
 *   1. EXACT normalized word match, OR
 *   2. CONTAINMENT where the SHORTER side has ≥ 3 real words (so "Ed"
 *      and "Star" never match into anything), AND release years agree
 *      within ±1 year (or the shorter side has ≥ 5 words for rock-solid
 *      containment). Years are extracted from either title if present.
 */
object TitleMatcher {

    private val BRACKETED = Regex("""\[[^]]*]""")
    private val LEADING_COUNTRY = Regex("""^\s*[a-z]{2,3}\s*[|:\-]\s*""")
    private val TRAILING_PIPE = Regex("""\|[^|]*$""")
    private val YEAR_PAREN = Regex("""\(\s*(19|20)\d{2}\s*\)""")
    private val YEAR_RAW = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val QUALITY = Regex(
        """\b(4k|uhd|fhd|hd|hdr|dv|sdr|bluray|blu-ray|remux|x264|x265|hevc|bdrip|web-dl|webrip|ac3|dts|aac|dolby|atmos)\b"""
    )
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Produce a stable matching key from a messy title. KEEPS articles
     * ("the", "a", "an", "of") so short titles like "Return of the Jedi"
     * stay at 4 meaningful words instead of collapsing to "return jedi"
     * (which would then dangerously match on 2-word threshold).
     */
    fun normalize(raw: String): String {
        var s = raw.lowercase()
        s = s.replace(BRACKETED, " ")
        s = s.replace(LEADING_COUNTRY, "")
        s = s.replace(TRAILING_PIPE, "")
        s = s.replace(YEAR_PAREN, " ")
        s = s.replace(QUALITY, " ")
        s = s.replace(NON_ALNUM, " ")
        s = s.replace(WHITESPACE, " ").trim()
        return s
    }

    /** Pull the first 19xx / 20xx year from a raw title. Null if none. */
    fun extractYear(raw: String): Int? =
        YEAR_RAW.find(raw)?.value?.toIntOrNull()

    /**
     * Strict matcher — only accepts when we're confident the two titles
     * refer to the same film.
     *
     * @param tmdbTitle the clean TMDB title (e.g. "Star Wars: Episode VI - Return of the Jedi")
     * @param tmdbYear  the TMDB release year (may be null)
     * @param libTitle  the raw Xtream library title (may have brackets, years, quality tags)
     * @param libYear   optional library year override. Falls back to scraping from libTitle.
     */
    fun isStrongMatch(
        tmdbTitle: String,
        tmdbYear: Int?,
        libTitle: String,
        libYear: Int? = null,
    ): Boolean {
        val tn = normalize(tmdbTitle)
        val ln = normalize(libTitle)
        return isStrongMatchNormalized(
            tn = tn,
            tnYear = tmdbYear ?: extractYear(tmdbTitle),
            ln = ln,
            lnYear = libYear ?: extractYear(libTitle),
        )
    }

    /**
     * Hot-path overload — caller passes already-normalised strings on
     * BOTH sides plus their already-extracted years. Avoids redoing
     * the 6-regex normalize() and the year-extract on every entry of
     * a library scan; with a 5 000-entry library and 90 lookups
     * that's ~3M regex evaluations saved per scan.
     */
    fun isStrongMatchNormalized(
        tn: String,
        tnYear: Int?,
        ln: String,
        lnYear: Int?,
    ): Boolean {
        if (tn.isBlank() || ln.isBlank()) return false

        if (tn == ln) {
            return if (tnYear != null && lnYear != null) {
                abs(tnYear - lnYear) <= 1
            } else true
        }

        val tWords = tn.split(" ").filter { it.isNotBlank() }
        val lWords = ln.split(" ").filter { it.isNotBlank() }
        if (tWords.size < 3 || lWords.size < 3) return false

        val (shorter, longer) =
            if (tWords.size <= lWords.size) tWords to lWords
            else lWords to tWords

        val shorterPhrase = shorter.joinToString(" ")
        val longerPhrase = longer.joinToString(" ")
        if (!longerPhrase.contains(shorterPhrase)) return false

        if (tnYear != null && lnYear != null) {
            if (abs(tnYear - lnYear) > 1) return false
        }
        return true
    }

    /**
     * Find the BEST library card for a given TMDB title. Returns null
     * when no card passes the strong-match bar. `libraryIndex` is a
     * pre-built list of (normalizedKey, rawLibTitle, libYear, card)
     * tuples that callers can cache across many lookups.
     */
    fun <T> findBestMatch(
        tmdbTitle: String,
        tmdbYear: Int?,
        libraryIndex: List<LibraryEntry<T>>,
    ): T? {
        // Pre-normalise the TMDB side ONCE — the loop below scans the
        // entire library, so paying the regex cost per entry would be
        // catastrophic on the themed-list catalog (90 titles × 5k+
        // entries × 6 regexes = ~2.7M regex hits). The library side
        // is already pre-normalised at index-build time.
        val tn = normalize(tmdbTitle)
        if (tn.isBlank()) return null

        // Prefer exact normalized matches first — they're always
        // unambiguously correct.
        libraryIndex.firstOrNull { it.normalized == tn }?.let { return it.payload }

        // Strong-containment matcher fallback. Uses the pre-normalised
        // [LibraryEntry.normalized] directly via [isStrongMatchNormalized]
        // so we avoid re-running normalize() on every library entry.
        val effectiveTmdbYear = tmdbYear ?: extractYear(tmdbTitle)
        return libraryIndex.firstOrNull { entry ->
            isStrongMatchNormalized(
                tn = tn,
                tnYear = effectiveTmdbYear,
                ln = entry.normalized,
                lnYear = entry.year,
            )
        }?.payload
    }

    /**
     * Helper tuple for batch lookups — callers build this once per
     * library kind (movie / series) and reuse it across many matches.
     */
    data class LibraryEntry<T>(
        val normalized: String,
        val raw: String,
        val year: Int?,
        val payload: T,
    )

    /** Convenience builder — indexes a list of items by their title. */
    fun <T> buildIndex(
        items: List<T>,
        titleOf: (T) -> String,
    ): List<LibraryEntry<T>> = items.map { item ->
        val raw = titleOf(item)
        LibraryEntry(
            normalized = normalize(raw),
            raw = raw,
            year = extractYear(raw),
            payload = item,
        )
    }
}
