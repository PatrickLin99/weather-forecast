package com.example.weatherforecast.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ForecastResponseDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    @SerialName("current") val current: CurrentWeatherDto,
    @SerialName("daily") val daily: DailyForecastDto,
    @SerialName("hourly") val hourly: HourlyForecastDto,
)