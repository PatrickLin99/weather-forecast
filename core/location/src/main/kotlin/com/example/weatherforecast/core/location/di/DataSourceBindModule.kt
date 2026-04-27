package com.example.weatherforecast.core.location.di

import com.example.weatherforecast.core.location.datasource.LocationDataSource
import com.example.weatherforecast.core.location.datasource.LocationDataSourceImpl
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
    abstract fun bindLocationDataSource(
        impl: LocationDataSourceImpl,
    ): LocationDataSource
}
