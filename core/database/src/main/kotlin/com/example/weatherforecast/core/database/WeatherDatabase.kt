package com.example.weatherforecast.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.weatherforecast.core.database.dao.CityDao
import com.example.weatherforecast.core.database.dao.WeatherDao
import com.example.weatherforecast.core.database.entity.CityEntity
import com.example.weatherforecast.core.database.entity.CurrentWeatherEntity
import com.example.weatherforecast.core.database.entity.DailyForecastEntity
import com.example.weatherforecast.core.database.entity.HourlyForecastEntity

@Database(
    entities = [
        CityEntity::class,
        CurrentWeatherEntity::class,
        DailyForecastEntity::class,
        HourlyForecastEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class WeatherDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
    abstract fun weatherDao(): WeatherDao
}