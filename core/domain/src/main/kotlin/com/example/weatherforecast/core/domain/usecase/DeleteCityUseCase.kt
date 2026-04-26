package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deletes a city. If the deleted city is currently selected, fall back to
 * DefaultCity.TAIPEI (re-saving it if needed) so selectedCityId never
 * points at a non-existent row.
 */
class DeleteCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(cityId: String): Result<Unit, AppError> {
        val currentSelectedId = userPreferencesRepository.selectedCityId.first()
        return cityRepository.deleteCity(cityId).flatMap {
            if (cityId == currentSelectedId) {
                cityRepository.saveCity(DefaultCity.TAIPEI).flatMap {
                    userPreferencesRepository.setSelectedCityId(DefaultCity.TAIPEI.id)
                    Result.Success(Unit)
                }
            } else {
                Result.Success(Unit)
            }
        }
    }
}