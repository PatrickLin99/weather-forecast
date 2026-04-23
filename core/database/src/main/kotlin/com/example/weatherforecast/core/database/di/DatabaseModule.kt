package com.example.weatherforecast.core.database.di

import android.content.Context
import androidx.room.Room
import com.example.weatherforecast.core.database.WeatherDatabase
import com.example.weatherforecast.core.database.dao.CityDao
import com.example.weatherforecast.core.database.dao.WeatherDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeatherDatabase =
        Room.databaseBuilder(
            context,
            WeatherDatabase::class.java,
            "weather.db",
        ).build()

    @Provides
    fun provideCityDao(db: WeatherDatabase): CityDao = db.cityDao()

    @Provides
    fun provideWeatherDao(db: WeatherDatabase): WeatherDao = db.weatherDao()
}