package com.example.weatherforecast.feature.weather.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weatherforecast.core.model.Weather

@Composable
internal fun WeatherDetailsRow(
    weather: Weather,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        WeatherDetailItem(label = "Feels like", value = "${weather.feelsLike.toInt()}°")
        WeatherDetailItem(label = "Humidity", value = "${weather.humidity}%")
        WeatherDetailItem(label = "Wind", value = "${weather.windSpeed.toInt()} m/s")
    }
}

@Composable
private fun WeatherDetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}