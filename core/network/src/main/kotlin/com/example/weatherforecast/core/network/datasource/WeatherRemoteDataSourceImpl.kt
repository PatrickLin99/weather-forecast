package com.example.weatherforecast.core.network.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import com.example.weatherforecast.core.network.api.OpenMeteoForecastApi
import com.example.weatherforecast.core.network.mapper.toDomain
import com.example.weatherforecast.core.network.util.apiCall
import javax.inject.Inject

internal class WeatherRemoteDataSourceImpl @Inject constructor(
    private val api: OpenMeteoForecastApi,
) : WeatherRemoteDataSource {
    override suspend fun fetchWeather(
        city: City,
        unit: TemperatureUnit,
    ): Result<Weather, AppError> = apiCall {
        val tempUnitParam = if (unit == TemperatureUnit.FAHRENHEIT) "fahrenheit" else "celsius"
        api.getForecast(
            latitude = city.latitude,
            longitude = city.longitude,
            temperatureUnit = tempUnitParam,
        ).toDomain(cityId = city.id, unit = unit)
    }
}