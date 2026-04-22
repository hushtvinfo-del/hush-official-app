package com.hushtv.tv.data

import com.squareup.moshi.JsonClass

/** Matches the web app's localStorage "playlists" entries. */
@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String,
    val name: String,
    val username: String,
    val password: String,
    val host: String,
    val epgUrl: String? = null,
    val isActive: Boolean = true
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val username: String? = null,
    val password: String? = null,
    val message: String? = null,
    val auth: Int? = null,
    val status: String? = null,
    val exp_date: String? = null,
    val is_trial: String? = null,
    val active_cons: String? = null,
    val created_at: String? = null,
    val max_connections: String? = null
)

@JsonClass(generateAdapter = true)
data class ServerInfo(
    val url: String? = null,
    val port: String? = null,
    val https_port: String? = null,
    val server_protocol: String? = null,
    val rtmp_port: String? = null,
    val timezone: String? = null,
    val timestamp_now: Long? = null,
    val time_now: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val user_info: UserInfo? = null,
    val server_info: ServerInfo? = null
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    val category_id: String,
    val category_name: String,
    val parent_id: Int? = 0
)

@JsonClass(generateAdapter = true)
data class XtreamLiveStream(
    val num: Int? = null,
    val name: String = "",
    val stream_type: String? = null,
    val stream_id: Int = 0,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val added: String? = null,
    val category_id: String? = null,
    val tv_archive: Int? = 0,
    val direct_source: String? = null,
    val tv_archive_duration: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamVod(
    val num: Int? = null,
    val name: String = "",
    val stream_type: String? = null,
    val stream_id: Int = 0,
    val stream_icon: String? = null,
    val rating: String? = null,
    val rating_5based: Double? = null,
    val added: String? = null,
    val category_id: String? = null,
    val container_extension: String? = "mp4",
    val direct_source: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamSeries(
    val num: Int? = null,
    val name: String = "",
    val series_id: Int = 0,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val last_modified: String? = null,
    val rating: String? = null,
    val rating_5based: Double? = null,
    val category_id: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamEpisodeInfo(
    val movie_image: String? = null,
    val plot: String? = null,
    val duration_secs: Int? = null,
    val duration: String? = null,
    val rating: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamEpisode(
    val id: String = "",
    val episode_num: Int = 0,
    val title: String = "",
    val container_extension: String? = "mp4",
    val info: XtreamEpisodeInfo? = null,
    val season: Int? = 0
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesInfo(
    val seasons: List<Map<String, Any>>? = null,
    val info: Map<String, Any>? = null,
    val episodes: Map<String, List<XtreamEpisode>>? = null
)

/** Response from `get_vod_info` — rich metadata for a single movie. */
@JsonClass(generateAdapter = true)
data class XtreamVodInfoInner(
    val movie_image: String? = null,
    val backdrop_path: List<String>? = null,
    val cover_big: String? = null,
    val youtube_trailer: String? = null,
    val plot: String? = null,
    val description: String? = null,
    val rating: String? = null,
    val rating_kinopoisk: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val duration_secs: Int? = null,
    val releasedate: String? = null,
    val release_date: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val country: String? = null,
    val actors: String? = null,
    val tmdb_id: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodMovieData(
    val stream_id: Int? = null,
    val name: String? = null,
    val added: String? = null,
    val category_id: String? = null,
    val container_extension: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfo(
    val info: XtreamVodInfoInner? = null,
    val movie_data: XtreamVodMovieData? = null,
)

/** Unified "card" item the UI uses. */
data class MediaCard(
    val id: String,
    val title: String,
    val poster: String?,
    val rating: String?,
    val streamId: Int,
    val seriesId: Int,
    val containerExtension: String?,
    val kind: String, // "live" | "movie" | "series"
    val addedTs: Long = 0L, // unix seconds — used for "New" filter
)
