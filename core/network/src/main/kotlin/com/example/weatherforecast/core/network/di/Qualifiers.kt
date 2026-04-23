package com.example.weatherforecast.core.network.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ForecastRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class GeocodingRetrofit