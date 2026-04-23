package com.example.weatherforecast.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun WeatherAppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = WeatherAppTypography,
        shapes = WeatherAppShapes,
        content = content,
    )
}