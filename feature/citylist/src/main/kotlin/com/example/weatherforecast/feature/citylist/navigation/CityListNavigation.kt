package com.example.weatherforecast.feature.citylist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.weatherforecast.feature.citylist.CityListScreen
import kotlinx.serialization.Serializable

@Serializable
data object CityListRoute

fun NavGraphBuilder.cityListScreen(
    onNavigateBack: () -> Unit,
) {
    composable<CityListRoute> {
        CityListScreen(onNavigateBack = onNavigateBack)
    }
}