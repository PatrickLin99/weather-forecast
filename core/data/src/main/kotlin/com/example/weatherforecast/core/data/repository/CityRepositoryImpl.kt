package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.database.datasource.CityLocalDataSource
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CityRepositoryImpl @Inject constructor(
    private val local: CityLocalDataSource,
) : CityRepository {

    override fun observeSavedCities(): Flow<List<City>> = local.observeCities()

    override suspend fun getCityById(cityId: String): City? = local.getCityById(cityId)

    override suspend fun saveCity(city: City): Result<Unit, AppError> = local.upsertCity(city)

    override suspend fun deleteCity(cityId: String): Result<Unit, AppError> =
        local.deleteCity(cityId)
}