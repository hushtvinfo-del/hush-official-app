package com.hushtv.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tiny IMDb-style rating badge for poster overlays.
 *
 * Renders the iconic black-on-yellow IMDb wordmark next to the rating
 * value (e.g. `IMDb  7.8`). Uses official IMDb yellow `#F5C518`.
 *
 * Xtream-Codes IPTV providers conventionally put the IMDb rating in
 * the `rating` field of `get_vod_streams` / `get_series` responses.
 * Other providers leave it blank or store something else (e.g.
 * `R`/`PG-13` MPAA codes). [parseRating] is liberal but only renders
 * when we actually find a sensible 0–10 number, so MPAA codes get
 * silently dropped instead of polluting the UI.
 *
 * Usage:
 *   Box {
 *       AsyncImage(...)
 *       ImdbBadge(card.rating, Modifier.align(Alignment.TopStart).padding(6.dp))
 *   }
 */
@Composable
fun ImdbBadge(
    rating: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
) {
    val parsed = parseRating(rating) ?: return
    Row(
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The IMDb logo block — yellow chip with bold "IMDb" wordmark.
        Box(
            Modifier
                .background(Color(0xFFF5C518), RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                "IMDb",
                color = Color.Black,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                lineHeight = (fontSize.value + 2).sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            String.format("%.1f", parsed),
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize.value + 2).sp,
        )
    }
}

/**
 * Parses an Xtream rating string into a 0..10 number.
 *
 * Most Xtream providers return the IMDb rating as a string of a
 * decimal number (e.g. `"7.8"`). Some return it as an integer
 * (`"8"`), some as `"N/A"`, some as MPAA codes (`"R"`, `"PG-13"`),
 * some as the 5-based scale (`"4.2"`). We:
 *
 *   1. Reject anything that doesn't parse as a number.
 *   2. Reject obvious junk (≤ 0 or ≥ 11).
 *   3. Auto-rescale 0..5 ratings to 0..10 — providers that only
 *      populate `rating_5based` sometimes mirror it into `rating`.
 *
 * Returns null when nothing usable is in the string, in which case the
 * caller should not render any badge.
 */
private fun parseRating(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val n = raw.trim().toFloatOrNull() ?: return null
    return when {
        n <= 0f -> null
        n in 0.1f..5f -> n * 2f          // re-scale 5-based to 10-based
        n in 5.01f..10f -> n              // already 10-based, the typical case
        else -> null                       // junk / out-of-range
    }
}
