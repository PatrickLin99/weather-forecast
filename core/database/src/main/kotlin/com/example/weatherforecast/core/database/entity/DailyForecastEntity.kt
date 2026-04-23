package com.example.weatherforecast.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "daily_forecasts",
    primaryKeys = ["cityId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["id"],
            childColumns = ["cityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class DailyForecastEntity(
    val cityId: String,
    val date: String,
    val tempMin: Double,
    val tempMax: Double,
    val conditionCode: String,
    val sunriseEpoch: Long?,
    val sunsetEpoch: Long?,
    val precipitationProbability: Int?,
)