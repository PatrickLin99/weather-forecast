package com.example.weatherforecast.core.database.di

import com.example.weatherforecast.core.database.datasource.CityLocalDataSource
import com.example.weatherforecast.core.database.datasource.CityLocalDataSourceImpl
import com.example.weatherforecast.core.database.datasource.WeatherLocalDataSource
import com.example.weatherforecast.core.database.datasource.WeatherLocalDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataSourceBindModule {

    @Binds
    @Singleton
    abstract fun bindWeatherLocalDataSource(
        impl: WeatherLocalDataSourceImpl,
    ): WeatherLocalDataSource

    @Binds
    @Singleton
    abstract fun bindCityLocalDataSource(
        impl: CityLocalDataSourceImpl,
    ): CityLocalDataSource
}