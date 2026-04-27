package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.common.result.getOrNull
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.LocationRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves which city to show on app launch.
 *
 * Fallback chain (PR 05):
 *   1. If `useLocation` AND location succeeds → save and return that city
 *   2. Last-selected city (DataStore → Room) → return
 *   3. DefaultCity.TAIPEI (persisted on first call so FK-dependent writes succeed)
 *
 * @param useLocation if false, skip the location branch entirely. Caller passes false when
 *                    permission is not granted, so we don't waste a SecurityException trip.
 */
class ResolveInitialCityUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(useLocation: Boolean): City {
        if (useLocation) {
            val locationCity = locationRepository.getCurrentLocationCity().getOrNull()
            if (locationCity != null) {
                cityRepository.saveCity(locationCity)
                userPreferencesRepository.setSelectedCityId(locationCity.id)
                return locationCity
            }
            // Location failed (timeout / disabled / geocoder) — fall through silently.
            // The banner UX in WeatherViewModel surfaces the issue.
        }

        val lastCityId = userPreferencesRepository.selectedCityId.first()
        if (lastCityId != null) {
            cityRepository.getCityById(lastCityId)?.let { return it }
        }

        cityRepository.saveCity(DefaultCity.TAIPEI)
        userPreferencesRepository.setSelectedCityId(DefaultCity.TAIPEI.id)
        return DefaultCity.TAIPEI
    }
}