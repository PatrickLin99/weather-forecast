package com.example.weatherforecast.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.weatherforecast.core.database.entity.CurrentWeatherEntity
import com.example.weatherforecast.core.database.entity.DailyForecastEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class WeatherDao {

    @Query("SELECT * FROM current_weather WHERE cityId = :cityId")
    abstract fun observeCurrentWeather(cityId: String): Flow<CurrentWeatherEntity?>

    @Query("SELECT * FROM daily_forecasts WHERE cityId = :cityId ORDER BY date ASC")
    abstract fun observeDailyForecasts(cityId: String): Flow<List<DailyForecastEntity>>

    @Upsert
    abstract suspend fun upsertCurrentWeather(weather: CurrentWeatherEntity)

    @Upsert
    abstract suspend fun upsertDailyForecasts(forecasts: List<DailyForecastEntity>)

    @Query("DELETE FROM daily_forecasts WHERE cityId = :cityId")
    abstract suspend fun deleteDailyForecasts(cityId: String)

    @Transaction
    open suspend fun upsertFullWeather(
        current: CurrentWeatherEntity,
        daily: List<DailyForecastEntity>,
    ) {
        upsertCurrentWeather(current)
        deleteDailyForecasts(current.cityId)
        upsertDailyForecasts(daily)
    }
}