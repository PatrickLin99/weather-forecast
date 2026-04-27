package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

/**
 * Emits the currently selected City whenever selectedCityId or the saved-cities list changes.
 * Using observeSavedCities() means an in-place upsert of the current_location row
 * (same id, updated name/coords) triggers a fresh emission — fixing the stale-city bug (TD-002).
 * Falls back to DefaultCity.TAIPEI if the id no longer resolves to a saved row.
 */
class ObserveSelectedCityUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cityRepository: CityRepository,
) {
    operator fun invoke(): Flow<City> =
        combine(
            userPreferencesRepository.selectedCityId.filterNotNull(),
            cityRepository.observeSavedCities(),
        ) { id, cities ->
            cities.firstOrNull { it.id == id } ?: DefaultCity.TAIPEI
        }.distinctUntilChanged()
}