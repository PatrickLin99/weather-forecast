package com.example.weatherforecast.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.onFailure
import com.example.weatherforecast.core.common.result.onSuccess
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityUseCase
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.RefreshWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.ResolveInitialCityUseCase
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.TemperatureUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val resolveInitialCity: ResolveInitialCityUseCase,
    observeSelectedCityWeather: ObserveSelectedCityWeatherUseCase,
    private val observeSelectedCity: ObserveSelectedCityUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _lastRefreshError = MutableStateFlow<AppError?>(null)
    private val _hasLocationPermission = MutableStateFlow(false)
    private val _isLocationEnabled = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)
    private val refreshMutex = Mutex()

    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val locationPromptState: StateFlow<LocationPromptState> = combine(
        _hasLocationPermission,
        _isLocationEnabled,
    ) { hasPerm, locOn ->
        when {
            !hasPerm -> LocationPromptState.NeedsPermission
            !locOn -> LocationPromptState.LocationDisabled
            else -> LocationPromptState.Hidden
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocationPromptState.NeedsPermission,
    )

    val uiState: StateFlow<WeatherUiState> = combine(
        observeSelectedCityWeather(),
        _lastRefreshError,
    ) { (city, weather), lastError ->
        val state: WeatherUiState = when {
            weather != null -> WeatherUiState.Success(
                weather = weather,
                city = city,
                isStale = lastError != null,
                transientError = lastError,
            )
            lastError != null -> WeatherUiState.Error(
                error = lastError,
                canRetry = true,
            )
            else -> WeatherUiState.Loading
        }
        state
    }
        .catch { emit(WeatherUiState.Error(AppError.Unexpected(it), canRetry = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherUiState.Loading,
        )

    init {
        viewModelScope.launch {
            // Bootstrap with the permission value at this moment. The Composable's
            // first composition pushes the actual state via onLocationPermissionChanged,
            // which re-runs resolve when permission is granted.
            resolveInitialCity(useLocation = _hasLocationPermission.value)

            // Refresh whenever city OR unit changes — both require a new fetch.
            combine(
                observeSelectedCity(),
                userPreferencesRepository.temperatureUnit,
            ) { city, unit -> city to unit }
                .distinctUntilChanged()
                .collect { (city, _) ->
                    refresh(city)
                }
        }
    }

    fun onLocationPermissionChanged(granted: Boolean, locationEnabled: Boolean) {
        val wasUsable = _hasLocationPermission.value && _isLocationEnabled.value
        _hasLocationPermission.value = granted
        _isLocationEnabled.value = locationEnabled
        val nowUsable = granted && locationEnabled
        // Only resolve when transitioning from non-usable to usable. This avoids
        // redundant resolves on every WeatherScreen re-entry (e.g. back from CityList).
        if (nowUsable && !wasUsable) {
            viewModelScope.launch {
                try {
                    resolveInitialCity(useLocation = true)
                    // No manual refresh needed: resolveInitialCity upserts the
                    // current_location row, which triggers observeSavedCities() → the
                    // observe chain re-emits the updated City → init collect fires refresh.
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _lastRefreshError.value = AppError.Unexpected(e)
                }
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            try {
                val city = when (val s = uiState.value) {
                    is WeatherUiState.Success -> s.city
                    else -> resolveInitialCity(useLocation = _hasLocationPermission.value)
                }
                _isRefreshing.value = true
                try {
                    refresh(city)
                } finally {
                    _isRefreshing.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _lastRefreshError.value = AppError.Unexpected(e)
            }
        }
    }

    fun onToggleTemperatureUnit() {
        viewModelScope.launch {
            val current = userPreferencesRepository.temperatureUnit.first()
            val next = if (current == TemperatureUnit.CELSIUS) {
                TemperatureUnit.FAHRENHEIT
            } else {
                TemperatureUnit.CELSIUS
            }
            userPreferencesRepository.setTemperatureUnit(next)
        }
    }

    private suspend fun refresh(city: City) {
        refreshMutex.withLock {
            refreshWeather(city)
                .onSuccess { _lastRefreshError.value = null }
                .onFailure { _lastRefreshError.value = it }
        }
    }
}