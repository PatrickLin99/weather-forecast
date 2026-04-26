package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// PR03 scope: simplified — last-selected city → DefaultCity.TAIPEI.
// PR05 will insert location-detection ahead of the last-selected branch.
class ResolveInitialCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(): City {
        val lastCityId = userPreferencesRepository.selectedCityId.first()
        if (lastCityId != null) {
            cityRepository.getCityById(lastCityId)?.let { return it }
        }
        // Persist DefaultCity so the FK-dependent weather upsert can succeed,
        // and record selection so subsequent launches take the fast path above.
        cityRepository.saveCity(DefaultCity.TAIPEI)
        userPreferencesRepository.setSelectedCityId(DefaultCity.TAIPEI.id)
        return DefaultCity.TAIPEI
    }
}