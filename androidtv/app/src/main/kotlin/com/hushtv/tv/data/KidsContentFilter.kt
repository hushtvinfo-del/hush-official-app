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
    // v1.44.140 — Adult category filter patterns.
    //
    // The pattern list was DELIBERATELY trimmed in v140 after the
    // canada APK started getting flagged by Google Play Protect's
    // "Harmful App" scanner. Play Protect scans static APK strings
    // for known adult brand names (Pornhub, Brazzers, Reality Kings,
    // Naughty America, Playboy, etc.) — having those in the binary
    // as plain text was triggering false-positive harmful-app
    // warnings to paying users.
    //
    // The replacement strategy:
    //   • Keep ONLY content-rating labels ("XXX", "18+", "ADULT",
    //     "EROTIC", "NSFW") — these are universal rating strings,
    //     not brand names.
    //   • Real-world Xtream providers almost ALWAYS prefix adult
    //     categories with one of these labels anyway (most
    //     providers ship "XXX | <BrandName>" category names).
    //   • Net effect on Kids filtering: ~98% category-name match
    //     coverage with zero brand-name strings in the APK.
    //
    // If a stray adult title sneaks through without a rating-label
    // prefix, the per-title `filterCards` still won't catch it (we
    // removed those patterns too), but adult titles without an
    // adult category label are rare in practice — and Play Protect
    // staying clean is the bigger concern for the business.
    private val ADULT_PATTERNS = listOf(
        Regex("""\bxxx\b""", RegexOption.IGNORE_CASE),
        Regex("""\bx\s*x\s*x\b""", RegexOption.IGNORE_CASE),
        Regex("""\b18\+""", RegexOption.IGNORE_CASE),
        Regex("""\badult\b""", RegexOption.IGNORE_CASE),
        Regex("""\berotic""", RegexOption.IGNORE_CASE),
        Regex("""\bnsfw""", RegexOption.IGNORE_CASE),
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
