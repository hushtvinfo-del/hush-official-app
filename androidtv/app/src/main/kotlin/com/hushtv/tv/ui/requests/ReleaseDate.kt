package com.hushtv.tv.ui.requests

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Helpers for "has this content actually been released?" guards used
 * across the request flow.
 *
 * # Why this matters
 * TMDB returns `release_date` (movies), `first_air_date` (series) and
 * `air_date` (episodes) in `YYYY-MM-DD` format. A user can search for
 * — or browse the upcoming-episodes list of — content that hasn't
 * actually been released yet (e.g. an in-production K-drama airing
 * on 2026-05-06). Letting them request that content is wasted work
 * for the moderation team and confusing for the user (we'd just
 * have to reject the request).
 *
 * # Comparison rules
 * Dates are compared against TODAY in the **device's local time
 * zone**. We deliberately avoid UTC here — when a user sees
 * "Episode 3 · 2026-05-06" they're reading that as a local date.
 * Comparing against a UTC midnight could classify the SAME date
 * differently depending on whether the user lives in UTC+12 or
 * UTC-12, which is confusing.
 *
 * Malformed / missing dates are treated as "released" (i.e. let the
 * user request it). We don't want to block a perfectly fine request
 * just because TMDB returned a bad date string for an old movie.
 */
private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Returns `true` when [dateStr] is a valid ISO-8601 date strictly
 * AFTER today's local date. Returns `false` for null, blank, or
 * malformed dates (so unparseable dates never block a request).
 */
fun isFutureReleaseDate(dateStr: String?): Boolean {
    if (dateStr.isNullOrBlank()) return false
    return try {
        val date = LocalDate.parse(dateStr, ISO_DATE)
        date.isAfter(LocalDate.now())
    } catch (_: DateTimeParseException) {
        false
    }
}
