package com.example.weatherforecast.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.weatherforecast.feature.citylist.navigation.CityListRoute
import com.example.weatherforecast.feature.citylist.navigation.cityListScreen
import com.example.weatherforecast.feature.weather.navigation.WeatherRoute
import com.example.weatherforecast.feature.weather.navigation.weatherScreen

@Composable
fun WeatherAppNavHost(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = WeatherRoute,
    ) {
        weatherScreen(
            onNavigateToCityList = {
                navController.navigate(CityListRoute)
            },
        )
        cityListScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
        )
    }
}