package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.repository.WeatherRepository
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.Weather
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveSelectedCityWeatherUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cityRepository: CityRepository,
    private val weatherRepository: WeatherRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Pair<City, Weather?>> {
        return combine(
            userPreferencesRepository.selectedCityId,
            userPreferencesRepository.temperatureUnit,
            cityRepository.observeSavedCities(),
        ) { cityId, unit, cities ->
            val city = cityId?.let { id -> cities.firstOrNull { it.id == id } } ?: DefaultCity.TAIPEI
            city to unit
        }
            .flatMapLatest { (city, unit) ->
                weatherRepository.observeWeather(city.id, unit)
                    .map { weather -> city to weather }
            }
    }
}