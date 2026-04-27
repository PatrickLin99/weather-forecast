package com.example.weatherforecast.core.location.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City

interface LocationDataSource {
    suspend fun fetchCurrentLocationCity(): Result<City, AppError>
}