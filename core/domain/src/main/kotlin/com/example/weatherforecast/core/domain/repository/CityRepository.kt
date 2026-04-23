package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow

interface CityRepository {
    fun observeSavedCities(): Flow<List<City>>

    suspend fun getCityById(cityId: String): City?

    suspend fun saveCity(city: City): Result<Unit, AppError>

    suspend fun deleteCity(cityId: String): Result<Unit, AppError>
}