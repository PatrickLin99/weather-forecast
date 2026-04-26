package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.repository.WeatherRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(city: City): Result<Unit, AppError> {
        val unit = userPreferencesRepository.temperatureUnit.first()
        return weatherRepository.refreshWeather(city, unit)
    }
}