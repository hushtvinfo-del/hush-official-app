package com.hushtv.tv.data.sports

/**
 * Blacklist of channel-name patterns that should be HIDDEN from the
 * per-game [com.hushtv.tv.ui.screens.sports.GameChannelSheet]
 * results list.
 *
 * Why this exists
 * ───────────────
 * Some EPGs label every Canadian over-the-air feed as "CBC ..." even
 * for games that are properly broadcast on Sportsnet / TSN / regional
 * RSNs. Users found the CBC rows noisy because they're not the feed
 * they actually want to watch (often blacked out, news bulletin
 * overlays, French-only audio, etc.). Letting them blanket-block by
 * name keeps the picker focused on the real broadcast feeds.
 *
 * Matching is case-insensitive and matches anywhere in the channel
 * name. So `"CBC"` filters out "CBC TORONTO", "CBC SPORTS",
 * "[EN] CBC HD" and so on — but does NOT filter "ABC", "NBC", or
 * "CBS" because each entry must appear as a whole standalone token
 * surrounded by word boundaries.
 *
 * To add a new pattern, just append a String to [PATTERNS] — no
 * other file change required. The patterns are kept here (not in
 * `themes_pack.json` / remote JSON) on purpose: blocking a known
 * bad feed is a code-level safety decision the operator owns.
 */
object SportsChannelBlacklist {

    /**
     * Word-boundary tokens that, if found in the channel name (case
     * insensitive), cause the channel to be dropped from the
     * per-game results list.
     */
    private val PATTERNS: List<String> = listOf(
        "CBC",
    )

    /** Pre-compiled case-insensitive regexes — one per pattern. */
    private val regexes: List<Regex> = PATTERNS.map { token ->
        // \\b is a word boundary so "CBC" doesn't match inside "CBCSPORTS"
        // BUT we also want to handle separator-less variants like
        // "CBC1", so we accept either a word-boundary OR a digit
        // boundary on either side.
        Regex(
            pattern = "(?i)(?:^|[^A-Z0-9])${Regex.escape(token)}(?:[^A-Z])",
        )
    }

    /**
     * True when [channelName] should be HIDDEN from the picker.
     * Empty / blank names are never blacklisted (let the picker
     * decide how to handle those).
     */
    fun isBlocked(channelName: String): Boolean {
        if (channelName.isBlank()) return false
        // Pad the name so a token at the very end ("FOX SPORTS CBC")
        // still triggers the trailing word-boundary in the regex.
        val padded = " ${channelName.uppercase()} "
        return regexes.any { it.containsMatchIn(padded) }
    }
}
