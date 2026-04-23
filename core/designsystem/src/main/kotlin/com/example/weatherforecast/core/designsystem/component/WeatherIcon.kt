package com.example.weatherforecast.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.weatherforecast.core.model.WeatherCondition

@Composable
fun WeatherIcon(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (condition) {
        WeatherCondition.CLEAR -> Icons.Filled.WbSunny
        WeatherCondition.CLOUDY -> Icons.Filled.Cloud
        WeatherCondition.RAIN -> Icons.Filled.WaterDrop
        WeatherCondition.SNOW -> Icons.Filled.AcUnit
        WeatherCondition.THUNDERSTORM -> Icons.Filled.Bolt
        WeatherCondition.FOG -> Icons.Filled.Cloud
        WeatherCondition.UNKNOWN -> Icons.Filled.HelpOutline
    }
    Icon(
        imageVector = icon,
        contentDescription = condition.name,
        modifier = modifier,
    )
}