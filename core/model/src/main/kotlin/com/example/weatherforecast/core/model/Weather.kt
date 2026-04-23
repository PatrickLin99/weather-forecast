package com.example.weatherforecast.core.model

import kotlinx.datetime.Instant

data class Weather(
    val cityId: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val condition: WeatherCondition,
    val unit: TemperatureUnit,
    val updatedAt: Instant,
    val daily: List<DailyForecast>,
    val hourly: List<HourlyForecast> = emptyList(),
)