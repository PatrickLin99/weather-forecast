package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Emits the currently selected City whenever selectedCityId changes.
 * Falls back to DefaultCity.TAIPEI if the id no longer resolves to a saved row,
 * so observers can rely on a non-null City even mid-deletion.
 */
class ObserveSelectedCityUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cityRepository: CityRepository,
) {
    operator fun invoke(): Flow<City> =
        userPreferencesRepository.selectedCityId
            .filterNotNull()
            .distinctUntilChanged()
            .map { id -> cityRepository.getCityById(id) ?: DefaultCity.TAIPEI }
}