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
 *
 * v1.44.2 PERF FIX:
 * The original implementation re-normalized every live channel on
 * every match() call. With ~5,000 channels in a typical Xtream
 * playlist and ~160 games to match, that produced ~1.6M regex
 * operations on whatever thread called filterPlayableGames(). Inside
 * a Compose `remember{}` block this happens on the MAIN thread,
 * causing 5-15 s ANR hangs and an OS-killed process (which bypasses
 * the JVM uncaught-exception handler — that's why no crash report
 * ever made it to the server).
 *
 * The fix is [ChannelIndex]: pre-normalize every channel once, then
 * match against the cached form. The caller does this transformation
 * on Dispatchers.Default and feeds the resulting index into the
 * filter functions (which then run cheap O(games × index.size)
 * comparisons instead of nested regex.replaceAll calls).
 */
object SportsChannelMatcher {

    /**
     * Pre-normalized snapshot of the user's live-channel list. Build
     * once per playlist load; reuse for every match.
     */
    class ChannelIndex(channels: List<MediaCard>) {
        // (channel, normalized-tokens) — built once.
        val rows: List<Triple<MediaCard, String, List<String>>> =
            channels.map { c ->
                val normalized = normalize(c.title)
                val tokens = normalized.split(" ").filter { it.isNotBlank() }
                Triple(c, normalized, tokens)
            }
        val size: Int get() = rows.size
        val isEmpty: Boolean get() = rows.isEmpty()
    }

    /** Returns the best-match live channel from [index], or
     *  null if nothing meets the bar. */
    fun match(canonicalName: String, index: ChannelIndex): MediaCard? {
        if (canonicalName.isBlank() || index.isEmpty) return null
        val canonical = normalize(canonicalName)
        val canonicalTokens = canonical.split(" ").filter { it.isNotBlank() }
        if (canonicalTokens.isEmpty()) return null
        val canonicalDigitsRewritten = canonicalTokens.map(::digitToWord).joinToString(" ")

        // 1. Exact match on the normalized form.
        index.rows.firstOrNull { it.second == canonical }?.let { return it.first }

        // Also check the digit-rewritten form (so "SPORTSNET 1" finds
        // canonical "SPORTSNET ONE" and vice versa).
        index.rows.firstOrNull { it.second == canonicalDigitsRewritten }?.let { return it.first }

        // 2. Token superset: playlist must contain every canonical
        //    token. Score by extra tokens (fewer = better).
        var best: Triple<MediaCard, String, List<String>>? = null
        var bestScore = Int.MAX_VALUE
        for (row in index.rows) {
            val pTokens = row.third
            if (canonicalTokens.all { ct -> ct in pTokens }) {
                val score = pTokens.size
                if (score < bestScore) {
                    best = row
                    bestScore = score
                }
            }
        }
        if (best != null) return best.first

        // 3. Same again but with digit-word swaps everywhere.
        val cToksDigitForm = canonicalTokens.map(::digitToWord)
        bestScore = Int.MAX_VALUE
        for (row in index.rows) {
            val pTokensDigitForm = row.third.map(::digitToWord)
            if (cToksDigitForm.all { ct -> ct in pTokensDigitForm }) {
                val score = pTokensDigitForm.size
                if (score < bestScore) {
                    best = row
                    bestScore = score
                }
            }
        }
        return best?.first
    }

    /** Convenience wrapper — builds an index and matches a single
     *  name. Avoid in tight loops; build [ChannelIndex] yourself
     *  and call [match] with it. */
    fun match(canonicalName: String, liveChannels: List<MediaCard>): MediaCard? =
        match(canonicalName, ChannelIndex(liveChannels))

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
