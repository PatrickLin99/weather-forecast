package com.example.weatherforecast.core.common.dispatcher

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DispatcherQualifier(val value: Dispatcher)