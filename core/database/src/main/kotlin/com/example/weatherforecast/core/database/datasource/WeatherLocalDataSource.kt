package com.example.weatherforecast.core.database.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import kotlinx.coroutines.flow.Flow

interface WeatherLocalDataSource {
    fun observeWeather(cityId: String, unit: TemperatureUnit): Flow<Weather?>
    suspend fun upsertWeather(weather: Weather): Result<Unit, AppError>
}