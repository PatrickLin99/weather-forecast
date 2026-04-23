package com.example.weatherforecast.core.designsystem.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Blue10 = Color(0xFF001F3D)
private val Blue40 = Color(0xFF0056A8)
private val Blue80 = Color(0xFFAAC7FF)
private val Blue90 = Color(0xFFD5E3FF)

private val Gray10 = Color(0xFF1A1C1E)
private val Gray20 = Color(0xFF2F3033)
private val Gray90 = Color(0xFFE2E2E5)
private val Gray95 = Color(0xFFF0F0F3)
private val Gray99 = Color(0xFFFBFBFE)

internal val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    background = Gray99,
    onBackground = Gray10,
    surface = Gray99,
    onSurface = Gray10,
    surfaceVariant = Gray90,
    onSurfaceVariant = Gray20,
    outline = Gray20,
)