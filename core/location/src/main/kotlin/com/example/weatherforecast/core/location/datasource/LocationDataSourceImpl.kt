package com.example.weatherforecast.core.location.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.location.geocoder.GeocoderWrapper
import com.example.weatherforecast.core.location.provider.LocationProvider
import com.example.weatherforecast.core.model.City
import javax.inject.Inject

internal class LocationDataSourceImpl @Inject constructor(
    private val locationProvider: LocationProvider,
    private val geocoder: GeocoderWrapper,
) : LocationDataSource {
    override suspend fun fetchCurrentLocationCity(): Result<City, AppError> =
        locationProvider.getCurrentLocation().flatMap { coords ->
            geocoder.reverseGeocode(coords)
        }
}