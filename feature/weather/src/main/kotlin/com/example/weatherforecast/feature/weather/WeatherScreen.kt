package com.example.weatherforecast.feature.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherforecast.core.designsystem.component.ErrorState
import com.example.weatherforecast.core.designsystem.component.LoadingIndicator
import com.example.weatherforecast.feature.weather.component.CurrentWeatherHeader
import com.example.weatherforecast.feature.weather.component.DailyForecastList
import com.example.weatherforecast.feature.weather.component.StaleDataBanner
import com.example.weatherforecast.feature.weather.component.WeatherDetailsRow

@Composable
fun WeatherScreen(
    onNavigateToCityList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WeatherContent(
        uiState = uiState,
        onRetry = viewModel::onRefresh,
        onNavigateToCityList = onNavigateToCityList,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherContent(
    uiState: WeatherUiState,
    onRetry: () -> Unit,
    onNavigateToCityList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    when (uiState) {
                        is WeatherUiState.Success -> Text(
                            text = uiState.city.name,
                            modifier = Modifier.clickable { onNavigateToCityList() },
                        )
                        else -> Text("Weather")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToCityList) {
                        Text("Cities")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                is WeatherUiState.Loading -> LoadingIndicator()
                is WeatherUiState.Error -> ErrorState(
                    message = "Unable to load weather",
                    onRetry = if (uiState.canRetry) onRetry else null,
                    modifier = Modifier.fillMaxSize(),
                )
                is WeatherUiState.Success -> WeatherSuccessContent(state = uiState)
            }
        }
    }
}

@Composable
private fun WeatherSuccessContent(state: WeatherUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isStale && state.transientMessage != null) {
            StaleDataBanner(message = state.transientMessage)
        }
        CurrentWeatherHeader(weather = state.weather)
        WeatherDetailsRow(weather = state.weather)
        DailyForecastList(forecasts = state.weather.daily)
    }
}