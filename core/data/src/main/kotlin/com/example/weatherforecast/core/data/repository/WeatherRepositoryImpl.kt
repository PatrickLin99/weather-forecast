package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.database.datasource.WeatherLocalDataSource
import com.example.weatherforecast.core.domain.repository.WeatherRepository
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import com.example.weatherforecast.core.network.datasource.WeatherRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WeatherRepositoryImpl @Inject constructor(
    private val remote: WeatherRemoteDataSource,
    private val local: WeatherLocalDataSource,
) : WeatherRepository {

    override fun observeWeather(cityId: String, unit: TemperatureUnit): Flow<Weather?> =
        local.observeWeather(cityId, unit)

    override suspend fun refreshWeather(
        city: City,
        unit: TemperatureUnit,
    ): Result<Unit, AppError> =
        remote.fetchWeather(city, unit)
            .flatMap { weather -> local.upsertWeather(weather) }
}