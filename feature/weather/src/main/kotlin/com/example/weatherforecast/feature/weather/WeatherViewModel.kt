package com.example.weatherforecast.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.onFailure
import com.example.weatherforecast.core.common.result.onSuccess
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
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val resolveInitialCity: ResolveInitialCityUseCase,
    observeSelectedCityWeather: ObserveSelectedCityWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
) : ViewModel() {

    private val _lastRefreshError = MutableStateFlow<AppError?>(null)

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
        launchRefresh { resolveInitialCity() }
    }

    fun onRefresh() {
        launchRefresh {
            when (val state = uiState.value) {
                is WeatherUiState.Success -> state.city
                else -> resolveInitialCity()
            }
        }
    }

    private fun launchRefresh(cityProvider: suspend () -> City) {
        viewModelScope.launch {
            try {
                val city = cityProvider()
                refreshWeather(city)
                    .onSuccess { _lastRefreshError.value = null }
                    .onFailure { _lastRefreshError.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _lastRefreshError.value = AppError.Unexpected(e)
            }
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