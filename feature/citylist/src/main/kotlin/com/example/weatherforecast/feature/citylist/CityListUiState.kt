package com.example.weatherforecast.feature.citylist

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.model.City

data class CityListUiState(
    val query: String = "",
    val searchState: SearchState = SearchState.Idle,
    val savedCities: List<City> = emptyList(),
    val selectedCityId: String? = null,
)

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val cities: List<City>) : SearchState
    data class Error(val error: AppError) : SearchState
}