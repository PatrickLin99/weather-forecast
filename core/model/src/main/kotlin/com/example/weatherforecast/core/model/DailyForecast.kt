package com.example.weatherforecast.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class DailyForecast(
    val date: LocalDate,
    val tempMin: Double,
    val tempMax: Double,
    val condition: WeatherCondition,
    val sunrise: Instant?,
    val sunset: Instant?,
    val precipitationProbability: Int?,
)