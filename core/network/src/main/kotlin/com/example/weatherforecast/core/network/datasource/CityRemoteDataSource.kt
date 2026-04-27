package com.example.weatherforecast.core.network.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.network.api.OpenMeteoGeocodingApi
import com.example.weatherforecast.core.network.mapper.toDomain
import com.example.weatherforecast.core.network.util.apiCall
import javax.inject.Inject

class CityRemoteDataSource @Inject internal constructor(
    private val api: OpenMeteoGeocodingApi,
) {
    suspend fun searchCities(query: String): Result<List<City>, AppError> = apiCall {
        api.search(name = query).results.orEmpty().map { it.toDomain() }
    }
}