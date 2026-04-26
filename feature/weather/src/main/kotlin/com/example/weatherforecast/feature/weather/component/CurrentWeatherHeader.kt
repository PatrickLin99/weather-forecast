package com.example.weatherforecast.feature.weather.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weatherforecast.core.designsystem.component.TemperatureText
import com.example.weatherforecast.core.designsystem.component.WeatherIcon
import com.example.weatherforecast.core.model.Weather

@Composable
internal fun CurrentWeatherHeader(
    weather: Weather,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WeatherIcon(
            condition = weather.condition,
            modifier = Modifier.size(96.dp),
        )
        TemperatureText(
            value = weather.temperature,
            unit = weather.unit,
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = weather.condition.name,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}