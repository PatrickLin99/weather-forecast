package com.example.weatherforecast.core.database.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.database.dao.WeatherDao
import com.example.weatherforecast.core.database.mapper.toDomain
import com.example.weatherforecast.core.database.mapper.toCurrentEntity
import com.example.weatherforecast.core.database.mapper.toEntity
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

internal class WeatherLocalDataSource @Inject constructor(
    private val weatherDao: WeatherDao,
) {
    fun observeWeather(cityId: String, unit: TemperatureUnit): Flow<Weather?> =
        combine(
            weatherDao.observeCurrentWeather(cityId),
            weatherDao.observeDailyForecasts(cityId),
        ) { current, daily ->
            current?.toDomain(daily = daily.map { it.toDomain() }, unit = unit)
        }

    suspend fun upsertWeather(weather: Weather): Result<Unit, AppError> = try {
        weatherDao.upsertFullWeather(
            current = weather.toCurrentEntity(),
            daily = weather.daily.map { it.toEntity(weather.cityId) },
        )
        Result.Success(Unit)
    } catch (e: Throwable) {
        Result.Failure(AppError.DatabaseError)
    }
}