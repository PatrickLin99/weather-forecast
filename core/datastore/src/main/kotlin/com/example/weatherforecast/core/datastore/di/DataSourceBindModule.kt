package com.example.weatherforecast.core.datastore.di

import com.example.weatherforecast.core.datastore.UserPreferencesDataSource
import com.example.weatherforecast.core.datastore.UserPreferencesDataSourceImpl
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
    abstract fun bindUserPreferencesDataSource(
        impl: UserPreferencesDataSourceImpl,
    ): UserPreferencesDataSource
}