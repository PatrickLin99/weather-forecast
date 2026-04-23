package com.example.weatherforecast.core.database.mapper

import com.example.weatherforecast.core.database.entity.CurrentWeatherEntity
import com.example.weatherforecast.core.database.entity.DailyForecastEntity
import com.example.weatherforecast.core.model.DailyForecast
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import com.example.weatherforecast.core.model.WeatherCondition
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

internal fun Weather.toCurrentEntity(): CurrentWeatherEntity = CurrentWeatherEntity(
    cityId = cityId,
    temperature = temperature,
    feelsLike = feelsLike,
    humidity = humidity,
    windSpeed = windSpeed,
    conditionCode = condition.name,
    unit = unit.name,
    updatedAt = updatedAt.toEpochMilliseconds(),
)

internal fun CurrentWeatherEntity.toDomain(
    daily: List<DailyForecast>,
    unit: TemperatureUnit,
): Weather = Weather(
    cityId = cityId,
    temperature = temperature,
    feelsLike = feelsLike,
    humidity = humidity,
    windSpeed = windSpeed,
    condition = runCatching { WeatherCondition.valueOf(conditionCode) }.getOrDefault(WeatherCondition.UNKNOWN),
    unit = unit,
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    daily = daily,
)

internal fun DailyForecast.toEntity(cityId: String): DailyForecastEntity = DailyForecastEntity(
    cityId = cityId,
    date = date.toString(),
    tempMin = tempMin,
    tempMax = tempMax,
    conditionCode = condition.name,
    sunriseEpoch = sunrise?.toEpochMilliseconds(),
    sunsetEpoch = sunset?.toEpochMilliseconds(),
    precipitationProbability = precipitationProbability,
)

internal fun DailyForecastEntity.toDomain(): DailyForecast = DailyForecast(
    date = LocalDate.parse(date),
    tempMin = tempMin,
    tempMax = tempMax,
    condition = runCatching { WeatherCondition.valueOf(conditionCode) }.getOrDefault(WeatherCondition.UNKNOWN),
    sunrise = sunriseEpoch?.let { Instant.fromEpochMilliseconds(it) },
    sunset = sunsetEpoch?.let { Instant.fromEpochMilliseconds(it) },
    precipitationProbability = precipitationProbability,
)