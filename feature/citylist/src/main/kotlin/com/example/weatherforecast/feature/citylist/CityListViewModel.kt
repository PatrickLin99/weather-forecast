package com.example.weatherforecast.feature.citylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherforecast.core.common.result.onFailure
import com.example.weatherforecast.core.common.result.onSuccess
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.usecase.DeleteCityUseCase
import com.example.weatherforecast.core.domain.usecase.SearchCitiesUseCase
import com.example.weatherforecast.core.domain.usecase.SelectCityUseCase
import com.example.weatherforecast.core.model.City
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CityListViewModel @Inject constructor(
    cityRepository: CityRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val searchCities: SearchCitiesUseCase,
    private val selectCity: SelectCityUseCase,
    private val deleteCity: DeleteCityUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    private val searchStateFlow = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow {
                if (q.isBlank()) {
                    emit(SearchState.Idle)
                    return@flow
                }
                emit(SearchState.Loading)
                searchCities(q)
                    .onSuccess { results -> emit(SearchState.Results(results)) }
                    .onFailure { err -> emit(SearchState.Error(err)) }
            }
        }

    val uiState: StateFlow<CityListUiState> = combine(
        _query,
        searchStateFlow,
        cityRepository.observeSavedCities(),
        userPreferencesRepository.selectedCityId,
    ) { query, searchState, saved, selectedId ->
        CityListUiState(
            query = query,
            searchState = searchState,
            savedCities = saved,
            selectedCityId = selectedId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CityListUiState(),
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onCityTapped(city: City, onDone: () -> Unit) {
        viewModelScope.launch {
            selectCity(city)
            onDone()
        }
    }

    fun onDeleteCity(cityId: String) {
        viewModelScope.launch {
            deleteCity(cityId)
        }
    }
}