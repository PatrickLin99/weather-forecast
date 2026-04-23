package com.example.weatherforecast.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.example.weatherforecast.core.model.TemperatureUnit

@Composable
fun TemperatureText(
    value: Double,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
) {
    val symbol = when (unit) {
        TemperatureUnit.CELSIUS -> "°C"
        TemperatureUnit.FAHRENHEIT -> "°F"
    }
    Text(
        text = "${value.toInt()}$symbol",
        style = style,
        modifier = modifier,
    )
}