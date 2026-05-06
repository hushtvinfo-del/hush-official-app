@file:Suppress("MemberVisibilityCanBePrivate")

package com.hushtv.tv.data.sports

import com.squareup.moshi.JsonClass

/**
 * Wire models for the sync server's `/api/sports/...` endpoints. Field
 * names match the Python serializer in `sports_module.py`. We use
 * Moshi's codegen because the Sync server already runs gigabytes of
 * traffic through Moshi for the regular sync path.
 */

@JsonClass(generateAdapter = true)
data class SportsLeague(
    val slug: String,
    val name: String,
    val accent: String? = null,
    val display_order: Int = 100,
)

@JsonClass(generateAdapter = true)
data class SportsTeam(
    val name: String,
    val short_name: String? = null,
    val logo_url: String? = null,
    val badge_url: String? = null,
)

@JsonClass(generateAdapter = true)
data class SportsGame(
    val id: Int,
    val league: SportsLeague? = null,
    val home: SportsTeam? = null,
    val away: SportsTeam? = null,
    val start_utc: Long,
    val status: String = "scheduled",
    val score_home: String? = null,
    val score_away: String? = null,
    val venue: String? = null,
    val round: String? = null,
    /** Channel name as it should appear in the user's Xtream playlist
     *  (e.g. "SPORTSNET ONE"). The client does a fuzzy match against
     *  loaded live channels via [com.hushtv.tv.data.sports.SportsChannelMatcher].
     */
    val channel: String? = null,
)

@JsonClass(generateAdapter = true)
data class SportsHero(
    /** "ppv" | "game" */
    val kind: String,
    val id: Int,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val start_utc: Long,
    val channel: String? = null,
)

@JsonClass(generateAdapter = true)
data class SportsLeagueBucket(
    val league: SportsLeague,
    val games: List<SportsGame>,
)

@JsonClass(generateAdapter = true)
data class SportsPpvEvent(
    val id: Int,
    val source: String,
    val title: String,
    val subtitle: String? = null,
    val poster_url: String? = null,
    val start_utc: Long,
    val status: String = "scheduled",
    val channel: String? = null,
)

@JsonClass(generateAdapter = true)
data class SportsHomeResponse(
    val hero: List<SportsHero> = emptyList(),
    val ppv: List<SportsPpvEvent> = emptyList(),
    val leagues: List<SportsLeagueBucket> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SportsLeagueResponse(
    val league: SportsLeague,
    val games: List<SportsGame> = emptyList(),
    val count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class SportsPpvListResponse(
    val events: List<SportsPpvEvent> = emptyList(),
)
