package com.example.weatherforecast.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.onFailure
import com.example.weatherforecast.core.common.result.onSuccess
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityUseCase
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.RefreshWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.ResolveInitialCityUseCase
import com.example.weatherforecast.core.model.City
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {

    private val _lastRefreshError = MutableStateFlow<AppError?>(null)
    private val _hasLocationPermission = MutableStateFlow(false)
    private val _isLocationEnabled = MutableStateFlow(true)
    private val refreshMutex = Mutex()

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
                transientMessage = lastError?.toUserMessage(),
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
            observeSelectedCity().collect { city ->
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
                    // observe chain re-emits the updated City → WeatherViewModel.init
                    // collect loop fires refresh automatically.
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _lastRefreshError.value = AppError.Unexpected(e)
                }
            }
        }
    }

    fun onRefresh() {
        launchRefresh {
            when (val state = uiState.value) {
                is WeatherUiState.Success -> state.city
                else -> resolveInitialCity(useLocation = _hasLocationPermission.value)
            }
        }
    }

    private fun launchRefresh(cityProvider: suspend () -> City) {
        viewModelScope.launch {
            try {
                refresh(cityProvider())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _lastRefreshError.value = AppError.Unexpected(e)
            }
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

private fun AppError.toUserMessage(): String = when (this) {
    AppError.NoNetwork -> "No internet connection"
    AppError.NetworkTimeout -> "Request timed out"
    is AppError.ServerError -> "Server error. Please try again later."
    AppError.UnknownNetworkError -> "Network error"
    AppError.DatabaseError -> "Local data error"
    is AppError.DataParsingError -> "Unexpected data format"
    is AppError.Unexpected -> "Something went wrong"
    AppError.CityNotFound,
    AppError.GeocoderFailed,
    AppError.LocationDisabled,
    AppError.LocationPermissionDenied,
    AppError.LocationTimeout,
    AppError.LocationUnavailable,
        -> "Something went wrong"
}