package com.example.weatherforecast.core.database.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow

interface CityLocalDataSource {
    fun observeCities(): Flow<List<City>>
    suspend fun getCityById(cityId: String): City?
    suspend fun upsertCity(city: City): Result<Unit, AppError>
    suspend fun deleteCity(cityId: String): Result<Unit, AppError>
}