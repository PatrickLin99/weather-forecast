package com.example.weatherforecast.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "hourly_forecasts",
    primaryKeys = ["cityId", "time"],
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["id"],
            childColumns = ["cityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class HourlyForecastEntity(
    val cityId: String,
    val time: Long,
    val temperature: Double,
    val conditionCode: String,
)