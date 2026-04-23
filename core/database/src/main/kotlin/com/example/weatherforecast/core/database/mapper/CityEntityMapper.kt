package com.example.weatherforecast.core.database.mapper

import com.example.weatherforecast.core.database.entity.CityEntity
import com.example.weatherforecast.core.model.City

internal fun City.toEntity(): CityEntity = CityEntity(
    id = id,
    name = name,
    country = country,
    latitude = latitude,
    longitude = longitude,
    isCurrentLocation = isCurrentLocation,
    addedAt = System.currentTimeMillis(),
)

internal fun CityEntity.toDomain(): City = City(
    id = id,
    name = name,
    country = country,
    latitude = latitude,
    longitude = longitude,
    isCurrentLocation = isCurrentLocation,
)