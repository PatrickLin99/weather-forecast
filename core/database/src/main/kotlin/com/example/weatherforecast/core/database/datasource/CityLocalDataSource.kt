package com.example.weatherforecast.core.database.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.database.dao.CityDao
import com.example.weatherforecast.core.database.mapper.toDomain
import com.example.weatherforecast.core.database.mapper.toEntity
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class CityLocalDataSource @Inject constructor(
    private val cityDao: CityDao,
) {
    fun observeCities(): Flow<List<City>> =
        cityDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getCityById(cityId: String): City? =
        cityDao.getById(cityId)?.toDomain()

    suspend fun upsertCity(city: City): Result<Unit, AppError> = try {
        cityDao.upsert(city.toEntity())
        Result.Success(Unit)
    } catch (e: Throwable) {
        Result.Failure(AppError.DatabaseError)
    }

    suspend fun deleteCity(cityId: String): Result<Unit, AppError> = try {
        cityDao.deleteById(cityId)
        Result.Success(Unit)
    } catch (e: Throwable) {
        Result.Failure(AppError.DatabaseError)
    }
}