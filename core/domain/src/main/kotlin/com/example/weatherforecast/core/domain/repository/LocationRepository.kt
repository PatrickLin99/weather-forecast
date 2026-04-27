package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City

interface LocationRepository {
    /**
     * Returns the device's current location as a City (with isCurrentLocation = true,
     * id = "current_location"). Caller must verify ACCESS_COARSE_LOCATION before calling.
     */
    suspend fun getCurrentLocationCity(): Result<City, AppError>
}