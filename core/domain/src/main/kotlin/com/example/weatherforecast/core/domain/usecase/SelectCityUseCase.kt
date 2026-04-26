package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import javax.inject.Inject

class SelectCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(city: City): Result<Unit, AppError> =
        cityRepository.saveCity(city).flatMap {
            userPreferencesRepository.setSelectedCityId(city.id)
            Result.Success(Unit)
        }
}