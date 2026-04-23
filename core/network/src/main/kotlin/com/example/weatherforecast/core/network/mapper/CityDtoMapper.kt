package com.example.weatherforecast.core.network.mapper

import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.network.dto.GeocodingResultDto

internal fun GeocodingResultDto.toDomain(): City = City(
    id = id.toString(),
    name = name,
    country = country,
    latitude = latitude,
    longitude = longitude,
    isCurrentLocation = false,
)