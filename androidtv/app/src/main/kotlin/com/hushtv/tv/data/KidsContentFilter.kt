package com.hushtv.tv.data

/**
 * v1.44.138-dev — Kids' profile category filter.
 *
 * Runs at the XtreamApi.getCategories() boundary so every consumer
 * surface (Home rails, Browse screens, Live Guide, Settings,
 * Boot refresh) automatically receives a sanitized list when the
 * active profile is flagged isKids.
 *
 * Match rule
 * ──────────
 * Substring + regex match (case-insensitive) against the category
 * name. We're deliberately CONSERVATIVE here — false positives on
 * a Kids profile are far less harmful than false negatives. If a
 * grown-up's news category happens to mention "XXX III" the
 * filter blocks it; the grown-up can switch profiles to see it.
 *
 * The pattern list is intentionally tight and English-centric.
 * Streams from Canadian Xtream providers near-universally use
 * English category labels, so the simpler implementation works.
 * If we extend internationally we'd swap this for a backend-side
 * adult flag.
 *
 * Canada / official: ActiveProfile.isKids() is hardwired false on
 * those flavors (multi-profile system dormant), so this filter
 * is a no-op pass-through.
 */
object KidsContentFilter {
    private val ADULT_PATTERNS = listOf(
        Regex("""\bxxx\b""", RegexOption.IGNORE_CASE),
        Regex("""\bx\s*x\s*x\b""", RegexOption.IGNORE_CASE),
        Regex("""\b18\+""", RegexOption.IGNORE_CASE),
        Regex("""\badult\b""", RegexOption.IGNORE_CASE),
        Regex("""\berotic""", RegexOption.IGNORE_CASE),
        Regex("""\bporn""", RegexOption.IGNORE_CASE),
        Regex("""\bhentai""", RegexOption.IGNORE_CASE),
        Regex("""\bnsfw""", RegexOption.IGNORE_CASE),
        Regex("""\bplayboy""", RegexOption.IGNORE_CASE),
        Regex("""\bbrazzers""", RegexOption.IGNORE_CASE),
        Regex("""\bpornhub""", RegexOption.IGNORE_CASE),
        Regex("""\breality\s*king""", RegexOption.IGNORE_CASE),
        Regex("""\bnaughty\s*america""", RegexOption.IGNORE_CASE),
        Regex("""\bplayground\b""", RegexOption.IGNORE_CASE),  // catches "Jack's Playground"
    )

    /** True when the category name matches any adult pattern. */
    fun isAdult(categoryName: String): Boolean {
        return ADULT_PATTERNS.any { it.containsMatchIn(categoryName) }
    }

    /** Filter for a Kids profile — strips adult-flagged categories.
     *  No-op when the active profile isn't Kids. */
    fun filterCategories(input: List<XtreamCategory>): List<XtreamCategory> {
        if (!ActiveProfile.isKids()) return input
        return input.filterNot { isAdult(it.category_name) }
    }

    /** Sibling filter for stream lists already loaded into MediaCard
     *  shape. Catches anything an adult-named title might have slipped
     *  through (e.g. category was clean but individual streams were
     *  tagged with adult-pattern keywords). */
    fun filterCards(input: List<MediaCard>): List<MediaCard> {
        if (!ActiveProfile.isKids()) return input
        return input.filterNot { isAdult(it.title) }
    }
}
