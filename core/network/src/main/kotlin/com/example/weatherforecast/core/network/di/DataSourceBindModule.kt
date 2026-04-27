package com.example.weatherforecast.core.network.di

import com.example.weatherforecast.core.network.datasource.CityRemoteDataSource
import com.example.weatherforecast.core.network.datasource.CityRemoteDataSourceImpl
import com.example.weatherforecast.core.network.datasource.WeatherRemoteDataSource
import com.example.weatherforecast.core.network.datasource.WeatherRemoteDataSourceImpl
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
    abstract fun bindWeatherRemoteDataSource(
        impl: WeatherRemoteDataSourceImpl,
    ): WeatherRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindCityRemoteDataSource(
        impl: CityRemoteDataSourceImpl,
    ): CityRemoteDataSource
}