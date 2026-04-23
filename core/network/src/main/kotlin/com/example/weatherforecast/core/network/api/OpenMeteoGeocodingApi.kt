package com.example.weatherforecast.core.network.api

import com.example.weatherforecast.core.network.dto.GeocodingResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

internal interface OpenMeteoGeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponseDto
}