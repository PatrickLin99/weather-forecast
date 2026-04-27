package com.example.weatherforecast.core.network.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather

interface WeatherRemoteDataSource {
    suspend fun fetchWeather(city: City, unit: TemperatureUnit): Result<Weather, AppError>
}