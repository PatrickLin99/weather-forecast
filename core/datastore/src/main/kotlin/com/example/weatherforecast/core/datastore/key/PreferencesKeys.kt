package com.example.weatherforecast.core.datastore.key

import androidx.datastore.preferences.core.stringPreferencesKey

internal object PreferencesKeys {
    val SELECTED_CITY_ID = stringPreferencesKey("selected_city_id")
    val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
}