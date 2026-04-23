package com.example.weatherforecast.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val WeatherAppTypography = Typography().copy(
    displayLarge = Typography().displayLarge.copy(
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        letterSpacing = (-0.5).sp,
    ),
)