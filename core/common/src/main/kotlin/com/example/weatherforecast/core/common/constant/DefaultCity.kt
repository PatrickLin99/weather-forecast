package com.example.weatherforecast.core.common.constant

import com.example.weatherforecast.core.model.City

object DefaultCity {
    val TAIPEI = City(
        id = "default_taipei",
        name = "Taipei",
        country = "Taiwan",
        latitude = 25.0330,
        longitude = 121.5654,
        isCurrentLocation = false,
    )
}