package com.example.weatherforecast.feature.weather.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.weatherforecast.feature.weather.WeatherScreen
import kotlinx.serialization.Serializable

@Serializable
data object WeatherRoute

fun NavGraphBuilder.weatherScreen(
    onNavigateToCityList: () -> Unit,
) {
    composable<WeatherRoute> {
        WeatherScreen(onNavigateToCityList = onNavigateToCityList)
    }
}