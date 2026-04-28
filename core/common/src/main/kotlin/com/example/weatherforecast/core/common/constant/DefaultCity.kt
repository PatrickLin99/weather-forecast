package com.example.weatherforecast.core.common.constant

import com.example.weatherforecast.core.model.City

object DefaultCity {
    /**
     * Initial city on fresh install and fallback when deletion would orphan selectedCityId.
     *
     * id, latitude, and longitude match Open-Meteo Geocoding's record for Taipei (GeoNames-backed,
     * stable). This ensures search results for Taipei Upsert into the same row rather than
     * creating a duplicate alongside the default-installed one.
     */
    val TAIPEI = City(
        id = "1668341",
        name = "Taipei",
        country = "Taiwan",
        latitude = 25.05306,
        longitude = 121.52639,
        isCurrentLocation = false,
    )
}