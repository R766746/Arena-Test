package com.nova.iptv.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface XtreamApi {
    @GET
    suspend fun authenticate(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
    ): XtreamAuth

    @GET
    suspend fun liveStreams(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
    ): List<XtreamLive>

    @GET
    suspend fun vodStreams(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
    ): List<XtreamVod>

    @GET
    suspend fun series(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
    ): List<XtreamSeries>

    @GET
    suspend fun liveCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories",
    ): List<XtreamCategory>

    @GET
    suspend fun vodCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories",
    ): List<XtreamCategory>

    @GET
    suspend fun seriesCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories",
    ): List<XtreamCategory>

    @GET
    suspend fun vodInfo(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: String,
    ): XtreamVodInfo

    @GET
    suspend fun seriesInfo(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: String,
    ): XtreamSeriesInfo

    @GET
    suspend fun shortEpg(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: String,
        @Query("limit") limit: Int = 20,
    ): XtreamShortEpg
}

@JsonClass(generateAdapter = true)
data class XtreamAuth(
    @Json(name = "user_info") val userInfo: XtreamUserInfo? = null,
    @Json(name = "server_info") val serverInfo: XtreamServerInfo? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamUserInfo(
    val username: String? = null,
    val status: String? = null,
    val auth: Int? = null,
    @Json(name = "exp_date") val expDate: String? = null,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamServerInfo(
    val url: String? = null,
    val port: String? = null,
    val https_port: String? = null,
    val server_protocol: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamLive(
    val num: Int? = null,
    val name: String? = null,
    @Json(name = "stream_type") val streamType: String? = null,
    @Json(name = "stream_id") val streamId: Int? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "tv_archive") val tvArchive: Int? = null,
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Int? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVod(
    val num: Int? = null,
    val name: String? = null,
    @Json(name = "stream_id") val streamId: Int? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    val rating: String? = null,
    val year: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "container_extension") val container: String? = null,
    val plot: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val duration: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamSeries(
    val num: Int? = null,
    val name: String? = null,
    @Json(name = "series_id") val seriesId: Int? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val year: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    val backdrop_path: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "category_name") val categoryName: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfo(
    val info: XtreamVodExtra? = null,
    @Json(name = "movie_data") val movieData: XtreamVod? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodExtra(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val movie_image: String? = null,
    val rating: String? = null,
    val releasedate: String? = null,
    val backdrop_path: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesInfo(
    val info: XtreamSeries? = null,
    val episodes: Map<String, List<XtreamEpisode>>? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamEpisode(
    val id: String? = null,
    @Json(name = "episode_num") val episodeNum: Int? = null,
    val title: String? = null,
    val container_extension: String? = null,
    val season: Int? = null,
    val plot: String? = null,
    val duration: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamShortEpg(
    @Json(name = "epg_listings") val listings: List<XtreamEpgListing>? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamEpgListing(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val start: String? = null,
    val end: String? = null,
    @Json(name = "start_timestamp") val startTs: String? = null,
    @Json(name = "stop_timestamp") val stopTs: String? = null,
)
