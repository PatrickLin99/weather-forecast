package com.example.weatherforecast.feature.weather

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.Weather

sealed interface WeatherUiState {
    data object Loading : WeatherUiState

    data class Success(
        val weather: Weather,
        val city: City,
        val isStale: Boolean = false,
        val transientMessage: String? = null,
    ) : WeatherUiState

    data class Error(
        val error: AppError,
        val canRetry: Boolean,
    ) : WeatherUiState
}
