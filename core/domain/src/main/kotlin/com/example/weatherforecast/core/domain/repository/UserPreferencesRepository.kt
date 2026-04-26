package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val selectedCityId: Flow<String?>
    val temperatureUnit: Flow<TemperatureUnit>

    suspend fun setSelectedCityId(cityId: String)
    suspend fun setTemperatureUnit(unit: TemperatureUnit)
}