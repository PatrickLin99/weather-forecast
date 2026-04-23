package com.example.weatherforecast.core.network.mapper

import com.example.weatherforecast.core.model.DailyForecast
import com.example.weatherforecast.core.model.HourlyForecast
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import com.example.weatherforecast.core.network.dto.DailyForecastDto
import com.example.weatherforecast.core.network.dto.ForecastResponseDto
import com.example.weatherforecast.core.network.dto.HourlyForecastDto
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant

internal fun ForecastResponseDto.toDomain(cityId: String, unit: TemperatureUnit): Weather =
    Weather(
        cityId = cityId,
        temperature = current.temperature,
        feelsLike = current.apparentTemperature,
        humidity = current.humidity,
        windSpeed = current.windSpeed,
        condition = current.weatherCode.toWeatherCondition(),
        unit = unit,
        updatedAt = Clock.System.now(),
        daily = daily.toDomain(utcOffsetSeconds),
        hourly = hourly.toDomain(utcOffsetSeconds),
    )

private fun DailyForecastDto.toDomain(utcOffsetSeconds: Int): List<DailyForecast> {
    val offset = UtcOffset(seconds = utcOffsetSeconds)
    return time.mapIndexed { index, dateStr ->
        DailyForecast(
            date = LocalDate.parse(dateStr),
            tempMin = temperatureMin.getOrElse(index) { 0.0 },
            tempMax = temperatureMax.getOrElse(index) { 0.0 },
            condition = weatherCode.getOrNull(index)?.toWeatherCondition() ?: com.example.weatherforecast.core.model.WeatherCondition.UNKNOWN,
            sunrise = sunrise.getOrNull(index)?.let { runCatching { LocalDateTime.parse(it).toInstant(offset) }.getOrNull() },
            sunset = sunset.getOrNull(index)?.let { runCatching { LocalDateTime.parse(it).toInstant(offset) }.getOrNull() },
            precipitationProbability = precipitationProbabilityMax.getOrNull(index),
        )
    }
}

private fun HourlyForecastDto.toDomain(utcOffsetSeconds: Int): List<HourlyForecast> {
    val offset = UtcOffset(seconds = utcOffsetSeconds)
    return time.mapIndexed { index, timeStr ->
        HourlyForecast(
            time = runCatching { LocalDateTime.parse(timeStr).toInstant(offset) }.getOrDefault(Clock.System.now()),
            temperature = temperature.getOrElse(index) { 0.0 },
            condition = weatherCode.getOrNull(index)?.toWeatherCondition() ?: com.example.weatherforecast.core.model.WeatherCondition.UNKNOWN,
        )
    }
}