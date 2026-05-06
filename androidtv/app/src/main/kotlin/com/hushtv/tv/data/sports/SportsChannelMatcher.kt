package com.hushtv.tv.data.sports

import com.hushtv.tv.data.MediaCard

/**
 * Resolves a sports-server channel name (e.g. "SPORTSNET ONE") to
 * an actual live channel from the user's Xtream playlist.
 *
 * The sports server stores channel names in a CANONICAL form (the
 * names that appear on official broadcaster websites), but providers
 * name them in dozens of inconsistent ways:
 *   "SPORTSNET ONE"   (canonical)
 *   "SN1 HD"
 *   "CA: SPORTSNET 1 HD"
 *   "SNET-1"
 *   "Sportsnet One"
 *   "[CA] Sportsnet 1 4K"
 *
 * Matching strategy (highest confidence wins, first match returns):
 *   1. Exact normalized match.
 *   2. Token superset (every token of the canonical name appears in
 *      the playlist channel name — e.g. canonical "SPORTSNET ONE"
 *      matches "CA SPORTSNET ONE HD" because both tokens appear).
 *   3. Token superset with digit-word equivalence (canonical
 *      "SPORTSNET ONE" also matches "SPORTSNET 1" via the digit map).
 *
 * Stable: deterministic for a given (canonical, playlist) pair. We
 * never fall back to a substring match without all canonical tokens —
 * that'd cause "SPORTSNET" → match "SPORTSNET WEST" when the user
 * actually wanted the main "SPORTSNET" channel.
 */
object SportsChannelMatcher {

    /** Returns the best-match live channel from [liveChannels], or
     *  null if nothing meets the bar. */
    fun match(canonicalName: String, liveChannels: List<MediaCard>): MediaCard? {
        if (canonicalName.isBlank() || liveChannels.isEmpty()) return null
        val canonical = normalize(canonicalName)
        val canonicalTokens = canonical.split(" ").filter { it.isNotBlank() }
        if (canonicalTokens.isEmpty()) return null
        val canonicalDigitsRewritten = canonicalTokens.map(::digitToWord).joinToString(" ")

        // Pre-normalize playlist names once.
        val rows = liveChannels.map { it to normalize(it.title) }

        // 1. Exact match on the normalized form.
        rows.firstOrNull { it.second == canonical }?.let { return it.first }

        // Also check the digit-rewritten form (so "SPORTSNET 1" finds
        // canonical "SPORTSNET ONE" and vice versa).
        rows.firstOrNull { it.second == canonicalDigitsRewritten }?.let { return it.first }

        // 2. Token superset: playlist must contain every canonical
        //    token. Score by extra tokens (fewer = better).
        val supersetMatches = rows
            .filter { (_, n) ->
                val pTokens = n.split(" ")
                canonicalTokens.all { ct -> ct in pTokens }
            }
            .sortedBy { (_, n) -> n.split(" ").size }
        if (supersetMatches.isNotEmpty()) return supersetMatches.first().first

        // 3. Same again but with digit-word swaps everywhere.
        val mixedMatches = rows
            .filter { (_, n) ->
                val pTokens = n.split(" ").map(::digitToWord)
                val cToks = canonicalTokens.map(::digitToWord)
                cToks.all { ct -> ct in pTokens }
            }
            .sortedBy { (_, n) -> n.split(" ").size }
        if (mixedMatches.isNotEmpty()) return mixedMatches.first().first

        return null
    }

    /** Normalize a channel name for matching: uppercase, strip
     *  non-alnum (`HD`, `4K`, `[CA]`, `:`, dashes...) collapse runs of
     *  whitespace, drop the most common provider noise tokens. */
    private fun normalize(s: String): String {
        val upper = s.uppercase()
            .replace(Regex("\\[.*?]"), " ")     // drop bracketed prefixes
            .replace(Regex("[^A-Z0-9]+"), " ")  // collapse non-alnum to space
        return upper.split(" ")
            .filter { it.isNotBlank() && it !in noiseTokens }
            .joinToString(" ")
            .trim()
    }

    /** Common provider-side noise tokens we drop from channel names so
     *  matching focuses on the actual broadcaster identity. */
    private val noiseTokens = setOf(
        "HD", "FHD", "UHD", "SD", "4K", "8K",
        "CA", "USA", "US", "UK", "INTL",
        "HEVC", "H264", "H265",
        "BACKUP", "BAK", "ALT", "ALT2",
    )

    /** Word-form of digits 0-10 so "SPORTSNET ONE" matches "SPORTSNET 1". */
    private val digitWords = mapOf(
        "0" to "ZERO", "1" to "ONE", "2" to "TWO", "3" to "THREE",
        "4" to "FOUR", "5" to "FIVE", "6" to "SIX", "7" to "SEVEN",
        "8" to "EIGHT", "9" to "NINE", "10" to "TEN",
        "ZERO" to "0", "ONE" to "1", "TWO" to "2", "THREE" to "3",
        "FOUR" to "4", "FIVE" to "5", "SIX" to "6", "SEVEN" to "7",
        "EIGHT" to "8", "NINE" to "9", "TEN" to "10",
    )

    private fun digitToWord(token: String): String =
        digitWords[token] ?: token
}
