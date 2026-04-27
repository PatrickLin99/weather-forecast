package com.example.weatherforecast.feature.weather

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherforecast.core.designsystem.component.ErrorState
import com.example.weatherforecast.core.designsystem.component.LoadingIndicator
import com.example.weatherforecast.feature.weather.component.CurrentWeatherHeader
import com.example.weatherforecast.feature.weather.component.DailyForecastList
import com.example.weatherforecast.feature.weather.component.LocationDisabledBanner
import com.example.weatherforecast.feature.weather.component.LocationPermissionBanner
import com.example.weatherforecast.feature.weather.component.StaleDataBanner
import com.example.weatherforecast.feature.weather.component.WeatherDetailsRow
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherScreen(
    onNavigateToCityList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationPromptState by viewModel.locationPromptState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val permissionState = rememberPermissionState(
        permission = android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) { granted ->
        viewModel.onLocationPermissionChanged(
            granted = granted,
            locationEnabled = isLocationEnabled(context),
        )
    }

    LaunchedEffect(permissionState.status) {
        viewModel.onLocationPermissionChanged(
            granted = permissionState.status.isGranted,
            locationEnabled = isLocationEnabled(context),
        )
    }

    WeatherContent(
        uiState = uiState,
        locationPromptState = locationPromptState,
        onRequestPermission = { permissionState.launchPermissionRequest() },
        onOpenLocationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onRetry = viewModel::onRefresh,
        onNavigateToCityList = onNavigateToCityList,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherContent(
    uiState: WeatherUiState,
    locationPromptState: LocationPromptState,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (locationPromptState) {
                LocationPromptState.NeedsPermission -> LocationPermissionBanner(
                    onTap = onRequestPermission,
                )
                LocationPromptState.LocationDisabled -> LocationDisabledBanner(
                    onOpenSettings = onOpenLocationSettings,
                )
                LocationPromptState.Hidden -> Unit
            }
            Box(modifier = Modifier.fillMaxSize()) {
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

private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}