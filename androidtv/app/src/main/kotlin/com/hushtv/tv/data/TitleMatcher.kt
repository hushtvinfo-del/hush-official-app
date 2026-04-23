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
        if (tn.isBlank() || ln.isBlank()) return false

        val effectiveLibYear = libYear ?: extractYear(libTitle)
        val effectiveTmdbYear = tmdbYear ?: extractYear(tmdbTitle)

        // 1. EXACT normalized match — strongest signal. Still honour
        //    the year gate when both sides report years (prevents a
        //    2003 remake library entry from matching a 1969 TMDB part
        //    that happens to share the name).
        if (tn == ln) {
            return if (effectiveTmdbYear != null && effectiveLibYear != null) {
                abs(effectiveTmdbYear - effectiveLibYear) <= 1
            } else true
        }

        // 2. Containment check — shorter side's phrase must appear as a
        //    contiguous substring inside the longer side. Require BOTH
        //    sides to have ≥ 3 real words — this alone is strong enough
        //    to prevent junk library titles like "Ed", "Star", "Plastic
        //    Galaxy" from matching long TMDB titles via coincidental
        //    substrings, because those library titles all fail the
        //    word-count bar.
        val tWords = tn.split(" ").filter { it.isNotBlank() }
        val lWords = ln.split(" ").filter { it.isNotBlank() }
        if (tWords.size < 3 || lWords.size < 3) return false

        val (shorter, longer) =
            if (tWords.size <= lWords.size) tWords to lWords
            else lWords to tWords

        val shorterPhrase = shorter.joinToString(" ")
        val longerPhrase = longer.joinToString(" ")
        if (!longerPhrase.contains(shorterPhrase)) return false

        // 3. Year gate — when BOTH sides report a year and they disagree
        //    by more than 1, reject (defends against franchise remakes
        //    or numbered sequels sharing a phrase). When either side is
        //    unknown, trust the contiguous-phrase + word-count bar.
        if (effectiveTmdbYear != null && effectiveLibYear != null) {
            if (abs(effectiveTmdbYear - effectiveLibYear) > 1) return false
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
        // Prefer exact normalized matches first — they're always
        // unambiguously correct.
        val tn = normalize(tmdbTitle)
        libraryIndex.firstOrNull { it.normalized == tn }?.let { return it.payload }
        // Then fall back to the strong-containment matcher.
        return libraryIndex.firstOrNull { entry ->
            isStrongMatch(
                tmdbTitle = tmdbTitle,
                tmdbYear = tmdbYear,
                libTitle = entry.raw,
                libYear = entry.year,
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
