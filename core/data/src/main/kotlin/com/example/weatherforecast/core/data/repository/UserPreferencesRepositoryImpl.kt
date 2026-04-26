package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.datastore.UserPreferencesDataSource
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : UserPreferencesRepository {

    override val selectedCityId: Flow<String?> = dataSource.selectedCityId
    override val temperatureUnit: Flow<TemperatureUnit> = dataSource.temperatureUnit

    override suspend fun setSelectedCityId(cityId: String) {
        dataSource.setSelectedCityId(cityId)
    }

    override suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataSource.setTemperatureUnit(unit)
    }
}