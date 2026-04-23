package com.example.weatherforecast.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.onFailure
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.RefreshWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.ResolveInitialCityUseCase
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

    private val _transientMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WeatherUiState> = combine(
        observeSelectedCityWeather(),
        _transientMessage,
    ) { (city, weather), message ->
        val state: WeatherUiState = if (weather != null) {
            WeatherUiState.Success(
                weather = weather,
                city = city,
                isStale = message != null,
                transientMessage = message,
            )
        } else {
            WeatherUiState.Loading
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
            try {
                val initialCity = resolveInitialCity()
                refreshWeather(initialCity).onFailure { handleRefreshFailure(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _transientMessage.value = AppError.Unexpected(e).toUserMessage()
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            val currentCity = (uiState.value as? WeatherUiState.Success)?.city ?: return@launch
            refreshWeather(currentCity).onFailure { handleRefreshFailure(it) }
        }
    }

    fun onDismissTransientMessage() {
        _transientMessage.value = null
    }

    private fun handleRefreshFailure(error: AppError) {
        _transientMessage.value = error.toUserMessage()
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