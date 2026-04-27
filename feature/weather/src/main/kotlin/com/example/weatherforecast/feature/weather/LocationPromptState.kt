package com.example.weatherforecast.feature.weather

sealed interface LocationPromptState {
    data object Hidden : LocationPromptState
    data object NeedsPermission : LocationPromptState
    data object LocationDisabled : LocationPromptState
}