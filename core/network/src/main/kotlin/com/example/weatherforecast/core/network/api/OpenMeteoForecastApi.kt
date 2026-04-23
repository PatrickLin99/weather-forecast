package com.example.weatherforecast.core.network.api

import com.example.weatherforecast.core.network.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

internal interface OpenMeteoForecastApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("forecast_days") forecastDays: Int = 7,
    ): ForecastResponseDto

    companion object {
        private const val CURRENT_PARAMS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
        private const val DAILY_PARAMS =
            "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
        private const val HOURLY_PARAMS =
            "temperature_2m,weather_code"
    }
}