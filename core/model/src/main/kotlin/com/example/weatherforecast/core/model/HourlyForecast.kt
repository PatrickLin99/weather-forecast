package com.example.weatherforecast.core.model

import kotlinx.datetime.Instant

data class HourlyForecast(
    val time: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
)