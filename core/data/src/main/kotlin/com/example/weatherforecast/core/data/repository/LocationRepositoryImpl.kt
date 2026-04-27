package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.LocationRepository
import com.example.weatherforecast.core.location.datasource.LocationDataSource
import com.example.weatherforecast.core.model.City
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocationRepositoryImpl @Inject constructor(
    private val locationDataSource: LocationDataSource,
) : LocationRepository {
    override suspend fun getCurrentLocationCity(): Result<City, AppError> =
        locationDataSource.fetchCurrentLocationCity()
}