package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun observeWeather(cityId: String, unit: TemperatureUnit): Flow<Weather?>

    suspend fun refreshWeather(city: City, unit: TemperatureUnit): Result<Unit, AppError>
}