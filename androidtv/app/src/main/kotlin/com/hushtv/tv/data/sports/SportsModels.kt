@file:Suppress("MemberVisibilityCanBePrivate", "ConstructorParameterNaming")

package com.hushtv.tv.data.sports

/**
 * Wire models for the sync server's `/api/sports/...` endpoints. Field
 * names match the Python serializer in `sports_module.py`.
 *
 * NOTE: We intentionally do NOT annotate these with
 * `@JsonClass(generateAdapter = true)`. The project uses
 * `moshi-kotlin` (reflection-based) without the kapt codegen plugin —
 * if the annotation were present, Moshi's `KotlinJsonAdapterFactory`
 * would refuse to handle the class (it expects a generated adapter),
 * causing a runtime "No adapter found" crash on the first JSON parse.
 * Plain data classes work cleanly via reflection.
 */

data class SportsLeague(
    val slug: String,
    val name: String,
    val accent: String? = null,
    val display_order: Int = 100,
)

data class SportsTeam(
    val name: String,
    val short_name: String? = null,
    val logo_url: String? = null,
    val badge_url: String? = null,
)

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
     *  loaded live channels via [SportsChannelMatcher]. */
    val channel: String? = null,
)

data class SportsHero(
    /** "ppv" or "game" */
    val kind: String,
    val id: Int,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val start_utc: Long,
    val channel: String? = null,
    /** v1.44.5 — server-supplied status: "scheduled", "live", "final".
     *  Used by SportsHero to pick the correct eyebrow label and the
     *  correct countdown text instead of inferring from the time
     *  delta alone (which mislabelled in-progress games as "FINAL"
     *  the moment they crossed the +2h mark). */
    val status: String = "scheduled",
    /** Score lines for live/final games. Strings (not Ints) because
     *  cricket / motorsport sometimes use non-numeric scores
     *  ("DNF", "RAINED OUT"). */
    val score_home: String? = null,
    val score_away: String? = null,
)

data class SportsLeagueBucket(
    val league: SportsLeague,
    val games: List<SportsGame>,
)

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

data class SportsHomeResponse(
    val hero: List<SportsHero> = emptyList(),
    val ppv: List<SportsPpvEvent> = emptyList(),
    val leagues: List<SportsLeagueBucket> = emptyList(),
)

data class SportsLeagueResponse(
    val league: SportsLeague,
    val games: List<SportsGame> = emptyList(),
    val count: Int = 0,
)

data class SportsPpvListResponse(
    val events: List<SportsPpvEvent> = emptyList(),
)
