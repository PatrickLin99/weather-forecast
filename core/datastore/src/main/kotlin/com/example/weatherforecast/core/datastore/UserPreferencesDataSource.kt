package com.example.weatherforecast.core.datastore

import com.example.weatherforecast.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow

interface UserPreferencesDataSource {
    val selectedCityId: Flow<String?>
    val temperatureUnit: Flow<TemperatureUnit>
    suspend fun setSelectedCityId(cityId: String)
    suspend fun setTemperatureUnit(unit: TemperatureUnit)
}