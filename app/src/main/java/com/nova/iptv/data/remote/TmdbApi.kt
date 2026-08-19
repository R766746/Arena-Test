package com.nova.iptv.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApi {
    @GET("3/search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
    ): TmdbSearchResponse

    @GET("3/search/tv")
    suspend fun searchSeries(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null,
    ): TmdbSearchResponse
}

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse(val results: List<TmdbSearchResult> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbSearchResult(
    val id: Long,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
)
