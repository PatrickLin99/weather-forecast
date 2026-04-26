package com.example.weatherforecast.feature.weather.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weatherforecast.core.designsystem.component.WeatherIcon
import com.example.weatherforecast.core.model.DailyForecast

@Composable
internal fun DailyForecastList(
    forecasts: List<DailyForecast>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("7-Day Forecast", style = MaterialTheme.typography.titleMedium)
        forecasts.forEach { forecast ->
            DailyForecastRow(forecast = forecast)
        }
    }
}

@Composable
private fun DailyForecastRow(forecast: DailyForecast) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = forecast.date.toString(),
            modifier = Modifier.weight(1f),
        )
        WeatherIcon(
            condition = forecast.condition,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text("${forecast.tempMin.toInt()}° / ${forecast.tempMax.toInt()}°")
    }
}
