package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.model.City
import javax.inject.Inject

class SearchCitiesUseCase @Inject constructor(
    private val cityRepository: CityRepository,
) {
    suspend operator fun invoke(query: String): Result<List<City>, AppError> =
        cityRepository.searchCities(query)
}