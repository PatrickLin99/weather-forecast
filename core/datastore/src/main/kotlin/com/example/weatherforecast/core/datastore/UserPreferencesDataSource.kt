package com.example.weatherforecast.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.weatherforecast.core.datastore.key.PreferencesKeys
import com.example.weatherforecast.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class UserPreferencesDataSource @Inject internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val selectedCityId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.SELECTED_CITY_ID] }

    val temperatureUnit: Flow<TemperatureUnit> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            it[PreferencesKeys.TEMPERATURE_UNIT]
                ?.let { name -> runCatching { TemperatureUnit.valueOf(name) }.getOrNull() }
                ?: TemperatureUnit.CELSIUS
        }

    suspend fun setSelectedCityId(cityId: String) {
        dataStore.edit { it[PreferencesKeys.SELECTED_CITY_ID] = cityId }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[PreferencesKeys.TEMPERATURE_UNIT] = unit.name }
    }
}